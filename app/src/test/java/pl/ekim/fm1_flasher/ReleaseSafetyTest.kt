package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.ArrayDeque

class ReleaseSafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private class Link(override val endpoint: Endpoint, val name: String, val requests: List<ByteArray>) : MidiConnection {
        val sent = mutableListOf<ByteArray>()
        val queued = ArrayDeque<ByteArray>()
        override fun send(bytes: ByteArray) {
            sent += bytes
            if (bytes.contentEquals(Protocol.identityQuery)) queued += identity(name)
            if (bytes.contentEquals(Protocol.upgrade)) queued.addAll(requests)
        }
        override fun receive(timeoutMs: Long) = queued.pollFirst()
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
        assertThrows(IllegalStateException::class.java) { session.flash(image(), initial, FirmwareIdentity("FM-1", "015")) }
        assertEquals(1, normal.sent.size)
        assertArrayEquals(Protocol.identityQuery, normal.sent.single())
    }
    @Test fun expiredReconnectReceivesNoCommands() {
        val initial = Link(Endpoint(1, "a"), "FM-1_015", listOf(readRequest(0, 4), readRequest(Protocol.VERIFIED, 8)))
        val loader = Link(Endpoint(2, "b"), "ota-FM-1_015", emptyList())
        var time = 0L
        val session = FlashSession({ _, _ -> time += FlashSession.MAX_SESSION_MS; loader }, { _, _ -> }, { time += it }, { time++ })
        assertThrows(IllegalStateException::class.java) { session.flash(image(), initial, FirmwareIdentity("FM-1", "015")) }
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
    @Test fun damagedSettingsKeepPackagesAndDefaultToOffline() {
        val dir = temp.newFolder(); val library = LocalLibrary(dir)
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        File(dir, "settings.json").writeText("{invalid")
        assertEquals(LibrarySettings(offline = true), library.settings())
        assertTrue(library.settingsRepaired)
        assertTrue(File(dir, "settings.invalid").exists())
        assertEquals(entry, library.list().single().entry)
        assertEquals(entry.sha256, library.load(entry.id, null).second.sha256)
        assertTrue(LocalLibrary(dir).settings().offline)
    }
    @Test fun previewEvictionNeverDeletesOtherFiles() {
        val dir = temp.newFolder()
        val first = File(dir, "a.img").apply { writeBytes(ByteArray(20)); setLastModified(1000) }
        val second = File(dir, "b.img").apply { writeBytes(ByteArray(20)); setLastModified(2000) }
        val firmware = File(dir, "keep.fwsc").apply { writeBytes(ByteArray(30)) }
        trimPreviewCache(dir, 20)
        assertFalse(first.exists()); assertTrue(second.exists()); assertTrue(firmware.exists())
    }
    @Test fun unresolvedJournalSurvivesRestartAndNeverClaimsSuccessFromCorruption() {
        val file = File(temp.newFolder(), "session.json")
        val journal = FlashJournal(file)
        assertFalse(journal.unresolved())
        journal.record("a".repeat(64), "device", "writing")
        assertTrue(FlashJournal(file).unresolved())
        journal.record("a".repeat(64), "device", "finished", "confirmed")
        assertFalse(FlashJournal(file).unresolved())
        file.writeText("{")
        assertTrue(journal.unresolved())
    }
    @Test fun exportWriterMatchesExistingFormatAndSnapshotsSkipUnchangedRevision() {
        val log = MidiLog()
        log.add("A", byteArrayOf(0x90.toByte(), 60, 100))
        val snapshot = log.snapshot()
        assertNull(log.snapshotIfChanged(snapshot.revision))
        val text = StringBuilder(); snapshot.writeTo(text)
        assertEquals(snapshot.export(), text.toString())
        log.clear(); assertNotNull(log.snapshotIfChanged(snapshot.revision))
    }
}
