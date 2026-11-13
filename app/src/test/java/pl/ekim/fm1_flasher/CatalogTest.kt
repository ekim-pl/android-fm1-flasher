package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class CatalogTest {
    private val source = """{"updated":"test","firmwares":[{
      "id":"demo","name":"Demo","author":"Example","repo":"example/demo",
      "version":"1.0","what":"Synthetic catalog entry","tag":"v1.0","asset":"1234 2026-11-13",
      "sha256":"${"a".repeat(64)}","product":"FM-1_900","chip":"new"
    }]}"""

    @Test fun parsesPinnedAssetRatherThanGuessingTheNewestRelease() {
        val entry = CatalogParser.parse(source).first.single()
        assertEquals("1234", entry.assetId)
        assertEquals("v1.0", entry.tag)
        assertTrue(entry.downloadable)
    }

    @Test fun parsesCategoriesAuthorPreviewAndReleaseDate() {
        val extended = source.replace("\"id\":\"demo\"", "\"kind\":[\"groove\",\"dev\"],\"card\":\"fw/cards/demo.webp\",\"published\":\"2026-11-13T10:00:00Z\",\"id\":\"demo\"")
        val entry = CatalogParser.parse(extended).first.single()
        assertEquals(setOf("groove", "dev"), entry.kinds)
        assertEquals("https://thefirmwarehub.com/fw/cards/demo.webp", entry.cardUrl)
        assertEquals("2026-11-13T10:00:00Z", entry.date)
        assertTrue(entry.copy(description = "word ".repeat(100)).shortDescription.length <= 165)
    }

    @Test fun refusesOtherProductsAndMissingHashes() {
        assertThrows(IllegalArgumentException::class.java) { CatalogParser.parse(source.replace("FM-1_900", "SMK-37_900")) }
        assertThrows(IllegalArgumentException::class.java) { CatalogParser.parse(source.replace("a".repeat(64), "")) }
    }

    @Test fun releaseUrlAllowsGithubAccountCaseButRejectsOtherHostsAndRepositories() {
        assertTrue(CatalogParser.isReleaseDownload("https://github.com/DeadActive/hortator/releases/download/v1/demo.fwsc", "deadactive/hortator"))
        for (url in listOf("http://github.com/example/demo/releases/download/v1/a.fwsc",
            "https://github.com.evil.test/example/demo/releases/download/v1/a.fwsc",
            "https://github.com/other/demo/releases/download/v1/a.fwsc",
            "https://github.com@example.test/example/demo/releases/download/v1/a.fwsc")) {
            assertFalse(CatalogParser.isReleaseDownload(url, "example/demo"))
        }
    }

    @Test fun unknownOrNewChipNeverAllowsAnOldOnlyPackage() {
        val entry = CatalogParser.parse(source).first.single()
        assertTrue(FlashChip.UNKNOWN.allows(entry))
        assertFalse(FlashChip.UNKNOWN.allows(entry.copy(chip = "old")))
        assertFalse(FlashChip.NEW.allows(entry.copy(chip = "old")))
        assertFalse(FlashChip.OLD.allows(entry.copy(chip = "unknown")))
        assertTrue(FlashChip.OLD.allows(entry.copy(chip = "old")))
        assertFalse(FlashChip.UNKNOWN.allows(entry.copy(chip = "unknown", stock = true)))
    }

    @Test fun staleCatalogCanBeBrowsedButIsNotFreshAndDoesNotFetchAgainWithinDay() {
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            File(directory, "catalog.json").writeText(source)
            File(directory, "catalog-fetched.txt").writeText("1")
            File(directory, "catalog-attempt.txt").writeText((CatalogRepository.DAY * 2).toString())
            val repo = CatalogRepository(directory)
            assertNotNull(repo.cached(CatalogRepository.DAY * 2 + 1000))
            assertFalse(repo.cached(CatalogRepository.DAY * 2 + 1000)!!.fresh)
            assertFalse(repo.load(CatalogRepository.DAY * 2 + 1000).fresh)
            File(directory, "catalog-fetched.txt").writeText((CatalogRepository.DAY * 2).toString())
            assertTrue(repo.load(CatalogRepository.DAY * 2 + 1000).fresh)
        } finally { directory.deleteRecursively() }
    }

    @Test fun parsesLiveCatalogFixtureWhenProvided() {
        val path = System.getenv("FM1_TEST_CATALOG")
        assumeTrue(path != null)
        val catalog = CatalogParser.parse(File(path!!).readText()).first
        assertTrue(catalog.size > 10)
        assertTrue(catalog.any { it.stock })
        assertTrue(catalog.any { it.downloadable && it.chip == "new" })
    }
}
