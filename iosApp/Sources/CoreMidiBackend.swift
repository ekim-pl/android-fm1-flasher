import Foundation
import CoreMIDI
import Darwin
import FM1Compose

private func midiCheck(_ status: OSStatus, _ operation: String) throws {
    guard status == noErr else {
        throw NSError(domain: "FM1.CoreMIDI", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "\(operation): Core MIDI \(status)"])
    }
}

private func midiName(_ object: MIDIObjectRef) -> String {
    var value: Unmanaged<CFString>?
    guard MIDIObjectGetStringProperty(object, kMIDIPropertyDisplayName, &value) == noErr,
          let value else { return "MIDI #\(object)" }
    return value.takeRetainedValue() as String
}

func kotlinBytes(_ data: [UInt8]) -> KotlinByteArray {
    let result = KotlinByteArray(size: Int32(data.count))
    for (index, value) in data.enumerated() { result.set(index: Int32(index), value: Int8(bitPattern: value)) }
    return result
}

/// Enumerates paired endpoints of hardware entities. Ambiguous entities are omitted;
/// names are display labels, never evidence that two endpoints are the same FM-1.
final class CoreMidiBackend: NSObject, ProbeBackend, PassiveMidiBackend {
    private struct Pair: Equatable {
        let source: MIDIEndpointRef
        let destination: MIDIEndpointRef
        let label: String
    }
    let midiLog = MidiLog(maxEntries: 5000, maxBytes: 4 * 1024 * 1024)
    private let monitorLock = NSLock()
    private let monitorLifecycle = NSRecursiveLock()
    private var monitorInputs: [(MIDIPortRef, MIDIEndpointRef)] = []
    private var monitoring = false
    var monitorRunning: Bool { monitorLock.lock(); defer { monitorLock.unlock() }; return monitoring }
    private let decodeQueue = DispatchQueue(label: "FM1 MIDI decode")
    private var monitorGeneration = 0
    private var monitorParsers: [MidiStreamParser] = []
    private var pendingPackets = 0
    private var lostBySource: [MIDIEndpointRef: Int64] = [:]
    private let lock = NSLock()
    private var listed: [Int32: Pair] = [:]
    private var client = MIDIClientRef()
    private var clientStatus: OSStatus = noErr

    override init() {
        super.init()
        clientStatus = MIDIClientCreateWithBlock("FM1 Flasher" as CFString, &client) { _ in }
    }
    deinit { stopMonitor(); if client != 0 { MIDIClientDispose(client) } }

    private func enumerate() -> [Int32: Pair] {
        var pairs: [Int32: Pair] = [:]
        for index in 0..<MIDIGetNumberOfDevices() {
            let device = MIDIGetDevice(index)
            var offline: Int32 = 0
            if MIDIObjectGetIntegerProperty(device, kMIDIPropertyOffline, &offline) == noErr && offline != 0 { continue }
            for entityIndex in 0..<MIDIDeviceGetNumberOfEntities(device) {
                let entity = MIDIDeviceGetEntity(device, entityIndex)
                guard MIDIEntityGetNumberOfSources(entity) == 1,
                      MIDIEntityGetNumberOfDestinations(entity) == 1 else { continue }
                let source = MIDIEntityGetSource(entity, 0)
                let destination = MIDIEntityGetDestination(entity, 0)
                guard source != 0 && destination != 0 else { continue }
                let id = Int32(bitPattern: destination)
                pairs[id] = Pair(source: source, destination: destination,
                    label: "\(midiName(device)) · \(midiName(entity))\nIN #\(source) · OUT #\(destination)")
            }
        }
        return pairs
    }

    func devices() throws -> [ProbePort] {
        try midiCheck(clientStatus, "Create MIDI client")
        lock.lock(); defer { lock.unlock() }
        listed = enumerate()
        return listed.sorted { $0.key < $1.key }.map { ProbePort(id: $0.key, label: $0.value.label) }
    }

