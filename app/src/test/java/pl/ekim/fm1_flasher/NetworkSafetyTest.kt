package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test

class NetworkSafetyTest {
    @Test fun artworkOnlyAllowsHubHttpsWithoutCredentialsOrUnexpectedPorts() {
        assertTrue(Network.allowed("https://thefirmwarehub.com/fw/cards/a.webp", Network.ARTWORK_HOSTS))
        for (address in listOf("https://untrusted.example/a", "http://thefirmwarehub.com/a", "https://thefirmwarehub.com.evil/a",
            "https://user@thefirmwarehub.com/a", "https://thefirmwarehub.com:444/a", "https://127.0.0.1/a"))
            assertFalse(address, Network.allowed(address, Network.ARTWORK_HOSTS))
    }
    @Test fun limitsRejectDeepStructuresLargeNumbersAndLenientComments() {
        for (text in listOf("[".repeat(33), "1".repeat(257), "/*\"*/" + "[".repeat(40), "'\"'" + "[".repeat(40)))
            assertThrows(IllegalArgumentException::class.java) { JsonLimits.check(text) }
        JsonLimits.check("{\"text\":\"[\\\"hello\\\"]\",\"number\":123}")
    }
}
