package pl.ekim.fm1_flasher

import android.content.res.AssetManager
import android.hardware.usb.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** JieLi ROM protocol from jvitkauskas/m-flasher (MIT). Only RAM is written. */
object ChipDetector {
    private class Refused : Exception("USB command refused")
    fun supports(device: UsbDevice) = device.vendorId == 0x4C4A && device.productId == 0x8057
    fun classify(id: Long) = when (id) {
        0x856014L -> FlashChip.OLD
        0xB36014L -> FlashChip.NEW
        else -> FlashChip.UNKNOWN
    }

    fun detect(manager: UsbManager, device: UsbDevice, assets: AssetManager): Long {
        require(supports(device))
        val iface = (0 until device.interfaceCount).map(device::getInterface).first {
            it.interfaceClass == 8 && it.interfaceSubclass == 6 && it.interfaceProtocol == 80
        }
        val endpoints = (0 until iface.endpointCount).map(iface::getEndpoint).filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val input = endpoints.single { it.direction == UsbConstants.USB_DIR_IN }
        val output = endpoints.single { it.direction == UsbConstants.USB_DIR_OUT }
        val connection = checkNotNull(manager.openDevice(device)) { "USB open failed" }
        try {
            check(connection.claimInterface(iface, true)) { "USB interface busy" }
            var tag = java.util.Random().nextInt()
            fun execute(cdb: ByteArray, data: ByteArray, read: Boolean = true): ByteArray {
                val currentTag = ++tag
                val cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(0x43425355).putInt(currentTag).putInt(data.size)
                    .put(if (read) 0x80.toByte() else 0).put(0).put(cdb.size.toByte())
                    .put(cdb).array()
                check(connection.bulkTransfer(output, cbw, cbw.size, 3000) == cbw.size) { "USB command failed" }
                val buffer = if (read) ByteArray(maxOf(input.maxPacketSize, data.size)) else data
                val count = connection.bulkTransfer(if (read) input else output, buffer, buffer.size, 3000)
                if (read && count < 0) connection.controlTransfer(2, 1, 0, input.address, null, 0, 1000)
                val earlyStatus = read && count == 13 && Protocol.uint(buffer, 0, 4) == 0x53425355L &&
                    Protocol.uint(buffer, 4, 4) == (currentTag.toLong() and 0xFFFFFFFFL)
                val status = if (earlyStatus) buffer else ByteArray(maxOf(64, input.maxPacketSize))
                if (!earlyStatus) check(connection.bulkTransfer(input, status, status.size, 3000) == 13) { "USB status missing" }
                check(Protocol.uint(status, 0, 4) == 0x53425355L && Protocol.uint(status, 4, 4) == (currentTag.toLong() and 0xFFFFFFFFL)) { "USB status mismatch" }
                if (status[12] == 1.toByte()) throw Refused()
                check(status[12] == 0.toByte() && Protocol.uint(status, 8, 4) == 0L) { "USB command failed" }
                check(count == data.size) { "USB data: $count/${data.size}" }
                return if (read) buffer.copyOf(count) else data
            }
            fun call(command: Int, args: ByteArray = byteArrayOf()): ByteArray {
                val reply = execute(cdb(command, args), ByteArray(16))
                check(reply[0] == (command shr 8).toByte() && reply[1] == command.toByte()) { "Loader reply mismatch" }
                return reply
            }
            val inquiry = execute(byteArrayOf(0x12, 0, 0, 0, 36, 0), ByteArray(36))
            check(String(inquiry, 8, 8).trim() == "WL82" && String(inquiry, 16, 16).trim() == "UBOOT1.00") { "Not a JieLi download device" }
            val running = try { call(0xFC40).also { check(String(it, 2, 4) == "MFLR") } } catch (_: Refused) { null }
            if (running == null) {
                // Never jump through an unrelated running loader.
                val other = try { call(0xFC0A); true } catch (_: Refused) { false }
                check(!other) { "Another loader is running; power cycle in download mode" }
            }
            val slotB = running != null && Protocol.uint(running.copyOfRange(10, 14).reversedArray(), 0, 4) != 0x01C28000L
            val address = if (slotB) 0x01C28000 else 0x01C20000
            val image = assets.open(if (slotB) "mflr-loader-b.bin" else "mflr-loader-a.bin").use { it.readBytes() }
            image.asList().chunked(512).forEachIndexed { index, block ->
                val data = scramble(block.toByteArray())
                val args = be(address + index * 512, 4) + be(data.size, 2) + byteArrayOf(0) + Protocol.little(crc16(data).toLong(), 2)
                execute(cdb(0xFB06, args), data, false)
            }
            call(0xFB08, be(address, 4) + be(1, 2))
            Thread.sleep(150)
            check(String(call(0xFC40), 2, 4) == "MFLR") { "Loader did not start" }
            return Protocol.uint(call(0xFC0A), 4, 4)
        } finally {
            connection.releaseInterface(iface)
            connection.close()
        }
    }

    internal fun cdb(command: Int, args: ByteArray) = ByteArray(16) { 0xFF.toByte() }.apply {
        require(args.size <= 14)
        this[0] = (command shr 8).toByte(); this[1] = command.toByte()
        args.copyInto(this, 2)
    }
    private fun be(value: Int, size: Int) = ByteArray(size) { (value ushr (8 * (size - it - 1))).toByte() }
    internal fun crc16(data: ByteArray, initial: Int = 0): Int {
        var crc = initial
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 255) shl 8)
            repeat(8) { crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 65535 }
        }
        return crc
    }
    internal fun scramble(data: ByteArray): ByteArray {
        val magic = Protocol.hex("C3 CF C0 E8 CE D2 B0 AE C4 E3 A3 AC D3 F1 C1 D6")
        var crc = crc16(byteArrayOf(-1, -1), 65535)
        return data.mapIndexed { i, b ->
            crc = crc16(byteArrayOf(magic[i % magic.size]), crc)
            (b.toInt() xor crc).toByte()
        }.toByteArray()
    }
}
