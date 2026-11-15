package pl.ekim.fm1_flasher

/** Attachment IDs are deliberately session-local; USB paths and firmware are not hardware IDs. */
data class FleetDevice(
    val id: String, val alias: String, val color: Int = 0,
    val endpoint: Endpoint? = null, val identity: FirmwareIdentity? = null,
    val chip: FlashChip = FlashChip.UNKNOWN, val serial: String? = null,
)
enum class JobOutcome { QUEUED, RUNNING, CONFIRMED, UNCONFIRMED, CANCELLED }
data class JobEvent(val phase: String, val time: Long)
data class FlashJob(
    val id: String, val deviceId: String, val alias: String, val endpoint: Endpoint,
    val packageKey: String, val hash: String, val title: String, val version: String,
    val expected: FirmwareIdentity, val chip: FlashChip,
    val outcome: JobOutcome = JobOutcome.QUEUED, val phase: String = "fleet_queued",
    val progress: Int = -1, val updatedAt: Long = 0, val events: List<JobEvent> = emptyList(),
)
object FleetPolicy {
    fun runPlan(jobs: List<FlashJob>, stopped: () -> Boolean, prepare: (FlashJob) -> Unit, run: (FlashJob) -> Unit) {
        val plan = jobs.filter { it.outcome == JobOutcome.QUEUED }.toList()
        plan.forEach(prepare)
        for (job in plan) { if (stopped()) break; run(job) }
    }
    fun token(jobs: List<FlashJob>) = jobs.filter { it.outcome == JobOutcome.QUEUED }.joinToString("|") { it.id }
    fun interrupted(jobs: List<FlashJob>) = jobs.map {
        if (it.outcome == JobOutcome.RUNNING) it.copy(outcome = JobOutcome.UNCONFIRMED, phase = "fleet_interrupted", progress = -1) else it
    }
    fun ready(job: FlashJob, device: FleetDevice?) = device != null && device.id == job.deviceId &&
        device.endpoint == job.endpoint && device.identity?.sameAs(job.expected) == true && device.chip == job.chip
    fun pinned(jobs: List<FlashJob>, key: String) = jobs.any {
        it.packageKey == key && it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING)
    }
}
