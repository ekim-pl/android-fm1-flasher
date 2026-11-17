package pl.ekim.fm1_flasher

/** Called only with a session-bound, validated plan by FlasherService. */
internal object DeviceToolsWriter {
    fun apply(flash: RecoveryFlash, info: DeviceDiagnostics, before: ByteArray, plan: FlashWritePlan,
              report: (Int) -> Unit = {}, validate: () -> Unit = {}) {
        require(info == plan.diagnostics && flash.flashId == info.flashId && flash.chipKey == info.chipKey)
        require(FirmwarePackage.sha256(before) == plan.beforeHash)
        val target = plan.targetImage()
        require(FirmwarePackage.sha256(target) == plan.targetHash)
        plan.changedSectors.forEach(plan::checkSector)
        validate()
        check(Recovery.readAll(flash) {}.contentEquals(before)) { "Flash changed after preview" }
        for ((index, address) in plan.changedSectors.withIndex()) {
            val bytes = target.copyOfRange(address, address + Recovery.SECTOR_SIZE)
            validate()
            flash.eraseSector(address)
            flash.programSector(address, bytes)
            check(flash.read(address, bytes.size).contentEquals(bytes)) { "Sector verification failed" }
            report((index + 1) * 100 / plan.changedSectors.size)
        }
        check(Recovery.readAll(flash) {}.contentEquals(target)) { "Full flash verification failed" }
    }
}
