package pl.ekim.fm1_flasher

import kotlin.test.*

class FirmwareRepositoryKeysTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(path: String, limit: Int) = files[path]?.also { check(it.size <= limit) }?.copyOf()
        override fun write(path: String, bytes: ByteArray) { files[path] = bytes.copyOf() }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = files.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { it.removePrefix("$path/") }
    }
    private val raw = testPackage()
    private val entry = CatalogEntry("demo", "Demo", "Author", "", "1", "author/demo", "v1", "123",
        FirmwarePackage.sha256(raw), "FM-1_900", "new", false)
    private fun catalog(vararg entries: CatalogEntry) = """{"firmwares":[${entries.joinToString(",") {
        """{"id":"${it.id}","name":"${it.name}","version":"${it.version}","repo":"${it.repo}","sha256":"${it.sha256}","product":"${it.product}","tag":"${it.tag}","asset":"${it.assetId}","chip":"${it.chip}"}"""
    }}]}"""
    private fun repo(storage: Storage, fetch: (String) -> ByteArray = { error("Unexpected network: $it") }) =
        FirmwareRepository(storage, object : HttpClient {
            override fun get(url: String, limit: Int, artworkOnly: Boolean) = fetch(url).also { check(it.size <= limit) }
        }, { FirmwareRepository.DAY * 3 })

    @Test fun loadFullKeyWithPresentCatalog() {
        val repository = repo(Storage(), { catalog(entry).encodeToByteArray() })
        repository.save(entry, raw)
        repository.refresh()
        assertEquals(entry.key, repository.load(entry.key).first.entry.key)
    }

    @Test fun bothFormatsLoadWithAndWithoutCachedCatalog() {
        for (withCatalog in listOf(false, true)) {
            val storage = Storage()
            val repository = repo(storage, { catalog(entry).encodeToByteArray() })
            repository.save(entry, raw)
            if (withCatalog) repository.refresh()
            for (id in listOf(entry.id, entry.key)) {
                val (saved, image) = repo(storage).load(id)
                assertEquals(entry.id, saved.entry.id)
                assertEquals(entry.sha256, image.sha256)
            }
            assertEquals(1, storage.list("library/records").size)
        }
    }

    @Test fun selectionInEitherFormatSurvivesRepositoryRestart() {
        for (id in listOf(entry.id, entry.key)) {
            val storage = Storage(); val repository = repo(storage)
            repository.save(entry, raw)
            val prefs = LibrarySettings(setOf(entry.key), true, id)
            repository.saveSettings(prefs)
            val restarted = repo(storage)
            assertEquals(prefs, restarted.settings())
            assertEquals(entry, restarted.load(restarted.settings().selectedId!!).first.entry)
        }
    }

    @Test fun removalClearsSelectedPackageInEveryFormatCombination() {
        for (selected in listOf(entry.id, entry.key)) for (removed in listOf(entry.id, entry.key)) {
            val storage = Storage(); val repository = repo(storage)
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(setOf(entry.key), true, selected))
            repository.remove(removed)
            assertEquals(LibrarySettings(setOf(entry.key), true), repo(storage).settings())
            assertTrue(repository.list().isEmpty())
            assertTrue(storage.list("library/packages").isEmpty())
        }
    }

    @Test fun removalKeepsAnotherSelectionEvenWhenPackagesShareBytes() {
        val other = entry.copy(id = "demo-extra", name = "Other")
        for (selected in listOf(other.id, other.key)) for (removed in listOf(entry.id, entry.key)) {
            val storage = Storage(); val repository = repo(storage)
            repository.save(entry, raw); repository.save(other, raw)
            repository.saveSettings(LibrarySettings(selectedId = selected))
            repository.remove(removed)
            assertEquals(selected, repository.settings().selectedId)
            assertEquals(other, repository.load(selected).first.entry)
            assertEquals(listOf(other), repository.list().map { it.entry })
        }
    }

    private fun downloadRepo(storage: Storage, latest: CatalogEntry, bytes: ByteArray) = repo(storage) { url ->
        when {
            url.endsWith("catalog.json") -> catalog(latest).encodeToByteArray()
            url.contains("api.github.com") -> """{"assets":[{"id":123,"name":"demo.fwsc","browser_download_url":"https://github.com/author/demo/releases/download/v1/demo.fwsc"}]}""".encodeToByteArray()
            else -> bytes
        }
    }

    private fun updatedBytes() = raw.copyOf().apply { this[7 * 48 + 47] = ('1'.code + 8).toByte() }

    @Test fun successfulDownloadClearsSelectedPackageInBothFormats() {
        val bytes = updatedBytes()
        val latest = entry.copy(version = "2", sha256 = FirmwarePackage.sha256(bytes), product = "FM-1_901")
        for (selected in listOf(entry.id, entry.key)) {
            val storage = Storage(); val repository = downloadRepo(storage, latest, bytes)
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(setOf(entry.key), true, selected))
            repository.download(latest)
            val restarted = repo(storage)
            assertEquals(LibrarySettings(setOf(entry.key), true), restarted.settings())
            assertEquals(latest.sha256, restarted.load(entry.key).second.sha256)
            assertFalse(storage.files.containsKey("library/packages/${entry.sha256}.fwsc"))
        }
    }

    @Test fun downloadKeepsAnotherSelectionInBothFormats() {
        val other = entry.copy(id = "demo-extra")
        for (selected in listOf(other.id, other.key)) {
            val storage = Storage(); val repository = downloadRepo(storage, entry, raw)
            repository.save(other, raw)
            repository.saveSettings(LibrarySettings(selectedId = selected))
            repository.download(entry)
            assertEquals(selected, repo(storage).settings().selectedId)
            assertEquals(other, repository.load(selected, null).first.entry)
        }
    }

    @Test fun failedDownloadKeepsSelectionAndPreviousBytesInBothFormats() {
        val latest = entry.copy(version = "2", sha256 = FirmwarePackage.sha256(updatedBytes()), product = "FM-1_901")
        for (selected in listOf(entry.id, entry.key)) {
            val storage = Storage(); val repository = downloadRepo(storage, latest, raw)
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(selectedId = selected))
            assertFails { repository.download(latest) }
            assertEquals(selected, repository.settings().selectedId)
            assertEquals(entry, repository.load(selected).first.entry)
        }
    }

    @Test fun currentMetadataAppliesOnlyToTheSameBytesForBothFormats() {
        val storage = Storage(); val repository = repo(storage)
        repository.save(entry, raw)
        val corrected = entry.copy(chip = "old")
        val newer = corrected.copy(sha256 = FirmwarePackage.sha256(updatedBytes()), product = "FM-1_901")
        for (id in listOf(entry.id, entry.key)) {
            assertEquals(corrected, repository.load(id, Catalog(listOf(corrected), "", 1, false)).first.entry)
            assertEquals(entry, repository.load(id, Catalog(listOf(newer), "", 1, false)).first.entry)
        }
    }

    @Test fun withdrawalBlocksBothFormatsWithoutDeletingPackageOrSelection() {
        for (selected in listOf(entry.id, entry.key)) {
            val storage = Storage(); val repository = repo(storage) { catalog(entry.copy(id = "other")).encodeToByteArray() }
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(selectedId = selected))
            repository.refresh()
            for (id in listOf(entry.id, entry.key)) {
                val failure = assertFails { repository.load(id) }
                assertEquals(msg("firmware_removed"), failure.appMessage("local_library_error"))
            }
            assertEquals(selected, repository.settings().selectedId)
            assertEquals(entry, repository.list().single().entry)
        }
    }

    @Test fun malformedAndForeignKeysNeverAliasTheSavedPackage() {
        val storage = Storage(); val repository = repo(storage)
        repository.save(entry, raw)
        repository.saveSettings(LibrarySettings(selectedId = entry.key))
        for (id in listOf("", "firmwarehub/", "other/demo", "firmwarehub/firmwarehub/demo", "firmwarehub//demo", "/demo", "demo/", "../demo")) {
            assertFails { repository.load(id) }
            repository.remove(id)
            assertEquals(entry.key, repository.settings().selectedId)
            assertEquals(entry, repository.load(entry.key).first.entry)
        }
    }

    @Test fun bothFormatsStillValidateRecordIdentitySizeHashAndProduct() {
        for (id in listOf(entry.id, entry.key)) {
            val storage = Storage(); val repository = repo(storage)
            repository.save(entry, raw)
            val path = "library/packages/${entry.sha256}.fwsc"
            storage.files[path] = raw.copyOf(raw.size - 1)
            assertFails { repository.load(id) }
            storage.files[path] = raw.copyOf().apply { this[1000] = (this[1000].toInt() xor 1).toByte() }
            assertFails { repository.load(id) }
            storage.files[path] = raw
            assertFails { repository.load(id, Catalog(listOf(entry.copy(product = "FM-1_901")), "", 1, false)) }
            val record = storage.files.keys.single { it.startsWith("library/records/") }
            storage.files[record] = storage.files.getValue(record).decodeToString().replace("\"id\":\"demo\"", "\"id\":\"other\"").encodeToByteArray()
            assertFails { repository.load(id) }
        }
    }
}
