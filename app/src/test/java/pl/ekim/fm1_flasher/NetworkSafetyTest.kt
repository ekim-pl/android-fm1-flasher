package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class NetworkSafetyTest {
    private class Connection(url: URL, val code: Int = 200, val body: InputStream = ByteArrayInputStream(byteArrayOf(1)), val location: String? = null) : HttpsURLConnection(url) {
        var disconnected = false
        override fun getResponseCode() = code
        override fun getInputStream() = body
        override fun getContentLengthLong() = -1L
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    @Test fun artworkOnlyAllowsHubHttpsWithoutCredentialsOrUnexpectedPorts() {
        assertTrue(Network.allowed("https://thefirmwarehub.com/fw/cards/a.webp", Network.ARTWORK_HOSTS))
        for (address in listOf("https://untrusted.example/a", "http://thefirmwarehub.com/a", "https://thefirmwarehub.com.evil/a",
            "https://user@thefirmwarehub.com/a", "https://thefirmwarehub.com:444/a", "https://127.0.0.1/a"))
            assertFalse(address, Network.allowed(address, Network.ARTWORK_HOSTS))
    }
    @Test fun redirectOriginIsCheckedBeforeOpeningIt() {
        val opened = mutableListOf<Connection>()
        assertThrows(IllegalStateException::class.java) {
            Network.fetch("https://thefirmwarehub.com/a", 10, Network.ARTWORK_HOSTS, 60_000, { url ->
                Connection(url, 302, location = "https://untrusted.example/track").also { opened += it }
            })
        }
        assertEquals(1, opened.size); assertTrue(opened.single().disconnected)
    }
    @Test fun trickleCannotKeepRequestAliveBeyondTotalBudget() {
        var time = 0L
        var connection: Connection? = null
        val input = object : InputStream() {
            override fun read(): Int { time += 10; return 1 }
            override fun read(bytes: ByteArray, offset: Int, count: Int): Int { bytes[offset] = 1; time += 10; return 1 }
        }
        assertThrows(IllegalStateException::class.java) {
            Network.fetch("https://thefirmwarehub.com/a", 100, Network.ARTWORK_HOSTS, 30, { url ->
                Connection(url, body = input).also { connection = it }
            }, { time })
        }
        assertTrue(connection!!.disconnected)
        assertEquals(30, time)
    }
    @Test fun unknownLengthStillEnforcesByteCap() {
        assertThrows(IllegalStateException::class.java) {
            Network.fetch("https://thefirmwarehub.com/a", 10, Network.ARTWORK_HOSTS, 60_000, { url ->
                Connection(url, body = ByteArrayInputStream(ByteArray(11)))
            })
        }
    }
    @Test fun limitsRejectDeepStructuresLargeNumbersAndLenientComments() {
        for (text in listOf("[".repeat(33), "1".repeat(257), "/*\"*/" + "[".repeat(40), "'\"'" + "[".repeat(40)))
            assertThrows(IllegalArgumentException::class.java) { JsonLimits.check(text) }
        JsonLimits.check("{\"text\":\"[\\\"hello\\\"]\",\"number\":123}")
    }
}
