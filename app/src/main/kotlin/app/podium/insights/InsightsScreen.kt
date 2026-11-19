package app.podium.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.AppGraph
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.tonal.ContinuousShape
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelButton
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberPodiumHaptics
import app.podium.feature.library.LibraryState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private sealed interface InsightsCardPage {
    data object Overview : InsightsCardPage
    data class Category(val category: InsightsCategory) : InsightsCardPage
}

private data class LiveLibraryMetrics(
    val musicSongsCount: Int,
    val downloadsCount: Int,
    val ytMusicLikedCount: Int,
    val ytMusicRecent: List<Pair<String, String>>,
    val soundCloudUsername: String?,
    val soundCloudLikesCount: Int,
    val soundCloudPlaylistsCount: Int,
    val radioFavoritesCount: Int,
    val radioRecents: List<Pair<String, String>>,
    val podcastSubsCount: Int,
    val podcastRecents: List<Pair<String, String>>,
    val quranBookmarksCount: Int,
    val quranLastReadTitle: String?,
    val booksTotalCount: Int,
    val booksFavoritesCount: Int,
    val booksInProgressCount: Int,
    val booksRecentTitles: List<Pair<String, String>>,
    val galleryPhotosCount: Int,
    val galleryVideosCount: Int,
    val galleryFavoritesCount: Int,
)

