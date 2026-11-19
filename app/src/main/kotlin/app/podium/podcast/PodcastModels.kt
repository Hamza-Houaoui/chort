package app.podium.podcast

import app.podium.core.model.AlbumId
import app.podium.core.model.AlbumRef
import app.podium.core.model.ArtistCredit
import app.podium.core.model.ArtworkRef
import app.podium.core.model.Availability
import app.podium.core.model.Explicitness
import app.podium.core.model.MediaKind
import app.podium.core.model.PlaybackRoute
import app.podium.core.model.SourceId
import app.podium.core.model.SourceRef
import app.podium.core.model.Track
import app.podium.core.model.TrackId
import kotlinx.serialization.Serializable

@Serializable
data class Podcast(
    val id: String,
    val title: String,
    val author: String,
    val description: String? = null,
    val feedUrl: String,
    val artworkUrl: String? = null,
    val genres: List<String> = emptyList(),
    val episodeCount: Int = 0,
    val isSubscribed: Boolean = false,
    val country: String? = null,
    val isCustomRss: Boolean = false,
    val isYtMusic: Boolean = false,
) {
    val displayArtwork: String?
        get() = artworkUrl?.takeIf { it.isNotBlank() }

    val subtitle: String
        get() {
            val parts = mutableListOf<String>()
            if (author.isNotBlank()) parts.add(author)
            val firstGenre = genres.firstOrNull()?.takeIf { it.isNotBlank() }
            if (firstGenre != null && !firstGenre.equals(author, ignoreCase = true)) {
                parts.add(firstGenre)
            }
            if (episodeCount > 0) parts.add("$episodeCount eps")
            return if (parts.isNotEmpty()) parts.joinToString(" · ") else "Podcast"
        }
}

@Serializable
data class PodcastEpisode(
    val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val podcastAuthor: String? = null,
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val durationMs: Long? = null,
    val releaseDate: String? = null,
    val artworkUrl: String? = null,
    val playedPositionMs: Long = 0L,
    val isFinished: Boolean = false,
    val isYtMusic: Boolean = false,
) {
    fun toPodiumTrack(podcast: Podcast? = null): Track {
        val ytm = isYtMusic || (podcast?.isYtMusic == true) || audioUrl.startsWith("ytmusic:") || id.startsWith("ytmusic:")
        val effectiveSourceId = if (ytm) YT_MUSIC_SOURCE_ID else PODCAST_SOURCE_ID
        val effectiveKey = if (ytm) {
            audioUrl.removePrefix("ytmusic:").ifBlank { id.removePrefix("ytmusic:") }
        } else id

        val effectivePodcastTitle = podcast?.title?.takeIf { it.isNotBlank() } ?: podcastTitle.ifBlank { "Podcast" }
        val effectiveAuthor = podcast?.author?.takeIf { it.isNotBlank() }
            ?: podcastAuthor?.takeIf { it.isNotBlank() }
            ?: effectivePodcastTitle
        val effectiveArtwork = artworkUrl?.takeIf { it.isNotBlank() } ?: podcast?.artworkUrl
        val artRef = effectiveArtwork?.let { ArtworkRef(effectiveSourceId, it) }

        return Track(
            id = TrackId.of(effectiveSourceId, effectiveKey),
            title = title.ifBlank { "Episode" },
            artists = listOf(ArtistCredit(effectivePodcastTitle)),
            album = AlbumRef(
                title = effectivePodcastTitle,
                id = AlbumId.of(effectiveSourceId, podcastId),
                albumArtist = effectiveAuthor,
            ),
            durationMs = durationMs,
            trackNumber = null,
            discNumber = null,
            explicitness = Explicitness.NOT_EXPLICIT,
            artwork = artRef,
            source = SourceRef(
                sourceId = effectiveSourceId,
                providerKey = effectiveKey,
                providerUri = if (!ytm) audioUrl else null,
            ),
            availability = Availability.Playable,
            routes = setOf(PlaybackRoute.DIRECT),
            kind = MediaKind.EPISODE,
        )
    }

    companion object {
        val PODCAST_SOURCE_ID = SourceId("podcast")
        val YT_MUSIC_SOURCE_ID = SourceId("ytmusic")
    }
}

