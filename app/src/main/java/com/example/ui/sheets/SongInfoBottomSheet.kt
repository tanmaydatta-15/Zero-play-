package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaTrack
import com.example.metadata.AudioMetadataEngine
import com.example.metadata.SongDetailedMetadata
import com.example.ui.components.TrackArtwork
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoBottomSheet(
    track: MediaTrack,
    onEditTags: (SongDetailedMetadata?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var details by remember(track.id, track.dateModified) { mutableStateOf<SongDetailedMetadata?>(null) }
    var isLoading by remember(track.id, track.dateModified) { mutableStateOf(true) }

    LaunchedEffect(track.id, track.dateModified) {
        isLoading = true
        details = AudioMetadataEngine.inspectTrack(context, track)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("song_info_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header with cover thumbnail, file name & storage location
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    track = track,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    cornerRadius = 14.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Song info",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = details?.fileName ?: track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = details?.storageLocation ?: track.folderPath.ifBlank { "Internal Storage" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (isLoading && details == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                }
            } else {
                val info = details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Metadata tags section
                    SongInfoSectionCard(
                        icon = Icons.Rounded.Tag,
                        title = "Metadata tags"
                    ) {
                        val t = info?.tags
                        InfoSpecRow("Title", t?.title.orEmpty().ifBlank { track.title })
                        InfoSpecRow("Album", t?.album.orEmpty().ifBlank { track.album })
                        InfoSpecRow("Artist", t?.artist.orEmpty().ifBlank { track.artist })
                        InfoSpecRow("Album-Artist", t?.albumArtist.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Composer", t?.composer.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Genre", t?.genre.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Lyricist", t?.lyricist.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Track number", t?.trackNumber.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Disc number", t?.discNumber.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Year", t?.year.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Comment", t?.comment.orEmpty().ifBlank { "—" })
                    }

                    // Technical audio specs section
                    SongInfoSectionCard(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Technical audio specs"
                    ) {
                        InfoSpecRow("Duration", formatDuration(info?.durationMs ?: track.durationMs))
                        InfoSpecRow("Bitrate", info?.bitrateKbps?.let { "$it kbps" } ?: "—")
                        InfoSpecRow("Sample rate", info?.sampleRateHz?.let { "$it Hz" } ?: "—")
                        InfoSpecRow("Bits per sample", info?.bitsPerSample?.let { "$it-bit" } ?: "—")
                        InfoSpecRow("Format", info?.formatLabel.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Encoding", info?.encodingLabel.orEmpty().ifBlank { "—" })
                        InfoSpecRow("Channels", info?.channelsLabel.orEmpty().ifBlank { "Stereo" })
                        InfoSpecRow("Size", formatBytes(info?.sizeBytes ?: track.sizeBytes))
                    }

                    // Playback history section
                    SongInfoSectionCard(
                        icon = Icons.Rounded.History,
                        title = "Playback history"
                    ) {
                        InfoSpecRow("Date added to library", formatTimestamp(info?.dateAddedMs ?: track.dateAdded))
                        InfoSpecRow("Date modified", formatTimestamp(info?.dateModifiedMs ?: track.dateModified))
                        InfoSpecRow(
                            "Date last played",
                            if ((info?.dateLastPlayedMs ?: track.lastPlayedAt) > 0L) {
                                formatTimestamp(info?.dateLastPlayedMs ?: track.lastPlayedAt)
                            } else "Never played"
                        )
                        InfoSpecRow("Played count", "${info?.playCount ?: track.playCount} plays")
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Bottom EDIT TAGS and DONE action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onEditTags(details) },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("song_info_edit_tags_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EDIT TAGS", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onDismiss,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("song_info_done_btn")
                ) {
                    Text("DONE", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SongInfoSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "—"
    return runCatching {
        SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(ms))
    }.getOrDefault("—")
}
