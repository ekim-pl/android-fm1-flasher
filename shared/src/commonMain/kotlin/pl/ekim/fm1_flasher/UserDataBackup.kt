package pl.ekim.fm1_flasher

/** The payload contains a compatibility hash followed by the user partitions in flash order. */
object UserDataBackup {
    const val HASH_SIZE = 64
    const val MIN_SIZE = HASH_SIZE + FlashLayout.SECTOR
    const val MAX_SIZE = HASH_SIZE + FlashLayout.FACTORY - FlashLayout.BOOT_END
    private val partitions = setOf("VM", "USR", "SEQ64", "USRFLASH")

    private fun ranges(image: ByteArray, key: Int): List<IntRange> {
        val layout = FlashLayout.analyze(image, key)
        require(layout.fm1Evidence && layout.layoutValid && layout.appValid) { "Unrecognized FM-1 data layout" }
        return FlashLayout.appTable(image, key)
            .filter { it.name in partitions && it.attr in setOf(0x12, 0x92) && it.length > 0 }
            .sortedBy { it.address }
            .map {
                require(it.address % FlashLayout.SECTOR == 0 && it.length % FlashLayout.SECTOR == 0) {
                    "User partition is not sector-aligned"
                }
                it.address until it.address + it.length
            }.also { require(it.isNotEmpty()) { "No user partitions found" } }
    }

    private fun compatibility(image: ByteArray, key: Int): ByteArray {
        val firmware = FlashLayout.analyze(image, key).regions.single { it.name == "tools_region_firmware" }
        return FirmwarePackage.sha256(image.copyOfRange(firmware.start, firmware.end) +
            image.copyOfRange(FlashLayout.FACTORY, FlashLayout.SIZE)).encodeToByteArray()
    }

    fun capture(image: ByteArray, key: Int): ByteArray {
        val ranges = ranges(image, key)
        val payload = ByteArray(HASH_SIZE + ranges.sumOf { it.last + 1 - it.first })
        compatibility(image, key).copyInto(payload)
        var offset = HASH_SIZE
        for (range in ranges) {
            image.copyInto(payload, offset, range.first, range.last + 1)
            offset += range.last + 1 - range.first
        }
        return payload
    }

    internal fun restore(image: ByteArray, key: Int, payload: ByteArray, target: ByteArray): List<IntRange> {
        val ranges = ranges(image, key)
        require(payload.size == HASH_SIZE + ranges.sumOf { it.last + 1 - it.first } &&
            compatibility(image, key).contentEquals(payload.copyOfRange(0, HASH_SIZE))) {
            "Backup requires matching firmware and factory data"
        }
        var offset = HASH_SIZE
        for (range in ranges) {
            val length = range.last + 1 - range.first
            payload.copyInto(target, range.first, offset, offset + length)
            offset += length
        }
        return ranges
    }
}
