package pl.ekim.fm1_flasher

import android.content.Intent
import android.test.InstrumentationTestCase

@Suppress("DEPRECATION")
class MainActivityLaunchTest : InstrumentationTestCase() {
    fun testLauncherUsesFullComposeApplication() {
        val context = instrumentation.targetContext
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertEquals(ComposeActivity::class.java.name, launch?.component?.className)
    }
    fun testColdLaunchCreatesWindow() {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ComposeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotNull(activity.window.decorView)
                assertNotNull(activity.window.insetsController)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
