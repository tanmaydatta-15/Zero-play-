package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MediaSource
import com.example.data.model.MediaTrack
import com.example.ui.EchoTopSheetType
import com.example.ui.components.EchoQuickAccessCard
import com.example.ui.components.EchoTopBar
import com.example.ui.components.GroupedTrackRow
import com.example.ui.components.SectionHeaderWithPlayAll

@Composable
fun HomeScreen(
    tracks: List<MediaTrack>,
    currentTrackId: String?,
    onTopBarAction: (EchoTopSheetType) -> Unit,
    onLogoClick: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenDownloader: () -> Unit = {},
    onRescanMedia: () -> Unit = {},
    onOpenCodecDiagnostics: () -> Unit = {},
    onPlayTrack: (MediaTrack, List<MediaTrack>) -> Unit,
    onPlayNext: (MediaTrack) -> Unit,
    onAddToPlaylist: (MediaTrack) -> Unit,
    onToggleLike: (MediaTrack) -> Unit,
    onConvertOrExtract: (MediaTrack) -> Unit,
    onShowSongInfo: (MediaTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val downloadedAndExported = tracks.filter {
        it.source == MediaSource.DOWNLOADED || it.source == MediaSource.EXPORTED
    }
    val localTracks = tracks.filter {
        it.source != MediaSource.DOWNLOADED && it.source != MediaSource.EXPORTED
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            EchoTopBar(
                title = stringResource(R.string.app_name),
                onActionClick = onTopBarAction,
                onLogoClick = onLogoClick,
                onMenuClick = onOpenDrawer
            )
        }

        // Quick-Action Strip: Fast URL Download + Live Codec Diagnostics
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EchoQuickAccessCard(
                    title = "Fast Download",
                    subtitle = "Parallel 8× • AV1 • MKV",
                    icon = Icons.Rounded.Bolt,
                    onClick = onOpenDownloader,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_download_card")
                )
                EchoQuickAccessCard(
                    title = "Codec Diagnostics",
                    subtitle = "AV1 • VP9 • HEVC • H.264",
                    icon = Icons.Rounded.DeveloperBoard,
                    onClick = onOpenCodecDiagnostics,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_codecs_card")
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (tracks.isEmpty()) {
            item {
                EmptyHomeWorkspaceCard(
                    onOpenDownloader = onOpenDownloader,
                    onRescanMedia = onRescanMedia,
                    onOpenCodecDiagnostics = onOpenCodecDiagnostics
                )
            }
        } else {
            if (downloadedAndExported.isNotEmpty()) {
                item {
                    SectionHeaderWithPlayAll(
                        title = "DOWNLOADS & EXPORTS",
                        onPlayAll = {
                            downloadedAndExported.firstOrNull()?.let { onPlayTrack(it, downloadedAndExported) }
                        }
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val preview = downloadedAndExported.take(8)
                        preview.forEachIndexed { index, track ->
                            GroupedTrackRow(
                                track = track,
                                index = index,
                                totalCount = preview.size,
                                isActive = track.id == currentTrackId,
                                onClick = { onPlayTrack(track, downloadedAndExported) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onToggleLike = { onToggleLike(track) },
                                onConvertOrExtract = { onConvertOrExtract(track) },
                                onSongInfo = { onShowSongInfo(track) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            if (localTracks.isNotEmpty()) {
                item {
                    SectionHeaderWithPlayAll(
                        title = "DEVICE MEDIA",
                        onPlayAll = {
                            localTracks.firstOrNull()?.let { onPlayTrack(it, localTracks) }
                        }
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val preview = localTracks.take(12)
                        preview.forEachIndexed { index, track ->
                            GroupedTrackRow(
                                track = track,
                                index = index,
                                totalCount = preview.size,
                                isActive = track.id == currentTrackId,
                                onClick = { onPlayTrack(track, localTracks) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onToggleLike = { onToggleLike(track) },
                                onConvertOrExtract = { onConvertOrExtract(track) },
                                onSongInfo = { onShowSongInfo(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeWorkspaceCard(
    onOpenDownloader: () -> Unit,
    onRescanMedia: () -> Unit,
    onOpenCodecDiagnostics: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("home_empty_state"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Clean Slate, Zero Clutter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Download videos & audio at multi-connection speed or scan local files on your device. Only your own media appears here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onOpenDownloader,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("home_empty_download_btn")
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download from URL", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onRescanMedia,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_empty_scan_btn")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Media")
                }
                OutlinedButton(
                    onClick = onOpenCodecDiagnostics,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_empty_codecs_btn")
                ) {
                    Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Codecs")
                }
            }
        }
    }
}
