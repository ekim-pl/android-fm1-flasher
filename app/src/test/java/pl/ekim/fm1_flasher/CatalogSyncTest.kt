package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test

class CatalogSyncTest {
    private val json = """{"updated":"2026-11-14 18:01 UTC","license":{"license":"CC0"},"firmwares":[]}"""

    @Test fun successfulSnapshotAndConditionalValidationKeepPublisherDateSeparate() {
        val source = CatalogSource.awesome.copy(snapshot = true, updated = "older", etag = "old", modified = "yesterday")
        assertEquals(mapOf("If-None-Match" to "old", "If-Modified-Since" to "yesterday"), CatalogSync.headers(source))
        val fresh = CatalogSync.decode(source, CatalogResponse(200, json.toByteArray(), "new"))
        assertTrue(fresh.entries!!.isEmpty())
        assertEquals("2026-11-14 18:01 UTC", fresh.updated)
        assertTrue(fresh.license.contains("CC0"))
        assertEquals("new", fresh.etag)
        val validated = CatalogSync.decode(source, CatalogResponse(304))
        assertNull(validated.entries)
        assertEquals("older", validated.updated)
        assertEquals("old", validated.etag)
        assertThrows(IllegalStateException::class.java) { CatalogSync.decode(CatalogSource.awesome, CatalogResponse(304)) }
        assertThrows(IllegalStateException::class.java) { CatalogSync.decode(source.copy(etag = null, modified = null), CatalogResponse(304)) }
        assertTrue(CatalogSync.headers(source.copy(snapshot = false)).isEmpty())
    }

    @Test fun invalidResponsesCannotProduceRemovalSnapshots() {
        for (body in listOf("{", "{\"firmwares\":[]}{\"firmwares\":[]}", "{\"firmwares\":[{}]}")) {
            assertThrows(Exception::class.java) { CatalogSync.decode(CatalogSource.hub, CatalogResponse(200, body.toByteArray())) }
        }
        for (status in listOf(404, 429, 500)) {
            assertThrows(IllegalStateException::class.java) { CatalogSync.decode(CatalogSource.hub, CatalogResponse(status)) }
        }
        assertThrows(IllegalStateException::class.java) {
            CatalogSync.decode(CatalogSource.hub, CatalogResponse(200, ByteArray(4 * 1024 * 1024 + 1)))
        }
    }

    @Test fun retryAfterSupportsSecondsAndHttpDatesWithoutResettingDailyLimit() {
        val now = CatalogDates.millis("2026-11-14T00:00:00Z")
        assertEquals(now + 172800000, CatalogSync.retryAt("172800", now))
        assertEquals(now + 86400000, CatalogSync.retryAt("Sun, 15 Nov 2026 00:00:00 GMT", now))
        assertEquals(0L, CatalogSync.retryAt("bad", now))
        assertEquals(now, CatalogSync.retryAt("-1", now))
    }

    @Test fun datesSortByInstantAcrossHubIsoAndDailyFormats() {
        val instant = CatalogDates.millis("2026-11-14T18:01:00Z")
        assertEquals(instant, CatalogDates.millis("2026-11-14 18:01 UTC"))
        assertEquals(instant, CatalogDates.millis("2026-11-14T20:01:00+02:00"))
        assertEquals(CatalogDates.millis("2026-11-14T00:00:00Z"), CatalogDates.millis("2026-11-14"))
        assertEquals(0L, CatalogDates.millis("unknown"))
        val a = localEntry().copy(id = "a", date = "2026-11-14T01:00:00+02:00")
        val b = a.copy(id = "b", date = "2026-11-13T23:30:00Z")
        val entries = CatalogBrowser.entries(Catalog(listOf(a, b), "", 0, false), emptyList(), emptySet(),
            BrowseFilters(sort = FirmwareSort.NEWEST), LibraryTab.CATALOG)
        assertEquals(listOf(b, a), entries)
    }

    @Test fun foreignStockCannotGrantTheHubChipExceptionOrUseAnUnapprovedDownload() {
        val json = """{"firmwares":[],"stock":{"product":"FM-1_015","sha256":"${Recovery.V15_PACKAGE}","pkg":"file.fwsc"}}"""
        val source = CatalogSource.awesome.copy(enabled = true, snapshot = true, checkedAt = 100)
        val entry = CatalogParser.parse(json, source).first.single()
        assertFalse(entry.stock)
        assertFalse(FlashChip.OLD.allows(entry))
        assertFalse(Catalog(listOf(entry), "", 100, true, listOf(source)).canDownload(entry, 101))
        assertFalse(CatalogParser.isReleaseDownload("https://github.com/a/b/releases/download/../../../elsewhere/a.fwsc", "a/b"))
    }
}
