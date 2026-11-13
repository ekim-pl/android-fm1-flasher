package pl.fm1.flasher

import android.hardware.usb.UsbDevice
import android.media.midi.*
import android.os.Build
import android.os.Handler
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class UsbMidi(val info: MidiDeviceInfo, val usb: UsbDevice) {
    val endpoint get() = Endpoint(info.id, usb.deviceName)
    val label get() = "${info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: usb.productName ?: "USB MIDI"}\n" +
        "${usb.deviceName} · %04X:%04X · MIDI #%d".format(usb.vendorId, usb.productId, info.id)
}

class AndroidMidi(private val manager: MidiManager?, private val main: Handler) {
    @Suppress("DEPRECATION")
    fun devices(): List<UsbMidi> {
        val manager = manager ?: return emptyList()
        val infos = if (Build.VERSION.SDK_INT >= 33) manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
            else manager.devices.toSet()
        return infos.filter { it.type == MidiDeviceInfo.TYPE_USB && it.inputPortCount > 0 && it.outputPortCount > 0 }
            .mapNotNull { info -> info.properties.getParcelable<UsbDevice>(MidiDeviceInfo.PROPERTY_USB_DEVICE)?.let { UsbMidi(info, it) } }
            .sortedBy { it.usb.deviceName }
    }

    fun open(target: UsbMidi): MidiConnection {
        val manager = manager ?: error("Ten telefon nie udostępnia systemu MIDI.")
        check(devices().any { it.endpoint == target.endpoint }) { "Wybrany port USB jest już niedostępny." }
        val ready = CountDownLatch(1)
        val lock = Any()
        var opened: MidiDevice? = null
        var expired = false
        manager.openDevice(target.info, { device ->
            synchronized(lock) {
                if (expired) device?.close() else { opened = device; ready.countDown() }
            }
        }, main)
        try {
            check(ready.await(8, TimeUnit.SECONDS)) { "Nie można otworzyć wybranego portu MIDI." }
        } catch (failure: Exception) {
            synchronized(lock) { expired = true; opened?.close() }
            throw failure
        }
        val device = synchronized(lock) { opened } ?: error("Port MIDI jest zajęty lub niedostępny.")
        try {
            val inputIndex = target.info.ports.first { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }.portNumber
            val outputIndex = target.info.ports.first { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.portNumber
            val input = device.openInputPort(inputIndex) ?: error("Port zapisu MIDI jest zajęty.")
            try {
                val output = device.openOutputPort(outputIndex) ?: error("Port odczytu MIDI jest zajęty.")
                try { return Link(target.endpoint, device, input, output) }
                catch (failure: Exception) { output.close(); throw failure }
            } catch (failure: Exception) { input.close(); throw failure }
        } catch (failure: Exception) { device.close(); throw failure }
    }

    private inner class Link(
        override val endpoint: Endpoint, private val device: MidiDevice,
        private val input: MidiInputPort, private val output: MidiOutputPort,
    ) : MidiConnection {
        private val queue = LinkedBlockingQueue<ByteArray>(128)
        private val failure = AtomicReference<Exception?>()
        @Volatile private var closed = false
        private val assembler = SysExAssembler {
            check(queue.offer(it)) { "Przepełniony bufor odpowiedzi MIDI." }
        }
        private val receiver = object : MidiReceiver() {
            override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                try { assembler.accept(data, offset, count) }
                catch (problem: Exception) { failure.compareAndSet(null, problem) }
            }
        }
        init { output.connect(receiver) }

        override fun send(bytes: ByteArray) {
            checkAlive()
            input.send(bytes, 0, bytes.size)
        }

        override fun receive(timeoutMs: Long): ByteArray? {
            val deadline = System.nanoTime() / 1_000_000 + timeoutMs
            while (true) {
                checkAlive()
                val remaining = deadline - System.nanoTime() / 1_000_000
                if (remaining <= 0) return null
                queue.poll(remaining.coerceAtMost(200), TimeUnit.MILLISECONDS)?.let { return it }
            }
        }

        private fun checkAlive() {
            failure.get()?.let { throw IOException(it.message, it) }
            if (closed || devices().none { it.endpoint == endpoint }) throw IOException("Wybrany FM‑1 odłączył port MIDI.")
        }

        override fun close() {
            if (closed) return
            closed = true
            output.disconnect(receiver)
            runCatching { output.close() }
            runCatching { input.close() }
            runCatching { device.close() }
        }
    }
}
