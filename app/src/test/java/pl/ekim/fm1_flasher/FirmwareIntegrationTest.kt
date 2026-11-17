package pl.ekim.fm1_flasher

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Explicit Gradle task only; missing or changed official packages fail instead of skipping. */
internal class FirmwareIntegrationTest : RecoveryTest() {
    private fun packageFor(version: String): FirmwarePackage {
        val directory = File(checkNotNull(System.getProperty("fm1.fixtureDir")) { "Run :app:firmwareIntegrationTest" })
        val manifest = JSONObject(File(checkNotNull(System.getProperty("fm1.fixtureManifest"))).readText())
        val item = manifest.getJSONObject(version)
        val raw = File(directory, "$version.fwsc").readBytes()
        return FirmwarePackage.parse(raw, item.getString("sha256"), item.getString("product"))
    }

    override fun v15() = packageFor("v15")
    override fun operations(image: FirmwarePackage) = Recovery.operations

    @Test fun officialPackagesHavePinnedIdentitiesAndV16CannotBeRecovered() {
        val v15 = packageFor("v15")
        val v16 = packageFor("v16")
        assertEquals(FirmwareIdentity("FM-1", "015"), v15.identity)
        assertEquals(FirmwareIdentity("FM-1", "016"), v16.identity)
        assertEquals(Recovery.V15_PACKAGE, v15.sha256)
        assertEquals(Recovery.V15_REGION, FirmwarePackage.sha256(Recovery.region(v15)))
        assertTrue(v16.flashImage.size >= Recovery.END)
        assertThrows(IllegalArgumentException::class.java) { Recovery.region(v16) }
        for (image in listOf(v15, v16)) {
            val raw = File(System.getProperty("fm1.fixtureDir"), if (image === v15) "v15.fwsc" else "v16.fwsc").readBytes()
            raw[0x500] = (raw[0x500].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { FirmwarePackage.parse(raw, image.sha256, image.identity.toString()) }
        }
    }
    @Test fun deviceToolsParseOfficialLayoutsAndBuildBothBootTargets() {
        val boot = File("src/main/assets/fm1-uboot-2.4.boot").readBytes()
        for (version in listOf("v15", "v16")) {
            val pkg = packageFor(version)
            val full = ByteArray(FlashLayout.SIZE) { -1 }.also { pkg.flashImage.copyInto(it) }
            val analysis = FlashLayout.analyze(full, 0x980F)
            assertTrue("$version layout", analysis.layoutValid)
            assertTrue("$version FM1", analysis.fm1Evidence)
            assertTrue("$version app", analysis.appValid)
            assertTrue("$version boot", analysis.bootValid)
            val info = DeviceDiagnostics(0x856014, 0x980F, full.size)
            val plan = FlashWritePlan.create(FlashOperation.MBOOT, full, info, boot, FirmwarePackage.sha256(boot))
            FlashLayout.checkBoot(plan.targetImage())
            assertEquals("2.4-395e6cc", FlashLayout.bootIdentity(FlashLayout.storedBoot(plan.targetImage()))!!.second)
            val raw = File(System.getProperty("fm1.fixtureDir"), "v15.fwsc").readBytes()
            val stock = FlashWritePlan.create(FlashOperation.STOCK_BOOT, plan.targetImage(), info, raw, FirmwarePackage.sha256(raw))
            FlashLayout.checkBoot(stock.targetImage())
            assertArrayEquals(packageFor("v15").flashImage.copyOfRange(0, 0x4000), stock.targetImage().copyOfRange(0, 0x4000))
        }
    }
    @Test fun allDeviceToolsOperationsWriteAndVerifyOnBothSupportedChips() {
        val boot = File("src/main/assets/fm1-uboot-2.4.boot").readBytes()
        val stock = File(System.getProperty("fm1.fixtureDir"), "v15.fwsc").readBytes()
        val firmware = File(System.getProperty("fm1.fixtureDir"), "v16.fwsc").readBytes()
        for (chip in listOf(0x856014L, 0xB36014L)) {
            val info = DeviceDiagnostics(chip, 0x980F, FlashLayout.SIZE, "0102030405060708", "2.3")
            val original = ByteArray(FlashLayout.SIZE) { -1 }.also {
                packageFor("v15").flashImage.copyInto(it)
                it.fill(0x5A, FlashLayout.FACTORY, it.size)
                it[0x95000] = 7
            }
            var current = original.copyOf()
            fun apply(operation: FlashOperation, input: ByteArray, saved: DeviceDiagnostics? = null): ByteArray {
                val before = current.copyOf()
                val plan = FlashWritePlan.create(operation, before, info, input, FirmwarePackage.sha256(input), saved)
                assertTrue("$operation must exercise writes", plan.changedSectors.isNotEmpty())
                val erased = mutableListOf<Int>()
                val flash = object : RecoveryFlash {
                    override val flashId = chip
                    override val chipKey = info.chipKey
                    override fun read(address: Int, length: Int) = current.copyOfRange(address, address + length)
                    override fun eraseSector(address: Int) {
                        erased += address
                        current.fill(-1, address, address + FlashLayout.SECTOR)
                    }
                    override fun programSector(address: Int, data: ByteArray) { data.copyInto(current, address) }
                }
                DeviceToolsWriter.apply(flash, info, before, plan)
                assertEquals(plan.changedSectors, erased)
                assertArrayEquals(plan.targetImage(), current)
                assertArrayEquals(original.copyOfRange(FlashLayout.FACTORY, original.size), current.copyOfRange(FlashLayout.FACTORY, current.size))
                return current.copyOf()
            }
            val mboot = apply(FlashOperation.MBOOT, boot)
            assertArrayEquals(original.copyOfRange(FlashLayout.BOOT_END, original.size), mboot.copyOfRange(FlashLayout.BOOT_END, mboot.size))
            assertEquals("2.4-395e6cc", FlashLayout.bootIdentity(FlashLayout.storedBoot(mboot))!!.second)
            val installed = apply(FlashOperation.FIRMWARE, firmware)
            val app = packageFor("v16").flashImage
            assertArrayEquals(mboot.copyOfRange(0, FlashLayout.BOOT_END), installed.copyOfRange(0, FlashLayout.BOOT_END))
            assertArrayEquals(app.copyOfRange(FlashLayout.BOOT_END, app.size), installed.copyOfRange(FlashLayout.BOOT_END, app.size))
            assertArrayEquals(mboot.copyOfRange(app.size, mboot.size), installed.copyOfRange(app.size, installed.size))
            val restored = apply(FlashOperation.RESTORE, original, info)
            assertArrayEquals(mboot.copyOfRange(0, FlashLayout.BOOT_END), restored.copyOfRange(0, FlashLayout.BOOT_END))
            assertArrayEquals(original.copyOfRange(FlashLayout.BOOT_END, original.size), restored.copyOfRange(FlashLayout.BOOT_END, restored.size))
            assertArrayEquals(original, apply(FlashOperation.STOCK_BOOT, stock))
            apply(FlashOperation.MBOOT, boot)
            apply(FlashOperation.FIRMWARE, firmware)
            assertArrayEquals(original, apply(FlashOperation.RESTORE_BOOT, original, info))
        }
    }

}
