package pl.ekim.fm1_flasher

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        val localized = AppLanguage.wrap(base)
        java.util.Locale.setDefault(localized.resources.configuration.locales[0])
        super.attachBaseContext(localized)
    }

    private enum class Appearance { SYSTEM, LIGHT, DARK }

    private lateinit var ui: Ui
    private lateinit var artwork: ArtworkLoader
    private var service: FlasherService? = null
    private var bound = false
    private var state = UiState()
    private var tab = LibraryTab.CATALOG
    private var browseFilters = BrowseFilters()
    private var changingControls = false
    private lateinit var list: PullRefreshListView
    private lateinit var browser: View
    private lateinit var about: FrameLayout
    private lateinit var refreshHint: TextView
    private var started = false
    private lateinit var logger: MidiLoggerPanel
    private var exportBackupId: String? = null
    private var exportBackupUri: Uri? = null
    private lateinit var flasher: FlasherPanel
    private lateinit var adapter: FirmwareAdapter
    private lateinit var offline: Switch
    private lateinit var usbStatus: TextView
    private lateinit var operationStatus: TextView
    private lateinit var operationProgress: ProgressBar
    private lateinit var heading: TextView
    private lateinit var subtitle: TextView
    private lateinit var totalCount: TextView
    private lateinit var downloadedCount: TextView
    private lateinit var favoritesCount: TextView
    private lateinit var results: TextView
    private lateinit var catalogDate: TextView
    private lateinit var search: EditText
    private lateinit var category: Spinner
    private lateinit var sort: Spinner
    private lateinit var downloadedOnly: CheckBox
    private lateinit var favoritesOnly: CheckBox
    private lateinit var refresh: Button
    private lateinit var updatePackages: Button
    private lateinit var empty: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private val navigation = mutableMapOf<LibraryTab, LinearLayout>()
    private var visibleEntries = emptyList<CatalogEntry>()
    private var detail: Details? = null
    private var restoreEntryId: String? = null
    private var restoreDetailsId: String? = null
    private var pendingUsb: Pair<Int, () -> Unit>? = null
    private var automaticUsbPermission: Endpoint? = null
    private val permissionAction get() = "$packageName.USB_PERMISSION"
    private val observer: (UiState) -> Unit = ::render

    private val binding = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as FlasherService.LocalBinder).service
            service?.observe(observer)
            if (started) service?.onAppOpened()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            operationStatus.text = s(R.string.service_stopped)
        }
    }

    private val permissionResult = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val pending = pendingUsb ?: return
            pendingUsb = null
            // Immutable PendingIntents do not accept the USB result extras; query the actual grant.
            val manager = getSystemService(UsbManager::class.java)
            val device = manager.deviceList.values.singleOrNull { it.deviceId == pending.first }
            if (device != null && manager.hasPermission(device)) pending.second()
            else Toast.makeText(this@MainActivity, s(R.string.usb_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportBackupId = savedInstanceState?.getString("exportBackupId")
        exportBackupUri = savedInstanceState?.getString("exportBackupUri")?.let(Uri::parse)
        val prefs = getSharedPreferences("browse", MODE_PRIVATE)
        val appearance = runCatching { Appearance.valueOf(prefs.getString("appearance", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(Appearance.SYSTEM)
        val dark = when (appearance) {
            Appearance.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            Appearance.LIGHT -> false
            Appearance.DARK -> true
        }
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light)
        Ui.setDark(dark)
        window.setDecorFitsSystemWindows(false)
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        ui = Ui(this)
        restoreEntryId = savedInstanceState?.getString("visibleEntry")
        restoreDetailsId = savedInstanceState?.getString("detailEntry")
        fun value(key: String, fallback: String) = savedInstanceState?.getString(key) ?: prefs.getString(key, fallback) ?: fallback
        tab = runCatching { LibraryTab.valueOf(value("tab", "CATALOG")) }.getOrDefault(LibraryTab.CATALOG)
        browseFilters = BrowseFilters(value("query", ""),
            runCatching { FirmwareCategory.valueOf(value("category", "ALL")) }.getOrDefault(FirmwareCategory.ALL),
            runCatching { FirmwareSort.valueOf(value("sort", "NAME")) }.getOrDefault(FirmwareSort.NAME),
            value("downloaded", "false").toBoolean(), value("favorites", "false").toBoolean())
        artwork = ArtworkLoader(File(filesDir, "previews")) { state.offline }
        buildUi()
        window.insetsController?.setSystemBarsAppearance(if (dark) 0 else lightBars, lightBars)
        registerReceiver(permissionResult, IntentFilter(permissionAction), RECEIVER_NOT_EXPORTED)
        bound = bindService(Intent(this, FlasherService::class.java), binding, BIND_AUTO_CREATE)
    }

    private fun buildUi() {
        val root = ui.column().apply {
            setBackgroundColor(Ui.BG)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or
                    WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        val appBar = ui.row().apply {
            setPadding(ui.dp(if (ui.twoColumns) 12 else 20), ui.dp(if (ui.twoColumns) 0 else 12), ui.dp(20), ui.dp(if (ui.twoColumns) 0 else 10))
            minimumHeight = ui.dp(48)
        }
        val brand = if (ui.twoColumns) ui.row() else ui.column()
        brand.addView(ui.text(s(R.string.app_name), if (ui.twoColumns) 18 else 20, Ui.TEXT, true))
        usbStatus = ui.text(s(R.string.usb_disconnected), 11, Ui.MUTED).apply {
            setPadding(ui.dp(if (ui.twoColumns) 12 else 0), ui.dp(if (ui.twoColumns) 0 else 5), 0, 0)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { setTab(LibraryTab.FLASHER) }
        }
        brand.addView(usbStatus, LinearLayout.LayoutParams(if (ui.twoColumns) 0 else -1, -2, if (ui.twoColumns) 1f else 0f))
        appBar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        offline = Switch(this).apply {
            text = s(R.string.offline_label); textSize = 12f; setTextColor(Ui.MUTED); minHeight = ui.dp(48)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.ACCENT, Ui.MUTED))
            contentDescription = s(R.string.offline_mode)
            setOnCheckedChangeListener { _, checked -> if (!changingControls && checked != state.offline) service?.setOffline(checked) }
        }
        appBar.addView(offline)
        val appearanceButton = ui.text(s(R.string.settings_button), 13, Ui.ACCENT, true).apply {
            minHeight = ui.dp(48); gravity = Gravity.CENTER
            setPadding(ui.dp(8), 0, 0, 0)
            contentDescription = s(R.string.settings); isClickable = true; isFocusable = true
            setOnClickListener { showSettings() }
        }
        appBar.addView(appearanceButton)
        root.addView(appBar)
        refreshHint = ui.text(s(R.string.pull_refresh_short), 12, Ui.ACCENT).apply {
            gravity = Gravity.CENTER; minHeight = ui.dp(36); visibility = View.GONE
        }
        root.addView(refreshHint)
        val body = FrameLayout(this)
        list = PullRefreshListView(this).apply {
            divider = null; cacheColorHint = Ui.BG; clipToPadding = false
            setPadding(ui.dp(if (ui.twoColumns) 0 else 20), 0, ui.dp(if (ui.twoColumns) 0 else 20), ui.dp(16))
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            onRefresh = { service?.refreshCatalog() ?: setRefreshing(false) }
            onPull = { amount, refreshing ->
                refreshHint.visibility = if (amount > 0 || refreshing) View.VISIBLE else View.GONE
                refreshHint.text = when {
                    refreshing -> s(R.string.refreshing_catalog)
                    amount >= 1f -> s(R.string.release_to_refresh)
                    else -> s(R.string.pull_to_refresh)
                }
            }
        }
        val controls = buildBrowseHeader()
        val browseStatus = buildBrowseStatus()
        if (!ui.twoColumns) {
            controls.addView(browseStatus)
            list.addHeaderView(controls, null, false)
        }
        empty = ui.card().apply { layoutParams = AbsListView.LayoutParams(-1, -2) }
        emptyTitle = ui.text("", 20, Ui.TEXT, true); emptyBody = ui.text("", 14, Ui.MUTED)
        empty.addView(emptyTitle); empty.addView(ui.gap(10)); empty.addView(emptyBody)
        empty.addView(ui.button(s(R.string.clear_filters)) {
            browseFilters = BrowseFilters(); syncControls(); setTab(LibraryTab.CATALOG)
        })
        list.addFooterView(empty, null, false)
        val credits = ui.column().apply { setPadding(0, ui.dp(18), 0, ui.dp(16)) }
        credits.addView(ui.text(s(R.string.catalog_credit), 12, Ui.ACCENT).apply {
            minHeight = ui.dp(40); gravity = Gravity.CENTER_VERTICAL; setOnClickListener { openUrl("https://thefirmwarehub.com") }
        })
        credits.addView(ui.text(s(R.string.catalog_license), 11, Ui.MUTED).apply {
            minHeight = ui.dp(40); setOnClickListener { openUrl("https://creativecommons.org/licenses/by/4.0/") }
        })
        credits.addView(ui.text(s(R.string.catalog_language_note), 11, Ui.MUTED))
        list.addFooterView(credits, null, false)
        adapter = FirmwareAdapter(); list.adapter = adapter
        browser = if (ui.twoColumns) {
            val filters = ScrollView(this).apply { addView(controls) }
            val entries = ui.column().apply {
                addView(browseStatus)
                addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            ui.row().apply {
                setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4))
                addView(filters, LinearLayout.LayoutParams(ui.dp(264), -1).apply { marginEnd = ui.dp(12) })
                addView(entries, LinearLayout.LayoutParams(0, -1, 1f))
            }
        } else list
        body.addView(browser, FrameLayout.LayoutParams(-1, -1))
        flasher = FlasherPanel(this,
            connectAction = { target -> withUsbPermission(target) { service?.connect(target.info.id) } },
            resumeAction = { target -> withUsbPermission(target) { service?.resumeWith(target.info.id) } },
            recoveryAction = ::showRecovery, detectAction = ::detectChip, chipAction = { service?.setChip(it) }, firmwareAction = { service?.useLocal(it) },
            flashAction = ::confirmFlash, logsAction = ::showLogs)
        body.addView(flasher, FrameLayout.LayoutParams(-1, -1))
        logger = MidiLoggerPanel(this, { service?.monitor }, { !state.busy && !state.flashing },
            { port, action -> withUsbPermission(port.usb, action) }, { service?.toggleMonitor() }, ::exportMidiLog)
        body.addView(logger, FrameLayout.LayoutParams(-1, -1))
        about = FrameLayout(this)
        body.addView(about, FrameLayout.LayoutParams(-1, -1))
        val operation = ui.row().apply {
            val vertical = ui.dp(if (ui.twoColumns) 4 else 10)
            setPadding(ui.dp(20), vertical, ui.dp(20), vertical); setBackgroundColor(Ui.PANEL)
            setOnClickListener { if (state.flashing) setTab(LibraryTab.FLASHER) else showLogs() }
        }
        operationProgress = ProgressBar(this).apply { isIndeterminate = true }
        operation.addView(operationProgress, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { marginEnd = ui.dp(12) })
        operationStatus = ui.text(s(R.string.loading_library), 12, Ui.MUTED).apply {
            maxLines = if (ui.twoColumns) 1 else 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        operation.addView(operationStatus, LinearLayout.LayoutParams(0, -2, 1f))
        val nav = (if (ui.twoColumns) ui.column() else ui.row()).apply {
            setPadding(ui.dp(if (ui.twoColumns) 4 else 10), ui.dp(6), ui.dp(if (ui.twoColumns) 4 else 10), ui.dp(6))
        }
        LibraryTab.entries.forEach { destination ->
            val item = ui.column().apply {
                gravity = Gravity.CENTER; minimumHeight = ui.dp(if (ui.twoColumns) 48 else 62); isFocusable = true; contentDescription = s(destination.label)
                addView(ui.text(destination.icon, 21, Ui.ACCENT).apply { gravity = Gravity.CENTER })
                addView(ui.text(s(destination.label), 11, Ui.MUTED, true).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(4), 0, 0) })
                setOnClickListener { setTab(destination) }
            }
            navigation[destination] = item
            nav.addView(item, if (ui.twoColumns) LinearLayout.LayoutParams(-1, -2) else
                LinearLayout.LayoutParams(0, ui.dp(62), 1f).apply { marginStart = ui.dp(2); marginEnd = ui.dp(2) })
        }
        if (ui.twoColumns) {
            val rail = ScrollView(this).apply { isFillViewport = true; addView(nav) }
            val workspace = ui.row().apply {
                addView(rail, LinearLayout.LayoutParams(ui.dp(84), -1))
                addView(body, LinearLayout.LayoutParams(0, -1, 1f))
            }
            root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(operation)
        } else {
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(operation); root.addView(nav)
        }
        setContentView(root); syncControls(); setTab(tab)
    }

    private fun buildBrowseHeader(): LinearLayout {
        val header = ui.column().apply { setPadding(0, ui.dp(if (ui.twoColumns) 0 else 18), 0, ui.dp(8)) }
        heading = ui.text(s(R.string.discover_title), 27, Ui.TEXT, true)
        subtitle = ui.text(s(R.string.discover_subtitle), 13, Ui.MUTED)
        if (!ui.twoColumns) {
            header.addView(ui.label(s(R.string.library_eyebrow))); header.addView(ui.gap(12))
            header.addView(heading); header.addView(ui.gap(8)); header.addView(subtitle)
        }
        val metrics = ui.row().apply { setPadding(0, ui.dp(12), 0, ui.dp(14)) }
        fun metric(label: String, action: () -> Unit): TextView {
            val box = ui.column().apply { setOnClickListener { action() }; contentDescription = label }
            val count = ui.text("0", 24, Ui.TEXT, true); box.addView(count)
            box.addView(ui.text(label, 11, Ui.MUTED).apply { setPadding(0, ui.dp(4), 0, 0) })
            metrics.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
            return count
        }
        totalCount = metric(s(R.string.metric_catalog)) { setTab(LibraryTab.CATALOG) }
        downloadedCount = metric(s(R.string.metric_downloaded)) { setTab(LibraryTab.DOWNLOADS) }
        favoritesCount = metric(s(R.string.metric_favorites)) { setTab(LibraryTab.FAVORITES) }
        if (!ui.twoColumns) header.addView(metrics)
        search = EditText(this).apply {
            hint = s(R.string.search_hint); textSize = 14f; isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
                    clearFocus(); true
                } else false
            }
            setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED); setPadding(ui.dp(16), 0, ui.dp(12), 0)
            background = ui.rounded(Ui.FIELD, 14); minimumHeight = ui.dp(50); contentDescription = s(R.string.search_accessibility)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!changingControls) { browseFilters = browseFilters.copy(query = s.toString()); filterEntries(resetPosition = true) }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(search, LinearLayout.LayoutParams(-1, ui.dp(50))); header.addView(ui.gap(if (ui.twoColumns) 8 else 12))
        val selectors = ui.row()
        fun selector(title: String, options: List<String>, weight: Float): Spinner {
            val column = ui.column(); column.addView(ui.text(title, 11, Ui.MUTED)); column.addView(ui.gap(6))
            val picker = ui.spinner(options).apply { contentDescription = title }
            column.addView(picker, LinearLayout.LayoutParams(-1, ui.dp(48)))
            selectors.addView(column, LinearLayout.LayoutParams(0, -2, weight))
            return picker
        }
        category = selector(s(R.string.filter_category), FirmwareCategory.entries.map { s(it.label) }, 1.2f)
        selectors.addView(View(this), LinearLayout.LayoutParams(ui.dp(10), 1))
        sort = selector(s(R.string.filter_sort), FirmwareSort.entries.map { s(it.label) }, 1f)
        category.onItemSelectedListener = selection {
            val selected = FirmwareCategory.entries[it]
            if (selected != browseFilters.category) { browseFilters = browseFilters.copy(category = selected); filterEntries(resetPosition = true) }
        }
        sort.onItemSelectedListener = selection {
            val selected = FirmwareSort.entries[it]
            if (selected != browseFilters.sort) { browseFilters = browseFilters.copy(sort = selected); filterEntries(resetPosition = true) }
        }
        header.addView(selectors)
        val quick = ui.row().apply { setPadding(0, ui.dp(4), 0, 0) }
        downloadedOnly = CheckBox(this).apply {
            text = s(R.string.downloaded_only); textSize = 13f; setTextColor(Ui.TEXT); buttonTintList = ColorStateList.valueOf(Ui.ACCENT)
            minHeight = ui.dp(48); contentDescription = s(R.string.downloaded_accessibility)
            setOnCheckedChangeListener { _, checked -> if (!changingControls) { browseFilters = browseFilters.copy(downloadedOnly = checked); filterEntries(resetPosition = true) } }
        }
        favoritesOnly = CheckBox(this).apply {
            text = s(R.string.favorites_only); textSize = 13f; setTextColor(Ui.TEXT); buttonTintList = ColorStateList.valueOf(Ui.ACCENT)
            minHeight = ui.dp(48)
            setOnCheckedChangeListener { _, checked -> if (!changingControls) { browseFilters = browseFilters.copy(favoritesOnly = checked); filterEntries(resetPosition = true) } }
        }
        quick.addView(downloadedOnly, LinearLayout.LayoutParams(0, -2, 1f)); quick.addView(favoritesOnly, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(quick)
        updatePackages = ui.button(s(R.string.update_local), true) { service?.updateDownloads() }; header.addView(updatePackages)
        return header
    }

    private fun buildBrowseStatus(): View {
        val status = ui.column()
        val resultRow = ui.row()
        results = ui.text("", 13, Ui.TEXT, true)
        catalogDate = ui.text("", 11, Ui.MUTED).apply {
            setPadding(0, ui.dp(if (ui.twoColumns) 2 else 6), 0, ui.dp(if (ui.twoColumns) 0 else 6))
            if (ui.twoColumns) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        }
        val info = if (ui.twoColumns) ui.column().apply { addView(results); addView(catalogDate) } else results
        resultRow.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        refresh = ui.button(s(R.string.refresh_button)) { service?.refreshCatalog() }.apply { contentDescription = s(R.string.refresh_accessibility) }
        resultRow.addView(refresh, LinearLayout.LayoutParams(ui.dp(if (ui.twoColumns) 104 else 118), ui.dp(48)).apply { topMargin = ui.dp(if (ui.twoColumns) 0 else 4) })
        status.addView(resultRow)
        if (!ui.twoColumns) status.addView(catalogDate)
        return status
    }

    private fun selection(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (!changingControls) action(position) }
    }

    private fun syncControls() {
        changingControls = true
        if (search.text.toString() != browseFilters.query) search.setText(browseFilters.query)
        category.setSelection(browseFilters.category.ordinal); sort.setSelection(browseFilters.sort.ordinal)
        downloadedOnly.isChecked = browseFilters.downloadedOnly; favoritesOnly.isChecked = browseFilters.favoritesOnly
        changingControls = false
    }

    private fun setTab(destination: LibraryTab) {
        tab = destination
        browser.visibility = if (tab == LibraryTab.FLASHER || tab == LibraryTab.ABOUT || tab == LibraryTab.LOGGER) View.GONE else View.VISIBLE
        logger.visibility = if (tab == LibraryTab.LOGGER) View.VISIBLE else View.GONE
        flasher.visibility = if (tab == LibraryTab.FLASHER) View.VISIBLE else View.GONE
        about.visibility = if (tab == LibraryTab.ABOUT) View.VISIBLE else View.GONE
        if (tab == LibraryTab.ABOUT && about.childCount == 0) about.addView(AboutPanel(this, ::openUrl), FrameLayout.LayoutParams(-1, -1))
        navigation.forEach { (key, item) ->
            item.background = ui.rounded(if (key == tab) Ui.PANEL else Ui.BG, 14)
            (item.getChildAt(0) as TextView).setTextColor(if (key == tab) Ui.ACCENT else Ui.MUTED)
            item.isSelected = key == tab
        }
        heading.text = when (tab) {
            LibraryTab.FAVORITES -> s(R.string.favorites_title)
            LibraryTab.DOWNLOADS -> s(R.string.downloads_title)
            else -> s(R.string.discover_title)
        }
        subtitle.text = when (tab) {
            LibraryTab.FAVORITES -> s(R.string.favorites_subtitle)
            LibraryTab.DOWNLOADS -> s(R.string.downloads_subtitle)
            else -> s(R.string.discover_subtitle)
        }
        downloadedOnly.visibility = if (tab == LibraryTab.DOWNLOADS) View.GONE else View.VISIBLE
        favoritesOnly.visibility = if (tab == LibraryTab.FAVORITES) View.GONE else View.VISIBLE
        updatePackages.visibility = if (tab == LibraryTab.DOWNLOADS && state.downloads.isNotEmpty()) View.VISIBLE else View.GONE
        filterEntries(resetPosition = restoreEntryId == null)
    }

    private fun filterEntries(resetPosition: Boolean = false) {
        if (!::adapter.isInitialized) return
        visibleEntries = CatalogBrowser.entries(state.catalog, state.downloads, state.favorites, browseFilters, tab)
        adapter.notifyDataSetChanged()
        if (resetPosition) {
            restoreEntryId = null
            list.setSelection(0)
        } else restoreEntryId?.let { id ->
            val position = visibleEntries.indexOfFirst { it.id == id }
            if (position >= 0 && !state.busy) {
                restoreEntryId = null
                list.post { list.setSelectionFromTop(position + list.headerViewsCount, 0) }
            }
        }
        results.text = s(R.string.results_count, visibleEntries.size) + if (browseFilters.downloadedOnly || tab == LibraryTab.DOWNLOADS) s(R.string.downloaded_suffix) else ""
        empty.visibility = if (visibleEntries.isEmpty()) View.VISIBLE else View.GONE
        emptyTitle.text = if (state.busy && state.catalog == null) s(R.string.loading_library) else s(R.string.empty_title)
        emptyBody.text = when {
            browseFilters.query.isNotBlank() || browseFilters.category != FirmwareCategory.ALL -> s(R.string.empty_filter)
            browseFilters.downloadedOnly || tab == LibraryTab.DOWNLOADS -> s(R.string.empty_downloads)
            browseFilters.favoritesOnly || tab == LibraryTab.FAVORITES -> s(R.string.empty_favorites)
            else -> s(R.string.empty_catalog)
        }
        getSharedPreferences("browse", MODE_PRIVATE).edit().putString("tab", tab.name)
            .putString("query", browseFilters.query).putString("category", browseFilters.category.name).putString("sort", browseFilters.sort.name)
            .putString("downloaded", browseFilters.downloadedOnly.toString()).putString("favorites", browseFilters.favoritesOnly.toString()).apply()
    }

    private fun render(next: UiState) {
        if (!next.busy) dispatchRecoveryExport()
        if (isDestroyed) return
        val previous = state
        state = next
        changingControls = true; offline.isChecked = next.offline; changingControls = false
        offline.isEnabled = !next.busy
        usbStatus.text = next.identity?.let { "USB · $it" } ?: s(R.string.usb_ports, next.devices.size)
        operationStatus.text = resolve(next.status); operationProgress.visibility = if (next.busy) View.VISIBLE else View.GONE
        totalCount.text = (next.catalog?.entries?.size ?: 0).toString()
        downloadedCount.text = next.downloads.size.toString(); favoritesCount.text = next.favorites.size.toString()
        refresh.isEnabled = !next.busy && !next.offline
        list.refreshEnabled = !next.busy && !next.offline
        list.setRefreshing(next.refreshingCatalog)
        updatePackages.isEnabled = !next.busy && !next.offline && next.downloads.isNotEmpty()
        val updates = CatalogBrowser.updates(next.catalog, next.downloads).size
        updatePackages.text = if (updates > 0) s(R.string.updates_count, updates) else s(R.string.update_check)
        updatePackages.visibility = if (tab == LibraryTab.DOWNLOADS && next.downloads.isNotEmpty()) View.VISIBLE else View.GONE
        catalogDate.text = next.catalog?.let {
            (if (next.offline) s(R.string.catalog_offline_prefix) else if (!it.fresh) s(R.string.catalog_cached_prefix) else s(R.string.catalog_prefix)) + it.updated.ifBlank { s(R.string.unknown_date) }
        } ?: if (next.offline) s(R.string.catalog_offline) else s(R.string.catalog_unavailable)
        flasher.render(next)
        requestAutomaticUsbPermission()
        if ((next.awaitingPort && !previous.awaitingPort) || (next.flashing && !previous.flashing)) setTab(LibraryTab.FLASHER)
        else if (previous.catalog !== next.catalog || previous.downloads != next.downloads || previous.favorites != next.favorites ||
            previous.busy != next.busy || previous.offline != next.offline) filterEntries()
        if (next.flashing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        detail?.render(next)
        if (detail == null && restoreDetailsId != null && (next.catalog != null || !next.busy)) {
            val id = restoreDetailsId
            restoreDetailsId = null
            (next.catalog?.entries?.find { it.id == id } ?: next.downloads.find { it.entry.id == id }?.entry)?.let(::showEntry)
        }
    }

    private data class RowViews(val root: LinearLayout, val card: LinearLayout, val artwork: FirmwareArtwork,
        val name: TextView, val author: TextView, val category: TextView, val description: TextView, val availability: TextView, val star: Button)

    private inner class FirmwareAdapter : BaseAdapter() {
        override fun getCount() = visibleEntries.size
        override fun getItem(position: Int) = visibleEntries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = getItem(position)
            val row = (convertView?.tag as? RowViews) ?: newRow()
            row.name.text = entryTitle(entry)
            row.author.text = "${entry.author} · ${entry.version}"
            row.category.text = categoryLabel(entry.kinds)
            row.category.setTextColor(ui.categoryColor(entry))
            row.description.text = entryDescription(entry, true).ifBlank { s(R.string.description_fallback) }
            val saved = state.downloads.singleOrNull { it.entry.id == entry.id }
            val latest = state.catalog?.entries?.singleOrNull { it.id == entry.id }
            val hasUpdate = saved != null && latest != null && latest.sha256 != saved.entry.sha256
            row.availability.text = when {
                saved != null -> s(R.string.saved_version, saved.entry.version) + if (hasUpdate) s(R.string.update_badge) else ""
                entry.downloadable -> s(R.string.available_download) + if (entry.beta) s(R.string.beta_badge) else ""
                else -> s(R.string.author_info)
            }
            row.availability.setTextColor(if (hasUpdate) Ui.WARNING else if (saved != null) Ui.ACCENT else Ui.MUTED)
            row.star.text = if (entry.id in state.favorites) "★" else "☆"
            row.star.contentDescription = if (entry.id in state.favorites) s(R.string.remove_favorite_accessibility, entryTitle(entry)) else s(R.string.add_favorite_accessibility, entryTitle(entry))
            row.star.isEnabled = !state.busy
            row.star.setOnClickListener { service?.toggleFavorite(entry.id) }
            row.card.contentDescription = s(R.string.preview_accessibility, entryTitle(entry), entry.version)
            row.card.setOnClickListener { showEntry(entry) }
            artwork.bind(row.artwork, entry)
            return row.root
        }
    }

    private fun newRow(): RowViews {
        val outer = ui.column().apply { setPadding(0, ui.dp(if (ui.twoColumns) 4 else 6), 0, ui.dp(if (ui.twoColumns) 4 else 6)) }
        val card = ui.card().apply {
            val padding = ui.dp(if (ui.twoColumns) 12 else 14)
            layoutParams = LinearLayout.LayoutParams(-1, -2); setPadding(padding, padding, padding, padding); isFocusable = true
        }
        val top = ui.row().apply { gravity = Gravity.TOP }
        val image = FirmwareArtwork(this); top.addView(image, LinearLayout.LayoutParams(ui.dp(if (ui.twoColumns) 64 else 88), ui.dp(if (ui.twoColumns) 52 else 84)))
        val info = ui.column().apply { setPadding(ui.dp(12), ui.dp(1), 0, 0) }
        val name = ui.text("", if (ui.twoColumns) 16 else 17, Ui.TEXT, true).apply { maxLines = if (ui.twoColumns) 1 else 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        val author = ui.text("", 11, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, ui.dp(if (ui.twoColumns) 3 else 6), 0, 0) }
        val category = ui.text("", 11, Ui.ACCENT).apply { maxLines = if (ui.twoColumns) 1 else 2; setPadding(0, ui.dp(if (ui.twoColumns) 3 else 7), 0, 0) }
        info.addView(name); info.addView(author); info.addView(category); top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val star = ui.button("☆") {}.apply { textSize = 24f; setPadding(0, 0, 0, 0) }
        top.addView(star, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply { marginStart = ui.dp(4) })
        card.addView(top); card.addView(ui.gap(if (ui.twoColumns) 6 else 12))
        val description = ui.text("", 13, Ui.MUTED).apply { maxLines = if (ui.twoColumns) 1 else 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        val availability = ui.text("", 11, Ui.ACCENT).apply { setPadding(0, ui.dp(if (ui.twoColumns) 4 else 12), 0, 0) }
        card.addView(description); card.addView(availability); outer.addView(card)
        return RowViews(outer, card, image, name, author, category, description, availability, star).also { outer.tag = it }
    }

    private fun showEntry(entry: CatalogEntry) {
        detail?.dialog?.dismiss()
        detail = Details(entry).also {
            it.dialog.show()
            if (ui.twoColumns) it.dialog.window?.setLayout((resources.displayMetrics.widthPixels * .92f).toInt(), -2)
            it.render(state)
        }
    }

    private inner class Details(val entry: CatalogEntry) {
        val dialog: AlertDialog
        private val favorite: Button
        private val download: Button
        private val use: Button
        private val remove: Button
        private val localStatus: TextView

        init {
            val content = ui.column().apply { setPadding(ui.dp(22), ui.dp(10), ui.dp(if (ui.twoColumns) 12 else 22), ui.dp(18)) }
            val actions = if (ui.twoColumns) ui.column().apply { setPadding(ui.dp(12), ui.dp(10), ui.dp(22), ui.dp(18)) } else content
            val image = FirmwareArtwork(this@MainActivity); artwork.bind(image, entry)
            val title = ui.text(entryTitle(entry), if (ui.twoColumns) 21 else 25, Ui.TEXT, true)
            val author = ui.text("${entry.author} · ${categoryLabel(entry.kinds)}", 12, ui.categoryColor(entry))
            if (ui.twoColumns) {
                val summary = ui.column().apply { addView(title); addView(ui.gap(8)); addView(author) }
                content.addView(ui.row().apply {
                    addView(image, LinearLayout.LayoutParams(ui.dp(100), ui.dp(80)).apply { marginEnd = ui.dp(12) })
                    addView(summary, LinearLayout.LayoutParams(0, -2, 1f))
                })
            } else {
                content.addView(image, LinearLayout.LayoutParams(-1, ui.dp(150))); content.addView(ui.gap(18))
                content.addView(title); content.addView(ui.gap(8)); content.addView(author)
            }
            content.addView(ui.gap(if (ui.twoColumns) 10 else 14))
            val description = ui.text(entryDescription(entry, true), 14, Ui.MUTED); content.addView(description)
            if (entryDescription(entry, true) != entryDescription(entry)) content.addView(ui.button(s(R.string.full_description)) {
                description.text = if (description.text.toString() == entryDescription(entry)) entryDescription(entry, true) else entryDescription(entry)
            })
            favorite = ui.button(s(R.string.favorite_add)) { service?.toggleFavorite(entry.id) }
            if (!ui.twoColumns) { content.addView(favorite); content.addView(ui.gap(16)) }
            localStatus = ui.text("", 13, Ui.MUTED); actions.addView(localStatus)
            use = ui.button(s(R.string.use_to_flash), true) {
                service?.useLocal(entry.id); dialog.dismiss(); setTab(LibraryTab.FLASHER)
            }; actions.addView(use)
            download = ui.button(s(R.string.download_to_device)) {
                state.catalog?.entries?.singleOrNull { it.id == entry.id }?.let { service?.download(it) }
            }; actions.addView(download)
            if (ui.twoColumns) actions.addView(favorite)
            remove = ui.button(s(R.string.remove_local)) {
                AlertDialog.Builder(this@MainActivity).setTitle(s(R.string.remove_confirm_title))
                    .setMessage(s(R.string.remove_confirm_message, entryTitle(entry)))
                    .setNegativeButton(s(R.string.keep), null).setPositiveButton(s(R.string.remove_package)) { _, _ -> service?.removeLocal(entry.id) }.show()
            }; actions.addView(remove)
            val repository = ui.button(s(R.string.author_repository)) { openUrl(entry.sourceUrl) }
            val technical = ui.button(s(R.string.technical_info)) {
                val local = state.downloads.singleOrNull { it.entry.id == entry.id }?.entry
                val shown = local ?: entry
                AlertDialog.Builder(this@MainActivity).setTitle(s(R.string.package_details))
                    .setMessage(s(R.string.package_technical, shown.version, shown.product,
                        s(when (shown.chip) { "new" -> R.string.chip_both; "old" -> R.string.chip_older_only; else -> R.string.chip_unconfirmed }), shown.sha256))
                    .setPositiveButton(s(R.string.close), null).show()
            }
            if (ui.twoColumns) {
                content.addView(ui.row().apply {
                    addView(repository, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8); topMargin = ui.dp(10) })
                    addView(technical, LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = ui.dp(10) })
                })
            } else { content.addView(repository); content.addView(technical) }
            val scroll = ScrollView(this@MainActivity).apply { addView(content) }
            val custom = if (ui.twoColumns) {
                val height = ui.dp((resources.configuration.screenHeightDp - 104).coerceIn(160, 420))
                ui.row().apply {
                    gravity = Gravity.TOP
                    addView(scroll, LinearLayout.LayoutParams(0, height, 1.1f))
                    addView(ScrollView(this@MainActivity).apply { addView(actions) }, LinearLayout.LayoutParams(0, height, 1f))
                }
            } else scroll
            dialog = AlertDialog.Builder(this@MainActivity).setView(custom).setNegativeButton(s(R.string.close), null).create()
            dialog.setOnDismissListener { if (detail === this) detail = null }
        }

        fun render(next: UiState) {
            val saved = next.downloads.singleOrNull { it.entry.id == entry.id }
            val latest = next.catalog?.entries?.singleOrNull { it.id == entry.id }
            val retired = next.catalog != null && latest == null
            val hasUpdate = saved != null && latest != null && latest.sha256 != saved.entry.sha256
            favorite.text = if (entry.id in next.favorites) s(R.string.favorite_remove) else s(R.string.favorite_add)
            favorite.isEnabled = !next.busy
            localStatus.text = if (saved == null) s(R.string.package_absent) else
                s(R.string.local_version, saved.entry.version, android.text.format.Formatter.formatFileSize(this@MainActivity, saved.bytes)) +
                    if (retired) s(R.string.package_retired)
                    else if (hasUpdate) s(R.string.package_update_available, latest?.version.orEmpty())
                    else s(R.string.package_offline_ready)
            use.visibility = if (saved != null) View.VISIBLE else View.GONE
            use.isEnabled = !next.busy && saved != null && !retired
            remove.visibility = if (saved != null) View.VISIBLE else View.GONE; remove.isEnabled = !next.busy
            download.text = when {
                latest?.downloadable != true -> s(R.string.package_author_link)
                next.offline -> s(R.string.download_requires_online)
                hasUpdate -> s(R.string.update_version, latest.version)
                saved != null -> s(R.string.download_again_version, latest.version)
                else -> s(R.string.download_version, latest.version)
            }
            download.isEnabled = !next.busy && !next.offline && latest?.downloadable == true && next.catalog?.fresh == true
        }
    }

    private fun exportMidiLog() {
        if (service == null) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "midi-log-${System.currentTimeMillis()}.txt")
        }, 41)
    }

    @Deprecated("Platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 61) {
            exportBackupUri = if (resultCode == RESULT_OK) data?.data else null
            if (exportBackupUri == null) exportBackupId = null
            dispatchRecoveryExport()
        }
        if (requestCode != 41) return
        val snapshot = service?.monitor?.log?.snapshot()
        val uri = data?.data
        if (resultCode != RESULT_OK || snapshot == null || uri == null) return
        Thread {
            val result = runCatching {
                checkNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(snapshot.export()) }
            }
            runOnUiThread {
                Toast.makeText(this, s(if (result.isSuccess) R.string.logger_saved else R.string.logger_save_failed), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun confirmFlash() {
        val entry = state.ready ?: return
        val target = state.target ?: return
        AlertDialog.Builder(this).setTitle(s(R.string.flash_confirm_title))
            .setMessage(s(R.string.flash_confirmation, target.label, state.identity.toString(),
                entryTitle(entry), entry.version, entry.product))
            .setNegativeButton(s(R.string.back), null).setPositiveButton(s(R.string.install_this_device)) { _, _ ->
                withUsbPermission(target) {
                    if (state.target?.endpoint == target.endpoint && state.ready == entry && !state.busy) {
                        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
                        try { startForegroundService(Intent(this, FlasherService::class.java).setAction("FLASH")) }
                        catch (failure: RuntimeException) { Toast.makeText(this, resolve(failure.appMessage(R.string.flash_result_unknown)), Toast.LENGTH_LONG).show() }
                    }
                }
            }.show()
    }

    private fun showLogs() = AlertDialog.Builder(this).setTitle(s(R.string.app_log))
        .setMessage(state.logs.joinToString("\n\n") { resolve(it) }.ifEmpty { s(R.string.no_events) }).setPositiveButton(s(R.string.close), null).show()

    private fun showRecovery() {
        if (state.busy) return
        val options = arrayOf(s(R.string.recovery_enter), s(R.string.recovery_backup),
            s(R.string.recovery_prepare), s(R.string.recovery_write_plan), s(R.string.recovery_export), s(R.string.recovery_help_title))
        AlertDialog.Builder(this).setTitle(s(R.string.recovery_title)).setItems(options) { _, choice ->
            when (choice) {
                0 -> {
                    val target = state.target
                    if (target == null || state.identity?.isLoader != false) recoveryMessage(s(R.string.recovery_midi_required))
                    else AlertDialog.Builder(this).setTitle(s(R.string.recovery_enter))
                        .setMessage(s(R.string.recovery_enter_confirm, target.label))
                        .setNegativeButton(s(R.string.cancel), null).setPositiveButton(s(R.string.recovery_enter)) { _, _ ->
                            withUsbPermission(target) {
                                if (!state.busy && state.target?.endpoint == target.endpoint) service?.enterDownloadMode()
                            }
                        }.show()
                }
                1 -> startRecovery("RECOVERY_BACKUP")
                2 -> if (Recovery.eligible(state.ready)) startRecovery("RECOVERY_PREPARE")
                    else recoveryMessage(s(R.string.recovery_v15_only))
                3 -> confirmRecovery()
                4 -> chooseRecoveryExport()
                5 -> recoveryMessage(s(R.string.recovery_help))
            }
        }.setNegativeButton(s(R.string.close), null).show()
    }

    private fun recoveryMessage(text: String) = AlertDialog.Builder(this).setTitle(s(R.string.recovery_title))
        .setMessage(text).setPositiveButton(s(R.string.close), null).show()

    private fun startRecovery(action: String, plan: RecoveryPlan? = null) {
        if (state.busy) return
        val devices = getSystemService(UsbManager::class.java).deviceList.values.filter(ChipDetector::supports)
        if (devices.size != 1) { recoveryMessage(s(R.string.recovery_help)); return }
        val device = devices.single()
        withUsbPermission(device) {
            if (!state.busy) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
                try {
                    startForegroundService(Intent(this, FlasherService::class.java).setAction(action)
                        .putExtra("deviceName", device.deviceName).putExtra("deviceId", device.deviceId)
                        .putExtra("token", plan?.token))
                } catch (failure: RuntimeException) { recoveryMessage(resolve(failure.appMessage(R.string.flash_result_unknown))) }
            }
        }
    }

    private fun confirmRecovery() {
        val plan = state.recoveryPlan ?: run { recoveryMessage(s(R.string.recovery_no_plan)); return }
        AlertDialog.Builder(this).setTitle(s(R.string.recovery_write_plan))
            .setMessage(s(R.string.recovery_confirmation, plan.deviceName, "0x%06X".format(plan.backup.flashId),
                plan.changedSectors, plan.backup.id, plan.backup.sha256))
            .setNegativeButton(s(R.string.cancel), null)
            .setPositiveButton(s(R.string.recovery_write)) { _, _ ->
                if (state.recoveryPlan?.token == plan.token) startRecovery("RECOVERY_WRITE", plan)
            }.show()
    }

    private fun chooseRecoveryExport() {
        val backups = state.recoveryBackups
        if (backups.isEmpty()) { recoveryMessage(s(R.string.recovery_no_backups)); return }
        AlertDialog.Builder(this).setTitle(s(R.string.recovery_export))
            .setItems(backups.map { "${it.id} · 0x%06X".format(it.flashId) }.toTypedArray()) { _, index ->
                exportBackupId = backups[index].id
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip").putExtra(Intent.EXTRA_TITLE, "fm1-backup-${backups[index].id}.zip"), 61)
            }.setNegativeButton(s(R.string.cancel), null).show()
    }

    private fun dispatchRecoveryExport() {
        val active = service ?: return
        val id = exportBackupId ?: return
        val uri = exportBackupUri ?: return
        if (active.state.busy) return
        exportBackupId = null; exportBackupUri = null
        active.exportRecovery(id, uri)
    }

    private fun detectChip() {
        if (state.busy) return
        val devices = getSystemService(UsbManager::class.java).deviceList.values.filter(ChipDetector::supports)
        if (devices.size != 1) {
            AlertDialog.Builder(this).setMessage(s(R.string.detect_chip_help)).setPositiveButton(s(R.string.close), null).show()
            return
        }
        val device = devices.single()
        withUsbPermission(device) { service?.detectChip(device) }
    }

    private fun withUsbPermission(target: UsbMidi, action: () -> Unit) = withUsbPermission(target.usb, action)

    private fun withUsbPermission(target: android.hardware.usb.UsbDevice, action: () -> Unit) {
        val usb = getSystemService(UsbManager::class.java)
        if (usb.hasPermission(target)) { action(); return }
        if (pendingUsb != null) return
        pendingUsb = target.deviceId to action
        val intent = Intent(permissionAction).setPackage(packageName)
        usb.requestPermission(target, PendingIntent.getBroadcast(this, target.deviceId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun requestAutomaticUsbPermission() {
        if (!state.awaitingPort) automaticUsbPermission = null
        state.automaticReconnect?.let { target ->
            val onlyUsb = getSystemService(UsbManager::class.java).deviceList.keys.singleOrNull()
            if (started && pendingUsb == null && automaticUsbPermission != target.endpoint && onlyUsb == target.usb.deviceName) {
                automaticUsbPermission = target.endpoint
                // The worker rechecks the sole-device rule after permission is granted.
                withUsbPermission(target) { }
            }
        }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, s(R.string.no_browser), Toast.LENGTH_SHORT).show() }
    }

    private fun showSettings() {
        val appearance = runCatching {
            Appearance.valueOf(getSharedPreferences("browse", MODE_PRIVATE).getString("appearance", "SYSTEM") ?: "SYSTEM")
        }.getOrDefault(Appearance.SYSTEM)
        val themes = arrayOf(s(R.string.system_default), s(R.string.theme_light), s(R.string.theme_dark))
        val language = AppLanguage.selected(this).substringBefore('-')
        val languageIndex = AppLanguage.tags.indexOfFirst { it.substringBefore('-') == language }
        val languageName = if (languageIndex > 0) AppLanguage.names[languageIndex - 1] else s(R.string.language_system)
        AlertDialog.Builder(this)
            .setTitle(s(R.string.settings))
            .setItems(arrayOf(s(R.string.appearance_setting, themes[appearance.ordinal]),
                s(R.string.language_setting, languageName), s(R.string.privacy_title))) { _, which ->
                when (which) {
                    0 -> showAppearancePicker()
                    1 -> showLanguagePicker()
                    2 -> AlertDialog.Builder(this).setTitle(s(R.string.privacy_title))
                        .setMessage(s(R.string.privacy_body)).setPositiveButton(s(R.string.close), null).show()
                }
            }
            .setNegativeButton(s(R.string.close), null).show()
    }

    private fun showLanguagePicker() {
        val selected = AppLanguage.selected(this).substringBefore('-')
        val current = AppLanguage.tags.indexOfFirst { it.substringBefore('-') == selected }.coerceAtLeast(0)
        val languages = (listOf(s(R.string.language_system)) + AppLanguage.names).toTypedArray()
        AlertDialog.Builder(this).setTitle(s(R.string.language_title))
            .setSingleChoiceItems(languages, current) { dialog, which ->
                dialog.dismiss()
                if (which != current) AppLanguage.choose(this, AppLanguage.tags[which])
            }
            .setNegativeButton(s(R.string.cancel), null).show()
    }

    private fun showAppearancePicker() {
        val prefs = getSharedPreferences("browse", MODE_PRIVATE)
        val current = runCatching { Appearance.valueOf(prefs.getString("appearance", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(Appearance.SYSTEM)
        AlertDialog.Builder(this)
            .setTitle(s(R.string.appearance_title))
            .setSingleChoiceItems(arrayOf(s(R.string.system_default), s(R.string.theme_light), s(R.string.theme_dark)), current.ordinal) { dialog, which ->
                prefs.edit().putString("appearance", Appearance.entries[which].name).apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(s(R.string.cancel), null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val visiblePosition = list.firstVisiblePosition - list.headerViewsCount
        outState.putString("visibleEntry", visibleEntries.getOrNull(visiblePosition)?.id)
        outState.putString("detailEntry", detail?.entry?.id)
        outState.putString("tab", tab.name); outState.putString("query", browseFilters.query)
        outState.putString("category", browseFilters.category.name); outState.putString("sort", browseFilters.sort.name)
        outState.putString("downloaded", browseFilters.downloadedOnly.toString()); outState.putString("favorites", browseFilters.favoritesOnly.toString())
        outState.putString("exportBackupId", exportBackupId)
        outState.putString("exportBackupUri", exportBackupUri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart(); started = true; service?.onAppOpened(); requestAutomaticUsbPermission()
    }

    override fun onStop() {
        started = false; if (!isChangingConfigurations) service?.onAppClosed(); super.onStop()
    }

    override fun onDestroy() {
        detail?.dialog?.dismiss(); artwork.close(); service?.unobserve(observer)
        if (bound) unbindService(binding)
        unregisterReceiver(permissionResult)
        super.onDestroy()
    }
}
