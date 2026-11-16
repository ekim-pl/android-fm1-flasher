package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.ArrayDeque

class FlashPersistenceTest {
    private class Fixture : AutoCloseable {
        val dir = Files.createTempDirectory("flash-persistence").toFile()
        val fleetFile = File(dir, "fleet.json")
        val journalFile = File(dir, "journal.json")
        var failNextFleet = false
        var failFleet = false
        var failJournal = false
        var writes = 0
        var stopped = false
        var warnings = 0
        val fleet = FleetStore(fleetFile) { file, bytes ->
            writes++
            if (failNextFleet) { failNextFleet = false; throw IOException("one failed write") }
            if (failFleet) throw IOException("disk unavailable")
            LocalLibrary.atomicWrite(file, bytes)
        }
        val journal = FlashJournal(journalFile) { file, bytes ->
            writes++
            if (failJournal) throw IOException("journal unavailable")
            LocalLibrary.atomicWrite(file, bytes)
        }
        val job: FlashJob
        val persistence: FlashPersistence
        init {
            val endpoint = Endpoint(1, "usb/1", 1)
            fleet.attach(listOf(endpoint))
            fleet.identify(endpoint, FirmwareIdentity("FM-1", "015"), null)
            val device = fleet.devices.single()
            fleet.configure(device.id, alias = "Test", chip = FlashChip.OLD)
            fleet.enqueue(device.id, localEntry())
            job = fleet.jobs.single()
            persistence = FlashPersistence(fleet, journal, job) { stopped = true; warnings++ }
        }
        override fun close() { dir.deleteRecursively() }
    }

    private class Link(override val endpoint: Endpoint, val name: String, val terminal: Long?) : MidiConnection {
        val sent = mutableListOf<ByteArray>()
        val replies = ArrayDeque<ByteArray>()
        var closed = false
        override fun send(bytes: ByteArray) {
            sent += bytes
            if (bytes.contentEquals(Protocol.identityQuery)) replies += identity(name)
            if (bytes.contentEquals(Protocol.upgrade) && terminal != null) {
                replies += readRequest(0, 4); replies += readRequest(terminal, 8)
            }
        }
        override fun receive(timeoutMs: Long) = replies.pollFirst()
        override fun close() { closed = true }
    }

    @Test fun eitherRequiredWriteFailingPreventsAllDeviceCommands() {
        for (journalFailure in listOf(false, true)) Fixture().use { f ->
            f.failJournal = journalFailure; f.failFleet = !journalFailure
            val link = Link(f.job.endpoint, "FM-1_015", Protocol.VERIFIED)
            var cleaned = false
            assertThrows(IOException::class.java) {
                try {
                    f.persistence.begin()
                    link.send(Protocol.upgrade)
                } catch (error: Exception) {
                    f.persistence.unconfirmed("fleet_unconfirmed")
                    throw error
                } finally { link.close(); cleaned = true }
            }
            assertTrue(link.sent.isEmpty()); assertTrue(cleaned && link.closed)
            assertTrue(f.stopped); assertEquals(1, f.warnings)
            assertEquals(JobOutcome.UNCONFIRMED, f.fleet.jobs.single().outcome)
            assertNotEquals(JobOutcome.CONFIRMED, FleetStore(f.fleetFile).jobs.single().outcome)
        }
    }

