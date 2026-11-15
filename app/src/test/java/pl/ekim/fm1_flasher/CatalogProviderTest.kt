package pl.ekim.fm1_flasher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogProviderTest {
    private val raw = testPackage()
    private val hash = FirmwarePackage.sha256(raw)
    private val repo = CatalogSource("custom", "My FM-1", "https://github.com/owner/repository", provider = CatalogProviderType.REPO_URL)
    private fun release(digest: Boolean = true) = JSONObject().put("tag_name", "v1").put("published_at", "2026-11-15T00:00:00Z").put("prerelease", true)
        .put("assets", JSONArray().put(JSONObject().put("id", 10).put("name", "test.fwsc").put("size", raw.size).put("updated_at", "2026-11-15")
            .apply { if (digest) put("digest", "sha256:$hash") }))
    @Test fun githubReleasesUsePinnedAssetApiAndUnknownChipCompatibilityIsNotGuessed() {
        val response = CatalogResponse(200, JSONArray().put(release()).toString().toByteArray())
        var calls = 0
        val snapshot = RepositoryUrlProvider.decode(repo, response, emptyList()) { url, headers ->
            calls++; assertEquals("https://api.github.com/repos/owner/repository/releases/assets/10", url)
            assertEquals("bytes=0-959", headers["Range"]); assertEquals("application/octet-stream", headers["Accept"])
            CatalogResponse(206, raw.copyOfRange(0, 960))
        }
        val entry = snapshot.entries!!.single()
        assertEquals(hash, entry.sha256); assertEquals("FM-1_900", entry.product)
        assertEquals(repo.id, entry.sourceId); assertEquals("unknown", entry.chip)
        assertTrue(FlashChip.entries.none { it.allows(entry) }); assertEquals(1, calls)
        RepositoryUrlProvider.decode(repo, response, snapshot.entries!!) { _, _ -> error("Unchanged asset must reuse metadata") }
    }
    @Test fun olderGithubAssetsWithoutDigestAreInspectedAndHashed() {
        val response = CatalogResponse(200, JSONArray().put(release(false)).toString().toByteArray())
        val snapshot = RepositoryUrlProvider.decode(repo.copy(packageChips = mapOf(hash to "old")), response, emptyList()) { _, headers ->
            assertNull(headers["Range"]); CatalogResponse(200, raw)
        }
        assertEquals(hash, snapshot.entries!!.single().sha256)
        assertTrue(FlashChip.OLD.allows(snapshot.entries!!.single()))
        assertFalse(FlashChip.NEW.allows(snapshot.entries!!.single()))
    }
    @Test fun directPackageProviderRejectsCorruptionAndUsesActualPackageIdentity() {
        val source = repo.copy(url = "https://publisher.example/file.fwsc", provider = CatalogProviderType.PKG_URL)
        val snapshot = PackageUrlProvider.decode(source, CatalogResponse(200, raw), emptyList()) { _, _ -> error("Unexpected request") }
        assertEquals(source.url, snapshot.entries!!.single().directUrl)
        assertEquals(hash, snapshot.entries!!.single().sha256)
        assertThrows(IllegalArgumentException::class.java) {
            PackageUrlProvider.decode(source, CatalogResponse(200, raw.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }), emptyList()) { _, _ -> error("Unexpected request") }
        }
    }
    @Test fun repositoryUrlsAndProviderRequestScopesAreValidated() {
        assertEquals("owner/repository", RepositoryUrlProvider.repository("https://github.com/owner/repository.git"))
        assertThrows(IllegalArgumentException::class.java) { RepositoryUrlProvider.repository("https://evil.example/owner/repository") }
        assertTrue(CatalogProviders.permits(repo, "https://api.github.com/repos/owner/repository/releases?page=2"))
        assertFalse(CatalogProviders.permits(repo, "https://api.github.com/repos/other/repository/releases"))
        assertEquals("https://api.github.com/", CatalogProviders.authenticationScope(repo))
    }
}
