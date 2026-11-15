package pl.ekim.fm1_flasher


enum class FirmwareCategory(val key: String?, val labelKey: String) {
    ALL(null, "category_all"), SYNTH("synth", "category_synth"), GROOVE("groove", "category_groove"),
    DRUMS("drums", "category_drums"), TRACKER("tracker", "category_tracker"), CHORDS("chords", "category_chords"),
    DEV("dev", "category_dev"), STOCK("stock", "category_stock"), OTHER("other", "category_other");
}

enum class FirmwareSort(val labelKey: String) { NAME("sort_name"), NEWEST("sort_newest"), AUTHOR("sort_author"), DOWNLOADED("sort_downloaded") }
enum class LibraryTab(val labelKey: String, val icon: String) {
    DEVICES("fleet_devices", "▤"), TASKS("fleet_tasks", "⇅"),
    CATALOG("tab_catalog", "▦"), FAVORITES("tab_favorites", "★"), DOWNLOADS("tab_downloads", "↓"),
    LOGGER("tab_logger", "≋"), FLASHER("tab_flasher", "↗"), ABOUT("tab_about", "ⓘ")
}
data class BrowseFilters(val query: String = "", val category: FirmwareCategory = FirmwareCategory.ALL,
    val sort: FirmwareSort = FirmwareSort.NAME, val downloadedOnly: Boolean = false, val favoritesOnly: Boolean = false,
    val sourceId: String? = null)

object CatalogBrowser {
    fun entries(catalog: Catalog?, local: List<LocalPackage>, favorites: Set<String>, filters: BrowseFilters,
        tab: LibraryTab): List<CatalogEntry> {
        val saved = local.associateBy { it.entry.key }
        val combined = linkedMapOf<String, CatalogEntry>()
        (if (tab == LibraryTab.FAVORITES) catalog?.knownEntries else catalog?.entries)?.forEach { combined[it.key] = it }
        local.forEach { saved ->
            val visibleSource = catalog?.source(saved.entry)?.active != false
            if ((visibleSource || tab == LibraryTab.DOWNLOADS || tab == LibraryTab.FAVORITES) &&
                (saved.entry.key !in combined || tab == LibraryTab.DOWNLOADS)) combined[saved.entry.key] = saved.entry
        }
        val query = filters.query.trim()
        val visible = combined.values.filter { entry ->
            (filters.sourceId == null || filters.sourceId == entry.sourceId) &&
            (query.isBlank() || "${entry.name} ${entry.author} ${entry.description}".contains(query, true)) &&
                (filters.category.key == null || filters.category.key in entry.kinds) &&
                (!(filters.downloadedOnly || tab == LibraryTab.DOWNLOADS) || entry.key in saved) &&
                (!(filters.favoritesOnly || tab == LibraryTab.FAVORITES) || entry.key in favorites)
        }

        val byName = Comparator<CatalogEntry> { a, b -> a.name.lowercase().compareTo(b.name.lowercase()).takeIf { it != 0 } ?: a.key.compareTo(b.key) }
        return when (filters.sort) {
            FirmwareSort.NAME -> visible.sortedWith(byName)
            FirmwareSort.AUTHOR -> visible.sortedWith(Comparator<CatalogEntry> { a, b -> a.author.lowercase().compareTo(b.author.lowercase()) }.then(byName))
            FirmwareSort.NEWEST -> visible.sortedWith(compareByDescending<CatalogEntry> { it.dateMillis }.then(byName))
            FirmwareSort.DOWNLOADED -> visible.sortedWith(compareByDescending<CatalogEntry> { saved[it.key]?.downloadedAt ?: 0L }.then(byName))
        }
    }

    fun updates(catalog: Catalog?, local: List<LocalPackage>): List<CatalogEntry> {
        val latest = catalog?.entries?.associateBy { it.key }.orEmpty()
        return local.mapNotNull { saved -> latest[saved.entry.key]?.takeIf { it.sha256 != saved.entry.sha256 && it.downloadable } }
    }
}
