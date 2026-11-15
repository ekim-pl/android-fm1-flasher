package pl.ekim.fm1_flasher

import android.test.InstrumentationTestCase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

@Suppress("DEPRECATION")
class CatalogDatabaseTest : InstrumentationTestCase() {
    private lateinit var database: CatalogDatabase
    private lateinit var directory: File
    private lateinit var databaseName: String
    private val now = CatalogRepository.DAY * 10

    override fun setUp() {
        super.setUp()
        databaseName = "catalog-test-${UUID.randomUUID()}.db"
        database = CatalogDatabase(instrumentation.targetContext, databaseName)
        directory = File(instrumentation.targetContext.cacheDir, databaseName).apply { mkdirs() }
    }
    override fun tearDown() {
        database.close(); instrumentation.targetContext.deleteDatabase(databaseName); directory.deleteRecursively()
        super.tearDown()
    }
    private fun json(id: String = "demo", chip: String = "new") = JSONObject().put("updated", "2026-11-14 18:01 UTC")
        .put("firmwares", JSONArray().put(JSONObject().put("id", id).put("name", "Demo").put("repo", "example/demo")
            .put("tag", "v1").put("asset", "123 2026-11-14").put("product", "FM-1_900").put("chip", chip)
            .put("sha256", FirmwarePackage.sha256(testPackage())))).toString()
    private fun response(text: String = json(), etag: String? = "v1") = CatalogResponse(200, text.toByteArray(), etag)
    private fun expectFailure(action: () -> Unit) {
        var failed = false
        try { action() } catch (_: Exception) { failed = true }
        assertTrue("Expected a rejected operation", failed)
    }

