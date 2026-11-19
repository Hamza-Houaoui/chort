package app.podium.soundcloud

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.core.designsystem.artwork.ArtworkImage
import app.podium.core.designsystem.component.FocusList
import app.podium.core.designsystem.component.KeyboardAction
import app.podium.core.designsystem.component.KeyboardAwayOnLeave
import app.podium.core.designsystem.component.ListInputEffect
import app.podium.core.designsystem.component.LocalMiniature
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.component.MenuPreview
import app.podium.core.designsystem.component.MenuRow
import app.podium.core.designsystem.component.PaperTextField
import app.podium.core.designsystem.component.TrackRow
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.symbol.Symbol
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.theme.Spacing
import app.podium.core.designsystem.tonal.ContinuousShape
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelButton
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberFocusListState
import app.podium.core.interaction.rememberPodiumHaptics
import app.podium.player.api.PlaybackController
import app.podium.player.api.TrackCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SoundCloudScreen(
    place: SoundCloudPlace,
    repository: SoundCloudRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    navigate: (SoundCloudPlace) -> Unit,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    when (place) {
        SoundCloudPlace.Menu -> SoundCloudMenuScreen(
            repository = repository,
            navigate = navigate,
        )
        SoundCloudPlace.WebLogin -> SoundCloudWebLoginScreen(
            repository = repository,
            onSuccess = { navigate(SoundCloudPlace.Menu) },
            onBack = onBack,
        )
        SoundCloudPlace.Profile -> SoundCloudProfileScreen(
            repository = repository,
            navigate = navigate,
        )
        SoundCloudPlace.Stream -> SoundCloudTrackListScreen(
            title = "Stream",
            loadTracks = { repository.loadStream(); repository.streamTracks },
            catalog = catalog,
            controller = controller,
            onOpenNowPlaying = onOpenNowPlaying,
        )
        SoundCloudPlace.Likes -> SoundCloudTrackListScreen(
            title = "Likes",
            loadTracks = { repository.loadLikes(); repository.likedTracks },
            catalog = catalog,
            controller = controller,
            onOpenNowPlaying = onOpenNowPlaying,
        )
        SoundCloudPlace.Playlists -> SoundCloudPlaylistsScreen(
            repository = repository,
            navigate = navigate,
        )
        is SoundCloudPlace.PlaylistDetail -> SoundCloudPlaylistDetailScreen(
            playlistId = place.playlistId,
            playlistTitle = place.playlistTitle,
            repository = repository,
            catalog = catalog,
            controller = controller,
            onOpenNowPlaying = onOpenNowPlaying,
        )
        SoundCloudPlace.Charts -> SoundCloudTrackListScreen(
            title = "Top 50 Charts",
            loadTracks = { repository.loadCharts(); repository.chartsTracks },
            catalog = catalog,
            controller = controller,
            onOpenNowPlaying = onOpenNowPlaying,
        )
        SoundCloudPlace.Search -> SoundCloudSearchScreen(
            repository = repository,
            catalog = catalog,
            controller = controller,
            onOpenNowPlaying = onOpenNowPlaying,
        )
    }
}

// -------------------------------------------------------------
// 1. Menu Screen
// -------------------------------------------------------------

private data class SoundCloudMenuRowItem(
    val label: String,
    val leading: PodiumSymbol? = null,
    val value: String? = null,
    val previewArt: List<String> = emptyList(),
    val action: () -> Unit,
)

