package app.podium.voicememo

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One saved voice memo: an audio file in the shared Recordings folder. */
data class VoiceMemo(val uri: Uri, val name: String, val durationMs: Long, val dateMs: Long, val bytes: Long)

/** What the recorder is doing. */
sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val elapsedMs: Long, val paused: Boolean) : RecorderState
}

/**
 * Voice Memos, like the iPod's: records the microphone to AAC (.m4a) straight into the shared
 * `Recordings/Podium` folder (Music/Podium Recordings before Android 12), so other apps see the
 * memos too; lists, plays, renames, deletes and shares them. One per process.
 */
class VoiceMemoStore private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _memos = MutableStateFlow<List<VoiceMemo>>(emptyList())
    val memos: StateFlow<List<VoiceMemo>> = _memos.asStateFlow()

    private val _recorder = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val recorder: StateFlow<RecorderState> = _recorder.asStateFlow()

    /** Recent input levels, 0..1, newest last — the VU meter / waveform while recording. */
    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    /** The memo playing now (null: none) and its position. */
    private val _playing = MutableStateFlow<Uri?>(null)
    val playing: StateFlow<Uri?> = _playing.asStateFlow()
    private val _playPosition = MutableStateFlow(0L)
    val playPosition: StateFlow<Long> = _playPosition.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pendingUri: Uri? = null
    private var startedAt = 0L
    private var accumulated = 0L
    private var ticker: Job? = null
    private var player: MediaPlayer? = null
    private var playTicker: Job? = null

    private val folder: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Environment.DIRECTORY_RECORDINGS}/Podium/"
        else "${Environment.DIRECTORY_MUSIC}/Podium Recordings/"

    private val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // --- Listing ---------------------------------------------------------------------------------------

    fun refresh() {
        scope.launch { _memos.value = withContext(Dispatchers.IO) { query() } }
    }

    private fun query(): List<VoiceMemo> = runCatching {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.SIZE,
        )
        val out = ArrayList<VoiceMemo>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.IS_PENDING} = 0", arrayOf(folder),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += VoiceMemo(
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(1).orEmpty().substringBeforeLast('.'),
                    durationMs = c.getLong(2),
                    dateMs = c.getLong(3) * 1000,
                    bytes = c.getLong(4),
                )
            }
        }
        out
    }.getOrElse { Log.w(TAG, "listing memos failed", it); emptyList() }

    // --- Recording -------------------------------------------------------------------------------------

    /** Starts a new memo. False when the microphone or storage couldn't be opened. */
    fun start(): Boolean {
        if (_recorder.value !is RecorderState.Idle) return true
        stopPlayback()
        val name = "Memo " + SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.m4a")
                put(MediaStore.Audio.Media.TITLE, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
            }
            val uri = context.contentResolver.insert(collection, values) ?: error("no uri")
            pendingUri = uri
            val fd = context.contentResolver.openFileDescriptor(uri, "w") ?: error("no fd")
            pfd = fd
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
            rec.setAudioChannels(1)
            rec.setOutputFile(fd.fileDescriptor)
            rec.prepare()
            rec.start()
            mediaRecorder = rec
            accumulated = 0L
            startedAt = SystemClock.elapsedRealtime()
            _levels.value = emptyList()
            _recorder.value = RecorderState.Recording(0L, paused = false)
            startTicker()
            true
        }.getOrElse {
            Log.w(TAG, "could not start recording", it)
            cleanupFailed()
            false
        }
    }

    fun togglePause() {
        val state = _recorder.value as? RecorderState.Recording ?: return
        val rec = mediaRecorder ?: return
        runCatching {
            if (state.paused) {
                rec.resume()
                startedAt = SystemClock.elapsedRealtime()
                _recorder.value = state.copy(paused = false)
            } else {
                rec.pause()
                accumulated += SystemClock.elapsedRealtime() - startedAt
                _recorder.value = RecorderState.Recording(accumulated, paused = true)
            }
        }
    }

    /** Stops and saves the memo. */
    fun stop() {
        if (_recorder.value !is RecorderState.Recording) return
        ticker?.cancel()
        val uri = pendingUri
        val ok = runCatching { mediaRecorder?.stop() }.isSuccess
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        runCatching { pfd?.close() }
        pfd = null
        pendingUri = null
        _recorder.value = RecorderState.Idle
        _levels.value = emptyList()
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                if (ok) {
                    context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                } else {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                withContext(Dispatchers.Main) { refresh() }
            }
        }
    }

    private fun cleanupFailed() {
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        runCatching { pfd?.close() }
        pfd = null
        pendingUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        pendingUri = null
        _recorder.value = RecorderState.Idle
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val state = _recorder.value as? RecorderState.Recording ?: break
                if (!state.paused) {
                    val elapsed = accumulated + SystemClock.elapsedRealtime() - startedAt
                    _recorder.value = state.copy(elapsedMs = elapsed)
                    val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    // Perceptual: a log curve, so speech fills the meter and silence rests low.
                    val level = if (amp <= 0) 0f else (kotlin.math.log10(amp.toFloat()) / 4.5f).coerceIn(0f, 1f)
                    _levels.value = (_levels.value + level).takeLast(LEVELS)
                }
                delay(TICK_MS)
            }
        }
    }

    // --- Playback --------------------------------------------------------------------------------------

    /** Plays [memo], or stops it when it's the one playing. */
    fun togglePlay(memo: VoiceMemo) {
        if (_playing.value == memo.uri) {
            stopPlayback()
            return
        }
        stopPlayback()
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(context, memo.uri)
            mp.setOnCompletionListener { stopPlayback() }
            mp.prepare()
            mp.start()
            player = mp
            _playing.value = memo.uri
            playTicker = scope.launch {
                while (isActive) {
                    _playPosition.value = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                    delay(200)
                }
            }
        }.onFailure { Log.w(TAG, "could not play memo", it); stopPlayback() }
    }

    fun stopPlayback() {
        playTicker?.cancel()
        playTicker = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        _playing.value = null
        _playPosition.value = 0L
    }

    // --- Managing --------------------------------------------------------------------------------------

    fun rename(uri: Uri, newName: String) {
        val clean = newName.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").ifEmpty { return }
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, "$clean.m4a")
                        put(MediaStore.Audio.Media.TITLE, clean)
                    },
                    null, null,
                )
            }.onFailure { Log.w(TAG, "rename failed", it) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun delete(uri: Uri) {
        if (_playing.value == uri) stopPlayback()
        scope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.delete(uri, null, null) }.onFailure { Log.w(TAG, "delete failed", it) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    /** Hands [memo] to another app (messages, mail, drive…). */
    fun share(memo: VoiceMemo) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, memo.uri)
            .putExtra(Intent.EXTRA_TITLE, memo.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Share ${memo.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    fun memo(uri: Uri): VoiceMemo? = _memos.value.firstOrNull { it.uri == uri }

    companion object {
        private const val TAG = "VoiceMemos"
        private const val TICK_MS = 60L
        const val LEVELS = 48

        @Volatile private var instance: VoiceMemoStore? = null

        fun get(context: Context): VoiceMemoStore =
            instance ?: synchronized(this) { instance ?: VoiceMemoStore(context.applicationContext).also { instance = it } }

        fun formatDuration(ms: Long): String {
            val total = ms / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
