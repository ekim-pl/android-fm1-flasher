package pl.fm1.flasher

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

// Synthetic UFW fixture; contains no manufacturer firmware.
internal fun testPackage(): ByteArray {
    val logical = ByteArray(0x4404)
    fun put(offset: Int, value: Long, length: Int) = Protocol.little(value, length).copyInto(logical, offset)
    put(4, logical.size.toLong(), 4); put(8, 3, 2)
    "AC791N".toByteArray().copyInto(logical, 16)
    val flash = ByteArray(0x4000) { (it * 31).toByte() }
    flash.copyInto(logical, 0x400)
    val loader = byteArrayOf(1, 2, 3, 4)
    loader.copyInto(logical, 0x4400)
    fun entry(index: Int, type: Int, name: String, offset: Int, content: ByteArray) {
        val base = 64 + index * 80
        put(base, type.toLong(), 2); put(base + 2, index.toLong(), 2)
        put(base + 4, FirmwarePackage.crc16(content).toLong(), 2)
        put(base + 8, offset.toLong(), 4); put(base + 12, content.size.toLong(), 4); put(base + 16, content.size.toLong(), 4)
        name.toByteArray().copyInto(logical, base + 64)
        scramble(logical, base, 80)
    }
    entry(0, 0, "flash.bin", 0x400, flash)
    entry(1, 2, "info.log", 0x4400, byteArrayOf())
    entry(2, 100, "ota.bin", 0x4400, loader)
    // Community packages can declare alignment padding without including it after the last file.
    scramble(logical, 224, 80)
    put(224 + 16, 32, 4)
    scramble(logical, 224, 80)
    Protocol.little(FirmwarePackage.crc16(logical, 64, 304).toLong(), 2).copyInto(logical, 2)
    Protocol.little(FirmwarePackage.crc16(logical, 2, 64).toLong(), 2).copyInto(logical, 0)
    scramble(logical, 0, 64)
    val product = "FM-1_900"
    val raw = ByteArray(logical.size + 20)
    repeat(20) { i ->
        logical.copyInto(raw, i * 48, i * 47, i * 47 + 47)
        raw[i * 48 + 47] = if (i < product.length) (product[i].code + i + 1).toByte() else 125
    }
    logical.copyInto(raw, 960, 940)
    return raw
}

private fun scramble(bytes: ByteArray, offset: Int, size: Int) {
    var key = 65535
    repeat(size) { i ->
        bytes[offset + i] = (bytes[offset + i].toInt() xor (key and 255)).toByte()
        key = ((key shl 1) xor if (key and 32768 != 0) 4129 else 0) and 65535
    }
}
