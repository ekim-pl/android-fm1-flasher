package pl.ekim.fm1_flasher

import kotlin.test.*

class PlatformTest {
    @Test fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", FirmwarePackage.sha256(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FirmwarePackage.sha256("abc".encodeToByteArray()))
    }
    @Test fun monotonicClockAdvances() {
        val before = monotonicMillis()
        platformPause(5)
        assertTrue(monotonicMillis() >= before)
    }
}
