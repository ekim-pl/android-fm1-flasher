package pl.ekim.fm1_flasher

import kotlin.test.*

class FirmwareRepositoryConflictTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(path: String, limit: Int) = files[path]?.also { check(it.size <= limit) }?.copyOf()
        override fun write(path: String, bytes: ByteArray) { files[path] = bytes.copyOf() }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = files.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { it.removePrefix("$path/") }
    }

    private val storage = Storage()
    private val repo = FirmwareRepository(storage, object : HttpClient {
        override fun get(url: String, limit: Int, artworkOnly: Boolean): ByteArray = error("Network unexpected: $url")
    }, { 42L })
    private val raw = testPackage()
    private val local = CatalogEntry("demo", "Demo", "Author", "", "1", "author/demo", "v1", "123",
        FirmwarePackage.sha256(raw), "FM-1_900", "old", false)
    private val packagePath = "library/packages/${local.sha256}.fwsc"
    private fun catalog(vararg entries: CatalogEntry) = Catalog(entries.toList(), "known", 1, false)

    @Test fun conflictingOldAndNewDeclarationsForSameBytesAreBlocked() {
        for (chip in listOf("old", "new")) {
            val saved = local.copy(chip = chip)
            repo.save(saved, raw)
            val conflict = saved.copy(id = "conflict", chip = if (chip == "old") "new" else "old")
            val catalog = catalog(saved, conflict)
            assertTrue(catalog.conflicting(saved))
            val failure = assertFailsWith<OperationFailed> { repo.load(saved.id, catalog) }
            assertEquals(msg("source_conflict"), failure.text)
            assertEquals(saved, repo.list().single().entry)
        }
    }

    @Test fun conflictBlocksBothIdFormatsWithoutClearingSelection() {
        repo.save(local, raw)
        repo.saveSettings(LibrarySettings(offline = true, selectedId = local.key))
        val current = catalog(local, local.copy(id = "conflict", chip = "new"))
        for (id in listOf(local.id, local.key)) {
            val failure = assertFailsWith<OperationFailed> { repo.load(id, current) }
            assertEquals(msg("source_conflict"), failure.text)
            assertEquals(local.key, repo.settings().selectedId)
            assertEquals(local, repo.list().single().entry)
        }
    }

    @Test fun conflictingProductForSameBytesIsBlocked() {
        repo.save(local, raw)
        val conflict = local.copy(id = "conflict", product = "FM-1_901")
        val failure = assertFailsWith<OperationFailed> { repo.load(local.id, catalog(local, conflict)) }
        assertEquals(msg("source_conflict"), failure.text)
    }

    @Test fun conflictInKnownEntriesIsBlockedEvenWhenOtherOfferIsHidden() {
        repo.save(local, raw)
        val conflict = local.copy(id = "hidden", chip = "new")
        val catalog = catalog(local).copy(knownEntries = listOf(local, conflict))
        val failure = assertFailsWith<OperationFailed> { repo.load(local.id, catalog) }
        assertEquals(msg("source_conflict"), failure.text)
    }

    @Test fun agreeingDeclarationsAllowCorrectedMetadataForSameBytes() {
        repo.save(local, raw)
        val corrected = local.copy(chip = "new", name = "Corrected")
        val (saved, image) = repo.load(local.id, catalog(corrected, corrected.copy(id = "agrees")))
        assertEquals(corrected, saved.entry)
        assertEquals(local.sha256, image.sha256)
        assertEquals(FirmwareIdentity("FM-1", "900"), image.identity)
    }

    @Test fun unknownChipDeclarationDoesNotConflictWithKnownChip() {
        repo.save(local, raw)
        val (saved, _) = repo.load(local.id, catalog(local, local.copy(id = "unknown", chip = "unknown")))
        assertEquals(local, saved.entry)
    }

    @Test fun newerBytesCannotGrantTheirCompatibilityToOlderLocalPackage() {
        repo.save(local, raw)
        val latest = local.copy(sha256 = "a".repeat(64), product = "FM-1_901", chip = "new", version = "2")
        val conflictForNewBytes = latest.copy(id = "conflict", chip = "old")
        val (saved, image) = repo.load(local.id, catalog(latest, conflictForNewBytes))
        assertEquals(local, saved.entry)
        assertEquals(local.sha256, image.sha256)
        assertFalse(FlashChip.NEW.allows(saved.entry))
    }

    @Test fun conflictForOlderLocalBytesStillBlocksWhenNewerVersionExists() {
        repo.save(local, raw)
        val latest = local.copy(sha256 = "a".repeat(64), chip = "new", version = "2")
        val conflictForLocalBytes = local.copy(id = "conflict", chip = "new")
        val failure = assertFailsWith<OperationFailed> { repo.load(local.id, catalog(latest, conflictForLocalBytes)) }
        assertEquals(msg("source_conflict"), failure.text)
    }

    @Test fun missingCatalogAllowsVerifiedLocalPackageWithoutNetwork() {
        repo.save(local, raw)
        assertNull(repo.cached())
        val (saved, image) = repo.load(local.id)
        assertEquals(local, saved.entry)
        assertEquals(local.sha256, image.sha256)
    }

    @Test fun withdrawalBlocksLoadWithoutDeletingLocalPackage() {
        repo.save(local, raw)
        val failure = assertFailsWith<OperationFailed> { repo.load(local.id, catalog()) }
        assertEquals(msg("firmware_removed"), failure.text)
        assertEquals(local, repo.list().single().entry)
        assertEquals(local.sha256, repo.load(local.id, null).second.sha256)
    }

    @Test fun agreeingMetadataCannotBypassHashValidation() {
        repo.save(local, raw)
        storage.files.getValue(packagePath)[1000] = (raw[1000].toInt() xor 1).toByte()
        val failure = assertFailsWith<InvalidInput> { repo.load(local.id, catalog(local)) }
        assertEquals(msg("package_hash_mismatch"), failure.text)
    }

    @Test fun agreeingMetadataCannotBypassSizeValidation() {
        repo.save(local, raw)
        storage.files[packagePath] = raw.copyOf(raw.size - 1)
        val failure = assertFailsWith<OperationFailed> { repo.load(local.id, catalog(local)) }
        assertEquals(msg("local_file_invalid"), failure.text)
    }

    @Test fun uncontestedProductCorrectionStillMustMatchPackageBytes() {
        repo.save(local, raw)
        val corrected = local.copy(product = "FM-1_901")
        val failure = assertFailsWith<InvalidInput> { repo.load(local.id, catalog(corrected)) }
        assertEquals(msg("package_identity_invalid"), failure.text)
    }
}
