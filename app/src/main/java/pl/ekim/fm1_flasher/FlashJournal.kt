package pl.ekim.fm1_flasher

import java.io.File
import org.json.JSONObject

/** A journal is evidence of an unresolved attempt, never an instruction to resume writing. */
class FlashJournal(private val file: File) {
    fun unresolved(): Boolean = file.exists() && runCatching {
        JSONObject(file.readText()).getString("outcome") != "confirmed"
    }.getOrDefault(true)

    fun record(hash: String, target: String, phase: String, outcome: String = "unconfirmed") {
        val json = JSONObject().put("sha256", hash).put("target", target).put("phase", phase)
            .put("outcome", outcome).put("updatedAt", System.currentTimeMillis())
        LocalLibrary.atomicWrite(file, json.toString().toByteArray(Charsets.UTF_8))
    }
}
