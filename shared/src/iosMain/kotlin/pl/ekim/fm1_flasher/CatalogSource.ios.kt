package pl.ekim.fm1_flasher

import platform.Foundation.*

actual object UrlScope {
    actual fun canonical(address: String): String {
        val match = Regex("(?i)https://([a-z0-9.-]+)(?::443)?(/[^?#]*)?").matchEntire(address.trim())
            ?: invalid(msg("source_invalid_url"))
        val host = match.groupValues[1].lowercase().trimEnd('.')
        val path = match.groupValues[2].ifEmpty { "/" }
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") &&
            !host.matches(Regex("[0-9.]+")) && '\\' !in path && !Regex("(?i)%(2e|2f|5c|25)").containsMatchIn(path)) { invalid(msg("source_invalid_url")) }
        val parts = mutableListOf<String>()
        for (part in path.split('/')) when(part) { "", "." -> Unit; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts += part }
        return "https://$host/" + parts.joinToString("/") + if (path.endsWith('/') && parts.isNotEmpty()) "/" else ""
    }
    actual fun contains(scope: String, address: String): Boolean = runCatching {
        require('#' !in address)
        val s = canonical(scope); val a = canonical(address.substringBefore('?'))
        a == s || s.endsWith('/') && a.startsWith(s)
    }.getOrDefault(false)
}
internal actual fun catalogBaseUrl(url: String) = UrlScope.canonical(url).substringBeforeLast('/') + "/"
internal actual fun catalogDateMillis(value: String): Long {
    val iso = NSISO8601DateFormatter()
    iso.dateFromString(value)?.let { return (it.timeIntervalSince1970 * 1000).toLong() }
    for (pattern in listOf("yyyy-MM-dd", "yyyy-MM-dd HH:mm 'UTC'", "yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX")) {
        val format = NSDateFormatter().apply { dateFormat = pattern; locale = NSLocale("en_US_POSIX"); timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0) }
        format.dateFromString(value)?.let { return (it.timeIntervalSince1970 * 1000).toLong() }
    }
    return 0
}

actual fun formatCatalogTime(epoch: Long): String = NSDateFormatter().apply {
    dateStyle = NSDateFormatterMediumStyle; timeStyle = NSDateFormatterShortStyle
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epoch / 1000.0))
