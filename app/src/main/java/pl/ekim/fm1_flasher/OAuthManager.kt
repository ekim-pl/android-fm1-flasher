package pl.ekim.fm1_flasher

import android.net.Uri
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Browser authorization uses S256 PKCE; tokens and pending verifiers stay in the encrypted store. */
internal class OAuthManager(
    private val store: SourceCredentialStore,
    private val post: (String, Map<String, String>, Map<String, String>) -> CatalogResponse = SourceNetwork::postForm,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val REDIRECT = "pl.ekim.fm1-flasher://oauth/callback"
        private val NETWORK_LOCK = Any()
        private const val PENDING = "_oauth_pending"
        internal fun challenge(verifier: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
    private fun random() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun begin(source: CatalogSource): String {
        check(source.authMethod == HttpAuthMethod.OAUTH2 && source.oauth.grant == OAuthGrant.PKCE && !source.deleted) { failed(msg("auth_invalid")) }
        val credentials = store.read(source.id) ?: failed(msg("auth_login_required"))
        check(credentials.oauth == source.oauth) { failed(msg("auth_login_required")) }
        val verifier = random(); val state = random()
        store.writeOpaque(PENDING, JSONObject().put("source", source.id).put("revision", source.revision).put("state", state)
            .put("verifier", verifier).put("redirect", REDIRECT).put("expires", now() + 10 * 60_000).toString())
        val builder = Uri.parse(source.oauth.authorizationUrl).buildUpon().appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", source.oauth.clientId).appendQueryParameter("redirect_uri", REDIRECT)
        if (source.oauth.scope.isNotBlank()) builder.appendQueryParameter("scope", source.oauth.scope)
        return builder.appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge(verifier)).appendQueryParameter("code_challenge_method", "S256").build().toString()
    }
    fun finish(uri: Uri, source: (String) -> CatalogSource): String = synchronized(NETWORK_LOCK) { finishLocked(uri, source) }
    private fun finishLocked(uri: Uri, source: (String) -> CatalogSource): String {
        val pending = store.readOpaque(PENDING)?.let(::JSONObject) ?: failed(msg("oauth_callback_error"))
        fun single(name: String) = uri.getQueryParameters(name).singleOrNull()
        check(pending.optString("redirect") == REDIRECT && uri.buildUpon().clearQuery().fragment(null).build().toString() == REDIRECT && uri.fragment == null &&
            single("state")?.let { MessageDigest.isEqual(it.toByteArray(), pending.getString("state").toByteArray()) } == true && now() < pending.getLong("expires")) { failed(msg("oauth_callback_error")) }
        store.remove(PENDING)
        val current = source(pending.getString("source"))
        check(current.revision == pending.getLong("revision") && !current.deleted && current.authMethod == HttpAuthMethod.OAUTH2) { failed(msg("source_changed")) }
        val code = single("code")?.takeIf { it.length <= 8192 && it.isNotBlank() } ?: failed(msg("oauth_callback_error"))
        val (old, version) = store.readStamped(current.id) ?: failed(msg("auth_login_required"))
        exchange(current.id, old, mapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT, "code_verifier" to pending.getString("verifier")), version)
        return current.id
    }
    fun authorizeClient(source: CatalogSource): HttpCredentials = synchronized(NETWORK_LOCK) { authorizeClientLocked(source) }
    private fun authorizeClientLocked(source: CatalogSource): HttpCredentials {
        check(source.authMethod == HttpAuthMethod.OAUTH2 && source.oauth.grant == OAuthGrant.CLIENT_CREDENTIALS && !source.deleted) { failed(msg("auth_invalid")) }
        val (c, version) = store.readStamped(source.id) ?: failed(msg("auth_login_required"))
        check(c.oauth == source.oauth) { failed(msg("auth_login_required")) }
        return exchange(source.id, c, mapOf("grant_type" to "client_credentials"), version)
    }
    fun credentials(source: CatalogSource): HttpCredentials? = if (source.authMethod == HttpAuthMethod.OAUTH2)
        synchronized(NETWORK_LOCK) { credentialsLocked(source) } else credentialsLocked(source)
    private fun credentialsLocked(source: CatalogSource): HttpCredentials? {
        if (source.authMethod == HttpAuthMethod.NONE) return null
        val (c, version) = store.readStamped(source.id) ?: failed(msg("auth_login_required"))
        check(c.method == source.authMethod && c.username == source.authUsername && c.header == source.authHeader &&
            (c.method != HttpAuthMethod.OAUTH2 || c.oauth == source.oauth)) { failed(msg("auth_login_required")) }
        if (c.method != HttpAuthMethod.OAUTH2) return c
        if (c.secret.isNotBlank() && (c.expiresAt == 0L || c.expiresAt > now())) return c
        return when {
            c.refreshToken.isNotBlank() -> exchange(source.id, c, mapOf("grant_type" to "refresh_token", "refresh_token" to c.refreshToken), version)
            c.oauth.grant == OAuthGrant.CLIENT_CREDENTIALS -> exchange(source.id, c, mapOf("grant_type" to "client_credentials"), version)
            else -> failed(msg("auth_login_required"))
        }
    }
    private fun exchange(id: String, old: HttpCredentials, grant: Map<String, String>, version: String): HttpCredentials {
        val fields = grant.toMutableMap().apply { put("client_id", old.oauth.clientId); if (grant["grant_type"] == "client_credentials" && old.oauth.scope.isNotBlank()) put("scope", old.oauth.scope) }
        val headers = mutableMapOf("Accept" to "application/json")
        if (old.clientSecret.isNotBlank()) {
            if (old.oauth.clientAuth == OAuthClientAuth.BASIC) {
                fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
                headers["Authorization"] = okhttp3.Credentials.basic(encode(old.oauth.clientId), encode(old.clientSecret))
            } else fields["client_secret"] = old.clientSecret
        }
        val response = post(old.oauth.tokenUrl, fields, headers)
        check(response.code == 200) { failed(AppText.Joined(listOf(msg("oauth_token_error"), msg(R.string.http_error, response.code)))) }
        val tokens = try {
            val text = response.bytes.toString(Charsets.UTF_8); JsonLimits.check(text)
            val tokener = org.json.JSONTokener(text)
            val j = tokener.nextValue() as? JSONObject ?: error("Invalid token response")
            require(tokener.nextClean() == '\u0000')
            val access = j.getString("access_token")
            require(j.optString("token_type").equals("Bearer", true) && access.isNotBlank() && access.length <= 8192)
            val refresh = j.optString("refresh_token").ifBlank { old.refreshToken }
            val expires = if (j.has("expires_in")) now() + j.getLong("expires_in").also { require(it in 1..2_592_000) } * 1000 else 0
            HttpCredentials(HttpAuthMethod.OAUTH2, old.username, access, old.header, old.oauth, old.clientSecret, refresh, expires).also { it.validate() }
        } catch (_: Exception) { failed(msg("oauth_token_error")) }
        store.writeIfUnchanged(id, version, tokens)
        return tokens
    }
}
