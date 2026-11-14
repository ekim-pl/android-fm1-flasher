package pl.ekim.fm1_flasher

import android.app.*
import android.content.Intent
import android.hardware.usb.UsbManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class UiState(
    val devices: List<UsbMidi> = emptyList(),
    val target: UsbMidi? = null,
    val identity: FirmwareIdentity? = null,
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
    private val resumePort = LinkedBlockingQueue<Int>(1)
    lateinit var monitor: MidiMonitor
        private set
    private lateinit var midi: AndroidMidi
    private lateinit var repository: CatalogRepository
    private lateinit var library: LocalLibrary
    private var manager: MidiManager? = null
    @Volatile private var uiVisible = false
    @Volatile private var refreshWhenIdle = false
    @Volatile var state = UiState()
        private set

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) = refreshDevices()
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            monitor.removed(info.id)
            refreshDevices()
            if (!state.flashing && state.target?.info?.id == info.id) {
                update { it.copy(target = null, identity = null, chip = FlashChip.UNKNOWN) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(MidiManager::class.java)
        midi = AndroidMidi(manager, main)
        monitor = MidiMonitor(manager, getSystemService(UsbManager::class.java), main)
        repository = CatalogRepository(filesDir)
        library = LocalLibrary(File(filesDir, "library"))
        manager?.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, mainExecutor, callback)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("flash", AppLanguage.wrap(this).s(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
        refreshDevices()
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        worker.execute {
            try {
                publishLibrary()
                val cached = repository.cached()
                if (cached != null) {
                    update { it.copy(catalog = cached) }
                    status(msg(R.string.library_ready))
                } else status(msg(R.string.catalog_missing))
                restoreSelection()
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
        update { it.copy(status = text, progress = progress, logs = (it.logs + text).takeLast(100)) }
        if (state.flashing) getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    fun refreshDevices() { update { it.copy(devices = midi.devices()) } }

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
        val now = System.currentTimeMillis()
        state.catalog?.let { cached ->
            val fresh = now >= cached.fetchedAt && now - cached.fetchedAt < CatalogRepository.DAY
            if (fresh != cached.fresh) update { it.copy(catalog = cached.copy(fresh = fresh)) }
        }
        if (CatalogRefreshPolicy.shouldRefresh(state.offline, hasNetwork(), state.catalog?.fetchedAt, now)) refreshCatalog()
    }

    fun toggleMonitor() {
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

    private fun finishLibraryTask() {
        update { it.copy(busy = false, refreshingCatalog = false) }
        if (!monitor.running) stopSelf()
        checkDeferredRefresh()
    }

    private fun checkDeferredRefresh() {
        main.post {
            if (uiVisible && refreshWhenIdle) { refreshWhenIdle = false; onAppOpened() }
        }
    }

    fun refreshCatalog() {
        if (state.busy) return
        if (state.offline) { status(msg(R.string.offline_refresh_blocked)); return }
        if (!hasNetwork()) { status(msg(R.string.no_network_preserved)); return }
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        update { it.copy(busy = true, refreshingCatalog = true) }
        status(msg(R.string.checking_catalog))
        worker.execute { loadCatalog() }
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
        monitor.stop()
        detectChipStopped(device)
    }

    private fun detectChipStopped(device: android.hardware.usb.UsbDevice) = libraryTask {
        val id = ChipDetector.detect(getSystemService(UsbManager::class.java), device, assets)
        val chip = ChipDetector.classify(id)
        update { it.copy(chip = chip) }
        status(msg(R.string.chip_detected, "0x%06X".format(id)))
    }

    fun setChip(chip: FlashChip) {
        if (!state.busy) update { it.copy(chip = chip) }
    }

    fun connect(id: Int) {
        if (state.busy) return
        val target = midi.devices().singleOrNull { it.info.id == id } ?: return
        update { it.copy(busy = true, target = target, identity = null, chip = FlashChip.UNKNOWN) }
        worker.execute {
            try {
                val identity = midi.open(target).use { FlashSession({ _, _ -> failed(msg(R.string.not_applicable)) }, { _, _ -> }).identify(it) }
                check(identity.baseModel == "FM-1") { failed(msg(R.string.wrong_device_identity, identity)) }
                update { it.copy(identity = identity) }
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
        update { it.copy(downloads = downloads, favorites = settings.favorites, offline = settings.offline) }
        if (library.problems.isNotEmpty()) status(AppText.Joined(library.problems.toList()))
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

    private fun libraryTask(message: AppText? = null, action: () -> Unit) {
        if (state.busy) return
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        update { it.copy(busy = true) }
        if (message != null) status(message)
        worker.execute {
            try { action() }
            catch (failure: Exception) { status(failure.appMessage(R.string.library_change_error)) }
            finally {
                runCatching { publishLibrary() }.onFailure { status(it.appMessage(R.string.local_library_error)) }
                finishLibraryTask()
            }
        }
    }

    fun setOffline(offline: Boolean) = libraryTask {
        library.saveSettings(library.settings().copy(offline = offline))
        refreshWhenIdle = !offline
        status(if (offline) msg(R.string.offline_enabled) else msg(R.string.online_enabled))
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
        library.remove(id)
        if (state.ready?.id == id) update { it.copy(ready = null) }
        status(msg(R.string.package_removed))
    }

    private fun storeDownload(entry: CatalogEntry) {
        check(!state.offline) { failed(msg(R.string.download_offline_blocked)) }
        library.save(entry, repository.download(entry))
        if (state.ready?.id == entry.id) {
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
        check(catalog.fresh) { failed(msg(R.string.catalog_update_unavailable)) }
        restoreSelection()
        val updates = CatalogBrowser.updates(catalog, library.list())
        val errors = mutableListOf<AppText>()
        var completed = 0
        updates.forEachIndexed { index, entry ->
            status(msg(R.string.updating_package, index + 1, updates.size, entryTitleText(entry)))
            try { storeDownload(entry); completed++ }
            catch (failure: Exception) { errors += AppText.Joined(listOf(entryTitleText(entry), failure.appMessage(R.string.local_library_error)), ": ") }
        }
        status(if (updates.isEmpty()) msg(R.string.packages_current)
            else AppText.Joined(listOf(msg(R.string.packages_updated, completed, updates.size)) +
                if (errors.isEmpty()) emptyList() else listOf(msg(R.string.old_packages_kept, AppText.Joined(errors, "; ")))))
    }

    fun resumeWith(id: Int) {
        if (state.awaitingPort && state.reconnectCandidates.any { it.info.id == id }) resumePort.offer(id)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != "FLASH") return START_NOT_STICKY
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
        val target = snapshot.target
        val entry = snapshot.ready
        val identity = snapshot.identity
        if (target == null || entry == null || identity == null || !snapshot.chip.allows(entry) ||
            midi.devices().none { it.endpoint == target.endpoint } ||
            !getSystemService(UsbManager::class.java).hasPermission(target.usb)) {
            status(msg(R.string.flash_missing_requirements))
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(1, notification(msg(R.string.flash_preparing)))
        } catch (failure: RuntimeException) {
            status(failure.appMessage(R.string.flash_result_unknown))
            stopSelf()
            return START_NOT_STICKY
        }
        update { it.copy(busy = true, flashing = true) }
        worker.execute {
            val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fm1:flash")
            try {
                wake.acquire(15 * 60_000L)
                // Starting a flash never performs a network request, including with an expired catalog.
                val (saved, firmware) = library.load(entry.id, repository.cached())
                check(saved.entry.sha256 == entry.sha256) { failed(msg(R.string.local_package_changed)) }
                check(snapshot.chip.allows(saved.entry)) { failed(msg(R.string.local_chip_unsupported)) }
                var before = midi.devices().map { it.endpoint }
                val session = FlashSession(reconnect = { previous, loader ->
                    val chosen = awaitReconnect(ReconnectGate(previous, before), loader)
                    before = midi.devices().map { it.endpoint }
                    update { it.copy(target = chosen, identity = null) }
                    midi.open(chosen)
                }, report = ::status)
                val installed = session.flash(firmware, midi.open(target), identity)
                update { it.copy(identity = installed) }
            } catch (failure: Exception) {
                status(msg(R.string.flash_unconfirmed, failure.appMessage(R.string.flash_result_unknown)))
            } finally {
                if (wake.isHeld) wake.release()
                update { it.copy(busy = false, flashing = false, awaitingPort = false, reconnectCandidates = emptyList(), automaticReconnect = null) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                refreshDevices()
                checkDeferredRefresh()
            }
        }
        return START_NOT_STICKY
    }

    private fun awaitReconnect(gate: ReconnectGate, loader: Boolean): UsbMidi {
        resumePort.clear()
        status(if (loader) msg(R.string.reconnect_loader)
            else msg(R.string.reconnect_final))
        update { it.copy(awaitingPort = true, reconnectCandidates = emptyList(), automaticReconnect = null) }
        val usb = getSystemService(UsbManager::class.java)
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
        while (System.nanoTime() < deadline) {
            val all = midi.devices()
            val allowed = gate.candidates(all.map { it.endpoint }).toSet()
            val candidates = all.filter { it.endpoint in allowed }
            val automatic = gate.automaticCandidate(all.map { it.endpoint }, usb.deviceList.keys)
            val automaticTarget = candidates.singleOrNull { it.endpoint == automatic }
            if (state.reconnectCandidates.map { it.endpoint } != candidates.map { it.endpoint } ||
                state.automaticReconnect?.endpoint != automatic) {
                update { it.copy(reconnectCandidates = candidates, automaticReconnect = automaticTarget) }
            }
            val id = resumePort.poll(500, TimeUnit.MILLISECONDS)
            val current = midi.devices()
            val chosen = if (id != null) current.singleOrNull { it.info.id == id }
                else gate.automaticCandidate(current.map { it.endpoint }, usb.deviceList.keys)?.let { endpoint ->
                    current.singleOrNull { it.endpoint == endpoint && usb.hasPermission(it.usb) }
                }
            if (chosen == null) continue
            check(chosen.endpoint in gate.candidates(current.map { it.endpoint })) { failed(msg(R.string.reconnect_wrong_port)) }
            check(usb.hasPermission(chosen.usb)) { failed(msg(R.string.reconnect_permission)) }
            if (id == null) status(msg(R.string.reconnect_automatic, chosen.label))
            update { it.copy(awaitingPort = false, reconnectCandidates = emptyList(), automaticReconnect = null) }
            return chosen
        }
        failed(msg(R.string.reconnect_timeout))
    }

    private fun notification(text: AppText): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "flash").setSmallIcon(R.drawable.ic_app)
            .setContentTitle(AppLanguage.wrap(this).s(R.string.notification_title))
            .setContentText(AppLanguage.wrap(this).resolve(text)).setContentIntent(open).setOngoing(true).build()
    }

    override fun onDestroy() {
        monitor.stop()
        manager?.unregisterDeviceCallback(callback)
        worker.shutdownNow()
        listeners.clear()
        super.onDestroy()
    }
}
