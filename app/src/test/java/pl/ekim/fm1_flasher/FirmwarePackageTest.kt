package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test

class FirmwarePackageTest {
    @Test fun standardCrcVector() = assertEquals(0x31C3, FirmwarePackage.crc16("123456789".toByteArray()))

    @Test fun parsesValidPackageAndStripsOnlyTwentyMarkers() {
        val raw = testPackage()
        val parsed = FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), "FM-1_900")
        assertEquals(FirmwareIdentity("FM-1", "900"), parsed.identity)
        assertEquals(raw.size - 20, parsed.logical.size)
        assertArrayEquals(raw.copyOfRange(960, raw.size), parsed.logical.copyOfRange(940, parsed.logical.size))
    }

    @Test fun rejectsHashMismatchAndWrongProduct() {
        val raw = testPackage()
        assertThrows(IllegalArgumentException::class.java) { FirmwarePackage.parse(raw, "0".repeat(64), "FM-1_900") }
        assertThrows(IllegalArgumentException::class.java) { FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), "FM-1_901") }
    }

    @Test fun rejectsPayloadCorruptionEvenWhenExpectedHashWasUpdated() {
        val raw = testPackage().apply { this[0x500] = (this[0x500].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), "FM-1_900") }
    }

    @Test fun validatesDifferentSyntheticPackages() {
        for (product in listOf("FM-1_015", "FM-1_016")) {
            val raw = testPackage(product, ByteArray(0x94000) { (it * 13).toByte() })
            val parsed = FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), product)
            assertEquals(product.substringAfter('_'), parsed.identity.version)
            assertEquals(raw.size - 20, parsed.logical.size)
            assertEquals(0x94000, parsed.flashImage.size)
        }
    }
}