    func monitorPorts() throws -> [ProbePort] {
        try midiCheck(clientStatus, "Create MIDI client")
        return monitorSources().sorted { $0.key < $1.key }.map { ProbePort(id: $0.key, label: "\(midiName($0.value)) · #\($0.value)") }
    }
    private func monitorSources() -> [Int32: MIDIEndpointRef] {
        var sources: [Int32: MIDIEndpointRef] = [:]
        for i in 0..<MIDIGetNumberOfDevices() {
            let device = MIDIGetDevice(i)
            var offline: Int32 = 0
            if MIDIObjectGetIntegerProperty(device, kMIDIPropertyOffline, &offline) == noErr && offline != 0 { continue }
            for e in 0..<MIDIDeviceGetNumberOfEntities(device) {
                let entity = MIDIDeviceGetEntity(device, e)
                for s in 0..<MIDIEntityGetNumberOfSources(entity) {
                    let source = MIDIEntityGetSource(entity, s)
                    if source != 0 { sources[Int32(bitPattern: source)] = source }
                }
            }
        }
        return sources
    }
    func startMonitor(ids: [KotlinInt]) throws {
        monitorLifecycle.lock(); defer { monitorLifecycle.unlock() }
        stopMonitor()
        let sources = monitorSources()
        let chosen = ids.compactMap { sources[$0.int32Value] }
        if chosen.isEmpty { return }
        monitorLock.lock(); monitoring = true; let epoch = monitorGeneration; monitorLock.unlock()
        midiLog.add(source: "Logger", bytes: kotlinBytes([]), annotation: "START", received: PlatformKt.monotonicTimeMillis() * 1_000_000, timestamp: 0)
        do {
            for source in chosen {
                let label = "\(midiName(source)) · #\(source)"
                var receivedNs: Int64 = 0
                var timestampNs: Int64 = 0
                let parser = MidiStreamParser { [weak self] bytes, annotation in
                    self?.midiLog.add(source: label, bytes: bytes, annotation: annotation, received: receivedNs, timestamp: timestampNs)
                    return KotlinUnit()
                }
                monitorLock.lock(); monitorParsers.append(parser); monitorLock.unlock()
                var input = MIDIPortRef()
                try midiCheck(MIDIInputPortCreateWithBlock(client, "FM1 monitor" as CFString, &input) { [weak self] packets, _ in
                    guard let self else { return }
                    let offset = MemoryLayout<MIDIPacketList>.offset(of: \.packet)!
                    let dataOffset = MemoryLayout<MIDIPacket>.offset(of: \.data)!
                    var packet = UnsafeRawPointer(packets).advanced(by: offset).assumingMemoryBound(to: MIDIPacket.self)
                    for _ in 0..<packets.pointee.numPackets {
                        let count = Int(packet.pointee.length)
                        let data = UnsafeRawPointer(packet).advanced(by: dataOffset).assumingMemoryBound(to: UInt8.self)
                        var at = 0
                        while at < count {
                            let n = min(4096, count - at)
                            self.monitorLock.lock()
                            let accept = self.monitoring && self.monitorGeneration == epoch && self.pendingPackets < 256
                            let lost = self.lostBySource[source] ?? 0
                            if accept {
                                self.pendingPackets += 1; self.lostBySource[source] = 0
                                let copied = Array(UnsafeBufferPointer(start: data.advanced(by: at), count: n))
                                let received = PlatformKt.monotonicTimeMillis() * 1_000_000
                                var timebase = mach_timebase_info_data_t()
                                mach_timebase_info(&timebase)
                                let stamp = Int64(Double(packet.pointee.timeStamp) * Double(timebase.numer) / Double(max(timebase.denom, 1)))
                                self.decodeQueue.async {
                                    if lost > 0 { parser.reset(); self.midiLog.loss(source: label, bytes: lost) }
                                    receivedNs = received; timestampNs = stamp
                                    parser.accept(bytes: kotlinBytes(copied))
                                    self.monitorLock.lock(); self.pendingPackets -= 1; self.monitorLock.unlock()
                                }
                            } else if self.monitoring && self.monitorGeneration == epoch { self.lostBySource[source] = lost + Int64(n) }
                            self.monitorLock.unlock()
                            at += n
                        }
                        packet = UnsafePointer(MIDIPacketNext(packet))
                    }
                }, "Open passive MIDI input")
                do { try midiCheck(MIDIPortConnectSource(input, source, nil), "Connect monitor") }
                catch { MIDIPortDispose(input); throw error }
                monitorLock.lock(); monitorInputs.append((input, source)); monitorLock.unlock()
            }
        } catch { stopMonitor(); throw error }
    }
    func stopMonitor() {
        monitorLifecycle.lock(); defer { monitorLifecycle.unlock() }
        monitorLock.lock(); let wasRunning = monitoring; monitoring = false; monitorGeneration += 1
        let inputs = monitorInputs; monitorInputs.removeAll(); let parsers = monitorParsers; monitorParsers.removeAll(); let losses = lostBySource; lostBySource.removeAll(); monitorLock.unlock()
        for (input, source) in inputs { MIDIPortDisconnectSource(input, source); MIDIPortDispose(input) }
        let log = midiLog
        decodeQueue.async {
            for parser in parsers { parser.finish() }
            for (source, count) in losses where count > 0 { log.loss(source: "MIDI #\(source)", bytes: count) }
            if wasRunning { log.add(source: "Logger", bytes: kotlinBytes([]), annotation: "STOP", received: PlatformKt.monotonicTimeMillis() * 1_000_000, timestamp: 0) }
        }
        // Decoding runs off Core MIDI callbacks and lifecycle threads; FIFO drains before restart data.
    }

