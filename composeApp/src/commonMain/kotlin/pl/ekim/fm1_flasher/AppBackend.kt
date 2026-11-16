package pl.ekim.fm1_flasher

import kotlinx.coroutines.flow.StateFlow

data class AppPort(val id: String, val label: String)
data class LogRow(val id: Long, val source: String, val channel: Int?, val type: String, val text: String)
data class BackupRow(val id: String, val label: String)
data class RecoveryView(val token: String, val confirmation: AppText)
data class AppSnapshot(
    val sourceColors: Map<String, SourceColor> = emptyMap(), val sourceManagementAvailable: Boolean = true,
    val analyticsAvailable: Boolean = false, val analyticsPreference: AnalyticsPreference = AnalyticsPreference.DEFAULT,
    val undoAvailable: Boolean = false, val fleetAvailable: Boolean = false, val fleet: List<FleetDevice> = emptyList(),
    val jobs: List<FlashJob> = emptyList(), val queueRunning: Boolean = false, val queueStopping: Boolean = false,
    val historyRetentionMillis: Long? = 30L * 86_400_000,
    val operationLogDays: Int = OperationLog.DEFAULT_DAYS,
    val backgroundSync: Boolean = false,
    val backgroundAvailable: Boolean = false,
    val savedSourceId: String? = null,
    val sourceError: AppText? = null,
    val initialized: Boolean = false,
    val catalog: Catalog? = null, val downloads: List<LocalPackage> = emptyList(),
    val favorites: Set<String> = emptySet(), val offline: Boolean = false,
    val busy: Boolean = true, val flashing: Boolean = false, val experimental: Boolean = false,
    val ports: List<AppPort> = emptyList(), val target: AppPort? = null, val identity: FirmwareIdentity? = null,
    val chipDetection: AppText? = null,
    val chip: FlashChip = FlashChip.UNKNOWN, val ready: CatalogEntry? = null,
    val awaitingPort: Boolean = false, val candidates: List<AppPort> = emptyList(),
    val progress: Int = -1, val status: AppText = msg("loading_catalog"), val logs: List<AppText> = emptyList(),
    val monitorPorts: List<AppPort> = emptyList(), val monitorSelected: Set<String> = emptySet(),
    val monitorRunning: Boolean = false, val monitorDraining: Boolean = false,
    val monitorRows: List<LogRow> = emptyList(), val monitorStatus: AppText = msg("logger_stopped"),
    val backups: List<BackupRow> = emptyList(), val recovery: RecoveryView? = null,
    val usbRecovery: Boolean = false,
    val language: String = "en", val appearance: String = "SYSTEM", val browse: BrowseFilters = BrowseFilters(),
)
enum class AppActionKind {
    HISTORY_RETENTION, HISTORY_CLEAR, ANALYTICS_PREFERENCE, LOG_EXPORT, LOG_CLEAR, LOG_RETENTION, CLEAR_DOWNLOADS,
    UNDO_REMOVE, DEVICE_COLOR, DEVICE_ALIAS, DEVICE_CHIP, DEVICE_PAIR, QUEUE_ADD, QUEUE_REMOVE, QUEUE_START, QUEUE_STOP,
    AUTH_LOGIN, SOURCE_COLOR, SOURCE_ENABLE, SOURCE_CHECK, SOURCE_REMOVE, SOURCE_RESTORE, SOURCE_SAVE, BACKGROUND_SYNC,
    PORTS_REFRESH, REFRESH, OFFLINE, FAVORITE, DOWNLOAD, USE, REMOVE, UPDATE_ALL, CONNECT, CHIP, FLASH, RECONNECT,
    EXPERIMENTAL, LOGGER_PORT, LOGGER_TOGGLE, LOGGER_CLEAR, LOGGER_EXPORT, LOGGER_REFRESH,
    DETECT, RECOVERY_ENTER, RECOVERY_BACKUP, RECOVERY_PREPARE, RECOVERY_WRITE, RECOVERY_EXPORT,
    LANGUAGE, APPEARANCE, OPEN_LINK,
}
data class SourceConfiguration(val name: String, val url: String, val hours: Int, val packages: Set<String>, val artwork: Set<String>,
    val provider: CatalogProviderType? = null, val authentication: HttpCredentials? = null, val packageChips: Map<String, String> = emptyMap())
data class AppAction(val kind: AppActionKind, val id: String = "", val value: String = "", val source: SourceConfiguration? = null, val forceUnknown: Boolean = false, val confirmedHash: String = "")
interface AppBackend {
    val state: StateFlow<AppSnapshot>
    fun screen(screen: AnalyticsScreen) {}
    fun perform(action: AppAction)
    fun saveBrowse(filters: BrowseFilters)
    suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap?
    fun document(name: String): String
}
