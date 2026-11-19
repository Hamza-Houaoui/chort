package app.podium

import app.podium.core.common.DiskActivity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.podium.core.designsystem.artwork.ArtworkLoader
import app.podium.sources.api.ArtworkPayload
import app.podium.sources.api.ArtworkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artwork for the UI: resolved through the owning source (no provider URLs here), decoded at the
 * requested size bucket, and kept in a memory cache sized to ~1/8 of the heap (performance.md §3).
 */
class ResolvingArtworkLoader(private val resolver: ArtworkResolver) : ArtworkLoader {

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    override fun peek(uri: String, sizePx: Int): ImageBitmap? = cache.get("$uri@$sizePx")?.asImageBitmap()

    override suspend fun load(uri: String, sizePx: Int): ImageBitmap? {
        DiskActivity.pulse()
        val key = "$uri@$sizePx"
        cache.get(key)?.let { return it.asImageBitmap() }
        val bitmap = withContext(Dispatchers.IO) {
            when {
                uri.startsWith("file://") -> {
                    val path = uri.removePrefix("file://")
                    decodeWithExif(path, sizePx)
                }
                uri.startsWith("gallery-video://") -> {
                    val path = uri.removePrefix("gallery-video://")
                    runCatching {
                        val s = sizePx.coerceIn(128, 1024)
                        ThumbnailUtils.createVideoThumbnail(File(path), Size(s, s), CancellationSignal())
                    }.getOrNull()
                }
                uri.startsWith("book-pdf-cover://") -> {
                    val path = uri.removePrefix("book-pdf-cover://")
                    renderPdfFirstPage(path, sizePx)
                }
                uri.startsWith("http://") || uri.startsWith("https://") -> {
                    downloadHttpBitmap(uri, sizePx)
                }
                else -> when (val payload = resolver.load(uri, sizePx)) {
                    is ArtworkPayload.LocalFile -> decode(sizePx) { opts -> BitmapFactory.decodeFile(payload.path, opts) }
                    is ArtworkPayload.Bytes -> decode(sizePx) { opts -> BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size, opts) }
                    null -> null
                }
            }
        } ?: return null
        cache.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    private inline fun decode(sizePx: Int, block: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        block(bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        return block(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun decodeWithExif(path: String, sizePx: Int): Bitmap? = runCatching {
        val raw = decode(sizePx) { opts -> BitmapFactory.decodeFile(path, opts) } ?: return null
        val exif = ExifInterface(path)
        val deg = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
    }.getOrNull()

    private fun renderPdfFirstPage(path: String, sizePx: Int): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val targetW = sizePx.coerceIn(180, 900)
                    val targetH = ((targetW.toFloat() / page.width.coerceAtLeast(1)) * page.height).toInt().coerceAtLeast(180)
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()

    private fun downloadHttpBitmap(urlStr: String, sizePx: Int): Bitmap? = runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "CHORT/1.4")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        decode(sizePx) { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
    }.getOrNull()
}
