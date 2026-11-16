package pl.ekim.fm1_flasher

import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** No attachment survives process death. Persisted labels are available for explicit pairing only. */
class FleetStore(private val file: File, private val write: (File, ByteArray) -> Unit = LocalLibrary::atomicWrite) {
    companion object { const val DEFAULT_HISTORY_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000 }
    @Volatile var historyRetentionMillis: Long? = DEFAULT_HISTORY_RETENTION_MILLIS; private set
    private var committedRetention: Long? = DEFAULT_HISTORY_RETENTION_MILLIS

    @Synchronized fun setHistoryRetention(retentionMillis: Long?, now: Long = System.currentTimeMillis()) {
        require(retentionMillis == null || retentionMillis >= 0)
        historyRetentionMillis = retentionMillis
        jobs = retainedHistory(retentionMillis, now)
        persist()
    }

    @Volatile var confirmedJobId: String? = null; private set
    private var committedConfirmation: String? = null

    /** null disables age retention. Active jobs are never history. */
    @Synchronized fun pruneHistory(retentionMillis: Long?, now: Long = System.currentTimeMillis()) {
        require(retentionMillis == null || retentionMillis >= 0)
        if (retentionMillis == null) return
        val retained = retainedHistory(retentionMillis, now)
        if (retained == jobs) return
        jobs = retained
        persist()
    }

    private fun retainedHistory(retentionMillis: Long?, now: Long): List<FlashJob> {
        if (retentionMillis == null) return jobs
        val cutoff = if (now < Long.MIN_VALUE + retentionMillis) Long.MIN_VALUE else now - retentionMillis
        return jobs.filter { it.outcome == JobOutcome.QUEUED || it.outcome == JobOutcome.RUNNING || it.updatedAt >= cutoff }
    }

    @Synchronized fun clearHistory() = removeHistory { true }

