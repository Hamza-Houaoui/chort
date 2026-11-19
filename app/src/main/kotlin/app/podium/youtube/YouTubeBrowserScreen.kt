package app.podium.youtube

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import app.podium.core.designsystem.shell.LocalPhysicalRotation
import app.podium.core.designsystem.shell.ShellPalette
import app.podium.core.designsystem.shell.rememberPhysicalRotation
import app.podium.core.designsystem.theme.PodiumTheme
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val YOUTUBE_HOME_URL = "https://m.youtube.com"

enum class YouTubeWheelMode(val label: String) {
    VOLUME("🔊 Volume"),
    NAVIGATION("↕ Scroll & Page Nav"),
    VIDEO_CONTROL("⏩ Video Control"),
    BRIGHTNESS("☀ Brightness"),
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object YouTubeController {
    var activeWebView by mutableStateOf<WebView?>(null)
    var hostActivity: Activity? = null
    var hasStarted by mutableStateOf(false)
    var isScreenVisible by mutableStateOf(false)
    var viewportBounds by mutableStateOf<Rect?>(null)
    var viewportRotation by mutableStateOf(0f)
    var canGoBack by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(true)
    var fullscreenView by mutableStateOf<View?>(null)
    var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    var wheelMode by mutableStateOf(YouTubeWheelMode.VOLUME)
    var brightness by mutableFloatStateOf(0.7f)
    var volumeFraction by mutableFloatStateOf(0.6f)
    var badgeText by mutableStateOf<String?>(null)
    var badgeTrigger by mutableLongStateOf(0L)
    var shareLinkDialogUrl by mutableStateOf<String?>(null)

    fun showBadge(text: String) {
        badgeText = text
        badgeTrigger = System.currentTimeMillis()
    }

    fun cycleWheelMode() {
        wheelMode = when (wheelMode) {
            YouTubeWheelMode.VOLUME -> YouTubeWheelMode.NAVIGATION
            YouTubeWheelMode.NAVIGATION -> YouTubeWheelMode.VIDEO_CONTROL
            YouTubeWheelMode.VIDEO_CONTROL -> YouTubeWheelMode.BRIGHTNESS
            YouTubeWheelMode.BRIGHTNESS -> YouTubeWheelMode.VOLUME
        }
        val text = if (wheelMode == YouTubeWheelMode.BRIGHTNESS) {
            "${wheelMode.label} ${(brightness * 100).roundToInt()}%"
        } else {
            wheelMode.label
        }
        showBadge(text)
    }

    fun openShareMenu(overrideUrl: String? = null) {
        val url = overrideUrl?.takeIf { it.isNotBlank() }
            ?: activeWebView?.url?.takeIf { it.isNotBlank() }
            ?: YOUTUBE_HOME_URL
        shareLinkDialogUrl = url
    }

    fun closeShareMenu() {
        shareLinkDialogUrl = null
    }

    fun extractYouTubeMedia(url: String?): Pair<String?, String?> {
        if (url.isNullOrBlank()) return null to null
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null to null
        val host = uri.host?.lowercase() ?: return null to null
        if (!host.contains("youtube.com") && host != "youtu.be") return null to null
        val videoId = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
            !uri.getQueryParameter("v").isNullOrBlank() -> uri.getQueryParameter("v")
            else -> {
                val segs = uri.pathSegments
                val idx = segs.indexOfFirst { it == "shorts" || it == "embed" || it == "live" }
                if (idx >= 0 && idx + 1 < segs.size) segs[idx + 1].takeIf { it.isNotBlank() } else null
            }
        }
        val playlistId = uri.getQueryParameter("list")?.takeIf { it.isNotBlank() && videoId == null }
        return videoId to playlistId
    }

