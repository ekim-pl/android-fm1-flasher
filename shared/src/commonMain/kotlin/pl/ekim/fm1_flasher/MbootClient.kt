package pl.ekim.fm1_flasher

/** Read-only MIDI extensions. A validated MBOOT handshake is required for every connection. */
class MbootClient(private val link: MidiConnection, private val now: () -> Long = ::monotonicMillis) {
    private var info: DeviceDiagnostics? = null
    private fun exchange(command: Int, body: ByteArray): ByteArray {
        link.send(Protocol.frame(command, body))
        val deadline = now() + 3000
        var packets = 0
        while (now() < deadline && packets++ < 128) {
            val frame = link.receive((deadline - now()).coerceAtLeast(1)) ?: break
            if (Protocol.payload(frame, 0) != null) error("MBOOT command refused")
            Protocol.payload(frame, command)?.let { return it }
        }
        error("MBOOT reply missing")
    }
    fun identify(): DeviceDiagnostics {
        info = null
        val body = exchange(0x12, byteArrayOf())
        return parseInfo(body).also { info = it }
    }
    fun read(address: Int, count: Int): ByteArray {
        val device = checkNotNull(info) { "MBOOT handshake required" }
        require(address >= 0 && count in 1..512 && address.toLong() + count <= device.capacity)
        val request = byteArrayOf(0) + Protocol.little(address.toLong(), 4) + Protocol.little(count.toLong(), 3)
        val reply = exchange(0x23, request)
        require(reply.size == 8 + count && reply.copyOfRange(0, 8).contentEquals(request)) { "MBOOT read range mismatch" }
        return reply.copyOfRange(8, reply.size)
    }
    fun crc32(address: Int, count: Int): Long {
        val device = checkNotNull(info) { "MBOOT handshake required" }
        require(address >= 0 && count > 0 && address.toLong() + count <= device.capacity)
        val request = byteArrayOf(0) + Protocol.little(address.toLong(), 4) + Protocol.little(count.toLong(), 3)
        val reply = exchange(0x24, request)
        require(reply.size == 12 && reply.copyOfRange(0, 8).contentEquals(request)) { "MBOOT checksum range mismatch" }
        return Protocol.uint(reply, 8, 4)
    }
    companion object {
        fun parseInfo(body: ByteArray): DeviceDiagnostics {
            require(body.size in 32..96 && body.copyOfRange(0, 5).decodeToString() == "MBOOT" && body[5] == 1.toByte() && body[6].toInt() in 1..2) { "Unsupported MBOOT protocol" }
            require(body[26] == 0.toByte() && body[27] == 0.toByte() && body.last() == 0.toByte())
            val capacity = Protocol.uint(body, 10, 4)
            require(capacity == FlashLayout.SIZE.toLong()) { "Unsupported flash capacity" }
            val version = body.copyOfRange(28, body.lastIndex)
            require(version.isNotEmpty() && version.all { it.toInt() in 32..126 })
            return DeviceDiagnostics(Protocol.uint(body, 14, 4), Protocol.uint(body, 8, 2).toInt(), capacity.toInt(),
                DeviceDiagnostics.uid(body.copyOfRange(18, 26)), version.decodeToString(), "MIDI")
        }
    }
}
