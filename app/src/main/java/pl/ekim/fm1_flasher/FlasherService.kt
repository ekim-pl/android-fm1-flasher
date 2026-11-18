package pl.ekim.fm1_flasher

import android.app.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class UiState(
    val sourceColors: Map<String, SourceColor> = emptyMap(),
    val fleet: List<FleetDevice> = emptyList(), val jobs: List<FlashJob> = emptyList(),
    val undoAvailable: Boolean = false, val queueRunning: Boolean = false, val queueStopping: Boolean = false,
    val browse: Map<String, String>? = null,
    val backgroundSync: Boolean = false,
    val experimental: Boolean = false,
    val recoveryPlan: RecoveryPlan? = null,
    val recoveryBackups: List<RecoveryBackup> = emptyList(),
    val devices: List<UsbMidi> = emptyList(),
    val target: UsbMidi? = null,
    val identity: FirmwareIdentity? = null,
    val chipDetection: AppText? = null, val diagnostics: DeviceDiagnostics? = null, val tools: ToolsView? = null,
    val chip: FlashChip = FlashChip.UNKNOWN,
    val catalog: Catalog? = null,
    val downloads: List<LocalPackage> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val offline: Boolean = false,
    val refreshingCatalog: Boolean = false,
    val ready: CatalogEntry? = null,
    val status: AppText = msg(R.string.loading_catalog),
    val busy: Boolean = true,
    val flashing: Boolean = false,
    val progress: Int = -1,
    val awaitingPort: Boolean = false,
    val reconnectCandidates: List<UsbMidi> = emptyList(),
    val automaticReconnect: UsbMidi? = null,
    val logs: List<AppText> = emptyList(),
)

