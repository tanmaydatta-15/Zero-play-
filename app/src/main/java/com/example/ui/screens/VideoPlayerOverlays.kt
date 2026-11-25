package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaTrack
import com.example.ui.components.formatDuration
import kotlin.math.abs
import kotlin.math.roundToInt

internal val YouTubeRed = Color(0xFFFF0033)
internal val YouTubeSheetBg = Color(0xFF1F1F1F)
internal val YouTubeOverlayPillBg = Color(0x88000000)

internal enum class VideoSettingsSheetPage {
    MAIN,
    MORE,
    SPEED,
    ASPECT_RATIO,
    CAPTIONS,
    AUDIO_TRACK,
    SLEEP_TIMER
}

internal enum class DoubleTapSeekSide {
    LEFT,
    RIGHT
}

internal enum class GestureAdjustType {
    BRIGHTNESS,
    VOLUME
}

internal data class VideoTrackOption(
    val id: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

internal enum class VideoSleepTimerOption(val label: String, val minutes: Int?) {
    OFF("Off", null),
    MIN_10("10 minutes", 10),
    MIN_15("15 minutes", 15),
    MIN_30("30 minutes", 30),
    MIN_45("45 minutes", 45),
    MIN_60("60 minutes", 60),
    END_OF_VIDEO("End of video", -1)
}

internal val YOUTUBE_PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
internal fun VideoTopAutoplayToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 0.dp,
        animationSpec = tween(180),
        label = "autoplay_thumb_offset"
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .testTag("video_player_autoplay_toggle"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.22f))
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else Color.White.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (checked) "Autoplay on" else "Autoplay off",
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
internal fun VideoDoubleTapSeekOverlay(
    side: DoubleTapSeekSide?,
    accumulatedSeconds: Int,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "double_tap_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "double_tap_chevron_alpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Left semicircular YouTube seek ripple
        AnimatedVisibility(
            visible = side == DoubleTapSeekSide.LEFT && accumulatedSeconds > 0,
            enter = fadeIn(tween(110)),
            exit = fadeOut(tween(240)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.45f)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.24f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(0f, size.height / 2f),
                            radius = size.maxDimension * 0.95f
                        ),
                        radius = size.maxDimension * 0.95f,
                        center = Offset(0f, size.height / 2f)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .testTag("video_double_tap_left_hud")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "$accumulatedSeconds seconds",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Right semicircular YouTube seek ripple
        AnimatedVisibility(
            visible = side == DoubleTapSeekSide.RIGHT && accumulatedSeconds > 0,
            enter = fadeIn(tween(110)),
            exit = fadeOut(tween(240)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.45f)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.24f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width, size.height / 2f),
                            radius = size.maxDimension * 0.95f
                        ),
                        radius = size.maxDimension * 0.95f,
                        center = Offset(size.width, size.height / 2f)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .testTag("video_double_tap_right_hud")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "$accumulatedSeconds seconds",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
internal fun VideoGestureAdjustHud(
    type: GestureAdjustType?,
    levelFraction: Float,
    modifier: Modifier = Modifier
) {
    val clamped = levelFraction.coerceIn(0f, 1f)
    val percent = (clamped * 100f).roundToInt()

    Box(modifier = modifier.fillMaxSize()) {
        // Place Brightness HUD neatly on the right-side edge when swiping left,
        // and Volume HUD neatly on the left-side edge when swiping right so the user's thumb never covers it
        // and it never collides with the center play/pause button.
        val alignment = when (type) {
            GestureAdjustType.BRIGHTNESS -> Alignment.CenterEnd
            GestureAdjustType.VOLUME -> Alignment.CenterStart
            null -> Alignment.Center
        }

        AnimatedVisibility(
            visible = type != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(alignment)
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .testTag("video_gesture_adjust_hud")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val icon = when (type) {
                        GestureAdjustType.BRIGHTNESS -> when {
                            clamped < 0.33f -> Icons.Rounded.BrightnessLow
                            clamped < 0.70f -> Icons.Rounded.BrightnessMedium
                            else -> Icons.Rounded.BrightnessHigh
                        }
                        GestureAdjustType.VOLUME -> when {
                            percent == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
                            clamped < 0.50f -> Icons.AutoMirrored.Rounded.VolumeDown
                            else -> Icons.AutoMirrored.Rounded.VolumeUp
                        }
                        null -> Icons.Rounded.BrightnessHigh
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = type?.name,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )

                    // Vertical sleek progress track
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(112.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(clamped)
                                .clip(CircleShape)
                                .background(if (type == GestureAdjustType.BRIGHTNESS) Color.White else YouTubeRed)
                        )
                    }

                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YouTubeSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onScrubbingChange: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (isDragging) {
        dragFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val bufferedFraction = (bufferedMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 3.dp,
        animationSpec = tween(140),
        label = "yt_track_height"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 13.dp,
        animationSpec = tween(140),
        label = "yt_thumb_size"
    )

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
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(YouTubeRed)
                    .border(1.5.dp, Color.White.copy(alpha = if (isDragging) 0.9f else 0f), CircleShape)
            )
        },
        track = { sliderState ->
            val activeFraction = sliderState.value.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                if (bufferedFraction > activeFraction) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferedFraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.42f))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(activeFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(YouTubeRed)
                )
            }
        },
        colors = SliderDefaults.colors(
            thumbColor = YouTubeRed,
            activeTrackColor = YouTubeRed,
            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .testTag("video_player_seek_slider")
    )
}

