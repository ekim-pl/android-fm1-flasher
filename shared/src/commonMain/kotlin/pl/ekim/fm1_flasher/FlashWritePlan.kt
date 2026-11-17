package pl.ekim.fm1_flasher

enum class FlashOperation { MBOOT, STOCK_BOOT, FIRMWARE, RESTORE, RESTORE_BOOT }

/** Owns its bytes: a preview cannot be changed after validation. */
class FlashWritePlan private constructor(
    val operation: FlashOperation, before: ByteArray, target: ByteArray, val diagnostics: DeviceDiagnostics,
    val inputHash: String, val layoutChanges: Boolean,
) {
    private val bytes = target.copyOf()
    val beforeHash = FirmwarePackage.sha256(before)
    val targetHash = FirmwarePackage.sha256(bytes)
    val changedSectors: List<Int> = (0 until FlashLayout.SIZE step FlashLayout.SECTOR).filter { a ->
        (a until a + FlashLayout.SECTOR).any { before[it] != bytes[it] }
    }
    fun targetImage() = bytes.copyOf()
    fun checkSector(address: Int) {
        require(address % FlashLayout.SECTOR == 0 && address in changedSectors)
        require(when (operation) {
            FlashOperation.MBOOT, FlashOperation.STOCK_BOOT -> address in 0 until FlashLayout.BOOT_END
            FlashOperation.FIRMWARE, FlashOperation.RESTORE -> address in FlashLayout.BOOT_END until FlashLayout.FACTORY
            FlashOperation.RESTORE_BOOT -> address in 0 until FlashLayout.FACTORY
        }) { "Protected flash sector" }
    }
    companion object {
        const val STOCK_V15 = "db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a"
        fun create(operation: FlashOperation, before: ByteArray, info: DeviceDiagnostics,
                   input: ByteArray, inputHash: String, backupInfo: DeviceDiagnostics? = null): FlashWritePlan {
            require(before.size == FlashLayout.SIZE && info.capacity == FlashLayout.SIZE && info.chipKey == 0x980F && info.flashId in setOf(0x856014L, 0xB36014L)) { "Unsupported hardware" }
            require(FirmwarePackage.sha256(input) == inputHash) { "Input changed" }
            val analysis = FlashLayout.analyze(before, info.chipKey)
            require(analysis.fm1Evidence && analysis.layoutValid) { "Insufficient FM-1 evidence" }
            val target = before.copyOf()
            when (operation) {
                FlashOperation.MBOOT -> FlashLayout.installBoot(before, input).copyInto(target)
                FlashOperation.STOCK_BOOT, FlashOperation.FIRMWARE -> {
                    val pkg = FirmwarePackage.inspect(input)
                    require(pkg.identity.model == "FM-1")
                    FlashLayout.checkApp(pkg.flashImage, info.chipKey)
                    if (operation == FlashOperation.STOCK_BOOT) {
                        require(pkg.sha256 == STOCK_V15) { "Stock package is not approved" }
                        FlashLayout.checkBoot(pkg.flashImage)
                        pkg.flashImage.copyInto(target, 0, 0, FlashLayout.BOOT_END)
                    } else {
                        require(analysis.bootValid) { "Repair the bootloader first" }
                        require(pkg.flashImage.size in (FlashLayout.BOOT_END + 1)..FlashLayout.FACTORY)
                        pkg.flashImage.copyInto(target, FlashLayout.BOOT_END, FlashLayout.BOOT_END)
                    }
                }
                FlashOperation.RESTORE, FlashOperation.RESTORE_BOOT -> {
                    require(input.size == FlashLayout.SIZE && info.uid != null && backupInfo?.uid == info.uid &&
                        backupInfo.flashId == info.flashId && backupInfo.chipKey == info.chipKey && backupInfo.capacity == info.capacity) { "Backup device identity is missing or different" }
                    require(input.copyOfRange(FlashLayout.FACTORY, FlashLayout.SIZE).contentEquals(before.copyOfRange(FlashLayout.FACTORY, FlashLayout.SIZE))) { "Factory data differs" }
                    val saved = FlashLayout.analyze(input, info.chipKey)
                    require(saved.fm1Evidence && saved.layoutValid && saved.appValid) { "Backup firmware is invalid" }
                    if (operation == FlashOperation.RESTORE_BOOT) FlashLayout.checkBoot(input)
                    else require(analysis.bootValid) { "Retained bootloader is damaged" }
                    val start = if (operation == FlashOperation.RESTORE_BOOT) 0 else FlashLayout.BOOT_END
                    input.copyInto(target, start, start, FlashLayout.FACTORY)
                }
            }
            val next = FlashLayout.analyze(target, info.chipKey)
            val result = FlashWritePlan(operation, before, target, info, inputHash, analysis.regions != next.regions)
            result.changedSectors.forEach(result::checkSector)
            require(before.copyOfRange(FlashLayout.FACTORY, FlashLayout.SIZE).contentEquals(target.copyOfRange(FlashLayout.FACTORY, FlashLayout.SIZE)))
            return result
        }
    }
}
