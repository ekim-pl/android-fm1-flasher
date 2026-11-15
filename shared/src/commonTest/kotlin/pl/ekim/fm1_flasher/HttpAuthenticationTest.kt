package pl.ekim.fm1_flasher

import kotlin.test.*

class HttpAuthenticationTest {
    @Test fun basicBearerAndApiKeyHeadersAreCorrectAndStringRepresentationsAreRedacted() {
        assertEquals("Basic dXNlcjpwYXNz", HttpCredentials(HttpAuthMethod.BASIC, "user", "pass").headers()["Authorization"])
        assertEquals("Bearer token", HttpCredentials(HttpAuthMethod.BEARER, secret = "token").headers()["Authorization"])
        assertEquals(mapOf("X-Private-Key" to "key"), HttpCredentials(HttpAuthMethod.API_KEY, secret = "key", header = "X-Private-Key").headers())
        assertEquals("Token private-key", HttpCredentials(HttpAuthMethod.API_KEY, secret = "Token private-key", header = "Authorization").headers()["Authorization"])
        assertFalse(HttpCredentials(HttpAuthMethod.BEARER, secret = "never-show-this").toString().contains("never-show-this"))
    }
    @Test fun malformedAndReservedHeadersNeverReachTheTransport() {
        for (header in listOf("Host", "Cookie", "Content-Length", "Bad\r\nHeader"))
            assertFails { HttpCredentials(HttpAuthMethod.API_KEY, secret = "key", header = header).headers() }
        for (secret in listOf("bad\r\nvalue", "secret\u0000", "has space", "秘密"))
            assertFails { HttpCredentials(HttpAuthMethod.BEARER, secret = secret).headers() }
        assertFails { HttpCredentials(HttpAuthMethod.BASIC, "bad:user", "secret").headers() }
    }
    @Test fun oauthRequiresHttpsAndClientCredentialsNeedsASecret() {
        val c = OAuthConfiguration(OAuthGrant.CLIENT_CREDENTIALS, "client", tokenUrl = "https://auth.example/token")
        assertFails { HttpCredentials(HttpAuthMethod.OAUTH2, oauth = c).validate() }
        HttpCredentials(HttpAuthMethod.OAUTH2, oauth = c, clientSecret = "client-secret").validate()
        assertFails { HttpCredentials(HttpAuthMethod.OAUTH2, oauth = c.copy(tokenUrl = "http://auth.example/token"), clientSecret = "secret").validate() }
        assertFalse(HttpCredentials(HttpAuthMethod.OAUTH2, clientSecret = "hidden-client-secret").toString().contains("hidden-client-secret"))
    }
}
