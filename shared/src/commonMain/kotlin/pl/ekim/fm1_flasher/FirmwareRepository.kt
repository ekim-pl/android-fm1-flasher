package pl.ekim.fm1_flasher

import kotlinx.serialization.json.*

/** Paths are relative to private application storage. Writes replace the destination atomically. */
interface AppStorage {
    @Throws(Exception::class) fun read(path: String, limit: Int): ByteArray?
    @Throws(Exception::class) fun write(path: String, bytes: ByteArray)
    @Throws(Exception::class) fun remove(path: String)
    @Throws(Exception::class) fun size(path: String): Long?
    @Throws(Exception::class) fun list(path: String): List<String>
}
interface HttpClient {
    /** Enforce HTTPS host allowlists at every redirect, a total deadline and the byte limit. */
    @Throws(Exception::class) fun get(url: String, limit: Int, artworkOnly: Boolean): ByteArray
}

internal fun JsonObject.string(key: String, default: String = "") = (get(key) as? JsonPrimitive)?.contentOrNull ?: default
internal fun JsonObject.optional(key: String) = string(key).takeIf { it.isNotBlank() }
internal fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
internal fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: error("Invalid $key")
internal fun JsonObject.array(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
internal fun parseObject(text: String): JsonObject {
    require(text.length <= 4 * 1024 * 1024)
    var quoted = false; var escaped = false; var depth = 0; var token = 0
    for (c in text) {
        if (quoted) {
            require(++token <= 32768)
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') { quoted = false; token = 0 }
        } else when (c) {
            '"' -> { quoted = true; token = 0 }
            '[', '{' -> { require(++depth <= 32); token = 0 }
            ']', '}' -> { require(--depth >= 0); token = 0 }
            ',', ':', ' ', '\r', '\n', '\t' -> token = 0
            else -> require(++token <= 256)
        }
    }
    require(!quoted && depth == 0)
    return Json.parseToJsonElement(text).jsonObject
}

object CatalogCodec {
    fun parse(text: String): Pair<List<CatalogEntry>, String> {
        val root = parseObject(text)
        val records = root.array("firmwares")
        require(records.size <= 5000) { invalid(msg("catalog_too_large")) }
        val entries = records.mapNotNull { value ->
            val item = value.jsonObject
            val repo = item.string("repo"); val hash = item.string("sha256"); val product = item.string("product")
            if (!valid(item.string("id"), repo, hash, product)) return@mapNotNull null
            val kind = item["kind"]
            val card = item.optional("card")?.let {
                when { it.startsWith("https://") -> it; it.startsWith("//") -> "https:$it"; else -> "https://thefirmwarehub.com/${it.trimStart('/')}" }
            }?.takeIf { allowedUrl(it, true) }
            CatalogEntry(item.string("id"), item.string("name"), item.string("author"), item.string("what"),
                item.string("version"), repo, item.optional("tag"), item.optional("asset")?.substringBefore(' ')?.takeIf { it.matches(Regex("[0-9]+")) },
                hash, product, item.string("chip", "unknown"), item.flag("beta"), kinds = when(kind) {
                    is JsonArray -> kind.map { it.jsonPrimitive.content }.toSet()
                    is JsonPrimitive -> setOf(kind.content)
                    else -> setOf("other")
                }, date = item.optional("published") ?: item.string("date"), cardUrl = card)
        }.toMutableList()
        (root["stock"] as? JsonObject)?.let { stock ->
            val match = Regex("https://github.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/blob/([a-f0-9]{40})/(.+)").matchEntire(stock.string("source"))
            if (match != null && valid("mvave-stock", match.groupValues[1], stock.string("sha256"), stock.string("product"))) {
                entries.add(0, CatalogEntry("mvave-stock", "M‑VAVE · stock firmware", "M‑VAVE", "", stock.string("product"),
                    match.groupValues[1], null, null, stock.string("sha256"), stock.string("product"), "unknown", false, true,
                    "https://raw.githubusercontent.com/${match.groupValues[1]}/${match.groupValues[2]}/${match.groupValues[3]}", setOf("stock")))
            }
        }
        require(entries.isNotEmpty() && entries.map { it.id }.distinct().size == entries.size) { invalid(msg("catalog_invalid")) }
        return entries to root.string("updated")
    }
    internal fun valid(id: String, repo: String, hash: String, product: String) = id.length in 1..256 &&
        repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) && hash.matches(Regex("[a-f0-9]{64}")) && product.matches(Regex("FM-1_[0-9]{1,16}"))
    internal fun encode(entry: CatalogEntry) = buildJsonObject {
        put("id", entry.id); put("name", entry.name); put("author", entry.author); put("description", entry.description)
        put("version", entry.version); put("repo", entry.repo); put("tag", entry.tag); put("assetId", entry.assetId)
        put("sha256", entry.sha256); put("product", entry.product); put("chip", entry.chip); put("beta", entry.beta)
        put("stock", entry.stock); put("directUrl", entry.directUrl); put("date", entry.date); put("cardUrl", entry.cardUrl)
        put("kinds", JsonArray(entry.kinds.map(::JsonPrimitive)))
    }
    internal fun decode(item: JsonObject): CatalogEntry {
        require(valid(item.string("id"), item.string("repo"), item.string("sha256"), item.string("product")))
        return CatalogEntry(item.string("id"), item.string("name"), item.string("author"), item.string("description"), item.string("version"),
            item.string("repo"), item.optional("tag"), item.optional("assetId"), item.string("sha256"), item.string("product"), item.string("chip"),
            item.flag("beta"), item.flag("stock"), item.optional("directUrl"), item.array("kinds").map { it.jsonPrimitive.content }.toSet(), item.string("date"), item.optional("cardUrl"))
    }
}

