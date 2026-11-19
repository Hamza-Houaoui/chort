package app.podium.quran

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import app.podium.core.common.Outcome
import app.podium.core.common.PodiumError
import app.podium.core.lyrics.Lyrics
import app.podium.core.lyrics.LyricsLine
import app.podium.core.model.AlbumId
import app.podium.core.model.AlbumRef
import app.podium.core.model.ArtistCredit
import app.podium.core.model.ArtistId
import app.podium.core.model.ArtworkRef
import app.podium.core.model.Explicitness
import app.podium.core.model.PlaybackRoute
import app.podium.core.model.SourceId
import app.podium.core.model.SourceRef
import app.podium.core.model.Track
import app.podium.core.model.TrackId
import app.podium.sources.api.AlbumDetail
import app.podium.sources.api.ArtistDetail
import app.podium.sources.api.ArtworkFacet
import app.podium.sources.api.ArtworkPayload
import app.podium.sources.api.Basis
import app.podium.sources.api.Capability
import app.podium.sources.api.CatalogFacet
import app.podium.sources.api.FacetResolution
import app.podium.sources.api.MissReason
import app.podium.sources.api.MusicEnvironment
import app.podium.sources.api.MusicSource
import app.podium.sources.api.PlayableMedia
import app.podium.sources.api.PlaybackFacet
import app.podium.sources.api.PlaybackTarget
import app.podium.sources.api.Purpose
import app.podium.sources.api.QualityRequest
import app.podium.sources.api.SearchQuery
import app.podium.sources.api.SearchResults
import app.podium.sources.api.SourceCapabilities
import app.podium.sources.api.SourceDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Top celebrated Quran reciters bundled for instant offline selection.
 */
val DefaultReciters: List<Reciter> = listOf(
    Reciter(id = 7, nameArabic = "مشاري راشد العفاسي", nameEnglish = "Mishari Rashid al-`Afasy"),
    Reciter(id = 2, nameArabic = "عبد الباسط عبد الصمد (مرتل)", nameEnglish = "AbdulBaset AbdulSamad (Murattal)", style = "Murattal"),
    Reciter(id = 1, nameArabic = "عبد الباسط عبد الصمد (مجود)", nameEnglish = "AbdulBaset AbdulSamad (Mujawwad)", style = "Mujawwad"),
    Reciter(id = 9, nameArabic = "محمد صديق المنشاوي (مرتل)", nameEnglish = "Mohamed Siddiq al-Minshawi (Murattal)", style = "Murattal"),
    Reciter(id = 8, nameArabic = "محمد صديق المنشاوي (مجود)", nameEnglish = "Mohamed Siddiq al-Minshawi (Mujawwad)", style = "Mujawwad"),
    Reciter(id = 6, nameArabic = "محمود خليل الحصري", nameEnglish = "Mahmoud Khalil Al-Husary"),
    Reciter(id = 12, nameArabic = "محمود خليل الحصري (المعلم)", nameEnglish = "Mahmoud Khalil Al-Husary (Muallim)", style = "Muallim"),
    Reciter(id = 3, nameArabic = "عبدالرحمن السديس", nameEnglish = "Abdur-Rahman as-Sudais"),
    Reciter(id = 10, nameArabic = "سعود الشريم", nameEnglish = "Sa`ud ash-Shuraym"),
    Reciter(id = 4, nameArabic = "أبو بكر الشاطري", nameEnglish = "Abu Bakr al-Shatri"),
    Reciter(id = 5, nameArabic = "هاني الرفاعي", nameEnglish = "Hani ar-Rifai"),
    Reciter(id = 11, nameArabic = "محمد الطبلاوي", nameEnglish = "Mohamed al-Tablawi"),
)