@Composable
fun InsightsScreen(
    graph: AppGraph,
) {
    val insets = LocalScreenInsets.current
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val motion = PodiumTheme.motion
    val strings = LocalAppStrings.current
    val isAr = strings.settings == "الإعدادات"
    val haptics = rememberPodiumHaptics()
    val scope = rememberCoroutineScope()

    var period by remember { mutableStateOf(InsightsPeriod.ALL_TIME) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val statsVersion by graph.insightsRepository.version.collectAsStateWithLifecycle()
    val overall = remember(statsVersion, period) { graph.insightsRepository.computeOverview(period) }

    // Collect live existing data from all repositories
    val libState by graph.library.songs.collectAsStateWithLifecycle()
    val downloads by graph.downloads.completedTracks.collectAsStateWithLifecycle()
    val ytLiked by graph.online.likedIds.collectAsStateWithLifecycle()
    val ytRecent by graph.online.recentlyPlayed.collectAsStateWithLifecycle(initialValue = emptyList())
    val scUser by graph.soundcloudRepository.currentUser.collectAsStateWithLifecycle()
    val scLikes by graph.soundcloudRepository.likedTracks.collectAsStateWithLifecycle()
    val scPlaylists by graph.soundcloudRepository.userPlaylists.collectAsStateWithLifecycle()
    val radioFavs by graph.radioRepository.favorites.collectAsStateWithLifecycle()
    val radioRecents by graph.radioRepository.recents.collectAsStateWithLifecycle()
    val podcastSubs by graph.podcastRepository.subscriptions.collectAsStateWithLifecycle()
    val podcastRecents by graph.podcastRepository.recentEpisodes.collectAsStateWithLifecycle()
    val quranBookmarks by graph.quranRepository.bookmarks.collectAsStateWithLifecycle()
    val quranLastRead by graph.quranRepository.lastRead.collectAsStateWithLifecycle()
    val localBooks by graph.bookRepository.localBooks.collectAsStateWithLifecycle()
    val bookFavs by graph.bookRepository.favoriteIds.collectAsStateWithLifecycle()
    val bookProgress by graph.bookRepository.progressMap.collectAsStateWithLifecycle()
    val galleryAll by graph.galleryRepository.allItems.collectAsStateWithLifecycle()
    val galleryFavs by graph.galleryRepository.favoriteIds.collectAsStateWithLifecycle()

    val live = remember(
        libState, downloads, ytLiked, ytRecent, scUser, scLikes, scPlaylists,
        radioFavs, radioRecents, podcastSubs, podcastRecents, quranBookmarks, quranLastRead,
        localBooks, bookFavs, bookProgress, galleryAll, galleryFavs,
    ) {
        val libTracks = (libState as? LibraryState.Ready)?.tracks.orEmpty()
        val booksRecent = bookProgress.entries
            .sortedByDescending { it.value.updatedAt }
            .take(4)
            .mapNotNull { (bookId, prog) ->
                val b = graph.bookRepository.findBook(bookId) ?: return@mapNotNull null
                b.title to "${prog.percent}%"
            }
        LiveLibraryMetrics(
            musicSongsCount = libTracks.size,
            downloadsCount = downloads.size,
            ytMusicLikedCount = ytLiked.size,
            ytMusicRecent = ytRecent.take(4).map { it.title to it.artistDisplay },
            soundCloudUsername = scUser?.username,
            soundCloudLikesCount = maxOf(scLikes.size, scUser?.likesCount ?: 0),
            soundCloudPlaylistsCount = maxOf(scPlaylists.size, scUser?.playlistCount ?: 0),
            radioFavoritesCount = radioFavs.size,
            radioRecents = radioRecents.take(4).map { it.name to it.subtitle },
            podcastSubsCount = podcastSubs.size,
            podcastRecents = podcastRecents.take(4).map { it.title to it.podcastTitle },
            quranBookmarksCount = quranBookmarks.size,
            quranLastReadTitle = quranLastRead?.let { "${it.surahName} • Ayah ${it.ayahNumber}" },
            booksTotalCount = localBooks.size,
            booksFavoritesCount = bookFavs.size,
            booksInProgressCount = bookProgress.size,
            booksRecentTitles = booksRecent,
            galleryPhotosCount = galleryAll.count { !it.isVideo },
            galleryVideosCount = galleryAll.count { it.isVideo },
            galleryFavoritesCount = galleryFavs.size,
        )
    }

    val pages = remember {
        listOf(InsightsCardPage.Overview) + InsightsCategory.entries.map { InsightsCardPage.Category(it) }
    }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val position = remember { Animatable(0f) }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex.coerceIn(0, pages.lastIndex).toFloat()
        if (motion.reduced) position.snapTo(target)
        else position.animateTo(target, spring(dampingRatio = 0.88f, stiffness = 360f))
    }

    fun stepCard(delta: Int) {
        val next = (focusedIndex + delta).coerceIn(0, pages.lastIndex)
        if (next != focusedIndex) {
            focusedIndex = next
            haptics.step()
        } else {
            haptics.boundary()
        }
    }

    fun cyclePeriod() {
        val entries = InsightsPeriod.entries
        period = entries[(entries.indexOf(period) + 1) % entries.size]
        haptics.press()
    }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        if (showResetConfirm) {
            when (input) {
                is PodiumInput.Press -> when (input.button) {
                    WheelButton.CENTER -> {
                        graph.insightsRepository.resetAll()
                        showResetConfirm = false
                        haptics.press()
                        true
                    }
                    WheelButton.MENU -> {
                        showResetConfirm = false
                        haptics.press()
                        true
                    }
                    else -> false
                }
                else -> false
            }
        } else {
            when (input) {
                is PodiumInput.Rotate -> {
                    stepCard(if (input.detents > 0) 1 else -1)
                    true
                }
                is PodiumInput.Press -> when (input.button) {
                    WheelButton.NEXT -> {
                        stepCard(1)
                        true
                    }
                    WheelButton.PREVIOUS -> {
                        stepCard(-1)
                        true
                    }
                    WheelButton.CENTER -> {
                        cyclePeriod()
                        true
                    }
                    else -> false
                }
                is PodiumInput.LongPress -> when (input.button) {
                    WheelButton.CENTER -> {
                        showResetConfirm = true
                        haptics.press()
                        true
                    }
                    else -> false
                }
                else -> false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.top, bottom = insets.bottom),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Period Selector Bar + Reset button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (p in InsightsPeriod.entries) {
                    val selected = p == period
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ContinuousShape(10.dp))
                            .background(if (selected) colors.highlight else colors.canvasRaised)
                            .then(if (selected) Modifier else Modifier.border(1.dp, colors.separator, ContinuousShape(10.dp)))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    period = p
                                    haptics.press()
                                },
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PodiumText(
                            text = if (isAr) p.labelAr else p.labelEn,
                            style = type.footnote,
                            color = if (selected) colors.onHighlight else colors.labelSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(ContinuousShape(10.dp))
                        .background(colors.canvasRaised)
                        .border(1.dp, colors.separator, ContinuousShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                showResetConfirm = true
                                haptics.press()
                            },
                        )
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PodiumText(
                        text = "↺",
                        style = type.footnote,
                        color = colors.labelSecondary,
                    )
                }
            }

            // Large 3D Cover Flow Stage for Report Cards
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val cardW = (maxWidth * 0.86f).coerceAtLeast(220.dp)
                val cardH = (maxHeight * 0.94f).coerceAtLeast(220.dp)
                val spacingDp = cardW.value * 0.84f
                val pxPerDp = LocalDensity.current.density
                val middle by remember {
                    derivedStateOf { position.value.roundToInt().coerceIn(0, pages.lastIndex) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            state = rememberDraggableState { dx ->
                                val next = (position.value - dx / (spacingDp * pxPerDp)).coerceIn(0f, pages.lastIndex.toFloat())
                                scope.launch(start = CoroutineStart.UNDISPATCHED) { position.snapTo(next) }
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = { pixelsPerSecond ->
                                val speed = -pixelsPerSecond / (spacingDp * pxPerDp)
                                val target = (position.value + speed * 0.14f).roundToInt().coerceIn(0, pages.lastIndex)
                                scope.launch {
                                    position.animateTo(
                                        target.toFloat(),
                                        spring(dampingRatio = 0.88f, stiffness = 360f),
                                        initialVelocity = speed,
                                    )
                                }
                                focusedIndex = target
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val window = (middle - 2)..(middle + 2)
                    for (i in window) {
                        val page = pages.getOrNull(i) ?: continue
                        val isCenter = i == focusedIndex
                        Box(
                            modifier = Modifier
                                .zIndex(-abs(i - middle).toFloat())
                                .size(width = cardW, height = cardH)
                                .graphicsLayer {
                                    val d = i - position.value
                                    val near = d.coerceIn(-1f, 1f)
                                    val x = if (abs(d) <= 1f) d * spacingDp else sign(d) * (spacingDp + (abs(d) - 1f) * cardW.value * 0.25f)
                                    translationX = x * density
                                    rotationY = -near * 24f
                                    cameraDistance = 16f * density
                                    val s = 1f - 0.10f * abs(near)
                                    scaleX = s
                                    scaleY = s
                                    alpha = (1f - 0.42f * abs(near)).coerceIn(0.25f, 1f)
                                }
                                .pointerInput(i, isCenter) {
                                    detectTapGestures(
                                        onTap = {
                                            if (!isCenter) {
                                                focusedIndex = i
                                                haptics.step()
                                            }
                                        },
                                        onLongPress = {
                                            showResetConfirm = true
                                            haptics.press()
                                        },
                                    )
                                },
                        ) {
                            when (page) {
                                InsightsCardPage.Overview -> OverviewReportCard(
                                    overall = overall,
                                    live = live,
                                    period = period,
                                    isAr = isAr,
                                    isFocused = isCenter,
                                )
                                is InsightsCardPage.Category -> {
                                    val catStats = overall.byCategory.firstOrNull { it.category == page.category }
                                        ?: CategoryStats(page.category, 0L, 0, emptyList())
                                    CategoryReportCard(
                                        stats = catStats,
                                        totalOverallMs = overall.totalDurationMs,
                                        live = live,
                                        period = period,
                                        isAr = isAr,
                                        isFocused = isCenter,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Page Dots & Label
            val activeTitle = when (val p = pages.getOrNull(focusedIndex)) {
                is InsightsCardPage.Overview -> if (isAr) "الكل (ملخّص)" else "Overview (All)"
                is InsightsCardPage.Category -> if (isAr) p.category.titleAr else p.category.titleEn
                null -> ""
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pages.forEachIndexed { idx, p ->
                        val active = idx == focusedIndex
                        val dotColor = if (active) {
                            when (p) {
                                InsightsCardPage.Overview -> colors.highlight
                                is InsightsCardPage.Category -> Color(p.category.accentHex)
                            }
                        } else {
                            colors.labelTertiary.copy(alpha = 0.45f)
                        }
                        Box(
                            modifier = Modifier
                                .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        focusedIndex = idx
                                        haptics.step()
                                    },
                                ),
                        )
                    }
                }
                PodiumText(
                    text = "${focusedIndex + 1} / ${pages.size}  •  $activeTitle",
                    style = type.footnote,
                    color = colors.labelSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Reset Confirmation Dialog (Themed)
        if (showResetConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showResetConfirm = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .background(colors.canvasRaised, ContinuousShape(18.dp))
                        .border(1.dp, colors.separator, ContinuousShape(18.dp))
                        .clickable(enabled = false) {}
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PodiumText(
                        text = if (isAr) "تصفير الإحصائيات؟" else "Reset Insights?",
                        style = type.rowFocused,
                        color = colors.labelPrimary,
                    )
                    PodiumText(
                        text = if (isAr) "سيتم مسح سجلّ أوقات الاستماع والقراءة والمشاهدة المحفوظة." else "This clears all tracked listening, reading, and viewing time history.",
                        style = type.footnote,
                        color = colors.labelSecondary,
                        maxLines = 3,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .background(colors.canvas, ContinuousShape(12.dp))
                                .border(1.dp, colors.separator, ContinuousShape(12.dp))
                                .clickable { showResetConfirm = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            PodiumText(if (isAr) "إلغاء" else "Cancel", type.row, colors.labelPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .background(colors.highlight, ContinuousShape(12.dp))
                                .clickable {
                                    graph.insightsRepository.resetAll()
                                    showResetConfirm = false
                                    haptics.press()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            PodiumText(if (isAr) "تصفير" else "Reset", type.rowFocused, colors.onHighlight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewReportCard(
    overall: OverallStats,
    live: LiveLibraryMetrics,
    period: InsightsPeriod,
    isAr: Boolean,
    isFocused: Boolean,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val accent = colors.highlight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasRaised, ContinuousShape(20.dp))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) accent.copy(alpha = 0.65f) else colors.separator,
                shape = ContinuousShape(20.dp),
            )
            .clip(ContinuousShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                PodiumText(
                    text = if (isAr) "الملخّص الشامل" else "All Activity Overview",
                    style = type.rowFocused,
                    color = colors.labelPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                PodiumText(
                    text = if (isAr) period.labelAr else period.labelEn,
                    style = type.footnote,
                    color = accent,
                )
            }
        }

        // Hero Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InsightMetricTile(
                value = formatInsightsDuration(overall.totalDurationMs),
                label = if (isAr) "الوقت الإجمالي" else "Total Time",
                accent = accent,
                modifier = Modifier.weight(1.2f),
            )
            InsightMetricTile(
                value = "${overall.totalSessions}",
                label = if (isAr) "العمليات" else "Sessions",
                accent = null,
                modifier = Modifier.weight(0.9f),
            )
            InsightMetricTile(
                value = "${overall.currentStreakDays}d",
                label = if (isAr) "سلسلة الأيام" else "Streak",
                accent = null,
                modifier = Modifier.weight(0.9f),
            )
        }

        // Category Breakdown Stacked Bar Chart
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PodiumText(
                    text = if (isAr) "توزيع الوقت حسب القسم" else "Activity Breakdown",
                    style = type.caption,
                    color = colors.labelSecondary,
                )
                overall.topCategory?.let { topCat ->
                    PodiumText(
                        text = (if (isAr) "الأكثر: ${topCat.titleAr}" else "Top: ${topCat.titleEn}"),
                        style = type.footnote,
                        color = Color(topCat.accentHex),
                    )
                }
            }

            // Stacked proportional bar
            val activeCats = overall.byCategory.filter { it.durationMs > 0L }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.separator.copy(alpha = 0.35f)),
            ) {
                if (activeCats.isNotEmpty() && overall.totalDurationMs > 0L) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (c in activeCats) {
                            val weight = (c.durationMs.toFloat() / overall.totalDurationMs.toFloat()).coerceAtLeast(0.03f)
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .background(Color(c.category.accentHex)),
                            )
                        }
                    }
                }
            }

            // Top 4 active categories with mini progress bars
            val shown = activeCats.take(4)
            if (shown.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (cs in shown) {
                        val frac = if (overall.totalDurationMs > 0L) {
                            (cs.durationMs.toFloat() / overall.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val pct = (frac * 100f).roundToInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(cs.category.accentHex)),
                            )
                            PodiumText(
                                text = if (isAr) cs.category.titleAr else cs.category.titleEn,
                                style = type.footnote,
                                color = colors.labelPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            PodiumText(
                                text = "${formatInsightsDuration(cs.durationMs)} • $pct%",
                                style = type.footnote,
                                color = colors.labelSecondary,
                            )
                        }
                    }
                }
            } else {
                PodiumText(
                    text = if (isAr) "ابدأ بالاستماع أو القراءة أو المشاهدة لتظهر الإحصائيات الحية هنا." else "Listen, read, or watch across any tab to see your live breakdown here.",
                    style = type.footnote,
                    color = colors.labelTertiary,
                    maxLines = 2,
                )
            }
        }

        // Combined Library Snapshot (Existing App Data)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PodiumText(
                text = if (isAr) "محتوى تطبيقك الكل" else "Your Library Snapshot",
                style = type.caption,
                color = colors.labelSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InsightMetricTile(
                    value = "${live.musicSongsCount}",
                    label = if (isAr) "أغاني" else "Songs",
                    accent = null,
                    modifier = Modifier.weight(1f),
                )
                InsightMetricTile(
                    value = "${live.booksTotalCount}",
                    label = if (isAr) "كتب" else "Books",
                    accent = null,
                    modifier = Modifier.weight(1f),
                )
                InsightMetricTile(
                    value = "${live.galleryPhotosCount + live.galleryVideosCount}",
                    label = if (isAr) "صور/فيديو" else "Media",
                    accent = null,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InsightMetricTile(
                    value = "${live.downloadsCount}",
                    label = if (isAr) "محمّلة" else "Downloads",
                    accent = null,
                    modifier = Modifier.weight(1f),
                )
                InsightMetricTile(
                    value = "${live.radioFavoritesCount}",
                    label = if (isAr) "راديو ★" else "Radio ★",
                    accent = null,
                    modifier = Modifier.weight(1f),
                )
                InsightMetricTile(
                    value = "${live.quranBookmarksCount}",
                    label = if (isAr) "علامات قرآن" else "Quran ★",
                    accent = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Top Overall Activity
        if (overall.topOverallItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PodiumText(
                    text = if (isAr) "الأكثر نشاطاً" else "Most Active Items",
                    style = type.caption,
                    color = colors.labelSecondary,
                )
                overall.topOverallItems.take(3).forEachIndexed { idx, (cat, item) ->
                    InsightTopItemRow(
                        rank = idx + 1,
                        title = item.title,
                        subtitle = if (isAr) cat.titleAr else cat.titleEn,
                        trailing = if (item.durationMs > 0L) formatInsightsDuration(item.durationMs) else "${item.count}×",
                        accent = Color(cat.accentHex),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryReportCard(
    stats: CategoryStats,
    totalOverallMs: Long,
    live: LiveLibraryMetrics,
    period: InsightsPeriod,
    isAr: Boolean,
    isFocused: Boolean,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val cat = stats.category
    val accent = Color(cat.accentHex)
    val shareFrac = if (totalOverallMs > 0L) (stats.durationMs.toFloat() / totalOverallMs.toFloat()).coerceIn(0f, 1f) else 0f
    val sharePct = (shareFrac * 100f).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasRaised, ContinuousShape(20.dp))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) accent.copy(alpha = 0.72f) else colors.separator,
                shape = ContinuousShape(20.dp),
            )
            .clip(ContinuousShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Category Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                PodiumText(
                    text = if (isAr) cat.titleAr else cat.titleEn,
                    style = type.rowFocused,
                    color = colors.labelPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                PodiumText(
                    text = "$sharePct% ${if (isAr) "من وقتك" else "of time"}",
                    style = type.footnote,
                    color = accent,
                )
            }
        }

        // Time + Sessions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InsightMetricTile(
                value = formatInsightsDuration(stats.durationMs),
                label = when (cat) {
                    InsightsCategory.YOUTUBE -> if (isAr) "وقت المشاهدة" else "Watch Time"
                    InsightsCategory.BOOKS -> if (isAr) "وقت القراءة" else "Reading Time"
                    InsightsCategory.GALLERY -> if (isAr) "وقت التصفّح" else "Viewing Time"
                    else -> if (isAr) "وقت الاستماع" else "Listening Time"
                },
                accent = accent,
                modifier = Modifier.weight(1.2f),
            )
            InsightMetricTile(
                value = "${stats.sessionsCount}",
                label = when (cat) {
                    InsightsCategory.YOUTUBE -> if (isAr) "فيديوهات" else "Videos"
                    InsightsCategory.BOOKS -> if (isAr) "جلسات قراءة" else "Sessions"
                    InsightsCategory.GALLERY -> if (isAr) "معاينات" else "Views"
                    InsightsCategory.QURAN -> if (isAr) "سور" else "Surahs"
                    InsightsCategory.RADIO -> if (isAr) "محطّات" else "Tunes"
                    else -> if (isAr) "تشغيل" else "Plays"
                },
                accent = null,
                modifier = Modifier.weight(0.8f),
            )
        }

        // Visual Share Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.separator.copy(alpha = 0.35f)),
        ) {
            if (shareFrac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shareFrac.coerceAtLeast(0.04f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent.copy(alpha = 0.75f), accent),
                            ),
                        ),
                )
            }
        }

        // Category-specific Live Metrics from App Repositories
        val liveTiles: List<Pair<String, String>> = when (cat) {
            InsightsCategory.MUSIC -> listOf(
                "${live.musicSongsCount}" to (if (isAr) "أغاني المكتبة" else "Library Songs"),
                "${live.downloadsCount}" to (if (isAr) "محمّلة Offline" else "Downloaded"),
            )
            InsightsCategory.YT_MUSIC -> listOf(
                "${live.ytMusicLikedCount}" to (if (isAr) "الإعجابات" else "Liked Songs"),
                "${live.ytMusicRecent.size}" to (if (isAr) "الأخيرة" else "Recent"),
            )
            InsightsCategory.YOUTUBE -> listOf(
                (if (isAr) period.labelAr else period.labelEn) to (if (isAr) "الفترة" else "Period"),
                "${stats.topItems.size}" to (if (isAr) "فيديوهات مسجّلة" else "Tracked Videos"),
            )
            InsightsCategory.SOUNDCLOUD -> listOf(
                "${live.soundCloudLikesCount}" to (if (isAr) "الإعجابات" else "Likes"),
                "${live.soundCloudPlaylistsCount}" to (if (isAr) "قوائم التشغيل" else "Playlists"),
            )
            InsightsCategory.RADIO -> listOf(
                "${live.radioFavoritesCount}" to (if (isAr) "المفضّلة" else "Favorites"),
                "${live.radioRecents.size}" to (if (isAr) "الأخيرة" else "Recent"),
            )
            InsightsCategory.PODCASTS -> listOf(
                "${live.podcastSubsCount}" to (if (isAr) "الاشتراكات" else "Subscribed"),
                "${live.podcastRecents.size}" to (if (isAr) "الحلقات الأخيرة" else "Recent Episodes"),
            )
            InsightsCategory.QURAN -> listOf(
                "${live.quranBookmarksCount}" to (if (isAr) "العلامات" else "Bookmarks"),
                (live.quranLastReadTitle ?: "—") to (if (isAr) "آخر قراءة" else "Last Read"),
            )
            InsightsCategory.BOOKS -> listOf(
                "${live.booksTotalCount}" to (if (isAr) "الكتب" else "Books"),
                "${live.booksInProgressCount}" to (if (isAr) "قيد القراءة" else "In Progress"),
                "${live.booksFavoritesCount}" to (if (isAr) "المفضّلة" else "Favorites"),
            )
            InsightsCategory.GALLERY -> listOf(
                "${live.galleryPhotosCount}" to (if (isAr) "صور" else "Photos"),
                "${live.galleryVideosCount}" to (if (isAr) "فيديوهات" else "Videos"),
                "${live.galleryFavoritesCount}" to (if (isAr) "المفضّلة" else "Favorites"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((v, lbl) in liveTiles) {
                InsightMetricTile(
                    value = v,
                    label = lbl,
                    accent = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Top Items (Real-time tracked, falling back to existing recent items if empty)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PodiumText(
                text = when (cat) {
                    InsightsCategory.YOUTUBE -> if (isAr) "أكثر الفيديوهات مشاهدة" else "Top Watched Videos"
                    InsightsCategory.BOOKS -> if (isAr) "الكتب المقروءة" else "Top Read Books"
                    InsightsCategory.GALLERY -> if (isAr) "الأكثر معاينة" else "Most Viewed Media"
                    InsightsCategory.QURAN -> if (isAr) "أكثر السور استماعاً وقراءة" else "Most Read & Listened Surahs"
                    InsightsCategory.RADIO -> if (isAr) "أكثر المحطّات استماعاً" else "Top Radio Stations"
                    else -> if (isAr) "الأكثر تشغيلاً" else "Top Played"
                },
                style = type.caption,
                color = colors.labelSecondary,
            )

            if (stats.topItems.isNotEmpty()) {
                stats.topItems.take(4).forEachIndexed { idx, item ->
                    InsightTopItemRow(
                        rank = idx + 1,
                        title = item.title,
                        subtitle = item.subtitle,
                        trailing = if (item.durationMs > 0L) formatInsightsDuration(item.durationMs) else "${item.count}×",
                        accent = accent,
                    )
                }
            } else {
                val fallback: List<Pair<String, String>> = when (cat) {
                    InsightsCategory.YT_MUSIC -> live.ytMusicRecent
                    InsightsCategory.RADIO -> live.radioRecents
                    InsightsCategory.PODCASTS -> live.podcastRecents
                    InsightsCategory.BOOKS -> live.booksRecentTitles
                    else -> emptyList()
                }
                if (fallback.isNotEmpty()) {
                    fallback.take(4).forEachIndexed { idx, (t, s) ->
                        InsightTopItemRow(
                            rank = idx + 1,
                            title = t,
                            subtitle = s,
                            trailing = if (isAr) "سابقاً" else "Recent",
                            accent = accent,
                        )
                    }
                } else {
                    PodiumText(
                        text = if (isAr) "لا يوجد نشاط مسجّل في هذه الفترة بعد." else "No activity recorded for this period yet.",
                        style = type.footnote,
                        color = colors.labelTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightMetricTile(
    value: String,
    label: String,
    accent: Color?,
    modifier: Modifier = Modifier,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    Column(
        modifier = modifier
            .background(colors.canvas.copy(alpha = 0.65f), ContinuousShape(12.dp))
            .border(1.dp, colors.separator.copy(alpha = 0.7f), ContinuousShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PodiumText(
            text = value,
            style = type.rowFocused,
            color = accent ?: colors.labelPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        PodiumText(
            text = label,
            style = type.footnote,
            color = colors.labelSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun InsightTopItemRow(
    rank: Int,
    title: String,
    subtitle: String,
    trailing: String,
    accent: Color,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.canvas.copy(alpha = 0.5f), ContinuousShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PodiumText(
            text = "$rank",
            style = type.caption,
            color = accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            PodiumText(
                text = title,
                style = type.footnote,
                color = colors.labelPrimary,
                maxLines = 1,
            )
            if (subtitle.isNotBlank()) {
                PodiumText(
                    text = subtitle,
                    style = type.footnote,
                    color = colors.labelTertiary,
                    maxLines = 1,
                )
            }
        }
        PodiumText(
            text = trailing,
            style = type.footnote,
            color = colors.labelSecondary,
            maxLines = 1,
        )
    }
}
