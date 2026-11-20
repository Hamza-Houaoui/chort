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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val AppleDestructiveRed = Color(0xFFFF3B30)

private enum class InsightsLang { EN, AR, FR, TR, RU }

private fun insightsLangOf(strings: app.podium.core.designsystem.l10n.AppStrings): InsightsLang = when (strings) {
    is app.podium.core.designsystem.l10n.ArabicStrings -> InsightsLang.AR
    is app.podium.core.designsystem.l10n.FrenchStrings -> InsightsLang.FR
    is app.podium.core.designsystem.l10n.TurkishStrings -> InsightsLang.TR
    is app.podium.core.designsystem.l10n.RussianStrings -> InsightsLang.RU
    else -> InsightsLang.EN
}

private fun InsightsLang.t(en: String, ar: String, fr: String, tr: String, ru: String): String = when (this) {
    InsightsLang.EN -> en
    InsightsLang.AR -> ar
    InsightsLang.FR -> fr
    InsightsLang.TR -> tr
    InsightsLang.RU -> ru
}

private fun InsightsPeriod.label(lang: InsightsLang): String = when (this) {
    InsightsPeriod.TODAY -> lang.t("Today", "اليوم", "Auj.", "Bugün", "Сегодня")
    InsightsPeriod.WEEK -> lang.t("Week", "الأسبوع", "Sem.", "Hafta", "Неделя")
    InsightsPeriod.MONTH -> lang.t("Month", "الشهر", "Mois", "Ay", "Месяц")
    InsightsPeriod.ALL_TIME -> lang.t("All", "الكل", "Tout", "Tümü", "Всё")
}

private fun InsightsCategory.title(lang: InsightsLang): String = when (this) {
    InsightsCategory.MUSIC -> lang.t("Music", "الموسيقى", "Musique", "Müzik", "Музыка")
    InsightsCategory.YT_MUSIC -> lang.t("YT Music", "YT Music", "YT Music", "YT Music", "YT Music")
    InsightsCategory.YOUTUBE -> "YouTube"
    InsightsCategory.SOUNDCLOUD -> "SoundCloud"
    InsightsCategory.RADIO -> lang.t("Radio", "الراديو", "Radio", "Radyo", "Радио")
    InsightsCategory.PODCASTS -> lang.t("Podcasts", "البودكاست", "Podcasts", "Podcastler", "Подкасты")
    InsightsCategory.QURAN -> lang.t("Quran", "القرآن", "Coran", "Kur'an", "Коран")
    InsightsCategory.BOOKS -> lang.t("Books", "الكتب", "Livres", "Kitaplar", "Книги")
    InsightsCategory.GALLERY -> lang.t("Gallery", "المعرض", "Galerie", "Galeri", "Галерея")
}

