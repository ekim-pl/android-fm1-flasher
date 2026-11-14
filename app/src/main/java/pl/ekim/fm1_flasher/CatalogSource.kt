package pl.ekim.fm1_flasher

import java.net.URI
import java.util.Locale

data class CatalogSource(
    val id: String, val name: String, val url: String,
    val enabled: Boolean = false, val builtin: Boolean = false, val deleted: Boolean = false,
    val intervalHours: Int = 24, val packageScopes: Set<String> = emptySet(), val artworkScopes: Set<String> = emptySet(),
    val revision: Long = 0, val attemptedAt: Long = 0, val checkedAt: Long = 0,
    val nextAttemptAt: Long = 0, val snapshot: Boolean = false, val updated: String = "",
    val etag: String? = null, val modified: String? = null, val error: String? = null,
    val license: String = "", val entryCount: Int = 0,
) {
    val baseUrl: String get() = URI(url).resolve(".").toString()
    val active get() = enabled && !deleted
    fun fresh(now: Long = System.currentTimeMillis()) = snapshot && checkedAt > 0 && now >= checkedAt &&
        now - checkedAt < intervalHours * HOUR
    fun due(now: Long = System.currentTimeMillis()) = now >= nextAttemptAt &&
        (attemptedAt == 0L || now >= attemptedAt && now - attemptedAt >= intervalHours * HOUR)
    fun permitsPackage(url: String) = packageScopes.any { UrlScope.contains(it, url) }
    fun permitsArtwork(url: String) = artworkScopes.any { UrlScope.contains(it, url) }

    companion object {
        const val HUB = "firmwarehub"
        const val HOUR = 3_600_000L
        val hub get() = CatalogSource(HUB, "The Firmware Hub", "https://thefirmwarehub.com/catalog.json",
            enabled = true, builtin = true, artworkScopes = setOf("https://thefirmwarehub.com/"))
        val awesome get() = CatalogSource("awesome-fm1", "Awesome FM-1",
            "https://raw.githubusercontent.com/ekim-pl/awesome-fm1/main/catalog.json", builtin = true,
            packageScopes = setOf("https://shrub.audio/editor/firmware/"))
        fun key(source: String, id: String) = "$source/$id"
    }
}

/** Grants are configured locally, never read from the downloaded catalog. */
object UrlScope {
    fun canonical(address: String): String {
        val uri = URI(address.trim())
        require(uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null &&
            uri.port in listOf(-1, 443) && uri.rawFragment == null && uri.rawQuery == null &&
            !uri.rawPath.contains('\\') && !Regex("(?i)%(2e|2f|5c|25)").containsMatchIn(uri.rawPath)) {
            invalid(msg(R.string.source_invalid_url))
        }
        val normalized = uri.normalize()
        val host = uri.host.lowercase(Locale.ROOT).trimEnd('.')
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") &&
            !host.contains(':') && !Regex("[0-9.]+").matches(host)) { invalid(msg(R.string.source_invalid_url)) }
        return "https://$host" + normalized.rawPath.ifEmpty { "/" }
    }

    fun contains(scope: String, address: String): Boolean = runCatching {
        val a = URI(address)
        val s = URI(canonical(scope))
        val clean = URI(canonical(URI(a.scheme, a.authority, a.path, null, null).toString()))
        a.userInfo == null && a.rawFragment == null && a.port in listOf(-1, 443) &&
            !Regex("(?i)%(2e|2f|5c|25)").containsMatchIn(a.rawPath) &&
            clean.host == s.host && (clean.path == s.path || s.path.endsWith('/') && clean.path.startsWith(s.path))
    }.getOrDefault(false)
}
