package app.podium.gallery

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.ViewGroup
import android.widget.VideoView
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import app.podium.core.designsystem.tonal.ContinuousShape
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelButton
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberFocusListState
import app.podium.core.interaction.rememberPodiumHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GalleryController {
    var fullscreenItem by mutableStateOf<GalleryMediaItem?>(null)
    var fullscreenZoom by mutableFloatStateOf(1f)

    fun toggleFullscreen(item: GalleryMediaItem, currentZoom: Float = 1f) {
        if (fullscreenItem != null) {
            fullscreenItem = null
        } else {
            fullscreenZoom = currentZoom
            fullscreenItem = item
        }
    }

    fun exitFullscreen() {
        fullscreenItem = null
    }
}

@Composable
fun GalleryScreen(
    place: GalleryPlace,
    repository: GalleryRepository,
    navigate: (GalleryPlace) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hasPerm by remember { mutableStateOf(repository.hasMediaPermission()) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPerm = result.values.any { it } || repository.hasMediaPermission()
        if (hasPerm) {
            scope.launch { repository.refreshMedia() }
        }
    }

    val context = LocalContext.current

    DisposableEffect(context, hasPerm) {
        if (!hasPerm) return@DisposableEffect onDispose {}
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch { repository.refreshMedia() }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(hasPerm, place) {
        if (!hasPerm) {
            val perms = if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permLauncher.launch(perms)
        } else if (
            place is GalleryPlace.Menu ||
            place is GalleryPlace.PhotosCoverFlow ||
            place is GalleryPlace.VideosCoverFlow ||
            place is GalleryPlace.AlbumsCoverFlow ||
            place is GalleryPlace.FoldersCoverFlow
        ) {
            repository.refreshMedia()
        }
    }

    when (place) {
        GalleryPlace.Menu -> GalleryMainMenu(
            repository = repository,
            hasPermission = hasPerm,
            onRequestPermission = {
                val perms = if (Build.VERSION.SDK_INT >= 33) {
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                permLauncher.launch(perms)
            },
            navigate = navigate,
        )
        GalleryPlace.PhotosCoverFlow -> {
            val all by repository.allItems.collectAsStateWithLifecycle()
            val trashed by repository.trashedIds.collectAsStateWithLifecycle()
            val photos = remember(all, trashed) { repository.activePhotos() }
            GalleryItemsCoverFlowScreen(
                items = photos,
                stateKey = "gallery_photos_cf",
                contextKey = "photos",
                emptyTitle = "No Photos Yet",
                emptyMessage = "Photos on your phone will appear here in Cover Flow.",
                repository = repository,
                navigate = navigate,
            )
        }
        GalleryPlace.VideosCoverFlow -> {
            val all by repository.allItems.collectAsStateWithLifecycle()
            val trashed by repository.trashedIds.collectAsStateWithLifecycle()
            val videos = remember(all, trashed) { repository.activeVideos() }
            GalleryItemsCoverFlowScreen(
                items = videos,
                stateKey = "gallery_videos_cf",
                contextKey = "videos",
                emptyTitle = "No Videos Yet",
                emptyMessage = "Videos on your phone will appear here in Cover Flow.",
                repository = repository,
                navigate = navigate,
            )
        }
        GalleryPlace.AlbumsCoverFlow -> {
            val all by repository.allItems.collectAsStateWithLifecycle()
            val trashed by repository.trashedIds.collectAsStateWithLifecycle()
            val albums = remember(all, trashed) { repository.albums() }
            CoverFlowStage(
                items = albums,
                stateKey = "gallery_albums_cf",
                keyOf = { it.id },
                titleOf = { it.title },
                subtitleOf = { it.subtitle },
                artworkOf = { it.coverArtworkUri },
                onActivate = { group, _ ->
                    navigate(GalleryPlace.GroupItemsCoverFlow(group.id, group.title, isFolder = false))
                },
                emptyTitle = "No Albums",
                emptyMessage = "Albums will appear here once photos are loaded.",
            )
        }
        GalleryPlace.FoldersCoverFlow -> {
            val all by repository.allItems.collectAsStateWithLifecycle()
            val trashed by repository.trashedIds.collectAsStateWithLifecycle()
            val folders = remember(all, trashed) { repository.folders() }
            CoverFlowStage(
                items = folders,
                stateKey = "gallery_folders_cf",
                keyOf = { it.id },
                titleOf = { it.title },
                subtitleOf = { it.subtitle },
                artworkOf = { it.coverArtworkUri },
                onActivate = { group, _ ->
                    navigate(GalleryPlace.GroupItemsCoverFlow(group.id, group.title, isFolder = true))
                },
                emptyTitle = "No Folders",
                emptyMessage = "Media folders on your device will appear here.",
            )
        }
        is GalleryPlace.GroupItemsCoverFlow -> {
            val all by repository.allItems.collectAsStateWithLifecycle()
            val trashed by repository.trashedIds.collectAsStateWithLifecycle()
            val ctxKey = if (place.isFolder) "folder:${place.groupId}" else "album:${place.groupId}"
            val items = remember(all, trashed, ctxKey) { repository.itemsForContext(ctxKey) }
            GalleryItemsCoverFlowScreen(
                items = items,
                stateKey = "gallery_group_${place.groupId}",
                contextKey = ctxKey,
                emptyTitle = place.groupTitle,
                emptyMessage = "This folder is empty.",
                repository = repository,
                navigate = navigate,
            )
        }
        GalleryPlace.Search -> GallerySearchCoverFlowScreen(
            repository = repository,
            navigate = navigate,
        )
        GalleryPlace.FavoritesCoverFlow -> {
            val all by repository.allItems.collectAsStateWithLifecycle()
            val favs by repository.favoriteIds.collectAsStateWithLifecycle()
            val trashed by repository.trashedIds.collectAsStateWithLifecycle()
            val items = remember(all, favs, trashed) { repository.activeFavorites() }
            GalleryItemsCoverFlowScreen(
                items = items,
                stateKey = "gallery_favorites_cf",
                contextKey = "favorites",
                emptyTitle = "No Favorites Yet",
                emptyMessage = "Press Center on any photo or use Edit Photo to favorite it.",
                repository = repository,
                navigate = navigate,
            )
        }
        GalleryPlace.TrashCoverFlow -> {
            val all by repository.allItems.collectAsStateWithLifecycle()
            val trashed by repository.trashedIds.collectAsStateWithLifecycle()
            val items = remember(all, trashed) { repository.trashedItems() }
            CoverFlowStage(
                items = items,
                stateKey = "gallery_trash_cf",
                keyOf = { it.id.toString() },
                titleOf = { it.name },
                subtitleOf = { "Press Center to Restore • Hold Center to Empty Trash" },
                artworkOf = { it.coverFlowArtworkUri },
                onActivate = { item, _ ->
                    repository.restoreFromTrash(item.id)
                },
                onLongPress = { _, _ ->
                    repository.emptyTrash()
                },
                emptyTitle = "Trash is Empty",
                emptyMessage = "Deleted photos and videos appear here.",
            )
        }
        GalleryPlace.TelegramSync -> GalleryTelegramSyncScreen(
            repository = repository,
        )
        is GalleryPlace.PhotoViewer -> GalleryPhotoViewerScreen(
            mediaId = place.mediaId,
            contextKey = place.contextKey,
            repository = repository,
            navigate = navigate,
            onBack = onBack,
        )
        is GalleryPlace.PhotoEditor -> GalleryPhotoEditorScreen(
            mediaId = place.mediaId,
            repository = repository,
            onBack = onBack,
        )
        is GalleryPlace.VideoPlayer -> GalleryVideoPlayerScreen(
            mediaId = place.mediaId,
            repository = repository,
        )
    }
}

private data class GalleryMenuEntry(
    val label: String,
    val value: String?,
    val leading: PodiumSymbol,
    val action: () -> Unit,
)

@Composable
private fun GalleryMainMenu(
    repository: GalleryRepository,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    navigate: (GalleryPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val all by repository.allItems.collectAsStateWithLifecycle()
    val favs by repository.favoriteIds.collectAsStateWithLifecycle()
    val trashed by repository.trashedIds.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current

    val rows = remember(strings, all, favs, trashed, hasPermission) {
        val photosCount = repository.activePhotos().size
        val videosCount = repository.activeVideos().size
        val albumsCount = repository.albums().size
        val foldersCount = repository.folders().size
        val favsCount = repository.activeFavorites().size
        val trashCount = repository.trashedItems().size
        buildList {
            if (!hasPermission) {
                add(
                    GalleryMenuEntry(
                        label = "Allow Photo & Video Access",
                        value = "Required",
                        leading = PodiumSymbol.Lock,
                        action = onRequestPermission,
                    ),
                )
            }
            add(
                GalleryMenuEntry(
                    label = strings.photos,
                    value = "$photosCount",
                    leading = PodiumSymbol.Album,
                    action = { navigate(GalleryPlace.PhotosCoverFlow) },
                ),
            )
            add(
                GalleryMenuEntry(
                    label = strings.albums,
                    value = "$albumsCount",
                    leading = PodiumSymbol.Library,
                    action = { navigate(GalleryPlace.AlbumsCoverFlow) },
                ),
            )
            add(
                GalleryMenuEntry(
                    label = strings.videos,
                    value = "$videosCount",
                    leading = PodiumSymbol.Play,
                    action = { navigate(GalleryPlace.VideosCoverFlow) },
                ),
            )
            add(
                GalleryMenuEntry(
                    label = strings.folders,
                    value = "$foldersCount",
                    leading = PodiumSymbol.Library,
                    action = { navigate(GalleryPlace.FoldersCoverFlow) },
                ),
            )
            add(
                GalleryMenuEntry(
                    label = strings.search,
                    value = null,
                    leading = PodiumSymbol.Search,
                    action = { navigate(GalleryPlace.Search) },
                ),
            )
            add(
                GalleryMenuEntry(
                    label = strings.favorites,
                    value = if (favsCount > 0) "$favsCount" else null,
                    leading = PodiumSymbol.Favorite,
                    action = { navigate(GalleryPlace.FavoritesCoverFlow) },
                ),
            )
            add(
                GalleryMenuEntry(
                    label = strings.telegramSync,
                    value = if (repository.telegramBotToken.isNotBlank()) strings.connected else strings.setup,
                    leading = PodiumSymbol.Sparkle,
                    action = { navigate(GalleryPlace.TelegramSync) },
                ),
            )
            add(
                GalleryMenuEntry(
                    label = strings.trash,
                    value = if (trashCount > 0) "$trashCount" else null,
                    leading = PodiumSymbol.Settings,
                    action = { navigate(GalleryPlace.TrashCoverFlow) },
                ),
            )
        }
    }

    val focus = rememberFocusListState("gallery_main_menu")
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
private fun GalleryItemsCoverFlowScreen(
    items: List<GalleryMediaItem>,
    stateKey: String,
    contextKey: String,
    emptyTitle: String,
    emptyMessage: String,
    repository: GalleryRepository,
    navigate: (GalleryPlace) -> Unit,
) {
    val haptics = rememberPodiumHaptics()
    CoverFlowStage(
        items = items,
        stateKey = stateKey,
        keyOf = { it.id.toString() },
        titleOf = { it.name },
        subtitleOf = { it.subtitle },
        artworkOf = { it.coverFlowArtworkUri },
        onActivate = { item, _ ->
            if (item.isVideo) {
                navigate(GalleryPlace.VideoPlayer(item.id))
            } else {
                navigate(GalleryPlace.PhotoViewer(item.id, contextKey))
            }
        },
        onLongPress = { item, _ ->
            haptics.press()
            repository.shareMedia(item)
        },
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
    )
}

@Composable
private fun GallerySearchCoverFlowScreen(
    repository: GalleryRepository,
    navigate: (GalleryPlace) -> Unit,
) {
    val all by repository.allItems.collectAsStateWithLifecycle()
    val trashed by repository.trashedIds.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    val results = remember(all, trashed, query) { repository.search(query) }

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
                placeholder = "Search photos, albums, dates...",
                description = "Search Gallery",
                action = KeyboardAction.SEARCH,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CoverFlowStage(
                items = results,
                stateKey = "gallery_search_cf",
                keyOf = { it.id.toString() },
                titleOf = { it.name },
                subtitleOf = { it.subtitle },
                artworkOf = { it.coverFlowArtworkUri },
                onActivate = { item, _ ->
                    if (item.isVideo) {
                        navigate(GalleryPlace.VideoPlayer(item.id))
                    } else {
                        navigate(GalleryPlace.PhotoViewer(item.id, "search:$query"))
                    }
                },
                onLongPress = { item, _ ->
                    repository.shareMedia(item)
                },
                emptySymbol = PodiumSymbol.Search,
                emptyTitle = "No Matching Media",
                emptyMessage = "Try searching by filename, folder, or month.",
            )
        }
    }
}

@Composable
private fun GalleryPhotoViewerScreen(
    mediaId: Long,
    contextKey: String,
    repository: GalleryRepository,
    navigate: (GalleryPlace) -> Unit,
    onBack: () -> Unit,
) {
    val all by repository.allItems.collectAsStateWithLifecycle()
    val favs by repository.favoriteIds.collectAsStateWithLifecycle()
    val trashed by repository.trashedIds.collectAsStateWithLifecycle()
    val haptics = rememberPodiumHaptics()
    val insets = LocalScreenInsets.current

    val list = remember(all, favs, trashed, contextKey) {
        val raw = repository.itemsForContext(contextKey).filter { !it.isVideo }
        if (raw.isNotEmpty()) raw else repository.activePhotos()
    }

    var currentIndex by remember(mediaId, list.size) {
        mutableIntStateOf(list.indexOfFirst { it.id == mediaId }.coerceAtLeast(0))
    }
    val currentItem = list.getOrNull(currentIndex)
    var zoom by remember(currentIndex) { mutableFloatStateOf(1f) }
    var panX by remember(currentIndex) { mutableFloatStateOf(0f) }
    var panY by remember(currentIndex) { mutableFloatStateOf(0f) }
    var badgeText by remember { mutableStateOf<String?>(null) }
    var badgeTrigger by remember { mutableLongStateOf(0L) }

    fun showBadge(msg: String) {
        badgeText = msg
        badgeTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(badgeTrigger) {
        if (badgeTrigger > 0L) {
            delay(1100)
            badgeText = null
        }
    }

    if (currentItem == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(PodiumSymbol.Album, "Photo Unavailable", "Press Menu to go back.")
        }
        return
    }

    var bitmap by remember(currentItem.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(currentItem.path) {
        bitmap = withContext(Dispatchers.IO) {
            repository.renderEditedBitmap(
                path = currentItem.path,
                maxDimPx = 1600,
                extraRotationDeg = 0f,
                flipHorizontal = false,
                brightnessOffset = 0f,
                contrastScale = 1f,
                saturationScale = 1f,
            )
        }
    }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                val nextZoom = (zoom + input.detents * 0.18f).coerceIn(1.0f, 5.0f)
                zoom = nextZoom
                if (nextZoom <= 1.02f) {
                    panX = 0f
                    panY = 0f
                }
                if (GalleryController.fullscreenItem != null) {
                    GalleryController.fullscreenZoom = nextZoom
                }
                showBadge("🔍 ${String.format(java.util.Locale.US, "%.1fx", nextZoom)}")
                true
            }
            is PodiumInput.Press -> when (input.button) {
                WheelButton.NEXT -> {
                    if (currentIndex < list.lastIndex) {
                        currentIndex++
                        if (GalleryController.fullscreenItem != null) {
                            GalleryController.fullscreenItem = list[currentIndex]
                            GalleryController.fullscreenZoom = 1f
                        }
                    } else {
                        haptics.boundary()
                    }
                    true
                }
                WheelButton.PREVIOUS -> {
                    if (currentIndex > 0) {
                        currentIndex--
                        if (GalleryController.fullscreenItem != null) {
                            GalleryController.fullscreenItem = list[currentIndex]
                            GalleryController.fullscreenZoom = 1f
                        }
                    } else {
                        haptics.boundary()
                    }
                    true
                }
                WheelButton.CENTER -> {
                    if (zoom > 1.05f) {
                        zoom = 1f
                        panX = 0f
                        panY = 0f
                        GalleryController.fullscreenZoom = 1f
                        showBadge("🔍 1.0x")
                    } else {
                        val added = repository.toggleFavorite(currentItem.id)
                        showBadge(if (added) "♥ Added to Favorites" else "Removed from Favorites")
                    }
                    true
                }
                WheelButton.MENU -> {
                    if (GalleryController.fullscreenItem != null) {
                        GalleryController.exitFullscreen()
                        true
                    } else {
                        onBack()
                        true
                    }
                }
                WheelButton.PLAY_PAUSE -> true
            }
            is PodiumInput.LongPress -> when (input.button) {
                WheelButton.CENTER -> {
                    GalleryController.exitFullscreen()
                    navigate(GalleryPlace.PhotoEditor(currentItem.id))
                    true
                }
                WheelButton.PLAY_PAUSE -> {
                    GalleryController.toggleFullscreen(currentItem, zoom)
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = insets.top)
            .pointerInput(currentIndex) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val nextZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                    zoom = nextZoom
                    if (nextZoom > 1.02f) {
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
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = currentItem.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX
                        translationY = panY
                    },
            )
        }

        // Bottom subtle info bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = "${currentIndex + 1}/${list.size} • ${currentItem.name}",
                style = TextStyle(color = Color.White, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = if (currentItem.id in favs) "♥ • Hold OK: Edit" else "Hold OK: Edit",
                style = TextStyle(color = Color(0xFFFFD60A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
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
private fun GalleryPhotoEditorScreen(
    mediaId: Long,
    repository: GalleryRepository,
    onBack: () -> Unit,
) {
    val all by repository.allItems.collectAsStateWithLifecycle()
    val favs by repository.favoriteIds.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val isAr = strings is app.podium.core.designsystem.l10n.ArabicStrings
    val item = remember(all, mediaId) { all.find { it.id == mediaId } }
    val insets = LocalScreenInsets.current
    val scope = rememberCoroutineScope()

    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(PodiumSymbol.Settings, "Photo Not Found", "Press Menu to go back.")
        }
        return
    }

    var rotationDeg by remember { mutableFloatStateOf(0f) }
    var flipH by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0f) } // -80..80
    var contrast by remember { mutableFloatStateOf(1.0f) } // 0.5..1.6
    var saturation by remember { mutableFloatStateOf(1.0f) } // 0.0..2.0
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.path, rotationDeg, flipH, brightness, contrast, saturation) {
        previewBitmap = withContext(Dispatchers.IO) {
            repository.renderEditedBitmap(
                path = item.path,
                maxDimPx = 900,
                extraRotationDeg = rotationDeg,
                flipHorizontal = flipH,
                brightnessOffset = brightness,
                contrastScale = contrast,
                saturationScale = saturation,
            )
        }
    }

    val tools = remember(rotationDeg, flipH, brightness, contrast, saturation, favs, statusMsg, isAr) {
        listOf(
            (if (isAr) "تدوير 90°" else "Rotate 90°") to "${rotationDeg.roundToInt()}°",
            (if (isAr) "عكس أفقياً" else "Flip Horizontal") to if (flipH) (if (isAr) "مفعل" else "On") else (if (isAr) "معطل" else "Off"),
            (if (isAr) "السطوع (+15 / -15)" else "Brightness (+15 / -15)") to "${brightness.roundToInt()}",
            (if (isAr) "التباين" else "Contrast") to "${(contrast * 100).roundToInt()}%",
            (if (isAr) "المؤثر: أبيض وأسود / زاهٍ / عادي" else "Filter: B&W / Vivid / Normal") to when {
                saturation == 0f -> if (isAr) "أبيض وأسود" else "B&W"
                saturation > 1.3f -> if (isAr) "زاهٍ" else "Vivid"
                else -> if (isAr) "عادي" else "Normal"
            },
            (if (isAr) "حفظ نسخة معدلة" else "Save Edited Copy") to (statusMsg ?: "Pictures/CHORT"),
            (if (isAr) "إرسال إلى تيليجرام" else "Send to Telegram") to if (repository.telegramBotToken.isNotBlank()) (if (isAr) "إرسال" else "Send") else (if (isAr) "قم بإعداد الرمز أولاً" else "Setup Token First"),
            (if (isAr) "المفضلة" else "Favorite") to if (item.id in favs) (if (isAr) "♥ نعم" else "♥ Yes") else (if (isAr) "لا" else "No"),
            (if (isAr) "نقل إلى سلة المحذوفات" else "Move to Trash") to (if (isAr) "حذف" else "Delete"),
        )
    }

    val focus = rememberFocusListState("gallery_photo_editor_${item.id}")
    val activate: (Int) -> Unit = { idx ->
        when (idx) {
            0 -> rotationDeg = (rotationDeg + 90f) % 360f
            1 -> flipH = !flipH
            2 -> brightness = if (brightness >= 45f) -45f else brightness + 15f
            3 -> contrast = when {
                contrast < 1.0f -> 1.0f
                contrast < 1.25f -> 1.25f
                contrast < 1.45f -> 1.45f
                else -> 0.8f
            }
            4 -> saturation = when {
                saturation == 1.0f -> 0f
                saturation == 0f -> 1.5f
                else -> 1.0f
            }
            5 -> {
                statusMsg = "Saving..."
                scope.launch {
                    val saved = repository.saveEditedPhoto(
                        source = item,
                        extraRotationDeg = rotationDeg,
                        flipHorizontal = flipH,
                        brightnessOffset = brightness,
                        contrastScale = contrast,
                        saturationScale = saturation,
                    )
                    repository.refreshMedia()
                    statusMsg = if (saved != null) "✓ Saved!" else "Failed"
                }
            }
            6 -> {
                statusMsg = "Uploading to Telegram..."
                scope.launch {
                    val ok = repository.sendToTelegram(item)
                    statusMsg = if (ok) "✓ Sent to Telegram!" else "Check Bot Token"
                }
            }
            7 -> repository.toggleFavorite(item.id)
            8 -> {
                repository.moveToTrash(item.id)
                onBack()
            }
        }
    }
    ListInputEffect(focus, activate)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.top),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            previewBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Edited preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        }
        FocusList(
            items = tools,
            state = focus,
            key = { it.first },
            contentPadding = insets.listPadding(),
            onActivate = activate,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { pair, _, focused ->
            MenuRow(
                label = pair.first,
                focused = focused,
                value = pair.second,
                showChevron = false,
            )
        }
    }
}

