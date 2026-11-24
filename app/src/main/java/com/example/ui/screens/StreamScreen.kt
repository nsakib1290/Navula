package com.example.ui.screens

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.ProviderExtensionEntity
import com.example.data.media.DownloadLiveMetrics
import com.example.data.media.PrefetchStreamInfo
import com.example.data.media.VideoDownloadManager
import com.example.data.media.VideoMetadataHelper
import com.example.security.ContentBlockerEngine
import com.example.security.FilterCategory
import com.example.security.ShieldMetrics
import com.example.security.StreamRedirectGuard
import com.example.security.UrlSecurityValidator
import com.example.telemetry.PerformanceMonitor
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.util.FullscreenSystemBars
import com.example.ui.viewmodel.CompletedDownloadNotice
import com.example.ui.viewmodel.DetectedStream
import com.example.ui.viewmodel.DetectedSubtitleTrack
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.StreamBrowserState
import android.os.Bundle
import java.io.ByteArrayInputStream
import java.net.URI

private const val SPACEDOM_HOME_URL = "https://spacedom.live/"
private const val IDLE_TIMER_PAUSE_MS = 15_000L

private val DIRECT_MEDIA_EXTENSIONS = listOf(".mp4", ".m3u8", ".mpd", ".webm", ".mkv", ".mov")
private val DIRECT_AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus")
private val DIRECT_SUBTITLE_EXTENSIONS = listOf(".vtt", ".srt", ".ass", ".ssa")

private const val MEDIA_PLUGIN_JS = """
(function() {
    function isMediaUrl(u) {
        if (!u || typeof u !== 'string') return false;
        var lowAll = u.toLowerCase();
        var lower = lowAll.split('?')[0];
        return lower.endsWith('.mp4') || lower.endsWith('.m3u8') || lower.endsWith('.webm') ||
               lower.endsWith('.mkv') || lower.endsWith('.mov') || lower.endsWith('.mpd') ||
               lower.endsWith('.mp3') || lower.endsWith('.m4a') || lower.endsWith('.aac') ||
               lowAll.indexOf('master.m3u8') !== -1 ||
               lowAll.indexOf('playlist.m3u8') !== -1 ||
               lowAll.indexOf('index.m3u8') !== -1 ||
               lowAll.indexOf('chunklist') !== -1;
    }

    function isSubUrl(u) {
        if (!u || typeof u !== 'string') return false;
        var lower = u.toLowerCase().split('?')[0];
        return lower.endsWith('.vtt') || lower.endsWith('.srt') || lower.endsWith('.ass') || lower.endsWith('.ssa');
    }

    window.__spacedomReported = window.__spacedomReported || Object.create(null);
    window.__spacedomSubReported = window.__spacedomSubReported || Object.create(null);
    window.__spacedomPerfIdx = window.__spacedomPerfIdx || 0;
    window.__navulaVideoPlaying = window.__navulaVideoPlaying || false;

    function report(u) {
        try {
            if (!u || typeof u !== 'string') return;
            var abs = new URL(u, window.location.href).href;
            if (isSubUrl(abs)) {
                reportSub(abs, '', '');
                return;
            }
            if (!isMediaUrl(abs) || window.__spacedomReported[abs]) return;
            window.__spacedomReported[abs] = 1;
            if (window.SpacedomPlugin) {
                window.SpacedomPlugin.onStreamFound(abs, document.title || 'Navula Video');
            }
        } catch(e) {}
    }

    function reportSub(u, label, lang) {
        try {
            if (!u || typeof u !== 'string') return;
            var abs = new URL(u, window.location.href).href;
            if (!isSubUrl(abs) || window.__spacedomSubReported[abs]) return;
            window.__spacedomSubReported[abs] = 1;
            if (window.SpacedomPlugin && window.SpacedomPlugin.onSubtitleFound) {
                window.SpacedomPlugin.onSubtitleFound(abs, label || '', lang || '');
            }
        } catch(e) {}
    }

    window.__spacedomScanMedia = function(forceRescan) {
        try {
            if (document.hidden && !forceRescan) return;
            if (forceRescan) {
                window.__spacedomReported = Object.create(null);
                window.__spacedomSubReported = Object.create(null);
                window.__spacedomPerfIdx = 0;
            }
            var mediaEls = document.querySelectorAll('video, audio, source, a[download], a[href*=".mp4"], a[href*=".mp3"], a[href*=".m3u8"]');
            for (var i = 0; i < mediaEls.length; i++) {
                var el = mediaEls[i];
                if (el.currentSrc) report(el.currentSrc);
                if (el.src) report(el.src);
                if (el.href) report(el.href);
            }
            var tracks = document.querySelectorAll('track[src], track[kind="subtitles"], track[kind="captions"]');
            for (var k = 0; k < tracks.length; k++) {
                var tr = tracks[k];
                if (tr.src) reportSub(tr.src, tr.label || '', tr.srclang || '');
            }
            if (window.performance && window.performance.getEntriesByType) {
                var entries = window.performance.getEntriesByType('resource');
                var startIdx = window.__spacedomPerfIdx || 0;
                for (var j = startIdx; j < entries.length; j++) {
                    if (entries[j] && entries[j].name) {
                        report(entries[j].name);
                    }
                }
                window.__spacedomPerfIdx = entries.length;
            }
        } catch(e) {}
    };

    if (window.__spacedomHooksInstalled) {
        window.__spacedomScanMedia(false);
        return;
    }
    window.__spacedomHooksInstalled = true;

    try {
        window.open = function() { return null; };
    } catch(e) {}

    try {
        var origFetch = window.fetch;
        if (origFetch) {
            window.fetch = function() {
                try {
                    var arg = arguments[0];
                    var reqUrl = typeof arg === 'string' ? arg : (arg && arg.url ? arg.url : '');
                    report(reqUrl);
                } catch(e) {}
                return origFetch.apply(this, arguments);
            };
        }
    } catch(e) {}

    try {
        var origOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            try { report(url); } catch(e) {}
            return origOpen.apply(this, arguments);
        };
    } catch(e) {}

    try {
        var origPlay = HTMLMediaElement.prototype.play;
        HTMLMediaElement.prototype.play = function() {
            try {
                if (this.currentSrc) report(this.currentSrc);
                if (this.src) report(this.src);
            } catch(e) {}
            return origPlay.apply(this, arguments);
        };
    } catch(e) {}

    // Zero MutationObserver: rely on fetch/XHR/play/loadedmetadata hooks and on-demand __spacedomScanMedia

    try {
        function notifyPlayback(el) {
            try {
                if (!el) return;
                window.__navulaVideoPlaying = !el.paused && !el.ended;
                var candidate = el.currentSrc || el.src || '';
                if (candidate) report(candidate);
                var abs = '';
                try { abs = candidate ? new URL(candidate, window.location.href).href : window.location.href; } catch(ex) { abs = window.location.href; }
                if (window.SpacedomPlugin && window.SpacedomPlugin.onVideoPlaying) {
                    window.SpacedomPlugin.onVideoPlaying(abs, document.title || 'Navula Video');
                }
            } catch(e) {}
        }
        document.addEventListener('play', function(ev) { notifyPlayback(ev.target); }, true);
        document.addEventListener('playing', function(ev) { notifyPlayback(ev.target); }, true);
        document.addEventListener('pause', function() { window.__navulaVideoPlaying = false; }, true);
        document.addEventListener('ended', function() { window.__navulaVideoPlaying = false; }, true);
        document.addEventListener('loadedmetadata', function(ev) {
            if (ev.target && ev.target.tagName === 'VIDEO') {
                notifyPlayback(ev.target);
                window.__spacedomScanMedia(false);
            }
        }, true);
    } catch(e) {}

    window.__spacedomScanMedia(false);
})();
"""

private class SpacedomJsBridge(
    private val onStreamFoundCallback: (url: String, title: String) -> Unit,
    private val onVideoPlayingCallback: (url: String, title: String) -> Unit,
    private val onSubtitleFoundCallback: (url: String, label: String, lang: String) -> Unit
) {
    @JavascriptInterface
    fun onStreamFound(url: String, title: String) {
        onStreamFoundCallback(url, title)
    }

    @JavascriptInterface
    fun onVideoPlaying(url: String, title: String) {
        onVideoPlayingCallback(url, title)
    }

    @JavascriptInterface
    fun onSubtitleFound(url: String, label: String, lang: String) {
        onSubtitleFoundCallback(url, label, lang)
    }
}

class NavulaWebViewHolder {
    var webView: WebView? = null
    var generation: Int = 0
    private var trackedAlive: Boolean = false
    var onOpenDownloadSheet: () -> Unit = {}
    var onVideoPlaybackDetected: () -> Unit = {}
    var onCustomShowView: (View?, WebChromeClient.CustomViewCallback?) -> Unit = { _, _ -> }
    var onCustomHideView: () -> Unit = {}
    var onRendererGoneRecreate: () -> Unit = {}

    fun markCreated() {
        if (!trackedAlive) {
            trackedAlive = true
            PerformanceMonitor.onWebViewCreated()
        }
    }

    fun markDestroyed() {
        if (trackedAlive) {
            trackedAlive = false
            PerformanceMonitor.onWebViewDestroyed()
        }
    }

    fun clearCallbacks() {
        onOpenDownloadSheet = {}
        onVideoPlaybackDetected = {}
        onCustomShowView = { _, _ -> }
        onCustomHideView = {}
        onRendererGoneRecreate = {}
    }

    fun dispose() {
        val wv = webView ?: return
        webView = null
        clearCallbacks()
        markDestroyed()
        runCatching {
            wv.stopLoading()
            wv.onPause()
            wv.pauseTimers()
            PerformanceMonitor.onWebViewTimersStateChanged(true)
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
    }
}

private fun isDeviceNetworkOnline(context: Context): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching true
        val net = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun StreamScreen(
    initialUrl: String,
    viewModel: MainViewModel,
    onOpenOfflineLibrary: () -> Unit,
    onPlayStreamUrl: (url: String, title: String) -> Unit,
    onPlayOfflineVideo: (videoId: String) -> Unit = { onOpenOfflineLibrary() },
    webViewHolder: NavulaWebViewHolder? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val localFallbackHolder = remember { NavulaWebViewHolder() }
    val activeHolder = webViewHolder ?: localFallbackHolder

    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val startupResolvedUrl by viewModel.startupResolvedUrl.collectAsStateWithLifecycle()
    val startupDesktopMode by viewModel.startupDesktopMode.collectAsStateWithLifecycle()
    val pendingStreamUrl by viewModel.pendingStreamUrl.collectAsStateWithLifecycle()
    val homeRefreshTrigger by viewModel.homeRefreshTrigger.collectAsStateWithLifecycle()
    val homeResetTrigger by viewModel.homeResetTrigger.collectAsStateWithLifecycle()
    val openPluginSheetTrigger by viewModel.openPluginSheetTrigger.collectAsStateWithLifecycle()
    val cinemaFullscreenTrigger by viewModel.cinemaFullscreenTrigger.collectAsStateWithLifecycle()
    val isCurrentBookmarked by viewModel.isCurrentUrlBookmarked.collectAsStateWithLifecycle()

    var webViewRef by remember { mutableStateOf(activeHolder.webView) }
    var webViewGeneration by remember { mutableIntStateOf(activeHolder.generation) }
    var showDownloadPluginSheet by remember { mutableStateOf(false) }
    var isSourceStripExpanded by remember { mutableStateOf(false) }
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var manualCinemaFullscreen by remember { mutableStateOf(false) }
    var isVideoPlaybackDetected by remember { mutableStateOf(false) }
    var dismissedAutoBannerPageUrl by remember { mutableStateOf<String?>(null) }
    var pendingClearHistoryAfterLoad by remember { mutableStateOf(false) }
    var lastPluginRefreshMs by remember { mutableLongStateOf(0L) }
    var lastUserInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var timersIdlePaused by remember { mutableStateOf(false) }

    val isFullscreenActive = customFullscreenView != null || manualCinemaFullscreen

    fun markActiveAndWakeTimers(wv: WebView? = activeHolder.webView ?: webViewRef) {
        lastUserInteractionMs = System.currentTimeMillis()
        if (timersIdlePaused && wv != null) {
            timersIdlePaused = false
            runCatching {
                wv.resumeTimers()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                }
            }
            PerformanceMonitor.onWebViewTimersStateChanged(false)
        }
    }

