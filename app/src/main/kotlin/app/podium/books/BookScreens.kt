package app.podium.books

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.core.designsystem.component.CoverFlowStage
import app.podium.core.designsystem.component.FocusList
import app.podium.core.designsystem.component.KeyboardAction
import app.podium.core.designsystem.component.ListInputEffect
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.component.MenuRow
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.component.MessageState
import app.podium.core.designsystem.component.PaperTextField
import app.podium.core.designsystem.shell.rememberPhysicalRotation
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelButton
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberFocusListState
import app.podium.core.interaction.rememberPodiumHaptics
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BookController {
    var isPhoneFullscreen by mutableStateOf(false)
    var fullscreenPageBitmap by mutableStateOf<android.graphics.Bitmap?>(null)
    var fullscreenPageText by mutableStateOf("")
    var fullscreenPageTitle by mutableStateOf("")
    var fullscreenPageFooter by mutableStateOf("")
    var fullscreenZoom by mutableFloatStateOf(1f)
    var folderFormatFilter by mutableStateOf<BookFormat?>(null)

    fun exitFullscreen() {
        isPhoneFullscreen = false
    }
}

@Composable
fun BookScreen(
    place: BookPlace,
    repository: BookRepository,
    navigate: (BookPlace) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = repository.importBookUri(uri)
                if (imported != null) {
                    navigate(BookPlace.Reader(imported.id))
                }
            }
        }
    }

    LaunchedEffect(place) {
        if (
            place is BookPlace.Menu ||
            place is BookPlace.AllBooksCoverFlow ||
            place is BookPlace.FoldersCoverFlow
        ) {
            repository.scanLocalBooks()
        }
    }

    when (place) {
        BookPlace.Menu -> BookMainMenu(
            repository = repository,
            onImportFile = {
                importLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/epub+zip",
                        "text/plain",
                        "*/*",
                    ),
                )
            },
            navigate = navigate,
        )
        BookPlace.AllBooksCoverFlow -> {
            val local by repository.localBooks.collectAsStateWithLifecycle()
            val progress by repository.progressMap.collectAsStateWithLifecycle()
            BooksCoverFlowList(
                books = local,
                progressMap = progress,
                stateKey = "books_all_cf",
                emptyTitle = "No Local Books Found",
                emptyMessage = "Use Import Book File or Online Library to add books.",
                repository = repository,
                navigate = navigate,
            )
        }
        BookPlace.ContinueReadingCoverFlow -> {
            val local by repository.localBooks.collectAsStateWithLifecycle()
            val online by repository.onlineBooks.collectAsStateWithLifecycle()
            val progress by repository.progressMap.collectAsStateWithLifecycle()
            val list = remember(local, online, progress) { repository.continueReadingBooks() }
            BooksCoverFlowList(
                books = list,
                progressMap = progress,
                stateKey = "books_continue_cf",
                emptyTitle = "Nothing in Progress",
                emptyMessage = "Books you start reading will appear here with your progress %.",
                repository = repository,
                navigate = navigate,
            )
        }
        BookPlace.OnlineCatalogCoverFlow -> OnlineBooksCatalogScreen(
            repository = repository,
            navigate = navigate,
        )
        BookPlace.IslamicBooksCoverFlow -> IslamicBooksCatalogScreen(
            repository = repository,
            navigate = navigate,
        )
        BookPlace.QuranBooksCoverFlow -> QuranBooksCatalogScreen(
            repository = repository,
            navigate = navigate,
        )
        BookPlace.FoldersCoverFlow -> {
            val local by repository.localBooks.collectAsStateWithLifecycle()
            val filter = BookController.folderFormatFilter
            val folders = remember(local, filter) { repository.folders(filter) }
            val insets = LocalScreenInsets.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = insets.top),
            ) {
                BookExtensionFilterBar(
                    selected = filter,
                    onSelect = { BookController.folderFormatFilter = it },
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CoverFlowStage(
                        items = folders,
                        stateKey = "books_folders_cf_${filter?.name ?: "ALL"}",
                        keyOf = { it.folderPath },
                        titleOf = { it.folderName },
                        subtitleOf = { it.subtitle },
                        artworkOf = { it.coverUri },
                        onActivate = { group, _ ->
                            navigate(BookPlace.FolderBooksCoverFlow(group.folderPath, group.folderName))
                        },
                        emptyTitle = if (filter == null) "No Book Folders" else "No ${filter.label} Folders",
                        emptyMessage = "Folders with PDF, EPUB, TXT, DOC, FB2, or CBZ files appear here.",
                    )
                }
            }
        }
        is BookPlace.FolderBooksCoverFlow -> {
            val local by repository.localBooks.collectAsStateWithLifecycle()
            val progress by repository.progressMap.collectAsStateWithLifecycle()
            val filter = BookController.folderFormatFilter
            val books = remember(local, place.folderPath, filter) {
                local.filter { it.folderPath == place.folderPath && (filter == null || it.format == filter) }
            }
            val insets = LocalScreenInsets.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = insets.top),
            ) {
                BookExtensionFilterBar(
                    selected = filter,
                    onSelect = { BookController.folderFormatFilter = it },
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    BooksCoverFlowList(
                        books = books,
                        progressMap = progress,
                        stateKey = "books_folder_${place.folderPath.hashCode()}_${filter?.name ?: "ALL"}",
                        emptyTitle = place.folderName,
                        emptyMessage = if (filter == null) "No books in this folder." else "No ${filter.label} books in this folder.",
                        repository = repository,
                        navigate = navigate,
                    )
                }
            }
        }
        BookPlace.SearchCoverFlow -> BookSearchScreen(
            repository = repository,
            navigate = navigate,
        )
        BookPlace.FavoritesCoverFlow -> {
            val local by repository.localBooks.collectAsStateWithLifecycle()
            val online by repository.onlineBooks.collectAsStateWithLifecycle()
            val favs by repository.favoriteIds.collectAsStateWithLifecycle()
            val progress by repository.progressMap.collectAsStateWithLifecycle()
            val list = remember(local, online, favs) { repository.favoriteBooks() }
            BooksCoverFlowList(
                books = list,
                progressMap = progress,
                stateKey = "books_favorites_cf",
                emptyTitle = "No Favorite Books",
                emptyMessage = "Hold Center on any book in Cover Flow to favorite it.",
                repository = repository,
                navigate = navigate,
            )
        }
        BookPlace.BookmarksList -> BookmarksListScreen(
            repository = repository,
            navigate = navigate,
        )
        BookPlace.SelectFolders -> BookSelectFoldersScreen(
            repository = repository,
        )
        is BookPlace.Reader -> BookReaderScreen(
            bookId = place.bookId,
            initialPage = place.initialPage,
            repository = repository,
            onBack = onBack,
        )
    }
}

