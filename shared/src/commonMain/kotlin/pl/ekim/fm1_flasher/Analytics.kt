package pl.ekim.fm1_flasher

import kotlinx.serialization.json.*

/** Platform SDK boundary. Implementations must start disabled; no application data crosses this API. */
interface AnalyticsSink {
    val available: Boolean
    fun configure(enabled: Boolean, reset: Boolean)
    fun record(name: String, parameters: Map<String, String>)
}
object NoAnalytics : AnalyticsSink {
    override val available = false
    override fun configure(enabled: Boolean, reset: Boolean) {}
    override fun record(name: String, parameters: Map<String, String>) {}
}
enum class AnalyticsPreference { DEFAULT, DISABLED, ENABLED }
enum class AnalyticsOperation(val event: String) {
    CATALOG("catalog_refresh_completed"), DOWNLOAD("firmware_download_completed"),
    CONNECT("device_connection_completed"), FLASH("flash_completed"), RECOVERY("recovery_completed")
}
enum class AnalyticsScreen { CATALOG, DOWNLOADS, DEVICES, TASKS, FLASHER, LOGGER, ABOUT, SETTINGS, SOURCES, PRIVACY, DETAIL, RECOVERY }
enum class AnalyticsStage { PREPARING, RECONNECTING, WRITING, VERIFYING, FINISHED }
enum class AnalyticsInitiator { MANUAL, AUTOMATIC, BACKGROUND }
enum class AnalyticsError { NONE, TIMEOUT, DISCONNECTED, INVALID_PACKAGE, CANCELLED, OTHER }
fun analyticsError(failure: Throwable): AnalyticsError {
    if (failure is kotlin.coroutines.cancellation.CancellationException) return AnalyticsError.CANCELLED
    val key = ((failure as? LocalizedFailure)?.text as? AppText.Resource)?.key
    return when (key) {
        "reconnect_timeout", "identity_timeout", "flash_timeout" -> AnalyticsError.TIMEOUT
        "usb_port_gone", "fleet_continuity_lost", "reconnect_permission" -> AnalyticsError.DISCONNECTED
        "local_package_changed", "local_chip_unsupported", "download_stale" -> AnalyticsError.INVALID_PACKAGE
        else -> AnalyticsError.OTHER
    }
}
fun analyticsStage(text: AppText): AnalyticsStage? = when ((text as? AppText.Resource)?.key) {
    "flash_stage_write" -> AnalyticsStage.WRITING
    "flash_stage_verify" -> AnalyticsStage.VERIFYING
    "flash_stage_reconnect", "ios_reconnect" -> AnalyticsStage.RECONNECTING
    else -> null
}
private data class AnalyticsDecision(val preference: AnalyticsPreference, val version: Int, val decidedAt: Long)

