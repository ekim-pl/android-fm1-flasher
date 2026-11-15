package pl.ekim.fm1_flasher

import android.content.Context
import android.net.Uri
import android.test.InstrumentationTestCase
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID

@Suppress("DEPRECATION")
class SourceAuthenticationTest : InstrumentationTestCase() {
    private fun fixture(test: (SourceCredentialStore, String) -> Unit) {
        val context = instrumentation.targetContext
        val name = "source-auth-test-${UUID.randomUUID()}"
        try { test(SourceCredentialStore(context, name), name) }
        finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("${context.packageName}.$name.v1") }
        }
    }
    fun testCredentialPersistenceEncryptionAndSourceBinding() = fixture { store, name ->
        val value = "private-fixture-secret"
        store.write("source-a", HttpCredentials(HttpAuthMethod.BEARER, secret = value))
        val prefs = instrumentation.targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        val encrypted = prefs.getString("source-a", null)!!
        assertFalse(encrypted.contains(value))
        assertEquals(value, SourceCredentialStore(instrumentation.targetContext, name).read("source-a")!!.secret)
        prefs.edit().putString("source-b", encrypted).commit()
        var rejected = false
        try { store.read("source-b") } catch (_: OperationFailed) { rejected = true }
        assertTrue("Ciphertext cannot be reassigned to a different source", rejected)
        val stamp = store.readStamped("source-a")!!.second
        SourceCredentialStore(instrumentation.targetContext, name).write("source-a", HttpCredentials(HttpAuthMethod.BEARER, secret = "replacement-token"))
        var staleRejected = false
        try { store.writeIfUnchanged("source-a", stamp, HttpCredentials(HttpAuthMethod.BEARER, secret = "late-token")) } catch (_: OperationFailed) { staleRejected = true }
        assertTrue(staleRejected); assertEquals("replacement-token", store.read("source-a")!!.secret)
        store.remove("source-a"); assertNull(store.read("source-a"))
        staleRejected = false
        try { store.writeIfUnchanged("source-a", stamp, HttpCredentials(HttpAuthMethod.BEARER, secret = "resurrected-token")) } catch (_: OperationFailed) { staleRejected = true }
        assertTrue(staleRejected); assertNull(store.read("source-a"))
    }
    fun testPkceStateReplayAndRefreshTokenFlow() = fixture { store, _ ->
        var time = 100_000L
        val config = OAuthConfiguration(OAuthGrant.PKCE, "client", "https://auth.example/authorize", "https://auth.example/token", "read")
        val source = CatalogSource("source", "Private", "https://publisher.example/catalog.json", authMethod = HttpAuthMethod.OAUTH2, oauth = config)
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        val calls = mutableListOf<Map<String, String>>()
        val manager = OAuthManager(store, { endpoint, fields, headers ->
            assertEquals(config.tokenUrl, endpoint); assertEquals("application/json", headers["Accept"])
            assertFalse(fields.containsKey("scope"))
            calls += fields
            CatalogResponse(200, JSONObject().put("access_token", "token-${calls.size}").put("token_type", "Bearer").put("refresh_token", "refresh-secret").put("expires_in", 60).toString().toByteArray())
        }, { time })
        val authorization = Uri.parse(manager.begin(source))
        assertEquals("S256", authorization.getQueryParameter("code_challenge_method"))
        assertEquals(OAuthManager.REDIRECT, authorization.getQueryParameter("redirect_uri"))
        fun callback(state: String) = Uri.parse(OAuthManager.REDIRECT).buildUpon().appendQueryParameter("state", state).appendQueryParameter("code", "one-time-code").build()
        var rejected = false
        try { manager.finish(callback("wrong-state")) { source } } catch (_: OperationFailed) { rejected = true }
        assertTrue(rejected); assertTrue(calls.isEmpty())
        val result = callback(authorization.getQueryParameter("state")!!)
        assertEquals(source.id, manager.finish(result) { source })
        assertEquals(authorization.getQueryParameter("code_challenge"), OAuthManager.challenge(calls.single().getValue("code_verifier")))
        assertEquals(OAuthManager.REDIRECT, calls.single()["redirect_uri"])
        assertEquals("token-1", manager.credentials(source)!!.secret)
        rejected = false
        try { manager.finish(result) { source } } catch (_: OperationFailed) { rejected = true }
        assertTrue(rejected); assertEquals(1, calls.size)
        time += 70_000
        assertEquals("token-2", manager.credentials(source)!!.secret)
        assertEquals("refresh_token", calls.last()["grant_type"])
        assertEquals("refresh-secret", calls.last()["refresh_token"])
    }
    fun testLegacyPendingAuthorizationSurvivesUpgrade() = fixture { store, _ ->
        val config = OAuthConfiguration(OAuthGrant.PKCE, "client", "https://auth.example/authorize", "https://auth.example/token")
        val source = CatalogSource("source", "Private", "https://publisher.example/catalog.json", authMethod = HttpAuthMethod.OAUTH2, oauth = config)
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        val manager = OAuthManager(store, { _, fields, _ ->
            assertEquals(OAuthManager.LEGACY_REDIRECT, fields["redirect_uri"])
            CatalogResponse(200, """{"access_token":"token","token_type":"Bearer"}""".toByteArray())
        }, { 100_000L })
        val authorization = Uri.parse(manager.begin(source))
        val state = authorization.getQueryParameter("state")!!
        fun callback(redirect: String) = Uri.parse(redirect).buildUpon().appendQueryParameter("state", state).appendQueryParameter("code", "code").build()
        var rejected = false
        try { manager.finish(callback(OAuthManager.LEGACY_REDIRECT)) { source } } catch (_: OperationFailed) { rejected = true }
        assertTrue("A new request cannot be completed through the old redirect", rejected)
        val pending = JSONObject(store.readOpaque("_oauth_pending")!!).apply { remove("redirect") }
        store.writeOpaque("_oauth_pending", pending.toString())
        rejected = false
        try { manager.finish(callback(OAuthManager.REDIRECT)) { source } } catch (_: OperationFailed) { rejected = true }
        assertTrue("An old request cannot be completed through the new redirect", rejected)
        assertEquals(source.id, manager.finish(callback(OAuthManager.LEGACY_REDIRECT)) { source })
    }

    fun testClientCredentialsBasicAndPostAndExpiredTokens() = fixture { store, _ ->
        var time = 100_000L
        for (clientAuth in OAuthClientAuth.entries) {
            val config = OAuthConfiguration(OAuthGrant.CLIENT_CREDENTIALS, "client", tokenUrl = "https://auth.example/token", scope = "read", clientAuth = clientAuth)
            val source = CatalogSource("client-${clientAuth.name}", "Private", "https://publisher.example/catalog.json", authMethod = HttpAuthMethod.OAUTH2, oauth = config)
            store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config, clientSecret = "client-secret"))
            var calls = 0
            val manager = OAuthManager(store, { _, fields, headers ->
                calls++; assertEquals("client_credentials", fields["grant_type"]); assertEquals("read", fields["scope"])
                if (clientAuth == OAuthClientAuth.BASIC) {
                    assertTrue(headers["Authorization"]!!.startsWith("Basic ")); assertFalse(fields.containsKey("client_secret"))
                } else { assertEquals("client-secret", fields["client_secret"]); assertNull(headers["Authorization"]) }
                CatalogResponse(200, """{"access_token":"service-token","token_type":"Bearer","expires_in":60}""".toByteArray())
            }, { time })
            assertEquals("service-token", manager.credentials(source)!!.secret)
            assertEquals("service-token", manager.credentials(source)!!.secret); assertEquals(1, calls)
            time += 70_000
            manager.credentials(source); assertEquals(2, calls)
        }
    }
    fun testConcurrentRefreshUsesOneGrantAcrossManagerInstances() = fixture { store, name ->
        val config = OAuthConfiguration(OAuthGrant.CLIENT_CREDENTIALS, "client", tokenUrl = "https://auth.example/token")
        val source = CatalogSource("concurrent", "Private", "https://publisher.example/catalog.json", authMethod = HttpAuthMethod.OAUTH2, oauth = config)
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config, clientSecret = "secret"))
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val jobs = (1..2).map {
                executor.submit<String> {
                    start.await()
                    OAuthManager(SourceCredentialStore(instrumentation.targetContext, name), { _, _, _ ->
                        calls.incrementAndGet(); Thread.sleep(100)
                        CatalogResponse(200, """{"access_token":"shared-token","token_type":"Bearer","expires_in":3600}""".toByteArray())
                    }).credentials(source)!!.secret
                }
            }
            start.countDown()
            jobs.forEach { assertEquals("shared-token", it.get(10, java.util.concurrent.TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
        } finally { executor.shutdownNow() }
    }
    fun testPkceChallengeMatchesRfc7636() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuthManager.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    fun testServiceSavesSecretsSeparatelyAndChangingOriginCannotRetainThem() {
        val context = instrumentation.targetContext
        val connected = java.util.concurrent.CountDownLatch(1)
        var service: FlasherService? = null
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) { service = (binder as FlasherService.LocalBinder).service; connected.countDown() }
            override fun onServiceDisconnected(name: android.content.ComponentName) = Unit
        }
        val database = CatalogDatabase.get(context)
        val name = "auth-fixture-${UUID.randomUUID()}"
        val password = "private-service-fixture-secret"
        assertTrue(context.bindService(android.content.Intent(context, FlasherService::class.java), connection, Context.BIND_AUTO_CREATE))
        fun waitIdle() { repeat(100) { if (service?.state?.busy == false) return; Thread.sleep(50) }; error("Service did not become idle") }
        try {
            assertTrue(connected.await(10, java.util.concurrent.TimeUnit.SECONDS)); waitIdle()
            var savedId: String? = null
            var failure: AppText? = null
            val saved = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                service!!.configureSource(null, name, "https://publisher.example/file.fwsc", 24, emptySet(), emptySet(), false,
                    CatalogProviderType.PKG_URL, HttpCredentials(HttpAuthMethod.BASIC, "owner", password)) { id, error -> savedId = id; failure = error; saved.countDown() }
            }
            assertTrue(saved.await(10, java.util.concurrent.TimeUnit.SECONDS)); assertNull(failure)
            val id = savedId!!
            assertEquals(password, SourceCredentialStore(context).read(id)!!.secret)
            database.readableDatabase.rawQuery("SELECT json FROM sources WHERE id=?", arrayOf(id)).use { c -> assertTrue(c.moveToFirst()); assertFalse(c.getString(0).contains(password)) }
            val rejected = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                service!!.configureSource(id, name, "https://another.example/file.fwsc", 24, emptySet(), emptySet(), false,
                    CatalogProviderType.PKG_URL, HttpCredentials(HttpAuthMethod.BASIC, "owner")) { _, error -> failure = error; rejected.countDown() }
            }
            assertTrue(rejected.await(10, java.util.concurrent.TimeUnit.SECONDS)); assertNotNull(failure)
            assertEquals("https://publisher.example/file.fwsc", database.source(id).url)
            assertFalse(database.source(id).deleted)
            assertEquals(password, SourceCredentialStore(context).read(id)!!.secret)
        } finally {
            context.unbindService(connection)
            database.sources().filter { it.name == name }.forEach { source ->
                SourceCredentialStore(context).remove(source.id)
                database.writableDatabase.delete("sources", "id=?", arrayOf(source.id))
            }
        }
    }

}
