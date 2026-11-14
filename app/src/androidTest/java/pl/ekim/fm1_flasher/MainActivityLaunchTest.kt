package pl.ekim.fm1_flasher

import android.content.Intent
import android.test.InstrumentationTestCase

@Suppress("DEPRECATION")
class MainActivityLaunchTest : InstrumentationTestCase() {
    fun testColdLaunchCreatesWindow() {
        val database = CatalogDatabase.get(instrumentation.targetContext)
        database.migrate(instrumentation.targetContext.filesDir)
        val preferences = database.settings()
        database.saveSettings(preferences.copy(offline = true))
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotNull(activity.window.decorView)
                assertNotNull(activity.window.insetsController)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            database.saveSettings(preferences)
        }
    }
}