    /**
     * Reads the live SPA URL and current video timestamp from the YouTube WebView.
     * If a video or playlist is open, pauses the WebView video, exits fullscreen, and invokes
     * [onMediaFound] so the app can play it audio-only in Now Playing from the same timestamp.
     * Otherwise, falls back to the normal Page Link Copy/Share menu.
     */
    fun captureForAudioHandoff(
        onMediaFound: (videoId: String?, playlistId: String?, positionMs: Long) -> Unit,
    ) {
        val wv = activeWebView
        if (wv == null) {
            openShareMenu()
            return
        }
        wv.evaluateJavascript(
            """
            (function(){
                var href = window.location.href || '';
                var v = document.querySelector('video');
                var t = (v && isFinite(v.currentTime)) ? Math.floor(v.currentTime * 1000) : 0;
                return href + '|||' + t;
            })();
            """.trimIndent(),
        ) { raw ->
            val cleaned = raw
                ?.trim()
                ?.removeSurrounding("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\/", "/")
                .orEmpty()
            val sepIdx = cleaned.lastIndexOf("|||")
            val liveUrl = (if (sepIdx >= 0) cleaned.substring(0, sepIdx) else wv.url)
                ?.takeIf { it.isNotBlank() }
                ?: wv.url
            val posMs = if (sepIdx >= 0) {
                cleaned.substring(sepIdx + 3).toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            } else {
                0L
            }
            val (videoId, playlistId) = extractYouTubeMedia(liveUrl)
            if (videoId == null && playlistId == null) {
                openShareMenu(liveUrl)
                return@evaluateJavascript
            }
            // Pause WebView video so only the audio player plays
            wv.evaluateJavascript(
                """
                (function(){
                    var v = document.querySelector('video');
                    if (v && !v.paused) { try { v.pause(); } catch(e) {} }
                    window.__podiumWasPlaying = false;
                })();
                """.trimIndent(),
                null,
            )
            exitFullscreen()
            showBadge("🎧 Audio Player...")
            onMediaFound(videoId, playlistId, posMs)
        }
    }

    private var lastScrollTimeMs = 0L

    fun handleWheelRotate(detents: Int, velocity: Float = 0f) {
        when (wheelMode) {
            YouTubeWheelMode.VOLUME -> adjustVolume(detents)
            YouTubeWheelMode.NAVIGATION -> scrollPage(detents, velocity)
            YouTubeWheelMode.VIDEO_CONTROL -> scrubVideo(detents, velocity)
            YouTubeWheelMode.BRIGHTNESS -> adjustBrightness(detents)
        }
    }

    fun handleNextPress() {
        when (wheelMode) {
            YouTubeWheelMode.NAVIGATION -> activeWebView?.goForward()
            YouTubeWheelMode.VIDEO_CONTROL -> {
                seekForward(10)
                showBadge("⏩ +10s")
            }
            YouTubeWheelMode.VOLUME, YouTubeWheelMode.BRIGHTNESS -> nextVideo()
        }
    }

    fun handlePreviousPress() {
        when (wheelMode) {
            YouTubeWheelMode.NAVIGATION -> if (activeWebView?.canGoBack() == true) activeWebView?.goBack()
            YouTubeWheelMode.VIDEO_CONTROL -> {
                seekBackward(10)
                showBadge("⏪ -10s")
            }
            YouTubeWheelMode.VOLUME, YouTubeWheelMode.BRIGHTNESS -> seekBackward(10)
        }
    }

    fun handleNextLongPress() {
        when (wheelMode) {
            YouTubeWheelMode.NAVIGATION -> scrollPage(5)
            YouTubeWheelMode.VOLUME, YouTubeWheelMode.VIDEO_CONTROL, YouTubeWheelMode.BRIGHTNESS -> seekForward(10)
        }
    }

    fun handlePreviousLongPress() {
        when (wheelMode) {
            YouTubeWheelMode.NAVIGATION -> scrollPage(-5)
            YouTubeWheelMode.VOLUME, YouTubeWheelMode.VIDEO_CONTROL, YouTubeWheelMode.BRIGHTNESS -> seekBackward(10)
        }
    }

    private var lastScrubTimeMs = 0L

