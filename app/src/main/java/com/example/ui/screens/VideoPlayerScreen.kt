package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.playback.PlaybackUiState
import com.example.ui.components.formatDuration
import kotlinx.coroutines.delay

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

private val VIDEO_PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playbackState: PlaybackUiState,
    player: Player?,
    isFullScreen: Boolean,
    isInPipMode: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleFullScreen: () -> Unit,
    onEnterPipMode: () -> Unit
) {
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val view = LocalView.current

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var aspectRatioMode by rememberSaveable { mutableStateOf(VideoAspectRatioMode.FIT) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var aspectHudLabel by remember { mutableStateOf<String?>(null) }

    // Keep the display awake while actively watching video.
    DisposableEffect(view, playbackState.isPlaying) {
        view.keepScreenOn = playbackState.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    // Force crisp light icons on the dark video canvas, and guarantee orientation + system bars
    // are restored cleanly when leaving the video player screen.
    DisposableEffect(view, context) {
        val activity = context.findVideoHostActivity()
        val window = activity?.window
        val controller = if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view)
        } else null
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevLightNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = prevLightStatus
            controller?.isAppearanceLightNavigationBars = prevLightNav
        }
    }

    // Lock sensor landscape and hide system bars when fullscreen is enabled (outside PiP).
    DisposableEffect(isFullScreen, isInPipMode, view, context) {
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
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {}
    }

    BackHandler {
        if (isFullScreen) {
            onToggleFullScreen()
        } else {
            onBack()
        }
    }

    // Auto-hide controls after inactivity while playing.
    LaunchedEffect(
        controlsVisible,
        playbackState.isPlaying,
        isScrubbing,
        speedMenuExpanded,
        interactionTick
    ) {
        if (controlsVisible && playbackState.isPlaying && !isScrubbing && !speedMenuExpanded) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    // Briefly display an aspect-ratio HUD pill when cycling Fit / Fill / Zoom.
    LaunchedEffect(aspectHudLabel) {
        if (aspectHudLabel != null) {
            delay(1_100L)
            aspectHudLabel = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isInPipMode) {
                if (!isInPipMode) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) interactionTick++
                        }
                    )
                }
            }
            .testTag("video_player_screen")
    ) {
        // Dedicated full-bleed Media3 PlayerView surface.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspectRatioMode.resizeMode
                    this.player = player
                }
            },
            update = { playerView ->
                if (playerView.player !== player) {
                    playerView.player = player
                }
                if (playerView.resizeMode != aspectRatioMode.resizeMode) {
                    playerView.resizeMode = aspectRatioMode.resizeMode
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("video_player_surface")
        )

        if (isInPipMode) {
            return@Box
        }

        // Brief HUD pill when switching Fit / Fill / Zoom.
        AnimatedVisibility(
            visible = aspectHudLabel != null,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.72f)
            ) {
                Text(
                    text = aspectHudLabel.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Tap-to-toggle overlay controls (no Lyrics, Flow, or Song DNA).
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            val safeInsetsModifier = if (isFullScreen) {
                Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            } else {
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.74f),
                            0.24f to Color.Black.copy(alpha = 0.24f),
                            0.72f to Color.Black.copy(alpha = 0.26f),
                            1.0f to Color.Black.copy(alpha = 0.84f)
                        )
                    )
                    .testTag("video_player_controls_overlay")
            ) {
                Box(modifier = safeInsetsModifier) {
                    // Top Bar: Back button, video title, codec/container badge, PiP
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isFullScreen) onToggleFullScreen() else onBack()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.42f))
                                .testTag("video_player_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
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
                            val badges = track.formatBadges
                            if (badges.isNotEmpty()) {
                                Text(
                                    text = badges.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                interactionTick++
                                onEnterPipMode()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.42f))
                                .testTag("video_player_pip_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.action_pip),
                                tint = Color.White
                            )
                        }
                    }

                    // Center Transport: Rewind 10s • Play / Pause • Forward 10s
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                interactionTick++
                                onSeekTo((playbackState.positionMs - 10_000L).coerceAtLeast(0L))
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                                .testTag("video_player_rewind_10_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    interactionTick++
                                    onTogglePlayPause()
                                }
                                .testTag("video_player_play_pause_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playbackState.isBuffering && !playbackState.isPlaying) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(30.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = stringResource(R.string.action_play_pause),
                                    tint = Color.Black,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                interactionTick++
                                val maxPos = playbackState.durationMs.coerceAtLeast(playbackState.positionMs)
                                onSeekTo((playbackState.positionMs + 10_000L).coerceAtMost(maxPos))
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                                .testTag("video_player_forward_10_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Bottom Control Deck: Seek bar + timestamps + Speed / Aspect Ratio / Fullscreen
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        VideoSeekBar(
                            positionMs = playbackState.positionMs,
                            durationMs = playbackState.durationMs,
                            onScrubbingChange = { scrubbing ->
                                isScrubbing = scrubbing
                                interactionTick++
                            },
                            onSeekTo = { targetMs ->
                                interactionTick++
                                onSeekTo(targetMs)
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Playback Speed Selector
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.Black.copy(alpha = 0.50f),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable {
                                                interactionTick++
                                                speedMenuExpanded = true
                                            }
                                            .testTag("video_player_speed_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Speed,
                                                contentDescription = "Playback speed",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "${formatSpeedValue(playbackState.playbackSpeed)}x",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = speedMenuExpanded,
                                        onDismissRequest = {
                                            speedMenuExpanded = false
                                            interactionTick++
                                        }
                                    ) {
                                        VIDEO_PLAYBACK_SPEEDS.forEach { speed ->
                                            val isSelected = kotlin.math.abs(playbackState.playbackSpeed - speed) < 0.01f
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "${formatSpeedValue(speed)}x",
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                trailingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = "Selected speed"
                                                        )
                                                    }
                                                } else null,
                                                onClick = {
                                                    onSetSpeed(speed)
                                                    speedMenuExpanded = false
                                                    interactionTick++
                                                }
                                            )
                                        }
                                    }
                                }

                                // Aspect Ratio Toggle (Fit -> Fill -> Zoom)
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Black.copy(alpha = 0.50f),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable {
                                            interactionTick++
                                            aspectRatioMode = aspectRatioMode.next()
                                            aspectHudLabel = aspectRatioMode.label
                                        }
                                        .testTag("video_player_aspect_ratio_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AspectRatio,
                                            contentDescription = "Aspect ratio: ${aspectRatioMode.label}",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = aspectRatioMode.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Fullscreen Orientation & System Bars Lock Button
                            IconButton(
                                onClick = {
                                    interactionTick++
                                    onToggleFullScreen()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .testTag("video_player_fullscreen_btn")
                            ) {
                                Icon(
                                    imageVector = if (isFullScreen) {
                                        Icons.Rounded.FullscreenExit
                                    } else {
                                        Icons.Rounded.Fullscreen
                                    },
                                    contentDescription = if (isFullScreen) {
                                        "Exit fullscreen"
                                    } else {
                                        stringResource(R.string.action_fullscreen)
                                    },
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    onScrubbingChange: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (isDragging) {
        dragFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayedPositionMs = if (isDragging) {
        (dragFraction * safeDuration).toLong()
    } else {
        positionMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.testTag("video_player_position_text")
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.testTag("video_player_duration_text")
            )
        }

        Slider(
            value = fraction,
            onValueChange = { value ->
                if (!isDragging) {
                    isDragging = true
                    onScrubbingChange(true)
                }
                dragFraction = value
            },
            onValueChangeFinished = {
                isDragging = false
                onScrubbingChange(false)
                onSeekTo((dragFraction * safeDuration).toLong())
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            },
            track = { sliderState ->
                val activeFraction = sliderState.value.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(activeFraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_player_seek_slider")
        )
    }
}

private fun formatSpeedValue(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}.0"
    } else {
        speed.toString().trimEnd('0').trimEnd('.')
    }
}

private tailrec fun Context.findVideoHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findVideoHostActivity()
    else -> null
}
