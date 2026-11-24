package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.converter.PhotoTargetFormat
import com.example.converter.VideoCodecOption
import com.example.converter.VideoCompressionPreset
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.VideoContainerOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.echoDataStore by preferencesDataStore(name = "echo_preferences")

class PreferencesManager(private val context: Context) {
    private object Keys {
        // Appearance
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PURE_BLACK_AMOLED = booleanPreferencesKey("pure_black_amoled")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_MINI_PROGRESS = booleanPreferencesKey("show_mini_progress")
        val FULL_BLEED_PLAYER = booleanPreferencesKey("full_bleed_player")
        val PLAYER_BLUR_BG = booleanPreferencesKey("player_blur_bg")
        val SHOW_QUALITY_BADGE = booleanPreferencesKey("show_quality_badge")
        val SHOW_SONG_DNA_BADGE = booleanPreferencesKey("show_song_dna_badge")

        // Playback, Audio & Lyrics
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val CROSSFADE = stringPreferencesKey("crossfade")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")
        val GAPLESS_FLOW = booleanPreferencesKey("gapless_flow")
        val PAUSE_HEADSET_DISCONNECT = booleanPreferencesKey("pause_headset_disconnect")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val WORD_BY_WORD_LYRICS = booleanPreferencesKey("word_by_word_lyrics")
        val LYRICS_PRIORITY = stringPreferencesKey("lyrics_priority")
        val CHART_REGION = stringPreferencesKey("chart_region")
        val EXCLUDE_EXPLICIT = booleanPreferencesKey("exclude_explicit")
        val AUTO_PIP_VIDEO = booleanPreferencesKey("auto_pip_video")
        val PERSISTENT_QUEUE = booleanPreferencesKey("persistent_queue")

        // Seal Downloader
        val SEAL_DOWNLOAD_MODE = stringPreferencesKey("seal_download_mode")
        val DEFAULT_DOWNLOAD_PRESET = stringPreferencesKey("default_download_preset")
        val SEAL_VIDEO_CONTAINER = stringPreferencesKey("seal_video_container")
        val SEAL_VIDEO_RES = stringPreferencesKey("seal_video_res")
        val SEAL_AUDIO_FORMAT = stringPreferencesKey("seal_audio_format")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")
        val EMBED_DOWNLOAD_METADATA = booleanPreferencesKey("embed_download_metadata")
        val EMBED_SUBTITLES = booleanPreferencesKey("embed_subtitles")
        val SPLIT_SUBFOLDERS = booleanPreferencesKey("split_subfolders")
        val RESTRICT_FILENAMES = booleanPreferencesKey("restrict_filenames")
        val FILENAME_TEMPLATE = stringPreferencesKey("filename_template")
        val USE_COOKIES_AUTH = booleanPreferencesKey("use_cookies_auth")
        val SEAL_USER_AGENT = stringPreferencesKey("seal_user_agent")
        val AUTO_IMPORT_DOWNLOADS = booleanPreferencesKey("auto_import_downloads")

        // Minimo Video
        val DEFAULT_VIDEO_PRESET = stringPreferencesKey("default_video_preset")
        val DEFAULT_VIDEO_CODEC = stringPreferencesKey("default_video_codec")
        val DEFAULT_VIDEO_BITRATE = floatPreferencesKey("default_video_bitrate")
        val STRIP_VIDEO_AUDIO = booleanPreferencesKey("strip_video_audio")
        val HW_ENCODER_PRIORITY = booleanPreferencesKey("hw_encoder_priority")
        val KEEP_ORIGINAL_VIDEO = booleanPreferencesKey("keep_original_video")

        // Minimo Photo
        val DEFAULT_PHOTO_FORMAT = stringPreferencesKey("default_photo_format")
        val DEFAULT_PHOTO_QUALITY = intPreferencesKey("default_photo_quality")
        val DEFAULT_PHOTO_SCALE = intPreferencesKey("default_photo_scale")
        val STRIP_PHOTO_EXIF = booleanPreferencesKey("strip_photo_exif")
        val PHOTO_PREFIX = stringPreferencesKey("photo_prefix")
        val KEEP_ORIGINAL_PHOTO = booleanPreferencesKey("keep_original_photo")

        // Storage & Library
        val SKIP_SMALL_SAVINGS = booleanPreferencesKey("skip_small_savings")
        val AUTO_SCAN_LAUNCH = booleanPreferencesKey("auto_scan_launch")
        val CACHE_LIMIT = stringPreferencesKey("cache_limit")