    fun scrubVideo(detents: Int, velocity: Float = 0f) {
        val now = android.os.SystemClock.uptimeMillis()
        val dt = (now - lastScrubTimeMs).coerceIn(10L, 300L)
        lastScrubTimeMs = now

        val stepSeconds = when {
            dt < 30L -> 8
            dt < 60L -> 5
            dt < 110L -> 3
            else -> 2
        } + (kotlin.math.abs(velocity) * 0.5f).roundToInt().coerceAtMost(6)

        val deltaSec = detents * stepSeconds
        activeWebView?.evaluateJavascript(
            """
            (function(){
                var v = document.querySelector('video');
                if (!v || !isFinite(v.currentTime)) return '';
                var dur = isFinite(v.duration) ? v.duration : (v.currentTime + 3600);
                v.currentTime = Math.max(0, Math.min(dur, v.currentTime + ($deltaSec)));
                function fmt(s) {
                    s = Math.floor(s || 0);
                    var m = Math.floor(s / 60);
                    var sec = s % 60;
                    return m + ':' + (sec < 10 ? '0' : '') + sec;
                }
                return fmt(v.currentTime) + (isFinite(v.duration) ? ' / ' + fmt(v.duration) : '');
            })();
            """.trimIndent(),
        ) { result ->
            val clean = result?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
            if (clean != null) {
                val arrow = if (detents > 0) "⏩" else "⏪"
                showBadge("$arrow $clean")
            }
        }
    }

