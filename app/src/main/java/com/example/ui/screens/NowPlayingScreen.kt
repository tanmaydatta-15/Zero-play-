package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaSource
import com.example.data.model.MediaTrack
import com.example.playback.PlaybackUiState
import com.example.ui.components.TrackArtwork
import com.example.ui.components.cleanArtistLabel
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatPlayCount
import com.example.ui.components.formatTrackQualityBadge
import com.example.ui.components.groupedCardShape
import com.example.ui.components.safeDrawableResId
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Cookie9SidedShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = min(cx, cy)
        val steps = 180
        for (i in 0..steps) {
            val angle = (2.0 * PI * i) / steps - PI / 2.0
            val r = maxRadius * (0.905f + 0.095f * cos(9.0 * angle).toFloat())
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    isFullBleedMode: Boolean,
    playerBlurBackground: Boolean = true,
    showQualityBadge: Boolean = true,
    showSongDnaBadge: Boolean = true,
    wordByWordLyrics: Boolean = true,
    onCollapse: () -> Unit,
    onToggleFullBleed: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: (MediaTrack) -> Unit,
    onDownloadTrack: (MediaTrack) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onConvertOrExtract: (MediaTrack) -> Unit,
    onAddToPlaylist: (MediaTrack) -> Unit,
    onSelectQueueTrack: (MediaTrack) -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenSongInfo: (MediaTrack) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val track = playbackState.currentTrack ?: return
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    var showSongDnaSheet by remember { mutableStateOf(false) }

    // Always force crisp white status & nav bar icons on top of the dark Now Playing backdrop,
    // even when the app is in Light Mode, and restore previous light/dark flags when collapsed.
    DisposableEffect(view, context) {
        val window = context.findActivityOrNull()?.window
        val controller = if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view)
        } else null
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevLightNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = prevLightStatus
            controller?.isAppearanceLightNavigationBars = prevLightNav
        }
    }

    BackHandler {
        onCollapse()
    }

    // 3 Zero Play Music Pages: Song, Lyrics (word-by-word), Music Flow (Queue)
    val pagerState = rememberPagerState(pageCount = { 3 })
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E))
            .testTag("now_playing_screen")
    ) {
        // Blurred Album Artwork Background
        val safeArtId = safeDrawableResId(track.artworkResId, R.drawable.img_cover_midnight_1794995218900)
        if (playerBlurBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
            ) {
                if (!track.artworkUriString.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUriString,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = safeArtId),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = safeArtId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Dark Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.72f),
                            Color(0xFF09090D).copy(alpha = 0.94f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Header Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("now_playing_collapse_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Mode Switcher Pill (Song | Lyrics | Flow)
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        val labels = listOf("Song", "Lyrics", "Flow")
                        labels.forEachIndexed { pageIndex, label ->
                            val selected = pagerState.currentPage == pageIndex
                            Surface(
                                shape = CircleShape,
                                color = if (selected) Color.White else Color.Transparent,
                                modifier = Modifier.clickable {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex) }
                                }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onToggleFullBleed,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("now_playing_fullbleed_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Toggle full-bleed art",
                        tint = Color.White
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PlayerMainPage(
                        track = track,
                        playbackState = playbackState,
                        isFullBleedMode = isFullBleedMode,
                        showQualityBadge = showQualityBadge,
                        showSongDnaBadge = showSongDnaBadge,
                        speedMenuExpanded = speedMenuExpanded,
                        moreMenuExpanded = moreMenuExpanded,
                        onSpeedMenuExpandedChange = { speedMenuExpanded = it },
                        onMoreMenuExpandedChange = { moreMenuExpanded = it },
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekTo = onSeekTo,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onToggleLike = { onToggleLike(track) },
                        onDownloadTrack = { onDownloadTrack(track) },
                        onSetSpeed = onSetSpeed,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onOpenLyrics = { scope.launch { pagerState.animateScrollToPage(1) } },
                        onOpenSongDna = { showSongDnaSheet = true },
                        onConvertOrExtract = { onConvertOrExtract(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onOpenEqualizer = onOpenEqualizer,
                        onOpenSongInfo = { onOpenSongInfo(track) },
                        onOpenSettings = onOpenSettings
                    )

                    1 -> SynchronizedLyricsPage(
                        track = track,
                        playbackState = playbackState,
                        wordByWordLyrics = wordByWordLyrics,
                        onSeekTo = onSeekTo,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onExpandPlayer = { scope.launch { pagerState.animateScrollToPage(0) } },
                        onConvertOrExtract = { onConvertOrExtract(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )

                    else -> MusicFlowQueuePage(
                        currentTrack = track,
                        queue = playbackState.queue,
                        onSelectTrack = onSelectQueueTrack,
                        onOpenSongDna = { showSongDnaSheet = true }
                    )
                }
            }
        }
    }

    if (showSongDnaSheet) {
        SongDnaBottomSheet(
            track = track,
            onDismiss = { showSongDnaSheet = false }
        )
    }
}

@Composable
private fun PlayerMainPage(
    track: MediaTrack,
    playbackState: PlaybackUiState,
    isFullBleedMode: Boolean,
    showQualityBadge: Boolean,
    showSongDnaBadge: Boolean,
    speedMenuExpanded: Boolean,
    moreMenuExpanded: Boolean,
    onSpeedMenuExpandedChange: (Boolean) -> Unit,
    onMoreMenuExpandedChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    onDownloadTrack: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSongDna: () -> Unit,
    onConvertOrExtract: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSongInfo: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isFullBleedMode) 0.dp else 24.dp,
                vertical = 8.dp
            ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Album Artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (isFullBleedMode) {
                val safeFullBleedArt = safeDrawableResId(track.artworkResId, R.drawable.img_cover_midnight_1794995218900)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.95f)
                ) {
                    if (!track.artworkUriString.isNullOrBlank()) {
                        AsyncImage(
                            model = track.artworkUriString,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = safeFullBleedArt),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = safeFullBleedArt),
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                }
            } else {
                TrackArtwork(
                    track = track,
                    cornerRadius = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isFullBleedMode) 24.dp else 0.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Format Badge & Song DNA Pill
            if (showQualityBadge || showSongDnaBadge) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showQualityBadge) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = formatTrackQualityBadge(track),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (showSongDnaBadge) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenSongDna)
                                .testTag("song_dna_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Song DNA",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Song DNA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title + Artist on left, circular Download + Heart on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanArtistLabel(track),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDownloadTrack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("now_playing_download_btn")
                ) {
                    Icon(
                        imageVector = if (track.source == MediaSource.DOWNLOADED) Icons.Rounded.Check else Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.action_download),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("now_playing_like_btn")
                ) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_like),
                        tint = if (track.isLiked) Color(0xFFFF6B86) else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Slider + Timestamps
            PlaybackProgressSlider(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Transport Row with 9-Sided Scalloped Cookie Play/Pause Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffleEnabled) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("now_playing_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.action_skip_previous),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(Cookie9SidedShape)
                        .background(Color.White)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("now_playing_cookie_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_play_pause),
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("now_playing_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.action_skip_next),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (playbackState.repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Controls Row (Speed, Lyrics, More / Convert)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { onSpeedMenuExpandedChange(true) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = "Playback speed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${playbackState.playbackSpeed}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { onSpeedMenuExpandedChange(false) }
                    ) {
                        listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            DropdownMenuItem(
                                text = { Text("${spd}x") },
                                onClick = {
                                    onSetSpeed(spd)
                                    onSpeedMenuExpandedChange(false)
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onOpenLyrics) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Lyrics",
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }

                Box {
                    IconButton(onClick = { onMoreMenuExpandedChange(true) }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.action_more_options),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { onMoreMenuExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Song info") },
                            leadingIcon = { Icon(Icons.Rounded.Info, null) },
                            onClick = {
                                onMoreMenuExpandedChange(false)
                                onOpenSongInfo()
                            },
                            modifier = Modifier.testTag("now_playing_menu_song_info")
                        )
                        DropdownMenuItem(
                            text = { Text("Equalizer") },
                            leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                            onClick = {
                                onMoreMenuExpandedChange(false)
                                onOpenEqualizer()
                            },
                            modifier = Modifier.testTag("now_playing_menu_equalizer")
                        )
                        DropdownMenuItem(
                            text = { Text("Song DNA") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                            onClick = {
                                onMoreMenuExpandedChange(false)
                                onOpenSongDna()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Convert / Extract Audio") },
                            leadingIcon = { Icon(Icons.Rounded.Transform, null) },
                            onClick = {
                                onMoreMenuExpandedChange(false)
                                onConvertOrExtract()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = {
                                onMoreMenuExpandedChange(false)
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                            onClick = {
                                onMoreMenuExpandedChange(false)
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SynchronizedLyricsPage(
    track: MediaTrack,
    playbackState: PlaybackUiState,
    wordByWordLyrics: Boolean,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpandPlayer: () -> Unit,
    onConvertOrExtract: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val lyrics = track.lyrics
    val activeIndex = lyrics.indexOfLast { it.timeMs <= playbackState.positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty()) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .testTag("synchronized_lyrics_page")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrent = index == activeIndex
                val lineEndMs = lyrics.getOrNull(index + 1)?.timeMs ?: playbackState.durationMs.coerceAtLeast(line.timeMs + 3500L)
                val lineSpan = (lineEndMs - line.timeMs).coerceAtLeast(500L)
                val wordProgress = if (isCurrent) {
                    ((playbackState.positionMs - line.timeMs).toFloat() / lineSpan.toFloat()).coerceIn(0f, 1f)
                } else 0f

                val annotated = if (isCurrent && wordByWordLyrics) {
                    val words = line.text.split(" ")
                    val highlightedCount = ((words.size * wordProgress) + 1).toInt().coerceIn(1, words.size)
                    buildAnnotatedString {
                        words.forEachIndexed { wIdx, word ->
                            withStyle(
                                SpanStyle(
                                    color = if (wIdx < highlightedCount) Color.White else Color.White.copy(alpha = 0.58f),
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(word)
                            }
                            if (wIdx < words.lastIndex) append(" ")
                        }
                    }
                } else if (isCurrent) {
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(line.text)
                        }
                    }
                } else {
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = Color.White.copy(alpha = 0.40f),
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(line.text)
                        }
                    }
                }

                Text(
                    text = annotated,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = if (isCurrent) 24.sp else 20.sp,
                        lineHeight = 30.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSeekTo(line.timeMs) }
                        .padding(vertical = 4.dp)
                )
            }
        }

        // Floating rounded mini-player card right above the progress slider
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.14f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    track = track,
                    cornerRadius = 10.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = cleanArtistLabel(track),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onExpandPlayer,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "Show Player",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.action_more_options),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Convert / Extract Audio") },
                            onClick = {
                                menuExpanded = false
                                onConvertOrExtract()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                    }
                }
            }
        }

        PlaybackProgressSlider(
            positionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            onSeekTo = onSeekTo
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(Cookie9SidedShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play_pause),
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun MusicFlowQueuePage(
    currentTrack: MediaTrack,
    queue: List<MediaTrack>,
    onSelectTrack: (MediaTrack) -> Unit,
    onOpenSongDna: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("music_flow_page")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Music Flow Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${queue.size} tracks in continuous flow",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSongDna)
            ) {
                Text(
                    text = "Song DNA",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            itemsIndexed(queue) { index, item ->
                val isCurrent = item.id == currentTrack.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(groupedCardShape(index, queue.size))
                        .clickable { onSelectTrack(item) },
                    shape = groupedCardShape(index, queue.size),
                    color = if (isCurrent) {
                        Color.White.copy(alpha = 0.22f)
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(
                            track = item,
                            cornerRadius = 10.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${cleanArtistLabel(item)} • ${formatDuration(item.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Now playing",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongDnaBottomSheet(
    track: MediaTrack,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("song_dna_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Song DNA & Audio Specs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${track.title} — ${cleanArtistLabel(track)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            val rows = listOf(
                "Album / Collection" to track.album,
                "Mood Profile" to track.moodTag,
                "Audio / Video Codec" to "${formatTrackQualityBadge(track)} (${track.mimeType})",
                "Source Category" to track.source.name.lowercase().replaceFirstChar { it.uppercase() },
                "Total Plays" to formatPlayCount(track.playCount),
                "File Size" to if (track.sizeBytes > 0L) formatBytes(track.sizeBytes) else "Stream / Local",
                "Track Story" to "Mastered for Zero Play with dynamic range preservation and synchronized lyric timing."
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEachIndexed { idx, (label, value) ->
                    Surface(
                        shape = groupedCardShape(idx, rows.size),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackProgressSlider(
    positionMs: Long,
    durationMs: Long,
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = {
                isDragging = true
                dragFraction = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeekTo((dragFraction * safeDuration).toLong())
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
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
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(activeFraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("now_playing_slider")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(if (isDragging) (dragFraction * safeDuration).toLong() else positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
