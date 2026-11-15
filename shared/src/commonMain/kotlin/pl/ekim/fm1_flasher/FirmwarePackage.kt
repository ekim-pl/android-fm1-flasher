package pl.ekim.fm1_flasher


class FirmwarePackage private constructor(
    val identity: FirmwareIdentity,
    val logical: ByteArray,
    val sha256: String,
    val rawSize: Int,
    val flashImage: ByteArray,
) {
    companion object {
        const val MAX_SIZE = 4 * 1024 * 1024

        fun parse(raw: ByteArray, expectedHash: String, expectedProduct: String): FirmwarePackage {
            require(raw.size in 960..MAX_SIZE) { invalid(msg("package_invalid_size")) }
            require(expectedHash.matches(Regex("[0-9a-fA-F]{64}")) && sha256(raw).equals(expectedHash, true)) {
                invalid(msg("package_hash_mismatch"))
            }
            val product = productPrefix(raw)
            require(product == expectedProduct && Regex("FM-1_[0-9]{1,16}").matches(product)) {
                invalid(msg("package_identity_invalid"))
            }
            val logical = ByteArray(raw.size - 20)
            repeat(20) { raw.copyInto(logical, it * 47, it * 48, it * 48 + 47) }
            raw.copyInto(logical, 940, 960)
            val header = decrypt(logical.copyOfRange(0, 64))
            require(crc16(header, 2, 64) == Protocol.uint(header, 0, 2).toInt()) { invalid(msg("ufw_header_crc")) }
            require(header.copyOfRange(16, 64).decodeToString().trimEnd('\u0000') == "AC791N") {
                invalid(msg("unsupported_processor"))
            }
            val imageSize = Protocol.uint(header, 4, 4)
            val count = Protocol.uint(header, 8, 2).toInt()
            require(imageSize == logical.size.toLong() && count in 1..10) { invalid(msg("ufw_invalid_list")) }
            val listEnd = 64 + count * 80
            require(listEnd <= 940 && listEnd <= logical.size &&
                crc16(logical, 64, listEnd) == Protocol.uint(header, 2, 2).toInt()) { invalid(msg("ufw_list_crc")) }
            var flashImage: ByteArray? = null
            var hasLoader = false
            val ranges = mutableListOf<LongRange>()
            repeat(count) { i ->
                val entry = decrypt(logical.copyOfRange(64 + i * 80, 144 + i * 80))
                val offset = Protocol.uint(entry, 8, 4)
                val size = Protocol.uint(entry, 12, 4)
                val padded = Protocol.uint(entry, 16, 4)
                val name = entry.copyOfRange(64, 80).decodeToString().trimEnd('\u0000')
                // A final entry may omit alignment padding on disk; only its real bytes must exist.
                require(offset >= 940 && padded >= size && padded <= MAX_SIZE && offset + size <= imageSize &&
                    (size == 0L || ranges.none { offset <= it.last && offset + size - 1 >= it.first })) { invalid(msg("ufw_overlap")) }
                if (size > 0) ranges += offset until offset + size
                val type = Protocol.uint(entry, 0, 2).toInt()
                // Auxiliary vendor entries may be encrypted and carry a plaintext CRC.
                // The flash image and OTA loader have CRCs over their stored bytes; SHA-256 covers the whole package.
                if (type == 0 || type == 100) {
                    require(crc16(logical, offset.toInt(), (offset + size).toInt()) == Protocol.uint(entry, 4, 2).toInt()) {
                        invalid(msg("ufw_file_crc", name))
                    }
                }
                if (name == "flash.bin" && type == 0 && size >= 0x4000) {
                    require(flashImage == null) { invalid(msg("ufw_overlap")) }
                    flashImage = logical.copyOfRange(offset.toInt(), (offset + size).toInt())
                }
                if (name == "ota.bin" && type == 100 && size > 0) hasLoader = true
            }
            require(flashImage != null && hasLoader) { invalid(msg("ufw_missing_images")) }
            return FirmwarePackage(FirmwareIdentity("FM-1", product.substringAfter('_')), logical, expectedHash.lowercase(), raw.size, checkNotNull(flashImage))
        }

        /** Identifies metadata only. Installation still requires parse() with the pinned hash. */
        fun productPrefix(raw: ByteArray): String {
            require(raw.size >= 960) { invalid(msg("package_invalid_size")) }
            return buildString {
                for (i in 0 until 20) {
                    val marker = raw[i * 48 + 47].toInt() and 255
                    if (marker != 0x7D) append(((marker - i - 1) and 255).toChar())
                }
            }
        }
        fun inspect(raw: ByteArray) = parse(raw, sha256(raw), productPrefix(raw))

        fun sha256(bytes: ByteArray): String = platformSha256(bytes)

        fun crc16(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): Int {
            var crc = 0
            for (i in start until end) {
                crc = crc xor ((bytes[i].toInt() and 255) shl 8)
                repeat(8) { crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xFFFF }
            }
            return crc
        }

        private fun decrypt(bytes: ByteArray): ByteArray {
            var key = 0xFFFF
            return ByteArray(bytes.size) { i ->
                val value = (bytes[i].toInt() xor key).toByte()
                key = ((key shl 1) xor if (key and 0x8000 != 0) 0x1021 else 0) and 0xFFFF
                value
            }
        }
    }
}
