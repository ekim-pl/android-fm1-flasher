package pl.ekim.fm1_flasher

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files

class FleetStoreTest {
    private fun fixture(block: (FleetStore, File) -> Unit) {
        val dir = Files.createTempDirectory("fleet-test").toFile()
        try { val file = File(dir, "fleet.json"); block(FleetStore(file), file) } finally { dir.deleteRecursively() }
    }
    private fun ready(store: FleetStore): FleetDevice {
        val endpoint = Endpoint(2, "usb/A", 9)
        store.attach(listOf(endpoint)); store.identify(endpoint, FirmwareIdentity("FM-1", "015"), null)
        val id = store.devices.single().id
        store.configure(id, alias = "Bass", chip = FlashChip.OLD)
        return store.devices.single()
    }
    @Test fun processDeathKeepsHistoryButNeverRestoresPhysicalAttachment() = fixture { store, file ->
        val device = ready(store); store.enqueue(device.id, localEntry())
        val job = store.jobs.single(); store.result(job.id, JobOutcome.RUNNING, "flash_stage_write", 47)
        val restored = FleetStore(file)
        assertEquals("Bass", restored.devices.single().alias)
        assertNull(restored.devices.single().endpoint)
        assertEquals(JobOutcome.UNCONFIRMED, restored.jobs.single().outcome)
        assertEquals("fleet_interrupted", restored.jobs.single().phase)
        assertFalse(FleetPolicy.ready(job, restored.devices.single()))
        restored.attach(listOf(job.endpoint))
        assertEquals(2, restored.devices.size)
        assertNotEquals(device.id, restored.devices.single { it.endpoint != null }.id)
    }
    @Test fun queuedTargetAndPackageAreFrozenAndProtected() = fixture { store, _ ->
        val device = ready(store); val entry = localEntry(); store.enqueue(device.id, entry)
        val job = store.jobs.single()
        assertEquals(entry.sha256, job.hash); assertEquals(device.endpoint, job.endpoint)
        assertTrue(FleetPolicy.pinned(store.jobs, entry.key))
        assertThrows(IllegalStateException::class.java) { store.enqueue(device.id, entry) }
        assertThrows(IllegalStateException::class.java) { store.configure(device.id, alias = "Other") }
        assertThrows(IllegalStateException::class.java) { store.configure(device.id, chip = FlashChip.NEW) }
        val token = FleetPolicy.token(store.jobs)
        store.cancel(job.id)
        assertNotEquals(token, FleetPolicy.token(store.jobs))
        assertFalse(FleetPolicy.pinned(store.jobs, entry.key))
    }
    @Test fun reusedAddressAndMidiIdRequireNewRecord() = fixture { store, _ ->
        val device = ready(store)
        store.attach(emptyList()); store.attach(listOf(device.endpoint!!.copy(generation = 10)))
        assertNull(store.devices.single { it.id == device.id }.endpoint)
        assertNull(store.devices.single { it.endpoint != null }.identity)
        assertEquals(FlashChip.UNKNOWN, store.devices.single { it.endpoint != null }.chip)
    }
    @Test fun explicitPairingPreservesNameButDropsOldChipEvidence() = fixture { store, _ ->
        val old = ready(store); store.attach(emptyList())
        val next = Endpoint(3, "usb/B", 10); store.attach(listOf(next)); store.identify(next, FirmwareIdentity("FM-1", "016"), null)
        store.pair(old.id, store.devices.single { it.endpoint == next }.id)
        assertEquals(1, store.devices.size); assertEquals("Bass", store.devices.single().alias)
        assertEquals(FlashChip.UNKNOWN, store.devices.single().chip)
        assertEquals(next, store.devices.single().endpoint)
    }
    @Test fun deniedOrUnidentifiedDeviceCannotBeQueued() = fixture { store, _ ->
        store.attach(listOf(Endpoint(1, "usb")))
        assertThrows(IllegalStateException::class.java) { store.enqueue(store.devices.single().id, localEntry()) }
        assertTrue(store.jobs.isEmpty())
    }
    @Test fun readOnlyPlanDoesNotChangeWithUiPackageSelection() = fixture { store, _ ->
        val device = ready(store); store.enqueue(device.id, localEntry())
        val job = store.jobs.single()
        assertTrue(FleetPolicy.ready(job, device))
        assertFalse(FleetPolicy.ready(job, device.copy(endpoint = device.endpoint!!.copy(generation = 10))))
        assertFalse(FleetPolicy.ready(job, device.copy(identity = FirmwareIdentity("FM-1", "016"))))
        assertFalse(FleetPolicy.ready(job, device.copy(chip = FlashChip.NEW)))
    }
    @Test fun brokenJournalBlocksInsteadOfOverwritingEvidence() = fixture { _, file ->
        file.writeText("broken")
        val restored = FleetStore(file)
        assertTrue(restored.loadFailed)
        restored.attach(listOf(Endpoint(1, "usb")))
        assertTrue(restored.devices.isEmpty())
        assertEquals("broken", file.readText())
    }
    @Test fun failedAtomicWriteRollsBackInMemoryPlan() = fixture { store, file ->
        val device = ready(store)
        // A directory at the atomic temporary-file path simulates a persistent write failure.
        File(file.parentFile, file.name + ".tmp").mkdir()
        assertThrows(Exception::class.java) { store.enqueue(device.id, localEntry()) }
        assertTrue(store.jobs.isEmpty())
    }
    @Test fun everyPackageIsCheckedBeforeFirstWriteAndFailureStopsPlan() = fixture { store, _ ->
        val device = ready(store); store.enqueue(device.id, localEntry())
        val first = store.jobs.single(); val second = first.copy(id = "second", deviceId = "second-device")
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            FleetPolicy.runPlan(listOf(first, second), { false }, { events += "check-${it.id}"; if (it.id == second.id) error("Bad hash") }, { events += "write-${it.id}" })
        }
        assertEquals(listOf("check-${first.id}", "check-second"), events)
        events.clear()
        assertThrows(IllegalStateException::class.java) {
            FleetPolicy.runPlan(listOf(first, second), { false }, {}, { events += it.id; error("Lost hub") })
        }
        assertEquals(listOf(first.id), events)
    }
    @Test fun stopFinishesCurrentJobAndKeepsRemainingJobsQueued() = fixture { store, _ ->
        val device = ready(store); store.enqueue(device.id, localEntry())
        val first = store.jobs.single(); val second = first.copy(id = "second", deviceId = "second-device")
        var stop = false; val ran = mutableListOf<String>()
        FleetPolicy.runPlan(listOf(first, second), { stop }, {}, { ran += it.id; stop = true; ran += "finished" })
        assertEquals(listOf(first.id, "finished"), ran)
        assertEquals(JobOutcome.QUEUED, second.outcome)
    }
    @Test fun duplicateSerialNeverMergesTwoPhysicalAttachments() = fixture { store, _ ->
        val first = Endpoint(1, "usb/1", 1); val second = Endpoint(2, "usb/2", 2)
        store.attach(listOf(first, second))
        listOf(first, second).forEach { store.identify(it, FirmwareIdentity("FM-1", "015"), "DUPLICATE") }
        assertEquals(2, store.devices.map { it.id }.toSet().size)
        store.attach(emptyList()); store.attach(listOf(Endpoint(3, "usb/3", 3)))
        store.identify(Endpoint(3, "usb/3", 3), FirmwareIdentity("FM-1", "015"), "DUPLICATE")
        assertEquals(3, store.devices.size)
    }
    @Test fun twoMidiEndpointsOnSameUsbDeviceCannotBecomeTwoJobs() = fixture { store, _ ->
        val first = Endpoint(1, "usb/shared", 1); val second = Endpoint(2, "usb/shared", 2)
        store.attach(listOf(first, second)); store.identify(first, FirmwareIdentity("FM-1", "015"), null)
        val id = store.devices.single { it.endpoint == first }.id
        store.configure(id, chip = FlashChip.OLD)
        assertThrows(IllegalStateException::class.java) { store.enqueue(id, localEntry()) }
        assertTrue(store.jobs.isEmpty())
    }
}
