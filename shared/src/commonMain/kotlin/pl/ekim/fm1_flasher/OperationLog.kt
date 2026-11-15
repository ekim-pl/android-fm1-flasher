package pl.ekim.fm1_flasher

import kotlinx.serialization.json.*

/** Call on one serialized I/O executor. Diagnostics must never interrupt a firmware transfer. */
class OperationLog(private val storage: AppStorage, private val clock: () -> Long) {
    companion object { const val LIMIT = 256 * 1024; const val PATH = "diagnostics/operations.jsonl" }
    private var lines: List<String>? = null
    private fun loaded(): List<String> = lines ?: storage.read(PATH, LIMIT)?.decodeToString()?.lineSequence()
        ?.filter { it.isNotBlank() }?.toList().orEmpty().also { lines = it }
    fun append(level: String, event: String, message: String = "") {
        val clean = message.replace(Regex("(https?://[^\\s?#]+)[?#][^\\s]*"), "$1[redacted]").take(2048)
        val line = buildJsonObject { put("timeEpochMs", clock()); put("level", level); put("event", event.take(80)); put("message", clean) }.toString()
        var next = loaded() + line
        var bytes = next.sumOf { it.encodeToByteArray().size + 1 }
        var first = 0
        while (bytes > LIMIT && first < next.size) { bytes -= next[first].encodeToByteArray().size + 1; first++ }
        next = next.drop(first)
        storage.write(PATH, (next.joinToString("\n") + "\n").encodeToByteArray())
        lines = next
    }
    fun export(): String = loaded().joinToString("\n", postfix = if (loaded().isEmpty()) "" else "\n")
    fun clear() { storage.remove(PATH); lines = emptyList() }
}
