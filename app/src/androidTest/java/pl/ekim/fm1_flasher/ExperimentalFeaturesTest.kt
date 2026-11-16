package pl.ekim.fm1_flasher

import android.content.*
import android.os.IBinder
import android.test.InstrumentationTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class ExperimentalFeaturesTest : InstrumentationTestCase() {
    fun testOptInPersistsAndDisabledServiceRejectsExperimentalActions() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("features", Context.MODE_PRIVATE)
        val previous = preferences.all["experimental"] as? Boolean
        preferences.edit().remove("experimental").commit()
        val connected = CountDownLatch(1)
        var service: FlasherService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as FlasherService.LocalBinder).service; connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent(context, FlasherService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val ready = CountDownLatch(1)
            val observer: (UiState) -> Unit = { if (!it.busy) ready.countDown() }
            instrumentation.runOnMainSync { service!!.observe(observer) }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val active = service!!
                active.unobserve(observer)
                assertFalse(active.state.experimental)
                active.toggleMonitor()
                active.enterDownloadMode()
                active.onStartCommand(Intent().setAction("RECOVERY_WRITE"), 0, 1)
                for (action in listOf("TOOLS_READ", "TOOLS_PREPARE", "TOOLS_WRITE", "TOOLS_MIDI_INFO", "TOOLS_MIDI_BACKUP")) {
                    active.onStartCommand(Intent().setAction(action), 0, 1)
                    assertNull(active.state.tools)
                    assertNull(active.state.diagnostics)
                }
                assertFalse(active.monitor.running)
                assertFalse(active.state.flashing)
                assertFalse(active.state.busy)
                active.setExperimental(true)
                assertTrue(active.state.experimental)
                assertTrue(preferences.getBoolean("experimental", false))
                // Fabricated write intents cannot bypass hardware, export or session gates.
                active.onStartCommand(Intent().setAction("TOOLS_WRITE").putExtra("token", "forged"), 0, 1)
                assertFalse(active.state.flashing)
                assertFalse(active.state.busy)
                active.setExperimental(false)
                assertFalse(active.state.experimental)
                assertNull(active.state.recoveryPlan)
                assertFalse(preferences.getBoolean("experimental", true))
            }
        } finally {
            context.unbindService(connection)
            preferences.edit().apply {
                if (previous == null) remove("experimental") else putBoolean("experimental", previous)
            }.commit()
        }
    }
}
