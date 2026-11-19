package app.podium.gallery

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class GalleryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("chort_gallery_prefs", Context.MODE_PRIVATE)

    private val _allItems = MutableStateFlow<List<GalleryMediaItem>>(emptyList())
    val allItems: StateFlow<List<GalleryMediaItem>> = _allItems.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Long>>(loadLongSet("favorites"))
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    private val _trashedIds = MutableStateFlow<Set<Long>>(loadLongSet("trashed"))
    val trashedIds: StateFlow<Set<Long>> = _trashedIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var telegramBotToken: String
        get() = prefs.getString("tg_bot_token", "") ?: ""
        set(value) = prefs.edit().putString("tg_bot_token", value.trim()).apply()

    var telegramChatId: String
        get() = prefs.getString("tg_chat_id", "") ?: ""
        set(value) = prefs.edit().putString("tg_chat_id", value.trim()).apply()

    fun shareMedia(item: GalleryMediaItem) {
        runCatching {
            val mime = if (item.isVideo) "video/*" else "image/*"
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime
                putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, item.name)
                clipData = android.content.ClipData.newRawUri(item.name, item.uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(send, item.name).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadLongSet(key: String): Set<Long> {
        return prefs.getStringSet(key, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    private fun saveLongSet(key: String, set: Set<Long>) {
        prefs.edit().putStringSet(key, set.map { it.toString() }.toSet()).apply()
    }

    fun toggleFavorite(id: Long): Boolean {
        val next = _favoriteIds.value.toMutableSet()
        val added = if (id in next) {
            next.remove(id)
            false
        } else {
            next.add(id)
            true
        }
        _favoriteIds.value = next
        saveLongSet("favorites", next)
        return added
    }

    fun moveToTrash(id: Long) {
        val next = _trashedIds.value + id
        _trashedIds.value = next
        saveLongSet("trashed", next)
    }

    fun restoreFromTrash(id: Long) {
        val next = _trashedIds.value - id
        _trashedIds.value = next
        saveLongSet("trashed", next)
    }

    fun emptyTrash() {
        _trashedIds.value = emptySet()
        saveLongSet("trashed", emptySet())
    }

    fun activePhotos(): List<GalleryMediaItem> {
        val trashed = _trashedIds.value
        return _allItems.value.filter { !it.isVideo && it.id !in trashed }
    }

    fun activeVideos(): List<GalleryMediaItem> {
        val trashed = _trashedIds.value
        return _allItems.value.filter { it.isVideo && it.id !in trashed }
    }

    fun activeFavorites(): List<GalleryMediaItem> {
        val favs = _favoriteIds.value
        val trashed = _trashedIds.value
        return _allItems.value.filter { it.id in favs && it.id !in trashed }
    }

    fun trashedItems(): List<GalleryMediaItem> {
        val trashed = _trashedIds.value
        return _allItems.value.filter { it.id in trashed }
    }

    fun albums(): List<GalleryGroup> {
        val trashed = _trashedIds.value
        return _allItems.value
            .filter { it.id !in trashed }
            .groupBy { it.bucketId to it.bucketName }
            .map { (key, list) ->
                val title = key.second.ifBlank { "Album" }
                GalleryGroup(
                    id = key.first.toString(),
                    title = title,
                    subtitle = "${list.size} items",
                    coverArtworkUri = list.firstOrNull()?.coverFlowArtworkUri,
                    items = list,
                )
            }
            .sortedByDescending { it.items.size }
    }

    fun folders(): List<GalleryGroup> {
        val trashed = _trashedIds.value
        return _allItems.value
            .filter { it.id !in trashed && it.folderPath.isNotBlank() }
            .groupBy { it.folderPath }
            .map { (folderPath, list) ->
                val folderName = File(folderPath).name.ifBlank { folderPath }
                GalleryGroup(
                    id = folderPath,
                    title = folderName,
                    subtitle = "${list.size} items • $folderPath",
                    coverArtworkUri = list.firstOrNull()?.coverFlowArtworkUri,
                    items = list,
                )
            }
            .sortedByDescending { it.items.size }
    }

    fun itemsForContext(contextKey: String): List<GalleryMediaItem> {
        val trashed = _trashedIds.value
        return when {
            contextKey == "photos" -> activePhotos()
            contextKey == "videos" -> activeVideos()
            contextKey == "favorites" -> activeFavorites()
            contextKey == "trash" -> trashedItems()
            contextKey.startsWith("album:") -> {
                val bid = contextKey.removePrefix("album:")
                _allItems.value.filter { it.id !in trashed && it.bucketId.toString() == bid }
            }
            contextKey.startsWith("folder:") -> {
                val fp = contextKey.removePrefix("folder:")
                _allItems.value.filter { it.id !in trashed && it.folderPath == fp }
            }
            contextKey.startsWith("search:") -> {
                val q = contextKey.removePrefix("search:").trim()
                search(q)
            }
            else -> _allItems.value.filter { it.id !in trashed }
        }
    }

    fun search(query: String): List<GalleryMediaItem> {
        val q = query.trim()
        val trashed = _trashedIds.value
        val active = _allItems.value.filter { it.id !in trashed }
        if (q.isBlank()) return active.take(60)
        return active.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.bucketName.contains(q, ignoreCase = true) ||
                it.folderPath.contains(q, ignoreCase = true) ||
                it.formattedDate.contains(q, ignoreCase = true)
        }
    }

    suspend fun refreshMedia() = withContext(Dispatchers.IO) {
        if (!hasMediaPermission()) return@withContext
        _isLoading.value = true
        try {
            val result = ArrayList<GalleryMediaItem>(512)
            queryMediaCollection(
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false,
                out = result,
            )
            queryMediaCollection(
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true,
                out = result,
            )
            result.sortByDescending { it.dateTaken }
            _allItems.value = result
        } finally {
            _isLoading.value = false
        }
    }

    @Suppress("DEPRECATION")
    private fun queryMediaCollection(
        collection: android.net.Uri,
        isVideo: Boolean,
        out: MutableList<GalleryMediaItem>,
    ) {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.BUCKET_ID)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            if (isVideo) {
                add(MediaStore.Video.VideoColumns.DURATION)
            } else {
                add(MediaStore.Images.ImageColumns.ORIENTATION)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        runCatching {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val extraCol = if (isVideo) {
                    cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                } else {
                    cursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION)
                }

                var count = 0
                while (cursor.moveToNext() && count < 1500) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol) ?: continue
                    if (!File(path).exists()) continue
                    val name = cursor.getString(nameCol) ?: File(path).name
                    val dt = cursor.getLong(dateTakenCol).takeIf { it > 0L }
                        ?: (cursor.getLong(dateAddedCol) * 1000L)
                    val w = cursor.getInt(wCol)
                    val h = cursor.getInt(hCol)
                    val bId = cursor.getLong(bucketIdCol)
                    val bName = cursor.getString(bucketNameCol) ?: File(path).parentFile?.name ?: "Media"
                    val size = cursor.getLong(sizeCol)
                    val duration = if (isVideo && extraCol >= 0) cursor.getLong(extraCol) else 0L
                    val orient = if (!isVideo && extraCol >= 0) cursor.getInt(extraCol) else 0

                    out.add(
                        GalleryMediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            path = path,
                            name = name,
                            dateTaken = dt,
                            width = w,
                            height = h,
                            bucketId = bId,
                            bucketName = bName,
                            folderPath = File(path).parent ?: "",
                            isVideo = isVideo,
                            durationMs = duration,
                            sizeBytes = size,
                            orientation = orient,
                        ),
                    )
                    count++
                }
            }
        }
    }

    /**
     * Loads and transforms a photo bitmap for the editor / viewer with rotation, flip,
     * brightness (-100..100), contrast (50..150%), and saturation (0..200%).
     */
    fun renderEditedBitmap(
        path: String,
        maxDimPx: Int,
        extraRotationDeg: Float,
        flipHorizontal: Boolean,
        brightnessOffset: Float,
        contrastScale: Float,
        saturationScale: Float,
    ): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimPx && bounds.outHeight / (sample * 2) >= maxDimPx) {
            sample *= 2
        }
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val exif = ExifInterface(path)
        val exifDeg = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val matrix = Matrix().apply {
            if (exifDeg != 0f || extraRotationDeg != 0f) postRotate(exifDeg + extraRotationDeg)
            if (flipHorizontal) postScale(-1f, 1f)
        }
        val transformed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)

        val cm = ColorMatrix().apply { setSaturation(saturationScale) }
        val c = contrastScale
        val b = brightnessOffset * 2.2f
        val translate = (1f - c) * 128f + b
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        cm.postConcat(contrastMatrix)

        val out = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(transformed, 0f, 0f, paint)
        out
    }.getOrNull()

    suspend fun saveEditedPhoto(
        source: GalleryMediaItem,
        extraRotationDeg: Float,
        flipHorizontal: Boolean,
        brightnessOffset: Float,
        contrastScale: Float,
        saturationScale: Float,
    ): File? = withContext(Dispatchers.IO) {
        val bmp = renderEditedBitmap(
            path = source.path,
            maxDimPx = 2048,
            extraRotationDeg = extraRotationDeg,
            flipHorizontal = flipHorizontal,
            brightnessOffset = brightnessOffset,
            contrastScale = contrastScale,
            saturationScale = saturationScale,
        ) ?: return@withContext null

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CHORT",
        ).apply { mkdirs() }
        val outFile = File(dir, "CHORT_${System.currentTimeMillis()}.jpg")
        runCatching {
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 94, fos)
            }
            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("image/jpeg"), null)
            outFile
        }.getOrNull()
    }

    /**
     * TeleDrop-inspired Telegram Bot uploader: sends a photo or video directly to the user's
     * configured Telegram Chat ID via Bot API (`sendDocument`).
     */
    suspend fun sendToTelegram(item: GalleryMediaItem): Boolean = withContext(Dispatchers.IO) {
        val token = telegramBotToken.takeIf { it.isNotBlank() } ?: return@withContext false
        val chatId = telegramChatId.takeIf { it.isNotBlank() } ?: return@withContext false
        val file = File(item.path)
        if (!file.exists()) return@withContext false

        runCatching {
            val boundary = "----ChortTeleDrop${System.currentTimeMillis()}"
            val url = URL("https://api.telegram.org/bot$token/sendDocument")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
                out.writeBytes("$chatId\r\n")

                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { input -> input.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }
}
