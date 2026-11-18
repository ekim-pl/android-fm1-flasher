package pl.ekim.fm1_flasher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppUpdateFeedTest {
    private val packageName = "pl.ekim.fm1_flasher"
    private val signer = "a".repeat(64)
    private val repoSigner = "b".repeat(64)

    @Test fun fdroidComparesVersionCodesAndRequiresCompatibleSignature() {
        val data = metadata("fdroid", 43, "0.10.3").put("repo_fingerprint", repoSigner)
        val release = AppUpdateFeed.parse(data.toString(), AppUpdateChannel.FDROID, packageName)
        assertEquals(repoSigner, release.repoFingerprint)
        assertEquals(AppUpdateStatus.CURRENT, release.view(AppUpdateChannel.FDROID, 43, setOf(signer)).status)
        assertEquals(AppUpdateStatus.CURRENT, release.view(AppUpdateChannel.FDROID, 44, setOf(signer)).status)
        assertEquals(AppUpdateStatus.AVAILABLE, release.view(AppUpdateChannel.FDROID, 42, setOf(signer.uppercase())).status)
        assertEquals(AppUpdateStatus.INCOMPATIBLE, release.view(AppUpdateChannel.FDROID, 42, setOf(repoSigner)).status)
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateFeed.parse(data.toString(), AppUpdateChannel.FDROID, "other.package")
        }
    }

    @Test fun nightlySelectsOnlyCompletePublishedPrereleases() {
        val releases = JSONArray().put(nightly("v0.10.3")).put(nightly("nightly-1000007", draft = true))
            .put(nightly("nightly-1000006", complete = false)).put(nightly("nightly-1000002"))
            .put(nightly("nightly-1000004")).put(nightly("nightly-1000005", prerelease = false))
        assertEquals(1_000_004, AppUpdateFeed.latestNightly(releases)?.first)
    }

    @Test fun nightlyManifestMustMatchItsBuildNumberAndAssetName() {
        val data = metadata("debug", 1_000_002, "0.10.2-nightly.2")
            .put("file", "fm1-flasher-0.10.2-nightly.2-debug.apk")
        val release = AppUpdateFeed.parse(data.toString(), AppUpdateChannel.NIGHTLY, packageName)
        assertEquals("https://github.com/ekim-pl/android-fm1-flasher/releases/tag/nightly-1000002", release.nightlyPage)
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateFeed.parse(data.put("version_code", 1_000_003).toString(), AppUpdateChannel.NIGHTLY, packageName)
        }
    }

    private fun metadata(variant: String, code: Int, version: String) = JSONObject().put("schema", 1)
        .put("package", packageName).put("variant", variant).put("version_code", code)
        .put("version_name", version).put("certificate_sha256", signer)

    private fun nightly(tag: String, draft: Boolean = false, prerelease: Boolean = true, complete: Boolean = true): JSONObject {
        val assets = JSONArray().put(JSONObject().put("name", "debug-manifest.json").put("state", "uploaded"))
        if (complete) assets.put(JSONObject().put("name", "fm1-flasher-0.10.2-debug.apk").put("state", "uploaded"))
        return JSONObject().put("tag_name", tag).put("prerelease", prerelease).put("draft", draft).put("assets", assets)
    }
}
