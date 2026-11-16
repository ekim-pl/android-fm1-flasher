package pl.ekim.fm1_flasher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FleetRetentionTest {
    private val day = 24L * 60 * 60 * 1000
    private fun fixture(block: (File, JSONObject) -> Unit) {
        val dir = Files.createTempDirectory("fleet-retention").toFile()
        try {
            val file = File(dir, "fleet.json")
            val store = FleetStore(file)
            val endpoint = Endpoint(1, "usb/1", 1)
            store.attach(listOf(endpoint))
            store.identify(endpoint, FirmwareIdentity("FM-1", "015"), null)
            store.enqueue(store.devices.single().id, localEntry())
            block(file, JSONObject(file.readText()))
        } finally { dir.deleteRecursively() }
    }
    private fun row(root: JSONObject, id: String, outcome: JobOutcome, time: Long) =
        JSONObject(root.getJSONArray("jobs").getJSONObject(0).toString())
            .put("id", id).put("outcome", outcome.name).put("time", time)

    @Test fun defaultThirtyDaysPrunesLargeLegacyHistoryWithoutLosingQueuedOrInterruptedJobs() = fixture { file, root ->
        val now = System.currentTimeMillis()
        val rows = JSONArray()
        repeat(10_000) { rows.put(row(root, "old-$it", JobOutcome.CANCELLED, now - 31 * day)) }
        rows.put(row(root, "queued", JobOutcome.QUEUED, 1))
        rows.put(row(root, "running", JobOutcome.RUNNING, 1))
        rows.put(row(root, "recent", JobOutcome.CONFIRMED, now - day))
        root.remove("historyRetentionMillis") // Upgrade from the old schema.
        root.put("jobs", rows); file.writeText(root.toString())
        val store = FleetStore(file)
        assertFalse(store.loadFailed)
        assertEquals(30 * day, store.historyRetentionMillis)
        assertEquals(listOf("queued", "running", "recent"), store.jobs.map { it.id })
        assertEquals(JobOutcome.UNCONFIRMED, store.jobs[1].outcome)
        assertEquals("fleet_interrupted", store.jobs[1].phase)
        assertEquals(3, JSONObject(file.readText()).getJSONArray("jobs").length())
        assertTrue(FleetPolicy.pinned(store.jobs, store.jobs.first().packageKey))
    }

    @Test fun configurableBoundaryUnlimitedAndManualClearPreserveActiveOrderAndPins() = fixture { file, root ->
        val now = System.currentTimeMillis()
        root.put("jobs", JSONArray()
            .put(row(root, "before", JobOutcome.CANCELLED, now - 7 * day - 1))
            .put(row(root, "queued", JobOutcome.QUEUED, 0))
            .put(row(root, "boundary", JobOutcome.CONFIRMED, now - 7 * day))
            .put(row(root, "running", JobOutcome.QUEUED, 0)))
        file.writeText(root.toString())
        val store = FleetStore(file)
        store.result("running", JobOutcome.RUNNING, "write")
        store.setHistoryRetention(7 * day, now)
        assertEquals(listOf("queued", "boundary", "running"), store.jobs.map { it.id })
        assertEquals(7 * day, FleetStore(file).historyRetentionMillis)
        val token = FleetPolicy.token(store.jobs)
        store.setHistoryRetention(null)
        store.pruneHistory(null, Long.MAX_VALUE)
        assertEquals(3, store.jobs.size)
        assertNull(FleetStore(file).historyRetentionMillis)
        store.clearHistory()
        assertEquals(listOf("queued", "running"), store.jobs.map { it.id })
        assertEquals(JobOutcome.RUNNING, store.jobs.last().outcome)
        assertEquals(token, FleetPolicy.token(store.jobs))
        assertTrue(FleetPolicy.pinned(store.jobs, store.jobs.first().packageKey))
        assertThrows(IllegalStateException::class.java) { store.configure(store.jobs.first().deviceId, alias = "Changed") }
        assertThrows(IllegalArgumentException::class.java) { store.setHistoryRetention(-1) }
    }

    @Test fun failedClearAndPolicyWriteRollBackHistorySettingAndConfirmation() = fixture { file, _ ->
        var fail = false
        val store = FleetStore(file) { path, bytes ->
            if (fail) throw IOException("disk full")
            LocalLibrary.atomicWrite(path, bytes)
        }
        val id = store.jobs.single().id
        store.result(id, JobOutcome.CONFIRMED, "done")
        val bytes = file.readText()
        fail = true
        assertThrows(IOException::class.java) { store.clearHistory() }
        assertEquals(id, store.jobs.single().id)
        assertThrows(IOException::class.java) { store.setHistoryRetention(0, Long.MAX_VALUE) }
        assertEquals(30 * day, store.historyRetentionMillis)
        assertEquals(id, store.confirmedJobId)
        assertEquals(bytes, file.readText())
    }

    @Test fun failedAutomaticPruningKeepsDecodedQueueAndOriginalFile() = fixture { file, root ->
        root.put("jobs", JSONArray().put(row(root, "expired", JobOutcome.CANCELLED, 0))
            .put(row(root, "active", JobOutcome.QUEUED, 0)))
        file.writeText(root.toString())
        val bytes = file.readText()
        val store = FleetStore(file) { _, _ -> throw IOException("disk full") }
        assertTrue(store.loadFailed)
        assertEquals(listOf("expired", "active"), store.jobs.map { it.id })
        assertEquals(bytes, file.readText())
    }

    @Test fun retentionNeverTouchesBackupsOrUnresolvedJournal() = fixture { file, _ ->
        val journalFile = File(file.parentFile, "flash-session.json")
        val backup = File(file.parentFile, "backup.bin").apply { writeText("backup") }
        val store = FleetStore(file)
        val job = store.jobs.single()
        val journal = FlashJournal(journalFile)
        journal.record(job.hash, job.deviceId, "write", jobId = job.id)
        store.result(job.id, JobOutcome.UNCONFIRMED, "interrupted")
        val marker = journalFile.readText()
        store.setHistoryRetention(0, Long.MAX_VALUE)
        store.clearHistory()
        assertTrue(store.jobs.isEmpty())
        assertEquals(marker, journalFile.readText())
        assertTrue(journal.unresolved(store.confirmedJobId))
        assertEquals("backup", backup.readText())
    }
}
