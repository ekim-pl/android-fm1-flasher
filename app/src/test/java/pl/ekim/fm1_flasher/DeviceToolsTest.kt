package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

internal class DeviceToolsTest {
    private val info = DeviceDiagnostics(0x856014, 0x980F, FlashLayout.SIZE, "0102030405060708", "2.3")
    private fun put(bytes: ByteArray, at: Int, value: Long, size: Int) = Protocol.little(value, size).copyInto(bytes, at)
    private fun record(name: String, address: Int, length: Int, attr: Int = 0, crc: Int = 0) = ByteArray(32).also {
        put(it, 2, crc.toLong(), 2); put(it, 4, address.toLong(), 4); put(it, 8, length.toLong(), 4)
        it[12] = attr.toByte(); name.toByteArray().copyInto(it, 16); put(it, 0, FirmwarePackage.crc16(it, 2).toLong(), 2)
    }
    private fun boot(open: Boolean = false): ByteArray {
        val payload = ByteArray(128) { 3 }
        if (open) {
            "AC79BLID".toByteArray().copyInto(payload, 64)
            ByteArray(8).also { "fm1".toByteArray().copyInto(it) }.copyInto(payload, 72)
            ByteArray(32).also { "2.4-test".toByteArray().copyInto(it) }.copyInto(payload, 80)
        }
        return ByteArray(16).also {
            put(it, 0, 1, 2); put(it, 2, payload.size.toLong(), 2); put(it, 8, 16, 4)
            put(it, 12, FirmwarePackage.crc16(payload).toLong(), 2); put(it, 14, FirmwarePackage.crc16(it, 0, 14).toLong(), 2)
        } + payload
    }
    private fun image(): ByteArray {
        val image = ByteArray(FlashLayout.SIZE) { -1 }
        val header = ByteArray(32) { 7 }.also { put(it, 0, FirmwarePackage.crc16(it, 2).toLong(), 2) }
        FlashLayout.cipher(header, 65535).copyInto(image)
        val boot = boot()
        val config = ByteArray(128) { 2 }.also { put(it, 32, FirmwarePackage.crc16(it, 0, 32).toLong(), 2) }
        val records = listOf(record("uboot.boot", 0xA0, boot.size, crc = FirmwarePackage.crc16(boot)),
            record("isd_config.ini", 0x3900, config.size, crc = FirmwarePackage.crc16(config)), record("app_dir_head", 0x4000, 0), record("key_mac", 0xFF000, 4096))
        records.forEachIndexed { i, r -> FlashLayout.cipher(r, 65535).copyInto(image, 32 + 32 * i) }
        FlashLayout.cipher(boot.copyOfRange(0, 16), 65535).copyInto(image, 0xA0)
        FlashLayout.cipher(boot.copyOfRange(16, boot.size), 65535).copyInto(image, 0xB0)
        config.copyInto(image, 0x3900)
        val app = ByteArray(32) { 11 }
        val entries = listOf(record("app_area_head", 0x02000200, 544, 0x83), record("app.bin", 512, 32, 0x82, FirmwarePackage.crc16(app)), record("VM", 0x93000, 0x56000, 0x12),
            record("BTIF", 0xE9000, 0x1000, 0x12), record("USR", 0xEA000, 0x12000, 0x12))
        entries.forEachIndexed { i, r -> FlashLayout.cipher(r, info.chipKey xor (i * 8)).copyInto(image, 0x4000 + 32 * i) }
        FlashLayout.cipher(app, info.chipKey xor 128).copyInto(image, 0x4200)
        return image
    }
    private fun plan(before: ByteArray = image()) = boot(true).let { FlashWritePlan.create(FlashOperation.MBOOT, before, info, it, FirmwarePackage.sha256(it)) }
    private class Flash(val bytes: ByteArray, val fail: String? = null) : RecoveryFlash {
        override val flashId = 0x856014L; override val chipKey = 0x980F
        val erased = mutableListOf<Int>(); var programmed = false
        override fun read(address: Int, length: Int) = bytes.copyOfRange(address, address + length).also {
            if (programmed && fail == "verify") it[0] = (it[0].toInt() xor 1).toByte()
        }
        override fun eraseSector(address: Int) { erased += address; if (fail == "erase") error("erase failed"); bytes.fill(-1, address, address + 4096) }
        override fun programSector(address: Int, data: ByteArray) { if (fail == "program") error("write failed"); data.copyInto(bytes, address); programmed = true }
    }
    @Test fun identifiesRealLayoutAndProtectsUnknownGaps() {
        val report = FlashLayout.analyze(image(), info.chipKey)
        assertTrue(report.appValid && report.bootValid && report.fm1Evidence && report.layoutValid)
        assertEquals("tools_region_factory", report.regions.last().name)
        assertTrue(report.regions.any { it.name == "tools_region_unknown" })
        val bad = image().also { it[0x4200] = 0 }
        assertFalse(FlashLayout.analyze(bad, info.chipKey).appValid)
        assertFalse(FlashLayout.analyze(image(), 0).layoutValid)
    }
    @Test fun malformedOrOverlappingTablesNeverProduceTrustedLayout() {
        for (r in listOf(record("VM", 0x4100, 0x1000, 0x12), record("VM", 0xFF000, 0x1000, 0x12))) {
            val bad = image().also { FlashLayout.cipher(r, info.chipKey xor 16).copyInto(it, 0x4040) }
            assertFalse(FlashLayout.analyze(bad, info.chipKey).layoutValid)
        }
        val overflow = record("VM", 0, 1, 0x12).also { put(it, 4, 0xFFFFFFFF, 4); put(it, 0, FirmwarePackage.crc16(it, 2).toLong(), 2) }
        val bad = image().also { FlashLayout.cipher(overflow, info.chipKey xor 16).copyInto(it, 0x4040) }
        assertFalse(FlashLayout.analyze(bad, info.chipKey).layoutValid)
    }
    @Test fun bootPreviewChangesOnlyBootAndKeepsKeyRecord() {
        val before = image(); val plan = plan(before); val target = plan.targetImage()
        FlashLayout.checkBoot(target)
        assertEquals("2.4-test", FlashLayout.bootIdentity(FlashLayout.storedBoot(target))!!.second)
        assertArrayEquals(before.copyOfRange(0x3900, 0x3922), target.copyOfRange(0x3FA0, 0x3FC2))
        assertArrayEquals(before.copyOfRange(0x4000, before.size), target.copyOfRange(0x4000, target.size))
        for (address in listOf(-4096, 1, 0x4000, 0xFF000)) assertThrows(IllegalArgumentException::class.java) { plan.checkSector(address) }
        target[0] = 0
        assertEquals(plan.targetHash, FirmwarePackage.sha256(plan.targetImage()))
        val repeat = boot(true).let { FlashWritePlan.create(FlashOperation.MBOOT, plan.targetImage(), info, it, FirmwarePackage.sha256(it)) }
        assertTrue(repeat.changedSectors.isEmpty())
    }
    @Test fun staleOrWrongDeviceNeverErasesAndEveryFailureStops() {
        val before = image(); val plan = plan(before)
        val stale = Flash(before.copyOf().also { it[0x95000] = 1 })
        assertThrows(IllegalStateException::class.java) { DeviceToolsWriter.apply(stale, info, before, plan) }
        assertTrue(stale.erased.isEmpty())
        val wrong = Flash(before.copyOf())
        assertThrows(IllegalArgumentException::class.java) { DeviceToolsWriter.apply(wrong, info.copy(uid = null), before, plan) }
        assertTrue(wrong.erased.isEmpty())
        for (failure in listOf("erase", "program", "verify")) {
            val flash = Flash(before.copyOf(), failure)
            assertThrows(IllegalStateException::class.java) { DeviceToolsWriter.apply(flash, info, before, plan) }
            assertEquals(1, flash.erased.size)
        }
        val good = Flash(before.copyOf()); DeviceToolsWriter.apply(good, info, before, plan)
        assertArrayEquals(plan.targetImage(), good.bytes)
    }
    @Test fun restoreRequiresSameDeviceAndPreservesFactory() {
        val before = image(); val saved = before.copyOf().also { it[0x95000] = 7 }
        fun restore(identity: DeviceDiagnostics?) = FlashWritePlan.create(FlashOperation.RESTORE, before, info, saved, FirmwarePackage.sha256(saved), identity)
        assertThrows(IllegalArgumentException::class.java) { restore(null) }
        assertThrows(IllegalArgumentException::class.java) { restore(info.copy(uid = "1111111111111111")) }
        val plan = restore(info)
        assertEquals(listOf(0x95000), plan.changedSectors)
        assertThrows(IllegalArgumentException::class.java) { plan.checkSector(0) }
        saved[0xFF000] = 9
        assertThrows(IllegalArgumentException::class.java) { restore(info) }
    }
    @Test fun userDataArchiveRestoresOnlyUserPartitions() {
        val before = image()
        FlashLayout.cipher(record("SEQ64", 0xFC000, 0x1000, 0x12), info.chipKey xor 40).copyInto(before, 0x40A0)
        FlashLayout.cipher(record("USRFLASH", 0xFD000, 0x1000, 0x92), info.chipKey xor 48).copyInto(before, 0x40C0)
        val saved = before.copyOf().also {
            it[0x95000] = 3 // VM
            it[0xEA000] = 4 // USR
            it[0xE9000] = 5 // BTIF
            it[0xFC000] = 6 // SEQ64
            it[0xFD000] = 7 // USRFLASH
            it[0xFE000] = 8 // unclassified
        }
        val payload = UserDataBackup.capture(saved, info.chipKey)
        assertEquals(0x6A000 + UserDataBackup.HASH_SIZE, payload.size)
        val root = java.nio.file.Files.createTempDirectory("user-data-backup").toFile()
        try {
            val store = RecoveryStore(root)
            val backup = store.save(payload, info.flashId, info.chipKey, info, dataOnly = true)
            val archive = ByteArrayOutputStream().also { store.export(backup, it) }.toByteArray()
            ZipInputStream(archive.inputStream()).use { zip ->
                assertEquals(listOf("user-data.bin", "backup.json"), generateSequence { zip.nextEntry?.name }.toList())
            }
            val imported = BackupImport.read(archive, null, store)
            assertTrue(imported.dataOnly)
            assertEquals(imported, store.list().single { it.id == imported.id })
            assertArrayEquals(payload, store.load(imported))

            fun restore(current: ByteArray = before, identity: DeviceDiagnostics = imported.diagnostics,
                        input: ByteArray = store.load(imported)) =
                FlashWritePlan.create(FlashOperation.RESTORE_DATA, current, info, input, FirmwarePackage.sha256(input), identity)
            val plan = restore()
            assertEquals(listOf(0x95000, 0xEA000, 0xFC000, 0xFD000), plan.changedSectors)
            val flash = Flash(before.copyOf())
            DeviceToolsWriter.apply(flash, info, before, plan)
            assertArrayEquals(saved.copyOf().also { it[0xE9000] = before[0xE9000]; it[0xFE000] = before[0xFE000] }, flash.bytes)
            assertThrows(IllegalArgumentException::class.java) { restore(identity = info.copy(uid = "1111111111111111")) }
            assertThrows(IllegalArgumentException::class.java) { restore(input = payload.copyOf(payload.size - 1)) }
            assertThrows(IllegalArgumentException::class.java) { restore(current = before.copyOf().also { it[0x4F00] = 0 }) }
            assertThrows(IllegalArgumentException::class.java) { restore(current = before.copyOf().also { it[0xFF000] = 0 }) }
        } finally { root.deleteRecursively() }
    }