@Composable
private fun SoundCloudMenuScreen(
    repository: SoundCloudRepository,
    navigate: (SoundCloudPlace) -> Unit,
) {
    val isLoggedIn by repository.isLoggedIn.collectAsStateWithLifecycle()
    val currentUser by repository.currentUser.collectAsStateWithLifecycle()
    val streamTracks by repository.streamTracks.collectAsStateWithLifecycle()
    val likedTracks by repository.likedTracks.collectAsStateWithLifecycle()
    val playlists by repository.userPlaylists.collectAsStateWithLifecycle()
    val chartsTracks by repository.chartsTracks.collectAsStateWithLifecycle()

    val streamArt = remember(streamTracks) { streamTracks.mapNotNull { it.artworkUrl }.take(5) }
    val likesArt = remember(likedTracks) { likedTracks.mapNotNull { it.artworkUrl }.take(5) }
    val chartsArt = remember(chartsTracks) { chartsTracks.mapNotNull { it.artworkUrl }.take(5) }

    val rows = buildList {
        if (isLoggedIn && currentUser != null) {
            val user = currentUser!!
            add(
                SoundCloudMenuRowItem(
                    label = user.username,
                    leading = PodiumSymbol.Person,
                    value = "${user.followersCount} followers",
                    previewArt = listOfNotNull(user.avatarUrl),
                    action = { navigate(SoundCloudPlace.Profile) },
                )
            )
            add(
                SoundCloudMenuRowItem(
                    label = "Stream",
                    leading = PodiumSymbol.Headphones,
                    value = if (streamTracks.isNotEmpty()) "${streamTracks.size}" else null,
                    previewArt = streamArt,
                    action = { navigate(SoundCloudPlace.Stream) },
                )
            )
            add(
                SoundCloudMenuRowItem(
                    label = "Likes",
                    leading = PodiumSymbol.Favorite,
                    value = if (likedTracks.isNotEmpty()) "${likedTracks.size}" else if (user.likesCount > 0) "${user.likesCount}" else null,
                    previewArt = likesArt,
                    action = { navigate(SoundCloudPlace.Likes) },
                )
            )
            add(
                SoundCloudMenuRowItem(
                    label = "Playlists",
                    leading = PodiumSymbol.PlaylistPlay,
                    value = if (playlists.isNotEmpty()) "${playlists.size}" else if (user.playlistCount > 0) "${user.playlistCount}" else null,
                    action = { navigate(SoundCloudPlace.Playlists) },
                )
            )
        } else {
            add(
                SoundCloudMenuRowItem(
                    label = "Sign in to SoundCloud",
                    leading = PodiumSymbol.Lock,
                    action = { navigate(SoundCloudPlace.WebLogin) },
                )
            )
        }

        add(
            SoundCloudMenuRowItem(
                label = "Top 50 Charts",
                leading = PodiumSymbol.Sparkle,
                previewArt = chartsArt,
                action = { navigate(SoundCloudPlace.Charts) },
            )
        )
        add(
            SoundCloudMenuRowItem(
                label = "Search SoundCloud",
                leading = PodiumSymbol.Search,
                action = { navigate(SoundCloudPlace.Search) },
            )
        )

        if (isLoggedIn) {
            add(
                SoundCloudMenuRowItem(
                    label = "Sign Out",
                    leading = PodiumSymbol.Power,
                    action = { repository.signOut() },
                )
            )
        }
    }

    val focus = rememberFocusListState("soundcloud-menu")
    val activate: (Int) -> Unit = { index ->
        val item = rows.getOrNull(index)
        item?.action?.invoke()
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.label },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { item ->
            if (item.previewArt.isNotEmpty()) MenuPreview.Carousel(item.previewArt) else MenuPreview.Instrument
        },
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        MenuRow(
            label = item.label,
            focused = focused,
            value = item.value,
        )
    }
}

