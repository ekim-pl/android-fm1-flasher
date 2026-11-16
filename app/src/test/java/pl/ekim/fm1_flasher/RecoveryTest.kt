package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

internal open class RecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    private val silent: (AppText, Int) -> Unit = { _, _ -> }
    protected class Flash(val bytes: ByteArray = ByteArray(Recovery.FLASH_SIZE) { (it * 17).toByte() }) : RecoveryFlash {
        override var flashId = 0x856014L
        override var chipKey = 0x980F
        val erased = mutableListOf<Int>()
        var failWrite = false
        var corruptWrite = false
        var shortRead = false
        var reads = 0
        var changeOnSecondRead = false
        override fun read(address: Int, length: Int): ByteArray {
            reads++
            if (changeOnSecondRead && reads == 257) bytes[address] = (bytes[address].toInt() xor 1).toByte()
            return bytes.copyOfRange(address, address + length - if (shortRead) 1 else 0)
        }
        override fun eraseSector(address: Int) {
            erased += address
            bytes.fill(0xFF.toByte(), address, address + Recovery.SECTOR_SIZE)
        }
        override fun programSector(address: Int, data: ByteArray) {
            if (failWrite) error("cable lost")
            data.copyInto(bytes, address)
            if (corruptWrite) bytes[address] = (bytes[address].toInt() xor 1).toByte()
        }
    }
    private fun store() = RecoveryStore(temp.newFolder())
    private val syntheticFlash = ByteArray(Recovery.END) { (it * 31 + 7).toByte() }
    protected open fun v15(): FirmwarePackage = FirmwarePackage.inspect(testPackage("FM-1_015", syntheticFlash))
    protected open fun operations(image: FirmwarePackage) = RecoveryOperations(
        image.sha256, FirmwarePackage.sha256(image.flashImage.copyOfRange(Recovery.START, Recovery.END)),
        image.identity, setOf(FirmwarePackage.sha256(image.flashImage.copyOfRange(0, Recovery.START))))

    @Test fun refusesEveryProtectedAndUnalignedAddress() {
        for (address in listOf(Int.MIN_VALUE, -4096, 0, 0x3000, 0x4001, 0x93000, 0xFF000, Int.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java) { Recovery.checkSector(address) }
        Recovery.checkSector(0x4000); Recovery.checkSector(0x92000)
    }
    @Test fun backupRequiresMatchingFullReadsAndSavesNoWrites() {
        val flash = Flash(); val store = store()
        val backup = Recovery.backup(flash, store, silent)
        assertEquals(512, flash.reads)
        assertArrayEquals(flash.bytes, store.load(backup))
        assertTrue(flash.erased.isEmpty())
        assertEquals(backup, store.list().single())
    }
    @Test fun unstableReadDoesNotPublishBackup() {
        val flash = Flash().apply { changeOnSecondRead = true }; val store = store()
        assertThrows(IllegalStateException::class.java) { Recovery.backup(flash, store, silent) }
        assertTrue(store.list().isEmpty()); assertTrue(flash.erased.isEmpty())
    }
    @Test fun shortReadIsRejected() {
        val flash = Flash().apply { shortRead = true }
        assertThrows(IllegalStateException::class.java) { Recovery.backup(flash, store(), silent) }
        assertTrue(flash.erased.isEmpty())
    }
    @Test fun unknownChipOrKeyStopsBeforeReading() {
        for (flash in listOf(Flash().apply { flashId = 0xFFFFFF }, Flash().apply { chipKey = 0 })) {
            assertThrows(IllegalStateException::class.java) { Recovery.backup(flash, store(), silent) }
            assertEquals(0, flash.reads)
        }
        Recovery.checkDevice(Flash().apply { flashId = 0xB36014 })
    }
    @Test fun writesOnlyDifferingSectorsAndPreservesAllOtherBytes() {
        val flash = Flash(); val before = flash.bytes.copyOf()
        val region = before.copyOfRange(Recovery.START, Recovery.END)
        region[9] = (region[9].toInt() xor 1).toByte()
        region[region.lastIndex] = (region.last().toInt() xor 1).toByte()
        val changes = Recovery.changedSectors(before, region)
        Recovery.writeAndVerify(flash, before, region, changes, silent)
        assertEquals(listOf(Recovery.START, Recovery.END - Recovery.SECTOR_SIZE), flash.erased)
        assertArrayEquals(before.copyOfRange(0, Recovery.START), flash.bytes.copyOfRange(0, Recovery.START))
        assertArrayEquals(before.copyOfRange(Recovery.END, before.size), flash.bytes.copyOfRange(Recovery.END, before.size))
    }
    @Test fun noChangesStillVerifiesWholeFlashWithoutErasing() {
        val flash = Flash(); val before = flash.bytes.copyOf()
        val region = before.copyOfRange(Recovery.START, Recovery.END)
        Recovery.writeAndVerify(flash, before, region, emptyList(), silent)
        assertEquals(256, flash.reads); assertTrue(flash.erased.isEmpty())
    }
    @Test fun interruptedOrCorruptWriteStopsBeforeNextSector() {
        for (corrupt in listOf(false, true)) {
            val flash = Flash().apply { failWrite = !corrupt; corruptWrite = corrupt }
            val before = flash.bytes.copyOf(); val region = ByteArray(Recovery.END - Recovery.START)
            assertThrows(IllegalStateException::class.java) {
                Recovery.writeAndVerify(flash, before, region, listOf(Recovery.START, Recovery.START + Recovery.SECTOR_SIZE), silent)
            }
            assertEquals(listOf(Recovery.START), flash.erased)
        }
    }
    @Test fun badSectorListIsRejectedBeforeAnyErase() {
        val flash = Flash()
        assertThrows(IllegalArgumentException::class.java) {
            Recovery.writeAndVerify(flash, flash.bytes.copyOf(), ByteArray(Recovery.END - Recovery.START), listOf(Recovery.START, 0), silent)
        }
        assertTrue(flash.erased.isEmpty())
    }
    @Test fun backupCorruptionBlocksLoadAndExport() {
        val dir = temp.newFolder(); val store = RecoveryStore(dir)
        val backup = store.save(ByteArray(Recovery.FLASH_SIZE), 0x856014, 0x980F)
        File(dir, "${backup.id}/full-flash-before.bin").writeBytes(ByteArray(Recovery.FLASH_SIZE) { 1 })
        assertThrows(IllegalStateException::class.java) { store.load(backup) }
        assertThrows(IllegalStateException::class.java) { store.export(backup, ByteArrayOutputStream()) }
    }
    @Test fun exportIncludesVerifiedFullBackupAndMetadata() {
        val store = store(); val bytes = ByteArray(Recovery.FLASH_SIZE) { it.toByte() }
        val backup = store.save(bytes, 0xB36014, 0x980F)
        store.result(backup, "verified", Recovery.V15_PACKAGE)
        val output = ByteArrayOutputStream(); store.export(backup, output)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
        }
        assertArrayEquals(bytes, entries["full-flash-before.bin"])
        assertTrue(entries.keys.containsAll(listOf("backup.json", "result.json")))
    }
    @Test fun packageParserExposesExactFlashImageRatherThanTheUfwContainer() {
        val raw = testPackage(); val parsed = FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), "FM-1_900")
        assertArrayEquals(ByteArray(0x4000) { (it * 31).toByte() }, parsed.flashImage)
        assertThrows(IllegalArgumentException::class.java) { Recovery.region(parsed) }
    }
    @Test fun recoveryRevalidatesBackupAndPreservesDeviceDataOnBothChips() {
        val image = v15()
        val operations = operations(image)
        for (id in listOf(0x856014L, 0xB36014L)) {
            val flash = Flash().apply { flashId = id }
            image.flashImage.copyInto(flash.bytes, 0, 0, Recovery.END)
            flash.bytes[Recovery.START + 18] = (flash.bytes[Recovery.START + 18].toInt() xor 1).toByte()
            val before = flash.bytes.copyOf(); val store = store()
            val backup = Recovery.backup(flash, store, silent)
            val plan = operations(image).plan(backup, store, image, "/dev/usb/1", 1, "mvave-stock")
            assertEquals(1, plan.changedSectors)
            operations(image).restore(flash, store, plan, image, silent)
            assertEquals(listOf(Recovery.START), flash.erased)
            assertArrayEquals(operations.region(image), flash.bytes.copyOfRange(Recovery.START, Recovery.END))
            assertArrayEquals(before.copyOfRange(Recovery.END, before.size), flash.bytes.copyOfRange(Recovery.END, before.size))
        }
    }
    @Test fun productionRecoveryRejectsSyntheticFirmware() {
        val raw = testPackage("FM-1_015", syntheticFlash)
        assertThrows(IllegalArgumentException::class.java) { Recovery.region(FirmwarePackage.inspect(raw)) }
    }
    @Test fun wrongPackageOrChangedPlanStopsBeforeErasing() {
        val image = v15(); val operations = operations(image)
        val flash = Flash(); image.flashImage.copyInto(flash.bytes, 0, 0, Recovery.END)
        flash.bytes[Recovery.START] = (flash.bytes[Recovery.START].toInt() xor 1).toByte()
        val store = store(); val backup = Recovery.backup(flash, store, silent)
        val plan = operations.plan(backup, store, image, "usb", 1, "stock")
        assertThrows(IllegalStateException::class.java) {
            operations.restore(flash, store, plan.copy(packageHash = "0".repeat(64)), image, silent)
        }
        assertThrows(IllegalStateException::class.java) {
            operations.restore(flash, store, plan.copy(changedSectors = plan.changedSectors + 1), image, silent)
        }
        flash.flashId = 0xB36014L
        assertThrows(IllegalStateException::class.java) { operations.restore(flash, store, plan, image, silent) }
        assertTrue(flash.erased.isEmpty())
    }
    @Test fun modifiedFirmwareRegionIsRefused() {
        val image = v15(); val operations = operations(image)
        image.flashImage[Recovery.START] = (image.flashImage[Recovery.START].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { operations.region(image) }
    }
    @Test fun changedDeviceAndUnknownBootBlockRecovery() {
        val image = v15(); val flash = Flash(); val store = store()
        val unknown = Recovery.backup(flash, store, silent)
        assertThrows(IllegalStateException::class.java) { operations(image).plan(unknown, store, image, "usb", 1, "stock") }
        image.flashImage.copyInto(flash.bytes, 0, 0, Recovery.END)
        val backup = Recovery.backup(flash, store, silent)
        val plan = operations(image).plan(backup, store, image, "usb", 1, "stock")
        flash.bytes[0xFF123] = (flash.bytes[0xFF123].toInt() xor 1).toByte()
        assertThrows(IllegalStateException::class.java) { operations(image).restore(flash, store, plan, image, silent) }
        assertTrue(flash.erased.isEmpty())
    }
}