@Composable
private fun GalleryVideoPlayerScreen(
    mediaId: Long,
    repository: GalleryRepository,
) {
    val all by repository.allItems.collectAsStateWithLifecycle()
    val item = remember(all, mediaId) { all.find { it.id == mediaId } }
    val context = LocalContext.current
    val insets = LocalScreenInsets.current
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(PodiumSymbol.Play, "Video Unavailable", "Press Menu to go back.")
        }
        return
    }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val dir = if (input.detents > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                true
            }
            is PodiumInput.Press -> when (input.button) {
                WheelButton.PLAY_PAUSE, WheelButton.CENTER -> {
                    videoViewRef?.let { vv ->
                        if (vv.isPlaying) vv.pause() else vv.start()
                    }
                    true
                }
                WheelButton.NEXT -> {
                    videoViewRef?.let { vv ->
                        vv.seekTo((vv.currentPosition + 10_000).coerceAtMost(vv.duration.coerceAtLeast(0)))
                    }
                    true
                }
                WheelButton.PREVIOUS -> {
                    videoViewRef?.let { vv ->
                        vv.seekTo((vv.currentPosition - 10_000).coerceAtLeast(0))
                    }
                    true
                }
                else -> false
            }
            is PodiumInput.LongPress -> when (input.button) {
                WheelButton.PLAY_PAUSE -> {
                    GalleryController.toggleFullscreen(item)
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = insets.top),
        contentAlignment = Alignment.Center,
    ) {
        if (GalleryController.fullscreenItem == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(item.path)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                        videoViewRef = this
                    }
                },
            )
        }
    }
}

