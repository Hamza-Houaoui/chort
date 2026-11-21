package app.podium.youtube

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.podium.AppGraph
import app.podium.core.common.Outcome
import app.podium.core.model.ArtistCredit
import app.podium.core.model.Availability
import app.podium.core.model.PlaybackRoute
import app.podium.core.model.SourceRef
import app.podium.core.model.Track
import app.podium.core.model.TrackId
import app.podium.sources.api.PlaybackTarget
import app.podium.sources.api.Purpose
import app.podium.sources.api.QualityRequest
import app.podium.sources.api.resolve.ResolveOutcome
import app.podium.sources.api.resolve.ResolveRequest
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class Mp3DownloadState(
    val active: Boolean = false,
    val progressPercent: Int = 0,
    val title: String? = null,
)

/**
 * Silent, high-reliability YouTube -> MP3 (320 kbps) downloader:
 * 1. Resolves the highest-quality direct audio stream through CHORT's own InnerTube resolver
 *    (zero "Please sign in" / HTTP 400 errors, uses VISIONOS/WEB_REMIX).
 * 2. Downloads the audio stream with live byte-accurate progress (0..80%).
 * 3. Converts to true 320 kbps MP3 using bundled FFmpeg (80..98%).
 * 4. Writes silently to the user's chosen Download Folder (SAF tree URI) or `Music/CHORT Downloads`,
 *    showing 100% briefly before dismissing.
 */
object YouTubeMp3Downloader {
    private const val TAG = "YouTubeMp3Downloader"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val downloadMutex = Mutex()
    @Volatile
    private var ffmpegReady = false

    private val _state = MutableStateFlow(Mp3DownloadState())
    val state: StateFlow<Mp3DownloadState> = _state.asStateFlow()

