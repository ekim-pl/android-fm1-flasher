package pl.ekim.fm1_flasher

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogBrowserTest {
    @Test fun searchMatchesAllTermsAcrossFieldsInAnyOrder() {
        val entry = CatalogEntry(
            id = "lead", name = "Żółty Lead", author = "Alicja", description = "Virtual analog",
            version = "2.1", repo = "", tag = null, assetId = null, sha256 = "",
            product = "", chip = "", beta = false, kinds = setOf("synth"),
        )
        val catalog = Catalog(
            listOf(entry, entry.copy(id = "drums", name = "Drums", kinds = setOf("drums"))),
            updated = "", fetchedAt = 0, fresh = true,
        )
        val results = CatalogBrowser.entries(catalog, emptyList(), emptySet(),
            BrowseFilters(query = "  2.1  ALICJA zolty synth "), LibraryTab.CATALOG)

        assertEquals(listOf(entry), results)
    }
}
