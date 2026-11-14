package pl.ekim.fm1_flasher

data class CatalogSource(
    val id: String, val name: String, val url: String,
    val enabled: Boolean = false, val builtin: Boolean = false, val deleted: Boolean = false,
    val intervalHours: Int = 24, val packageScopes: Set<String> = emptySet(), val artworkScopes: Set<String> = emptySet(),
    val revision: Long = 0, val attemptedAt: Long = 0, val checkedAt: Long = 0,
    val nextAttemptAt: Long = 0, val snapshot: Boolean = false, val updated: String = "",
    val etag: String? = null, val modified: String? = null, val error: String? = null,
    val license: String = "", val entryCount: Int = 0,
) {
    val baseUrl: String get() = catalogBaseUrl(url)
    val active get() = enabled && !deleted
    fun fresh(now: Long = epochMillis()) = snapshot && checkedAt > 0 && now >= checkedAt &&
        now - checkedAt < intervalHours * HOUR
    fun due(now: Long = epochMillis()) = now >= nextAttemptAt &&
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

expect object UrlScope {
    fun canonical(address: String): String
    fun contains(scope: String, address: String): Boolean
}
internal expect fun catalogBaseUrl(url: String): String

expect fun formatCatalogTime(epoch: Long): String
