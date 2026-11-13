package pl.ekim.fm1_flasher

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class FlashChip(val label: Int) {
    UNKNOWN(R.string.chip_unknown), NEW(R.string.chip_new), OLD(R.string.chip_old);
    fun allows(entry: CatalogEntry) = entry.chip == "new" || (this == OLD && (entry.chip == "old" || entry.stock))
}

data class CatalogEntry(
    val id: String, val name: String, val author: String, val description: String,
    val version: String, val repo: String, val tag: String?, val assetId: String?,
    val sha256: String, val product: String, val chip: String,
    val beta: Boolean, val stock: Boolean = false, val directUrl: String? = null,
    val kinds: Set<String> = setOf("other"), val date: String = "", val cardUrl: String? = null,
) {
    val sourceUrl get() = "https://github.com/$repo"
    val downloadable get() = directUrl != null || (tag != null && assetId != null)
    val shortDescription get(): String {
        val clean = description.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= 165) return clean
        val sentence = clean.indexOf(". ")
        if (sentence in 40..164) return clean.take(sentence + 1)
        return clean.take(162).substringBeforeLast(' ', clean.take(162)).trimEnd('.', ',') + "…"
    }
}

data class Catalog(val entries: List<CatalogEntry>, val updated: String, val fetchedAt: Long, val fresh: Boolean)

object CatalogParser {
    fun isReleaseDownload(address: String, repo: String): Boolean = runCatching {
        val url = java.net.URI(address)
        url.scheme == "https" && url.host.equals("github.com", true) && url.userInfo == null &&
            url.port in listOf(-1, 443) && url.rawQuery == null && url.rawFragment == null &&
            url.rawPath.startsWith("/$repo/releases/download/", ignoreCase = true)
    }.getOrDefault(false)

