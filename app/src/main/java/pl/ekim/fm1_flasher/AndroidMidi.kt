package pl.ekim.fm1_flasher

import android.hardware.usb.UsbDevice
import android.media.midi.*
import android.os.Handler
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class UsbMidi(val info: MidiDeviceInfo, val usb: UsbDevice, val generation: Long = 0) {
    val endpoint get() = Endpoint(info.id, usb.deviceName, generation)
    val label get() = "${info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: usb.productName ?: "USB MIDI"}\n" +
        "${usb.deviceName} · %04X:%04X · MIDI #%d".format(usb.vendorId, usb.productId, info.id)
}

class AndroidMidi(private val manager: MidiManager?, private val main: Handler) {
    private val generations = mutableMapOf<Int, Long>()
    private val paths = mutableMapOf<Int, String>()
    private var sequence = 0L
    private val retired = mutableSetOf<Int>()
    @Synchronized fun added(id: Int) { retired.remove(id) }
    @Synchronized fun removed(id: Int) { generations.remove(id); retired.add(id) }
    @Synchronized fun removedPath(path: String) { paths.filterValues { it == path }.keys.toList().forEach { removed(it) } }
    @Synchronized fun devices(): List<UsbMidi> {
        val manager = manager ?: return emptyList()
        val infos = manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
        return infos.filter { it.id !in retired && it.type == MidiDeviceInfo.TYPE_USB && it.inputPortCount > 0 && it.outputPortCount > 0 }
            .mapNotNull { info -> info.properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE, UsbDevice::class.java)?.let { UsbMidi(info, it, generations.getOrPut(info.id) { ++sequence }).also { paths[info.id] = it.usb.deviceName } } }
            .sortedBy { it.usb.deviceName }
    }

    fun open(target: UsbMidi): MidiConnection {
        MidiSessionGuard.acquire()
        try {
            val connection = openUnlocked(target)
            return object : MidiConnection by connection {
                private var closed = false
                override fun close() {
                    if (closed) return
                    closed = true
                    try { connection.close() } finally { MidiSessionGuard.release() }
                }
            }
        } catch (failure: Exception) { MidiSessionGuard.release(); throw failure }
    }

    private fun openUnlocked(target: UsbMidi): MidiConnection {
        val manager = manager ?: failed(msg(R.string.midi_unavailable))
        check(devices().any { it.endpoint == target.endpoint }) { failed(msg(R.string.usb_port_gone)) }
        check(devices().count { it.usb.deviceName == target.usb.deviceName } == 1) { failed(msg("fleet_ports_ambiguous")) }
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
            check(ready.await(8, TimeUnit.SECONDS)) { failed(msg(R.string.midi_open_timeout)) }
        } catch (failure: Exception) {
            synchronized(lock) { expired = true; opened?.close() }
            throw failure
        }
        check(devices().any { it.endpoint == target.endpoint }) { synchronized(lock) { opened?.close() }; failed(msg(R.string.usb_port_gone)) }
        val device = synchronized(lock) { opened } ?: failed(msg(R.string.midi_busy))
        try {
            check(target.info.inputPortCount == 1 && target.info.outputPortCount == 1) { failed(msg("fleet_ports_ambiguous")) }
            val inputIndex = target.info.ports.first { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }.portNumber
            val outputIndex = target.info.ports.first { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.portNumber
            val input = device.openInputPort(inputIndex) ?: failed(msg(R.string.midi_write_busy))
            try {
                val output = device.openOutputPort(outputIndex) ?: failed(msg(R.string.midi_read_busy))
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
        @Volatile private var disconnected = false
        private val callback = object : MidiManager.DeviceCallback() {
            override fun onDeviceRemoved(info: MidiDeviceInfo) {
                if (info.id == endpoint.id) disconnected = true
            }
        }
        private val assembler = SysExAssembler {
            check(queue.offer(it)) { failed(msg(R.string.midi_buffer_full)) }
        }
        private val receiver = object : MidiReceiver() {
            override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                try { assembler.accept(data, offset, count) }
                catch (problem: Exception) { failure.compareAndSet(null, problem) }
            }
        }
        init {
            manager?.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, { command -> main.post(command) }, callback)
            try {
                check(devices().any { it.endpoint == endpoint }) { failed(msg(R.string.midi_disconnected)) }
                output.connect(receiver)
            } catch (failure: Exception) { manager?.unregisterDeviceCallback(callback); throw failure }
        }

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
            if (closed || disconnected) failed(msg(R.string.midi_disconnected))
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { manager?.unregisterDeviceCallback(callback) }
            runCatching { output.disconnect(receiver) }
            runCatching { output.close() }
            runCatching { input.close() }
            runCatching { device.close() }
        }
    }
}
