package pl.ekim.fm1_flasher

import kotlin.test.*


class FlashSessionTest {
    private fun image(): FirmwarePackage {
        val raw = testPackage()
        return FirmwarePackage.parse(raw, FirmwarePackage.sha256(raw), "FM-1_900")
    }

    private class Link(override val endpoint: Endpoint, val name: String, private val terminal: Long?) : MidiConnection {
        val sent = mutableListOf<ByteArray>()
        val queued = ArrayDeque<ByteArray>()
        var closed = false
        override fun send(bytes: ByteArray) {
            check(!closed)
            sent += bytes
            if (bytes.contentEquals(Protocol.identityQuery)) queued += identity(name)
            if (bytes.contentEquals(Protocol.upgrade) && terminal != null) {
                queued += readRequest(0, 4)
                queued += readRequest(terminal, 8)
            }
        }
        override fun receive(timeoutMs: Long) = queued.removeFirstOrNull()
        override fun close() { closed = true }
    }

    private fun session(reconnect: (Endpoint, Boolean) -> MidiConnection, reports: MutableList<Int> = mutableListOf()): FlashSession {
        var clock = 0L
        return FlashSession(reconnect, { _, percent -> reports += percent }, { clock += it }, { clock++ })
    }

    @Test fun updatesOnlyChosenMiddleDeviceThenVerifiesExactIdentity() {
        val sibling1 = Link(Endpoint(1, "usb/2"), "FM-1_015", null)
        val target = Link(Endpoint(2, "usb/3"), "FM-1_015", Protocol.VERIFIED)
        val sibling3 = Link(Endpoint(3, "usb/4"), "FM-1_015", null)
        val loader = Link(Endpoint(4, "usb/5"), "ota-FM-1_015", Protocol.FINISHED)
        val installed = Link(Endpoint(5, "usb/6"), "FM-1_900", null)
        val confirmations = mutableListOf<Pair<Endpoint, Boolean>>()
        val progress = mutableListOf<Int>()
        val engine = session({ previous, isLoader ->
            confirmations += previous to isLoader
            if (isLoader) loader else installed
        }, progress)
        assertEquals(FirmwareIdentity("FM-1", "900"), engine.flash(image(), target, FirmwareIdentity("FM-1", "015")))
        assertEquals(listOf(target.endpoint to true, loader.endpoint to false), confirmations)
        assertTrue(sibling1.sent.isEmpty() && sibling3.sent.isEmpty())
        assertEquals(1, target.sent.count { it.contentEquals(Protocol.upgrade) })
        assertEquals(1, loader.sent.count { it.contentEquals(Protocol.upgrade) })
        assertFalse(installed.sent.any { it.contentEquals(Protocol.upgrade) })
        assertTrue(target.closed && loader.closed && installed.closed)
        assertEquals(100, progress.last())
    }

    @Test fun failedContinuityValidationSendsZeroUpgradeCommands() {
        val target = Link(Endpoint(2, "usb/3"), "FM-1_015", Protocol.VERIFIED)
        var clock = 0L
        val engine = FlashSession({ _, _ -> error("Must not reconnect") }, { _, _ -> }, { clock += it }, { clock++ },
            validate = { error("Neighbour lost") })
        assertFailsWith<IllegalStateException> { engine.flash(image(), target, FirmwareIdentity("FM-1", "015")) }
        assertFalse(target.sent.any { it.contentEquals(Protocol.upgrade) })
        assertTrue(target.closed)
    }

    @Test fun wrongCurrentModelSendsNoUpgrade() {
        val target = Link(Endpoint(2, "usb/3"), "OTHER_015", Protocol.VERIFIED)
        assertFailsWith<IllegalStateException> {
            session({ _, _ -> error("Must not reconnect") }).flash(image(), target, FirmwareIdentity("OTHER", "015"))
        }
        assertFalse(target.sent.any { it.contentEquals(Protocol.upgrade) })
        assertTrue(target.closed)
    }

    @Test fun failedSecondStageVerificationCannotReportSuccess() {
        val loader = Link(Endpoint(2, "usb/3"), "ota-FM-1_015", Protocol.VERIFIED)
        val progress = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            session({ _, _ -> error("Must not reconnect") }, progress).flash(image(), loader, FirmwareIdentity("ota-FM-1", "015"))
        }
        assertFalse(100 in progress)
        assertTrue(loader.closed)
    }

    @Test fun outOfBoundsReadGetsNoDataResponse() {
        val link = object : MidiConnection {
            override val endpoint = Endpoint(2, "usb/3")
            val sent = mutableListOf<ByteArray>()
            var queries = 0
            override fun send(bytes: ByteArray) { sent += bytes }
            override fun receive(timeoutMs: Long) = if (queries++ == 0) identity("ota-FM-1_015") else readRequest(0xFFFFFFFF, 512)
            override fun close() = Unit
        }
        assertFailsWith<IllegalStateException> {
            session({ _, _ -> error("Must not reconnect") }).flash(image(), link, FirmwareIdentity("ota-FM-1", "015"))
        }
        assertEquals(2, link.sent.size) // Identity query and upgrade command only.
    }

    @Test fun wrongVersionAfterRebootIsNotSuccess() {
        val loader = Link(Endpoint(4, "usb/5"), "ota-FM-1_015", Protocol.FINISHED)
        val installed = Link(Endpoint(5, "usb/6"), "FM-1_015", null)
        val progress = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            session({ _, _ -> installed }, progress).flash(image(), loader, FirmwareIdentity("ota-FM-1", "015"))
        }
        assertFalse(100 in progress)
        assertTrue(installed.closed)
    }

    @Test fun reconnectedWrongModelReceivesNoFirmwareWrite() {
        val target = Link(Endpoint(2, "usb/3"), "FM-1_015", Protocol.VERIFIED)
        val other = Link(Endpoint(4, "usb/5"), "OTHER_015", Protocol.FINISHED)
        assertFailsWith<IllegalStateException> {
            session({ _, _ -> other }).flash(image(), target, FirmwareIdentity("FM-1", "015"))
        }
        assertFalse(other.sent.any { it.contentEquals(Protocol.upgrade) })
        assertTrue(target.closed && other.closed)
    }
    @Test fun mbootServesTwoPassesWithoutIntermediateReconnectOrFalseProgress() {
        val image = image()
        val queue = ArrayDeque<ByteArray>()
        val initial = object : MidiConnection {
            override val endpoint = Endpoint(1, "mboot")
            override fun send(bytes: ByteArray) {
                if (bytes.contentEquals(Protocol.identityQuery)) queue += identity("ota-FM-1_900")
                if (bytes.contentEquals(Protocol.upgrade)) {
                    repeat(2) {
                        for (at in image.logical.indices step 512) queue += readRequest(at.toLong(), minOf(512, image.logical.size - at))
                        queue += readRequest(0, 32)
                    }
                    queue += readRequest(Protocol.FINISHED, 8)
                }
            }
            override fun receive(timeoutMs: Long) = queue.removeFirstOrNull()
            override fun close() = Unit
        }
        var reconnects = 0
        val reports = mutableListOf<Int>()
        val result = session({ _, loader ->
            assertFalse(loader); reconnects++
            Link(Endpoint(2, "app"), "FM-1_900", null)
        }, reports).flash(image, initial, FirmwareIdentity("ota-FM-1", "900"))
        assertEquals(image.identity, result)
        assertEquals(1, reconnects)
        assertTrue(reports.dropLast(1).all { it == -1 })
        assertEquals(100, reports.last())
    }
}
