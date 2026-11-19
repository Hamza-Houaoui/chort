package app.podium.soundcloud

import app.podium.core.model.ArtistCredit
import app.podium.core.model.ArtworkRef
import app.podium.core.model.Explicitness
import app.podium.core.model.SourceId
import app.podium.core.model.SourceRef
import app.podium.core.model.Track
import app.podium.core.model.TrackId

/**
 * SoundCloud Track model.
 */
data class SoundCloudTrack(
    val id: Long,
    val title: String,
    val artistName: String,
    val artistId: Long?,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUrl: String?,
    val streamUrls: List<String> = listOfNotNull(streamUrl),
    val trackAuthorization: String? = null,
    val permalinkUrl: String?,
    val likesCount: Int = 0,
    val playbackCount: Int = 0,
) {
    fun toPodiumTrack(): Track {
        val sourceId = SourceId("soundcloud")
        val trackId = TrackId.of(sourceId, id.toString())
        val artRef = if (!artworkUrl.isNullOrBlank()) {
            ArtworkRef(sourceId, "$id\u0000$artworkUrl")
        } else {
            ArtworkRef(sourceId, id.toString())
        }

        return Track(
            id = trackId,
            title = title,
            artists = listOf(ArtistCredit(artistName)),
            album = null,
            durationMs = durationMs.takeIf { it > 0 },
            trackNumber = null,
            discNumber = null,
            explicitness = Explicitness.NOT_EXPLICIT,
            artwork = artRef,
            source = SourceRef(
                sourceId = sourceId,
                providerKey = id.toString(),
                providerUri = permalinkUrl,
            ),
        )
    }
}

/**
 * SoundCloud User profile.
 */
data class SoundCloudUser(
    val id: Long,
    val username: String,
    val avatarUrl: String?,
    val followersCount: Int = 0,
    val followingsCount: Int = 0,
    val likesCount: Int = 0,
    val trackCount: Int = 0,
    val playlistCount: Int = 0,
    val fullName: String? = null,
    val city: String? = null,
    val country: String? = null,
    val description: String? = null,
    val permalinkUrl: String? = null,
)

/**
 * SoundCloud Playlist / Set.
 */
data class SoundCloudPlaylist(
    val id: Long,
    val title: String,
    val authorName: String,
    val artworkUrl: String?,
    val trackCount: Int,
    val tracks: List<SoundCloudTrack> = emptyList(),
)

/**
 * Navigation places inside SoundCloud feature.
 */
sealed interface SoundCloudPlace {
    val title: String

    data object Menu : SoundCloudPlace {
        override val title: String = "SoundCloud"
    }

    data object WebLogin : SoundCloudPlace {
        override val title: String = "Sign in to SoundCloud"
    }

    data object Profile : SoundCloudPlace {
        override val title: String = "Profile"
    }

    data object Stream : SoundCloudPlace {
        override val title: String = "Stream"
    }

    data object Likes : SoundCloudPlace {
        override val title: String = "Likes"
    }

    data object Playlists : SoundCloudPlace {
        override val title: String = "Playlists"
    }

    data class PlaylistDetail(
        val playlistId: Long,
        val playlistTitle: String,
    ) : SoundCloudPlace {
        override val title: String = playlistTitle
    }

    data object Charts : SoundCloudPlace {
        override val title: String = "Top 50 Charts"
    }

    data object Search : SoundCloudPlace {
        override val title: String = "Search SoundCloud"
    }

    companion object {
        fun encode(place: SoundCloudPlace): String = when (place) {
            is Menu -> "menu"
            is WebLogin -> "login"
            is Profile -> "profile"
            is Stream -> "stream"
            is Likes -> "likes"
            is Playlists -> "playlists"
            is PlaylistDetail -> "playlist:${place.playlistId}:${place.playlistTitle}"
            is Charts -> "charts"
            is Search -> "search"
        }

        fun decode(payload: String): SoundCloudPlace {
            if (payload == "menu") return Menu
            if (payload == "login") return WebLogin
            if (payload == "profile") return Profile
            if (payload == "stream") return Stream
            if (payload == "likes") return Likes
            if (payload == "playlists") return Playlists
            if (payload == "charts") return Charts
            if (payload == "search") return Search
            if (payload.startsWith("playlist:")) {
                val parts = payload.removePrefix("playlist:").split(':', limit = 2)
                val id = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val title = parts.getOrNull(1) ?: "Playlist"
                return PlaylistDetail(id, title)
            }
            return Menu
        }
    }
}