    fun enqueue(
        context: Context,
        graph: AppGraph,
        videoId: String,
        fallbackTitle: String?,
        targetFolderUri: String?,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            downloadMutex.withLock {
                _state.value = Mp3DownloadState(active = true, progressPercent = 1)
                val tmpDir = File(appContext.cacheDir, "yt_mp3_tmp/${UUID.randomUUID()}")
                try {
                    ensureFfmpegReady(appContext)
                    tmpDir.mkdirs()

                    // 1. Fetch track metadata & resolve highest-quality direct audio stream
                    val ytmId = graph.youtubeMusic.descriptor.id
                    val fetched = (graph.youtubeMusic.catalog.track(SourceRef(ytmId, videoId)) as? Outcome.Success)?.value
                    val trackTitle = fetched?.title?.takeIf { it.isNotBlank() }
                        ?: fallbackTitle?.removeSuffix(" - YouTube")?.trim()?.takeIf { it.isNotBlank() }
                        ?: "YouTube_$videoId"
                    val artistName = fetched?.artists?.firstOrNull()?.name.orEmpty()
                    val track = fetched ?: Track(
                        id = TrackId.of(ytmId, videoId),
                        source = SourceRef(ytmId, videoId, providerUri = "https://music.youtube.com/watch?v=$videoId"),
                        title = trackTitle,
                        artists = listOf(ArtistCredit(artistName.ifBlank { "YouTube" })),
                        availability = Availability.Playable,
                        routes = setOf(PlaybackRoute.DIRECT),
                    )
                    _state.value = Mp3DownloadState(active = true, progressPercent = 5, title = trackTitle)

                    val resolved = graph.resolver.resolve(
                        ResolveRequest(
                            track = track,
                            purpose = Purpose.PLAYBACK,
                            quality = QualityRequest.Maximum,
                        ),
                    )
                    val target = (resolved as? ResolveOutcome.Resolved)?.selection?.target as? PlaybackTarget.DirectStream
                        ?: throw IllegalStateException("Could not resolve playable audio stream for $videoId")
                    val media = target.media
                    Log.i(TAG, "Resolved audio stream for $videoId (${trackTitle}), downloading...")

                    // 2. Download raw audio stream with live progress (5% -> 80%)
                    val rawFile = File(tmpDir, "source.audio")
                    downloadStream(media.uri, media.headers, rawFile) { frac ->
                        val pct = (5 + (frac * 75f).toInt()).coerceIn(5, 80)
                        _state.value = _state.value.copy(active = true, progressPercent = pct)
                    }
                    Log.i(TAG, "Downloaded ${rawFile.length()} bytes, converting to 320kbps MP3...")

                    // 3. Convert to real 320 kbps MP3 using bundled FFmpeg (80% -> 98%)
                    _state.value = _state.value.copy(active = true, progressPercent = 85)
                    val safeName = sanitizeFileName(trackTitle) + ".mp3"
                    val mp3File = File(tmpDir, safeName)
                    convertToMp3(appContext, rawFile, mp3File, trackTitle, artistName)

                    if (!mp3File.exists() || mp3File.length() == 0L) {
                        throw IllegalStateException("FFmpeg did not produce output MP3")
                    }

                    // 4. Save silently to user's Download Folder
                    _state.value = _state.value.copy(active = true, progressPercent = 98)
                    saveToDestination(appContext, mp3File, targetFolderUri)

                    // 5. Show 100% briefly so user sees completion clearly
                    _state.value = _state.value.copy(active = true, progressPercent = 100)
                    Log.i(TAG, "Saved 320kbps MP3 successfully: $safeName (${mp3File.length()} bytes)")
                    delay(1_200L)
                } catch (t: Throwable) {
                    Log.e(TAG, "MP3 download failed for $videoId", t)
                } finally {
                    tmpDir.deleteRecursively()
                    _state.value = Mp3DownloadState(active = false, progressPercent = 0)
                }
            }
        }
    }

    private suspend fun ensureFfmpegReady(context: Context) {
        if (ffmpegReady) return
        initMutex.withLock {
            if (ffmpegReady) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ffmpegReady = true
        }
    }

    private fun downloadStream(
        streamUrl: String,
        headers: Map<String, String>,
        dest: File,
        onProgress: (Float) -> Unit,
    ) {
        if (streamUrl.startsWith("file://") || streamUrl.startsWith("/")) {
            val localPath = if (streamUrl.startsWith("file://")) Uri.parse(streamUrl).path else streamUrl
            val srcFile = localPath?.let { File(it) }
            if (srcFile != null && srcFile.exists()) {
                srcFile.copyTo(dest, overwrite = true)
                onProgress(1f)
                return
            }
        }
        val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (getRequestProperty("User-Agent") == null) {
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code downloading audio stream")
            val total = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }

    private fun convertToMp3(
        context: Context,
        inputFile: File,
        outputMp3: File,
        title: String,
        artist: String,
    ) {
        val pkgDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
        val binFile = File(pkgDir, "usr/bin/ffmpeg").takeIf { it.exists() }
            ?: File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val libDir = File(pkgDir, "usr/lib")

        val cmd = mutableListOf(
            binFile.absolutePath,
            "-y",
            "-i", inputFile.absolutePath,
            "-vn",
            "-codec:a", "libmp3lame",
            "-b:a", "320k",
            "-metadata", "title=$title",
        )
        if (artist.isNotBlank()) {
            cmd.addAll(listOf("-metadata", "artist=$artist"))
        }
        cmd.add(outputMp3.absolutePath)

        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        if (libDir.exists()) {
            val env = pb.environment()
            val existing = env["LD_LIBRARY_PATH"].orEmpty()
            env["LD_LIBRARY_PATH"] = if (existing.isBlank()) libDir.absolutePath else "${libDir.absolutePath}:$existing"
        }
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            Log.e(TAG, "FFmpeg exit=$exitCode output=${output.takeLast(500)}")
            throw IllegalStateException("FFmpeg failed with exit code $exitCode")
        }
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(110)
            .ifBlank { "audio_${System.currentTimeMillis()}" }

    private fun saveToDestination(context: Context, mp3File: File, targetFolderUri: String?) {
        val fileName = mp3File.name
        if (!targetFolderUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(targetFolderUri)
                val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (treeDoc != null && treeDoc.canWrite()) {
                    treeDoc.findFile(fileName)?.delete()
                    val destDoc = treeDoc.createFile("audio/mpeg", fileName)
                    if (destDoc != null) {
                        context.contentResolver.openOutputStream(destDoc.uri)?.use { out ->
                            mp3File.inputStream().use { input -> input.copyTo(out) }
                        }
                        return
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "SAF write failed, falling back to Music/CHORT Downloads", t)
            }
        }
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val fallbackDir = File(musicDir, "CHORT Downloads").apply { mkdirs() }
        val destFile = File(fallbackDir, fileName)
        mp3File.copyTo(destFile, overwrite = true)
    }
}
