package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.DownloadTaskEntity
import com.example.data.media.VideoDownloadManager
import com.example.data.media.VideoMetadataHelper
import com.example.security.ContentBlockerEngine
import com.example.security.UrlSecurityValidator
import com.example.telemetry.PerformanceMonitor
import java.io.File
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.CosmicCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.RoseAlert
import com.example.ui.util.FullscreenSystemBars
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val PLAYER_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    initialVideoId: String?,
    streamUrl: String?,
    initialTitle: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val allVideos by viewModel.allVideos.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val liveDownloadMetrics by viewModel.liveDownloadMetrics.collectAsStateWithLifecycle()
    val prefetchedStreams by viewModel.prefetchedStreams.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    var activeVideoId by remember(initialVideoId) { mutableStateOf(initialVideoId) }
    var currentTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var activeUri by remember(streamUrl) { mutableStateOf(streamUrl) }
    var isOfflineItem by remember(initialVideoId) { mutableStateOf(initialVideoId != null) }

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderPos by remember { mutableFloatStateOf(0f) }

    var playbackSpeed by rememberSaveable { mutableFloatStateOf(preferences.defaultPlaybackSpeed) }
    var isLongPressBoost by remember { mutableStateOf(false) }
    var resizeModeIndex by rememberSaveable { mutableIntStateOf(0) } // 0=FIT, 1=FILL, 2=ZOOM
    var explicitLandscapeLock by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val isCurrentlyLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIsBrightness by remember { mutableStateOf(true) }

    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var showTrackSelectorSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var autoDownloadBannerVisible by remember(streamUrl) { mutableStateOf(initialVideoId == null && !streamUrl.isNullOrBlank()) }
    var autoDownloadBannerDismissed by remember(streamUrl) { mutableStateOf(false) }

    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "FIT",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "FILL",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "ZOOM"
        )
    }

    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val exoPlayer = remember(context) {
        val ua = streamState.activeUserAgent?.takeIf { it.isNotBlank() }
            ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        val headers = buildMap {
            val referer = streamState.currentUrl.takeIf { it.startsWith("http") }
            if (referer != null) {
                put("Referer", referer)
            }
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 45_000, 2_000, 4_000)
            .build()
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Enter immersive system bars mode, track active player telemetry & release cleanly on exit
    DisposableEffect(Unit) {
        PerformanceMonitor.onPlayerCreated()
        val window = activity?.window
        FullscreenSystemBars.applyImmersive(activity, true)
        onDispose {
            activeVideoId?.let { vid ->
                viewModel.savePlaybackProgress(vid, exoPlayer.currentPosition, exoPlayer.duration)
            }
            playerViewRef?.let { pv ->
                pv.keepScreenOn = false
                pv.player = null
            }
            playerViewRef = null
            exoPlayer.release()
            PerformanceMonitor.onPlayerReleased()
            FullscreenSystemBars.applyImmersive(activity, false)
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = -1f
                window.attributes = lp
            }
            FullscreenSystemBars.setLandscapeLocked(activity, false)
        }
    }

    // Re-enforce immersive mode after bottom sheets close on OEMs (Samsung, Xiaomi, Oppo, Vivo)
    LaunchedEffect(showTrackSelectorSheet, showQueueSheet) {
        if (!showTrackSelectorSheet && !showQueueSheet) {
            FullscreenSystemBars.applyImmersive(activity, true)
        }
    }

    // Apply orientation only when user explicitly taps the rotate toggle; otherwise follow sensor seamlessly
    LaunchedEffect(explicitLandscapeLock) {
        val locked = explicitLandscapeLock ?: return@LaunchedEffect
        FullscreenSystemBars.requestOrientation(
            activity,
            if (locked) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        )
    }

    // Pause on lifecycle pause, re-apply immersive bars on resume & save position
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    playerViewRef?.onResume()
                    FullscreenSystemBars.applyImmersive(activity, true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                    playerViewRef?.onPause()
                    activeVideoId?.let { vid ->
                        viewModel.savePlaybackProgress(vid, exoPlayer.currentPosition, exoPlayer.duration)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load target offline video or direct URL
    LaunchedEffect(activeVideoId, streamUrl) {
        playerError = null
        isBuffering = true
        if (activeVideoId != null) {
            val entity = viewModel.getVideoById(activeVideoId!!)
            if (entity != null) {
                currentTitle = entity.title
                activeUri = entity.uriString
                isOfflineItem = true
                val subConfig = entity.subtitlePath?.let { path ->
                    val f = File(path)
                    if (f.exists() && f.length() > 0L) {
                        val subMime = if (f.name.endsWith(".srt", true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
                        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(f))
                            .setMimeType(subMime)
                            .setLanguage(entity.subtitleLanguage ?: "en")
                            .setLabel(entity.subtitleLanguage ?: "Offline Subtitles")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    } else null
                }
                val itemBuilder = MediaItem.Builder().setUri(Uri.parse(entity.uriString))
                if (entity.uriString.lowercase().contains(".m3u8")) {
                    itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                if (subConfig != null) {
                    itemBuilder.setSubtitleConfigurations(listOf(subConfig))
                }
                val mediaItem = itemBuilder.build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (preferences.autoResumeEnabled && entity.lastPositionMs > 0L) {
                    exoPlayer.seekTo(entity.lastPositionMs)
                }
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                exoPlayer.playWhenReady = true
            } else {
                playerError = "Offline video file could not be found."
                isBuffering = false
            }
        } else if (!streamUrl.isNullOrBlank()) {
            val safeStream = UrlSecurityValidator.validatePublicHttpsUrl(streamUrl).getOrNull()
            val shieldDecision = safeStream?.let { ContentBlockerEngine.evaluateProviderOrMediaUrl(it) }
            if (safeStream != null && shieldDecision?.blocked != true) {
                isOfflineItem = false
                activeUri = safeStream
                viewModel.prefetchStream(url = safeStream)
                val item = if (safeStream.lowercase().contains(".m3u8")) {
                    MediaItem.Builder()
                        .setUri(Uri.parse(safeStream))
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                } else {
                    MediaItem.fromUri(Uri.parse(safeStream))
                }
                exoPlayer.setMediaItem(item)
                exoPlayer.prepare()
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                exoPlayer.playWhenReady = true
            } else {
                if (shieldDecision?.blocked == true) {
                    ContentBlockerEngine.recordBlocked(shieldDecision)
                }
                playerError = if (shieldDecision?.blocked == true) {
                    "Blocked unsafe stream URL (${shieldDecision.category?.label ?: "Shield"})."
                } else {
                    "Blocked insecure or invalid stream URL."
                }
                isBuffering = false
            }
        }
    }

    // Player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    activeVideoId?.let { vid ->
                        viewModel.savePlaybackProgress(vid, exoPlayer.duration, exoPlayer.duration)
                    }
                    showControls = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.localizedMessage ?: "Playback error (${error.errorCodeName})"
                showControls = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Poll playback position ONLY while actively playing to avoid idle CPU/battery wakeups
    LaunchedEffect(exoPlayer, isPlaying) {
        if (!isSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
        }
        if (!isPlaying) return@LaunchedEffect
        var tick = 0
        while (isPlaying) {
            if (!isSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) durationMs = dur
            }
            if (tick % 10 == 0) {
                activeVideoId?.let { vid ->
                    viewModel.savePlaybackProgress(vid, exoPlayer.currentPosition, exoPlayer.duration)
                }
            }
            tick++
            delay(500L)
        }
    }

    // Auto-hide controls after 3.5s while playing (keep visible while sheets are open)
    LaunchedEffect(showControls, isPlaying, isLocked, showTrackSelectorSheet, showQueueSheet) {
        if (showControls && isPlaying && !isLocked && !showTrackSelectorSheet && !showQueueSheet) {
            delay(3500L)
            showControls = false
        }
    }

    // Keep automatic download discovery pill visible for 6.5s when entering/playing a stream
    LaunchedEffect(autoDownloadBannerVisible, activeUri) {
        if (autoDownloadBannerVisible) {
            delay(6500L)
            autoDownloadBannerVisible = false
        }
    }

    // Auto-clear gesture HUD
    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(750L)
            doubleTapFeedback = null
        }
    }

    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(1100L)
            gestureHudText = null
        }
    }

    val currentQueueIndex = remember(activeVideoId, allVideos) {
        if (activeVideoId == null) -1 else allVideos.indexOfFirst { it.id == activeVideoId }
    }
    val hasPrev = currentQueueIndex > 0
    val hasNext = currentQueueIndex in 0 until (allVideos.size - 1)

    BackHandler {
        if (isLocked) {
            isLocked = false
            showControls = true
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
    ) {
        // Media3 Video Surface
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    keepScreenOn = true
                    player = exoPlayer
                    resizeMode = resizeModes[resizeModeIndex].first
                    playerViewRef = this
                }
            },
            update = { pv ->
                playerViewRef = pv
                if (pv.player !== exoPlayer) {
                    pv.player = exoPlayer
                }
                pv.resizeMode = resizeModes[resizeModeIndex].first
            }
        )

        // Gesture Detection Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, containerSize, playbackSpeed) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onLongPress = {
                            if (isLocked || !exoPlayer.isPlaying) return@detectTapGestures
                            isLongPressBoost = true
                            exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                        },
                        onPress = {
                            try {
                                awaitRelease()
                            } finally {
                                if (isLongPressBoost) {
                                    isLongPressBoost = false
                                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                }
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isLocked || containerSize.width == 0) return@detectTapGestures
                            val isLeft = offset.x < containerSize.width * 0.45f
                            val isRight = offset.x > containerSize.width * 0.55f
                            if (isLeft) {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                doubleTapFeedback = "-10s"
                            } else if (isRight) {
                                val maxDur = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                doubleTapFeedback = "+10s"
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                    )
                }
                .pointerInput(isLocked, containerSize) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    var startX = 0f
                    var brightnessAcc = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                    var volumeAcc = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            startX = startOffset.x
                            brightnessAcc = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                            volumeAcc = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (isLocked || containerSize.height <= 0) return@detectVerticalDragGestures
                            val deltaFraction = -dragAmount / (containerSize.height * 0.65f)
                            if (startX < containerSize.width / 2f) {
                                brightnessAcc = (brightnessAcc + deltaFraction).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = brightnessAcc
                                    win.attributes = lp
                                }
                                gestureHudIsBrightness = true
                                gestureHudText = "${(brightnessAcc * 100).roundToInt()}%"
                            } else {
                                volumeAcc = (volumeAcc + deltaFraction * maxVol).coerceIn(0f, maxVol.toFloat())
                                val targetVol = volumeAcc.roundToInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                gestureHudIsBrightness = false
                                gestureHudText = "${((targetVol.toFloat() / maxVol) * 100).roundToInt()}%"
                            }
                        }
                    )
                }
        )

        // Buffering Spinner
        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CosmicCyan
            )
        }

        // Double Tap Seek Feedback Pill
        AnimatedVisibility(
            visible = doubleTapFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = ObsidianBg.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.border(1.dp, CosmicCyan.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Text(
                    text = doubleTapFeedback.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CosmicCyan,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        // 2x Long-Press Speed Boost HUD
        AnimatedVisibility(
            visible = isLongPressBoost,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp)
        ) {
            Surface(
                color = ObsidianCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.border(1.dp, CosmicCyan.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = CosmicCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("2.0x Boost", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }

        // Brightness / Volume Swipe HUD
        AnimatedVisibility(
            visible = gestureHudText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        ) {
            Surface(
                color = ObsidianCard.copy(alpha = 0.9f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.border(1.dp, ElectricViolet.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (gestureHudIsBrightness) Icons.Rounded.BrightnessHigh
                        else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = CosmicCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = gestureHudText.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }

        // Error State
        playerError?.let { msg ->
            Surface(
                color = ObsidianCard.copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .border(1.dp, RoseAlert.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Playback Error", style = MaterialTheme.typography.titleLarge, color = RoseAlert)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onBack) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                playerError = null
                                isBuffering = true
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Cinema Controls Overlay HUD
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(ObsidianBg.copy(alpha = 0.75f))
                            .border(1.dp, CosmicCyan.copy(alpha = 0.6f), CircleShape)
                            .testTag("player_unlock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.cd_lock_controls),
                            tint = CosmicCyan
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBg.copy(alpha = 0.78f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    ObsidianBg.copy(alpha = 0.88f)
                                )
                            )
                        )
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            StatusPillBadge(
                                text = if (isOfflineItem) "OFFLINE VAULT" else "DIRECT STREAM",
                                accentColor = if (isOfflineItem) CosmicCyan else ElectricViolet
                            )
                        }

                        if (!isOfflineItem && !activeUri.isNullOrBlank()) {
                            val matchingTask = downloads.firstOrNull { it.sourceUrl == activeUri }
                            val activeMetrics = matchingTask?.let { liveDownloadMetrics[it.id] }
                            val prefetchInfo = prefetchedStreams[activeUri]
                            val isSaved = matchingTask?.status == DownloadTaskEntity.STATUS_COMPLETED
                            val isPaused = matchingTask?.status == DownloadTaskEntity.STATUS_PAUSED
                            val isDrmLocked = prefetchInfo?.isDrmProtected == true
                            val isDownloading = matchingTask?.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                                matchingTask?.status == DownloadTaskEntity.STATUS_QUEUED
                            val pctText = when {
                                isSaved -> "Saved"
                                isDrmLocked -> "DRM"
                                isPaused -> "Resume"
                                isDownloading -> "${activeMetrics?.percent ?: ((matchingTask?.progressFraction ?: 0f) * 100).toInt()}%"
                                prefetchInfo != null && prefetchInfo.contentLengthBytes > 0L ->
                                    VideoMetadataHelper.formatFileSize(prefetchInfo.contentLengthBytes)
                                else -> "Save"
                            }
                            Surface(
                                onClick = {
                                    if (isDrmLocked) {
                                        viewModel.enqueueDownload(url = activeUri!!, title = currentTitle)
                                    } else if (isPaused && matchingTask != null) {
                                        viewModel.resumeDownload(matchingTask)
                                    } else if (!isSaved && !isDownloading) {
                                        val bestVariant = prefetchInfo?.variants?.firstOrNull()
                                        val bestSub = prefetchInfo?.subtitleTracks?.firstOrNull()
                                        viewModel.enqueueDownload(
                                            url = activeUri!!,
                                            title = currentTitle,
                                            preferredVariantUrl = bestVariant?.url,
                                            formatLabel = prefetchInfo?.mediaKind,
                                            qualityLabel = bestVariant?.resolutionLabel ?: prefetchInfo?.qualityLabel,
                                            audioLabel = listOfNotNull(prefetchInfo?.audioLanguage, prefetchInfo?.audioCodec).joinToString(" • ").ifBlank { null },
                                            subtitleUrl = bestSub?.url,
                                            subtitleLanguage = bestSub?.language ?: bestSub?.label
                                        )
                                    }
                                },
                                color = when {
                                    isSaved -> EmeraldSuccess.copy(alpha = 0.2f)
                                    isDrmLocked -> RoseAlert.copy(alpha = 0.78f)
                                    else -> ElectricViolet.copy(alpha = 0.88f)
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("player_download_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = CosmicCyan
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                            contentDescription = "Download full video locally",
                                            tint = if (isSaved) EmeraldSuccess else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = pctText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSaved) EmeraldSuccess else Color.White
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size },
                            modifier = Modifier.testTag("player_aspect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = stringResource(R.string.cd_aspect_ratio),
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { explicitLandscapeLock = !isCurrentlyLandscape },
                            modifier = Modifier.testTag("player_rotate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ScreenRotation,
                                contentDescription = stringResource(R.string.cd_fullscreen),
                                tint = if (isCurrentlyLandscape) CosmicCyan else Color.White
                            )
                        }

                        IconButton(
                            onClick = { isLocked = true },
                            modifier = Modifier.testTag("player_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockOpen,
                                contentDescription = stringResource(R.string.cd_lock_controls),
                                tint = Color.White
                            )
                        }
                    }

                    // Center Transport Row
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(ObsidianBg.copy(alpha = 0.55f))
                                .testTag("player_rewind_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = stringResource(R.string.cd_rewind_10),
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.play()
                                } else if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet)
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val maxDur = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(ObsidianBg.copy(alpha = 0.55f))
                                .testTag("player_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = stringResource(R.string.cd_forward_10),
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Bottom Seekbar & Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        val effectiveDuration = durationMs.coerceAtLeast(1L)
                        val sliderValue = if (isSeeking) {
                            seekSliderPos
                        } else {
                            (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = VideoMetadataHelper.formatDuration(
                                    if (isSeeking) (seekSliderPos * effectiveDuration).toLong() else currentPositionMs
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = CosmicCyan
                            )
                            Slider(
                                value = sliderValue,
                                onValueChange = { fraction ->
                                    isSeeking = true
                                    seekSliderPos = fraction
                                },
                                onValueChangeFinished = {
                                    val seekPos = (seekSliderPos * effectiveDuration).toLong()
                                    exoPlayer.seekTo(seekPos)
                                    currentPositionMs = seekPos
                                    isSeeking = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = CosmicCyan,
                                    activeTrackColor = ElectricViolet,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                )
                            )
                            Text(
                                text = VideoMetadataHelper.formatDuration(durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (hasPrev) {
                                            activeVideoId?.let { vid ->
                                                viewModel.savePlaybackProgress(vid, exoPlayer.currentPosition, exoPlayer.duration)
                                            }
                                            activeVideoId = allVideos[currentQueueIndex - 1].id
                                        }
                                    },
                                    enabled = hasPrev
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous video",
                                        tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.35f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (hasNext) {
                                            activeVideoId?.let { vid ->
                                                viewModel.savePlaybackProgress(vid, exoPlayer.currentPosition, exoPlayer.duration)
                                            }
                                            activeVideoId = allVideos[currentQueueIndex + 1].id
                                        }
                                    },
                                    enabled = hasNext
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "Next video",
                                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isOfflineItem && allVideos.size > 1) {
                                    Surface(
                                        onClick = { showQueueSheet = true },
                                        color = ObsidianCard.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("player_queue_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                contentDescription = "Queue",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Queue",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    onClick = { showTrackSelectorSheet = true },
                                    color = ObsidianCard.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("player_tracks_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tune,
                                            contentDescription = "Audio & quality tracks",
                                            tint = CosmicCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Tracks",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        val idx = PLAYER_SPEEDS.indexOf(playbackSpeed)
                                        val nextSpeed = PLAYER_SPEEDS[(idx + 1) % PLAYER_SPEEDS.size]
                                        playbackSpeed = nextSpeed
                                        exoPlayer.playbackParameters = PlaybackParameters(nextSpeed)
                                    },
                                    color = ObsidianCard.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("player_speed_chip")
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CosmicCyan,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }

                                Surface(
                                    onClick = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size },
                                    color = ObsidianCard.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = resizeModes[resizeModeIndex].second,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vega-style Auto-Download Discovery Pill when entering or playing a stream (stays accessible even when HUD auto-hides)
        val matchingStreamTask = if (!isOfflineItem && !activeUri.isNullOrBlank()) {
            downloads.firstOrNull { it.sourceUrl == activeUri }
        } else null
        val activeStreamDownload = matchingStreamTask?.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
            matchingStreamTask?.status == DownloadTaskEntity.STATUS_QUEUED
        val showPlayerAutoDownloadPill = !isOfflineItem &&
            !activeUri.isNullOrBlank() &&
            !showControls &&
            !autoDownloadBannerDismissed &&
            (autoDownloadBannerVisible || activeStreamDownload)

        AnimatedVisibility(
            visible = showPlayerAutoDownloadPill,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .testTag("player_auto_download_pill")
        ) {
            val prefetchInfo = activeUri?.let { prefetchedStreams[it] }
            val metrics = matchingStreamTask?.let { liveDownloadMetrics[it.id] }
            val isDone = matchingStreamTask?.status == DownloadTaskEntity.STATUS_COMPLETED

            val pillText = when {
                isDone -> "Saved to Offline Vault"
                activeStreamDownload -> buildString {
                    val pct = metrics?.percent ?: ((matchingStreamTask?.progressFraction ?: 0f) * 100).toInt()
                    append("Downloading $pct%")
                    metrics?.speedBytesPerSec?.takeIf { it > 0L }?.let {
                        append(" • ${VideoDownloadManager.formatSpeed(it)}")
                    }
                }
                prefetchInfo != null -> buildString {
                    append("Download Ready")
                    prefetchInfo.qualityLabel?.let { append(" • $it") }
                    if (prefetchInfo.contentLengthBytes > 0L) {
                        append(" • ${VideoMetadataHelper.formatFileSize(prefetchInfo.contentLengthBytes)}")
                    }
                }
                else -> "Stream Ready • Tap to Download"
            }

            Surface(
                onClick = {
                    if (isDone && matchingStreamTask != null) {
                        activeVideoId = matchingStreamTask.id
                    } else if (!activeStreamDownload && !activeUri.isNullOrBlank()) {
                        viewModel.enqueueDownload(
                            url = activeUri!!,
                            title = currentTitle,
                            preferredVariantUrl = prefetchInfo?.variants?.firstOrNull()?.url
                        )
                    }
                },
                color = ObsidianCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, CosmicCyan.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeStreamDownload) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CosmicCyan
                        )
                    } else {
                        Icon(
                            imageVector = if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                            contentDescription = "Download stream",
                            tint = if (isDone) EmeraldSuccess else CosmicCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { autoDownloadBannerDismissed = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    if (showTrackSelectorSheet) {
        PlayerTracksBottomSheet(
            tracks = currentTracks,
            exoPlayer = exoPlayer,
            onDismiss = { showTrackSelectorSheet = false }
        )
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Offline Vault Queue (${allVideos.size})", style = MaterialTheme.typography.titleLarge)
                }
                items(allVideos, key = { it.id }) { item ->
                    val isCurrent = item.id == activeVideoId
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            activeVideoId?.let { vid ->
                                viewModel.savePlaybackProgress(vid, exoPlayer.currentPosition, exoPlayer.duration)
                            }
                            activeVideoId = item.id
                            showQueueSheet = false
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${VideoMetadataHelper.formatDuration(item.durationMs)} • ${VideoMetadataHelper.formatFileSize(item.fileSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCurrent) {
                                StatusPillBadge("PLAYING", CosmicCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTracksBottomSheet(
    tracks: Tracks,
    exoPlayer: ExoPlayer,
    onDismiss: () -> Unit
) {
    val videoGroups = remember(tracks) {
        tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
    }
    val audioGroups = remember(tracks) {
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
    }
    val textGroups = remember(tracks) {
        tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Video Quality, Audio & Subtitles", style = MaterialTheme.typography.titleLarge)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = CosmicCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Video Resolution", style = MaterialTheme.typography.titleSmall)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val hasOverride = exoPlayer.trackSelectionParameters.overrides.keys.any { it.type == C.TRACK_TYPE_VIDEO }
                    FilterChip(
                        selected = !hasOverride,
                        onClick = {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .build()
                        },
                        label = { Text("Auto") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
                videoGroups.forEach { group ->
                    for (trackIdx in 0 until group.length) {
                        if (!group.isTrackSupported(trackIdx)) continue
                        val format = group.getTrackFormat(trackIdx)
                        val label = buildString {
                            if (format.height > 0) append("${format.height}p") else append("Track ${trackIdx + 1}")
                            if (format.bitrate > 0) append(" (${format.bitrate / 1000} kbps)")
                        }
                        val isSelected = group.isTrackSelected(trackIdx)
                        item {
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(group.mediaTrackGroup, trackIdx)
                                        )
                                        .build()
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }
            }

            if (audioGroups.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = CosmicCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audio Track", style = MaterialTheme.typography.titleSmall)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    audioGroups.forEachIndexed { gIdx, group ->
                        for (trackIdx in 0 until group.length) {
                            if (!group.isTrackSupported(trackIdx)) continue
                            val format = group.getTrackFormat(trackIdx)
                            val label = format.label
                                ?: format.language?.uppercase()
                                ?: "Audio ${gIdx + 1}"
                            val isSelected = group.isTrackSelected(trackIdx)
                            item {
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(
                                                TrackSelectionOverride(group.mediaTrackGroup, trackIdx)
                                            )
                                            .build()
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (textGroups.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = CosmicCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Subtitles", style = MaterialTheme.typography.titleSmall)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        val disabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                        FilterChip(
                            selected = disabled,
                            onClick = {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                            },
                            label = { Text("Off") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    textGroups.forEachIndexed { gIdx, group ->
                        for (trackIdx in 0 until group.length) {
                            if (!group.isTrackSupported(trackIdx)) continue
                            val format = group.getTrackFormat(trackIdx)
                            val label = format.label
                                ?: format.language?.uppercase()
                                ?: "Subtitle ${gIdx + 1}"
                            val isSelected = group.isTrackSelected(trackIdx)
                            item {
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setOverrideForType(
                                                TrackSelectionOverride(group.mediaTrackGroup, trackIdx)
                                            )
                                            .build()
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
