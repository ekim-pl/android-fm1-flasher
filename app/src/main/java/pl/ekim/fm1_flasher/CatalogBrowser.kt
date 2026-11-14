package pl.ekim.fm1_flasher

import java.text.Collator
import java.util.Locale

enum class FirmwareCategory(val key: String?, val label: Int) {
    ALL(null, R.string.category_all), SYNTH("synth", R.string.category_synth), GROOVE("groove", R.string.category_groove),
    DRUMS("drums", R.string.category_drums), TRACKER("tracker", R.string.category_tracker), CHORDS("chords", R.string.category_chords),
    DEV("dev", R.string.category_dev), STOCK("stock", R.string.category_stock), OTHER("other", R.string.category_other);
}

enum class FirmwareSort(val label: Int) { NAME(R.string.sort_name), NEWEST(R.string.sort_newest), AUTHOR(R.string.sort_author), DOWNLOADED(R.string.sort_downloaded) }
enum class LibraryTab(val label: Int, val icon: String) {
    CATALOG(R.string.tab_catalog, "▦"), FAVORITES(R.string.tab_favorites, "★"), DOWNLOADS(R.string.tab_downloads, "↓"),
    LOGGER(R.string.tab_logger, "≋"), FLASHER(R.string.tab_flasher, "↗"), ABOUT(R.string.tab_about, "ⓘ")
}
data class BrowseFilters(val query: String = "", val category: FirmwareCategory = FirmwareCategory.ALL,
    val sort: FirmwareSort = FirmwareSort.NAME, val downloadedOnly: Boolean = false, val favoritesOnly: Boolean = false)

object CatalogBrowser {
    fun entries(catalog: Catalog?, local: List<LocalPackage>, favorites: Set<String>, filters: BrowseFilters,
        tab: LibraryTab): List<CatalogEntry> {
        val saved = local.associateBy { it.entry.id }
        val combined = linkedMapOf<String, CatalogEntry>()
        catalog?.entries?.forEach { combined[it.id] = it }
        local.forEach { if (it.entry.id !in combined || tab == LibraryTab.DOWNLOADS) combined[it.entry.id] = it.entry }
        val query = filters.query.trim()
        val visible = combined.values.filter { entry ->
            (query.isBlank() || "${entry.name} ${entry.author} ${entry.description}".contains(query, true)) &&
                (filters.category.key == null || filters.category.key in entry.kinds) &&
                (!(filters.downloadedOnly || tab == LibraryTab.DOWNLOADS) || entry.id in saved) &&
                (!(filters.favoritesOnly || tab == LibraryTab.FAVORITES) || entry.id in favorites)
        }
        val collator = Collator.getInstance(Locale.getDefault())
        val byName = Comparator<CatalogEntry> { a, b -> collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
        return when (filters.sort) {
            FirmwareSort.NAME -> visible.sortedWith(byName)
            FirmwareSort.AUTHOR -> visible.sortedWith(Comparator<CatalogEntry> { a, b -> collator.compare(a.author, b.author) }.then(byName))
            FirmwareSort.NEWEST -> visible.sortedWith(compareByDescending<CatalogEntry> { it.date }.then(byName))
            FirmwareSort.DOWNLOADED -> visible.sortedWith(compareByDescending<CatalogEntry> { saved[it.id]?.downloadedAt ?: 0L }.then(byName))
        }
    }

    fun updates(catalog: Catalog?, local: List<LocalPackage>): List<CatalogEntry> {
        val latest = catalog?.entries?.associateBy { it.id }.orEmpty()
        return local.mapNotNull { saved -> latest[saved.entry.id]?.takeIf { it.sha256 != saved.entry.sha256 && it.downloadable } }
    }
}