@Composable
private fun SoundCloudProfileScreen(
    repository: SoundCloudRepository,
    navigate: (SoundCloudPlace) -> Unit,
) {
    val user by repository.currentUser.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val strings = LocalAppStrings.current
    val isAr = strings.settings == "الإعدادات"
    val haptics = rememberPodiumHaptics()
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        repository.refreshUserProfile()
    }

    val u = user
    if (u == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.top),
            contentAlignment = Alignment.Center,
        ) {
            PodiumText(
                text = if (isAr) "جاري تحميل معلومات الحساب..." else "Loading account profile...",
                style = type.body,
                color = colors.labelSecondary,
            )
        }
        return
    }

    val location = listOfNotNull(u.city, u.country).filter { it.isNotBlank() }.joinToString(", ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = insets.top + 8.dp, start = 16.dp, end = 16.dp, bottom = insets.bottom + 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Circular HD Avatar
        ArtworkImage(
            uri = u.avatarUrl,
            size = 92.dp,
            fallbackText = u.username,
            round = true,
        )

        // Name & @username
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PodiumText(
                text = u.fullName?.takeIf { it.isNotBlank() } ?: u.username,
                style = type.rowFocused,
                color = colors.labelPrimary,
                textAlign = TextAlign.Center,
            )
            PodiumText(
                text = "@${u.username}",
                style = type.rowSecondary,
                color = colors.labelSecondary,
                textAlign = TextAlign.Center,
            )
            if (location.isNotBlank()) {
                PodiumText(
                    text = location,
                    style = type.footnote,
                    color = colors.labelTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Bio / Description
        if (!u.description.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.canvasRaised, ContinuousShape(14.dp))
                    .border(1.dp, colors.separator, ContinuousShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                PodiumText(
                    text = u.description,
                    style = type.footnote,
                    color = colors.labelSecondary,
                    maxLines = 5,
                )
            }
        }

        // Stats Row 1: Followers / Following / Likes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileStatCard(
                label = if (isAr) "المتابعون" else "Followers",
                value = u.followersCount.toString(),
                modifier = Modifier.weight(1f),
            )
            ProfileStatCard(
                label = if (isAr) "يتابع" else "Following",
                value = u.followingsCount.toString(),
                modifier = Modifier.weight(1f),
            )
            ProfileStatCard(
                label = if (isAr) "الإعجابات" else "Likes",
                value = u.likesCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        // Stats Row 2: Tracks / Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileStatCard(
                label = if (isAr) "المقاطع" else "Tracks",
                value = u.trackCount.toString(),
                modifier = Modifier.weight(1f),
            )
            ProfileStatCard(
                label = if (isAr) "قوائم التشغيل" else "Playlists",
                value = u.playlistCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        // Quick Actions: Likes / Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileActionButton(
                label = strings.likes,
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.press()
                    navigate(SoundCloudPlace.Likes)
                },
            )
            ProfileActionButton(
                label = strings.playlists,
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.press()
                    navigate(SoundCloudPlace.Playlists)
                },
            )
        }

        // Refresh button
        ProfileActionButton(
            label = if (refreshing) {
                if (isAr) "جاري التحديث..." else "Refreshing..."
            } else {
                if (isAr) "تحديث المعلومات" else "Refresh Profile"
            },
            primary = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                haptics.press()
                scope.launch {
                    refreshing = true
                    repository.refreshUserProfile()
                    delay(800)
                    refreshing = false
                }
            },
        )
    }
}

