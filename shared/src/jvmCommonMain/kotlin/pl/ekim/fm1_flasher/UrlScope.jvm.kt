package pl.ekim.fm1_flasher

import java.net.URI
import java.util.Locale

/** Grants are configured locally, never read from the downloaded catalog. */
actual object UrlScope {
    actual fun canonical(address: String): String {
        val uri = try { URI(address.trim()) } catch (_: java.net.URISyntaxException) { invalid(msg("source_invalid_url")) }
        require(uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null &&
            uri.port in listOf(-1, 443) && uri.rawFragment == null && uri.rawQuery == null &&
            !uri.rawPath.contains('\\') && !Regex("(?i)%(2e|2f|5c|25)").containsMatchIn(uri.rawPath)) {
            invalid(msg("source_invalid_url"))
        }
        val normalized = uri.normalize()
        val host = uri.host.lowercase(Locale.ROOT).trimEnd('.')
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") &&
            !host.contains(':') && !Regex("[0-9.]+").matches(host)) { invalid(msg("source_invalid_url")) }
        return "https://$host" + normalized.rawPath.ifEmpty { "/" }
    }

    actual fun contains(scope: String, address: String): Boolean = runCatching {
        val a = URI(address)
        val s = URI(canonical(scope))
        val clean = URI(canonical(URI(a.scheme, a.authority, a.path, null, null).toString()))
        a.userInfo == null && a.rawFragment == null && a.port in listOf(-1, 443) &&
            !Regex("(?i)%(2e|2f|5c|25)").containsMatchIn(a.rawPath) &&
            clean.host == s.host && (clean.path == s.path || s.path.endsWith('/') && clean.path.startsWith(s.path))
    }.getOrDefault(false)
}

internal actual fun catalogBaseUrl(url: String) = URI(url).resolve(".").toString()

actual fun formatCatalogTime(epoch: Long): String = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(epoch))
