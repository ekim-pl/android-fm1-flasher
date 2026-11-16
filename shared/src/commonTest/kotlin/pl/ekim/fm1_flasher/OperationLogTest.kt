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
        override fun list(path: String) = files.keys.filter { it.startsWith("$path/") }.map { it.removePrefix("$path/") }
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
        assertTrue(storage.files.filterKeys { it.endsWith(".jsonl") }.values.sumOf { it.size } <= OperationLog.LIMIT)
        val events = log.export().lineSequence().filter(String::isNotBlank).map { Json.parseToJsonElement(it).jsonObject["event"]!!.jsonPrimitive.content }.toList()
        assertEquals("EVENT_159", events.last()); assertFalse("EVENT_0" in events)
    }
    @Test fun redactsNativeOAuthCallbacksAndAuthenticationFields() {
        val storage = Storage(); val log = OperationLog(storage) { 1 }
        log.append("ERROR", "AUTH", "pl.ekim.fm1-flasher://oauth/callback?code=private-code&state=private-state")
        log.append("ERROR", "AUTH", "Authorization: Bearer private-token")
        log.append("ERROR", "AUTH", "{\"client_secret\":\"private-secret\",\"access_token\":\"private-access\"}")
        val text = log.export()
        for (value in listOf("private-code", "private-state", "private-token", "private-secret", "private-access")) assertFalse(text.contains(value))
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

class DailyOperationLogTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        var failWrite = false
        var failRemove = false
        override fun read(path: String, limit: Int) = files[path]?.also { require(it.size <= limit) }
        override fun write(path: String, bytes: ByteArray) { check(!failWrite); files[path] = bytes }
        override fun remove(path: String) { check(!failRemove); files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = files.keys.filter { it.startsWith("$path/") }.map { it.removePrefix("$path/") }
    }
    private fun log(storage: Storage, now: () -> Long) = OperationLog(storage, { it / 86400000 }, now)
    @Test fun rotatesAtMidnightAndPrunesByCalendarDayAcrossRestart() {
        val s = Storage(); var now = 86400000L - 1
        val log = log(s) { now }; log.append("INFO", "DAY_ZERO")
        now++; log.append("INFO", "DAY_ONE")
        assertEquals(2, s.files.size)
        now = 7 * 86400000L
        val restarted = log(s) { now }
        assertFalse(restarted.export().contains("DAY_ZERO"))
        assertTrue(restarted.export().contains("DAY_ONE"))
        now = 8 * 86400000L
        assertEquals("", restarted.export())
    }
    @Test fun retentionIsPersistentAndClearRemovesAllDaysButNotSettingsOrOtherFiles() {
        val s = Storage(); var now = 0L; val log = log(s) { now }
        log.setRetentionDays(30); log.append("INFO", "FIRST")
        now = 20 * 86400000L; log.append("INFO", "SECOND")
        s.files["flash-session.json"] = "unconfirmed".encodeToByteArray()
        s.files["diagnostics/unrelated.txt"] = "keep".encodeToByteArray()
        assertTrue(log(s) { now }.export().contains("FIRST"))
        assertEquals(30, log(s) { now }.retentionDays())
        log.setRetentionDays(7)
        assertFalse(log.export().contains("FIRST"))
        log.clear(); assertEquals("", log(s) { now }.export())
        assertEquals(7, log.retentionDays())
        assertEquals("unconfirmed", s.files.getValue("flash-session.json").decodeToString())
        assertTrue(s.files.containsKey("diagnostics/unrelated.txt"))
        log.append("INFO", "AFTER_CLEAR"); assertTrue(log.export().contains("AFTER_CLEAR"))
    }
    @Test fun legacyMigrationKeepsDatesAndRetryDoesNotDuplicate() {
        val s = Storage(); val now = 8 * 86400000L
        fun row(day: Int) = "{\"timeEpochMs\":${day * 86400000L},\"event\":\"D$day\"}"
        s.files[OperationLog.PATH] = (row(1) + "\n" + row(2) + "\n" + row(8) + "\n").encodeToByteArray()
        val log = log(s) { now }; s.failRemove = true
        assertFails { log.export() }; assertTrue(s.files.containsKey(OperationLog.PATH))
        s.failRemove = false
        val rows = log(s) { now }.export().lines().filter(String::isNotBlank)
        assertEquals(2, rows.size); assertTrue(rows.any { it.contains("D2") })
        assertFalse(s.files.containsKey(OperationLog.PATH))
    }
    @Test fun totalLimitAlsoBoundsFutureFilesAfterClockRollback() {
        val s = Storage()
        for (day in 0..45) s.files["diagnostics/operations-$day.jsonl"] = ByteArray(OperationLog.LIMIT) { 32 }
        log(s) { 0 }.maintain()
        assertTrue(s.files.values.sumOf { it.size } <= OperationLog.TOTAL_LIMIT)
        assertFalse(s.files.containsKey("diagnostics/operations-0.jsonl"))
        assertTrue(s.files.containsKey("diagnostics/operations-45.jsonl"))
    }
    @Test fun injectedLocalDayHandlesTimeZoneChangeWithoutOverwritingOtherDay() {
        val s = Storage(); var day = 10L
        val log = OperationLog(s, { day }, { 1L })
        log.append("INFO", "BEFORE"); day--; log.append("INFO", "AFTER")
        assertEquals(2, s.files.size)
        assertTrue(log.export().contains("BEFORE")); assertTrue(log.export().contains("AFTER"))
    }
    @Test fun invalidOrFailedPreferenceDoesNotApplyNewRetention() {
        val s = Storage(); val log = log(s) { 0 }
        assertFails { log.setRetentionDays(0) }; assertEquals(7, log.retentionDays())
        s.failWrite = true
        assertFails { log.setRetentionDays(30) }; assertEquals(7, log.retentionDays())
    }
}
