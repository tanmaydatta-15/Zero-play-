package com.example.ui.sheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.converter.PhotoTargetFormat
import com.example.converter.VideoCodecOption
import com.example.converter.VideoCompressionPreset
import com.example.converter.VideoFileMetrics
import com.example.data.local.SealAudioFormatPref
import com.example.data.local.SealAudioQualityPref
import com.example.data.local.SealDownloadModePref
import com.example.data.local.SealPreferredVideoFormatPref
import com.example.data.local.SealVideoContainerPref
import com.example.data.local.SealVideoQualityLimitPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.AudioTargetFormat
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.MediaTrack
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.PlaylistUi
import com.example.data.model.VideoContainerOption
import com.example.diagnostics.DeviceCapabilityInspector
import com.example.ui.EchoTopSheetType
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.groupedCardShape
import kotlin.math.roundToInt

private enum class SealConfigureType(val label: String) {
    AUDIO("Audio"),
    VIDEO("Video"),
    COMMANDS("Commands")
}

private enum class SealFormatSelection {
    AUTO,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaDownloaderSheet(
    initialUrl: String,
    defaultMode: SealDownloadModePref = SealDownloadModePref.VIDEO,
    defaultPreset: DownloadFormatPreset = DownloadFormatPreset.VIDEO_MP4_HIGH,
    defaultPreferredVideoFormat: SealPreferredVideoFormatPref = SealPreferredVideoFormatPref.QUALITY,
    defaultPreferredCodec: VideoCodecPreference = VideoCodecPreference.AUTO,
    defaultVideoContainer: SealVideoContainerPref = SealVideoContainerPref.MP4,
    defaultVideoQualityLimit: SealVideoQualityLimitPref = SealVideoQualityLimitPref.BEST,
    defaultAudioFormat: SealAudioFormatPref = SealAudioFormatPref.NOT_SPECIFIED,
    defaultAudioQuality: SealAudioQualityPref = SealAudioQualityPref.UNLIMITED,
    defaultMp3Bitrate: Mp3BitrateOption = Mp3BitrateOption.KBPS_320,
    defaultConvertAudio: Boolean = false,
    defaultEmbedSubtitles: Boolean = false,
    defaultSaveThumbnail: Boolean = true,
    onSaveConfigurePrefs: (
        downloadMode: SealDownloadModePref,
        preferredVideoFormat: SealPreferredVideoFormatPref,
        preferredCodec: VideoCodecPreference,
        videoContainer: SealVideoContainerPref,
        videoQualityLimit: SealVideoQualityLimitPref,
        audioFormat: SealAudioFormatPref,
        audioQuality: SealAudioQualityPref,
        mp3Bitrate: Mp3BitrateOption,
        convertAudio: Boolean,
        embedSubtitles: Boolean,
        saveThumbnail: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onDismiss: () -> Unit,
    onStartDownload: (
        url: String,
        customTitle: String?,
        preset: DownloadFormatPreset,
        customCommandFlags: String?,
        preferredCodec: VideoCodecPreference?,
        videoContainer: VideoContainerOption?,
        mp3Bitrate: Mp3BitrateOption?
    ) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val av1SoftwareOnly = remember(context) { DeviceCapabilityInspector.isAv1SoftwareOnly(context) }

    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var downloadType by remember(defaultMode) {
        mutableStateOf(
            when (defaultMode) {
                SealDownloadModePref.AUDIO -> SealConfigureType.AUDIO
                SealDownloadModePref.COMMANDS -> SealConfigureType.COMMANDS
                SealDownloadModePref.VIDEO,
                SealDownloadModePref.BATCH -> SealConfigureType.VIDEO
            }
        )
    }
    var formatSelection by remember { mutableStateOf(SealFormatSelection.AUTO) }
    var preferredVideoFormat by remember(defaultPreferredVideoFormat) {
        mutableStateOf(defaultPreferredVideoFormat)
    }
    var preferredCodec by remember(defaultPreferredCodec) {
        mutableStateOf(defaultPreferredCodec)
    }
    var videoContainer by remember(defaultVideoContainer) {
        mutableStateOf(defaultVideoContainer)
    }
    var videoQualityLimit by remember(defaultVideoQualityLimit) {
        mutableStateOf(defaultVideoQualityLimit)
    }
    var audioFormat by remember(defaultAudioFormat) {
        mutableStateOf(defaultAudioFormat)
    }
    var audioQuality by remember(defaultAudioQuality) {
        mutableStateOf(defaultAudioQuality)
    }
    var mp3Bitrate by remember(defaultMp3Bitrate) {
        mutableStateOf(defaultMp3Bitrate)
    }
    var convertAudio by remember(defaultConvertAudio) { mutableStateOf(defaultConvertAudio) }
    var customPreset by remember(defaultPreset) { mutableStateOf(defaultPreset) }

    var downloadPlaylist by remember(defaultMode) {
        mutableStateOf(defaultMode == SealDownloadModePref.BATCH)
    }
    var downloadSubtitles by remember(defaultEmbedSubtitles) {
        mutableStateOf(defaultEmbedSubtitles)
    }
    var saveThumbnail by remember(defaultSaveThumbnail) {
        mutableStateOf(defaultSaveThumbnail)
    }

    var customCommandFlags by remember(
        videoQualityLimit,
        preferredVideoFormat,
        preferredCodec,
        videoContainer,
        audioFormat,
        downloadSubtitles,
        saveThumbnail
    ) {
        mutableStateOf(
            buildString {
                append(
                    if (preferredVideoFormat == SealPreferredVideoFormatPref.LEGACY) {
                        "-f mp4/best "
                    } else {
                        "-f bv*+ba/best "
                    }
                )
                if (videoQualityLimit != SealVideoQualityLimitPref.BEST && videoQualityLimit != SealVideoQualityLimitPref.LOWEST) {
                    append("-S res:${videoQualityLimit.label.removeSuffix("p")} ")
                }
                preferredCodec.ytDlpSortCodec?.let { append("-S vcodec:$it ") }
                append("--merge-output-format ${videoContainer.ext} ")
                if (downloadSubtitles) append("--write-subs ")
                if (saveThumbnail) append("--embed-thumbnail ")
            }.trim()
        )
    }

    var showPreferredVideoDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showAudioFormatDialog by remember { mutableStateOf(false) }

    val resolvedPreset = remember(
        downloadType,
        formatSelection,
        customPreset,
        videoQualityLimit,
        videoContainer,
        preferredCodec,
        audioFormat
    ) {
        if (formatSelection == SealFormatSelection.CUSTOM) {
            customPreset
        } else if (downloadType == SealConfigureType.AUDIO) {
            when (audioFormat) {
                SealAudioFormatPref.FLAC -> DownloadFormatPreset.AUDIO_FLAC_LOSSLESS
                SealAudioFormatPref.MP3 -> DownloadFormatPreset.AUDIO_MP3_STANDARD
                SealAudioFormatPref.OPUS -> DownloadFormatPreset.AUDIO_OPUS_HIGH
                SealAudioFormatPref.NOT_SPECIFIED,
                SealAudioFormatPref.M4A -> DownloadFormatPreset.AUDIO_M4A_HIGH
            }
        } else if (videoContainer == SealVideoContainerPref.MKV) {
            if (preferredCodec == VideoCodecPreference.AV1) {
                DownloadFormatPreset.VIDEO_MKV_AV1
            } else {
                DownloadFormatPreset.VIDEO_MKV_HIGH
            }
        } else {
            when (videoQualityLimit) {
                SealVideoQualityLimitPref.BEST,
                SealVideoQualityLimitPref.P2160 -> DownloadFormatPreset.VIDEO_MP4_4K
                SealVideoQualityLimitPref.P1440,
                SealVideoQualityLimitPref.P1080 -> DownloadFormatPreset.VIDEO_MP4_HIGH
                SealVideoQualityLimitPref.P720 -> DownloadFormatPreset.VIDEO_MP4_STANDARD
                SealVideoQualityLimitPref.P480,
                SealVideoQualityLimitPref.P360,
                SealVideoQualityLimitPref.LOWEST -> DownloadFormatPreset.VIDEO_MP4_COMPACT
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("downloader_bottom_sheet")
        ) {
            // Centered Seal Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Configure before download",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Adjust this download",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Video Link Input Box (Seal-style)
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = {
                    Text(
                        if (downloadPlaylist) "Video links (one per line)"
                        else "Video link"
                    )
                },
                placeholder = { Text("https://...") },
                singleLine = !downloadPlaylist,
                minLines = if (downloadPlaylist) 3 else 1,
                maxLines = if (downloadPlaylist) 5 else 2,
                trailingIcon = {
                    if (urlText.isNotEmpty()) {
                        IconButton(onClick = { urlText = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Cancel,
                                contentDescription = "Clear link"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.text?.let { pasted ->
                                    if (pasted.isNotBlank()) urlText = pasted
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Paste link"
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("downloader_url_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Download type section (Segmented control: Audio | Video | Commands)
            Text(
                text = "Download type",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SealConfigureType.entries.forEachIndexed { index, type ->
                    val selected = downloadType == type
                    SegmentedButton(
                        selected = selected,
                        onClick = { downloadType = type },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SealConfigureType.entries.size
                        ),
                        icon = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        modifier = Modifier.testTag("seal_type_${type.name.lowercase()}")
                    ) {
                        Text(
                            text = type.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Commands custom flags field when Commands is chosen
            AnimatedVisibility(visible = downloadType == SealConfigureType.COMMANDS) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customCommandFlags,
                        onValueChange = { customCommandFlags = it },
                        label = { Text("Command flags") },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (downloadType != SealConfigureType.COMMANDS) {
                Spacer(modifier = Modifier.height(16.dp))

                // Format selection (Auto | Custom)
                Text(
                    text = "Format selection",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = formatSelection == SealFormatSelection.AUTO,
                        onClick = { formatSelection = SealFormatSelection.AUTO },
                        label = { Text("Auto") },
                        leadingIcon = if (formatSelection == SealFormatSelection.AUTO) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = CircleShape,
                        modifier = Modifier.testTag("seal_format_auto")
                    )
                    FilterChip(
                        selected = formatSelection == SealFormatSelection.CUSTOM,
                        onClick = {
                            formatSelection = SealFormatSelection.CUSTOM
                            val matching = DownloadFormatPreset.entries.filter {
                                it.isVideo == (downloadType == SealConfigureType.VIDEO)
                            }
                            if (customPreset !in matching && matching.isNotEmpty()) {
                                customPreset = matching.first()
                            }
                        },
                        label = { Text("Custom") },
                        leadingIcon = if (formatSelection == SealFormatSelection.CUSTOM) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = CircleShape,
                        modifier = Modifier.testTag("seal_format_custom")
                    )
                }

                AnimatedVisibility(visible = formatSelection == SealFormatSelection.CUSTOM) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DownloadFormatPreset.entries
                                .filter { it.isVideo == (downloadType == SealConfigureType.VIDEO) }
                                .forEach { preset ->
                                    FilterChip(
                                        selected = customPreset == preset,
                                        onClick = { customPreset = preset },
                                        label = { Text(preset.label) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Format preference row
                Text(
                    text = "Format preference",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (downloadType == SealConfigureType.VIDEO) {
                        SealActionChip(
                            icon = Icons.Outlined.VideoFile,
                            label = preferredVideoFormat.label,
                            onClick = { showPreferredVideoDialog = true },
                            testTag = "seal_chip_preferred_video"
                        )
                        SealActionChip(
                            icon = Icons.Outlined.Hd,
                            label = videoQualityLimit.label,
                            onClick = { showVideoQualityDialog = true },
                            testTag = "seal_chip_video_quality"
                        )
                        SealActionChip(
                            icon = Icons.Outlined.AudioFile,
                            label = if (audioFormat == SealAudioFormatPref.NOT_SPECIFIED) {
                                "Audio format"
                            } else {
                                "Audio: ${audioFormat.ext.uppercase()}"
                            },
                            onClick = { showAudioFormatDialog = true },
                            testTag = "seal_chip_audio_format"
                        )
                    } else {
                        SealActionChip(
                            icon = Icons.Outlined.AudioFile,
                            label = if (audioFormat == SealAudioFormatPref.NOT_SPECIFIED) {
                                "Audio format"
                            } else {
                                audioFormat.label
                            },
                            onClick = { showAudioFormatDialog = true },
                            testTag = "seal_chip_audio_format"
                        )
                        SealActionChip(
                            icon = Icons.Rounded.Autorenew,
                            label = if (convertAudio) {
                                "Convert (${audioFormat.ext.uppercase()})"
                            } else {
                                "Unconverted"
                            },
                            selected = convertAudio,
                            onClick = { convertAudio = !convertAudio },
                            testTag = "seal_chip_convert_audio"
                        )
                    }
                }

                if (downloadType == SealConfigureType.VIDEO) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Container & Video Codec",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SealVideoContainerPref.entries.forEach { cont ->
                            FilterChip(
                                selected = videoContainer == cont,
                                onClick = { videoContainer = cont },
                                label = { Text(cont.ext.uppercase()) },
                                shape = CircleShape,
                                modifier = Modifier.testTag("dl_container_${cont.ext}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoCodecPreference.entries.forEach { codecOpt ->
                            FilterChip(
                                selected = preferredCodec == codecOpt,
                                onClick = { preferredCodec = codecOpt },
                                label = { Text(codecOpt.shortBadge) },
                                shape = CircleShape,
                                modifier = Modifier.testTag("dl_codec_${codecOpt.name.lowercase()}")
                            )
                        }
                    }

                    val showAv1Warning = av1SoftwareOnly &&
                        (preferredCodec == VideoCodecPreference.AV1 || resolvedPreset == DownloadFormatPreset.VIDEO_MKV_AV1)
                    AnimatedVisibility(visible = showAv1Warning) {
                        Av1SoftwareWarningCard(
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else if (audioFormat == SealAudioFormatPref.MP3 || resolvedPreset == DownloadFormatPreset.AUDIO_MP3_STANDARD) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "MP3 Bitrate (libmp3lame)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Mp3BitrateOption.entries.forEach { rate ->
                            FilterChip(
                                selected = mp3Bitrate == rate,
                                onClick = { mp3Bitrate = rate },
                                label = { Text(rate.label) },
                                shape = CircleShape
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Additional settings row
            Text(
                text = "Additional settings",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = downloadPlaylist,
                    onClick = { downloadPlaylist = !downloadPlaylist },
                    label = { Text("Download playlist") },
                    leadingIcon = if (downloadPlaylist) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("seal_chip_playlist")
                )
                FilterChip(
                    selected = downloadSubtitles,
                    onClick = { downloadSubtitles = !downloadSubtitles },
                    label = { Text("Download subtitles") },
                    leadingIcon = if (downloadSubtitles) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("seal_chip_subtitles")
                )
                FilterChip(
                    selected = saveThumbnail,
                    onClick = { saveThumbnail = !saveThumbnail },
                    label = { Text("Save thumbnail") },
                    leadingIcon = if (saveThumbnail) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("seal_chip_thumbnail")
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Bottom Cancel & Download action buttons (right-aligned pill buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("downloader_cancel_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        val persistedMode = when {
                            downloadPlaylist -> SealDownloadModePref.BATCH
                            downloadType == SealConfigureType.AUDIO -> SealDownloadModePref.AUDIO
                            downloadType == SealConfigureType.COMMANDS -> SealDownloadModePref.COMMANDS
                            else -> SealDownloadModePref.VIDEO
                        }
                        val mappedContainer = when (videoContainer) {
                            SealVideoContainerPref.MKV -> VideoContainerOption.MKV
                            SealVideoContainerPref.WEBM -> VideoContainerOption.WEBM
                            SealVideoContainerPref.MP4 -> VideoContainerOption.MP4
                        }
                        onSaveConfigurePrefs(
                            persistedMode,
                            preferredVideoFormat,
                            preferredCodec,
                            videoContainer,
                            videoQualityLimit,
                            audioFormat,
                            audioQuality,
                            mp3Bitrate,
                            convertAudio,
                            downloadSubtitles,
                            saveThumbnail
                        )
                        onStartDownload(
                            urlText,
                            null,
                            resolvedPreset,
                            if (downloadType == SealConfigureType.COMMANDS) customCommandFlags else null,
                            preferredCodec,
                            mappedContainer,
                            mp3Bitrate
                        )
                    },
                    enabled = urlText.isNotBlank(),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("downloader_start_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download")
                }
            }
        }
    }

    if (showPreferredVideoDialog) {
        PreferredVideoFormatDialog(
            initialSelection = preferredVideoFormat,
            onDismiss = { showPreferredVideoDialog = false },
            onConfirm = {
                preferredVideoFormat = it
                showPreferredVideoDialog = false
            }
        )
    }

    if (showVideoQualityDialog) {
        VideoQualityDialog(
            initialQuality = videoQualityLimit,
            onDismiss = { showVideoQualityDialog = false },
            onConfirm = {
                videoQualityLimit = it
                showVideoQualityDialog = false
            }
        )
    }

    if (showAudioFormatDialog) {
        AudioFormatDialog(
            initialFormat = audioFormat,
            initialQuality = audioQuality,
            onDismiss = { showAudioFormatDialog = false },
            onConfirm = { fmt, qual ->
                audioFormat = fmt
                audioQuality = qual
                showAudioFormatDialog = false
            }
        )
    }
}

@Composable
private fun SealActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        shape = RoundedCornerShape(10.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun PreferredVideoFormatDialog(
    initialSelection: SealPreferredVideoFormatPref,
    onDismiss: () -> Unit,
    onConfirm: (SealPreferredVideoFormatPref) -> Unit
) {
    var pending by remember(initialSelection) { mutableStateOf(initialSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                imageVector = Icons.Outlined.VideoFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(
                text = "Preferred video format",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SealPreferredVideoFormatPref.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pending = option }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pending == option,
                            onClick = { pending = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pending) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VideoQualityDialog(
    initialQuality: SealVideoQualityLimitPref,
    onDismiss: () -> Unit,
    onConfirm: (SealVideoQualityLimitPref) -> Unit
) {
    var pending by remember(initialQuality) { mutableStateOf(initialQuality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Hd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(
                text = "Video quality",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Limit the video quality when multiple are present",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SealVideoQualityLimitPref.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { pending = option }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pending == option,
                                onClick = { pending = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pending) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioFormatDialog(
    initialFormat: SealAudioFormatPref,
    initialQuality: SealAudioQualityPref,
    onDismiss: () -> Unit,
    onConfirm: (SealAudioFormatPref, SealAudioQualityPref) -> Unit
) {
    var pendingFormat by remember(initialFormat) { mutableStateOf(initialFormat) }
    var pendingQuality by remember(initialQuality) { mutableStateOf(initialQuality) }
    var formatMenuExpanded by remember { mutableStateOf(false) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                imageVector = Icons.Outlined.AudioFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(
                text = "Audio format",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ExposedDropdownMenuBox(
                    expanded = formatMenuExpanded,
                    onExpandedChange = { formatMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = pendingFormat.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Preferred audio format") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AudioFile,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatMenuExpanded)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = formatMenuExpanded,
                        onDismissRequest = { formatMenuExpanded = false }
                    ) {
                        SealAudioFormatPref.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    pendingFormat = option
                                    formatMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = qualityMenuExpanded,
                    onExpandedChange = { qualityMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = pendingQuality.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Audio quality") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Hd,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityMenuExpanded)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = qualityMenuExpanded,
                        onDismissRequest = { qualityMenuExpanded = false }
                    ) {
                        SealAudioQualityPref.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    pendingQuality = option
                                    qualityMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pendingFormat, pendingQuality) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoConverterSheet(
    defaultFormat: PhotoTargetFormat = PhotoTargetFormat.WEBP,
    defaultQuality: Int = 80,
    defaultScalePercent: Int = 100,
    defaultSkipSmall: Boolean = true,
    onDismiss: () -> Unit,
    onStartConvert: (List<Uri>, PhotoTargetFormat, Int, Int, Boolean) -> Unit
) {
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var format by remember(defaultFormat) { mutableStateOf(defaultFormat) }
    var quality by remember(defaultQuality) { mutableFloatStateOf(defaultQuality.toFloat()) }
    var scalePercent by remember(defaultScalePercent) { mutableFloatStateOf(defaultScalePercent.toFloat()) }
    var skipIfNotSmaller by remember(defaultSkipSmall) { mutableStateOf(defaultSkipSmall) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) selectedUris = uris
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Photo Converter & Compressor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Batch resize, compress & convert between JPEG, PNG, and WebP",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Collections, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (selectedUris.isEmpty()) "Select Photos via Photo Picker"
                    else "${selectedUris.size} photo(s) selected"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Quick Presets", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = quality.roundToInt() == 75 && scalePercent.roundToInt() == 75,
                    onClick = { quality = 75f; scalePercent = 75f; format = PhotoTargetFormat.WEBP },
                    label = { Text("Social 75%") },
                    shape = CircleShape
                )
                FilterChip(
                    selected = quality.roundToInt() == 85 && scalePercent.roundToInt() == 100,
                    onClick = { quality = 85f; scalePercent = 100f; format = PhotoTargetFormat.JPEG },
                    label = { Text("Balanced 85%") },
                    shape = CircleShape
                )
                FilterChip(
                    selected = quality.roundToInt() == 95 && scalePercent.roundToInt() == 100,
                    onClick = { quality = 95f; scalePercent = 100f; format = PhotoTargetFormat.WEBP },
                    label = { Text("Archival 95%") },
                    shape = CircleShape
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Target Format", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoTargetFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = format == fmt,
                        onClick = { format = fmt },
                        label = { Text(fmt.label) },
                        shape = CircleShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Quality: ${quality.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = quality,
                onValueChange = { quality = it },
                valueRange = 15f..100f
            )

            Text("Resolution Scale: ${scalePercent.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = scalePercent,
                onValueChange = { scalePercent = it },
                valueRange = 25f..100f
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip if savings < 10%", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Smart storage guard avoids larger outputs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = skipIfNotSmaller, onCheckedChange = { skipIfNotSmaller = it })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    onStartConvert(
                        selectedUris,
                        format,
                        quality.roundToInt(),
                        scalePercent.roundToInt(),
                        skipIfNotSmaller
                    )
                },
                enabled = selectedUris.isNotEmpty(),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Convert & Save to Pictures/ZeroPlay")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoCompressorSheet(
    preselectedUri: Uri?,
    preselectedTitle: String?,
    inspectedMetrics: VideoFileMetrics?,
    defaultPreset: VideoCompressionPreset = VideoCompressionPreset.BALANCED_720P,
    defaultCodec: VideoCodecOption = VideoCodecOption.AUTO,
    defaultContainer: VideoContainerOption = VideoContainerOption.MP4,
    defaultBitrateMbps: Float = 3.0f,
    defaultStripAudio: Boolean = false,
    defaultSkipSmall: Boolean = true,
    onInspectVideo: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onStartCompress: (
        Uri,
        VideoCompressionPreset,
        VideoCodecOption,
        VideoContainerOption,
        Float,
        Boolean,
        Boolean,
        Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val av1SoftwareOnly = remember(context) { DeviceCapabilityInspector.isAv1SoftwareOnly(context) }

    var selectedUri by remember(preselectedUri) { mutableStateOf(preselectedUri) }
    var preset by remember(defaultPreset) { mutableStateOf(defaultPreset) }
    var codec by remember(defaultCodec) { mutableStateOf(defaultCodec) }
    var container by remember(defaultContainer) { mutableStateOf(defaultContainer) }
    var bitrateMbps by remember(defaultBitrateMbps) { mutableFloatStateOf(defaultBitrateMbps) }
    var stripAudio by remember(defaultStripAudio) { mutableStateOf(defaultStripAudio) }
    var skipIfNotSmaller by remember(defaultSkipSmall) { mutableStateOf(defaultSkipSmall) }
    var remuxOnly by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            onInspectVideo(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Video Converter & Remuxer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = preselectedTitle ?: "AV1 / HEVC / VP9 / H.264 encoding & lossless MP4 ↔ MKV remuxing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedUri == null) "Select Video" else "Video Selected • Change")
            }

            if (inspectedMetrics != null) {
                val estBytes = if (remuxOnly) {
                    inspectedMetrics.sizeBytes
                } else if (inspectedMetrics.durationMs > 0L) {
                    val durSec = inspectedMetrics.durationMs / 1000.0
                    ((durSec * bitrateMbps * 1_000_000.0 / 8.0) + (durSec * 128_000.0 / 8.0)).toLong()
                } else 0L
                val savedPct = if (inspectedMetrics.sizeBytes > 0L && estBytes > 0L) {
                    (((inspectedMetrics.sizeBytes - estBytes).toDouble() / inspectedMetrics.sizeBytes) * 100.0)
                        .roundToInt()
                        .coerceIn(0, 95)
                } else 0

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${inspectedMetrics.displayName} • ${inspectedMetrics.width}x${inspectedMetrics.height} • ${formatDuration(inspectedMetrics.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (remuxOnly) {
                                "Lossless stream copy -> ${container.ext.uppercase()} (~${formatBytes(inspectedMetrics.sizeBytes)})"
                            } else {
                                "Original: ${formatBytes(inspectedMetrics.sizeBytes)}  ->  Est. ${formatBytes(estBytes)} (~$savedPct% saved)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Output Container", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoContainerOption.entries.forEach { cont ->
                    FilterChip(
                        selected = container == cont,
                        onClick = { container = cont },
                        label = { Text(cont.ext.uppercase()) },
                        shape = CircleShape,
                        modifier = Modifier.testTag("conv_container_${cont.ext}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lossless Remux (MP4 ↔ MKV)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Instant container switch via FFmpeg (-c copy) without re-encoding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = remuxOnly, onCheckedChange = { remuxOnly = it })
                }
            }

            AnimatedVisibility(visible = !remuxOnly) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Resolution Preset", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoCompressionPreset.entries.forEach { item ->
                            FilterChip(
                                selected = preset == item,
                                onClick = {
                                    preset = item
                                    bitrateMbps = item.bitrateBps / 1_000_000f
                                },
                                label = { Text("${item.heightPx}p") },
                                shape = CircleShape
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Video Codec", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VideoCodecOption.entries.forEach { opt ->
                            FilterChip(
                                selected = codec == opt,
                                onClick = { codec = opt },
                                label = { Text(opt.shortBadge) },
                                shape = CircleShape,
                                modifier = Modifier.testTag("conv_codec_${opt.name.lowercase()}")
                            )
                        }
                    }

                    AnimatedVisibility(visible = av1SoftwareOnly && codec == VideoCodecOption.AV1) {
                        Av1SoftwareWarningCard(modifier = Modifier.padding(top = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Target Bitrate: ${String.format("%.1f", bitrateMbps)} Mbps",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = bitrateMbps,
                        onValueChange = { bitrateMbps = (it * 2f).roundToInt() / 2f },
                        valueRange = 1.0f..12.0f
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Strip audio track", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Remove audio stream for silent / smaller clips",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = stripAudio, onCheckedChange = { stripAudio = it })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Skip if savings < 10%", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Smart storage guard avoids larger outputs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = skipIfNotSmaller, onCheckedChange = { skipIfNotSmaller = it })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    selectedUri?.let {
                        onStartCompress(
                            it,
                            preset,
                            codec,
                            container,
                            bitrateMbps,
                            skipIfNotSmaller,
                            stripAudio,
                            remuxOnly
                        )
                    }
                },
                enabled = selectedUri != null,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    if (remuxOnly) "Remux Losslessly to ${container.ext.uppercase()}"
                    else "Encode ${codec.shortBadge} to ${container.ext.uppercase()}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioExtractorSheet(
    preselectedUri: Uri?,
    preselectedTitle: String?,
    defaultMp3Bitrate: Mp3BitrateOption = Mp3BitrateOption.KBPS_320,
    onDismiss: () -> Unit,
    onStartExtract: (Uri, String?, AudioTargetFormat, Mp3BitrateOption) -> Unit
) {
    var selectedUri by remember(preselectedUri) { mutableStateOf(preselectedUri) }
    var customName by remember(preselectedTitle) { mutableStateOf(preselectedTitle.orEmpty()) }
    var targetFormat by remember { mutableStateOf(AudioTargetFormat.OPUS) }
    var mp3Bitrate by remember(defaultMp3Bitrate) { mutableStateOf(defaultMp3Bitrate) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedUri = uri
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) selectedUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Audio Extractor & Converter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Extract from video or convert audio into Opus (libopus), MP3 (libmp3lame), M4A, or FLAC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Video", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { audioPicker.launch("audio/*") },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Audio", maxLines = 1)
                }
            }

            if (selectedUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Source media ready • Tap above to change",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Target Audio Format", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AudioTargetFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = targetFormat == fmt,
                        onClick = { targetFormat = fmt },
                        label = { Text(fmt.label) },
                        shape = CircleShape,
                        modifier = Modifier.testTag("audio_fmt_${fmt.ext}")
                    )
                }
            }

            AnimatedVisibility(visible = targetFormat == AudioTargetFormat.MP3) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("MP3 Bitrate (libmp3lame)", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Mp3BitrateOption.entries.forEach { rate ->
                            FilterChip(
                                selected = mp3Bitrate == rate,
                                onClick = { mp3Bitrate = rate },
                                label = { Text(rate.label) },
                                shape = CircleShape
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Output audio title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    selectedUri?.let {
                        onStartExtract(
                            it,
                            customName.takeIf { s -> s.isNotBlank() },
                            targetFormat,
                            mp3Bitrate
                        )
                    }
                },
                enabled = selectedUri != null,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Export ${targetFormat.ext.uppercase()} to Music/ZeroPlay")
            }
        }
    }
}

@Composable
private fun Av1SoftwareWarningCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AV1 is Software-Only on this device",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Zero Play plays AV1 smoothly using Media3 dav1d software decoding, though encoding uses more CPU/battery. Choose Auto, HEVC, or VP9 for hardware acceleration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.ifBlank { "My Playlist" }) },
                shape = CircleShape
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    track: MediaTrack,
    playlists: List<PlaylistUi>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreateNewPlaylist: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add \"${track.title}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playlists.forEach { playlist ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(playlist.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Or create new playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (newName.isNotBlank()) {
                Button(onClick = { onCreateNewPlaylist(newName) }) {
                    Text("Create & Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoTopActionSheet(
    sheetType: EchoTopSheetType,
    tracks: List<MediaTrack>,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onRescanMedia: () -> Unit,
    onPlayTrack: (MediaTrack) -> Unit,
    onOpenFullSettings: () -> Unit = {},
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
        ) {
            when (sheetType) {
                EchoTopSheetType.HISTORY -> {
                    Text("Recently Played", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    val recent = tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(6)
                    if (recent.isEmpty()) {
                        Text(
                            text = "No playback history yet. Play a local or downloaded track to see it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recent.forEachIndexed { idx, track ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp)
                                    .clickable {
                                        onPlayTrack(track)
                                        onDismiss()
                                    },
                                shape = groupedCardShape(idx, recent.size),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(track.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${track.playCount} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                EchoTopSheetType.CODECS,
                EchoTopSheetType.DOWNLOAD -> {
                    onDismiss()
                }
                EchoTopSheetType.PROFILE -> {
                    Text("Zero Play Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Theme", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = darkTheme, onCheckedChange = onToggleDarkTheme)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Material You Dynamic Color", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = dynamicColor, onCheckedChange = onToggleDynamicColor)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onRescanMedia()
                            onDismiss()
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rescan Device Audio & Video")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenFullSettings()
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Full Settings")
                    }
                }
            }
        }
    }
}
