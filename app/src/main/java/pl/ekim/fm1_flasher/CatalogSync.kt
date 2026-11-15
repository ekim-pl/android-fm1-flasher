package pl.ekim.fm1_flasher

import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Response validation is independent of Android/SQLite; a snapshot is committed only after this succeeds. */
internal object CatalogSync {
    fun headers(source: CatalogSource): Map<String, String> = if (!source.snapshot) emptyMap() else buildMap {
        source.etag?.let { put("If-None-Match", it) }
        source.modified?.let { put("If-Modified-Since", it) }
    }
    fun decode(source: CatalogSource, response: CatalogResponse): CatalogSnapshot {
        if (response.code == 304) {
            check(source.snapshot && (source.etag != null || source.modified != null)) { failed(msg(R.string.catalog_invalid)) }
            return CatalogSnapshot(null, source.updated, source.license, response.etag ?: source.etag, response.modified ?: source.modified)
        }
        check(response.code == 200) { failed(msg(R.string.http_error, response.code)) }
        check(response.bytes.size <= 4 * 1024 * 1024) { failed(msg(R.string.catalog_too_large)) }
        val json = response.bytes.toString(Charsets.UTF_8)
        val (entries, updated) = CatalogParser.parse(json, source)
        return CatalogSnapshot(entries, updated, JSONObject(json).opt("license")?.toString().orEmpty(), response.etag, response.modified)
    }
    fun retryAt(value: String?, now: Long): Long = value?.let {
        it.toLongOrNull()?.coerceIn(0, 30 * 86400)?.let { seconds -> now + seconds * 1000 }
            ?: runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
    } ?: 0
}
