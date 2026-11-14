package pl.ekim.fm1_flasher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.test.InstrumentationTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class UsbPermissionIntentTest : InstrumentationTestCase() {
    fun testImmutableCallbackArrivesWithoutUsbResultExtras() {
        val context = instrumentation.targetContext
        val action = "${context.packageName}.USB_PERMISSION_TEST"
        val received = CountDownLatch(1)
        var result: Intent? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                result = intent
                received.countDown()
            }
        }
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        val callback = PendingIntent.getBroadcast(context, 0,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            callback.send(context, 0, Intent().putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true))
            assertTrue("Permission callback must be delivered", received.await(5, TimeUnit.SECONDS))
            assertFalse("USB grants must be queried from UsbManager, not immutable callback extras",
                result!!.hasExtra(UsbManager.EXTRA_PERMISSION_GRANTED))
        } finally {
            callback.cancel()
            context.unregisterReceiver(receiver)
        }
    }
}