@Composable
private fun GalleryTelegramSyncScreen(
    repository: GalleryRepository,
) {
    val context = LocalContext.current
    val insets = LocalScreenInsets.current
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val strings = LocalAppStrings.current
    val haptics = rememberPodiumHaptics()
    val scope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val tokenReq = remember { FocusRequester() }
    val chatReq = remember { FocusRequester() }
    var botToken by remember { mutableStateOf(repository.telegramBotToken) }
    var chatId by remember { mutableStateOf(repository.telegramChatId) }
    var status by remember { mutableStateOf("Configure your Telegram Bot Token & Chat ID to back up photos.") }

    fun readClipboardText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = insets.top, start = 14.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PodiumText("TeleDrop Telegram Sync", type.rowFocused, colors.labelPrimary)
        PaperTextField(
            value = botToken,
            onChange = {
                botToken = it
                repository.telegramBotToken = it
            },
            focus = tokenReq,
            onDone = {
                focusManager.clearFocus()
                keyboard?.hide()
            },
            placeholder = "Telegram Bot Token (123456:ABC...)",
            description = "Telegram Bot Token",
            action = KeyboardAction.DONE,
        )
        TeleDropEntryButton(
            label = strings.paste,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val pasted = readClipboardText()
                if (pasted != null) {
                    botToken = pasted
                    repository.telegramBotToken = pasted
                    status = "Bot Token pasted."
                    haptics.press()
                } else {
                    status = "Clipboard is empty."
                    haptics.reject()
                }
            },
        )
        PaperTextField(
            value = chatId,
            onChange = {
                chatId = it
                repository.telegramChatId = it
            },
            focus = chatReq,
            onDone = {
                focusManager.clearFocus()
                keyboard?.hide()
            },
            placeholder = "Telegram Chat ID (e.g. 987654321)",
            description = "Telegram Chat ID",
            action = KeyboardAction.DONE,
        )
        TeleDropEntryButton(
            label = strings.paste,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val pasted = readClipboardText()
                if (pasted != null) {
                    chatId = pasted
                    repository.telegramChatId = pasted
                    status = "Chat ID pasted."
                    haptics.press()
                } else {
                    status = "Clipboard is empty."
                    haptics.reject()
                }
            },
        )
        TeleDropEntryButton(
            label = "Sync Favorites / Recent to Telegram",
            primary = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                haptics.press()
                val favs = repository.activeFavorites().ifEmpty { repository.activePhotos().take(3) }
                if (favs.isEmpty()) {
                    status = "No photos found to sync."
                } else {
                    status = "Uploading ${favs.size} item(s) to Telegram..."
                    scope.launch {
                        var sent = 0
                        for (item in favs.take(5)) {
                            if (repository.sendToTelegram(item)) sent++
                        }
                        status = if (sent > 0) "Uploaded $sent item(s) to Telegram." else "Upload failed. Check Token & Chat ID."
                    }
                }
            },
        )
        PodiumText(status, type.footnote, colors.labelSecondary, maxLines = 3)
    }
}

