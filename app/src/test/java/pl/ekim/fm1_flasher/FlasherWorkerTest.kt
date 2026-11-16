package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class FlasherWorkerTest {
    @Test fun slowFleetWritesLeaveCallerFreeAndQueuedEditsKeepOrderAndPinning() {
        val dir = Files.createTempDirectory("fleet-worker").toFile()
        val worker = FlasherWorker()
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val caller = Thread.currentThread()
        val events = mutableListOf<String>()
        try {
            lateinit var fleet: FleetStore
            worker.execute {
                try {
                    fleet = FleetStore(File(dir, "fleet.json")) { file, bytes ->
                        assertNotSame(caller, Thread.currentThread())
                        writing.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                        LocalLibrary.atomicWrite(file, bytes)
                    }
                    fleet.attach(listOf(Endpoint(1, "usb", 1)))
                    events += "attached"
                } catch (error: Throwable) { failure.set(error) }
            }
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            // We can submit and inspect published snapshots while the filesystem is blocked.
            worker.execute {
                try {
                    val device = fleet.devices.single()
                    fleet.identify(device.endpoint!!, FirmwareIdentity("FM-1", "015"), null)
                    fleet.configure(device.id, alias = "Pinned", chip = FlashChip.OLD)
                    events += "configured"
                    fleet.enqueue(device.id, localEntry())
                    events += "enqueued"
                    assertThrows(IllegalStateException::class.java) { fleet.configure(device.id, alias = "Too late") }
                    assertEquals("Pinned", fleet.jobs.single().alias)
                    assertTrue(FleetPolicy.pinned(fleet.jobs, localEntry().key))
                } catch (error: Throwable) { failure.set(error) }
                finally { finished.countDown() }
            }
            assertTrue(fleet.jobs.isEmpty())
            assertEquals(1L, finished.count)
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Worker failed", it) }
            assertEquals(listOf("attached", "configured", "enqueued"), events)
        } finally { release.countDown(); worker.shutdownNow(); dir.deleteRecursively() }
    }
}
