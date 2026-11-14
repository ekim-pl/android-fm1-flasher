package pl.ekim.fm1_flasher

import java.util.ArrayDeque
import java.util.Locale

/** One parser per port. Real-time messages never disturb running status or SysEx. */
class MidiStreamParser(private val emit: (ByteArray, String) -> Unit) {
    private var running = 0
    private var status = 0
    private var needed = 0
    private val data = ArrayList<Byte>()
    private var sysex = false
    private var truncated = false
    fun reset() { running = 0; status = 0; needed = 0; data.clear(); sysex = false; truncated = false }
    fun finish() {
        if (data.isNotEmpty()) emit(data.toByteArray(), "interrupted at port close")
        reset()
    }
    fun accept(bytes: ByteArray) {
        for (byte in bytes) {
            val v = byte.toInt() and 255
            if (v >= 0xF8) { emit(byteArrayOf(byte), if (v in listOf(0xF9, 0xFD)) "undefined" else ""); continue }
            if (sysex) {
                if (v == 0xF7) {
                    if (data.size < 65536) data += byte else truncated = true
                    emit(data.toByteArray(), if (truncated) "truncated" else "")
                    reset(); continue
                }
                if (v < 0x80) { if (data.size < 65535) data += byte else truncated = true; continue }
                emit(data.toByteArray(), "interrupted"); reset()
            }
            if (v >= 0x80) {
                if (status != 0 && data.isNotEmpty()) emit(data.toByteArray(), "interrupted")
                data.clear(); status = 0
                if (v >= 0xF0) running = 0 else running = v
                if (v == 0xF0) { sysex = true; data += byte; continue }
                val length = when {
                    v < 0xF0 -> if (v and 0xE0 == 0xC0) 1 else 2
                    v == 0xF1 || v == 0xF3 -> 1
                    v == 0xF2 -> 2
                    else -> 0
                }
                if (length == 0) emit(byteArrayOf(byte), if (v in listOf(0xF4, 0xF5, 0xF7)) "unexpected/undefined" else "")
                else { status = v; needed = length; data += byte }
            } else {
                if (status == 0) {
                    if (running == 0) { emit(byteArrayOf(byte), "orphan data"); continue }
                    status = running; needed = if (status and 0xE0 == 0xC0) 1 else 2
                    data.clear(); data += status.toByte()
                }
                data += byte
                if (data.size == needed + 1) { emit(data.toByteArray(), ""); data.clear(); status = 0 }
            }
        }
    }
}

data class MidiLogEntry(val sequence: Long, val elapsedNs: Long, val timestampNs: Long,
    val source: String, val bytes: ByteArray, val annotation: String = "") {
    val channel: Int? get() = bytes.firstOrNull()?.toInt()?.and(255)?.takeIf { it in 0x80..0xEF }?.let { (it and 15) + 1 }
    val type: String get() = when (val s = bytes.firstOrNull()?.toInt()?.and(255) ?: -1) {
        in 0x80..0x8F -> "Note Off"
        in 0x90..0x9F -> "Note On"
        in 0xA0..0xAF -> "Poly Pressure"
        in 0xB0..0xBF -> "CC"
        in 0xC0..0xCF -> "Program Change"
        in 0xD0..0xDF -> "Channel Pressure"
        in 0xE0..0xEF -> "Pitch Bend"
        0xF0 -> "SysEx"; 0xF1 -> "MTC"; 0xF2 -> "Song Position"; 0xF3 -> "Song Select"
        0xF6 -> "Tune Request"; 0xF8 -> "Clock"; 0xFA -> "Start"; 0xFB -> "Continue"
        0xFC -> "Stop"; 0xFE -> "Active Sensing"; 0xFF -> "Reset"
        else -> if (s < 0) "Event" else "Unknown"
    }
    fun line(full: Boolean = false): String {
        val values = if (type == "SysEx") emptyList() else bytes.drop(1).map { it.toInt() and 255 }
        val detail = when {
            type == "Pitch Bend" && values.size == 2 -> " ${values[0] + (values[1] shl 7) - 8192}"
            type in listOf("Note On", "Note Off") && values.size == 2 -> " note=${values[0]} velocity=${values[1]}" +
                if (type == "Note On" && values[1] == 0) " (Note Off)" else ""
            type != "SysEx" && values.isNotEmpty() -> " " + values.joinToString(" ")
            else -> ""
        }
        val hex = (if (full) bytes.asIterable() else bytes.take(64)).joinToString(" ") { "%02X".format(it.toInt() and 255) }
        return String.format(Locale.ROOT, "%.3f", elapsedNs / 1e9) + " · $source · $type" +
            (channel?.let { " ch=$it" } ?: "") + detail + "\n" + hex +
            (if (!full && bytes.size > 64) " … (${bytes.size} bytes)" else "") +
            (if (annotation.isNotEmpty()) " [$annotation]" else "")
    }
}

data class MidiLogSnapshot(val revision: Long, val started: String, val entries: List<MidiLogEntry>,
    val evicted: Long, val lostBytes: Long, val total: Long) {
    fun export() = buildString {
        append("FM1 experimental MIDI logger\nStarted: $started\nTime: monotonic receive seconds; timestamp_ns: Android MIDI (0 = unavailable)\n")
        append("Messages: $total; evicted: $evicted; lost input bytes: $lostBytes\nFilters do not affect export. Running status is expanded to full messages. SysEx may be truncated at 64 KiB.\n\n")
        entries.forEach { append("#${it.sequence} timestamp_ns=${it.timestampNs} "); append(it.line(true)); append('\n') }
    }
}

class MidiLog(private val maxEntries: Int = 5000, private val maxBytes: Int = 4 * 1024 * 1024) {
    private val entries = ArrayDeque<MidiLogEntry>()
    private var size = 0
    private var sequence = 0L
    private var revision = 0L
    private var evicted = 0L
    private var lost = 0L
    private var start = System.nanoTime()
    private var wall = java.time.Instant.now().toString()
    @Synchronized fun add(source: String, bytes: ByteArray, annotation: String = "", received: Long = System.nanoTime(), timestamp: Long = 0) {
        val entry = MidiLogEntry(++sequence, (received - start).coerceAtLeast(0), timestamp, source, bytes.copyOf(), annotation)
        entries.addLast(entry); size += bytes.size; revision++
        while (entries.size > maxEntries || size > maxBytes) { size -= entries.removeFirst().bytes.size; evicted++ }
    }
    @Synchronized fun loss(source: String, bytes: Long) { lost += bytes; add(source, byteArrayOf(), "lost $bytes input bytes; parser reset") }
    @Synchronized fun clear() { entries.clear(); size = 0; sequence = 0; evicted = 0; lost = 0; start = System.nanoTime(); wall = java.time.Instant.now().toString(); revision++ }
    @Synchronized fun snapshot() = MidiLogSnapshot(revision, wall, entries.toList(), evicted, lost, sequence)
}