@Composable
private fun TeleDropEntryButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    Box(
        modifier
            .height(48.dp)
            .background(if (primary) colors.highlight else colors.canvasRaised, ContinuousShape(14.dp))
            .then(if (primary) Modifier else Modifier.border(1.dp, colors.separator, ContinuousShape(14.dp)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PodiumText(label, type.rowFocused, if (primary) colors.onHighlight else colors.labelPrimary)
    }
}

@Composable
fun GalleryFullscreenOverlay(repository: GalleryRepository) {
    val item = GalleryController.fullscreenItem ?: return
    val zoom = GalleryController.fullscreenZoom
    val physicalRotation = rememberPhysicalRotation()
    val sideways = kotlin.math.abs(physicalRotation) == 90f

    BackHandler {
        GalleryController.exitFullscreen()
    }

    var bitmap by remember(item.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(item.path, item.isVideo) {
        if (!item.isVideo) {
            bitmap = withContext(Dispatchers.IO) {
                repository.renderEditedBitmap(
                    path = item.path,
                    maxDimPx = 2048,
                    extraRotationDeg = 0f,
                    flipHorizontal = false,
                    brightnessOffset = 0f,
                    contrastScale = 1f,
                    saturationScale = 1f,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { GalleryController.exitFullscreen() },
        contentAlignment = Alignment.Center,
    ) {
        val innerMod = Modifier
            .fillMaxSize()
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
            )

        if (item.isVideo) {
            AndroidView(
                modifier = innerMod,
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setVideoPath(item.path)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
            )
        } else {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = innerMod.graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    },
                )
            }
        }
    }
}
