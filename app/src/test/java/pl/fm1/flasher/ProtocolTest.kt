package pl.fm1.flasher

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun knownPartialResponseMatchesCapturedWireFormat() {
        val actual = Protocol.response(Protocol.ReadRequest(0, 0x1234, 4), Protocol.hex("00 7f 80 ff"))
        assertArrayEquals(Protocol.hex("f0 00 32 41 61 00 00 00 00 34 24 00 00 40 00 00 00 00 7e 01 7c 7f 16 f7"), actual)
    }

    @Test fun allPartialLengthsKeepFullAddressAndChecksum() {
        for (length in 1..512) {
            val data = ByteArray(length) { (it * 37 + length).toByte() }
            val packet = Protocol.response(Protocol.ReadRequest(255, 0xFFFFFFFF, length), data)
            assertTrue(packet.copyOfRange(1, packet.lastIndex).all { it.toInt() and 255 < 128 })
            val decoded = Protocol.unpack7(packet.copyOfRange(1, packet.lastIndex))!!
            assertEquals(length + 15, decoded.size)
            assertEquals((length + 8).toLong(), Protocol.uint(decoded, 3, 3))
            assertEquals(0xFFFFFFFF, Protocol.uint(decoded, 7, 4))
            assertEquals(length.toLong(), Protocol.uint(decoded, 11, 3))
            assertArrayEquals(data, decoded.copyOfRange(14, decoded.lastIndex))
            assertEquals(255, decoded.copyOfRange(6, decoded.size).sumOf { it.toInt() and 255 } and 255)
        }
    }

    @Test fun capturedLoaderIdentityAndDamagedIdentity() {
        val captured = Protocol.hex("f0 00 32 45 58 01 00 40 37 74 42 35 31 54 29 4b 18 5f 60 44 29 03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 2a f7")
        assertEquals(FirmwareIdentity("ota-FM-1", "015"), Protocol.identity(captured))
        for (position in listOf(8, 20, captured.lastIndex - 1)) {
            val damaged = captured.clone()
            damaged[position] = (damaged[position].toInt() xor 1).toByte()
            assertNull(Protocol.identity(damaged))
        }
    }

    @Test fun supportsPlainAndOlderEncodedIdentity() {
        assertEquals(FirmwareIdentity("FM-1", "015"), Protocol.identity(identity("FM-1_015")))
        val plain = "FM-1_009".toByteArray(Charsets.US_ASCII)
        val encoded = plain.map { (it - 48).toByte() }.toByteArray()
        val payload = plain + encoded + ByteArray(11) { 0xD0.toByte() }
        assertEquals(FirmwareIdentity("FM-1", "009"), Protocol.identity(Protocol.frame(0x11, payload)))
        assertNull(Protocol.identity(identity("OTHER_DEVICE")))
    }

    @Test fun onlyExactLegacyV16ChecksumIsPermitted() {
        val decoded = byteArrayOf(0, 0x59, 0x11, 27, 0, 0) + "FM-1_016".toByteArray().copyOf(27) + 0x19.toByte()
        val legacy = byteArrayOf(0xF0.toByte()) + Protocol.pack7(decoded) + 0xF7.toByte()
        assertEquals(FirmwareIdentity("FM-1", "016"), Protocol.identity(legacy))
        decoded[13] = '7'.code.toByte()
        assertNull(Protocol.identity(byteArrayOf(0xF0.toByte()) + Protocol.pack7(decoded) + 0xF7.toByte()))
    }

    @Test fun requestChecksumAndHighAddressAreValidated() {
        val packet = readRequest(Protocol.FINISHED, 8)
        assertEquals(Protocol.ReadRequest(0, Protocol.FINISHED, 8), Protocol.request(packet))
        val damaged = packet.clone().apply { this[10] = (this[10].toInt() xor 1).toByte() }
        assertNull(Protocol.request(damaged))
        assertNull(Protocol.unpack7(byteArrayOf(0x80.toByte())))
    }

    @Test fun fragmentedCoalescedSysexAndRealtimeAreHandled() {
        val received = mutableListOf<ByteArray>()
        val assembler = SysExAssembler { received += it }
        val first = readRequest(0x123, 17)
        val second = identity("FM-1_015")
        val wire = first.take(5).toByteArray() + 0xF8.toByte() + first.drop(5).toByteArray() + second
        for (byte in wire) assembler.accept(byteArrayOf(byte), 0, 1)
        assertEquals(2, received.size)
        assertArrayEquals(first, received[0]); assertArrayEquals(second, received[1])
    }

    @Test fun abortedAndOversizedFramesCannotLeakIntoNextMessage() {
        val received = mutableListOf<ByteArray>()
        val assembler = SysExAssembler { received += it }
        assembler.accept(Protocol.hex("f0 01 02 90 03 f7"), 0, 6)
        assertTrue(received.isEmpty())
        assertThrows(IllegalStateException::class.java) { assembler.accept(byteArrayOf(0xF0.toByte()) + ByteArray(1030), 0, 1031) }
        val valid = identity("FM-1_015")
        assembler.accept(valid, 0, valid.size)
        assertEquals(1, received.size)
    }
}

internal fun identity(name: String) = Protocol.frame(0x11, name.toByteArray(Charsets.US_ASCII).copyOf(27))
internal fun readRequest(address: Long, length: Int, type: Int = 0) = Protocol.frame(0x30,
    byteArrayOf(type.toByte()) + Protocol.little(address, 4) + Protocol.little(length.toLong(), 3))
