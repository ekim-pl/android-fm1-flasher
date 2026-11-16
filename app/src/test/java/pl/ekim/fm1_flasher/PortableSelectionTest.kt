package pl.ekim.fm1_flasher

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class PortableSelectionTest {
    private class Storage : AppStorage {
        val files = ConcurrentHashMap<String, ByteArray>()
        override fun read(path: String, limit: Int) = files[path]?.also { check(it.size <= limit) }?.copyOf()
        override fun write(path: String, bytes: ByteArray) { files[path] = bytes.copyOf() }
        override fun remove(path: String) { files.remove(path) }
        override fun size(path: String) = files[path]?.size?.toLong()
        override fun list(path: String) = files.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { it.removePrefix("$path/") }
    }
    private class Midi : ProbeBackend, PassiveMidiBackend {
        override val midiLog = MidiLog()
        override val monitorRunning = false
        override fun devices() = emptyList<ProbePort>()
        override fun open(port: ProbePort): MidiConnection = error("Selection must not open MIDI")
        override fun monitorPorts() = devices()
        override fun startMonitor(ids: List<Int>) = Unit
        override fun stopMonitor() = Unit
    }
    private class Host : AppHost {
        override val systemLanguage = "en"
        override fun setAppearance(theme: String) = Unit
        override fun setFlashing(active: Boolean) = Unit
        override fun document(name: String) = ""
        override fun openLink(url: String) = Unit
        override fun exportText(name: String, text: String) = Unit
    }
    private val raw = testPackage()
    private val entry = localEntry(raw)
    private val other = entry.copy(id = "demo-extra", name = "Other")
    private fun ids(entry: CatalogEntry) = listOf(entry.id, entry.key)
    private fun catalog(entries: List<CatalogEntry>) = """{"firmwares":[${entries.joinToString(",") {
        """{"id":"${it.id}","name":"${it.name}","version":"${it.version}","repo":"${it.repo}","sha256":"${it.sha256}","product":"${it.product}","tag":"${it.tag}","asset":"${it.assetId}","chip":"${it.chip}"}"""
    }}]}""".encodeToByteArray()

    private class Http(private val bytes: ByteArray) : HttpClient {
        var requests = 0
        override fun get(url: String, limit: Int, artworkOnly: Boolean): ByteArray {
            requests++
            check(url == "https://api.github.com/repos/example/demo/releases/tags/v1" ||
                url == "https://github.com/example/demo/releases/download/v1/demo.fwsc") { "Unexpected network: $url" }
            return (if (url.contains("api.github.com"))
                """{"assets":[{"id":123,"name":"demo.fwsc","browser_download_url":"https://github.com/example/demo/releases/download/v1/demo.fwsc"}]}""".encodeToByteArray()
            else bytes).also { check(it.size <= limit) }
        }
    }
    private fun repository(storage: Storage, http: HttpClient) = FirmwareRepository(storage, http, ::epochMillis)
    private fun backend(storage: Storage, http: HttpClient): PortableAppBackend {
        val midi = Midi()
        return PortableAppBackend(midi, midi, storage, http, Host())
    }
    private suspend fun idle(backend: PortableAppBackend) = withTimeout(5000) {
        backend.state.first { it.initialized && !it.busy }
    }
    private fun cache(storage: Storage, entries: List<CatalogEntry>) {
        storage.write("catalog.json", catalog(entries))
        storage.write("catalog-fetched.txt", epochMillis().toString().encodeToByteArray())
    }

    @Test fun usePersistsCanonicalKeyAndRestoresAfterRestartWithOrWithoutCatalog() = runBlocking {
        for (withCatalog in listOf(false, true)) for (id in ids(entry)) {
            val storage = Storage(); val http = Http(raw); val repository = repository(storage, http)
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(offline = true))
            if (withCatalog) cache(storage, listOf(entry))
            val backend = backend(storage, http); idle(backend)
            backend.perform(AppAction(AppActionKind.USE, id)); idle(backend)
            assertEquals(entry.key, backend.state.value.ready?.key)
            assertEquals(entry.key, repository.settings().selectedId)
            val restored = backend(storage, http); idle(restored)
            assertEquals(entry.key, restored.state.value.ready?.key)
            assertEquals(0, http.requests)
        }
    }

    @Test fun restoreAcceptsEitherSavedFormatWithOrWithoutCatalog() = runBlocking {
        for (withCatalog in listOf(false, true)) for (selected in ids(entry)) {
            val storage = Storage(); val http = Http(raw); val repository = repository(storage, http)
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(setOf(other.key), true, selected))
            if (withCatalog) cache(storage, listOf(entry))
            val backend = backend(storage, http); idle(backend)
            assertEquals(entry.key, backend.state.value.ready?.key)
            assertEquals(setOf(other.key), repository.settings().favorites)
            assertEquals(selected, repository.settings().selectedId)
            assertEquals(0, http.requests)
        }
    }

    @Test fun removeClearsReadyAndSavedSelectionAcrossBothFormats() = runBlocking {
        for (selected in ids(entry)) for (removed in ids(entry)) {
            val storage = Storage(); val http = Http(raw); val repository = repository(storage, http)
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(offline = true, selectedId = selected))
            cache(storage, listOf(entry))
            val backend = backend(storage, http); idle(backend)
            assertEquals(entry.key, backend.state.value.ready?.key)
            backend.perform(AppAction(AppActionKind.REMOVE, removed)); idle(backend)
            assertNull(backend.state.value.ready)
            assertNull(repository.settings().selectedId)
            assertTrue(backend.state.value.downloads.isEmpty())
            val restored = backend(storage, http); idle(restored)
            assertNull(restored.state.value.ready)
            assertEquals(0, http.requests)
        }
    }

    @Test fun removeKeepsAnotherReadyPackageAndItsSavedSelection() = runBlocking {
        for (selected in ids(other)) for (removed in ids(entry)) {
            val storage = Storage(); val http = Http(raw); val repository = repository(storage, http)
            repository.save(entry, raw); repository.save(other, raw)
            repository.saveSettings(LibrarySettings(offline = true, selectedId = selected))
            cache(storage, listOf(entry, other))
            val backend = backend(storage, http); idle(backend)
            backend.perform(AppAction(AppActionKind.REMOVE, removed)); idle(backend)
            assertEquals(other.key, backend.state.value.ready?.key)
            assertEquals(selected, repository.settings().selectedId)
            val restored = backend(storage, http); idle(restored)
            assertEquals(other.key, restored.state.value.ready?.key)
            assertEquals(0, http.requests)
        }
    }

    @Test fun downloadClearsOnlyTheUpdatedSelectionAcrossBothFormats() = runBlocking {
        val bytes = updatedPackage()
        val latest = entry.copy(version = "2", sha256 = FirmwarePackage.sha256(bytes), product = "FM-1_901")
        for (chosen in listOf(entry, other)) for (selected in ids(chosen)) for (downloaded in ids(entry)) {
            val storage = Storage(); val http = Http(bytes); val repository = repository(storage, http)
            repository.save(entry, raw); repository.save(other, raw)
            repository.saveSettings(LibrarySettings(selectedId = selected))
            cache(storage, listOf(latest, other))
            val backend = backend(storage, http); idle(backend)
            assertEquals(chosen.key, backend.state.value.ready?.key)
            backend.perform(AppAction(AppActionKind.DOWNLOAD, downloaded)); idle(backend)
            assertEquals(msg("package_downloaded", latest.name, latest.version), backend.state.value.status)
            assertEquals(latest.sha256, backend.state.value.downloads.single { it.entry.id == entry.id }.entry.sha256)
            assertEquals(if (chosen == entry) null else other.key, backend.state.value.ready?.key)
            assertEquals(if (chosen == entry) null else selected, repository.settings().selectedId)
            val restored = backend(storage, http); idle(restored)
            assertEquals(if (chosen == entry) null else other.key, restored.state.value.ready?.key)
            assertEquals(2, http.requests)
        }
    }

    @Test fun updateAllClearsTheSelectedUpdatedPackageInEitherFormat() = runBlocking {
        val bytes = updatedPackage()
        val latest = entry.copy(version = "2", sha256 = FirmwarePackage.sha256(bytes), product = "FM-1_901")
        for (selected in ids(entry)) {
            val storage = Storage(); val http = Http(bytes); val repository = repository(storage, http)
            repository.save(entry, raw)
            repository.saveSettings(LibrarySettings(selectedId = selected))
            cache(storage, listOf(latest))
            val backend = backend(storage, http); idle(backend)
            backend.perform(AppAction(AppActionKind.UPDATE_ALL)); idle(backend)
            assertEquals(latest.sha256, backend.state.value.downloads.single().entry.sha256)
            assertNull(backend.state.value.ready)
            assertNull(repository.settings().selectedId)
            assertEquals(2, http.requests)
        }
    }

    @Test fun withdrawalAndMalformedKeysNeverRestoreOrSelectAnotherPackage() = runBlocking {
        for (selected in ids(entry) + listOf("other/demo", "firmwarehub/firmwarehub/demo", "firmwarehub/", "../demo")) {
            val storage = Storage(); val http = Http(raw); val repository = repository(storage, http)
            repository.save(entry, raw); repository.save(other, raw)
            repository.saveSettings(LibrarySettings(offline = true, selectedId = selected))
            cache(storage, listOf(other))
            val backend = backend(storage, http); idle(backend)
            assertNull(backend.state.value.ready)
            assertEquals(selected, repository.settings().selectedId)
            backend.perform(AppAction(AppActionKind.USE, other.key)); idle(backend)
            assertEquals(other.key, backend.state.value.ready?.key)
            backend.perform(AppAction(AppActionKind.USE, selected)); idle(backend)
            assertEquals(other.key, backend.state.value.ready?.key)
            assertEquals(other.key, repository.settings().selectedId)
            if (selected in ids(entry)) assertEquals(msg("firmware_removed"), backend.state.value.status)
            assertEquals(2, backend.state.value.downloads.size)
            assertEquals(0, http.requests)
        }
    }
}
