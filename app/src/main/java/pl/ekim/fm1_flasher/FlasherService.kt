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
import kotlin.concurrent.withLock
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class UiState(
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
    val chipDetection: AppText? = null,
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
    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArraySet<(UiState) -> Unit>()
    private val resumePort = LinkedBlockingQueue<Endpoint>(1)
    lateinit var monitor: MidiMonitor
        private set
    private lateinit var midi: AndroidMidi
    private lateinit var database: CatalogDatabase
    private lateinit var repository: CatalogRepository
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
            }
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
        state = state.copy(experimental = getSharedPreferences("features", MODE_PRIVATE).getBoolean("experimental", false))
        manager = getSystemService(MidiManager::class.java)
        midi = AndroidMidi(manager, main)
        monitor = MidiMonitor(manager, getSystemService(UsbManager::class.java), main)
        database = CatalogDatabase.get(this)
        repository = CatalogRepository(database)
        recoveryStore = RecoveryStore(File(filesDir, "recovery"))
        library = LocalLibrary(File(filesDir, "library"), database)
        fleet = FleetStore(File(filesDir, "fleet.json"))
        registerReceiver(usbEvents, IntentFilter().apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }, RECEIVER_NOT_EXPORTED)
        journal = FlashJournal(File(filesDir, "flash-session.json"))
        manager?.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, mainExecutor, callback)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("flash", AppLanguage.wrap(this).s(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        refreshDevices()
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        execute {
            try {
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
                if (journal.unresolved() || fleet.jobs.any { it.outcome == JobOutcome.UNCONFIRMED }) status(msg(R.string.flash_interrupted))
                if (fleet.loadFailed) status(msg("fleet_storage_error"))
            } catch (failure: Exception) { status(failure.appMessage(R.string.library_read_error)) }
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
        update { it.copy(status = text, progress = progress, logs = (it.logs + text).takeLast(100)) }
        if (state.flashing) getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    private fun execute(action: () -> Unit) = worker.execute { CatalogOperations.lock.withLock(action) }

    fun refreshDevices() {
        val devices = midi.devices()
        synchronized(continuityLock) { activeGate?.observe(devices.map { it.endpoint }); activeGate?.observeUsb(getSystemService(UsbManager::class.java).deviceList.keys) }
        // Keep transition candidates out of the registry until explicitly bound by the session.
        if (!state.flashing) runCatching { fleet.attach(devices.map { it.endpoint }) }.onFailure { status(it.appMessage(R.string.library_read_error)) }
        update { it.copy(devices = devices, fleet = fleet.devices, jobs = fleet.jobs) }
    }
    private fun publishFleet() { update { it.copy(fleet = fleet.devices, jobs = fleet.jobs) } }
    fun configureDevice(id: String, alias: String? = null, chip: FlashChip? = null) {
        if (state.busy) return
        fleet.configure(id, alias, chip); publishFleet()
    }
    fun pairDevice(oldId: String, liveId: String) {
        if (state.busy) return
        fleet.pair(oldId, liveId); publishFleet()
    }
    fun enqueue(id: String, packageKey: String) {
        if (state.busy) return
        val entry = state.downloads.singleOrNull { it.entry.key == packageKey }?.entry ?: return
        fleet.enqueue(id, entry); publishFleet()
    }
    fun cancelJob(id: String) { if (!state.queueRunning) { fleet.cancel(id); publishFleet() } }
    fun stopQueue() { stopQueue = true; update { it.copy(queueStopping = true) } }


    private fun hasNetwork(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun onAppOpened() {
        uiVisible = true
        if (state.busy) { refreshWhenIdle = true; return }
        refreshWhenIdle = false
        libraryTask {
            if (CatalogRefreshPolicy.shouldRefresh(library.settings().offline, hasNetwork(), database.sources(), System.currentTimeMillis())) repository.load()
            publishLibrary()
            restoreSelection()
        }
    }

    fun setExperimental(enabled: Boolean) {
        if (state.busy) return
        getSharedPreferences("features", MODE_PRIVATE).edit().putBoolean("experimental", enabled).apply()
        if (!enabled) monitor.stop()
        update { it.copy(experimental = enabled, recoveryPlan = null) }
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

    private fun loadCatalog() {
        try {
            val catalog = repository.load()
            update { it.copy(catalog = catalog) }
            restoreSelection()
            val updates = CatalogBrowser.updates(catalog, state.downloads).size
            status(if (catalog.fresh) msg(R.string.catalog_checked, updates)
                else msg(R.string.catalog_stale_local_ready))
        } catch (failure: Exception) { status(failure.appMessage(R.string.catalog_read_error)) }
        finally { finishLibraryTask() }
    }

    fun detectChip(device: android.hardware.usb.UsbDevice) {
        if (!state.experimental || state.busy) return
        monitor.stop()
        update { it.copy(chipDetection = null) }
        detectChipStopped(device)
    }

    private fun detectChipStopped(device: android.hardware.usb.UsbDevice) = libraryTask {
        val id = ChipDetector.detect(getSystemService(UsbManager::class.java), device, assets)
        // ROM enumeration is not evidence that this is the selected MIDI instrument.
        val result = msg(R.string.chip_detected_separate, device.deviceName, "0x%06X".format(id))
        update { it.copy(chipDetection = result) }
        status(result)
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
            try {
                val identity = midi.open(target).use { FlashSession({ _, _ -> failed(msg(R.string.not_applicable)) }, { _, _ -> }).identify(it) }
                check(identity.baseModel == "FM-1") { failed(msg(R.string.wrong_device_identity, identity)) }
                fleet.identify(target.endpoint, identity, runCatching { target.usb.serialNumber }.getOrNull())
                publishFleet()
                update { it.copy(identity = identity, chip = fleet.devices.single { d -> d.endpoint == target.endpoint }.chip) }
                status(msg(R.string.connected_only, target.usb.deviceName, identity))
            } catch (failure: Exception) {
                update { it.copy(target = null, identity = null) }
                status(failure.appMessage(R.string.connect_error))
            } finally { finishLibraryTask() }
        }
    }

    private fun publishLibrary() {
        val settings = library.settings()
        val downloads = library.list()
        update { it.copy(downloads = downloads, favorites = settings.favorites, offline = settings.offline,
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
            catch (failure: Exception) { status(msg(R.string.choose_package_again, failure.appMessage(R.string.local_library_error))) }
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
            catch (failure: Exception) { status(failure.appMessage(R.string.library_change_error)) }
            finally {
                runCatching { publishLibrary() }.onFailure { status(it.appMessage(R.string.local_library_error)) }
                finishLibraryTask(completion)
            }
        }
    }

    fun saveBrowse(values: Map<String, String>) = execute {
        try { database.saveBrowse(values) } catch (e: Exception) { status(e.appMessage(R.string.library_change_error)) }
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

    fun setSourceEnabled(id: String, enabled: Boolean) = libraryTask {
        database.setEnabled(id, enabled)
        if (enabled && !library.settings().offline && hasNetwork()) repository.load(sourceId = id)
        publishLibrary(); restoreSelection()
        if (enabled) sourceResult(id) else status(msg(R.string.source_disabled))
    }

    fun checkSource(id: String) = libraryTask(msg(R.string.checking_catalog)) {
        check(!library.settings().offline) { failed(msg(R.string.offline_refresh_blocked)) }
        check(hasNetwork()) { failed(msg(R.string.no_network_preserved)) }
        repository.load(sourceId = id)
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

    fun removeSource(id: String) = libraryTask { database.removeSource(id); publishLibrary(); restoreSelection() }
    fun restoreSources() = libraryTask { database.restoreDefaults(); refreshWhenIdle = true }
    fun configureSource(id: String?, name: String, url: String, hours: Int, packages: Set<String>, artwork: Set<String>,
                        checkNow: Boolean, finished: (String?, AppText?) -> Unit) {
        if (!libraryReady || state.busy) { finished(null, msg(R.string.library_read_error)); return }
        var savedId: String? = null
        var failure: AppText? = null
        libraryTask(completion = { finished(savedId, failure) }) {
            try {
                val saved = database.configure(id, name, url, hours, packages, artwork)
                savedId = saved.id
                if (checkNow) {
                    if (!library.settings().offline && hasNetwork()) repository.load(sourceId = saved.id)
                    else status(msg(R.string.source_check_offline))
                }
                publishLibrary(); restoreSelection()
            } catch (e: Exception) { failure = e.appMessage(R.string.source_invalid_config); throw e }
        }
    }

    fun toggleFavorite(id: String) = libraryTask {
        val settings = library.settings()
        val favorites = if (id in settings.favorites) settings.favorites - id else settings.favorites + id
        library.saveSettings(settings.copy(favorites = favorites))
    }

    fun useLocal(id: String) = libraryTask(msg(R.string.checking_local_package)) {
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

    fun undoRemove() = libraryTask {
        val removed = removedPackage ?: return@libraryTask
        check(!FleetPolicy.pinned(fleet.jobs, removed.first.key) && library.list().none { it.entry.key == removed.first.key }) { failed(msg("fleet_package_pinned")) }
        library.save(removed.first, removed.second)
        removedPackage = null; update { it.copy(undoAvailable = false) }
        status(msg("fleet_restored"))
    }

    private fun storeDownload(entry: CatalogEntry) {
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
        val catalog = repository.load()
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
            catch (failure: Exception) { errors += AppText.Joined(listOf(entryTitleText(entry), failure.appMessage(R.string.local_library_error)), ": ") }
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
                        check(saved.entry.sha256 == job.hash && job.chip.allows(saved.entry)) { failed(msg("local_package_changed")) }
                    }, run = { job ->
                        check(FleetPolicy.ready(job, fleet.devices.singleOrNull { it.id == job.deviceId })) { failed(msg("fleet_plan_changed")) }
                        runJob(job)
                    })
                } catch (failure: Exception) {
                    status(msg(R.string.flash_unconfirmed, failure.appMessage(R.string.flash_result_unknown)))
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
            status(failure.appMessage(R.string.flash_result_unknown)); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun runJob(job: FlashJob) {
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fm1:flash")
        var ownsMidi = false
        try {
            val target = midi.devices().singleOrNull { it.endpoint == job.endpoint } ?: failed(msg("fleet_plan_changed"))
            check(getSystemService(UsbManager::class.java).hasPermission(target.usb)) { failed(msg("reconnect_permission")) }
            MidiSessionGuard.acquire(); ownsMidi = true; wake.acquire(16 * 60_000L)
            val (saved, firmware) = library.load(job.packageKey, repository.cached())
            check(saved.entry.sha256 == job.hash && job.chip.allows(saved.entry)) { failed(msg("local_package_changed")) }
            fleet.result(job.id, JobOutcome.RUNNING, "flash_preparing"); activeJob = job.id; publishFleet()
            update { it.copy(target = target, identity = job.expected, chip = job.chip, progress = -1) }
            journal.record(job.hash, job.deviceId, "preparing")
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FlashSession.MAX_SESSION_MS)
            synchronized(continuityLock) { activeGate = ReconnectGate(target.endpoint, midi.devices().map { it.endpoint }, getSystemService(UsbManager::class.java).deviceList.keys) }
            val sessionPaths = mutableSetOf(target.endpoint.usbPath)
            val originalSerial = fleet.devices.single { it.id == job.deviceId }.serial
            val session = FlashSession(reconnect = { _, loader ->
                fleet.result(job.id, JobOutcome.RUNNING, if (loader) "reconnect_loader" else "reconnect_final"); publishFleet()
                val chosen = awaitReconnect(checkNotNull(activeGate), loader, deadline)
                val serial = runCatching { chosen.usb.serialNumber }.getOrNull()
                check(originalSerial.isNullOrBlank() || serial.isNullOrBlank() || originalSerial == serial) { failed(msg("fleet_serial_changed")) }
                synchronized(continuityLock) {
                    activeGate!!.observeUsb(getSystemService(UsbManager::class.java).deviceList.keys)
                    check(activeGate!!.valid(midi.devices().map { it.endpoint })) { failed(msg("fleet_continuity_lost")) }
                    sessionPaths += chosen.endpoint.usbPath
                    activeGate = ReconnectGate(chosen.endpoint, midi.devices().map { it.endpoint }, getSystemService(UsbManager::class.java).deviceList.keys, sessionPaths.toSet())
                }
                fleet.rebind(job.deviceId, chosen.endpoint); publishFleet()
                update { it.copy(target = chosen, identity = null) }
                midi.open(chosen)
            }, report = { text, progress ->
                val phase = (text as? AppText.Resource)?.key ?: "flash_preparing"
                fleet.result(job.id, JobOutcome.RUNNING, phase, progress); publishFleet()
                journal.record(job.hash, job.deviceId, phase)
                status(text, progress)
            }, validate = { endpoint ->
                synchronized(continuityLock) {
                    val current = midi.devices().map { it.endpoint }
                    activeGate?.observeUsb(getSystemService(UsbManager::class.java).deviceList.keys)
                    check(endpoint in current && activeGate?.valid(current) == true) { failed(msg("fleet_continuity_lost")) }
                }
            })
            val installed = session.flash(firmware, midi.open(target), job.expected)
            journal.record(job.hash, job.deviceId, "finished", "confirmed")
            fleet.rebind(job.deviceId, checkNotNull(state.target).endpoint, installed)
            fleet.result(job.id, JobOutcome.CONFIRMED, "fleet_confirmed", 100); publishFleet()
            update { it.copy(identity = installed) }
        } catch (failure: Exception) {
            val reason = (failure.appMessage(R.string.flash_result_unknown) as? AppText.Resource)?.takeIf { it.arguments.isEmpty() }?.key ?: "fleet_unconfirmed"
            fleet.result(job.id, JobOutcome.UNCONFIRMED, reason); publishFleet()
            throw failure
        } finally {
            activeJob = null
            synchronized(continuityLock) { activeGate = null }
            if (ownsMidi) MidiSessionGuard.release()
            if (wake.isHeld) wake.release()
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
        check(state.experimental) { failed(msg(R.string.experimental_disabled)) }
        val backup = recoveryStore.list().single { it.id == id }
        val output = checkNotNull(contentResolver.openOutputStream(uri, "wt"))
        output.use { recoveryStore.export(backup, it) }
        status(msg(R.string.recovery_exported))
    }

    private fun startRecovery(intent: Intent): Int {
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
            status(failure.appMessage(R.string.flash_result_unknown))
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
            status(failure.appMessage(R.string.flash_result_unknown)); stopSelf(); return START_NOT_STICKY
        }
        update { it.copy(busy = true, flashing = true, recoveryPlan = null) }
        execute {
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
                        status(msg(R.string.recovery_backup_done, backup.id, "0x%06X".format(backup.flashId),
                            if (Recovery.knownBoot(backup.bootHash)) msg(R.string.recovery_boot_known) else msg(R.string.recovery_boot_unknown)), 100)
                        if (action == "RECOVERY_PREPARE") {
                            val prepared = Recovery.plan(backup, recoveryStore, image!!, device.deviceName, device.deviceId, packageId!!)
                            update { it.copy(recoveryPlan = prepared) }
                            status(msg(R.string.recovery_plan_ready, prepared.changedSectors), 100)
                        }
                    }
                }
            } catch (failure: Exception) {
                status(msg(R.string.recovery_failed, failure.appMessage(R.string.flash_result_unknown)))
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
        worker.shutdownNow()
        listeners.clear()
        super.onDestroy()
    }
}
