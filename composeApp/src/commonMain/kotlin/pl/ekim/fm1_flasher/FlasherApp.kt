package pl.ekim.fm1_flasher

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FlasherApp(backend: AppBackend) {
    val state by backend.state.collectAsState()
    if (!state.initialized) {
        MaterialTheme { Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(24.dp)) { CircularProgressIndicator() } } }
        return
    }
    val language = state.language.substringBefore('-').let { if (it == "zh") "zh" else it }
    fun text(key: String, vararg arguments: Any) = resolveCoreText(msg(key, *arguments), language)
    fun send(kind: AppActionKind, id: String = "", value: String = "") = backend.perform(AppAction(kind, id, value))
    var tabName by rememberSaveable { mutableStateOf(LibraryTab.CATALOG.name) }
    var workspaceInitialized by rememberSaveable { mutableStateOf(false) }
    val tab = runCatching { LibraryTab.valueOf(tabName) }.getOrDefault(LibraryTab.CATALOG)
    var settings by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<Pair<String, AppText>?>(null) }
    var confirmation by remember { mutableStateOf<Pair<AppAction, AppText>?>(null) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var settingsPrivacy by rememberSaveable { mutableStateOf(false) }
    var recovery by rememberSaveable { mutableStateOf(false) }
    val screen = when {
        settingsPrivacy -> AnalyticsScreen.PRIVACY
        sources -> AnalyticsScreen.SOURCES
        settings -> AnalyticsScreen.SETTINGS
        recovery -> AnalyticsScreen.RECOVERY
        detailId != null -> AnalyticsScreen.DETAIL
        else -> AnalyticsScreen.valueOf(tab.name)
    }
    LaunchedEffect(screen, state.analyticsPreference, state.offline) { backend.screen(screen) }
    val settingsScroll = rememberScrollState()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var sourceFilter by rememberSaveable { mutableStateOf(state.browse.sourceId.orEmpty()) }
    var query by rememberSaveable { mutableStateOf(state.browse.query) }
    var categoryName by rememberSaveable { mutableStateOf(state.browse.category.name) }
    var sortName by rememberSaveable { mutableStateOf(state.browse.sort.name) }
    var downloadedOnly by rememberSaveable { mutableStateOf(state.browse.downloadedOnly) }
    var favoritesOnly by rememberSaveable { mutableStateOf(state.browse.favoritesOnly) }
    fun openDocumentOrLink(url: String) {
        val name = url.removePrefix("../")
        when {
            url == "app://library" -> { settings = false; sources = false; tabName = LibraryTab.CATALOG.name }
            url == "app://devices" -> { settings = false; sources = false; tabName = (if (state.fleetAvailable) LibraryTab.DEVICES else LibraryTab.FLASHER).name }
            url == "app://tasks" -> { settings = false; sources = false; tabName = (if (state.fleetAvailable) LibraryTab.TASKS else LibraryTab.FLASHER).name }
            url == "app://settings/sources" -> { settings = true; sources = true }
            name == "PRIVACY_POLICY.md" -> dialog = text("privacy_title") to msg("privacy_body")
            name == "LICENSE.md" || name.startsWith("third_party/") -> dialog = name to AppText.Plain(backend.document(name))
            url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:") -> send(AppActionKind.OPEN_LINK, url)
        }
    }
    LaunchedEffect(state.chipDetection) {
        state.chipDetection?.let { dialog = text("chip_result_title") to it }
    }
    val filters = BrowseFilters(query, FirmwareCategory.valueOf(categoryName), FirmwareSort.valueOf(sortName), downloadedOnly, favoritesOnly, sourceFilter.ifEmpty { null })
    LaunchedEffect(filters) { backend.saveBrowse(filters) }
    LaunchedEffect(state.experimental) { if (!state.experimental && tab == LibraryTab.LOGGER) tabName = LibraryTab.CATALOG.name }
    val dark = state.appearance == "DARK" || (state.appearance == "SYSTEM" && isSystemInDarkTheme())
    LaunchedEffect(state.fleetAvailable) {
        if (state.fleetAvailable && !workspaceInitialized) { tabName = LibraryTab.DEVICES.name; workspaceInitialized = true }
    }
    val tabs = if (state.fleetAvailable) listOf(LibraryTab.DEVICES, LibraryTab.CATALOG, LibraryTab.TASKS)
        else LibraryTab.entries.filter { it !in listOf(LibraryTab.DEVICES, LibraryTab.TASKS) && (it != LibraryTab.LOGGER || state.experimental) }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF64D8CB), secondary = Color(0xFFA3CCC3), secondaryContainer = Color(0xFF284E48), onSecondaryContainer = Color(0xFFC5EDE4), background = Color(0xFF10161D), surface = Color(0xFF151D26), surfaceContainer = Color(0xFF202B36)) else lightColorScheme(primary = Color(0xFF006B61), secondary = Color(0xFF3F665D), secondaryContainer = Color(0xFFD0E8E1), onSecondaryContainer = Color(0xFF12372F), background = Color(0xFFF3F6F8), surface = Color(0xFFFAFCFE), surfaceContainer = Color(0xFFE8EFF2))) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val wide = maxWidth >= 600.dp
                when {
                    sources -> SourceSettings(state, language, backend) { sources = false }
                    settingsPrivacy -> SettingsPage(text("privacy_title"), text("back"), { settingsPrivacy = false }) {
                        Text(text("privacy_body"))
                    }
                    settings -> SettingsScreen(state, language, settingsScroll, backend::perform,
                        { settings = false }, { sources = true }, { settingsPrivacy = true },
                        { action, message -> confirmation = action to message },
                        { destination -> settings = false; tabName = destination.name })
                    else -> Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text("app_name"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { settings = true }) { Text(text("settings")) }
                    }
                    Row(Modifier.weight(1f)) {
                        if (wide) NavigationRail {
                            tabs.forEach { item -> NavigationRailItem(selected = tab == item, onClick = { focus.clearFocus(); tabName = item.name },
                                icon = { Text(item.icon) }, label = { Text(text(if (state.fleetAvailable && item == LibraryTab.CATALOG) "fleet_library" else item.labelKey)) }) }
                        }
                        Column(Modifier.weight(1f)) {
                            if (state.busy || state.progress >= 0) {
                                if (state.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                            }
                            Text(resolveCoreText(state.status, language), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                            if (state.undoAvailable) Row(Modifier.padding(horizontal = 16.dp)) {
                                TextButton({ send(AppActionKind.UNDO_REMOVE) }, enabled = !state.busy) { Text(text("fleet_undo_remove")) }
                            }
                            when (tab) {
                                LibraryTab.DEVICES -> FleetScreen(state, language, backend::perform, { tabName = LibraryTab.CATALOG.name }, { tabName = LibraryTab.TASKS.name }, { tabName = LibraryTab.FLASHER.name })
                                LibraryTab.TASKS -> TaskScreen(state, language, backend::perform, { tabName = LibraryTab.DEVICES.name })
                                LibraryTab.CATALOG, LibraryTab.FAVORITES, LibraryTab.DOWNLOADS -> {
                                    val entries = remember(state.catalog, state.downloads, state.favorites, filters, tab) { CatalogBrowser.entries(state.catalog, state.downloads, state.favorites, filters, tab) }
                                    PullToRefreshBox(isRefreshing = state.busy && !state.flashing, onRefresh = { if (!state.busy) send(AppActionKind.REFRESH) }, modifier = Modifier.weight(1f)) {
                                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            item {
                                                Text(text(when(tab) { LibraryTab.FAVORITES -> "favorites_title"; LibraryTab.DOWNLOADS -> "downloads_title"; else -> "discover_title" }), style = MaterialTheme.typography.headlineLarge)
                                                Text(text("results_count", entries.size), style = MaterialTheme.typography.bodySmall)
                                                OutlinedTextField(query, { query = it }, label = { Text(text("search_hint")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                if (filtersExpanded) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Choice(text("filter_category"), text(filters.category.labelKey), FirmwareCategory.entries.map { it.name to text(it.labelKey) }, { categoryName = it }, Modifier.weight(1f))
                                                    Choice(text("filter_sort"), text(filters.sort.labelKey), FirmwareSort.entries.map { it.name to text(it.labelKey) }, { sortName = it }, Modifier.weight(1f))
                                                }
                                                if (state.catalog?.sources?.isNotEmpty() == true) Choice(text("source_filter"), state.catalog?.sources?.firstOrNull { it.id == sourceFilter }?.name ?: text("source_all"), listOf("" to text("source_all")) + state.catalog!!.sources.filter { !it.deleted }.map { it.id to it.name }, { sourceFilter = it })
                                                }
                                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                                    FilterChip(downloadedOnly, { downloadedOnly = !downloadedOnly }, label = { Text(text("downloaded_only")) })
                                                    Spacer(Modifier.width(8.dp))
                                                    FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text(text("favorites_only")) })
                                                }
                                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                                    TextButton({ filtersExpanded = !filtersExpanded }) { Text(text("fleet_filters", (if (categoryName != "ALL") 1 else 0) + (if (sourceFilter.isNotEmpty()) 1 else 0))) }
                                                    TextButton({ send(AppActionKind.REFRESH) }, enabled = !state.busy && !state.offline) { Text(text("refresh_button")) }
                                                    if (tab == LibraryTab.DOWNLOADS || downloadedOnly) TextButton({ send(AppActionKind.UPDATE_ALL) }, enabled = !state.busy && !state.offline) { Text(text("updates_count", CatalogBrowser.updates(state.catalog, state.downloads).size)) }
                                                }
                                                Text(state.catalog?.let { (if (state.offline) text("catalog_offline_prefix") else if (!it.fresh) text("catalog_cached_prefix") else text("catalog_prefix")) + it.updated } ?: text("catalog_offline"), style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (entries.isEmpty()) item { Text(text("empty_title")); Text(text(when(tab) { LibraryTab.DOWNLOADS -> "empty_downloads"; LibraryTab.FAVORITES -> "empty_favorites"; else -> "empty_filter" })) }
                                            items(entries, key = { it.key }) { entry ->
                                                OutlinedCard(
                                                    onClick = { focus.clearFocus(); detailId = entry.key },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                ) {
                                                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color(
                                                        (state.sourceColors[entry.sourceId] ?: SourceColor.defaultFor(entry.sourceId)).argb)))
                                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        SourceBadge(entry, state)
                                                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Text(title(entry, language), style = MaterialTheme.typography.titleLarge)
                                                                if (entry.author.isNotBlank()) Text(entry.author,
                                                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                            val favorite = entry.key in state.favorites
                                                            // Do not observe the whole app state from semantics on recyclable nodes.
                                                            val favoriteDescription = text(if (favorite) "remove_favorite_accessibility" else "add_favorite_accessibility", title(entry, language))
                                                            IconToggleButton(checked = favorite, onCheckedChange = { send(AppActionKind.FAVORITE, entry.key) },
                                                                enabled = !state.busy, modifier = Modifier.semantics { contentDescription = favoriteDescription }) {
                                                                Text(if (favorite) "★" else "☆", fontSize = 28.sp)
                                                            }
                                                        }
                                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            (listOf(entry.version).filter { it.isNotBlank() } + entry.kinds.map { kind ->
                                                                text(FirmwareCategory.entries.firstOrNull { it.key == kind }?.labelKey ?: "category_other")
                                                            }).forEach { label ->
                                                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                                                                    Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                                                                }
                                                            }
                                                        }
                                                        Cover(entry, state.offline, backend, state.catalog?.source(entry), compact = true)
                                                        val description = if (entry.stock) text("stock_description") else entry.shortDescription
                                                        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        val saved = state.downloads.singleOrNull { it.entry.key == entry.key }
                                                        if (saved != null) {
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                            Text(text("saved_version", saved.entry.version) + if (state.catalog?.entries?.any { it.key == entry.key && it.sha256 != saved.entry.sha256 } == true) text("update_badge") else "",
                                                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                }
                                            }
                                            item {
                                                val shownSources = state.catalog?.sources.orEmpty().filter { it.active && (sourceFilter.isEmpty() || it.id == sourceFilter) }
                                                if (shownSources.isEmpty() || shownSources.any { it.id == CatalogSource.HUB }) {
                                                    TextButton({ send(AppActionKind.OPEN_LINK, "https://creativecommons.org/licenses/by/4.0/") }) { Text(text("catalog_license")) }
                                                }
                                                shownSources.filter { it.id != CatalogSource.HUB }.forEach { source ->
                                                    TextButton({ send(AppActionKind.OPEN_LINK, if (source.id == "awesome-fm1") "https://github.com/ekim-pl/awesome-fm1" else source.url) }) { Text("${source.name} ↗") }
                                                    if (source.license.isNotBlank()) Text(source.license, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                                LibraryTab.FLASHER -> FlasherScreen(state, language, { send(it.kind, it.id, it.value) }, { action, message -> confirmation = action to message }, { recovery = true }, { dialog = text("app_log") to AppText.Joined(state.logs, "\n\n") })
                                LibraryTab.LOGGER -> LoggerScreen(state, language, backend)
                                LibraryTab.ABOUT -> AboutScreen(
                                    backend.document(text("about_document")), text("app_name"), text("about_version", "0.9.5-experimental.30"),
                                    if (!state.usbRecovery) text("ios_platform_limits") else null,
                                    text("privacy_title"), text("about_privacy_summary"), text("about_privacy_action"),
                                    { dialog = text("privacy_title") to msg("privacy_body") }, ::openDocumentOrLink)
                            }
                        }
                    }
                    if (!wide) NavigationBar {
                        tabs.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { focus.clearFocus(); tabName = item.name },
                            icon = { Text(item.icon) }, label = { Text(text(if (state.fleetAvailable && item == LibraryTab.CATALOG) "fleet_library" else item.labelKey), fontSize = 10.sp, maxLines = 1) }) }
                    }
                }
                }
            }
        }
        val entry = (if (tab == LibraryTab.DOWNLOADS) state.downloads.map { it.entry } + state.catalog?.entries.orEmpty() else state.catalog?.entries.orEmpty() + state.downloads.map { it.entry }).firstOrNull { it.key == detailId }
        if (entry != null) {
            val saved = state.downloads.singleOrNull { it.entry.key == entry.key }
            val latest = state.catalog?.entries?.singleOrNull { it.key == entry.key }
            val retired = state.catalog?.removed(entry) == true
            AppDialog(title(entry, language), { detailId = null }, text("close")) {
                Cover(entry, state.offline, backend, state.catalog?.source(entry))
                Text("${entry.author} · ${entry.version}")
                SourceBadge(entry, state)
                MarkdownText(if (entry.stock) text("stock_description") else entry.description) { send(AppActionKind.OPEN_LINK, it) }
                if (retired) Text(text("package_retired"), color = MaterialTheme.colorScheme.error)
                if (saved != null) Text(text("local_version", saved.entry.version, "${saved.bytes / 1024} KiB"))
                TextButton({ send(AppActionKind.FAVORITE, entry.key) }, enabled = !state.busy) { Text(text(if (entry.key in state.favorites) "favorite_remove" else "favorite_add")) }
                Button({ send(AppActionKind.DOWNLOAD, latest!!.key) }, enabled = !state.busy && !state.offline && latest != null && state.catalog?.canDownload(latest) == true) { Text(text(if (saved != null) "download_again_version" else "download_version", latest?.version ?: entry.version)) }
                if (saved != null) {
                    Button({ send(AppActionKind.USE, entry.key); detailId = null; tabName = if (state.fleetAvailable) LibraryTab.DEVICES.name else LibraryTab.FLASHER.name }, enabled = !state.busy && !retired) { Text(text("use_to_flash")) }
                    TextButton({ send(AppActionKind.REMOVE, entry.key); detailId = null }, enabled = !state.busy && !FleetPolicy.pinned(state.jobs, entry.key)) { Text(text("remove_local")) }
                }
                TextButton({ send(AppActionKind.OPEN_LINK, entry.sourceUrl) }) { Text(text("author_repository")) }
                Text(text("package_technical", entry.version, entry.product, text(when(entry.chip) { "new" -> "chip_both"; "old" -> "chip_older_only"; else -> "chip_unconfirmed" }), entry.sha256), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (recovery) AppDialog(text("recovery_title"), { recovery = false }, text("close")) {
            Text(text("recovery_help"))
            if (state.usbRecovery && state.experimental) {
                Button({ confirmation = AppAction(AppActionKind.RECOVERY_ENTER, state.target?.id.orEmpty()) to msg("recovery_enter_confirm", state.target?.label.orEmpty()) }, enabled = !state.busy && state.identity?.isLoader == false) { Text(text("recovery_enter")) }
                Button({ send(AppActionKind.RECOVERY_BACKUP) }, enabled = !state.busy) { Text(text("recovery_backup")) }
                Button({ send(AppActionKind.RECOVERY_PREPARE) }, enabled = !state.busy) { Text(text("recovery_prepare")) }
                state.recovery?.let { plan -> Button({ confirmation = AppAction(AppActionKind.RECOVERY_WRITE, plan.token) to plan.confirmation }, enabled = !state.busy) { Text(text("recovery_write_plan")) } }
                state.backups.forEach { backup -> TextButton({ send(AppActionKind.RECOVERY_EXPORT, backup.id) }, enabled = !state.busy) { Text("${text("logger_export")} · ${backup.label}") } }
            }
        }
        dialog?.let { (heading, message) -> AppDialog(heading, { dialog = null }, text("close")) { Text(resolveCoreText(message, language)) } }
        confirmation?.let { (action, message) -> AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(text(if (action.kind == AppActionKind.FLASH) "flash_confirm_title" else if (action.kind == AppActionKind.REMOVE) "remove_confirm_title" else if (action.kind == AppActionKind.CLEAR_DOWNLOADS) "downloads_clear" else if (action.kind == AppActionKind.LOG_CLEAR) "operation_log_clear" else "recovery_title")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(resolveCoreText(message, language)) } },
            confirmButton = { TextButton({ confirmation = null; backend.perform(action) }, enabled = !state.busy) { Text(text(when(action.kind) { AppActionKind.FLASH -> "install_this_device"; AppActionKind.REMOVE -> "remove_package"; AppActionKind.CLEAR_DOWNLOADS -> "downloads_clear"; AppActionKind.LOG_CLEAR -> "operation_log_clear"; AppActionKind.RECOVERY_ENTER -> "recovery_enter"; else -> "recovery_write" })) } },
            dismissButton = { TextButton({ confirmation = null }) { Text(text("cancel")) } }) }
    }
}