/** Opt-out preference; disabled events and operations are discarded, never replayed. */
class AnalyticsController(private val sink: AnalyticsSink, private val storage: AppStorage) {
    private val lock = PlatformLock()
    private var decision = readDecision()
    private fun readDecision(): AnalyticsDecision? = try {
        // Keep an explicit opt-out from the earlier consent-based release proposal.
        val bytes = storage.read("analytics-preference.json", 4096)
            ?: storage.read("analytics-consent.json", 4096)
        bytes?.let {
            val value = Json.parseToJsonElement(it.decodeToString()).jsonObject
            val preference = value["preference"]?.jsonPrimitive?.content?.let(AnalyticsPreference::valueOf)
                ?: when (value.getValue("consent").jsonPrimitive.content) {
                    "DENIED" -> AnalyticsPreference.DISABLED
                    "GRANTED" -> AnalyticsPreference.ENABLED
                    "UNKNOWN" -> AnalyticsPreference.DEFAULT
                    else -> error("Invalid analytics preference")
                }
            // A notice revision must never silently turn a saved opt-out back on.
            AnalyticsDecision(preference, value.getValue("version").jsonPrimitive.int,
                value.getValue("decidedAt").jsonPrimitive.long)
        }
    } catch (_: Exception) {
        // A damaged/unreadable preference may represent an opt-out: fail closed.
        AnalyticsDecision(AnalyticsPreference.DISABLED, NOTICE_VERSION, 0)
    }
    val available get() = sink.available
    val preference get() = lock.locked { decision?.preference ?: AnalyticsPreference.DEFAULT }
    private var offline = true // Fail closed until persistent application settings have loaded.
    private var enabled = false
    private var generation = 0L
    private var lastScreen: AnalyticsScreen? = null
    fun setOffline(value: Boolean) = lock.locked { offline = value; applyPolicy() }
    fun setPreference(value: AnalyticsPreference) = lock.locked {
        require(value != AnalyticsPreference.DEFAULT)
        if (value == AnalyticsPreference.DISABLED) {
            enabled = false; generation++; lastScreen = null
            decision = AnalyticsDecision(value, NOTICE_VERSION, epochMillis())
            runCatching { sink.configure(false, true) }
        }
        val next = AnalyticsDecision(value, NOTICE_VERSION, epochMillis())
        // Save an explicit enable before applying it; failed writes leave an opt-out in effect.
        storage.write("analytics-preference.json", buildJsonObject {
            put("preference", next.preference.name); put("version", next.version); put("decidedAt", next.decidedAt)
        }.toString().encodeToByteArray())
        decision = next
        applyPolicy()
    }
    private fun applyPolicy() {
        val next = available && !offline && decision?.preference != AnalyticsPreference.DISABLED
        if (next == enabled) return
        enabled = false; generation++; lastScreen = null
        runCatching { sink.configure(next, !next) }.onSuccess { enabled = next }
            .onFailure { runCatching { sink.configure(false, true) } }
    }
    fun screen(screen: AnalyticsScreen) = lock.locked {
        if (enabled && lastScreen != screen) {
            lastScreen = screen
            emit("screen_view", mapOf("screen_name" to screen.name.lowercase()))
        }
    }
    fun begin(operation: AnalyticsOperation, initiator: AnalyticsInitiator = AnalyticsInitiator.MANUAL): AnalyticsMeasurement = lock.locked {
        if (enabled && operation == AnalyticsOperation.FLASH) emit("flash_started", emptyMap())
        AnalyticsMeasurement(this, operation, initiator, if (enabled) generation else -1, monotonicTimeMillis())
    }
    internal fun finish(measurement: AnalyticsMeasurement, error: AnalyticsError, success: Boolean, stage: AnalyticsStage) = lock.locked {
        if (measurement.finished) return@locked
        measurement.finished = true
        if (!enabled || measurement.generation != generation) return@locked
        emit(measurement.operation.event, mapOf(
            "result" to if (success) "success" else if (error == AnalyticsError.CANCELLED) "cancelled" else "failure",
            "error_category" to error.name.lowercase(), "stage" to stage.name.lowercase(),
            "initiator" to measurement.initiator.name.lowercase(),
            "duration_ms" to (monotonicTimeMillis() - measurement.started).coerceIn(0, 86_400_000).toString(),
            "schema_version" to "1"))
    }
    private fun emit(name: String, parameters: Map<String, String>) {
        // Analytics must never affect device operations, even when an SDK adapter fails.
        runCatching { sink.record(name, parameters) }
    }
    companion object { const val NOTICE_VERSION = 2 }
}
class AnalyticsMeasurement internal constructor(
    private val controller: AnalyticsController, internal val operation: AnalyticsOperation,
    internal val initiator: AnalyticsInitiator, internal val generation: Long, internal val started: Long,
) {
    internal var finished = false
    fun complete(success: Boolean = true, error: AnalyticsError = AnalyticsError.NONE, stage: AnalyticsStage = AnalyticsStage.FINISHED) =
        controller.finish(this, error, success, stage)
}

fun <T> AnalyticsController.measure(operation: AnalyticsOperation, block: () -> T): T {
    val measurement = begin(operation)
    try { return block().also { measurement.complete() } }
    catch (failure: Throwable) { measurement.complete(false, analyticsError(failure)); throw failure }
}
