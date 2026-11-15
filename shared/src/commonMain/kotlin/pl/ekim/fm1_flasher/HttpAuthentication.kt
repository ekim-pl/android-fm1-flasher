package pl.ekim.fm1_flasher

import kotlin.io.encoding.Base64

enum class HttpAuthMethod(val labelKey: String) {
    NONE("auth_none"), BASIC("auth_basic"), BEARER("auth_bearer"), API_KEY("auth_api_key"), OAUTH2("auth_oauth2")
}

/** Secrets are deliberately excluded from string representations and source metadata. */
class HttpCredentials(val method: HttpAuthMethod, val username: String = "", val secret: String = "", val header: String = "X-API-Key",
    val oauth: OAuthConfiguration = OAuthConfiguration(), val clientSecret: String = "", val refreshToken: String = "", val expiresAt: Long = 0,
) {
    fun validate() {
        require(secret.length <= 8192 && username.length <= 256 && secret.none { it == '\r' || it == '\n' }) { invalid(msg("auth_invalid")) }
        if (method !in listOf(HttpAuthMethod.NONE, HttpAuthMethod.OAUTH2)) require(secret.isNotBlank()) { invalid(msg("auth_secret_required")) }
        if (method in listOf(HttpAuthMethod.BEARER, HttpAuthMethod.OAUTH2)) require(secret.all { it.code in 0x21..0x7e }) { invalid(msg("auth_invalid")) }
        if (method == HttpAuthMethod.OAUTH2) {
            require(oauth.clientId.isNotBlank() && oauth.clientId.length <= 1024 && oauth.scope.length <= 2048 && clientSecret.length <= 8192 && refreshToken.length <= 8192) { invalid(msg("auth_invalid")) }
            UrlScope.canonical(oauth.tokenUrl)
            if (oauth.grant == OAuthGrant.PKCE) UrlScope.canonical(oauth.authorizationUrl)
            else require(clientSecret.isNotBlank()) { invalid(msg("auth_secret_required")) }
        }
        if (method == HttpAuthMethod.BASIC) require(username.isNotBlank() && ':' !in username && username.none { it == '\r' || it == '\n' }) { invalid(msg("auth_invalid")) }
        if (method == HttpAuthMethod.API_KEY) require(secret.all { it.code in 0x20..0x7e }) { invalid(msg("auth_invalid")) }
        if (method == HttpAuthMethod.API_KEY) require(header.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")) && header.lowercase() !in setOf("proxy-authorization", "host", "cookie", "content-length", "connection", "transfer-encoding", "user-agent")) { invalid(msg("auth_invalid")) }
    }
    fun headers(): Map<String, String> {
        validate()
        return when (method) {
            HttpAuthMethod.NONE -> emptyMap()
            HttpAuthMethod.BASIC -> mapOf("Authorization" to "Basic ${Base64.encode("$username:$secret".encodeToByteArray())}")
            HttpAuthMethod.BEARER -> mapOf("Authorization" to "Bearer $secret")
            HttpAuthMethod.API_KEY -> mapOf(header to secret)
            HttpAuthMethod.OAUTH2 -> { check(secret.isNotBlank()) { failed(msg("auth_login_required")) }; mapOf("Authorization" to "Bearer $secret") }
        }
    }
    fun equivalentTo(other: HttpCredentials?) = other != null && method == other.method && username == other.username &&
        secret == other.secret && header == other.header && oauth == other.oauth && clientSecret == other.clientSecret &&
        refreshToken == other.refreshToken && expiresAt == other.expiresAt
    override fun toString() = "HttpCredentials(method=$method, secret=[redacted])"
}
