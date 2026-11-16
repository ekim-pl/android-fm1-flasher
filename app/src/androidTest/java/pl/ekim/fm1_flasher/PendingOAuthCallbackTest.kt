package pl.ekim.fm1_flasher

import android.content.Intent
import android.net.Uri
import android.test.InstrumentationTestCase
import java.security.KeyStore
import java.util.UUID

@Suppress("DEPRECATION")
class PendingOAuthCallbackTest : InstrumentationTestCase() {
    private fun fixture(test: (SourceCredentialStore, String) -> Unit) {
        val context = instrumentation.targetContext
        val name = "callback-test-${UUID.randomUUID()}"
        try { test(SourceCredentialStore(context, name), name) }
        finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("${context.packageName}.$name.v1") }
        }
    }
    private val config = OAuthConfiguration(OAuthGrant.PKCE, "client", "https://auth.example/authorize", "https://auth.example/token")
    private fun source() = CatalogSource("callback-${UUID.randomUUID()}", "Callback test", "https://publisher.example/catalog.json", authMethod = HttpAuthMethod.OAUTH2, oauth = config)
    private fun callback(address: String, code: String = "private-callback-code") = Uri.parse(OAuthManager.REDIRECT).buildUpon()
        .appendQueryParameter("state", Uri.parse(address).getQueryParameter("state"))
        .appendQueryParameter("code", code).build()
    private fun response() = CatalogResponse(200, """{"access_token":"private-token","token_type":"Bearer"}""".toByteArray())

    fun testBusyReconstructionEncryptionDeduplicationAndRealPkceExchange() = fixture { store, name ->
        val source = source()
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        var calls = 0
        var challenge: String? = null
        val manager = OAuthManager(store, { _, fields, _ ->
            calls++
            assertEquals("private-callback-code", fields["code"])
            assertEquals(challenge, OAuthManager.challenge(fields.getValue("code_verifier")))
            response()
        })
        val address = manager.begin(source)
        challenge = Uri.parse(address).getQueryParameter("code_challenge")
        val uri = callback(address)
        val first = PendingOAuthCallback(store)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        first.capture(intent)
        assertNull(intent.data)
        first.dispatch(true) { fail("Busy must defer") }
        val encrypted = instrumentation.targetContext.getSharedPreferences(name, 0).getString("_oauth_callback", null)!!
        assertFalse(encrypted.contains("private-callback-code"))
        assertFalse(encrypted.contains(uri.getQueryParameter("state")!!))
        val restored = PendingOAuthCallback(SourceCredentialStore(instrumentation.targetContext, name))
        restored.capture(Intent(Intent.ACTION_VIEW, callback(address, "replacement-code")))
        restored.dispatch(false) {
            // Also reject a reentrant dispatch during handoff.
            first.dispatch(false) { fail("Reentrant duplicate") }
            manager.finish(it) { source }
        }
        restored.capture(Intent(Intent.ACTION_VIEW, uri))
        restored.dispatch(false) { fail("Replay") }
        assertEquals(1, calls)
        assertNull(store.readOpaque("_oauth_callback"))
    }

    fun testLateCompletionCannotInvalidateNewLogin() = fixture { store, _ ->
        val source = source()
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        var calls = 0
        val manager = OAuthManager(store, { _, _, _ -> calls++; response() })
        val queue = PendingOAuthCallback(store)
        val old = callback(manager.begin(source))
        queue.capture(Intent(Intent.ACTION_VIEW, old))
        queue.dispatch(false) { } // Asynchronous service has not consumed the state yet.
        val fresh = callback(manager.begin(source))
        queue.capture(Intent(Intent.ACTION_VIEW, fresh))
        assertFalse(queue.complete(old))
        assertNotNull(store.readOpaque("_oauth_pending"))
        queue.dispatch(false) { manager.finish(it) { source }; assertFalse(queue.complete(it)) }
        queue.dispatch(false) { fail("Duplicate token exchange") }
        assertEquals(1, calls)
        assertNull(store.readOpaque("_oauth_callback"))
    }

    fun testOriginalStateDeadlineAndInvalidRedirectCannotOverwriteCallback() = fixture { store, _ ->
        var time = 100L
        val source = source()
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        val manager = OAuthManager(store, { _, _, _ -> fail("Expired callback exchanged"); response() }, { time })
        val address = manager.begin(source)
        val queue = PendingOAuthCallback(store) { time }
        val good = callback(address)
        queue.capture(Intent(Intent.ACTION_VIEW, good))
        for (uri in listOf(good.buildUpon().authority("evil").build(), good.buildUpon().fragment("secret").build(),
            good.buildUpon().appendQueryParameter("state", "duplicate").build(), Uri.parse("pl.ekim.fm1-flasher://oauth/callback?state=wrong&code=bad"))) {
            val incoming = Intent(Intent.ACTION_VIEW, uri)
            queue.capture(incoming)
            assertNull(incoming.data)
            assertEquals(good.toString(), org.json.JSONObject(store.readOpaque("_oauth_callback")!!).getString("uri"))
        }
        time += 600_000
        queue.capture(Intent(Intent.ACTION_VIEW, good))
        queue.dispatch(false) { fail("Expired callback dispatched") }
        assertNull(store.readOpaque("_oauth_callback"))
    }

    fun testSourceRevisionStillCheckedByProductionManager() = fixture { store, _ ->
        val source = source()
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        var calls = 0
        val manager = OAuthManager(store, { _, _, _ -> calls++; response() })
        val queue = PendingOAuthCallback(store)
        queue.capture(Intent(Intent.ACTION_VIEW, callback(manager.begin(source))))
        var rejected = false
        queue.dispatch(false) { uri ->
            try { manager.finish(uri) { source.copy(revision = source.revision + 1) } }
            catch (_: OperationFailed) { rejected = true }
        }
        assertTrue(rejected)
        assertEquals(0, calls)
        queue.dispatch(false) { fail("Consumed state must not be replayed") }
    }

    // Run twice with -e callbackColdPhase seed/recover to exercise distinct Android processes.
    fun testColdProcessRecovery() {
        val context = instrumentation.targetContext
        val name = "callback-cold-process-test"
        val phase = (instrumentation as android.test.InstrumentationTestRunner).arguments.getString("callbackColdPhase")
        val source = source().copy(id = "cold-process-source")
        val store = SourceCredentialStore(context, name)
        if (phase != "recover") {
            context.deleteSharedPreferences(name)
            store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
            val manager = OAuthManager(store)
            PendingOAuthCallback(store).apply {
                capture(Intent(Intent.ACTION_VIEW, callback(manager.begin(source))))
                dispatch(true) { fail("Busy must defer") }
            }
            assertNotNull(store.readOpaque("_oauth_callback"))
            if (phase == "seed") return
        }
        try {
            var calls = 0
            val manager = OAuthManager(store, { _, _, _ -> calls++; response() })
            val restored = PendingOAuthCallback(SourceCredentialStore(context, name))
            restored.dispatch(false) { manager.finish(it) { source } }
            restored.dispatch(false) { fail("Duplicate after cold start") }
            assertEquals(1, calls)
            assertEquals("private-token", store.read(source.id)!!.secret)
        } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("${context.packageName}.$name.v1") }
        }
    }

    fun testComposeRecreateWhileBusyDispatchesThroughRealServiceOnce() {
        val context = instrumentation.targetContext
        val store = SourceCredentialStore(context)
        val source = source()
        val database = CatalogDatabase.get(context)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val manager = OAuthManager(store, { _, _, _ -> calls.incrementAndGet(); response() })
        database.putSource(source)
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        val monitor = instrumentation.addMonitor(ComposeActivity::class.java.name, null, false)
        var activity = instrumentation.startActivitySync(Intent(context, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        val serviceField = ComposeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }
        val stateField = FlasherService::class.java.getDeclaredField("state").apply { isAccessible = true }
        val oauthField = FlasherService::class.java.getDeclaredField("oauth").apply { isAccessible = true }
        // Retain Service as a real running library task would while Activity unbinds on rotation.
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) = Unit
            override fun onServiceDisconnected(name: android.content.ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent(context, FlasherService::class.java), connection, android.content.Context.BIND_AUTO_CREATE))
        var service: FlasherService? = null
        var original: Any? = null
        fun waitUntil(check: () -> Boolean) {
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000
            while (!check() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
            assertTrue("Timed out waiting for lifecycle/service", check())
        }
        try {
            waitUntil { (serviceField.get(activity) as? FlasherService)?.state?.busy == false }
            service = serviceField.get(activity) as FlasherService
            original = oauthField.get(service)
            oauthField.set(service, manager)
            val uri = callback(manager.begin(source))
            instrumentation.runOnMainSync {
                stateField.set(service, service.state.copy(busy = true, offline = false))
                instrumentation.callActivityOnNewIntent(activity, Intent(Intent.ACTION_VIEW, uri))
            }
            assertEquals(0, calls.get())
            assertNotNull(store.readOpaque("_oauth_callback"))
            instrumentation.runOnMainSync {
                val saved = android.os.Bundle()
                instrumentation.callActivityOnSaveInstanceState(activity, saved)
                val parcel = android.os.Parcel.obtain()
                try {
                    parcel.writeBundle(saved)
                    assertFalse(parcel.marshall().toString(Charsets.UTF_16LE).contains("private-callback-code"))
                } finally { parcel.recycle() }
            }
            val previous = activity
            instrumentation.runOnMainSync { activity.recreate() }
            waitUntil { monitor.lastActivity != null && monitor.lastActivity !== previous }
            activity = monitor.lastActivity as ComposeActivity
            waitUntil { serviceField.get(activity) != null }
            assertEquals(0, calls.get())
            instrumentation.runOnMainSync {
                stateField.set(service, service.state.copy(busy = false))
                // Exercise Activity's production dispatch -> Service.libraryTask -> OAuthManager.finish.
                ComposeActivity::class.java.getDeclaredMethod("dispatchOAuthCallback").apply { isAccessible = true }.invoke(activity)
            }
            waitUntil { !service.state.busy && calls.get() == 1 }
            instrumentation.runOnMainSync { instrumentation.callActivityOnNewIntent(activity, Intent(Intent.ACTION_VIEW, uri)) }
            assertEquals(1, calls.get())
            assertEquals("private-token", store.read(source.id)!!.secret)
            assertFalse(service.state.logs.toString().contains("private-callback-code"))
            assertFalse(service.state.logs.toString().contains("private-token"))
        } finally {
            instrumentation.runOnMainSync {
                if (service != null) {
                    stateField.set(service, service.state.copy(busy = false))
                    if (original != null) oauthField.set(service, original)
                }
                activity.finish()
            }
            context.unbindService(connection)
            instrumentation.removeMonitor(monitor)
            store.remove(source.id); store.remove("_oauth_pending"); store.remove("_oauth_callback")
            database.writableDatabase.delete("sources", "id=?", arrayOf(source.id))
        }
    }
    fun testOfflineHandoffTerminatesWithoutRetryAndFreshLoginWorks() {
        val context = instrumentation.targetContext
        val store = SourceCredentialStore(context)
        val source = source()
        val database = CatalogDatabase.get(context)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val manager = OAuthManager(store, { _, _, _ -> calls.incrementAndGet(); response() })
        database.putSource(source)
        store.write(source.id, HttpCredentials(HttpAuthMethod.OAUTH2, oauth = config))
        val monitor = instrumentation.addMonitor(ComposeActivity::class.java.name, null, false)
        var activity = instrumentation.startActivitySync(Intent(context, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        val serviceField = ComposeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }
        val stateField = FlasherService::class.java.getDeclaredField("state").apply { isAccessible = true }
        val oauthField = FlasherService::class.java.getDeclaredField("oauth").apply { isAccessible = true }
        // Retain Service as a real running library task would while Activity unbinds on rotation.
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) = Unit
            override fun onServiceDisconnected(name: android.content.ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent(context, FlasherService::class.java), connection, android.content.Context.BIND_AUTO_CREATE))
        var service: FlasherService? = null
        var original: Any? = null
        fun waitUntil(check: () -> Boolean) {
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000
            while (!check() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
            assertTrue("Timed out waiting for lifecycle/service", check())
        }
        try {
            waitUntil { (serviceField.get(activity) as? FlasherService)?.state?.busy == false }
            service = serviceField.get(activity) as FlasherService
            original = oauthField.get(service)
            oauthField.set(service, manager)
            val uri = callback(manager.begin(source))
            instrumentation.runOnMainSync {
                stateField.set(service, service.state.copy(busy = false, offline = true))
                instrumentation.callActivityOnNewIntent(activity, Intent(Intent.ACTION_VIEW, uri))
            }
            waitUntil { !service.state.busy && store.readOpaque("_oauth_callback") == null }
            assertNull(store.readOpaque("_oauth_pending"))
            assertEquals(0, calls.get())
            instrumentation.runOnMainSync {
                stateField.set(service, service.state.copy(offline = false))
                repeat(3) {
                    ComposeActivity::class.java.getDeclaredMethod("dispatchOAuthCallback").apply { isAccessible = true }.invoke(activity)
                    instrumentation.callActivityOnNewIntent(activity, Intent(Intent.ACTION_VIEW, uri))
                }
            }
            assertEquals(0, calls.get())
            val fresh = callback(manager.begin(source))
            instrumentation.runOnMainSync { instrumentation.callActivityOnNewIntent(activity, Intent(Intent.ACTION_VIEW, fresh)) }
            waitUntil { !service.state.busy && calls.get() == 1 }
            assertEquals("private-token", store.read(source.id)!!.secret)
            assertFalse(service.state.logs.toString().contains("private-callback-code"))
            assertFalse(service.state.logs.toString().contains("private-token"))
        } finally {
            instrumentation.runOnMainSync {
                if (service != null) {
                    stateField.set(service, service.state.copy(busy = false))
                    if (original != null) oauthField.set(service, original)
                }
                activity.finish()
            }
            context.unbindService(connection)
            instrumentation.removeMonitor(monitor)
            store.remove(source.id); store.remove("_oauth_pending"); store.remove("_oauth_callback")
            database.writableDatabase.delete("sources", "id=?", arrayOf(source.id))
        }
    }
}
