package pl.ekim.fm1_flasher

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque

class PortableAppBackendTest {
    private class Storage : AppStorage {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(path: String, limit: Int) = files[path]?.also { check(it.size <= limit) }?.copyOf()
        override fun write(path: String, bytes: ByteArray) { files[path] = bytes.copyOf() }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = files.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { it.removePrefix("$path/") }
    }
    private class Midi : ProbeBackend, PassiveMidiBackend {
        var current = 1
        val sent = mutableMapOf<Int, MutableList<ByteArray>>()
        override val midiLog = MidiLog()
        override var monitorRunning = false
        override fun devices() = listOf(ProbePort(current, "Physical FM-1 endpoint $current", 0))
        override fun open(port: ProbePort): MidiConnection {
            val id = port.id
            check(port in devices())
            return object : MidiConnection {
                override val endpoint = Endpoint(id, "native:$id", port.generation)
                val queue = ArrayDeque<ByteArray>()
                var closed = false
                var upgraded = false
                override fun send(bytes: ByteArray) {
                    check(!closed); sent.getOrPut(id) { mutableListOf() } += bytes
                    if (bytes.contentEquals(Protocol.identityQuery)) queue += identity(when(id) { 1 -> "FM-1_015"; 2 -> "ota-FM-1_015"; else -> "FM-1_900" })
                    if (bytes.contentEquals(Protocol.upgrade)) { upgraded = true; queue += readRequest(0, 4); queue += readRequest(if (id == 1) Protocol.VERIFIED else Protocol.FINISHED, 8) }
                }
                override fun receive(timeoutMs: Long) = queue.pollFirst()
                override fun close() { if (!closed && upgraded) current++; closed = true }
            }
        }
        override fun monitorPorts() = devices()
        override fun startMonitor(ids: List<Int>) { monitorRunning = ids.isNotEmpty() }
        override fun stopMonitor() { monitorRunning = false }
    }
    private class Host : AppHost {
        override val systemLanguage = "pl-PL"
        var flashActive = false
        override fun setAppearance(theme: String) = Unit
        override fun setFlashing(active: Boolean) { flashActive = active }
        override fun document(name: String) = ""
        override fun openLink(url: String) = Unit
        override fun exportText(name: String, text: String) = Unit
    }
    @Test fun sourceColorPersistsOfflineWithoutProviderRequests() = runBlocking {
        val storage = Storage(); val midi = Midi(); var requests = 0
        val http = object : HttpClient { override fun get(url: String, limit: Int, artworkOnly: Boolean): ByteArray { requests++; error("Network must not be used") } }
        FirmwareRepository(storage, http, ::epochMillis).saveSettings(LibrarySettings(offline = true))
        val backend = PortableAppBackend(midi, midi, storage, http, Host())
        suspend fun idle(instance: PortableAppBackend) = withTimeout(5000) { instance.state.first { it.initialized && !it.busy } }
        idle(backend); assertFalse(backend.state.value.sourceManagementAvailable)
        backend.perform(AppAction(AppActionKind.SOURCE_COLOR, CatalogSource.HUB, "BLUE")); idle(backend)
        assertEquals(SourceColor.BLUE, backend.state.value.sourceColors[CatalogSource.HUB])
        val restored = PortableAppBackend(midi, midi, storage, http, Host()); idle(restored)
        assertEquals(SourceColor.BLUE, restored.state.value.sourceColors[CatalogSource.HUB])
        restored.perform(AppAction(AppActionKind.SOURCE_COLOR, CatalogSource.HUB)); idle(restored)
        assertTrue(restored.state.value.sourceColors.isEmpty()); assertEquals(0, requests)
    }
    @Test fun offlineFullFlashRequiresBothExplicitReconnectsAndPersistsConfirmedJournal() = fullFlash("new", false)
    @Test fun unknownPackageRequiresExplicitForceAndStillVerifiesBothReconnects() = fullFlash("unknown", true)
    private fun fullFlash(chip: String, forceUnknown: Boolean) = runBlocking {
        val storage = Storage(); val midi = Midi(); val host = Host(); var requests = 0
        val http = object : HttpClient { override fun get(url: String, limit: Int, artworkOnly: Boolean): ByteArray { requests++; error("Offline") } }
        val repository = FirmwareRepository(storage, http, ::epochMillis)
        val raw = testPackage(); val hash = FirmwarePackage.sha256(raw)
        val entry = localEntry(raw).copy(chip = chip)
        repository.save(entry, raw); repository.saveSettings(LibrarySettings(offline = true))
        val backend = PortableAppBackend(midi, midi, storage, http, host)
        suspend fun idle() = withTimeout(5000) { backend.state.first { it.initialized && !it.busy } }
        idle(); assertEquals("pl", backend.state.value.language)
        backend.perform(AppAction(AppActionKind.USE, entry.id)); idle()
        backend.perform(AppAction(AppActionKind.CONNECT, "1:0")); idle()
        if (forceUnknown) {
            backend.perform(AppAction(AppActionKind.FLASH, "1:0", hash)); idle()
            assertEquals(msg("local_chip_unsupported"), backend.state.value.status)
            assertFalse(midi.sent.values.flatten().any { it.contentEquals(Protocol.upgrade) })
        }
        backend.perform(AppAction(AppActionKind.FLASH, "1:0", hash, forceUnknown = forceUnknown))
        withTimeout(15000) { backend.state.first { it.awaitingPort && it.candidates.any { p -> p.id == "2:0" } } }
        assertTrue(host.flashActive)
        assertFalse(midi.sent[2].orEmpty().any { it.contentEquals(Protocol.upgrade) })
        backend.perform(AppAction(AppActionKind.RECONNECT, "2:0"))
        withTimeout(15000) { backend.state.first { it.awaitingPort && it.candidates.any { p -> p.id == "3:0" } } }
        backend.perform(AppAction(AppActionKind.RECONNECT, "3:0")); idle()
        assertEquals(FirmwareIdentity("FM-1", "900"), backend.state.value.identity)
        assertEquals(100, backend.state.value.progress)
        assertFalse(host.flashActive); assertEquals(0, requests)
        assertTrue(storage.files.getValue("flash-session.json").decodeToString().contains("\"confirmed\""))
        assertFalse(midi.sent[3].orEmpty().any { it.contentEquals(Protocol.upgrade) })
    }
}
