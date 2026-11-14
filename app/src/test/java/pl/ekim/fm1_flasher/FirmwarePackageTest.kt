package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

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

    @Test fun validatesRealOfficialPackagesWhenProvided() {
        val paths = System.getenv("FM1_TEST_PACKAGES")?.split(File.pathSeparator).orEmpty()
        assumeTrue("Set FM1_TEST_PACKAGES to external .fwsc files", paths.isNotEmpty())
        paths.forEach { path ->
            val raw = File(path).readBytes()
            val product = buildString {
                repeat(20) { i -> val m = raw[i * 48 + 47].toInt() and 255; if (m != 125) append(((m - i - 1) and 255).toChar()) }
            }
            assertEquals(raw.size - 20, FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), product).logical.size)
        }
    }
}
