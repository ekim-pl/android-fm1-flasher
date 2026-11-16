package pl.ekim.fm1_flasher

import kotlinx.serialization.json.*

/** One serialized I/O owner. Callers isolate diagnostic failures from device operations. */
class OperationLog(private val storage: AppStorage, private val dayFor: (Long) -> Long, private val clock: () -> Long) {
    constructor(storage: AppStorage, clock: () -> Long) : this(storage, ::localCalendarDay, clock)
    companion object {
        const val LIMIT = 256 * 1024 // Preserve the per-day bound on rewriting diagnostic data.
        const val TOTAL_LIMIT = 10 * 1024 * 1024
        const val PATH = "diagnostics/operations.jsonl" // Legacy single-file log.
        const val DEFAULT_DAYS = 7
        val RETENTION_DAYS = listOf(7, 14, 30)
        private const val SETTINGS = "diagnostics/retention.json"
    }
    private fun path(day: Long) = "diagnostics/operations-$day.jsonl"
    private fun files(): List<Pair<Long, String>> = storage.list("diagnostics").mapNotNull { name ->
        Regex("operations-(-?[0-9]+)\\.jsonl").matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()?.let { it to "diagnostics/$name" }
    }.sortedBy { it.first }
    fun retentionDays(): Int = runCatching {
        storage.read(SETTINGS, 128)?.decodeToString()?.toIntOrNull()?.takeIf { it in RETENTION_DAYS }
    }.getOrNull() ?: DEFAULT_DAYS
    fun setRetentionDays(days: Int) {
        require(days in RETENTION_DAYS)
        storage.write(SETTINGS, days.toString().encodeToByteArray())
        prune(dayFor(clock()))
    }
    private fun read(path: String): List<String> = storage.read(path, LIMIT)?.decodeToString()
        ?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty()
    private fun write(path: String, lines: List<String>) {
        var bytes = lines.sumOf { it.encodeToByteArray().size + 1 }
        var first = 0
        while (bytes > LIMIT && first < lines.size) { bytes -= lines[first].encodeToByteArray().size + 1; first++ }
        storage.write(path, lines.drop(first).joinToString("\n", postfix = "\n").encodeToByteArray())
    }
    private fun migrate(today: Long) {
        if (storage.size(PATH) == null) return
        val oldest = today - retentionDays() + 1
        val groups = read(PATH).mapNotNull { line ->
            runCatching { dayFor(Json.parseToJsonElement(line).jsonObject.getValue("timeEpochMs").jsonPrimitive.long) to line }.getOrNull()
        }.filter { it.first >= oldest }.groupBy({ it.first }, { it.second })
        groups.forEach { (day, lines) ->
            // Retry after a failed migration must not duplicate previously copied records.
            val existing = read(path(day))
            write(path(day), (lines + existing).distinct())
        }
        storage.remove(PATH)
    }
    private fun prune(today: Long) {
        val oldest = today - retentionDays() + 1
        files().filter { it.first < oldest }.forEach { storage.remove(it.second) }
        val remaining = files()
        var bytes = remaining.sumOf { storage.size(it.second) ?: 0 }
        for ((_, file) in remaining) {
            if (bytes <= TOTAL_LIMIT) break
            val size = storage.size(file) ?: 0
            storage.remove(file)
            bytes -= size
        }
    }
    fun maintain() {
        val today = dayFor(clock())
        migrate(today)
        prune(today)
    }
    fun append(level: String, event: String, message: String = "") {
        maintain()
        val timestamp = clock()
        val clean = message
            .replace(Regex("([A-Za-z][A-Za-z0-9+.-]*://[^\\s?#]+)[?#][^\\s]*"), "$1[redacted]")
            .replace(Regex("(?i)(authorization\\s*[:=]\\s*)[^\\r\\n]+"), "$1[redacted]")
            .replace(Regex("(?i)(\"?(?:access_token|refresh_token|client_secret|password)\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^\\s,}]+)"), "$1[redacted]")
            .take(2048)
        val line = buildJsonObject { put("timeEpochMs", timestamp); put("level", level); put("event", event.take(80)); put("message", clean) }.toString()
        val file = path(dayFor(timestamp))
        write(file, read(file) + line)
        prune(dayFor(timestamp))
    }
    fun export(): String {
        maintain()
        return files().flatMap { read(it.second) }.joinToString("\n").let { if (it.isEmpty()) it else "$it\n" }
    }
    fun clear() {
        files().forEach { storage.remove(it.second) }
        storage.remove(PATH)
    }
}
