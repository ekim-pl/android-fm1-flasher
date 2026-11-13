package pl.fm1.flasher

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
    val status: String = "Ładowanie katalogu…",
    val busy: Boolean = true,
    val flashing: Boolean = false,
    val progress: Int = -1,
    val awaitingPort: Boolean = false,
    val reconnectCandidates: List<UsbMidi> = emptyList(),
    val logs: List<String> = emptyList(),
)

class FlasherService : Service() {
    inner class LocalBinder : Binder() { val service get() = this@FlasherService }
    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArraySet<(UiState) -> Unit>()
    private val resumePort = LinkedBlockingQueue<Int>(1)
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
        repository = CatalogRepository(filesDir)
        library = LocalLibrary(File(filesDir, "library"))
        manager?.registerDeviceCallback(callback, main)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("flash", "Aktualizacja FM‑1", NotificationManager.IMPORTANCE_LOW))
        refreshDevices()
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        worker.execute {
            try {
                publishLibrary()
                val cached = repository.cached()
                if (cached != null) {
                    update { it.copy(catalog = cached) }
                    status("Biblioteka gotowa. Pobrane paczki działają bez internetu.")
                } else status("Brak lokalnego katalogu. Połącz się z internetem, aby pobrać listę i paczki.")
                restoreSelection()
            } catch (failure: Exception) { status(failure.message ?: "Nie można odczytać biblioteki.") }
            finally { finishLibraryTask() }
        }
    }

    override fun onBind(intent: Intent) = binder
    fun observe(listener: (UiState) -> Unit) { listeners += listener; listener(state) }
    fun unobserve(listener: (UiState) -> Unit) { listeners -= listener }

    @Synchronized private fun update(transform: (UiState) -> UiState) {
        state = transform(state)
        val snapshot = state
        main.post { listeners.forEach { it(snapshot) } }
    }

    private fun status(text: String, progress: Int = -1) {
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

    fun onAppClosed() { uiVisible = false; refreshWhenIdle = false }

    private fun finishLibraryTask() {
        update { it.copy(busy = false, refreshingCatalog = false) }
        stopSelf()
        checkDeferredRefresh()
    }

    private fun checkDeferredRefresh() {
        main.post {
            if (uiVisible && refreshWhenIdle) { refreshWhenIdle = false; onAppOpened() }
        }
    }

    fun refreshCatalog() {
        if (state.busy) return
        if (state.offline) { status("Wyłącz tryb offline, aby sprawdzić katalog i aktualizacje."); return }
        if (!hasNetwork()) { status("Brak dostępu do internetu. Zachowano katalog i lokalne paczki."); return }
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        update { it.copy(busy = true, refreshingCatalog = true) }
        status("Sprawdzanie katalogu firmware…")
        worker.execute { loadCatalog() }
    }

    private fun loadCatalog() {
        try {
            val catalog = repository.load()
            update { it.copy(catalog = catalog) }
            restoreSelection()
            val updates = CatalogBrowser.updates(catalog, state.downloads).size
            status(if (catalog.fresh) "Katalog sprawdzony · $updates aktualizacji pobranych paczek. Źródło odświeżamy najwyżej raz na dobę."
                else "Brak aktualnego katalogu online. Lokalne paczki nadal są dostępne do flashowania.")
        } catch (failure: Exception) { status(failure.message ?: "Nie można odczytać katalogu.") }
        finally { finishLibraryTask() }
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
                val identity = midi.open(target).use { FlashSession({ _, _ -> error("Nie dotyczy") }, { _, _ -> }).identify(it) }
                check(identity.baseModel == "FM-1") { "Wybrany port zgłasza $identity, a nie FM‑1." }
                update { it.copy(identity = identity) }
                status("Połączono wyłącznie z ${target.usb.deviceName} · $identity")
            } catch (failure: Exception) {
                update { it.copy(target = null, identity = null) }
                status(failure.message ?: "Nie udało się połączyć z FM‑1.")
            } finally { finishLibraryTask() }
        }
    }

    private fun publishLibrary() {
        val settings = library.settings()
        val downloads = library.list()
        update { it.copy(downloads = downloads, favorites = settings.favorites, offline = settings.offline) }
        if (library.problems.isNotEmpty()) status(library.problems.joinToString(" "))
    }

    private fun restoreSelection() {
        update { it.copy(ready = null) }
        val selected = library.settings().selectedId
        if (selected != null) {
            try {
                val ready = library.load(selected, state.catalog).first.entry
                update { it.copy(ready = ready) }
            }
            catch (failure: Exception) { status("Wybierz paczkę ponownie: ${failure.message}") }
        }
    }

    private fun libraryTask(message: String? = null, action: () -> Unit) {
        if (state.busy) return
        startService(Intent(this, FlasherService::class.java).setAction("LIBRARY"))
        update { it.copy(busy = true) }
        if (message != null) status(message)
        worker.execute {
            try { action() }
            catch (failure: Exception) { status(failure.message ?: "Nie udało się zmienić biblioteki.") }
            finally {
                runCatching { publishLibrary() }.onFailure { status(it.message ?: "Błąd lokalnej biblioteki.") }
                finishLibraryTask()
            }
        }
    }

    fun setOffline(offline: Boolean) = libraryTask {
        library.saveSettings(library.settings().copy(offline = offline))
        refreshWhenIdle = !offline
        status(if (offline) "Tryb offline · używaj paczek zapisanych na telefonie." else "Tryb online · możesz sprawdzić katalog i pobrać aktualizacje.")
    }

    fun toggleFavorite(id: String) = libraryTask {
        val settings = library.settings()
        val favorites = if (id in settings.favorites) settings.favorites - id else settings.favorites + id
        library.saveSettings(settings.copy(favorites = favorites))
    }

    fun useLocal(id: String) = libraryTask("Sprawdzanie lokalnej paczki…") {
        val saved = library.load(id, state.catalog).first
        library.saveSettings(library.settings().copy(selectedId = id))
        update { it.copy(ready = saved.entry) }
        status("${saved.entry.name} ${saved.entry.version} · lokalny pakiet sprawdzony i wybrany do flashowania.")
    }

    fun removeLocal(id: String) = libraryTask {
        library.remove(id)
        if (state.ready?.id == id) update { it.copy(ready = null) }
        status("Paczka usunięta z telefonu. Ulubione pozostają zapisane.")
    }

    private fun storeDownload(entry: CatalogEntry) {
        check(!state.offline) { "Wyłącz tryb offline, aby pobierać paczki." }
        library.save(entry, repository.download(entry))
        if (state.ready?.id == entry.id) {
            library.saveSettings(library.settings().copy(selectedId = null))
            update { it.copy(ready = null) }
        }
    }

    fun download(entry: CatalogEntry) = libraryTask("Pobieranie ${entry.name} ${entry.version}…") {
        storeDownload(entry)
        status("${entry.name} ${entry.version} · zapisano na telefonie. Paczka gotowa do użycia offline.")
    }

    fun updateDownloads() = libraryTask("Sprawdzanie aktualizacji lokalnych paczek…") {
        check(!state.offline) { "Wyłącz tryb offline, aby zaktualizować lokalne paczki." }
        val catalog = repository.load()
        update { it.copy(catalog = catalog) }
        check(catalog.fresh) { "Nie można pobrać aktualnego katalogu. Dotychczasowe paczki pozostają dostępne offline." }
        restoreSelection()
        val updates = CatalogBrowser.updates(catalog, library.list())
        val errors = mutableListOf<String>()
        var completed = 0
        updates.forEachIndexed { index, entry ->
            status("Aktualizacja paczek ${index + 1}/${updates.size} · ${entry.name}…")
            try { storeDownload(entry); completed++ }
            catch (failure: Exception) { errors += "${entry.name}: ${failure.message}" }
        }
        status(if (updates.isEmpty()) "Pobrane paczki są zgodne z ostatnim znanym katalogiem."
            else "Zaktualizowano $completed/${updates.size} paczek." + if (errors.isEmpty()) "" else " Zachowano stare pliki dla: ${errors.joinToString("; ")}")
    }

    fun resumeWith(id: Int) {
        if (state.awaitingPort && state.reconnectCandidates.any { it.info.id == id }) resumePort.offer(id)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != "FLASH") return START_NOT_STICKY
        startForeground(1, notification("Przygotowanie aktualizacji wybranego FM‑1…"))
        if (state.busy) {
            if (!state.flashing) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            return START_NOT_STICKY
        }
        val snapshot = state
        val target = snapshot.target
        val entry = snapshot.ready
        val identity = snapshot.identity
        if (target == null || entry == null || identity == null || !snapshot.chip.allows(entry) ||
            midi.devices().none { it.endpoint == target.endpoint } ||
            !getSystemService(UsbManager::class.java).hasPermission(target.usb)) {
            status("Brakuje wybranego FM‑1, uprawnień USB lub zgodnego pakietu.")
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        update { it.copy(busy = true, flashing = true) }
        worker.execute {
            val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fm1:flash")
            try {
                wake.acquire(15 * 60_000L)
                // Starting a flash never performs a network request, including with an expired catalog.
                val (saved, firmware) = library.load(entry.id, repository.cached())
                check(saved.entry.sha256 == entry.sha256) { "Lokalna paczka zmieniła się. Wybierz ją ponownie przed flashowaniem." }
                check(snapshot.chip.allows(saved.entry)) { "Lokalna wersja nie obsługuje wybranej kości flash." }
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
                status("Nie potwierdzono aktualizacji: ${failure.message ?: failure.javaClass.simpleName}")
            } finally {
                if (wake.isHeld) wake.release()
                update { it.copy(busy = false, flashing = false, awaitingPort = false, reconnectCandidates = emptyList()) }
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
        status(if (loader) "FM‑1 restartuje się. Wskaż jego nowy port, aby kontynuować zapis."
            else "Zapis zakończony. Wskaż port uruchomionego FM‑1, aby sprawdzić jego tożsamość.")
        update { it.copy(awaitingPort = true, reconnectCandidates = emptyList()) }
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
        while (System.nanoTime() < deadline) {
            val all = midi.devices()
            val allowed = gate.candidates(all.map { it.endpoint }).toSet()
            val candidates = all.filter { it.endpoint in allowed }
            if (state.reconnectCandidates.map { it.endpoint } != candidates.map { it.endpoint }) {
                update { it.copy(reconnectCandidates = candidates) }
            }
            val id = resumePort.poll(500, TimeUnit.MILLISECONDS) ?: continue
            val chosen = midi.devices().singleOrNull { it.info.id == id } ?: continue
            check(chosen.endpoint in gate.candidates(midi.devices().map { it.endpoint })) { "Wskazany port zmienił się lub należy do innego urządzenia z huba." }
            check(getSystemService(UsbManager::class.java).hasPermission(chosen.usb)) { "Nie udzielono zgody na nowy port USB." }
            update { it.copy(awaitingPort = false, reconnectCandidates = emptyList()) }
            return chosen
        }
        error("Nie wskazano nowego portu wybranego FM‑1 w ciągu 3 minut. Pozostałe urządzenia nie zostały wybrane.")
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "flash").setSmallIcon(R.drawable.ic_app)
            .setContentTitle("FM‑1 · aktualizacja wybranego urządzenia")
            .setContentText(text).setContentIntent(open).setOngoing(true).build()
    }

    override fun onDestroy() {
        manager?.unregisterDeviceCallback(callback)
        worker.shutdownNow()
        listeners.clear()
        super.onDestroy()
    }
}
