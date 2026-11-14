package pl.ekim.fm1_flasher

import kotlin.test.*

class FirmwareRepositoryTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        var failingPath: String? = null
        override fun read(path: String, limit: Int): ByteArray? = files[path]?.also { check(it.size <= limit) }?.copyOf()
        override fun write(path: String, bytes: ByteArray) { check(path != failingPath); files[path] = bytes.copyOf() }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = files.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { it.removePrefix("$path/") }
    }
    private val raw = testPackage()
    private val hash = FirmwarePackage.sha256(raw)
    private fun entry(id: String = "demo") = CatalogEntry(id, "Demo", "Author", "Description", "1", "author/demo", "v1", "123", hash, "FM-1_900", "new", false)
    private fun catalog(id: String = "demo") = """{"firmwares":[{"id":"$id","name":"Demo","repo":"author/demo","sha256":"$hash","product":"FM-1_900","tag":"v1","asset":"123 demo.fwsc","chip":"new"}]}"""
    private fun repo(storage: Storage, fetch: (String) -> ByteArray = { error("Network unexpected: $it") }, clock: () -> Long = { FirmwareRepository.DAY * 3 }) = FirmwareRepository(storage, object : HttpClient {
        override fun get(url: String, limit: Int, artworkOnly: Boolean) = fetch(url).also { require(it.size <= limit) }
    }, clock)
    @Test fun dailyBudgetSurvivesFailedFetchAndProcessRestart() {
        val storage = Storage(); var calls = 0; var now = FirmwareRepository.DAY * 3
        val first = repo(storage, { calls++; error("Offline") }, { now })
        assertFails { first.refresh() }; assertEquals(1, calls)
        val second = repo(storage, { calls++; catalog().encodeToByteArray() }, { now })
        assertFails { second.refresh() }; assertEquals(1, calls)
        now += FirmwareRepository.DAY
        assertTrue(second.refresh().fresh); assertEquals(2, calls)
        assertTrue(second.refresh().fresh); assertEquals(2, calls)
    }
    @Test fun corruptCatalogBlocksLocalFlashUntilRepairedWithoutExtraDailyFetch() {
        val storage = Storage(); val repo = repo(storage, { catalog().encodeToByteArray() })
        repo.refresh(); repo.save(entry(), raw)
        storage.files["catalog.json"] = "{".encodeToByteArray()
        assertFails { repo.load("demo") }
        assertFails { repo.refresh() }
        assertTrue(storage.files.keys.any { it.endsWith(".fwsc") })
    }
    @Test fun libraryRetainsMetadataFormatAndChecksBytesOnEveryLoad() {
        val storage = Storage(); val repo = repo(storage)
        repo.save(entry(), raw)
        assertEquals(entry(), repo.list().single().entry)
        assertEquals(hash, repo.load("demo").second.sha256)
        val record = storage.files.keys.single { it.startsWith("library/records/") }
        assertEquals("library/records/${FirmwarePackage.sha256("demo".encodeToByteArray())}.json", record)
        val path = "library/packages/$hash.fwsc"
        storage.files[path]!![1000] = (storage.files[path]!![1000].toInt() xor 1).toByte()
        assertFails { repo.load("demo") }
    }
    @Test fun failedUpdateKeepsPreviousRecordAndPackage() {
        val storage = Storage(); val repo = repo(storage); val old = entry()
        repo.save(old, raw)
        val metadata = storage.files.keys.single { it.startsWith("library/records/") }
        storage.failingPath = metadata
        assertFails { repo.save(old.copy(version = "2"), raw) }
        assertEquals("1", repo.load("demo").first.entry.version)
    }
    @Test fun corruptSettingsKeepPackagesResetSelectionAndEnableOffline() {
        val storage = Storage(); val repo = repo(storage)
        repo.save(entry(), raw); storage.files["library/settings.json"] = "{broken".encodeToByteArray()
        assertEquals(LibrarySettings(offline = true), repo.settings()); assertTrue(repo.settingsRepaired)
        assertTrue(storage.files.containsKey("library/settings.invalid")); assertEquals(1, repo.list().size)
    }
    @Test fun missingPackageIsNotListedAndReportsProblem() {
        val storage = Storage(); val repo = repo(storage)
        repo.save(entry(), raw); storage.remove("library/packages/$hash.fwsc")
        assertTrue(repo.list().isEmpty()); assertEquals(1, repo.problems.size)
    }
    @Test fun withdrawnPackageBlocksOfflineLoad() {
        val storage = Storage(); val repo = repo(storage, { catalog("other").encodeToByteArray() })
        repo.save(entry(), raw); repo.refresh()
        assertFails { repo.load("demo") }
    }
    @Test fun parserBoundsAndArtworkOriginApplyBeforeUse() {
        assertFails { CatalogCodec.parse("[".repeat(33) + "]".repeat(33)) }
        assertFalse(allowedUrl("https://thefirmwarehub.com.evil.test/image.png", true))
        assertFalse(allowedUrl("https://user@thefirmwarehub.com/image.png", true))
        assertFalse(allowedUrl("https://thefirmwarehub.com:8443/image.png", true))
        assertFalse(allowedUrl("http://thefirmwarehub.com/image.png", true))
        assertTrue(allowedUrl("https://thefirmwarehub.com/card.png", true))
    }
    @Test fun downloadResolvesUniqueAuthorAssetAndValidatesBeforeSaving() {
        val storage = Storage(); val requested = mutableListOf<String>()
        val repo = repo(storage, { url -> requested += url; when {
            url.endsWith("catalog.json") -> catalog().encodeToByteArray()
            url.contains("api.github.com") -> """{"assets":[{"id":123,"name":"demo.fwsc","browser_download_url":"https://github.com/author/demo/releases/download/v1/demo.fwsc"}]}""".encodeToByteArray()
            else -> raw
        } })
        repo.download(repo.refresh().entries.single())
        assertEquals(hash, repo.load("demo").second.sha256)
        assertEquals(3, requested.size)
        assertTrue(requested.none { it.contains("mirror") })
    }
}
