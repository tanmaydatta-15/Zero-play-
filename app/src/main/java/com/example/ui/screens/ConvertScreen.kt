package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ConversionJobUi
import com.example.data.model.ConversionType
import com.example.data.model.DownloadTaskUi
import com.example.data.model.JobStatus
import com.example.ui.EchoTopSheetType
import com.example.ui.components.EchoQuickAccessCard
import com.example.ui.components.EchoTopBar
import com.example.ui.components.formatBytes
import com.example.ui.components.groupedCardShape
import kotlin.math.roundToInt

@Composable
fun ConvertScreen(
    conversionJobs: List<ConversionJobUi>,
    downloads: List<DownloadTaskUi>,
    onOpenPhotoConverter: () -> Unit,
    onOpenVideoCompressor: () -> Unit,
    onOpenAudioExtractor: () -> Unit,
    onOpenDownloader: () -> Unit,
    onOpenCodecDiagnostics: () -> Unit = {},
    onRetryDownload: (DownloadTaskUi) -> Unit = {},
    onDismissDownload: (String) -> Unit = {},
    onTopBarAction: (EchoTopSheetType) -> Unit,
    onLogoClick: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var jobForComparison by remember { mutableStateOf<ConversionJobUi?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("convert_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            EchoTopBar(
                title = stringResource(R.string.convert_title),
                onActionClick = onTopBarAction,
                onLogoClick = onLogoClick,
                onMenuClick = onOpenDrawer
            )
        }

        // 2-Column Quick-Access Cards styled identically to Library Screen
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EchoQuickAccessCard(
                        title = "Photo Compress",
                        subtitle = "JPEG • PNG • WebP",
                        icon = Icons.Rounded.Image,
                        onClick = onOpenPhotoConverter,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("convert_card_photo")
                    )
                    EchoQuickAccessCard(
                        title = "Video Convert",
                        subtitle = "AV1 • HEVC • MKV",
                        icon = Icons.Rounded.Videocam,
                        onClick = onOpenVideoCompressor,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("convert_card_video")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EchoQuickAccessCard(
                        title = "Audio Convert",
                        subtitle = "Opus • MP3 • FLAC",
                        icon = Icons.Rounded.AudioFile,
                        onClick = onOpenAudioExtractor,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("convert_card_audio")
                    )
                    EchoQuickAccessCard(
                        title = "URL Download",
                        subtitle = "4× Parallel • yt-dlp",
                        icon = Icons.Rounded.Download,
                        onClick = onOpenDownloader,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("convert_card_download")
                    )
                }
                EchoQuickAccessCard(
                    title = "Hardware Codec Diagnostics",
                    subtitle = "Query MediaCodecList for AV1 • VP9 • HEVC • H.264",
                    icon = Icons.Rounded.DeveloperBoard,
                    onClick = onOpenCodecDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("convert_card_codec_diagnostics")
                )
            }
        }

        // Active & Completed Downloads
        if (downloads.isNotEmpty()) {
            item {
                Text(
                    text = "DOWNLOAD QUEUE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    downloads.forEachIndexed { index, task ->
                        DownloadProgressRow(
                            task = task,
                            index = index,
                            totalCount = downloads.size,
                            onRetry = { onRetryDownload(task) },
                            onDismiss = { onDismissDownload(task.id) }
                        )
                    }
                }
            }
        }

        // Conversion Progress & Export History
        item {
            Text(
                text = "CONVERSION PROGRESS & EXPORTED",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        item {
            if (conversionJobs.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Transform,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Select Photo Compress, Video Compress, or Extract Audio above to begin. Completed photo jobs support interactive Before/After split-screen comparison.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    conversionJobs.forEachIndexed { index, job ->
                        ConversionJobRow(
                            job = job,
                            index = index,
                            totalCount = conversionJobs.size,
                            onCompareClick = {
                                if (job.status == JobStatus.COMPLETED && !job.inputUriString.isNullOrBlank() && !job.outputUriString.isNullOrBlank()) {
                                    jobForComparison = job
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    jobForComparison?.let { job ->
        BeforeAfterComparisonDialog(
            job = job,
            onDismiss = { jobForComparison = null }
        )
    }
}

@Composable
private fun ConversionJobRow(
    job: ConversionJobUi,
    index: Int,
    totalCount: Int,
    onCompareClick: () -> Unit
) {
    val canCompare = job.status == JobStatus.COMPLETED &&
        job.type == ConversionType.PHOTO_COMPRESS &&
        !job.inputUriString.isNullOrBlank() &&
        !job.outputUriString.isNullOrBlank()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupedCardShape(index, totalCount))
            .clickable(enabled = canCompare, onClick = onCompareClick),
        shape = groupedCardShape(index, totalCount),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (job.type) {
                        ConversionType.PHOTO_COMPRESS -> Icons.Rounded.Collections
                        ConversionType.VIDEO_COMPRESS -> Icons.Rounded.Videocam
                        ConversionType.AUDIO_EXTRACT -> Icons.Rounded.AudioFile
                    },
                    contentDescription = job.type.label,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeSummary = if (job.status == JobStatus.COMPLETED && job.sizeAfterBytes > 0L) {
                    val before = if (job.sizeBeforeBytes > 0L) "${formatBytes(job.sizeBeforeBytes)} -> " else ""
                    val pct = if (job.sizeBeforeBytes > job.sizeAfterBytes) {
                        val saved = (((job.sizeBeforeBytes - job.sizeAfterBytes).toDouble() / job.sizeBeforeBytes) * 100.0).roundToInt()
                        " (-$saved%)"
                    } else ""
                    "$before${formatBytes(job.sizeAfterBytes)}$pct • ${job.detailLabel}"
                } else {
                    job.errorMessage ?: job.detailLabel
                }
                Text(
                    text = sizeSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (job.status == JobStatus.RUNNING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                canCompare -> Icon(
                    imageVector = Icons.Rounded.Compare,
                    contentDescription = "Before and after compare",
                    tint = MaterialTheme.colorScheme.secondary
                )
                job.status == JobStatus.COMPLETED -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.secondary
                )
                job.status == JobStatus.FAILED -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error
                )
                else -> Text(
                    text = "${(job.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BeforeAfterComparisonDialog(
    job: ConversionJobUi,
    onDismiss: () -> Unit
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Before vs After Comparison",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${formatBytes(job.sizeBeforeBytes)} -> ${formatBytes(job.sizeAfterBytes)} (${job.detailLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    val fullWidthPx = constraints.maxWidth.toFloat()
                    // Compressed ("After") full background
                    AsyncImage(
                        model = job.outputUriString,
                        contentDescription = "Compressed output",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Original ("Before") clipped on the left
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(splitFraction.coerceIn(0.05f, 0.95f))
                            .clip(RectangleShape)
                            .clipToBounds()
                    ) {
                        AsyncImage(
                            model = job.inputUriString,
                            contentDescription = "Original input",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Divider line
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((fullWidthPx * splitFraction).roundToInt(), 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.7f)) {
                            Text("BEFORE", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.7f)) {
                            Text("AFTER", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                Slider(
                    value = splitFraction,
                    onValueChange = { splitFraction = it },
                    valueRange = 0.05f..0.95f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DownloadProgressRow(
    task: DownloadTaskUi,
    index: Int,
    totalCount: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedCardShape(index, totalCount),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = if (task.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    task.engineLabel?.takeIf { it.isNotBlank() }?.let { engine ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = engine,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    task.formatBadge?.takeIf { it.isNotBlank() }?.let { badge ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                val liveSubtitle = when (task.status) {
                    JobStatus.FAILED -> task.errorMessage ?: "Download failed"
                    JobStatus.RUNNING -> buildList {
                        add("${(task.progress * 100).roundToInt()}%")
                        task.speedLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                        task.etaLabel?.takeIf { it.isNotBlank() }?.let { add("ETA $it") }
                    }.joinToString(" • ").ifBlank { task.preset.label }
                    JobStatus.QUEUED -> "Queued • ${task.preset.label}"
                    JobStatus.COMPLETED -> "Saved to Library • ${task.preset.label}"
                }
                Text(
                    text = liveSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.status == JobStatus.RUNNING || task.status == JobStatus.QUEUED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { task.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            if (task.status == JobStatus.FAILED) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("download_retry_btn_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Retry download",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            } else if (task.status == JobStatus.COMPLETED) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("download_dismiss_btn_${task.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = if (task.status == JobStatus.RUNNING) "Cancel download" else "Dismiss download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
