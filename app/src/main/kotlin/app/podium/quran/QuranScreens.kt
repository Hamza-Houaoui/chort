package app.podium.quran

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.core.designsystem.component.FocusList
import app.podium.core.designsystem.component.KeyboardAction
import app.podium.core.designsystem.component.KeyboardAwayOnLeave
import app.podium.core.designsystem.component.ListInputEffect
import app.podium.core.designsystem.component.LoadingRow
import app.podium.core.designsystem.component.MenuPreview
import app.podium.core.designsystem.component.MenuRow
import app.podium.core.designsystem.component.PaperTextField
import app.podium.core.designsystem.component.TrackRow
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.symbol.Symbol
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.theme.Spacing
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.rememberFocusListState
import app.podium.player.api.PlayIntent
import app.podium.player.api.PlaybackController
import app.podium.player.api.TrackCatalog
import androidx.compose.foundation.gestures.animateScrollBy
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelButton
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberPodiumHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun QuranScreen(
    place: QuranPlace,
    repository: QuranRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    navigate: (QuranPlace) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onBack: () -> Unit,
) {
    when (place) {
        QuranPlace.Menu -> QuranMainMenu(
            repository = repository,
            navigate = navigate,
        )
        QuranPlace.SurahsList -> QuranSurahsScreen(
            repository = repository,
            catalog = catalog,
            controller = controller,
            navigate = navigate,
        )
        QuranPlace.TarjamaSurahsList -> QuranSurahsScreen(
            repository = repository,
            catalog = catalog,
            controller = controller,
            navigate = navigate,
            showTranslation = true,
        )
        is QuranPlace.Mosshaf -> QuranMosshafScreen(
            surahId = place.surahId,
            initialAyah = place.initialAyah,
            showTranslation = place.showTranslation,
            repository = repository,
            catalog = catalog,
            controller = controller,
            navigate = navigate,
            onOpenNowPlaying = onOpenNowPlaying,
        )
        QuranPlace.RecitersList -> QuranRecitersScreen(
            repository = repository,
            onDone = onBack,
        )
        QuranPlace.TranslationSettings -> QuranTranslationSettingsScreen(
            repository = repository,
            onDone = onBack,
        )
        QuranPlace.Search -> QuranSearchScreen(
            repository = repository,
            navigate = navigate,
        )
        QuranPlace.Bookmarks -> QuranBookmarksScreen(
            repository = repository,
            navigate = navigate,
        )
    }
}

// -------------------------------------------------------------
// 1. Main Quran Menu
// -------------------------------------------------------------

private sealed interface QuranMenuRow {
    data class Entry(
        val id: String,
        val label: String,
        val value: String? = null,
        val action: () -> Unit,
    ) : QuranMenuRow
}

@Composable
private fun QuranMainMenu(
    repository: QuranRepository,
    navigate: (QuranPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val activeReciter by repository.activeReciter.collectAsStateWithLifecycle()
    val activeTranslation by repository.activeTranslation.collectAsStateWithLifecycle()
    val lastRead by repository.lastRead.collectAsStateWithLifecycle()
    val bookmarks by repository.bookmarks.collectAsStateWithLifecycle()

    val rows = remember(strings, activeReciter, activeTranslation, lastRead, bookmarks.size) {
        buildList {
            add(
                QuranMenuRow.Entry(
                    id = "surahs",
                    label = strings.quran,
                    value = "114",
                    action = { navigate(QuranPlace.SurahsList) },
                )
            )
            add(
                QuranMenuRow.Entry(
                    id = "tarjama",
                    label = strings.tafsirAndTranslation,
                    value = activeTranslation.label.substringBefore(" ("),
                    action = { navigate(QuranPlace.TarjamaSurahsList) },
                )
            )
            if (lastRead != null) {
                val lr = lastRead!!
                add(
                    QuranMenuRow.Entry(
                        id = "continue",
                        label = strings.continueReading,
                        value = "${lr.surahName} : ${lr.ayahNumber}",
                        action = { navigate(QuranPlace.Mosshaf(lr.surahId, lr.ayahNumber)) },
                    )
                )
            }
            add(
                QuranMenuRow.Entry(
                    id = "reciters",
                    label = strings.reciters,
                    value = activeReciter.nameArabic,
                    action = { navigate(QuranPlace.RecitersList) },
                )
            )
            add(
                QuranMenuRow.Entry(
                    id = "translation_settings",
                    label = "${strings.tafsirAndTranslation} (${strings.language})",
                    value = activeTranslation.label.substringBefore(" ("),
                    action = { navigate(QuranPlace.TranslationSettings) },
                )
            )
            add(
                QuranMenuRow.Entry(
                    id = "search",
                    label = strings.searchQuran,
                    action = { navigate(QuranPlace.Search) },
                )
            )
            add(
                QuranMenuRow.Entry(
                    id = "bookmarks",
                    label = strings.bookmarks,
                    value = if (bookmarks.isNotEmpty()) "${bookmarks.size}" else null,
                    action = { navigate(QuranPlace.Bookmarks) },
                )
            )
        }
    }

    val focus = rememberFocusListState("quran-main-menu")
    val activate: (Int) -> Unit = { index ->
        val row = rows.getOrNull(index)
        row?.action()
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.id },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        MenuRow(
            label = row.label,
            focused = focused,
            value = row.value,
        )
    }
}

