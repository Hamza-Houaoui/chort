package app.podium.podcast

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.core.designsystem.component.DetailHeader
import app.podium.core.designsystem.component.FocusList
import app.podium.core.designsystem.component.KeyboardAction
import app.podium.core.designsystem.component.KeyboardAwayOnLeave
import app.podium.core.designsystem.component.ListInputEffect
import app.podium.core.designsystem.component.LoadingRow
import app.podium.core.designsystem.component.LoadingScreen
import app.podium.core.designsystem.component.LocalOverlayHost
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.component.MenuAction
import app.podium.core.designsystem.component.MenuPreview
import app.podium.core.designsystem.component.MenuRow
import app.podium.core.designsystem.component.MenuSpec
import app.podium.core.designsystem.component.MessageState
import app.podium.core.designsystem.component.OverlayHost
import app.podium.core.designsystem.component.PaperTextField
import app.podium.core.designsystem.component.TrackRow
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.theme.Spacing
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.PodiumHaptics
import app.podium.core.interaction.rememberFocusListState
import app.podium.core.interaction.rememberPodiumHaptics
import app.podium.player.api.PlaybackController
import app.podium.player.api.TrackCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PodcastScreen(
    place: PodcastPlace,
    repository: PodcastRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    navigate: (PodcastPlace) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onBack: () -> Unit,
) {
    val haptics = rememberPodiumHaptics()
    val overlay = LocalOverlayHost.current

    when (place) {
        PodcastPlace.Menu -> PodcastMainMenu(repository, navigate)
        is PodcastPlace.Top -> PodcastTopScreen(place.category, repository, navigate, overlay, haptics)
        PodcastPlace.Search -> PodcastSearchScreen(repository, navigate, overlay, haptics)
        PodcastPlace.Subscriptions -> PodcastSubscriptionsScreen(repository, navigate, overlay, haptics)
        PodcastPlace.Categories -> PodcastCategoriesScreen(navigate)
        PodcastPlace.RecentEpisodes -> PodcastRecentEpisodesScreen(repository, catalog, controller, navigate, onOpenNowPlaying, overlay, haptics)
        is PodcastPlace.ShowDetails -> PodcastShowDetailsScreen(place.podcastId, repository, catalog, controller, navigate, onOpenNowPlaying, overlay, haptics)
        is PodcastPlace.EpisodeDetails -> PodcastEpisodeDetailsScreen(place.podcastId, place.episodeId, repository, catalog, controller, onOpenNowPlaying)
        PodcastPlace.AddRss -> PodcastAddRssScreen(repository, navigate, overlay, haptics)
        PodcastPlace.Settings -> PodcastSettingsScreen(repository, onBack, overlay, haptics)
    }
}

// -------------------------------------------------------------
// 1. Main Podcast Menu
// -------------------------------------------------------------

private sealed interface PodcastMenuRow {
    data class Entry(val label: String, val leading: PodiumSymbol? = null, val value: String? = null, val action: () -> Unit) : PodcastMenuRow
    data class Section(val title: String) : PodcastMenuRow
    data class Recent(val episode: PodcastEpisode) : PodcastMenuRow
}

