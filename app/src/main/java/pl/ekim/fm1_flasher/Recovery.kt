package pl.ekim.fm1_flasher

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

interface RecoveryFlash {
    val flashId: Long
    val chipKey: Int
    fun read(address: Int, length: Int): ByteArray
    fun eraseSector(address: Int)
    fun programSector(address: Int, data: ByteArray)
}

data class RecoveryBackup(val id: String, val sha256: String, val flashId: Long, val chipKey: Int, val bootHash: String)
data class RecoveryPlan(
    val token: String, val backup: RecoveryBackup, val deviceName: String, val deviceId: Int,
    val packageId: String, val packageHash: String, val changedSectors: Int,
)

/** Deliberately limited to the hash-pinned stock V15 application region. No bootloader writes. */
object Recovery {
    const val FLASH_SIZE = 0x100000
    const val SECTOR_SIZE = 0x1000
    const val START = 0x4000
    const val END = 0x93000
    const val V15_PACKAGE = "db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a"
    const val V15_REGION = "6edf3c37fb5bbbc33607c89375ee024d5477c17914d72221c8c68e58a8255686"
    private val knownBoots = setOf(
        "d9f43191777656de01c7bed4f9e9ba2e34e94832c5d2ed02c3fc7ab7a6d9bd67",
        "8f5228c2d6ea2068838ef4916f946bea877d14b07e6cfca49e41957f4c7b234f",
    )
    fun knownBoot(hash: String) = hash in knownBoots
    fun eligible(entry: CatalogEntry?) = entry?.sha256 == V15_PACKAGE && entry.product == "FM-1_015"
    fun checkSector(address: Int) {
        require(address >= START && address <= END - SECTOR_SIZE && address % SECTOR_SIZE == 0) {
            invalid(msg(R.string.recovery_protected))
        }
    }
    fun checkDevice(flash: RecoveryFlash) {
        check(flash.flashId in setOf(0x856014L, 0xB36014L) && flash.chipKey == 0x980F) {
            failed(msg(R.string.recovery_unknown_device))
        }
    }
    fun region(image: FirmwarePackage): ByteArray {
        require(image.sha256 == V15_PACKAGE && image.identity == FirmwareIdentity("FM-1", "015") && image.flashImage.size >= END) {
            invalid(msg(R.string.recovery_v15_only))
        }
        val bytes = image.flashImage.copyOfRange(START, END)
        require(FirmwarePackage.sha256(bytes) == V15_REGION) { invalid(msg(R.string.recovery_v15_only)) }
        return bytes
    }
    fun readAll(flash: RecoveryFlash, progress: (Int) -> Unit): ByteArray {
        val bytes = ByteArray(FLASH_SIZE)
        for (address in 0 until FLASH_SIZE step SECTOR_SIZE) {
            val block = flash.read(address, SECTOR_SIZE)
            check(block.size == SECTOR_SIZE) { failed(msg(R.string.recovery_short_read)) }
            block.copyInto(bytes, address)
            progress((address + SECTOR_SIZE) * 100 / FLASH_SIZE)
        }
        return bytes
    }
    fun backup(flash: RecoveryFlash, store: RecoveryStore, report: (AppText, Int) -> Unit): RecoveryBackup {
        checkDevice(flash)
        val first = readAll(flash) { report(msg(R.string.recovery_read_first), it) }
        val second = readAll(flash) { report(msg(R.string.recovery_read_second), it) }
        check(first.contentEquals(second)) { failed(msg(R.string.recovery_reads_differ)) }
        return store.save(first, flash.flashId, flash.chipKey)
    }
    internal fun changedSectors(before: ByteArray, region: ByteArray): List<Int> {
        require(before.size == FLASH_SIZE && region.size == END - START)
        return (START until END step SECTOR_SIZE).filter { address ->
            (0 until SECTOR_SIZE).any { before[address + it] != region[address - START + it] }
        }
    }
    fun plan(backup: RecoveryBackup, store: RecoveryStore, image: FirmwarePackage,
             deviceName: String, deviceId: Int, packageId: String): RecoveryPlan {
        check(knownBoot(backup.bootHash)) { failed(msg(R.string.recovery_unknown_boot)) }
        val before = store.load(backup)
        return RecoveryPlan(UUID.randomUUID().toString(), backup, deviceName, deviceId, packageId, image.sha256,
            changedSectors(before, region(image)).size)
    }
    fun restore(flash: RecoveryFlash, store: RecoveryStore, plan: RecoveryPlan, image: FirmwarePackage,
                report: (AppText, Int) -> Unit) {
        checkDevice(flash)
        check(flash.flashId == plan.backup.flashId && flash.chipKey == plan.backup.chipKey &&
            image.sha256 == plan.packageHash && knownBoot(plan.backup.bootHash)) { failed(msg(R.string.recovery_changed)) }
        val before = store.load(plan.backup)
        val firmware = region(image)
        val current = readAll(flash) { report(msg(R.string.recovery_recheck), it) }
        check(current.contentEquals(before)) { failed(msg(R.string.recovery_changed)) }
        // The plan and complete backup are revalidated before the first destructive command.
        val sectors = changedSectors(before, firmware)
        check(sectors.size == plan.changedSectors) { failed(msg(R.string.recovery_changed)) }
        store.result(plan.backup, "writing", plan.packageHash)
        try {
            writeAndVerify(flash, before, firmware, sectors, report)
            store.result(plan.backup, "verified; boot not checked", plan.packageHash)
        } catch (failure: Exception) {
            runCatching { store.result(plan.backup, "failed; write may be incomplete", plan.packageHash) }
            throw failure
        }
    }
    internal fun writeAndVerify(flash: RecoveryFlash, before: ByteArray, firmware: ByteArray,
                                sectors: List<Int>, report: (AppText, Int) -> Unit) {
        require(before.size == FLASH_SIZE && firmware.size == END - START)
        sectors.forEach(::checkSector)
        sectors.forEachIndexed { index, address ->
            val bytes = firmware.copyOfRange(address - START, address - START + SECTOR_SIZE)
            report(msg(R.string.recovery_writing, index + 1, sectors.size), index * 100 / sectors.size)
            flash.eraseSector(address)
            flash.programSector(address, bytes)
            check(flash.read(address, SECTOR_SIZE).contentEquals(bytes)) { failed(msg(R.string.recovery_verify_failed)) }
        }
        val expected = before.copyOf().also { firmware.copyInto(it, START) }
        val final = readAll(flash) { report(msg(R.string.recovery_verifying), it) }
        check(final.contentEquals(expected)) { failed(msg(R.string.recovery_verify_failed)) }
    }
}