    @Test fun unavailableDiskDuringTransferDoesNotInterruptSessionAndStopsNextJob() = Fixture().use { f ->
        val target = Link(f.job.endpoint, "FM-1_015", Protocol.VERIFIED)
        val loader = Link(Endpoint(2, "usb/2"), "ota-FM-1_015", Protocol.FINISHED)
        val installed = Link(Endpoint(3, "usb/3"), "FM-1_900", null)
        val raw = testPackage()
        val firmware = FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), "FM-1_900")
        var clock = 0L
        var transferWrites = -1
        var reconnects = 0
        val ran = mutableListOf<String>()
        FleetPolicy.runPlan(listOf(f.job, f.job.copy(id = "next")), { f.stopped }, {}, { job ->
            ran += job.id
            f.persistence.begin()
            assertTrue(FlashJournal(f.journalFile).unresolved())
            transferWrites = f.writes
            val session = FlashSession({ _, isLoader ->
                reconnects++
                val next = if (isLoader) loader else installed
                f.persistence.rebind(next.endpoint)
                next
            }, { _, percent ->
                f.failFleet = true; f.failJournal = true
                f.persistence.progress("writing", percent)
                assertEquals(transferWrites, f.writes)
            }, { clock += it }, { clock++ })
            assertEquals(FirmwareIdentity("FM-1", "900"), session.flash(firmware, target, f.job.expected))
            assertEquals(transferWrites, f.writes)
            assertFalse(f.persistence.confirmed())
        })
        assertEquals(listOf(f.job.id), ran)
        assertEquals(2, reconnects)
        assertTrue(target.closed && loader.closed && installed.closed)
        assertTrue(f.stopped); assertEquals(1, f.warnings)
        val restored = FleetStore(f.fleetFile)
        assertEquals(JobOutcome.UNCONFIRMED, restored.jobs.single().outcome)
        assertTrue(FlashJournal(f.journalFile).unresolved(restored.confirmedJobId))
    }

    @Test fun successfulResultIsAtomicAndHistoryClearDoesNotReopenResolvedMarker() = Fixture().use { f ->
        f.persistence.begin()
        repeat(1000) { f.persistence.progress("write", it % 101) }
        assertTrue(f.persistence.confirmed())
        val restored = FleetStore(f.fleetFile)
        assertEquals(JobOutcome.CONFIRMED, restored.jobs.single().outcome)
        assertFalse(f.journal.unresolved(restored.confirmedJobId))
        val marker = f.journalFile.readText()
        restored.clearHistory()
        val cleared = FleetStore(f.fleetFile)
        assertTrue(cleared.jobs.isEmpty())
        assertEquals(marker, f.journalFile.readText())
        assertFalse(f.journal.unresolved(cleared.confirmedJobId))
    }

    @Test fun transportFailureAndRepeatedResultWriteFailurePreserveOriginalExceptionAndCleanup() = Fixture().use { f ->
        f.persistence.begin()
        f.failFleet = true
        val original = IOException("transport lost")
        var cleaned = false
        val caught = assertThrows(IOException::class.java) {
            try { throw original }
            catch (error: Exception) { f.persistence.unconfirmed("transport_lost"); throw error }
            finally { cleaned = true }
        }
        assertSame(original, caught); assertTrue(cleaned)
        assertEquals(JobOutcome.UNCONFIRMED, f.fleet.jobs.single().outcome)
        assertTrue(f.stopped)
        // Even a recovered disk must not later turn this attempt into a durable success.
        f.failFleet = false
        assertFalse(f.persistence.confirmed())
        val restored = FleetStore(f.fleetFile)
        assertTrue(f.journal.unresolved(restored.confirmedJobId))
        assertEquals(JobOutcome.UNCONFIRMED, restored.jobs.single().outcome)
    }

    @Test fun restartBeforeFinalCommitStaysUnconfirmedEvenAfterVolatileHundredPercent() = Fixture().use { f ->
        f.persistence.begin()
        f.persistence.progress("finished", 100)
        val restored = FleetStore(f.fleetFile)
        assertEquals(JobOutcome.UNCONFIRMED, restored.jobs.single().outcome)
        assertTrue(f.journal.unresolved(restored.confirmedJobId))
        val marker = f.journalFile.readText()
        restored.clearHistory()
        assertTrue(f.journal.unresolved(FleetStore(f.fleetFile).confirmedJobId))
        assertEquals(marker, f.journalFile.readText())
    }
    @Test fun singleFinalWriteFailureCannotBeReclassifiedAsSuccessBySuccessfulErrorWrite() = Fixture().use { f ->
        f.persistence.begin()
        f.failNextFleet = true
        assertFalse(f.persistence.confirmed())
        assertTrue(f.stopped)
        val restored = FleetStore(f.fleetFile)
        assertEquals(JobOutcome.UNCONFIRMED, restored.jobs.single().outcome)
        assertNull(restored.confirmedJobId)
        assertTrue(f.journal.unresolved(restored.confirmedJobId))
    }

    @Test fun warningFailureDoesNotMaskStorageFailureOrPreventErrorRecording() = Fixture().use { f ->
        val persistence = FlashPersistence(f.fleet, f.journal, f.job) { error("notification unavailable") }
        f.failJournal = true
        assertThrows(IOException::class.java) { persistence.begin() }
        persistence.unconfirmed("fleet_storage_error")
        assertNotNull(persistence.failure)
        assertEquals(JobOutcome.UNCONFIRMED, FleetStore(f.fleetFile).jobs.single().outcome)
    }

}