// -------------------------------------------------------------
// 2. Surahs List Screen (114 Surahs)
// -------------------------------------------------------------

@Composable
private fun QuranSurahsScreen(
    repository: QuranRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    navigate: (QuranPlace) -> Unit,
    showTranslation: Boolean = false,
) {
    val surahs = AllSurahs
    val focus = rememberFocusListState(if (showTranslation) "quran-tarjama-surahs-list" else "quran-surahs-list")

    val activate: (Int) -> Unit = { index ->
        val surah = surahs.getOrNull(index)
        if (surah != null) {
            navigate(QuranPlace.Mosshaf(surah.id, showTranslation = showTranslation))
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = surahs,
        state = focus,
        key = { it.id.toString() },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { surah ->
            val track = surah.toPodiumTrack(repository.activeReciter.value)
            track.artwork?.uri?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument
        },
        modifier = Modifier.fillMaxSize(),
    ) { surah, _, focused ->
        val verses = "${surah.versesCount} آيات"
        val revPlace = surah.revelationPlace.arabicName
        TrackRow(
            title = "${surah.id}. ${surah.nameArabic}",
            subtitle = surah.nameEnglish,
            focused = focused,
            artworkUri = null,
            showArtwork = false,
            trailing = "ص ${surah.startPage}",
            trailingSubtitle = "$verses • $revPlace",
        )
    }
}

// -------------------------------------------------------------
// 3. Mosshaf Reading Screen with Synchronized Ayah Timings
// -------------------------------------------------------------

@Composable
private fun QuranMosshafScreen(
    surahId: Int,
    initialAyah: Int? = null,
    showTranslation: Boolean = false,
    repository: QuranRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    navigate: (QuranPlace) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    var currentSurahId by remember(surahId) { androidx.compose.runtime.mutableIntStateOf(surahId) }
    var playbackStartedHere by remember(surahId) { mutableStateOf(false) }
    val surah = remember(currentSurahId) { findSurah(currentSurahId) ?: AllSurahs.first() }
    val activeReciter by repository.activeReciter.collectAsStateWithLifecycle()
    val activeTranslation by repository.activeTranslation.collectAsStateWithLifecycle()
    val bookmarks by repository.bookmarks.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var ayahs by remember(currentSurahId, activeReciter.id, showTranslation, activeTranslation.id) { mutableStateOf<List<Ayah>>(emptyList()) }
    var loading by remember(currentSurahId, activeReciter.id, showTranslation, activeTranslation.id) { mutableStateOf(true) }

    LaunchedEffect(currentSurahId, activeReciter.id, showTranslation, activeTranslation.id) {
        loading = true
        ayahs = if (showTranslation) {
            repository.loadSurahWithTranslation(currentSurahId, activeReciter.id, activeTranslation)
        } else {
            repository.getSurahAyahs(currentSurahId, activeReciter.id)
        }
        loading = false
    }

    // Playback state monitoring & auto-flip Mosshaf page when queue advances to next Surah
    val snapshot by controller.snapshot.collectAsStateWithLifecycle()
    val playingSurahId = remember(snapshot.item?.trackId) {
        val tid = snapshot.item?.trackId
        if (tid?.sourceId == QuranMusicSource.QURAN_SOURCE_ID) {
            tid.providerKey.substringBefore(':').toIntOrNull()
        } else {
            null
        }
    }
    LaunchedEffect(playingSurahId, snapshot.intent) {
        if (playingSurahId == currentSurahId && snapshot.intent == PlayIntent.PLAY) {
            playbackStartedHere = true
        } else if (playbackStartedHere && playingSurahId != null && playingSurahId != currentSurahId) {
            currentSurahId = playingSurahId
        }
    }

    val hasFullQueue = (snapshot.item?.queueSize ?: 0) >= AllSurahs.size
    val isThisSurahLoaded = playingSurahId == surah.id && hasFullQueue && snapshot.status != app.podium.player.api.PlaybackStatus.Ended
    val isPlayingThisSurah = playingSurahId == surah.id && snapshot.intent == PlayIntent.PLAY

    val playSurahFromQueue: (Int, Long) -> Unit = { targetSurahId, seekMs ->
        coroutineScope.launch {
            playbackStartedHere = true
            if (currentSurahId != targetSurahId) {
                currentSurahId = targetSurahId
            }
            val allTracks = AllSurahs.map { it.toPodiumTrack(activeReciter) }
            val startIdx = AllSurahs.indexOfFirst { it.id == targetSurahId }.coerceAtLeast(0)
            catalog.remember(allTracks)
            controller.setRepeat(app.podium.player.api.RepeatMode.ALL)
            controller.playContext(allTracks.map { it.id }, startIdx, "القرآن الكريم")
            if (seekMs > 0L) {
                var waited = 0
                while (controller.snapshot.value.item?.trackId?.providerKey?.startsWith("$targetSurahId:") != true && waited < 5000) {
                    delay(100)
                    waited += 100
                }
                delay(250)
                controller.seekTo(seekMs)
            }
        }
    }

    val playNextSurahNow: () -> Unit = {
        val nextId = if (surah.id < 114) surah.id + 1 else 1
        playSurahFromQueue(nextId, 0L)
    }

    var currentPosMs by remember(currentSurahId) { mutableLongStateOf(0L) }
    var reachedLastAyah by remember(currentSurahId) { mutableStateOf(false) }

    LaunchedEffect(playingSurahId, surah.id) {
        if (playingSurahId == surah.id) {
            while (isActive) {
                currentPosMs = controller.positionMs()
                delay(200)
            }
        }
    }

    val activeAyahNumber = remember(currentPosMs, ayahs, playingSurahId, surah.id) {
        if (playingSurahId != surah.id || ayahs.isEmpty()) null
        else ayahs.firstOrNull { it.timestampFromMs <= currentPosMs && (it.timestampToMs == 0L || currentPosMs < it.timestampToMs) }?.ayahNumber
    }

    // Auto-advance watcher when the final Ayah finishes or the player reaches Ended
    LaunchedEffect(playingSurahId, surah.id, currentPosMs, snapshot.status, ayahs, showTranslation) {
        if (playingSurahId != surah.id || ayahs.isEmpty()) return@LaunchedEffect
        val lastAyah = ayahs.last()
        if (activeAyahNumber == lastAyah.ayahNumber || (lastAyah.timestampFromMs > 0L && currentPosMs >= lastAyah.timestampFromMs)) {
            reachedLastAyah = true
        }
        if (!showTranslation) {
            val endMs = lastAyah.timestampToMs
            if (snapshot.status == app.podium.player.api.PlaybackStatus.Ended) {
                playNextSurahNow()
            } else if (reachedLastAyah && endMs > 0L && currentPosMs >= endMs + 350L) {
                playNextSurahNow()
            }
        }
    }

    // In Tarjama mode: after each Arabic Ayah finishes, pause recitation, speak its translation via TTS, then resume next Ayah (or advance to next Surah).
    if (showTranslation) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var speakingAyah by remember(currentSurahId) { mutableStateOf<Int?>(null) }
        var lastSpokenAyah by remember(currentSurahId) { mutableStateOf<Int?>(null) }
        val tts = remember(context) {
            var instance: TextToSpeech? = null
            instance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val locale = when (activeTranslation) {
                        QuranTranslationOption.ENGLISH -> java.util.Locale.US
                        QuranTranslationOption.TURKISH -> java.util.Locale("tr", "TR")
                        QuranTranslationOption.RUSSIAN -> java.util.Locale("ru", "RU")
                        QuranTranslationOption.TAFSIR_MUYASSAR,
                        QuranTranslationOption.TAFSIR_IBN_KATHIR -> java.util.Locale("ar")
                    }
                    instance?.language = locale
                }
            }
            instance
        }

        DisposableEffect(tts) {
            onDispose {
                tts.stop()
                tts.shutdown()
            }
        }

        LaunchedEffect(activeTranslation, tts) {
            val locale = when (activeTranslation) {
                QuranTranslationOption.ENGLISH -> java.util.Locale.US
                QuranTranslationOption.TURKISH -> java.util.Locale("tr", "TR")
                QuranTranslationOption.RUSSIAN -> java.util.Locale("ru", "RU")
                QuranTranslationOption.TAFSIR_MUYASSAR,
                QuranTranslationOption.TAFSIR_IBN_KATHIR -> java.util.Locale("ar")
            }
            tts.language = locale
        }

        LaunchedEffect(currentPosMs, isPlayingThisSurah, ayahs) {
            if (!isPlayingThisSurah || ayahs.isEmpty() || speakingAyah != null) return@LaunchedEffect
            val current = ayahs.firstOrNull {
                it.timestampToMs > 0L && currentPosMs >= it.timestampToMs - 180L && currentPosMs <= it.timestampToMs + 600L
            } ?: return@LaunchedEffect
            if (current.ayahNumber == lastSpokenAyah) return@LaunchedEffect
            val textToSpeak = current.translationText?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            val nextAyah = ayahs.firstOrNull { it.ayahNumber == current.ayahNumber + 1 }

            lastSpokenAyah = current.ayahNumber
            speakingAyah = current.ayahNumber
            controller.pause()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    coroutineScope.launch {
                        speakingAyah = null
                        if (nextAyah != null) {
                            controller.seekTo(nextAyah.timestampFromMs)
                            controller.play()
                        } else {
                            // End of Surah in Tarjama mode: advance automatically to the next Surah
                            playNextSurahNow()
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    coroutineScope.launch {
                        speakingAyah = null
                        if (nextAyah != null) {
                            controller.seekTo(nextAyah.timestampFromMs)
                            controller.play()
                        } else {
                            playNextSurahNow()
                        }
                    }
                }
            })
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "ayah_${current.ayahNumber}")
        }
    }

    // Save reading progress when active ayah changes
    LaunchedEffect(activeAyahNumber, surah.id) {
        if (activeAyahNumber != null) {
            repository.saveLastRead(surah.id, activeAyahNumber, currentPosMs)
        }
    }

    val listState = rememberLazyListState()

    // Scroll to top when Surah flips automatically, or to initial ayah on first open
    LaunchedEffect(currentSurahId, initialAyah, ayahs) {
        if (ayahs.isNotEmpty()) {
            if (currentSurahId == surahId && initialAyah != null) {
                val targetIdx = ayahs.indexOfFirst { it.ayahNumber == initialAyah }.coerceAtLeast(0)
                listState.animateScrollToItem(targetIdx + 1)
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(activeAyahNumber) {
        if (activeAyahNumber != null && ayahs.isNotEmpty()) {
            val targetIdx = ayahs.indexOfFirst { it.ayahNumber == activeAyahNumber }
            if (targetIdx >= 0) {
                listState.animateScrollToItem(targetIdx + 1)
            }
        }
    }

    fun isLoadedNow(): Boolean {
        val item = controller.snapshot.value.item ?: return false
        val id = item.trackId
        return id.sourceId == QuranMusicSource.QURAN_SOURCE_ID &&
            (id.providerKey.startsWith("${surah.id}:") || id.providerKey == "${surah.id}") &&
            item.queueSize >= AllSurahs.size &&
            controller.snapshot.value.status != app.podium.player.api.PlaybackStatus.Ended
    }

    val startOrSeekPlayback: (Ayah?) -> Unit = { ayah ->
        val target = ayah?.timestampFromMs ?: 0L
        if (!isLoadedNow()) {
            playSurahFromQueue(surah.id, target)
        } else {
            playbackStartedHere = true
            if (ayah != null) controller.seekTo(target)
            controller.play()
        }
    }

    // The ayah the reader is on: the one being recited, else the first visible on the page.
    fun currentAyahIndex(): Int {
        val active = activeAyahNumber
        if (active != null) return ayahs.indexOfFirst { it.ayahNumber == active }
        return (listState.firstVisibleItemIndex - 1).coerceIn(0, (ayahs.size - 1).coerceAtLeast(0))
    }

    val haptics = rememberPodiumHaptics()
    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                // Scroll the page like the browser.
                coroutineScope.launch { listState.animateScrollBy(input.detents * 140f) }
                haptics.step(level = 0.4f)
                true
            }
            is PodiumInput.Press -> when (input.button) {
                WheelButton.NEXT -> {
                    if (ayahs.isNotEmpty()) {
                        val idx = currentAyahIndex()
                        val next = ayahs.getOrNull(if (activeAyahNumber == null && !isThisSurahLoaded) idx else idx + 1)
                        if (next != null) {
                            startOrSeekPlayback(next)
                            coroutineScope.launch { listState.animateScrollToItem(ayahs.indexOf(next) + 1) }
                        } else {
                            playNextSurahNow()
                        }
                    }
                    haptics.press()
                    true
                }
                WheelButton.PREVIOUS -> {
                    if (ayahs.isNotEmpty()) {
                        val idx = currentAyahIndex()
                        val prev = ayahs.getOrNull((idx - 1).coerceAtLeast(0))
                        if (prev != null) {
                            startOrSeekPlayback(prev)
                            coroutineScope.launch { listState.animateScrollToItem(ayahs.indexOf(prev) + 1) }
                        }
                    }
                    haptics.press()
                    true
                }
                WheelButton.PLAY_PAUSE, WheelButton.CENTER -> {
                    if (isThisSurahLoaded) {
                        controller.togglePlayPause()
                    } else {
                        startOrSeekPlayback(initialAyah?.let { n -> ayahs.firstOrNull { it.ayahNumber == n } })
                    }
                    haptics.press()
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PodiumTheme.colors.canvas)
                .padding(top = LocalScreenInsets.current.top)
        ) {
            // Action & Reciter Bar
            SurahTopBar(
                reciter = activeReciter,
                isPlaying = isPlayingThisSurah,
                onTogglePlay = {
                    if (isThisSurahLoaded) controller.togglePlayPause() else startOrSeekPlayback(null)
                },
                onNowPlaying = onOpenNowPlaying,
                onChangeReciter = { navigate(QuranPlace.RecitersList) },
            )

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingRow("جاري تحميل آيات سورة ${surah.nameArabic}...")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.m),
                ) {
                    // Header with Bismillah
                    item(key = "header") {
                        SurahHeaderCard(surah = surah)
                    }

                    // Verses
                    itemsIndexed(ayahs, key = { _, it -> it.verseKey }) { _, ayah ->
                        val isHighlighted = activeAyahNumber == ayah.ayahNumber
                        val isBookmarked = bookmarks.any { it.surahId == surah.id && it.ayahNumber == ayah.ayahNumber }

                        AyahItemCard(
                            ayah = ayah,
                            isHighlighted = isHighlighted,
                            isBookmarked = isBookmarked,
                            onClick = { startOrSeekPlayback(ayah) },
                            onToggleBookmark = {
                                if (isBookmarked) {
                                    repository.removeBookmark(surah.id, ayah.ayahNumber)
                                } else {
                                    repository.addBookmark(surah.id, ayah.ayahNumber, ayah.timestampFromMs)
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }

                    item(key = "footer") {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SurahTopBar(
    reciter: Reciter,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNowPlaying: () -> Unit,
    onChangeReciter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PodiumTheme.colors.canvasRaised)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onChangeReciter)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        ) {
            Symbol(
                symbol = PodiumSymbol.Headphones,
                color = PodiumTheme.colors.highlight,
                size = 18.dp,
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            PodiumText(
                text = "القارئ: ${reciter.nameArabic}",
                style = PodiumTheme.type.caption,
                color = PodiumTheme.colors.labelSecondary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Play/Pause Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PodiumTheme.colors.highlight.copy(alpha = 0.2f))
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    symbol = if (isPlaying) PodiumSymbol.Pause else PodiumSymbol.Play,
                    color = PodiumTheme.colors.highlight,
                    size = 18.dp,
                )
            }

            Spacer(modifier = Modifier.width(Spacing.s))

            // Now Playing / Lyrics Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PodiumTheme.colors.canvas)
                    .clickable(onClick = onNowPlaying),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    symbol = PodiumSymbol.Lyrics,
                    color = PodiumTheme.colors.labelPrimary,
                    size = 18.dp,
                )
            }
        }
    }
}

