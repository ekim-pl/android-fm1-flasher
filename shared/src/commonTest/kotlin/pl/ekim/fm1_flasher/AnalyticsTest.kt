package pl.ekim.fm1_flasher

import kotlin.test.*

class AnalyticsTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        var failWrites = false
        override fun read(path: String, limit: Int) = files[path]?.also { require(it.size <= limit) }
        override fun write(path: String, bytes: ByteArray) { check(!failWrites); files[path] = bytes }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = emptyList<String>()
    }
    private class Sink : AnalyticsSink {
        override val available = true
        val configurations = mutableListOf<Pair<Boolean, Boolean>>()
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        var broken = false
        override fun configure(enabled: Boolean, reset: Boolean) { configurations += enabled to reset; if (broken) error("SDK failed") }
        override fun record(name: String, parameters: Map<String, String>) { if (broken) error("SDK failed"); events += name to parameters }
    }
    @Test fun firstOnlineLaunchEnablesByDefaultWithoutRecordingConsent() {
        val sink = Sink(); val storage = Storage(); val controller = AnalyticsController(sink, storage)
        controller.screen(AnalyticsScreen.CATALOG)
        controller.begin(AnalyticsOperation.DOWNLOAD).complete()
        assertTrue(sink.events.isEmpty()); assertTrue(sink.configurations.isEmpty())
        assertEquals(AnalyticsPreference.DEFAULT, controller.preference)
        controller.setOffline(false); controller.screen(AnalyticsScreen.CATALOG)
        assertEquals(listOf(true to false), sink.configurations)
        assertEquals("screen_view", sink.events.single().first)
        assertTrue(storage.files.isEmpty()) // A default is not evidence of user consent.
    }
    @Test fun sdkFreeBuildNeverEnablesStatistics() {
        val controller = AnalyticsController(NoAnalytics, Storage())
        controller.setOffline(false); controller.screen(AnalyticsScreen.CATALOG)
        controller.begin(AnalyticsOperation.FLASH).complete()
        assertFalse(controller.available)
    }
    @Test fun disableStopsEventsAndSurvivesRestartWithoutInitializingSdk() {
        val storage = Storage(); val sink = Sink(); val controller = AnalyticsController(sink, storage)
        controller.setOffline(false); controller.screen(AnalyticsScreen.CATALOG)
        val pending = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.setPreference(AnalyticsPreference.DISABLED)
        pending.complete(); controller.screen(AnalyticsScreen.ABOUT)
        assertEquals(1, sink.events.size)
        assertEquals(false to true, sink.configurations.last())
        val afterRestart = Sink(); val restored = AnalyticsController(afterRestart, storage)
        restored.setOffline(false); restored.screen(AnalyticsScreen.CATALOG)
        assertEquals(AnalyticsPreference.DISABLED, restored.preference)
        assertTrue(afterRestart.configurations.isEmpty()); assertTrue(afterRestart.events.isEmpty())
    }
    @Test fun explicitEnablePersistsAndOverridesLegacyOptOut() {
        val storage = Storage()
        storage.files["analytics-consent.json"] = """{"consent":"DENIED","version":1,"decidedAt":1}""".encodeToByteArray()
        val sink = Sink(); val controller = AnalyticsController(sink, storage)
        controller.setOffline(false); assertTrue(sink.configurations.isEmpty())
        controller.setPreference(AnalyticsPreference.ENABLED)
        assertEquals(true to false, sink.configurations.single())
        val afterRestart = Sink(); val restored = AnalyticsController(afterRestart, storage); restored.setOffline(false)
        assertEquals(AnalyticsPreference.ENABLED, restored.preference)
        assertEquals(true to false, afterRestart.configurations.single())
        val saved = storage.files.getValue("analytics-preference.json").decodeToString()
        assertTrue(saved.contains("ENABLED")); assertFalse(saved.contains("consent"))
    }
    @Test fun legacyAndRevisedNoticeOptOutsAreNeverSilentlyEnabled() {
        for (version in listOf(0, 1, AnalyticsController.NOTICE_VERSION, 999)) {
            for ((path, json) in listOf(
                "analytics-consent.json" to """{"consent":"DENIED","version":$version,"decidedAt":1}""",
                "analytics-preference.json" to """{"preference":"DISABLED","version":$version,"decidedAt":1}""")) {
                val storage = Storage(); storage.files[path] = json.encodeToByteArray()
                val sink = Sink(); val controller = AnalyticsController(sink, storage); controller.setOffline(false)
                controller.screen(AnalyticsScreen.SETTINGS)
                assertEquals(AnalyticsPreference.DISABLED, controller.preference)
                assertTrue(sink.configurations.isEmpty()); assertTrue(sink.events.isEmpty())
            }
        }
    }
    @Test fun offlineStartupAndRestartStayPausedUntilOnline() {
        val storage = Storage(); val sink = Sink(); val controller = AnalyticsController(sink, storage)
        controller.setOffline(true); controller.screen(AnalyticsScreen.ABOUT)
        assertTrue(sink.configurations.isEmpty()); assertTrue(sink.events.isEmpty())
        val restored = AnalyticsController(sink, storage); restored.setOffline(true)
        assertTrue(sink.configurations.isEmpty())
        restored.setOffline(false); assertEquals(true to false, sink.configurations.single())
        restored.setOffline(true); assertEquals(false to true, sink.configurations.last())
        restored.screen(AnalyticsScreen.ABOUT); assertTrue(sink.events.isEmpty())
    }
    @Test fun enablingNeverReplaysPausedOperations() {
        val sink = Sink(); val controller = AnalyticsController(sink, Storage())
        val beforeSettings = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.setOffline(false); beforeSettings.complete()
        val beforeOffline = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.setOffline(true); controller.setOffline(false); beforeOffline.complete()
        val beforeDisable = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.setPreference(AnalyticsPreference.DISABLED)
        val whileDisabled = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.setPreference(AnalyticsPreference.ENABLED)
        beforeDisable.complete(); whileDisabled.complete()
        assertTrue(sink.events.isEmpty())
    }
    @Test fun screensAndCompletionsAreDeduplicatedAndPayloadIsClosed() {
        val sink = Sink(); val controller = AnalyticsController(sink, Storage())
        controller.setOffline(false)
        repeat(3) { controller.screen(AnalyticsScreen.CATALOG) }
        assertEquals(1, sink.events.size)
        val operation = controller.begin(AnalyticsOperation.FLASH)
        operation.complete(false, AnalyticsError.DISCONNECTED, AnalyticsStage.WRITING); operation.complete()
        assertEquals(listOf("screen_view", "flash_started", "flash_completed"), sink.events.map { it.first })
        val payload = sink.events.last().second
        assertEquals(setOf("result", "error_category", "stage", "initiator", "duration_ms", "schema_version"), payload.keys)
        assertEquals("disconnected", payload["error_category"]); assertEquals("writing", payload["stage"])
        assertTrue(payload.getValue("duration_ms").toLong() >= 0)
        assertEquals(AnalyticsError.OTHER, analyticsError(IllegalStateException("private.example?token=SECRET")))
    }
    @Test fun failedPreferenceWritesDoNotReenableAndSdkFailuresDoNotInterruptOperations() {
        val storage = Storage(); val sink = Sink(); val controller = AnalyticsController(sink, storage)
        controller.setOffline(false); controller.setPreference(AnalyticsPreference.DISABLED)
        sink.configurations.clear(); storage.failWrites = true
        assertFails { controller.setPreference(AnalyticsPreference.ENABLED) }
        assertFalse(sink.configurations.any { it.first })
        storage.failWrites = false; sink.broken = true
        controller.setPreference(AnalyticsPreference.ENABLED)
        assertEquals(42, controller.measure(AnalyticsOperation.FLASH) { 42 })
        assertTrue(sink.events.isEmpty())
        sink.broken = false; controller.setOffline(true); controller.setOffline(false)
        storage.failWrites = true
        assertFails { controller.setPreference(AnalyticsPreference.DISABLED) }
        assertEquals(AnalyticsPreference.DISABLED, controller.preference)
        controller.screen(AnalyticsScreen.ABOUT); assertTrue(sink.events.isEmpty())
    }
    @Test fun cancellationUsesFixedCategoryAndPropagates() {
        val sink = Sink(); val controller = AnalyticsController(sink, Storage())
        controller.setOffline(false)
        assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
            controller.measure(AnalyticsOperation.DOWNLOAD) { throw kotlin.coroutines.cancellation.CancellationException("private detail") }
        }
        assertEquals("cancelled", sink.events.single().second["result"])
        assertEquals("cancelled", sink.events.single().second["error_category"])
    }
    @Test fun damagedPreferencesFailClosedInsteadOfOverridingPossibleOptOut() {
        for ((path, text) in listOf(
            "analytics-preference.json" to "not JSON",
            "analytics-preference.json" to """{"preference":"invalid","version":2,"decidedAt":1}""",
            "analytics-consent.json" to "not JSON")) {
            val storage = Storage(); storage.files[path] = text.encodeToByteArray()
            val sink = Sink(); val controller = AnalyticsController(sink, storage); controller.setOffline(false)
            assertEquals(AnalyticsPreference.DISABLED, controller.preference)
            assertTrue(sink.configurations.isEmpty())
        }
    }
}
