package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.converter.MediaConverterEngine
import com.example.converter.PhotoTargetFormat
import com.example.converter.VideoCodecOption
import com.example.converter.VideoCompressionPreset
import com.example.converter.VideoFileMetrics
import com.example.data.local.AudioQualityPref
import com.example.data.local.CacheLimitPref
import com.example.data.local.ChartRegionPref
import com.example.data.local.CrossfadePref
import com.example.data.local.EchoDatabase
import com.example.data.local.EchoSettings
import com.example.data.local.LyricsPriorityPref
import com.example.data.local.PhotoPrefixPref
import com.example.data.local.PreferencesManager
import com.example.data.local.SealAudioFormatPref
import com.example.data.local.SealAudioQualityPref
import com.example.data.local.SealDownloadModePref
import com.example.data.local.SealFilenameTemplatePref
import com.example.data.local.SealPreferredVideoFormatPref
import com.example.data.local.SealUserAgentPref
import com.example.data.local.SealVideoContainerPref
import com.example.data.local.SealVideoQualityLimitPref
import com.example.data.local.SealVideoResolutionPref
import com.example.data.local.ThemeModePref
import com.example.data.local.UpdateChannelPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.ArtistChartItem
import com.example.data.model.AudioTargetFormat
import com.example.data.model.ConversionJobUi
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.DownloadTaskUi
import com.example.data.model.LibraryQuickCategory
import com.example.data.model.LibrarySortOption
import com.example.data.model.MediaTrack
import com.example.data.model.MediaType
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.PlaylistUi
import com.example.data.model.VideoContainerOption
import com.example.data.repository.EchoRepository
import com.example.data.repository.MediaCatalogSeed
import com.example.downloader.MediaDownloadManager
import com.example.playback.PlaybackController
import com.example.playback.PlaybackUiState
import com.example.updater.AppReleaseInfo
import com.example.updater.AppUpdateManager
import com.example.updater.UpdateCheckOutcome
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EchoTopSheetType {
    CODECS,
    DOWNLOAD,
    HISTORY,
    PROFILE
}

data class EchoAppUiState(
    val selectedMood: String? = null,
    val searchQuery: String = "",
    val chartPage: Int = 0,
    val selectedLibraryChip: String = "Playlists",
    val librarySortOption: LibrarySortOption = LibrarySortOption.DATE_ADDED,
    val librarySortAscending: Boolean = false,
    val selectedQuickCategory: LibraryQuickCategory? = null,
    val selectedPlaylistId: Long? = null,
    val isPlayerExpanded: Boolean = false,
    val isFullScreenVideo: Boolean = false,
    val isInPipMode: Boolean = false,
    val showSettingsScreen: Boolean = false,
    val showAboutScreen: Boolean = false,
    val showAutoUpdateScreen: Boolean = false,
    val showDeviceInfoDialog: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val updateCheckResultMessage: String? = null,
    val availableRelease: AppReleaseInfo? = null,
    val latestGitHubRelease: AppReleaseInfo? = null,
    val dismissedOtaBanner: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val downloadedApkPath: String? = null,
    val needsInstallPermission: Boolean = false,
    val downloadSheetUrl: String? = null,
    val showPhotoConverterSheet: Boolean = false,
    val showVideoConverterSheet: Boolean = false,
    val showAudioExtractorSheet: Boolean = false,
    val preselectedConverterUri: Uri? = null,
    val preselectedConverterTitle: String? = null,
    val inspectedVideoMetrics: VideoFileMetrics? = null,
    val trackForPlaylistDialog: MediaTrack? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val activeTopSheet: EchoTopSheetType? = null,
    val statusBannerMessage: String? = null,
    val ytDlpVersionLabel: String = "NewPipe + yt-dlp • FFmpegKit full-gpl",
    val isUpdatingYtDlp: Boolean = false
)

class EchoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = EchoDatabase.getInstance(application)
    private val repository = EchoRepository(application, db.echoDao())
    private val preferences = PreferencesManager(application)
    private val downloadManager = MediaDownloadManager(application, repository)
    private val converterEngine = MediaConverterEngine(application, repository)
    private val updateManager = AppUpdateManager(application)

    val playbackController = PlaybackController(application) { trackId ->
        viewModelScope.launch { repository.recordPlay(trackId) }
    }

    private val _uiState = MutableStateFlow(EchoAppUiState())
    val uiState: StateFlow<EchoAppUiState> = _uiState.asStateFlow()

    val allTracks: StateFlow<List<MediaTrack>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistUi>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversionJobs: StateFlow<List<ConversionJobUi>> = repository.conversionJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadTaskUi>> = downloadManager.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState

    val settings: StateFlow<EchoSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EchoSettings())

    val trendingArtists: List<ArtistChartItem> = MediaCatalogSeed.trendingArtists

    val filteredCharts: StateFlow<List<MediaTrack>> = combine(allTracks, _uiState) { tracks, state ->
        val query = state.searchQuery.trim().lowercase()
        val base = tracks.sortedByDescending { it.playCount }
        if (query.isEmpty() || query.startsWith("http://") || query.startsWith("https://")) {
            base
        } else {
            base.filter {
                it.title.lowercase().contains(query) ||
                    it.artist.lowercase().contains(query) ||
                    it.album.lowercase().contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        playbackController.connect()
        viewModelScope.launch {
            repository.initializeSeedData()
            val initialSettings = preferences.settings.first()
            if (initialSettings.autoScanOnLaunch) {
                repository.scanDeviceMediaStore()
            }
            playbackController.applyAudioEngineSettings(
                skipSilence = initialSettings.skipSilence,
                pauseOnHeadsetDisconnect = initialSettings.pauseOnHeadsetDisconnect
            )
            if (initialSettings.autoUpdateEnabled &&
                (initialSettings.updateRepoSlug.isNotBlank() || initialSettings.directUpdateFeedUrl.isNotBlank())
            ) {
                val outcome = updateManager.checkForUpdate(
                    repoInput = initialSettings.updateRepoSlug,
                    directFeedUrl = initialSettings.directUpdateFeedUrl,
                    channel = initialSettings.updateChannel
                )
                if (outcome is UpdateCheckOutcome.UpdateAvailable) {
                    _uiState.update {
                        it.copy(
                            availableRelease = outcome.release,
                            latestGitHubRelease = outcome.release,
                            dismissedOtaBanner = false,
                            updateCheckResultMessage = "New update ${outcome.release.resolvedVersionLabel} available",
                            statusBannerMessage = "Update ${outcome.release.resolvedVersionLabel} ready — tap to install"
                        )
                    }
                } else if (outcome is UpdateCheckOutcome.UpToDate && outcome.latestRelease != null) {
                    _uiState.update { it.copy(latestGitHubRelease = outcome.latestRelease) }
                }
            }
            val ytLabel = runCatching { downloadManager.getYtDlpVersionLabel() }.getOrNull()
            if (!ytLabel.isNullOrBlank()) {
                _uiState.update { it.copy(ytDlpVersionLabel = ytLabel) }
            }
        }
    }

    fun updateYtDlpEngine() {
        if (_uiState.value.isUpdatingYtDlp) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingYtDlp = true) }
            val result = downloadManager.updateYtDlp()
            val newLabel = runCatching { downloadManager.getYtDlpVersionLabel() }.getOrNull()
            _uiState.update { state ->
                state.copy(
                    isUpdatingYtDlp = false,
                    ytDlpVersionLabel = newLabel ?: state.ytDlpVersionLabel
                )
            }
            showStatus(
                result.getOrElse { it.message ?: "Failed to update yt-dlp extractor" }
            )
        }
    }

    fun rescanMediaStore() {
        viewModelScope.launch {
            repository.scanDeviceMediaStore()
            showStatus("Device media library refreshed")
        }
    }

    fun selectMood(mood: String) {
        _uiState.update { state ->
            state.copy(selectedMood = if (state.selectedMood == mood) null else mood)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, chartPage = 0) }
    }

    fun setChartPage(page: Int, maxPage: Int) {
        _uiState.update { it.copy(chartPage = page.coerceIn(0, maxPage.coerceAtLeast(0))) }
    }

    fun selectLibraryChip(chip: String) {
        _uiState.update {
            it.copy(
                selectedLibraryChip = chip,
                selectedQuickCategory = null,
                selectedPlaylistId = null
            )
        }
    }

    fun cycleSortOption() {
        _uiState.update { state ->
            val values = LibrarySortOption.entries
            val next = values[(values.indexOf(state.librarySortOption) + 1) % values.size]
            state.copy(librarySortOption = next)
        }
    }

    fun toggleSortDirection() {
        _uiState.update { it.copy(librarySortAscending = !it.librarySortAscending) }
    }

    fun openQuickCategory(category: LibraryQuickCategory?) {
        _uiState.update { it.copy(selectedQuickCategory = category, selectedPlaylistId = null) }
    }

    fun openPlaylist(playlistId: Long?) {
        _uiState.update { it.copy(selectedPlaylistId = playlistId, selectedQuickCategory = null) }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isPlayerExpanded = expanded) }
    }

    fun setFullScreenVideo(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullScreenVideo = fullscreen) }
    }

    fun setPipMode(inPip: Boolean) {
        _uiState.update { it.copy(isInPipMode = inPip) }
    }

    fun openSettings(show: Boolean) {
        _uiState.update { it.copy(showSettingsScreen = show, activeTopSheet = null) }
    }

    fun openAbout(show: Boolean) {
        _uiState.update { it.copy(showAboutScreen = show, activeTopSheet = null) }
    }

    fun openDeviceInfo(show: Boolean) {
        _uiState.update { it.copy(showDeviceInfoDialog = show) }
    }

    fun openAutoUpdate(show: Boolean) {
        _uiState.update {
            it.copy(
                showAutoUpdateScreen = show,
                activeTopSheet = null,
                updateCheckResultMessage = if (show) it.updateCheckResultMessage else null
            )
        }
    }

    fun dismissOtaBanner() {
        _uiState.update { it.copy(dismissedOtaBanner = true) }
    }

    fun setAutoUpdateEnabled(v: Boolean) {
        viewModelScope.launch { preferences.setAutoUpdateEnabled(v) }
    }

    fun setUpdateChannel(channel: UpdateChannelPref) {
        viewModelScope.launch { preferences.setUpdateChannel(channel) }
    }

    fun setUpdateRepoSlug(slug: String) {
        viewModelScope.launch { preferences.setUpdateRepoSlug(slug) }
    }

    fun setDirectUpdateFeedUrl(url: String) {
        viewModelScope.launch { preferences.setDirectUpdateFeedUrl(url) }
    }

    fun getInstalledVersionLabel(): String = updateManager.getInstalledVersionLabel()

    fun checkForUpdates(
        repoOverride: String? = null,
        feedOverride: String? = null
    ) {
        if (_uiState.value.isCheckingUpdates) return
        val s = settings.value
        val repoToCheck = repoOverride ?: s.updateRepoSlug
        val feedToCheck = feedOverride ?: s.directUpdateFeedUrl
        viewModelScope.launch {
            if (repoOverride != null) preferences.setUpdateRepoSlug(repoOverride)
            if (feedOverride != null) preferences.setDirectUpdateFeedUrl(feedOverride)

            _uiState.update {
                it.copy(
                    isCheckingUpdates = true,
                    updateCheckResultMessage = null,
                    availableRelease = null,
                    latestGitHubRelease = null,
                    needsInstallPermission = false
                )
            }
            when (val outcome = updateManager.checkForUpdate(repoToCheck, feedToCheck, s.updateChannel)) {
                is UpdateCheckOutcome.NeedsConfiguration -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdates = false,
                            updateCheckResultMessage = outcome.message
                        )
                    }
                }
                is UpdateCheckOutcome.UpToDate -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdates = false,
                            latestGitHubRelease = outcome.latestRelease,
                            updateCheckResultMessage = outcome.message
                        )
                    }
                }
                is UpdateCheckOutcome.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdates = false,
                            availableRelease = outcome.release,
                            latestGitHubRelease = outcome.release,
                            dismissedOtaBanner = false,
                            updateCheckResultMessage = "Update ${outcome.release.resolvedVersionLabel} available (Installed: ${outcome.installedVersion})"
                        )
                    }
                }
                is UpdateCheckOutcome.Error -> {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdates = false,
                            updateCheckResultMessage = outcome.message
                        )
                    }
                }
            }
        }
    }

    fun downloadAndInstallUpdate(overrideRelease: AppReleaseInfo? = null) {
        val release = overrideRelease ?: _uiState.value.availableRelease ?: _uiState.value.latestGitHubRelease ?: return
        val apkUrl = release.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            _uiState.update {
                it.copy(updateCheckResultMessage = "This release does not contain an .apk asset yet. Use Release button to open GitHub.")
            }
            return
        }
        if (_uiState.value.isDownloadingUpdate) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    availableRelease = release,
                    isDownloadingUpdate = true,
                    updateDownloadProgress = 0.02f,
                    downloadedApkPath = null,
                    needsInstallPermission = false
                )
            }
            val result = updateManager.downloadApk(apkUrl, release.resolvedVersionLabel) { p ->
                _uiState.update { it.copy(updateDownloadProgress = p) }
            }
            result.onSuccess { apkFile ->
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadProgress = 1f,
                        downloadedApkPath = apkFile.absolutePath
                    )
                }
                triggerApkInstall(apkFile, release)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateCheckResultMessage = "Download failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun retryInstallDownloadedApk() {
        val path = _uiState.value.downloadedApkPath ?: return
        val file = File(path)
        if (file.exists()) {
            triggerApkInstall(file, _uiState.value.availableRelease)
        }
    }

    fun openUnknownSourcesSettings() {
        updateManager.openInstallPermissionSettings()
    }

    private fun triggerApkInstall(apkFile: File, release: AppReleaseInfo? = null) {
        if (!updateManager.canInstallPackages()) {
            _uiState.update {
                it.copy(
                    needsInstallPermission = true,
                    updateCheckResultMessage = "APK downloaded. Grant 'Install unknown apps' permission, then tap Install APK."
                )
            }
            updateManager.openInstallPermissionSettings()
            return
        }
        val installResult = updateManager.installApk(apkFile)
        installResult.onSuccess {
            release?.let { updateManager.markReleaseInstallLaunched(it) }
            _uiState.update {
                it.copy(
                    needsInstallPermission = false,
                    updateCheckResultMessage = "Installer launched for ${apkFile.name}"
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(updateCheckResultMessage = "Could not open installer: ${err.message}")
            }
        }
    }

    // --- Appearance Settings ---
    fun setThemeMode(mode: ThemeModePref) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setPureBlackAmoled(v: Boolean) {
        viewModelScope.launch { preferences.setPureBlackAmoled(v) }
    }

    fun setDynamicColor(v: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(v) }
    }

    fun setShowMiniProgressBar(v: Boolean) {
        viewModelScope.launch { preferences.setShowMiniProgressBar(v) }
    }

    fun toggleFullBleedPlayer() {
        viewModelScope.launch { preferences.setFullBleedPlayer(!settings.value.fullBleedPlayer) }
    }

    fun setFullBleedPlayer(v: Boolean) {
        viewModelScope.launch { preferences.setFullBleedPlayer(v) }
    }

    fun setPlayerBlurBackground(v: Boolean) {
        viewModelScope.launch { preferences.setPlayerBlurBackground(v) }
    }

    fun setShowQualityBadge(v: Boolean) {
        viewModelScope.launch { preferences.setShowQualityBadge(v) }
    }

    fun setShowSongDnaBadge(v: Boolean) {
        viewModelScope.launch { preferences.setShowSongDnaBadge(v) }
    }

    // --- Playback, Audio & Lyrics Settings ---
    fun cycleAudioQuality() {
        val next = nextEnum(settings.value.audioQuality, AudioQualityPref.entries)
        viewModelScope.launch { preferences.setAudioQuality(next) }
    }

    fun cycleDefaultSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val idx = speeds.indexOf(settings.value.defaultSpeed)
        val next = speeds[(if (idx >= 0) idx + 1 else 2) % speeds.size]
        viewModelScope.launch { preferences.setDefaultSpeed(next) }
        playbackController.setPlaybackSpeed(next)
    }

    fun cycleCrossfade() {
        val next = nextEnum(settings.value.crossfade, CrossfadePref.entries)
        viewModelScope.launch { preferences.setCrossfade(next) }
    }

    fun setSkipSilence(v: Boolean) {
        viewModelScope.launch { preferences.setSkipSilence(v) }
        playbackController.applyAudioEngineSettings(v, settings.value.pauseOnHeadsetDisconnect)
    }

    fun setNormalizeVolume(v: Boolean) {
        viewModelScope.launch { preferences.setNormalizeVolume(v) }
    }

    fun setGaplessFlow(v: Boolean) {
        viewModelScope.launch { preferences.setGaplessFlow(v) }
    }

    fun setPauseOnHeadsetDisconnect(v: Boolean) {
        viewModelScope.launch { preferences.setPauseOnHeadsetDisconnect(v) }
        playbackController.applyAudioEngineSettings(settings.value.skipSilence, v)
    }

    fun setKeepScreenOn(v: Boolean) {
        viewModelScope.launch { preferences.setKeepScreenOn(v) }
    }

    fun setWordByWordLyrics(v: Boolean) {
        viewModelScope.launch { preferences.setWordByWordLyrics(v) }
    }

    fun cycleLyricsPriority() {
        val next = nextEnum(settings.value.lyricsPriority, LyricsPriorityPref.entries)
        viewModelScope.launch { preferences.setLyricsPriority(next) }
    }

    fun cycleChartRegion() {
        val next = nextEnum(settings.value.chartRegion, ChartRegionPref.entries)
        viewModelScope.launch { preferences.setChartRegion(next) }
    }

    fun setExcludeExplicit(v: Boolean) {
        viewModelScope.launch { preferences.setExcludeExplicit(v) }
    }

    fun setAutoPipForVideo(v: Boolean) {
        viewModelScope.launch { preferences.setAutoPipForVideo(v) }
    }

    fun setPersistentQueue(v: Boolean) {
        viewModelScope.launch { preferences.setPersistentQueue(v) }
    }

    // --- Seal Downloader Settings ---
    fun cycleSealDownloadMode() {
        val next = nextEnum(settings.value.sealDownloadMode, SealDownloadModePref.entries)
        viewModelScope.launch { preferences.setSealDownloadMode(next) }
    }

    fun cycleDefaultDownloadPreset() {
        val next = nextEnum(settings.value.defaultDownloadPreset, DownloadFormatPreset.entries)
        viewModelScope.launch { preferences.setDefaultDownloadPreset(next) }
    }

    fun cycleSealVideoContainer() {
        val next = nextEnum(settings.value.sealVideoContainer, SealVideoContainerPref.entries)
        viewModelScope.launch { preferences.setSealVideoContainer(next) }
    }

    fun cyclePreferredDownloadCodec() {
        val next = nextEnum(settings.value.preferredDownloadCodec, VideoCodecPreference.entries)
        viewModelScope.launch { preferences.setPreferredDownloadCodec(next) }
    }

    fun cycleSealVideoResolution() {
        val next = nextEnum(settings.value.sealVideoResolution, SealVideoResolutionPref.entries)
        viewModelScope.launch { preferences.setSealVideoResolution(next) }
    }

    fun cycleSealAudioFormat() {
        val next = nextEnum(settings.value.sealAudioFormat, SealAudioFormatPref.entries)
        viewModelScope.launch { preferences.setSealAudioFormat(next) }
    }

    fun cycleMp3Bitrate() {
        val next = nextEnum(settings.value.mp3Bitrate, Mp3BitrateOption.entries)
        viewModelScope.launch { preferences.setMp3Bitrate(next) }
    }

    fun setWifiOnlyDownloads(v: Boolean) {
        viewModelScope.launch { preferences.setWifiOnlyDownloads(v) }
    }

    fun cycleConcurrentDownloads() {
        val next = (settings.value.concurrentDownloads % 4) + 1
        viewModelScope.launch { preferences.setConcurrentDownloads(next) }
    }

    fun setEmbedDownloadMetadata(v: Boolean) {
        viewModelScope.launch { preferences.setEmbedDownloadMetadata(v) }
    }

    fun setEmbedSubtitles(v: Boolean) {
        viewModelScope.launch { preferences.setEmbedSubtitles(v) }
    }

    fun setSplitSubfolders(v: Boolean) {
        viewModelScope.launch { preferences.setSplitSubfolders(v) }
    }

    fun setRestrictFilenames(v: Boolean) {
        viewModelScope.launch { preferences.setRestrictFilenames(v) }
    }

    fun cycleFilenameTemplate() {
        val next = nextEnum(settings.value.filenameTemplate, SealFilenameTemplatePref.entries)
        viewModelScope.launch { preferences.setFilenameTemplate(next) }
    }

    fun setUseCookiesAuth(v: Boolean) {
        viewModelScope.launch { preferences.setUseCookiesAuth(v) }
    }

    fun cycleSealUserAgent() {
        val next = nextEnum(settings.value.sealUserAgent, SealUserAgentPref.entries)
        viewModelScope.launch { preferences.setSealUserAgent(next) }
    }

    fun setAutoImportDownloads(v: Boolean) {
        viewModelScope.launch { preferences.setAutoImportDownloads(v) }
    }

    fun saveSealConfigurePrefs(
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
    ) {
        viewModelScope.launch {
            preferences.setSealDownloadMode(downloadMode)
            preferences.setSealPreferredVideoFormat(preferredVideoFormat)
            preferences.setPreferredDownloadCodec(preferredCodec)
            preferences.setSealVideoContainer(videoContainer)
            preferences.setSealVideoQualityLimit(videoQualityLimit)
            preferences.setSealAudioFormat(audioFormat)
            preferences.setSealAudioQuality(audioQuality)
            preferences.setMp3Bitrate(mp3Bitrate)
            preferences.setConvertSealAudio(convertAudio)
            preferences.setEmbedSubtitles(embedSubtitles)
            preferences.setSaveDownloadThumbnail(saveThumbnail)
        }
    }

    // --- Video Converter Settings ---
    fun cycleDefaultVideoPreset() {
        val next = nextEnum(settings.value.defaultVideoPreset, VideoCompressionPreset.entries)
        viewModelScope.launch { preferences.setDefaultVideoPreset(next) }
    }

    fun cycleDefaultVideoCodec() {
        val next = nextEnum(settings.value.defaultVideoCodec, VideoCodecOption.entries)
        viewModelScope.launch { preferences.setDefaultVideoCodec(next) }
    }

    fun cycleDefaultVideoContainer() {
        val next = nextEnum(settings.value.defaultVideoContainer, VideoContainerOption.entries)
        viewModelScope.launch { preferences.setDefaultVideoContainer(next) }
    }

    fun cycleDefaultVideoBitrate() {
        val rates = listOf(1.5f, 3.0f, 5.0f, 8.0f)
        val idx = rates.indexOf(settings.value.defaultVideoBitrateMbps)
        val next = rates[(if (idx >= 0) idx + 1 else 1) % rates.size]
        viewModelScope.launch { preferences.setDefaultVideoBitrateMbps(next) }
    }

    fun setStripVideoAudioByDefault(v: Boolean) {
        viewModelScope.launch { preferences.setStripVideoAudioByDefault(v) }
    }

    fun setHardwareEncoderPriority(v: Boolean) {
        viewModelScope.launch { preferences.setHardwareEncoderPriority(v) }
    }

    fun setKeepOriginalVideo(v: Boolean) {
        viewModelScope.launch { preferences.setKeepOriginalVideo(v) }
    }

    // --- Minimo Photo Settings ---
    fun cycleDefaultPhotoFormat() {
        val next = nextEnum(settings.value.defaultPhotoFormat, PhotoTargetFormat.entries)
        viewModelScope.launch { preferences.setDefaultPhotoFormat(next) }
    }

    fun cycleDefaultPhotoQuality() {
        val qualities = listOf(60, 75, 80, 90, 95)
        val idx = qualities.indexOf(settings.value.defaultPhotoQuality)
        val next = qualities[(if (idx >= 0) idx + 1 else 2) % qualities.size]
        viewModelScope.launch { preferences.setDefaultPhotoQuality(next) }
    }

    fun cycleDefaultPhotoScale() {
        val scales = listOf(50, 75, 100)
        val idx = scales.indexOf(settings.value.defaultPhotoScalePercent)
        val next = scales[(if (idx >= 0) idx + 1 else 2) % scales.size]
        viewModelScope.launch { preferences.setDefaultPhotoScalePercent(next) }
    }

    fun setStripPhotoExif(v: Boolean) {
        viewModelScope.launch { preferences.setStripPhotoExif(v) }
    }

    fun cyclePhotoOutputPrefix() {
        val next = nextEnum(settings.value.photoOutputPrefix, PhotoPrefixPref.entries)
        viewModelScope.launch { preferences.setPhotoOutputPrefix(next) }
    }

    fun setKeepOriginalPhoto(v: Boolean) {
        viewModelScope.launch { preferences.setKeepOriginalPhoto(v) }
    }

    // --- Storage & Reset ---
    fun setSkipSmallSavings(v: Boolean) {
        viewModelScope.launch { preferences.setSkipSmallSavings(v) }
    }

    fun setAutoScanOnLaunch(v: Boolean) {
        viewModelScope.launch { preferences.setAutoScanOnLaunch(v) }
    }

    fun cycleCacheLimit() {
        val next = nextEnum(settings.value.cacheLimit, CacheLimitPref.entries)
        viewModelScope.launch { preferences.setCacheLimit(next) }
    }

    fun clearConversionHistory() {
        viewModelScope.launch {
            repository.clearConversionHistory()
            showStatus("Conversion history cleared")
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            preferences.resetAllSettings()
            playbackController.setPlaybackSpeed(1.0f)
            playbackController.applyAudioEngineSettings(skipSilence = false, pauseOnHeadsetDisconnect = true)
            showStatus("All settings restored to defaults")
        }
    }

    private fun <T> nextEnum(current: T, entries: List<T>): T =
        entries[(entries.indexOf(current) + 1) % entries.size]

    // --- Playback & Media Actions ---
    fun playTrack(track: MediaTrack, queue: List<MediaTrack> = allTracks.value) {
        val s = settings.value
        val activeQueue = if (s.excludeExplicit) queue.filterNot { it.title.contains("explicit", ignoreCase = true) }.ifEmpty { queue } else queue
        playbackController.playTrack(track, activeQueue)
        playbackController.setPlaybackSpeed(s.defaultSpeed)
        playbackController.applyAudioEngineSettings(s.skipSilence, s.pauseOnHeadsetDisconnect)
        if (track.mediaType == MediaType.VIDEO) {
            _uiState.update { it.copy(isPlayerExpanded = true) }
        }
    }

    fun playNextInQueue(track: MediaTrack) {
        playbackController.playNextInQueue(track)
        showStatus("Queued \"${track.title}\" next")
    }

    fun toggleLike(track: MediaTrack) {
        viewModelScope.launch {
            repository.toggleLike(track.id)
            playbackController.updateCurrentTrackMetadata(track.copy(isLiked = !track.isLiked))
        }
    }

    fun openDownloadSheet(initialUrl: String = "") {
        _uiState.update { it.copy(downloadSheetUrl = initialUrl) }
    }

    fun dismissDownloadSheet() {
        _uiState.update { it.copy(downloadSheetUrl = null) }
    }

    fun startDownload(
        url: String,
        customTitle: String?,
        preset: DownloadFormatPreset,
        customCommandFlags: String? = null,
        preferredCodecOverride: VideoCodecPreference? = null,
        videoContainerOverride: VideoContainerOption? = null,
        mp3BitrateOverride: Mp3BitrateOption? = null
    ) {
        val urlRegex = Regex("""https?://[^\s<>"']+""")
        val urls = url.lines()
            .mapNotNull { line -> urlRegex.find(line.trim())?.value ?: line.trim().takeIf { it.isNotBlank() } }
            .filter { it.isNotBlank() }
        if (urls.isEmpty()) {
            showStatus("Enter a valid http:// or https:// media URL")
            return
        }
        val s = settings.value
        val activeCodec = preferredCodecOverride ?: s.preferredDownloadCodec
        val activeContainer = videoContainerOverride ?: when (s.sealVideoContainer) {
            SealVideoContainerPref.MKV -> VideoContainerOption.MKV
            SealVideoContainerPref.WEBM -> VideoContainerOption.WEBM
            SealVideoContainerPref.MP4 -> VideoContainerOption.MP4
        }
        val activeMp3Bitrate = mp3BitrateOverride ?: s.mp3Bitrate

        var startedCount = 0
        var lastError: String? = null
        urls.forEachIndexed { idx, singleUrl ->
            val titleForItem = if (urls.size == 1) customTitle else customTitle?.let { "${it}_${idx + 1}" }
            val maxHeight = if (preset.isVideo) {
                MediaDownloadManager.defaultHeightForPreset(preset)
            } else {
                s.sealVideoResolution.heightPx
            }
            val result = downloadManager.enqueueDownload(
                rawUrl = singleUrl,
                customTitle = titleForItem,
                preset = preset,
                maxVideoHeightPx = maxHeight,
                wifiOnly = s.wifiOnlyDownloads,
                restrictFilenames = s.restrictFilenames,
                splitSubfolders = s.splitSubfolders,
                filenameTemplate = s.filenameTemplate,
                preferredVideoFormat = s.sealPreferredVideoFormat,
                preferredCodec = activeCodec,
                videoContainer = activeContainer,
                audioFormat = s.sealAudioFormat,
                mp3Bitrate = activeMp3Bitrate,
                embedMetadata = s.embedDownloadMetadata,
                embedSubtitles = s.embedSubtitles,
                saveThumbnail = s.saveDownloadThumbnail,
                customCommandFlags = customCommandFlags,
                autoImportToLibrary = s.autoImportDownloads,
                onCompleted = { title ->
                    viewModelScope.launch { showStatus("Downloaded: $title") }
                },
                onError = { msg -> viewModelScope.launch { showStatus(msg) } }
            )
            result.onSuccess { startedCount++ }.onFailure { lastError = it.message }
        }
        if (startedCount > 0) {
            _uiState.update { it.copy(downloadSheetUrl = null) }
            showStatus(
                if (startedCount == 1) "Started background download (NewPipe • yt-dlp • FFmpegKit)"
                else "Queued $startedCount background downloads"
            )
        } else {
            showStatus(lastError ?: "Failed to start download")
        }
    }

    fun retryDownload(task: com.example.data.model.DownloadTaskUi) {
        downloadManager.dismissTask(task.id)
        startDownload(task.url, null, task.preset)
    }

    fun dismissDownload(taskId: String) {
        downloadManager.dismissTask(taskId)
    }

    fun openPhotoConverter() {
        _uiState.update { it.copy(showPhotoConverterSheet = true) }
    }

    fun openVideoConverter(uri: Uri? = null, title: String? = null) {
        _uiState.update {
            it.copy(
                showVideoConverterSheet = true,
                preselectedConverterUri = uri,
                preselectedConverterTitle = title,
                inspectedVideoMetrics = null
            )
        }
        if (uri != null) inspectVideoForSheet(uri)
    }

    fun inspectVideoForSheet(uri: Uri) {
        viewModelScope.launch {
            val metrics = converterEngine.inspectVideoMetrics(uri)
            _uiState.update { it.copy(inspectedVideoMetrics = metrics) }
        }
    }

    fun openAudioExtractor(uri: Uri? = null, title: String? = null) {
        _uiState.update {
            it.copy(
                showAudioExtractorSheet = true,
                preselectedConverterUri = uri,
                preselectedConverterTitle = title
            )
        }
    }

    fun dismissConverterSheets() {
        _uiState.update {
            it.copy(
                showPhotoConverterSheet = false,
                showVideoConverterSheet = false,
                showAudioExtractorSheet = false,
                preselectedConverterUri = null,
                preselectedConverterTitle = null,
                inspectedVideoMetrics = null
            )
        }
    }

    fun startPhotoBatchConvert(
        uris: List<Uri>,
        format: PhotoTargetFormat,
        quality: Int,
        scalePercent: Int,
        skipIfNotSmaller: Boolean
    ) {
        if (uris.isEmpty()) return
        dismissConverterSheets()
        showStatus("Converting ${uris.size} photo(s) to ${format.label}...")
        val prefix = settings.value.photoOutputPrefix.prefix
        viewModelScope.launch {
            converterEngine.batchCompressPhotos(uris, format, quality, scalePercent, skipIfNotSmaller, prefix)
            showStatus("Saved converted photo(s) to Pictures/ZeroPlay")
        }
    }

    fun startVideoCompress(
        uri: Uri,
        preset: VideoCompressionPreset,
        codec: VideoCodecOption,
        container: VideoContainerOption = settings.value.defaultVideoContainer,
        bitrateMbps: Float,
        skipIfNotSmaller: Boolean,
        removeAudio: Boolean = settings.value.stripVideoAudioByDefault,
        remuxOnly: Boolean = false
    ) {
        dismissConverterSheets()
        val label = if (remuxOnly) {
            "Remuxing container to ${container.ext.uppercase()}..."
        } else {
            "Encoding video (${preset.heightPx}p ${codec.shortBadge} -> ${container.ext.uppercase()})..."
        }
        showStatus(label)
        viewModelScope.launch {
            converterEngine.compressVideo(
                uri = uri,
                preset = preset,
                codec = codec,
                container = container,
                bitrateMbps = bitrateMbps,
                skipIfNotSmaller = skipIfNotSmaller,
                removeAudio = removeAudio,
                remuxOnly = remuxOnly
            )
            showStatus("Video export finished — see Library > Exported")
        }
    }

    fun startAudioExtract(
        uri: Uri,
        customTitle: String? = null,
        targetFormat: AudioTargetFormat = AudioTargetFormat.OPUS,
        mp3Bitrate: Mp3BitrateOption = settings.value.mp3Bitrate
    ) {
        dismissConverterSheets()
        showStatus("Converting / extracting ${targetFormat.label} audio...")
        viewModelScope.launch {
            converterEngine.extractAudioFromVideo(
                uri = uri,
                customTitle = customTitle,
                targetFormat = targetFormat,
                mp3Bitrate = mp3Bitrate
            )
            showStatus("${targetFormat.ext.uppercase()} audio exported to Library > Exported")
        }
    }

    fun openPlaylistPickerForTrack(track: MediaTrack) {
        _uiState.update { it.copy(trackForPlaylistDialog = track) }
    }

    fun dismissPlaylistPicker() {
        _uiState.update { it.copy(trackForPlaylistDialog = null) }
    }

    fun openCreatePlaylistDialog() {
        _uiState.update { it.copy(showCreatePlaylistDialog = true) }
    }

    fun dismissCreatePlaylistDialog() {
        _uiState.update { it.copy(showCreatePlaylistDialog = false) }
    }

    fun createPlaylist(name: String, initialTrackId: String? = null) {
        viewModelScope.launch {
            repository.createPlaylist(name, initialTrackId)
            _uiState.update { it.copy(showCreatePlaylistDialog = false, trackForPlaylistDialog = null) }
            showStatus("Playlist \"$name\" created")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: MediaTrack) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track.id)
            _uiState.update { it.copy(trackForPlaylistDialog = null) }
            showStatus("Added \"${track.title}\" to playlist")
        }
    }

    fun openTopSheet(type: EchoTopSheetType?) {
        when (type) {
            EchoTopSheetType.PROFILE -> _uiState.update { it.copy(showSettingsScreen = true, activeTopSheet = null) }
            EchoTopSheetType.CODECS -> _uiState.update { it.copy(showDeviceInfoDialog = true, activeTopSheet = null) }
            EchoTopSheetType.DOWNLOAD -> _uiState.update { it.copy(downloadSheetUrl = "", activeTopSheet = null) }
            else -> _uiState.update { it.copy(activeTopSheet = type) }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun showStatus(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
