package pl.ekim.fm1_flasher

import android.content.*
import android.net.Uri
import android.os.IBinder
import android.test.InstrumentationTestCase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class OperationLogServiceTest : InstrumentationTestCase() {
    fun testPersistentLogExportAndClearRequireExperimentalMode() {
        val context = instrumentation.targetContext
        val connected = CountDownLatch(1)
        var service: FlasherService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { service = (binder as FlasherService.LocalBinder).service; connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val output = File(context.cacheDir, "operation-log-test.jsonl")
        assertTrue(context.bindService(Intent(context, FlasherService::class.java), connection, Context.BIND_AUTO_CREATE))
        var previous = false
        fun waitUntil(check: () -> Boolean) {
            repeat(100) { if (check()) return; Thread.sleep(50) }
            assertTrue("Timed out waiting for diagnostic operation", check())
        }
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            waitUntil { !service!!.state.busy }
            val active = service!!; previous = active.state.experimental
            instrumentation.runOnMainSync { active.setExperimental(true); active.recordEvent("PERSISTENCE_TEST", "USB ready") }
            fun logText() = File(context.filesDir, "diagnostics").listFiles().orEmpty()
                .filter { it.name.startsWith("operations-") && it.extension == "jsonl" }.joinToString("\n") { it.readText() }
            waitUntil { logText().contains("PERSISTENCE_TEST") }
            instrumentation.runOnMainSync { active.setExperimental(false); active.exportOperationLog(Uri.fromFile(output)); active.clearOperationLog() }
            Thread.sleep(150)
            assertFalse(output.exists()); assertTrue(logText().contains("PERSISTENCE_TEST"))
            instrumentation.runOnMainSync { active.setExperimental(true); active.exportOperationLog(Uri.fromFile(output)) }
            waitUntil { output.exists() && output.readText().contains("PERSISTENCE_TEST") && (active.state.status as? AppText.Resource)?.key == "operation_log_saved" }
            instrumentation.runOnMainSync { active.clearOperationLog() }
            waitUntil { (active.state.status as? AppText.Resource)?.key == "operation_log_cleared" }
            assertFalse(logText().contains("PERSISTENCE_TEST"))
            assertTrue(output.readText().contains("PERSISTENCE_TEST"))
            active.recordEvent("AFTER_CLEAR")
            waitUntil { logText().contains("AFTER_CLEAR") }
            assertFalse(logText().contains("PERSISTENCE_TEST"))
        } finally {
            instrumentation.runOnMainSync { service?.setExperimental(previous) }
            context.unbindService(connection); output.delete()
        }
    }
    fun testDocumentResultWaitsForForegroundRefreshBeforeExporting() {
        val context = instrumentation.targetContext
        val database = CatalogDatabase.get(context)
        val previousOffline = database.setting("offline")
        // Exercise deferred export without depending on remote catalog response time.
        database.setting("offline", "true")
        var service: FlasherService? = null
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { service = (binder as FlasherService.LocalBinder).service; connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val activity = instrumentation.startActivitySync(Intent(context, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        assertTrue(context.bindService(Intent(context, FlasherService::class.java), connection, Context.BIND_AUTO_CREATE))
        val output = File(context.cacheDir, "pending-operation-log.jsonl").apply { delete() }
        var previous = false
        fun waitUntil(check: () -> Boolean) {
            repeat(100) { if (check()) return; Thread.sleep(50) }
            assertTrue("Pending export did not finish", check())
        }
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val active = service!!
            val activityService = ComposeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }
            var started = false
            // The Activity binds independently and can start another foreground refresh.
            // Check readiness and deliver the result in one main-thread turn.
            waitUntil {
                instrumentation.runOnMainSync {
                    if (!started && activityService.get(activity) === active && !active.state.busy) {
                        previous = active.state.experimental
                        active.setExperimental(true)
                        assertTrue(active.state.experimental)
                        active.recordEvent("DOCUMENT_RESULT_TEST")
                        active.onAppOpened()
                        assertTrue(active.state.busy)
                        val callback = ComposeActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
                        callback.isAccessible = true
                        callback.invoke(activity, 62, android.app.Activity.RESULT_OK, Intent().setData(Uri.fromFile(output)))
                        started = true
                    }
                }
                started
            }
            waitUntil { output.exists() && output.readText().contains("DOCUMENT_RESULT_TEST") }
        } finally {
            instrumentation.runOnMainSync { service?.setExperimental(previous); activity.finish() }
            context.unbindService(connection); output.delete()
            database.setting("offline", previousOffline)
        }
    }

}