@Composable
private fun ProfileStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    Column(
        modifier = modifier
            .background(colors.canvasRaised, ContinuousShape(12.dp))
            .border(1.dp, colors.separator, ContinuousShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PodiumText(value, type.rowFocused, colors.labelPrimary, textAlign = TextAlign.Center)
        PodiumText(label, type.footnote, colors.labelSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    Box(
        modifier
            .height(44.dp)
            .background(if (primary) colors.highlight else colors.canvasRaised, ContinuousShape(14.dp))
            .then(if (primary) Modifier else Modifier.border(1.dp, colors.separator, ContinuousShape(14.dp)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PodiumText(label, type.rowFocused, if (primary) colors.onHighlight else colors.labelPrimary)
    }
}

// -------------------------------------------------------------
// 2. Web Sign-in Screen
// -------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SoundCloudWebLoginScreen(
    repository: SoundCloudRepository,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    if (LocalMiniature.current) {
        Box(Modifier.fillMaxSize())
        return
    }

    val haptics = rememberPodiumHaptics()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0.15f) }
    var showTokenInput by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf(false) }
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val coroutineScope = rememberCoroutineScope()
    var cookiesCleared by remember { mutableStateOf(false) }

    fun finishSignIn(token: String, clientId: String?) {
        val clean = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (clean.isBlank() || completed) return
        mainHandler.post {
            if (!completed) {
                completed = true
                repository.saveSession(clean, clientId)
                haptics.confirm()
                onSuccess()
            }
        }
    }

    fun tryVerifyAndFinish(token: String, clientId: String?) {
        val clean = token.removePrefix("OAuth ").trim()
        if (clean.isBlank() || completed) return
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Verify the token is actually a real logged-in user token via /me
            val testApi = SoundCloudApi(
                clientIdProvider = { clientId ?: SoundCloudApi.FALLBACK_CLIENT_ID },
                clientIdUpdater = {},
                oauthTokenProvider = { clean },
            )
            val user = testApi.getMe()
            if (user != null) {
                finishSignIn(clean, clientId)
            }
        }
    }

    fun checkForAuthCookies(extractedClientId: String? = null): Boolean {
        if (!cookiesCleared) return false
        val cm = CookieManager.getInstance()
        val origins = listOf(
            "https://soundcloud.com",
            "https://m.soundcloud.com",
            "https://secure.soundcloud.com",
            "https://api-v2.soundcloud.com",
        )
        val oauthRegex = Regex("oauth_token=([a-zA-Z0-9_-]+)")
        for (origin in origins) {
            val cookieStr = cm.getCookie(origin) ?: continue
            val match = oauthRegex.find(cookieStr)
            if (match != null) {
                val token = match.groupValues[1]
                if (token.isNotBlank()) {
                    tryVerifyAndFinish(token, extractedClientId)
                    return true
                }
            }
        }
        return false
    }

    LaunchedEffect(Unit) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            cookiesCleared = true
        }
        while (!completed) {
            delay(1500)
            checkForAuthCookies()
        }
    }

    BackHandler { onBack() }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                val scrollStepPx = 160
                webViewInstance?.scrollBy(0, input.detents * scrollStepPx)
                haptics.step(level = 0.4f)
                true
            }
            is PodiumInput.Press -> when (input.button) {
                WheelButton.MENU -> {
                    onBack()
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewInstance = null
        }
    }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                .safeDrawingPadding(),
        ) {
            // Top bar with Back / Check Sign-In / Token option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.canvas)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PodiumText(
                    text = "SoundCloud Sign-In",
                    style = type.rowFocused,
                    color = colors.labelPrimary,
                    modifier = Modifier.weight(1f),
                )
                PodiumText(
                    text = if (showTokenInput) "Web Login" else "Use Token",
                    style = type.caption,
                    color = colors.highlight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showTokenInput = !showTokenInput }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
                PodiumText(
                    text = "Done",
                    style = type.row,
                    color = colors.labelPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (!checkForAuthCookies()) {
                                webViewInstance?.loadUrl("https://soundcloud.com/you/library")
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
                PodiumText(
                    text = "Close",
                    style = type.row,
                    color = colors.labelSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            if (showTokenInput) {
                val isAr = LocalAppStrings.current.settings == "الإعدادات"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.canvas)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    PodiumText(
                        text = if (isAr) "أدخل OAuth Token الخاص بحسابك (من الكوكيز oauth_token):" else "Enter your SoundCloud OAuth Token (from oauth_token cookie):",
                        style = type.caption,
                        color = colors.labelSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            if (manualToken.isEmpty()) {
                                PodiumText("2-xxxxxx-xxxxxxxx-xxxxxxxxxxxx", type.row, colors.labelTertiary)
                            }
                            BasicTextField(
                                value = manualToken,
                                onValueChange = { manualToken = it },
                                singleLine = true,
                                textStyle = type.row.copy(color = colors.labelPrimary),
                                cursorBrush = SolidColor(colors.highlight),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.highlight)
                                .clickable {
                                    if (manualToken.isNotBlank()) {
                                        finishSignIn(manualToken.trim(), null)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            PodiumText(if (isAr) "دخول" else "Sign In", type.rowFocused, Color.White)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isLoading && progress < 1f,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(colors.canvas),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(colors.highlight),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                            webViewInstance = this

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = SoundCloudApi.USER_AGENT
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = (newProgress.coerceIn(5, 100)) / 100f
                                    if (newProgress >= 100) {
                                        isLoading = false
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUri = request?.url ?: return false
                                    val scheme = reqUri.scheme?.lowercase() ?: return false
                                    if (scheme != "http" && scheme != "https") {
                                        // Ignore soundcloud:// custom app scheme redirects so it stays in WebView
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    checkForAuthCookies()
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    CookieManager.getInstance().flush()
                                    checkForAuthCookies()
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: ""
                                    var extractedClientId: String? = null
                                    if (reqUrl.contains("client_id=")) {
                                        val clientRegex = Regex("client_id=([a-zA-Z0-9]{32})")
                                        extractedClientId = clientRegex.find(reqUrl)?.groupValues?.getOrNull(1)
                                    }

                                    // Check Authorization header directly in case SoundCloud passes OAuth token in header
                                    val authHeader = request?.requestHeaders?.entries
                                        ?.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
                                        ?.value
                                    if (!authHeader.isNullOrBlank() && authHeader.startsWith("OAuth ", ignoreCase = true)) {
                                        val token = authHeader.substringAfter(" ").trim()
                                        if (token.length > 10) {
                                            tryVerifyAndFinish(token, extractedClientId)
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }

                                    if (extractedClientId != null) {
                                        checkForAuthCookies(extractedClientId)
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            loadUrl("https://soundcloud.com/signin")
                        }
                    },
                    update = { view ->
                        webViewInstance = view
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 3. Track List Screen (Stream, Likes, Charts)
// -------------------------------------------------------------

@Composable
private fun SoundCloudTrackListScreen(
    title: String,
    loadTracks: () -> kotlinx.coroutines.flow.StateFlow<List<SoundCloudTrack>>,
    catalog: TrackCatalog,
    controller: PlaybackController,
    onOpenNowPlaying: () -> Unit,
) {
    val tracksState = remember { loadTracks() }
    val tracks by tracksState.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    val focus = rememberFocusListState("sc-track-list-$title")
    val coroutineScope = rememberCoroutineScope()
    val playingItem by controller.snapshot.collectAsStateWithLifecycle()

    val activate: (Int) -> Unit = { index ->
        val track = tracks.getOrNull(index)
        if (track != null) {
            coroutineScope.launch {
                val podiumTracks = tracks.map { it.toPodiumTrack() }
                catalog.remember(podiumTracks)
                controller.playContext(
                    podiumTracks.map { it.id },
                    index.coerceIn(0, podiumTracks.lastIndex),
                    title,
                )
                onOpenNowPlaying()
            }
        }
    }
    ListInputEffect(focus, activate)

    if (tracks.isEmpty()) {
        val isAr = LocalAppStrings.current.settings == "الإعدادات"
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.top),
            contentAlignment = Alignment.Center,
        ) {
            PodiumText(
                text = if (isAr) "جاري التحميل من ساوند كلاود..." else "Loading from SoundCloud...",
                style = PodiumTheme.type.body,
                color = PodiumTheme.colors.labelSecondary,
            )
        }
    } else {
        FocusList(
            items = tracks,
            state = focus,
            key = { "${it.id}" },
            contentPadding = insets.listPadding(),
            onActivate = activate,
            preview = { MenuPreview.Artwork(listOfNotNull(it.artworkUrl)) },
            modifier = Modifier.fillMaxSize(),
        ) { track, _, focused ->
            val isPlaying = playingItem.item?.trackId?.providerKey == track.id.toString()
            TrackRow(
                title = track.title,
                subtitle = track.artistName,
                focused = focused,
                artworkUri = track.artworkUrl,
                trailing = if (track.durationMs > 0) formatDuration(track.durationMs) else null,
                active = isPlaying,
            )
        }
    }
}

// -------------------------------------------------------------
// 4. Playlists Screen
// -------------------------------------------------------------

@Composable
private fun SoundCloudPlaylistsScreen(
    repository: SoundCloudRepository,
    navigate: (SoundCloudPlace) -> Unit,
) {
    LaunchedEffect(Unit) {
        repository.loadPlaylists()
    }

    val playlists by repository.userPlaylists.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    val focus = rememberFocusListState("sc-playlists-list")

    val activate: (Int) -> Unit = { index ->
        val pl = playlists.getOrNull(index)
        if (pl != null) {
            navigate(SoundCloudPlace.PlaylistDetail(pl.id, pl.title))
        }
    }
    ListInputEffect(focus, activate)

    if (playlists.isEmpty()) {
        val isAr = LocalAppStrings.current.settings == "الإعدادات"
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.top),
            contentAlignment = Alignment.Center,
        ) {
            PodiumText(
                text = if (isAr) "لا توجد قوائم تشغيل محفوظة في حسابك." else "No playlists saved in your account.",
                style = PodiumTheme.type.body,
                color = PodiumTheme.colors.labelSecondary,
            )
        }
    } else {
        FocusList(
            items = playlists,
            state = focus,
            key = { "${it.id}" },
            contentPadding = insets.listPadding(),
            onActivate = activate,
            preview = { MenuPreview.Artwork(listOfNotNull(it.artworkUrl)) },
            modifier = Modifier.fillMaxSize(),
        ) { pl, _, focused ->
            TrackRow(
                title = pl.title,
                subtitle = "${pl.trackCount} tracks • ${pl.authorName}",
                focused = focused,
                artworkUri = pl.artworkUrl,
                trailing = "${pl.trackCount}",
            )
        }
    }
}

// -------------------------------------------------------------
// 5. Playlist Detail Screen
// -------------------------------------------------------------

@Composable
private fun SoundCloudPlaylistDetailScreen(
    playlistId: Long,
    playlistTitle: String,
    repository: SoundCloudRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    onOpenNowPlaying: () -> Unit,
) {
    var playlist by remember { mutableStateOf<SoundCloudPlaylist?>(null) }
    var loading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val insets = LocalScreenInsets.current
    val focus = rememberFocusListState("sc-pl-detail-$playlistId")
    val playingItem by controller.snapshot.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) {
        loading = true
        playlist = repository.getPlaylist(playlistId)
        loading = false
    }

    val tracks = playlist?.tracks.orEmpty()

    val activate: (Int) -> Unit = { index ->
        val track = tracks.getOrNull(index)
        if (track != null) {
            coroutineScope.launch {
                val podiumTracks = tracks.map { it.toPodiumTrack() }
                catalog.remember(podiumTracks)
                controller.playContext(
                    podiumTracks.map { it.id },
                    index.coerceIn(0, podiumTracks.lastIndex),
                    playlistTitle,
                )
                onOpenNowPlaying()
            }
        }
    }
    ListInputEffect(focus, activate)

    val isAr = LocalAppStrings.current.settings == "الإعدادات"
    if (loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.top),
            contentAlignment = Alignment.Center,
        ) {
            PodiumText(
                text = if (isAr) "جاري تحميل قائمة التشغيل..." else "Loading playlist...",
                style = PodiumTheme.type.body,
                color = PodiumTheme.colors.labelSecondary,
            )
        }
    } else if (tracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.top),
            contentAlignment = Alignment.Center,
        ) {
            PodiumText(
                text = if (isAr) "القائمة فارغة أو غير متاحة." else "Playlist is empty or unavailable.",
                style = PodiumTheme.type.body,
                color = PodiumTheme.colors.labelSecondary,
            )
        }
    } else {
        FocusList(
            items = tracks,
            state = focus,
            key = { "${it.id}" },
            contentPadding = insets.listPadding(),
            onActivate = activate,
            preview = { MenuPreview.Artwork(listOfNotNull(it.artworkUrl)) },
            modifier = Modifier.fillMaxSize(),
        ) { track, _, focused ->
            val isPlaying = playingItem.item?.trackId?.providerKey == track.id.toString()
            TrackRow(
                title = track.title,
                subtitle = track.artistName,
                focused = focused,
                artworkUri = track.artworkUrl,
                trailing = if (track.durationMs > 0) formatDuration(track.durationMs) else null,
                active = isPlaying,
            )
        }
    }
}

