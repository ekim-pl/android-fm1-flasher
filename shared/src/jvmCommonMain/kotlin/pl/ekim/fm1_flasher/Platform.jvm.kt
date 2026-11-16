package pl.ekim.fm1_flasher

internal actual fun platformSha256(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
internal actual fun platformPause(milliseconds: Long) = Thread.sleep(milliseconds)
internal actual fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
internal actual class PlatformLock actual constructor() {
    actual fun <T> locked(block: () -> T): T = synchronized(this, block)
}

actual fun epochMillis(): Long = System.currentTimeMillis()

internal actual fun localCalendarDay(timestamp: Long): Long =
    java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
