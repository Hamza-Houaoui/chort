package app.podium.podcast

import android.content.Context
import android.content.SharedPreferences
import android.util.Xml
import app.podium.core.common.Outcome
import app.podium.core.common.PodiumError
import app.podium.core.model.AlbumId
import app.podium.core.model.AlbumRef
import app.podium.core.model.ArtworkRef
import app.podium.core.model.AudioQuality
import app.podium.core.model.MediaKind
import app.podium.core.model.PlaybackRoute
import app.podium.core.model.PlaylistId
import app.podium.core.model.SourceId
import app.podium.core.model.SourceRef
import app.podium.core.model.Track
import app.podium.core.model.TrackId
import app.podium.sources.api.ArtworkFacet
import app.podium.sources.api.ArtworkPayload
import app.podium.sources.api.Basis
import app.podium.sources.api.Capability
import app.podium.sources.api.FacetResolution
import app.podium.sources.api.MissReason
import app.podium.sources.api.MusicEnvironment
import app.podium.sources.api.MusicSource
import app.podium.sources.api.PlayableMedia
import app.podium.sources.api.PlaybackFacet
import app.podium.sources.api.PlaybackTarget
import app.podium.sources.api.Purpose
import app.podium.sources.api.QualityRequest
import app.podium.sources.api.SearchKind
import app.podium.sources.api.SearchQuery
import app.podium.sources.api.SourceCapabilities
import app.podium.sources.api.SourceDescriptor
import app.podium.sources.youtubemusic.YouTubeMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal XML RSS feed parser for podcasts.
 */
object RssFeedParser {
    fun parse(inputStream: InputStream, defaultFeedUrl: String = ""): Pair<Podcast?, List<PodcastEpisode>> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        var channelTitle = ""
        var channelAuthor = ""
        var channelDesc = ""
        var channelImage = ""

        val episodes = mutableListOf<PodcastEpisode>()
        var inChannel = false
        var inItem = false

