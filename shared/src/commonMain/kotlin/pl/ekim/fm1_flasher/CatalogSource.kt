package pl.ekim.fm1_flasher

data class CatalogSource(
    val id: String, val name: String, val url: String,
    val enabled: Boolean = false, val builtin: Boolean = false, val deleted: Boolean = false,
    val intervalHours: Int = 8, val packageScopes: Set<String> = emptySet(), val artworkScopes: Set<String> = emptySet(),
    val revision: Long = 0, val attemptedAt: Long = 0, val checkedAt: Long = 0,
    val nextAttemptAt: Long = 0, val snapshot: Boolean = false, val updated: String = "",
    val etag: String? = null, val modified: String? = null, val error: String? = null,
    val license: String = "", val entryCount: Int = 0,
    val provider: CatalogProviderType = CatalogProviderType.legacy(id),
    val authMethod: HttpAuthMethod = HttpAuthMethod.NONE, val authUsername: String = "", val authHeader: String = "X-API-Key",
    val oauth: OAuthConfiguration = OAuthConfiguration(), val packageChips: Map<String, String> = emptyMap(), val authRevision: Long = 0,
    val serverRetryAt: Long = 0,
) {
    val baseUrl: String get() = catalogBaseUrl(url)
    val active get() = enabled && !deleted
    // The Firmware Hub permits catalog requests at most once per day.
    val isFirmwareHub: Boolean get() = provider == CatalogProviderType.THE_FIRMWARE_HUB ||
        url.substringBefore('?').trimEnd('/').equals("https://thefirmwarehub.com/catalog.json", true)
    val effectiveIntervalHours: Int get() = if (isFirmwareHub) maxOf(intervalHours, 24) else intervalHours
    fun fresh(now: Long = epochMillis()) = snapshot && checkedAt > 0 && now >= checkedAt &&
        now - checkedAt < effectiveIntervalHours * HOUR
    fun due(now: Long = epochMillis(), manualExperimental: Boolean = false): Boolean {
        if (attemptedAt > now) return false
        val scheduledAt = if (attemptedAt == 0L) 0L else attemptedAt + effectiveIntervalHours * HOUR
        val serverDelay = now < serverRetryAt || nextAttemptAt > scheduledAt && now < nextAttemptAt
        if (serverDelay) return false
        return if (manualExperimental && !isFirmwareHub) true else now >= nextAttemptAt && now >= scheduledAt
    }
    fun permitsPackage(url: String) = (provider == CatalogProviderType.PKG_URL && url == this.url) || packageScopes.any { UrlScope.contains(it, url) }
    fun permitsArtwork(url: String) = artworkScopes.any { UrlScope.contains(it, url) }

    companion object {
        const val HUB = "firmwarehub"
        const val HOUR = 3_600_000L
        val hub get() = CatalogSource(HUB, "The Firmware Hub", "https://thefirmwarehub.com/catalog.json",
            enabled = true, builtin = true, intervalHours = 24, artworkScopes = setOf("https://thefirmwarehub.com/"))
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
