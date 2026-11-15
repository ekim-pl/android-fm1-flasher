package pl.ekim.fm1_flasher

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SourceNetworkTest {
    private val url = "https://example.org/firmware/catalog.json"
    private val scope = "https://example.org/firmware/"
    private fun reply(request: Request, code: Int = 200, body: ResponseBody = "{}".toResponseBody(), headers: Map<String, String> = emptyMap()): Response =
        Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("test").body(body).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
    private fun client(action: (Request) -> Response) = OkHttpClient.Builder().addInterceptor { action(it.request()) }.build()

    @Test fun authenticationNeverFollowsAnAllowedCrossOriginRedirect() {
        val seen = mutableListOf<Request>()
        val transport = client { request ->
            seen += request
            if (seen.size == 1) reply(request, 302, headers = mapOf("Location" to "https://cdn.example/file")) else reply(request)
        }
        SourceNetwork.requestWith(transport, url, 100, { true }, authentication = { mapOf("Authorization" to "Bearer private-token", "X-API-Key" to "private-key") })
        assertEquals("Bearer private-token", seen.first().header("Authorization"))
        assertNull(seen.last().header("Authorization")); assertNull(seen.last().header("X-API-Key"))
    }
    @Test fun tokenPostRedirectsAreRejectedAndNeverReplayClientSecrets() {
        var calls = 0
        val transport = client { request -> calls++; assertEquals("POST", request.method); reply(request, 302, headers = mapOf("Location" to "https://evil.example/token")) }
        assertThrows(IllegalStateException::class.java) { SourceNetwork.requestWith(transport, url, 100, { true }, form = mapOf("client_secret" to "secret")) }
        assertEquals(1, calls)
    }
    @Test fun conditionalHeadersAndStatusesSurviveTheTransport() {
        val transport = client { request ->
            assertEquals("version1", request.header("If-None-Match"))
            assertTrue(request.header("User-Agent")!!.startsWith("FM1FlasherAndroid/"))
            reply(request, 304, headers = mapOf("ETag" to "version2", "Retry-After" to "86400"))
        }
        val result = SourceNetwork.requestWith(transport, url, 100, { UrlScope.contains(scope, it) }, mapOf("If-None-Match" to "version1"))
        assertEquals(304, result.code); assertEquals("version2", result.etag); assertEquals("86400", result.retryAfter)
    }

    @Test fun forbiddenRedirectIsRejectedBeforeASecondRequest() {
        var calls = 0
        val transport = client { request -> calls++; reply(request, 302, headers = mapOf("Location" to "https://evil.test/file")) }
        assertThrows(IllegalStateException::class.java) { SourceNetwork.requestWith(transport, url, 100, { UrlScope.contains(scope, it) }) }
        assertEquals(1, calls)
    }

    @Test fun relativeRedirectAllowedButLoopsAreBounded() {
        var calls = 0
        val transport = client { request -> calls++; reply(request, 302, headers = mapOf("Location" to "./catalog.json")) }
        assertThrows(IllegalStateException::class.java) { SourceNetwork.requestWith(transport, url, 100, { UrlScope.contains(scope, it) }) }
        assertEquals(6, calls)
    }

    @Test fun chunkedOversizeBodiesAndDisabledSourcesAreRejected() {
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source() = Buffer().write(ByteArray(101))
        }
        var calls = 0
        val transport = client { request -> calls++; reply(request, body = body) }
        assertThrows(IllegalStateException::class.java) { SourceNetwork.requestWith(transport, url, 100, { true }) }
        assertThrows(IllegalStateException::class.java) { SourceNetwork.requestWith(transport, url, 100, { true }, active = { false }) }
        assertEquals(1, calls)
    }

    @Test fun literalPrivateIpAndAnExpiredDeadlineNeverReachTheTransport() {
        var calls = 0
        val transport = client { request -> calls++; reply(request) }
        assertThrows(IllegalArgumentException::class.java) { SourceNetwork.requestWith(transport, "https://127.0.0.1/file", 100, { true }) }
        var time = 0L
        assertThrows(IllegalStateException::class.java) {
            SourceNetwork.requestWith(transport, url, 100, { true }, clock = { time.also { time += TimeUnit.SECONDS.toNanos(61) } })
        }
        assertEquals(0, calls)
    }
    @Test fun trickleCannotExtendTheDeadlineAndResponseIsClosedOnFailure() {
        var time = 0L
        var closed = false
        val input = object : okio.Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                time += TimeUnit.SECONDS.toNanos(20); sink.writeByte(1); return 1
            }
            override fun timeout() = okio.Timeout.NONE
            override fun close() { closed = true }
        }
        val body = object : ResponseBody() {
            private val data = input.buffer()
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source() = data
        }
        assertThrows(IllegalStateException::class.java) {
            SourceNetwork.requestWith(client { reply(it, body = body) }, url, 100, { true }, clock = { time })
        }
        assertEquals(TimeUnit.SECONDS.toNanos(60), time)
        assertTrue(closed)
    }

}