/** Backups survive process death; verified bytes and metadata are synced before a plan can be used. */
class RecoveryStore(private val root: File) {
    private fun directory(id: String): File {
        require(Regex("[0-9a-f-]{36}").matches(id))
        return File(root, id)
    }
    private fun durable(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    fun save(bytes: ByteArray, flashId: Long, chipKey: Int): RecoveryBackup {
        require(bytes.size == Recovery.FLASH_SIZE)
        val backup = RecoveryBackup(UUID.randomUUID().toString(), FirmwarePackage.sha256(bytes), flashId, chipKey,
            FirmwarePackage.sha256(bytes.copyOfRange(0, Recovery.START)))
        val dir = directory(backup.id)
        check(dir.mkdirs())
        durable(File(dir, "full-flash-before.bin"), bytes)
        val metadata = JSONObject().put("id", backup.id).put("sha256", backup.sha256)
            .put("flashId", flashId).put("chipKey", chipKey).put("bootHash", backup.bootHash)
            .put("createdAt", System.currentTimeMillis()).put("size", bytes.size)
        durable(File(dir, "backup.json"), metadata.toString(2).toByteArray())
        load(backup)
        return backup
    }
    fun list(): List<RecoveryBackup> = root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }
        ?.mapNotNull { dir -> runCatching {
            val meta = JSONObject(File(dir, "backup.json").readText())
            RecoveryBackup(dir.name, meta.getString("sha256"), meta.getLong("flashId"), meta.getInt("chipKey"), meta.getString("bootHash"))
        }.getOrNull() }.orEmpty()
    fun load(backup: RecoveryBackup): ByteArray {
        val file = File(directory(backup.id), "full-flash-before.bin")
        check(file.length() == Recovery.FLASH_SIZE.toLong()) { failed(msg(R.string.recovery_backup_corrupt)) }
        val bytes = file.readBytes()
        check(FirmwarePackage.sha256(bytes) == backup.sha256 &&
            FirmwarePackage.sha256(bytes.copyOfRange(0, Recovery.START)) == backup.bootHash) { failed(msg(R.string.recovery_backup_corrupt)) }
        return bytes
    }
    fun result(backup: RecoveryBackup, status: String, packageHash: String) {
        val json = JSONObject().put("status", status).put("packageSha256", packageHash).put("updatedAt", System.currentTimeMillis())
        LocalLibrary.atomicWrite(File(directory(backup.id), "result.json"), json.toString(2).toByteArray())
    }
    fun export(backup: RecoveryBackup, output: OutputStream) {
        load(backup)
        ZipOutputStream(output).use { zip ->
            for (name in listOf("full-flash-before.bin", "backup.json", "result.json")) {
                val file = File(directory(backup.id), name)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }
    }
}
