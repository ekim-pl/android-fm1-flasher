package pl.fm1.flasher

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var artwork: ArtworkLoader
    private var service: FlasherService? = null
    private var bound = false
    private var state = UiState()
    private var tab = LibraryTab.CATALOG
    private var browseFilters = BrowseFilters()
    private var changingControls = false
    private lateinit var list: PullRefreshListView
    private lateinit var refreshHint: TextView
    private var started = false
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
    private var pendingUsb: Pair<Int, () -> Unit>? = null
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
            operationStatus.text = "Usługa zatrzymana. Nie potwierdzono wyniku aktualizacji."
        }
    }

    private val permissionResult = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val pending = pendingUsb ?: return
            pendingUsb = null
            @Suppress("DEPRECATION") val usb = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && usb?.deviceId == pending.first) pending.second()
            else Toast.makeText(this@MainActivity, "Nie udzielono dostępu do wybranego portu USB.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val prefs = getSharedPreferences("browse", MODE_PRIVATE)
        fun value(key: String, fallback: String) = savedInstanceState?.getString(key) ?: prefs.getString(key, fallback) ?: fallback
        tab = runCatching { LibraryTab.valueOf(value("tab", "CATALOG")) }.getOrDefault(LibraryTab.CATALOG)
        browseFilters = BrowseFilters(value("query", ""),
            runCatching { FirmwareCategory.valueOf(value("category", "ALL")) }.getOrDefault(FirmwareCategory.ALL),
            runCatching { FirmwareSort.valueOf(value("sort", "NAME")) }.getOrDefault(FirmwareSort.NAME),
            value("downloaded", "false").toBoolean(), value("favorites", "false").toBoolean())
        artwork = ArtworkLoader(File(filesDir, "previews")) { state.offline }
        buildUi()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionResult, IntentFilter(permissionAction), RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(permissionResult, IntentFilter(permissionAction)) }
        bound = bindService(Intent(this, FlasherService::class.java), binding, BIND_AUTO_CREATE)
    }

    private fun buildUi() {
        val root = ui.column().apply {
            setBackgroundColor(Ui.BG)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        val appBar = ui.row().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(10)) }
        val brand = ui.column()
        brand.addView(ui.text("FM‑1 Flasher", 20, Ui.TEXT, true))
        usbStatus = ui.text("USB MIDI · brak połączenia", 11, Ui.MUTED).apply {
            setPadding(0, ui.dp(5), 0, 0); setOnClickListener { setTab(LibraryTab.FLASHER) }
        }
        brand.addView(usbStatus); appBar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        offline = Switch(this).apply {
            text = "Offline  "; textSize = 12f; setTextColor(Ui.MUTED); minHeight = ui.dp(48)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.ACCENT, Ui.MUTED))
            contentDescription = "Tryb offline"
            setOnCheckedChangeListener { _, checked -> if (!changingControls && checked != state.offline) service?.setOffline(checked) }
        }
        appBar.addView(offline); root.addView(appBar)
        refreshHint = ui.text("↓ Przeciągnij, aby odświeżyć", 12, Ui.ACCENT).apply {
            gravity = Gravity.CENTER; minHeight = ui.dp(36); visibility = View.GONE
        }
        root.addView(refreshHint)
        val body = FrameLayout(this)
        list = PullRefreshListView(this).apply {
            divider = null; cacheColorHint = Ui.BG; clipToPadding = false
            setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(16))
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            onRefresh = { service?.refreshCatalog() ?: setRefreshing(false) }
            onPull = { amount, refreshing ->
                refreshHint.visibility = if (amount > 0 || refreshing) View.VISIBLE else View.GONE
                refreshHint.text = when {
                    refreshing -> "Odświeżanie katalogu…"
                    amount >= 1f -> "↑ Puść, aby odświeżyć katalog"
                    else -> "↓ Przeciągnij, aby odświeżyć katalog"
                }
            }
        }
        list.addHeaderView(buildBrowseHeader(), null, false)
        empty = ui.card()
        emptyTitle = ui.text("", 20, Ui.TEXT, true); emptyBody = ui.text("", 14, Ui.MUTED)
        empty.addView(emptyTitle); empty.addView(ui.gap(10)); empty.addView(emptyBody)
        empty.addView(ui.button("Wyczyść filtry i zobacz katalog") {
            browseFilters = BrowseFilters(); syncControls(); setTab(LibraryTab.CATALOG)
        })
        list.addFooterView(empty, null, false)
        val credits = ui.column().apply { setPadding(0, ui.dp(18), 0, ui.dp(16)) }
        credits.addView(ui.text("Lista: The Firmware Hub ↗", 12, Ui.ACCENT).apply {
            minHeight = ui.dp(40); gravity = Gravity.CENTER_VERTICAL; setOnClickListener { openUrl("https://thefirmwarehub.com") }
        })
        credits.addView(ui.text("CC BY 4.0 · lista filtrowana. Opisy i zdjęcia należą do autorów. Licencja ↗", 11, Ui.MUTED).apply {
            minHeight = ui.dp(40); setOnClickListener { openUrl("https://creativecommons.org/licenses/by/4.0/") }
        })
        list.addFooterView(credits, null, false)
        adapter = FirmwareAdapter(); list.adapter = adapter
        body.addView(list, FrameLayout.LayoutParams(-1, -1))
        flasher = FlasherPanel(this,
            connectAction = { target -> withUsbPermission(target) { service?.connect(target.info.id) } },
            resumeAction = { target -> withUsbPermission(target) { service?.resumeWith(target.info.id) } },
            chipAction = { service?.setChip(it) }, flashAction = ::confirmFlash, logsAction = ::showLogs)
        body.addView(flasher, FrameLayout.LayoutParams(-1, -1)); root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val operation = ui.row().apply {
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10)); setBackgroundColor(Ui.PANEL)
            setOnClickListener { if (state.flashing) setTab(LibraryTab.FLASHER) else showLogs() }
        }
        operationProgress = ProgressBar(this).apply { isIndeterminate = true }
        operation.addView(operationProgress, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { marginEnd = ui.dp(12) })
        operationStatus = ui.text("Ładowanie biblioteki…", 12, Ui.MUTED).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        operation.addView(operationStatus, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(operation)
        val nav = ui.row().apply { setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6)) }
        LibraryTab.entries.forEachIndexed { index, destination ->
            val item = ui.column().apply {
                gravity = Gravity.CENTER; minimumHeight = ui.dp(62); isFocusable = true; contentDescription = destination.label
                addView(ui.text(listOf("▦", "★", "↓", "↗")[index], 21, Ui.ACCENT).apply { gravity = Gravity.CENTER })
                addView(ui.text(destination.label, 11, Ui.MUTED, true).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(4), 0, 0) })
                setOnClickListener { setTab(destination) }
            }
            navigation[destination] = item
            nav.addView(item, LinearLayout.LayoutParams(0, ui.dp(62), 1f).apply { marginStart = ui.dp(2); marginEnd = ui.dp(2) })
        }
        root.addView(nav); setContentView(root); syncControls(); setTab(tab)
    }

    private fun buildBrowseHeader(): View {
        val header = ui.column().apply { setPadding(0, ui.dp(18), 0, ui.dp(8)) }
        header.addView(ui.label("BIBLIOTEKA FIRMWARE  /  0.2")); header.addView(ui.gap(12))
        heading = ui.text("Odkryj firmware.", 27, Ui.TEXT, true); header.addView(heading)
        header.addView(ui.gap(8)); subtitle = ui.text("Znajdź swój firmware. Zapisz go na później.", 13, Ui.MUTED); header.addView(subtitle)
        val metrics = ui.row().apply { setPadding(0, ui.dp(12), 0, ui.dp(14)) }
        fun metric(label: String, action: () -> Unit): TextView {
            val box = ui.column().apply { setOnClickListener { action() }; contentDescription = label }
            val count = ui.text("0", 24, Ui.TEXT, true); box.addView(count)
            box.addView(ui.text(label, 11, Ui.MUTED).apply { setPadding(0, ui.dp(4), 0, 0) })
            metrics.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
            return count
        }
        totalCount = metric("W KATALOGU") { setTab(LibraryTab.CATALOG) }
        downloadedCount = metric("NA TELEFONIE") { setTab(LibraryTab.DOWNLOADS) }
        favoritesCount = metric("ULUBIONE") { setTab(LibraryTab.FAVORITES) }
        header.addView(metrics)
        search = EditText(this).apply {
            hint = "Szukaj firmware lub autora…"; textSize = 14f; isSingleLine = true
            setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED); setPadding(ui.dp(16), 0, ui.dp(12), 0)
            background = ui.rounded(Ui.FIELD, 14); minimumHeight = ui.dp(50); contentDescription = "Szukaj w katalogu firmware"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!changingControls) { browseFilters = browseFilters.copy(query = s.toString()); filterEntries() }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(search, LinearLayout.LayoutParams(-1, ui.dp(50))); header.addView(ui.gap(12))
        val selectors = ui.row()
        fun selector(title: String, options: List<String>, weight: Float): Spinner {
            val column = ui.column(); column.addView(ui.text(title, 11, Ui.MUTED)); column.addView(ui.gap(6))
            val picker = ui.spinner(options).apply { contentDescription = title }
            column.addView(picker, LinearLayout.LayoutParams(-1, ui.dp(48)))
            selectors.addView(column, LinearLayout.LayoutParams(0, -2, weight))
            return picker
        }
        category = selector("Kategoria", FirmwareCategory.entries.map { it.label }, 1.2f)
        selectors.addView(View(this), LinearLayout.LayoutParams(ui.dp(10), 1))
        sort = selector("Sortowanie", FirmwareSort.entries.map { it.label }, 1f)
        category.onItemSelectedListener = selection { browseFilters = browseFilters.copy(category = FirmwareCategory.entries[it]); filterEntries() }
        sort.onItemSelectedListener = selection { browseFilters = browseFilters.copy(sort = FirmwareSort.entries[it]); filterEntries() }
        header.addView(selectors)
        val quick = ui.row().apply { setPadding(0, ui.dp(4), 0, 0) }
        downloadedOnly = CheckBox(this).apply {
            text = "Tylko pobrane"; textSize = 13f; setTextColor(Ui.TEXT); buttonTintList = ColorStateList.valueOf(Ui.ACCENT)
            minHeight = ui.dp(48); contentDescription = "Tylko pobrane do flashowania offline"
            setOnCheckedChangeListener { _, checked -> if (!changingControls) { browseFilters = browseFilters.copy(downloadedOnly = checked); filterEntries() } }
        }
        favoritesOnly = CheckBox(this).apply {
            text = "Ulubione"; textSize = 13f; setTextColor(Ui.TEXT); buttonTintList = ColorStateList.valueOf(Ui.ACCENT)
            minHeight = ui.dp(48)
            setOnCheckedChangeListener { _, checked -> if (!changingControls) { browseFilters = browseFilters.copy(favoritesOnly = checked); filterEntries() } }
        }
        quick.addView(downloadedOnly, LinearLayout.LayoutParams(0, -2, 1f)); quick.addView(favoritesOnly, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(quick)
        updatePackages = ui.button("Aktualizuj lokalne paczki", true) { service?.updateDownloads() }; header.addView(updatePackages)
        val resultRow = ui.row()
        results = ui.text("", 13, Ui.TEXT, true); resultRow.addView(results, LinearLayout.LayoutParams(0, -2, 1f))
        refresh = ui.button("↻ Sprawdź") { service?.refreshCatalog() }.apply { contentDescription = "Sprawdź katalog i aktualizacje" }
        resultRow.addView(refresh, LinearLayout.LayoutParams(ui.dp(118), ui.dp(48)).apply { topMargin = ui.dp(4) })
        header.addView(resultRow)
        catalogDate = ui.text("", 11, Ui.MUTED).apply { setPadding(0, ui.dp(6), 0, ui.dp(6)) }; header.addView(catalogDate)
        return header
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
        list.visibility = if (tab == LibraryTab.FLASHER) View.GONE else View.VISIBLE
        flasher.visibility = if (tab == LibraryTab.FLASHER) View.VISIBLE else View.GONE
        navigation.forEach { (key, item) ->
            item.background = ui.rounded(if (key == tab) Ui.PANEL else Ui.BG, 14)
            (item.getChildAt(0) as TextView).setTextColor(if (key == tab) Ui.ACCENT else Ui.MUTED)
            item.isSelected = key == tab
        }
        heading.text = when (tab) {
            LibraryTab.FAVORITES -> "Twoje ulubione."
            LibraryTab.DOWNLOADS -> "Gotowe offline."
            else -> "Odkryj firmware."
        }
        subtitle.text = when (tab) {
            LibraryTab.FAVORITES -> "Twoja lokalna lista. Bez konta i logowania."
            LibraryTab.DOWNLOADS -> "Paczki zostają na telefonie po zamknięciu aplikacji."
            else -> "Znajdź swój firmware. Zapisz go na później."
        }
        downloadedOnly.visibility = if (tab == LibraryTab.DOWNLOADS) View.GONE else View.VISIBLE
        favoritesOnly.visibility = if (tab == LibraryTab.FAVORITES) View.GONE else View.VISIBLE
        updatePackages.visibility = if (tab == LibraryTab.DOWNLOADS && state.downloads.isNotEmpty()) View.VISIBLE else View.GONE
        filterEntries()
    }

    private fun filterEntries() {
        if (!::adapter.isInitialized) return
        visibleEntries = CatalogBrowser.entries(state.catalog, state.downloads, state.favorites, browseFilters, tab)
        adapter.notifyDataSetChanged()
        results.text = "${visibleEntries.size} firmware" + if (browseFilters.downloadedOnly || tab == LibraryTab.DOWNLOADS) " · zapisane na telefonie" else ""
        empty.visibility = if (visibleEntries.isEmpty()) View.VISIBLE else View.GONE
        emptyTitle.text = if (state.busy && state.catalog == null) "Ładowanie biblioteki…" else "Tu jeszcze nic nie ma"
        emptyBody.text = when {
            browseFilters.query.isNotBlank() || browseFilters.category != FirmwareCategory.ALL -> "Zmień kategorię lub wyczyść wyszukiwanie."
            browseFilters.downloadedOnly || tab == LibraryTab.DOWNLOADS -> "Pobierz paczki w katalogu, żeby mieć je zawsze pod ręką — także bez internetu."
            browseFilters.favoritesOnly || tab == LibraryTab.FAVORITES -> "Dodaj firmware gwiazdką. Ulubione zapisujemy lokalnie na tym telefonie."
            else -> "Sprawdź katalog w trybie online. Zapisane paczki są dostępne w zakładce Pobrane."
        }
        getSharedPreferences("browse", MODE_PRIVATE).edit().putString("tab", tab.name)
            .putString("query", browseFilters.query).putString("category", browseFilters.category.name).putString("sort", browseFilters.sort.name)
            .putString("downloaded", browseFilters.downloadedOnly.toString()).putString("favorites", browseFilters.favoritesOnly.toString()).apply()
    }

    private fun render(next: UiState) {
        if (isDestroyed) return
        val previous = state
        state = next
        changingControls = true; offline.isChecked = next.offline; changingControls = false
        offline.isEnabled = !next.busy
        usbStatus.text = next.identity?.let { "USB · $it" } ?: "USB MIDI · ${next.devices.size} portów"
        operationStatus.text = next.status; operationProgress.visibility = if (next.busy) View.VISIBLE else View.GONE
        totalCount.text = (next.catalog?.entries?.size ?: 0).toString()
        downloadedCount.text = next.downloads.size.toString(); favoritesCount.text = next.favorites.size.toString()
        refresh.isEnabled = !next.busy && !next.offline
        list.refreshEnabled = !next.busy && !next.offline
        list.setRefreshing(next.refreshingCatalog)
        updatePackages.isEnabled = !next.busy && !next.offline && next.downloads.isNotEmpty()
        val updates = CatalogBrowser.updates(next.catalog, next.downloads).size
        updatePackages.text = if (updates > 0) "Aktualizuj paczki ($updates)" else "Sprawdź i aktualizuj paczki"
        updatePackages.visibility = if (tab == LibraryTab.DOWNLOADS && next.downloads.isNotEmpty()) View.VISIBLE else View.GONE
        catalogDate.text = next.catalog?.let {
            (if (next.offline) "OFFLINE · " else if (!it.fresh) "KATALOG Z PAMIĘCI · " else "KATALOG · ") + it.updated
        } ?: if (next.offline) "OFFLINE · tylko lokalna biblioteka" else "Katalog niedostępny"
        flasher.render(next)
        if ((next.awaitingPort && !previous.awaitingPort) || (next.flashing && !previous.flashing)) setTab(LibraryTab.FLASHER)
        else if (previous.catalog !== next.catalog || previous.downloads != next.downloads || previous.favorites != next.favorites ||
            previous.busy != next.busy || previous.offline != next.offline) filterEntries()
        if (next.flashing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        detail?.render(next)
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
            row.name.text = entry.name
            row.author.text = "${entry.author} · ${entry.version}"
            row.category.text = FirmwareCategory.label(entry.kinds)
            row.category.setTextColor(ui.categoryColor(entry))
            row.description.text = entry.shortDescription.ifBlank { "Otwórz podgląd, aby zobaczyć szczegóły firmware." }
            val saved = state.downloads.singleOrNull { it.entry.id == entry.id }
            val latest = state.catalog?.entries?.singleOrNull { it.id == entry.id }
            val hasUpdate = saved != null && latest != null && latest.sha256 != saved.entry.sha256
            row.availability.text = when {
                saved != null -> "↓ Na telefonie · ${saved.entry.version}" + if (hasUpdate) "   ↻ Aktualizacja" else ""
                entry.downloadable -> "↓ Dostępny do pobrania" + if (entry.beta) "   · BETA" else ""
                else -> "↗ Informacje u autora"
            }
            row.availability.setTextColor(if (hasUpdate) Ui.WARNING else if (saved != null) Ui.ACCENT else Ui.MUTED)
            row.star.text = if (entry.id in state.favorites) "★" else "☆"
            row.star.contentDescription = if (entry.id in state.favorites) "Usuń ${entry.name} z ulubionych" else "Dodaj ${entry.name} do ulubionych"
            row.star.isEnabled = !state.busy
            row.star.setOnClickListener { service?.toggleFavorite(entry.id) }
            row.card.contentDescription = "Podgląd ${entry.name}, ${entry.version}"
            row.card.setOnClickListener { showEntry(entry) }
            artwork.bind(row.artwork, entry)
            return row.root
        }
    }

    private fun newRow(): RowViews {
        val outer = ui.column().apply { setPadding(0, ui.dp(6), 0, ui.dp(6)) }
        val card = ui.card().apply { layoutParams = LinearLayout.LayoutParams(-1, -2); setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14)); isFocusable = true }
        val top = ui.row().apply { gravity = Gravity.TOP }
        val image = FirmwareArtwork(this); top.addView(image, LinearLayout.LayoutParams(ui.dp(88), ui.dp(84)))
        val info = ui.column().apply { setPadding(ui.dp(12), ui.dp(1), 0, 0) }
        val name = ui.text("", 17, Ui.TEXT, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        val author = ui.text("", 11, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, ui.dp(6), 0, 0) }
        val category = ui.text("", 11, Ui.ACCENT).apply { maxLines = 2; setPadding(0, ui.dp(7), 0, 0) }
        info.addView(name); info.addView(author); info.addView(category); top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val star = ui.button("☆") {}.apply { textSize = 24f; setPadding(0, 0, 0, 0) }
        top.addView(star, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply { marginStart = ui.dp(4) })
        card.addView(top); card.addView(ui.gap(12))
        val description = ui.text("", 13, Ui.MUTED).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        val availability = ui.text("", 11, Ui.ACCENT).apply { setPadding(0, ui.dp(12), 0, 0) }
        card.addView(description); card.addView(availability); outer.addView(card)
        return RowViews(outer, card, image, name, author, category, description, availability, star).also { outer.tag = it }
    }

    private fun showEntry(entry: CatalogEntry) {
        detail?.dialog?.dismiss()
        detail = Details(entry).also { it.dialog.show(); it.render(state) }
    }

    private inner class Details(private val entry: CatalogEntry) {
        val dialog: AlertDialog
        private val favorite: Button
        private val download: Button
        private val use: Button
        private val remove: Button
        private val localStatus: TextView

        init {
            val content = ui.column().apply { setPadding(ui.dp(22), ui.dp(10), ui.dp(22), ui.dp(18)) }
            val image = FirmwareArtwork(this@MainActivity); artwork.bind(image, entry)
            content.addView(image, LinearLayout.LayoutParams(-1, ui.dp(150))); content.addView(ui.gap(18))
            content.addView(ui.text(entry.name, 25, Ui.TEXT, true)); content.addView(ui.gap(8))
            content.addView(ui.text("${entry.author} · ${FirmwareCategory.label(entry.kinds)}", 12, ui.categoryColor(entry)))
            content.addView(ui.gap(14))
            val description = ui.text(entry.shortDescription, 14, Ui.MUTED); content.addView(description)
            if (entry.shortDescription != entry.description) content.addView(ui.button("Pełny opis") {
                description.text = if (description.text.toString() == entry.description) entry.shortDescription else entry.description
            })
            favorite = ui.button("☆ Dodaj do ulubionych") { service?.toggleFavorite(entry.id) }; content.addView(favorite)
            content.addView(ui.gap(16)); localStatus = ui.text("", 13, Ui.MUTED); content.addView(localStatus)
            use = ui.button("Użyj do flashowania", true) {
                service?.useLocal(entry.id); dialog.dismiss(); setTab(LibraryTab.FLASHER)
            }; content.addView(use)
            download = ui.button("Pobierz na telefon") {
                state.catalog?.entries?.singleOrNull { it.id == entry.id }?.let { service?.download(it) }
            }; content.addView(download)
            remove = ui.button("Usuń lokalną paczkę") {
                AlertDialog.Builder(this@MainActivity).setTitle("Usunąć paczkę z telefonu?")
                    .setMessage("${entry.name} przestanie być dostępny offline. Lista ulubionych pozostanie zapisana.")
                    .setNegativeButton("Zachowaj", null).setPositiveButton("Usuń paczkę") { _, _ -> service?.removeLocal(entry.id) }.show()
            }; content.addView(remove)
            content.addView(ui.button("Repozytorium autora ↗") { openUrl(entry.sourceUrl) })
            content.addView(ui.button("Informacje techniczne") {
                val local = state.downloads.singleOrNull { it.entry.id == entry.id }?.entry
                val shown = local ?: entry
                AlertDialog.Builder(this@MainActivity).setTitle("Szczegóły paczki")
                    .setMessage("Wersja: ${shown.version}\nTożsamość: ${shown.product}\nKość flash: ${when (shown.chip) { "new" -> "starsza i nowa"; "old" -> "tylko starsza"; else -> "niepotwierdzona" }}\n\nSHA‑256:\n${shown.sha256}")
                    .setPositiveButton("Zamknij", null).show()
            })
            val scroll = ScrollView(this@MainActivity).apply { addView(content) }
            dialog = AlertDialog.Builder(this@MainActivity).setView(scroll).setNegativeButton("Zamknij", null).create()
            dialog.setOnDismissListener { if (detail === this) detail = null }
        }

        fun render(next: UiState) {
            val saved = next.downloads.singleOrNull { it.entry.id == entry.id }
            val latest = next.catalog?.entries?.singleOrNull { it.id == entry.id }
            val retired = next.catalog != null && latest == null
            val hasUpdate = saved != null && latest != null && latest.sha256 != saved.entry.sha256
            favorite.text = if (entry.id in next.favorites) "★ W ulubionych · usuń" else "☆ Dodaj do ulubionych"
            favorite.isEnabled = !next.busy
            localStatus.text = if (saved == null) "Brak paczki na telefonie." else
                "Na telefonie: ${saved.entry.version} · ${android.text.format.Formatter.formatFileSize(this@MainActivity, saved.bytes)}\n" +
                    if (retired) "Usunięty z ostatniego katalogu — zapis zablokowany."
                    else if (hasUpdate) "W katalogu jest inna paczka: ${latest?.version}. Możesz ją pobrać, zachowując stary plik do końca weryfikacji."
                    else "Gotowa do flashowania bez internetu."
            use.visibility = if (saved != null) View.VISIBLE else View.GONE
            use.isEnabled = !next.busy && saved != null && !retired
            remove.visibility = if (saved != null) View.VISIBLE else View.GONE; remove.isEnabled = !next.busy
            download.text = when {
                latest?.downloadable != true -> "Paczka dostępna u autora ↗"
                next.offline -> "Pobieranie wymaga trybu online"
                hasUpdate -> "Aktualizuj paczkę · ${latest.version}"
                saved != null -> "Pobierz ponownie · ${latest.version}"
                else -> "Pobierz na telefon · ${latest.version}"
            }
            download.isEnabled = !next.busy && !next.offline && latest?.downloadable == true && next.catalog?.fresh == true
        }
    }

    private fun confirmFlash() {
        val entry = state.ready ?: return
        val target = state.target ?: return
        AlertDialog.Builder(this).setTitle("Aktualizować ten FM‑1?")
            .setMessage("CEL:\n${target.label}\n${state.identity}\n\nLOKALNA PACZKA:\n${entry.name} ${entry.version} (${entry.product})\n\n" +
                "Przed zapisem ponownie sprawdzimy plik z telefonu. Aktualizacja może usunąć dane instrumentu. Zrób kopię i zapewnij stabilne zasilanie huba.\n\n" +
                "Po restartach wskaż nowy port tego samego FM‑1. Nie odłączaj kabla podczas zapisu. Wersja eksperymentalna nie była sprawdzona na fizycznym FM‑1.")
            .setNegativeButton("Wróć", null).setPositiveButton("Zainstaluj na tym urządzeniu") { _, _ ->
                withUsbPermission(target) {
                    if (state.target?.endpoint == target.endpoint && state.ready == entry && !state.busy) {
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
                        try { startForegroundService(Intent(this, FlasherService::class.java).setAction("FLASH")) }
                        catch (failure: RuntimeException) { Toast.makeText(this, failure.message, Toast.LENGTH_LONG).show() }
                    }
                }
            }.show()
    }

    private fun showLogs() = AlertDialog.Builder(this).setTitle("Dziennik aplikacji")
        .setMessage(state.logs.joinToString("\n\n").ifEmpty { "Brak zdarzeń." }).setPositiveButton("Zamknij", null).show()

    private fun withUsbPermission(target: UsbMidi, action: () -> Unit) {
        val usb = getSystemService(UsbManager::class.java)
        if (usb.hasPermission(target.usb)) { action(); return }
        if (pendingUsb != null) return
        pendingUsb = target.usb.deviceId to action
        val intent = Intent(permissionAction).setPackage(packageName)
        usb.requestPermission(target.usb, PendingIntent.getBroadcast(this, target.usb.deviceId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "Brak przeglądarki.", Toast.LENGTH_SHORT).show() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", tab.name); outState.putString("query", browseFilters.query)
        outState.putString("category", browseFilters.category.name); outState.putString("sort", browseFilters.sort.name)
        outState.putString("downloaded", browseFilters.downloadedOnly.toString()); outState.putString("favorites", browseFilters.favoritesOnly.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart(); started = true; service?.onAppOpened()
    }

    override fun onStop() {
        started = false; service?.onAppClosed(); super.onStop()
    }

    override fun onDestroy() {
        detail?.dialog?.dismiss(); artwork.close(); service?.unobserve(observer)
        if (bound) unbindService(binding)
        unregisterReceiver(permissionResult)
        super.onDestroy()
    }
}