fun Surah.toPodiumTrack(reciter: Reciter): Track {
    val key = "$id:${reciter.id}"
    val trackId = TrackId.of(QuranMusicSource.QURAN_SOURCE_ID, key)
    val title = nameArabic
    val reciterName = reciter.nameArabic.ifBlank { reciter.nameEnglish }
    val artRef = ArtworkRef(QuranMusicSource.QURAN_SOURCE_ID, "surah_$id")

    return Track(
        id = trackId,
        title = title,
        artists = listOf(ArtistCredit(reciterName)),
        album = AlbumRef(
            title = "القرآن الكريم",
            id = AlbumId.of(QuranMusicSource.QURAN_SOURCE_ID, "holy_quran"),
            albumArtist = reciterName,
        ),
        durationMs = null,
        trackNumber = id,
        discNumber = 1,
        explicitness = Explicitness.NOT_EXPLICIT,
        artwork = artRef,
        source = SourceRef(
            sourceId = QuranMusicSource.QURAN_SOURCE_ID,
            providerKey = key,
            providerUri = null,
        ),
    )
}

/**
 * Data holder for recitation audio metadata and timestamps from Quran.com API.
 */
data class QuranRecitationAudio(
    val audioUrl: String,
    val timestamps: List<AyahTimestamp>,
)

data class AyahTimestamp(
    val verseKey: String,
    val timestampFromMs: Long,
    val timestampToMs: Long,
)

/**
 * Client for Quran.com API v4.
 */
class QuranApi(private val cacheDir: File) {

    private val baseCache = File(cacheDir, "quran").apply { mkdirs() }