    @Test fun unsupportedHardwareAndChangedInputCannotPrepareWrites() {
        val before = image(); val input = boot(true)
        for (device in listOf(info.copy(flashId = 0x123456), info.copy(chipKey = 0), info.copy(capacity = FlashLayout.SIZE * 2))) {
            assertThrows(IllegalArgumentException::class.java) {
                FlashWritePlan.create(FlashOperation.MBOOT, before, device, input, FirmwarePackage.sha256(input))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlashWritePlan.create(FlashOperation.MBOOT, before, info, input, "0".repeat(64))
        }
    }
    @Test fun revokedSessionNeverErasesAndNoOpNeverPrograms() {
        val before = image(); val plan = plan(before); val flash = Flash(before.copyOf())
        var validations = 0
        assertThrows(IllegalStateException::class.java) {
            DeviceToolsWriter.apply(flash, info, before, plan, validate = {
                if (++validations == 2) error("USB permission revoked after recheck")
            })
        }
        assertTrue(flash.erased.isEmpty())
        assertFalse(flash.programmed)
        val installed = plan.targetImage()
        val noOp = plan(installed); val unchanged = Flash(installed.copyOf())
        DeviceToolsWriter.apply(unchanged, info, installed, noOp)
        assertTrue(unchanged.erased.isEmpty())
        assertFalse(unchanged.programmed)
        assertArrayEquals(installed, unchanged.bytes)
    }
    @Test fun backupRoundTripAndHostileImports() {
        val root = java.nio.file.Files.createTempDirectory("tools-backup").toFile()
        try {
            val store = RecoveryStore(root)
            val original = store.save(image(), info.flashId, info.chipKey, info)
            val archive = ByteArrayOutputStream().also { store.export(original, it) }.toByteArray()
            val imported = BackupImport.read(archive, null, store)
            assertEquals(original.sha256, imported.sha256); assertEquals(info.uid, imported.diagnostics.uid)
            assertTrue(imported.verified)
            val sidecar = """{"sha256":"${original.sha256}","flash chip":"0x856014 (old)","chip key":"0x980F","unit id":"0102030405060708"}""".toByteArray()
            val desktop = BackupImport.read(image(), sidecar, store)
            assertTrue(desktop.verified); assertEquals(info.uid, desktop.diagnostics.uid)
            assertThrows(IllegalArgumentException::class.java) { BackupImport.read(image(), "{\"chipKey\":4295006223}".toByteArray(), store) }
            val raw = BackupImport.read(image(), null, store)
            assertFalse(raw.verified); assertEquals(-1, raw.chipKey)
            val malicious = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { it.putNextEntry(ZipEntry("../backup.bin")); it.write(1) } }.toByteArray()
            assertThrows(IllegalArgumentException::class.java) { BackupImport.read(malicious, null, store) }
            assertThrows(IllegalArgumentException::class.java) { BackupImport.read(image(), "{\"sha256\":\"bad\"}".toByteArray(), store) }
            val metadata = File(root, "${original.id}/backup.json")
            metadata.writeText(metadata.readText().replace("\"schema\": 2", "\"schema\": 99"))
            assertFalse(store.list().any { it.id == original.id })
            // Existing records without diagnostics remain readable.
            val legacy = store.save(image(), info.flashId, info.chipKey)
            val legacyFile = File(root, "${legacy.id}/backup.json")
            val oldMeta = org.json.JSONObject(legacyFile.readText())
            for (field in listOf("schema", "uid", "loader", "transport", "verified")) oldMeta.remove(field)
            legacyFile.writeText(oldMeta.toString())
            assertEquals(legacy, store.list().single { it.id == legacy.id })
        } finally { root.deleteRecursively() }
    }
    @Test fun chipKeyReplyMatchesPublishedVector() {
        val reply = Protocol.hex("FC 09 00 00 00 00 3C 20 00 00 00 00 00 00 00 00")
        assertEquals(0x980F, Protocol.uint(ChipDetector.scramble(byteArrayOf(reply[7], reply[6])), 0, 2).toInt())
    }
}