        var itemTitle = ""
        var itemDesc = ""
        var itemAudioUrl = ""
        var itemDurationMs: Long? = null
        var itemPubDate = ""
        var itemGuid = ""
        var itemImage = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase(Locale.ROOT)
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "channel" -> inChannel = true
                        "item" -> {
                            inItem = true
                            itemTitle = ""
                            itemDesc = ""
                            itemAudioUrl = ""
                            itemDurationMs = null
                            itemPubDate = ""
                            itemGuid = ""
                            itemImage = ""
                        }
                        "enclosure" -> {
                            if (inItem) {
                                val url = parser.getAttributeValue(null, "url")
                                val type = parser.getAttributeValue(null, "type")
                                if (!url.isNullOrBlank() && (type == null || type.contains("audio") || type.contains("mp3") || type.contains("m4a") || type.contains("octet-stream") || url.endsWith(".mp3") || url.endsWith(".m4a"))) {
                                    itemAudioUrl = url.trim()
                                }
                            }
                        }
                        "itunes:image" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank()) {
                                if (inItem) itemImage = href.trim() else channelImage = href.trim()
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        if (inItem) {
                            when (tagName) {
                                "title" -> if (itemTitle.isBlank()) itemTitle = text
                                "description", "summary", "itunes:summary" -> if (itemDesc.isBlank()) itemDesc = stripHtml(text)
                                "pubdate" -> if (itemPubDate.isBlank()) itemPubDate = formatPubDate(text)
                                "guid" -> if (itemGuid.isBlank()) itemGuid = text
                                "duration", "itunes:duration" -> if (itemDurationMs == null) itemDurationMs = parseDuration(text)
                            }
                        } else if (inChannel) {
                            when (tagName) {
                                "title" -> if (channelTitle.isBlank()) channelTitle = text
                                "description", "summary", "itunes:summary" -> if (channelDesc.isBlank()) channelDesc = stripHtml(text)
                                "author", "itunes:author", "managingeditor" -> if (channelAuthor.isBlank()) channelAuthor = text
                                "url" -> if (channelImage.isBlank()) channelImage = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "item") {
                        inItem = false
                        if (itemAudioUrl.isNotBlank()) {
                            val epId = if (itemGuid.isNotBlank()) itemGuid else itemAudioUrl.hashCode().toString()
                            episodes.add(
                                PodcastEpisode(
                                    id = epId,
                                    podcastId = defaultFeedUrl.ifBlank { channelTitle },
                                    podcastTitle = channelTitle,
                                    podcastAuthor = channelAuthor,
                                    title = itemTitle.ifBlank { "Episode ${episodes.size + 1}" },
                                    description = itemDesc.take(1000),
                                    audioUrl = itemAudioUrl,
                                    durationMs = itemDurationMs,
                                    releaseDate = itemPubDate,
                                    artworkUrl = itemImage.ifBlank { channelImage },
                                ),
                            )
                        }
                    } else if (tagName == "channel") {
                        inChannel = false
                    }
                }
            }
            eventType = parser.next()
        }

        val podcast = if (channelTitle.isNotBlank()) {
            Podcast(
                id = defaultFeedUrl.ifBlank { channelTitle },
                title = channelTitle,
                author = channelAuthor.ifBlank { channelTitle },
                description = channelDesc.take(800),
                feedUrl = defaultFeedUrl,
                artworkUrl = channelImage,
                episodeCount = episodes.size,
            )
        } else null

        return podcast to episodes
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseDuration(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return try {
            if (s.contains(":")) {
                val parts = s.split(":")
                when (parts.size) {
                    3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble().toLong()) * 1000L
                    2 -> (parts[0].toLong() * 60 + parts[1].toDouble().toLong()) * 1000L
                    else -> null
                }
            } else {
                s.toDoubleOrNull()?.let { (it * 1000L).toLong() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatPubDate(raw: String): String {
        return try {
            val idx = raw.indexOf(",")
            val clean = if (idx >= 0 && idx + 1 < raw.length) raw.substring(idx + 1).trim() else raw.trim()
            clean.split(" ").take(3).joinToString(" ")
        } catch (_: Exception) {
            raw.take(16)
        }
    }
}

/**
 * Client for iTunes / Apple Podcasts Search API, YouTube Music Podcasts, and Podcast Index API.
 */
class PodcastApi(
    private val youtubeMusic: YouTubeMusicSource? = null,
) {

    private suspend fun fetchText(urlStr: String, headers: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "CHORT/1.3 (Android; Podcast Player)")
                setRequestProperty("Accept", "application/json, text/xml, application/xml, */*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun search(
        query: String,
        provider: PodcastProvider = PodcastProvider.ITUNES,
        piKey: String = "",
        piSecret: String = "",
    ): List<Podcast> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (provider == PodcastProvider.YOUTUBE_MUSIC && youtubeMusic != null) {
            val ytm = searchYouTubeMusicPodcasts(trimmed)
            if (ytm.isNotEmpty()) return ytm
        }

        if (provider == PodcastProvider.PODCAST_INDEX && piKey.isNotBlank() && piSecret.isNotBlank()) {
            val piResults = searchPodcastIndex(trimmed, piKey, piSecret)
            if (piResults.isNotEmpty()) return piResults
        }

        // Apple Podcasts / iTunes Search API (Default, free, high quality)
        return searchItunes(trimmed)
    }

    private suspend fun searchYouTubeMusicPodcasts(query: String): List<Podcast> = withContext(Dispatchers.IO) {
        val ytm = youtubeMusic ?: return@withContext emptyList()
        val searchQuery = if (query.contains("podcast", ignoreCase = true)) query else "$query podcast"
        val outcome = ytm.catalog.search(SearchQuery(searchQuery, limit = 30, offset = 0, kinds = setOf(SearchKind.PLAYLISTS, SearchKind.TRACKS, SearchKind.VIDEOS)))
        if (outcome !is Outcome.Success) return@withContext emptyList()

        val results = outcome.value
        val list = mutableListOf<Podcast>()

        // 1. YouTube Music podcast playlists / shows
        results.playlists.forEach { p ->
            list.add(
                Podcast(
                    id = "ytm_${p.id.value}",
                    title = p.title,
                    author = p.ownerName.ifBlank { "YouTube Music" },
                    description = "YouTube Music Podcast · ${p.trackCount ?: 0} episodes",
                    feedUrl = "ytmusic:${p.id.value}",
                    artworkUrl = p.artwork?.uri,
                    episodeCount = p.trackCount ?: 0,
                    isYtMusic = true,
                ),
            )
        }

        // 2. YouTube Music individual podcast episodes
        (results.tracks + results.videos).filter { it.kind == MediaKind.EPISODE || it.title.contains("episode", ignoreCase = true) || it.title.contains("#", ignoreCase = true) }.take(15).forEach { t ->
            list.add(
                Podcast(
                    id = "ytm_${t.id.value}",
                    title = t.title,
                    author = t.artists.firstOrNull()?.name ?: "YouTube Music",
                    description = t.album?.title ?: "Podcast Episode",
                    feedUrl = "ytmusic:${t.source.providerKey}",
                    artworkUrl = t.artwork?.uri,
                    episodeCount = 1,
                    isYtMusic = true,
                ),
            )
        }

        list
    }

    suspend fun searchItunes(query: String, limit: Int = 35): List<Podcast> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?media=podcast&term=$encoded&limit=$limit"
        val jsonStr = fetchText(url) ?: return@withContext emptyList()
        val list = mutableListOf<Podcast>()
        try {
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val id = item.optLong("collectionId", 0L).takeIf { it != 0L }?.toString()
                    ?: item.optString("collectionId")
                val title = item.optString("collectionName").ifBlank { item.optString("trackName") }
                val author = item.optString("artistName")
                val feedUrl = item.optString("feedUrl")
                val artwork = item.optString("artworkUrl600")
                    .ifBlank { item.optString("artworkUrl100") }
                val genre = item.optString("primaryGenreName")
                val count = item.optInt("trackCount", 0)

                if (id.isNotBlank() && title.isNotBlank() && feedUrl.isNotBlank()) {
                    list.add(
                        Podcast(
                            id = id,
                            title = title,
                            author = author,
                            description = genre,
                            feedUrl = feedUrl,
                            artworkUrl = artwork,
                            genres = if (genre.isNotBlank()) listOf(genre) else emptyList(),
                            episodeCount = count,
                        ),
                    )
                }
            }
        } catch (_: Exception) {}
        list
    }

    private suspend fun searchPodcastIndex(query: String, key: String, secret: String, limit: Int = 30): List<Podcast> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.podcastindex.org/api/1.0/search/byterm?q=$encoded&max=$limit"
        val time = (System.currentTimeMillis() / 1000).toString()
        val data = key + secret + time
        val hash = try {
            val md = MessageDigest.getInstance("SHA-1")
            md.digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }

        val headers = mapOf(
            "X-Auth-Key" to key,
            "X-Auth-Date" to time,
            "Authorization" to hash,
            "User-Agent" to "CHORT/1.3",
        )
        val jsonStr = fetchText(url, headers) ?: return@withContext emptyList()
        val list = mutableListOf<Podcast>()
        try {
            val root = JSONObject(jsonStr)
            val feeds = root.optJSONArray("feeds") ?: return@withContext emptyList()
            for (i in 0 until feeds.length()) {
                val item = feeds.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L).toString()
                val title = item.optString("title")
                val author = item.optString("author")
                val desc = item.optString("description")
                val feedUrl = item.optString("url")
                val art = item.optString("artwork").ifBlank { item.optString("image") }
                val count = item.optInt("episodeCount", 0)

                if (title.isNotBlank() && feedUrl.isNotBlank()) {
                    list.add(
                        Podcast(
                            id = id,
                            title = title,
                            author = author,
                            description = desc,
                            feedUrl = feedUrl,
                            artworkUrl = art,
                            episodeCount = count,
                        ),
                    )
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getTopPodcasts(countryCode: String = "us", genreId: Int? = null, limit: Int = 35): List<Podcast> = withContext(Dispatchers.IO) {
        val safeCountry = countryCode.trim().lowercase(Locale.ROOT).ifBlank { "us" }
        val genrePath = if (genreId != null) "/genre=$genreId" else ""
        val url = "https://itunes.apple.com/$safeCountry/rss/toppodcasts/limit=$limit$genrePath/json"
        val jsonStr = fetchText(url) ?: return@withContext emptyList()
        val list = mutableListOf<Podcast>()
        try {
            val root = JSONObject(jsonStr)
            val feed = root.optJSONObject("feed") ?: return@withContext emptyList()
            val entries = feed.optJSONArray("entry") ?: return@withContext emptyList()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val title = entry.optJSONObject("im:name")?.optString("label")
                    ?: entry.optJSONObject("title")?.optString("label").orEmpty()
                val author = entry.optJSONObject("im:artist")?.optString("label").orEmpty()
                val id = entry.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id")
                    ?: entry.optJSONObject("id")?.optString("label").orEmpty()
                val images = entry.optJSONArray("im:image")
                val artwork = if (images != null && images.length() > 0) {
                    images.optJSONObject(images.length() - 1)?.optString("label")
                } else null
                val genre = entry.optJSONObject("category")?.optJSONObject("attributes")?.optString("label").orEmpty()

                if (id.isNotBlank() && title.isNotBlank()) {
                    list.add(
                        Podcast(
                            id = id,
                            title = title,
                            author = author,
                            description = genre,
                            feedUrl = "",
                            artworkUrl = artwork,
                            genres = if (genre.isNotBlank()) listOf(genre) else emptyList(),
                            country = safeCountry,
                        ),
                    )
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getEpisodes(
        podcast: Podcast,
        piKey: String = "",
        piSecret: String = "",
        limit: Int = 100,
    ): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        // 1. YouTube Music podcast playlist / episode
        if (podcast.isYtMusic || podcast.feedUrl.startsWith("ytmusic:")) {
            val ytm = youtubeMusic
            if (ytm != null) {
                val rawKey = podcast.feedUrl.removePrefix("ytmusic:").ifBlank { podcast.id.removePrefix("ytm_") }
                val playlistId = PlaylistId.of(SourceId("ytmusic"), rawKey)
                val outcome = ytm.catalog.playlist(playlistId)
                if (outcome is Outcome.Success) {
                    val tracks = outcome.value.tracks
                    return@withContext tracks.mapIndexed { idx, t ->
                        PodcastEpisode(
                            id = t.id.value,
                            podcastId = podcast.id,
                            podcastTitle = podcast.title,
                            podcastAuthor = podcast.author,
                            title = t.title,
                            description = t.artists.joinToString(", ") { it.name },
                            audioUrl = "ytmusic:${t.source.providerKey}",
                            durationMs = t.durationMs,
                            releaseDate = null,
                            artworkUrl = t.artwork?.uri ?: podcast.artworkUrl,
                            isYtMusic = true,
                        )
                    }
                }
            }
        }

        // 2. Try iTunes Lookup if id is numeric collectionId
        if (podcast.id.all { it.isDigit() }) {
            val episodes = lookupItunesEpisodes(podcast.id, podcast, limit)
            if (episodes.isNotEmpty()) return@withContext episodes
        }

        // 3. Fetch and parse direct RSS XML feed
        val feedUrl = podcast.feedUrl.takeIf { it.isNotBlank() }
        if (!feedUrl.isNullOrBlank()) {
            val parsed = fetchRssFeed(feedUrl)
            if (parsed.isNotEmpty()) return@withContext parsed
        }

        emptyList()
    }

    private suspend fun lookupItunesEpisodes(collectionId: String, fallbackPodcast: Podcast, limit: Int): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/lookup?id=$collectionId&entity=podcastEpisode&limit=$limit"
        val jsonStr = fetchText(url) ?: return@withContext emptyList()
        val list = mutableListOf<PodcastEpisode>()
        try {
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val wrapperType = item.optString("wrapperType")
                if (wrapperType != "podcastEpisode") continue

                val trackId = item.optLong("trackId", 0L).toString()
                val trackName = item.optString("trackName")
                val audioUrl = item.optString("episodeUrl")
                if (audioUrl.isBlank()) continue

                val desc = item.optString("description")
                val durationMs = item.optLong("trackTimeMillis", 0L).takeIf { it > 0L }
                val releaseDate = item.optString("releaseDate")
                val artwork = item.optString("artworkUrl600")
                    .ifBlank { item.optString("artworkUrl160") }
                    .ifBlank { item.optString("artworkUrl100") }
                    .ifBlank { fallbackPodcast.artworkUrl.orEmpty() }

                val formattedDate = releaseDate.take(10)
                list.add(
                    PodcastEpisode(
                        id = trackId,
                        podcastId = fallbackPodcast.id,
                        podcastTitle = fallbackPodcast.title,
                        podcastAuthor = fallbackPodcast.author,
                        title = trackName.ifBlank { "Episode ${list.size + 1}" },
                        description = desc.take(1000),
                        audioUrl = audioUrl,
                        durationMs = durationMs,
                        releaseDate = formattedDate,
                        artworkUrl = artwork,
                    ),
                )
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun fetchRssFeed(feedUrl: String): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CHORT/1.3 (Android; Podcast Player)")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { stream ->
                    val (_, episodes) = RssFeedParser.parse(stream, feedUrl)
                    episodes
                }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun parseCustomRss(feedUrl: String): Pair<Podcast?, List<PodcastEpisode>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CHORT/1.3 (Android; Podcast Player)")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { stream ->
                    RssFeedParser.parse(stream, feedUrl)
                }
            } else (null to emptyList())
        } catch (_: Exception) {
            null to emptyList()
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Repository managing subscriptions, episode history, resume positions, and settings.
 */
class PodcastRepository(
    private val context: Context,
    youtubeMusic: YouTubeMusicSource? = null,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chort_podcasts", Context.MODE_PRIVATE)
    val api = PodcastApi(youtubeMusic)

    private val _subscriptions = MutableStateFlow<List<Podcast>>(emptyList())
    val subscriptions: StateFlow<List<Podcast>> = _subscriptions.asStateFlow()

    private val _recentEpisodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val recentEpisodes: StateFlow<List<PodcastEpisode>> = _recentEpisodes.asStateFlow()

    private val podcastCache = ConcurrentHashMap<String, Podcast>()
    private val episodeCache = ConcurrentHashMap<String, PodcastEpisode>()
    private val showEpisodesCache = ConcurrentHashMap<String, List<PodcastEpisode>>()

    init {
        loadSubscriptions()
        loadRecentEpisodes()
    }

    // --- Provider Settings ---
    var provider: PodcastProvider
        get() = try {
            PodcastProvider.valueOf(prefs.getString("provider", PodcastProvider.ITUNES.name) ?: PodcastProvider.ITUNES.name)
        } catch (_: Exception) {
            PodcastProvider.ITUNES
        }
        set(value) { prefs.edit().putString("provider", value.name).apply() }

    var podcastIndexApiKey: String
        get() = prefs.getString("pi_api_key", "").orEmpty()
        set(value) { prefs.edit().putString("pi_api_key", value.trim()).apply() }

    var podcastIndexApiSecret: String
        get() = prefs.getString("pi_api_secret", "").orEmpty()
        set(value) { prefs.edit().putString("pi_api_secret", value.trim()).apply() }

    var chartsCountry: String
        get() = prefs.getString("charts_country", "us").orEmpty()
        set(value) { prefs.edit().putString("charts_country", value.trim()).apply() }

    // --- Subscriptions ---
    fun isSubscribed(podcastId: String): Boolean {
        return _subscriptions.value.any { it.id == podcastId }
    }

    fun toggleSubscription(podcast: Podcast): Boolean {
        val current = _subscriptions.value.toMutableList()
        val exists = current.any { it.id == podcast.id }
        val newState = if (exists) {
            current.removeAll { it.id == podcast.id }
            false
        } else {
            current.add(0, podcast.copy(isSubscribed = true))
            true
        }
        _subscriptions.value = current
        saveSubscriptions(current)
        return newState
    }

    // --- Playback Progress & Resume ---
    fun savePlayedPosition(episodeId: String, positionMs: Long, isFinished: Boolean = false) {
        if (positionMs <= 0L && !isFinished) return
        prefs.edit()
            .putLong("pos_$episodeId", positionMs)
            .putBoolean("fin_$episodeId", isFinished)
            .apply()
    }

    fun getPlayedPosition(episodeId: String): Long {
        return prefs.getLong("pos_$episodeId", 0L)
    }

    fun isEpisodeFinished(episodeId: String): Boolean {
        return prefs.getBoolean("fin_$episodeId", false)
    }

    // --- Caching & Lookup ---
    fun rememberPodcast(podcast: Podcast) {
        podcastCache[podcast.id] = podcast
    }

    fun rememberPodcasts(podcasts: List<Podcast>) {
        podcasts.forEach { rememberPodcast(it) }
    }

    fun getPodcast(id: String): Podcast? {
        return podcastCache[id] ?: _subscriptions.value.firstOrNull { it.id == id }
    }

    fun rememberEpisodes(podcastId: String, episodes: List<PodcastEpisode>) {
        showEpisodesCache[podcastId] = episodes
        episodes.forEach { episodeCache[it.id] = it }
    }

    fun getEpisode(episodeId: String): PodcastEpisode? {
        return episodeCache[episodeId] ?: _recentEpisodes.value.firstOrNull { it.id == episodeId }
    }

    fun getCachedEpisodes(podcastId: String): List<PodcastEpisode>? {
        return showEpisodesCache[podcastId]
    }

    fun addRecentEpisode(episode: PodcastEpisode) {
        val current = _recentEpisodes.value.toMutableList()
        current.removeAll { it.id == episode.id }
        current.add(0, episode)
        val trimmed = current.take(40)
        _recentEpisodes.value = trimmed
        saveRecentEpisodes(trimmed)
    }

    private fun loadSubscriptions() {
        val raw = prefs.getString("subscriptions_json", null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Podcast>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = Podcast(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    author = o.optString("author"),
                    description = o.optString("description"),
                    feedUrl = o.optString("feedUrl"),
                    artworkUrl = o.optString("artworkUrl"),
                    genres = o.optString("genre").takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
                    episodeCount = o.optInt("episodeCount", 0),
                    isSubscribed = true,
                    isCustomRss = o.optBoolean("isCustomRss", false),
                    isYtMusic = o.optBoolean("isYtMusic", false),
                )
                list.add(p)
                rememberPodcast(p)
            }
            _subscriptions.value = list
        } catch (_: Exception) {}
    }

    private fun saveSubscriptions(list: List<Podcast>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("author", p.author)
                    p.description?.let { put("description", it) }
                    put("feedUrl", p.feedUrl)
                    p.artworkUrl?.let { put("artworkUrl", it) }
                    p.genres.firstOrNull()?.let { put("genre", it) }
                    put("episodeCount", p.episodeCount)
                    put("isCustomRss", p.isCustomRss)
                    put("isYtMusic", p.isYtMusic)
                },
            )
        }
        prefs.edit().putString("subscriptions_json", arr.toString()).apply()
    }

    private fun loadRecentEpisodes() {
        val raw = prefs.getString("recent_episodes_json", null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PodcastEpisode>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ep = PodcastEpisode(
                    id = o.optString("id"),
                    podcastId = o.optString("podcastId"),
                    podcastTitle = o.optString("podcastTitle"),
                    podcastAuthor = o.optString("podcastAuthor"),
                    title = o.optString("title"),
                    description = o.optString("description"),
                    audioUrl = o.optString("audioUrl"),
                    durationMs = o.optLong("durationMs", 0L).takeIf { it > 0L },
                    releaseDate = o.optString("releaseDate"),
                    artworkUrl = o.optString("artworkUrl"),
                    isYtMusic = o.optBoolean("isYtMusic", false),
                )
                list.add(ep)
                episodeCache[ep.id] = ep
            }
            _recentEpisodes.value = list
        } catch (_: Exception) {}
    }

    private fun saveRecentEpisodes(list: List<PodcastEpisode>) {
        val arr = JSONArray()
        list.forEach { ep ->
            arr.put(
                JSONObject().apply {
                    put("id", ep.id)
                    put("podcastId", ep.podcastId)
                    put("podcastTitle", ep.podcastTitle)
                    ep.podcastAuthor?.let { put("podcastAuthor", it) }
                    put("title", ep.title)
                    ep.description?.let { put("description", it) }
                    put("audioUrl", ep.audioUrl)
                    ep.durationMs?.let { put("durationMs", it) }
                    ep.releaseDate?.let { put("releaseDate", it) }
                    ep.artworkUrl?.let { put("artworkUrl", it) }
                    put("isYtMusic", ep.isYtMusic)
                },
            )
        }
        prefs.edit().putString("recent_episodes_json", arr.toString()).apply()
    }
}

