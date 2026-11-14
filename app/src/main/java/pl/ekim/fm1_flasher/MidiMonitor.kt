package pl.ekim.fm1_flasher

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.*
import android.os.Handler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

data class MonitorPort(val info: MidiDeviceInfo, val usb: UsbDevice, val number: Int, val name: String) {
    val key get() = "${info.id}:${usb.deviceName}:$number"
    val label get() = "${info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: usb.productName ?: "USB MIDI"} · ${usb.deviceName} · #${info.id} OUT $number ${name}"
}

/** Passive monitor: deliberately never opens MidiInputPort or sends MIDI. Main-thread lifecycle. */
class MidiMonitor(private val manager: MidiManager?, private val usb: UsbManager, private val main: Handler) {
    val log = MidiLog()
    val selected = linkedSetOf<String>()
    @Volatile var running = false
        private set
    private var generation = 0
    private var worker: ThreadPoolExecutor? = null
    private val opening = mutableSetOf<Int>()
    private val devices = mutableMapOf<Int, MidiDevice>()
    private val outputs = mutableMapOf<String, Pair<MidiOutputPort, MidiReceiver>>()
    private val disableReceivers = mutableMapOf<String, () -> Unit>()
    private val parsers = mutableMapOf<String, MidiStreamParser>()
    private val losses = mutableMapOf<String, AtomicLong>()
    val activeCount get() = outputs.size
    private var activePorts = emptyList<MonitorPort>()
    fun ports(): List<MonitorPort> = manager?.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
        ?.filter { it.type == MidiDeviceInfo.TYPE_USB }
        ?.flatMap { info ->
            val device = info.properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE, UsbDevice::class.java)
            if (device == null) emptyList() else info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                .map { MonitorPort(info, device, it.portNumber, it.name ?: "") }
        }?.sortedBy { it.label } ?: emptyList()

    fun start() {
        stop()
        val chosen = ports().filter { it.key in selected }
        if (chosen.isEmpty()) return
        running = true
        val epoch = generation
        val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(256))
        worker = executor
        activePorts = chosen
        log.add("Logger", byteArrayOf(), "START")
        chosen.groupBy { it.info.id }.forEach { (id, ports) ->
            if (!usb.hasPermission(ports.first().usb)) {
                ports.forEach { log.add(it.label, byteArrayOf(), "USB permission denied") }; return@forEach
            }
            opening += id
            main.postDelayed({
                if (running && generation == epoch && opening.remove(id)) {
                    ports.forEach { log.add(it.label, byteArrayOf(), "Open timeout") }
                }
            }, 8000)
            try {
                manager?.openDevice(ports.first().info, { device ->
                    if (!running || generation != epoch || !opening.remove(id) || ports.none { requested -> ports().any { it.key == requested.key } }) { device?.close(); return@openDevice }
                    if (device == null) { ports.forEach { log.add(it.label, byteArrayOf(), "Open failed") }; return@openDevice }
                    devices[id] = device
                    ports.forEach { source ->
                        try {
                            val port = device.openOutputPort(source.number) ?: error("Port busy")
                            val pendingLoss = AtomicLong()
                            losses[source.label] = pendingLoss
                            var received = 0L
                            var stamp = 0L
                            val parser = MidiStreamParser { bytes, note -> log.add(source.label, bytes, note, received, stamp) }
                            parsers[source.key] = parser
                            var accepting = true
                            val receiver = object : MidiReceiver() {
                                @Synchronized override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                                    if (!accepting) return
                                    var at = offset
                                    val end = offset + count
                                    while (at < end) {
                                        val n = minOf(4096, end - at)
                                        val bytes = data.copyOfRange(at, at + n)
                                        val time = System.nanoTime()
                                        val lost = pendingLoss.getAndSet(0)
                                        try {
                                            executor.execute {
                                                if (lost > 0) { parser.reset(); log.loss(source.label, lost) }
                                                received = time; stamp = timestamp
                                                parser.accept(bytes)
                                            }
                                        } catch (_: RejectedExecutionException) { pendingLoss.addAndGet(lost + n) }
                                        at += n
                                    }
                                }
                            }
                            disableReceivers[source.key] = { synchronized(receiver) { accepting = false } }
                            try { port.connect(receiver) } catch (e: Exception) { port.close(); throw e }
                            outputs[source.key] = port to receiver
                            log.add(source.label, byteArrayOf(), "OPEN")
                        } catch (e: Exception) { log.add(source.label, byteArrayOf(), "Open failed: ${e.message}") }
                    }
                    if (ports.none { it.key in outputs }) { devices.remove(id); runCatching { device.close() } }
                }, main)
            } catch (e: Exception) { ports.forEach { log.add(it.label, byteArrayOf(), "Open failed: ${e.message}") } }
        }
    }

    fun removed(id: Int) {
        opening.remove(id)
        activePorts.filter { it.info.id == id }.forEach {
            closePort(it.key); log.add(it.label, byteArrayOf(), "DISCONNECTED")
        }
        activePorts.filter { it.info.id == id }.forEach { source ->
            losses.remove(source.label)?.getAndSet(0)?.takeIf { it > 0 }?.let { log.loss(source.label, it) }
        }
        devices.remove(id)?.let { runCatching { it.close() } }
    }
    private fun closePort(key: String) {
        disableReceivers.remove(key)?.invoke()
        outputs.remove(key)?.let { (port, receiver) ->
            runCatching { port.disconnect(receiver) }.onFailure { log.add("Logger", byteArrayOf(), "Disconnect failed: ${it.message}") }
            runCatching { port.close() }.onFailure { log.add("Logger", byteArrayOf(), "Close failed: ${it.message}") }
        }
        parsers.remove(key)?.let { parser ->
            runCatching { worker?.execute { parser.finish() } }.onFailure {
                log.add("Logger", byteArrayOf(), "Partial message discarded at port close")
            }
        }
    }
    fun stop() {
        generation++
        opening.clear()
        (outputs.keys + parsers.keys).toSet().forEach(::closePort)
        devices.values.forEach { runCatching { it.close() } }; devices.clear()
        worker?.let { executor ->
            executor.shutdown()
            // Bound the handover: drain queued decoding before flash can take ownership.
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                val dropped = executor.shutdownNow().size
                log.add("Logger", byteArrayOf(), "STOP discarded $dropped queued packets")
                check(executor.awaitTermination(1, TimeUnit.SECONDS)) { "MIDI logger did not stop" }
            }
        }
        losses.forEach { (source, count) -> count.getAndSet(0).takeIf { it > 0 }?.let { log.loss(source, it) } }; losses.clear()
        worker = null; activePorts = emptyList()
        if (running) log.add("Logger", byteArrayOf(), "STOP")
        running = false
    }
}