    private fun get(urlStr: String, cacheFile: File? = null, maxAgeMs: Long = 7 * 24 * 3600 * 1000L): String? {
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0L) {
            val age = System.currentTimeMillis() - cacheFile.lastModified()
            if (age < maxAgeMs) {
                runCatching { return cacheFile.readText() }
            }
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CHORT/1.3")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.isNotBlank()) {
                    cacheFile?.let { runCatching { it.writeText(text) } }
                    return text
                }
            }
        } catch (_: Exception) {
            if (cacheFile != null && cacheFile.exists()) {
                runCatching { return cacheFile.readText() }
            }
        } finally {
            conn?.disconnect()
        }
        return if (cacheFile != null && cacheFile.exists()) runCatching { cacheFile.readText() }.getOrNull() else null
    }

    suspend fun fetchChapterRecitation(reciterId: Int, chapterId: Int): QuranRecitationAudio? = withContext(Dispatchers.IO) {
        val cacheFile = File(baseCache, "recitation_${reciterId}_${chapterId}.json")
        val url = "https://api.quran.com/api/v4/chapter_recitations/$reciterId/$chapterId?segments=true"
        val raw = get(url, cacheFile) ?: return@withContext null

        runCatching {
            val json = JSONObject(raw)
            val audioObj = json.optJSONObject("audio_file") ?: return@withContext null
            val audioUrl = audioObj.optString("audio_url").takeIf { it.isNotBlank() } ?: return@withContext null
            val tsArr = audioObj.optJSONArray("timestamps") ?: JSONArray()
            val timestamps = mutableListOf<AyahTimestamp>()
            for (i in 0 until tsArr.length()) {
                val tObj = tsArr.optJSONObject(i) ?: continue
                val vKey = tObj.optString("verse_key")
                val from = tObj.optLong("timestamp_from", 0L)
                val to = tObj.optLong("timestamp_to", 0L)
                if (vKey.isNotBlank()) {
                    timestamps.add(AyahTimestamp(vKey, from, to))
                }
            }
            QuranRecitationAudio(audioUrl, timestamps)
        }.getOrNull()
    }

    suspend fun fetchUthmaniVerses(chapterId: Int): List<Ayah> = withContext(Dispatchers.IO) {
        val cacheFile = File(baseCache, "verses_uthmani_${chapterId}.json")
        val url = "https://api.quran.com/api/v4/quran/verses/uthmani?chapter_number=$chapterId"
        val raw = get(url, cacheFile) ?: return@withContext emptyList()

        runCatching {
            val json = JSONObject(raw)
            val versesArr = json.optJSONArray("verses") ?: JSONArray()
            val ayahs = mutableListOf<Ayah>()
            for (i in 0 until versesArr.length()) {
                val vObj = versesArr.optJSONObject(i) ?: continue
                val id = vObj.optInt("id", i + 1)
                val verseKey = vObj.optString("verse_key", "$chapterId:${i + 1}")
                val text = vObj.optString("text_uthmani")
                val ayahNum = verseKey.substringAfter(':').toIntOrNull() ?: (i + 1)
                ayahs.add(Ayah(id = id, verseKey = verseKey, ayahNumber = ayahNum, textUthmani = text))
            }
            ayahs
        }.getOrDefault(emptyList())
    }

    suspend fun fetchChapterTranslation(chapterId: Int, option: QuranTranslationOption): Map<String, String> = withContext(Dispatchers.IO) {
        val kind = if (option.isTafsir) "tafsirs" else "translations"
        val cacheFile = File(baseCache, "${kind}_${option.id}_${chapterId}.json")
        val url = "https://api.quran.com/api/v4/quran/$kind/${option.id}?chapter_number=$chapterId"
        val raw = get(url, cacheFile, maxAgeMs = 30L * 24 * 3600 * 1000) ?: return@withContext emptyMap()

        runCatching {
            val json = JSONObject(raw)
            val arr = json.optJSONArray(kind) ?: JSONArray()
            val map = HashMap<String, String>(arr.length())
            val htmlTagRegex = Regex("<[^>]+>")
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val vKey = obj.optString("verse_key").takeIf { it.isNotBlank() } ?: "$chapterId:${i + 1}"
                val rawText = obj.optString("text", "")
                val clean = rawText.replace(htmlTagRegex, "").replace("&nbsp;", " ").trim()
                if (clean.isNotBlank()) {
                    map[vKey] = clean
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    suspend fun search(query: String): List<QuranSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = mutableListOf<QuranSearchResult>()

        // 1. Check local Surahs matches (by name or ID)
        val matchedSurahs = AllSurahs.filter {
            it.nameArabic.contains(trimmed) ||
            it.nameEnglish.contains(trimmed, ignoreCase = true) ||
            it.id.toString() == trimmed
        }
        matchedSurahs.take(3).forEach { s ->
            results.add(
                QuranSearchResult(
                    verseKey = "${s.id}:1",
                    surahId = s.id,
                    ayahNumber = 1,
                    textArabic = "سورة ${s.nameArabic} (${s.revelationPlace.arabicName} • ${s.versesCount} آيات)",
                    translation = s.nameEnglish,
                )
            )
        }

        // 2. Check surah:ayah format (e.g. "2:255")
        if (trimmed.contains(':')) {
            val parts = trimmed.split(':')
            val sId = parts.getOrNull(0)?.toIntOrNull()
            val aNum = parts.getOrNull(1)?.toIntOrNull()
            if (sId != null && sId in 1..114 && aNum != null) {
                val s = findSurah(sId)
                if (s != null && aNum in 1..s.versesCount) {
                    results.add(
                        0,
                        QuranSearchResult(
                            verseKey = "$sId:$aNum",
                            surahId = sId,
                            ayahNumber = aNum,
                            textArabic = "سورة ${s.nameArabic} - الآية $aNum",
                            translation = "Surah ${s.nameEnglish} : Ayah $aNum",
                        )
                    )
                }
            }
        }

        // 3. Full-text search over the whole Quran (cached locally), API as fallback
        val local = searchLocal(trimmed)
        if (local != null) {
            results.addAll(local)
        } else {
            results.addAll(searchOnline(trimmed))
        }

        results
    }

    @Volatile
    private var allVerses: List<Triple<String, String, String>>? = null

    private fun loadAllVerses(): List<Triple<String, String, String>>? {
        allVerses?.let { return it }
        val cacheFile = File(baseCache, "all_verses_imlaei.json")
        val raw = get("https://api.quran.com/api/v4/quran/verses/imlaei", cacheFile, maxAgeMs = 365L * 24 * 3600 * 1000) ?: return null
        return runCatching {
            val arr = JSONObject(raw).optJSONArray("verses") ?: return null
            val list = ArrayList<Triple<String, String, String>>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("text_imlaei")
                list.add(Triple(o.optString("verse_key"), text, normalizeArabic(text)))
            }
            list.takeIf { it.isNotEmpty() }?.also { allVerses = it }
        }.getOrNull()
    }

    private fun searchLocal(query: String): List<QuranSearchResult>? {
        val verses = loadAllVerses() ?: return null
        val q = normalizeArabic(query)
        if (q.isBlank()) return emptyList()
        return verses.asSequence()
            .filter { it.third.contains(q) }
            .take(200)
            .map { (key, text, _) ->
                val parts = key.split(':')
                QuranSearchResult(
                    verseKey = key,
                    surahId = parts.getOrNull(0)?.toIntOrNull() ?: 1,
                    ayahNumber = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                    textArabic = text,
                )
            }.toList()
    }

    private fun searchOnline(trimmed: String): List<QuranSearchResult> {
        val out = mutableListOf<QuranSearchResult>()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            conn = (URL("https://api.quran.com/api/v4/search?q=$encoded&size=50&language=ar").openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", "CHORT/1.4")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val resArr = JSONObject(body).optJSONObject("search")?.optJSONArray("results") ?: JSONArray()
                for (i in 0 until resArr.length()) {
                    val item = resArr.optJSONObject(i) ?: continue
                    val vKey = item.optString("verse_key")
                    val parts = vKey.split(':')
                    out.add(
                        QuranSearchResult(
                            verseKey = vKey,
                            surahId = parts.getOrNull(0)?.toIntOrNull() ?: 1,
                            ayahNumber = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                            textArabic = item.optString("text"),
                        )
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
        return out
    }

    companion object {
        private val diacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

        fun normalizeArabic(input: String): String = diacritics.replace(input, "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/**
 * Repository managing Quran reciters, active selections, Ayahs, and bookmarks.
 */
class QuranRepository(
    private val context: Context,
) {
    val api = QuranApi(context.cacheDir)
    private val prefs = context.getSharedPreferences("chort_quran_prefs", Context.MODE_PRIVATE)

    private val _activeReciter = MutableStateFlow(loadActiveReciter())
    val activeReciter: StateFlow<Reciter> = _activeReciter.asStateFlow()

    private val _activeTranslation = MutableStateFlow(loadActiveTranslation())
    val activeTranslation: StateFlow<QuranTranslationOption> = _activeTranslation.asStateFlow()

    private val _lastRead = MutableStateFlow(loadLastRead())
    val lastRead: StateFlow<QuranBookmark?> = _lastRead.asStateFlow()

    private val _bookmarks = MutableStateFlow(loadBookmarks())
    val bookmarks: StateFlow<List<QuranBookmark>> = _bookmarks.asStateFlow()

    private val ayahsCache = ConcurrentHashMap<Pair<Int, Int>, List<Ayah>>()
    private val audioCache = ConcurrentHashMap<Pair<Int, Int>, QuranRecitationAudio>()

    private fun loadActiveReciter(): Reciter {
        val reciterId = prefs.getInt("active_reciter_id", 7)
        return DefaultReciters.find { it.id == reciterId } ?: DefaultReciters.first()
    }

    fun selectReciter(reciter: Reciter) {
        _activeReciter.value = reciter
        prefs.edit().putInt("active_reciter_id", reciter.id).apply()
    }

    private fun loadActiveTranslation(): QuranTranslationOption {
        val id = prefs.getInt("active_translation_id", QuranTranslationOption.ENGLISH.id)
        return QuranTranslationOption.entries.find { it.id == id } ?: QuranTranslationOption.ENGLISH
    }

    fun selectTranslation(option: QuranTranslationOption) {
        _activeTranslation.value = option
        prefs.edit().putInt("active_translation_id", option.id).apply()
    }

    suspend fun loadSurahWithTranslation(surahId: Int, reciterId: Int, option: QuranTranslationOption): List<Ayah> {
        val ayahs = getSurahAyahs(surahId, reciterId)
        val translations = api.fetchChapterTranslation(surahId, option)
        return ayahs.map { ayah ->
            ayah.copy(translationText = translations[ayah.verseKey])
        }
    }

    private fun loadLastRead(): QuranBookmark? {
        val sId = prefs.getInt("last_read_surah", -1)
        if (sId <= 0) return null
        val aNum = prefs.getInt("last_read_ayah", 1)
        val sName = prefs.getString("last_read_name", findSurah(sId)?.nameArabic.orEmpty()).orEmpty()
        val ts = prefs.getLong("last_read_ts", 0L)
        return QuranBookmark(sId, aNum, sName, ts)
    }

    fun saveLastRead(surahId: Int, ayahNumber: Int, timestampMs: Long = 0L) {
        val surah = findSurah(surahId) ?: return
        val bm = QuranBookmark(surahId, ayahNumber, surah.nameArabic, timestampMs)
        _lastRead.value = bm
        prefs.edit()
            .putInt("last_read_surah", surahId)
            .putInt("last_read_ayah", ayahNumber)
            .putString("last_read_name", surah.nameArabic)
            .putLong("last_read_ts", timestampMs)
            .apply()
    }

    private fun loadBookmarks(): List<QuranBookmark> {
        val raw = prefs.getString("bookmarks_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<QuranBookmark>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    QuranBookmark(
                        surahId = obj.optInt("surahId"),
                        ayahNumber = obj.optInt("ayahNumber"),
                        surahName = obj.optString("surahName"),
                        timestampMs = obj.optLong("timestampMs", 0L),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun persistBookmarks(list: List<QuranBookmark>) {
        _bookmarks.value = list
        val arr = JSONArray()
        list.forEach { bm ->
            arr.put(
                JSONObject().apply {
                    put("surahId", bm.surahId)
                    put("ayahNumber", bm.ayahNumber)
                    put("surahName", bm.surahName)
                    put("timestampMs", bm.timestampMs)
                    put("addedAt", bm.addedAt)
                }
            )
        }
        prefs.edit().putString("bookmarks_json", arr.toString()).apply()
    }

    fun addBookmark(surahId: Int, ayahNumber: Int, timestampMs: Long = 0L) {
        val surah = findSurah(surahId) ?: return
        val existing = _bookmarks.value.filterNot { it.surahId == surahId && it.ayahNumber == ayahNumber }
        val updated = listOf(QuranBookmark(surahId, ayahNumber, surah.nameArabic, timestampMs)) + existing
        persistBookmarks(updated)
    }

    fun removeBookmark(surahId: Int, ayahNumber: Int) {
        val updated = _bookmarks.value.filterNot { it.surahId == surahId && it.ayahNumber == ayahNumber }
        persistBookmarks(updated)
    }

    fun isBookmarked(surahId: Int, ayahNumber: Int): Boolean =
        _bookmarks.value.any { it.surahId == surahId && it.ayahNumber == ayahNumber }

    suspend fun getSurahAudio(surahId: Int, reciterId: Int = activeReciter.value.id): QuranRecitationAudio? {
        val pair = Pair(surahId, reciterId)
        audioCache[pair]?.let { return it }
        val fetched = api.fetchChapterRecitation(reciterId, surahId) ?: return null
        audioCache[pair] = fetched
        return fetched
    }

    suspend fun getSurahAyahs(surahId: Int, reciterId: Int = activeReciter.value.id): List<Ayah> {
        val pair = Pair(surahId, reciterId)
        ayahsCache[pair]?.let { return it }

        val rawVerses = api.fetchUthmaniVerses(surahId)
        val audio = getSurahAudio(surahId, reciterId)

        val merged = if (audio != null && audio.timestamps.isNotEmpty()) {
            val timeMap = audio.timestamps.associateBy { it.verseKey }
            rawVerses.map { ayah ->
                val timing = timeMap[ayah.verseKey]
                if (timing != null) {
                    ayah.copy(
                        timestampFromMs = timing.timestampFromMs,
                        timestampToMs = timing.timestampToMs,
                    )
                } else {
                    ayah
                }
            }
        } else {
            rawVerses
        }

        if (merged.isNotEmpty()) {
            ayahsCache[pair] = merged
        }
        return merged
    }

    /**
     * Supplies synchronized Mosshaf Ayahs for Now Playing lyrics view.
     */
    suspend fun getSyncedAyahsForLyrics(trackKey: String): Lyrics.Synced? {
        // Format of trackKey: "$surahId:$reciterId" or "$surahId"
        val clean = trackKey.removePrefix("quran|").removePrefix("quran:")
        val parts = clean.split(':')
        val surahId = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val reciterId = parts.getOrNull(1)?.toIntOrNull() ?: activeReciter.value.id

        val ayahs = getSurahAyahs(surahId, reciterId)
        if (ayahs.isEmpty()) return null

        val lines = ayahs.map { ayah ->
            val lineText = "﴿${ayah.ayahNumber}﴾ ${ayah.textUthmani}"
            val end = if (ayah.timestampToMs > ayah.timestampFromMs) ayah.timestampToMs else null
            LyricsLine(
                startMs = ayah.timestampFromMs,
                text = lineText,
                endMs = end,
            )
        }

        return Lyrics.Synced(lines)
    }

    companion object {
        fun toArabicDigits(num: Int): String {
            val chars = num.toString().toCharArray()
            val sb = java.lang.StringBuilder()
            for (c in chars) {
                if (c in '0'..'9') {
                    sb.append((c.code - '0'.code + 0x0660).toChar())
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}

/**
 * Custom MusicSource integrating Quran.com streaming into CHORT.
 */
class QuranMusicSource(
    private val repository: QuranRepository,
    private val artCacheDir: File,
) : MusicSource {

    companion object {
        val QURAN_SOURCE_ID = SourceId("quran")
    }

    override val descriptor = SourceDescriptor(
        id = QURAN_SOURCE_ID,
        displayName = "القرآن الكريم",
        providerName = "Quran.com Audio API",
        basis = Basis.OFFICIAL_API,
        environment = MusicEnvironment.ONLINE,
    )

    override val capabilities: StateFlow<SourceCapabilities> = MutableStateFlow(
        SourceCapabilities.available(Capability.DIRECT_STREAM, Capability.ARTWORK),
    )

    override val playback: PlaybackFacet = object : PlaybackFacet {
        override val routes: Set<PlaybackRoute> = setOf(PlaybackRoute.DIRECT)

        override suspend fun resolve(track: Track, quality: QualityRequest, purpose: Purpose): FacetResolution {
            if (purpose == Purpose.DOWNLOAD) {
                return FacetResolution.Miss(MissReason.NOT_PERMITTED)
            }

            val parts = track.source.providerKey.split(':')
            val surahId = parts.getOrNull(0)?.toIntOrNull() ?: return FacetResolution.Miss(MissReason.NOT_FOUND)
            val reciterId = parts.getOrNull(1)?.toIntOrNull() ?: repository.activeReciter.value.id

            val audio = repository.getSurahAudio(surahId, reciterId)
            val audioUrl = audio?.audioUrl?.takeIf { it.isNotBlank() }
                ?: return FacetResolution.Miss(MissReason.NOT_FOUND)

            return FacetResolution.Resolved(
                PlaybackTarget.DirectStream(
                    trackId = track.id,
                    media = PlayableMedia(
                        uri = audioUrl,
                        sourceId = QURAN_SOURCE_ID,
                        mimeType = "audio/mpeg",
                        claimedQuality = null,
                        headers = mapOf(
                            "User-Agent" to "CHORT/1.3 (Android; Quran Audio Player)",
                        ),
                        cacheKey = "${track.id.value}|quran",
                    ),
                ),
            )
        }
    }

    override val artwork: ArtworkFacet = object : ArtworkFacet {
        override suspend fun load(ref: ArtworkRef, sizePx: Int): ArtworkPayload? = withContext(Dispatchers.IO) {
            val key = ref.key
            val surahId = key.removePrefix("surah_").toIntOrNull() ?: 1
            artCacheDir.mkdirs()
            val cachedFile = File(artCacheDir, "quran_cover_$surahId.png")
            if (cachedFile.exists() && cachedFile.length() > 0L) {
                return@withContext ArtworkPayload.LocalFile(cachedFile.absolutePath)
            }

            runCatching {
                generateQuranArtwork(surahId, cachedFile)
                if (cachedFile.exists() && cachedFile.length() > 0L) {
                    return@withContext ArtworkPayload.LocalFile(cachedFile.absolutePath)
                }
            }
            null
        }
    }

    override val catalog: CatalogFacet = object : CatalogFacet {
        override suspend fun search(query: SearchQuery): Outcome<SearchResults> =
            Outcome.Success(SearchResults())

        override suspend fun track(ref: SourceRef): Outcome<Track> {
            val parts = ref.providerKey.split(':')
            val surahId = parts.getOrNull(0)?.toIntOrNull()
                ?: return Outcome.Failure(PodiumError.NotFound(ref.providerKey))
            val reciterId = parts.getOrNull(1)?.toIntOrNull() ?: repository.activeReciter.value.id
            val surah = findSurah(surahId)
                ?: return Outcome.Failure(PodiumError.NotFound(ref.providerKey))
            val reciter = DefaultReciters.find { it.id == reciterId } ?: repository.activeReciter.value

            return Outcome.Success(surah.toPodiumTrack(reciter))
        }

        override suspend fun album(id: AlbumId): Outcome<AlbumDetail> =
            Outcome.Failure(PodiumError.NotFound(id.value))

        override suspend fun artist(id: ArtistId): Outcome<ArtistDetail> =
            Outcome.Failure(PodiumError.NotFound(id.value))
    }

    private fun generateQuranArtwork(surahId: Int, destFile: File) {
        val surah = findSurah(surahId)
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Deep emerald green gradient
        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.parseColor("#062319"), Color.parseColor("#0a382a"),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Gold decorative outer border
        val goldBorderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(24f, 24f, (width - 24).toFloat(), (height - 24).toFloat(), goldBorderPaint)

        val innerBorderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#80D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(36f, 36f, (width - 36).toFloat(), (height - 36).toFloat(), innerBorderPaint)

        // Title: "القرآن الكريم"
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D4AF37")
            textSize = 42f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("القرآن الكريم", (width / 2).toFloat(), 175f, titlePaint)

        // Surah Name: "سورة الفاتحة"
        val surahName = surah?.nameArabic ?: ""
        val surahPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 50f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("سورة $surahName", (width / 2).toFloat(), 265f, surahPaint)

        // Subtitle: e.g. "مكية • ٧ آيات"
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#B0D4AF37")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val place = surah?.revelationPlace?.arabicName ?: ""
        val verses = surah?.versesCount ?: 0
        canvas.drawText("$place • $verses آية", (width / 2).toFloat(), 340f, subPaint)

        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()
    }
}
