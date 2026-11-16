package pl.ekim.fm1_flasher

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

/** Shared by foreground tasks and WorkManager. No background import during a flash. */
object CatalogOperations { val lock = ReentrantLock() }

class CatalogDatabase(internal val context: Context, name: String = "firmware-library.db") : SQLiteOpenHelper(context, name, null, 2) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sources (id TEXT PRIMARY KEY, url TEXT NOT NULL UNIQUE, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE catalog_entries (source_id TEXT NOT NULL, entry_id TEXT NOT NULL, json TEXT NOT NULL, removed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(source_id,entry_id))")
        db.execSQL("CREATE TABLE downloaded_packages (source_id TEXT NOT NULL, entry_id TEXT NOT NULL, sha256 TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY(source_id,entry_id,sha256))")
        db.execSQL("CREATE TABLE favorites (entry_key TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE app_settings (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE INDEX package_hash ON downloaded_packages(sha256)")
        listOf(CatalogSource.hub, CatalogSource.awesome.copy(intervalHours = BuildConfig.DEFAULT_SOURCE_REFRESH_HOURS)).forEach { putSource(db, it) }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) { "Unsupported database migration $oldVersion → $newVersion" }
        val preset = CatalogSource.awesome
        val source = db.rawQuery("SELECT json FROM sources WHERE id=?", arrayOf(preset.id)).use { cursor ->
            if (cursor.moveToFirst()) sourceFromJson(JSONObject(cursor.getString(0))) else null
        }
        if (source?.builtin == true && source.name == "awesome-fm1") putSource(db, source.copy(name = preset.name))
    }

    fun <T> transaction(action: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try { val result = action(); db.setTransactionSuccessful(); return result }
        finally { db.endTransaction() }
    }

    fun describe(failure: Exception) = AppLanguage.wrap(context).resolve(failure.appMessage(R.string.catalog_read_error))

    fun sources(): List<CatalogSource> = readableDatabase.rawQuery("SELECT json FROM sources ORDER BY CASE id WHEN 'firmwarehub' THEN 0 WHEN 'awesome-fm1' THEN 1 ELSE 2 END, id", null).use { c ->
        buildList { while (c.moveToNext()) add(sourceFromJson(JSONObject(c.getString(0)))) }
    }
    fun source(id: String) = sources().single { it.id == id }
    private fun putSource(db: SQLiteDatabase, source: CatalogSource) {
        db.insertWithOnConflict("sources", null, ContentValues().apply {
            put("id", source.id); put("url", source.url); put("json", sourceToJson(source).toString())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }
    fun putSource(source: CatalogSource) = putSource(writableDatabase, source)

    fun configure(id: String?, name: String, url: String, hours: Int, packages: Set<String>, artwork: Set<String>,
                  provider: CatalogProviderType? = null, authMethod: HttpAuthMethod? = null,
                  username: String? = null, authHeader: String? = null, oauth: OAuthConfiguration? = null, packageChips: Map<String, String>? = null): CatalogSource = transaction {
        val previous = id?.let(::source)
        val kind = provider ?: previous?.provider ?: CatalogProviderType.JSON_URL
        val canonical = if (kind == CatalogProviderType.REPO_URL) "https://github.com/${RepositoryUrlProvider.repository(url)}" else UrlScope.canonical(url)
        require(previous?.builtin == true || kind !in listOf(CatalogProviderType.THE_FIRMWARE_HUB, CatalogProviderType.AWESOME_FM1)) { invalid(msg(R.string.source_builtin_locked)) }
        require(previous?.builtin != true || kind == previous.provider) { invalid(msg(R.string.source_builtin_locked)) }
        require(name.isNotBlank() && name.length <= 100 && hours in 1..168) { invalid(msg(R.string.source_invalid_config)) }
        val packageScopes = packages.map(UrlScope::canonical).toSet()
        val artworkScopes = artwork.map(UrlScope::canonical).toSet()
        require(previous?.builtin != true || previous.url == canonical && previous.name == name) { invalid(msg(R.string.source_builtin_locked)) }
        // Alternate spellings/queries cannot create another subscription with the Hub's identity.
        require(previous?.builtin == true || !URIHost(canonical).let { it == "thefirmwarehub.com" || it.endsWith(".thefirmwarehub.com") }) { invalid(msg(R.string.source_builtin_locked)) }
        require(packageChips == null || packageChips.size <= 5000 && packageChips.all { (key, value) -> key.matches(Regex("[0-9a-f]{64}")) && value in listOf("unknown", "old", "new") } && (kind in listOf(CatalogProviderType.REPO_URL, CatalogProviderType.PKG_URL) || packageChips.isEmpty())) { invalid(msg(R.string.source_invalid_config)) }
        val sameUrl = sources().singleOrNull { it.url == canonical }
        require(sameUrl == null || sameUrl.deleted || sameUrl.id == id) { invalid(msg(R.string.source_duplicate)) }
        val base = sameUrl ?: CatalogSource(UUID.randomUUID().toString(), name.trim(), canonical)
        if (previous != null && previous.url != canonical) putSource(previous.copy(enabled = false, deleted = true, revision = previous.revision + 1))
        val policyChanged = base.packageScopes != packageScopes || base.artworkScopes != artworkScopes || base.provider != kind ||
            authMethod?.let { it != base.authMethod } == true ||
            username?.let { it != base.authUsername } == true || authHeader?.let { it != base.authHeader } == true || oauth?.let { it != base.oauth } == true
        base.copy(checkedAt = if (policyChanged) 0 else base.checkedAt,
            etag = if (policyChanged) null else base.etag, modified = if (policyChanged) null else base.modified,
            name = name.trim(), intervalHours = hours, packageScopes = packageScopes, artworkScopes = artworkScopes,
            provider = kind, authMethod = authMethod ?: base.authMethod,
            authUsername = username ?: base.authUsername, authHeader = authHeader ?: base.authHeader, oauth = oauth ?: base.oauth, packageChips = packageChips ?: base.packageChips,
            deleted = false, enabled = if (sameUrl != null && sameUrl.id == id && !sameUrl.deleted) sameUrl.enabled else false,
            revision = base.revision + 1).also { saved ->
                putSource(saved)
                if (saved.provider in listOf(CatalogProviderType.REPO_URL, CatalogProviderType.PKG_URL) &&
                    saved.packageChips != base.packageChips) updateCompatibility(saved)
            }
    }
    private fun updateCompatibility(source: CatalogSource) {
        fun chip(hash: String) = source.packageChips[hash] ?: "unknown"
        val entries = readableDatabase.rawQuery("SELECT entry_id,json FROM catalog_entries WHERE source_id=?", arrayOf(source.id)).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
        entries.forEach { (id, raw) -> writableDatabase.update("catalog_entries", ContentValues().apply {
            put("json", JSONObject(raw).let { it.put("chip", chip(it.getString("sha256"))).toString() })
        }, "source_id=? AND entry_id=?", arrayOf(source.id, id)) }
        val packages = readableDatabase.rawQuery("SELECT entry_id,json FROM downloaded_packages WHERE source_id=?", arrayOf(source.id)).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
        packages.forEach { (id, raw) ->
            val json = JSONObject(raw); json.getJSONObject("entry").let { it.put("chip", chip(it.getString("sha256"))) }
            writableDatabase.update("downloaded_packages", ContentValues().apply { put("json", json.toString()) }, "source_id=? AND entry_id=?", arrayOf(source.id, id))
        }
    }

    fun authenticationChanged(id: String) {
        val s = source(id)
        putSource(s.copy(checkedAt = 0, attemptedAt = if (s.isFirmwareHub) s.attemptedAt else 0, nextAttemptAt = if (s.isFirmwareHub) s.nextAttemptAt else 0, serverRetryAt = s.serverRetryAt, etag = null, modified = null, error = null, revision = s.revision + 1, authRevision = s.authRevision + 1))
    }
    fun setEnabled(id: String, enabled: Boolean) = transaction {
        val s = source(id); check(!s.deleted)
        putSource(s.copy(enabled = enabled, revision = s.revision + 1))
    }
    fun removeSource(id: String) = transaction {
        val s = source(id); require(!s.builtin) { invalid(msg(R.string.source_builtin_locked)) }
        putSource(s.copy(enabled = false, deleted = true, revision = s.revision + 1))
    }
    fun restoreDefaults() = transaction {
        listOf(CatalogSource.hub, CatalogSource.awesome.copy(intervalHours = BuildConfig.DEFAULT_SOURCE_REFRESH_HOURS)).forEach { default ->
            val s = source(default.id)
            putSource(s.copy(enabled = default.enabled, intervalHours = default.intervalHours,
                packageScopes = default.packageScopes, artworkScopes = default.artworkScopes, authMethod = HttpAuthMethod.NONE,
                authUsername = "", authHeader = "X-API-Key", oauth = OAuthConfiguration(), revision = s.revision + 1))
        }
    }

    fun commitSnapshot(expected: CatalogSource, entries: List<CatalogEntry>?, updated: String, license: String,
                       now: Long, etag: String?, modified: String?) = transaction {
        val current = source(expected.id)
        check(current.revision == expected.revision && !current.deleted) { failed(msg(R.string.source_changed)) }
        if (entries == null) check(current.snapshot) { failed(msg(R.string.catalog_invalid)) }
        else {
            writableDatabase.execSQL("UPDATE catalog_entries SET removed=1 WHERE source_id=?", arrayOf(current.id))
            entries.forEach { entry ->
                check(entry.sourceId == current.id)
                writableDatabase.insertWithOnConflict("catalog_entries", null, ContentValues().apply {
                    put("source_id", current.id); put("entry_id", entry.id); put("json", LocalLibrary.entryToJson(entry).toString()); put("removed", 0)
                }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            }
        }
        putSource(current.copy(snapshot = true, checkedAt = now, updated = updated, license = license,
            etag = etag, modified = modified, error = null, entryCount = entries?.size ?: current.entryCount))
    }

    fun catalog(now: Long = System.currentTimeMillis()): Catalog {
        val sources = sources()
        val active = sources.filter { it.active }.map { it.id }.toSet()
        val removed = mutableSetOf<String>()
        val known = readableDatabase.rawQuery("SELECT json,removed FROM catalog_entries", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val e = LocalLibrary.entryFromJson(JSONObject(c.getString(0)), allowUnsupported = true)
                    add(e); if (c.getInt(1) != 0) removed += e.key
                }
            }
        }
        val live = known.filter { it.sourceId in active && it.key !in removed && it.chip != "unsupported" }
        val selected = sources.filter { it.active }
        return Catalog(live, selected.map { it.updated }.filter { it.isNotBlank() }.maxOrNull().orEmpty(),
            selected.minOfOrNull { it.checkedAt } ?: 0, selected.isNotEmpty() && selected.all { it.fresh(now) }, sources, known, removed)
    }

    fun downloaded(): List<LocalPackage> = readableDatabase.rawQuery("SELECT json FROM downloaded_packages", null).use { c ->
        buildList { while (c.moveToNext()) add(packageFromJson(JSONObject(c.getString(0)))) }
    }
    fun savePackage(saved: LocalPackage) = transaction {
        writableDatabase.delete("downloaded_packages", "source_id=? AND entry_id=?", arrayOf(saved.entry.sourceId, saved.entry.id))
        writableDatabase.insertOrThrow("downloaded_packages", null, ContentValues().apply {
            put("source_id", saved.entry.sourceId); put("entry_id", saved.entry.id); put("sha256", saved.entry.sha256)
            put("json", JSONObject().put("entry", LocalLibrary.entryToJson(saved.entry)).put("downloadedAt", saved.downloadedAt).put("bytes", saved.bytes).toString())
        })
    }
    fun removePackage(key: String) {
        val p = downloaded().singleOrNull { it.entry.key == key } ?: return
        writableDatabase.delete("downloaded_packages", "source_id=? AND entry_id=?", arrayOf(p.entry.sourceId, p.entry.id))
    }
    fun clearPackages() = transaction {
        writableDatabase.delete("downloaded_packages", null, null)
        writableDatabase.delete("app_settings", "name=?", arrayOf("selected"))
    }
    fun settings(): LibrarySettings {
        val favorites = readableDatabase.rawQuery("SELECT entry_key FROM favorites", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        return LibrarySettings(favorites, setting("offline") == "true", setting("selected"))
    }
    fun saveSettings(s: LibrarySettings) = transaction {
        writableDatabase.delete("favorites", null, null)
        s.favorites.forEach { writableDatabase.insertOrThrow("favorites", null, ContentValues().apply { put("entry_key", it) }) }
        setting("offline", s.offline.toString()); setting("selected", s.selectedId)
    }
    fun setting(name: String): String? = readableDatabase.rawQuery("SELECT value FROM app_settings WHERE name=?", arrayOf(name)).use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
    fun setting(name: String, value: String?) {
        if (value == null) writableDatabase.delete("app_settings", "name=?", arrayOf(name))
        else writableDatabase.insertWithOnConflict("app_settings", null, ContentValues().apply {
            put("name", name); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    /** Original files remain as a recovery copy; only SQLite is written after this transaction. */
    fun migrate(directory: File) = transaction {
        if (setting("migration") == "1") return@transaction
        val legacy = LocalLibrary(File(directory, "library"))
        val preferences = legacy.settings()
        val problems = mutableListOf<String>()
        val locals = legacy.list()
        if (legacy.problems.isNotEmpty()) problems += "Some legacy records could not be read; original files retained."
        locals.forEach { saved ->
            val valid = try { legacy.load(saved.entry.id, null); true }
            catch (_: Exception) { problems += saved.entry.name; false }
            if (valid) savePackage(saved)
        }
        fun stamp(name: String) = File(directory, name).takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0
        val fetched = stamp("catalog-fetched.txt")
        val attempt = maxOf(fetched, stamp("catalog-attempt.txt"))
        val hub = source(CatalogSource.HUB).copy(attemptedAt = attempt,
            nextAttemptAt = if (attempt > 0) attempt + 24 * CatalogSource.HOUR else 0)
        putSource(hub)
        val file = File(directory, "catalog.json")
        if (file.exists()) {
            val raw = try {
                check(file.length() in 1..4 * 1024 * 1024)
                file.readText().also { CatalogParser.parse(it) }
            } catch (_: Exception) { problems += "Legacy catalog unreadable; original file retained."; null }
            if (raw != null) {
                val parsed = CatalogParser.parse(raw)
                commitSnapshot(hub, parsed.first, parsed.second, JSONObject(raw).opt("license")?.toString().orEmpty(), fetched, null, null)
            }
        }
        saveSettings(preferences.copy(favorites = preferences.favorites.map { CatalogSource.key(CatalogSource.HUB, it) }.toSet(),
            selectedId = preferences.selectedId?.let { CatalogSource.key(CatalogSource.HUB, it) }))
        val browse = context.getSharedPreferences("browse", Context.MODE_PRIVATE)
        BROWSE_KEYS.forEach { key -> browse.all[key]?.toString()?.let { setting("browse.$key", it) } }
        if (problems.isNotEmpty()) setting("migrationProblems", problems.joinToString("\n"))
        if (legacy.settingsRepaired) setting("settingsRepaired", "true")
        setting("migration", "1")
    }

    fun browseSettings() = BROWSE_KEYS.mapNotNull { key -> setting("browse.$key")?.let { key to it } }.toMap()
    fun saveBrowse(values: Map<String, String>) = transaction {
        values.filterKeys { it in BROWSE_KEYS }.forEach { (key, value) -> setting("browse.$key", value) }
    }

    companion object {
        val BROWSE_KEYS = listOf("tab", "query", "category", "sort", "downloaded", "favorites", "source")
        @Volatile private var instance: CatalogDatabase? = null
        fun get(context: Context): CatalogDatabase = instance ?: synchronized(this) {
            instance ?: CatalogDatabase(context.applicationContext).also { instance = it }
        }
        private fun URIHost(url: String) = java.net.URI(url).host
        private fun packageFromJson(j: JSONObject) = LocalPackage(LocalLibrary.entryFromJson(j.getJSONObject("entry")), j.getLong("downloadedAt"), j.getLong("bytes"))
        private fun sourceToJson(s: CatalogSource) = JSONObject().apply {
            put("id", s.id); put("name", s.name); put("url", s.url); put("enabled", s.enabled); put("builtin", s.builtin); put("deleted", s.deleted)
            put("hours", s.intervalHours); put("packages", JSONArray(s.packageScopes.toList())); put("artwork", JSONArray(s.artworkScopes.toList()))
            put("revision", s.revision); put("attempted", s.attemptedAt); put("checked", s.checkedAt); put("next", s.nextAttemptAt)
            put("serverRetryAt", s.serverRetryAt); put("snapshot", s.snapshot); put("updated", s.updated); put("etag", s.etag); put("modified", s.modified); put("error", s.error)
            put("license", s.license); put("count", s.entryCount)
            put("provider", s.provider.name); put("authMethod", s.authMethod.name)
            put("authUsername", s.authUsername); put("authHeader", s.authHeader); put("oauth", oauthToJson(s.oauth)); put("packageChips", JSONObject(s.packageChips)); put("authRevision", s.authRevision)
        }
        private fun sourceFromJson(j: JSONObject): CatalogSource {
            fun list(key: String) = j.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            fun optional(key: String) = if (j.isNull(key)) null else j.optString(key).takeIf { it.isNotBlank() }
            return CatalogSource(j.getString("id"), j.getString("name"), j.getString("url"), j.getBoolean("enabled"),
                j.getBoolean("builtin"), j.getBoolean("deleted"), j.getInt("hours"), list("packages"), list("artwork"),
                j.getLong("revision"), j.getLong("attempted"), j.getLong("checked"), j.getLong("next"), j.getBoolean("snapshot"),
                j.getString("updated"), optional("etag"), optional("modified"), optional("error"), j.optString("license"), j.optInt("count"),
                j.optString("provider").takeIf(String::isNotBlank)?.let(CatalogProviderType::valueOf) ?: CatalogProviderType.legacy(j.getString("id")),
                HttpAuthMethod.valueOf(j.optString("authMethod", "NONE")),
                j.optString("authUsername"), j.optString("authHeader", "X-API-Key"), oauthFromJson(j.optJSONObject("oauth")), j.optJSONObject("packageChips")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty(), j.optLong("authRevision"), j.optLong("serverRetryAt"))
        }
    }
}
