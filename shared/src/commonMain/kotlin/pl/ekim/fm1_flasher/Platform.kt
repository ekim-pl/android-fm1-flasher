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