    private fun removeHistory(remove: (FlashJob) -> Boolean) {
        val retained = jobs.filter { it.outcome == JobOutcome.QUEUED || it.outcome == JobOutcome.RUNNING || !remove(it) }
        if (retained == jobs) return
        jobs = retained
        persist()
    }
    @Volatile var devices: List<FleetDevice> = emptyList(); private set
    @Volatile var jobs: List<FlashJob> = emptyList(); private set
    var loadFailed: Boolean = false; private set
    private var committedDevices: List<FleetDevice> = emptyList()
    private var committedJobs: List<FlashJob> = emptyList()
    init {
        try {
        if (file.exists()) {
            val root = JSONObject(file.readText())
            historyRetentionMillis = if (!root.has("historyRetentionMillis")) DEFAULT_HISTORY_RETENTION_MILLIS
                else if (root.isNull("historyRetentionMillis")) null else root.getLong("historyRetentionMillis").also { require(it >= 0) }
            confirmedJobId = root.optString("confirmedJobId").takeIf(String::isNotBlank)
            val rows = root.getJSONArray("devices")
            devices = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
                FleetDevice(it.getString("id"), it.getString("alias"), DeviceColor.fromIndex(it.optInt("color")).ordinal,
                    identity = it.optJSONObject("identity")?.identity(), chip = FlashChip.valueOf(it.getString("chip")),
                    serial = it.optString("serial").takeIf(String::isNotBlank), remembered = it.optBoolean("remembered", false))
            } }
            val queue = root.getJSONArray("jobs")
            val loadedJobs = (0 until queue.length()).map { i -> queue.getJSONObject(i).let {
                FlashJob(it.getString("id"), it.getString("device"), it.getString("alias"),
                    Endpoint(it.getInt("midi"), it.getString("path"), it.getLong("generation")),
                    it.getString("package"), it.getString("hash"), it.getString("title"), it.getString("version"),
                    it.getJSONObject("expected").identity(), FlashChip.valueOf(it.getString("chip")),
                    JobOutcome.valueOf(it.getString("outcome")), it.getString("phase"), it.getInt("progress"), it.getLong("time"),
                    it.optJSONArray("events")?.let { events -> (0 until events.length()).map { n -> events.getJSONObject(n).let { e -> JobEvent(e.getString("phase"), e.getLong("time")) } } } ?: emptyList(), it.optBoolean("forceUnknown", false),
                    color = DeviceColor.fromIndex(it.optInt("color", devices.singleOrNull { d -> d.id == it.getString("device") }?.color ?: 0)).ordinal)
            } }
            jobs = loadedJobs
            committedDevices = devices; committedJobs = jobs; committedConfirmation = confirmedJobId; committedRetention = historyRetentionMillis
        }
        } catch (_: Exception) { loadFailed = true; devices = emptyList(); jobs = emptyList(); confirmedJobId = null }
        // Pruning failures must not discard the successfully decoded queue.
        if (!loadFailed) try { pruneHistory(historyRetentionMillis) } catch (_: Exception) { loadFailed = true }
        jobs = FleetPolicy.interrupted(jobs)
        committedJobs = jobs
    }
    @Synchronized fun attach(current: List<Endpoint>) {
        if (loadFailed) return
        var next = devices.map { if (it.endpoint != null && it.endpoint !in current) it.copy(endpoint = null) else it }
        // Default names are temporary attachments; history keeps its own name and target.
        next = next.filter { device -> device.endpoint != null || device.remembered ||
            !device.alias.matches(Regex("FM-1 [0-9]+")) ||
            jobs.any { it.deviceId == device.id && it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) } }
        current.filter { endpoint -> next.none { it.endpoint == endpoint } }.forEach { endpoint ->
            val number = generateSequence(1) { it + 1 }.first { n -> next.none { it.alias == "FM-1 $n" } }
            next = next + FleetDevice(UUID.randomUUID().toString(), "FM-1 $number", (number - 1) % DeviceColor.entries.size, endpoint)
        }
        if (next != devices) { devices = next; persist() }
    }
    @Synchronized fun identify(endpoint: Endpoint, identity: FirmwareIdentity, serial: String?) {
        devices = devices.map { if (it.endpoint == endpoint) it.copy(identity = identity, serial = serial) else it }; persist()
    }
    @Synchronized fun configure(id: String, alias: String? = null, chip: FlashChip? = null, color: Int? = null) {
        require(color == null || color in DeviceColor.entries.indices)
        require(devices.any { it.id == id })
        check(jobs.none { it.deviceId == id && it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) }) { failed(msg("fleet_device_pinned")) }
        devices = devices.map { if (it.id == id) it.copy(alias = alias?.trim()?.take(48)?.takeIf(String::isNotBlank) ?: it.alias, chip = chip ?: it.chip, color = color ?: it.color, remembered = it.remembered || color != null) else it }; persist()
    }
    @Synchronized fun pair(oldId: String, liveId: String) {
        val old = devices.single { it.id == oldId }; val live = devices.single { it.id == liveId }
        check(old.endpoint == null && live.endpoint != null && live.identity != null)
        check(jobs.none { it.deviceId in listOf(oldId, liveId) && it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) })
        // Pairing never restores chip evidence from an earlier physical connection.
        devices = devices.filter { it.id != liveId }.map { if (it.id == oldId) live.copy(id = old.id, alias = old.alias, color = old.color, remembered = old.remembered, chip = FlashChip.UNKNOWN) else it }; persist()
    }
    @Synchronized fun rebind(id: String, endpoint: Endpoint, identity: FirmwareIdentity? = null, durable: Boolean = true) {
        devices = devices.filter { it.id == id || it.endpoint != endpoint }.map {
            if (it.id == id) it.copy(endpoint = endpoint, identity = identity ?: it.identity) else it
        }; if (durable) persist()
    }
    @Synchronized fun enqueue(deviceId: String, entry: CatalogEntry, forceUnknown: Boolean = false) {
        val device = devices.single { it.id == deviceId }
        check(devices.count { it.endpoint?.usbPath == device.endpoint?.usbPath } == 1) { failed(msg("fleet_ports_ambiguous")) }
        check(device.endpoint != null && device.identity != null && device.chip.allows(entry, forceUnknown)) { failed(msg("flash_missing_requirements")) }
        check(jobs.none { it.deviceId == deviceId && it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) }) { failed(msg("fleet_device_pinned")) }
        jobs = jobs + FlashJob(UUID.randomUUID().toString(), device.id, device.alias, checkNotNull(device.endpoint),
            entry.key, entry.sha256, entry.name, entry.version, checkNotNull(device.identity), device.chip, updatedAt = System.currentTimeMillis(), forceUnknown = forceUnknown && device.chip.canForce(entry), color = device.color); persist()
    }
    @Synchronized fun result(id: String, outcome: JobOutcome, phase: String, progress: Int = -1, durable: Boolean = true) {
        require(jobs.any { it.id == id })
        val time = System.currentTimeMillis()
        jobs = jobs.map { if (it.id == id) it.copy(outcome = outcome, phase = phase, progress = progress, updatedAt = time,
            events = if (it.events.lastOrNull()?.phase == phase) it.events else (it.events + JobEvent(phase, time)).takeLast(64)) else it }
        if (outcome == JobOutcome.CONFIRMED) confirmedJobId = id
        else if (id == confirmedJobId) confirmedJobId = null
        if (durable) { jobs = retainedHistory(historyRetentionMillis, time); persist() }
    }
    @Synchronized fun cancel(id: String) { if (jobs.any { it.id == id && it.outcome == JobOutcome.QUEUED }) result(id, JobOutcome.CANCELLED, "fleet_cancelled") }
    private fun JSONObject.identity() = FirmwareIdentity(getString("model"), getString("version"))
    private fun FirmwareIdentity.json() = JSONObject().put("model", model).put("version", version)
    private fun persist() {
        try {
            check(!loadFailed) { failed(msg("fleet_storage_error")) }
            val ds = JSONArray(); devices.forEach { ds.put(JSONObject().put("id", it.id).put("alias", it.alias).put("color", it.color).put("remembered", it.remembered)
                .put("identity", it.identity?.json()).put("chip", it.chip.name).put("serial", it.serial.orEmpty())) }
            val js = JSONArray(); jobs.forEach { js.put(JSONObject().put("id", it.id).put("device", it.deviceId).put("alias", it.alias).put("color", it.color)
                .put("midi", it.endpoint.id).put("path", it.endpoint.usbPath).put("generation", it.endpoint.generation)
                .put("package", it.packageKey).put("hash", it.hash).put("title", it.title).put("version", it.version)
                .put("expected", it.expected.json()).put("chip", it.chip.name).put("outcome", it.outcome.name)
                .put("forceUnknown", it.forceUnknown).put("phase", it.phase).put("progress", it.progress).put("time", it.updatedAt)
                .put("events", JSONArray().apply { it.events.forEach { e -> put(JSONObject().put("phase", e.phase).put("time", e.time)) } })) }
            write(file, JSONObject().put("devices", ds).put("jobs", js).put("confirmedJobId", confirmedJobId).put("historyRetentionMillis", historyRetentionMillis ?: JSONObject.NULL).toString().toByteArray())
            committedDevices = devices; committedJobs = jobs; committedConfirmation = confirmedJobId; committedRetention = historyRetentionMillis
        } catch (failure: Exception) {
            devices = committedDevices; jobs = committedJobs; confirmedJobId = committedConfirmation; historyRetentionMillis = committedRetention
            throw failure
        }
    }
}
