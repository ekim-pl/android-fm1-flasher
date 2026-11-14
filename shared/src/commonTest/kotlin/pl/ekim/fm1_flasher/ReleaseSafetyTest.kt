package pl.ekim.fm1_flasher

import kotlin.test.*

class ReleaseSafetyTest {
    private class Link(override val endpoint: Endpoint, val name: String, val requests: List<ByteArray>) : MidiConnection {
        val sent = mutableListOf<ByteArray>()
        val queued = ArrayDeque<ByteArray>()
        override fun send(bytes: ByteArray) {
            sent += bytes
            if (bytes.contentEquals(Protocol.identityQuery)) queued += identity(name)
            if (bytes.contentEquals(Protocol.upgrade)) queued.addAll(requests)
        }
        override fun receive(timeoutMs: Long) = queued.removeFirstOrNull()
        override fun close() = Unit
    }
    private fun image(): FirmwarePackage {
        val raw = testPackage()
        return FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), "FM-1_900")
    }
    @Test fun normalModeReconnectReceivesNoUpgradeOrFirmware() {
        val initial = Link(Endpoint(1, "a"), "FM-1_015", listOf(readRequest(0, 4), readRequest(Protocol.VERIFIED, 8)))
        val normal = Link(Endpoint(2, "b"), "FM-1_015", emptyList())
        var time = 0L
        val session = FlashSession({ _, _ -> normal }, { _, _ -> }, { time += it }, { time++ })
        assertFailsWith<IllegalStateException> { session.flash(image(), initial, FirmwareIdentity("FM-1", "015")) }
        assertEquals(1, normal.sent.size)
        assertContentEquals(Protocol.identityQuery, normal.sent.single())
    }
    @Test fun expiredReconnectReceivesNoCommands() {
        val initial = Link(Endpoint(1, "a"), "FM-1_015", listOf(readRequest(0, 4), readRequest(Protocol.VERIFIED, 8)))
        val loader = Link(Endpoint(2, "b"), "ota-FM-1_015", emptyList())
        var time = 0L
        val session = FlashSession({ _, _ -> time += FlashSession.MAX_SESSION_MS; loader }, { _, _ -> }, { time += it }, { time++ })
        assertFailsWith<IllegalStateException> { session.flash(image(), initial, FirmwareIdentity("FM-1", "015")) }
        assertTrue(loader.sent.isEmpty())
    }
    @Test fun duplicateAndOverlappingReadsDoNotInflateProgress() {
        val image = image()
        val loader = Link(Endpoint(1, "a"), "ota-FM-1_015", listOf(readRequest(0, 512), readRequest(0, 512), readRequest(256, 512), readRequest(Protocol.FINISHED, 8)))
        val final = Link(Endpoint(2, "b"), "FM-1_900", emptyList())
        var time = 0L
        val progress = mutableListOf<Int>()
        FlashSession({ _, _ -> final }, { _, n -> progress += n }, { time += it }, { time++ })
            .flash(image, loader, FirmwareIdentity("ota-FM-1", "015"))
        assertEquals(listOf(0, 512 * 100 / image.logical.size, 768 * 100 / image.logical.size, 100), progress)
    }
}
