package pl.ekim.fm1_flasher

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.view.WindowInsetsController
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Compose is the application UI; Android owns USB grants, document pickers and service lifetime. */
class ComposeActivity : ComponentActivity(), AppBackend {
    override val state = MutableStateFlow(AppSnapshot())
    override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try { coverLock.withLock {
            val catalog = state.value.catalog ?: return@withLock null
            val source = catalog.source(entry) ?: return@withLock null
            val url = entry.cardUrl ?: return@withLock null
            if (!source.permitsArtwork(url)) return@withLock null
            val directory = File(filesDir, "previews-v2").apply { mkdirs() }
            val file = File(directory, FirmwarePackage.sha256("${source.id}:${source.authRevision}:${source.provider}:${source.authMethod}:${source.authUsername}:${source.authHeader}:${source.oauth}:$url".toByteArray()) + ".img")
            val legacy = File(directory, FirmwarePackage.sha256(url.toByteArray()) + ".img")
            var bytes = file.takeIf { it.length() in 1..2 * 1024 * 1024 }?.readBytes()
                ?: legacy.takeIf { source.authMethod == HttpAuthMethod.NONE && source.authRevision == 0L && it.length() in 1..2 * 1024 * 1024 }?.readBytes()
            var image = bytes?.let { decodeAndroidArtwork(it) }
            if (image == null && source.active && !state.value.offline) {
                val authScope = CatalogProviders.authenticationScope(source)
                val credentials = if (UrlScope.contains(authScope, url)) OAuthManager(SourceCredentialStore(this@ComposeActivity)).credentials(source) else null
                val response = SourceNetwork.request(url, 2 * 1024 * 1024, source::permitsArtwork, active = {
                    !state.value.offline && state.value.catalog?.sources?.any { it.id == source.id && it.active && it.revision == source.revision } == true
                }, authentication = { address -> if (UrlScope.contains(authScope, address)) credentials?.headers().orEmpty() else emptyMap() })
                if (response.code != 200) service?.recordEvent("ARTWORK_ERROR", "${source.id} · HTTP ${response.code}", "ERROR")
                check(response.code == 200)
                bytes = response.bytes; image = decodeAndroidArtwork(bytes)
                if (image != null) { LocalLibrary.atomicWrite(file, bytes); trimPreviewCache(directory) }
            }
            image
        } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { service?.recordFailure(failure); null }
    }
    private val coverLock = java.util.concurrent.locks.ReentrantLock()
    private var service: FlasherService? = null
    private var bound = false
    private var visible = false
    private data class UsbRequest(val device: UsbDevice, val epoch: Long, val token: String, val action: () -> Unit)
    private var pendingUsb: UsbRequest? = null
    private val usbRequests = java.util.ArrayDeque<UsbRequest>()
    private var automaticPermission: Endpoint? = null
    private var pendingOAuthCallback: Uri? = null
    private var exportOperationLogUri: Uri? = null
    private var exportBackupId: String? = null
    private var exportBackupUri: Uri? = null
    private var logRevision = -1L
    private val prefs get() = getSharedPreferences("browse", MODE_PRIVATE)
    private val permissionAction get() = "$packageName.COMPOSE_USB_PERMISSION"
    private fun port(device: UsbMidi) = AppPort("${device.info.id}:${device.usb.deviceName}:${device.generation}", device.label)
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    private val observer: (UiState) -> Unit = { next ->
        state.value = state.value.copy(analyticsAvailable = AndroidAnalytics.get(this).available, analyticsConsent = AndroidAnalytics.get(this).consent, undoAvailable = next.undoAvailable, fleetAvailable = true, fleet = next.fleet, jobs = next.jobs,
            queueRunning = next.queueRunning, queueStopping = next.queueStopping, catalog = next.catalog, downloads = next.downloads, favorites = next.favorites,
            offline = next.offline, backgroundSync = next.backgroundSync, backgroundAvailable = true, busy = next.busy, flashing = next.flashing, experimental = next.experimental,
            ports = next.devices.map(::port), target = next.target?.let(::port), identity = next.identity, chip = next.chip, chipDetection = next.chipDetection,
            ready = next.ready, awaitingPort = next.awaitingPort, candidates = next.reconnectCandidates.map(::port),
            progress = next.progress, status = next.status, logs = next.logs, usbRecovery = true,
            backups = next.recoveryBackups.map { BackupRow(it.id, "${it.id} · 0x%06X".format(it.flashId)) },
            recovery = next.recoveryPlan?.let { RecoveryView(it.token, msg(R.string.recovery_confirmation, it.deviceName,
                "0x%06X".format(it.backup.flashId), it.changedSectors, it.backup.id, it.backup.sha256)) })
        refreshLogger()
        if (!next.awaitingPort) automaticPermission = null
        next.automaticReconnect?.let { target ->
            if (visible && pendingUsb == null && automaticPermission != target.endpoint && getSystemService(UsbManager::class.java).deviceList.keys.singleOrNull() == target.usb.deviceName) {
                automaticPermission = target.endpoint; withPermission(target.usb) {}
            }
        }
        dispatchBackupExport()
        dispatchOperationLogExport()
        dispatchOAuthCallback()
    }
    private val binding = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as FlasherService.LocalBinder).service
            service?.observe(observer)
            if (visible) service?.onAppOpened()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; state.value = state.value.copy(busy = true, status = msg(R.string.service_stopped)) }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val pending = pendingUsb ?: return
            if (intent.getStringExtra("request") != pending.token) return
            pendingUsb = null
            val usb = getSystemService(UsbManager::class.java)
            val device = usb.deviceList[pending.device.deviceName]
            if (device != null && device == pending.device && service?.usbEpoch(device.deviceName) == pending.epoch && usb.hasPermission(device)) {
                service?.recordEvent("USB_PERMISSION_GRANTED", device.deviceName)
                runCatching(pending.action).onFailure { service?.recordFailure(it); showFailure(it.appMessage(R.string.usb_permission_denied)) }
            } else {
                service?.recordEvent("USB_PERMISSION_DENIED_OR_STALE", pending.device.deviceName, "WARN")
                showFailure(msg(R.string.usb_permission_denied))
            }
            dispatchUsbRequest()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureOAuthCallback(intent)
        exportOperationLogUri = savedInstanceState?.getString("exportOperationLogUri")?.let(Uri::parse)
        exportBackupId = savedInstanceState?.getString("exportBackupId")
        exportBackupUri = savedInstanceState?.getString("exportBackupUri")?.let(Uri::parse)
        state.value = state.value.copy(initialized = true, language = resources.configuration.locales[0].language.let { if (it in listOf("pl", "zh")) it else "en" },
            appearance = prefs.getString("appearance", "SYSTEM") ?: "SYSTEM", browse = BrowseFilters(
                prefs.getString("query", "").orEmpty(), runCatching { FirmwareCategory.valueOf(prefs.getString("category", "ALL")!!) }.getOrDefault(FirmwareCategory.ALL),
                runCatching { FirmwareSort.valueOf(prefs.getString("sort", "NAME")!!) }.getOrDefault(FirmwareSort.NAME), prefs.all["downloaded"].toString().toBoolean(), prefs.all["favorites"].toString().toBoolean(), prefs.getString("source", null)?.takeIf { it.isNotBlank() }))
        window.setDecorFitsSystemWindows(false)
        registerReceiver(receiver, IntentFilter(permissionAction).apply { addDataScheme("fm1-permission") }, RECEIVER_NOT_EXPORTED)
        setContent {
            val snapshot by state.collectAsState()
            val dark = snapshot.appearance == "DARK" || (snapshot.appearance == "SYSTEM" && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES))
            SideEffect {
                val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)
            }
            FlasherApp(this)
        }
        bound = bindService(Intent(this, FlasherService::class.java), binding, BIND_AUTO_CREATE)
    }
    private fun captureOAuthCallback(incoming: Intent?) {
        incoming?.data?.takeIf { it.scheme == "pl.ekim.multiplatform-fm1-flasher" }?.let {
            pendingOAuthCallback = it
            incoming.data = null
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); captureOAuthCallback(intent); dispatchOAuthCallback() }
    private fun dispatchOAuthCallback() {
        val active = service ?: return
        if (active.state.busy) return
        val uri = pendingOAuthCallback ?: return
        pendingOAuthCallback = null
        active.finishOAuth(uri)
    }
    override fun onStart() { super.onStart(); visible = true; service?.onAppOpened() }
    override fun onStop() { visible = false; if (!isChangingConfigurations) service?.onAppClosed(); super.onStop() }
    override fun onDestroy() {
        service?.unobserve(observer)
        if (bound) unbindService(binding)
        unregisterReceiver(receiver)
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportOperationLogUri", exportOperationLogUri?.toString())
        outState.putString("exportBackupId", exportBackupId); outState.putString("exportBackupUri", exportBackupUri?.toString())
        super.onSaveInstanceState(outState)
    }
    override fun saveBrowse(filters: BrowseFilters) {
        service?.saveBrowse(mapOf("query" to filters.query, "category" to filters.category.name, "sort" to filters.sort.name, "downloaded" to filters.downloadedOnly.toString(), "favorites" to filters.favoritesOnly.toString(), "source" to filters.sourceId.orEmpty()))
        prefs.edit().putString("source", filters.sourceId.orEmpty()).putString("query", filters.query).putString("category", filters.category.name).putString("sort", filters.sort.name)
            .putBoolean("downloaded", filters.downloadedOnly).putBoolean("favorites", filters.favoritesOnly).apply()
    }
    override fun document(name: String): String = runCatching { assets.open("docs/$name").bufferedReader().use { it.readText() } }.getOrElse { getString(R.string.about_document_unavailable) }
    private fun showFailure(text: AppText) {
        service?.recordEvent("UI_STATUS", resolve(text))
        state.value = state.value.copy(status = text)
    }
    override fun screen(screen: AnalyticsScreen) { AndroidAnalytics.get(this).screen(screen) }
    override fun perform(action: AppAction) {
        if (action.kind == AppActionKind.ANALYTICS_CONSENT) {
            runCatching { AndroidAnalytics.get(this).decide(AnalyticsConsent.valueOf(action.id)) }
                .onFailure { showFailure(msg("analytics_save_failed")) }
            state.value = state.value.copy(analyticsConsent = AndroidAnalytics.get(this).consent)
            return
        }
        try { dispatch(action) } catch (failure: Exception) { service?.recordFailure(failure); showFailure(failure.appMessage(R.string.library_change_error)) }
    }
    private fun dispatch(action: AppAction) {
        val active = service ?: return
        val next = active.state
        active.recordAction(action)
        when (action.kind) {
            AppActionKind.LOG_CLEAR -> active.clearOperationLog()
            AppActionKind.LOG_EXPORT -> if (next.experimental && !next.busy) startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE, "fm1-operations-${System.currentTimeMillis()}.jsonl"), 62)
            AppActionKind.ANALYTICS_CONSENT -> Unit
            AppActionKind.CLEAR_DOWNLOADS -> active.clearDownloads()
            AppActionKind.UNDO_REMOVE -> active.undoRemove()
            AppActionKind.DEVICE_ALIAS -> active.configureDevice(action.id, alias = action.value)
            AppActionKind.DEVICE_CHIP -> active.configureDevice(action.id, chip = FlashChip.valueOf(action.value))
            AppActionKind.DEVICE_PAIR -> active.pairDevice(action.id, action.value)
            AppActionKind.QUEUE_ADD -> active.enqueue(action.id, action.value)
            AppActionKind.QUEUE_REMOVE -> active.cancelJob(action.id)
            AppActionKind.QUEUE_STOP -> active.stopQueue()
            AppActionKind.QUEUE_START -> {
                if (next.busy || action.id != FleetPolicy.token(next.jobs)) return
                foreground(Intent(this, FlasherService::class.java).setAction("QUEUE").putExtra("plan", action.id))
            }
            AppActionKind.BACKGROUND_SYNC -> active.setBackgroundSync(action.id.toBooleanStrict())
            AppActionKind.SOURCE_ENABLE -> active.setSourceEnabled(action.id, action.value.toBooleanStrict())
            AppActionKind.SOURCE_CHECK -> active.checkSource(action.id)
            AppActionKind.SOURCE_REMOVE -> active.removeSource(action.id)
            AppActionKind.SOURCE_RESTORE -> active.restoreSources()
            AppActionKind.AUTH_LOGIN -> active.beginOAuth(action.id) { address -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))) }
            AppActionKind.SOURCE_SAVE -> {
                val config = action.source ?: return
                state.value = state.value.copy(sourceError = null, savedSourceId = null)
                active.configureSource(action.id.ifEmpty { null }, config.name, config.url, config.hours, config.packages, config.artwork, false, config.provider, config.authentication, config.packageChips) { savedId, failure ->
                    state.value = state.value.copy(sourceError = failure, savedSourceId = savedId)
                    failure?.let(::showFailure)
                }
            }
            AppActionKind.PORTS_REFRESH -> active.refreshDevices()
            AppActionKind.REFRESH -> active.refreshCatalog()
            AppActionKind.OFFLINE -> active.setOffline(action.id.toBooleanStrict())
            AppActionKind.FAVORITE -> active.toggleFavorite(action.id)
            AppActionKind.DOWNLOAD -> next.catalog?.entries?.singleOrNull { it.key == action.id }?.let(active::download)
            AppActionKind.USE -> active.useLocal(action.id)
            AppActionKind.REMOVE -> active.removeLocal(action.id)
            AppActionKind.UPDATE_ALL -> active.updateDownloads()
            AppActionKind.CONNECT -> next.devices.singleOrNull { port(it).id == action.id }?.let { target -> withPermission(target.usb) { if (active.state.devices.any { it.endpoint == target.endpoint }) active.connect(target.info.id) } }
            AppActionKind.CHIP -> active.setChip(FlashChip.valueOf(action.id))
            AppActionKind.RECONNECT -> next.reconnectCandidates.singleOrNull { port(it).id == action.id }?.let { target -> withPermission(target.usb) { active.resumeWith(target.endpoint) } }
            AppActionKind.FLASH -> {
                val target = next.target ?: return
                if (port(target).id != action.id || next.ready?.sha256 != action.value || next.busy) return
                withPermission(target.usb) {
                    if (!active.state.busy && active.state.target?.endpoint == target.endpoint && active.state.ready?.sha256 == action.value) foreground(Intent(this, FlasherService::class.java).setAction("FLASH"))
                }
            }
            AppActionKind.EXPERIMENTAL -> active.setExperimental(action.id.toBooleanStrict())
            AppActionKind.LOGGER_REFRESH -> refreshLogger()
            AppActionKind.LOGGER_TOGGLE -> { active.toggleMonitor(); refreshLogger() }
            AppActionKind.LOGGER_CLEAR -> { active.monitor.log.clear(); refreshLogger() }
            AppActionKind.LOGGER_PORT -> {
                if (!next.experimental || next.busy || active.monitor.running) return
                val target = active.monitor.ports().singleOrNull { it.key == action.id } ?: return
                if (target.key in active.monitor.selected) { active.monitor.selected.remove(target.key); refreshLogger() }
                else withPermission(target.usb) { if (!active.state.busy && active.state.experimental && !active.monitor.running) { active.monitor.selected.add(target.key); refreshLogger() } }
            }
            AppActionKind.LOGGER_EXPORT -> if (next.experimental) startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE, "midi-log-${System.currentTimeMillis()}.txt"), 41)
            AppActionKind.DETECT -> if (next.experimental && !next.busy) {
                val devices = getSystemService(UsbManager::class.java).deviceList.values.filter(ChipDetector::supports)
                if (devices.size != 1) showFailure(msg(R.string.detect_chip_help)) else withPermission(devices.single()) { active.detectChip(devices.single()) }
            }
            AppActionKind.RECOVERY_ENTER -> next.target?.takeIf { port(it).id == action.id && !next.busy && next.experimental }?.let { target -> withPermission(target.usb) { if (active.state.target?.endpoint == target.endpoint) active.enterDownloadMode() } }
            AppActionKind.RECOVERY_BACKUP, AppActionKind.RECOVERY_PREPARE, AppActionKind.RECOVERY_WRITE -> startRecovery(action)
            AppActionKind.RECOVERY_EXPORT -> if (next.experimental && !next.busy && next.recoveryBackups.any { it.id == action.id }) {
                exportBackupId = action.id
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, "fm1-backup-${action.id}.zip"), 61)
            }
            AppActionKind.LANGUAGE -> {
                require(action.id in listOf("en", "pl", "zh")); state.value = state.value.copy(language = action.id)
                AppLanguage.choose(this, if (action.id == "zh") "zh-Hans" else action.id)
            }
            AppActionKind.APPEARANCE -> {
                require(action.id in listOf("SYSTEM", "LIGHT", "DARK")); prefs.edit().putString("appearance", action.id).apply()
                state.value = state.value.copy(appearance = action.id)
            }
            AppActionKind.OPEN_LINK -> {
                val uri = Uri.parse(action.id); require(uri.scheme in listOf("https", "http", "mailto"))
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }
    private fun refreshLogger() {
        val monitor = service?.monitor ?: return
        val snapshot = monitor.log.snapshotIfChanged(logRevision)
        if (snapshot != null) logRevision = snapshot.revision
        state.value = state.value.copy(monitorPorts = monitor.ports().map { AppPort(it.key, it.label) }, monitorSelected = monitor.selected.toSet(),
            monitorRunning = monitor.running, monitorDraining = monitor.draining,
            monitorRows = snapshot?.entries?.map { LogRow(it.sequence, it.source, it.channel, it.type, it.line(true)) } ?: state.value.monitorRows,
            monitorStatus = snapshot?.let { AppText.Joined(listOf(msg(R.string.logger_status, it.total, it.evicted, it.lostBytes), msg(if (monitor.running) R.string.logger_running else R.string.logger_stopped)), "\n") }
                ?: msg(if (monitor.running) R.string.logger_running else R.string.logger_stopped))
    }
    private fun withPermission(device: UsbDevice, action: () -> Unit) {
        val active = service ?: return
        val usb = getSystemService(UsbManager::class.java)
        val request = UsbRequest(device, active.usbEpoch(device.deviceName), java.util.UUID.randomUUID().toString(), action)
        if (usb.deviceList[device.deviceName] != device) { showFailure(msg(R.string.usb_port_gone)); return }
        if (usb.hasPermission(device)) { action(); return }
        if (pendingUsb?.let { it.device == device && it.epoch == request.epoch } == true ||
            usbRequests.any { it.device == device && it.epoch == request.epoch }) return
        usbRequests.add(request)
        dispatchUsbRequest()
    }
    private fun dispatchUsbRequest() {
        if (pendingUsb != null) return
        val usb = getSystemService(UsbManager::class.java)
        while (usbRequests.isNotEmpty()) {
            val request = usbRequests.removeFirst()
            if (usb.deviceList[request.device.deviceName] != request.device || service?.usbEpoch(request.device.deviceName) != request.epoch) continue
            if (usb.hasPermission(request.device)) { request.action(); continue }
            pendingUsb = request
            service?.recordEvent("USB_PERMISSION_REQUESTED", request.device.deviceName)
            usb.requestPermission(request.device, PendingIntent.getBroadcast(this, request.token.hashCode(),
                Intent(permissionAction).setPackage(packageName).setData(Uri.parse("fm1-permission:${request.token}"))
                    .putExtra("request", request.token), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT))
            return
        }
    }
    private fun foreground(intent: Intent) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("notificationAsked", false)) {
            prefs.edit().putBoolean("notificationAsked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
        }
        startForegroundService(intent)
    }
    private fun startRecovery(action: AppAction) {
        val active = service ?: return
        if (active.state.busy || !active.state.experimental) return
        if (action.kind == AppActionKind.RECOVERY_PREPARE && !Recovery.eligible(active.state.ready)) { showFailure(msg(R.string.recovery_v15_only)); return }
        val plan = active.state.recoveryPlan
        if (action.kind == AppActionKind.RECOVERY_WRITE && plan?.token != action.id) { showFailure(msg(R.string.recovery_no_plan)); return }
        val device = getSystemService(UsbManager::class.java).deviceList.values.filter(ChipDetector::supports).singleOrNull()
        if (device == null) { showFailure(msg(R.string.recovery_help)); return }
        withPermission(device) {
            if (!active.state.busy && active.state.experimental) foreground(Intent(this, FlasherService::class.java).setAction(action.kind.name)
                .putExtra("deviceName", device.deviceName).putExtra("deviceId", device.deviceId).putExtra("token", plan?.token))
        }
    }
    private fun dispatchOperationLogExport() {
        val active = service ?: return
        val uri = exportOperationLogUri ?: return
        // Returning from DocumentsUI starts a library refresh before the result arrives.
        if (active.state.busy) return
        exportOperationLogUri = null
        if (active.state.experimental) active.exportOperationLog(uri)
        else showFailure(msg(R.string.experimental_disabled))
    }
    private fun dispatchBackupExport() {
        val active = service ?: return; val id = exportBackupId ?: return; val uri = exportBackupUri ?: return
        if (active.state.busy) return
        exportBackupId = null; exportBackupUri = null; active.exportRecovery(id, uri)
    }
    @Deprecated("Platform document picker API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 62) {
            exportOperationLogUri = if (resultCode == RESULT_OK) data?.data else null
            dispatchOperationLogExport()
        }
        if (requestCode == 61) { exportBackupUri = if (resultCode == RESULT_OK) data?.data else null; if (exportBackupUri == null) exportBackupId = null; dispatchBackupExport() }
        if (requestCode == 41 && resultCode == RESULT_OK && service?.state?.experimental == true) {
            val snapshot = service!!.monitor.log.snapshot(); val uri = data?.data ?: return
            Thread {
                val result = runCatching { checkNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use(snapshot::writeTo) }
                runOnUiThread { showFailure(msg(if (result.isSuccess) R.string.logger_saved else R.string.logger_save_failed)) }
            }.start()
        }
    }
}