    fun exitHtml5CustomFullscreen() {
        val cb = customViewCallback
        customFullscreenView = null
        customViewCallback = null
        if (cb != null) {
            runCatching { cb.onCustomViewHidden() }
        }
    }

    activeHolder.onOpenDownloadSheet = { showDownloadPluginSheet = true }
    activeHolder.onVideoPlaybackDetected = { isVideoPlaybackDetected = true }
    activeHolder.onCustomShowView = { view, callback ->
        if (view == null) {
            runCatching { callback?.onCustomViewHidden() }
        } else if (customFullscreenView != null && customFullscreenView !== view) {
            runCatching { callback?.onCustomViewHidden() }
        } else {
            customFullscreenView = view
            customViewCallback = callback
        }
    }
    activeHolder.onCustomHideView = {
        customFullscreenView = null
        customViewCallback = null
    }
    activeHolder.onRendererGoneRecreate = {
        activeHolder.generation += 1
        webViewGeneration = activeHolder.generation
    }

    val mobileChromeUa = remember(context) {
        runCatching {
            WebSettings.getDefaultUserAgent(context)
                .replace("; wv)", ")")
                .replace("; wv;", ";")
        }.getOrElse {
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }
    }

    val desktopChromeUa = remember {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    fun refreshDownloadPlugin(clearExisting: Boolean = false) {
        val wv = webViewRef ?: return
        if (clearExisting) {
            viewModel.clearDetectedStreamsForNewPage()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastPluginRefreshMs < 400L) return
            lastPluginRefreshMs = now
        }
        wv.evaluateJavascript(
            "if (window.__spacedomScanMedia) { window.__spacedomScanMedia(${if (clearExisting) "true" else "false"}); } else { $MEDIA_PLUGIN_JS }",
            null
        )
    }

    // Refresh download plugin scan without wiping existing streams or page history
    LaunchedEffect(homeRefreshTrigger) {
        if (homeRefreshTrigger > 0) {
            refreshDownloadPlugin(clearExisting = false)
        }
    }

    // Home button reset: exit fullscreen, clear temporary movie/episode history, and load current provider's Home URL
    LaunchedEffect(homeResetTrigger) {
        if (homeResetTrigger > 0) {
            exitHtml5CustomFullscreen()
            manualCinemaFullscreen = false
            showDownloadPluginSheet = false
            isSourceStripExpanded = false
            isVideoPlaybackDetected = false
            dismissedAutoBannerPageUrl = null
            viewModel.setStreamFullscreen(false)

            val wv = activeHolder.webView ?: webViewRef
            if (wv != null) {
                markActiveAndWakeTimers(wv)
                val target = viewModel.pendingStreamUrl.value ?: viewModel.streamState.value.currentUrl
                val safeTarget = UrlSecurityValidator.validatePublicHttpsUrl(target).getOrNull()
                viewModel.consumePendingStreamUrl()
                if (safeTarget != null) {
                    pendingClearHistoryAfterLoad = true
                    wv.stopLoading()
                    wv.loadUrl(safeTarget)
                }
            }
        }
    }