        // Auto Update & Seal Sheet Prefs
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val UPDATE_REPO_SLUG = stringPreferencesKey("update_repo_slug")
        val DIRECT_UPDATE_FEED_URL = stringPreferencesKey("direct_update_feed_url")
        val SEAL_PREFERRED_VIDEO_FORMAT = stringPreferencesKey("seal_preferred_video_format")
        val SEAL_VIDEO_QUALITY_LIMIT = stringPreferencesKey("seal_video_quality_limit")
        val SEAL_AUDIO_QUALITY = stringPreferencesKey("seal_audio_quality")
        val SAVE_DOWNLOAD_THUMBNAIL = booleanPreferencesKey("save_download_thumbnail")
        val CONVERT_SEAL_AUDIO = booleanPreferencesKey("convert_seal_audio")
        val PREFERRED_DOWNLOAD_CODEC = stringPreferencesKey("preferred_download_codec")
        val MP3_BITRATE = stringPreferencesKey("mp3_bitrate")
        val DEFAULT_VIDEO_CONTAINER = stringPreferencesKey("default_video_container")
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    val settings: Flow<EchoSettings> = context.echoDataStore.data.map { prefs ->
        EchoSettings(
            themeMode = parseEnum(prefs[Keys.THEME_MODE], ThemeModePref.DARK),
            pureBlackAmoled = prefs[Keys.PURE_BLACK_AMOLED] ?: false,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            showMiniProgressBar = prefs[Keys.SHOW_MINI_PROGRESS] ?: true,
            fullBleedPlayer = prefs[Keys.FULL_BLEED_PLAYER] ?: false,
            playerBlurBackground = prefs[Keys.PLAYER_BLUR_BG] ?: true,
            showQualityBadge = prefs[Keys.SHOW_QUALITY_BADGE] ?: true,
            showSongDnaBadge = prefs[Keys.SHOW_SONG_DNA_BADGE] ?: true,

            audioQuality = parseEnum(prefs[Keys.AUDIO_QUALITY], AudioQualityPref.STUDIO),
            defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            crossfade = parseEnum(prefs[Keys.CROSSFADE], CrossfadePref.OFF),
            skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
            normalizeVolume = prefs[Keys.NORMALIZE_VOLUME] ?: true,
            gaplessFlow = prefs[Keys.GAPLESS_FLOW] ?: true,
            pauseOnHeadsetDisconnect = prefs[Keys.PAUSE_HEADSET_DISCONNECT] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            wordByWordLyrics = prefs[Keys.WORD_BY_WORD_LYRICS] ?: true,
            lyricsPriority = parseEnum(prefs[Keys.LYRICS_PRIORITY], LyricsPriorityPref.SYNCED_FIRST),
            chartRegion = parseEnum(prefs[Keys.CHART_REGION], ChartRegionPref.SYSTEM_DEFAULT),
            excludeExplicit = prefs[Keys.EXCLUDE_EXPLICIT] ?: false,
            autoPipForVideo = prefs[Keys.AUTO_PIP_VIDEO] ?: true,
            persistentQueue = prefs[Keys.PERSISTENT_QUEUE] ?: true,

            sealDownloadMode = parseEnum(prefs[Keys.SEAL_DOWNLOAD_MODE], SealDownloadModePref.VIDEO),
            defaultDownloadPreset = parseEnum(prefs[Keys.DEFAULT_DOWNLOAD_PRESET], DownloadFormatPreset.VIDEO_MP4_HIGH),
            sealVideoContainer = parseEnum(prefs[Keys.SEAL_VIDEO_CONTAINER], SealVideoContainerPref.MP4),
            preferredDownloadCodec = parseEnum(prefs[Keys.PREFERRED_DOWNLOAD_CODEC], VideoCodecPreference.AUTO),
            sealVideoResolution = parseEnum(prefs[Keys.SEAL_VIDEO_RES], SealVideoResolutionPref.RES_1080P),
            sealAudioFormat = parseEnum(prefs[Keys.SEAL_AUDIO_FORMAT], SealAudioFormatPref.NOT_SPECIFIED),
            sealPreferredVideoFormat = parseEnum(prefs[Keys.SEAL_PREFERRED_VIDEO_FORMAT], SealPreferredVideoFormatPref.QUALITY),
            sealVideoQualityLimit = parseEnum(prefs[Keys.SEAL_VIDEO_QUALITY_LIMIT], SealVideoQualityLimitPref.BEST),
            sealAudioQuality = parseEnum(prefs[Keys.SEAL_AUDIO_QUALITY], SealAudioQualityPref.UNLIMITED),
            mp3Bitrate = parseEnum(prefs[Keys.MP3_BITRATE], Mp3BitrateOption.KBPS_320),
            saveDownloadThumbnail = prefs[Keys.SAVE_DOWNLOAD_THUMBNAIL] ?: true,
            convertSealAudio = prefs[Keys.CONVERT_SEAL_AUDIO] ?: false,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: false,
            concurrentDownloads = prefs[Keys.CONCURRENT_DOWNLOADS] ?: 2,
            embedDownloadMetadata = prefs[Keys.EMBED_DOWNLOAD_METADATA] ?: true,
            embedSubtitles = prefs[Keys.EMBED_SUBTITLES] ?: false,
            splitSubfolders = prefs[Keys.SPLIT_SUBFOLDERS] ?: true,
            restrictFilenames = prefs[Keys.RESTRICT_FILENAMES] ?: true,
            filenameTemplate = parseEnum(prefs[Keys.FILENAME_TEMPLATE], SealFilenameTemplatePref.TITLE_ARTIST),
            useCookiesAuth = prefs[Keys.USE_COOKIES_AUTH] ?: false,
            sealUserAgent = parseEnum(prefs[Keys.SEAL_USER_AGENT], SealUserAgentPref.ANDROID_MOBILE),
            autoImportDownloads = prefs[Keys.AUTO_IMPORT_DOWNLOADS] ?: true,

            defaultVideoPreset = parseEnum(prefs[Keys.DEFAULT_VIDEO_PRESET], VideoCompressionPreset.BALANCED_720P),
            defaultVideoCodec = parseEnum(prefs[Keys.DEFAULT_VIDEO_CODEC], VideoCodecOption.AUTO),
            defaultVideoContainer = parseEnum(prefs[Keys.DEFAULT_VIDEO_CONTAINER], VideoContainerOption.MP4),
            defaultVideoBitrateMbps = prefs[Keys.DEFAULT_VIDEO_BITRATE] ?: 3.0f,
            stripVideoAudioByDefault = prefs[Keys.STRIP_VIDEO_AUDIO] ?: false,
            hardwareEncoderPriority = prefs[Keys.HW_ENCODER_PRIORITY] ?: true,
            keepOriginalVideo = prefs[Keys.KEEP_ORIGINAL_VIDEO] ?: true,

            defaultPhotoFormat = parseEnum(prefs[Keys.DEFAULT_PHOTO_FORMAT], PhotoTargetFormat.WEBP),
            defaultPhotoQuality = prefs[Keys.DEFAULT_PHOTO_QUALITY] ?: 80,
            defaultPhotoScalePercent = prefs[Keys.DEFAULT_PHOTO_SCALE] ?: 100,
            stripPhotoExif = prefs[Keys.STRIP_PHOTO_EXIF] ?: true,
            photoOutputPrefix = parseEnum(prefs[Keys.PHOTO_PREFIX], PhotoPrefixPref.ECHO),
            keepOriginalPhoto = prefs[Keys.KEEP_ORIGINAL_PHOTO] ?: true,

            skipSmallCompressionSavings = prefs[Keys.SKIP_SMALL_SAVINGS] ?: true,
            autoScanOnLaunch = prefs[Keys.AUTO_SCAN_LAUNCH] ?: true,
            cacheLimit = parseEnum(prefs[Keys.CACHE_LIMIT], CacheLimitPref.GB_1),

            autoUpdateEnabled = prefs[Keys.AUTO_UPDATE_ENABLED] ?: true,
            updateChannel = parseEnum(prefs[Keys.UPDATE_CHANNEL], UpdateChannelPref.STABLE),
            updateRepoSlug = prefs[Keys.UPDATE_REPO_SLUG]?.takeIf { it.isNotBlank() } ?: "nilimadas567345-sudo/Zero-play-",
            directUpdateFeedUrl = prefs[Keys.DIRECT_UPDATE_FEED_URL].orEmpty()
        )
    }

