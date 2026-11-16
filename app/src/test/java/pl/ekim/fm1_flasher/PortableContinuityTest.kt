package pl.ekim.fm1_flasher

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class PortableContinuityTest {
    private class Storage : AppStorage {
        private val files = mutableMapOf<String, ByteArray>()
        @Synchronized override fun read(path: String, limit: Int) = files[path]?.also { check(it.size <= limit) }?.copyOf()
        @Synchronized override fun write(path: String, bytes: ByteArray) { files[path] = bytes.copyOf() }
        @Synchronized override fun remove(path: String) { files.remove(path) }
        @Synchronized override fun size(path: String) = files[path]?.size?.toLong()
        @Synchronized override fun list(path: String) = files.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { it.removePrefix("$path/") }
    }

    /** Events change continuity without changing the endpoint number, name or firmware version. */
    private class Midi : ProbeBackend, PassiveMidiBackend {
        val generation = AtomicLong(1)
        @Volatile var stage = 0
        @Volatile var beforeOpen: (() -> Unit)? = null
        @Volatile var afterClose: (() -> Unit)? = null
        @Volatile var afterIdentity: (() -> Unit)? = null
        val opened = CopyOnWriteArrayList<ProbePort>()
        val sent = CopyOnWriteArrayList<Pair<Long, ByteArray>>()
        val rejectedLinks = AtomicLong()
        override val midiLog = MidiLog()
        override val monitorRunning = false
        override fun devices() = listOf(ProbePort(7, "Same physical endpoint label", generation.get()))
        override fun open(port: ProbePort): MidiConnection {
            beforeOpen?.also { beforeOpen = null }?.invoke()
            check(port in devices()) { "Stale port selection" }
            opened += port
            return object : MidiConnection {
                override val endpoint = Endpoint(port.id, "coremidi:7:7", port.generation)
                private val queue = ArrayDeque<ByteArray>()
                private var closed = false
                private var upgraded = false
                private fun checkAlive() {
                    if (port !in devices()) { rejectedLinks.incrementAndGet(); error("Stale open link") }
                    check(!closed)
                }
                override fun send(bytes: ByteArray) {
                    checkAlive()
                    sent += port.generation to bytes.copyOf()
                    if (bytes.contentEquals(Protocol.identityQuery)) queue += identity(when (stage) {
                        0 -> "FM-1_015"; 1 -> "ota-FM-1_015"; else -> "FM-1_900"
                    })
                    if (bytes.contentEquals(Protocol.upgrade)) {
                        upgraded = true
                        queue += readRequest(0, 4)
                        queue += readRequest(if (stage == 0) Protocol.VERIFIED else Protocol.FINISHED, 8)
                    }
                }
                override fun receive(timeoutMs: Long): ByteArray? {
                    checkAlive()
                    return queue.pollFirst()?.also { bytes ->
                        if (Protocol.identity(bytes) != null) afterIdentity?.also { afterIdentity = null }?.invoke()
                    }
                }
                override fun close() {
                    if (closed) return
                    closed = true
                    if (upgraded) { stage++; generation.incrementAndGet() }
                    afterClose?.also { afterClose = null }?.invoke()
                }
            }
        }
        override fun monitorPorts() = devices()
        override fun startMonitor(ids: List<Int>) = Unit
        override fun stopMonitor() = Unit
    }

    private class Fixture {
        val midi = Midi()
        val storage = Storage()
        val http = object : HttpClient {
            override fun get(url: String, limit: Int, artworkOnly: Boolean): ByteArray = error("No network in continuity tests")
        }
        val entry = localEntry().copy(chip = "old")
        val repository = FirmwareRepository(storage, http, ::epochMillis).apply {
            save(entry, testPackage()); saveSettings(LibrarySettings(offline = true))
        }
        val backend = PortableAppBackend(midi, midi, storage, http, object : AppHost {
            override val systemLanguage = "en"
            override fun openLink(url: String) = Unit
            override fun exportText(name: String, text: String) = Unit
            override fun document(name: String) = ""
            override fun setAppearance(theme: String) = Unit
            override fun setFlashing(active: Boolean) = Unit
        })
        suspend fun idle() = withTimeout(5000) { backend.state.first { it.initialized && !it.busy } }
        suspend fun action(kind: AppActionKind, id: String = "", value: String = "") {
            backend.perform(AppAction(kind, id, value)); idle()
        }
        suspend fun connect() {
            idle()
            action(AppActionKind.USE, entry.id)
            action(AppActionKind.CONNECT, "7:1")
            action(AppActionKind.CHIP, "OLD")
            assertEquals(FlashChip.OLD, backend.state.value.chip)
        }
        fun assertUntrusted() {
            assertNull(backend.state.value.target)
            assertNull(backend.state.value.identity)
            assertEquals(FlashChip.UNKNOWN, backend.state.value.chip)
        }
        fun assertNoUpgrade() = assertFalse(midi.sent.any { it.second.contentEquals(Protocol.upgrade) })
        suspend fun candidate(generation: Long) = withTimeout(15000) {
            backend.state.first { it.awaitingPort && it.candidates.singleOrNull()?.id == "7:$generation" }
        }
    }

    @Test fun refreshKeepsContinuousPortButRevokesRecycledIdAndName() = runBlocking {
        val f = Fixture(); f.connect()
        f.action(AppActionKind.PORTS_REFRESH)
        assertEquals(FlashChip.OLD, f.backend.state.value.chip)
        assertEquals(FirmwareIdentity("FM-1", "015"), f.backend.state.value.identity)
        f.midi.generation.incrementAndGet()
        f.action(AppActionKind.PORTS_REFRESH); f.assertUntrusted()
        assertEquals("7:2", f.backend.state.value.ports.single().id)
        val opened = f.midi.opened.size
        f.action(AppActionKind.CONNECT, "7:1")
        f.assertUntrusted(); assertEquals(opened, f.midi.opened.size)
        f.action(AppActionKind.CONNECT, "7:2")
        assertEquals("7:2", f.backend.state.value.target?.id)
        assertEquals(FirmwareIdentity("FM-1", "015"), f.backend.state.value.identity)
        assertEquals(FlashChip.UNKNOWN, f.backend.state.value.chip)
    }

    @Test fun chipSelectionRefreshesContinuityBeforeTrustingDeclaration() = runBlocking {
        val f = Fixture(); f.connect()
        f.midi.generation.incrementAndGet()
        f.action(AppActionKind.CHIP, "NEW")
        f.assertUntrusted()
    }

    @Test fun flashCannotUseSnapshotFromBeforeGenerationChange() = runBlocking {
        val f = Fixture(); f.connect()
        f.midi.generation.incrementAndGet()
        val opened = f.midi.opened.size
        f.action(AppActionKind.FLASH, "7:1", f.entry.sha256)
        f.assertUntrusted(); f.assertNoUpgrade()
        assertEquals(opened, f.midi.opened.size)
        assertNull(f.storage.read("flash-session.json", 128000))
    }

    @Test fun generationChangeBetweenRefreshAndOpenRejectsOldSelection() = runBlocking {
        val f = Fixture(); f.connect()
        f.midi.beforeOpen = { f.midi.generation.incrementAndGet() }
        val opened = f.midi.opened.size
        f.action(AppActionKind.FLASH, "7:1", f.entry.sha256)
        f.assertUntrusted(); f.assertNoUpgrade()
        assertEquals(opened, f.midi.opened.size)
        assertFalse(f.storage.read("flash-session.json", 128000)!!.decodeToString().contains("confirmed"))
    }

    @Test fun identityReadCannotPublishTrustAfterContinuityIsLostOnClose() = runBlocking {
        val f = Fixture(); f.idle()
        f.midi.afterClose = { f.midi.generation.incrementAndGet() }
        f.action(AppActionKind.CONNECT, "7:1")
        f.assertUntrusted(); f.assertNoUpgrade()
    }

    @Test fun alreadyOpenLinkCannotUpgradeReplacementWithIdenticalFirmwareIdentity() = runBlocking {
        val f = Fixture(); f.connect()
        f.midi.afterIdentity = { f.midi.generation.incrementAndGet() }
        f.action(AppActionKind.FLASH, "7:1", f.entry.sha256)
        f.assertUntrusted(); f.assertNoUpgrade()
        assertEquals(1, f.midi.rejectedLinks.get())
        assertEquals(1, f.midi.opened.last().generation)
    }

    @Test fun continuousFreshReconnectSelectionsFinishConfirmed() = runBlocking {
        val f = Fixture(); f.connect()
        f.backend.perform(AppAction(AppActionKind.FLASH, "7:1", f.entry.sha256))
        f.candidate(2)
        f.assertUntrusted()
        assertFalse(f.midi.opened.any { it.generation == 2L })
        f.backend.perform(AppAction(AppActionKind.RECONNECT, "7:2"))
        f.candidate(3)
        assertFalse(f.midi.opened.any { it.generation == 3L })
        f.backend.perform(AppAction(AppActionKind.RECONNECT, "7:3")); f.idle()
        assertEquals(listOf(1L, 1L, 2L, 3L), f.midi.opened.map { it.generation })
        assertEquals(FirmwareIdentity("FM-1", "900"), f.backend.state.value.identity)
        assertEquals(100, f.backend.state.value.progress)
        assertTrue(f.storage.read("flash-session.json", 128000)!!.decodeToString().contains("confirmed"))
    }

    @Test fun sameIdReconnectsRequireFreshExplicitSelectionsAndFinishConfirmed() = runBlocking {
        val f = Fixture(); f.connect()
        f.backend.perform(AppAction(AppActionKind.FLASH, "7:1", f.entry.sha256))
        f.candidate(2)
        f.assertUntrusted()
        assertFalse(f.midi.opened.any { it.generation == 2L })
        // The displayed candidate is replaced before its selection reaches the flash worker.
        f.midi.generation.incrementAndGet()
        f.backend.perform(AppAction(AppActionKind.RECONNECT, "7:2"))
        val outcome = withTimeout(15000) {
            f.backend.state.first { !it.busy || (it.awaitingPort && it.candidates.singleOrNull()?.id == "7:3") }
        }
        if (!outcome.busy) {
            // Selection may win the old snapshot race; final continuity validation then aborts safely.
            assertFalse(f.midi.opened.any { it.generation >= 2L })
            assertFalse(f.storage.read("flash-session.json", 128000)!!.decodeToString().contains("confirmed"))
            return@runBlocking
        }
        f.backend.perform(AppAction(AppActionKind.RECONNECT, "7:2"))
        assertFalse(f.midi.opened.any { it.generation >= 2L })
        f.backend.perform(AppAction(AppActionKind.RECONNECT, "7:3"))
        f.candidate(4)
        assertFalse(f.midi.opened.any { it.generation == 4L })
        f.backend.perform(AppAction(AppActionKind.RECONNECT, "7:4")); f.idle()
        assertEquals(listOf(1L, 1L, 3L, 4L), f.midi.opened.map { it.generation })
        assertEquals(FirmwareIdentity("FM-1", "900"), f.backend.state.value.identity)
        assertEquals(FlashChip.UNKNOWN, f.backend.state.value.chip)
        assertEquals("7:4", f.backend.state.value.target?.id)
        assertEquals(100, f.backend.state.value.progress)
        assertTrue(f.storage.read("flash-session.json", 128000)!!.decodeToString().contains("confirmed"))
    }
}
