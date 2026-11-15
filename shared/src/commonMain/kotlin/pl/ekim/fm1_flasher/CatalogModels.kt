package pl.ekim.fm1_flasher

enum class FlashChip(val labelKey: String) {
    UNKNOWN("chip_unknown"), NEW("chip_new"), OLD("chip_old");
    fun allows(entry: CatalogEntry) = entry.chip == "new" || (this == OLD && (entry.chip == "old" || (entry.stock && StockFirmware.eligible(entry))))
}

data class CatalogEntry(
    val id: String, val name: String, val author: String, val description: String,
    val version: String, val repo: String, val tag: String?, val assetId: String?,
    val sha256: String, val product: String, val chip: String,
    val beta: Boolean, val stock: Boolean = false, val directUrl: String? = null,
    val kinds: Set<String> = setOf("other"), val date: String = "", val cardUrl: String? = null,
    val sourceId: String = CatalogSource.HUB, val site: String? = null, val originalJson: String = "",
) {
    val dateMillis get() = CatalogDates.millis(date)
    val key get() = CatalogSource.key(sourceId, id)
    val sourceUrl get() = site ?: if (repo.isNotEmpty()) "https://github.com/$repo" else ""
    val downloadable get() = chip != "unsupported" && (directUrl != null || (repo.isNotEmpty() && tag != null && assetId != null))
    val shortDescription get(): String {
        val clean = description.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= 165) return clean
        val sentence = clean.indexOf(". ")
        if (sentence in 40..164) return clean.take(sentence + 1)
        return clean.take(162).substringBeforeLast(' ', clean.take(162)).trimEnd('.', ',') + "…"
    }
}

data class Catalog(val entries: List<CatalogEntry>, val updated: String, val fetchedAt: Long, val fresh: Boolean,
    val sources: List<CatalogSource> = emptyList(), val knownEntries: List<CatalogEntry> = entries,
    val removedKeys: Set<String> = emptySet(),
) {
    fun source(entry: CatalogEntry) = sources.singleOrNull { it.id == entry.sourceId }
    fun downloadBlock(entry: CatalogEntry, now: Long = epochMillis()): AppText? = when {
        removed(entry) -> msg("firmware_removed")
        conflicting(entry) -> msg("source_conflict")
        !entry.downloadable -> msg("release_not_identified")
        sources.isNotEmpty() && source(entry)?.active != true -> msg("source_disabled")
        !(if (sources.isEmpty()) fresh else source(entry)?.fresh(now) == true) -> msg("download_stale")
        entry.directUrl != null && sources.isNotEmpty() &&
            !(entry.sourceId == CatalogSource.HUB && StockFirmware.eligible(entry)) &&
            source(entry)?.permitsPackage(entry.directUrl) != true -> msg("network_url_blocked")
        else -> null
    }
    fun canDownload(entry: CatalogEntry, now: Long = epochMillis()) = downloadBlock(entry, now) == null
    fun removed(entry: CatalogEntry) = entry.key in removedKeys ||
        (if (sources.isEmpty()) knownEntries.none { it.key == entry.key }
         else source(entry)?.snapshot == true && knownEntries.none { it.key == entry.key })
    fun conflicting(entry: CatalogEntry) = knownEntries.any { it.sha256 == entry.sha256 &&
        it.key !in removedKeys && (it.product != entry.product || it.chip in listOf("old", "new") && entry.chip in listOf("old", "new") && it.chip != entry.chip) }
}

object StockFirmware {
    const val V15_PACKAGE = "db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a"
    fun eligible(entry: CatalogEntry?) = entry?.sha256 == V15_PACKAGE && entry.product == "FM-1_015"
}
object CatalogDates { fun millis(value: String): Long = catalogDateMillis(value) }
internal expect fun catalogDateMillis(value: String): Long
