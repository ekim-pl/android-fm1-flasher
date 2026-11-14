package pl.ekim.fm1_flasher

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import java.io.File
import java.lang.ref.WeakReference

/** Author cover when supplied; otherwise a category illustration, not an invented screenshot. */
class FirmwareArtwork(context: Context) : View(context) {
    var entry: CatalogEntry? = null
        set(value) { field = value; bitmap = null; contentDescription = s(R.string.artwork_preview, value?.let { context.entryTitle(it) }.orEmpty()); invalidate() }
    var bitmap: Bitmap? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ui = Ui(context)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val clip = Path().apply { addRoundRect(bounds, ui.dp(14).toFloat(), ui.dp(14).toFloat(), Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clip)
        val image = bitmap
        if (image != null) {
            paint.color = Ui.PANEL; canvas.drawRect(bounds, paint)
            val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
            val w = image.width * scale; val h = image.height * scale
            canvas.drawBitmap(image, null, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), paint)
        } else {
            canvas.scale(width / 160f, height / 128f)
            val color = entry?.let(ui::categoryColor) ?: Ui.ACCENT
            paint.shader = LinearGradient(0f, 0f, 160f, 128f, Color.rgb(44, 60, 53), Color.rgb(21, 30, 26), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 160f, 128f, paint); paint.shader = null
            paint.color = Color.rgb(10, 18, 14); canvas.drawRoundRect(13f, 16f, 147f, 80f, 7f, 7f, paint)
            paint.color = color; paint.textSize = 11f; paint.typeface = Typeface.create("monospace", Typeface.BOLD)
            canvas.drawText(entry?.name.orEmpty().take(15).uppercase(), 23f, 35f, paint)
            paint.strokeWidth = 2f; paint.style = Paint.Style.STROKE
            val path = Path()
            for (x in 0..110) {
                val y = if (entry?.kinds?.contains("drums") == true) (if (x % 25 < 4) 43 else 65).toFloat()
                    else (58 + 10 * kotlin.math.sin(x * .16)).toFloat()
                if (x == 0) path.moveTo(23f, y) else path.lineTo(23f + x, y)
            }
            canvas.drawPath(path, paint)
            repeat(4) { i ->
                val x = 30f + i * 33
                paint.color = Color.rgb(105, 124, 112); canvas.drawCircle(x, 103f, 9f, paint)
                paint.color = color; canvas.drawLine(x, 103f, x + 5f, 97f, paint)
            }
            paint.style = Paint.Style.FILL
        }
        canvas.restore()
    }
}

class ArtworkLoader(private val directory: File, private val offline: () -> Boolean,
                    private val source: (String) -> CatalogSource?) {
    private val worker = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(32))
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val waiting = mutableMapOf<String, MutableList<WeakReference<FirmwareArtwork>>>()
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun bind(view: FirmwareArtwork, entry: CatalogEntry) {
        view.entry = entry
        view.tag = entry.cardUrl
        val url = entry.cardUrl ?: return
        val requestKey = entry.sourceId + "|" + url
        if (closed) return
        cache.get(url)?.let { view.bitmap = it; return }
        waiting[requestKey]?.let { targets ->
            targets.removeAll { it.get()?.tag != url }
            if (targets.none { it.get() === view }) targets += WeakReference(view)
            return
        }
        waiting[requestKey] = mutableListOf(WeakReference(view))
        try {
            worker.execute {
                val image = if (closed) null else runCatching { load(url, entry.sourceId) }.getOrNull()
                if (image != null) cache.put(url, image)
                main.post {
                    val targets = waiting.remove(requestKey).orEmpty()
                    if (!closed && image != null) targets.forEach { it.get()?.takeIf { it.tag == url }?.bitmap = image }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { waiting.remove(requestKey) }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..10000 || bounds.outHeight !in 1..10000) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / inSampleSize > 640 || bounds.outHeight / inSampleSize > 640) inSampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun load(url: String, sourceId: String): Bitmap? {
        cache.get(url)?.let { return it }
        directory.mkdirs()
        val file = File(directory, FirmwarePackage.sha256((sourceId + "|" + url).toByteArray()) + ".img")
        val legacy = File(directory, FirmwarePackage.sha256(url.toByteArray()) + ".img")
        if (!file.exists() && legacy.isFile && legacy.length() <= 2 * 1024 * 1024) legacy.copyTo(file)
        if (file.exists()) {
            val decoded = if (file.length() in 1..2 * 1024 * 1024) decode(file.readBytes()) else null
            if (decoded != null) { file.setLastModified(System.currentTimeMillis()); return decoded }
            file.delete()
        }
        if (closed || offline()) return null
        val policy = source(sourceId) ?: return null
        if (!policy.active || !policy.permitsArtwork(url)) return null
        val response = SourceNetwork.request(url, 2 * 1024 * 1024, policy::permitsArtwork, active = {
            !closed && !offline() && source(sourceId)?.let { it.active && it.revision == policy.revision } == true
        })
        if (response.code != 200) return null
        val bytes = response.bytes
        val image = decode(bytes) ?: return null
        val temporary = File(directory, file.name + ".tmp")
        try {
            temporary.writeBytes(bytes)
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            trimPreviewCache(directory)
        } finally { temporary.delete() }
        return image
    }

    fun close() { closed = true; waiting.clear(); worker.shutdownNow() }
}

internal fun trimPreviewCache(directory: File, limit: Long = 24 * 1024 * 1024) {
    val files = directory.listFiles().orEmpty().filter { it.extension == "img" }.sortedBy { it.lastModified() }
    var bytes = files.sumOf { it.length() }
    for (file in files) {
        if (bytes <= limit) break
        val size = file.length()
        if (file.delete()) bytes -= size
    }
}
