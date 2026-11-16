package pl.ekim.fm1_flasher

import android.content.*
import android.os.IBinder
import android.test.InstrumentationTestCase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class HistoryServiceTest : InstrumentationTestCase() {
    fun testActivityActionsPersistAndBusyServiceRejectsMutations() {
        val context = instrumentation.targetContext
        val database = CatalogDatabase.get(context)
        val offline = database.setting("offline")
        database.setting("offline", "true")
        val connected = CountDownLatch(1)
        var service: FlasherService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { service = (binder as FlasherService.LocalBinder).service; connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val activity = instrumentation.startActivitySync(Intent(context, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        assertTrue(context.bindService(Intent(context, FlasherService::class.java), connection, Context.BIND_AUTO_CREATE))
        val file = File(context.cacheDir, "history-service-test.json").apply { delete() }
        val fleetField = FlasherService::class.java.getDeclaredField("fleet").apply { isAccessible = true }
        var previousFleet: Any? = null
        var original: Long? = 30L * 86_400_000
        fun waitUntil(check: () -> Boolean) {
            repeat(200) { if (check()) return; Thread.sleep(50) }
            assertTrue("Timed out waiting for history operation", check())
        }
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val active = service!!
            val bound = ComposeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }
            waitUntil { bound.get(activity) === active && !active.state.busy }
            original = active.historyRetentionMillis
            val fleet = FleetStore(file)
            val endpoint = Endpoint(1234, "test-history", 1)
            fleet.attach(listOf(endpoint))
            fleet.identify(endpoint, FirmwareIdentity("FM-1", "015"), null)
            val entry = CatalogEntry("test", "Test", "", "", "1", "", null, null, "a".repeat(64), "FM-1", "new", false)
            fleet.enqueue(fleet.devices.single().id, entry)
            fleet.result(fleet.jobs.single().id, JobOutcome.CANCELLED, "cancelled")
            fleet.enqueue(fleet.devices.single().id, entry)
            assertEquals(2, fleet.jobs.size)
            previousFleet = fleetField.get(active)
            instrumentation.runOnMainSync { fleetField.set(active, fleet) }
            for (days in listOf("7", "30", "90", "unlimited")) {
                instrumentation.runOnMainSync { activity.perform(AppAction(AppActionKind.HISTORY_RETENTION, days)) }
                val expected = if (days == "unlimited") null else days.toLong() * 86_400_000
                waitUntil { !active.state.busy && active.historyRetentionMillis == expected }
                assertEquals(expected, FleetStore(file).historyRetentionMillis)
                assertEquals(expected, activity.state.value.historyRetentionMillis)
            }
            val stateField = FlasherService::class.java.getDeclaredField("state").apply { isAccessible = true }
            val before = file.readBytes()
            instrumentation.runOnMainSync {
                stateField.set(active, active.state.copy(busy = true))
                activity.perform(AppAction(AppActionKind.HISTORY_RETENTION, "7"))
                activity.perform(AppAction(AppActionKind.HISTORY_CLEAR))
                active.setHistoryRetention(7L * 86_400_000)
                active.clearHistory()
                stateField.set(active, active.state.copy(busy = false))
            }
            assertTrue(before.contentEquals(file.readBytes()))
            assertNull(active.historyRetentionMillis)
            instrumentation.runOnMainSync { activity.perform(AppAction(AppActionKind.HISTORY_CLEAR)) }
            waitUntil { !active.state.busy }
            assertEquals(1, FleetStore(file).jobs.size)
            assertTrue(FleetStore(file).jobs.all { it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) })
        } finally {
            instrumentation.runOnMainSync { if (previousFleet != null) fleetField.set(service, previousFleet); service?.setHistoryRetention(original) }
            waitUntil { service?.state?.busy != true }
            instrumentation.runOnMainSync { activity.finish() }
            context.unbindService(connection)
            file.delete()
            database.setting("offline", offline)
        }
    }
}
