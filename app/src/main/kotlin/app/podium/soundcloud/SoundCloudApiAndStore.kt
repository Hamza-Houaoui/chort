package app.podium.soundcloud

import android.content.Context
import app.podium.core.common.Outcome
import app.podium.core.common.PodiumError
import app.podium.core.model.AlbumId
import app.podium.core.model.ArtistId
import app.podium.core.model.ArtworkRef
import app.podium.core.model.PlaybackRoute
import app.podium.core.model.SourceId
import app.podium.core.model.SourceRef
import app.podium.core.model.Track
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Direct SoundCloud API v2 client.
 */
class SoundCloudApi(
    private var clientIdProvider: () -> String,
    private var clientIdUpdater: (String) -> Unit,
    private var oauthTokenProvider: () -> String?,
) {
    companion object {
        const val BASE_URL = "https://api-v2.soundcloud.com"
        const val FALLBACK_CLIENT_ID = "qLcgHhXgeEFFaSolTB9I71LBsqncsD93"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private fun openConnection(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        overrideClientId: String? = null,
        includeAuth: Boolean = true,
    ): HttpURLConnection {
        val clientId = (overrideClientId ?: clientIdProvider()).ifBlank { FALLBACK_CLIENT_ID }
        val oauthToken = if (includeAuth) oauthTokenProvider() else null

        val fullParams = params.toMutableMap()
        if (!fullParams.containsKey("client_id")) {
            fullParams["client_id"] = clientId
        }

        val query = fullParams.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        val separator = if (endpoint.contains("?")) "&" else "?"
        val fullUrl = if (endpoint.startsWith("http")) {
            "$endpoint$separator$query"
        } else {
            "$BASE_URL$endpoint$separator$query"
        }

        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
        conn.setRequestProperty("Origin", "https://soundcloud.com")
        conn.setRequestProperty("Referer", "https://soundcloud.com/")

        if (!oauthToken.isNullOrBlank()) {
            val clean = oauthToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
            conn.setRequestProperty("Authorization", "OAuth $clean")
        }

        return conn
    }

    /**
     * Dynamically scrapes a fresh client_id from soundcloud.com JS asset bundles if the current one expires.
     */
    fun refreshClientId(): String? {
        return try {
            val homeConn = URL("https://soundcloud.com").openConnection() as HttpURLConnection
            homeConn.connectTimeout = 10000
            homeConn.readTimeout = 10000
            homeConn.setRequestProperty("User-Agent", USER_AGENT)
            val html = readString(homeConn)
            val scriptRegex = Regex("""https://a-v2\.sndcdn\.com/assets/[0-9]+-[a-f0-9]+\.js""")
            val scripts = scriptRegex.findAll(html).map { it.value }.toList().takeLast(6).reversed()
            val idRegex = Regex("""client_id\s*:\s*"([a-zA-Z0-9]{32})"""")
            for (scriptUrl in scripts) {
                val jsConn = URL(scriptUrl).openConnection() as HttpURLConnection
                jsConn.connectTimeout = 10000
                jsConn.readTimeout = 10000
                jsConn.setRequestProperty("User-Agent", USER_AGENT)
                val js = readString(jsConn)
                val match = idRegex.find(js)
                if (match != null) {
                    val fresh = match.groupValues[1]
                    if (fresh.isNotBlank()) {
                        clientIdUpdater(fresh)
                        return fresh
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun requestJson(endpoint: String, params: Map<String, String> = emptyMap()): String? {
        return try {
            var conn = openConnection(endpoint, params)
            val code = conn.responseCode
            if (code == 401 || code == 403) {
                conn.disconnect()
                // If an invalid/expired OAuth token was sent, retry without auth for public endpoints
                if (!oauthTokenProvider().isNullOrBlank() && endpoint != "/me") {
                    conn = openConnection(endpoint, params, includeAuth = false)
                    if (conn.responseCode in 200..299) {
                        return readString(conn)
                    }
                    conn.disconnect()
                }
                val freshId = refreshClientId()
                if (freshId != null) {
                    conn = openConnection(endpoint, params, overrideClientId = freshId, includeAuth = endpoint == "/me")
                } else {
                    return null
                }
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            readString(conn)
        } catch (_: Exception) {
            null
        }
    }

    private fun readString(conn: HttpURLConnection): String {
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    fun getMe(): SoundCloudUser? {
        if (oauthTokenProvider().isNullOrBlank()) return null
        return try {
            val body = requestJson("/me") ?: return null
            val json = JSONObject(body)
            parseUser(json)
        } catch (_: Exception) {
            null
        }
    }

    fun getStream(limit: Int = 30): List<SoundCloudTrack> {
        return try {
            val body = requestJson("/stream", mapOf("limit" to limit.toString())) ?: return emptyList()
            val json = JSONObject(body)
            val collection = json.optJSONArray("collection") ?: JSONArray()
            val list = mutableListOf<SoundCloudTrack>()
            for (i in 0 until collection.length()) {
                val item = collection.optJSONObject(i) ?: continue
                val trackObj = item.optJSONObject("track") ?: if (item.optString("kind") == "track") item else null
                if (trackObj != null) {
                    val track = parseTrack(trackObj)
                    if (track != null) list.add(track)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLikes(userId: Long, limit: Int = 40): List<SoundCloudTrack> {
        return try {
            val body = requestJson("/users/$userId/likes", mapOf("limit" to limit.toString())) ?: return emptyList()
            val json = JSONObject(body)
            val collection = json.optJSONArray("collection") ?: JSONArray()
            val list = mutableListOf<SoundCloudTrack>()
            for (i in 0 until collection.length()) {
                val item = collection.optJSONObject(i) ?: continue
                val trackObj = item.optJSONObject("track") ?: item
                val track = parseTrack(trackObj)
                if (track != null) list.add(track)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getPlaylists(userId: Long, limit: Int = 30): List<SoundCloudPlaylist> {
        return try {
            val body = requestJson("/users/$userId/playlists", mapOf("limit" to limit.toString())) ?: return emptyList()
            val json = JSONObject(body)
            val collection = json.optJSONArray("collection") ?: JSONArray()
            val list = mutableListOf<SoundCloudPlaylist>()
            for (i in 0 until collection.length()) {
                val item = collection.optJSONObject(i) ?: continue
                val pl = parsePlaylist(item)
                if (pl != null) list.add(pl)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getPlaylistDetails(playlistId: Long): SoundCloudPlaylist? {
        return try {
            val body = requestJson("/playlists/$playlistId") ?: return null
            val json = JSONObject(body)
            val pl = parsePlaylist(json) ?: return null
            // SoundCloud playlists often only include full metadata for the first 5 tracks and bare IDs for the rest.
            if (pl.tracks.isNotEmpty()) return pl
            pl
        } catch (_: Exception) {
            null
        }
    }

    fun getCharts(genre: String = "soundcloud:genres:all-music", limit: Int = 30): List<SoundCloudTrack> {
        val chartTracks = try {
            val body = requestJson("/charts", mapOf("kind" to "top", "genre" to genre, "limit" to limit.toString()))
            if (body != null) {
                val json = JSONObject(body)
                val collection = json.optJSONArray("collection") ?: JSONArray()
                val list = mutableListOf<SoundCloudTrack>()
                for (i in 0 until collection.length()) {
                    val item = collection.optJSONObject(i) ?: continue
                    val trackObj = item.optJSONObject("track") ?: if (item.optString("kind") == "track") item else null
                    if (trackObj != null) {
                        val track = parseTrack(trackObj)
                        if (track != null) list.add(track)
                    }
                }
                list
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (chartTracks.isNotEmpty()) return chartTracks
        // Fallback if SoundCloud's /charts v2 returns {} for unauthenticated requests
        return search("top hits", limit)
    }

    fun search(query: String, limit: Int = 30): List<SoundCloudTrack> {
        if (query.isBlank()) return emptyList()
        return try {
            val body = requestJson("/search/tracks", mapOf("q" to query, "limit" to limit.toString())) ?: return emptyList()
            val json = JSONObject(body)
            val collection = json.optJSONArray("collection") ?: JSONArray()
            val list = mutableListOf<SoundCloudTrack>()
            for (i in 0 until collection.length()) {
                val item = collection.optJSONObject(i) ?: continue
                val track = parseTrack(item)
                if (track != null) list.add(track)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getTrack(trackId: Long): SoundCloudTrack? {
        return try {
            val body = requestJson("/tracks/$trackId") ?: return null
            val json = JSONObject(body)
            parseTrack(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves the actual playable MP3 / HLS audio stream URL from a transcoding URL.
     */
    fun resolveStreamUrl(transcodingUrl: String, trackAuthorization: String? = null): String? {
        return try {
            val params = if (!trackAuthorization.isNullOrBlank()) {
                mapOf("track_authorization" to trackAuthorization)
            } else {
                emptyMap()
            }
            val body = requestJson(transcodingUrl, params) ?: return null
            val json = JSONObject(body)
            json.optString("url").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUser(json: JSONObject): SoundCloudUser {
        var avatar = json.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" }
        if (avatar != null && avatar.contains("-large.")) {
            avatar = avatar.replace("-large.", "-t500x500.")
        }
        return SoundCloudUser(
            id = json.optLong("id"),
            username = json.optString("username", "SoundCloud User"),
            avatarUrl = avatar,
            followersCount = json.optInt("followers_count", 0),
            followingsCount = json.optInt("followings_count", 0),
            likesCount = json.optInt("likes_count", 0),
            trackCount = json.optInt("track_count", 0),
            playlistCount = json.optInt("playlist_count", 0),
            fullName = json.optString("full_name").takeIf { it.isNotBlank() && it != "null" },
            city = json.optString("city").takeIf { it.isNotBlank() && it != "null" },
            country = json.optString("country_code").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("country").takeIf { it.isNotBlank() && it != "null" },
            description = json.optString("description").takeIf { it.isNotBlank() && it != "null" },
            permalinkUrl = json.optString("permalink_url").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun parseTrack(json: JSONObject): SoundCloudTrack? {
        val id = json.optLong("id", -1L)
        if (id <= 0) return null

        val title = json.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        val userObj = json.optJSONObject("user")
        val artistName = userObj?.optString("username")?.takeIf { it.isNotBlank() }
            ?: json.optString("label_name", "").takeIf { it.isNotBlank() }
            ?: "SoundCloud Artist"
        val artistId = userObj?.optLong("id")
        val durationMs = json.optLong("duration", json.optLong("full_duration", 0L))
        var artworkUrl = json.optString("artwork_url").takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("avatar_url")?.takeIf { it.isNotBlank() && it != "null" }

        if (artworkUrl != null && artworkUrl.contains("-large.")) {
            artworkUrl = artworkUrl.replace("-large.", "-t500x500.")
        }

        val permalinkUrl = json.optString("permalink_url").takeIf { it.isNotBlank() }
        val likesCount = json.optInt("likes_count", 0)
        val playbackCount = json.optInt("playback_count", 0)

        val trackAuthorization = json.optString("track_authorization").takeIf { it.isNotBlank() && it != "null" }
        val mediaObj = json.optJSONObject("media")
        val transcodings = mediaObj?.optJSONArray("transcodings")
        val fullProgressive = mutableListOf<String>()
        val fullHls = mutableListOf<String>()
        val snippedList = mutableListOf<String>()

        if (transcodings != null) {
            for (i in 0 until transcodings.length()) {
                val t = transcodings.optJSONObject(i) ?: continue
                val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
                val snipped = t.optBoolean("snipped", false)
                val format = t.optJSONObject("format")
                val protocol = format?.optString("protocol") ?: ""
                val preset = t.optString("preset", "")

                if (!snipped) {
                    if (protocol == "progressive") {
                        fullProgressive.add(url)
                    } else if (protocol == "hls") {
                        // Prefer standard aac/mp3 presets before abr_sq (which can 404 without special auth)
                        if (preset == "abr_sq") {
                            fullHls.add(url)
                        } else {
                            fullHls.add(0, url)
                        }
                    }
                } else {
                    snippedList.add(url)
                }
            }
        }

        val allStreamUrls = fullProgressive + fullHls + snippedList
        val streamUrl = allStreamUrls.firstOrNull()

        return SoundCloudTrack(
            id = id,
            title = title,
            artistName = artistName,
            artistId = artistId,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            streamUrl = streamUrl,
            streamUrls = allStreamUrls,
            trackAuthorization = trackAuthorization,
            permalinkUrl = permalinkUrl,
            likesCount = likesCount,
            playbackCount = playbackCount,
        )
    }

    private fun parsePlaylist(json: JSONObject): SoundCloudPlaylist? {
        val id = json.optLong("id", -1L)
        if (id <= 0) return null

        val title = json.optString("title", "Untitled Set")
        val userObj = json.optJSONObject("user")
        val authorName = userObj?.optString("username") ?: "SoundCloud"
        var artworkUrl = json.optString("artwork_url").takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("avatar_url")?.takeIf { it.isNotBlank() && it != "null" }

        if (artworkUrl != null && artworkUrl.contains("-large.")) {
            artworkUrl = artworkUrl.replace("-large.", "-t500x500.")
        }

        val trackCount = json.optInt("track_count", 0)
        val tracksArray = json.optJSONArray("tracks")
        val tracks = mutableListOf<SoundCloudTrack>()
        if (tracksArray != null) {
            for (i in 0 until tracksArray.length()) {
                val t = tracksArray.optJSONObject(i) ?: continue
                val track = parseTrack(t)
                if (track != null) tracks.add(track)
            }
        }

        return SoundCloudPlaylist(
            id = id,
            title = title,
            authorName = authorName,
            artworkUrl = artworkUrl,
            trackCount = trackCount,
            tracks = tracks,
        )
    }
}

/**
 * Repository managing SoundCloud authentication and cached media collections.
 */
class SoundCloudRepository(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("soundcloud_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _clientId = MutableStateFlow(prefs.getString("sc_client_id", SoundCloudApi.FALLBACK_CLIENT_ID) ?: SoundCloudApi.FALLBACK_CLIENT_ID)
    private val _oauthToken = MutableStateFlow(prefs.getString("sc_oauth_token", null))
    private val _currentUser = MutableStateFlow<SoundCloudUser?>(null)
    private val _isLoggedIn = MutableStateFlow(prefs.getString("sc_oauth_token", null) != null)

    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    val currentUser: StateFlow<SoundCloudUser?> = _currentUser.asStateFlow()

    val streamTracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val likedTracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val userPlaylists = MutableStateFlow<List<SoundCloudPlaylist>>(emptyList())
    val chartsTracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val searchResults = MutableStateFlow<List<SoundCloudTrack>>(emptyList())

    private val trackCache = ConcurrentHashMap<Long, SoundCloudTrack>()

    val api = SoundCloudApi(
        clientIdProvider = { _clientId.value },
        clientIdUpdater = { newId ->
            _clientId.value = newId
            prefs.edit().putString("sc_client_id", newId).apply()
        },
        oauthTokenProvider = { _oauthToken.value },
    )

    init {
        val savedUserId = prefs.getLong("sc_user_id", -1L)
        val savedUsername = prefs.getString("sc_user_name", null)
        if (savedUserId > 0 && !savedUsername.isNullOrBlank()) {
            _currentUser.value = SoundCloudUser(
                id = savedUserId,
                username = savedUsername,
                avatarUrl = prefs.getString("sc_user_avatar", null),
                followersCount = prefs.getInt("sc_user_followers", 0),
                followingsCount = prefs.getInt("sc_user_followings", 0),
                likesCount = prefs.getInt("sc_user_likes", 0),
                trackCount = prefs.getInt("sc_user_tracks", 0),
                playlistCount = prefs.getInt("sc_user_playlists", 0),
                fullName = prefs.getString("sc_user_fullname", null),
                city = prefs.getString("sc_user_city", null),
                country = prefs.getString("sc_user_country", null),
                description = prefs.getString("sc_user_desc", null),
                permalinkUrl = prefs.getString("sc_user_permalink", null),
            )
        }

        if (_oauthToken.value != null) {
            refreshUserProfile()
        }
    }

    fun cacheTracks(tracks: List<SoundCloudTrack>) {
        for (t in tracks) {
            if (t.streamUrl != null || !trackCache.containsKey(t.id)) {
                trackCache[t.id] = t
            }
        }
    }

    fun getCachedOrFetchTrack(trackId: Long): SoundCloudTrack? {
        val cached = trackCache[trackId]
        if (cached != null && !cached.streamUrl.isNullOrBlank()) {
            return cached
        }
        val fetched = api.getTrack(trackId)
        if (fetched != null) {
            trackCache[trackId] = fetched
        }
        return fetched ?: cached
    }

    fun saveSession(oauthToken: String, clientId: String?) {
        val cleanToken = oauthToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return
        val finalClientId = if (!clientId.isNullOrBlank()) clientId else _clientId.value
        _oauthToken.value = cleanToken
        _clientId.value = finalClientId
        _isLoggedIn.value = true

        prefs.edit()
            .putString("sc_oauth_token", cleanToken)
            .putString("sc_client_id", finalClientId)
            .apply()

        refreshUserProfile()
    }

    fun refreshUserProfile() {
        scope.launch {
            val user = api.getMe()
            if (user != null) {
                _currentUser.value = user
                _isLoggedIn.value = true
                prefs.edit()
                    .putLong("sc_user_id", user.id)
                    .putString("sc_user_name", user.username)
                    .putString("sc_user_avatar", user.avatarUrl)
                    .putInt("sc_user_followers", user.followersCount)
                    .putInt("sc_user_followings", user.followingsCount)
                    .putInt("sc_user_likes", user.likesCount)
                    .putInt("sc_user_tracks", user.trackCount)
                    .putInt("sc_user_playlists", user.playlistCount)
                    .putString("sc_user_fullname", user.fullName)
                    .putString("sc_user_city", user.city)
                    .putString("sc_user_country", user.country)
                    .putString("sc_user_desc", user.description)
                    .putString("sc_user_permalink", user.permalinkUrl)
                    .apply()

                loadStream()
                loadLikes()
                loadPlaylists()
            }
        }
    }

    fun signOut() {
        _oauthToken.value = null
        _currentUser.value = null
        _isLoggedIn.value = false
        streamTracks.value = emptyList()
        likedTracks.value = emptyList()
        userPlaylists.value = emptyList()

        prefs.edit()
            .remove("sc_oauth_token")
            .remove("sc_user_id")
            .remove("sc_user_name")
            .remove("sc_user_avatar")
            .remove("sc_user_followers")
            .remove("sc_user_likes")
            .remove("sc_user_tracks")
            .remove("sc_user_playlists")
            .apply()
    }

    fun loadStream() {
        scope.launch {
            val tracks = api.getStream()
            if (tracks.isNotEmpty()) {
                cacheTracks(tracks)
                streamTracks.value = tracks
            }
        }
    }

    fun loadLikes() {
        val user = _currentUser.value ?: return
        scope.launch {
            val tracks = api.getLikes(user.id)
            if (tracks.isNotEmpty()) {
                cacheTracks(tracks)
                likedTracks.value = tracks
            }
        }
    }

    fun loadPlaylists() {
        val user = _currentUser.value ?: return
        scope.launch {
            val playlists = api.getPlaylists(user.id)
            if (playlists.isNotEmpty()) {
                playlists.forEach { cacheTracks(it.tracks) }
                userPlaylists.value = playlists
            }
        }
    }

    fun loadCharts(genre: String = "soundcloud:genres:all-music") {
        scope.launch {
            val tracks = api.getCharts(genre)
            if (tracks.isNotEmpty()) {
                cacheTracks(tracks)
                chartsTracks.value = tracks
            }
        }
    }

    suspend fun search(query: String): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        val results = api.search(query)
        cacheTracks(results)
        searchResults.value = results
        results
    }

    suspend fun getPlaylist(id: Long): SoundCloudPlaylist? = withContext(Dispatchers.IO) {
        val pl = api.getPlaylistDetails(id)
        if (pl != null) cacheTracks(pl.tracks)
        pl
    }
}

/**
 * Custom MusicSource integrating SoundCloud into CHORT / Podium.
 */
class SoundCloudMusicSource(
    val repository: SoundCloudRepository,
    private val artCacheDir: File,
) : MusicSource {

    companion object {
        val SOUNDCLOUD_SOURCE_ID = SourceId("soundcloud")
    }

    override val descriptor = SourceDescriptor(
        id = SOUNDCLOUD_SOURCE_ID,
        displayName = "SoundCloud",
        providerName = "SoundCloud API",
        basis = Basis.OFFICIAL_API,
        environment = MusicEnvironment.ONLINE,
    )

    override val capabilities: StateFlow<SourceCapabilities> = MutableStateFlow(
        SourceCapabilities.available(Capability.DIRECT_STREAM, Capability.ARTWORK, Capability.SEARCH),
    )

    override val playback: PlaybackFacet = object : PlaybackFacet {
        override val routes: Set<PlaybackRoute> = setOf(PlaybackRoute.DIRECT)

        override suspend fun resolve(track: Track, quality: QualityRequest, purpose: Purpose): FacetResolution = withContext(Dispatchers.IO) {
            if (purpose == Purpose.DOWNLOAD) {
                return@withContext FacetResolution.Miss(MissReason.NOT_PERMITTED)
            }

            val trackId = track.source.providerKey.toLongOrNull()
            if (trackId == null) {
                android.util.Log.e("SoundCloudPlayback", "Invalid trackId: ${track.source.providerKey}")
                return@withContext FacetResolution.Miss(MissReason.NOT_FOUND)
            }
            val scTrack = repository.getCachedOrFetchTrack(trackId)
            if (scTrack == null) {
                android.util.Log.e("SoundCloudPlayback", "getCachedOrFetchTrack($trackId) returned null")
                return@withContext FacetResolution.Miss(MissReason.NOT_FOUND)
            }
            val candidates = scTrack.streamUrls.ifEmpty { listOfNotNull(scTrack.streamUrl) }
            if (candidates.isEmpty()) {
                android.util.Log.e("SoundCloudPlayback", "scTrack($trackId).streamUrls is empty")
                return@withContext FacetResolution.Miss(MissReason.NOT_FOUND)
            }

            var resolvedMediaUrl: String? = null
            var usedTranscodingUrl: String? = null
            for (transcodingUrl in candidates) {
                val url = repository.api.resolveStreamUrl(transcodingUrl, scTrack.trackAuthorization)
                if (url != null) {
                    resolvedMediaUrl = url
                    usedTranscodingUrl = transcodingUrl
                    break
                }
            }
            if (resolvedMediaUrl == null || usedTranscodingUrl == null) {
                android.util.Log.e("SoundCloudPlayback", "All ${candidates.size} transcodings failed for track $trackId")
                return@withContext FacetResolution.Miss(MissReason.NOT_FOUND)
            }

            val isHls = usedTranscodingUrl.contains("/hls") ||
                resolvedMediaUrl.contains(".m3u8") ||
                resolvedMediaUrl.contains("/playlist.m3u8")

            android.util.Log.i("SoundCloudPlayback", "Resolved track $trackId isHls=$isHls url=${resolvedMediaUrl.take(80)}")

            FacetResolution.Resolved(
                PlaybackTarget.DirectStream(
                    trackId = track.id,
                    media = PlayableMedia(
                        uri = resolvedMediaUrl,
                        sourceId = SOUNDCLOUD_SOURCE_ID,
                        mimeType = if (isHls) "application/x-mpegURL" else "audio/mpeg",
                        claimedQuality = null,
                        headers = mapOf(
                            "User-Agent" to SoundCloudApi.USER_AGENT,
                        ),
                        cacheKey = "${track.id.value}|soundcloud",
                    ),
                ),
            )
        }
    }

    override val artwork: ArtworkFacet = object : ArtworkFacet {
        override suspend fun load(ref: ArtworkRef, sizePx: Int): ArtworkPayload? = withContext(Dispatchers.IO) {
            val parts = ref.key.split('\u0000', limit = 2)
            val trackId = parts[0]
            val cachedFile = File(artCacheDir, "sc_$trackId.jpg")
            if (cachedFile.exists() && cachedFile.length() > 0L) {
                return@withContext ArtworkPayload.LocalFile(cachedFile.absolutePath)
            }

            val urlString = parts.getOrNull(1)
                ?: repository.getCachedOrFetchTrack(trackId.toLongOrNull() ?: 0L)?.artworkUrl
                ?: return@withContext null

            artCacheDir.mkdirs()
            try {
                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.inputStream.use { input ->
                    FileOutputStream(cachedFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (cachedFile.exists() && cachedFile.length() > 0L) {
                    ArtworkPayload.LocalFile(cachedFile.absolutePath)
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    override val catalog: CatalogFacet = object : CatalogFacet {
        override suspend fun search(query: SearchQuery): Outcome<SearchResults> = withContext(Dispatchers.IO) {
            val results = repository.api.search(query.text, query.limit)
            repository.cacheTracks(results)
            val tracks = results.map { it.toPodiumTrack() }
            Outcome.Success(SearchResults(tracks = tracks))
        }

        override suspend fun track(ref: SourceRef): Outcome<Track> = withContext(Dispatchers.IO) {
            val id = ref.providerKey.toLongOrNull() ?: return@withContext Outcome.Failure(PodiumError.NotFound(ref.providerKey))
            val scTrack = repository.getCachedOrFetchTrack(id) ?: return@withContext Outcome.Failure(PodiumError.NotFound(ref.providerKey))
            Outcome.Success(scTrack.toPodiumTrack())
        }

        override suspend fun album(id: AlbumId): Outcome<AlbumDetail> =
            Outcome.Failure(PodiumError.NotFound(id.value))

        override suspend fun artist(id: ArtistId): Outcome<ArtistDetail> =
            Outcome.Failure(PodiumError.NotFound(id.value))
    }
}
