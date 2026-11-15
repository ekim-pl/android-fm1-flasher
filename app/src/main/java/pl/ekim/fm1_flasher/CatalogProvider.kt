package pl.ekim.fm1_flasher

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

internal abstract class JsonCatalogProvider : CatalogProvider {
    override fun decode(source: CatalogSource, response: CatalogResponse, previous: List<CatalogEntry>, get: (String, Map<String, String>) -> CatalogResponse) = CatalogSync.decode(source, response)
}
internal object FirmwareHubProvider : JsonCatalogProvider() { override val type = CatalogProviderType.THE_FIRMWARE_HUB }
internal object AwesomeFm1Provider : JsonCatalogProvider() { override val type = CatalogProviderType.AWESOME_FM1 }
internal object JsonUrlProvider : JsonCatalogProvider() { override val type = CatalogProviderType.JSON_URL }

internal object PackageUrlProvider : CatalogProvider {
    override val type = CatalogProviderType.PKG_URL
    override fun decode(source: CatalogSource, response: CatalogResponse, previous: List<CatalogEntry>, get: (String, Map<String, String>) -> CatalogResponse): CatalogSnapshot {
        if (response.code == 304) return CatalogSync.decode(source, response)
        check(response.code == 200) { failed(msg(R.string.http_error, response.code)) }
        val image = FirmwarePackage.inspect(response.bytes)
        val entry = CatalogEntry("package", source.name, "", "", image.identity.version, "", null, null,
            image.sha256, "FM-1_${image.identity.version}", source.packageChips[image.sha256] ?: "unknown", false, directUrl = source.url, sourceId = source.id, site = source.url)
        return CatalogSnapshot(listOf(entry), image.identity.version, "", response.etag, response.modified)
    }
}

internal fun githubError(code: Int): AppText = if (code in listOf(401, 403, 404))
    AppText.Joined(listOf(msg(R.string.http_error, code), msg("github_access_hint")))
else msg(R.string.http_error, code)

internal object RepositoryUrlProvider : CatalogProvider {
    override val type = CatalogProviderType.REPO_URL
    fun repository(address: String): String {
        val uri = URI(UrlScope.canonical(address))
        val repo = uri.path.trim('/').removeSuffix(".git")
        require(uri.host == "github.com" && CatalogParser.validRepo(repo)) { invalid(msg("provider_repo_invalid")) }
        return repo.lowercase(java.util.Locale.ROOT)
    }
    override fun endpoint(source: CatalogSource) = "https://api.github.com/repos/${repository(source.url)}/releases?per_page=100&page=1"
    override fun decode(source: CatalogSource, response: CatalogResponse, previous: List<CatalogEntry>, get: (String, Map<String, String>) -> CatalogResponse): CatalogSnapshot {
        if (response.code == 304) return CatalogSync.decode(source, response)
        val repo = repository(source.url)
        val entries = mutableListOf<CatalogEntry>()
        var updated = ""
        var page = 1
        var current = response
        while (true) {
            check(current.code == 200) { failed(githubError(current.code)) }
            val text = current.bytes.toString(Charsets.UTF_8); JsonLimits.check(text)
            val releases = JSONArray(text)
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft")) continue
                val tag = release.getString("tag_name")
                val assets = release.getJSONArray("assets")
                updated = maxOf(updated, release.optString("published_at"))
                for (n in 0 until assets.length()) {
                    val asset = assets.getJSONObject(n)
                    if (!asset.getString("name").endsWith(".fwsc", true)) continue
                    require(entries.size < 5000) { invalid(msg(R.string.catalog_too_large)) }
                    val id = asset.getLong("id").toString()
                    require(asset.getLong("size") in 960..FirmwarePackage.MAX_SIZE.toLong()) { invalid(msg(R.string.package_invalid_size)) }
                    val old = previous.singleOrNull { it.id == id }?.takeIf {
                        runCatching { JSONObject(it.originalJson).let { saved -> saved.optString("updated_at") == asset.optString("updated_at") && saved.getLong("size") == asset.getLong("size") && saved.optString("digest") == asset.optString("digest") } }.getOrDefault(false)
                    }
                    val hash: String
                    val product: String
                    if (old != null) { hash = old.sha256; product = old.product }
                    else {
                        val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                        val headers = buildMap { put("Accept", "application/octet-stream"); if (digest != null) put("Range", "bytes=0-959") }
                        val payload = get("https://api.github.com/repos/$repo/releases/assets/$id", headers)
                        check(payload.code in listOf(200, 206)) { failed(githubError(payload.code)) }
                        if (digest != null) { hash = digest; product = FirmwarePackage.productPrefix(payload.bytes) }
                        else { check(payload.code == 200); val image = FirmwarePackage.inspect(payload.bytes); hash = image.sha256; product = "FM-1_${image.identity.version}" }
                    }
                    require(product.matches(Regex("FM-1_[0-9]{1,16}"))) { invalid(msg(R.string.package_identity_invalid)) }
                    entries += CatalogEntry(id, asset.getString("name").removeSuffix(".fwsc"), repo.substringBefore('/'), release.optString("body").take(16384),
                        tag, repo, tag, id, hash, product, source.packageChips[hash] ?: "unknown", release.optBoolean("prerelease"), sourceId = source.id,
                        site = "https://github.com/$repo/releases", originalJson = asset.toString(), date = release.optString("published_at"))
                }
            }
            if (releases.length() < 100) break
            check(++page <= 20) { failed(msg(R.string.catalog_too_large)) }
            current = get("https://api.github.com/repos/$repo/releases?per_page=100&page=$page", mapOf("Accept" to "application/vnd.github+json"))
        }
        return CatalogSnapshot(entries, updated, "", response.etag, response.modified)
    }
}

internal object CatalogProviders {
    private val providers = listOf(FirmwareHubProvider, AwesomeFm1Provider, RepositoryUrlProvider, PackageUrlProvider, JsonUrlProvider).associateBy { it.type }
    fun get(source: CatalogSource): CatalogProvider = providers.getValue(source.provider)
    fun authenticationScope(source: CatalogSource): String {
        val endpoint = URI(get(source).endpoint(source))
        return "https://${endpoint.host}/"
    }
    fun permits(source: CatalogSource, address: String): Boolean {
        if (source.provider != CatalogProviderType.REPO_URL) return UrlScope.contains(source.url, address) || source.permitsPackage(address)
        val repo = RepositoryUrlProvider.repository(source.url)
        return UrlScope.contains("https://api.github.com/repos/$repo/releases", address) ||
            UrlScope.contains("https://api.github.com/repos/$repo/releases/", address) ||
            Network.allowed(address, setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com"))
    }
}
