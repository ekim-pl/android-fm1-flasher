package pl.ekim.fm1_flasher

import java.io.File
import org.json.JSONObject

/** A journal is evidence of an unresolved attempt, never an instruction to resume writing. */
class FlashJournal(private val file: File, private val write: (File, ByteArray) -> Unit = LocalLibrary::atomicWrite) {
    fun unresolved(confirmedJobId: String? = null): Boolean = file.exists() && runCatching {
        val entry = JSONObject(file.readText())
        if (entry.has("job")) {
            // The atomic fleet result is the only commit point; the marker is never cleared first.
            confirmedJobId != entry.getString("job")
        } else entry.getString("outcome") != "confirmed"
    }.getOrDefault(true)

    fun record(hash: String, target: String, phase: String, outcome: String = "unconfirmed", jobId: String? = null) {
        val json = JSONObject().put("sha256", hash).put("target", target).put("phase", phase)
            .put("job", jobId).put("outcome", outcome).put("updatedAt", System.currentTimeMillis())
        write(file, json.toString().toByteArray(Charsets.UTF_8))
    }
}