/**
 * [MusicSource] adapter for Podcasts so that CHORT plays podcast episodes directly
 * with full Click Wheel seeking, lock-screen controls, and artwork rendering.
 */
class PodcastMusicSource(
    private val repository: PodcastRepository,
    private val artCacheDir: File,
) : MusicSource {

    override val descriptor = SourceDescriptor(
        id = PodcastEpisode.PODCAST_SOURCE_ID,
        displayName = "Podcasts",
        providerName = "Apple Podcasts / Podcast Index",
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

            val episode = repository.getEpisode(track.source.providerKey)
            val audioUrl = episode?.audioUrl?.takeIf { it.isNotBlank() }
                ?: track.source.providerUri?.takeIf { it.isNotBlank() }
                ?: return FacetResolution.Miss(MissReason.NOT_FOUND)

            val mimeType = when {
                audioUrl.contains(".m4a", ignoreCase = true) -> "audio/mp4"
                audioUrl.contains(".aac", ignoreCase = true) -> "audio/aac"
                audioUrl.contains(".ogg", ignoreCase = true) -> "audio/ogg"
                else -> "audio/mpeg"
            }

            return FacetResolution.Resolved(
                PlaybackTarget.DirectStream(
                    trackId = track.id,
                    media = PlayableMedia(
                        uri = audioUrl,
                        sourceId = PodcastEpisode.PODCAST_SOURCE_ID,
                        mimeType = mimeType,
                        claimedQuality = null,
                        headers = mapOf(
                            "User-Agent" to "CHORT/1.3 (Android; Podcast Player)",
                        ),
                        cacheKey = "${track.id.value}|podcast",
                    ),
                ),
            )
        }
    }

    override val artwork: ArtworkFacet = object : ArtworkFacet {
        override suspend fun load(ref: ArtworkRef, sizePx: Int): ArtworkPayload? = withContext(Dispatchers.IO) {
            val rawTarget = ref.key.takeIf { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("/") || it.startsWith("file://") }
                ?: repository.getPodcast(ref.key)?.displayArtwork
                ?: repository.getEpisode(ref.key)?.artworkUrl
                ?: return@withContext null

            if (rawTarget.startsWith("/") || rawTarget.startsWith("file://")) {
                val localFile = if (rawTarget.startsWith("file://")) File(java.net.URI(rawTarget)) else File(rawTarget)
                if (localFile.exists() && localFile.length() > 0L) {
                    return@withContext ArtworkPayload.LocalFile(localFile.absolutePath)
                }
                return@withContext null
            }

            artCacheDir.mkdirs()
            val safeKey = rawTarget.hashCode().toUInt().toString(16)
            val cached = File(artCacheDir, "art_$safeKey.img")
            if (cached.exists() && cached.length() > 0L) {
                return@withContext ArtworkPayload.LocalFile(cached.absolutePath)
            }

            var conn: HttpURLConnection? = null
            try {
                conn = (URL(rawTarget).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "CHORT/1.3")
                }
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        cached.writeBytes(bytes)
                        return@withContext ArtworkPayload.LocalFile(cached.absolutePath)
                    }
                }
            } catch (_: Exception) {} finally {
                conn?.disconnect()
            }
            null
        }
    }
}
