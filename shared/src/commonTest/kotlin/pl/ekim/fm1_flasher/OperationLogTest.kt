package pl.ekim.fm1_flasher

import kotlin.test.*
import kotlinx.serialization.json.*

class OperationLogTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        var failWrites = false
        override fun read(path: String, limit: Int) = files[path]?.also { require(it.size <= limit) }
        override fun write(path: String, bytes: ByteArray) { check(!failWrites); files[path] = bytes }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = emptyList<String>()
    }
    @Test fun survivesRestartEscapesMessagesAndRedactsUrlQuery() {
        val storage = Storage(); val log = OperationLog(storage) { 42 }
        log.append("ERROR", "USB", "Line\nhttps://example.com/a?token=secret#fragment")
        val exported = OperationLog(storage) { 43 }.export()
        val row = Json.parseToJsonElement(exported.trim()).jsonObject
        assertEquals("42", row["timeEpochMs"]!!.jsonPrimitive.content)
        assertEquals("ERROR", row["level"]!!.jsonPrimitive.content)
        assertFalse(exported.contains("secret")); assertEquals(1, exported.lines().filter(String::isNotBlank).size)
    }
    @Test fun boundsUtf8BytesAndRetainsNewestCompleteEvents() {
        val storage = Storage(); val log = OperationLog(storage) { 1 }
        repeat(160) { log.append("INFO", "EVENT_$it", "汉".repeat(2048)) }
        assertTrue(storage.files.getValue(OperationLog.PATH).size <= OperationLog.LIMIT)
        val events = log.export().lineSequence().filter(String::isNotBlank).map { Json.parseToJsonElement(it).jsonObject["event"]!!.jsonPrimitive.content }.toList()
        assertEquals("EVENT_159", events.last()); assertFalse("EVENT_0" in events)
    }
    @Test fun clearSurvivesRestartAndLoggingResumes() {
        val storage = Storage(); val log = OperationLog(storage) { 1 }
        log.append("INFO", "BEFORE"); log.clear()
        assertEquals("", OperationLog(storage) { 2 }.export())
        log.append("INFO", "AFTER")
        assertFalse(log.export().contains("BEFORE")); assertTrue(log.export().contains("AFTER"))
    }
    @Test fun failedWriteDoesNotReportUnpersistedEntry() {
        val storage = Storage(); val log = OperationLog(storage) { 1 }
        log.append("INFO", "SAVED"); storage.failWrites = true
        assertFails { log.append("INFO", "FAILED") }
        assertFalse(log.export().contains("FAILED"))
    }
}