    fun scrollPage(detents: Int, velocity: Float = 0f) {
        val now = android.os.SystemClock.uptimeMillis()
        val dt = (now - lastScrollTimeMs).coerceIn(10L, 300L)
        lastScrollTimeMs = now

        // Slow turn (dt >= 140ms) -> ~55px; fast spin (dt ~ 15-40ms) -> up to ~480px per detent
        val speedFactor = when {
            dt < 25L -> 7.5f
            dt < 45L -> 4.8f
            dt < 75L -> 2.8f
            dt < 120L -> 1.6f
            else -> 0.9f
        } + (kotlin.math.abs(velocity) * 0.35f).coerceAtMost(4f)

        val dy = (detents * 60f * speedFactor).roundToInt()
        activeWebView?.evaluateJavascript(
            """
            (function(){
                window.__podiumScrollVel = (window.__podiumScrollVel || 0) + ($dy);
                if (!window.__podiumScrolling) {
                    window.__podiumScrolling = true;
                    function step() {
                        var v = window.__podiumScrollVel || 0;
                        if (Math.abs(v) < 1.5) {
                            window.__podiumScrollVel = 0;
                            window.__podiumScrolling = false;
                            return;
                        }
                        var move = v * 0.32;
                        window.__podiumScrollVel = v - move;
                        window.scrollBy(0, move);
                        requestAnimationFrame(step);
                    }
                    requestAnimationFrame(step);
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun adjustBrightness(detents: Int) {
        val next = (brightness + detents * 0.05f).coerceIn(0.05f, 1.0f)
        setBrightnessFraction(next)
        showBadge("☀ Brightness ${(next * 100).roundToInt()}%")
    }

    fun setBrightnessFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0.05f, 1.0f)
        brightness = clamped
        hostActivity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = clamped
            win.attributes = lp
        }
    }

    fun syncVolumeFromSystem(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        volumeFraction = (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    fun setVolumeFraction(context: Context, fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        volumeFraction = clamped
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (clamped * max).roundToInt().coerceIn(0, max)
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
    }

    var isPhoneFullscreen by mutableStateOf(false)
    var isBrowserFullscreen by mutableStateOf(false)

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreenView = null
        if (isPhoneFullscreen) {
            isPhoneFullscreen = false
            setWebViewVideoFullscreenCss(false)
        }
        if (isBrowserFullscreen) {
            isBrowserFullscreen = false
        }
    }

    fun toggleBrowserFullscreen() {
        if (isBrowserFullscreen || isPhoneFullscreen || fullscreenView != null) {
            exitFullscreen()
            showBadge("Exit Fullscreen")
        } else {
            isBrowserFullscreen = true
        }
    }

    fun togglePhoneFullscreen() {
        if (fullscreenView != null || isPhoneFullscreen) {
            exitFullscreen()
            showBadge("Exit Fullscreen")
        } else {
            isPhoneFullscreen = true
            setWebViewVideoFullscreenCss(true)
        }
    }

    private fun setWebViewVideoFullscreenCss(enabled: Boolean) {
        val js = if (enabled) {
            """
            (function(){
                var s = document.getElementById('__podium_fs_css');
                if (!s) {
                    s = document.createElement('style');
                    s.id = '__podium_fs_css';
                    (document.head || document.documentElement).appendChild(s);
                }
                s.textContent = 'body { overflow: hidden !important; background: #000 !important; } ' +
                    '#player-container-id, #player, #movie_player, .html5-video-player, video.html5-main-video, video { ' +
                    'position: fixed !important; top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important; ' +
                    'width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; ' +
                    'z-index: 2147483647 !important; background: #000 !important; object-fit: contain !important; } ' +
                    'ytm-mobile-topbar-renderer, ytm-pivot-bar-renderer, .watch-below-the-player { display: none !important; }';
                window.dispatchEvent(new Event('resize'));
            })();
            """.trimIndent()
        } else {
            """
            (function(){
                var s = document.getElementById('__podium_fs_css');
                if (s) s.remove();
                window.dispatchEvent(new Event('resize'));
            })();
            """.trimIndent()
        }
        activeWebView?.evaluateJavascript(js, null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(context: Context): WebView {
        activeWebView?.let { return it }
        return BackgroundWebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            val webView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    YouTubeController.progress = newProgress
                    YouTubeController.isLoading = newProgress < 100
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    fullscreenCallback?.onCustomViewHidden()
                    fullscreenView = view
                    fullscreenCallback = callback
                }

                override fun onHideCustomView() {
                    fullscreenView = null
                    fullscreenCallback = null
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString()?.lowercase() ?: return null
                    val host = request.url?.host?.lowercase() ?: ""
                    val isAd = AD_HOST_KEYWORDS.any { host.contains(it) } ||
                        AD_PATH_KEYWORDS.any { url.contains(it) }
                    if (isAd) {
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0)),
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    canGoBack = view?.canGoBack() == true
                    view?.evaluateJavascript(BACKGROUND_AND_ADBLOCK_JS, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    canGoBack = view?.canGoBack() == true
                    view?.evaluateJavascript(BACKGROUND_AND_ADBLOCK_JS, null)
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean,
                ) {
                    canGoBack = view?.canGoBack() == true
                    view?.evaluateJavascript(BACKGROUND_AND_ADBLOCK_JS, null)
                }
            }
            loadUrl(YOUTUBE_HOME_URL)
            activeWebView = this
        }
    }

    fun nextVideo() {
        activeWebView?.evaluateJavascript(
            "(function(){var b=document.querySelector('.ytp-next-button,a.ytp-next-button,button[aria-label*=\"Next\"],button[title*=\"Next\"]');if(b)b.click();})();",
            null,
        )
    }

    fun seekForward(seconds: Int = 10) {
        activeWebView?.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v&&isFinite(v.currentTime)){v.currentTime=Math.min(v.duration||v.currentTime+$seconds,v.currentTime+$seconds);}})();",
            null,
        )
    }

    fun seekBackward(seconds: Int = 10) {
        activeWebView?.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v&&isFinite(v.currentTime)){v.currentTime=Math.max(0,v.currentTime-$seconds);}})();",
            null,
        )
    }

    fun togglePlayPause() {
        activeWebView?.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})();",
            null,
        )
    }

    fun adjustVolume(detents: Int) {
        val ctx = activeWebView?.context ?: return
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        val dir = if (detents > 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
        audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI)
        syncVolumeFromSystem(ctx)
    }
}

private val AD_HOST_KEYWORDS = listOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.",
    "2mdn.net",
    "google-analytics.com",
    "googletagmanager.com",
    "ads.youtube.com",
    "ad.youtube.com",
    "s0.2mdn.net",
    "static.doubleclick.net",
)

private val AD_PATH_KEYWORDS = listOf(
    "/pagead/",
    "/api/stats/ads",
    "/api/stats/atr",
    "/pcs/activeview",
    "/ptracking",
    "/get_midroll_info",
    "adformat=",
    "&oad=",
    "&ctier=l",
)

/**
 * WebView subclass that keeps reporting [View.VISIBLE] even when the window
 * goes to the background or the screen turns off, enabling Brave-style background playback.
 * It also transforms touch event coordinates when the device is held sideways, compensating
 * for Compose's graphicsLayer rotation which does not transform native View MotionEvents.
 */
private class BackgroundWebView(context: Context) : WebView(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }
}

private val BACKGROUND_AND_ADBLOCK_JS = """
    (function() {
        if (window.__podiumYtPatched) return;
        window.__podiumYtPatched = true;

        // 1. Keep page visible in background
        try {
            Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
            Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
            Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
            Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
        } catch (e) {}

        window.__podiumWasPlaying = false;
        window.__podiumBgTransitionUntil = 0;
        document.addEventListener('play', function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                window.__podiumWasPlaying = true;
            }
        }, true);
        document.addEventListener('pause', function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                if (Date.now() < window.__podiumBgTransitionUntil && window.__podiumWasPlaying) {
                    setTimeout(function() {
                        if (window.__podiumWasPlaying && e.target.paused && !e.target.ended) {
                            e.target.play().catch(function(){});
                        }
                    }, 50);
                } else {
                    window.__podiumWasPlaying = false;
                }
            }
        }, true);