@Composable
private fun SurahHeaderCard(surah: Surah) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF072B20))
                .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
                .padding(vertical = Spacing.m, horizontal = Spacing.l),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    text = "سورة ${surah.nameArabic}",
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37),
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = "${surah.revelationPlace.arabicName} • ${surah.versesCount} آية • ص ${surah.startPage}",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = Color(0xFFB0D4AF37),
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        if (surah.bismillahPre) {
            Spacer(modifier = Modifier.height(Spacing.m))
            BasicText(
                text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PodiumTheme.colors.highlight,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AyahItemCard(
    ayah: Ayah,
    isHighlighted: Boolean,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFF0F3B2A) else PodiumTheme.colors.canvasRaised.copy(alpha = 0.5f),
        label = "ayah_bg",
    )
    val borderColor = if (isHighlighted) Color(0xFFD4AF37) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (isHighlighted) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Ayah text with Uthmanic end symbol
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFFD4AF37), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "${ayah.ayahNumber}",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHighlighted) Color(0xFFFFDF7A) else PodiumTheme.colors.labelPrimary,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.s))
                BasicText(
                    text = ayah.textUthmani,
                    style = TextStyle(
                        fontSize = 20.sp,
                        lineHeight = 36.sp,
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHighlighted) Color(0xFFFFDF7A) else PodiumTheme.colors.labelPrimary,
                        textAlign = TextAlign.Right,
                    ),
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(Spacing.s))

                // Bookmark toggle button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleBookmark),
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(
                        symbol = PodiumSymbol.Favorite,
                        color = if (isBookmarked) Color(0xFFD4AF37) else PodiumTheme.colors.labelSecondary.copy(alpha = 0.35f),
                        size = 18.dp,
                        filled = isBookmarked,
                    )
                }
            }
            if (!ayah.translationText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                BasicText(
                    text = ayah.translationText,
                    style = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (isHighlighted) Color(0xFFE0E0E0) else PodiumTheme.colors.labelSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 4. Reciters Selector Screen
// -------------------------------------------------------------

@Composable
private fun QuranTranslationSettingsScreen(
    repository: QuranRepository,
    onDone: () -> Unit,
) {
    val activeTranslation by repository.activeTranslation.collectAsStateWithLifecycle()
    val options = QuranTranslationOption.entries
    val focus = rememberFocusListState("quran-translation-settings")

    val activate: (Int) -> Unit = { index ->
        val opt = options.getOrNull(index)
        if (opt != null) {
            repository.selectTranslation(opt)
            onDone()
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = options,
        state = focus,
        key = { it.id },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { opt, _, focused ->
        MenuRow(
            label = opt.label,
            focused = focused,
            value = if (opt.isTafsir) "تفسير" else "ترجمة",
            selected = opt.id == activeTranslation.id,
        )
    }
}

@Composable
private fun QuranRecitersScreen(
    repository: QuranRepository,
    onDone: () -> Unit,
) {
    val reciters = DefaultReciters
    val activeReciter by repository.activeReciter.collectAsStateWithLifecycle()
    val focus = rememberFocusListState("quran-reciters-list")

    val activate: (Int) -> Unit = { index ->
        val reciter = reciters.getOrNull(index)
        if (reciter != null) {
            repository.selectReciter(reciter)
            onDone()
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = reciters,
        state = focus,
        key = { it.id.toString() },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { reciter, _, focused ->
        val isSelected = reciter.id == activeReciter.id
        MenuRow(
            label = reciter.nameArabic,
            focused = focused,
            value = reciter.style ?: reciter.nameEnglish,
            selected = isSelected,
        )
    }
}

// -------------------------------------------------------------
// 5. Quran Search Screen (Surah, Ayah number, Word search)
// -------------------------------------------------------------

@Composable
private fun QuranSearchScreen(
    repository: QuranRepository,
    navigate: (QuranPlace) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<QuranSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(400) // debounce
        results = repository.api.search(trimmed)
        searching = false
    }

    val focus = rememberFocusListState("quran-search-list")
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val insets = LocalScreenInsets.current

    // Auto-focus and open keyboard on enter
    LaunchedEffect(Unit) {
        delay(200)
        requester.requestFocus()
        keyboard?.show()
    }
    KeyboardAwayOnLeave()

    val activate: (Int) -> Unit = { index ->
        val res = results.getOrNull(index)
        if (res != null) {
            focusManager.clearFocus()
            keyboard?.hide()
            navigate(QuranPlace.Mosshaf(res.surahId, res.ayahNumber))
        }
    }
    ListInputEffect(focus, activate)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.top + Spacing.m)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.xs)
        ) {
            PaperTextField(
                value = query,
                onChange = { query = it },
                focus = requester,
                onDone = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
                placeholder = "ابحث بسورة أو كلمة أو آية...",
                description = "البحث في القرآن الكريم",
                action = KeyboardAction.SEARCH,
            )
        }

        if (searching) {
            LoadingRow("جاري البحث في القرآن الكريم...")
        }

        FocusList(
            items = results,
            state = focus,
            key = { "${it.verseKey}_${it.ayahNumber}" },
            contentPadding = insets.listPadding(),
            onActivate = activate,
            preview = { MenuPreview.Instrument },
            modifier = Modifier.fillMaxSize(),
        ) { res, _, focused ->
            val surah = findSurah(res.surahId)
            val surahName = surah?.nameArabic ?: res.surahId.toString()
            TrackRow(
                title = res.textArabic,
                subtitle = "سورة $surahName : الآية ${res.ayahNumber} ${res.translation?.let { "• $it" } ?: ""}",
                focused = focused,
                artworkUri = null,
                showArtwork = false,
                trailing = res.verseKey,
            )
        }
    }
}

// -------------------------------------------------------------
// 6. Bookmarks Screen
// -------------------------------------------------------------

@Composable
private fun QuranBookmarksScreen(
    repository: QuranRepository,
    navigate: (QuranPlace) -> Unit,
) {
    val bookmarks by repository.bookmarks.collectAsStateWithLifecycle()
    val focus = rememberFocusListState("quran-bookmarks-list")

    val activate: (Int) -> Unit = { index ->
        val bm = bookmarks.getOrNull(index)
        if (bm != null) {
            navigate(QuranPlace.Mosshaf(bm.surahId, bm.ayahNumber))
        }
    }
    ListInputEffect(focus, activate)

    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PodiumText(
                text = "لا توجد علامات مرجعية محفوظة بعد.\nيمكنك حفظ أي آية بالضغط على أيقونة القلب أثناء القراءة.",
                style = PodiumTheme.type.body,
                color = PodiumTheme.colors.labelSecondary,
                modifier = Modifier.padding(Spacing.l),
            )
        }
    } else {
        FocusList(
            items = bookmarks,
            state = focus,
            key = { "${it.surahId}_${it.ayahNumber}_${it.addedAt}" },
            contentPadding = LocalScreenInsets.current.listPadding(),
            onActivate = activate,
            preview = { MenuPreview.Instrument },
            modifier = Modifier.fillMaxSize(),
        ) { bm, _, focused ->
            TrackRow(
                title = bm.surahName,
                subtitle = "الآية رقم ${bm.ayahNumber}",
                focused = focused,
                artworkUri = null,
                showArtwork = false,
                trailing = if (bm.timestampMs > 0) formatDuration(bm.timestampMs) else null,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}
