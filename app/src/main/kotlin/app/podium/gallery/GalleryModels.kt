package app.podium.gallery

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GalleryMediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucketName: String,
    val folderPath: String,
    val isVideo: Boolean,
    val durationMs: Long,
    val sizeBytes: Long = 0L,
    val orientation: Int = 0,
) {
    val coverFlowArtworkUri: String
        get() = if (isVideo) "gallery-video://$path" else "file://$path"

    val formattedDate: String
        get() = if (dateTaken > 0L) {
            SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(dateTaken))
        } else {
            bucketName
        }

    val formattedDuration: String
        get() {
            if (!isVideo || durationMs <= 0L) return ""
            val totalSec = durationMs / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format(Locale.US, "%d:%02d", m, s)
        }

    val subtitle: String
        get() = if (isVideo) {
            listOf(bucketName, formattedDuration).filter { it.isNotBlank() }.joinToString(" • ")
        } else {
            listOf(bucketName, formattedDate).filter { it.isNotBlank() }.joinToString(" • ")
        }
}

data class GalleryGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverArtworkUri: String?,
    val items: List<GalleryMediaItem>,
)

sealed interface GalleryPlace {
    val title: String

    data object Menu : GalleryPlace {
        override val title: String = "Gallery"
    }

    data object PhotosCoverFlow : GalleryPlace {
        override val title: String = "Photos"
    }

    data object AlbumsCoverFlow : GalleryPlace {
        override val title: String = "Albums"
    }

    data object VideosCoverFlow : GalleryPlace {
        override val title: String = "Videos"
    }

    data object FoldersCoverFlow : GalleryPlace {
        override val title: String = "Folders"
    }

    data class GroupItemsCoverFlow(
        val groupId: String,
        val groupTitle: String,
        val isFolder: Boolean,
    ) : GalleryPlace {
        override val title: String = groupTitle
    }

    data object Search : GalleryPlace {
        override val title: String = "Search Gallery"
    }

    data object FavoritesCoverFlow : GalleryPlace {
        override val title: String = "Favorites"
    }

    data object TrashCoverFlow : GalleryPlace {
        override val title: String = "Trash"
    }

    data object TelegramSync : GalleryPlace {
        override val title: String = "Telegram Sync"
    }

    data class PhotoViewer(
        val mediaId: Long,
        val contextKey: String = "all",
    ) : GalleryPlace {
        override val title: String = "Photo"
    }

    data class PhotoEditor(
        val mediaId: Long,
    ) : GalleryPlace {
        override val title: String = "Edit Photo"
    }

    data class VideoPlayer(
        val mediaId: Long,
    ) : GalleryPlace {
        override val title: String = "Video"
    }

    companion object {
        fun encode(place: GalleryPlace): String = when (place) {
            Menu -> "menu"
            PhotosCoverFlow -> "photos"
            AlbumsCoverFlow -> "albums"
            VideosCoverFlow -> "videos"
            FoldersCoverFlow -> "folders"
            is GroupItemsCoverFlow -> "group:${if (place.isFolder) "1" else "0"}:${place.groupId}:${place.groupTitle}"
            Search -> "search"
            FavoritesCoverFlow -> "favorites"
            TrashCoverFlow -> "trash"
            TelegramSync -> "telegram"
            is PhotoViewer -> "photo:${place.mediaId}:${place.contextKey}"
            is PhotoEditor -> "edit:${place.mediaId}"
            is VideoPlayer -> "video:${place.mediaId}"
        }

        fun decode(raw: String): GalleryPlace = when {
            raw == "photos" -> PhotosCoverFlow
            raw == "albums" -> AlbumsCoverFlow
            raw == "videos" -> VideosCoverFlow
            raw == "folders" -> FoldersCoverFlow
            raw.startsWith("group:") -> {
                val parts = raw.removePrefix("group:").split(":", limit = 3)
                GroupItemsCoverFlow(
                    groupId = parts.getOrElse(1) { "" },
                    groupTitle = parts.getOrElse(2) { "Album" },
                    isFolder = parts.getOrNull(0) == "1",
                )
            }
            raw == "search" -> Search
            raw == "favorites" -> FavoritesCoverFlow
            raw == "trash" -> TrashCoverFlow
            raw == "telegram" -> TelegramSync
            raw.startsWith("photo:") -> {
                val parts = raw.removePrefix("photo:").split(":", limit = 2)
                PhotoViewer(
                    mediaId = parts.getOrNull(0)?.toLongOrNull() ?: 0L,
                    contextKey = parts.getOrElse(1) { "all" },
                )
            }
            raw.startsWith("edit:") -> PhotoEditor(raw.removePrefix("edit:").toLongOrNull() ?: 0L)
            raw.startsWith("video:") -> VideoPlayer(raw.removePrefix("video:").toLongOrNull() ?: 0L)
            else -> Menu
        }
    }
}
