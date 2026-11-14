package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test

class ChipDetectorTest {
    @Test fun onlyKnownFm1IdsGrantCompatibility() {
        assertEquals(FlashChip.OLD, ChipDetector.classify(0x856014))
        assertEquals(FlashChip.NEW, ChipDetector.classify(0xB36014))
        for (id in listOf(0L, 0xFFFFFFL, 0xEB6014L)) assertEquals(FlashChip.UNKNOWN, ChipDetector.classify(id))
    }
    @Test fun commandUsesBigEndianOpcodeAndFfPadding() {
        assertArrayEquals(Protocol.hex("fc 0a ff ff ff ff ff ff ff ff ff ff ff ff ff ff"), ChipDetector.cdb(0xFC0A, byteArrayOf()))
    }
    @Test fun crcMatchesCcittReferenceAndCipherIsSymmetric() {
        assertEquals(0x31C3, ChipDetector.crc16("123456789".toByteArray()))
        val block = ByteArray(512) { it.toByte() }
        assertArrayEquals(block, ChipDetector.scramble(ChipDetector.scramble(block)))
        assertFalse(block.contentEquals(ChipDetector.scramble(block)))
    }
}