        var blockedEvents = { visibilitychange: 1, webkitvisibilitychange: 1, pagehide: 1, blur: 1 };
        var origDocAdd = document.addEventListener.bind(document);
        document.addEventListener = function(type, fn, opts) {
            if (blockedEvents[type]) return;
            return origDocAdd(type, fn, opts);
        };
        var origWinAdd = window.addEventListener.bind(window);
        window.addEventListener = function(type, fn, opts) {
            if (blockedEvents[type]) return;
            return origWinAdd(type, fn, opts);
        };

        // 2. Extension-style YouTube ad stripping (fetch + JSON.parse)
        function stripYouTubeAds(obj) {
            if (!obj || typeof obj !== 'object') return obj;
            if (Array.isArray(obj)) {
                return obj.filter(function(item) {
                    if (!item || typeof item !== 'object') return true;
                    return !item.adSlotRenderer &&
                           !item.promotedSparklesWebRenderer &&
                           !item.promotedVideoRenderer &&
                           !item.compactPromotedVideoRenderer &&
                           !item.actionCompanionAdRenderer &&
                           !item.bannerPromoRenderer &&
                           !item.statementBannerRenderer;
                }).map(stripYouTubeAds);
            }
            delete obj.adPlacements;
            delete obj.playerAds;
            delete obj.adSlots;
            delete obj.adBreakHeartbeatParams;
            delete obj.adBreakParams;
            if (obj.playerResponse && typeof obj.playerResponse === 'object') {
                stripYouTubeAds(obj.playerResponse);
            }
            for (var k in obj) {
                if (Object.prototype.hasOwnProperty.call(obj, k) && obj[k] && typeof obj[k] === 'object') {
                    if (k === 'contents' || k === 'items' || k === 'results' || k === 'engagementPanels' || k === 'onResponseReceivedActions') {
                        obj[k] = stripYouTubeAds(obj[k]);
                    }
                }
            }
            return obj;
        }

        if (window.ytInitialPlayerResponse) stripYouTubeAds(window.ytInitialPlayerResponse);
        if (window.ytInitialData) stripYouTubeAds(window.ytInitialData);

        var origJsonParse = JSON.parse;
        JSON.parse = function(text, reviver) {
            var res = origJsonParse.call(this, text, reviver);
            if (res && typeof res === 'object' && (res.adPlacements || res.playerAds || res.adSlots || res.playerResponse)) {
                stripYouTubeAds(res);
            }
            return res;
        };