    func open(id: Int32) throws -> any MidiConnection {
        try midiCheck(clientStatus, "Create MIDI client")
        lock.lock(); defer { lock.unlock() }
        guard let pair = listed[id], enumerate()[id] == pair else {
            throw NSError(domain: "FM1.CoreMIDI", code: 1, userInfo: [NSLocalizedDescriptionKey: "MIDI port changed. Refresh and select it again."])
        }
        return try CoreMidiConnection(client: client, id: id, source: pair.source, destination: pair.destination) { [weak self] in
            guard let self else { return false }
            self.lock.lock(); defer { self.lock.unlock() }
            return self.enumerate()[id] == pair
        }
    }
}

private final class CoreMidiConnection: NSObject, MidiConnection {
    let endpoint: Endpoint
    private let source: MIDIEndpointRef
    private let destination: MIDIEndpointRef
    private let alive: () -> Bool
    private var input = MIDIPortRef()
    private var output = MIDIPortRef()
    private let condition = NSCondition()
    private var queue: [KotlinByteArray] = []
    private var failure: Error?
    private var closed = false
    // Only Core MIDI's serial input callback accesses the assembler.
    private lazy var assembler = SysExAssembler { [weak self] packet in
        self?.enqueue(packet)
        return KotlinUnit()
    }

    init(client: MIDIClientRef, id: Int32, source: MIDIEndpointRef, destination: MIDIEndpointRef, alive: @escaping () -> Bool) throws {
        self.alive = alive
        self.source = source
        self.destination = destination
        self.endpoint = Endpoint(id: id, usbPath: "coremidi:\(source):\(destination)")
        super.init()
        do {
            try midiCheck(MIDIOutputPortCreate(client, "FM1 output" as CFString, &output), "Create output")
            try midiCheck(MIDIInputPortCreateWithBlock(client, "FM1 input" as CFString, &input) { [weak self] packets, _ in
                self?.receivePackets(packets)
            }, "Create input")
            try midiCheck(MIDIPortConnectSource(input, source, nil), "Connect input")
        } catch { try? close(); throw error }
    }

