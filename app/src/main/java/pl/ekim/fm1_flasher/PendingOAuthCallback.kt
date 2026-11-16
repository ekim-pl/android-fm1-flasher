package pl.ekim.fm1_flasher

import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.security.MessageDigest

/** The callback and PKCE request share Keystore encryption and the original request deadline. */
internal class PendingOAuthCallback(
    private val store: SourceCredentialStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private const val RECORD = "_oauth_callback"
        private val LOCK = Any()
        private var dispatchedState: String? = null
    }

    fun capture(incoming: Intent?) = synchronized(LOCK) {
        val uri = incoming?.data ?: return@synchronized
        if (uri.scheme != "pl.ekim.fm1-flasher") return@synchronized
        try {
            val pending = matchingRequest(uri) ?: return@synchronized
            // Keep the first response for this state; redelivery cannot replace it or extend its TTL.
            val existing = store.readOpaque(RECORD)?.let(::JSONObject)
            if (existing?.optString("state") == pending.getString("state")) return@synchronized
            store.writeOpaque(RECORD, JSONObject().put("uri", uri.toString())
                .put("state", pending.getString("state")).put("expires", pending.getLong("expires")).toString())
        } finally {
            // Never copy authorization codes into Activity saved state or retained intents.
            incoming.data = null
        }
    }

    fun dispatch(busy: Boolean, finish: (Uri) -> Unit) = synchronized(LOCK) {
        val saved = store.readOpaque(RECORD)?.let(::JSONObject) ?: return@synchronized
        val uri = Uri.parse(saved.getString("uri"))
        if (now() >= saved.getLong("expires") || matchingRequest(uri) == null) {
            store.remove(RECORD)
            return@synchronized
        }
        val state = saved.getString("state")
        if (busy || dispatchedState == state) return@synchronized
        // Claim before handing off to the asynchronous Service task or another Activity instance.
        dispatchedState = state
        try { finish(uri) } catch (failure: Exception) {
            dispatchedState = null
            throw failure
        }
        // Keep encrypted until OAuthManager consumes state. A new process can recover pre-dispatch work.
        // Once OAuthManager consumes state, an interrupted exchange requires a fresh login, never replay.
    }

    private fun matchingRequest(uri: Uri): JSONObject? {
        if (uri.toString().length > 16384 || uri.isOpaque || uri.fragment != null ||
            uri.buildUpon().clearQuery().fragment(null).build().toString() != OAuthManager.REDIRECT) return null
        val state = uri.getQueryParameters("state").singleOrNull() ?: return null
        val pending = store.readOpaque("_oauth_pending")?.let(::JSONObject) ?: return null
        return pending.takeIf {
            it.optString("redirect") == OAuthManager.REDIRECT && now() < it.getLong("expires") &&
                MessageDigest.isEqual(state.toByteArray(Charsets.UTF_8), it.getString("state").toByteArray(Charsets.UTF_8))
        }
    }
}