@Composable
private fun PodcastMainMenu(
    repository: PodcastRepository,
    navigate: (PodcastPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()
    val recents by repository.recentEpisodes.collectAsStateWithLifecycle()

    var topArtworks by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val top = repository.api.getTopPodcasts(repository.chartsCountry, limit = 8)
        topArtworks = top.mapNotNull { it.artworkUrl }.filter { it.isNotBlank() }
    }

    val rows = buildList {
        add(
            PodcastMenuRow.Entry(strings.searchPodcasts, leading = PodiumSymbol.Search) {
                navigate(PodcastPlace.Search)
            },
        )
        add(
            PodcastMenuRow.Entry(strings.topPodcasts, leading = PodiumSymbol.Speaker) {
                navigate(PodcastPlace.Top())
            },
        )
        add(
            PodcastMenuRow.Entry(
                strings.subscriptions,
                leading = PodiumSymbol.Favorite,
                value = if (subscriptions.isNotEmpty()) subscriptions.size.toString() else null,
            ) {
                navigate(PodcastPlace.Subscriptions)
            },
        )
        add(
            PodcastMenuRow.Entry(strings.categories, leading = PodiumSymbol.Library) {
                navigate(PodcastPlace.Categories)
            },
        )
        add(
            PodcastMenuRow.Entry(strings.addByRss, leading = PodiumSymbol.Queue) {
                navigate(PodcastPlace.AddRss)
            },
        )
        add(
            PodcastMenuRow.Entry(
                strings.podcastSettings,
                leading = PodiumSymbol.Settings,
                value = when (repository.provider) {
                    PodcastProvider.ITUNES -> "Apple (Free)"
                    PodcastProvider.YOUTUBE_MUSIC -> "YouTube Music"
                    PodcastProvider.PODCAST_INDEX -> "Podcast Index"
                },
            ) {
                navigate(PodcastPlace.Settings)
            },
        )

        if (recents.isNotEmpty()) {
            add(PodcastMenuRow.Section(strings.recentEpisodes))
            recents.take(5).forEach { add(PodcastMenuRow.Recent(it)) }
        }
    }

    val focus = rememberFocusListState("podcast-main-menu")
    val activate: (Int) -> Unit = { index ->
        when (val row = rows.getOrNull(index)) {
            is PodcastMenuRow.Entry -> row.action()
            is PodcastMenuRow.Recent -> navigate(PodcastPlace.ShowDetails(row.episode.podcastId))
            else -> Unit
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = {
            when (it) {
                is PodcastMenuRow.Entry -> "entry_${it.label}"
                is PodcastMenuRow.Section -> "sec_${it.title}"
                is PodcastMenuRow.Recent -> "recent_${it.episode.id}"
            }
        },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        focusable = { it !is PodcastMenuRow.Section },
        preview = { row ->
            when (row) {
                is PodcastMenuRow.Recent -> row.episode.artworkUrl?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument
                is PodcastMenuRow.Entry -> if (row.label == strings.topPodcasts && topArtworks.isNotEmpty()) MenuPreview.Carousel(topArtworks) else MenuPreview.Instrument
                else -> MenuPreview.Instrument
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            is PodcastMenuRow.Entry -> MenuRow(
                label = row.label,
                focused = focused,
                leading = row.leading,
                value = row.value,
            )
            is PodcastMenuRow.Section -> PodcastSectionHeader(row.title)
            is PodcastMenuRow.Recent -> {
                TrackRow(
                    title = row.episode.title,
                    subtitle = row.episode.podcastTitle,
                    focused = focused,
                    artworkUri = row.episode.artworkUrl,
                    trailing = formatDuration(row.episode.durationMs),
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Top / Trending Podcasts
// -------------------------------------------------------------

@Composable
private fun PodcastTopScreen(
    category: PodcastCategory?,
    repository: PodcastRepository,
    navigate: (PodcastPlace) -> Unit,
    overlay: OverlayHost,
    haptics: PodiumHaptics,
) {
    var loading by remember { mutableStateOf(true) }
    var podcasts by remember { mutableStateOf<List<Podcast>>(emptyList()) }

    LaunchedEffect(category) {
        loading = true
        val results = repository.api.getTopPodcasts(
            countryCode = repository.chartsCountry,
            genreId = category?.genreId,
            limit = 40,
        )
        podcasts = results
        repository.rememberPodcasts(results)
        loading = false
    }

    if (loading) {
        LoadingScreen("Loading top podcasts…")
        return
    }

    if (podcasts.isEmpty()) {
        MessageState(
            symbol = PodiumSymbol.Headphones,
            title = "No podcasts found",
            message = "Check your internet connection.",
        )
        return
    }

    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()
    val focus = rememberFocusListState("podcast-top-${category?.id.orEmpty()}")
    val activate: (Int) -> Unit = { index ->
        podcasts.getOrNull(index)?.let { podcast ->
            repository.rememberPodcast(podcast)
            navigate(PodcastPlace.ShowDetails(podcast.id))
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = podcasts,
        state = focus,
        key = { it.id },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        onLongPress = { index ->
            podcasts.getOrNull(index)?.let { podcast ->
                val isSub = subscriptions.any { it.id == podcast.id }
                overlay.show(
                    MenuSpec(
                        title = podcast.title,
                        actions = listOf(
                            MenuAction(if (isSub) "Unsubscribe" else "Subscribe to podcast") {
                                haptics.favorite(!isSub)
                                repository.toggleSubscription(podcast)
                            },
                            MenuAction("View episodes") {
                                navigate(PodcastPlace.ShowDetails(podcast.id))
                            },
                        ),
                    ),
                )
            }
        },
        preview = { p -> p.artworkUrl?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { podcast, index, focused ->
        val isSub = subscriptions.any { it.id == podcast.id }
        TrackRow(
            title = podcast.title,
            subtitle = podcast.author.ifBlank { podcast.subtitle },
            focused = focused,
            artworkUri = podcast.artworkUrl,
            trailing = if (isSub) "★" else "#${index + 1}",
        )
    }
}

// -------------------------------------------------------------
// 3. Search Podcasts
// -------------------------------------------------------------

private sealed interface PodcastSearchRow {
    data object Field : PodcastSearchRow
    data object Loading : PodcastSearchRow
    data class Message(val text: String) : PodcastSearchRow
    data class Item(val podcast: Podcast) : PodcastSearchRow
}

@Composable
private fun PodcastSearchScreen(
    repository: PodcastRepository,
    navigate: (PodcastPlace) -> Unit,
    overlay: OverlayHost,
    haptics: PodiumHaptics,
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Podcast>>(emptyList()) }

    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    KeyboardAwayOnLeave()

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(400)
        val res = repository.api.search(
            query = q,
            provider = repository.provider,
            piKey = repository.podcastIndexApiKey,
            piSecret = repository.podcastIndexApiSecret,
        )
        results = res
        repository.rememberPodcasts(res)
        loading = false
    }

    val rows = buildList {
        add(PodcastSearchRow.Field)
        if (loading) {
            add(PodcastSearchRow.Loading)
        } else if (query.trim().length >= 2 && results.isEmpty()) {
            add(PodcastSearchRow.Message("No podcasts found for \"$query\""))
        } else {
            results.forEach { add(PodcastSearchRow.Item(it)) }
        }
    }

    val focus = rememberFocusListState("podcast-search")
    val activate: (Int) -> Unit = { index ->
        when (val row = rows.getOrNull(index)) {
            PodcastSearchRow.Field -> fieldFocus.requestFocus()
            is PodcastSearchRow.Item -> {
                repository.rememberPodcast(row.podcast)
                navigate(PodcastPlace.ShowDetails(row.podcast.id))
            }
            else -> Unit
        }
    }
    ListInputEffect(focus, activate)

    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()

    FocusList(
        items = rows,
        state = focus,
        key = {
            when (it) {
                PodcastSearchRow.Field -> "search_field"
                PodcastSearchRow.Loading -> "loading"
                is PodcastSearchRow.Message -> "msg_${it.text}"
                is PodcastSearchRow.Item -> "item_${it.podcast.id}"
            }
        },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        onLongPress = { index ->
            val row = rows.getOrNull(index)
            if (row is PodcastSearchRow.Item) {
                val p = row.podcast
                val isSub = subscriptions.any { it.id == p.id }
                overlay.show(
                    MenuSpec(
                        title = p.title,
                        actions = listOf(
                            MenuAction(if (isSub) "Unsubscribe" else "Subscribe to podcast") {
                                haptics.favorite(!isSub)
                                repository.toggleSubscription(p)
                            },
                            MenuAction("View episodes") {
                                navigate(PodcastPlace.ShowDetails(p.id))
                            },
                        ),
                    ),
                )
            }
        },
        focusable = { it !is PodcastSearchRow.Loading && it !is PodcastSearchRow.Message },
        preview = { row ->
            when (row) {
                is PodcastSearchRow.Item -> row.podcast.artworkUrl?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument
                else -> MenuPreview.Instrument
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            PodcastSearchRow.Field -> PaperTextField(
                value = query,
                onChange = { query = it },
                focus = fieldFocus,
                onDone = { focusManager.clearFocus() },
                placeholder = "Search podcast title or topic…",
                description = "Search podcasts",
                action = KeyboardAction.SEARCH,
            )
            PodcastSearchRow.Loading -> LoadingRow("Searching podcasts…")
            is PodcastSearchRow.Message -> PodcastSectionHeader(row.text)
            is PodcastSearchRow.Item -> {
                val isSub = subscriptions.any { it.id == row.podcast.id }
                TrackRow(
                    title = row.podcast.title,
                    subtitle = row.podcast.author.ifBlank { row.podcast.subtitle },
                    focused = focused,
                    artworkUri = row.podcast.artworkUrl,
                    trailing = if (isSub) "★" else null,
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 4. Subscriptions Screen
// -------------------------------------------------------------

@Composable
private fun PodcastSubscriptionsScreen(
    repository: PodcastRepository,
    navigate: (PodcastPlace) -> Unit,
    overlay: OverlayHost,
    haptics: PodiumHaptics,
) {
    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()

    if (subscriptions.isEmpty()) {
        MessageState(
            symbol = PodiumSymbol.Favorite,
            title = "No subscriptions yet",
            message = "Browse Top Podcasts or search to follow your favorite shows.",
        )
        return
    }

    val focus = rememberFocusListState("podcast-subscriptions")
    val activate: (Int) -> Unit = { index ->
        subscriptions.getOrNull(index)?.let { podcast ->
            repository.rememberPodcast(podcast)
            navigate(PodcastPlace.ShowDetails(podcast.id))
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = subscriptions,
        state = focus,
        key = { it.id },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        onLongPress = { index ->
            subscriptions.getOrNull(index)?.let { podcast ->
                overlay.show(
                    MenuSpec(
                        title = podcast.title,
                        actions = listOf(
                            MenuAction("Unsubscribe") {
                                haptics.favorite(false)
                                repository.toggleSubscription(podcast)
                            },
                        ),
                    ),
                )
            }
        },
        preview = { p -> p.artworkUrl?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { podcast, _, focused ->
        TrackRow(
            title = podcast.title,
            subtitle = podcast.author.ifBlank { podcast.subtitle },
            focused = focused,
            artworkUri = podcast.artworkUrl,
            trailing = "★",
        )
    }
}

// -------------------------------------------------------------
// 5. Categories Screen
// -------------------------------------------------------------

@Composable
private fun PodcastCategoriesScreen(
    navigate: (PodcastPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val categories = remember { PodcastCategory.entries }
    val focus = rememberFocusListState("podcast-categories")
    val activate: (Int) -> Unit = { index ->
        categories.getOrNull(index)?.let { cat ->
            navigate(PodcastPlace.Top(cat))
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = categories,
        state = focus,
        key = { it.id },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { cat, _, focused ->
        MenuRow(
            label = strings.podcastCategoryLabel(cat),
            focused = focused,
            leading = PodiumSymbol.Library,
        )
    }
}

// -------------------------------------------------------------
// 6. Recent Episodes Screen
// -------------------------------------------------------------

@Composable
private fun PodcastRecentEpisodesScreen(
    repository: PodcastRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    navigate: (PodcastPlace) -> Unit,
    onOpenNowPlaying: () -> Unit,
    overlay: OverlayHost,
    haptics: PodiumHaptics,
) {
    val recents by repository.recentEpisodes.collectAsStateWithLifecycle()

    if (recents.isEmpty()) {
        MessageState(
            symbol = PodiumSymbol.Headphones,
            title = "No recent episodes",
            message = "Episodes you listen to will appear here.",
        )
        return
    }

    val playingSnapshot by controller.snapshot.collectAsStateWithLifecycle()
    val playingTrackId = playingSnapshot.item?.trackId

    val focus = rememberFocusListState("podcast-recent-episodes")
    val activate: (Int) -> Unit = { index ->
        if (index in recents.indices) {
            playEpisode(recents, index, null, repository, catalog, controller, onOpenNowPlaying)
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = recents,
        state = focus,
        key = { it.id },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        onLongPress = { index ->
            recents.getOrNull(index)?.let { ep ->
                overlay.show(
                    MenuSpec(
                        title = ep.title,
                        actions = listOf(
                            MenuAction("Play episode") {
                                playEpisode(recents, index, null, repository, catalog, controller, onOpenNowPlaying)
                            },
                            MenuAction("Go to podcast show") {
                                navigate(PodcastPlace.ShowDetails(ep.podcastId))
                            },
                        ),
                    ),
                )
            }
        },
        preview = { ep -> ep.artworkUrl?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { ep, _, focused ->
        val track = ep.toPodiumTrack()
        TrackRow(
            title = ep.title,
            subtitle = ep.podcastTitle,
            focused = focused,
            artworkUri = ep.artworkUrl,
            trailing = formatDuration(ep.durationMs),
            active = track.id == playingTrackId,
        )
    }
}

// -------------------------------------------------------------
// 7. Show Details & Episodes List
// -------------------------------------------------------------

private sealed interface ShowDetailsRow {
    data class Header(val podcast: Podcast) : ShowDetailsRow
    data class SubscribeToggle(val isSubscribed: Boolean) : ShowDetailsRow
    data class Section(val title: String) : ShowDetailsRow
    data object Loading : ShowDetailsRow
    data class EpisodeItem(val episode: PodcastEpisode, val index: Int) : ShowDetailsRow
}

@Composable
private fun PodcastShowDetailsScreen(
    podcastId: String,
    repository: PodcastRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    navigate: (PodcastPlace) -> Unit,
    onOpenNowPlaying: () -> Unit,
    overlay: OverlayHost,
    haptics: PodiumHaptics,
) {
    val strings = LocalAppStrings.current
    var podcast by remember(podcastId) { mutableStateOf(repository.getPodcast(podcastId)) }
    var episodes by remember(podcastId) { mutableStateOf(repository.getCachedEpisodes(podcastId).orEmpty()) }
    var loading by remember(podcastId) { mutableStateOf(episodes.isEmpty()) }

    LaunchedEffect(podcastId) {
        if (episodes.isEmpty()) {
            val target = podcast ?: Podcast(id = podcastId, title = "Podcast", author = "", feedUrl = "")
            val loaded = repository.api.getEpisodes(
                podcast = target,
                piKey = repository.podcastIndexApiKey,
                piSecret = repository.podcastIndexApiSecret,
            )
            episodes = loaded
            repository.rememberEpisodes(podcastId, loaded)
            loading = false
        }
    }

    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()
    val isSubscribed = subscriptions.any { it.id == podcastId }

    val playingSnapshot by controller.snapshot.collectAsStateWithLifecycle()
    val playingTrackId = playingSnapshot.item?.trackId

    val currentPodcast = podcast ?: Podcast(id = podcastId, title = "Podcast", author = "", feedUrl = "")

    val rows = buildList {
        add(ShowDetailsRow.Header(currentPodcast))
        add(ShowDetailsRow.SubscribeToggle(isSubscribed))
        if (loading) {
            add(ShowDetailsRow.Loading)
        } else {
            add(ShowDetailsRow.Section("Episodes (${episodes.size})"))
            episodes.forEachIndexed { idx, ep ->
                add(ShowDetailsRow.EpisodeItem(ep, idx))
            }
        }
    }

    val focus = rememberFocusListState("podcast-show-$podcastId")
    val activate: (Int) -> Unit = { index ->
        when (val row = rows.getOrNull(index)) {
            is ShowDetailsRow.SubscribeToggle -> {
                haptics.favorite(!isSubscribed)
                repository.toggleSubscription(currentPodcast)
            }
            is ShowDetailsRow.EpisodeItem -> {
                playEpisode(episodes, row.index, currentPodcast, repository, catalog, controller, onOpenNowPlaying)
            }
            else -> Unit
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = {
            when (it) {
                is ShowDetailsRow.Header -> "header"
                is ShowDetailsRow.SubscribeToggle -> "sub_btn"
                is ShowDetailsRow.Section -> "sec_${it.title}"
                ShowDetailsRow.Loading -> "loading"
                is ShowDetailsRow.EpisodeItem -> "ep_${it.episode.id}"
            }
        },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        onLongPress = { index ->
            val row = rows.getOrNull(index)
            if (row is ShowDetailsRow.EpisodeItem) {
                val ep = row.episode
                overlay.show(
                    MenuSpec(
                        title = ep.title,
                        actions = listOf(
                            MenuAction("Play episode") {
                                playEpisode(episodes, row.index, currentPodcast, repository, catalog, controller, onOpenNowPlaying)
                            },
                            MenuAction("Episode notes & info") {
                                navigate(PodcastPlace.EpisodeDetails(podcastId, ep.id))
                            },
                        ),
                    ),
                )
            }
        },
        focusable = { it !is ShowDetailsRow.Header && it !is ShowDetailsRow.Section && it !is ShowDetailsRow.Loading },
        preview = { currentPodcast.artworkUrl?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            is ShowDetailsRow.Header -> {
                DetailHeader(
                    artworkUri = row.podcast.artworkUrl,
                    title = row.podcast.title,
                    subtitle = row.podcast.author,
                    details = listOfNotNull(
                        row.podcast.description?.take(180)?.ifBlank { null },
                    ),
                )
            }
            is ShowDetailsRow.SubscribeToggle -> {
                MenuRow(
                    label = if (row.isSubscribed) strings.subscribed else strings.subscribe,
                    focused = focused,
                    leading = if (row.isSubscribed) PodiumSymbol.Check else PodiumSymbol.Favorite,
                    value = if (row.isSubscribed) "★" else null,
                )
            }
            is ShowDetailsRow.Section -> PodcastSectionHeader(row.title)
            ShowDetailsRow.Loading -> LoadingRow("Loading episodes…")
            is ShowDetailsRow.EpisodeItem -> {
                val ep = row.episode
                val track = ep.toPodiumTrack(currentPodcast)
                val pos = repository.getPlayedPosition(ep.id)
                val subtitle = buildString {
                    if (!ep.releaseDate.isNullOrBlank()) append(ep.releaseDate).append(" · ")
                    append(formatDuration(ep.durationMs))
                    if (pos > 10_000L) append(" · resumed")
                }
                TrackRow(
                    title = ep.title,
                    subtitle = subtitle,
                    focused = focused,
                    artworkUri = ep.artworkUrl ?: currentPodcast.artworkUrl,
                    trailing = if (pos > 0) "▶" else null,
                    active = track.id == playingTrackId,
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 8. Episode Details Screen
// -------------------------------------------------------------

private enum class EpisodeDetailsRow {
    PLAY_HEADER,
    DESCRIPTION
}

@Composable
private fun PodcastEpisodeDetailsScreen(
    podcastId: String,
    episodeId: String,
    repository: PodcastRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    onOpenNowPlaying: () -> Unit,
) {
    val episode = remember(episodeId) { repository.getEpisode(episodeId) }
    val podcast = remember(podcastId) { repository.getPodcast(podcastId) }

    if (episode == null) {
        MessageState(
            symbol = PodiumSymbol.Headphones,
            title = "Episode details not available",
            message = "Return to episodes list to play.",
        )
        return
    }

    val rows = EpisodeDetailsRow.entries
    val focus = rememberFocusListState("podcast-episode-$episodeId")
    val activate: (Int) -> Unit = { index ->
        when (rows.getOrNull(index)) {
            EpisodeDetailsRow.PLAY_HEADER -> {
                playEpisode(listOf(episode), 0, podcast, repository, catalog, controller, onOpenNowPlaying)
            }
            else -> Unit
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.name },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { episode.artworkUrl?.let { MenuPreview.Artwork(listOf(it)) } ?: MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        when (item) {
            EpisodeDetailsRow.PLAY_HEADER -> {
                DetailHeader(
                    artworkUri = episode.artworkUrl ?: podcast?.artworkUrl,
                    title = episode.title,
                    subtitle = episode.podcastTitle,
                    details = listOfNotNull(
                        episode.releaseDate,
                        formatDuration(episode.durationMs),
                    ),
                )
            }
            EpisodeDetailsRow.DESCRIPTION -> {
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.m)) {
                    PodiumText(
                        text = episode.description?.ifBlank { "No description available." } ?: "No description available.",
                        style = PodiumTheme.type.body,
                        color = PodiumTheme.colors.labelPrimary,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 9. Add Podcast by RSS
// -------------------------------------------------------------

private enum class AddRssRow {
    FIELD,
    PASTE,
    LOAD
}

@Composable
private fun PodcastAddRssScreen(
    repository: PodcastRepository,
    navigate: (PodcastPlace) -> Unit,
    overlay: OverlayHost,
    haptics: PodiumHaptics,
) {
    var rssUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val urlFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    KeyboardAwayOnLeave()

    val rows = AddRssRow.entries
    val focus = rememberFocusListState("podcast-add-rss")
    val activate: (Int) -> Unit = { index ->
        when (rows.getOrNull(index)) {
            AddRssRow.FIELD -> urlFocus.requestFocus()
            AddRssRow.PASTE -> {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clip?.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    rssUrl = text
                    haptics.press()
                }
            }
            AddRssRow.LOAD -> {
                val target = rssUrl.trim()
                if (target.isBlank() || (!target.startsWith("http://") && !target.startsWith("https://"))) {
                    errorMessage = "Please enter a valid RSS URL (https://…)"
                } else {
                    loading = true
                    errorMessage = null
                    scope.launch {
                        val (parsedPodcast, episodes) = repository.api.parseCustomRss(target)
                        loading = false
                        if (parsedPodcast != null) {
                            val saved = parsedPodcast.copy(isCustomRss = true, isSubscribed = true)
                            repository.rememberPodcast(saved)
                            repository.toggleSubscription(saved)
                            if (episodes.isNotEmpty()) {
                                repository.rememberEpisodes(saved.id, episodes)
                            }
                            navigate(PodcastPlace.ShowDetails(saved.id))
                        } else {
                            errorMessage = "Could not parse RSS feed. Check URL and try again."
                        }
                    }
                }
            }
            else -> Unit
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.name },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        when (item) {
            AddRssRow.FIELD -> PaperTextField(
                value = rssUrl,
                onChange = { rssUrl = it; errorMessage = null },
                focus = urlFocus,
                onDone = { focusManager.clearFocus() },
                placeholder = "Podcast RSS Feed URL (https://…)",
                description = "RSS Feed URL",
                action = KeyboardAction.DONE,
            )
            AddRssRow.PASTE -> MenuRow(
                label = "Paste RSS URL from clipboard",
                focused = focused,
                leading = PodiumSymbol.Queue,
            )
            AddRssRow.LOAD -> {
                if (loading) {
                    LoadingRow("Loading feed…")
                } else {
                    MenuRow(
                        label = if (errorMessage != null) errorMessage!! else "Add and open podcast",
                        focused = focused,
                        leading = PodiumSymbol.Check,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 10. Podcast Settings Screen
// -------------------------------------------------------------

private enum class PodcastSettingsRow {
    PROVIDER,
    COUNTRY,
    API_KEY,
    API_SECRET,
    SAVE
}

@Composable
private fun PodcastSettingsScreen(
    repository: PodcastRepository,
    onBack: () -> Unit,
    overlay: OverlayHost,
    haptics: PodiumHaptics,
) {
    var provider by remember { mutableStateOf(repository.provider) }
    var apiKey by remember { mutableStateOf(repository.podcastIndexApiKey) }
    var apiSecret by remember { mutableStateOf(repository.podcastIndexApiSecret) }
    var country by remember { mutableStateOf(repository.chartsCountry) }

    val keyFocus = remember { FocusRequester() }
    val secretFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    KeyboardAwayOnLeave()

    val rows = buildList {
        add(PodcastSettingsRow.PROVIDER)
        add(PodcastSettingsRow.COUNTRY)
        if (provider == PodcastProvider.PODCAST_INDEX) {
            add(PodcastSettingsRow.API_KEY)
            add(PodcastSettingsRow.API_SECRET)
        }
        add(PodcastSettingsRow.SAVE)
    }

    val focus = rememberFocusListState("podcast-settings")
    val activate: (Int) -> Unit = { index ->
        when (rows.getOrNull(index)) {
            PodcastSettingsRow.PROVIDER -> {
                provider = when (provider) {
                    PodcastProvider.ITUNES -> PodcastProvider.YOUTUBE_MUSIC
                    PodcastProvider.YOUTUBE_MUSIC -> PodcastProvider.PODCAST_INDEX
                    PodcastProvider.PODCAST_INDEX -> PodcastProvider.ITUNES
                }
                repository.provider = provider
                haptics.press()
            }
            PodcastSettingsRow.COUNTRY -> {
                country = when (country) {
                    "us" -> "tn"
                    "tn" -> "fr"
                    "fr" -> "sa"
                    else -> "us"
                }
                repository.chartsCountry = country
                haptics.press()
            }
            PodcastSettingsRow.API_KEY -> keyFocus.requestFocus()
            PodcastSettingsRow.API_SECRET -> secretFocus.requestFocus()
            PodcastSettingsRow.SAVE -> {
                repository.podcastIndexApiKey = apiKey
                repository.podcastIndexApiSecret = apiSecret
                repository.chartsCountry = country
                repository.provider = provider
                haptics.press()
                onBack()
            }
            else -> Unit
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.name },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        when (item) {
            PodcastSettingsRow.PROVIDER -> MenuRow(
                label = "Provider engine",
                focused = focused,
                value = when (provider) {
                    PodcastProvider.ITUNES -> "Apple Podcasts (Free)"
                    PodcastProvider.YOUTUBE_MUSIC -> "YouTube Music"
                    PodcastProvider.PODCAST_INDEX -> "Podcast Index"
                },
            )
            PodcastSettingsRow.COUNTRY -> MenuRow(
                label = "Top charts region",
                focused = focused,
                value = when (country) {
                    "tn" -> "Tunisia 🇹🇳"
                    "fr" -> "France 🇫🇷"
                    "sa" -> "Arab World 🇸🇦"
                    else -> "Global / US 🌐"
                },
            )
            PodcastSettingsRow.API_KEY -> PaperTextField(
                value = apiKey,
                onChange = { apiKey = it },
                focus = keyFocus,
                onDone = { focusManager.clearFocus() },
                placeholder = "Podcast Index API Key",
                description = "API Key",
                action = KeyboardAction.DONE,
            )
            PodcastSettingsRow.API_SECRET -> PaperTextField(
                value = apiSecret,
                onChange = { apiSecret = it },
                focus = secretFocus,
                onDone = { focusManager.clearFocus() },
                placeholder = "Podcast Index API Secret",
                description = "API Secret",
                action = KeyboardAction.DONE,
            )
            PodcastSettingsRow.SAVE -> MenuRow(
                label = "Save settings",
                focused = focused,
                leading = PodiumSymbol.Check,
            )
        }
    }
}

// -------------------------------------------------------------
// Helpers
// -------------------------------------------------------------

@Composable
private fun PodcastSectionHeader(text: String) {
    PodiumText(
        text = text,
        style = PodiumTheme.type.caption,
        color = PodiumTheme.colors.labelSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = 6.dp)
            .semantics { heading() },
    )
}

private fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0L) return ""
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun playEpisode(
    episodes: List<PodcastEpisode>,
    index: Int,
    podcast: Podcast?,
    repository: PodcastRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    onOpenNowPlaying: () -> Unit,
) {
    if (episodes.isEmpty()) return
    val safeIndex = index.coerceIn(0, episodes.lastIndex)
    val target = episodes[safeIndex]
    if (podcast != null) {
        repository.rememberPodcast(podcast)
    }
    repository.rememberEpisodes(target.podcastId, episodes)
    repository.addRecentEpisode(target)

    val tracks = episodes.map { it.toPodiumTrack(podcast) }
    catalog.remember(tracks)
    controller.playContext(tracks.map { it.id }, safeIndex, podcast?.title ?: target.podcastTitle)

    val savedPos = repository.getPlayedPosition(target.id)
    if (savedPos > 5_000L) {
        controller.seekTo(savedPos)
    }
    onOpenNowPlaying()
}

fun app.podium.core.designsystem.l10n.AppStrings.podcastCategoryLabel(category: PodcastCategory): String = when (category) {
    PodcastCategory.TRENDING -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "الشائع والأفضل" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Tendances / Top" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Trendler / Zirve" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Популярное / Топ" else category.label
    PodcastCategory.NEWS -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "الأخبار" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Actualités" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Haberler" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Новости" else category.label
    PodcastCategory.COMEDY -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "كوميديا" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Comédie" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Komedi" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Комедия" else category.label
    PodcastCategory.TECH -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "التكنولوجيا" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Technologie" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Teknoloji" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Технологии" else category.label
    PodcastCategory.TRUE_CRIME -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "جرائم واقعية" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Faits divers / Crimes" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Gerçek Suç" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Детективы" else category.label
    PodcastCategory.SOCIETY -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "المجتمع والثقافة" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Société et culture" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Toplum ve Kültür" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Общество и культура" else category.label
    PodcastCategory.RELIGION -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "الدين والروحانيات" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Religion et spiritualité" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Din ve Maneviyat" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Религия и духовность" else category.label
    PodcastCategory.SPORTS -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "الرياضة" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Sports" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Spor" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Спорт" else category.label
    PodcastCategory.BUSINESS -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "الأعمال والاستثمار" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Affaires et économie" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "İş Dünyası" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Бизнес" else category.label
    PodcastCategory.SCIENCE -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "العلوم" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Sciences" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Bilim" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Наука" else category.label
    PodcastCategory.HISTORY -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "التاريخ" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Histoire" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Tarih" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "История" else category.label
    PodcastCategory.ARTS -> if (this is app.podium.core.designsystem.l10n.ArabicStrings) "الفنون" else if (this is app.podium.core.designsystem.l10n.FrenchStrings) "Arts" else if (this is app.podium.core.designsystem.l10n.TurkishStrings) "Sanat" else if (this is app.podium.core.designsystem.l10n.RussianStrings) "Искусство" else category.label
}
