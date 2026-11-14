package pl.ekim.fm1_flasher

import android.content.res.AssetManager
import android.hardware.usb.*
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bulk-only SCSI transport and M-Flasher v2.3 RAM loader. See third_party/m-flasher-NOTICE.md. */
class UsbBootConnection private constructor(
    private val connection: UsbDeviceConnection, private val iface: UsbInterface,
    private val input: UsbEndpoint, private val output: UsbEndpoint,
) : RecoveryFlash, Closeable {
    private class Refused : Exception("USB command refused")
    private var tag = java.util.Random().nextInt()
    private val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(12)
    override var flashId = 0L; private set
    override var chipKey = 0; private set

    companion object {
        fun open(manager: UsbManager, device: UsbDevice, assets: AssetManager): UsbBootConnection {
            require(ChipDetector.supports(device))
            val iface = (0 until device.interfaceCount).map(device::getInterface).single {
                it.interfaceClass == 8 && it.interfaceSubclass == 6 && it.interfaceProtocol == 80
            }
            val endpoints = (0 until iface.endpointCount).map(iface::getEndpoint)
                .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            val input = endpoints.single { it.direction == UsbConstants.USB_DIR_IN }
            val output = endpoints.single { it.direction == UsbConstants.USB_DIR_OUT }
            val connection = checkNotNull(manager.openDevice(device)) { "USB open failed" }
            val session = UsbBootConnection(connection, iface, input, output)
            try {
                check(connection.claimInterface(iface, true)) { "USB interface busy" }
                session.start(assets)
                return session
            } catch (failure: Exception) { session.close(); throw failure }
        }
        internal fun be(value: Int, size: Int) = ByteArray(size) { (value ushr (8 * (size - it - 1))).toByte() }
        internal fun writeArgs(address: Int, data: ByteArray) = be(address, 4) + be(data.size, 2) +
            byteArrayOf(0) + Protocol.little(ChipDetector.crc16(data).toLong(), 2)
    }

    private fun execute(command: ByteArray, data: ByteArray, read: Boolean = true): ByteArray {
        check(!Thread.currentThread().isInterrupted && System.nanoTime() < deadline) { "USB session timeout" }
        val currentTag = ++tag
        val cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x43425355).putInt(currentTag).putInt(data.size)
            .put(if (read) 0x80.toByte() else 0).put(0).put(command.size.toByte()).put(command).array()
        check(connection.bulkTransfer(output, cbw, cbw.size, 3000) == cbw.size) { "USB command failed" }
        val buffer = if (read) ByteArray(maxOf(input.maxPacketSize, data.size)) else data
        val count = connection.bulkTransfer(if (read) input else output, buffer, buffer.size, 5000)
        if (read && count < 0) connection.controlTransfer(2, 1, 0, input.address, null, 0, 1000)
        val earlyStatus = read && count == 13 && Protocol.uint(buffer, 0, 4) == 0x53425355L &&
            Protocol.uint(buffer, 4, 4) == (currentTag.toLong() and 0xFFFFFFFFL)
        val status = if (earlyStatus) buffer else ByteArray(maxOf(64, input.maxPacketSize))
        if (!earlyStatus) check(connection.bulkTransfer(input, status, status.size, 5000) == 13) { "USB status missing" }
        check(Protocol.uint(status, 0, 4) == 0x53425355L &&
            Protocol.uint(status, 4, 4) == (currentTag.toLong() and 0xFFFFFFFFL)) { "USB status mismatch" }
        if (status[12] == 1.toByte()) throw Refused()
        check(status[12] == 0.toByte() && Protocol.uint(status, 8, 4) == 0L) { "USB command failed" }
        check(count == data.size) { "USB data: $count/${data.size}" }
        return if (read) buffer.copyOf(count) else data
    }

    // Keep the complete reply (including the two-byte opcode echo).
    private fun call(command: Int, args: ByteArray = byteArrayOf()): ByteArray {
        val reply = execute(ChipDetector.cdb(command, args), ByteArray(16))
        check(reply[0] == (command shr 8).toByte() && reply[1] == command.toByte()) { "Loader reply mismatch" }
        return reply
    }

    private fun start(assets: AssetManager) {
        val inquiry = execute(byteArrayOf(0x12, 0, 0, 0, 36, 0), ByteArray(36))
        check(String(inquiry, 8, 8).trim() == "WL82" && String(inquiry, 16, 16).trim() == "UBOOT1.00") { "Not a JieLi download device" }
        val running = try { call(0xFC40).also { check(String(it, 2, 4) == "MFLR") } } catch (_: Refused) { null }
        if (running == null) {
            val other = try { call(0xFC0A); true } catch (_: Refused) { false }
            check(!other) { "Another loader is running; power cycle in download mode" }
        }
        val slotB = running != null && Protocol.uint(running.copyOfRange(10, 14).reversedArray(), 0, 4) != 0x01C28000L
        val address = if (slotB) 0x01C28000 else 0x01C20000
        val image = assets.open(if (slotB) "mflr-loader-b.bin" else "mflr-loader-a.bin").use { it.readBytes() }
        val hash = if (slotB) "6f9d97fcd3b16a74dd2b056ebca5e516c56a1c789a4b46b5afdd824d42fd684f"
            else "ae0cdf4965cc2438a362e933ccf11d41159322d67f2dc630a61ab84cc58e04d7"
        check(FirmwarePackage.sha256(image) == hash) { "Loader integrity failure" }
        for (offset in image.indices step 512) {
            val data = ChipDetector.scramble(image.copyOfRange(offset, minOf(offset + 512, image.size)))
            execute(ChipDetector.cdb(0xFB06, writeArgs(address + offset, data)), data, false)
        }
        call(0xFB08, be(address, 4) + be(1, 2))
        Thread.sleep(150)
        check(String(call(0xFC40), 2, 4) == "MFLR") { "Loader did not start" }
        flashId = Protocol.uint(call(0xFC0A), 4, 4)
        val key = call(0xFC09, be(0xAC6900, 4))
        chipKey = Protocol.uint(ChipDetector.scramble(byteArrayOf(key[7], key[6])), 0, 2).toInt()
    }

    override fun read(address: Int, length: Int): ByteArray {
        require(address >= 0 && length > 0 && address.toLong() + length <= Recovery.FLASH_SIZE)
        val result = ByteArray(length)
        for (offset in 0 until length step 4096) {
            val size = minOf(4096, length - offset)
            execute(ChipDetector.cdb(0xFD05, be(address + offset, 4) + be(size, 2)), ByteArray(size)).copyInto(result, offset)
        }
        return result
    }

    override fun eraseSector(address: Int) {
        Recovery.checkSector(address)
        call(0xFB01, be(address, 4))
    }

    override fun programSector(address: Int, data: ByteArray) {
        Recovery.checkSector(address)
        require(data.size == Recovery.SECTOR_SIZE)
        // 256-byte writes work within the loader's minimum supported buffer and NOR page size.
        for (offset in data.indices step 256) {
            val block = data.copyOfRange(offset, offset + 256)
            if (block.any { it != 0xFF.toByte() })
                execute(ChipDetector.cdb(0xFB04, writeArgs(address + offset, block)), block, false)
        }
    }

    override fun close() { connection.releaseInterface(iface); connection.close() }
}
