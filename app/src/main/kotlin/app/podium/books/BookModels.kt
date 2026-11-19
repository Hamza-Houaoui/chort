package app.podium.books

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

enum class BookFormat(
    val ext: String,
    val label: String,
    val extensions: List<String> = listOf(ext),
) {
    PDF("pdf", "PDF"),
    EPUB("epub", "EPUB"),
    TXT("txt", "TXT", listOf("txt", "log", "csv")),
    DOC("doc", "DOC", listOf("doc", "docx", "rtf", "odt")),
    FB2("fb2", "FB2"),
    CBZ("cbz", "CBZ"),
    MD("md", "MD", listOf("md", "html", "htm")),
    ;

    companion object {
        fun fromFileName(name: String): BookFormat? {
            val lower = name.lowercase().trim()
            return entries.find { fmt ->
                fmt.extensions.any { e -> lower.endsWith(".$e") }
            }
        }
    }
}

enum class BookTheme(
    val label: String,
    val bg: Color,
    val fg: Color,
    val secondaryFg: Color,
) {
    DAY("☀ Day", Color(0xFFFAF8F5), Color(0xFF1A1A1A), Color(0xFF666666)),
    SEPIA("📜 Sepia", Color(0xFFF4ECD8), Color(0xFF3B2E1E), Color(0xFF7A6652)),
    NIGHT("🌙 Night", Color(0xFF121316), Color(0xFFE5E5EA), Color(0xFF8E8E93)),
    ;

    /**
     * Transforms PDF/CBZ page bitmaps so that both the page paper and text ink match
     * Day, Sepia (#F4ECD8 paper / #3B2E1E ink), or Night (#121316 paper / #E5E5EA ink).
     */
    fun pageColorFilter(): ColorFilter? = when (this) {
        DAY -> null
        SEPIA -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.725f, 0f, 0f, 0f, 59f,
                    0f, 0.745f, 0f, 0f, 46f,
                    0f, 0f, 0.729f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        NIGHT -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.827f, 0f, 0f, 0f, 229f,
                    0f, -0.823f, 0f, 0f, 229f,
                    0f, 0f, -0.831f, 0f, 234f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
}

enum class BookWheelMode(val label: String) {
    SCROLL("↕ Scroll"),
    ZOOM("🔍 Zoom / Font Size"),
    THEME("🌗 Reading Theme"),
}

data class BookItem(
    val id: String,
    val title: String,
    val author: String,
    val path: String,
    val format: BookFormat,
    val coverUri: String?,
    val sizeBytes: Long = 0L,
    val folderPath: String = "",
    val isOnline: Boolean = false,
    val downloadUrl: String? = null,
    val language: String = "",
) {
    val subtitle: String
        get() = buildList {
            if (author.isNotBlank()) add(author)
            add(format.label)
            if (language.isNotBlank()) add(language.uppercase())
        }.joinToString(" • ")
}

data class BookFolderGroup(
    val folderPath: String,
    val folderName: String,
    val subtitle: String,
    val coverUri: String?,
    val books: List<BookItem>,
)

data class BookProgress(
    val bookId: String,
    val pageIndex: Int,
    val totalPages: Int,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val percent: Int
        get() = if (totalPages <= 0) 0 else (((pageIndex + 1) * 100) / totalPages).coerceIn(1, 100)
}

data class BookBookmark(
    val bookId: String,
    val bookTitle: String,
    val pageIndex: Int,
    val totalPages: Int,
    val snippet: String,
    val addedAt: Long = System.currentTimeMillis(),
)

sealed interface BookPlace {
    val title: String

    data object Menu : BookPlace {
        override val title: String = "Books"
    }

    data object AllBooksCoverFlow : BookPlace {
        override val title: String = "All Books"
    }

    data object ContinueReadingCoverFlow : BookPlace {
        override val title: String = "Continue Reading"
    }

    data object OnlineCatalogCoverFlow : BookPlace {
        override val title: String = "Arabic Books"
    }

    data object IslamicBooksCoverFlow : BookPlace {
        override val title: String = "Islamic Books"
    }

    data object QuranBooksCoverFlow : BookPlace {
        override val title: String = "Quran & Tafsir"
    }

    data object FoldersCoverFlow : BookPlace {
        override val title: String = "Folders"
    }

    data class FolderBooksCoverFlow(
        val folderPath: String,
        val folderName: String,
    ) : BookPlace {
        override val title: String = folderName
    }

    data object SearchCoverFlow : BookPlace {
        override val title: String = "Search Books"
    }

    data object FavoritesCoverFlow : BookPlace {
        override val title: String = "Favorite Books"
    }

    data object BookmarksList : BookPlace {
        override val title: String = "Bookmarks"
    }

    data object SelectFolders : BookPlace {
        override val title: String = "Book Folders"
    }

    data class Reader(
        val bookId: String,
        val initialPage: Int = -1,
    ) : BookPlace {
        override val title: String = "Reader"
    }

    companion object {
        fun encode(place: BookPlace): String = when (place) {
            Menu -> "menu"
            AllBooksCoverFlow -> "all"
            ContinueReadingCoverFlow -> "continue"
            OnlineCatalogCoverFlow -> "online"
            IslamicBooksCoverFlow -> "islamic"
            QuranBooksCoverFlow -> "quran_books"
            FoldersCoverFlow -> "folders"
            is FolderBooksCoverFlow -> "folder:${place.folderPath}:${place.folderName}"
            SearchCoverFlow -> "search"
            FavoritesCoverFlow -> "favorites"
            BookmarksList -> "bookmarks"
            SelectFolders -> "select_folders"
            is Reader -> "reader:${place.bookId}:${place.initialPage}"
        }

        fun decode(raw: String): BookPlace = when {
            raw == "all" -> AllBooksCoverFlow
            raw == "continue" -> ContinueReadingCoverFlow
            raw == "online" -> OnlineCatalogCoverFlow
            raw == "islamic" -> IslamicBooksCoverFlow
            raw == "quran_books" -> QuranBooksCoverFlow
            raw == "folders" -> FoldersCoverFlow
            raw.startsWith("folder:") -> {
                val parts = raw.removePrefix("folder:").split(":", limit = 2)
                FolderBooksCoverFlow(
                    folderPath = parts.getOrElse(0) { "" },
                    folderName = parts.getOrElse(1) { "Folder" },
                )
            }
            raw == "search" -> SearchCoverFlow
            raw == "favorites" -> FavoritesCoverFlow
            raw == "bookmarks" -> BookmarksList
            raw == "select_folders" -> SelectFolders
            raw.startsWith("reader:") -> {
                val parts = raw.removePrefix("reader:").split(":", limit = 2)
                Reader(
                    bookId = parts.getOrElse(0) { "" },
                    initialPage = parts.getOrNull(1)?.toIntOrNull() ?: -1,
                )
            }
            else -> Menu
        }
    }
}
