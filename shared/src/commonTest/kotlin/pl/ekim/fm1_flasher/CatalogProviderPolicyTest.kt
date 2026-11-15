package pl.ekim.fm1_flasher

import kotlin.test.*

class CatalogProviderPolicyTest {
    @Test fun unknownCompatibilityDoesNotContradictKnownEvidenceButKnownConflictsStayBlocked() {
        val entry = CatalogEntry("one", "Known", "", "", "900", "", null, null, "a".repeat(64), "FM-1_900", "new", false)
        val unknown = entry.copy(id = "manual", sourceId = "manual", chip = "unknown")
        val catalog = Catalog(emptyList(), "", 0, false, knownEntries = listOf(entry, unknown))
        assertFalse(catalog.conflicting(entry)); assertFalse(catalog.conflicting(unknown))
        assertTrue(catalog.copy(knownEntries = listOf(entry, unknown.copy(chip = "old"))).conflicting(entry))
        assertTrue(catalog.copy(knownEntries = listOf(entry, unknown.copy(product = "FM-1_901"))).conflicting(entry))
        assertTrue(FlashChip.entries.none { it.allows(unknown) })
    }
    @Test fun directPackageUrlIsGrantedButAnotherPackageIsNot() {
        val source = CatalogSource("direct", "Direct", "https://publisher.example/firmware.fwsc", enabled = true,
            provider = CatalogProviderType.PKG_URL, snapshot = true, checkedAt = 1000)
        val entry = CatalogEntry("package", "Package", "", "", "900", "", null, null, "a".repeat(64), "FM-1_900", "unknown", false,
            sourceId = source.id, directUrl = source.url)
        val catalog = Catalog(listOf(entry), "", 1000, true, sources = listOf(source))
        assertTrue(source.permitsPackage(source.url))
        assertTrue(catalog.canDownload(entry, 1100))
        assertFalse(source.permitsPackage("https://publisher.example/other.fwsc"))
        assertFalse(source.permitsPackage("https://evil.example/firmware.fwsc"))
        assertFalse(catalog.canDownload(entry.copy(directUrl = "https://evil.example/firmware.fwsc"), 1100))
        assertTrue(FlashChip.entries.none { it.allows(entry) })
    }
}