@Composable
fun InsightsScreen(
    graph: AppGraph,
) {
    val insets = LocalScreenInsets.current
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val motion = PodiumTheme.motion
    val strings = LocalAppStrings.current
    val lang = insightsLangOf(strings)
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
            // Apple-style iOS Segmented Control + Minimal Reset Ghost Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ContinuousShape(10.dp))
                        .background(colors.labelPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.06f))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (p in InsightsPeriod.entries) {
                        val selected = p == period
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ContinuousShape(8.dp))
                                .background(
                                    if (selected) colors.canvasRaised else Color.Transparent,
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            0.5.dp,
                                            colors.separator.copy(alpha = 0.45f),
                                            ContinuousShape(8.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        period = p
                                        haptics.press()
                                    },
                                )
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PodiumText(
                                text = p.label(lang),
                                style = type.footnote.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (selected) colors.labelPrimary else colors.labelSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.labelPrimary.copy(alpha = if (colors.isDark) 0.10f else 0.06f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                showResetConfirm = true
                                haptics.press()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    PodiumText(
                        text = "↺",
                        style = type.footnote,
                        color = colors.labelSecondary,
                    )
                }
            }

            // Large 3D Cover Flow Stage for Minimal Editorial Cards
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val cardW = (maxWidth * 0.88f).coerceAtLeast(220.dp)
                val cardH = (maxHeight * 0.95f).coerceAtLeast(220.dp)
                val spacingDp = cardW.value * 0.86f
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
                                    val x = if (abs(d) <= 1f) d * spacingDp else sign(d) * (spacingDp + (abs(d) - 1f) * cardW.value * 0.24f)
                                    translationX = x * density
                                    rotationY = -near * 18f
                                    cameraDistance = 18f * density
                                    val s = 1f - 0.08f * abs(near)
                                    scaleX = s
                                    scaleY = s
                                    alpha = (1f - 0.45f * abs(near)).coerceIn(0.22f, 1f)
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
                                    lang = lang,
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
                                        lang = lang,
                                        isFocused = isCenter,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quiet Apple Pagination Dots & Caption
            val activeTitle = when (val p = pages.getOrNull(focusedIndex)) {
                is InsightsCardPage.Overview -> lang.t("Summary", "الملخّص", "Résumé", "Özet", "Сводка")
                is InsightsCardPage.Category -> p.category.title(lang)
                null -> ""
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pages.forEachIndexed { idx, _ ->
                        val active = idx == focusedIndex
                        Box(
                            modifier = Modifier
                                .size(width = if (active) 14.dp else 5.dp, height = 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) colors.labelPrimary else colors.labelTertiary.copy(alpha = 0.35f),
                                )
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
                    text = activeTitle,
                    style = type.footnote,
                    color = colors.labelTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Apple-style Minimal Reset Alert
        if (showResetConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showResetConfirm = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .clip(ContinuousShape(20.dp))
                        .background(colors.canvasRaised)
                        .border(0.5.dp, colors.separator.copy(alpha = 0.5f), ContinuousShape(20.dp))
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PodiumText(
                            text = lang.t("Reset Insights", "تصفير الإحصائيات", "Réinitialiser", "İstatistikleri Sıfırla", "Сбросить статистику"),
                            style = type.rowFocused,
                            color = colors.labelPrimary,
                            textAlign = TextAlign.Center,
                        )
                        PodiumText(
                            text = lang.t(
                                "All tracked listening, reading, and viewing history will be cleared.",
                                "سيتم مسح سجلّ أوقات الاستماع والقراءة والمشاهدة.",
                                "Tout l'historique d'écoute, de lecture et de visionnage sera effacé.",
                                "Tüm dinleme, okuma ve izleme geçmişi silinecek.",
                                "Вся история прослушивания, чтения и просмотра будет очищена.",
                            ),
                            style = type.footnote,
                            color = colors.labelSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                    }
                    AppleHairline()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { showResetConfirm = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            PodiumText(
                                text = lang.t("Cancel", "إلغاء", "Annuler", "İptal", "Отмена"),
                                style = type.row,
                                color = colors.labelPrimary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .fillMaxHeight()
                                .background(colors.separator.copy(alpha = 0.45f)),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    graph.insightsRepository.resetAll()
                                    showResetConfirm = false
                                    haptics.press()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            PodiumText(
                                text = lang.t("Reset", "تصفير", "Effacer", "Sıfırla", "Сбросить"),
                                style = type.rowFocused,
                                color = AppleDestructiveRed,
                            )
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
    lang: InsightsLang,
    isFocused: Boolean,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val heroStyle = type.title.copy(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(ContinuousShape(24.dp))
            .background(colors.canvasRaised)
            .border(
                width = 0.5.dp,
                color = colors.separator.copy(alpha = if (isFocused) 0.55f else 0.30f),
                shape = ContinuousShape(24.dp),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Editorial Hero Header (Apple Screen Time style)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PodiumText(
                    text = lang.t("Summary", "الملخّص", "Résumé", "Özet", "Сводка"),
                    style = type.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.labelSecondary,
                )
                PodiumText(
                    text = period.label(lang),
                    style = type.footnote,
                    color = colors.labelTertiary,
                )
            }
            Spacer(Modifier.height(2.dp))
            PodiumText(
                text = formatInsightsDuration(overall.totalDurationMs),
                style = heroStyle,
                color = colors.labelPrimary,
                maxLines = 1,
            )
            PodiumText(
                text = lang.t(
                    "${overall.totalSessions} sessions  •  ${overall.currentStreakDays}d streak",
                    "${overall.totalSessions} جلسة  •  سلسلة ${overall.currentStreakDays} يوم",
                    "${overall.totalSessions} sessions  •  série ${overall.currentStreakDays}j",
                    "${overall.totalSessions} oturum  •  ${overall.currentStreakDays}g seri",
                    "${overall.totalSessions} сеансов  •  серия ${overall.currentStreakDays}д",
                ),
                style = type.footnote,
                color = colors.labelSecondary,
            )
        }

        // Apple Storage / Screen Time Proportional Bar + Breakdown
        val activeCats = overall.byCategory.filter { it.durationMs > 0L }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.labelPrimary.copy(alpha = 0.08f)),
            ) {
                if (activeCats.isNotEmpty() && overall.totalDurationMs > 0L) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
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

            val shown = activeCats.take(4)
            if (shown.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    for (cs in shown) {
                        val pct = if (overall.totalDurationMs > 0L) {
                            ((cs.durationMs.toFloat() / overall.totalDurationMs.toFloat()) * 100f).roundToInt()
                        } else 0
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
                                text = cs.category.title(lang),
                                style = type.footnote,
                                color = colors.labelPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            PodiumText(
                                text = formatInsightsDuration(cs.durationMs),
                                style = type.footnote.copy(fontWeight = FontWeight.Medium),
                                color = colors.labelPrimary,
                            )
                            PodiumText(
                                text = "$pct%",
                                style = type.footnote,
                                color = colors.labelTertiary,
                            )
                        }
                    }
                }
            } else {
                PodiumText(
                    text = lang.t(
                        "Listen, read, or watch to see your breakdown.",
                        "ابدأ بالاستماع أو القراءة أو المشاهدة لتظهر الإحصائيات هنا.",
                        "Écoutez, lisez ou regardez pour voir vos statistiques.",
                        "Dağılımı görmek için dinleyin, okuyun veya izleyin.",
                        "Слушайте, читайте или смотрите, чтобы увидеть статистику.",
                    ),
                    style = type.footnote,
                    color = colors.labelTertiary,
                    maxLines = 2,
                )
            }
        }

        AppleHairline()

        // Clean Typographic Library Snapshot (No Nested Boxes)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PodiumText(
                text = lang.t("Library", "المكتبة", "Bibliothèque", "Kütüphane", "Медиатека"),
                style = type.caption.copy(fontWeight = FontWeight.Medium),
                color = colors.labelSecondary,
            )
            AppleStatRow(
                items = listOf(
                    "${live.musicSongsCount}" to lang.t("Songs", "أغاني", "Titres", "Şarkılar", "Песни"),
                    "${live.booksTotalCount}" to lang.t("Books", "كتب", "Livres", "Kitaplar", "Книги"),
                    "${live.galleryPhotosCount + live.galleryVideosCount}" to lang.t("Media", "الوسائط", "Médias", "Medya", "Медиа"),
                ),
            )
            AppleStatRow(
                items = listOf(
                    "${live.downloadsCount}" to lang.t("Downloads", "محمّلة", "Téléch.", "İndirilen", "Загрузки"),
                    "${live.radioFavoritesCount}" to lang.t("Radio", "راديو", "Radio", "Radyo", "Радио"),
                    "${live.quranBookmarksCount}" to lang.t("Quran", "القرآن", "Coran", "Kur'an", "Коран"),
                ),
            )
        }

        // Top Overall Activity (Grouped iOS List with Hairlines)
        if (overall.topOverallItems.isNotEmpty()) {
            AppleHairline()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PodiumText(
                    text = lang.t("Most Active", "الأكثر نشاطاً", "Les plus actifs", "En aktif", "Самые активные"),
                    style = type.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.labelSecondary,
                )
                val items = overall.topOverallItems.take(3)
                Column {
                    items.forEachIndexed { idx, (cat, item) ->
                        AppleListRow(
                            rank = idx + 1,
                            title = item.title,
                            subtitle = cat.title(lang),
                            trailing = if (item.durationMs > 0L) formatInsightsDuration(item.durationMs) else "${item.count}×",
                        )
                        if (idx < items.lastIndex) AppleHairline(indentStart = 20.dp)
                    }
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
    lang: InsightsLang,
    isFocused: Boolean,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val cat = stats.category
    val accent = Color(cat.accentHex)
    val shareFrac = if (totalOverallMs > 0L) (stats.durationMs.toFloat() / totalOverallMs.toFloat()).coerceIn(0f, 1f) else 0f
    val sharePct = (shareFrac * 100f).roundToInt()
    val heroStyle = type.title.copy(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(ContinuousShape(24.dp))
            .background(colors.canvasRaised)
            .border(
                width = 0.5.dp,
                color = colors.separator.copy(alpha = if (isFocused) 0.55f else 0.30f),
                shape = ContinuousShape(24.dp),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Editorial Category Hero Header
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    PodiumText(
                        text = cat.title(lang),
                        style = type.caption.copy(fontWeight = FontWeight.Medium),
                        color = colors.labelSecondary,
                    )
                }
                PodiumText(
                    text = "$sharePct%",
                    style = type.footnote.copy(fontWeight = FontWeight.Medium),
                    color = accent,
                )
            }
            Spacer(Modifier.height(2.dp))
            PodiumText(
                text = formatInsightsDuration(stats.durationMs),
                style = heroStyle,
                color = colors.labelPrimary,
                maxLines = 1,
            )
            val sessionsLabel = when (cat) {
                InsightsCategory.YOUTUBE -> lang.t("videos", "فيديو", "vidéos", "video", "видео")
                InsightsCategory.BOOKS -> lang.t("reading sessions", "جلسة قراءة", "sessions", "okuma", "сеансов")
                InsightsCategory.GALLERY -> lang.t("views", "معاينة", "vues", "görüntüleme", "просмотров")
                InsightsCategory.QURAN -> lang.t("surahs", "سورة", "sourates", "sure", "сур")
                InsightsCategory.RADIO -> lang.t("tunes", "محطّة", "écoutes", "istasyon", "станций")
                else -> lang.t("plays", "تشغيل", "lectures", "çalma", "воспроизв.")
            }
            PodiumText(
                text = "${stats.sessionsCount} $sessionsLabel  •  ${period.label(lang)}",
                style = type.footnote,
                color = colors.labelSecondary,
            )
        }

        // Minimal Slim Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.labelPrimary.copy(alpha = 0.08f)),
        ) {
            if (shareFrac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shareFrac.coerceAtLeast(0.03f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
            }
        }

        AppleHairline()

        // Category-specific Live Metrics (Clean Typographic Columns)
        val liveTiles: List<Pair<String, String>> = when (cat) {
            InsightsCategory.MUSIC -> listOf(
                "${live.musicSongsCount}" to lang.t("Library", "أغاني المكتبة", "Bibliothèque", "Kütüphane", "Медиатека"),
                "${live.downloadsCount}" to lang.t("Downloaded", "محمّلة", "Téléchargés", "İndirilen", "Скачано"),
            )
            InsightsCategory.YT_MUSIC -> listOf(
                "${live.ytMusicLikedCount}" to lang.t("Liked", "الإعجابات", "Aimés", "Beğenilen", "Понравилось"),
                "${live.ytMusicRecent.size}" to lang.t("Recent", "الأخيرة", "Récents", "Son", "Недавние"),
            )
            InsightsCategory.YOUTUBE -> listOf(
                "${stats.topItems.size}" to lang.t("Tracked", "فيديوهات مسجّلة", "Suivis", "Kayıtlı", "Записано"),
                "${stats.sessionsCount}" to lang.t("Watches", "مشاهدات", "Vues", "İzleme", "Просмотры"),
            )
            InsightsCategory.SOUNDCLOUD -> listOf(
                "${live.soundCloudLikesCount}" to lang.t("Likes", "الإعجابات", "J'aime", "Beğeniler", "Лайки"),
                "${live.soundCloudPlaylistsCount}" to lang.t("Playlists", "قوائم التشغيل", "Playlists", "Listeler", "Плейлисты"),
            )
            InsightsCategory.RADIO -> listOf(
                "${live.radioFavoritesCount}" to lang.t("Favorites", "المفضّلة", "Favoris", "Favoriler", "Избранное"),
                "${live.radioRecents.size}" to lang.t("Recent", "الأخيرة", "Récents", "Son", "Недавние"),
            )
            InsightsCategory.PODCASTS -> listOf(
                "${live.podcastSubsCount}" to lang.t("Subscribed", "الاشتراكات", "Abonnements", "Abonelik", "Подписки"),
                "${live.podcastRecents.size}" to lang.t("Episodes", "الحلقات", "Épisodes", "Bölümler", "Выпуски"),
            )
            InsightsCategory.QURAN -> listOf(
                "${live.quranBookmarksCount}" to lang.t("Bookmarks", "العلامات", "Signets", "Yer imleri", "Закладки"),
                (live.quranLastReadTitle ?: "—") to lang.t("Last Read", "آخر قراءة", "Dernière lecture", "Son okunan", "Последнее"),
            )
            InsightsCategory.BOOKS -> listOf(
                "${live.booksTotalCount}" to lang.t("Books", "الكتب", "Livres", "Kitaplar", "Книги"),
                "${live.booksInProgressCount}" to lang.t("Reading", "قيد القراءة", "En cours", "Okunuyor", "Читаю"),
                "${live.booksFavoritesCount}" to lang.t("Favorites", "المفضّلة", "Favoris", "Favoriler", "Избранное"),
            )
            InsightsCategory.GALLERY -> listOf(
                "${live.galleryPhotosCount}" to lang.t("Photos", "صور", "Photos", "Fotoğraf", "Фото"),
                "${live.galleryVideosCount}" to lang.t("Videos", "فيديوهات", "Vidéos", "Video", "Видео"),
                "${live.galleryFavoritesCount}" to lang.t("Favorites", "المفضّلة", "Favoris", "Favoriler", "Избранное"),
            )
        }

        AppleStatRow(items = liveTiles)

        AppleHairline()

        // Top Items (Clean iOS Grouped List)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PodiumText(
                text = when (cat) {
                    InsightsCategory.YOUTUBE -> lang.t("Top Watched", "أكثر الفيديوهات مشاهدة", "Les plus regardés", "En çok izlenen", "Чаще смотрели")
                    InsightsCategory.BOOKS -> lang.t("Top Read", "الكتب المقروءة", "Les plus lus", "En çok okunan", "Чаще читали")
                    InsightsCategory.GALLERY -> lang.t("Most Viewed", "الأكثر معاينة", "Les plus vus", "En çok bakılan", "Чаще открывали")
                    InsightsCategory.QURAN -> lang.t("Top Surahs", "أكثر السور", "Sourates fréquentes", "En çok dinlenen sureler", "Популярные суры")
                    InsightsCategory.RADIO -> lang.t("Top Stations", "أكثر المحطّات", "Stations favorites", "En çok dinlenen istasyonlar", "Топ станций")
                    else -> lang.t("Top Played", "الأكثر تشغيلاً", "Les plus écoutés", "En çok çalınan", "Чаще слушали")
                },
                style = type.caption.copy(fontWeight = FontWeight.Medium),
                color = colors.labelSecondary,
            )

            if (stats.topItems.isNotEmpty()) {
                val shown = stats.topItems.take(4)
                Column {
                    shown.forEachIndexed { idx, item ->
                        AppleListRow(
                            rank = idx + 1,
                            title = item.title,
                            subtitle = item.subtitle,
                            trailing = if (item.durationMs > 0L) formatInsightsDuration(item.durationMs) else "${item.count}×",
                        )
                        if (idx < shown.lastIndex) AppleHairline(indentStart = 20.dp)
                    }
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
                    val shown = fallback.take(4)
                    Column {
                        shown.forEachIndexed { idx, (t, s) ->
                            AppleListRow(
                                rank = idx + 1,
                                title = t,
                                subtitle = s,
                                trailing = lang.t("Recent", "سابقاً", "Récent", "Son", "Недавно"),
                            )
                            if (idx < shown.lastIndex) AppleHairline(indentStart = 20.dp)
                        }
                    }
                } else {
                    PodiumText(
                        text = lang.t(
                            "No activity recorded yet.",
                            "لا يوجد نشاط في هذه الفترة بعد.",
                            "Aucune activité enregistrée.",
                            "Henüz kayıtlı etkinlik yok.",
                            "Активность пока не записана.",
                        ),
                        style = type.footnote,
                        color = colors.labelTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleHairline(
    indentStart: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentStart)
            .height(0.5.dp)
            .background(PodiumTheme.colors.separator.copy(alpha = 0.38f)),
    )
}

@Composable
private fun AppleStatRow(
    items: List<Pair<String, String>>,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { idx, (value, label) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PodiumText(
                    text = value,
                    style = type.rowFocused.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.labelPrimary,
                    maxLines = 1,
                )
                PodiumText(
                    text = label,
                    style = type.footnote,
                    color = colors.labelTertiary,
                    maxLines = 1,
                )
            }
            if (idx < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(0.5.dp)
                        .height(26.dp)
                        .background(colors.separator.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

@Composable
private fun AppleListRow(
    rank: Int,
    title: String,
    subtitle: String,
    trailing: String,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PodiumText(
            text = "$rank",
            style = type.footnote,
            color = colors.labelTertiary,
            modifier = Modifier.width(12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            PodiumText(
                text = title,
                style = type.footnote.copy(fontWeight = FontWeight.Medium),
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
