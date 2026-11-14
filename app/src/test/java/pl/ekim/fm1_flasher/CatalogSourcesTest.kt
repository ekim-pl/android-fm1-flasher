package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class CatalogSourcesTest {
    @Test fun directCatalogResolvesRelativeUrlsWithoutInventingGithubRepo() {
        val source = CatalogSource("custom", "Custom", "https://example.org/catalogs/catalog.json")
        val json = """{"firmwares":[{"id":"demo","name":"Demo","repo":null,"site":"../", "pkg":"files/test.fwsc",
          "card":"images/demo.webp","product":"FM-1_901","sha256":"${"a".repeat(64)}","chip":"unknown","future":{"x":1}}]}"""
        val entry = CatalogParser.parse(json, source).first.single()
        assertEquals("https://example.org/catalogs/files/test.fwsc", entry.directUrl)
        assertEquals("https://example.org/catalogs/images/demo.webp", entry.cardUrl)
        assertEquals("https://example.org/", entry.sourceUrl)
        assertEquals("custom/demo", entry.key)
        assertTrue(entry.originalJson.contains("future"))
        assertFalse(FlashChip.OLD.allows(entry)); assertFalse(FlashChip.NEW.allows(entry))
        assertFalse(source.permitsPackage(entry.directUrl!!))
    }

    @Test fun hubNeverUsesPkgAsMirror() {
        val json = """{"firmwares":[{"id":"demo","name":"Demo","pkg":"fw/demo.fwsc","product":"FM-1_901","sha256":"${"a".repeat(64)}"}]}"""
        assertFalse(CatalogParser.parse(json).first.single().downloadable)
    }

    @Test fun scopesRejectOriginConfusionAndPathEscapes() {
        val scope = "https://example.org/firmware/"
        assertTrue(UrlScope.contains(scope, "https://example.org/firmware/a.fwsc"))
        for (url in listOf("http://example.org/firmware/a", "https://example.org.evil/firmware/a",
            "https://evil@example.org/firmware/a", "https://example.org:444/firmware/a",
            "https://example.org/firmware/../private/a", "https://example.org/firmware/%2e%2e/private/a",
            "https://example.org/firmware/%252e%252e/private/a", "https://example.org/firmware2/a")) {
            assertFalse(url, UrlScope.contains(scope, url))
        }
        assertEquals("https://example.org/catalog.json", UrlScope.canonical("https://EXAMPLE.ORG:443/catalog.json"))
    }

    @Test fun dnsRejectsLocalPrivateAndSpecialNetworks() {
        for (ip in listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1", "2001:db8::1")) {
            assertFalse(ip, SourceNetwork.publicAddress(InetAddress.getByName(ip)))
        }
        assertTrue(SourceNetwork.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(SourceNetwork.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test fun rollbackOfClockCannotBypassDailyBudget() {
        val source = CatalogSource.hub.copy(attemptedAt = 1000, nextAttemptAt = 1000 + CatalogRepository.DAY)
        assertFalse(source.due(1)); assertFalse(source.due(1001)); assertTrue(source.due(1000 + CatalogRepository.DAY))
    }

    @Test fun conflictsAndWithdrawalsDoNotDisappearWhenSourceIsDisabled() {
        val local = localEntry()
        val other = local.copy(sourceId = "another", chip = "old")
        val catalog = Catalog(emptyList(), "", 0, false, listOf(CatalogSource.hub.copy(enabled = false, snapshot = true)), listOf(local, other))
        assertThrows(IllegalStateException::class.java) { LocalLibrary.localFlashEntry(local, catalog) }
        val removed = catalog.copy(knownEntries = listOf(local), removedKeys = setOf(local.key))
        assertThrows(IllegalStateException::class.java) { LocalLibrary.localFlashEntry(local, removed) }
        assertFalse(catalog.canDownload(local))
    }

    @Test fun favoritesKeepDisabledOffersWhileCatalogHidesThem() {
        val entry = localEntry()
        val catalog = Catalog(emptyList(), "", 0, false, listOf(CatalogSource.hub.copy(enabled = false)), listOf(entry))
        val local = listOf(LocalPackage(entry, 1, 1000))
        assertTrue(CatalogBrowser.entries(catalog, local, setOf(entry.key), BrowseFilters(), LibraryTab.CATALOG).isEmpty())
        assertEquals(listOf(entry), CatalogBrowser.entries(catalog, local, setOf(entry.key), BrowseFilters(), LibraryTab.FAVORITES))
    }
}
