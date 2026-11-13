package pl.fm1.flasher

import java.text.Collator
import java.util.Locale

enum class FirmwareCategory(val key: String?, val label: String) {
    ALL(null, "Wszystkie kategorie"), SYNTH("synth", "Syntezatory"), GROOVE("groove", "Grooveboxy"),
    DRUMS("drums", "Perkusja i samplery"), TRACKER("tracker", "Trackery"), CHORDS("chords", "Akordy"),
    DEV("dev", "Narzędzia"), STOCK("stock", "Oryginalny M‑VAVE"), OTHER("other", "Pozostałe");
    companion object {
        fun label(kinds: Set<String>) = entries.filter { it.key in kinds }.joinToString(" · ") { it.label }.ifBlank { "Pozostałe" }
    }
}

enum class FirmwareSort(val label: String) { NAME("Nazwa A–Z"), NEWEST("Najnowsze wydania"), AUTHOR("Autor A–Z"), DOWNLOADED("Ostatnio pobrane") }
enum class LibraryTab(val label: String) { CATALOG("Katalog"), FAVORITES("Ulubione"), DOWNLOADS("Pobrane"), FLASHER("Flasher") }
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
        val collator = Collator.getInstance(Locale.forLanguageTag("pl"))
        val byName = Comparator<CatalogEntry> { a, b -> collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
        return when (filters.sort) {
            FirmwareSort.NAME -> visible.sortedWith(byName)
            FirmwareSort.AUTHOR -> visible.sortedWith(Comparator<CatalogEntry> { a, b -> collator.compare(a.author, b.author) }.then(byName))
            FirmwareSort.NEWEST -> visible.sortedWith(compareByDescending<CatalogEntry> { it.date }.then(byName))
            FirmwareSort.DOWNLOADED -> visible.sortedWith(compareByDescending<CatalogEntry> { saved[it.id]?.downloadedAt ?: 0L }.then(byName))
        }
    }

    fun updates(catalog: Catalog?, local: List<LocalPackage>) = local.mapNotNull { saved ->
        catalog?.entries?.singleOrNull { it.id == saved.entry.id && it.sha256 != saved.entry.sha256 && it.downloadable }
    }
}
