package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.EchoSettings
import com.example.data.local.ThemeModePref
import com.example.ui.EchoViewModel

private enum class SettingsCategory(val label: String) {
    ALL("All"),
    APPEARANCE("Appearance"),
    PLAYBACK("Audio & Playback"),
    SEAL("Downloader"),
    VIDEO("Video Compressor"),
    PHOTO("Photo Compressor"),
    STORAGE("Storage")
}

@Composable
fun SettingsScreen(
    settings: EchoSettings,
    trackCount: Int,
    playlistCount: Int,
    conversionCount: Int,
    viewModel: EchoViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.ALL) }
    var showResetDialog by remember { mutableStateOf(false) }

    val showSection: (SettingsCategory) -> Boolean = { cat ->
        selectedCategory == SettingsCategory.ALL || selectedCategory == cat
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Profile Summary Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF0F4FF),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_zero_play_logo),
                                contentDescription = "Zero Play Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zero Play",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Unified • $trackCount tracks • $playlistCount playlists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Category Filter Pills
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SettingsCategory.entries) { category ->
                        val selected = selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("settings_cat_${category.name.lowercase()}")
                        )
                    }
                }
            }

            // 1. APPEARANCE
            if (showSection(SettingsCategory.APPEARANCE)) {
                item { SettingsSectionHeader("APPEARANCE & PLAYER UI") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ThemeModeCardRow(
                            index = 0,
                            total = 8,
                            selectedMode = settings.themeMode,
                            onSelectMode = viewModel::setThemeMode
                        )
                        SettingsSwitchRow(
                            index = 1,
                            total = 8,
                            icon = Icons.Rounded.Contrast,
                            title = "Pure Black AMOLED Dark",
                            subtitle = "True #000000 canvas when dark mode is active",
                            checked = settings.pureBlackAmoled,
                            onCheckedChange = viewModel::setPureBlackAmoled,
                            testTag = "setting_amoled"
                        )
                        SettingsSwitchRow(
                            index = 2,
                            total = 8,
                            icon = Icons.Rounded.Palette,
                            title = "Material You Dynamic Color",
                            subtitle = "Adapt surfaces to wallpaper color palette",
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                            testTag = "setting_dynamic_color"
                        )
                        SettingsSwitchRow(
                            index = 3,
                            total = 8,
                            icon = Icons.Rounded.LinearScale,
                            title = "Mini-Player Progress Bar",
                            subtitle = "Show thin playback progress indicator at bottom of mini player",
                            checked = settings.showMiniProgressBar,
                            onCheckedChange = viewModel::setShowMiniProgressBar,
                            testTag = "setting_mini_progress"
                        )
                        SettingsSwitchRow(
                            index = 4,
                            total = 8,
                            icon = Icons.Rounded.OpenInFull,
                            title = "Full-Bleed Player Artwork",
                            subtitle = "Edge-to-edge album cover in Now Playing",
                            checked = settings.fullBleedPlayer,
                            onCheckedChange = viewModel::setFullBleedPlayer,
                            testTag = "setting_full_bleed"
                        )
                        SettingsSwitchRow(
                            index = 5,
                            total = 8,
                            icon = Icons.Rounded.BlurOn,
                            title = "Blurred Artwork Backdrop",
                            subtitle = "Ambient 60dp blurred cover art behind player controls",
                            checked = settings.playerBlurBackground,
                            onCheckedChange = viewModel::setPlayerBlurBackground,
                            testTag = "setting_player_blur"
                        )
                        SettingsSwitchRow(
                            index = 6,
                            total = 8,
                            icon = Icons.Rounded.Verified,
                            title = "Show Format Quality Badge",
                            subtitle = "Display FLAC / WAV / M4A / MP4 pill in Now Playing",
                            checked = settings.showQualityBadge,
                            onCheckedChange = viewModel::setShowQualityBadge,
                            testTag = "setting_quality_badge"
                        )
                        SettingsSwitchRow(
                            index = 7,
                            total = 8,
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Show Song DNA Pill",
                            subtitle = "Show interactive audio telemetry sheet button",
                            checked = settings.showSongDnaBadge,
                            onCheckedChange = viewModel::setShowSongDnaBadge,
                            testTag = "setting_song_dna_badge"
                        )
                    }
                }
            }

            // 2. PLAYBACK, AUDIO & CONTENT
            if (showSection(SettingsCategory.PLAYBACK)) {
                item { SettingsSectionHeader("AUDIO ENGINE, LYRICS & CONTENT") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsClickRow(
                            index = 0,
                            total = 15,
                            icon = Icons.Rounded.GraphicEq,
                            title = "10-Band Equalizer & Effects",
                            subtitle = if (settings.eqEnabled) {
                                "${settings.eqPreset.label} • Bass Boost & Surround"
                            } else {
                                "Off • Tap to configure 10-band EQ"
                            },
                            onClick = { viewModel.openEqualizer(true) },
                            showChevron = true,
                            testTag = "setting_equalizer"
                        )
                        SettingsClickRow(
                            index = 1,
                            total = 15,
                            icon = Icons.Rounded.HighQuality,
                            title = "Audio Playback Quality",
                            subtitle = settings.audioQuality.label,
                            onClick = viewModel::cycleAudioQuality,
                            testTag = "setting_audio_quality"
                        )
                        SettingsClickRow(
                            index = 2,
                            total = 15,
                            icon = Icons.Rounded.Speed,
                            title = "Default Playback Speed",
                            subtitle = "${settings.defaultSpeed}x",
                            onClick = viewModel::cycleDefaultSpeed,
                            testTag = "setting_playback_speed"
                        )
                        SettingsClickRow(
                            index = 3,
                            total = 15,
                            icon = Icons.Rounded.Tune,
                            title = "Track Crossfade",
                            subtitle = settings.crossfade.label,
                            onClick = viewModel::cycleCrossfade,
                            testTag = "setting_crossfade"
                        )
                        SettingsSwitchRow(
                            index = 4,
                            total = 15,
                            icon = Icons.Rounded.FastForward,
                            title = "Skip Silence",
                            subtitle = "Automatically skip silent gaps inside ExoPlayer",
                            checked = settings.skipSilence,
                            onCheckedChange = viewModel::setSkipSilence,
                            testTag = "setting_skip_silence"
                        )
                        SettingsSwitchRow(
                            index = 5,
                            total = 15,
                            icon = Icons.Rounded.VolumeUp,
                            title = "Normalize Audio Volume",
                            subtitle = "Keep perceived loudness balanced across tracks",
                            checked = settings.normalizeVolume,
                            onCheckedChange = viewModel::setNormalizeVolume,
                            testTag = "setting_normalize_volume"
                        )
                        SettingsSwitchRow(
                            index = 6,
                            total = 15,
                            icon = Icons.Rounded.GraphicEq,
                            title = "Music Flow Continuous Queue",
                            subtitle = "Gapless transition across station tracks",
                            checked = settings.gaplessFlow,
                            onCheckedChange = viewModel::setGaplessFlow,
                            testTag = "setting_gapless_flow"
                        )
                        SettingsSwitchRow(
                            index = 7,
                            total = 15,
                            icon = Icons.Rounded.HeadsetOff,
                            title = "Pause on Headset Disconnect",
                            subtitle = "Stop playback immediately when audio output unplugs",
                            checked = settings.pauseOnHeadsetDisconnect,
                            onCheckedChange = viewModel::setPauseOnHeadsetDisconnect,
                            testTag = "setting_headset_pause"
                        )
                        SettingsSwitchRow(
                            index = 8,
                            total = 15,
                            icon = Icons.Rounded.ScreenLockPortrait,
                            title = "Keep Screen On",
                            subtitle = "Prevent display sleep while audio or video is playing",
                            checked = settings.keepScreenOn,
                            onCheckedChange = viewModel::setKeepScreenOn,
                            testTag = "setting_keep_screen_on"
                        )
                        SettingsSwitchRow(
                            index = 9,
                            total = 15,
                            icon = Icons.Rounded.Lyrics,
                            title = "Word-by-Word Lyrics Highlight",
                            subtitle = "Animate each word smoothly during synced lyrics",
                            checked = settings.wordByWordLyrics,
                            onCheckedChange = viewModel::setWordByWordLyrics,
                            testTag = "setting_word_lyrics"
                        )
                        SettingsClickRow(
                            index = 10,
                            total = 15,
                            icon = Icons.Rounded.MusicNote,
                            title = "Lyrics Provider Priority",
                            subtitle = settings.lyricsPriority.label,
                            onClick = viewModel::cycleLyricsPriority,
                            testTag = "setting_lyrics_priority"
                        )
                        SettingsClickRow(
                            index = 11,
                            total = 15,
                            icon = Icons.Rounded.Public,
                            title = "Charts Country / Region",
                            subtitle = settings.chartRegion.label,
                            onClick = viewModel::cycleChartRegion,
                            testTag = "setting_chart_region"
                        )
                        SettingsSwitchRow(
                            index = 12,
                            total = 15,
                            icon = Icons.Rounded.Explicit,
                            title = "Filter Explicit Tracks",
                            subtitle = "Skip tracks tagged explicit in auto queues",
                            checked = settings.excludeExplicit,
                            onCheckedChange = viewModel::setExcludeExplicit,
                            testTag = "setting_exclude_explicit"
                        )
                        SettingsSwitchRow(
                            index = 13,
                            total = 15,
                            icon = Icons.Rounded.PictureInPictureAlt,
                            title = "Auto Picture-in-Picture",
                            subtitle = "Enter floating PiP when leaving active video playback",
                            checked = settings.autoPipForVideo,
                            onCheckedChange = viewModel::setAutoPipForVideo,
                            testTag = "setting_auto_pip"
                        )
                        SettingsSwitchRow(
                            index = 14,
                            total = 15,
                            icon = Icons.Rounded.QueueMusic,
                            title = "Persistent Queue",
                            subtitle = "Remember active queue order between sessions",
                            checked = settings.persistentQueue,
                            onCheckedChange = viewModel::setPersistentQueue,
                            testTag = "setting_persistent_queue"
                        )
                    }
                }
            }

            // 3. MEDIA DOWNLOADER
            if (showSection(SettingsCategory.SEAL)) {
                item { SettingsSectionHeader("MEDIA DOWNLOADER") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsClickRow(
                            index = 0,
                            total = 18,
                            icon = Icons.Rounded.Download,
                            title = "Default Download Mode",
                            subtitle = settings.sealDownloadMode.label,
                            onClick = viewModel::cycleSealDownloadMode,
                            testTag = "setting_seal_mode"
                        )
                        SettingsClickRow(
                            index = 1,
                            total = 18,
                            icon = Icons.Rounded.Tune,
                            title = "Default Format Preset",
                            subtitle = settings.defaultDownloadPreset.label,
                            onClick = viewModel::cycleDefaultDownloadPreset,
                            testTag = "setting_download_preset"
                        )
                        SettingsClickRow(
                            index = 2,
                            total = 18,
                            icon = Icons.Rounded.VideoFile,
                            title = "Preferred Video Container",
                            subtitle = settings.sealVideoContainer.label,
                            onClick = viewModel::cycleSealVideoContainer,
                            testTag = "setting_seal_container"
                        )
                        SettingsClickRow(
                            index = 3,
                            total = 18,
                            icon = Icons.Rounded.Memory,
                            title = "Preferred Video Codec",
                            subtitle = settings.preferredDownloadCodec.label,
                            onClick = viewModel::cyclePreferredDownloadCodec,
                            testTag = "setting_download_codec"
                        )
                        SettingsClickRow(
                            index = 4,
                            total = 18,
                            icon = Icons.Rounded.VideoSettings,
                            title = "Max Video Resolution",
                            subtitle = settings.sealVideoResolution.label,
                            onClick = viewModel::cycleSealVideoResolution,
                            testTag = "setting_seal_resolution"
                        )
                        SettingsClickRow(
                            index = 5,
                            total = 18,
                            icon = Icons.Rounded.AudioFile,
                            title = "Preferred Audio Format",
                            subtitle = settings.sealAudioFormat.label,
                            onClick = viewModel::cycleSealAudioFormat,
                            testTag = "setting_seal_audio_format"
                        )
                        SettingsClickRow(
                            index = 6,
                            total = 18,
                            icon = Icons.Rounded.HighQuality,
                            title = "MP3 Encoding Bitrate",
                            subtitle = settings.mp3Bitrate.label,
                            onClick = viewModel::cycleMp3Bitrate,
                            testTag = "setting_mp3_bitrate"
                        )
                        SettingsClickRow(
                            index = 7,
                            total = 18,
                            icon = Icons.Rounded.Layers,
                            title = "Concurrent Downloads",
                            subtitle = "${settings.concurrentDownloads} parallel jobs",
                            onClick = viewModel::cycleConcurrentDownloads,
                            testTag = "setting_seal_concurrent"
                        )
                        SettingsSwitchRow(
                            index = 8,
                            total = 18,
                            icon = Icons.Rounded.Wifi,
                            title = "Download Over Wi-Fi Only",
                            subtitle = "Block metered cellular & roaming downloads",
                            checked = settings.wifiOnlyDownloads,
                            onCheckedChange = viewModel::setWifiOnlyDownloads,
                            testTag = "setting_wifi_only"
                        )
                        SettingsSwitchRow(
                            index = 9,
                            total = 18,
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Embed Metadata & Cover Art",
                            subtitle = "Write title, artist, and artwork tags into output",
                            checked = settings.embedDownloadMetadata,
                            onCheckedChange = viewModel::setEmbedDownloadMetadata,
                            testTag = "setting_embed_metadata"
                        )
                        SettingsSwitchRow(
                            index = 10,
                            total = 18,
                            icon = Icons.Rounded.Subtitles,
                            title = "Embed Subtitles",
                            subtitle = "Include SRT / VTT captions when available",
                            checked = settings.embedSubtitles,
                            onCheckedChange = viewModel::setEmbedSubtitles,
                            testTag = "setting_seal_subtitles"
                        )
                        SettingsSwitchRow(
                            index = 11,
                            total = 18,
                            icon = Icons.Rounded.CreateNewFolder,
                            title = "Organize Audio / Video Subfolders",
                            subtitle = "Save into Movies/ZeroPlay or Music/ZeroPlay",
                            checked = settings.splitSubfolders,
                            onCheckedChange = viewModel::setSplitSubfolders,
                            testTag = "setting_seal_subfolders"
                        )
                        SettingsSwitchRow(
                            index = 12,
                            total = 18,
                            icon = Icons.Rounded.TextFields,
                            title = "Restrict Filenames (ASCII-Safe)",
                            subtitle = "Replace special characters & spaces cleanly",
                            checked = settings.restrictFilenames,
                            onCheckedChange = viewModel::setRestrictFilenames,
                            testTag = "setting_seal_restrict_names"
                        )
                        SettingsClickRow(
                            index = 13,
                            total = 18,
                            icon = Icons.Rounded.DriveFileRenameOutline,
                            title = "Output Filename Template",
                            subtitle = settings.filenameTemplate.label,
                            onClick = viewModel::cycleFilenameTemplate,
                            testTag = "setting_seal_template"
                        )
                        SettingsSwitchRow(
                            index = 14,
                            total = 18,
                            icon = Icons.Rounded.Cookie,
                            title = "Use Session Cookies",
                            subtitle = "Send authentication cookies for private/member URLs",
                            checked = settings.useCookiesAuth,
                            onCheckedChange = viewModel::setUseCookiesAuth,
                            testTag = "setting_seal_cookies"
                        )
                        SettingsClickRow(
                            index = 15,
                            total = 18,
                            icon = Icons.Rounded.Public,
                            title = "Network User-Agent",
                            subtitle = settings.sealUserAgent.label,
                            onClick = viewModel::cycleSealUserAgent,
                            testTag = "setting_seal_ua"
                        )
                        SettingsSwitchRow(
                            index = 16,
                            total = 18,
                            icon = Icons.Rounded.BookmarkAdded,
                            title = "Auto-Import Downloads to Library",
                            subtitle = "Register finished downloads in Zero Play Library",
                            checked = settings.autoImportDownloads,
                            onCheckedChange = viewModel::setAutoImportDownloads,
                            testTag = "setting_seal_auto_import"
                        )
                        SettingsClickRow(
                            index = 17,
                            total = 18,
                            icon = Icons.Rounded.SystemUpdateAlt,
                            title = "Hybrid Engine (NewPipe + yt-dlp + FFmpegKit)",
                            subtitle = if (uiState.isUpdatingYtDlp) {
                                "Updating yt-dlp extractor..."
                            } else {
                                "${uiState.ytDlpVersionLabel} • Tap to update"
                            },
                            onClick = viewModel::updateYtDlpEngine,
                            testTag = "setting_ytdlp_update"
                        )
                    }
                }
            }

            // 4. VIDEO COMPRESSOR
            if (showSection(SettingsCategory.VIDEO)) {
                item { SettingsSectionHeader("VIDEO COMPRESSOR") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsClickRow(
                            index = 0,
                            total = 7,
                            icon = Icons.Rounded.VideoSettings,
                            title = "Default Video Resolution",
                            subtitle = settings.defaultVideoPreset.label,
                            onClick = viewModel::cycleDefaultVideoPreset,
                            testTag = "setting_video_preset"
                        )
                        SettingsClickRow(
                            index = 1,
                            total = 7,
                            icon = Icons.Rounded.Videocam,
                            title = "Default Video Codec",
                            subtitle = settings.defaultVideoCodec.label,
                            onClick = viewModel::cycleDefaultVideoCodec,
                            testTag = "setting_video_codec"
                        )
                        SettingsClickRow(
                            index = 2,
                            total = 7,
                            icon = Icons.Rounded.VideoFile,
                            title = "Default Output Container",
                            subtitle = settings.defaultVideoContainer.label,
                            onClick = viewModel::cycleDefaultVideoContainer,
                            testTag = "setting_video_container"
                        )
                        SettingsClickRow(
                            index = 3,
                            total = 7,
                            icon = Icons.Rounded.Speed,
                            title = "Default Target Bitrate",
                            subtitle = "${settings.defaultVideoBitrateMbps} Mbps",
                            onClick = viewModel::cycleDefaultVideoBitrate,
                            testTag = "setting_video_bitrate"
                        )
                        SettingsSwitchRow(
                            index = 4,
                            total = 7,
                            icon = Icons.Rounded.VolumeOff,
                            title = "Strip Audio Track by Default",
                            subtitle = "Mute output using Media3 / FFmpegKit",
                            checked = settings.stripVideoAudioByDefault,
                            onCheckedChange = viewModel::setStripVideoAudioByDefault,
                            testTag = "setting_video_strip_audio"
                        )
                        SettingsSwitchRow(
                            index = 5,
                            total = 7,
                            icon = Icons.Rounded.Memory,
                            title = "Hardware MediaCodec Priority",
                            subtitle = "Prefer device hardware H.264 / HEVC / AV1 encoders",
                            checked = settings.hardwareEncoderPriority,
                            onCheckedChange = viewModel::setHardwareEncoderPriority,
                            testTag = "setting_video_hw_encoder"
                        )
                        SettingsSwitchRow(
                            index = 6,
                            total = 7,
                            icon = Icons.Rounded.Save,
                            title = "Keep Original Video",
                            subtitle = "Preserve source file alongside Movies/ZeroPlay export",
                            checked = settings.keepOriginalVideo,
                            onCheckedChange = viewModel::setKeepOriginalVideo,
                            testTag = "setting_video_keep_orig"
                        )
                    }
                }
            }

            // 5. PHOTO COMPRESSOR
            if (showSection(SettingsCategory.PHOTO)) {
                item { SettingsSectionHeader("PHOTO COMPRESSOR") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsClickRow(
                            index = 0,
                            total = 6,
                            icon = Icons.Rounded.Image,
                            title = "Default Photo Output Format",
                            subtitle = settings.defaultPhotoFormat.label,
                            onClick = viewModel::cycleDefaultPhotoFormat,
                            testTag = "setting_photo_format"
                        )
                        SettingsClickRow(
                            index = 1,
                            total = 6,
                            icon = Icons.Rounded.HighQuality,
                            title = "Default Compression Quality",
                            subtitle = "${settings.defaultPhotoQuality}%",
                            onClick = viewModel::cycleDefaultPhotoQuality,
                            testTag = "setting_photo_quality"
                        )
                        SettingsClickRow(
                            index = 2,
                            total = 6,
                            icon = Icons.Rounded.PhotoSizeSelectLarge,
                            title = "Default Resolution Scale",
                            subtitle = "${settings.defaultPhotoScalePercent}%",
                            onClick = viewModel::cycleDefaultPhotoScale,
                            testTag = "setting_photo_scale"
                        )
                        SettingsSwitchRow(
                            index = 3,
                            total = 6,
                            icon = Icons.Rounded.Security,
                            title = "Strip GPS & Camera EXIF",
                            subtitle = "Remove location & device metadata from exports",
                            checked = settings.stripPhotoExif,
                            onCheckedChange = viewModel::setStripPhotoExif,
                            testTag = "setting_photo_strip_exif"
                        )
                        SettingsClickRow(
                            index = 4,
                            total = 6,
                            icon = Icons.Rounded.DriveFileRenameOutline,
                            title = "Batch Filename Prefix",
                            subtitle = settings.photoOutputPrefix.label,
                            onClick = viewModel::cyclePhotoOutputPrefix,
                            testTag = "setting_photo_prefix"
                        )
                        SettingsSwitchRow(
                            index = 5,
                            total = 6,
                            icon = Icons.Rounded.Save,
                            title = "Keep Original Photos",
                            subtitle = "Save compressed copies into Pictures/ZeroPlay",
                            checked = settings.keepOriginalPhoto,
                            onCheckedChange = viewModel::setKeepOriginalPhoto,
                            testTag = "setting_photo_keep_orig"
                        )
                    }
                }
            }

            // 6. STORAGE, LIBRARY & RESET
            if (showSection(SettingsCategory.STORAGE)) {
                item { SettingsSectionHeader("STORAGE, LIBRARY & MAINTENANCE") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsSwitchRow(
                            index = 0,
                            total = 7,
                            icon = Icons.Rounded.Image,
                            title = "Auto-download artist images",
                            subtitle = "Fetch & cache circular artist portraits offline when connected",
                            checked = settings.autoDownloadArtistImages,
                            onCheckedChange = viewModel::setAutoDownloadArtistImages,
                            testTag = "setting_auto_download_artist_images"
                        )
                        SettingsSwitchRow(
                            index = 1,
                            total = 7,
                            icon = Icons.Rounded.Savings,
                            title = "Smart Storage Guard (<10% Skip)",
                            subtitle = "Skip saving compressed outputs with under 10% savings",
                            checked = settings.skipSmallCompressionSavings,
                            onCheckedChange = viewModel::setSkipSmallSavings,
                            testTag = "setting_skip_small"
                        )
                        SettingsSwitchRow(
                            index = 2,
                            total = 7,
                            icon = Icons.Rounded.Refresh,
                            title = "Auto-Scan MediaStore on Launch",
                            subtitle = "Discover local device audio and video automatically",
                            checked = settings.autoScanOnLaunch,
                            onCheckedChange = viewModel::setAutoScanOnLaunch,
                            testTag = "setting_auto_scan"
                        )
                        SettingsClickRow(
                            index = 3,
                            total = 7,
                            icon = Icons.Rounded.Storage,
                            title = "Max Offline Cache Size",
                            subtitle = settings.cacheLimit.label,
                            onClick = viewModel::cycleCacheLimit,
                            testTag = "setting_cache_limit"
                        )
                        SettingsClickRow(
                            index = 4,
                            total = 7,
                            icon = Icons.Rounded.Refresh,
                            title = "Rescan Device MediaStore Now",
                            subtitle = "Refresh local audio, video & exported files",
                            onClick = viewModel::rescanMediaStore,
                            testTag = "setting_rescan_media"
                        )
                        SettingsClickRow(
                            index = 5,
                            total = 7,
                            icon = Icons.Rounded.DeleteSweep,
                            title = "Clear Conversion History",
                            subtitle = "Remove completed job logs from Convert tab",
                            onClick = viewModel::clearConversionHistory,
                            testTag = "setting_clear_history"
                        )
                        SettingsClickRow(
                            index = 6,
                            total = 7,
                            icon = Icons.Rounded.RestartAlt,
                            title = "Reset All Settings",
                            subtitle = "Restore player, downloader & converter preferences to defaults",
                            onClick = { showResetDialog = true },
                            isDestructive = true,
                            testTag = "setting_reset_all"
                        )
                    }
                }
            }

            // DIAGNOSTICS, UPDATES & ABOUT
            item { SettingsSectionHeader("DIAGNOSTICS, UPDATES & ABOUT") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsClickRow(
                        index = 0,
                        total = 3,
                        icon = Icons.Rounded.Memory,
                        title = "Device Info & Codec Diagnostics",
                        subtitle = "Live MediaCodecList check • AV1, VP9, HEVC, H.264, Opus & Display",
                        onClick = { viewModel.openDeviceInfo(true) },
                        showChevron = true,
                        testTag = "setting_device_info"
                    )
                    SettingsClickRow(
                        index = 1,
                        total = 3,
                        icon = Icons.Rounded.Update,
                        title = "Auto update",
                        subtitle = "${settings.updateChannel.label} channel • ${if (settings.autoUpdateEnabled) "Enabled" else "Off"}",
                        onClick = { viewModel.openAutoUpdate(true) },
                        showChevron = true,
                        testTag = "setting_auto_update"
                    )
                    SettingsClickRow(
                        index = 2,
                        total = 3,
                        icon = Icons.Rounded.Info,
                        title = "About Zero Play",
                        subtitle = "Zero Play ${viewModel.getInstalledVersionLabel()} • Tanmay Datta • Licences",
                        onClick = { viewModel.openAbout(true) },
                        showChevron = true,
                        testTag = "setting_about"
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all settings?") },
            text = {
                Text("This restores player, downloader, video compressor, and photo compressor preferences to their defaults.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetAllSettings()
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