// -------------------------------------------------------------
// 6. Search Screen
// -------------------------------------------------------------

@Composable
private fun SoundCloudSearchScreen(
    repository: SoundCloudRepository,
    catalog: TrackCatalog,
    controller: PlaybackController,
    onOpenNowPlaying: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SoundCloudTrack>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(400) // Debounce
        results = repository.search(trimmed)
        searching = false
    }

    val focus = rememberFocusListState("sc-search-list")
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val insets = LocalScreenInsets.current
    val coroutineScope = rememberCoroutineScope()
    val playingItem by controller.snapshot.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        delay(200)
        requester.requestFocus()
        keyboard?.show()
    }
    KeyboardAwayOnLeave()

    val activate: (Int) -> Unit = { index ->
        val track = results.getOrNull(index)
        if (track != null) {
            focusManager.clearFocus()
            keyboard?.hide()
            coroutineScope.launch {
                val podiumTracks = results.map { it.toPodiumTrack() }
                catalog.remember(podiumTracks)
                controller.playContext(
                    podiumTracks.map { it.id },
                    index.coerceIn(0, podiumTracks.lastIndex),
                    "SoundCloud: $query",
                )
                onOpenNowPlaying()
            }
        }
    }
    ListInputEffect(focus, activate)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.top + Spacing.m)
    ) {
        val isAr = LocalAppStrings.current.settings == "الإعدادات"
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
                placeholder = if (isAr) "ابحث في SoundCloud (تراك، فنان)..." else "Search SoundCloud (track, artist)...",
                description = if (isAr) "البحث في ساوند كلاود" else "Search SoundCloud",
                action = KeyboardAction.SEARCH,
            )
        }

        if (searching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                PodiumText(
                    text = if (isAr) "جاري البحث في ساوند كلاود..." else "Searching SoundCloud...",
                    style = PodiumTheme.type.footnote,
                    color = PodiumTheme.colors.labelSecondary,
                )
            }
        }

        FocusList(
            items = results,
            state = focus,
            key = { "${it.id}" },
            contentPadding = insets.listPadding(),
            onActivate = activate,
            preview = { MenuPreview.Artwork(listOfNotNull(it.artworkUrl)) },
            modifier = Modifier.fillMaxSize(),
        ) { track, _, focused ->
            val isPlaying = playingItem.item?.trackId?.providerKey == track.id.toString()
            TrackRow(
                title = track.title,
                subtitle = track.artistName,
                focused = focused,
                artworkUri = track.artworkUrl,
                trailing = if (track.durationMs > 0) formatDuration(track.durationMs) else null,
                active = isPlaying,
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