enum class PodcastCategory(val id: String, val label: String, val genreId: Int? = null) {
    TRENDING("trending", "Trending / Top"),
    NEWS("news", "News", 1311),
    COMEDY("comedy", "Comedy", 1303),
    TECH("tech", "Technology", 1318),
    TRUE_CRIME("crime", "True Crime", 1488),
    SOCIETY("society", "Society & Culture", 1324),
    RELIGION("religion", "Religion & Spirituality", 1314),
    SPORTS("sports", "Sports", 1545),
    BUSINESS("business", "Business", 1321),
    SCIENCE("science", "Science", 1533),
    HISTORY("history", "History", 1487),
    ARTS("arts", "Arts", 1301);

    companion object {
        fun fromId(id: String): PodcastCategory? = entries.firstOrNull { it.id == id }
    }
}

enum class PodcastProvider(val displayName: String) {
    ITUNES("Apple Podcasts / iTunes (Free)"),
    YOUTUBE_MUSIC("YouTube Music Podcasts"),
    PODCAST_INDEX("Podcast Index (API Key)"),
}

sealed interface PodcastPlace {
    val title: String

    data object Menu : PodcastPlace {
        override val title: String = "Podcasts"
    }

    data class Top(val category: PodcastCategory? = null) : PodcastPlace {
        override val title: String = category?.label ?: "Top Podcasts"
    }

    data object Search : PodcastPlace {
        override val title: String = "Search podcasts"
    }

    data object Subscriptions : PodcastPlace {
        override val title: String = "Subscriptions"
    }

    data object Categories : PodcastPlace {
        override val title: String = "Categories"
    }

    data object RecentEpisodes : PodcastPlace {
        override val title: String = "Recent episodes"
    }

    data class ShowDetails(val podcastId: String) : PodcastPlace {
        override val title: String = "Podcast"
    }

    data class EpisodeDetails(val podcastId: String, val episodeId: String) : PodcastPlace {
        override val title: String = "Episode"
    }

    data object AddRss : PodcastPlace {
        override val title: String = "Add by RSS"
    }

    data object Settings : PodcastPlace {
        override val title: String = "Podcast settings"
    }

    companion object {
        fun encode(place: PodcastPlace): String = when (place) {
            Menu -> "Menu"
            is Top -> "Top:${place.category?.id.orEmpty()}"
            Search -> "Search"
            Subscriptions -> "Subscriptions"
            Categories -> "Categories"
            RecentEpisodes -> "RecentEpisodes"
            is ShowDetails -> "Show:${place.podcastId}"
            is EpisodeDetails -> "Episode:${place.podcastId}\u0000${place.episodeId}"
            AddRss -> "AddRss"
            Settings -> "Settings"
        }

        fun decode(raw: String): PodcastPlace? = when {
            raw == "Menu" -> Menu
            raw.startsWith("Top:") -> {
                val catId = raw.removePrefix("Top:")
                Top(PodcastCategory.fromId(catId))
            }
            raw == "Top" -> Top()
            raw == "Search" -> Search
            raw == "Subscriptions" -> Subscriptions
            raw == "Categories" -> Categories
            raw == "RecentEpisodes" -> RecentEpisodes
            raw.startsWith("Show:") -> ShowDetails(raw.removePrefix("Show:"))
            raw.startsWith("Episode:") -> {
                val parts = raw.removePrefix("Episode:").split("\u0000")
                if (parts.size >= 2) EpisodeDetails(parts[0], parts[1]) else null
            }
            raw == "AddRss" -> AddRss
            raw == "Settings" -> Settings
            else -> null
        }
    }
}
