package pl.ekim.fm1_flasher

import kotlin.test.*

class MbootClientTest {
    private fun info() = "MBOOT".encodeToByteArray() + byteArrayOf(1, 1, 0) + Protocol.little(0x980F, 2) +
        Protocol.little(0x100000, 4) + Protocol.little(0xB36014, 4) + ByteArray(8) { (it + 1).toByte() } + byteArrayOf(0, 0) + "2.4-test\u0000".encodeToByteArray()
    private class Link : MidiConnection {
        override val endpoint = Endpoint(1, "test")
        val sent = mutableListOf<ByteArray>(); val replies = ArrayDeque<ByteArray>()
        override fun send(bytes: ByteArray) { sent += bytes }
        override fun receive(timeoutMs: Long) = replies.removeFirstOrNull()
        override fun close() = Unit
    }
    @Test fun handshakeIsMandatoryAndReadMustEchoExactRange() {
        val link = Link(); val client = MbootClient(link)
        assertFails { client.read(0, 32) }; assertTrue(link.sent.isEmpty())
        link.replies += Protocol.frame(0x12, info())
        assertEquals(0x980F, client.identify().chipKey)
        assertEquals("0102030405060708", MbootClient.parseInfo(info()).uid)
        val header = byteArrayOf(0) + Protocol.little(0, 4) + Protocol.little(32, 3)
        link.replies += Protocol.frame(0x23, header + ByteArray(32) { 42 })
        assertContentEquals(ByteArray(32) { 42 }, client.read(0, 32))
        link.replies += Protocol.frame(0x23, header + ByteArray(32))
        assertFails { client.read(32, 32) }
        assertFails { client.read(0xFFFFF, 32) }
        assertFails { client.read(0, 513) }
        assertEquals(3, link.sent.size)
        link.replies += Protocol.frame(0x24, header + Protocol.little(0x12345678, 4))
        assertEquals(0x12345678L, client.crc32(0, 32))
    }
    @Test fun refusesStockUnsupportedVersionAndTruncatedFrames() {
        for (body in listOf(byteArrayOf(), info().copyOf(20), info().also { it[5] = 2 }, info().also { it[26] = 1 })) {
            assertFails { MbootClient.parseInfo(body) }
        }
        val link = Link(); link.replies += Protocol.frame(0, byteArrayOf(1))
        assertFails { MbootClient(link).identify() }
        assertEquals(1, link.sent.size)
        assertContentEquals(Protocol.frame(0x12, byteArrayOf()), link.sent.single())
    }
}
