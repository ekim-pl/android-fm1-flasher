package pl.ekim.fm1_flasher


data class FirmwareIdentity(val model: String, val version: String) {
    val isLoader get() = model.startsWith("ota-", ignoreCase = true)
    val baseModel get() = if (isLoader) model.substring(4) else model
    fun sameAs(other: FirmwareIdentity) = model == other.model &&
        version.trimStart('0') == other.version.trimStart('0')
    override fun toString() = "${model}_$version"
}

object Protocol {
    val identityQuery = hex("f0 00 32 45 00 00 00 40 7f f7")
    val upgrade = hex("f0 22 24 35 7f f7")
    const val VERIFIED = 0xE0000000L
    const val FINISHED = 0xF0000000L
    const val MAX_DATA = 512

    data class ReadRequest(val flashType: Int, val address: Long, val length: Int)

    fun pack7(bytes: ByteArray): ByteArray {
        val result = ByteAccumulator()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = buffer or ((byte.toInt() and 255) shl bits)
            bits += 8
            while (bits >= 7) {
                result.write(buffer and 127)
                buffer = buffer ushr 7
                bits -= 7
            }
        }
        if (bits > 0) result.write(buffer and 127)
        return result.toByteArray()
    }

    fun unpack7(bytes: ByteArray): ByteArray? {
        val result = ByteAccumulator()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            val value = byte.toInt() and 255
            if (value > 127) return null
            buffer = buffer or (value shl bits)
            bits += 7
            if (bits >= 8) {
                result.write(buffer and 255)
                buffer = buffer ushr 8
                bits -= 8
            }
        }
        val decoded = result.toByteArray()
        return decoded.takeIf { buffer == 0 && pack7(it).contentEquals(bytes) }
    }

    fun frame(command: Int, body: ByteArray): ByteArray {
        val decoded = byteArrayOf(0, 0x59, command.toByte()) + little(body.size.toLong(), 3) +
            body + checksum(body).toByte()
        return byteArrayOf(0xF0.toByte()) + pack7(decoded) + 0xF7.toByte()
    }

    private fun body(packet: ByteArray, command: Int): ByteArray? {
        if (packet.size < 4 || packet.first() != 0xF0.toByte() || packet.last() != 0xF7.toByte()) return null
        val decoded = unpack7(packet.copyOfRange(1, packet.lastIndex)) ?: return null
        if (decoded.size < 7 || decoded[0] != 0.toByte() || decoded[1] != 0x59.toByte() ||
            decoded[2] != command.toByte() || uint(decoded, 3, 3) != (decoded.size - 7).toLong()) return null
        val payload = decoded.copyOfRange(6, decoded.lastIndex)
        if (checksum(payload) != (decoded.last().toInt() and 255)) {
            // One observed V16 identity retained the V15 checksum. No other exception is accepted.
            val legacy = "FM-1_016".encodeToByteArray().copyOf(27)
            if (command != 0x11 || !payload.contentEquals(legacy) || decoded.last() != 0x19.toByte()) return null
        }
        return payload
    }

    fun identity(packet: ByteArray): FirmwareIdentity? {
        val payload = body(packet, 0x11)?.takeIf { it.size == 27 } ?: return null
        val plain = payload.latin1().trimEnd('\u0000')
        val match = Regex("([A-Za-z0-9-]+)_([0-9]{1,16})").matchEntire(plain)
        if (match != null) return FirmwareIdentity(match.groupValues[1], match.groupValues[2])
        // Older stock firmware includes a second identity field with ASCII '0' subtracted.
        val separator = payload.indexOf('_'.code.toByte())
        if (separator !in 1..12 || payload.take(separator).any { (it.toInt() and 255) !in 33..126 }) return null
        val legacyPlain = payload.copyOfRange(0, 8).decodeToString()
        if (!Regex("[A-Za-z0-9-]+_[0-9]+").matches(legacyPlain)) return null
        val encoded = payload.copyOfRange(8, 27).map { ((it.toInt() + 48) and 255).toChar() }.joinToString("")
        if (!encoded.startsWith(legacyPlain)) return null
        val version = encoded.drop(separator + 1).takeWhile { it in '0'..'9' }.takeIf { it.isNotEmpty() } ?: return null
        return FirmwareIdentity(payload.copyOfRange(0, separator).decodeToString(), version)
    }

    fun request(packet: ByteArray): ReadRequest? {
        val body = body(packet, 0x30)?.takeIf { it.size == 8 } ?: return null
        return ReadRequest(body[0].toInt() and 255, uint(body, 1, 4), uint(body, 5, 3).toInt())
    }

    fun response(request: ReadRequest, data: ByteArray): ByteArray {
        require(request.address in 0..0xFFFFFFFFL && request.flashType in 0..255)
        require(request.length in 1..MAX_DATA && data.size == request.length)
        return frame(0x30, byteArrayOf(request.flashType.toByte()) + little(request.address, 4) +
            little(data.size.toLong(), 3) + data)
    }

    fun checksum(bytes: ByteArray) = bytes.sumOf { it.toInt() and 255 }.inv() and 255
    fun uint(bytes: ByteArray, offset: Int, length: Int): Long =
        (0 until length).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 255) shl (8 * i)) }
    fun little(value: Long, length: Int) = ByteArray(length) { (value ushr (8 * it)).toByte() }
    fun hex(text: String) = text.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}

/** Android may deliver half a SysEx, several messages, or MIDI clock inside a SysEx. */
class SysExAssembler(private val emit: (ByteArray) -> Unit) {
    private val frame = ByteAccumulator()
    private var collecting = false

    private val lock = PlatformLock()

    @Throws(Exception::class)
    fun accept(bytes: ByteArray, offset: Int, count: Int) = lock.locked {
        require(offset >= 0 && count >= 0 && offset <= bytes.size - count)
        for (i in offset until offset + count) {
            val value = bytes[i].toInt() and 255
            when {
                value >= 0xF8 -> Unit
                value == 0xF0 -> { frame.reset(); collecting = true; frame.write(value) }
                value == 0xF7 -> if (collecting) {
                    frame.write(value); collecting = false; emit(frame.toByteArray()); frame.reset()
                }
                value >= 0x80 -> { collecting = false; frame.reset() }
                collecting -> {
                    check(frame.size() < 1024) { failed(msg("sysex_too_long")) }
                    frame.write(value)
                }
            }
        }
    }
}

private fun ByteArray.latin1() = map { (it.toInt() and 255).toChar() }.joinToString("")

private class ByteAccumulator {
    private var bytes = ByteArray(64)
    private var count = 0
    fun write(value: Int) {
        if (count == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
        bytes[count++] = value.toByte()
    }
    fun size() = count
    fun reset() { count = 0 }
    fun toByteArray() = bytes.copyOf(count)
}
