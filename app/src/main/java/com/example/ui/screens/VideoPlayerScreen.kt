package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaTrack
import com.example.playback.PlaybackUiState
import com.example.ui.components.formatDuration
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
enum class VideoAspectRatioMode(
    val label: String,
    val resizeMode: Int
) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    fun next(): VideoAspectRatioMode = when (this) {
        FIT -> FILL
        FILL -> ZOOM
        ZOOM -> FIT
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playbackState: PlaybackUiState,
    player: Player?,
    allVideos: List<MediaTrack>,
    isFullScreen: Boolean,
    isInPipMode: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetRepeatMode: (Int) -> Unit,
    onToggleLike: (MediaTrack) -> Unit,
    onAddToPlaylist: (MediaTrack) -> Unit,
    onSelectVideo: (MediaTrack) -> Unit,
    onToggleFullScreen: () -> Unit,
    onEnterPipMode: () -> Unit
) {
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var aspectRatioMode by rememberSaveable { mutableStateOf(VideoAspectRatioMode.FIT) }
    var autoplayEnabled by rememberSaveable { mutableStateOf(true) }
    var ambientModeEnabled by rememberSaveable { mutableStateOf(true) }
    var stableVolumeEnabled by rememberSaveable { mutableStateOf(false) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }
    var showUnlockPrompt by remember { mutableStateOf(false) }
    var unlockPromptTick by remember { mutableIntStateOf(0) }

    var settingsPage by remember { mutableStateOf<VideoSettingsSheetPage?>(null) }
    var showMoreVideos by remember { mutableStateOf(false) }
    var activeSleepTimer by rememberSaveable { mutableStateOf(VideoSleepTimerOption.OFF) }
    var sleepRemainingSec by remember { mutableIntStateOf(0) }

    var isScrubbing by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var statusHudLabel by remember { mutableStateOf<String?>(null) }
    var forcePortrait by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Double-tap seek gesture HUD state
    var doubleTapSide by remember { mutableStateOf<DoubleTapSeekSide?>(null) }
    var doubleTapSeconds by remember { mutableIntStateOf(0) }
    var doubleTapTick by remember { mutableIntStateOf(0) }

    // Long-press 2x speed boost state
    var isSpeedBoostActive by remember { mutableStateOf(false) }
    var preBoostSpeed by remember { mutableFloatStateOf(1.0f) }

    // Vertical swipe brightness & volume HUD state
    var activeGestureHudType by remember { mutableStateOf<GestureAdjustType?>(null) }
    var activeGestureHudFraction by remember { mutableFloatStateOf(0.5f) }
    var gestureHudDismissTick by remember { mutableIntStateOf(0) }

    // Live ExoPlayer track & quality state
    var playerTracksVersion by remember { mutableIntStateOf(0) }
    var videoHeightPx by remember { mutableIntStateOf(0) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }

    val latestPositionMs by rememberUpdatedState(playbackState.positionMs)
    val latestDurationMs by rememberUpdatedState(playbackState.durationMs)
    val latestSpeed by rememberUpdatedState(playbackState.playbackSpeed)
    val latestTrack by rememberUpdatedState(track)
    val latestAllVideos by rememberUpdatedState(allVideos)
    val latestAutoplay by rememberUpdatedState(autoplayEnabled)
    val latestSleepTimer by rememberUpdatedState(activeSleepTimer)
    val loopVideo = playbackState.repeatMode == Player.REPEAT_MODE_ONE

    val otherVideos = remember(allVideos, track.id) {
        allVideos.filter { it.id != track.id }
    }
    val nextSuggestedVideo = remember(allVideos, track.id) {
        if (allVideos.size <= 1) null
        else {
            val idx = allVideos.indexOfFirst { it.id == track.id }
            if (idx >= 0) allVideos[(idx + 1) % allVideos.size] else allVideos.firstOrNull { it.id != track.id }
        }
    }

    // Observe ExoPlayer tracks, video dimensions, and end-of-video for Autoplay / Sleep Timer
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        videoHeightPx = player.videoSize.height
        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        playerTracksVersion++

        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                playerTracksVersion++
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoHeightPx = videoSize.height
            }

            override fun onPlaybackStateChanged(state: Int) {
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    if (latestSleepTimer == VideoSleepTimerOption.END_OF_VIDEO) {
                        activeSleepTimer = VideoSleepTimerOption.OFF
                        onPause()
                        statusHudLabel = "Sleep timer paused video"
                    } else if (player.repeatMode != Player.REPEAT_MODE_ONE && latestAutoplay) {
                        val videos = latestAllVideos
                        val idx = videos.indexOfFirst { it.id == latestTrack.id }
                        if (videos.size > 1) {
                            val nextVideo = if (idx >= 0) videos[(idx + 1) % videos.size] else videos.first()
                            if (nextVideo.id != latestTrack.id) {
                                onSelectVideo(nextVideo)
                            }
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Keep buffered bar fresh during playback
    LaunchedEffect(player, playbackState.positionMs) {
        if (player != null) {
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(playbackState.positionMs)
        }
    }

    // Sleep timer countdown
    LaunchedEffect(activeSleepTimer) {
        val mins = activeSleepTimer.minutes
        if (mins != null && mins > 0) {
            sleepRemainingSec = mins * 60
            while (sleepRemainingSec > 0) {
                delay(1_000L)
                sleepRemainingSec--
            }
            if (activeSleepTimer.minutes == mins) {
                onPause()
                activeSleepTimer = VideoSleepTimerOption.OFF
                statusHudLabel = "Sleep timer paused video"
            }
        } else {
            sleepRemainingSec = 0
        }
    }

    // Apply gentle stable volume leveling when toggled
    LaunchedEffect(player, stableVolumeEnabled) {
        if (player != null) {
            player.volume = if (stableVolumeEnabled) 0.92f else 1.0f
        }
    }

    // Keep the display awake while actively watching video
    DisposableEffect(view, playbackState.isPlaying) {
        view.keepScreenOn = playbackState.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    // Guarantee orientation, brightness, and system bars are restored cleanly on exit
    DisposableEffect(view, context) {
        val activity = context.findVideoHostActivity()
        val window = activity?.window
        val controller = if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view)
        } else null
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevLightNav = controller?.isAppearanceLightNavigationBars ?: false
        val prevBrightness = window?.attributes?.screenBrightness ?: -1f
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { win ->
                win.attributes = win.attributes.apply { screenBrightness = prevBrightness }
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = prevLightStatus
            controller?.isAppearanceLightNavigationBars = prevLightNav
            view.post {
                view.requestLayout()
                ViewCompat.requestApplyInsets(view)
            }
        }
    }

    // Lock sensor landscape / exit cleanly to portrait without getting stuck
    DisposableEffect(isFullScreen, forcePortrait, isInPipMode, view, context) {
        val activity = context.findVideoHostActivity()
        val window = activity?.window
        val controller = if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view)
        } else null

        if (isFullScreen && !isInPipMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else if (forcePortrait && !isInPipMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        view.post {
            view.requestLayout()
            ViewCompat.requestApplyInsets(view)
        }

        onDispose {}
    }

    BackHandler {
        when {
            settingsPage != null -> settingsPage = null
            showMoreVideos -> showMoreVideos = false
            isScreenLocked -> {
                showUnlockPrompt = true
                unlockPromptTick++
            }
            isFullScreen -> {
                forcePortrait = true
                onToggleFullScreen()
            }
            else -> onBack()
        }
    }

    // Auto-hide main controls while playing
    LaunchedEffect(
        controlsVisible,
        playbackState.isPlaying,
        isScrubbing,
        settingsPage,
        showMoreVideos,
        interactionTick
    ) {
        if (controlsVisible && playbackState.isPlaying && !isScrubbing && settingsPage == null && !showMoreVideos) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    // Auto-hide double-tap seek ripple
    LaunchedEffect(doubleTapTick) {
        if (doubleTapTick > 0) {
            delay(850L)
            doubleTapSide = null
            doubleTapSeconds = 0
        }
    }

    // Auto-hide brightness/volume vertical pill HUD
    LaunchedEffect(gestureHudDismissTick) {
        if (gestureHudDismissTick > 0) {
            delay(750L)
            activeGestureHudType = null
        }
    }

    // Auto-hide unlock prompt when screen is locked
    LaunchedEffect(unlockPromptTick, isScreenLocked) {
        if (isScreenLocked && showUnlockPrompt) {
            delay(2_800L)
            showUnlockPrompt = false
        }
    }

    // Auto-hide transient status HUD pills (Aspect Ratio, Captions, Sleep Timer)
    LaunchedEffect(statusHudLabel) {
        if (statusHudLabel != null) {
            delay(1_400L)
            statusHudLabel = null
        }
    }

    // Derive live ExoPlayer captions & audio track lists
    val captionOptions = remember(player, playerTracksVersion) {
        extractPlayerTrackOptions(player, C.TRACK_TYPE_TEXT)
    }
    val audioOptions = remember(player, playerTracksVersion) {
        extractPlayerTrackOptions(player, C.TRACK_TYPE_AUDIO)
    }
    val activeCaption = captionOptions.firstOrNull { it.isSelected }
    val captionsLabel = activeCaption?.label ?: "Off"
    val audioTrackLabel = audioOptions.firstOrNull { it.isSelected }?.label ?: "Original"
    val qualityLabel = remember(videoHeightPx) {
        if (videoHeightPx > 0) "${videoHeightPx}p" else "Auto"
    }
    val sleepTimerLabel = when {
        activeSleepTimer == VideoSleepTimerOption.OFF -> "Off"
        activeSleepTimer == VideoSleepTimerOption.END_OF_VIDEO -> "End of video"
        sleepRemainingSec > 0 -> {
            val m = sleepRemainingSec / 60
            val s = sleepRemainingSec % 60
            String.format(Locale.US, "%d:%02d", m, s)
        }
        else -> activeSleepTimer.label
    }

    val edgeGuardPx = with(density) { 48.dp.toPx() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
    ) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val isLandscapeViewport = viewportWidth > viewportHeight
        val isEffectivelyFullScreen = isFullScreen || isLandscapeViewport

        // Hide system bars in fullscreen or landscape
        LaunchedEffect(isLandscapeViewport, isFullScreen, controlsVisible, isInPipMode) {
            val activity = context.findVideoHostActivity()
            val window = activity?.window
            val controller = if (window != null && !view.isInEditMode) {
                WindowCompat.getInsetsController(window, view)
            } else null
            if (!isInPipMode && (isFullScreen || isLandscapeViewport)) {
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            } else if (!isInPipMode) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // Single clean re-measure on orientation/viewport change
        LaunchedEffect(configuration.orientation, viewportWidth, viewportHeight) {
            playerViewRef?.post {
                playerViewRef?.requestLayout()
                playerViewRef?.invalidate()
            }
        }

        // Ambient Mode subtle YouTube cinema glow behind letterboxed video
        if (ambientModeEnabled && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                Color.Black
                            )
                        )
                    )
            )
        }

        // Full-bleed TextureView-backed Media3 PlayerView surface (prevents SurfaceView rotation clipping)
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_video_player, null, false) as PlayerView).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = aspectRatioMode.resizeMode
                    this.player = player
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerViewRef = playerView
                if (playerView.player !== player) {
                    playerView.player = player
                }
                if (playerView.resizeMode != aspectRatioMode.resizeMode) {
                    playerView.resizeMode = aspectRatioMode.resizeMode
                }
            },
            onRelease = { playerView ->
                if (playerViewRef === playerView) {
                    playerViewRef = null
                }
                playerView.player = null
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("video_player_surface")
        )

        if (isInPipMode) {
            return@BoxWithConstraints
        }

        // Unified Gesture Surface: Double-Tap Left/Right Seek, Long-Press 2x Speed, Vertical Swipe Brightness/Volume
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isScreenLocked, settingsPage, showMoreVideos) {
                    if (settingsPage != null || showMoreVideos) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            if (isScreenLocked) {
                                showUnlockPrompt = !showUnlockPrompt
                                if (showUnlockPrompt) unlockPromptTick++
                            } else {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) interactionTick++
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isScreenLocked) {
                                showUnlockPrompt = true
                                unlockPromptTick++
                                return@detectTapGestures
                            }
                            val widthF = size.width.toFloat().coerceAtLeast(1f)
                            when {
                                offset.x < widthF * 0.35f -> {
                                    val newPos = (latestPositionMs - 10_000L).coerceAtLeast(0L)
                                    onSeekTo(newPos)
                                    doubleTapSeconds = if (doubleTapSide == DoubleTapSeekSide.LEFT) {
                                        doubleTapSeconds + 10
                                    } else {
                                        10
                                    }
                                    doubleTapSide = DoubleTapSeekSide.LEFT
                                    doubleTapTick++
                                }
                                offset.x > widthF * 0.65f -> {
                                    val maxPos = latestDurationMs.coerceAtLeast(latestPositionMs)
                                    val newPos = (latestPositionMs + 10_000L).coerceAtMost(maxPos)
                                    onSeekTo(newPos)
                                    doubleTapSeconds = if (doubleTapSide == DoubleTapSeekSide.RIGHT) {
                                        doubleTapSeconds + 10
                                    } else {
                                        10
                                    }
                                    doubleTapSide = DoubleTapSeekSide.RIGHT
                                    doubleTapTick++
                                }
                                else -> {
                                    onTogglePlayPause()
                                    controlsVisible = true
                                    interactionTick++
                                }
                            }
                        },
                        onLongPress = {
                            if (!isScreenLocked && !isSpeedBoostActive) {
                                preBoostSpeed = latestSpeed
                                isSpeedBoostActive = true
                                onSetSpeed(2.0f)
                            }
                        },
                        onPress = {
                            try {
                                tryAwaitRelease()
                            } finally {
                                if (isSpeedBoostActive) {
                                    isSpeedBoostActive = false
                                    onSetSpeed(preBoostSpeed)
                                }
                            }
                        }
                    )
                }
                .pointerInput(isScreenLocked, settingsPage, showMoreVideos, audioManager, edgeGuardPx) {
                    if (isScreenLocked || settingsPage != null || showMoreVideos) return@pointerInput
                    var dragType: GestureAdjustType? = null
                    var dragFraction = 0.5f

                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            // Respect top/bottom OS gesture zones so pulling notification shade or nav bar never misfires
                            if (startOffset.y < edgeGuardPx || startOffset.y > size.height - edgeGuardPx) {
                                dragType = null
                                return@detectVerticalDragGestures
                            }
                            if (startOffset.x < size.width / 2f) {
                                dragType = GestureAdjustType.BRIGHTNESS
                                val winBrightness = context.findVideoHostActivity()?.window?.attributes?.screenBrightness ?: -1f
                                dragFraction = if (winBrightness in 0f..1f) {
                                    winBrightness
                                } else {
                                    runCatching {
                                        Settings.System.getInt(
                                            context.contentResolver,
                                            Settings.System.SCREEN_BRIGHTNESS,
                                            128
                                        ) / 255f
                                    }.getOrDefault(0.5f).coerceIn(0.05f, 1.0f)
                                }
                            } else {
                                dragType = GestureAdjustType.VOLUME
                                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                                val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
                                dragFraction = (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                            }
                            activeGestureHudType = dragType
                            activeGestureHudFraction = dragFraction
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val currentType = dragType ?: return@detectVerticalDragGestures
                            val sweepPx = (size.height * 0.62f).coerceAtLeast(180f)
                            dragFraction = (dragFraction - dragAmount / sweepPx).coerceIn(0f, 1f)
                            activeGestureHudType = currentType
                            activeGestureHudFraction = dragFraction

                            when (currentType) {
                                GestureAdjustType.BRIGHTNESS -> {
                                    context.findVideoHostActivity()?.window?.let { win ->
                                        win.attributes = win.attributes.apply {
                                            screenBrightness = dragFraction.coerceIn(0.01f, 1.0f)
                                        }
                                    }
                                }
                                GestureAdjustType.VOLUME -> {
                                    audioManager?.let { am ->
                                        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val targetStep = (dragFraction * maxVol).roundToInt().coerceIn(0, maxVol)
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragType != null) {
                                dragType = null
                                gestureHudDismissTick++
                            }
                        },
                        onDragCancel = {
                            if (dragType != null) {
                                dragType = null
                                gestureHudDismissTick++
                            }
                        }
                    )
                }
                .testTag("video_player_gesture_layer")
        )

        // Semicircular Double-Tap Seek Overlay (Left & Right)
        VideoDoubleTapSeekOverlay(
            side = doubleTapSide,
            accumulatedSeconds = doubleTapSeconds
        )

        // Vertical Brightness / Volume Pill HUD
        VideoGestureAdjustHud(
            type = activeGestureHudType,
            levelFraction = activeGestureHudFraction
        )

        // Long-press 2x Speed Pill
        AnimatedVisibility(
            visible = isSpeedBoostActive,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.testTag("video_speed_boost_hud")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "2x",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Transient Status HUD Pill (Aspect Ratio, Captions, Sleep Timer)
        AnimatedVisibility(
            visible = statusHudLabel != null && !isSpeedBoostActive,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.78f)
            ) {
                Text(
                    text = statusHudLabel.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Screen Locked Pill Overlay
        if (isScreenLocked) {
            ScreenLockedOverlay(
                showUnlockPrompt = showUnlockPrompt,
                onUnlock = {
                    isScreenLocked = false
                    showUnlockPrompt = false
                    controlsVisible = true
                    interactionTick++
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 24.dp)
            )
        }

        // Main YouTube-Inspired Video Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible && !isScreenLocked && !showMoreVideos && settingsPage == null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            val safeInsetsModifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = if (isLandscapeViewport) 20.dp else 12.dp,
                    vertical = if (isLandscapeViewport) 8.dp else 10.dp
                )

            val centerPlaySize = if (isLandscapeViewport) 62.dp else 68.dp
            val centerPlayIcon = if (isLandscapeViewport) 36.dp else 40.dp
            val centerSkipSize = if (isLandscapeViewport) 48.dp else 52.dp
            val centerSkipIcon = if (isLandscapeViewport) 26.dp else 28.dp
            val centerGap = if (isLandscapeViewport) 38.dp else 28.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.76f),
                            0.25f to Color.Black.copy(alpha = 0.16f),
                            0.68f to Color.Black.copy(alpha = 0.18f),
                            1.0f to Color.Black.copy(alpha = 0.84f)
                        )
                    )
                    .testTag("video_player_controls_overlay")
            ) {
                Box(modifier = safeInsetsModifier) {
                    // TOP BAR (YouTube Layout): Collapse chevron • Title & subtitle • Autoplay / PiP / CC / Settings
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isFullScreen) {
                                    forcePortrait = true
                                    onToggleFullScreen()
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_player_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("video_player_title")
                            )
                            val subtitleLine = remember(track, qualityLabel) {
                                buildList {
                                    val creator = track.artist.takeIf { it.isNotBlank() && it != "Unknown Artist" }
                                        ?: track.folderPath.substringAfterLast('/').takeIf { it.isNotBlank() }
                                        ?: "Zero Play"
                                    add(creator)
                                    if (qualityLabel != "Auto") add(qualityLabel)
                                    track.formatBadges.firstOrNull()?.let { add(it) }
                                }.joinToString(" • ")
                            }
                            Text(
                                text = subtitleLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.74f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        VideoTopAutoplayToggle(
                            checked = autoplayEnabled,
                            onCheckedChange = { enabled ->
                                autoplayEnabled = enabled
                                interactionTick++
                                statusHudLabel = if (enabled) "Autoplay is on" else "Autoplay is off"
                            }
                        )

                        IconButton(
                            onClick = {
                                interactionTick++
                                onEnterPipMode()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_player_pip_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.action_pip),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                interactionTick++
                                when {
                                    activeCaption != null -> {
                                        applyCaptionSelection(player, null)
                                        playerTracksVersion++
                                        statusHudLabel = "Captions off"
                                    }
                                    captionOptions.size == 1 -> {
                                        applyCaptionSelection(player, captionOptions.first())
                                        playerTracksVersion++
                                        statusHudLabel = "Captions: ${captionOptions.first().label}"
                                    }
                                    captionOptions.size > 1 -> {
                                        settingsPage = VideoSettingsSheetPage.CAPTIONS
                                    }
                                    else -> {
                                        statusHudLabel = "No captions available"
                                    }
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_player_cc_btn")
                        ) {
                            Icon(
                                imageVector = if (activeCaption != null) {
                                    Icons.Rounded.ClosedCaption
                                } else {
                                    Icons.Outlined.ClosedCaption
                                },
                                contentDescription = "Captions",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                interactionTick++
                                settingsPage = VideoSettingsSheetPage.MAIN
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_player_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Video settings",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // CENTER TRANSPORT (YouTube Translucent Dark Circles): Previous • Play/Pause • Next
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(centerGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canSkipPrev = playbackState.queue.size > 1 || playbackState.positionMs > 3_000L
                        IconButton(
                            onClick = {
                                interactionTick++
                                onSkipPrevious()
                            },
                            modifier = Modifier
                                .size(centerSkipSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.44f))
                                .testTag("video_player_prev_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous video",
                                tint = Color.White.copy(alpha = if (canSkipPrev) 1f else 0.45f),
                                modifier = Modifier.size(centerSkipIcon)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(centerPlaySize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                                .clickable {
                                    interactionTick++
                                    onTogglePlayPause()
                                }
                                .testTag("video_player_play_pause_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playbackState.isBuffering && !playbackState.isPlaying) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = stringResource(R.string.action_play_pause),
                                    tint = Color.White,
                                    modifier = Modifier.size(centerPlayIcon)
                                )
                            }
                        }

                        val canSkipNext = playbackState.queue.size > 1 || nextSuggestedVideo != null
                        IconButton(
                            onClick = {
                                interactionTick++
                                if (playbackState.queue.size > 1) {
                                    onSkipNext()
                                } else if (nextSuggestedVideo != null) {
                                    onSelectVideo(nextSuggestedVideo)
                                }
                            },
                            modifier = Modifier
                                .size(centerSkipSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.44f))
                                .testTag("video_player_next_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next video",
                                tint = Color.White.copy(alpha = if (canSkipNext) 1f else 0.45f),
                                modifier = Modifier.size(centerSkipIcon)
                            )
                        }
                    }

                    // BOTTOM DECK (YouTube Layout): Timestamp pill + Fullscreen • Red Seek Bar • Quick Actions + More Videos
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = YouTubeOverlayPillBg
                            ) {
                                Text(
                                    text = "${formatDuration(playbackState.positionMs)} / ${formatDuration(playbackState.durationMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("video_player_timestamp_pill")
                                )
                            }

                            IconButton(
                                onClick = {
                                    interactionTick++
                                    when {
                                        isFullScreen -> {
                                            forcePortrait = true
                                            onToggleFullScreen()
                                        }
                                        isLandscapeViewport -> {
                                            forcePortrait = true
                                        }
                                        else -> {
                                            forcePortrait = false
                                            onToggleFullScreen()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(YouTubeOverlayPillBg)
                                    .testTag("video_player_fullscreen_btn")
                            ) {
                                Icon(
                                    imageVector = if (isEffectivelyFullScreen) {
                                        Icons.Rounded.FullscreenExit
                                    } else {
                                        Icons.Rounded.Fullscreen
                                    },
                                    contentDescription = if (isEffectivelyFullScreen) {
                                        "Exit fullscreen"
                                    } else {
                                        stringResource(R.string.action_fullscreen)
                                    },
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        YouTubeSeekBar(
                            positionMs = playbackState.positionMs,
                            durationMs = playbackState.durationMs,
                            bufferedMs = bufferedPositionMs,
                            onScrubbingChange = { scrubbing ->
                                isScrubbing = scrubbing
                                interactionTick++
                            },
                            onSeekTo = { targetMs ->
                                interactionTick++
                                onSeekTo(targetMs)
                            }
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Bottom Quick Actions Row (YouTube style + 100% real actions)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isLandscapeViewport) 8.dp else 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onToggleLike(track)
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(YouTubeOverlayPillBg)
                                        .testTag("video_player_like_btn")
                                ) {
                                    Icon(
                                        imageVector = if (track.isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = if (track.isLiked) "Unlike video" else "Like video",
                                        tint = if (track.isLiked) YouTubeRed else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onAddToPlaylist(track)
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(YouTubeOverlayPillBg)
                                        .testTag("video_player_save_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkBorder,
                                        contentDescription = "Save to playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = YouTubeOverlayPillBg,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            interactionTick++
                                            aspectRatioMode = aspectRatioMode.next()
                                            statusHudLabel = aspectRatioMode.label
                                        }
                                        .testTag("video_player_aspect_ratio_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AspectRatio,
                                            contentDescription = "Aspect ratio: ${aspectRatioMode.label}",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = aspectRatioMode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        isScreenLocked = true
                                        controlsVisible = false
                                        showUnlockPrompt = true
                                        unlockPromptTick++
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(YouTubeOverlayPillBg)
                                        .testTag("video_player_lock_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LockOpen,
                                        contentDescription = "Lock screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                if (isLandscapeViewport) {
                                    IconButton(
                                        onClick = {
                                            interactionTick++
                                            settingsPage = VideoSettingsSheetPage.MAIN
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(YouTubeOverlayPillBg)
                                            .testTag("video_player_more_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreHoriz,
                                            contentDescription = "More options",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // "More videos" YouTube pill with live next-video thumbnail preview
                            Surface(
                                shape = CircleShape,
                                color = YouTubeOverlayPillBg,
                                modifier = Modifier
                                    .height(40.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                    .clip(CircleShape)
                                    .clickable {
                                        interactionTick++
                                        showMoreVideos = true
                                    }
                                    .testTag("video_player_more_videos_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "More videos",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    if (nextSuggestedVideo != null) {
                                        AsyncImage(
                                            model = nextSuggestedVideo.artworkUriString?.takeIf { it.isNotBlank() }
                                                ?: nextSuggestedVideo.uriString,
                                            contentDescription = nextSuggestedVideo.title,
                                            contentScale = ContentScale.Crop,
                                            placeholder = painterResource(id = R.drawable.img_cover_horizon_1794995219984),
                                            error = painterResource(id = R.drawable.img_cover_horizon_1794995219984),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // YouTube Floating Dark Settings Sheet (Main / More / Speed / Aspect Ratio / Captions / Audio / Sleep Timer)
        VideoSettingsModalSheet(
            page = settingsPage,
            qualityLabel = qualityLabel,
            aspectRatioMode = aspectRatioMode,
            playbackSpeed = playbackState.playbackSpeed,
            captionsLabel = captionsLabel,
            captionOptions = captionOptions,
            audioTrackLabel = audioTrackLabel,
            audioOptions = audioOptions,
            loopVideo = loopVideo,
            ambientMode = ambientModeEnabled,
            stableVolume = stableVolumeEnabled,
            sleepTimerLabel = sleepTimerLabel,
            activeSleepTimer = activeSleepTimer,
            onNavigate = { nextPage -> settingsPage = nextPage },
            onSelectAspectRatio = { mode ->
                aspectRatioMode = mode
                statusHudLabel = mode.label
            },
            onSelectSpeed = { speed ->
                onSetSpeed(speed)
                statusHudLabel = "Speed ${formatYouTubeSpeed(speed)}"
            },
            onSelectCaption = { opt ->
                applyCaptionSelection(player, opt)
                playerTracksVersion++
                statusHudLabel = if (opt == null) "Captions off" else "Captions: ${opt.label}"
            },
            onSelectAudioTrack = { opt ->
                applyAudioTrackSelection(player, opt)
                playerTracksVersion++
                statusHudLabel = "Audio: ${opt.label}"
            },
            onLockScreen = {
                isScreenLocked = true
                controlsVisible = false
                showUnlockPrompt = true
                unlockPromptTick++
            },
            onToggleLoopVideo = { enabled ->
                onSetRepeatMode(if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF)
                statusHudLabel = if (enabled) "Loop video on" else "Loop video off"
            },
            onToggleAmbientMode = { enabled ->
                ambientModeEnabled = enabled
                statusHudLabel = if (enabled) "Ambient mode on" else "Ambient mode off"
            },
            onToggleStableVolume = { enabled ->
                stableVolumeEnabled = enabled
                statusHudLabel = if (enabled) "Stable volume on" else "Stable volume off"
            },
            onSelectSleepTimer = { option ->
                activeSleepTimer = option
                statusHudLabel = if (option == VideoSleepTimerOption.OFF) {
                    "Sleep timer off"
                } else {
                    "Sleep timer: ${option.label}"
                }
            },
            onDismiss = {
                settingsPage = null
                interactionTick++
            }
        )

        // YouTube "More videos" Full-Width Horizontal Carousel Overlay
        MoreVideosOverlay(
            visible = showMoreVideos,
            videos = otherVideos,
            currentTrackId = track.id,
            onSelectVideo = { selected ->
                onSelectVideo(selected)
                controlsVisible = true
                interactionTick++
            },
            onDismiss = {
                showMoreVideos = false
                interactionTick++
            }
        )
    }
}

private fun extractPlayerTrackOptions(player: Player?, trackType: Int): List<VideoTrackOption> {
    if (player == null) return emptyList()
    val groups = player.currentTracks.groups
    val results = mutableListOf<VideoTrackOption>()
    groups.forEachIndexed { groupIdx, group ->
        if (group.type == trackType) {
            for (trackIdx in 0 until group.length) {
                if (!group.isTrackSupported(trackIdx)) continue
                val format = group.getTrackFormat(trackIdx)
                val langDisplay = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.let { code ->
                    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
                        .replaceFirstChar { it.uppercase() }
                }
                val baseLabel = format.label?.takeIf { it.isNotBlank() }
                    ?: langDisplay
                    ?: if (trackType == C.TRACK_TYPE_TEXT) "Subtitle ${results.size + 1}" else "Track ${results.size + 1}"
                val channelHint = if (trackType == C.TRACK_TYPE_AUDIO && format.channelCount > 2) {
                    " (${format.channelCount}ch)"
                } else ""
                results += VideoTrackOption(
                    id = "${groupIdx}_$trackIdx",
                    label = "$baseLabel$channelHint",
                    groupIndex = groupIdx,
                    trackIndex = trackIdx,
                    isSelected = group.isTrackSelected(trackIdx)
                )
            }
        }
    }
    return results
}

private fun applyCaptionSelection(player: Player?, option: VideoTrackOption?) {
    if (player == null) return
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
    }
    player.trackSelectionParameters = builder.build()
}

private fun applyAudioTrackSelection(player: Player?, option: VideoTrackOption) {
    if (player == null) return
    val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}

private tailrec fun Context.findVideoHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findVideoHostActivity()
    else -> null
}
