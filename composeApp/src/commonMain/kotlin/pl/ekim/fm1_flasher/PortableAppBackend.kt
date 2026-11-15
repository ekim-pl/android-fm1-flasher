package pl.ekim.fm1_flasher

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

interface AppHost {
    fun openLink(url: String)
    @Throws(Exception::class) fun exportText(name: String, text: String)
    fun document(name: String): String
    fun setAppearance(theme: String)
    fun setFlashing(active: Boolean)
    val systemLanguage: String
}

/** Native hosts provide I/O; catalog, library policy and application orchestration remain common. */
class PortableAppBackend(private val midi: ProbeBackend, private val monitor: PassiveMidiBackend,
    private val storage: AppStorage, private val http: HttpClient, private val host: AppHost, analyticsSink: AnalyticsSink = NoAnalytics) : AppBackend {
    override val state = MutableStateFlow(AppSnapshot(language = host.systemLanguage.substringBefore('-').let { if (it in listOf("pl", "zh")) it else "en" }))
    private val analytics = AnalyticsController(analyticsSink, storage)
    override fun screen(screen: AnalyticsScreen) = analytics.screen(screen)
    private val operationLog = OperationLog(storage, ::epochMillis)
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private fun logEvent(event: String, message: String = "", level: String = "INFO") {
        logScope.launch { runCatching { operationLog.append(level, event, message) } }
    }
    private val covers = ArtworkCache(storage, http) { state.value.offline }
    override suspend fun artwork(entry: CatalogEntry) = covers.load(entry)
    private val repository = FirmwareRepository(storage, http, ::epochMillis)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val requestedPort = MutableStateFlow<String?>(null)
    private var revision = -1L
    private var settingsRepairReported = false
    init { scope.launch {
        try {
            readConfiguration()
            analytics.setOffline(repository.settings().offline)
            state.update { it.copy(analyticsAvailable = analytics.available, analyticsPreference = analytics.preference) }
            host.setAppearance(state.value.appearance)
            state.update { it.copy(initialized = true) }
            publishLibrary()
            state.update { it.copy(catalog = repository.cached()) }
            refreshPorts()
            restoreSelection()
            val journal = storage.read("flash-session.json", 128000)
            val unresolved = journal != null && runCatching { Json.parseToJsonElement(journal.decodeToString()).jsonObject["outcome"]?.jsonPrimitive?.content != "confirmed" }.getOrDefault(true)
            if (unresolved) report(msg("flash_interrupted"))
            else report(msg("library_ready"))
            if (!state.value.offline) { state.update { it.copy(catalog = trackedRefresh()) }; restoreSelection() }
        } catch (failure: Exception) { report(failure.appMessage("library_read_error")) }
        finally { state.update { it.copy(busy = false, initialized = true) } }
    } }
    private fun report(text: AppText, progress: Int = -1) {
        logEvent((text as? AppText.Resource)?.key ?: "STATUS", "${resolveCoreText(text, state.value.language)}; progress=$progress")
        state.update { it.copy(status = text, progress = progress, logs = (it.logs + text).takeLast(100)) } }
    private fun task(action: () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true) }
        scope.launch {
            try { action() } catch (failure: Exception) { logEvent("ERROR", failure.toString(), "ERROR"); report(failure.appMessage("library_change_error")) }
            finally { runCatching { publishLibrary() }.onFailure { report(it.appMessage("local_library_error")) }; state.update { it.copy(busy = false) } }
        }
    }
    fun foreground(visible: Boolean) {
        if (!visible) { monitor.stopMonitor(); return }
        host.setAppearance(state.value.appearance)
        if (!state.value.busy) task {
            refreshPorts()
            state.update { it.copy(catalog = repository.cached()) }
            if (!state.value.offline && state.value.catalog?.fresh != true) state.update { it.copy(catalog = trackedRefresh()) }
        }
    }
    private fun trackedRefresh(initiator: AnalyticsInitiator = AnalyticsInitiator.AUTOMATIC): Catalog {
        val measurement = analytics.begin(AnalyticsOperation.CATALOG, initiator)
        try { return repository.refresh().also { measurement.complete(it.fresh, if (it.fresh) AnalyticsError.NONE else AnalyticsError.OTHER) } }
        catch (failure: Exception) { measurement.complete(false, analyticsError(failure)); throw failure }
    }
    private fun refreshPorts() {
        val ports = midi.devices().map { port -> AppPort(port.id.toString(), port.label) }
        state.update { if (it.target != null && ports.none { p -> p.id == it.target.id }) it.copy(ports = ports, target = null, identity = null, chip = FlashChip.UNKNOWN) else it.copy(ports = ports) }
    }
    private fun publishLibrary() {
        val prefs = repository.settings()
        analytics.setOffline(prefs.offline)
        state.update { it.copy(downloads = repository.list(), favorites = prefs.favorites.map { id -> if (id.startsWith("firmwarehub/")) id else CatalogSource.key(CatalogSource.HUB, id) }.toSet(), offline = prefs.offline) }
        if (repository.problems.isNotEmpty()) report(AppText.Joined(repository.problems.toList(), "\n"))
        if (repository.settingsRepaired && !settingsRepairReported) { settingsRepairReported = true; report(msg("settings_repaired")) }
    }
    private fun restoreSelection() {
        state.update { it.copy(ready = null) }
        repository.settings().selectedId?.let { id -> runCatching { repository.load(id).first.entry }.onSuccess { entry -> state.update { it.copy(ready = entry) } }.onFailure { report(it.appMessage("local_library_error")) } }
    }
    private fun readConfiguration() {
        val config = storage.read("interface.json", 128000)?.decodeToString()?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return
        fun value(name: String, default: String = "") = (config[name] as? JsonPrimitive)?.contentOrNull ?: default
        state.update { it.copy(language = value("language", it.language).takeIf { l -> l in listOf("pl", "en", "zh") } ?: it.language,
            appearance = value("appearance", "SYSTEM").takeIf { a -> a in listOf("SYSTEM", "LIGHT", "DARK") } ?: "SYSTEM",
            experimental = value("experimental") == "true", browse = BrowseFilters(value("query"),
                runCatching { FirmwareCategory.valueOf(value("category", "ALL")) }.getOrDefault(FirmwareCategory.ALL),
                runCatching { FirmwareSort.valueOf(value("sort", "NAME")) }.getOrDefault(FirmwareSort.NAME), value("downloaded") == "true", value("favorites") == "true")) }
    }
    private fun saveConfiguration() {
        val next = state.value
        storage.write("interface.json", buildJsonObject {
            put("language", next.language); put("appearance", next.appearance); put("experimental", next.experimental)
            put("query", next.browse.query); put("category", next.browse.category.name); put("sort", next.browse.sort.name)
            put("downloaded", next.browse.downloadedOnly); put("favorites", next.browse.favoritesOnly)
        }.toString().encodeToByteArray())
    }
    override fun saveBrowse(filters: BrowseFilters) {
        state.update { it.copy(browse = filters) }
        scope.launch { runCatching { saveConfiguration() }.onFailure { report(it.appMessage("library_change_error")) } }
    }
    override fun document(name: String) = host.document(name)
    override fun perform(action: AppAction) {
        if (action.kind !in listOf(AppActionKind.LOGGER_REFRESH, AppActionKind.LOG_CLEAR)) logEvent("ACTION_${action.kind}")
        when (action.kind) {
            AppActionKind.ANALYTICS_PREFERENCE -> {
                runCatching { analytics.setPreference(AnalyticsPreference.valueOf(action.id)) }
                    .onFailure { report(msg("analytics_save_failed")) }
                state.update { it.copy(analyticsPreference = analytics.preference) }
            }
            AppActionKind.LOG_EXPORT -> if (state.value.experimental && !state.value.busy) logScope.launch {
                runCatching { host.exportText("fm1-operations-${epochMillis()}.jsonl", operationLog.export()) }
                    .onFailure { report(it.appMessage("logger_save_failed")) }
            }
            AppActionKind.LOG_CLEAR -> if (state.value.experimental && !state.value.busy) logScope.launch {
                runCatching { operationLog.clear() }.onSuccess {
                    state.update { it.copy(logs = emptyList(), status = msg("operation_log_cleared")) }
                }.onFailure { report(it.appMessage("library_change_error")) }
            }
            AppActionKind.UNDO_REMOVE, AppActionKind.DEVICE_ALIAS, AppActionKind.DEVICE_CHIP, AppActionKind.DEVICE_PAIR, AppActionKind.QUEUE_ADD, AppActionKind.QUEUE_REMOVE, AppActionKind.QUEUE_START, AppActionKind.QUEUE_STOP -> report(msg("ios_platform_limits"))
            AppActionKind.RECONNECT -> { if (state.value.awaitingPort && state.value.candidates.any { it.id == action.id }) requestedPort.value = action.id }
            AppActionKind.OPEN_LINK -> if (action.id.startsWith("https://") || action.id.startsWith("mailto:")) host.openLink(action.id)
            AppActionKind.LOGGER_REFRESH -> runCatching { refreshLogger() }.onFailure { report(it.appMessage("connect_error")) }
            AppActionKind.LANGUAGE, AppActionKind.APPEARANCE -> {
                if (action.kind == AppActionKind.LANGUAGE && action.id in listOf("pl", "en", "zh")) state.update { it.copy(language = action.id) }
                if (action.kind == AppActionKind.APPEARANCE && action.id in listOf("SYSTEM", "LIGHT", "DARK")) { state.update { it.copy(appearance = action.id) }; host.setAppearance(action.id) }
                scope.launch { runCatching { saveConfiguration() }.onFailure { report(it.appMessage("library_change_error")) } }
            }
            else -> task { performBlocking(action) }
        }
    }
    private fun performBlocking(action: AppAction) {
        when(action.kind) {
            AppActionKind.AUTH_LOGIN, AppActionKind.SOURCE_SAVE, AppActionKind.SOURCE_ENABLE, AppActionKind.SOURCE_CHECK, AppActionKind.SOURCE_REMOVE, AppActionKind.SOURCE_RESTORE, AppActionKind.BACKGROUND_SYNC -> failed(msg("not_applicable"))
            AppActionKind.CLEAR_DOWNLOADS -> { repository.clearDownloads(); state.update { it.copy(ready = null) }; report(msg("downloads_cleared")) }
            AppActionKind.PORTS_REFRESH -> refreshPorts()
            AppActionKind.REFRESH -> {
                check(!state.value.offline) { failed(msg("offline_refresh_blocked")) }
                report(msg("checking_catalog")); state.update { it.copy(catalog = trackedRefresh(AnalyticsInitiator.MANUAL)) }; restoreSelection(); report(msg("library_ready"))
            }
            AppActionKind.OFFLINE -> { repository.saveSettings(repository.settings().copy(offline = action.id.toBooleanStrict())); publishLibrary(); report(msg(if (state.value.offline) "offline_enabled" else "online_enabled")) }
            AppActionKind.FAVORITE -> { val prefs = repository.settings(); val favorites = prefs.favorites.map { if (it.startsWith("firmwarehub/")) it else CatalogSource.key(CatalogSource.HUB, it) }.toSet(); repository.saveSettings(prefs.copy(favorites = if (action.id in favorites) favorites - action.id else favorites + action.id)) }
            AppActionKind.USE -> {
                val entry = repository.load(action.id).first.entry; repository.saveSettings(repository.settings().copy(selectedId = action.id))
                state.update { it.copy(ready = entry) }; report(msg("package_selected", entry.name, entry.version))
            }
            AppActionKind.REMOVE -> { repository.remove(action.id); if (state.value.ready?.key == action.id) state.update { it.copy(ready = null) }; report(msg("package_removed")) }
            AppActionKind.DOWNLOAD -> analytics.measure(AnalyticsOperation.DOWNLOAD) {
                check(!state.value.offline) { failed(msg("download_offline_blocked")) }
                val entry = state.value.catalog?.entries?.singleOrNull { it.key == action.id } ?: failed(msg("download_stale"))
                report(msg("downloading_package", entry.name, entry.version)); repository.download(entry); restoreSelection(); report(msg("package_downloaded", entry.name, entry.version))
            }
            AppActionKind.UPDATE_ALL -> {
                check(!state.value.offline) { failed(msg("update_offline_blocked")) }
                val catalog = trackedRefresh(AnalyticsInitiator.MANUAL); check(catalog.fresh) { failed(msg("catalog_update_unavailable")) }; state.update { it.copy(catalog = catalog) }
                val updates = CatalogBrowser.updates(catalog, repository.list()); val errors = mutableListOf<AppText>(); var count = 0
                updates.forEachIndexed { i, entry -> report(msg("updating_package", i + 1, updates.size, entry.name)); try { analytics.measure(AnalyticsOperation.DOWNLOAD) { repository.download(entry) }; count++ } catch (failure: Exception) { errors += failure.appMessage("local_library_error") } }
                restoreSelection(); report(AppText.Joined(listOf(msg("packages_updated", count, updates.size)) + errors, "\n"))
            }
            AppActionKind.CONNECT -> analytics.measure(AnalyticsOperation.CONNECT) {
                monitor.stopMonitor(); refreshPorts(); val port = state.value.ports.singleOrNull { it.id == action.id } ?: failed(msg("usb_port_gone"))
                state.update { it.copy(target = null, identity = null, chip = FlashChip.UNKNOWN) }
                val identity = midi.open(action.id.toInt()).use { FlashSession({ _, _ -> error("Identity never reconnects") }, { _, _ -> }).identify(it) }
                check(identity.baseModel == "FM-1") { failed(msg("wrong_device_identity", identity)) }
                state.update { it.copy(target = port, identity = identity) }; report(msg("connected_only", port.label, identity))
            }
            AppActionKind.CHIP -> if (state.value.target != null && state.value.identity != null) state.update { it.copy(chip = FlashChip.valueOf(action.id)) }
            AppActionKind.FLASH -> flash(action)
            AppActionKind.EXPERIMENTAL -> { val enabled = action.id.toBooleanStrict(); if (!enabled) monitor.stopMonitor(); state.update { it.copy(experimental = enabled) }; saveConfiguration(); refreshLogger() }
            AppActionKind.LOGGER_PORT -> {
                check(state.value.experimental && !monitor.monitorRunning)
                check(monitor.monitorPorts().any { it.id.toString() == action.id })
                state.update { it.copy(monitorSelected = if (action.id in it.monitorSelected) it.monitorSelected - action.id else it.monitorSelected + action.id) }
            }
            AppActionKind.LOGGER_TOGGLE -> { check(state.value.experimental); if (monitor.monitorRunning) monitor.stopMonitor() else monitor.startMonitor(state.value.monitorSelected.map(String::toInt)); refreshLogger() }
            AppActionKind.LOGGER_CLEAR -> { monitor.midiLog.clear(); refreshLogger() }
            AppActionKind.LOGGER_EXPORT -> { check(state.value.experimental); host.exportText("midi-log-${epochMillis()}.txt", monitor.midiLog.snapshot().export()) }
            AppActionKind.DETECT, AppActionKind.RECOVERY_ENTER, AppActionKind.RECOVERY_BACKUP, AppActionKind.RECOVERY_PREPARE, AppActionKind.RECOVERY_WRITE, AppActionKind.RECOVERY_EXPORT -> failed(msg("not_applicable"))
            else -> Unit // Handled synchronously so reconnect selection can reach a blocked flash worker.
        }
    }
    private fun refreshLogger() {
        val snapshot = monitor.midiLog.snapshotIfChanged(revision)
        if (snapshot != null) revision = snapshot.revision
        state.update { it.copy(monitorPorts = monitor.monitorPorts().map { p -> AppPort(p.id.toString(), p.label) }, monitorRunning = monitor.monitorRunning,
            monitorRows = snapshot?.entries?.map { row -> LogRow(row.sequence, row.source, row.channel, row.type, row.line(true)) } ?: it.monitorRows,
            monitorStatus = snapshot?.let { log -> AppText.Joined(listOf(msg("logger_status", log.total, log.evicted, log.lostBytes), msg(if (monitor.monitorRunning) "logger_running" else "logger_stopped")), "\n") } ?: it.monitorStatus) }
    }
    private fun journal(hash: String, port: String, outcome: String) {
        storage.write("flash-session.json", buildJsonObject { put("sha256", hash); put("port", port); put("outcome", outcome) }.toString().encodeToByteArray())
    }
    private fun flash(action: AppAction) {
        val snapshot = state.value; val target = snapshot.target ?: failed(msg("flash_missing_requirements"))
        val selected = snapshot.ready ?: failed(msg("flash_missing_requirements")); val identity = snapshot.identity ?: failed(msg("flash_missing_requirements"))
        check(target.id == action.id && selected.sha256 == action.value) { failed(msg("local_package_changed")) }
        monitor.stopMonitor(); refreshPorts(); check(state.value.ports.any { it.id == target.id }) { failed(msg("usb_port_gone")) }
        val (saved, image) = repository.load(selected.id)
        check(saved.entry.sha256 == action.value && snapshot.chip.allows(saved.entry, action.forceUnknown)) { failed(msg("local_chip_unsupported")) }
        state.update { it.copy(flashing = true) }; host.setFlashing(true)
        val measurement = analytics.begin(AnalyticsOperation.FLASH)
        var analyticsPhase = AnalyticsStage.PREPARING
        val deadline = monotonicTimeMillis() + FlashSession.MAX_SESSION_MS
        var before = midi.devices().map { it.id }.toSet()
        try {
            journal(image.sha256, target.id, "preparing")
            val session = FlashSession({ previous, loader ->
                requestedPort.value = null
                state.update { it.copy(awaitingPort = true, candidates = emptyList(), identity = null) }
                analyticsPhase = AnalyticsStage.RECONNECTING
                report(msg("ios_reconnect"))
                val reconnectDeadline = minOf(deadline, monotonicTimeMillis() + 180000)
                var chosen: ProbePort? = null
                while (monotonicTimeMillis() < reconnectDeadline && chosen == null) {
                    val current = midi.devices()
                    val candidates = if (current.any { it.id == previous.id }) emptyList() else current.filter { it.id !in before && it.id != previous.id }
                    state.update { it.copy(candidates = candidates.map { p -> AppPort(p.id.toString(), p.label) }) }
                    val requested = requestedPort.value
                    chosen = candidates.singleOrNull { it.id.toString() == requested }
                    if (chosen == null) blockingPause(200)
                }
                val port = chosen ?: failed(msg("reconnect_timeout"))
                before = midi.devices().map { it.id }.toSet()
                state.update { it.copy(target = AppPort(port.id.toString(), port.label), awaitingPort = false, candidates = emptyList()) }
                midi.open(port.id)
            }, { text, progress ->
                analyticsStage(text)?.let { analyticsPhase = it }
                if (text == msg("flash_stage_verify") || text == msg("flash_stage_write")) journal(image.sha256, target.id, if (text == msg("flash_stage_write")) "writing" else "verification")
                report(text, progress)
            })
            val installed = session.flash(image, midi.open(target.id.toInt()), identity)
            journal(image.sha256, target.id, "confirmed"); state.update { it.copy(identity = installed) }
            measurement.complete()
        } catch (failure: Exception) { measurement.complete(false, analyticsError(failure), analyticsPhase); report(msg("flash_unconfirmed", failure.appMessage("flash_result_unknown"))) }
        finally { host.setFlashing(false); state.update { it.copy(flashing = false, awaitingPort = false, candidates = emptyList()) }; refreshPorts() }
    }
}