    private func enqueue(_ packet: KotlinByteArray) {
        condition.lock(); defer { condition.unlock() }
        guard !closed else { return }
        if queue.count == 128 {
            failure = NSError(domain: "FM1.CoreMIDI", code: 2, userInfo: [NSLocalizedDescriptionKey: "MIDI receive buffer overflow"])
        } else { queue.append(packet) }
        condition.broadcast()
    }

    private func receivePackets(_ packets: UnsafePointer<MIDIPacketList>) {
        let packetOffset = MemoryLayout<MIDIPacketList>.offset(of: \.packet)!
        let dataOffset = MemoryLayout<MIDIPacket>.offset(of: \.data)!
        var packet = UnsafeRawPointer(packets).advanced(by: packetOffset).assumingMemoryBound(to: MIDIPacket.self)
        for _ in 0..<packets.pointee.numPackets {
            let count = Int(packet.pointee.length)
            let bytes = UnsafeRawPointer(packet).advanced(by: dataOffset).assumingMemoryBound(to: UInt8.self)
            let copied = kotlinBytes(Array(UnsafeBufferPointer(start: bytes, count: count)))
            do { try assembler.accept(bytes: copied, offset: 0, count: Int32(count)) }
            catch {
                condition.lock(); failure = error; condition.broadcast(); condition.unlock()
            }
            packet = UnsafePointer(MIDIPacketNext(packet))
        }
    }

    func send(bytes: KotlinByteArray) throws {
        condition.lock(); defer { condition.unlock() }
        if let failure { throw failure }
        guard alive() else { throw nativeError("Selected MIDI instrument disconnected") }
        guard !closed else { throw NSError(domain: "FM1.CoreMIDI", code: 3, userInfo: [NSLocalizedDescriptionKey: "MIDI connection closed"]) }
        let payload = (0..<Int(bytes.size)).map { UInt8(bitPattern: bytes.get(index: Int32($0))) }
        let size = MemoryLayout<MIDIPacketList>.size + payload.count + 64
        let storage = UnsafeMutableRawPointer.allocate(byteCount: size, alignment: MemoryLayout<MIDIPacketList>.alignment)
        defer { storage.deallocate() }
        let list = storage.bindMemory(to: MIDIPacketList.self, capacity: 1)
        let first = MIDIPacketListInit(list)
        try payload.withUnsafeBufferPointer { buffer in
            guard MIDIPacketListAdd(list, size, first, 0, payload.count, buffer.baseAddress!) != nil else {
                throw NSError(domain: "FM1.CoreMIDI", code: 4, userInfo: [NSLocalizedDescriptionKey: "MIDI packet exceeds output capacity"])
            }
            try midiCheck(MIDISend(output, destination, list), "Send SysEx")
        }
    }

    func receive(timeoutMs: Int64) throws -> KotlinByteArray? {
        condition.lock(); defer { condition.unlock() }
        let deadline = Date(timeIntervalSinceNow: Double(timeoutMs) / 1000)
        while queue.isEmpty && failure == nil && !closed {
            guard alive() else { throw nativeError("Selected MIDI instrument disconnected") }
            if Date() >= deadline { break }
            condition.wait(until: min(deadline, Date(timeIntervalSinceNow: 0.2)))
        }
        if let failure { throw failure }
        guard alive() else { throw nativeError("Selected MIDI instrument disconnected") }
        guard !closed else { throw NSError(domain: "FM1.CoreMIDI", code: 3, userInfo: [NSLocalizedDescriptionKey: "MIDI connection closed"]) }
        return queue.isEmpty ? nil : queue.removeFirst()
    }

    func close() throws {
        condition.lock()
        if closed { condition.unlock(); return }
        closed = true
        queue.removeAll()
        condition.broadcast()
        condition.unlock()
        if input != 0 { MIDIPortDisconnectSource(input, source); MIDIPortDispose(input); input = 0 }
        if output != 0 { MIDIPortDispose(output); output = 0 }
    }
    deinit { try? close() }
}
