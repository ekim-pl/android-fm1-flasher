package pl.ekim.fm1_flasher

/** Format algorithms adapted from m-flasher (MIT), see third_party/m-flasher-NOTICE.md. */
object FlashLayout {
    const val SIZE = 0x100000
    const val SECTOR = 0x1000
    const val BOOT_END = 0x4000
    const val FACTORY = 0xFF000
    data class Entry(val raw: ByteArray, val name: String, val address: Int, val length: Int, val attr: Int, val crc: Int)
    data class Region(val name: String, val start: Int, val end: Int)
    data class Analysis(val regions: List<Region>, val appValid: Boolean, val bootValid: Boolean,
                        val bootloader: String?, val fm1Evidence: Boolean, val layoutValid: Boolean)

    fun hex(value: Long, width: Int = 6) = "0x" + value.toString(16).uppercase().padStart(width, '0')
    fun cipher(bytes: ByteArray, initial: Int): ByteArray {
        var key = initial and 65535
        return ByteArray(bytes.size) { i ->
            val value = (bytes[i].toInt() xor key).toByte()
            key = ((key shl 1) xor if (key and 0x8000 != 0) 0x1021 else 0) and 65535
            value
        }
    }
    private fun number(bytes: ByteArray, at: Int, size: Int) = Protocol.uint(bytes, at, size)
    private fun name(bytes: ByteArray) = bytes.takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
    private fun entry(bytes: ByteArray): Entry? {
        if (bytes.size != 32 || FirmwarePackage.crc16(bytes, 2) != number(bytes, 0, 2).toInt()) return null
        val address = number(bytes, 4, 4); val length = number(bytes, 8, 4)
        val label = name(bytes.copyOfRange(16, 32)); val attr = bytes[12].toInt() and 255
        // app_area_head contains a CPU entry, not a physical flash address. Directory length can be a sentinel.
        when {
            label == "app_area_head" && attr == 0x83 -> require(address in 0x02000000L until 0x02100000L && length <= SIZE)
            label == "app_dir_head" -> require(address == BOOT_END.toLong() && length in setOf(0L, 0xFFFFFFFFL))
            else -> require(address <= SIZE && length <= SIZE && address + length <= SIZE) { "Flash record outside capacity" }
        }
        return Entry(bytes.copyOf(), label, address.toInt(), length.toInt(), attr, number(bytes, 2, 2).toInt())
    }
    fun bootTable(image: ByteArray): List<Entry> {
        require(image.size in BOOT_END..SIZE)
        val header = cipher(image.copyOfRange(0, 32), 65535)
        require(FirmwarePackage.crc16(header, 2) == number(header, 0, 2).toInt()) { "Invalid flash header" }
        val result = mutableListOf<Entry>()
        for (at in 32 until 0xA0 step 32) {
            val record = entry(cipher(image.copyOfRange(at, at + 32), 65535)) ?: break
            require(result.none { it.name == record.name }) { "Duplicate boot record" }
            result += record
        }
        return result
    }
    fun appRead(image: ByteArray, offset: Int, length: Int, key: Int): ByteArray {
        require(offset >= 0 && length >= 0 && BOOT_END.toLong() + offset + length <= image.size)
        val out = ByteArray(length)
        var copied = 0
        while (copied < length) {
            val relative = offset + copied
            val block = relative / 32 * 32
            val start = BOOT_END + block
            require(start + 32 <= image.size) { "Incomplete encrypted block" }
            val plain = cipher(image.copyOfRange(start, start + 32), key xor (block ushr 2))
            val skip = relative % 32; val count = minOf(32 - skip, length - copied)
            plain.copyInto(out, copied, skip, skip + count); copied += count
        }
        return out
    }
    fun appTable(image: ByteArray, key: Int): List<Entry> {
        val result = mutableListOf<Entry>()
        for (i in 0 until 16) {
            if (BOOT_END + (i + 1) * 32 > image.size) break
            val record = entry(appRead(image, i * 32, 32, key)) ?: break
            require(result.none { it.name == record.name }) { "Duplicate application record" }
            result += record
        }
        return result
    }
    fun checkApp(image: ByteArray, key: Int) {
        val table = appTable(image, key)
        val header = table.firstOrNull()
        require(header?.name == "app_area_head" && header.attr == 0x83) { "Application header missing" }
        val files = table.filter { it.attr == 0x82 }
        val app = files.singleOrNull { it.name == "app.bin" } ?: error("Application not found")
        val entry = header.address - 0x02000000
        require(entry >= app.address && entry.toLong() < app.address.toLong() + app.length) { "Invalid application entry" }
        require(files.any { it.name == "app.bin" } && files.all { it.length > 0 }) { "Application not found" }
        val ranges = mutableListOf<IntRange>()
        for (file in files) {
            require(file.address >= table.size * 32 && file.address.toLong() + file.length + BOOT_END <= minOf(image.size, FACTORY)) { "Application file outside image" }
            val range = file.address until file.address + file.length
            require(ranges.none { range.first <= it.last && it.first <= range.last }) { "Overlapping application files" }
            ranges += range
            require(FirmwarePackage.crc16(appRead(image, file.address, file.length, key)) == file.crc) { "Application checksum failed" }
        }
    }
    fun container(boot: ByteArray): ByteArray {
        require(boot.size >= 16 && number(boot, 0, 2) == 1L && FirmwarePackage.crc16(boot, 0, 14) == number(boot, 14, 2).toInt()) { "Invalid BANKCB header" }
        val offset = number(boot, 8, 4); val size = number(boot, 2, 2)
        require(offset >= 16 && size > 0 && offset + size <= boot.size) { "Invalid BANKCB range" }
        return boot.copyOfRange(offset.toInt(), (offset + size).toInt()).also {
            require(FirmwarePackage.crc16(it) == number(boot, 12, 2).toInt()) { "Invalid BANKCB payload" }
        }
    }
    fun bootIdentity(boot: ByteArray): Pair<String, String>? {
        val payload = container(boot)
        if (payload.size < 0x70 || payload.copyOfRange(0x40, 0x48).decodeToString() != "AC79BLID") return null
        return name(payload.copyOfRange(0x48, 0x50)) to name(payload.copyOfRange(0x50, 0x70))
    }
    fun storedBoot(image: ByteArray): ByteArray {
        val e = bootTable(image).firstOrNull() ?: error("Bootloader missing")
        require(e.name == "uboot.boot" && e.address == 0xA0 && e.length >= 16 && e.address + e.length <= BOOT_END)
        val boot = cipher(image.copyOfRange(e.address, e.address + 16), 65535) + cipher(image.copyOfRange(e.address + 16, e.address + e.length), 65535)
        require(FirmwarePackage.crc16(boot) == e.crc) { "Bootloader checksum failed" }
        container(boot)
        return boot
    }
    fun checkBoot(image: ByteArray) {
        val boot = storedBoot(image)
        val e = bootTable(image).singleOrNull { it.name == "isd_config.ini" } ?: error("Boot configuration missing")
        require(e.address >= 0xA0 + boot.size && e.length >= 34 && e.address + e.length <= BOOT_END)
        val config = image.copyOfRange(e.address, e.address + e.length)
        require(FirmwarePackage.crc16(config) == e.crc && FirmwarePackage.crc16(config, 0, 32) == number(config, 32, 2).toInt()) { "Boot configuration or key checksum failed" }
        val identity = bootIdentity(boot)
        val version = identity?.second?.substringBefore('-')?.split('.')?.mapNotNull { it.toIntOrNull() }
        val shortSupported = identity?.first == "fm1" && version?.size == 2 && (version[0] > 1 || version[0] == 1 && version[1] >= 4)
        require(shortSupported || e.length > 96) { "Stock bootloader needs full configuration" }
    }
    fun installBoot(image: ByteArray, boot: ByteArray): ByteArray {
        require(bootIdentity(boot)?.let { it.first == "fm1" && it.second.startsWith("2.4-") } == true) { "Unsupported bootloader" }
        require(boot.size <= 0x3FA0 - 0xA0)
        val table = bootTable(image)
        require(table.size >= 3 && table[0].name == "uboot.boot" && table[0].address == 0xA0)
        val config = table[1]
        require(config.name == "isd_config.ini" && config.address >= 0x38D0 && config.length >= 34 && config.address + config.length <= BOOT_END)
        require(table.drop(2).all { it.address >= BOOT_END })
        val key = image.copyOfRange(config.address, config.address + 34)
        require(FirmwarePackage.crc16(key, 0, 32) == number(key, 32, 2).toInt()) { "Damaged chip-key record" }
        val settings = Protocol.hex("04 4f 53 43 00 4f 53 43 30 04 4f 53 43 5f 46 52 45 51 00 00 36 6e 01 04 53 59 53 5f 43 4c 4b 00 00 2a 75 15 04 48 53 42 5f 44 49 56 00 01 00 00 00 04 4c 53 42 5f 44 49 56 00 02 00 00 00")
        val blob = key + settings
        check(blob.size == 96)
        val area = ByteArray(BOOT_END) { -1 }
        image.copyInto(area, 0, 0, 0xA0)
        fun update(record: Entry, at: Int, address: Int, bytes: ByteArray) {
            val raw = record.raw.copyOf()
            Protocol.little(FirmwarePackage.crc16(bytes).toLong(), 2).copyInto(raw, 2)
            Protocol.little(address.toLong(), 4).copyInto(raw, 4)
            Protocol.little(bytes.size.toLong(), 4).copyInto(raw, 8)
            Protocol.little(FirmwarePackage.crc16(raw, 2).toLong(), 2).copyInto(raw, 0)
            cipher(raw, 65535).copyInto(area, at)
        }
        update(table[0], 32, 0xA0, boot); update(config, 64, 0x3FA0, blob)
        cipher(boot.copyOfRange(0, 16), 65535).copyInto(area, 0xA0)
        cipher(boot.copyOfRange(16, boot.size), 65535).copyInto(area, 0xB0)
        blob.copyInto(area, 0x3FA0)
        checkBoot(area)
        return area
    }
    fun analyze(image: ByteArray, key: Int): Analysis {
        require(image.size == SIZE)
        val boot = runCatching { storedBoot(image) }.getOrNull()
        val identity = boot?.let { runCatching { bootIdentity(it) }.getOrNull() }
        val sectors = MutableList(SIZE / SECTOR) { "tools_region_unknown" }
        var valid = false; var fm1 = identity?.first == "fm1"
        runCatching {
            val table = appTable(image, key)
            val files = table.filter { it.attr == 0x82 }
            require(files.isNotEmpty())
            val end = files.maxOf { BOOT_END.toLong() + it.address + it.length }.toInt()
            require(end in BOOT_END..FACTORY)
            val protected = table.singleOrNull { it.name == "PRCT" }?.let {
                require(it.address == 0 && it.length in BOOT_END..FACTORY); it.length
            } ?: end
            val firmwareEnd = maxOf(end, protected)
            fun mark(name: String, start: Int, stop: Int) {
                require(start >= BOOT_END && stop > start && stop <= FACTORY)
                for (i in start / SECTOR until (stop + SECTOR - 1) / SECTOR) {
                    require(sectors[i] == "tools_region_unknown") { "Conflicting regions" }
                    sectors[i] = name
                }
            }
            mark("tools_region_firmware", BOOT_END, firmwareEnd)
            val regions = table.filter { it.attr in listOf(0x12, 0x92) && it.name != "PRCT" && it.length > 0 }
            val names = mapOf("VM" to "settings", "BTIF" to "pairings", "USR" to "sounds", "SEQ64" to "sequences", "USRFLASH" to "presets", "USRTRIM" to "calibration")
            for (e in regions) mark(names[e.name]?.let { "tools_region_$it" } ?: "tools_region_reserved", e.address, e.address + e.length)
            val stock = regions.any { it.name == "VM" && it.address == 0x93000 } && regions.any { it.name == "USR" && it.address == 0xEA000 }
            val v16 = regions.any { it.name == "VM" && it.address == 0xBA000 } && regions.any { it.name == "SEQ64" && it.address == 0xDA000 } && regions.any { it.name == "BTIF" && it.address == 0xEC000 }
            require(identity == null || identity.first == "fm1")
            fm1 = fm1 || stock || v16
            valid = true
        }.onFailure { sectors.fill("tools_region_unknown"); fm1 = false }
        for (i in 0 until BOOT_END / SECTOR) sectors[i] = "tools_region_boot"
        sectors[FACTORY / SECTOR] = "tools_region_factory"
        val regions = mutableListOf<Region>(); var start = 0
        while (start < sectors.size) {
            var end = start + 1
            while (end < sectors.size && sectors[end] == sectors[start]) end++
            regions += Region(sectors[start], start * SECTOR, end * SECTOR); start = end
        }
        return Analysis(regions, runCatching { checkApp(image, key) }.isSuccess, runCatching { checkBoot(image) }.isSuccess,
            identity?.let { "m-bootloader ${it.second} (${it.first})" }, fm1, valid)
    }
}

data class DeviceDiagnostics(val flashId: Long, val chipKey: Int, val capacity: Int,
    val uid: String? = null, val loader: String? = null, val transport: String = "ROM") {
    companion object {
        fun uid(bytes: ByteArray): String? = bytes.takeIf { it.size == 8 && it.any { b -> b != 0.toByte() } && it.any { b -> b != (-1).toByte() } }
            ?.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}
