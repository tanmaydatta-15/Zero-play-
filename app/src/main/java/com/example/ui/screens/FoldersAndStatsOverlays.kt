package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaTrack
import com.example.ui.components.GroupedTrackRow
import com.example.ui.components.formatBytes

@Composable
fun FoldersScreen(
    tracks: List<MediaTrack>,
    currentTrackId: String?,
    onPlayTrack: (MediaTrack, List<MediaTrack>) -> Unit,
    onPlayNext: (MediaTrack) -> Unit,
    onToggleLike: (MediaTrack) -> Unit,
    onAddToPlaylist: (MediaTrack) -> Unit,
    onShowSongInfo: (MediaTrack) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    BackHandler {
        if (selectedFolder != null) selectedFolder = null else onBack()
    }

    val groupedFolders = remember(tracks) {
        tracks.groupBy { it.folderPath.trimEnd('/').ifBlank { "Internal Storage" } }
            .entries
            .sortedBy { it.key.lowercase() }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("folders_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (selectedFolder != null) selectedFolder = null else onBack() },
                    modifier = Modifier.testTag("folders_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFolder?.substringAfterLast('/')?.ifBlank { selectedFolder } ?: "Folders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (selectedFolder != null) selectedFolder!! else "${groupedFolders.size} media folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selectedFolder == null) {
                if (groupedFolders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No folders found yet. Tap Scan media from the drawer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(groupedFolders, key = { it.key }) { (folder, folderTracks) ->
                            val totalBytes = folderTracks.sumOf { it.sizeBytes }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolder = folder }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.substringAfterLast('/').ifBlank { folder },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${folderTracks.size} items • ${formatBytes(totalBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val folderTracks = remember(tracks, selectedFolder) {
                    tracks.filter {
                        it.folderPath.trimEnd('/').ifBlank { "Internal Storage" } == selectedFolder
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (folderTracks.isNotEmpty()) {
                        item {
                            FilledTonalButton(
                                onClick = { onPlayTrack(folderTracks.first(), folderTracks) },
                                shape = CircleShape,
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play folder (${folderTracks.size})")
                            }
                        }
                    }
                    itemsIndexed(folderTracks, key = { _, it -> it.id }) { index, track ->
                        GroupedTrackRow(
                            track = track,
                            index = index,
                            totalCount = folderTracks.size,
                            isActive = track.id == currentTrackId,
                            onClick = { onPlayTrack(track, folderTracks) },
                            onPlayNext = { onPlayNext(track) },
                            onToggleLike = { onToggleLike(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onSongInfo = { onShowSongInfo(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ListeningStatsScreen(
    tracks: List<MediaTrack>,
    currentTrackId: String?,
    onPlayTrack: (MediaTrack, List<MediaTrack>) -> Unit,
    onShowSongInfo: (MediaTrack) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val totalPlays = remember(tracks) { tracks.sumOf { it.playCount } }
    val likedCount = remember(tracks) { tracks.count { it.isLiked } }
    val estimatedMinutes = remember(tracks) {
        (tracks.sumOf { (it.playCount.toLong() * it.durationMs.coerceAtLeast(0L)) } / 60_000L).toInt()
    }
    val topTracks = remember(tracks) {
        tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(10)
    }
    val topArtists = remember(tracks) {
        tracks.groupBy { it.artist }
            .map { (artist, list) -> artist to list.sumOf { it.playCount } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("listening_stats_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("listening_stats_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Listening stats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTileCard(
                            icon = Icons.Rounded.Headphones,
                            label = "Total plays",
                            value = totalPlays.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatTileCard(
                            icon = Icons.Rounded.Timer,
                            label = "Minutes",
                            value = estimatedMinutes.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatTileCard(
                            icon = Icons.Rounded.Favorite,
                            label = "Liked",
                            value = likedCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (topArtists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top artists",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(topArtists) { (artist, plays) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$plays plays",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Most played tracks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (topTracks.isEmpty()) {
                    item {
                        Text(
                            text = "Start playing songs from your library to build listening insights.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    itemsIndexed(topTracks, key = { _, it -> it.id }) { index, track ->
                        GroupedTrackRow(
                            track = track,
                            index = index,
                            totalCount = topTracks.size,
                            isActive = track.id == currentTrackId,
                            leadingRank = index + 1,
                            onClick = { onPlayTrack(track, topTracks) },
                            onSongInfo = { onShowSongInfo(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTileCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
