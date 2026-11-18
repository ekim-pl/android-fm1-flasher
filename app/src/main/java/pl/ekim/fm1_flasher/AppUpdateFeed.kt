package pl.ekim.fm1_flasher

import org.json.JSONArray
import org.json.JSONObject

internal object AppUpdateFeed {
    const val FDROID_REPO = "https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo"
    const val FDROID_HELP = "https://ekim-pl.github.io/android-fm1-flasher/#f-droid"
    private const val GITHUB_REPO = "https://github.com/ekim-pl/android-fm1-flasher"
    private const val GITHUB_API = "https://api.github.com/repos/ekim-pl/android-fm1-flasher/releases"
    private const val NIGHTLY_BASE = 1_000_000
    private const val LAST_VERSION_CODE = 2_100_000_000
    private val fingerprint = Regex("[0-9a-fA-F]{64}")
    private val stableVersion = Regex("[0-9]+[.][0-9]+[.][0-9]+")
    private val nightlyTag = Regex("nightly-([1-9][0-9]{6,9})")

    data class Release(val code: Int, val version: String, val certificate: String, val repoFingerprint: String? = null) {
        val nightlyPage: String get() = "$GITHUB_REPO/releases/tag/nightly-$code"
        val nightlyApk: String get() = "fm1-flasher-$version-debug.apk"

        fun view(channel: AppUpdateChannel, installedCode: Long, signers: Set<String>): AppUpdate {
            val status = when {
                code <= installedCode -> AppUpdateStatus.CURRENT
                signers.none { it.equals(certificate, ignoreCase = true) } -> AppUpdateStatus.INCOMPATIBLE
                else -> AppUpdateStatus.AVAILABLE
            }
            return AppUpdate(channel, status, version)
        }
    }

    fun parse(raw: String, channel: AppUpdateChannel, packageName: String): Release {
        JsonLimits.check(raw)
        val json = JSONObject(raw)
        require(json.optInt("schema") == 1 && json.optString("package") == packageName)
        val code = json.get("version_code")
        require(code is Int && code in 1..LAST_VERSION_CODE)
        val version = json.getString("version_name")
        val cert = json.getString("certificate_sha256")
        require(fingerprint.matches(cert))
        return when (channel) {
            AppUpdateChannel.FDROID -> {
                require(json.optString("variant") == "fdroid" && stableVersion.matches(version))
                val repoCert = json.getString("repo_fingerprint")
                require(fingerprint.matches(repoCert))
                Release(code, version, cert, repoCert)
            }
            AppUpdateChannel.NIGHTLY -> {
                require(json.optString("variant") == "debug" && code > NIGHTLY_BASE)
                val suffix = "-nightly.${code - NIGHTLY_BASE}"
                require(version.endsWith(suffix) && stableVersion.matches(version.removeSuffix(suffix)))
                Release(code, version, cert).also { require(json.optString("file") == it.nightlyApk) }
            }
        }
    }

    fun fetch(channel: AppUpdateChannel, packageName: String, active: () -> Boolean): Pair<String, Release> {
        if (channel == AppUpdateChannel.FDROID) {
            val url = "$FDROID_REPO/update.json"
            val raw = read(url, 16 * 1024, setOf("ekim-pl.github.io"), active)
            return raw to parse(raw, channel, packageName)
        }
        // A nightly has its own release, so its APK and manifest can be published together.
        for (page in 1..3) {
            val raw = read("$GITHUB_API?per_page=20&page=$page", 512 * 1024, setOf("api.github.com"), active)
            val releases = releaseList(raw)
            val candidate = latestNightly(releases)
            if (candidate != null) {
                val (code, assets) = candidate
                val url = "$GITHUB_REPO/releases/download/nightly-$code/debug-manifest.json"
                val manifest = read(url, 16 * 1024, setOf("github.com", "release-assets.githubusercontent.com",
                    "objects.githubusercontent.com"), active)
                val release = parse(manifest, channel, packageName)
                require(release.code == code && release.nightlyApk in assets)
                return manifest to release
            }
            if (releases.length() < 20) break
        }
        error("No published nightly builds")
    }

    internal fun latestNightly(releases: JSONArray): Pair<Int, Set<String>>? =
        (0 until releases.length()).mapNotNull { index ->
            val release = releases.getJSONObject(index)
            val tag = nightlyTag.matchEntire(release.optString("tag_name"))
            val code = tag?.groupValues?.get(1)?.toIntOrNull()
            if (code == null || code !in (NIGHTLY_BASE + 1)..LAST_VERSION_CODE ||
                release.optBoolean("draft") || !release.optBoolean("prerelease")) return@mapNotNull null
            val assets = release.getJSONArray("assets")
            val names = (0 until assets.length()).mapNotNull { asset ->
                val entry = assets.getJSONObject(asset)
                entry.getString("name").takeIf { entry.optString("state") == "uploaded" }
            }.toSet()
            (code to names).takeIf { "debug-manifest.json" in names && names.any { it.endsWith("-debug.apk") } }
        }.maxByOrNull { it.first }

    private fun releaseList(raw: String): JSONArray {
        JsonLimits.check(raw)
        return JSONArray(raw)
    }

    private fun read(url: String, limit: Int, hosts: Set<String>, active: () -> Boolean): String {
        val response = SourceNetwork.request(url, limit, { Network.allowed(it, hosts) }, active = active)
        check(response.code == 200) { "Update metadata request returned HTTP ${response.code}" }
        return response.bytes.decodeToString()
    }
}