private fun title(entry: CatalogEntry, language: String) = if (entry.stock) resolveCoreText(msg("stock_name"), language) else entry.name

@Composable internal fun Choice(label: String, current: String, choices: List<Pair<String, String>>, select: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(current) } }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { (id, value) -> DropdownMenuItem(text = { Text(value) }, onClick = { expanded = false; select(id) }) } }
    }
}
@Composable private fun AppDialog(title: String, dismiss: () -> Unit, close: String, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }, confirmButton = { TextButton(dismiss) { Text(close) } })
}
@Composable private fun FlasherScreen(state: AppSnapshot, language: String, send: (AppAction) -> Unit, confirm: (AppAction, AppText) -> Unit, recovery: () -> Unit, logs: () -> Unit) {
    fun text(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    if (state.fleetAvailable) {
        DeviceToolsScreen(state, language, send, confirm, recovery, logs)
        return
    }
    var forceDialog by remember(state.ready, state.target, state.chip, state.identity) { mutableStateOf(false) }
    var port by rememberSaveable { mutableStateOf("") }
    var reconnect by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(text("flasher_heading"), style = MaterialTheme.typography.headlineLarge) }
        if (state.awaitingPort) item {
            Text(text("reconnect_heading")); Text(text(if (state.usbRecovery) "reconnect_help" else "ios_reconnect"))
            Choice(text("select_reconnect_port"), state.candidates.firstOrNull { it.id == reconnect }?.label ?: text("select_reconnect_port"), state.candidates.map { it.id to it.label }, { reconnect = it })
            Button({ send(AppAction(AppActionKind.RECONNECT, reconnect)) }, enabled = state.candidates.any { it.id == reconnect }) { Text(text("resume_port")) }
        }
        item {
            Text(text("select_device_heading"), style = MaterialTheme.typography.titleMedium)
            TextButton({ send(AppAction(AppActionKind.PORTS_REFRESH)) }, enabled = !state.busy) { Text(text("refresh_ports")) }
            Choice(text("select_device_accessibility"), state.ports.firstOrNull { it.id == port }?.label ?: text("select_port"), state.ports.map { it.id to it.label }, { port = it }, enabled = !state.busy)
            Button({ send(AppAction(AppActionKind.CONNECT, port)) }, enabled = !state.busy && state.ports.any { it.id == port }) { Text(text("connect_selected")) }
            Text(state.target?.let { text("connected_identity", it.label, state.identity ?: text("identity_waiting")) } ?: text("connect_otg", state.ports.size))
            Choice(text("flash_chip_label"), text(state.chip.labelKey), FlashChip.entries.map { it.name to text(it.labelKey) }, { send(AppAction(AppActionKind.CHIP, it)) }, enabled = !state.busy && state.identity != null)
            Text(text("flash_chip_help"), style = MaterialTheme.typography.bodySmall)
            if (state.experimental && state.usbRecovery) {
                TextButton({ send(AppAction(AppActionKind.DETECT)) }, enabled = !state.busy) { Text(text("detect_chip")) }
                TextButton(recovery, enabled = !state.busy) { Text(text("recovery_title")) }
            }
        }
        item {
            Text(text("local_package_heading"), style = MaterialTheme.typography.titleMedium)
            Choice(text("select_downloaded_firmware"), state.ready?.let { "${title(it, language)} · ${it.version}" } ?: text("no_package_selected"), state.downloads.map { it.entry.key to "${title(it.entry, language)} · ${it.entry.version}" }, { send(AppAction(AppActionKind.USE, it)) }, enabled = !state.busy)
            Text(text(if (state.ready == null) "choose_downloaded_help" else if (state.chip.allows(state.ready)) "saved_ready" else "chip_not_supported"))
            val ready = state.ready; val target = state.target
            if (state.fleetAvailable) Text(text("fleet_legacy_queue")) else Button({ if (ready != null && target != null) confirm(AppAction(AppActionKind.FLASH, target.id, ready.sha256), msg("flash_confirmation", target.label, state.identity ?: "", title(ready, language), ready.version, ready.product)) },
                enabled = !state.busy && ready != null && target != null && state.identity != null && state.ports.any { it.id == target.id } && state.chip.allows(ready) && state.downloads.any { it.entry.key == ready.key && it.entry.sha256 == ready.sha256 }) { Text(text("install_selected")) }
            if (ready != null && target != null && state.chip.canForce(ready)) {
                OutlinedButton({ forceDialog = true }, enabled = !state.busy && state.identity != null && state.ports.any { it.id == target.id }) { Text(text("force_unknown")) }
                if (forceDialog) ForceUnknownDialog(language, "${target.label} · ${title(ready, language)} · ${ready.version}\nSHA-256 ${ready.sha256}", !state.busy, { forceDialog = false }) {
                    forceDialog = false
                    confirm(AppAction(AppActionKind.FLASH, target.id, ready.sha256, forceUnknown = true), msg("flash_confirmation", target.label, state.identity ?: "", title(ready, language), ready.version, ready.product))
                }
            }
            Text(text("experimental_warning"), style = MaterialTheme.typography.bodySmall)
            TextButton(logs) { Text(text("connection_log")) }
        }
    }
}
@Composable private fun LoggerScreen(state: AppSnapshot, language: String, backend: AppBackend) {
    fun text(key: String) = resolveCoreText(msg(key), language)
    fun send(kind: AppActionKind, id: String = "") = backend.perform(AppAction(kind, id))
    var paused by remember { mutableStateOf(false) }; var source by remember { mutableStateOf("") }; var channel by remember { mutableStateOf("") }; var type by remember { mutableStateOf("") }; var hideTiming by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val rows = remember(state.monitorRows, source, channel, type, hideTiming) { state.monitorRows.filter { (source.isBlank() || it.source == source) && (channel.isBlank() || it.channel?.toString() == channel) && (type.isBlank() || it.type == type) && (!hideTiming || it.type !in listOf("Clock", "Active Sensing")) } }
    LaunchedEffect(Unit) { while (true) { send(AppActionKind.LOGGER_REFRESH); delay(100) } }
    LaunchedEffect(rows.lastOrNull()?.id, paused) { if (!paused && rows.isNotEmpty()) list.scrollToItem(rows.lastIndex) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text("logger_help"), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Button({ send(AppActionKind.LOGGER_TOGGLE) }, enabled = !state.busy && !state.monitorDraining) { Text(text(if (state.monitorRunning) "logger_stop" else "logger_start")) }
            TextButton({ send(AppActionKind.LOGGER_EXPORT) }) { Text(text("logger_export")) }
            TextButton({ send(AppActionKind.LOGGER_CLEAR) }) { Text(text("logger_clear")) }
            FilterChip(paused, { paused = !paused }, label = { Text(text("logger_pause")) })
        }
        Choice(text("logger_ports"), "${state.monitorSelected.size}", state.monitorPorts.map { it.id to (if (it.id in state.monitorSelected) "✓ " else "") + it.label }, { send(AppActionKind.LOGGER_PORT, it) }, enabled = !state.busy && !state.monitorRunning)
        Row {
            Choice(text("logger_source"), source.ifEmpty { text("logger_all") }, listOf("" to text("logger_all")) + state.monitorPorts.map { it.label to it.label }, { source = it }, Modifier.weight(1f))
            Choice(text("logger_channel"), channel.ifEmpty { text("logger_all") }, listOf("" to text("logger_all")) + (1..16).map { "$it" to "$it" }, { channel = it }, Modifier.weight(1f))
        }
        Choice(text("logger_type"), type.ifEmpty { text("logger_all") }, listOf("" to text("logger_all")) + state.monitorRows.map { it.type }.distinct().sorted().map { it to it }, { type = it })
        FilterChip(hideTiming, { hideTiming = !hideTiming }, label = { Text(text("logger_hide_timing")) })
        Text(resolveCoreText(state.monitorStatus, language), style = MaterialTheme.typography.bodySmall)
        LazyColumn(state = list, modifier = Modifier.weight(1f)) { items(rows, key = { it.id }) { row -> SelectionContainerText(row.text) } }
    }
}
@Composable private fun SelectionContainerText(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text, fontSize = 12.sp) } }

@Composable private fun Cover(entry: CatalogEntry, offline: Boolean, backend: AppBackend, source: CatalogSource?, compact: Boolean = false) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, entry.cardUrl, offline, source) {
        value = null
        try { value = backend.artwork(entry) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { value = null }
    }
    val bitmap = image
    if (bitmap != null) Image(bitmap, entry.name, modifier = Modifier.fillMaxWidth().height(if (compact) 112.dp else 160.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    else if (!compact) Surface(Modifier.fillMaxWidth().height(100.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp)) { Text("FM‑1", style = MaterialTheme.typography.headlineMedium); Text(entry.name, style = MaterialTheme.typography.labelLarge) }
    }
}