class FlasherService : Service() {
    inner class LocalBinder : Binder() { val service get() = this@FlasherService }
    private val sourceColors by lazy { SourceColors(AndroidStorage(filesDir)) }
    private val analytics get() = AndroidAnalytics.get(this)
    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())
    private val worker = FlasherWorker()
    private lateinit var operationLogger: AndroidOperationLogger
    fun recordEvent(event: String, message: String = "", level: String = "INFO") {
        if (::operationLogger.isInitialized) operationLogger.record(event, message, level)
    }
    fun recordAction(action: AppAction) {
        if (action.kind !in listOf(AppActionKind.LOG_CLEAR, AppActionKind.LOGGER_REFRESH)) recordEvent("ACTION_${action.kind}")
    }
    fun recordFailure(failure: Throwable) { recordEvent("ERROR", "${failure.javaClass.simpleName}: ${resolve(failure.appMessage(R.string.library_change_error))}", "ERROR") }
    fun clearOperationLog() {
        if (!state.experimental || state.busy) return
        operationLogger.execute { operations ->
            runCatching { operations.clear() }.onSuccess {
                update { it.copy(logs = emptyList(), status = msg("operation_log_cleared")) }
            }.onFailure { status(failureText(it, R.string.library_change_error)) }
        }
    }
    fun exportOperationLog(uri: android.net.Uri) {
        if (!state.experimental || state.busy) return
        operationLogger.execute { operations ->
            runCatching { checkNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(operations.export()) } }
                .onSuccess { status(msg("operation_log_saved")) }
                .onFailure { status(failureText(it, R.string.logger_save_failed)) }
        }
    }
    private val listeners = CopyOnWriteArraySet<(UiState) -> Unit>()
    private val resumePort = LinkedBlockingQueue<Endpoint>(1)
    lateinit var monitor: MidiMonitor
        private set
    private lateinit var midi: AndroidMidi
    private lateinit var database: CatalogDatabase
    private lateinit var repository: CatalogRepository
    private lateinit var credentials: SourceCredentialStore
    private lateinit var oauth: OAuthManager
    private data class ToolPlan(val token: String, val backup: RecoveryBackup, val plan: FlashWritePlan,
        val deviceName: String, val deviceId: Int, val epoch: Long, val before: ByteArray? = null)
    @Volatile private var toolPlan: ToolPlan? = null
    private val exportedBackups = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private lateinit var recoveryStore: RecoveryStore
    private lateinit var library: LocalLibrary
    private var removedPackage: Pair<CatalogEntry, ByteArray>? = null
    private lateinit var fleet: FleetStore
    @Volatile private var stopQueue = false
    @Volatile private var activeGate: ReconnectGate? = null
    @Volatile private var activeJob: String? = null
    private val continuityLock = Any()
    private var usbGeneration = 0L
    private val usbEpochs = mutableMapOf<String, Long>()
    @Synchronized fun usbEpoch(path: String): Long = usbEpochs.getOrPut(path) { ++usbGeneration }
    private val usbEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                synchronized(this@FlasherService) { usbEpochs[device.deviceName] = ++usbGeneration }
                synchronized(continuityLock) { activeGate?.removedPath(device.deviceName) }
                midi.removedPath(device.deviceName)
                toolPlan = null
                update { it.copy(diagnostics = null, chipDetection = null, tools = it.tools?.copy(token = null)) }
            }
            recordEvent(intent.action.orEmpty(), device.deviceName)
            refreshDevices()
        }
    }
    private lateinit var journal: FlashJournal
    private var manager: MidiManager? = null
    @Volatile private var libraryReady = false
    @Volatile private var uiVisible = false
    @Volatile private var refreshWhenIdle = false
    @Volatile var state = UiState()
        private set

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) { midi.added(info.id); refreshDevices() }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            synchronized(continuityLock) { state.devices.filter { it.info.id == info.id }.forEach { activeGate?.removed(it.endpoint) } }
            midi.removed(info.id)
            monitor.removed(info.id)
            refreshDevices()
            if (!state.flashing && state.target?.info?.id == info.id) {
                update { it.copy(target = null, identity = null, chip = FlashChip.UNKNOWN) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        operationLogger = AndroidOperationLogger.get(this)
        recordEvent("SERVICE_STARTED", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        state = state.copy(experimental = getSharedPreferences("features", MODE_PRIVATE).getBoolean("experimental", false))
        manager = getSystemService(MidiManager::class.java)
        midi = AndroidMidi(manager, main)
        monitor = MidiMonitor(manager, getSystemService(UsbManager::class.java), main)
        database = CatalogDatabase.get(this)
        credentials = SourceCredentialStore(this)
        oauth = OAuthManager(credentials)
        repository = CatalogRepository(database)
        recoveryStore = RecoveryStore(File(filesDir, "recovery"))
        library = LocalLibrary(File(filesDir, "library"), database)
        registerReceiver(usbEvents, IntentFilter().apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }, RECEIVER_NOT_EXPORTED)
        journal = FlashJournal(File(filesDir, "flash-session.json"))
        manager?.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, mainExecutor, callback)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("flash", AppLanguage.wrap(this).s(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        execute {
            try {
                fleet = FleetStore(File(filesDir, "fleet.json"))
                refreshDevices()
                database.migrate(filesDir)
                libraryReady = true
                CatalogSyncWorker.schedule(this, database.setting("background") == "true" && !database.settings().offline)
                File(filesDir, "previews").deleteRecursively()
                publishLibrary()
                update { it.copy(recoveryBackups = recoveryStore.list()) }
                val cached = repository.cached()
                if (cached.entries.isNotEmpty()) {
                    update { it.copy(catalog = cached) }
                    status(msg(R.string.library_ready))
                } else status(msg(R.string.catalog_missing))
                restoreSelection()
                if (journal.unresolved(fleet.confirmedJobId) || fleet.jobs.any { it.outcome == JobOutcome.UNCONFIRMED }) status(msg(R.string.flash_interrupted))
                if (fleet.loadFailed) status(msg("fleet_storage_error"))
            } catch (failure: Exception) { status(failureText(failure, R.string.library_read_error)) }
            finally { finishLibraryTask() }
        }
    }

    override fun onBind(intent: Intent) = binder
    fun observe(listener: (UiState) -> Unit) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("flash", AppLanguage.wrap(this).s(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        if (state.flashing) getSystemService(NotificationManager::class.java).notify(1, notification(state.status))
        listeners += listener; listener(state)
    }
    fun unobserve(listener: (UiState) -> Unit) { listeners -= listener }

    @Synchronized private fun update(transform: (UiState) -> UiState) {
        state = transform(state)
        val snapshot = state
        main.post { listeners.forEach { it(snapshot) } }
    }

    private fun status(text: AppText, progress: Int = -1) {
        if (state.status == text && state.progress == progress) return
        recordEvent((text as? AppText.Resource)?.key ?: "STATUS", "${resolve(text)}; progress=$progress")
        update { it.copy(status = text, progress = progress, logs = (it.logs + text).takeLast(100)) }
        if (state.flashing) getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    private fun failureText(failure: Throwable, fallback: Int): AppText {
        recordFailure(failure)
        return failure.appMessage(fallback)
    }
    private fun execute(action: () -> Unit) = worker.execute(action)

    fun refreshDevices() {
        val devices = midi.devices()
        if (state.devices.map { it.endpoint } != devices.map { it.endpoint }) recordEvent("MIDI_PORTS_CHANGED", devices.joinToString { it.endpoint.toString() })
        synchronized(continuityLock) { activeGate?.observe(devices.map { it.endpoint }); activeGate?.observeUsb(getSystemService(UsbManager::class.java).deviceList.keys) }
        // Keep transition candidates out of the registry until explicitly bound by the session.
        update { it.copy(devices = devices) }
        if (!state.flashing) execute {
            if (::fleet.isInitialized && !state.flashing) {
                runCatching { fleet.attach(midi.devices().map { it.endpoint }) }
                    .onFailure { status(failureText(it, R.string.library_read_error)) }
                publishFleet()
            }
        }
    }
    private fun publishFleet() { update { it.copy(fleet = fleet.devices, jobs = fleet.jobs) } }
    fun configureDevice(id: String, alias: String? = null, chip: FlashChip? = null, color: Int? = null) {
        if (state.busy) return
        libraryTask { fleet.configure(id, alias, chip, color); publishFleet() }
    }
    fun pairDevice(oldId: String, liveId: String) {
        if (state.busy) return
        libraryTask { fleet.pair(oldId, liveId); publishFleet() }
    }
    fun enqueue(id: String, packageKey: String, forceUnknown: Boolean = false, confirmedHash: String = "") {
        if (state.busy) return
        val entry = state.downloads.singleOrNull { it.entry.key == packageKey }?.entry ?: return
        check(!forceUnknown || entry.sha256 == confirmedHash) { failed(msg("local_package_changed")) }
        libraryTask { fleet.enqueue(id, entry, forceUnknown); publishFleet() }
    }
    fun cancelJob(id: String) { if (!state.queueRunning) libraryTask { fleet.cancel(id); publishFleet() } }
    val historyRetentionMillis: Long?
        get() = if (::fleet.isInitialized) fleet.historyRetentionMillis else FleetStore.DEFAULT_HISTORY_RETENTION_MILLIS
    fun setHistoryRetention(retentionMillis: Long?) = libraryTask {
        fleet.setHistoryRetention(retentionMillis); publishFleet()
    }
    fun clearHistory() = libraryTask { fleet.clearHistory(); publishFleet() }
    fun stopQueue() { stopQueue = true; update { it.copy(queueStopping = true) } }


    private fun hasNetwork(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun onAppOpened() {
        recordEvent("APP_FOREGROUND")
        uiVisible = true
        if (state.busy) { refreshWhenIdle = true; return }
        refreshWhenIdle = false
        libraryTask {
            if (CatalogRefreshPolicy.shouldRefresh(library.settings().offline, hasNetwork(), database.sources(), System.currentTimeMillis())) trackedCatalog(AnalyticsInitiator.AUTOMATIC)
            publishLibrary()
            restoreSelection()
        }
    }

    fun setExperimental(enabled: Boolean) {
        if (state.busy) return
        getSharedPreferences("features", MODE_PRIVATE).edit().putBoolean("experimental", enabled).apply()
        if (!enabled) monitor.stop()
        toolPlan = null
        update { it.copy(experimental = enabled, recoveryPlan = null, tools = it.tools?.copy(token = null)) }
    }

    fun toggleMonitor() {
        if (!state.experimental) return
        if (monitor.running) {
            monitor.stop()
            if (!state.busy) stopSelf()
        } else if (!state.busy && !state.flashing) {
            startService(Intent(this, FlasherService::class.java).setAction("MONITOR"))
            monitor.start()
            if (!monitor.running) stopSelf()
        }
    }

    fun onAppClosed() {
        recordEvent("APP_BACKGROUND")
        uiVisible = false; refreshWhenIdle = false; monitor.stop()
        if (!state.busy) stopSelf()
    }

    private fun finishLibraryTask(completion: (() -> Unit)? = null) {
        main.post {
            if (!monitor.running) stopSelf()
            update { it.copy(busy = false, refreshingCatalog = false) }
            completion?.invoke()
            checkDeferredRefresh()
        }
    }

    private fun checkDeferredRefresh() {
        main.post {
            if (uiVisible && refreshWhenIdle) { refreshWhenIdle = false; onAppOpened() }
        }
    }

    fun refreshCatalog() {
        if (!libraryReady) return
        if (state.busy) return
        if (state.offline) { status(msg(R.string.offline_refresh_blocked)); return }
        if (!hasNetwork()) { status(msg(R.string.no_network_preserved)); return }
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        update { it.copy(busy = true, refreshingCatalog = true) }
        status(msg(R.string.checking_catalog))
        execute { loadCatalog() }
    }

    private fun trackedCatalog(initiator: AnalyticsInitiator = AnalyticsInitiator.MANUAL, sourceId: String? = null, manualRefresh: Boolean = false): Catalog {
        val measurement = analytics.begin(AnalyticsOperation.CATALOG, initiator)
        try { return repository.load(sourceId = sourceId, manualExperimental = manualRefresh && state.experimental).also { measurement.complete(it.fresh, if (it.fresh) AnalyticsError.NONE else AnalyticsError.OTHER) } }
        catch (failure: Exception) { measurement.complete(false, analyticsError(failure)); throw failure }
    }
    private fun loadCatalog() {
        try {
            val catalog = trackedCatalog(manualRefresh = true)
            update { it.copy(catalog = catalog) }
            restoreSelection()
            val updates = CatalogBrowser.updates(catalog, state.downloads).size
            status(if (catalog.fresh) msg(R.string.catalog_checked, updates)
                else msg(R.string.catalog_stale_local_ready))
        } catch (failure: Exception) { status(failureText(failure, R.string.catalog_read_error)) }
        finally { finishLibraryTask() }
    }

    fun detectChip(device: android.hardware.usb.UsbDevice) {
        if (!state.experimental || state.busy) return
        monitor.stop()
        update { it.copy(chipDetection = null) }
        detectChipStopped(device)
    }

    private fun detectChipStopped(device: android.hardware.usb.UsbDevice) = libraryTask {
        val epoch = usbEpoch(device.deviceName)
        MidiSessionGuard.acquire()
        try {
            val info = UsbBootConnection.open(getSystemService(UsbManager::class.java), device, assets).use { checkNotNull(it.diagnostics) }
            check(usbEpoch(device.deviceName) == epoch)
            val result = msg(R.string.chip_detected_separate, device.deviceName, msg(ChipDetector.classify(info.flashId).labelKey))
            update { if (usbEpoch(device.deviceName) == epoch) it.copy(chipDetection = result, diagnostics = info) else it }; status(result)
        } finally { MidiSessionGuard.release() }
    }

    fun setChip(chip: FlashChip) {
        if (!state.busy && state.target != null && state.identity != null) {
            fleet.devices.singleOrNull { it.endpoint == state.target?.endpoint }?.let { fleet.configure(it.id, chip = chip) }
            update { it.copy(chip = chip) }; publishFleet()
        }
    }

    fun connect(id: Int) {
        if (state.busy) return
        val target = midi.devices().singleOrNull { it.info.id == id } ?: return
        monitor.stop()
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        update { it.copy(busy = true, target = target, identity = null, chip = FlashChip.UNKNOWN) }
        execute {
            val measurement = analytics.begin(AnalyticsOperation.CONNECT)
            try {
                val identity = midi.open(target).use { FlashSession({ _, _ -> failed(msg(R.string.not_applicable)) }, { _, _ -> }).identify(it) }
                check(identity.baseModel == "FM-1") { failed(msg(R.string.wrong_device_identity, identity)) }
                fleet.identify(target.endpoint, identity, runCatching { target.usb.serialNumber }.getOrNull())
                publishFleet()
                update { it.copy(identity = identity, chip = fleet.devices.single { d -> d.endpoint == target.endpoint }.chip) }
                status(msg(R.string.connected_only, target.usb.deviceName, identity))
                measurement.complete()
            } catch (failure: Exception) {
                measurement.complete(false, analyticsError(failure))
                update { it.copy(target = null, identity = null) }
                status(failureText(failure, R.string.connect_error))
            } finally { finishLibraryTask() }
        }
    }

    private fun publishLibrary() {
        val settings = library.settings()
        analytics.setOffline(settings.offline)
        val downloads = library.list()
        update { it.copy(sourceColors = sourceColors.load(), downloads = downloads, favorites = settings.favorites, offline = settings.offline,
            catalog = repository.cached(), backgroundSync = database.setting("background") == "true", browse = database.browseSettings()) }
        if (library.problems.isNotEmpty()) status(AppText.Joined(library.problems.toList()))
        if (library.settingsRepaired) status(msg(R.string.settings_repaired))
    }

    private fun restoreSelection() {
        update { it.copy(ready = null) }
        val selected = library.settings().selectedId
        if (selected != null) {
            try {
                val ready = library.load(selected, state.catalog).first.entry
                update { it.copy(ready = ready) }
            }
            catch (failure: Exception) { status(msg(R.string.choose_package_again, failureText(failure, R.string.local_library_error))) }
        }
    }

    private fun libraryTask(message: AppText? = null, completion: (() -> Unit)? = null, action: () -> Unit) {
        if (!libraryReady) return
        if (state.busy) return
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        update { it.copy(busy = true) }
        if (message != null) status(message)
        execute {
            try { action() }
            catch (failure: Exception) { status(failureText(failure, R.string.library_change_error)) }
            finally {
                runCatching { publishLibrary() }.onFailure { status(failureText(it, R.string.local_library_error)) }
                finishLibraryTask(completion)
            }
        }
    }

    fun saveBrowse(values: Map<String, String>) = execute {
        try { database.saveBrowse(values) } catch (e: Exception) { status(failureText(e, R.string.library_change_error)) }
    }

    fun setOffline(offline: Boolean) = libraryTask {
        library.saveSettings(library.settings().copy(offline = offline))
        CatalogSyncWorker.schedule(this, database.setting("background") == "true" && !offline)
        refreshWhenIdle = !offline
        status(if (offline) msg(R.string.offline_enabled) else msg(R.string.online_enabled))
    }

    fun setBackgroundSync(enabled: Boolean) = libraryTask {
        database.setting("background", enabled.toString())
        CatalogSyncWorker.schedule(this, enabled && !library.settings().offline)
    }

    fun setSourceColor(id: String, color: SourceColor?) = libraryTask {
        check(!database.source(id).deleted)
        sourceColors.set(id, color)
    }

    fun setSourceEnabled(id: String, enabled: Boolean) = libraryTask {
        database.setEnabled(id, enabled)
        if (enabled && !library.settings().offline && hasNetwork()) trackedCatalog(sourceId = id)
        publishLibrary(); restoreSelection()
        if (enabled) sourceResult(id) else status(msg(R.string.source_disabled))
    }

    fun checkSource(id: String) = libraryTask(msg(R.string.checking_catalog)) {
        check(!library.settings().offline) { failed(msg(R.string.offline_refresh_blocked)) }
        check(hasNetwork()) { failed(msg(R.string.no_network_preserved)) }
        trackedCatalog(sourceId = id, manualRefresh = true)
        publishLibrary(); restoreSelection()
        sourceResult(id)
    }

    private fun sourceResult(id: String) {
        val source = database.source(id)
        status(when {
            library.settings().offline -> msg(R.string.offline_refresh_blocked)
            source.error != null -> msg(R.string.source_error_retained, source.name, source.error.orEmpty())
            !hasNetwork() -> msg(R.string.no_network_preserved)
            else -> msg(R.string.source_waiting, source.name,
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(source.nextAttemptAt)))
        })
    }

    fun removeSource(id: String) = libraryTask { database.removeSource(id); credentials.remove(id); publishLibrary(); restoreSelection() }
    fun restoreSources() = libraryTask { database.restoreDefaults(); listOf(CatalogSource.HUB, "awesome-fm1").forEach { credentials.remove(it); sourceColors.set(it, null) }; refreshWhenIdle = true }
    fun configureSource(id: String?, name: String, url: String, hours: Int, packages: Set<String>, artwork: Set<String>,
                        checkNow: Boolean, provider: CatalogProviderType? = null, authentication: HttpCredentials? = null, packageChips: Map<String, String>? = null,
                        finished: (String?, AppText?) -> Unit) {
        if (!libraryReady || state.busy) { finished(null, msg(R.string.library_read_error)); return }
        var savedId: String? = null
        var failure: AppText? = null
        libraryTask(completion = { finished(savedId, failure) }) {
            try {
                val saved = database.transaction {
                    val previous = id?.let(database::source)
                    val next = database.configure(id, name, url, hours, packages, artwork, provider,
                        authentication?.method, authentication?.username, authentication?.header, authentication?.oauth, packageChips)
                    if (authentication != null) {
                        val old = if (next.id == id && previous?.provider == next.provider && previous.url == next.url && previous.authMethod == authentication.method && previous.authUsername == authentication.username && previous.authHeader == authentication.header && authentication.method != HttpAuthMethod.NONE && authentication.secret.isEmpty() && authentication.clientSecret.isEmpty())
                            credentials.read(next.id) else null
                        val retainedOAuth = old?.takeIf { it.oauth == authentication.oauth }
                        val value = HttpCredentials(authentication.method, authentication.username,
                            if (authentication.method == HttpAuthMethod.OAUTH2) retainedOAuth?.secret.orEmpty() else authentication.secret.ifBlank { old?.secret.orEmpty() },
                            authentication.header, authentication.oauth, authentication.clientSecret.ifBlank { retainedOAuth?.clientSecret.orEmpty() },
                            retainedOAuth?.refreshToken.orEmpty(), retainedOAuth?.expiresAt ?: 0)
                        value.validate()
                        if (!value.equivalentTo(old)) credentials.write(next.id, value)
                        if (authentication.secret.isNotEmpty() || authentication.clientSecret.isNotEmpty() || (previous != null && (previous.authMethod != next.authMethod || previous.oauth != next.oauth)))
                            database.authenticationChanged(next.id)
                    }
                    database.source(next.id)
                }
                savedId = saved.id
                if (id != null && id != saved.id) credentials.remove(id)
                if (checkNow) {
                    if (!library.settings().offline && hasNetwork()) trackedCatalog(sourceId = saved.id)
                    else status(msg(R.string.source_check_offline))
                }
                publishLibrary(); restoreSelection()
                recordEvent("SOURCE_CONFIGURED", "${saved.id} · ${saved.provider.id}")
                if (checkNow) sourceResult(saved.id) else status(msg(R.string.source_saved))
            } catch (e: Exception) { failure = failureText(e, R.string.source_invalid_config); throw e }
        }
    }

    fun beginOAuth(id: String, openBrowser: (String) -> Unit) {
        var address: String? = null
        libraryTask(completion = { address?.let(openBrowser) }) {
            val source = database.source(id)
            check(!state.offline && hasNetwork()) { failed(msg(R.string.no_network_preserved)) }
            if (source.oauth.grant == OAuthGrant.CLIENT_CREDENTIALS) {
                oauth.authorizeClient(source); database.authenticationChanged(id); publishLibrary(); status(msg("oauth_authorized"))
            } else { address = oauth.begin(source); status(msg("oauth_waiting")) }
        }
    }
    fun finishOAuth(uri: android.net.Uri, completion: () -> Unit = {}) {
        if (!libraryReady || state.busy) { completion(); return }
        libraryTask(completion = completion) {
            recordEvent("OAUTH_CALLBACK_RECEIVED")
            check(!state.offline && hasNetwork()) { failed(msg(R.string.no_network_preserved)) }
            val id = oauth.finish(uri, database::source)
            database.authenticationChanged(id); publishLibrary(); status(msg("oauth_authorized"))
        }
    }

    fun toggleFavorite(id: String) = libraryTask {
        val settings = library.settings()
        val favorites = if (id in settings.favorites) settings.favorites - id else settings.favorites + id
        library.saveSettings(settings.copy(favorites = favorites))
    }

    fun useLocal(id: String) = libraryTask(msg(R.string.checking_local_package)) {
        toolPlan = null
        update { it.copy(tools = it.tools?.copy(token = null)) }
        val saved = library.load(id, state.catalog).first
        library.saveSettings(library.settings().copy(selectedId = id))
        update { it.copy(ready = saved.entry) }
        status(msg(R.string.package_selected, entryTitleText(saved.entry), saved.entry.version))
    }

    fun removeLocal(id: String) = libraryTask {
        check(!FleetPolicy.pinned(fleet.jobs, id)) { failed(msg("fleet_package_pinned")) }
        val removed = runCatching { library.removalBackup(id) }.getOrNull()
        library.remove(id)
        removedPackage = removed
        update { it.copy(undoAvailable = removed != null) }
        if (state.ready?.key == id) update { it.copy(ready = null) }
        status(msg(R.string.package_removed))
    }

    fun clearDownloads() = libraryTask {
        check(fleet.jobs.none { it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) }) { failed(msg("fleet_package_pinned")) }
        removedPackage = null
        update { it.copy(ready = null, undoAvailable = false) }
        library.clearDownloads()
        status(msg("downloads_cleared"))
    }

    fun undoRemove() = libraryTask {
        val removed = removedPackage ?: return@libraryTask
        check(!FleetPolicy.pinned(fleet.jobs, removed.first.key) && library.list().none { it.entry.key == removed.first.key }) { failed(msg("fleet_package_pinned")) }
        library.save(removed.first, removed.second)
        removedPackage = null; update { it.copy(undoAvailable = false) }
        status(msg("fleet_restored"))
    }

    private fun storeDownload(entry: CatalogEntry) = analytics.measure(AnalyticsOperation.DOWNLOAD) {
        check(!FleetPolicy.pinned(fleet.jobs, entry.key)) { failed(msg("fleet_package_pinned")) }
        check(!state.offline) { failed(msg(R.string.download_offline_blocked)) }
        val raw = repository.download(entry)
        val verified = repository.cached().entries.single { it.key == entry.key && it.sha256 == entry.sha256 }
        library.save(verified, raw)
        if (state.ready?.key == entry.key) {
            library.saveSettings(library.settings().copy(selectedId = null))
            update { it.copy(ready = null) }
        }
    }

    fun download(entry: CatalogEntry) = libraryTask(msg(R.string.downloading_package, entryTitleText(entry), entry.version)) {
        storeDownload(entry)
        status(msg(R.string.package_downloaded, entryTitleText(entry), entry.version))
    }

    fun updateDownloads() = libraryTask(msg(R.string.checking_package_updates)) {
        check(!state.offline) { failed(msg(R.string.update_offline_blocked)) }
        val catalog = trackedCatalog(manualRefresh = true)
        update { it.copy(catalog = catalog) }
        restoreSelection()
        val updates = CatalogBrowser.updates(catalog, library.list())
        val errors = mutableListOf<AppText>()
        val unavailable = library.list().filter { saved ->
            catalog.source(saved.entry)?.let { !it.active || !it.fresh() } != false
        }.map { entryTitleText(it.entry) }
        var completed = 0
        updates.forEachIndexed { index, entry ->
            status(msg(R.string.updating_package, index + 1, updates.size, entryTitleText(entry)))
            try { storeDownload(entry); completed++ }
            catch (failure: Exception) { errors += AppText.Joined(listOf(entryTitleText(entry), failureText(failure, R.string.local_library_error)), ": ") }
        }
        val report = mutableListOf<AppText>()
        report += if (updates.isEmpty() && unavailable.isEmpty()) msg(R.string.packages_current)
            else msg(R.string.packages_updated, completed, updates.size)
        if (errors.isNotEmpty()) report += msg(R.string.old_packages_kept, AppText.Joined(errors, "; "))
        if (unavailable.isNotEmpty()) report += msg(R.string.sources_updates_skipped, AppText.Joined(unavailable, ", "))
        status(AppText.Joined(report))
    }

    fun resumeWith(endpoint: Endpoint) {
        if (state.awaitingPort && state.reconnectCandidates.any { it.endpoint == endpoint }) resumePort.offer(endpoint)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action?.startsWith("TOOLS_") == true) return startTools(intent)
        if (intent?.action?.startsWith("RECOVERY_") == true) return startRecovery(intent)
        if (intent?.action != "QUEUE") return START_NOT_STICKY
        if (state.busy) return START_NOT_STICKY
        try {
            check(intent.getStringExtra("plan") == FleetPolicy.token(fleet.jobs)) { failed(msg("fleet_plan_changed")) }
            val plan = fleet.jobs.filter { it.outcome == JobOutcome.QUEUED }
            check(plan.isNotEmpty()) { failed(msg("fleet_empty_tasks")) }
            check(plan.all { job -> FleetPolicy.ready(job, fleet.devices.singleOrNull { it.id == job.deviceId }) }) { failed(msg("fleet_plan_changed")) }
            monitor.stop()
            startForeground(1, notification(msg(R.string.flash_preparing)))
            stopQueue = false
            update { it.copy(busy = true, flashing = true, queueRunning = true, queueStopping = false) }
            execute {
                try {
                    // Validate every pinned package before the first device receives any command.
                    FleetPolicy.runPlan(plan, { stopQueue }, prepare = { job ->
                        val (saved, _) = library.load(job.packageKey, repository.cached())
                        check(saved.entry.sha256 == job.hash && job.chip.allows(saved.entry, job.forceUnknown)) { failed(msg("local_package_changed")) }
                    }, run = { job ->
                        check(FleetPolicy.ready(job, fleet.devices.singleOrNull { it.id == job.deviceId })) { failed(msg("fleet_plan_changed")) }
                        runJob(job)
                    })
                } catch (failure: Exception) {
                    status(if (state.status == msg("fleet_storage_error")) state.status
                        else msg(R.string.flash_unconfirmed, failureText(failure, R.string.flash_result_unknown)))
                } finally {
                    activeJob = null
                    synchronized(continuityLock) { activeGate = null }
                    main.post {
                        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                        update { it.copy(busy = false, flashing = false, queueRunning = false, awaitingPort = false,
                            reconnectCandidates = emptyList(), automaticReconnect = null, queueStopping = false) }
                        refreshDevices(); checkDeferredRefresh()
                    }
                }
            }
        } catch (failure: Exception) {
            status(failureText(failure, R.string.flash_result_unknown)); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun runJob(job: FlashJob) {
        val measurement = analytics.begin(AnalyticsOperation.FLASH)
        var analyticsPhase = AnalyticsStage.PREPARING
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fm1:flash")
        var ownsMidi = false
        val persistence = FlashPersistence(fleet, journal, job) { error ->
            stopQueue()
            recordFailure(error)
            status(msg("fleet_storage_error"))
        }
        try {
            val target = midi.devices().singleOrNull { it.endpoint == job.endpoint } ?: failed(msg("fleet_plan_changed"))
            check(getSystemService(UsbManager::class.java).hasPermission(target.usb)) { failed(msg("reconnect_permission")) }
            MidiSessionGuard.acquire(); ownsMidi = true; wake.acquire(16 * 60_000L)
            val (saved, firmware) = library.load(job.packageKey, repository.cached())
            check(saved.entry.sha256 == job.hash && job.chip.allows(saved.entry, job.forceUnknown)) { failed(msg("local_package_changed")) }
            persistence.begin(); activeJob = job.id; publishFleet()
            update { it.copy(target = target, identity = job.expected, chip = job.chip, progress = -1) }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FlashSession.MAX_SESSION_MS)
            synchronized(continuityLock) { activeGate = ReconnectGate(target.endpoint, midi.devices().map { it.endpoint }, getSystemService(UsbManager::class.java).deviceList.keys) }
            val sessionPaths = mutableSetOf(target.endpoint.usbPath)
            val originalSerial = fleet.devices.single { it.id == job.deviceId }.serial
            val session = FlashSession(reconnect = { _, loader ->
                persistence.progress(if (loader) "reconnect_loader" else "reconnect_final"); publishFleet()
                analyticsPhase = AnalyticsStage.RECONNECTING
                val chosen = awaitReconnect(checkNotNull(activeGate), loader, deadline)
                val serial = runCatching { chosen.usb.serialNumber }.getOrNull()
                check(originalSerial.isNullOrBlank() || serial.isNullOrBlank() || originalSerial == serial) { failed(msg("fleet_serial_changed")) }
                synchronized(continuityLock) {
                    activeGate!!.observeUsb(getSystemService(UsbManager::class.java).deviceList.keys)
                    check(activeGate!!.valid(midi.devices().map { it.endpoint })) { failed(msg("fleet_continuity_lost")) }
                    sessionPaths += chosen.endpoint.usbPath
                    activeGate = ReconnectGate(chosen.endpoint, midi.devices().map { it.endpoint }, getSystemService(UsbManager::class.java).deviceList.keys, sessionPaths.toSet())
                }
                persistence.rebind(chosen.endpoint); publishFleet()
                update { it.copy(target = chosen, identity = null) }
                midi.open(chosen)
            }, report = { text, progress ->
                analyticsStage(text)?.let { analyticsPhase = it }
                val phase = (text as? AppText.Resource)?.key ?: "flash_preparing"
                persistence.progress(phase, progress); publishFleet()
                status(text, progress)
            }, validate = { endpoint ->
                synchronized(continuityLock) {
                    val current = midi.devices().map { it.endpoint }
                    activeGate?.observeUsb(getSystemService(UsbManager::class.java).deviceList.keys)
                    check(endpoint in current && activeGate?.valid(current) == true) { failed(msg("fleet_continuity_lost")) }
                }
            })
            val installed = session.flash(firmware, midi.open(target), job.expected)
            persistence.rebind(checkNotNull(state.target).endpoint, installed)
            val durable = persistence.confirmed()
            publishFleet()
            if (!durable) status(msg("fleet_storage_error"))
            update { it.copy(identity = installed) }
            measurement.complete()
        } catch (failure: Exception) {
            measurement.complete(false, analyticsError(failure), analyticsPhase)
            val reason = (failureText(failure, R.string.flash_result_unknown) as? AppText.Resource)?.takeIf { it.arguments.isEmpty() }?.key ?: "fleet_unconfirmed"
            persistence.unconfirmed(reason); publishFleet()
            throw failure
        } finally {
            activeJob = null
            synchronized(continuityLock) { activeGate = null }
            if (ownsMidi) MidiSessionGuard.release()
            if (wake.isHeld) wake.release()
            if (persistence.failure != null) status(msg("fleet_storage_error"))
        }
    }

    fun enterDownloadMode() {
        if (state.busy || !state.experimental) return
        monitor.stop()
        enterDownloadModeStopped()
    }

    private fun enterDownloadModeStopped() = libraryTask {
        val target = state.target ?: failed(msg(R.string.flash_missing_requirements))
        val expected = state.identity ?: failed(msg(R.string.flash_missing_requirements))
        check(!expected.isLoader) { failed(msg(R.string.recovery_midi_required)) }
        midi.open(target).use { link ->
            val current = FlashSession({ _, _ -> failed(msg(R.string.not_applicable)) }, { _, _ -> }).identify(link)
            check(current.sameAs(expected) && current.model == "FM-1") { failed(msg(R.string.flash_identity_changed)) }
            link.send(Protocol.hex("f0 22 24 35 7d f7"))
        }
        update { it.copy(target = null, identity = null, chip = FlashChip.UNKNOWN, recoveryPlan = null) }
        status(msg(R.string.recovery_key_sent))
    }

    fun exportRecovery(id: String, uri: android.net.Uri) = libraryTask {
        val backup = recoveryStore.list().single { it.id == id }
        val output = checkNotNull(contentResolver.openOutputStream(uri, "wt"))
        output.use { recoveryStore.export(backup, it) }
        exportedBackups.add(backup.id)
        update { it.copy(tools = it.tools?.let { view -> if (view.backupId == backup.id) view.copy(exported = true) else view }) }
        status(msg(R.string.recovery_exported))
    }

    fun inspectTools(id: String) = libraryTask {
        val backup = recoveryStore.list().single { it.id == id }
        showTools(backup)
    }
    private fun showTools(backup: RecoveryBackup) {
        val view = ToolsView(backup.id, backup.sha256, backup.diagnostics,
            recoveryStore.load(backup).let { if (backup.dataOnly) null else FlashLayout.analyze(it, backup.chipKey) }, exported = backup.id in exportedBackups)
        toolPlan = null
        update { it.copy(tools = view, recoveryBackups = recoveryStore.list()) }
    }
    fun importTools(uris: List<android.net.Uri>) = libraryTask {
        check(uris.size in 1..2)
        val values = uris.map { uri -> checkNotNull(contentResolver.openInputStream(uri)).use { BackupImport.bounded(it, Recovery.FLASH_SIZE + 65536) } }
        val image = values.single { it.size >= Recovery.FLASH_SIZE || it.take(2) == listOf(0x50.toByte(), 0x4B.toByte()) }
        val sidecar = values.singleOrNull { it !== image }
        showTools(BackupImport.read(image, sidecar, recoveryStore))
        status(msg("tools_imported"))
    }
    private fun startTools(intent: Intent): Int {
        val action = intent.action
        val midiMode = action in listOf("TOOLS_MIDI_INFO", "TOOLS_MIDI_BACKUP")
        if (state.busy || (!midiMode && !state.experimental) || !libraryReady) return START_NOT_STICKY
        if (action !in listOf("TOOLS_READ", "TOOLS_PREPARE", "TOOLS_WRITE", "TOOLS_MIDI_INFO", "TOOLS_MIDI_BACKUP")) return START_NOT_STICKY
        val snapshot = state
        val pending = toolPlan
        val usb = getSystemService(UsbManager::class.java)
        val device = if (midiMode) snapshot.target?.usb else usb.deviceList.values.filter(ChipDetector::supports).singleOrNull()
        if (device == null || device.deviceName != intent.getStringExtra("deviceName") || device.deviceId != intent.getIntExtra("deviceId", -1) || !usb.hasPermission(device)) {
            status(msg("recovery_changed")); return START_NOT_STICKY
        }
        if (action == "TOOLS_WRITE" && (pending == null || pending.token != intent.getStringExtra("token") ||
                pending.deviceName != device.deviceName || pending.deviceId != device.deviceId || pending.epoch != usbEpoch(device.deviceName) ||
                pending.backup.id !in exportedBackups)) {
            status(msg("tools_write_unavailable")); return START_NOT_STICKY
        }
        try { monitor.stop(); startForeground(1, notification(msg("tools_reading"))) }
        catch (failure: Exception) { status(failureText(failure, R.string.recovery_failed)); return START_NOT_STICKY }
        val epoch = usbEpoch(device.deviceName)
        toolPlan = null
        update { it.copy(busy = true, flashing = true, tools = it.tools?.copy(token = null)) }
        execute {
            val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fm1:tools")
            var owns = false
            try {
                MidiSessionGuard.acquire(); owns = true; wake.acquire(16 * 60_000L)
                if (midiMode) {
                    val target = checkNotNull(snapshot.target)
                    midi.open(target).use { link ->
                        val client = MbootClient(link)
                        val info = client.identify()
                        check(usbEpoch(device.deviceName) == epoch)
                        update { if (usbEpoch(device.deviceName) == epoch) it.copy(diagnostics = info) else it }
                        if (action == "TOOLS_MIDI_BACKUP") {
                            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(12)
                            fun read(): ByteArray {
                                val bytes = ByteArray(info.capacity)
                                for (at in bytes.indices step 512) {
                                    check(System.nanoTime() < deadline && usbEpoch(device.deviceName) == epoch) { "MIDI backup expired" }
                                    client.read(at, 512).copyInto(bytes, at)
                                    status(msg("tools_reading"), (at + 512) * 100 / bytes.size)
                                }
                                return bytes
                            }
                            val first = read(); check(first.contentEquals(read())) { "Backup reads differ" }
                            val crc = java.util.zip.CRC32().also { it.update(first) }.value
                            check(client.crc32(0, first.size) == crc) { "Device checksum differs" }
                            showTools(recoveryStore.save(first, info.flashId, info.chipKey, info))
                        }
                    }
                } else {
                    UsbBootConnection.open(usb, device, assets, if (action == "TOOLS_WRITE") pending!!.plan else null).use { flash ->
                        val info = checkNotNull(flash.diagnostics)
                        update { if (usbEpoch(device.deviceName) == epoch) it.copy(diagnostics = info) else it }
                        if (action == "TOOLS_WRITE") {
                            val job = checkNotNull(pending)
                            check(usbEpoch(device.deviceName) == epoch)
                            recoveryStore.result(job.backup, "writing", job.plan.inputHash)
                            try {
                                val saved = recoveryStore.load(job.backup)
                                DeviceToolsWriter.apply(flash, info, job.before ?: saved, job.plan,
                                    report = { status(msg("tools_writing"), it) }, validate = {
                                        check(usbEpoch(device.deviceName) == epoch && usb.hasPermission(device)) { "Device changed" }
                                    })
                                recoveryStore.result(job.backup, "verified; boot not checked", job.plan.inputHash)
                            } catch (failure: Exception) {
                                runCatching { recoveryStore.result(job.backup, "failed; write may be incomplete", job.plan.inputHash) }; throw failure
                            }
                        } else {
                            val operation = if (action == "TOOLS_PREPARE")
                                FlashOperation.valueOf(checkNotNull(intent.getStringExtra("operation"))) else null
                            val dataOnly = operation == FlashOperation.RESTORE_DATA ||
                                (action == "TOOLS_READ" && intent.getStringExtra("operation") == "USER_DATA")
                            if (dataOnly) check(info.uid != null) { "Device UID is required" }
                            val before = Recovery.readVerified(flash, ::status)
                            val payload = if (dataOnly) UserDataBackup.capture(before, info.chipKey) else before
                            val backup = recoveryStore.save(payload, flash.flashId, flash.chipKey, info, dataOnly = dataOnly)
                            check(usbEpoch(device.deviceName) == epoch)
                            showTools(backup)
                            if (operation != null) {
                                var savedInfo: DeviceDiagnostics? = null
                                val input = when (operation) {
                                    FlashOperation.MBOOT -> assets.open("fm1-uboot-2.4.boot").use { it.readBytes() }.also {
                                        check(FirmwarePackage.sha256(it) == "677ae52a11721b1811b2688b45f6f0cfae51882810e0a4f4b409da08ce5b49e4")
                                    }
                                    FlashOperation.STOCK_BOOT, FlashOperation.FIRMWARE -> {
                                        val selected = checkNotNull(snapshot.ready) { "Select a downloaded package" }
                                        val loaded = library.load(selected.key, repository.cached())
                                        check(ChipDetector.classify(info.flashId).allows(loaded.first.entry)) { "Package compatibility is unconfirmed" }
                                        library.removalBackup(selected.key).second.also { check(FirmwarePackage.sha256(it) == loaded.second.sha256) }
                                    }
                                    FlashOperation.RESTORE, FlashOperation.RESTORE_BOOT, FlashOperation.RESTORE_DATA -> {
                                        val saved = recoveryStore.list().single { it.id == intent.getStringExtra("sourceBackup") }
                                        check(saved.verified) { "Backup integrity metadata required" }
                                        check(saved.dataOnly == (operation == FlashOperation.RESTORE_DATA)) { "Wrong backup type" }
                                        savedInfo = saved.diagnostics; recoveryStore.load(saved)
                                    }
                                }
                                val plan = FlashWritePlan.create(operation, before, info, input, FirmwarePackage.sha256(input), savedInfo)
                                val token = java.util.UUID.randomUUID().toString()
                                synchronized(this@FlasherService) {
                                    check(usbEpoch(device.deviceName) == epoch)
                                    toolPlan = ToolPlan(token, backup, plan, device.deviceName, device.deviceId, epoch, before.takeIf { dataOnly })
                                    update { it.copy(tools = checkNotNull(it.tools).copy(operation = operation, changed = plan.changedSectors,
                                        target = if (dataOnly) null else FlashLayout.analyze(plan.targetImage(), info.chipKey), token = token,
                                        layoutChanges = plan.layoutChanges)) }
                                }
                            }
                        }
                    }
                }
                status(msg(if (action == "TOOLS_WRITE") "tools_verified" else "tools_ready"), 100)
            } catch (failure: Exception) { status(failureText(failure, R.string.recovery_failed)) }
            finally {
                if (owns) MidiSessionGuard.release()
                if (wake.isHeld) wake.release()
                val backups = recoveryStore.list()
                main.post {
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                    update { it.copy(busy = false, flashing = false, recoveryBackups = backups) }
                    refreshDevices(); checkDeferredRefresh()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecovery(intent: Intent): Int {
        toolPlan = null
        update { it.copy(tools = it.tools?.copy(token = null)) }
        if (!state.experimental) {
            if (!state.busy) stopSelf()
            status(msg(R.string.experimental_disabled))
            return START_NOT_STICKY
        }
        if (state.busy) {
            if (!state.flashing) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            return START_NOT_STICKY
        }
        try { monitor.stop() } catch (failure: Exception) {
            status(failureText(failure, R.string.flash_result_unknown))
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        val snapshot = state
        val action = intent.action
        val plan = snapshot.recoveryPlan
        val usb = getSystemService(UsbManager::class.java)
        val device = usb.deviceList.values.singleOrNull { ChipDetector.supports(it) }
        if (action !in listOf("RECOVERY_BACKUP", "RECOVERY_PREPARE", "RECOVERY_WRITE") || device == null ||
            device.deviceName != intent.getStringExtra("deviceName") || device.deviceId != intent.getIntExtra("deviceId", -1) ||
            !usb.hasPermission(device) ||
            (action == "RECOVERY_PREPARE" && !Recovery.eligible(snapshot.ready)) ||
            (action == "RECOVERY_WRITE" && (plan == null || plan.token != intent.getStringExtra("token") ||
                plan.deviceName != device.deviceName || plan.deviceId != device.deviceId))) {
            status(msg(R.string.recovery_changed)); stopSelf(); return START_NOT_STICKY
        }
        try { startForeground(1, notification(msg(R.string.recovery_starting))) }
        catch (failure: RuntimeException) {
            status(failureText(failure, R.string.flash_result_unknown)); stopSelf(); return START_NOT_STICKY
        }
        update { it.copy(busy = true, flashing = true, recoveryPlan = null) }
        execute {
            val measurement = analytics.begin(AnalyticsOperation.RECOVERY)
            val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fm1:recovery")
            var ownsMidi = false
            try {
                MidiSessionGuard.acquire()
                ownsMidi = true
                wake.acquire(16 * 60_000L)
                status(msg(R.string.recovery_starting))
                val packageId = if (action == "RECOVERY_WRITE") plan!!.packageId else snapshot.ready?.key
                val image = if (action != "RECOVERY_BACKUP") {
                    val loaded = library.load(checkNotNull(packageId), repository.cached()).second
                    val expected = if (action == "RECOVERY_WRITE") plan!!.packageHash else snapshot.ready!!.sha256
                    check(loaded.sha256 == expected) { failed(msg(R.string.local_package_changed)) }
                    Recovery.region(loaded)
                    loaded
                } else null
                UsbBootConnection.open(usb, device, assets).use { flash ->
                    if (action == "RECOVERY_WRITE") {
                        Recovery.restore(flash, recoveryStore, plan!!, image!!, ::status)
                        status(msg(R.string.recovery_complete), 100)
                    } else {
                        val backup = Recovery.backup(flash, recoveryStore, ::status)
                        update { it.copy(recoveryBackups = recoveryStore.list()) }
                        status(msg(R.string.recovery_backup_done, backup.id, msg(ChipDetector.classify(backup.flashId).labelKey),
                            if (Recovery.knownBoot(backup.bootHash)) msg(R.string.recovery_boot_known) else msg(R.string.recovery_boot_unknown)), 100)
                        if (action == "RECOVERY_PREPARE") {
                            val prepared = Recovery.plan(backup, recoveryStore, image!!, device.deviceName, device.deviceId, packageId!!)
                            update { it.copy(recoveryPlan = prepared) }
                            status(msg(R.string.recovery_plan_ready, prepared.changedSectors), 100)
                        }
                    }
                }
                measurement.complete()
            } catch (failure: Exception) {
                measurement.complete(false, analyticsError(failure), AnalyticsStage.PREPARING)
                status(msg(R.string.recovery_failed, failureText(failure, R.string.flash_result_unknown)))
            } finally {
                if (ownsMidi) MidiSessionGuard.release()
                if (wake.isHeld) wake.release()
                val backups = recoveryStore.list()
                main.post {
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                    update { it.copy(busy = false, flashing = false, recoveryBackups = backups) }
                    refreshDevices(); checkDeferredRefresh()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun awaitReconnect(gate: ReconnectGate, loader: Boolean, sessionDeadline: Long): UsbMidi {
        resumePort.clear()
        status(if (loader) msg(R.string.reconnect_loader)
            else msg(R.string.reconnect_final))
        update { it.copy(awaitingPort = true, reconnectCandidates = emptyList(), automaticReconnect = null) }
        val usb = getSystemService(UsbManager::class.java)
        val deadline = minOf(sessionDeadline, System.nanoTime() + TimeUnit.MINUTES.toNanos(3))
        while (System.nanoTime() < deadline) {
            val all = midi.devices()
            val allowed = synchronized(continuityLock) {
                gate.observeUsb(usb.deviceList.keys)
                check(gate.valid(all.map { it.endpoint })) { failed(msg("fleet_continuity_lost")) }
                gate.candidates(all.map { it.endpoint }, usb.deviceList.keys).toSet()
            }
            val candidates = all.filter { it.endpoint in allowed }
            val automatic = synchronized(continuityLock) { gate.automaticCandidate(all.map { it.endpoint }, usb.deviceList.keys) }
            val automaticTarget = candidates.singleOrNull { it.endpoint == automatic }
            if (state.reconnectCandidates.map { it.endpoint } != candidates.map { it.endpoint } ||
                state.automaticReconnect?.endpoint != automatic) {
                update { it.copy(reconnectCandidates = candidates, automaticReconnect = automaticTarget) }
            }
            val approved = resumePort.poll(500, TimeUnit.MILLISECONDS)
            val current = midi.devices()
            val chosen = if (approved != null) current.singleOrNull { it.endpoint == approved }
                else synchronized(continuityLock) { gate.automaticCandidate(current.map { it.endpoint }, usb.deviceList.keys) }?.let { endpoint ->
                    current.singleOrNull { it.endpoint == endpoint && usb.hasPermission(it.usb) }
                }
            if (chosen == null) continue
            check(synchronized(continuityLock) { chosen.endpoint in gate.candidates(current.map { it.endpoint }, usb.deviceList.keys) }) { failed(msg(R.string.reconnect_wrong_port)) }
            check(usb.hasPermission(chosen.usb)) { failed(msg(R.string.reconnect_permission)) }
            if (approved == null) status(msg(R.string.reconnect_automatic, chosen.label))
            update { it.copy(awaitingPort = false, reconnectCandidates = emptyList(), automaticReconnect = null) }
            return chosen
        }
        failed(msg(R.string.reconnect_timeout))
    }

    private fun notification(text: AppText): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, ComposeActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "flash").setSmallIcon(R.drawable.ic_app)
            .setContentTitle(AppLanguage.wrap(this).s(R.string.notification_title))
            .setContentText(AppLanguage.wrap(this).resolve(text)).setContentIntent(open).setOngoing(true).build()
    }

    override fun onDestroy() {
        monitor.stop()
        unregisterReceiver(usbEvents)
        manager?.unregisterDeviceCallback(callback)
        recordEvent("SERVICE_STOPPED")
        worker.shutdownNow()
        listeners.clear()
        super.onDestroy()
    }
}
