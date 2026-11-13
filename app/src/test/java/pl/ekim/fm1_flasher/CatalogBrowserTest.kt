package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test

class CatalogBrowserTest {
    private val a = localEntry().copy(id = "a", name = "Zeta", author = "Anna", kinds = setOf("synth"), date = "2026-10-01")
    private val b = a.copy(id = "b", name = "Alfa", author = "Zenon", kinds = setOf("drums", "groove"), date = "2026-11-01")
    private val c = a.copy(id = "c", name = "Beta", author = "Maria", kinds = setOf("synth"), date = "2026-09-01")
    private val catalog = Catalog(listOf(a, b, c), "now", 1, true)
    private val saved = listOf(LocalPackage(a, 20, 1000), LocalPackage(b, 10, 1000))

    @Test fun downloadedFilterWorksInsideCatalogAndCombinesWithFavoritesAndCategory() {
        val filters = BrowseFilters(category = FirmwareCategory.SYNTH, downloadedOnly = true, favoritesOnly = true)
        assertEquals(listOf(a), CatalogBrowser.entries(catalog, saved, setOf("a", "c"), filters, LibraryTab.CATALOG))
        assertEquals(listOf(b, a), CatalogBrowser.entries(catalog, saved, emptySet(), BrowseFilters(downloadedOnly = true), LibraryTab.CATALOG))
    }

    @Test fun categoryMatchesAnEntryWithMultipleKinds() {
        assertEquals(listOf(b), CatalogBrowser.entries(catalog, saved, emptySet(), BrowseFilters(category = FirmwareCategory.GROOVE), LibraryTab.CATALOG))
    }

    @Test fun downloadedAndFavoritesTabsIntersectWithSearch() {
        assertEquals(listOf(a), CatalogBrowser.entries(catalog, saved, setOf("a", "c"), BrowseFilters(query = "ANNA"), LibraryTab.DOWNLOADS))
        assertEquals(listOf(c), CatalogBrowser.entries(catalog, saved, setOf("c"), BrowseFilters(), LibraryTab.FAVORITES))
    }

    @Test fun sortOptionsAreDeterministicAndUseDatesNotVersionStrings() {
        fun sorted(sort: FirmwareSort) = CatalogBrowser.entries(catalog, saved, emptySet(), BrowseFilters(sort = sort), LibraryTab.CATALOG).map { it.id }
        assertEquals(listOf("b", "c", "a"), sorted(FirmwareSort.NAME))
        assertEquals(listOf("a", "c", "b"), sorted(FirmwareSort.AUTHOR))
        assertEquals(listOf("b", "a", "c"), sorted(FirmwareSort.NEWEST))
        assertEquals(listOf("a", "b", "c"), sorted(FirmwareSort.DOWNLOADED))
    }

    @Test fun offlineLibraryExistsWithoutCachedCatalogAndKeepsLocalVersion() {
        assertEquals(listOf(b, a), CatalogBrowser.entries(null, saved, emptySet(), BrowseFilters(), LibraryTab.DOWNLOADS))
        val changed = a.copy(version = "2", sha256 = "f".repeat(64))
        val latest = catalog.copy(entries = listOf(changed, b, c))
        assertEquals("1", CatalogBrowser.entries(latest, saved, emptySet(), BrowseFilters(query = "Zeta"), LibraryTab.DOWNLOADS).single().version)
        assertEquals("2", CatalogBrowser.entries(latest, saved, emptySet(), BrowseFilters(query = "Zeta"), LibraryTab.CATALOG).single().version)
    }

    @Test fun updateDetectionUsesHashEvenWhenVersionLabelDidNotChange() {
        val latest = catalog.copy(entries = listOf(a.copy(sha256 = "f".repeat(64)), b, c))
        assertEquals(listOf("a"), CatalogBrowser.updates(latest, saved).map { it.id })
        assertTrue(CatalogBrowser.updates(null, saved).isEmpty())
    }
}
