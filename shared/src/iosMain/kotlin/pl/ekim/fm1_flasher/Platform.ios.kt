@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package pl.ekim.fm1_flasher

import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSThread
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

internal actual fun platformSha256(bytes: ByteArray): String {
    val result = UByteArray(32)
    result.usePinned { output ->
        if (bytes.isEmpty()) CC_SHA256(null, 0u, output.addressOf(0))
        else bytes.usePinned { input -> CC_SHA256(input.addressOf(0), bytes.size.toUInt(), output.addressOf(0)) }
    }
    return result.joinToString("") { it.toString(16).padStart(2, '0') }
}
internal actual fun platformPause(milliseconds: Long) = NSThread.sleepForTimeInterval(milliseconds / 1000.0)
internal actual fun monotonicMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
internal actual class PlatformLock actual constructor() {
    private val lock = NSRecursiveLock()
    actual fun <T> locked(block: () -> T): T {
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }
}

actual fun epochMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

internal actual fun localCalendarDay(timestamp: Long): Long {
    val date = platform.Foundation.NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0)
    val offset = platform.Foundation.NSTimeZone.localTimeZone.secondsFromGMTForDate(date)
    return kotlin.math.floor((timestamp / 1000.0 + offset) / 86400.0).toLong()
}