private data class BookMenuRow(
    val label: String,
    val value: String?,
    val action: () -> Unit,
)

@Composable
private fun BookMainMenu(
    repository: BookRepository,
    onImportFile: () -> Unit,
    navigate: (BookPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val local by repository.localBooks.collectAsStateWithLifecycle()
    val online by repository.onlineBooks.collectAsStateWithLifecycle()
    val favs by repository.favoriteIds.collectAsStateWithLifecycle()
    val progress by repository.progressMap.collectAsStateWithLifecycle()
    val bookmarks by repository.bookmarks.collectAsStateWithLifecycle()
    val selectedFolders by repository.selectedFolders.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    val hasAllFiles = remember(local) {
        android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()
    }

    val rows = remember(strings, local, online, favs, progress, bookmarks, selectedFolders, hasAllFiles) {
        val continueCount = repository.continueReadingBooks().size
        val foldersCount = repository.folders().size
        val favsCount = repository.favoriteBooks().size
        buildList {
            if (!hasAllFiles) {
                add(
                    BookMenuRow(
                        label = if (strings is app.podium.core.designsystem.l10n.ArabicStrings) "السماح بالوصول لجميع الكتب" else "Allow All Books Access",
                        value = if (strings is app.podium.core.designsystem.l10n.ArabicStrings) "مطلوب لملفات PDF" else "Required for PDFs",
                        action = {
                            runCatching {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            }
                        },
                    ),
                )
            }
            add(
                BookMenuRow(
                    label = strings.allBooks,
                    value = "${local.size}",
                    action = { navigate(BookPlace.AllBooksCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.continueReading,
                    value = if (continueCount > 0) "$continueCount" else null,
                    action = { navigate(BookPlace.ContinueReadingCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.quran,
                    value = null,
                    action = { navigate(BookPlace.QuranBooksCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.islamicBooks,
                    value = null,
                    action = { navigate(BookPlace.IslamicBooksCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.arabicBooks,
                    value = null,
                    action = { navigate(BookPlace.OnlineCatalogCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.folders,
                    value = "$foldersCount",
                    action = { navigate(BookPlace.FoldersCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.chooseScanFolders,
                    value = if (selectedFolders.isEmpty()) "All" else "${selectedFolders.size}",
                    action = { navigate(BookPlace.SelectFolders) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.search,
                    value = null,
                    action = { navigate(BookPlace.SearchCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.favorites,
                    value = if (favsCount > 0) "$favsCount" else null,
                    action = { navigate(BookPlace.FavoritesCoverFlow) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.bookmarks,
                    value = if (bookmarks.isNotEmpty()) "${bookmarks.size}" else null,
                    action = { navigate(BookPlace.BookmarksList) },
                ),
            )
            add(
                BookMenuRow(
                    label = strings.importBookFile,
                    value = "PDF/EPUB/TXT/DOC",
                    action = onImportFile,
                ),
            )
        }
    }

    val focus = rememberFocusListState("books_main_menu")
    val activate: (Int) -> Unit = { idx -> rows.getOrNull(idx)?.action?.invoke() }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.label },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        MenuRow(
            label = item.label,
            focused = focused,
            value = item.value,
            showChevron = true,
        )
    }
}

@Composable
private fun BookExtensionFilterBar(
    selected: BookFormat?,
    onSelect: (BookFormat?) -> Unit,
) {
    val colors = PodiumTheme.colors
    val lang = app.podium.core.designsystem.l10n.LocalAppLanguage.current
    val allLabel = when (lang.code) {
        "ar" -> "الكل"
        "fr" -> "Tous"
        "tr" -> "Tümü"
        "ru" -> "Все"
        else -> "All"
    }
    val scroll = androidx.compose.foundation.rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val options: List<BookFormat?> = listOf(null) + BookFormat.entries
        for (fmt in options) {
            val isSelected = fmt == selected
            val label = fmt?.label ?: allLabel
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) colors.labelPrimary
                        else colors.labelPrimary.copy(alpha = 0.09f),
                    )
                    .clickable { onSelect(fmt) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = label,
                    style = TextStyle(
                        color = if (isSelected) colors.canvas else colors.labelPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BooksCoverFlowList(
    books: List<BookItem>,
    progressMap: Map<String, BookProgress>,
    stateKey: String,
    emptyTitle: String,
    emptyMessage: String,
    repository: BookRepository,
    navigate: (BookPlace) -> Unit,
) {
    val favIds by repository.favoriteIds.collectAsStateWithLifecycle()
    val lang = app.podium.core.designsystem.l10n.LocalAppLanguage.current
    val haptics = rememberPodiumHaptics()
    var actionBook by remember { mutableStateOf<BookItem?>(null) }

    val targetBook = actionBook
    if (targetBook != null) {
        val isFav = targetBook.id in favIds
        val readLabel = when (lang.code) {
            "ar" -> "قراءة الكتاب"
            "fr" -> "Lire le livre"
            "tr" -> "Kitabı Oku"
            "ru" -> "Читать книгу"
            else -> "Read Book"
        }
        val shareLabel = when (lang.code) {
            "ar" -> "مشاركة الكتاب (Share)"
            "fr" -> "Partager le livre"
            "tr" -> "Kitabı Paylaş"
            "ru" -> "Поделиться"
            else -> "Share Book"
        }
        val favLabel = when (lang.code) {
            "ar" -> if (isFav) "إزالة من المفضلة" else "إضافة للمفضلة"
            "fr" -> if (isFav) "Retirer des favoris" else "Ajouter aux favoris"
            "tr" -> if (isFav) "Favorilerden Çıkar" else "Favorilere Ekle"
            "ru" -> if (isFav) "Убрать из избранного" else "В избранное"
            else -> if (isFav) "Remove from Favorites" else "Add to Favorites"
        }
        val deleteLabel = when (lang.code) {
            "ar" -> "حذف الكتاب (Delete)"
            "fr" -> "Supprimer le livre"
            "tr" -> "Kitabı Sil"
            "ru" -> "Удалить книгу"
            else -> "Delete Book"
        }
        val cancelLabel = when (lang.code) {
            "ar" -> "إلغاء"
            "fr" -> "Annuler"
            "tr" -> "İptal"
            "ru" -> "Отмена"
            else -> "Cancel"
        }

        val actions = remember(targetBook, isFav, lang.code) {
            listOf(
                Triple("read", readLabel, targetBook.format.label),
                Triple("share", shareLabel, null),
                Triple("fav", favLabel, if (isFav) "★" else null),
                Triple("delete", deleteLabel, null),
                Triple("cancel", cancelLabel, null),
            )
        }
        val menuFocus = rememberFocusListState("book_action_${targetBook.id}")
        val runAction: (Int) -> Unit = { idx ->
            when (actions.getOrNull(idx)?.first) {
                "read" -> {
                    actionBook = null
                    navigate(BookPlace.Reader(targetBook.id))
                }
                "share" -> {
                    haptics.confirm()
                    actionBook = null
                    repository.shareBook(targetBook)
                }
                "fav" -> {
                    haptics.confirm()
                    repository.toggleFavorite(targetBook.id)
                    actionBook = null
                }
                "delete" -> {
                    haptics.confirm()
                    repository.deleteBook(targetBook)
                    actionBook = null
                }
                else -> {
                    actionBook = null
                }
            }
        }
        BackHandler { actionBook = null }
        ListInputEffect(menuFocus, runAction)

        FocusList(
            items = actions,
            state = menuFocus,
            key = { it.first },
            contentPadding = LocalScreenInsets.current.listPadding(),
            onActivate = runAction,
            modifier = Modifier.fillMaxSize(),
        ) { item, _, focused ->
            MenuRow(
                label = item.second,
                focused = focused,
                value = item.third,
                showChevron = item.first == "read",
            )
        }
        return
    }

    CoverFlowStage(
        items = books,
        stateKey = stateKey,
        keyOf = { it.id },
        titleOf = { it.title },
        subtitleOf = { book ->
            val p = progressMap[book.id]
            if (p != null) "${book.subtitle} • ${p.percent}%" else book.subtitle
        },
        artworkOf = { it.coverUri },
        onActivate = { book, _ ->
            navigate(BookPlace.Reader(book.id))
        },
        onLongPress = { book, _ ->
            haptics.confirm()
            actionBook = book
        },
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
    )
}

@Composable
private fun OnlineBooksCatalogScreen(
    repository: BookRepository,
    navigate: (BookPlace) -> Unit,
) {
    val online by repository.onlineBooks.collectAsStateWithLifecycle()
    val progress by repository.progressMap.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        repository.loadArabicCatalog()
    }

    BooksCoverFlowList(
        books = online,
        progressMap = progress,
        stateKey = "books_online_catalog_cf",
        emptyTitle = "Loading Arabic Library...",
        emptyMessage = "Fetching Arabic books.",
        repository = repository,
        navigate = navigate,
    )
}

@Composable
private fun IslamicBooksCatalogScreen(
    repository: BookRepository,
    navigate: (BookPlace) -> Unit,
) {
    val islamic by repository.islamicBooks.collectAsStateWithLifecycle()
    val progress by repository.progressMap.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        repository.loadIslamicCatalog()
    }

    BooksCoverFlowList(
        books = islamic,
        progressMap = progress,
        stateKey = "books_islamic_catalog_cf",
        emptyTitle = "Loading Islamic Library...",
        emptyMessage = "Fetching Hadith, Fiqh, and Seerah books.",
        repository = repository,
        navigate = navigate,
    )
}

@Composable
private fun QuranBooksCatalogScreen(
    repository: BookRepository,
    navigate: (BookPlace) -> Unit,
) {
    val quran by repository.quranBooks.collectAsStateWithLifecycle()
    val progress by repository.progressMap.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        repository.loadQuranCatalog()
    }

    BooksCoverFlowList(
        books = quran,
        progressMap = progress,
        stateKey = "books_quran_catalog_cf",
        emptyTitle = "Loading Quran & Tafsir Library...",
        emptyMessage = "Fetching Mushaf and Tafsir books.",
        repository = repository,
        navigate = navigate,
    )
}

@Composable
private fun BookSearchScreen(
    repository: BookRepository,
    navigate: (BookPlace) -> Unit,
) {
    val local by repository.localBooks.collectAsStateWithLifecycle()
    val progress by repository.progressMap.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BookItem>>(local) }

    LaunchedEffect(query, local) {
        val q = query.trim()
        val localMatches = if (q.isBlank()) {
            local
        } else {
            local.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.author.contains(q, ignoreCase = true)
            }
        }
        results = localMatches
        if (q.length >= 2) {
            delay(350)
            val onlineMatches = repository.searchOnlineBooks(q)
            results = (localMatches + onlineMatches).distinctBy { it.id }
        } else if (q.isBlank()) {
            results = (local + repository.onlineBooks.value).distinctBy { it.id }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.top),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            PaperTextField(
                value = query,
                onChange = { query = it },
                focus = requester,
                onDone = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
                placeholder = "Search Arabic & English books...",
                description = "Search Books",
                action = KeyboardAction.SEARCH,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BooksCoverFlowList(
                books = results,
                progressMap = progress,
                stateKey = "books_search_cf",
                emptyTitle = "No Matching Books",
                emptyMessage = "Search by book title or author.",
                repository = repository,
                navigate = navigate,
            )
        }
    }
}

@Composable
private fun BookmarksListScreen(
    repository: BookRepository,
    navigate: (BookPlace) -> Unit,
) {
    val bookmarks by repository.bookmarks.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current

    if (bookmarks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(
                PodiumSymbol.Lyrics,
                "No Bookmarks Yet",
                "Hold Center while reading any book to bookmark the page.",
            )
        }
        return
    }

    val focus = rememberFocusListState("books_bookmarks_list")
    val activate: (Int) -> Unit = { idx ->
        bookmarks.getOrNull(idx)?.let { bm ->
            navigate(BookPlace.Reader(bm.bookId, bm.pageIndex))
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = bookmarks,
        state = focus,
        key = { "${it.bookId}_${it.pageIndex}" },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { bm, _, focused ->
        MenuRow(
            label = bm.bookTitle,
            focused = focused,
            value = "Page ${bm.pageIndex + 1}/${bm.totalPages}",
            showChevron = true,
        )
    }
}

@Composable
private fun BookSelectFoldersScreen(
    repository: BookRepository,
) {
    val strings = LocalAppStrings.current
    val isAr = strings is app.podium.core.designsystem.l10n.ArabicStrings
    val selected by repository.selectedFolders.collectAsStateWithLifecycle()
    val custom by repository.customFolders.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    val scope = rememberCoroutineScope()

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            repository.addCustomFolderUri(uri)
            scope.launch { repository.scanLocalBooks() }
        }
    }

    val candidates = remember(custom) { repository.candidateFolders() }
    val rows = remember(candidates, selected, isAr) {
        buildList {
            add(
                Triple(
                    "__ALL__",
                    if (isAr) "جميع المجلدات (فحص تلقائي)" else "All Folders (Auto Scan)",
                    if (selected.isEmpty()) (if (isAr) "✓ مفعل" else "✓ Active") else (if (isAr) "اضغط لإعادة التعيين" else "Tap to Reset"),
                ),
            )
            add(
                Triple(
                    "__PICK__",
                    if (isAr) "اختيار مجلد مخصص..." else "Pick Custom Folder...",
                    if (isAr) "تصفح" else "Browse",
                ),
            )
            for ((path, label) in candidates) {
                val isChecked = selected.isEmpty() || path in selected
                add(Triple(path, label, if (isChecked) (if (isAr) "✓ مفعل" else "✓ On") else (if (isAr) "معطل" else "Off")))
            }
        }
    }

    val focus = rememberFocusListState("books_select_folders")
    val activate: (Int) -> Unit = { idx ->
        val row = rows.getOrNull(idx)
        if (row != null) {
            when (row.first) {
                "__ALL__" -> {
                    repository.selectAllFolders()
                    scope.launch { repository.scanLocalBooks() }
                }
                "__PICK__" -> {
                    treeLauncher.launch(null)
                }
                else -> {
                    repository.toggleSelectedFolder(row.first)
                    scope.launch { repository.scanLocalBooks() }
                }
            }
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.first },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        MenuRow(
            label = item.second,
            focused = focused,
            value = item.third,
            showChevron = item.first == "__PICK__",
        )
    }
}

@Composable
private fun BookReaderScreen(
    bookId: String,
    initialPage: Int,
    repository: BookRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val insets = LocalScreenInsets.current
    val haptics = rememberPodiumHaptics()
    val scope = rememberCoroutineScope()

    val theme by repository.theme.collectAsStateWithLifecycle()
    val fontSizeSp by repository.fontSizeSp.collectAsStateWithLifecycle()
    val localBooks by repository.localBooks.collectAsStateWithLifecycle()
    val onlineBooks by repository.onlineBooks.collectAsStateWithLifecycle()
    val rawBook = remember(bookId, localBooks, onlineBooks) { repository.findBook(bookId) }

    var book by remember(rawBook) { mutableStateOf(rawBook) }
    var isDownloading by remember { mutableStateOf(false) }
    var textPages by remember { mutableStateOf<List<String>>(emptyList()) }
    var totalPages by remember { mutableIntStateOf(1) }
    var currentPage by remember {
        val saved = repository.progressMap.value[bookId]?.pageIndex ?: 0
        mutableIntStateOf(if (initialPage >= 0) initialPage else saved)
    }
    var pageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var wheelMode by remember { mutableStateOf(BookWheelMode.SCROLL) }
    var zoom by remember(currentPage) { mutableFloatStateOf(1f) }
    var panX by remember(currentPage) { mutableFloatStateOf(0f) }
    var panY by remember(currentPage) { mutableFloatStateOf(0f) }

    var badgeText by remember { mutableStateOf<String?>(null) }
    var badgeTrigger by remember { mutableLongStateOf(0L) }
    var isReadingAloud by remember { mutableStateOf(false) }
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }
    val scrollState = rememberScrollState()

    fun showBadge(text: String) {
        badgeText = text
        badgeTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(badgeTrigger) {
        if (badgeTrigger > 0L) {
            delay(1200)
            badgeText = null
        }
    }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onError(utteranceId: String?) {
                            isReadingAloud = false
                        }
                        override fun onDone(utteranceId: String?) {
                            scope.launch {
                                if (isReadingAloud && currentPage < totalPages - 1) {
                                    currentPage++
                                } else {
                                    isReadingAloud = false
                                }
                            }
                        }
                    },
                )
            }
        }
        ttsRef = engine
        onDispose {
            isReadingAloud = false
            BookController.exitFullscreen()
            engine?.stop()
            engine?.shutdown()
        }
    }

    fun speakCurrentPage() {
        val pageText = textPages.getOrNull(currentPage).orEmpty()
        if (pageText.isBlank()) {
            showBadge("TTS available on text/EPUB books")
            isReadingAloud = false
            return
        }
        val hasArabic = pageText.any { it in '\u0600'..'\u06FF' }
        ttsRef?.language = if (hasArabic) Locale("ar") else Locale.US
        ttsRef?.speak(pageText.take(3500), TextToSpeech.QUEUE_FLUSH, null, "book_page_$currentPage")
    }

    LaunchedEffect(currentPage, isReadingAloud, textPages.size) {
        if (isReadingAloud && textPages.isNotEmpty()) {
            speakCurrentPage()
        }
    }

    // Download if online, then load pages
    LaunchedEffect(rawBook) {
        val b = rawBook ?: return@LaunchedEffect
        val ready = if (b.isOnline) {
            isDownloading = true
            val dl = repository.ensureBookDownloaded(b)
            isDownloading = false
            dl ?: b
        } else {
            b
        }
        book = ready
        if (ready.format == BookFormat.PDF || ready.format == BookFormat.CBZ) {
            val count = repository.getPdfOrCbzPageCount(ready).coerceAtLeast(1)
            totalPages = count
            currentPage = currentPage.coerceIn(0, count - 1)
        } else {
            val loaded = repository.loadTextPages(ready)
            textPages = loaded
            totalPages = loaded.size.coerceAtLeast(1)
            currentPage = currentPage.coerceIn(0, totalPages - 1)
        }
    }

    val isBitmapBook = book?.format == BookFormat.PDF || book?.format == BookFormat.CBZ

    LaunchedEffect(book, currentPage, totalPages) {
        val b = book ?: return@LaunchedEffect
        scrollState.scrollTo(0)
        if (isBitmapBook) {
            pageBitmap = repository.renderPdfOrCbzPage(b, currentPage)
        }
        repository.saveProgress(b.id, currentPage, totalPages)

        BookController.fullscreenPageBitmap = pageBitmap
        BookController.fullscreenPageText = textPages.getOrNull(currentPage).orEmpty()
        BookController.fullscreenPageTitle = b.title
        BookController.fullscreenPageFooter = "Page ${currentPage + 1} / $totalPages"
    }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                when (wheelMode) {
                    BookWheelMode.SCROLL -> {
                        val step = input.detents * 95 * (1 + kotlin.math.abs(input.velocity).roundToInt().coerceAtMost(4))
                        scope.launch {
                            scrollState.animateScrollTo((scrollState.value + step).coerceIn(0, scrollState.maxValue))
                        }
                    }
                    BookWheelMode.ZOOM -> {
                        if (isBitmapBook) {
                            val next = (zoom + input.detents * 0.16f).coerceIn(1.0f, 4.0f)
                            zoom = next
                            BookController.fullscreenZoom = next
                            showBadge("🔍 ${String.format(Locale.US, "%.1fx", next)}")
                        } else {
                            val nextSp = repository.adjustFontSize(input.detents)
                            showBadge("🔍 Font ${nextSp}sp")
                        }
                    }
                    BookWheelMode.THEME -> {
                        val nextTheme = repository.cycleTheme()
                        showBadge(nextTheme.label)
                    }
                }
                true
            }
            is PodiumInput.Press -> when (input.button) {
                WheelButton.CENTER -> {
                    wheelMode = when (wheelMode) {
                        BookWheelMode.SCROLL -> BookWheelMode.ZOOM
                        BookWheelMode.ZOOM -> BookWheelMode.THEME
                        BookWheelMode.THEME -> BookWheelMode.SCROLL
                    }
                    showBadge(wheelMode.label)
                    true
                }
                WheelButton.NEXT -> {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                        showBadge("Page ${currentPage + 1} / $totalPages")
                    } else {
                        haptics.boundary()
                    }
                    true
                }
                WheelButton.PREVIOUS -> {
                    if (currentPage > 0) {
                        currentPage--
                        showBadge("Page ${currentPage + 1} / $totalPages")
                    } else {
                        haptics.boundary()
                    }
                    true
                }
                WheelButton.PLAY_PAUSE -> {
                    if (isReadingAloud) {
                        isReadingAloud = false
                        ttsRef?.stop()
                        showBadge("🔇 Read Aloud Stopped")
                    } else {
                        isReadingAloud = true
                        showBadge("🔊 Reading Aloud...")
                        speakCurrentPage()
                    }
                    true
                }
                WheelButton.MENU -> {
                    if (BookController.isPhoneFullscreen) {
                        BookController.exitFullscreen()
                        true
                    } else {
                        onBack()
                        true
                    }
                }
            }
            is PodiumInput.LongPress -> when (input.button) {
                WheelButton.CENTER -> {
                    val b = book
                    if (b != null) {
                        val snippet = textPages.getOrNull(currentPage)?.take(80) ?: "Page ${currentPage + 1}"
                        val added = repository.toggleBookmark(b.id, b.title, currentPage, totalPages, snippet)
                        showBadge(if (added) "🔖 Page Bookmarked" else "Bookmark Removed")
                    }
                    true
                }
                WheelButton.PLAY_PAUSE -> {
                    BookController.isPhoneFullscreen = !BookController.isPhoneFullscreen
                    showBadge(if (BookController.isPhoneFullscreen) "Fullscreen" else "Exit Fullscreen")
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(PodiumSymbol.Library, "Book Not Found", "Press Menu to go back.")
        }
        return
    }

    if (isDownloading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(PodiumSymbol.Sparkle, "Downloading Book...", book?.title.orEmpty())
        }
        return
    }

    val currentText = textPages.getOrNull(currentPage).orEmpty()
    val isRtl = remember(currentText) { currentText.any { it in '\u0600'..'\u06FF' } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .padding(top = insets.top),
    ) {
        if (isBitmapBook) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp)
                    .verticalScroll(scrollState)
                    .pointerInput(currentPage) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val next = (zoom * gestureZoom).coerceIn(1f, 4f)
                            zoom = next
                            if (next > 1.02f) {
                                panX += pan.x
                                panY += pan.y
                            } else {
                                panX = 0f
                                panY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                pageBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${currentPage + 1}",
                        contentScale = ContentScale.FillWidth,
                        colorFilter = theme.pageColorFilter(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = panX
                                translationY = panY
                            },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
                    .padding(top = 8.dp, bottom = 26.dp)
                    .verticalScroll(scrollState),
            ) {
                BasicText(
                    text = currentText,
                    style = TextStyle(
                        color = theme.fg,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.52f).sp,
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Bottom status bar (page number, percent, TTS indicator)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(theme.bg.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = if (isReadingAloud) "🔊 Reading • ${book?.title.orEmpty()}" else book?.title.orEmpty(),
                style = TextStyle(color = theme.secondaryFg, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val pct = (((currentPage + 1) * 100) / totalPages.coerceAtLeast(1)).coerceIn(1, 100)
            BasicText(
                text = "${currentPage + 1}/$totalPages ($pct%)",
                style = TextStyle(color = theme.secondaryFg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        AnimatedVisibility(
            visible = badgeText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xD9161618))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                BasicText(
                    text = badgeText.orEmpty(),
                    style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
fun BookFullscreenOverlay(repository: BookRepository) {
    if (!BookController.isPhoneFullscreen) return
    val theme by repository.theme.collectAsStateWithLifecycle()
    val fontSizeSp by repository.fontSizeSp.collectAsStateWithLifecycle()
    val physicalRotation = rememberPhysicalRotation()
    val sideways = kotlin.math.abs(physicalRotation) == 90f
    val bmp = BookController.fullscreenPageBitmap
    val text = BookController.fullscreenPageText
    val isRtl = remember(text) { text.any { it in '\u0600'..'\u06FF' } }
    val scrollState = rememberScrollState()

    BackHandler {
        BookController.exitFullscreen()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .then(
                if (sideways) {
                    Modifier
                        .layout { measurable, constraints ->
                            val swapped = constraints.copy(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                            val placeable = measurable.measure(swapped)
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val x = (constraints.maxWidth - placeable.width) / 2
                                val y = (constraints.maxHeight - placeable.height) / 2
                                placeable.place(x, y)
                            }
                        }
                        .graphicsLayer { rotationZ = physicalRotation }
                } else {
                    Modifier
                }
            ),
    ) {
        if (bmp != null && text.isBlank()) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = BookController.fullscreenPageTitle,
                contentScale = ContentScale.Fit,
                colorFilter = theme.pageColorFilter(),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = BookController.fullscreenZoom
                        scaleY = BookController.fullscreenZoom
                    },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .verticalScroll(scrollState),
            ) {
                BasicText(
                    text = text,
                    style = TextStyle(
                        color = theme.fg,
                        fontSize = (fontSizeSp + 2).sp,
                        lineHeight = ((fontSizeSp + 2) * 1.55f).sp,
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(theme.bg.copy(alpha = 0.9f))
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = BookController.fullscreenPageTitle,
                style = TextStyle(color = theme.secondaryFg, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = BookController.fullscreenPageFooter,
                style = TextStyle(color = theme.secondaryFg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
