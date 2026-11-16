package pl.ekim.fm1_flasher

internal expect fun platformSha256(bytes: ByteArray): String
internal expect fun platformPause(milliseconds: Long)
internal expect fun monotonicMillis(): Long
internal expect class PlatformLock() {
    fun <T> locked(block: () -> T): T
}

expect fun epochMillis(): Long

fun blockingPause(milliseconds: Long) = platformPause(milliseconds)

fun monotonicTimeMillis(): Long = monotonicMillis()

/** Calendar-day ordinal in the current local time zone, independent of DST day length. */
internal expect fun localCalendarDay(timestamp: Long): Long