        var origFetch = window.fetch;
        if (origFetch) {
            window.fetch = function(input, init) {
                var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                return origFetch.apply(this, arguments).then(function(response) {
                    if (url.indexOf('/youtubei/v1/player') !== -1 || url.indexOf('/youtubei/v1/next') !== -1 || url.indexOf('/youtubei/v1/browse') !== -1) {
                        var cloned = response.clone();
                        return cloned.text().then(function(bodyText) {
                            try {
                                var data = origJsonParse(bodyText);
                                stripYouTubeAds(data);
                                return new Response(JSON.stringify(data), {
                                    status: response.status,
                                    statusText: response.statusText,
                                    headers: response.headers
                                });
                            } catch (e) {
                                return response;
                            }
                        });
                    }
                    return response;
                });
            };
        }

        // 3. Hide ad containers via CSS
        function injectAdCss() {
            if (document.getElementById('__podium_adblock_css')) return;
            var style = document.createElement('style');
            style.id = '__podium_adblock_css';
            style.textContent = [
                'ytm-promoted-sparkles-web-renderer',
                'ytm-promoted-video-renderer',
                'ytm-companion-slot-renderer',
                'ytm-ad-slot-renderer',
                'ytd-ad-slot-renderer',
                'ad-slot-renderer',
                'ytd-promoted-sparkles-web-renderer',
                'ytd-display-ad-renderer',
                'ytd-banner-promo-renderer',
                'ytm-statement-banner-renderer',
                '#player-ads',
                '.ytp-ad-module',
                '.ytp-ad-overlay-container',
                '.ytp-ad-message-container',
                '.ytp-ad-player-overlay',
                '.ytp-ad-player-overlay-layout',
                'ytm-mealbar-promo-renderer',
                'tp-yt-paper-dialog.ytd-popup-container'
            ].join(',') + ' { display: none !important; height: 0 !important; opacity: 0 !important; pointer-events: none !important; }';
            (document.head || document.documentElement).appendChild(style);
        }
        injectAdCss();

        // 4. Fast video ad skipper fallback
        var wasMutedForAd = false;
        setInterval(function() {
            injectAdCss();
            var skipBtn = document.querySelector(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-overlay-close-button, .ytp-ad-skip-button-container button'
            );
            if (skipBtn) skipBtn.click();

            var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
            var video = document.querySelector('video');
            if (adShowing && video) {
                if (!video.muted) {
                    wasMutedForAd = true;
                    video.muted = true;
                }
                video.playbackRate = 16.0;
                if (isFinite(video.duration) && video.duration > 0) {
                    video.currentTime = video.duration;
                }
            } else if (wasMutedForAd && video) {
                wasMutedForAd = false;
                video.muted = false;
                video.playbackRate = 1.0;
            }
        }, 250);
    })();
