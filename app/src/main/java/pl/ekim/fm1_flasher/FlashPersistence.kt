package pl.ekim.fm1_flasher

/** Storage failures must not escape a callback while the instrument is being written. */
internal class FlashPersistence(
    private val fleet: FleetStore,
    private val journal: FlashJournal,
    private val job: FlashJob,
    private val onFailure: (Exception) -> Unit,
) {
    var failure: Exception? = null
        private set

    fun begin() {
        try {
            fleet.result(job.id, JobOutcome.RUNNING, "flash_preparing")
            journal.record(job.hash, job.deviceId, "preparing", jobId = job.id)
        } catch (error: Exception) {
            failedWrite(error)
            throw error
        }
    }

    // Progress and reconnects are volatile. No filesystem work runs on the transfer path.
    fun progress(phase: String, percent: Int = -1) = checkpoint {
        fleet.result(job.id, JobOutcome.RUNNING, phase, percent, durable = false)
    }

    fun rebind(endpoint: Endpoint, identity: FirmwareIdentity? = null) = checkpoint {
        fleet.rebind(job.deviceId, endpoint, identity, durable = false)
    }

    private fun checkpoint(action: () -> Unit) {
        try { action() } catch (error: Exception) { failedWrite(error) }
    }

    fun confirmed(): Boolean {
        if (failure != null) { unconfirmed("fleet_storage_error"); return false }
        try {
            fleet.result(job.id, JobOutcome.CONFIRMED, "fleet_confirmed", 100)
            return true
        } catch (error: Exception) {
            failedWrite(error)
            unconfirmed("fleet_storage_error")
            return false
        }
    }

    fun unconfirmed(reason: String) {
        try { fleet.result(job.id, JobOutcome.UNCONFIRMED, reason) }
        catch (error: Exception) {
            failedWrite(error)
            // A second failed write rolls back to RUNNING; UI must still show uncertainty.
            fleet.result(job.id, JobOutcome.UNCONFIRMED, "fleet_storage_error", durable = false)
        }
    }

    private fun failedWrite(error: Exception) {
        if (failure != null) return
        failure = error
        runCatching { onFailure(error) }
    }
}