    fun parse(json: String): Pair<List<CatalogEntry>, String> {
        val root = JSONObject(json)
        val entries = mutableListOf<CatalogEntry>()
        val array = root.getJSONArray("firmwares")
        require(array.length() <= 5000) { invalid(msg(R.string.catalog_too_large)) }
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val repo = item.optString("repo")
            val hash = item.optString("sha256")
            val product = item.optString("product")
            if (item.optString("id").length !in 1..256 || !Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repo) ||
                !Regex("[0-9a-f]{64}").matches(hash) || !Regex("FM-1_[0-9]{1,16}").matches(product)) continue
            entries += CatalogEntry(
                item.getString("id"), item.getString("name"), item.optString("author"), item.optString("what"),
                item.optString("version"), repo, item.optional("tag"),
                item.optional("asset")?.substringBefore(' ')?.takeIf { it.matches(Regex("[0-9]+")) },
                hash, product, item.optString("chip", "unknown"), item.optBoolean("beta"),
                kinds = when (val kind = item.opt("kind")) {
                    is org.json.JSONArray -> (0 until kind.length()).map { kind.optString(it) }.toSet()
                    is String -> setOf(kind)
                    else -> setOf("other")
                },
                date = item.optional("published") ?: item.optional("date") ?: "",
                cardUrl = item.optional("card")?.let { address ->
                    runCatching { URL(URL("https://thefirmwarehub.com/"), address).toString() }
                        .getOrNull()?.takeIf { it.startsWith("https://") }
                },
            )
        }
        root.optJSONObject("stock")?.let { stock ->
            val source = stock.optString("source")
            val match = Regex("https://github.com/([^/]+/[^/]+)/blob/([a-f0-9]{40})/(.+)").matchEntire(source)
            val hash = stock.optString("sha256")
            val product = stock.optString("product")
            if (match != null && Regex("[0-9a-f]{64}").matches(hash) && Regex("FM-1_[0-9]+").matches(product)) {
                entries.add(0, CatalogEntry("mvave-stock", "M‑VAVE · stock firmware", "M‑VAVE",
                    "Stock package from the official M‑VAVE repository. The catalog version may not be the latest.",
                    product, match.groupValues[1], null, null, hash, product, "unknown", false, true,
                    "https://raw.githubusercontent.com/${match.groupValues[1]}/${match.groupValues[2]}/${match.groupValues[3]}", kinds = setOf("stock")))
            }
        }
        require(entries.isNotEmpty() && entries.map { it.id }.distinct().size == entries.size) { invalid(msg(R.string.catalog_invalid)) }
        return entries to root.optString("updated", "")
    }

    private fun JSONObject.optional(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

class CatalogRepository(private val directory: File) {
    companion object { const val DAY = 24 * 60 * 60 * 1000L }
    private val jsonFile = File(directory, "catalog.json")
    private val stampFile = File(directory, "catalog-fetched.txt")
    private val attemptFile = File(directory, "catalog-attempt.txt")

    fun cached(now: Long = System.currentTimeMillis()): Catalog? {
        if (!jsonFile.exists()) return null
        val stamp = stampFile.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0
        return read(stamp, now >= stamp && now - stamp < DAY)
    }

    fun load(now: Long = System.currentTimeMillis()): Catalog {
        directory.mkdirs()
        val fetched = stampFile.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0
        val attempted = attemptFile.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0
        val fresh = jsonFile.exists() && now >= fetched && now - fetched < DAY
        if (fresh) return read(fetched, true)
        if (now >= attempted && now - attempted < DAY) {
            check(jsonFile.exists()) { failed(msg(R.string.catalog_retry_later)) }
            return read(fetched, false)
        }
        attemptFile.writeText(now.toString())
        return try {
            val text = Network.get("https://thefirmwarehub.com/catalog.json", 4 * 1024 * 1024).toString(Charsets.UTF_8)
            CatalogParser.parse(text)
            val temp = File(directory, "catalog.tmp")
            temp.writeText(text)
            check(temp.renameTo(jsonFile)) { failed(msg(R.string.catalog_save_error)) }
            stampFile.writeText(now.toString())
            read(now, true)
        } catch (failure: Exception) {
            if (!jsonFile.exists()) throw failure
            read(fetched, false)
        }
    }

    private fun read(stamp: Long, fresh: Boolean): Catalog {
        val (entries, updated) = CatalogParser.parse(jsonFile.readText())
        return Catalog(entries, updated, stamp, fresh)
    }

    fun download(entry: CatalogEntry): ByteArray {
        val catalog = load()
        check(catalog.fresh && catalog.entries.any { it.id == entry.id && it.sha256 == entry.sha256 }) {
            failed(msg(R.string.download_stale))
        }
        val url = entry.directUrl ?: run {
            check(entry.tag != null && entry.assetId != null) { failed(msg(R.string.release_not_identified)) }
            val tag = java.net.URLEncoder.encode(entry.tag, "UTF-8").replace("+", "%20")
            val release = JSONObject(Network.get("https://api.github.com/repos/${entry.repo}/releases/tags/$tag", 2 * 1024 * 1024).toString(Charsets.UTF_8))
            val assets = release.getJSONArray("assets")
            val matching = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.getLong("id").toString() == entry.assetId && it.getString("name").endsWith(".fwsc", true) }
            check(matching.size == 1) { failed(msg(R.string.release_asset_missing)) }
            matching.single().getString("browser_download_url").also {
                check(CatalogParser.isReleaseDownload(it, entry.repo)) { failed(msg(R.string.release_url_invalid)) }
            }
        }
        val raw = Network.get(url, FirmwarePackage.MAX_SIZE)
        FirmwarePackage.parse(raw, entry.sha256, entry.product)
        return raw
    }
}

object Network {
    fun get(address: String, limit: Int): ByteArray {
        var url = URL(address)
        repeat(6) {
            check(url.protocol == "https") { failed(msg(R.string.https_required)) }
            val connection = url.openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", "FM1FlasherAndroid/0.5.1 (+native-android)")
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: failed(msg(R.string.redirect_missing)))
                } else {
                    check(code == 200) { failed(msg(R.string.http_error, code)) }
                    check(connection.contentLengthLong <= limit) { failed(msg(R.string.download_too_large)) }
                    return connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() <= limit - count) { failed(msg(R.string.download_too_large)) }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        failed(msg(R.string.too_many_redirects))
    }
}