fun allowedUrl(url: String, artworkOnly: Boolean = false): Boolean {
    val match = Regex("https://([A-Za-z0-9.-]+)(?::443)?(/[^#]*)?").matchEntire(url) ?: return false
    val hosts = if (artworkOnly) setOf("thefirmwarehub.com") else setOf("thefirmwarehub.com", "api.github.com", "github.com", "raw.githubusercontent.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    return match.groupValues[1].lowercase() in hosts
}

/** Common repository retains the Android metadata format and immutable SHA-256 package blobs. */
class FirmwareRepository(private val storage: AppStorage, private val http: HttpClient, private val clock: () -> Long) {
    companion object { const val DAY = 86_400_000L }
    var settingsRepaired = false
        private set
    val problems = mutableListOf<AppText>()
    private fun text(path: String, limit: Int = 128000) = storage.read(path, limit)?.decodeToString()
    private fun writeText(path: String, value: String) = storage.write(path, value.encodeToByteArray())
    fun cached(): Catalog? {
        val json = text("catalog.json", 4 * 1024 * 1024) ?: return null
        val stamp = text("catalog-fetched.txt")?.toLongOrNull() ?: 0L
        val (entries, updated) = CatalogCodec.parse(json)
        return Catalog(entries, updated, stamp, clock() >= stamp && clock() - stamp < DAY)
    }
    fun refresh(): Catalog {
        val existing = runCatching { cached() }.getOrNull()
        if (existing?.fresh == true) return existing
        val stamp = maxOf(text("catalog-fetched.txt")?.toLongOrNull() ?: 0, text("catalog-attempt.txt")?.toLongOrNull() ?: 0)
        val now = clock()
        if (now >= stamp && now - stamp < DAY) return existing ?: failed(msg("catalog_retry_later"))
        writeText("catalog-attempt.txt", now.toString())
        return try {
            val json = http.get("https://thefirmwarehub.com/catalog.json", 4 * 1024 * 1024, false).decodeToString()
            CatalogCodec.parse(json)
            writeText("catalog.json", json); writeText("catalog-fetched.txt", now.toString())
            checkNotNull(cached())
        } catch (failure: Exception) { existing?.copy(fresh = false) ?: throw failure }
    }
    fun settings(): LibrarySettings {
        val raw = storage.read("library/settings.json", 128000) ?: return LibrarySettings()
        return try {
            val json = parseObject(raw.decodeToString())
            LibrarySettings(json.array("favorites").map { it.jsonPrimitive.content }.toSet(), json.flag("offline"), json.optional("selectedId"))
        } catch (_: Exception) {
            storage.write("library/settings.invalid", raw)
            settingsRepaired = true
            LibrarySettings(offline = true).also(::saveSettings)
        }
    }
    fun saveSettings(settings: LibrarySettings) = writeText("library/settings.json", buildJsonObject {
        put("favorites", JsonArray(settings.favorites.sorted().map(::JsonPrimitive))); put("offline", settings.offline); put("selectedId", settings.selectedId)
    }.toString())
    private fun recordPath(id: String) = "library/records/${platformSha256(id.removePrefix("firmwarehub/").encodeToByteArray())}.json"
    private fun packagePath(hash: String): String {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        return "library/packages/$hash.fwsc"
    }
    private fun record(path: String): LocalPackage {
        val json = parseObject(text(path) ?: error("Missing package"))
        val saved = LocalPackage(CatalogCodec.decode(json.getValue("entry").jsonObject), json.number("downloadedAt"), json.number("bytes"))
        require(saved.bytes in 960..FirmwarePackage.MAX_SIZE.toLong())
        return saved
    }
    fun list(): List<LocalPackage> {
        problems.clear()
        return storage.list("library/records").filter { it.endsWith(".json") }.mapNotNull { name ->
            try {
                record("library/records/$name").also {
                    check(recordPath(it.entry.id) == "library/records/$name")
                    check(storage.size(packagePath(it.entry.sha256)) == it.bytes)
                }
            } catch (_: Exception) { problems += msg("local_package_unreadable", name.take(12)); null }
        }.sortedBy { it.entry.name.lowercase() }
    }
    fun load(id: String, catalog: Catalog? = cached()): Pair<LocalPackage, FirmwarePackage> {
        val saved = record(recordPath(id)); check(saved.entry.id == id || saved.entry.key == id)
        val latest = catalog?.entries?.singleOrNull { it.id == id }
        check(catalog == null || latest != null) { failed(msg("firmware_removed")) }
        val effective = if (latest?.sha256 == saved.entry.sha256) latest else saved.entry
        val bytes = storage.read(packagePath(saved.entry.sha256), FirmwarePackage.MAX_SIZE) ?: failed(msg("local_file_invalid"))
        check(bytes.size.toLong() == saved.bytes) { failed(msg("local_file_invalid")) }
        return saved.copy(entry = effective) to FirmwarePackage.parse(bytes, effective.sha256, effective.product)
    }
    fun save(entry: CatalogEntry, bytes: ByteArray) {
        FirmwarePackage.parse(bytes, entry.sha256, entry.product)
        require(CatalogCodec.valid(entry.id, entry.repo, entry.sha256, entry.product))
        val record = buildJsonObject { put("entry", CatalogCodec.encode(entry)); put("downloadedAt", clock()); put("bytes", bytes.size) }.toString()
        require(record.encodeToByteArray().size <= 128000)
        storage.write(packagePath(entry.sha256), bytes); writeText(recordPath(entry.id), record)
        prune()
    }
    fun remove(id: String) {
        storage.remove(recordPath(id)); val prefs = settings()
        if (prefs.selectedId == id) saveSettings(prefs.copy(selectedId = null))
        prune()
    }
    fun clearDownloads() {
        for (directory in listOf("library/records", "library/packages"))
            storage.list(directory).forEach { storage.remove("$directory/$it") }
        saveSettings(settings().copy(selectedId = null))
    }
    private fun prune() {
        val records = storage.list("library/records").filter { it.endsWith(".json") }
        val used = runCatching { records.map { record("library/records/$it").entry.sha256 }.toSet() }.getOrNull() ?: return
        storage.list("library/packages").filter { it.endsWith(".fwsc") && it.removeSuffix(".fwsc") !in used }.forEach { storage.remove("library/packages/$it") }
    }
    fun downloadBytes(entry: CatalogEntry): ByteArray {
        val catalog = refresh()
        check(catalog.fresh && catalog.entries.any { it.id == entry.id && it.sha256 == entry.sha256 }) { failed(msg("download_stale")) }
        val url = entry.directUrl ?: run {
            check(entry.tag != null && entry.assetId != null) { failed(msg("release_not_identified")) }
            val tag = entry.tag.encodeToByteArray().joinToString("") { byte ->
                val c = (byte.toInt() and 255).toChar()
                if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~") c.toString() else "%" + (byte.toInt() and 255).toString(16).padStart(2, '0')
            }
            val release = parseObject(http.get("https://api.github.com/repos/${entry.repo}/releases/tags/$tag", 2 * 1024 * 1024, false).decodeToString())
            val asset = release.array("assets").map { it.jsonObject }.singleOrNull { it.string("id") == entry.assetId && it.string("name").endsWith(".fwsc", true) }
                ?: failed(msg("release_asset_missing"))
            asset.string("browser_download_url").also { check(it.startsWith("https://github.com/${entry.repo}/releases/download/", true) && allowedUrl(it) && '?' !in it && '#' !in it) { failed(msg("release_url_invalid")) } }
        }
        check(allowedUrl(url)) { failed(msg("network_url_blocked")) }
        return http.get(url, FirmwarePackage.MAX_SIZE, false).also { FirmwarePackage.parse(it, entry.sha256, entry.product) }
    }
    fun download(entry: CatalogEntry) {
        save(entry, downloadBytes(entry))
        val prefs = settings()
        if (prefs.selectedId == entry.id) saveSettings(prefs.copy(selectedId = null))
    }
}
