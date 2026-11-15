package pl.ekim.fm1_flasher

import org.json.JSONObject

class CatalogRepository(private val database: CatalogDatabase,
    private val report: (String, String) -> Unit = { event, message -> AndroidOperationLogger.get(database.context).record(event, message, if (event.endsWith("ERROR")) "ERROR" else "INFO") },
    private val authenticate: (CatalogSource) -> HttpCredentials? = { OAuthManager(SourceCredentialStore(database.context)).credentials(it) },
    private val request: (CatalogSource, String, Map<String, String>, () -> Boolean) -> CatalogResponse = { source, address, headers, active ->
        val scope = CatalogProviders.authenticationScope(source)
        val credentials = if (UrlScope.contains(scope, address)) authenticate(source) else null
        val effectiveHeaders = if (source.provider == CatalogProviderType.REPO_URL) headers + ("X-GitHub-Api-Version" to "2026-03-10") else headers
        SourceNetwork.request(address, FirmwarePackage.MAX_SIZE, { CatalogProviders.permits(source, it) }, effectiveHeaders,
            active = active, authentication = { target -> if (UrlScope.contains(scope, target)) credentials?.headers().orEmpty() else emptyMap() })
    },
    private val fetch: (CatalogSource, Map<String, String>) -> CatalogResponse = { source, headers ->
        request(source, CatalogProviders.get(source).endpoint(source), headers, {
            !database.settings().offline && database.source(source.id).let { !it.deleted && it.revision == source.revision }
        })
    },
) {
    private fun event(key: String, message: String) { runCatching { report(key, message) } }
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
                event("PROVIDER_CHECK", "${original.id} · ${original.provider.id}")
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(2)
                val initialHeaders = CatalogSync.headers(original) + if (attempted.provider == CatalogProviderType.REPO_URL)
                    mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2026-03-10") else emptyMap()
                val response = fetch(attempted, initialHeaders)
                if (response.code !in listOf(200, 304)) {
                    val current = database.source(original.id)
                    if (current.revision == original.revision) database.putSource(current.copy(
                        nextAttemptAt = maxOf(attempted.nextAttemptAt, CatalogSync.retryAt(response.retryAfter, now))))
                }
                val snapshot = CatalogProviders.get(attempted).decode(attempted, response, cached(now).knownEntries.filter { it.sourceId == original.id }) { address, headers ->
                    check(System.nanoTime() < deadline) { failed(msg(R.string.network_deadline)) }
                    request(attempted, address, headers, {
                        System.nanoTime() < deadline && !database.settings().offline && database.source(attempted.id).let { !it.deleted && it.revision == attempted.revision }
                    }).also { extra ->
                        if (extra.code !in listOf(200, 206, 304)) {
                            val current = database.source(attempted.id)
                            if (current.revision == attempted.revision) database.putSource(current.copy(nextAttemptAt = maxOf(current.nextAttemptAt, CatalogSync.retryAt(extra.retryAfter, now))))
                        }
                    }
                }
                database.commitSnapshot(attempted, snapshot.entries, snapshot.updated, snapshot.license,
                    now, snapshot.etag, snapshot.modified)
                event("PROVIDER_UPDATED", "${original.id} · entries=${snapshot.entries?.size ?: original.entryCount}")
            } catch (e: Exception) {
                val failure = if (e is org.json.JSONException) OperationFailed(msg(R.string.catalog_invalid)) else e
                event("PROVIDER_ERROR", "${original.id} · ${database.describe(failure)}")
                val current = database.source(original.id)
                if (current.revision == original.revision) database.putSource(current.copy(error =
                    database.describe(failure).take(500)))
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
        val authScope = CatalogProviders.authenticationScope(source)
        val credentials = authenticate(source)
        fun auth(address: String) = if (UrlScope.contains(authScope, address)) credentials?.headers().orEmpty() else emptyMap()
        fun active() = !database.settings().offline && database.source(source.id).let { it.active && it.revision == source.revision }
        val cdnHosts = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
        val url: String
        val permitted: (String) -> Boolean
        if (source.provider == CatalogProviderType.REPO_URL) {
            val repo = RepositoryUrlProvider.repository(source.url)
            check(current.repo == repo && current.assetId?.matches(Regex("[0-9]+")) == true) { failed(msg(R.string.release_not_identified)) }
            url = "https://api.github.com/repos/$repo/releases/assets/${current.assetId}"
            permitted = { address -> address == url || Network.allowed(address, cdnHosts) }
        } else if (current.directUrl != null) {
            url = checkNotNull(current.directUrl)
            val pinnedStock = source.id == CatalogSource.HUB && Recovery.eligible(current)
            check(pinnedStock || source.id != CatalogSource.HUB && (source.permitsPackage(url) || source.provider == CatalogProviderType.PKG_URL && url == source.url)) { failed(msg(R.string.network_url_blocked)) }
            permitted = if (pinnedStock) { address -> address == url } else { address -> source.permitsPackage(address) || source.provider == CatalogProviderType.PKG_URL && address == source.url }
        } else {
            check(current.tag != null && current.assetId != null) { failed(msg(R.string.release_not_identified)) }
            val tag = java.net.URLEncoder.encode(current.tag, "UTF-8").replace("+", "%20")
            val api = "https://api.github.com/repos/${current.repo}/releases/tags/$tag"
            val metadata = SourceNetwork.request(api, 2 * 1024 * 1024, { it == api }, active = ::active, authentication = ::auth)
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
        val downloadHeaders = if (source.provider == CatalogProviderType.REPO_URL) mapOf("Accept" to "application/octet-stream", "X-GitHub-Api-Version" to "2026-03-10") else emptyMap()
        val response = SourceNetwork.request(url, FirmwarePackage.MAX_SIZE, permitted, downloadHeaders, active = ::active, authentication = ::auth)
        check(response.code == 200) { failed(if (source.provider == CatalogProviderType.REPO_URL) githubError(response.code) else msg(R.string.http_error, response.code)) }
        check(active()) { failed(msg(R.string.source_changed)) }
        FirmwarePackage.parse(response.bytes, current.sha256, current.product)
        return response.bytes
    }
}
