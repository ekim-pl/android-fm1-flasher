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
    @Test fun disabledUntilExplicitConsentAndOnlineSettingsLoaded() {
        val sink = Sink(); val controller = AnalyticsController(sink, Storage())
        controller.screen(AnalyticsScreen.CATALOG)
        controller.begin(AnalyticsOperation.DOWNLOAD).complete()
        controller.decide(AnalyticsConsent.GRANTED)
        assertTrue(sink.events.isEmpty()); assertTrue(sink.configurations.isEmpty())
        controller.setOffline(false); controller.screen(AnalyticsScreen.CATALOG)
        assertEquals(listOf(true to false), sink.configurations)
        assertEquals("screen_view", sink.events.single().first)
    }
    @Test fun refusalAndRestartNeverEnableSdk() {
        val storage = Storage(); val sink = Sink(); val controller = AnalyticsController(sink, storage)
        controller.setOffline(false); controller.decide(AnalyticsConsent.DENIED)
        val restored = AnalyticsController(sink, storage); restored.setOffline(false)
        assertEquals(AnalyticsConsent.DENIED, restored.consent)
        assertFalse(sink.configurations.any { it.first })
    }
    @Test fun consentPersistsButOfflineRestartStaysDisabled() {
        val storage = Storage(); val controller = AnalyticsController(Sink(), storage)
        controller.setOffline(false); controller.decide(AnalyticsConsent.GRANTED)
        val sink = Sink(); val restored = AnalyticsController(sink, storage)
        restored.setOffline(true); restored.screen(AnalyticsScreen.ABOUT)
        assertEquals(AnalyticsConsent.GRANTED, restored.consent); assertTrue(sink.events.isEmpty()); assertTrue(sink.configurations.isEmpty())
        restored.setOffline(false); assertTrue(sink.configurations.single().first)
    }
    @Test fun offlineAndWithdrawalInvalidateInFlightOperationsWithoutReplay() {
        val sink = Sink(); val controller = AnalyticsController(sink, Storage())
        controller.setOffline(false)
        val beforeConsent = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.decide(AnalyticsConsent.GRANTED); beforeConsent.complete()
        val beforeOffline = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.setOffline(true); controller.setOffline(false); beforeOffline.complete()
        val beforeWithdrawal = controller.begin(AnalyticsOperation.DOWNLOAD)
        controller.decide(AnalyticsConsent.DENIED); controller.decide(AnalyticsConsent.GRANTED); beforeWithdrawal.complete()
        assertTrue(sink.events.isEmpty())
        assertTrue(sink.configurations.contains(false to true))
    }
    @Test fun screensAndCompletionsAreDeduplicatedAndPayloadIsClosed() {
        val sink = Sink(); val controller = AnalyticsController(sink, Storage())
        controller.setOffline(false); controller.decide(AnalyticsConsent.GRANTED)
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
    @Test fun persistenceAndSdkFailuresNeverInterruptOperationsOrEnableCollection() {
        val storage = Storage().apply { failWrites = true }; val sink = Sink()
        val controller = AnalyticsController(sink, storage); controller.setOffline(false)
        assertFails { controller.decide(AnalyticsConsent.GRANTED) }
        assertFalse(sink.configurations.any { it.first })
        storage.failWrites = false; sink.broken = true
        controller.decide(AnalyticsConsent.GRANTED)
        assertEquals(42, controller.measure(AnalyticsOperation.FLASH) { 42 })
        assertTrue(sink.events.isEmpty())
        sink.broken = false; controller.setOffline(true); controller.setOffline(false)
        storage.failWrites = true
        assertFails { controller.decide(AnalyticsConsent.DENIED) }
        assertEquals(AnalyticsConsent.DENIED, controller.consent)
        controller.screen(AnalyticsScreen.ABOUT); assertTrue(sink.events.isEmpty())
    }
    @Test fun cancellationUsesFixedCategoryAndPropagates() {
        val sink = Sink(); val controller = AnalyticsController(sink, Storage())
        controller.setOffline(false); controller.decide(AnalyticsConsent.GRANTED)
        assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
            controller.measure(AnalyticsOperation.DOWNLOAD) { throw kotlin.coroutines.cancellation.CancellationException("private detail") }
        }
        assertEquals("cancelled", sink.events.single().second["result"])
        assertEquals("cancelled", sink.events.single().second["error_category"])
    }
    @Test fun invalidOrObsoleteConsentFailsClosed() {
        val storage = Storage()
        for (value in listOf("not JSON", """{"consent":"GRANTED","version":0,"decidedAt":1}""")) {
            storage.files["analytics-consent.json"] = value.encodeToByteArray()
            val sink = Sink(); val controller = AnalyticsController(sink, storage); controller.setOffline(false)
            assertEquals(AnalyticsConsent.UNKNOWN, controller.consent); assertTrue(sink.configurations.isEmpty())
        }
    }
}
