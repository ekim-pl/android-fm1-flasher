package pl.ekim.fm1_flasher

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

internal actual fun decodeArtwork(bytes: ByteArray): ImageBitmap? {
    val image = Image.makeFromEncoded(bytes)
    if (image.width !in 1..10000 || image.height !in 1..10000) { image.close(); return null }
    val scale = minOf(1f, 640f / maxOf(image.width, image.height))
    val surface = Surface.makeRasterN32Premul((image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1))
    return try {
        surface.canvas.scale(scale, scale)
        surface.canvas.drawImage(image, 0f, 0f)
        surface.makeImageSnapshot().toComposeImageBitmap()
    } finally { image.close(); surface.close() }
}