""".trimIndent()

@Composable
fun YouTubeBrowserScreen(
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        YouTubeController.hasStarted = true
        YouTubeController.isScreenVisible = true
        onDispose {
            YouTubeController.isScreenVisible = false
        }
    }
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F0F0F)))
}

private fun Modifier.forwardTouchToWebView(enabled: Boolean, targetView: () -> View?): Modifier =
    if (!enabled) this
    else this.pointerInput(enabled) {
        awaitPointerEventScope {
            var downTime = 0L
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                val now = android.os.SystemClock.uptimeMillis()
                val action = when {
                    !change.previousPressed && change.pressed -> {
                        downTime = now
                        MotionEvent.ACTION_DOWN
                    }
                    change.previousPressed && !change.pressed -> MotionEvent.ACTION_UP
                    change.previousPressed && change.pressed -> MotionEvent.ACTION_MOVE
                    else -> MotionEvent.ACTION_CANCEL
                }
                val ev = MotionEvent.obtain(
                    downTime,
                    now,
                    action,
                    change.position.x,
                    change.position.y,
                    0,
                )
                targetView()?.dispatchTouchEvent(ev)
                ev.recycle()
                change.consume()
            }
        }
    }

/**
 * Persistent YouTube browser layer hosted directly inside [VirtualScreen] (below the header).
 * Because it lives inside [VirtualScreen], it automatically inherits screen clipping, rounded corners,
 * and 90°/-90° rotation, while staying mounted off-screen (`translationX = -10000f`) when the user
 * presses MENU so background audio never pauses or stutters.
 */
@Composable
fun YouTubePersistentLayer(
    visible: Boolean,
    topInset: androidx.compose.ui.unit.Dp,
) {
    if (visible && !YouTubeController.hasStarted) {
        YouTubeController.hasStarted = true
    }
    if (!YouTubeController.hasStarted) return

    val physicalRotation = LocalPhysicalRotation.current
    val sideways = kotlin.math.abs(physicalRotation) == 90f
    SideEffect {
        if (visible) {
            YouTubeController.viewportRotation = physicalRotation
        }
    }

    val context = LocalContext.current
    val canGoBack = YouTubeController.canGoBack
    val progress = YouTubeController.progress
    val isLoading = YouTubeController.isLoading
    val badgeText = YouTubeController.badgeText
    val badgeTrigger = YouTubeController.badgeTrigger
    val shareUrl = YouTubeController.shareLinkDialogUrl

    DisposableEffect(context) {
        YouTubeController.hostActivity = context.findActivity()
        onDispose {}
    }

    LaunchedEffect(badgeTrigger) {
        if (badgeTrigger > 0L) {
            delay(1200)
            YouTubeController.badgeText = null
        }
    }

    BackHandler(enabled = visible && (shareUrl != null || canGoBack)) {
        if (shareUrl != null) {
            YouTubeController.closeShareMenu()
        } else {
            YouTubeController.activeWebView?.goBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset)
            .graphicsLayer {
                translationX = if (visible && YouTubeController.fullscreenView == null && !YouTubeController.isPhoneFullscreen && !YouTubeController.isBrowserFullscreen) 0f else -10000f
            }
            .background(Color(0xFF0F0F0F)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isLoading && progress in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF282828)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress / 100f)
                            .fillMaxHeight()
                            .background(Color(0xFFFF0000)),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .forwardTouchToWebView(sideways) { YouTubeController.activeWebView },
            ) {
                if (!YouTubeController.isPhoneFullscreen && !YouTubeController.isBrowserFullscreen) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val wv = YouTubeController.getOrCreateWebView(ctx)
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            wv.onResume()
                            wv.postDelayed({
                                wv.evaluateJavascript(
                                    "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended&&window.__podiumWasPlaying){v.play().catch(function(){});}})();",
                                    null,
                                )
                            }, 120)
                            wv
                        },
                    )
                }
            }
        }

        // Mode / Brightness floating pill badge (~1.2s)
        AnimatedVisibility(
            visible = badgeText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xD9161618))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                BasicText(
                    text = badgeText.orEmpty(),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        // Long-press Center Share/Copy popup inside screen
        if (shareUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { YouTubeController.closeShareMenu() },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .clickable(enabled = false) {}
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(
                        text = "Page Link",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2C2C2E))
                            .padding(10.dp),
                    ) {
                        BasicText(
                            text = shareUrl,
                            style = TextStyle(
                                color = Color(0xFFE5E5EA),
                                fontSize = 12.sp,
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF3A3A3C))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("YouTube Link", shareUrl))
                                    YouTubeController.closeShareMenu()
                                    YouTubeController.showBadge("✓ Link Copied")
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = "Copy Link",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF3B30))
                                .clickable {
                                    YouTubeController.closeShareMenu()
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    }
                                    val chooser = Intent.createChooser(sendIntent, "Share YouTube Link").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(chooser)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = "Share",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeFullscreenOverlay(
    palette: ShellPalette? = null,
) {
    val fsView = YouTubeController.fullscreenView
    val phoneFs = YouTubeController.isPhoneFullscreen
    val browserFs = YouTubeController.isBrowserFullscreen
    if (fsView == null && !phoneFs && !browserFs) return

    val context = LocalContext.current
    val colors = PodiumTheme.colors
    val physicalRotation = rememberPhysicalRotation()
    val sideways = kotlin.math.abs(physicalRotation) == 90f

    SideEffect {
        YouTubeController.viewportRotation = physicalRotation
    }

    LaunchedEffect(Unit) {
        YouTubeController.syncVolumeFromSystem(context)
    }

    BackHandler {
        if (browserFs && fsView == null && !phoneFs && YouTubeController.activeWebView?.canGoBack() == true) {
            YouTubeController.activeWebView?.goBack()
        } else {
            YouTubeController.exitFullscreen()
        }
    }

    val barTopColor = palette?.bodyTop ?: colors.canvas
    val barBottomColor = palette?.bodyBottom ?: colors.canvas
    val barInk = colors.labelPrimary
    val barAccent = colors.labelPrimary
    val barSeparator = colors.separator

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
    ) {
        val rotModifier = if (sideways) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(rotModifier),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .forwardTouchToWebView(sideways) { fsView ?: YouTubeController.activeWebView },
            ) {
                AndroidView(
                    factory = { ctx ->
                        val target = fsView ?: YouTubeController.getOrCreateWebView(ctx)
                        (target.parent as? ViewGroup)?.removeView(target)
                        (target as? WebView)?.let { wv ->
                            wv.onResume()
                            wv.postDelayed({
                                wv.evaluateJavascript(
                                    "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended&&window.__podiumWasPlaying){v.play().catch(function(){});}})();",
                                    null,
                                )
                            }, 120)
                        }
                        target
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Top-left Exit Fullscreen button styled with the app theme
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp)
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    barTopColor.copy(alpha = 0.92f),
                                    barBottomColor.copy(alpha = 0.92f),
                                ),
                            ),
                        )
                        .border(1.dp, barSeparator.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { YouTubeController.exitFullscreen() },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "⤡",
                        style = TextStyle(
                            color = barInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            // Bottom themed control bar: Volume & Brightness
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(barTopColor, barBottomColor),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = barSeparator.copy(alpha = 0.55f),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Volume Control
                ThemedSliderPill(
                    icon = "🔊",
                    label = "${(YouTubeController.volumeFraction * 100).roundToInt()}%",
                    fraction = YouTubeController.volumeFraction,
                    inkColor = barInk,
                    accentColor = barAccent,
                    trackBgColor = barInk.copy(alpha = 0.16f),
                    borderColor = barSeparator.copy(alpha = 0.5f),
                    onFractionChange = { newFrac ->
                        YouTubeController.setVolumeFraction(context, newFrac)
                    },
                    onStepDown = {
                        YouTubeController.setVolumeFraction(context, YouTubeController.volumeFraction - 0.07f)
                    },
                    onStepUp = {
                        YouTubeController.setVolumeFraction(context, YouTubeController.volumeFraction + 0.07f)
                    },
                    modifier = Modifier.weight(1f),
                )

                // Brightness Control
                ThemedSliderPill(
                    icon = "☀",
                    label = "${(YouTubeController.brightness * 100).roundToInt()}%",
                    fraction = YouTubeController.brightness,
                    inkColor = barInk,
                    accentColor = barAccent,
                    trackBgColor = barInk.copy(alpha = 0.16f),
                    borderColor = barSeparator.copy(alpha = 0.5f),
                    onFractionChange = { newFrac ->
                        YouTubeController.setBrightnessFraction(newFrac)
                    },
                    onStepDown = {
                        YouTubeController.setBrightnessFraction(YouTubeController.brightness - 0.07f)
                    },
                    onStepUp = {
                        YouTubeController.setBrightnessFraction(YouTubeController.brightness + 0.07f)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemedSliderPill(
    icon: String,
    label: String,
    fraction: Float,
    inkColor: Color,
    accentColor: Color,
    trackBgColor: Color,
    borderColor: Color,
    onFractionChange: (Float) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderWidthPx by remember { mutableFloatStateOf(1f) }
    val clamped = fraction.coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(inkColor.copy(alpha = 0.06f))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onStepDown() },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = icon,
                style = TextStyle(
                    color = inkColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .onSizeChanged { sliderWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onFractionChange((offset.x / sliderWidthPx).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onFractionChange((change.position.x / sliderWidthPx).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(trackBgColor),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clamped)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accentColor),
                )
            }
        }

        Box(
            modifier = Modifier
                .width(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onStepUp() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = inkColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
        }
    }
}