@Composable
internal fun ScreenLockedOverlay(
    showUnlockPrompt: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showUnlockPrompt,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.78f),
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onUnlock)
                .testTag("video_player_unlock_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = "Unlock screen",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Screen locked • Tap to unlock",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
internal fun VideoSettingsModalSheet(
    page: VideoSettingsSheetPage?,
    qualityLabel: String,
    aspectRatioMode: VideoAspectRatioMode,
    playbackSpeed: Float,
    captionsLabel: String,
    captionOptions: List<VideoTrackOption>,
    audioTrackLabel: String,
    audioOptions: List<VideoTrackOption>,
    loopVideo: Boolean,
    ambientMode: Boolean,
    stableVolume: Boolean,
    sleepTimerLabel: String,
    activeSleepTimer: VideoSleepTimerOption,
    onNavigate: (VideoSettingsSheetPage) -> Unit,
    onSelectAspectRatio: (VideoAspectRatioMode) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectCaption: (VideoTrackOption?) -> Unit,
    onSelectAudioTrack: (VideoTrackOption) -> Unit,
    onLockScreen: () -> Unit,
    onToggleLoopVideo: (Boolean) -> Unit,
    onToggleAmbientMode: (Boolean) -> Unit,
    onToggleStableVolume: (Boolean) -> Unit,
    onSelectSleepTimer: (VideoSleepTimerOption) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = page != null,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .safeDrawingPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = YouTubeSheetBg,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 380.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 360.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("video_settings_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 10.dp, bottom = 10.dp)
                ) {
                    // Subtle YouTube top drag handle pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.30f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (page) {
                        VideoSettingsSheetPage.MAIN -> {
                            SettingsNavRow(
                                icon = Icons.Rounded.Tune,
                                title = "Display & quality",
                                trailingValue = "${aspectRatioMode.label} ($qualityLabel)",
                                testTag = "video_setting_quality",
                                onClick = { onNavigate(VideoSettingsSheetPage.ASPECT_RATIO) }
                            )
                            SettingsNavRow(
                                icon = Icons.Rounded.SlowMotionVideo,
                                title = "Playback speed",
                                trailingValue = formatYouTubeSpeed(playbackSpeed),
                                testTag = "video_setting_speed",
                                onClick = { onNavigate(VideoSettingsSheetPage.SPEED) }
                            )
                            SettingsNavRow(
                                icon = Icons.Rounded.ClosedCaption,
                                title = "Captions",
                                trailingValue = captionsLabel,
                                testTag = "video_setting_captions",
                                onClick = { onNavigate(VideoSettingsSheetPage.CAPTIONS) }
                            )
                            SettingsNavRow(
                                icon = Icons.Rounded.RecordVoiceOver,
                                title = "Audio track",
                                trailingValue = audioTrackLabel,
                                testTag = "video_setting_audio_track",
                                onClick = { onNavigate(VideoSettingsSheetPage.AUDIO_TRACK) }
                            )
                            SettingsActionRow(
                                icon = Icons.Rounded.Lock,
                                title = "Lock screen",
                                testTag = "video_setting_lock_screen",
                                onClick = {
                                    onDismiss()
                                    onLockScreen()
                                }
                            )
                            SettingsNavRow(
                                icon = Icons.Rounded.Settings,
                                title = "More",
                                trailingValue = "",
                                testTag = "video_setting_more",
                                onClick = { onNavigate(VideoSettingsSheetPage.MORE) }
                            )
                        }

                        VideoSettingsSheetPage.MORE -> {
                            SettingsSubHeader(
                                title = "Additional settings",
                                onBack = { onNavigate(VideoSettingsSheetPage.MAIN) }
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.RepeatOne,
                                title = "Loop video",
                                checked = loopVideo,
                                testTag = "video_setting_loop_toggle",
                                onCheckedChange = onToggleLoopVideo
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.Tv,
                                title = "Ambient mode",
                                checked = ambientMode,
                                testTag = "video_setting_ambient_toggle",
                                onCheckedChange = onToggleAmbientMode
                            )
                            SettingsToggleRow(
                                icon = Icons.Rounded.GraphicEq,
                                title = "Stable volume",
                                checked = stableVolume,
                                testTag = "video_setting_stable_volume_toggle",
                                onCheckedChange = onToggleStableVolume
                            )
                            SettingsNavRow(
                                icon = Icons.Rounded.Bedtime,
                                title = "Sleep timer",
                                trailingValue = sleepTimerLabel,
                                testTag = "video_setting_sleep_timer",
                                onClick = { onNavigate(VideoSettingsSheetPage.SLEEP_TIMER) }
                            )
                        }

                        VideoSettingsSheetPage.SPEED -> {
                            SettingsSubHeader(
                                title = "Playback speed",
                                onBack = { onNavigate(VideoSettingsSheetPage.MAIN) }
                            )
                            YOUTUBE_PLAYBACK_SPEEDS.forEach { speed ->
                                val selected = abs(playbackSpeed - speed) < 0.01f
                                SettingsCheckRow(
                                    label = if (abs(speed - 1.0f) < 0.01f) "Normal (1x)" else "${formatYouTubeSpeed(speed)}",
                                    selected = selected,
                                    onClick = {
                                        onSelectSpeed(speed)
                                        onDismiss()
                                    }
                                )
                            }
                        }

                        VideoSettingsSheetPage.ASPECT_RATIO -> {
                            SettingsSubHeader(
                                title = "Display & aspect ratio ($qualityLabel)",
                                onBack = { onNavigate(VideoSettingsSheetPage.MAIN) }
                            )
                            VideoAspectRatioMode.entries.forEach { mode ->
                                SettingsCheckRow(
                                    label = when (mode) {
                                        VideoAspectRatioMode.FIT -> "Fit to screen"
                                        VideoAspectRatioMode.FILL -> "Stretch to fill"
                                        VideoAspectRatioMode.ZOOM -> "Zoom to fill"
                                    },
                                    selected = aspectRatioMode == mode,
                                    onClick = {
                                        onSelectAspectRatio(mode)
                                        onDismiss()
                                    }
                                )
                            }
                        }

                        VideoSettingsSheetPage.CAPTIONS -> {
                            SettingsSubHeader(
                                title = "Captions",
                                onBack = { onNavigate(VideoSettingsSheetPage.MAIN) }
                            )
                            val anySelected = captionOptions.any { it.isSelected }
                            SettingsCheckRow(
                                label = "Off",
                                selected = !anySelected,
                                onClick = {
                                    onSelectCaption(null)
                                    onDismiss()
                                }
                            )
                            if (captionOptions.isEmpty()) {
                                Text(
                                    text = "No embedded subtitle tracks in this video",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.62f),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            } else {
                                captionOptions.forEach { opt ->
                                    SettingsCheckRow(
                                        label = opt.label,
                                        selected = opt.isSelected,
                                        onClick = {
                                            onSelectCaption(opt)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }

                        VideoSettingsSheetPage.AUDIO_TRACK -> {
                            SettingsSubHeader(
                                title = "Audio track",
                                onBack = { onNavigate(VideoSettingsSheetPage.MAIN) }
                            )
                            if (audioOptions.isEmpty()) {
                                Text(
                                    text = "Default audio track",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                            } else {
                                audioOptions.forEach { opt ->
                                    SettingsCheckRow(
                                        label = opt.label,
                                        selected = opt.isSelected,
                                        onClick = {
                                            onSelectAudioTrack(opt)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }

                        VideoSettingsSheetPage.SLEEP_TIMER -> {
                            SettingsSubHeader(
                                title = "Sleep timer",
                                onBack = { onNavigate(VideoSettingsSheetPage.MORE) }
                            )
                            VideoSleepTimerOption.entries.forEach { opt ->
                                SettingsCheckRow(
                                    label = opt.label,
                                    selected = activeSleepTimer == opt,
                                    onClick = {
                                        onSelectSleepTimer(opt)
                                        onDismiss()
                                    }
                                )
                            }
                        }

                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSubHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.10f),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    trailingValue: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingValue.isNotBlank()) {
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1F1F1F),
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0xFFB5B5B5),
                uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.35f)
            )
        )
    }
}

@Composable
private fun SettingsCheckRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.84f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun MoreVideosOverlay(
    visible: Boolean,
    videos: List<MediaTrack>,
    currentTrackId: String,
    onSelectVideo: (MediaTrack) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(180)),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.86f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("video_more_videos_overlay")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(vertical = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "More videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("video_more_videos_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close more videos",
                            tint = Color.White
                        )
                    }
                }

                val otherVideos = remember(videos, currentTrackId) {
                    videos.filter { it.id != currentTrackId }
                }

                if (otherVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No other videos in your library yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(otherVideos, key = { it.id }) { item ->
                            MoreVideoCard(
                                video = item,
                                onClick = {
                                    onSelectVideo(item)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreVideoCard(
    video: MediaTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(236.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("more_video_card_${video.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            AsyncImage(
                model = video.artworkUriString?.takeIf { it.isNotBlank() } ?: video.uriString,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_cover_horizon_1794995219984),
                error = painterResource(id = R.drawable.img_cover_horizon_1794995219984),
                modifier = Modifier.fillMaxSize()
            )
            if (video.durationMs > 0L) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.80f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val subtitleParts = buildList {
            val folderOrArtist = video.artist.takeIf { it.isNotBlank() && it != "Unknown Artist" }
                ?: video.folderPath.substringAfterLast('/').takeIf { it.isNotBlank() }
                ?: "Local Video"
            add(folderOrArtist)
            video.formatBadges.firstOrNull()?.let { add(it) }
        }
        Text(
            text = subtitleParts.joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatYouTubeSpeed(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}x"
    } else {
        "${speed.toString().trimEnd('0').trimEnd('.')}x"
    }
}
