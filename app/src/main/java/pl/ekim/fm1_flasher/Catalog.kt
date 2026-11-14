package pl.ekim.fm1_flasher

import org.json.JSONObject
import java.net.URL

object CatalogParser {
    fun isReleaseDownload(address: String, repo: String): Boolean = runCatching {
        val url = java.net.URI(UrlScope.canonical(address))
        url.scheme == "https" && url.host.equals("github.com", true) && url.userInfo == null &&
            url.port in listOf(-1, 443) && url.rawQuery == null && url.rawFragment == null &&
            url.rawPath.startsWith("/$repo/releases/download/", ignoreCase = true)
    }.getOrDefault(false)

    fun validRepo(repo: String) = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repo) &&
        repo.split('/').none { it == "." || it == ".." }

    fun parse(json: String, source: CatalogSource = CatalogSource.hub): Pair<List<CatalogEntry>, String> {
        JsonLimits.check(json)
        val tokener = org.json.JSONTokener(json)
        val root = tokener.nextValue() as? JSONObject ?: invalid(msg(R.string.catalog_invalid))
        require(tokener.nextClean() == '\u0000') { invalid(msg(R.string.catalog_invalid)) }
        val entries = mutableListOf<CatalogEntry>()
        val array = root.getJSONArray("firmwares")
        require(array.length() <= 5000) { invalid(msg(R.string.catalog_too_large)) }
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val repo = item.optional("repo").orEmpty()
            val hash = item.optString("sha256")
            val product = item.optString("product")
            require(listOf("id", "name", "sha256", "product").all { item.opt(it) is String } && item.optString("id").length in 1..256 && item.optString("name").isNotBlank() &&
                (repo.isEmpty() || validRepo(repo)) &&
                Regex("[0-9a-f]{64}").matches(hash) && product.isNotBlank()) { invalid(msg(R.string.catalog_invalid)) }
            // Keep unsupported identities as non-flashable entries so they cannot masquerade as removals.
            val supported = Regex("FM-1_[0-9]{1,16}").matches(product)
            entries += CatalogEntry(
                item.getString("id"), item.getString("name"), item.optString("author"), item.optString("what"),
                item.optString("version"), repo, item.optional("tag"),
                item.optional("asset")?.substringBefore(' ')?.takeIf { it.matches(Regex("[0-9]+")) },
                hash, product, if (supported) item.optString("chip", "unknown") else "unsupported", item.optBoolean("beta"),
                directUrl = if (source.id == CatalogSource.HUB || !supported) null else item.optional("pkg")?.let { resolve(source, it) },
                sourceId = source.id, site = item.optional("site")?.let { resolve(source, it) }, originalJson = item.toString(),
                kinds = (when (val kind = item.opt("kind")) {
                    is org.json.JSONArray -> (0 until kind.length()).map { kind.optString(it) }.toSet()
                    is String -> setOf(kind)
                    else -> setOf("other")
                }).map { if (it in setOf("synth", "groove", "drums", "tracker", "chords", "dev", "stock")) it else "other" }.toSet(),
                date = item.optional("published") ?: item.optional("date") ?: "",
                cardUrl = item.optional("card")?.let { resolve(source, it) },
            )
        }
        require(root.isNull("stock") || root.opt("stock") is JSONObject) { invalid(msg(R.string.catalog_invalid)) }
        root.optJSONObject("stock")?.let { stock ->
            val stockSource = stock.optString("source")
            val match = Regex("https://github.com/([^/]+/[^/]+)/blob/([a-f0-9]{40})/(.+)").matchEntire(stockSource)
            val hash = stock.optString("sha256")
            val product = stock.optString("product")
            require(Regex("[0-9a-f]{64}").matches(hash) && product.isNotBlank()) { invalid(msg(R.string.catalog_invalid)) }
            val pinned = source.id == CatalogSource.HUB && hash == Recovery.V15_PACKAGE && product == "FM-1_015"
            val supported = Regex("FM-1_[0-9]{1,16}").matches(product)
            val upstream = match?.let { "https://raw.githubusercontent.com/${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}" }
            entries.add(0, CatalogEntry("mvave-stock", "M‑VAVE · stock firmware", "M‑VAVE",
                "Stock package declared by this catalog. Compatibility must be verified for these exact bytes.",
                product, match?.groupValues?.get(1).orEmpty(), null, null, hash, product,
                if (supported) "unknown" else "unsupported", false, pinned,
                if (source.id == CatalogSource.HUB) upstream else stock.optional("pkg")?.let { resolve(source, it) },
                kinds = setOf("stock"), sourceId = source.id, site = resolve(source, stockSource), originalJson = stock.toString()))
        }
        require(entries.map { it.id }.distinct().size == entries.size) { invalid(msg(R.string.catalog_invalid)) }
        return entries to root.optString("updated", "")
    }

    private fun resolve(source: CatalogSource, address: String): String? = runCatching {
        URL(URL(source.baseUrl), address).toString().takeIf { Network.allowed(it, setOf(URL(it).host.lowercase())) }
    }.getOrNull()

    private fun JSONObject.optional(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
