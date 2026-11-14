package pl.ekim.fm1_flasher

import android.content.res.AssetManager
import android.hardware.usb.*

/** JieLi ROM protocol from jvitkauskas/m-flasher (MIT). Only RAM is written. */
object ChipDetector {
    fun supports(device: UsbDevice) = device.vendorId == 0x4C4A && device.productId == 0x8057
    fun classify(id: Long) = when (id) {
        0x856014L -> FlashChip.OLD
        0xB36014L -> FlashChip.NEW
        else -> FlashChip.UNKNOWN
    }

    fun detect(manager: UsbManager, device: UsbDevice, assets: AssetManager): Long =
        UsbBootConnection.open(manager, device, assets).use { it.flashId }

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