    fun testCompatibilityDeclarationsAreBoundToBytesAndDoNotAffectAnotherVersion() {
        val source = database.configure(null, "Package", "https://publisher.example/file.fwsc", 24, emptySet(), emptySet(), CatalogProviderType.PKG_URL)
        val raw = testPackage(); val oldHash = FirmwarePackage.sha256(raw)
        val entry = localEntry(raw).copy(id = "package", sourceId = source.id, directUrl = source.url, chip = "unknown")
        val library = LocalLibrary(directory, database); library.save(entry, raw)
        database.commitSnapshot(source, listOf(entry), "v1", "", now, null, null)
        val known = database.configure(source.id, source.name, source.url, 24, emptySet(), emptySet(), packageChips = mapOf(oldHash to "new"))
        assertEquals("new", database.downloaded().single().entry.chip)
        assertEquals(now, known.checkedAt)
        val next = updatedPackage(); val newHash = FirmwarePackage.sha256(next)
        database.commitSnapshot(known, listOf(entry.copy(sha256 = newHash, product = "FM-1_901", chip = "unknown")), "v2", "", now, null, null)
        assertEquals("unknown", database.catalog(now).knownEntries.single { it.sourceId == source.id }.chip)
        database.configure(source.id, source.name, source.url, 24, emptySet(), emptySet(), packageChips = mapOf(oldHash to "new", newHash to "old"))
        assertEquals("old", database.catalog(now).knownEntries.single { it.sourceId == source.id }.chip)
        assertEquals("new", database.downloaded().single().entry.chip)
        assertEquals(oldHash, database.downloaded().single().entry.sha256)
    }
    fun testProviderConfigurationPersistsAndRepositoryImportsReleasePackages() {
        val configured = database.configure(null, "Repository", "https://github.com/owner/repo.git", 24, emptySet(), emptySet(),
            CatalogProviderType.REPO_URL, HttpAuthMethod.BEARER, packageChips = mapOf(FirmwarePackage.sha256(testPackage()) to "old"))
        assertEquals("https://github.com/owner/repo", configured.url)
        database.setEnabled(configured.id, true)
        val raw = testPackage()
        val payload = """[{"tag_name":"v1","published_at":"2026-11-15","assets":[{"id":1,"name":"demo.fwsc","size":${raw.size},"digest":"sha256:${FirmwarePackage.sha256(raw)}","updated_at":"today"}]}]""".toByteArray()
        val repository = CatalogRepository(database, request = { source, url, headers, active ->
            assertTrue(active()); assertEquals(configured.id, source.id)
            if (url.contains("/assets/")) { assertEquals("bytes=0-959", headers["Range"]); CatalogResponse(206, raw.copyOfRange(0, 960)) }
            else CatalogResponse(200, payload)
        })
        val result = repository.load(now, configured.id)
        val entry = result.knownEntries.single { it.sourceId == configured.id }
        assertEquals(FirmwarePackage.sha256(raw), entry.sha256); assertEquals("old", entry.chip)
        val restored = database.source(configured.id)
        assertEquals(CatalogProviderType.REPO_URL, restored.provider)
        assertEquals(HttpAuthMethod.BEARER, restored.authMethod)
        assertTrue(restored.snapshot)
    }
    fun testClearAllDownloadsPreservesFavoritesSourcesAndRecoveryFiles() {
        val library = LocalLibrary(directory, database)
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        database.saveSettings(LibrarySettings(setOf(entry.key), true, entry.key))
        val sourceCount = database.sources().size
        val backup = File(directory, "recovery/keep.bin").apply { parentFile.mkdirs(); writeText("keep") }
        library.clearDownloads()
        assertTrue(database.downloaded().isEmpty())
        assertTrue(library.list().isEmpty())
        assertEquals(setOf(entry.key), database.settings().favorites)
        assertTrue(database.settings().offline)
        assertNull(database.settings().selectedId)
        assertEquals(sourceCount, database.sources().size)
        assertEquals("keep", backup.readText())
    }
    fun testComposeBooleanBrowsePreferencesMigrateWithoutClassCast() {
        val prefs = instrumentation.targetContext.getSharedPreferences("browse", android.content.Context.MODE_PRIVATE)
        val old = prefs.all.filterKeys { it in setOf("downloaded", "favorites") }
        try {
            prefs.edit().putBoolean("downloaded", true).putBoolean("favorites", false).commit()
            database.migrate(directory)
            assertEquals("true", database.browseSettings()["downloaded"])
            assertEquals("false", database.browseSettings()["favorites"])
        } finally {
            prefs.edit().apply {
                for (key in listOf("downloaded", "favorites")) when (val value = old[key]) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    else -> remove(key)
                }
            }.commit()
        }
    }

    fun testOnlyHubEnabledAndDisabledSourceDoesNotFetch() {
        database.migrate(directory)
        val calls = mutableListOf<String>()
        val repository = CatalogRepository(database) { source, _ -> calls += source.id; response() }
        repository.load(now)
        assertEquals(listOf(CatalogSource.HUB), calls)
        assertFalse(database.source("awesome-fm1").enabled)
        assertEquals("Awesome FM-1", database.source("awesome-fm1").name)
        repository.load(now + 1)
        assertEquals(1, calls.size)
    }

    fun testMigrationKeepsLibraryOfflineFavoritesSelectionAndRequestBudget() {
        val legacy = LocalLibrary(File(directory, "library"))
        val entry = localEntry()
        legacy.save(entry, testPackage(), 42)
        legacy.saveSettings(LibrarySettings(setOf(entry.id), true, entry.id))
        File(directory, "catalog.json").writeText(json())
        File(directory, "catalog-fetched.txt").writeText(now.toString())
        File(directory, "catalog-attempt.txt").writeText((now + 1).toString())
        database.migrate(directory)
        assertEquals(LibrarySettings(setOf(entry.key), true, entry.key), database.settings())
        assertEquals(now + 1, database.source(CatalogSource.HUB).attemptedAt)
        val library = LocalLibrary(File(directory, "library"), database)
        assertEquals(entry.sha256, library.load(entry.key, database.catalog(now)).second.sha256)
        database.close(); database = CatalogDatabase(instrumentation.targetContext, databaseName)
        database.migrate(directory)
        assertEquals(1, database.downloaded().size)
        assertTrue(database.settings().offline)
        assertTrue(File(directory, "library/records").listFiles()!!.isNotEmpty())
    }

    fun testCorruptCatalogMigrationCannotBypassDailyBudget() {
        File(directory, "catalog.json").writeText("{")
        File(directory, "catalog-fetched.txt").writeText(now.toString())
        database.migrate(directory)
        var calls = 0
        val repository = CatalogRepository(database) { _, _ -> calls++; response() }
        assertTrue(repository.load(now + 1).entries.isEmpty())
        assertEquals(0, calls)
        assertTrue(repository.load(now + CatalogRepository.DAY).fresh)
        assertEquals(1, calls)
    }

    fun testMigrationTransactionRollsBackAndCanRetry() {
        expectFailure { database.transaction { database.setting("migration", "1"); error("interrupted") } }
        assertNull(database.setting("migration"))
        database.migrate(directory)
        assertEquals("1", database.setting("migration"))
    }

    fun testInvalidSnapshotKeepsExistingEntriesAndEmptySnapshotWithdraws() {
        var text = json()
        val repository = CatalogRepository(database) { _, _ -> response(text) }
        val catalog = repository.load(now)
        val entry = catalog.entries.single()
        val library = LocalLibrary(File(directory, "library"), database)
        library.save(entry, testPackage())
        text = "{"
        assertEquals(entry, repository.load(now + CatalogRepository.DAY).entries.single())
        assertNotNull(database.source(CatalogSource.HUB).error)
        text = "{\"updated\":\"empty\",\"firmwares\":[]}"
        val empty = repository.load(now + 2 * CatalogRepository.DAY)
        assertTrue(empty.entries.isEmpty())
        expectFailure { library.load(entry.key, empty) }
        database.setEnabled(CatalogSource.HUB, false)
        expectFailure { library.load(entry.key, database.catalog(now)) }
        assertEquals(1, library.list().size)
    }

    fun testConditionalResponseRefreshesSnapshotAndNeverInventsMissingOne() {
        var count = 0
        val repository = CatalogRepository(database) { _, headers ->
            count++
            if (count == 1) response() else {
                assertEquals("v1", headers["If-None-Match"])
                CatalogResponse(304)
            }
        }
        repository.load(now)
        assertTrue(repository.load(now + CatalogRepository.DAY).fresh)
        database.setEnabled("awesome-fm1", true)
        CatalogRepository(database) { _, _ -> CatalogResponse(304) }.load(now + CatalogRepository.DAY, "awesome-fm1")
        assertFalse(database.source("awesome-fm1").snapshot)
        assertNotNull(database.source("awesome-fm1").error)
    }

    fun testSeparateSourcesKeepSameIdsAndPartialFailureIndependent() {
        database.setEnabled("awesome-fm1", true)
        val repository = CatalogRepository(database) { source, _ ->
            if (source.id == CatalogSource.HUB) throw java.io.IOException("unavailable") else response()
        }
        val partial = repository.load(now)
        assertEquals("awesome-fm1", partial.entries.single().sourceId)
        assertFalse(partial.fresh)
        assertTrue(partial.canDownload(partial.entries.single(), now))
        val all = CatalogRepository(database) { _, _ -> response() }.load(now + CatalogRepository.DAY)
        assertEquals(2, all.entries.size)
        assertEquals(2, all.entries.map { it.key }.toSet().size)
    }

    fun testDisableDuringFetchDiscardsLateSnapshot() {
        CatalogRepository(database) { source, _ ->
            database.setEnabled(source.id, false); response()
        }.load(now)
        assertFalse(database.source(CatalogSource.HUB).snapshot)
        assertFalse(database.source(CatalogSource.HUB).enabled)
    }

    fun testDeleteAndReAddRestoresIdentityLimitsAndWithdrawals() {
        val source = database.configure(null, "Custom", "https://example.org/catalog.json", 24, emptySet(), emptySet())
        database.setEnabled(source.id, true)
        CatalogRepository(database) { _, _ -> response() }.load(now, source.id)
        val entry = database.catalog(now).entries.single()
        val library = LocalLibrary(File(directory, "library"), database)
        library.save(entry, testPackage())
        CatalogRepository(database) { _, _ -> response("{\"firmwares\":[]}") }.load(now + CatalogRepository.DAY, source.id)
        database.removeSource(source.id)
        val restored = database.configure(null, "Again", source.url, 24, emptySet(), emptySet())
        assertEquals(source.id, restored.id)
        assertFalse(restored.enabled)
        assertEquals(now + CatalogRepository.DAY, restored.attemptedAt)
        expectFailure { library.load(entry.key, database.catalog(now)) }
        assertEquals(1, library.list().size)
    }

    fun testDisablingSourceHidesOffersButRetainsUsableLocalPackageAndFavorites() {
        val entry = CatalogRepository(database) { _, _ -> response() }.load(now).entries.single()
        val library = LocalLibrary(File(directory, "library"), database)
        library.save(entry, testPackage())
        database.saveSettings(LibrarySettings(setOf(entry.key), true, entry.key))
        database.setEnabled(CatalogSource.HUB, false)
        val catalog = database.catalog(now)
        assertTrue(catalog.entries.isEmpty())
        assertEquals(entry.sha256, library.load(entry.key, catalog).second.sha256)
        assertEquals(setOf(entry.key), database.settings().favorites)
    }

    fun testPresetsAreNotResetOnReopenAndDuplicateUrlsAreRejected() {
        database.setEnabled(CatalogSource.HUB, false)
        database.setEnabled("awesome-fm1", true)
        database.close(); database = CatalogDatabase(instrumentation.targetContext, databaseName)
        assertFalse(database.source(CatalogSource.HUB).enabled)
        assertTrue(database.source("awesome-fm1").enabled)
        val s = database.configure(null, "A", "https://example.org/catalog.json", 24, emptySet(), emptySet())
        expectFailure { database.configure(null, "B", "https://EXAMPLE.ORG:443/catalog.json", 24, emptySet(), emptySet()) }
        expectFailure { database.configure(null, "Hub alias", "https://thefirmwarehub.com/catalog.json", 24, emptySet(), emptySet()) }
        database.restoreDefaults()
        assertTrue(database.source(CatalogSource.HUB).enabled)
        assertFalse(database.source("awesome-fm1").enabled)
        assertFalse(database.source(s.id).deleted)
    }
    fun testRealSQLiteFailureRollsBackTheEntireSnapshot() {
        val repository = CatalogRepository(database) { _, _ -> response() }
        val before = repository.load(now)
        database.writableDatabase.execSQL("CREATE TRIGGER reject_catalog BEFORE INSERT ON catalog_entries BEGIN SELECT RAISE(ABORT, 'test interruption'); END")
        val after = CatalogRepository(database) { _, _ -> response(json("replacement")) }.load(now + CatalogRepository.DAY)
        assertEquals(before.entries, after.entries)
        assertTrue(after.removedKeys.isEmpty())
        assertEquals(now, database.source(CatalogSource.HUB).checkedAt)
        assertNotNull(database.source(CatalogSource.HUB).error)
    }

    fun testFailedDatabaseMigrationCanRetryWithoutLosingPackageMetadata() {
        val legacy = LocalLibrary(File(directory, "library"))
        legacy.save(localEntry(), testPackage())
        database.writableDatabase.execSQL("CREATE TRIGGER reject_local BEFORE INSERT ON downloaded_packages BEGIN SELECT RAISE(ABORT, 'test interruption'); END")
        expectFailure { database.migrate(directory) }
        assertNull(database.setting("migration"))
        assertTrue(database.downloaded().isEmpty())
        database.writableDatabase.execSQL("DROP TRIGGER reject_local")
        database.migrate(directory)
        assertEquals(1, database.downloaded().size)
        assertEquals("1", database.setting("migration"))
    }

    fun testCorruptLegacyRecordDoesNotDeleteOtherPackagesOrItsOriginalBytes() {
        val legacy = LocalLibrary(File(directory, "library"))
        legacy.save(localEntry(), testPackage())
        val bad = File(directory, "library/records/damaged.json").apply { writeText("{") }
        val orphan = File(directory, "library/packages/orphan.fwsc").apply { writeBytes(byteArrayOf(1, 2)) }
        database.migrate(directory)
        assertEquals(1, database.downloaded().size)
        assertNotNull(database.setting("migrationProblems"))
        LocalLibrary(File(directory, "library"), database).save(localEntry(), testPackage())
        assertTrue(bad.exists()); assertTrue(orphan.exists())
    }

    fun testEndpointEditsKeepOldPackageOriginAndPolicyEditsNeedRevalidation() {
        val old = database.configure(null, "Custom", "https://example.org/catalog.json", 24, emptySet(), emptySet())
        database.setEnabled(old.id, true)
        val entry = CatalogRepository(database) { _, _ -> response() }.load(now, old.id).entries.single()
        val library = LocalLibrary(File(directory, "library"), database)
        library.save(entry, testPackage())
        val changedPolicy = database.configure(old.id, "Custom", old.url, 24, setOf("https://example.org/files/"), emptySet())
        assertFalse(changedPolicy.fresh(now)); assertNull(changedPolicy.etag)
        assertFalse(changedPolicy.due(now + 1))
        val moved = database.configure(old.id, "Custom", "https://example.net/catalog.json", 24, emptySet(), emptySet())
        assertNotSame(old.id, moved.id); assertTrue(database.source(old.id).deleted)
        assertEquals(old.id, library.list().single().entry.sourceId)
        assertEquals(entry.sha256, library.load(entry.key, database.catalog(now)).second.sha256)
    }

    fun testSharedBytesAreRetainedUntilTheLastLocalReferenceIsRemoved() {
        val library = LocalLibrary(File(directory, "library"), database)
        val one = localEntry(); val two = one.copy(sourceId = "awesome-fm1")
        library.save(one, testPackage()); library.save(two, testPackage())
        library.remove(one.key)
        assertEquals(two.sha256, library.load(two.key, null).second.sha256)
        library.remove(two.key)
        assertFalse(File(directory, "library/packages/${one.sha256}.fwsc").exists())
    }

    fun testRetryAfterAndReenableCannotResetSourceRequestBudget() {
        var calls = 0
        val repository = CatalogRepository(database) { _, _ -> calls++; CatalogResponse(429, retryAfter = "172800") }
        repository.load(now)
        database.setEnabled(CatalogSource.HUB, false); database.setEnabled(CatalogSource.HUB, true)
        repository.load(now + CatalogRepository.DAY)
        assertEquals(1, calls)
        repository.load(now + 2 * CatalogRepository.DAY)
        assertEquals(2, calls)
    }

    fun testVersionOneUpgradeRenamesOnlyTheBuiltInSourceAndPreservesItsState() {
        val id = CatalogSource.awesome.id
        val original = CatalogSource.awesome.copy(name = "awesome-fm1", enabled = true, revision = 9,
            intervalHours = 48, attemptedAt = now, nextAttemptAt = now + 48 * CatalogSource.HOUR,
            snapshot = true, checkedAt = now, etag = "preserved", error = "previous error")
        database.putSource(original)
        val custom = database.configure(null, "awesome-fm1", "https://example.org/catalog.json", 24, emptySet(), emptySet())
        val entry = localEntry().copy(sourceId = id)
        val library = LocalLibrary(File(directory, "library"), database)
        library.save(entry, testPackage())
        database.saveSettings(LibrarySettings(setOf(entry.key), true, entry.key))
        database.writableDatabase.version = 1
        database.close()
        database = CatalogDatabase(instrumentation.targetContext, databaseName)
        assertEquals(original.copy(name = "Awesome FM-1"), database.source(id))
        assertEquals(custom, database.source(custom.id))
        assertEquals(LibrarySettings(setOf(entry.key), true, entry.key), database.settings())
        assertEquals(entry.sha256, LocalLibrary(File(directory, "library"), database).load(entry.key, null).second.sha256)
        assertEquals(2, database.readableDatabase.version)
    }

}