    // Appearance
    suspend fun setThemeMode(mode: ThemeModePref) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setPureBlackAmoled(v: Boolean) = edit { it[Keys.PURE_BLACK_AMOLED] = v }
    suspend fun setDynamicColor(v: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setShowMiniProgressBar(v: Boolean) = edit { it[Keys.SHOW_MINI_PROGRESS] = v }
    suspend fun setFullBleedPlayer(v: Boolean) = edit { it[Keys.FULL_BLEED_PLAYER] = v }
    suspend fun setPlayerBlurBackground(v: Boolean) = edit { it[Keys.PLAYER_BLUR_BG] = v }
    suspend fun setShowQualityBadge(v: Boolean) = edit { it[Keys.SHOW_QUALITY_BADGE] = v }
    suspend fun setShowSongDnaBadge(v: Boolean) = edit { it[Keys.SHOW_SONG_DNA_BADGE] = v }

    // Playback, Audio & Lyrics
    suspend fun setAudioQuality(v: AudioQualityPref) = edit { it[Keys.AUDIO_QUALITY] = v.name }
    suspend fun setDefaultSpeed(v: Float) = edit { it[Keys.DEFAULT_SPEED] = v }
    suspend fun setCrossfade(v: CrossfadePref) = edit { it[Keys.CROSSFADE] = v.name }
    suspend fun setSkipSilence(v: Boolean) = edit { it[Keys.SKIP_SILENCE] = v }
    suspend fun setNormalizeVolume(v: Boolean) = edit { it[Keys.NORMALIZE_VOLUME] = v }
    suspend fun setGaplessFlow(v: Boolean) = edit { it[Keys.GAPLESS_FLOW] = v }
    suspend fun setPauseOnHeadsetDisconnect(v: Boolean) = edit { it[Keys.PAUSE_HEADSET_DISCONNECT] = v }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setWordByWordLyrics(v: Boolean) = edit { it[Keys.WORD_BY_WORD_LYRICS] = v }
    suspend fun setLyricsPriority(v: LyricsPriorityPref) = edit { it[Keys.LYRICS_PRIORITY] = v.name }
    suspend fun setChartRegion(v: ChartRegionPref) = edit { it[Keys.CHART_REGION] = v.name }
    suspend fun setExcludeExplicit(v: Boolean) = edit { it[Keys.EXCLUDE_EXPLICIT] = v }
    suspend fun setAutoPipForVideo(v: Boolean) = edit { it[Keys.AUTO_PIP_VIDEO] = v }
    suspend fun setPersistentQueue(v: Boolean) = edit { it[Keys.PERSISTENT_QUEUE] = v }

    // Seal Downloader
    suspend fun setSealDownloadMode(v: SealDownloadModePref) = edit { it[Keys.SEAL_DOWNLOAD_MODE] = v.name }
    suspend fun setDefaultDownloadPreset(v: DownloadFormatPreset) = edit { it[Keys.DEFAULT_DOWNLOAD_PRESET] = v.name }
    suspend fun setSealVideoContainer(v: SealVideoContainerPref) = edit { it[Keys.SEAL_VIDEO_CONTAINER] = v.name }
    suspend fun setSealVideoResolution(v: SealVideoResolutionPref) = edit { it[Keys.SEAL_VIDEO_RES] = v.name }
    suspend fun setSealAudioFormat(v: SealAudioFormatPref) = edit { it[Keys.SEAL_AUDIO_FORMAT] = v.name }
    suspend fun setWifiOnlyDownloads(v: Boolean) = edit { it[Keys.WIFI_ONLY_DOWNLOADS] = v }
    suspend fun setConcurrentDownloads(v: Int) = edit { it[Keys.CONCURRENT_DOWNLOADS] = v }
    suspend fun setEmbedDownloadMetadata(v: Boolean) = edit { it[Keys.EMBED_DOWNLOAD_METADATA] = v }
    suspend fun setEmbedSubtitles(v: Boolean) = edit { it[Keys.EMBED_SUBTITLES] = v }
    suspend fun setSplitSubfolders(v: Boolean) = edit { it[Keys.SPLIT_SUBFOLDERS] = v }
    suspend fun setRestrictFilenames(v: Boolean) = edit { it[Keys.RESTRICT_FILENAMES] = v }
    suspend fun setFilenameTemplate(v: SealFilenameTemplatePref) = edit { it[Keys.FILENAME_TEMPLATE] = v.name }
    suspend fun setUseCookiesAuth(v: Boolean) = edit { it[Keys.USE_COOKIES_AUTH] = v }
    suspend fun setSealUserAgent(v: SealUserAgentPref) = edit { it[Keys.SEAL_USER_AGENT] = v.name }
    suspend fun setAutoImportDownloads(v: Boolean) = edit { it[Keys.AUTO_IMPORT_DOWNLOADS] = v }

    // Minimo Video
    suspend fun setDefaultVideoPreset(v: VideoCompressionPreset) = edit { it[Keys.DEFAULT_VIDEO_PRESET] = v.name }
    suspend fun setDefaultVideoCodec(v: VideoCodecOption) = edit { it[Keys.DEFAULT_VIDEO_CODEC] = v.name }
    suspend fun setDefaultVideoBitrateMbps(v: Float) = edit { it[Keys.DEFAULT_VIDEO_BITRATE] = v }
    suspend fun setStripVideoAudioByDefault(v: Boolean) = edit { it[Keys.STRIP_VIDEO_AUDIO] = v }
    suspend fun setHardwareEncoderPriority(v: Boolean) = edit { it[Keys.HW_ENCODER_PRIORITY] = v }
    suspend fun setKeepOriginalVideo(v: Boolean) = edit { it[Keys.KEEP_ORIGINAL_VIDEO] = v }

    // Minimo Photo
    suspend fun setDefaultPhotoFormat(v: PhotoTargetFormat) = edit { it[Keys.DEFAULT_PHOTO_FORMAT] = v.name }
    suspend fun setDefaultPhotoQuality(v: Int) = edit { it[Keys.DEFAULT_PHOTO_QUALITY] = v }
    suspend fun setDefaultPhotoScalePercent(v: Int) = edit { it[Keys.DEFAULT_PHOTO_SCALE] = v }
    suspend fun setStripPhotoExif(v: Boolean) = edit { it[Keys.STRIP_PHOTO_EXIF] = v }
    suspend fun setPhotoOutputPrefix(v: PhotoPrefixPref) = edit { it[Keys.PHOTO_PREFIX] = v.name }
    suspend fun setKeepOriginalPhoto(v: Boolean) = edit { it[Keys.KEEP_ORIGINAL_PHOTO] = v }

    // Storage & Library
    suspend fun setSkipSmallSavings(v: Boolean) = edit { it[Keys.SKIP_SMALL_SAVINGS] = v }
    suspend fun setAutoScanOnLaunch(v: Boolean) = edit { it[Keys.AUTO_SCAN_LAUNCH] = v }
    suspend fun setCacheLimit(v: CacheLimitPref) = edit { it[Keys.CACHE_LIMIT] = v.name }

    // Auto Update & Downloader Sheet Prefs
    suspend fun setAutoUpdateEnabled(v: Boolean) = edit { it[Keys.AUTO_UPDATE_ENABLED] = v }
    suspend fun setUpdateChannel(v: UpdateChannelPref) = edit { it[Keys.UPDATE_CHANNEL] = v.name }
    suspend fun setUpdateRepoSlug(v: String) = edit { it[Keys.UPDATE_REPO_SLUG] = v.trim() }
    suspend fun setDirectUpdateFeedUrl(v: String) = edit { it[Keys.DIRECT_UPDATE_FEED_URL] = v.trim() }
    suspend fun setSealPreferredVideoFormat(v: SealPreferredVideoFormatPref) = edit { it[Keys.SEAL_PREFERRED_VIDEO_FORMAT] = v.name }
    suspend fun setSealVideoQualityLimit(v: SealVideoQualityLimitPref) = edit { it[Keys.SEAL_VIDEO_QUALITY_LIMIT] = v.name }
    suspend fun setSealAudioQuality(v: SealAudioQualityPref) = edit { it[Keys.SEAL_AUDIO_QUALITY] = v.name }
    suspend fun setSaveDownloadThumbnail(v: Boolean) = edit { it[Keys.SAVE_DOWNLOAD_THUMBNAIL] = v }
    suspend fun setConvertSealAudio(v: Boolean) = edit { it[Keys.CONVERT_SEAL_AUDIO] = v }
    suspend fun setPreferredDownloadCodec(v: VideoCodecPreference) = edit { it[Keys.PREFERRED_DOWNLOAD_CODEC] = v.name }
    suspend fun setMp3Bitrate(v: Mp3BitrateOption) = edit { it[Keys.MP3_BITRATE] = v.name }
    suspend fun setDefaultVideoContainer(v: VideoContainerOption) = edit { it[Keys.DEFAULT_VIDEO_CONTAINER] = v.name }

    suspend fun resetAllSettings() {
        context.echoDataStore.edit { it.clear() }
    }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.echoDataStore.edit { block(it) }
    }
}
