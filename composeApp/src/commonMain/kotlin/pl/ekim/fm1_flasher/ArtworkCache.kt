package pl.ekim.fm1_flasher

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal expect fun decodeArtwork(bytes: ByteArray): ImageBitmap?

/** Serial work deduplicates visible-card requests; disposable covers never evict firmware. */
class ArtworkCache(private val storage: AppStorage, private val http: HttpClient, private val offline: () -> Boolean) {
    private val mutex = Mutex()
    private val memory = linkedMapOf<String, ImageBitmap>()
    suspend fun load(entry: CatalogEntry): ImageBitmap? = withContext(Dispatchers.Default) {
        val url = entry.cardUrl?.takeIf { allowedUrl(it, true) } ?: return@withContext null
        mutex.withLock {
            memory.remove(url)?.let { memory[url] = it; return@withLock it }
            val name = FirmwarePackage.sha256(url.encodeToByteArray())
            val path = "previews-v2/$name.img"
            val bytes = runCatching { storage.read(path, 2 * 1024 * 1024) }.getOrNull()
            var image = bytes?.let { runCatching { decodeArtwork(it) }.getOrNull() }
            if (image == null) {
                storage.remove(path)
                if (offline()) return@withLock null
                val downloaded = http.get(url, 2 * 1024 * 1024, true)
                image = decodeArtwork(downloaded) ?: return@withLock null
                storage.write(path, downloaded)
            }
            storage.write("previews-v2/$name.stamp", epochMillis().toString().encodeToByteArray())
            val files = storage.list("previews-v2").filter { it.endsWith(".img") }
            var size = files.sumOf { storage.size("previews-v2/$it") ?: 0 }
            for (file in files.sortedBy { storage.read("previews-v2/${it.removeSuffix(".img")}.stamp", 64)?.decodeToString()?.toLongOrNull() ?: 0 }) {
                if (size <= 24 * 1024 * 1024) break
                size -= storage.size("previews-v2/$file") ?: 0
                storage.remove("previews-v2/$file"); storage.remove("previews-v2/${file.removeSuffix(".img")}.stamp")
            }
            memory[url] = image
            while (memory.values.sumOf { it.width.toLong() * it.height * 4 } > 4 * 1024 * 1024 && memory.size > 1) memory.remove(memory.keys.first())
            image
        }
    }
}
