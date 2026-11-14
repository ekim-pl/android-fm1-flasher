package pl.ekim.fm1_flasher

import org.json.JSONObject

class CatalogRepository(private val database: CatalogDatabase,
    private val fetch: (CatalogSource, Map<String, String>) -> CatalogResponse = { source, headers ->
        SourceNetwork.request(source.url, 4 * 1024 * 1024, { UrlScope.contains(source.url, it) }, headers, active = {
            !database.settings().offline && database.source(source.id).let { !it.deleted && it.revision == source.revision }
        })
    },
) {
    companion object { const val DAY = 24 * CatalogSource.HOUR }
    fun cached(now: Long = System.currentTimeMillis()) = database.catalog(now)

    fun load(now: Long = System.currentTimeMillis(), sourceId: String? = null): Catalog {
        check(!database.settings().offline) { failed(msg(R.string.offline_refresh_blocked)) }
        val sources = database.sources().filter { !it.deleted && (if (sourceId == null) it.active else it.id == sourceId) }
        sources.forEach { original ->
            if (!original.due(now)) return@forEach
            val attempted = original.copy(attemptedAt = now, nextAttemptAt = now + original.intervalHours * CatalogSource.HOUR)
            database.putSource(attempted)
            try {
                val response = fetch(attempted, CatalogSync.headers(original))
                if (response.code !in listOf(200, 304)) {
                    val current = database.source(original.id)
                    if (current.revision == original.revision) database.putSource(current.copy(
                        nextAttemptAt = maxOf(attempted.nextAttemptAt, CatalogSync.retryAt(response.retryAfter, now))))
                }
                val snapshot = CatalogSync.decode(attempted, response)
                database.commitSnapshot(attempted, snapshot.entries, snapshot.updated, snapshot.license,
                    now, snapshot.etag, snapshot.modified)
            } catch (e: Exception) {
                val current = database.source(original.id)
                if (current.revision == original.revision) database.putSource(current.copy(error =
                    database.describe(e).take(500)))
            }
        }
        return cached(now)
    }

    fun download(entry: CatalogEntry): ByteArray {
        check(!database.settings().offline) { failed(msg(R.string.download_offline_blocked)) }
        val catalog = load()
        val current = catalog.entries.singleOrNull { it.key == entry.key && it.sha256 == entry.sha256 }
        check(current != null && catalog.canDownload(current)) { failed(msg(R.string.download_stale)) }
        val source = database.source(entry.sourceId)
        fun active() = !database.settings().offline && database.source(source.id).let { it.active && it.revision == source.revision }
        val cdnHosts = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
        val url: String
        val permitted: (String) -> Boolean
        if (current.directUrl != null) {
            url = checkNotNull(current.directUrl)
            val pinnedStock = source.id == CatalogSource.HUB && Recovery.eligible(current)
            check(pinnedStock || source.id != CatalogSource.HUB && source.permitsPackage(url)) { failed(msg(R.string.network_url_blocked)) }
            permitted = if (pinnedStock) { address -> address == url } else source::permitsPackage
        } else {
            check(current.tag != null && current.assetId != null) { failed(msg(R.string.release_not_identified)) }
            val tag = java.net.URLEncoder.encode(current.tag, "UTF-8").replace("+", "%20")
            val api = "https://api.github.com/repos/${current.repo}/releases/tags/$tag"
            val metadata = SourceNetwork.request(api, 2 * 1024 * 1024, { it == api }, active = ::active)
            check(metadata.code == 200) { failed(msg(R.string.http_error, metadata.code)) }
            val json = metadata.bytes.toString(Charsets.UTF_8); JsonLimits.check(json)
            val assets = JSONObject(json).getJSONArray("assets")
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
                it.getLong("id").toString() == current.assetId && it.getString("name").endsWith(".fwsc", true)
            }
            check(matches.size == 1) { failed(msg(R.string.release_asset_missing)) }
            url = matches.single().getString("browser_download_url")
            check(CatalogParser.isReleaseDownload(url, current.repo)) { failed(msg(R.string.release_url_invalid)) }
            permitted = { address -> address == url || Network.allowed(address, cdnHosts) }
        }
        val response = SourceNetwork.request(url, FirmwarePackage.MAX_SIZE, permitted, active = ::active)
        check(response.code == 200) { failed(msg(R.string.http_error, response.code)) }
        check(active()) { failed(msg(R.string.source_changed)) }
        FirmwarePackage.parse(response.bytes, current.sha256, current.product)
        return response.bytes
    }
}
