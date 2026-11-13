package pl.fm1.flasher

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class LocalPackage(val entry: CatalogEntry, val downloadedAt: Long, val bytes: Long)
data class LibrarySettings(val favorites: Set<String> = emptySet(), val offline: Boolean = false, val selectedId: String? = null)

/** Package bytes are immutable by hash. Replacing one atomic metadata file commits an update. */
class LocalLibrary(private val directory: File) {
    private val packages = File(directory, "packages").apply { mkdirs() }
    private val records = File(directory, "records").apply { mkdirs() }
    private val settingsFile = File(directory, "settings.json")
    val problems = mutableListOf<String>()

    fun list(): List<LocalPackage> {
        problems.clear()
        return records.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
            try {
                val saved = readRecord(file)
                check(file.name == recordFile(saved.entry.id).name)
                check(packageFile(saved.entry).length() == saved.bytes && saved.bytes in 960..FirmwarePackage.MAX_SIZE.toLong())
                saved
            } catch (_: Exception) { problems += "Nie można odczytać lokalnej paczki ${file.name.take(12)}."; null }
        }.sortedBy { it.entry.name.lowercase() }
    }

    fun save(entry: CatalogEntry, raw: ByteArray, now: Long = System.currentTimeMillis()): LocalPackage {
        FirmwarePackage.parse(raw, entry.sha256, entry.product)
        require(entry.id.length in 1..256) { "Nieprawidłowy identyfikator paczki." }
        val saved = LocalPackage(entry, now, raw.size.toLong())
        val json = JSONObject().put("entry", entryToJson(entry)).put("downloadedAt", now).put("bytes", raw.size)
        val metadata = json.toString().toByteArray(Charsets.UTF_8)
        require(metadata.size <= 128_000) { "Metadane paczki są zbyt duże." }
        atomicWrite(packageFile(entry), raw)
        atomicWrite(recordFile(entry.id), metadata)
        pruneUnusedBytes()
        return saved
    }

    fun load(id: String, catalog: Catalog?): Pair<LocalPackage, FirmwarePackage> {
        val saved = readRecord(recordFile(id))
        check(saved.entry.id == id) { "Lokalna paczka ma inną tożsamość wpisu." }
        val effective = localFlashEntry(saved.entry, catalog)
        val file = packageFile(saved.entry)
        check(file.isFile && file.length() == saved.bytes && file.length() <= FirmwarePackage.MAX_SIZE) { "Brak lub nieprawidłowy rozmiar lokalnego pliku. Pobierz paczkę ponownie." }
        val firmware = FirmwarePackage.parse(file.readBytes(), effective.sha256, effective.product)
        return saved.copy(entry = effective) to firmware
    }

    fun remove(id: String) {
        val record = recordFile(id)
        check(!record.exists() || record.delete()) { "Nie można usunąć paczki." }
        val prefs = settings()
        if (prefs.selectedId == id) saveSettings(prefs.copy(selectedId = null))
        pruneUnusedBytes()
    }

    fun settings(): LibrarySettings {
        if (!settingsFile.exists()) return LibrarySettings()
        val json = JSONObject(settingsFile.readText())
        val favorites = json.optJSONArray("favorites") ?: JSONArray()
        return LibrarySettings((0 until favorites.length()).map { favorites.getString(it) }.toSet(),
            json.optBoolean("offline"), json.optString("selectedId").takeIf { !json.isNull("selectedId") && it.isNotBlank() })
    }

    fun saveSettings(settings: LibrarySettings) {
        val json = JSONObject().put("favorites", JSONArray(settings.favorites.sorted()))
            .put("offline", settings.offline).put("selectedId", settings.selectedId ?: JSONObject.NULL)
        atomicWrite(settingsFile, json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun readRecord(file: File): LocalPackage {
        check(file.length() in 1..128_000) { "Nieprawidłowe metadane lokalnej paczki." }
        val json = JSONObject(file.readText())
        return LocalPackage(entryFromJson(json.getJSONObject("entry")), json.getLong("downloadedAt"), json.getLong("bytes"))
    }

    private fun recordFile(id: String) = File(records, FirmwarePackage.sha256(id.toByteArray(Charsets.UTF_8)) + ".json")
    private fun packageFile(entry: CatalogEntry): File {
        require(Regex("[0-9a-f]{64}").matches(entry.sha256)) { "Nieprawidłowy identyfikator pliku." }
        return File(packages, entry.sha256 + ".fwsc")
    }

    private fun pruneUnusedBytes() {
        // A damaged metadata record might still own bytes; keep them until the record is repaired or removed.
        val valid = runCatching { records.listFiles().orEmpty().filter { it.extension == "json" }.map { readRecord(it).entry.sha256 }.toSet() }.getOrNull() ?: return
        packages.listFiles().orEmpty().filter { it.extension == "fwsc" && it.nameWithoutExtension !in valid }.forEach { it.delete() }
    }

    companion object {
        fun localFlashEntry(local: CatalogEntry, catalog: Catalog?): CatalogEntry {
            if (catalog == null) return local
            val latest = catalog.entries.singleOrNull { it.id == local.id }
                ?: error("Ten firmware został usunięty z ostatniego znanego katalogu. Zapis zablokowany.")
            // Compatibility changes for a newer binary must not be applied to an older local version.
            return if (latest.sha256 == local.sha256) latest else local
        }

        private fun atomicWrite(file: File, data: ByteArray) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            try {
                FileOutputStream(temp).use { it.write(data); it.fd.sync() }
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temp.delete() }
        }

        private fun entryToJson(entry: CatalogEntry) = JSONObject().apply {
            put("id", entry.id); put("name", entry.name); put("author", entry.author); put("description", entry.description)
            put("version", entry.version); put("repo", entry.repo); put("tag", entry.tag ?: JSONObject.NULL)
            put("assetId", entry.assetId ?: JSONObject.NULL); put("sha256", entry.sha256); put("product", entry.product)
            put("chip", entry.chip); put("beta", entry.beta); put("stock", entry.stock)
            put("directUrl", entry.directUrl ?: JSONObject.NULL); put("kinds", JSONArray(entry.kinds.toList()))
            put("date", entry.date); put("cardUrl", entry.cardUrl ?: JSONObject.NULL)
        }

        private fun entryFromJson(json: JSONObject): CatalogEntry {
            fun optional(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }
            val kinds = json.optJSONArray("kinds") ?: JSONArray().put("other")
            val entry = CatalogEntry(json.getString("id"), json.getString("name"), json.getString("author"), json.getString("description"),
                json.getString("version"), json.getString("repo"), optional("tag"), optional("assetId"), json.getString("sha256"),
                json.getString("product"), json.getString("chip"), json.getBoolean("beta"), json.optBoolean("stock"), optional("directUrl"),
                (0 until kinds.length()).map { kinds.getString(it) }.toSet(), json.optString("date"), optional("cardUrl"))
            require(entry.id.length in 1..256 && Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(entry.repo) &&
                Regex("[0-9a-f]{64}").matches(entry.sha256) && Regex("FM-1_[0-9]{1,16}").matches(entry.product)) { "Nieprawidłowe metadane paczki." }
            return entry
        }
    }
}
