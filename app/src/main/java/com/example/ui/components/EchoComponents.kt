package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaSource
import com.example.data.model.MediaTrack
import com.example.data.model.MediaType
import com.example.playback.PlaybackUiState
import com.example.ui.EchoTopSheetType
import java.util.Locale

@Composable
fun safeDrawableResId(
    resId: Int?,
    fallback: Int = R.drawable.img_cover_midnight_1794995218900
): Int {
    val context = LocalContext.current
    return remember(resId, fallback) {
        if (resId == null || resId == 0) {
            fallback
        } else {
            val isValid = runCatching {
                context.resources.getResourceTypeName(resId) == "drawable"
            }.getOrDefault(false)
            if (isValid) resId else fallback
        }
    }
}

/**
 * Exact 1:1 Echo Music grouped card shape:
 * - First item: 20.dp top corners, 4.dp bottom corners
 * - Middle items: 4.dp corners
 * - Last item: 4.dp top corners, 20.dp bottom corners
 * - Single item: 20.dp all corners
 */
fun groupedCardShape(index: Int, totalCount: Int): Shape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 4.dp,
            bottomEnd = 4.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 4.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )
        else -> RoundedCornerShape(4.dp)
    }
}

@Composable
fun EchoTopBar(
    title: String,
    onActionClick: (EchoTopSheetType) -> Unit,
    onLogoClick: () -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onMenuClick != null) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("topbar_drawer_menu_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open navigation menu",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F4FF))
                .clickable(onClick = onLogoClick)
                .testTag("topbar_zero_play_logo"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_zero_play_logo),
                contentDescription = "About Zero Play",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = { onActionClick(EchoTopSheetType.CODECS) },
            modifier = Modifier.testTag("topbar_codecs_btn")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeveloperBoard,
                contentDescription = "Codec Diagnostics",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(
            onClick = { onActionClick(EchoTopSheetType.DOWNLOAD) },
            modifier = Modifier.testTag("topbar_download_btn")
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.action_download),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(
            onClick = { onActionClick(EchoTopSheetType.HISTORY) },
            modifier = Modifier.testTag("topbar_history_btn")
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.action_history),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable { onActionClick(EchoTopSheetType.PROFILE) }
                .padding(6.dp)
                .testTag("topbar_profile_btn"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_cover_aurora_1794995218098),
                contentDescription = stringResource(R.string.action_profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun TrackArtwork(
    track: MediaTrack,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fallbackRes = if (track.mediaType == MediaType.VIDEO) {
        R.drawable.img_cover_horizon_1794995219984
    } else {
        R.drawable.img_cover_midnight_1794995218900
    }
    val resolvedResId = safeDrawableResId(track.artworkResId, fallbackRes)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (!track.artworkUriString.isNullOrBlank()) {
            AsyncImage(
                model = track.artworkUriString,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                error = painterResource(id = resolvedResId),
                placeholder = painterResource(id = resolvedResId),
                modifier = Modifier.fillMaxSize()
            )
        } else if (track.artworkResId != null) {
            Image(
                painter = painterResource(id = resolvedResId),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (track.mediaType == MediaType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.MusicNote,
                contentDescription = track.title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (track.mediaType == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeaderWithPlayAll(
    title: String,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        OutlinedButton(
            onClick = onPlayAll,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier
                .height(34.dp)
                .testTag("play_all_${title.lowercase().replace(" ", "_")}")
        ) {
            Text(
                text = stringResource(R.string.play_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GroupedTrackRow(
    track: MediaTrack,
    index: Int,
    totalCount: Int,
    isActive: Boolean = false,
    leadingRank: Int? = null,
    subtitleOverride: String? = null,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onConvertOrExtract: () -> Unit = {},
    onSongInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupedCardShape(index, totalCount))
            .clickable(onClick = onClick)
            .testTag("track_row_${track.id}"),
        shape = groupedCardShape(index, totalCount),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingRank != null) {
                Text(
                    text = leadingRank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (leadingRank <= 3) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(26.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(contentAlignment = Alignment.Center) {
                TrackArtwork(
                    track = track,
                    cornerRadius = 10.dp,
                    modifier = Modifier.size(48.dp)
                )
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = subtitleOverride ?: cleanArtistLabel(track),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val badges = remember(track.id, track.mimeType, track.uriString, track.artist, track.album) {
                        if (track.source == MediaSource.DOWNLOADED ||
                            track.source == MediaSource.EXPORTED ||
                            track.source == MediaSource.LOCAL
                        ) {
                            track.formatBadges
                        } else {
                            emptyList()
                        }
                    }
                    badges.forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("track_more_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (track.isLiked) "Remove from Liked" else "Add to Liked") },
                        leadingIcon = {
                            Icon(
                                if (track.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleLike()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Song info") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSongInfo()
                        },
                        modifier = Modifier.testTag("track_menu_song_info_${track.id}")
                    )
                    DropdownMenuItem(
                        text = { Text("Convert / Extract Audio") },
                        leadingIcon = {
                            Icon(
                                if (track.mediaType == MediaType.VIDEO) Icons.Rounded.AudioFile else Icons.Rounded.Transform,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onConvertOrExtract()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EchoQuickAccessCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EchoMiniPlayerBar(
    playbackState: PlaybackUiState,
    showProgressBar: Boolean = true,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val pillShape = RoundedCornerShape(26.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(pillShape)
            .clickable(onClick = onExpand)
            .testTag("mini_player_bar"),
        shape = pillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                )
            )
        ),
        tonalElevation = 10.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.98f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = if (showProgressBar) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TrackArtwork(
                        track = track,
                        cornerRadius = 14.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    if (playbackState.isBuffering && !playbackState.isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.48f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(11.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${cleanArtistLabel(track)} • ${formatTrackQualityBadge(track)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Primary Play / Pause inside a sleek tonal circular button
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("mini_player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.action_play_pause),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.action_skip_next),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Close / Dismiss (X) button to stop playback and remove the mini-player pill
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("mini_player_dismiss")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (showProgressBar) {
                // Inset rounded progress track so it aligns gracefully with the pill corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 5.dp)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

fun formatTrackQualityBadge(track: MediaTrack): String {
    val badges = track.formatBadges
    if (badges.size >= 2) {
        return "${badges[0]} • ${badges[1]}"
    }
    val mime = track.mimeType.lowercase()
    val uri = track.uriString.lowercase()
    return when {
        mime.contains("matroska") || uri.endsWith(".mkv") -> "MKV • Matroska"
        mime.contains("webm") || uri.endsWith(".webm") -> "WebM • VP9/Opus"
        mime.contains("opus") || uri.endsWith(".opus") -> "OPUS • 160k"
        track.mediaType == MediaType.VIDEO -> "MP4 • HD"
        mime.contains("flac") || uri.endsWith(".flac") -> "FLAC • Lossless"
        mime.contains("wav") -> "WAV • Studio"
        mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "M4A • AAC"
        else -> "MP3 • 320k"
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", min, sec)
}

fun formatPlayCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM plays", count / 1_000_000f)
        count >= 1_000 -> String.format(Locale.US, "%.0fK plays", count / 1_000f)
        else -> "$count plays"
    }
}

fun cleanArtistLabel(track: MediaTrack): String {
    val raw = track.artist.trim()
    return if (raw.isEmpty() || raw.equals("<unknown>", ignoreCase = true) || raw.equals("unknown", ignoreCase = true)) {
        if (track.mediaType == MediaType.VIDEO) "Zero Play Video" else "Zero Play Artist"
    } else {
        raw
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else String.format(Locale.US, "%.0f KB", kb)
}