    // Automatically suspend WebView JS timers when open & idle (no loading, no playback, no touch for 15s)
    // to prevent ad-refresh scripts, polling timers, and animations from heating the device while idle.
    LaunchedEffect(
        lastUserInteractionMs,
        streamState.isLoading,
        isVideoPlaybackDetected,
        isFullscreenActive,
        webViewRef
    ) {
        val wv = activeHolder.webView ?: webViewRef ?: return@LaunchedEffect
        if (streamState.isLoading || isVideoPlaybackDetected || isFullscreenActive) {
            markActiveAndWakeTimers(wv)
            return@LaunchedEffect
        }
        val elapsed = System.currentTimeMillis() - lastUserInteractionMs
        val remaining = (IDLE_TIMER_PAUSE_MS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) {
            delay(remaining)
        }
        if (!streamState.isLoading && !isVideoPlaybackDetected && !isFullscreenActive) {
            runCatching {
                wv.pauseTimers()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
                }
            }
            timersIdlePaused = true
            PerformanceMonitor.onWebViewTimersStateChanged(true)
        }
    }

    // Non-blocking slow network watchdog so users get helpful feedback without interrupting slow pages
    LaunchedEffect(streamState.isLoading, streamState.currentUrl) {
        if (streamState.isLoading && !streamState.hasError) {
            delay(25_000L)
            val latest = viewModel.streamState.value
            if (latest.isLoading && latest.progress < 35 && !isVideoPlaybackDetected && !latest.hasError) {
                viewModel.emitMessage("Slow connection — still loading, or tap Retry / switch source")
            }
        }
    }

    LaunchedEffect(openPluginSheetTrigger) {
        if (openPluginSheetTrigger > 0) {
            viewModel.consumeOpenPluginTrigger()
            refreshDownloadPlugin(clearExisting = false)
            showDownloadPluginSheet = true
        }
    }

    LaunchedEffect(cinemaFullscreenTrigger) {
        if (cinemaFullscreenTrigger > 0) {
            viewModel.consumeCinemaFullscreenTrigger()
            manualCinemaFullscreen = true
        }
    }

    // Manage WebView resume/pause, timer pausing (crucial for heat/battery), OEM immersive sync, and cookie flush
    DisposableEffect(lifecycleOwner, isFullscreenActive) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val wv = activeHolder.webView ?: webViewRef
                    timersIdlePaused = false
                    lastUserInteractionMs = System.currentTimeMillis()
                    runCatching {
                        wv?.onResume()
                        wv?.resumeTimers()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            wv?.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                        }
                        PerformanceMonitor.onWebViewTimersStateChanged(false)
                    }
                    FullscreenSystemBars.applyImmersive(activity, isFullscreenActive)
                    refreshDownloadPlugin(clearExisting = false)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    val wv = activeHolder.webView ?: webViewRef
                    runCatching {
                        wv?.onPause()
                        wv?.pauseTimers()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            wv?.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
                        }
                        PerformanceMonitor.onWebViewTimersStateChanged(true)
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { CookieManager.getInstance().flush() }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Detach cleanly on tab switch: pause timers while off-screen to prevent background CPU/heat buildup
    DisposableEffect(webViewGeneration) {
        onDispose {
            exitHtml5CustomFullscreen()
            activeHolder.clearCallbacks()
            val wv = activeHolder.webView ?: webViewRef
            if (wv != null) {
                if (webViewHolder == null) {
                    runCatching {
                        val stateBundle = Bundle()
                        if (wv.saveState(stateBundle) != null) {
                            viewModel.saveWebViewState(stateBundle)
                        }
                    }
                }
                runCatching {
                    wv.onPause()
                    wv.pauseTimers()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
                    }
                    PerformanceMonitor.onWebViewTimersStateChanged(true)
                    (wv.parent as? ViewGroup)?.removeView(wv)
                }
                if (webViewHolder == null) {
                    activeHolder.dispose()
                }
            }
        }
    }

    // Sync fullscreen state with MainActivity & apply immersive bars + orientation cleanly
    LaunchedEffect(isFullscreenActive, customFullscreenView != null) {
        viewModel.setStreamFullscreen(isFullscreenActive)
        FullscreenSystemBars.applyImmersive(activity, isFullscreenActive)
        if (customFullscreenView != null) {
            FullscreenSystemBars.setLandscapeLocked(activity, true)
        } else if (!isFullscreenActive) {
            FullscreenSystemBars.setLandscapeLocked(activity, false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setStreamFullscreen(false)
            FullscreenSystemBars.applyImmersive(activity, false)
            FullscreenSystemBars.setLandscapeLocked(activity, false)
        }
    }

    // Load pending URL from Search, Bookmarks, or Provider switch
    LaunchedEffect(pendingStreamUrl, webViewRef) {
        val url = pendingStreamUrl ?: return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull()
        viewModel.consumePendingStreamUrl()
        if (safeUrl != null) {
            markActiveAndWakeTimers(wv)
            viewModel.clearDetectedStreamsForNewPage()
            viewModel.updateStreamNavigation(hasError = false)
            wv.loadUrl(safeUrl)
        }
    }

    // Trigger secure prefetching whenever Download Plugin sheet opens
    LaunchedEffect(showDownloadPluginSheet) {
        if (showDownloadPluginSheet) {
            viewModel.prefetchAllDetectedStreams()
        }
    }

    // Sync desktop vs clean mobile Chrome user-agent only after startup config settles
    LaunchedEffect(preferences.desktopModeEnabled, startupDesktopMode) {
        if (startupDesktopMode == null) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        val desiredUa = if (preferences.desktopModeEnabled) desktopChromeUa else mobileChromeUa
        viewModel.setActiveUserAgent(desiredUa)
        if (wv.settings.userAgentString != desiredUa) {
            wv.settings.userAgentString = desiredUa
            wv.settings.useWideViewPort = true
            wv.settings.loadWithOverviewMode = true
            wv.reload()
        }
    }

    val currentWebView = webViewRef
    BackHandler(
        enabled = isFullscreenActive ||
            isSourceStripExpanded ||
            streamState.canGoBack ||
            (currentWebView?.canGoBack() == true)
    ) {
        if (customFullscreenView != null) {
            exitHtml5CustomFullscreen()
        } else if (manualCinemaFullscreen) {
            manualCinemaFullscreen = false
        } else if (isSourceStripExpanded) {
            isSourceStripExpanded = false
        } else {
            val wv = webViewRef
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
                viewModel.updateStreamNavigation(
                    canGoBack = wv.canGoBack(),
                    canGoForward = wv.canGoForward()
                )
            }
        }
    }

    val themeBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .let { if (isFullscreenActive) it else it.statusBarsPadding() }
        ) {
            AnimatedVisibility(
                visible = !isFullscreenActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                StreamSourceHeaderBar(
                    providers = providers,
                    activeProvider = activeProvider,
                    isLoading = streamState.isLoading,
                    isExpanded = isSourceStripExpanded,
                    onToggleExpand = { isSourceStripExpanded = !isSourceStripExpanded },
                    onSelectProvider = { prov ->
                        isSourceStripExpanded = false
                        viewModel.activateAndOpenProvider(prov)
                    },
                    onManualSwitchNext = { viewModel.switchToNextInCategory() }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if ((startupResolvedUrl == null || startupDesktopMode == null) && activeHolder.webView == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.secondary,
                            strokeWidth = 3.dp
                        )
                    }
                } else {
                    // Full-bleed WebView with app-mode presentation
                    key(webViewGeneration) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                    val reusable = activeHolder.webView
                    if (reusable != null) {
                        (reusable.parent as? ViewGroup)?.removeView(reusable)
                        reusable.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        reusable.onResume()
                        reusable.resumeTimers()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            reusable.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                        }
                        PerformanceMonitor.onWebViewTimersStateChanged(false)
                        reusable.setBackgroundColor(themeBackgroundArgb)
                        reusable.isVerticalScrollBarEnabled = true
                        reusable.isScrollbarFadingEnabled = true
                        reusable.isHorizontalScrollBarEnabled = false
                        reusable.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        reusable.isNestedScrollingEnabled = true
                        reusable.isFocusable = true
                        reusable.isFocusableInTouchMode = true
                        reusable.setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                    markActiveAndWakeTimers(reusable)
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            false
                        }
                        reusable.settings.cacheMode = if (isDeviceNetworkOnline(ctx)) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        webViewRef = reusable
                        if (viewModel.pendingStreamUrl.value == null) {
                            viewModel.updateStreamNavigation(
                                url = reusable.url ?: streamState.currentUrl,
                                title = reusable.title?.takeIf { it.isNotBlank() && !it.equals("Just a moment...", true) }
                                    ?: streamState.pageTitle,
                                isLoading = false,
                                hasError = false,
                                canGoBack = reusable.canGoBack(),
                                canGoForward = reusable.canGoForward()
                            )
                        }
                        return@AndroidView reusable
                    }

                    val createdWebView = runCatching {
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(themeBackgroundArgb)
                            isVerticalScrollBarEnabled = true
                            isScrollbarFadingEnabled = true
                            isHorizontalScrollBarEnabled = false
                            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                            isNestedScrollingEnabled = true
                            isFocusable = true
                            isFocusableInTouchMode = true
                            setOnTouchListener { v, event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                        markActiveAndWakeTimers(this)
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                }
                                false
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                            }

                            runCatching {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                            }

                            val useDesktop = startupDesktopMode ?: preferences.desktopModeEnabled
                            val activeUa = if (useDesktop) desktopChromeUa else mobileChromeUa
                            viewModel.setActiveUserAgent(activeUa)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(true)
                        allowFileAccess = false
                        allowContentAccess = false
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        setGeolocationEnabled(false)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = if (isDeviceNetworkOnline(ctx)) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        userAgentString = activeUa
                    }

                    addJavascriptInterface(
                        SpacedomJsBridge(
                            onStreamFoundCallback = { foundUrl, foundTitle ->
                                if (UrlSecurityValidator.isSafePublicHttpsUrl(foundUrl)) {
                                    post {
                                        viewModel.addDetectedStream(foundUrl, foundTitle)
                                    }
                                }
                            },
                            onVideoPlayingCallback = { playingUrl, playingTitle ->
                                post {
                                    activeHolder.onVideoPlaybackDetected()
                                    if (UrlSecurityValidator.isSafePublicHttpsUrl(playingUrl) &&
                                        (DIRECT_MEDIA_EXTENSIONS.any { playingUrl.substringBefore('?').lowercase().endsWith(it) } ||
                                            DIRECT_AUDIO_EXTENSIONS.any { playingUrl.substringBefore('?').lowercase().endsWith(it) })
                                    ) {
                                        viewModel.addDetectedStream(playingUrl, playingTitle)
                                    }
                                }
                            },
                            onSubtitleFoundCallback = { subUrl, subLabel, subLang ->
                                if (UrlSecurityValidator.isSafePublicHttpsUrl(subUrl)) {
                                    post {
                                        viewModel.addDetectedSubtitleTrack(
                                            url = subUrl,
                                            label = subLabel.takeIf { it.isNotBlank() },
                                            language = subLang.takeIf { it.isNotBlank() }
                                        )
                                    }
                                }
                            }
                        ),
                        "SpacedomPlugin"
                    )

                    setDownloadListener { url, userAgent, _, mimeType, _ ->
                        val liveState = viewModel.streamState.value
                        val safeUrl = UrlSecurityValidator.validatePublicHttpsUrl(url).getOrNull()
                        if (safeUrl == null) {
                            viewModel.emitMessage("Blocked insecure download URL")
                            return@setDownloadListener
                        }
                        val lower = safeUrl.substringBefore('?').lowercase()
                        val isVideoOrAudio = mimeType?.startsWith("video/") == true ||
                            mimeType?.startsWith("audio/") == true ||
                            DIRECT_MEDIA_EXTENSIONS.any { lower.endsWith(it) } ||
                            DIRECT_AUDIO_EXTENSIONS.any { lower.endsWith(it) } ||
                            mimeType?.contains("mpegurl", ignoreCase = true) == true
                        if (isVideoOrAudio) {
                            viewModel.addDetectedStream(safeUrl, liveState.pageTitle)
                            viewModel.enqueueDownload(
                                url = safeUrl,
                                title = liveState.pageTitle,
                                referer = liveState.currentUrl,
                                userAgent = userAgent ?: activeUa
                            )
                        } else if (DIRECT_SUBTITLE_EXTENSIONS.any { lower.endsWith(it) }) {
                            viewModel.addDetectedSubtitleTrack(safeUrl, label = liveState.pageTitle)
                            activeHolder.onOpenDownloadSheet()
                        } else {
                            viewModel.addDetectedStream(safeUrl, liveState.pageTitle, "FILE")
                            activeHolder.onOpenDownloadSheet()
                        }
                    }

                    var lastFinishedInjectedUrl: String? = null
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString()
                            if (!reqUrl.isNullOrBlank()) {
                                val pageUrlNow = viewModel.streamState.value.currentUrl
                                val blockDecision = ContentBlockerEngine.evaluateUrl(
                                    rawUrl = reqUrl,
                                    pageUrl = pageUrlNow,
                                    isMainFrame = request?.isForMainFrame == true
                                )
                                if (blockDecision.blocked) {
                                    ContentBlockerEngine.recordBlocked(blockDecision)
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                val qIdx = reqUrl.indexOf('?')
                                val cleanPath = (if (qIdx >= 0) reqUrl.substring(0, qIdx) else reqUrl).lowercase()
                                // Fast skip static images/fonts/styles before subtitle/media URL detection
                                val isLikelyStaticAsset = cleanPath.endsWith(".jpg") ||
                                    cleanPath.endsWith(".jpeg") ||
                                    cleanPath.endsWith(".png") ||
                                    cleanPath.endsWith(".webp") ||
                                    cleanPath.endsWith(".gif") ||
                                    cleanPath.endsWith(".svg") ||
                                    cleanPath.endsWith(".woff2") ||
                                    cleanPath.endsWith(".woff") ||
                                    cleanPath.endsWith(".css") ||
                                    cleanPath.endsWith(".ico")
                                if (!isLikelyStaticAsset) {
                                    val isSubtitle = DIRECT_SUBTITLE_EXTENSIONS.any { cleanPath.endsWith(it) }
                                    if (isSubtitle && UrlSecurityValidator.isSafePublicHttpsUrl(reqUrl)) {
                                        view?.post {
                                            viewModel.addDetectedSubtitleTrack(reqUrl)
                                        }
                                    }
                                    val lowerFull = reqUrl.lowercase()
                                    val isMedia = DIRECT_MEDIA_EXTENSIONS.any { cleanPath.endsWith(it) } ||
                                        DIRECT_AUDIO_EXTENSIONS.any { cleanPath.endsWith(it) } ||
                                        lowerFull.contains("master.m3u8") ||
                                        lowerFull.contains("playlist.m3u8") ||
                                        lowerFull.contains("index.m3u8") ||
                                        lowerFull.contains("chunklist") ||
                                        lowerFull.contains("mime=video") ||
                                        lowerFull.contains("mime=audio")
                                    if (isMedia && UrlSecurityValidator.isSafePublicHttpsUrl(reqUrl)) {
                                        val headers = request?.requestHeaders
                                        val reqReferer = headers?.get("Referer") ?: headers?.get("referer")
                                        val reqOrigin = headers?.get("Origin") ?: headers?.get("origin")
                                        view?.post {
                                            viewModel.addDetectedStream(
                                                url = reqUrl,
                                                title = view.title,
                                                referer = reqReferer,
                                                origin = reqOrigin
                                            )
                                        }
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onLoadResource(view: WebView?, url: String?) {
                            super.onLoadResource(view, url)
                            val targetUrl = url ?: return
                            val qIdx = targetUrl.indexOf('?')
                            val cleanPath = (if (qIdx >= 0) targetUrl.substring(0, qIdx) else targetUrl).lowercase()
                            val lowerFull = targetUrl.lowercase()
                            val isMedia = DIRECT_MEDIA_EXTENSIONS.any { cleanPath.endsWith(it) } ||
                                DIRECT_AUDIO_EXTENSIONS.any { cleanPath.endsWith(it) } ||
                                lowerFull.contains("master.m3u8") ||
                                lowerFull.contains("playlist.m3u8") ||
                                lowerFull.contains("index.m3u8") ||
                                lowerFull.contains("chunklist") ||
                                lowerFull.contains("mime=video") ||
                                lowerFull.contains("mime=audio")
                            if (isMedia) {
                                val existing = viewModel.streamState.value.detectedStreams
                                if (existing.firstOrNull()?.url != targetUrl &&
                                    UrlSecurityValidator.validatePublicHttpsUrl(targetUrl).isSuccess
                                ) {
                                    viewModel.addDetectedStream(targetUrl, view?.title)
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url ?: return false
                            val urlStr = target.toString()
                            val host = target.host?.lowercase().orEmpty()

                            // Enforce HTTPS and block unsafe schemes / private local network targets
                            val validated = UrlSecurityValidator.validatePublicHttpsUrl(urlStr).getOrNull()
                            if (validated == null) {
                                return true
                            }

                            val currentPage = viewModel.streamState.value.currentUrl
                            val providerBase = viewModel.activeProvider.value?.baseUrl

                            if (request.isForMainFrame &&
                                StreamRedirectGuard.recordAndCheckRedirectLoop(validated)
                            ) {
                                viewModel.incrementBlockedPopups()
                                viewModel.emitMessage("Blocked redirect loop")
                                return true
                            }

                            val shieldDecision = ContentBlockerEngine.evaluateUrl(
                                rawUrl = validated,
                                pageUrl = currentPage,
                                isMainFrame = request.isForMainFrame
                            )
                            if (shieldDecision.blocked) {
                                viewModel.recordBlockedRequest(shieldDecision)
                                if (request.isForMainFrame || shieldDecision.category == FilterCategory.MALWARE) {
                                    viewModel.emitMessage(
                                        "Shield blocked ${shieldDecision.category?.label ?: "ad/malware"} redirect"
                                    )
                                }
                                return true
                            }

                            if (host.contains("cloudflare.com") || host.contains("challenges.cloudflare") ||
                                StreamRedirectGuard.isEssentialAuthOrChallengeUrl(validated)
                            ) {
                                return false
                            }

                            if (StreamRedirectGuard.shouldBlockRedirect(
                                    targetUrl = validated,
                                    currentPageUrl = currentPage,
                                    activeProviderBaseUrl = providerBase,
                                    isMainFrame = request.isForMainFrame,
                                    blockRedirectsEnabled = viewModel.preferences.value.blockRedirectsEnabled
                                )
                            ) {
                                viewModel.incrementBlockedPopups()
                                viewModel.emitMessage("Blocked cross-site redirect")
                                return true
                            }

                            val cleanPath = validated.substringBefore('?').lowercase()
                            if (DIRECT_MEDIA_EXTENSIONS.any { cleanPath.endsWith(it) }) {
                                val headers = request.requestHeaders
                                val reqReferer = headers?.get("Referer") ?: headers?.get("referer")
                                val reqOrigin = headers?.get("Origin") ?: headers?.get("origin")
                                viewModel.addDetectedStream(
                                    url = validated,
                                    title = view?.title,
                                    referer = reqReferer,
                                    origin = reqOrigin
                                )
                                activeHolder.onOpenDownloadSheet()
                                return true
                            }

                            return false
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            viewModel.updateStreamNavigation(
                                url = url,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (url != null && url != viewModel.streamState.value.currentUrl && !url.contains("cdn-cgi")) {
                                viewModel.clearDetectedStreamsForNewPage()
                                isVideoPlaybackDetected = false
                                dismissedAutoBannerPageUrl = null
                            }
                            viewModel.updateStreamNavigation(
                                url = url,
                                isLoading = true,
                                hasError = false,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                        }

                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            super.onPageCommitVisible(view, url)
                            viewModel.updateStreamNavigation(hasError = false)
                            ContentBlockerEngine.buildCosmeticInjectionScript(url)?.let { script ->
                                view?.evaluateJavascript(script, null)
                            }
                            view?.evaluateJavascript(ContentBlockerEngine.buildAppModeCleanupScript(url), null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            viewModel.resetFailoverState()
                            if (pendingClearHistoryAfterLoad) {
                                pendingClearHistoryAfterLoad = false
                                runCatching { view?.clearHistory() }
                            }
                            val rawTitle = view?.title.orEmpty()
                            val cleanTitle = if (rawTitle.equals("Just a moment...", ignoreCase = true)) {
                                "Navula"
                            } else {
                                rawTitle.ifBlank { "Navula" }
                            }
                            viewModel.updateStreamNavigation(
                                url = url,
                                title = cleanTitle,
                                isLoading = false,
                                hasError = false,
                                progress = 100,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                            if (!url.isNullOrBlank() && url != lastFinishedInjectedUrl) {
                                lastFinishedInjectedUrl = url
                                view?.evaluateJavascript(MEDIA_PLUGIN_JS, null)
                                ContentBlockerEngine.buildCosmeticInjectionScript(url)?.let { script ->
                                    view?.evaluateJavascript(script, null)
                                }
                                view?.evaluateJavascript(ContentBlockerEngine.buildAppModeCleanupScript(url), null)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                val code = error?.errorCode ?: 0
                                val desc = error?.description?.toString().orEmpty()
                                // Ignore aborted/cancelled navigations caused by provider redirects
                                if (code == WebViewClient.ERROR_UNKNOWN ||
                                    code == -3 ||
                                    desc.contains("ERR_ABORTED", ignoreCase = true)
                                ) {
                                    return
                                }
                                viewModel.handleProviderLoadFailure(
                                    failedUrl = request.url?.toString(),
                                    reason = desc.ifBlank { "Unable to reach provider" },
                                    isDeviceOnline = isDeviceNetworkOnline(ctx)
                                )
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request?.isForMainFrame == true) {
                                val status = errorResponse?.statusCode ?: 0
                                val reqStr = request.url?.toString().orEmpty()
                                // Ignore Cloudflare Turnstile/JS challenge responses (403/503) so verification loads cleanly
                                if ((status == 500 || status >= 502) && status != 503 && !reqStr.contains("cdn-cgi")) {
                                    viewModel.handleProviderLoadFailure(
                                        failedUrl = reqStr,
                                        reason = "HTTP $status from source",
                                        isDeviceOnline = isDeviceNetworkOnline(ctx)
                                    )
                                }
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.cancel()
                            viewModel.handleProviderLoadFailure(
                                failedUrl = error?.url,
                                reason = "Blocked untrusted TLS certificate",
                                isDeviceOnline = isDeviceNetworkOnline(ctx)
                            )
                        }

                        @TargetApi(Build.VERSION_CODES.O)
                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                detail?.didCrash() == true
                            } else {
                                true
                            }
                            runCatching {
                                view?.clearCache(false)
                                (view?.parent as? ViewGroup)?.removeView(view)
                                view?.removeAllViews()
                                view?.destroy()
                            }
                            activeHolder.markDestroyed()
                            activeHolder.webView = null
                            webViewRef = null
                            viewModel.clearDetectedStreamsForNewPage()
                            if (activeHolder.generation < 1) {
                                activeHolder.onRendererGoneRecreate()
                            } else {
                                viewModel.updateStreamNavigation(
                                    isLoading = false,
                                    hasError = true,
                                    errorDescription = if (crashed) {
                                        "Streaming renderer stopped unexpectedly. Tap Retry or open Offline Vault."
                                    } else {
                                        "Renderer was reclaimed by system memory. Tap Retry to reload."
                                    }
                                )
                            }
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            if (!title.isNullOrBlank() && !title.equals("Just a moment...", ignoreCase = true)) {
                                viewModel.updateStreamNavigation(title = title)
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            if (resultMsg == null || view == null) return false
                            val prefsNow = viewModel.preferences.value
                            val currentPage = viewModel.streamState.value.currentUrl
                            val providerBase = viewModel.activeProvider.value?.baseUrl

                            if (!isUserGesture) {
                                if (prefsNow.blockPopupsEnabled) {
                                    viewModel.incrementBlockedPopups()
                                }
                                return false
                            }

                            val hit = runCatching { view.hitTestResult }.getOrNull()
                            val hitUrl = hit?.extra?.takeIf {
                                (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) &&
                                    it.isNotBlank()
                            }
                            if (hitUrl != null) {
                                val safeHit = UrlSecurityValidator.validatePublicHttpsUrl(hitUrl).getOrNull()
                                if (safeHit == null) {
                                    viewModel.incrementBlockedPopups()
                                    return false
                                }
                                val decision = ContentBlockerEngine.evaluateUrl(safeHit, currentPage, isMainFrame = true)
                                if (decision.blocked) {
                                    viewModel.recordBlockedRequest(decision)
                                    viewModel.emitMessage("Blocked pop-up window")
                                    return false
                                }
                                if (!StreamRedirectGuard.isEssentialAuthOrChallengeUrl(safeHit) &&
                                    StreamRedirectGuard.shouldBlockRedirect(
                                        targetUrl = safeHit,
                                        currentPageUrl = currentPage,
                                        activeProviderBaseUrl = providerBase,
                                        isMainFrame = true,
                                        blockRedirectsEnabled = prefsNow.blockRedirectsEnabled
                                    )
                                ) {
                                    viewModel.incrementBlockedPopups()
                                    viewModel.emitMessage("Blocked cross-site pop-up")
                                    return false
                                }
                                view.loadUrl(safeHit)
                                return false
                            }

                            // Inspect JS-triggered window.open target once using a zero-JS ephemeral transport WebView,
                            // destroy immediately without keeping a second renderer alive.
                            val transportWebView = runCatching {
                                WebView(view.context).apply {
                                    settings.javaScriptEnabled = false
                                    settings.loadsImagesAutomatically = false
                                    settings.blockNetworkImage = true
                                    webViewClient = object : WebViewClient() {
                                        private var handled = false
                                        override fun shouldOverrideUrlLoading(
                                            transport: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            if (handled) return true
                                            handled = true
                                            val popupUrl = request?.url?.toString().orEmpty()
                                            val safePopup = UrlSecurityValidator.validatePublicHttpsUrl(popupUrl).getOrNull()
                                            if (safePopup == null) {
                                                viewModel.incrementBlockedPopups()
                                            } else {
                                                val decision = ContentBlockerEngine.evaluateUrl(
                                                    rawUrl = safePopup,
                                                    pageUrl = currentPage,
                                                    isMainFrame = true
                                                )
                                                val blockedRedirect = !StreamRedirectGuard.isEssentialAuthOrChallengeUrl(safePopup) &&
                                                    StreamRedirectGuard.shouldBlockRedirect(
                                                        targetUrl = safePopup,
                                                        currentPageUrl = currentPage,
                                                        activeProviderBaseUrl = providerBase,
                                                        isMainFrame = true,
                                                        blockRedirectsEnabled = prefsNow.blockRedirectsEnabled
                                                    )
                                                if (decision.blocked) {
                                                    viewModel.recordBlockedRequest(decision)
                                                    viewModel.emitMessage("Blocked pop-up window")
                                                } else if (blockedRedirect) {
                                                    viewModel.incrementBlockedPopups()
                                                    viewModel.emitMessage("Blocked pop-up redirect")
                                                } else {
                                                    view.post { view.loadUrl(safePopup) }
                                                }
                                            }
                                            transport?.post {
                                                runCatching {
                                                    transport.stopLoading()
                                                    transport.destroy()
                                                }
                                            }
                                            return true
                                        }
                                    }
                                    postDelayed({
                                        runCatching {
                                            stopLoading()
                                            destroy()
                                        }
                                    }, 1200L)
                                }
                            }.getOrNull() ?: return false

                            val transport = resultMsg.obj as? WebView.WebViewTransport ?: run {
                                runCatching { transportWebView.destroy() }
                                return false
                            }
                            transport.webView = transportWebView
                            resultMsg.sendToTarget()
                            return true
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            activeHolder.onCustomShowView(view, callback)
                        }

                        override fun onHideCustomView() {
                            activeHolder.onCustomHideView()
                        }
                    }

                    // Restore preserved WebView history stack when returning from tabs/player; load new URL on top if queued
                    val activeUaNow = if (preferences.desktopModeEnabled) desktopChromeUa else mobileChromeUa
                    val pending = viewModel.pendingStreamUrl.value?.takeIf { it.isNotBlank() }
                    val activeBase = activeProvider?.baseUrl?.takeIf { it.isNotBlank() }
                    val savedState = viewModel.savedWebViewState
                    val restored = savedState != null && restoreState(savedState) != null
                    viewModel.setActiveUserAgent(activeUaNow)

                    if (pending != null) {
                        viewModel.consumePendingStreamUrl()
                        val safePending = UrlSecurityValidator.validatePublicHttpsUrl(pending)
                            .getOrDefault(SPACEDOM_HOME_URL)
                        loadUrl(safePending)
                    } else if (restored) {
                        viewModel.updateStreamNavigation(
                            url = url ?: streamState.currentUrl,
                            isLoading = false,
                            hasError = false,
                            canGoBack = canGoBack(),
                            canGoForward = canGoForward()
                        )
                    } else {
                        val candidateUrl = startupResolvedUrl
                            ?: activeBase
                            ?: streamState.currentUrl.takeIf { it.isNotBlank() }
                            ?: initialUrl.ifBlank { SPACEDOM_HOME_URL }
                        val safeStartUrl = UrlSecurityValidator.validatePublicHttpsUrl(candidateUrl)
                            .getOrDefault(SPACEDOM_HOME_URL)
                        loadUrl(safeStartUrl)
                    }
                    activeHolder.webView = this
                    activeHolder.markCreated()
                    PerformanceMonitor.onWebViewTimersStateChanged(false)
                    webViewRef = this
                        }
                    }.getOrElse { err ->
                        viewModel.updateStreamNavigation(
                            isLoading = false,
                            hasError = true,
                            errorDescription = err.localizedMessage ?: "WebView renderer unavailable on this device."
                        )
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(themeBackgroundArgb)
                        }
                    }
                    createdWebView
                },
                update = { view ->
                    view.setBackgroundColor(themeBackgroundArgb)
                    if (view is WebView) {
                        activeHolder.webView = view
                        webViewRef = view
                    }
                }
                    )
                }
            }

        // Subtle Exit Fullscreen button only when user manually entered cinema fullscreen
        androidx.compose.animation.AnimatedVisibility(
            visible = manualCinemaFullscreen && customFullscreenView == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .displayCutoutPadding()
                .padding(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { manualCinemaFullscreen = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("exit_cinema_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FullscreenExit,
                    contentDescription = "Exit fullscreen"
                )
            }
        }

        // Coordinated bottom overlay dock: isolated state collection prevents AndroidView recomposition stutter
        androidx.compose.animation.AnimatedVisibility(
            visible = !streamState.hasError && !isFullscreenActive && !showDownloadPluginSheet,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            StreamBottomOverlayDock(
                viewModel = viewModel,
                streamState = streamState,
                showHomeFloatingControls = preferences.showHomeFloatingControls,
                isCurrentBookmarked = isCurrentBookmarked,
                isVideoPlaybackDetected = isVideoPlaybackDetected,
                dismissedAutoBannerPageUrl = dismissedAutoBannerPageUrl,
                onDismissAutoBanner = { dismissedAutoBannerPageUrl = streamState.currentUrl },
                onEnterFullscreen = { manualCinemaFullscreen = true },
                onOpenPluginSheet = {
                    refreshDownloadPlugin(clearExisting = false)
                    showDownloadPluginSheet = true
                },
                onOpenOfflineLibrary = onOpenOfflineLibrary,
                onPlayOfflineVideo = onPlayOfflineVideo,
                onPlayStreamUrl = onPlayStreamUrl
            )
        }

        // Offline / Network Error State Overlay
        if (streamState.hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_spacedom),
                            contentDescription = stringResource(R.string.cd_hero_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surfaceContainer
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, RoseAlert.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = RoseAlert,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.stream_offline_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = streamState.errorDescription
                                ?: stringResource(R.string.stream_offline_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onOpenOfflineLibrary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("offline_fallback_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.OfflineBolt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_open_offline))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.updateStreamNavigation(hasError = false)
                                val wv = activeHolder.webView ?: webViewRef
                                if (wv != null) {
                                    wv.settings.cacheMode = if (isDeviceNetworkOnline(context)) {
                                        WebSettings.LOAD_DEFAULT
                                    } else {
                                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                                    }
                                    wv.reload()
                                } else {
                                    activeHolder.generation += 1
                                    webViewGeneration = activeHolder.generation
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        }
            }
        }

        // Fullscreen HTML5 Video Container rendered over entire screen outside Column so chrome layout changes cannot interrupt playback
        customFullscreenView?.let { fsView ->
            key(fsView) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            (fsView.parent as? ViewGroup)?.removeView(fsView)
                            addView(
                                fsView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    if (showDownloadPluginSheet) {
        val sheetDownloads by viewModel.downloads.collectAsStateWithLifecycle()
        val sheetLiveMetrics by viewModel.liveDownloadMetrics.collectAsStateWithLifecycle()
        val sheetPrefetchedStreams by viewModel.prefetchedStreams.collectAsStateWithLifecycle()
        val sheetPrefetchingUrls by viewModel.prefetchingUrls.collectAsStateWithLifecycle()
        val sheetShieldMetrics by viewModel.shieldMetrics.collectAsStateWithLifecycle()

        val currentHost = remember(streamState.currentUrl) {
            runCatching { URI(streamState.currentUrl).host?.lowercase()?.trimEnd('.') }.getOrNull().orEmpty()
        }
        val isWhitelisted = currentHost.isNotBlank() && preferences.whitelistedDomains.contains(currentHost)

        StreamDownloadPluginSheet(
            detectedStreams = streamState.detectedStreams,
            detectedSubtitles = streamState.detectedSubtitles,
            downloads = sheetDownloads,
            liveMetrics = sheetLiveMetrics,
            prefetchedStreams = sheetPrefetchedStreams,
            prefetchingUrls = sheetPrefetchingUrls,
            shieldMetrics = sheetShieldMetrics,
            shieldEnabled = preferences.adGuardEnabled,
            blockRedirectsEnabled = preferences.blockRedirectsEnabled,
            currentHost = currentHost,
            isCurrentHostWhitelisted = isWhitelisted,
            pageTitle = streamState.pageTitle,
            isBookmarked = isCurrentBookmarked,
            onToggleBookmark = { viewModel.toggleCurrentPageBookmark() },
            onToggleShield = { viewModel.setAdGuardEnabled(!preferences.adGuardEnabled) },
            onToggleBlockRedirects = { viewModel.setBlockRedirectsEnabled(!preferences.blockRedirectsEnabled) },
            onToggleWhitelistHost = {
                if (currentHost.isNotBlank()) {
                    viewModel.toggleWhitelistedDomain(currentHost)
                    webViewRef?.reload()
                }
            },
            onScanAgain = {
                refreshDownloadPlugin(clearExisting = false)
                viewModel.prefetchAllDetectedStreams()
                viewModel.emitMessage("Refreshed download plugin — inspecting available formats...")
            },
            onPrefetchStream = { stream ->
                viewModel.prefetchStream(
                    url = stream.url,
                    referer = stream.referer ?: streamState.currentUrl,
                    origin = stream.origin,
                    userAgent = streamState.activeUserAgent
                )
            },
            onDownloadOption = { targetUrl, sourceUrl, title, referer, origin, variantUrl, formatLabel, qualityLabel, audioLabel, subUrl, subLang ->
                viewModel.enqueueDownload(
                    url = targetUrl,
                    title = title,
                    referer = referer ?: streamState.currentUrl,
                    origin = origin,
                    userAgent = streamState.activeUserAgent,
                    preferredVariantUrl = variantUrl,
                    formatLabel = formatLabel,
                    qualityLabel = qualityLabel,
                    audioLabel = audioLabel,
                    subtitleUrl = subUrl,
                    subtitleLanguage = subLang
                )
            },
            onPrefetchManualUrl = { url -> viewModel.prefetchUrlDirect(url) },
            onDownloadManualOption = { targetUrl, title, variantUrl, formatLabel, qualityLabel, audioLabel, subUrl, subLang ->
                viewModel.enqueueDownload(
                    url = targetUrl,
                    title = title,
                    referer = streamState.currentUrl,
                    userAgent = streamState.activeUserAgent,
                    preferredVariantUrl = variantUrl,
                    formatLabel = formatLabel,
                    qualityLabel = qualityLabel,
                    audioLabel = audioLabel,
                    subtitleUrl = subUrl,
                    subtitleLanguage = subLang
                )
            },
            onPlayNative = { url, title ->
                showDownloadPluginSheet = false
                onPlayStreamUrl(url, title)
            },
            onOpenOfflineVideo = { videoId ->
                showDownloadPluginSheet = false
                onPlayOfflineVideo(videoId)
            },
            onPauseDownload = viewModel::pauseDownload,
            onResumeDownload = viewModel::resumeDownload,
            onRetryDownload = viewModel::retryDownload,
            onCancelDownload = viewModel::cancelDownload,
            onOpenOfflineVault = {
                showDownloadPluginSheet = false
                onOpenOfflineLibrary()
            },
            onDismiss = { showDownloadPluginSheet = false }
        )
    }
}

@Composable
private fun StreamSourceHeaderBar(
    providers: List<ProviderExtensionEntity>,
    activeProvider: ProviderExtensionEntity?,
    isLoading: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectProvider: (ProviderExtensionEntity) -> Unit,
    onManualSwitchNext: () -> Unit
) {
    val activeCat = activeProvider?.category ?: "Movie"
    val initialSection = remember(activeCat) {
        when {
            activeCat.equals("Live TV & Sports", ignoreCase = true) -> "Live TV & Sports"
            activeCat.equals("Movie", ignoreCase = true) -> "Movie"
            else -> "Cloud"
        }
    }
    var selectedSection by remember { mutableStateOf(initialSection) }
    LaunchedEffect(activeProvider?.id) {
        val cat = activeProvider?.category ?: return@LaunchedEffect
        selectedSection = when {
            cat.equals("Live TV & Sports", ignoreCase = true) -> "Live TV & Sports"
            cat.equals("Movie", ignoreCase = true) -> "Movie"
            else -> "Cloud"
        }
    }

    val movieProviders = remember(providers) {
        providers.filter { it.isEnabled && it.baseUrl.isNotBlank() && it.category.equals("Movie", ignoreCase = true) }
    }
    val liveProviders = remember(providers) {
        providers.filter { it.isEnabled && it.baseUrl.isNotBlank() && it.category.equals("Live TV & Sports", ignoreCase = true) }
    }
    val cloudProviders = remember(providers) {
        providers.filter {
            it.isEnabled && it.baseUrl.isNotBlank() &&
                !it.category.equals("Movie", ignoreCase = true) &&
                !it.category.equals("Live TV & Sports", ignoreCase = true)
        }
    }

    val visibleSources = remember(selectedSection, movieProviders, liveProviders, cloudProviders) {
        when (selectedSection) {
            "Live TV & Sports" -> liveProviders
            "Cloud" -> cloudProviders.ifEmpty { movieProviders }
            else -> movieProviders
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stream_source_header_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onToggleExpand,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.heightIn(min = 34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLoading) MaterialTheme.colorScheme.secondary else EmeraldSuccess
                                )
                        )
                        Text(
                            text = activeProvider?.name ?: "Spacedom Live",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(86.dp)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Toggle source strip",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isMovieTab = selectedSection == "Movie"
                        CompactSourceSectionPill(
                            label = "Movies (${movieProviders.size})",
                            icon = Icons.Rounded.Movie,
                            selected = isMovieTab,
                            onClick = {
                                if (isMovieTab && isExpanded) {
                                    onToggleExpand()
                                } else {
                                    selectedSection = "Movie"
                                    if (!isExpanded) onToggleExpand()
                                }
                            },
                            testTag = "stream_section_movie"
                        )
                    }
                    item {
                        val isLiveTab = selectedSection == "Live TV & Sports"
                        CompactSourceSectionPill(
                            label = "Live (${liveProviders.size})",
                            icon = Icons.Rounded.LiveTv,
                            selected = isLiveTab,
                            onClick = {
                                if (isLiveTab && isExpanded) {
                                    onToggleExpand()
                                } else {
                                    selectedSection = "Live TV & Sports"
                                    if (!isExpanded) onToggleExpand()
                                }
                            },
                            testTag = "stream_section_live"
                        )
                    }
                    if (cloudProviders.isNotEmpty()) {
                        item {
                            val isCloudTab = selectedSection == "Cloud"
                            CompactSourceSectionPill(
                                label = "Plugins (${cloudProviders.size})",
                                icon = null,
                                selected = isCloudTab,
                                onClick = {
                                    if (isCloudTab && isExpanded) {
                                        onToggleExpand()
                                    } else {
                                        selectedSection = "Cloud"
                                        if (!isExpanded) onToggleExpand()
                                    }
                                },
                                testTag = "stream_section_cloud"
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onManualSwitchNext,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("stream_next_source_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "Switch to next source",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded && visibleSources.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visibleSources, key = { "quick_${it.id}" }) { provider ->
                        val isCurrent = provider.id == activeProvider?.id
                        Surface(
                            onClick = { onSelectProvider(provider) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = if (isCurrent) null else androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag(
                                "quick_source_${provider.name.lowercase().replace(" ", "_")}"
                            )
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
private fun CompactSourceSectionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StreamBottomOverlayDock(
    viewModel: MainViewModel,
    streamState: StreamBrowserState,
    showHomeFloatingControls: Boolean,
    isCurrentBookmarked: Boolean,
    isVideoPlaybackDetected: Boolean,
    dismissedAutoBannerPageUrl: String?,
    onDismissAutoBanner: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onOpenPluginSheet: () -> Unit,
    onOpenOfflineLibrary: () -> Unit,
    onPlayOfflineVideo: (String) -> Unit,
    onPlayStreamUrl: (url: String, title: String) -> Unit
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val liveMetrics by viewModel.liveDownloadMetrics.collectAsStateWithLifecycle()
    val prefetchedStreams by viewModel.prefetchedStreams.collectAsStateWithLifecycle()
    val latestCompleted by viewModel.latestCompletedDownload.collectAsStateWithLifecycle()

    val activeDownloads = remember(downloads) {
        downloads.filter {
            it.status == DownloadTaskEntity.STATUS_DOWNLOADING || it.status == DownloadTaskEntity.STATUS_QUEUED
        }
    }
    val activeDownloadCount = activeDownloads.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Newly Completed Download Banner with direct 1-tap Watch & Vault actions
        AnimatedVisibility(
            visible = latestCompleted != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            latestCompleted?.let { notice ->
                CompletedDownloadCard(
                    notice = notice,
                    onWatchNow = {
                        viewModel.dismissCompletedDownloadNotice()
                        onPlayOfflineVideo(notice.taskId)
                    },
                    onOpenVault = {
                        viewModel.dismissCompletedDownloadNotice()
                        onOpenOfflineLibrary()
                    },
                    onDismiss = { viewModel.dismissCompletedDownloadNotice() }
                )
            }
        }

        // 2. MovieBox-style Live Local Download Pill while downloading
        if (activeDownloads.isNotEmpty()) {
            val primaryTask = activeDownloads.first()
            val metrics = liveMetrics[primaryTask.id]
            val pctText = if (metrics != null && metrics.percent > 0) "${metrics.percent}%"
            else if (primaryTask.totalBytes > 0L) "${(primaryTask.progressFraction * 100).toInt()}%"
            else VideoMetadataHelper.formatFileSize(primaryTask.downloadedBytes)
            val speedText = metrics?.speedBytesPerSec?.takeIf { it > 0L }?.let {
                " • ${VideoDownloadManager.formatSpeed(it)}"
            }.orEmpty()

            Surface(
                onClick = onOpenOfflineLibrary,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                ),
                shadowElevation = 6.dp,
                modifier = Modifier.testTag("stream_live_download_pill")
            ) {
                Column(
                    modifier = Modifier
                        .width(232.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = primaryTask.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$pctText$speedText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    if (primaryTask.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { primaryTask.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // 3. Vega-style Auto Download Quick Bar when entering or playing a video
        val primaryDetectedStream = streamState.detectedStreams.firstOrNull()
        val showAutoDownloadBanner = (primaryDetectedStream != null || isVideoPlaybackDetected) &&
            dismissedAutoBannerPageUrl != streamState.currentUrl &&
            activeDownloads.isEmpty() &&
            latestCompleted == null

        AnimatedVisibility(
            visible = showAutoDownloadBanner,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stream_auto_download_banner")
        ) {
            val primaryPrefetch = primaryDetectedStream?.let { prefetchedStreams[it.url] }
            val matchingCompletedTask = primaryDetectedStream?.let { stream ->
                downloads.firstOrNull {
                    it.sourceUrl == stream.url && it.status == DownloadTaskEntity.STATUS_COMPLETED
                }
            }
            val matchingCompleted = matchingCompletedTask != null

            val statusSummary = when {
                matchingCompleted -> "Saved Offline • Tap Watch"
                primaryPrefetch != null -> buildString {
                    append(primaryDetectedStream.formatLabel)
                    primaryPrefetch.qualityLabel?.let { append(" • $it") }
                    if (primaryPrefetch.contentLengthBytes > 0L) {
                        append(" • ${VideoMetadataHelper.formatFileSize(primaryPrefetch.contentLengthBytes)}")
                    } else if (primaryPrefetch.segmentCount > 0) {
                        append(" • ${primaryPrefetch.segmentCount} segs")
                    }
                }
                primaryDetectedStream != null -> "${primaryDetectedStream.formatLabel} Ready"
                else -> "Video Playing"
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                ),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (matchingCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                            contentDescription = null,
                            tint = if (matchingCompleted) EmeraldSuccess else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = primaryDetectedStream?.title ?: streamState.pageTitle.ifBlank { "Video Stream" },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (matchingCompleted) EmeraldSuccess else MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (primaryDetectedStream != null) {
                        IconButton(
                            onClick = {
                                if (matchingCompletedTask != null) {
                                    onPlayOfflineVideo(matchingCompletedTask.id)
                                } else {
                                    val targetPlayUrl = primaryPrefetch?.variants?.firstOrNull()?.url ?: primaryDetectedStream.url
                                    onPlayStreamUrl(targetPlayUrl, primaryDetectedStream.title)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("auto_download_play_media3_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play in Media3 player",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (matchingCompletedTask != null) {
                                onPlayOfflineVideo(matchingCompletedTask.id)
                            } else if (primaryDetectedStream != null &&
                                streamState.detectedStreams.size == 1 &&
                                (primaryPrefetch?.variants?.size ?: 0) <= 1
                            ) {
                                val bestUrl = primaryPrefetch?.variants?.firstOrNull()?.url
                                viewModel.enqueueDownload(
                                    url = primaryDetectedStream.url,
                                    title = primaryDetectedStream.title,
                                    referer = primaryDetectedStream.referer ?: streamState.currentUrl,
                                    origin = primaryDetectedStream.origin,
                                    userAgent = streamState.activeUserAgent,
                                    preferredVariantUrl = bestUrl
                                )
                            } else {
                                onOpenPluginSheet()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("auto_download_primary_btn")
                    ) {
                        Text(
                            text = when {
                                matchingCompleted -> "Watch"
                                (primaryPrefetch?.variants?.size ?: 0) > 1 || streamState.detectedStreams.size > 1 -> "Download (${streamState.detectedStreams.size})"
                                else -> "Download"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    IconButton(
                        onClick = onOpenPluginSheet,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("auto_download_options_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Stream quality & options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismissAutoBanner,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("auto_download_dismiss_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 4. Optional Home FAB cluster (Download / Save / Fullscreen — Back/Forward bar removed)
        if (showHomeFloatingControls) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = onEnterFullscreen,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            modifier = Modifier.testTag("stream_floating_fullscreen")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "Enter fullscreen mode"
                            )
                        }

                        SmallFloatingActionButton(
                            onClick = { viewModel.toggleCurrentPageBookmark() },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isCurrentBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            modifier = Modifier.testTag("stream_floating_bookmark")
                        ) {
                            Icon(
                                imageVector = if (isCurrentBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(R.string.cd_bookmark)
                            )
                        }

                        ExtendedFloatingActionButton(
                            onClick = onOpenPluginSheet,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                            modifier = Modifier.testTag("stream_download_fab"),
                            icon = {
                                if (activeDownloadCount > 0) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Download video"
                                    )
                                }
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (activeDownloadCount > 0) "Live ($activeDownloadCount)" else "Download",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    if (streamState.detectedStreams.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = streamState.detectedStreams.size.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun CompletedDownloadCard(
    notice: CompletedDownloadNotice,
    onWatchNow: () -> Unit,
    onOpenVault: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            EmeraldSuccess.copy(alpha = 0.65f)
        ),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stream_completed_download_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmeraldSuccess.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download Complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSuccess,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notice.fileSizeBytes > 0L) {
                    Text(
                        text = VideoMetadataHelper.formatFileSize(notice.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onWatchNow,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("completed_download_watch_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Watch",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onOpenVault,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("completed_download_vault_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = "Open Offline Vault",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("completed_download_dismiss_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss notification",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamDownloadPluginSheet(
    detectedStreams: List<DetectedStream>,
    detectedSubtitles: List<DetectedSubtitleTrack>,
    downloads: List<DownloadTaskEntity>,
    liveMetrics: Map<String, DownloadLiveMetrics>,
    prefetchedStreams: Map<String, PrefetchStreamInfo>,
    prefetchingUrls: Set<String>,
    shieldMetrics: ShieldMetrics,
    shieldEnabled: Boolean,
    blockRedirectsEnabled: Boolean,
    currentHost: String,
    isCurrentHostWhitelisted: Boolean,
    pageTitle: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onToggleShield: () -> Unit,
    onToggleBlockRedirects: () -> Unit,
    onToggleWhitelistHost: () -> Unit,
    onScanAgain: () -> Unit,
    onPrefetchStream: (DetectedStream) -> Unit,
    onDownloadOption: (
        targetUrl: String,
        sourceUrl: String,
        title: String,
        referer: String?,
        origin: String?,
        variantUrl: String?,
        formatLabel: String?,
        qualityLabel: String?,
        audioLabel: String?,
        subtitleUrl: String?,
        subtitleLanguage: String?
    ) -> Unit,
    onPrefetchManualUrl: suspend (String) -> Result<PrefetchStreamInfo>,
    onDownloadManualOption: (
        targetUrl: String,
        title: String,
        variantUrl: String?,
        formatLabel: String?,
        qualityLabel: String?,
        audioLabel: String?,
        subtitleUrl: String?,
        subtitleLanguage: String?
    ) -> Unit,
    onPlayNative: (url: String, title: String) -> Unit,
    onOpenOfflineVideo: (videoId: String) -> Unit,
    onPauseDownload: (id: String) -> Unit,
    onResumeDownload: (task: DownloadTaskEntity) -> Unit,
    onRetryDownload: (task: DownloadTaskEntity) -> Unit,
    onCancelDownload: (id: String) -> Unit,
    onOpenOfflineVault: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var manualUrl by remember { mutableStateOf("") }
    var manualTitle by remember { mutableStateOf(pageTitle) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var manualPrefetch by remember { mutableStateOf<PrefetchStreamInfo?>(null) }
    var isManualPrefetching by remember { mutableStateOf(false) }
    var manualSelectedVariant by remember { mutableStateOf<String?>(null) }
    var manualSelectedSubtitleUrl by remember { mutableStateOf<String?>(null) }

    val selectedVariants = remember { mutableStateMapOf<String, String?>() }
    val selectedAudioIds = remember { mutableStateMapOf<String, String?>() }
    val selectedSubtitleUrls = remember { mutableStateMapOf<String, String?>() }
    val customTitles = remember { mutableStateMapOf<String, String>() }
    val expandedOptions = remember { mutableStateMapOf<String, Boolean>() }

    val queueTasks = remember(downloads) {
        downloads.filter {
            it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                it.status == DownloadTaskEntity.STATUS_QUEUED ||
                it.status == DownloadTaskEntity.STATUS_PAUSED ||
                it.status == DownloadTaskEntity.STATUS_FAILED
        }.take(6)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Navula Download Manager", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Verified formats, resolutions, audio & subtitles",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = onToggleBookmark) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(R.string.cd_bookmark),
                                tint = if (isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onScanAgain) {
                            Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan")
                        }
                    }
                }
            }

            // Live Internal uBlock Origin + AdGuard Shield + Redirect Blocker Banner
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = if (shieldEnabled) EmeraldSuccess else AmberGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (shieldEnabled) "uBlock & AdGuard Shield Active" else "Shield Paused",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${shieldMetrics.totalBlocked} blocked (${shieldMetrics.adsBlocked} ads • ${shieldMetrics.trackersBlocked} trackers • ${shieldMetrics.malwareBlocked} threats) • ${shieldMetrics.activeRuleCount} rules",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = onToggleShield) {
                                Text(if (shieldEnabled) "Pause" else "Enable", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (blockRedirectsEnabled) "Cross-site redirects disabled" else "Redirects allowed",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (blockRedirectsEnabled) EmeraldSuccess else AmberGold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onToggleBlockRedirects,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (blockRedirectsEnabled) "Allow Redirects" else "Block Redirects",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        if (currentHost.isNotBlank() && shieldEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentHost,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onToggleWhitelistHost,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (isCurrentHostWhitelisted) "Re-protect Site" else "Whitelist Ads",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrentHostWhitelisted) EmeraldSuccess else AmberGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (queueTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download Queue (${queueTasks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        TextButton(onClick = onOpenOfflineVault) {
                            Text("Manage All")
                        }
                    }
                }
                items(queueTasks, key = { "queue_${it.id}" }) { task ->
                    val metrics = liveMetrics[task.id]
                    val isPaused = task.status == DownloadTaskEntity.STATUS_PAUSED
                    val isFailed = task.status == DownloadTaskEntity.STATUS_FAILED
                    val isActive = task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                        task.status == DownloadTaskEntity.STATUS_QUEUED
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val metaSummary = listOfNotNull(
                                        task.formatLabel,
                                        task.qualityLabel,
                                        task.subtitleLanguage?.let { "Sub: $it" }
                                    ).joinToString(" • ")
                                    if (metaSummary.isNotBlank()) {
                                        Text(
                                            text = metaSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isActive) {
                                        IconButton(
                                            onClick = { onPauseDownload(task.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("sheet_pause_${task.id}")
                                        ) {
                                            Icon(Icons.Rounded.Pause, contentDescription = "Pause download", tint = AmberGold)
                                        }
                                    } else if (isPaused) {
                                        IconButton(
                                            onClick = { onResumeDownload(task) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("sheet_resume_${task.id}")
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume download", tint = EmeraldSuccess)
                                        }
                                    } else if (isFailed) {
                                        IconButton(
                                            onClick = { onRetryDownload(task) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("sheet_retry_${task.id}")
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry download", tint = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                    IconButton(
                                        onClick = { onCancelDownload(task.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = RoseAlert)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (task.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { task.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = when {
                                        isFailed -> RoseAlert
                                        isPaused -> AmberGold
                                        else -> MaterialTheme.colorScheme.secondary
                                    },
                                    trackColor = MaterialTheme.colorScheme.outline
                                )
                            } else if (isActive) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val sizeLabel = VideoMetadataHelper.formatFileSize(task.downloadedBytes)
                                val totalLabel = if (task.totalBytes > 0L) {
                                    " / ${VideoMetadataHelper.formatFileSize(task.totalBytes)}"
                                } else ""
                                val pctText = if (task.totalBytes > 0L) {
                                    " (${(task.progressFraction * 100).toInt()}%)"
                                } else ""
                                Text(
                                    text = "$sizeLabel$totalLabel$pctText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val rightStatus = when {
                                    isFailed -> task.errorMessage ?: "Failed"
                                    isPaused -> "Paused"
                                    else -> buildString {
                                        if (metrics != null && metrics.speedBytesPerSec > 0L) {
                                            append(VideoDownloadManager.formatSpeed(metrics.speedBytesPerSec))
                                        }
                                        val eta = VideoDownloadManager.formatEta(metrics?.etaSeconds)
                                        if (eta.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(eta)
                                        }
                                    }
                                }
                                if (rightStatus.isNotBlank()) {
                                    Text(
                                        text = rightStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isFailed) RoseAlert else MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected Media & Formats (${detectedStreams.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onOpenOfflineVault) {
                        Text("Open Vault")
                    }
                }
            }

            if (detectedStreams.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "No stream captured yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Press Play on a video and tap Scan, or paste any secure HTTPS .mp4 / .mp3 / .m3u8 link below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(detectedStreams, key = { it.url }) { stream ->
                    val completedTask = downloads.firstOrNull {
                        it.sourceUrl == stream.url && it.status == DownloadTaskEntity.STATUS_COMPLETED
                    }
                    val activeOrPausedTask = downloads.firstOrNull {
                        (it.sourceUrl == stream.url || it.selectedVariantUrl == (selectedVariants[stream.url] ?: "")) &&
                            (it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                                it.status == DownloadTaskEntity.STATUS_QUEUED ||
                                it.status == DownloadTaskEntity.STATUS_PAUSED)
                    }
                    val prefetch = prefetchedStreams[stream.url]
                    val isPrefetching = prefetchingUrls.contains(stream.url)
                    val selectedVariant = selectedVariants[stream.url]
                    val selectedAudioId = selectedAudioIds[stream.url]
                    val selectedSubUrl = selectedSubtitleUrls[stream.url]
                    val editableTitle = customTitles[stream.url] ?: stream.title
                    val showOpts = expandedOptions[stream.url] == true

                    val mergedSubtitles = remember(prefetch?.subtitleTracks, detectedSubtitles) {
                        val combined = mutableListOf<com.example.data.media.MediaSubtitleTrackOption>()
                        prefetch?.subtitleTracks?.forEach { combined.add(it) }
                        detectedSubtitles.forEach { domSub ->
                            if (combined.none { it.url == domSub.url }) {
                                combined.add(
                                    com.example.data.media.MediaSubtitleTrackOption(
                                        id = domSub.url,
                                        url = domSub.url,
                                        label = domSub.label,
                                        language = domSub.language,
                                        format = domSub.format,
                                        mimeType = if (domSub.format == "SRT") "application/x-subrip" else "text/vtt"
                                    )
                                )
                            }
                        }
                        combined.toList()
                    }
                    val selectedSubObj = mergedSubtitles.firstOrNull { it.url == selectedSubUrl }

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    StatusPillBadge(
                                        text = prefetch?.fileExtension?.uppercase()?.let { "${prefetch.mediaKind} • .$it" }
                                            ?: stream.formatLabel,
                                        accentColor = MaterialTheme.colorScheme.secondary
                                    )
                                    if (prefetch != null) {
                                        val sourceBadge = buildString {
                                            if (prefetch.isDrmProtected) {
                                                append("DRM Protected")
                                            } else {
                                                append(if (prefetch.tlsVerified) "TLS HTTPS" else "HTTPS")
                                                if (prefetch.finalVerifiedHost.isNotBlank()) {
                                                    append(" • ${prefetch.finalVerifiedHost}")
                                                }
                                            }
                                        }
                                        StatusPillBadge(
                                            text = sourceBadge,
                                            accentColor = if (prefetch.isDrmProtected) RoseAlert else EmeraldSuccess
                                        )
                                    } else if (isPrefetching) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.8.dp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Inspecting...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (completedTask != null) {
                                        TextButton(
                                            onClick = { onOpenOfflineVideo(completedTask.id) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = EmeraldSuccess,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Saved", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                                        }
                                    } else if (prefetch == null && !isPrefetching) {
                                        TextButton(
                                            onClick = { onPrefetchStream(stream) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VerifiedUser,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Inspect", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    IconButton(
                                        onClick = { expandedOptions[stream.url] = !showOpts },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tune,
                                            contentDescription = "Edit title",
                                            tint = if (showOpts) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = editableTitle,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Truthful metadata summary line (no fabricated sizes or codecs)
                            val truthLine = if (prefetch != null) {
                                buildList {
                                    prefetch.qualityLabel?.let { add("Quality: $it") }
                                    add(
                                        if (prefetch.contentLengthBytes > 0L) {
                                            "Size: ${VideoMetadataHelper.formatFileSize(prefetch.contentLengthBytes)}"
                                        } else if (prefetch.segmentCount > 0) {
                                            "${prefetch.segmentCount} segments"
                                        } else {
                                            "Size not reported"
                                        }
                                    )
                                    prefetch.audioLanguage?.let { add("Audio: $it") }
                                    prefetch.audioCodec?.let { add("Codec: $it") }
                                    if (prefetch.supportsRange || prefetch.isHls) add("Pause/Resume ready")
                                }.joinToString(" • ")
                            } else {
                                stream.url
                            }
                            Text(
                                text = truthLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (prefetch?.isDrmProtected == true) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(RoseAlert.copy(alpha = 0.12f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = RoseAlert, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = prefetch.drmReason ?: "DRM-protected stream cannot be saved offline.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoseAlert
                                    )
                                }
                            } else {
                                OrganizedMediaSelectionPanel(
                                    streamFormat = stream.formatLabel,
                                    prefetch = prefetch,
                                    subtitles = mergedSubtitles,
                                    selectedVariantUrl = selectedVariant,
                                    onSelectVariant = { selectedVariants[stream.url] = it },
                                    selectedAudioId = selectedAudioId,
                                    onSelectAudioId = { selectedAudioIds[stream.url] = it },
                                    selectedSubtitleUrl = selectedSubUrl,
                                    onSelectSubtitleUrl = { selectedSubtitleUrls[stream.url] = it },
                                    onDownloadAudioDirect = { audioOpt ->
                                        onDownloadOption(
                                            audioOpt.url,
                                            audioOpt.url,
                                            "${editableTitle.trim().ifBlank { stream.title }} (${audioOpt.label})",
                                            stream.referer,
                                            stream.origin,
                                            null,
                                            audioOpt.fileExtension.uppercase(),
                                            if (audioOpt.bitrateBps > 0L) "${audioOpt.bitrateBps / 1000} kbps" else null,
                                            listOfNotNull(audioOpt.language, audioOpt.codecs).joinToString(" • ").ifBlank { audioOpt.label },
                                            null,
                                            null
                                        )
                                    },
                                    onDownloadSubtitleDirect = { subOpt ->
                                        onDownloadOption(
                                            subOpt.url,
                                            subOpt.url,
                                            "${editableTitle.trim().ifBlank { stream.title }} (${subOpt.label})",
                                            stream.referer,
                                            stream.origin,
                                            null,
                                            subOpt.format,
                                            null,
                                            null,
                                            null,
                                            subOpt.language ?: subOpt.label
                                        )
                                    }
                                )
                            }

                            if (showOpts) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editableTitle,
                                    onValueChange = { customTitles[stream.url] = it },
                                    label = { Text("Save Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onPlayNative(selectedVariant ?: stream.url, editableTitle) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play")
                                }
                                val matchedVariant = prefetch?.variants?.firstOrNull { it.url == selectedVariant }
                                val matchedAudio = prefetch?.audioTracks?.firstOrNull { it.id == selectedAudioId }
                                Button(
                                    onClick = {
                                        when {
                                            completedTask != null && selectedVariant == null -> {
                                                onOpenOfflineVideo(completedTask.id)
                                            }
                                            activeOrPausedTask?.status == DownloadTaskEntity.STATUS_PAUSED -> {
                                                onResumeDownload(activeOrPausedTask)
                                            }
                                            else -> {
                                                val chosenAudioLabel = matchedAudio?.let {
                                                    listOfNotNull(it.language, it.codecs, it.label).distinct().joinToString(" • ")
                                                } ?: prefetch?.let {
                                                    listOfNotNull(it.audioLanguage, it.audioCodec).joinToString(" • ").ifBlank { null }
                                                }
                                                onDownloadOption(
                                                    stream.url,
                                                    stream.url,
                                                    editableTitle.trim().ifBlank { stream.title },
                                                    stream.referer,
                                                    stream.origin,
                                                    selectedVariant,
                                                    prefetch?.mediaKind ?: stream.formatLabel,
                                                    matchedVariant?.resolutionLabel ?: prefetch?.qualityLabel,
                                                    chosenAudioLabel,
                                                    selectedSubObj?.url,
                                                    selectedSubObj?.language ?: selectedSubObj?.label
                                                )
                                            }
                                        }
                                    },
                                    enabled = prefetch?.isDrmProtected != true,
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .testTag("download_full_video_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (completedTask != null && selectedVariant == null) {
                                            EmeraldSuccess
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                ) {
                                    Icon(
                                        imageVector = when {
                                            completedTask != null && selectedVariant == null -> Icons.Rounded.PlayArrow
                                            else -> Icons.Rounded.Download
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when {
                                            prefetch?.isDrmProtected == true -> "DRM Locked"
                                            completedTask != null && selectedVariant == null -> "Watch Saved"
                                            activeOrPausedTask?.status == DownloadTaskEntity.STATUS_PAUSED -> "Resume"
                                            matchedVariant != null -> "Download ${matchedVariant.resolutionLabel ?: ""}".trim()
                                            prefetch?.mediaKind == "AUDIO" -> "Download Audio"
                                            else -> "Download Video"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Manual Media Link",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = {
                            manualUrl = it
                            urlError = null
                            manualPrefetch = null
                            manualSelectedVariant = null
                            manualSelectedSubtitleUrl = null
                        },
                        placeholder = { Text("https://.../video.mp4, .mp3 or .m3u8") },
                        singleLine = true,
                        isError = urlError != null,
                        supportingText = urlError?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("plugin_manual_url_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Save As Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (manualPrefetch != null) {
                            val info = manualPrefetch!!
                            val badgeText = buildString {
                                if (info.isDrmProtected) {
                                    append("DRM Protected")
                                } else {
                                    append("Verified ${info.fileExtension.uppercase()}")
                                    if (info.finalVerifiedHost.isNotBlank()) append(" (${info.finalVerifiedHost})")
                                    info.qualityLabel?.let { append(" • $it") }
                                    if (info.contentLengthBytes > 0L) {
                                        append(" • ${VideoMetadataHelper.formatFileSize(info.contentLengthBytes)}")
                                    } else if (info.segmentCount > 0) {
                                        append(" • ${info.segmentCount} segs")
                                    }
                                }
                            }
                            StatusPillBadge(badgeText, if (info.isDrmProtected) RoseAlert else EmeraldSuccess)
                        } else {
                            Text(
                                text = if (isManualPrefetching) "Checking secure source..." else "Inspect formats & verify TLS before saving",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                val check = UrlSecurityValidator.validatePublicHttpsUrl(manualUrl)
                                if (check.isFailure) {
                                    urlError = check.exceptionOrNull()?.localizedMessage ?: "Enter a valid HTTPS URL"
                                } else {
                                    coroutineScope.launch {
                                        isManualPrefetching = true
                                        urlError = null
                                        val res = onPrefetchManualUrl(check.getOrThrow())
                                        isManualPrefetching = false
                                        res.onSuccess { manualPrefetch = it }
                                            .onFailure { urlError = it.localizedMessage ?: "Secure prefetch failed" }
                                    }
                                }
                            },
                            enabled = !isManualPrefetching && manualUrl.isNotBlank(),
                            modifier = Modifier.testTag("plugin_manual_prefetch_btn")
                        ) {
                            Text(if (isManualPrefetching) "Checking..." else "Inspect")
                        }
                    }

                    manualPrefetch?.takeIf { !it.isDrmProtected }?.let { info ->
                        OrganizedMediaSelectionPanel(
                            streamFormat = info.mediaKind,
                            prefetch = info,
                            subtitles = info.subtitleTracks,
                            selectedVariantUrl = manualSelectedVariant,
                            onSelectVariant = { manualSelectedVariant = it },
                            selectedAudioId = null,
                            onSelectAudioId = {},
                            selectedSubtitleUrl = manualSelectedSubtitleUrl,
                            onSelectSubtitleUrl = { manualSelectedSubtitleUrl = it },
                            onDownloadAudioDirect = { audioOpt ->
                                onDownloadManualOption(
                                    audioOpt.url,
                                    "${manualTitle.trim().ifBlank { "Navula Audio" }} (${audioOpt.label})",
                                    null,
                                    audioOpt.fileExtension.uppercase(),
                                    if (audioOpt.bitrateBps > 0L) "${audioOpt.bitrateBps / 1000} kbps" else null,
                                    listOfNotNull(audioOpt.language, audioOpt.codecs).joinToString(" • ").ifBlank { audioOpt.label },
                                    null,
                                    null
                                )
                            },
                            onDownloadSubtitleDirect = { subOpt ->
                                onDownloadManualOption(
                                    subOpt.url,
                                    "${manualTitle.trim().ifBlank { "Navula Subtitle" }} (${subOpt.label})",
                                    null,
                                    subOpt.format,
                                    null,
                                    null,
                                    null,
                                    subOpt.language ?: subOpt.label
                                )
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val check = UrlSecurityValidator.validatePublicHttpsUrl(manualUrl)
                                if (check.isFailure) {
                                    urlError = check.exceptionOrNull()?.localizedMessage ?: "Enter a valid HTTPS URL"
                                } else {
                                    onPlayNative(
                                        manualSelectedVariant ?: check.getOrThrow(),
                                        manualTitle.trim().ifBlank { "Navula Stream" }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play URL")
                        }
                        Button(
                            onClick = {
                                val check = UrlSecurityValidator.validatePublicHttpsUrl(manualUrl)
                                if (check.isFailure) {
                                    urlError = check.exceptionOrNull()?.localizedMessage ?: "Enter a valid HTTPS URL"
                                } else {
                                    val info = manualPrefetch
                                    val variantObj = info?.variants?.firstOrNull { it.url == manualSelectedVariant }
                                    val subObj = info?.subtitleTracks?.firstOrNull { it.url == manualSelectedSubtitleUrl }
                                    onDownloadManualOption(
                                        check.getOrThrow(),
                                        manualTitle.trim().ifBlank { "Navula Video" },
                                        manualSelectedVariant,
                                        info?.mediaKind,
                                        variantObj?.resolutionLabel ?: info?.qualityLabel,
                                        listOfNotNull(info?.audioLanguage, info?.audioCodec).joinToString(" • ").ifBlank { null },
                                        subObj?.url,
                                        subObj?.language ?: subObj?.label
                                    )
                                    manualUrl = ""
                                }
                            },
                            enabled = manualPrefetch?.isDrmProtected != true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("plugin_manual_download_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (manualPrefetch?.isDrmProtected == true) "DRM Locked" else "Download")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizedMediaSelectionPanel(
    streamFormat: String,
    prefetch: PrefetchStreamInfo?,
    subtitles: List<com.example.data.media.MediaSubtitleTrackOption>,
    selectedVariantUrl: String?,
    onSelectVariant: (String?) -> Unit,
    selectedAudioId: String?,
    onSelectAudioId: (String?) -> Unit,
    selectedSubtitleUrl: String?,
    onSelectSubtitleUrl: (String?) -> Unit,
    onDownloadAudioDirect: (com.example.data.media.MediaAudioTrackOption) -> Unit,
    onDownloadSubtitleDirect: (com.example.data.media.MediaSubtitleTrackOption) -> Unit
) {
    val variants = prefetch?.variants.orEmpty()
    val audioTracks = prefetch?.audioTracks.orEmpty()
    val hasDirectAudio = prefetch?.mediaKind == "AUDIO" ||
        streamFormat.equals("MP3", true) ||
        streamFormat.equals("M4A", true) ||
        streamFormat.equals("AAC", true)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (variants.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Video Formats & Resolutions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedVariantUrl == null,
                        onClick = { onSelectVariant(null) },
                        label = { Text("Auto Best", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
                items(variants, key = { it.url }) { variant ->
                    val extText = prefetch?.fileExtension?.uppercase()?.takeIf { it.isNotBlank() } ?: "HLS"
                    val resText = variant.resolutionLabel ?: variant.label
                    val estSizeText = variant.estimatedSizeBytes?.takeIf { it > 0L }?.let {
                        " • ~${VideoMetadataHelper.formatFileSize(it)}"
                    }.orEmpty()
                    val chipText = "$extText – $resText$estSizeText"
                    FilterChip(
                        selected = selectedVariantUrl == variant.url,
                        onClick = { onSelectVariant(variant.url) },
                        label = { Text(chipText, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        } else if (prefetch != null && !hasDirectAudio) {
            Text(
                text = buildString {
                    append("Video: ${prefetch.fileExtension.uppercase()}")
                    prefetch.qualityLabel?.let { append(" – $it") }
                    if (prefetch.contentLengthBytes > 0L) {
                        append(" – ${VideoMetadataHelper.formatFileSize(prefetch.contentLengthBytes)}")
                    } else {
                        append(" – Size not reported")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (audioTracks.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Audio Tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(audioTracks, key = { it.id }) { audioOpt ->
                    val audioChipLabel = buildString {
                        append(audioOpt.fileExtension.uppercase())
                        append(" • ")
                        append(audioOpt.label)
                        if (audioOpt.bitrateBps > 0L) {
                            append(" (${audioOpt.bitrateBps / 1000} kbps)")
                        }
                        if (audioOpt.contentLengthBytes > 0L) {
                            append(" • ${VideoMetadataHelper.formatFileSize(audioOpt.contentLengthBytes)}")
                        }
                    }
                    FilterChip(
                        selected = selectedAudioId == audioOpt.id,
                        onClick = {
                            onSelectAudioId(if (selectedAudioId == audioOpt.id) null else audioOpt.id)
                            if (audioOpt.isSeparateStream && UrlSecurityValidator.isSafePublicHttpsUrl(audioOpt.url)) {
                                onDownloadAudioDirect(audioOpt)
                            }
                        },
                        label = { Text(audioChipLabel, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        } else if (prefetch != null && !hasDirectAudio) {
            Text(
                text = if (!prefetch.audioLanguage.isNullOrBlank() || !prefetch.audioCodec.isNullOrBlank()) {
                    "Audio: ${listOfNotNull(prefetch.audioLanguage, prefetch.audioCodec).joinToString(" • ")}"
                } else {
                    "Audio: Embedded in stream (separate tracks not exposed)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (subtitles.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Subtitles (tap to attach with video)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedSubtitleUrl == null,
                        onClick = { onSelectSubtitleUrl(null) },
                        label = { Text("No Subtitles", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
                items(subtitles, key = { it.url }) { subOpt ->
                    val label = "${subOpt.language ?: subOpt.label} – ${subOpt.format}"
                    FilterChip(
                        selected = selectedSubtitleUrl == subOpt.url,
                        onClick = { onSelectSubtitleUrl(subOpt.url) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onDownloadSubtitleDirect(subOpt) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Download subtitle file",
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        } else if (prefetch != null && !hasDirectAudio) {
            Text(
                text = "Subtitles: Not exposed by provider",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

