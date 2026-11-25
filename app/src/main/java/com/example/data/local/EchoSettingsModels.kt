package com.example.data.local

import com.example.converter.PhotoTargetFormat
import com.example.converter.VideoCodecOption
import com.example.converter.VideoCompressionPreset
import com.example.data.model.DownloadFormatPreset

enum class ThemeModePref(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AudioQualityPref(val label: String) {
    STUDIO("Studio Lossless"),
    HIGH("High (320 kbps)"),
    BALANCED("Balanced (192 kbps)"),
    DATA_SAVER("Data Saver (128 kbps)")
}

enum class CrossfadePref(val label: String, val seconds: Int) {
    OFF("Off", 0),
    SHORT("2 seconds", 2),
    MEDIUM("5 seconds", 5),
    LONG("8 seconds", 8),
    MAX("12 seconds", 12)
}

enum class LyricsPriorityPref(val label: String) {
    SYNCED_FIRST("Synced LRC -> Embedded"),
    EMBEDDED_FIRST("Embedded Tags -> Auto"),
    AUTO_MATCH("Auto Match")
}

enum class ChartRegionPref(val label: String) {
    SYSTEM_DEFAULT("System Default"),
    UNITED_STATES("United States"),
    UNITED_KINGDOM("United Kingdom"),
    JAPAN("Japan"),
    INDIA("India"),
    GLOBAL("Global Top 100")
}

enum class SealDownloadModePref(val label: String) {
    AUDIO("Audio"),
    VIDEO("Video"),
    COMMANDS("Commands"),
    BATCH("Batch / Playlist")
}

enum class UpdateChannelPref(val label: String) {
    STABLE("Stable"),
    PREVIEW("Preview")
}

enum class SealPreferredVideoFormatPref(val label: String, val description: String) {
    LEGACY("Legacy (H.264)", "Prefer MP4 (H.264 / AAC) for maximum compatibility when sharing"),
    QUALITY("Quality (AV1/VP9/HEVC)", "Prefer modern AOMedia AV1, VP9 or HEVC codecs for higher efficiency")
}

enum class VideoCodecPreference(
    val label: String,
    val shortBadge: String,
    val mimeType: String?,
    val ytDlpSortCodec: String?
) {
    AUTO("Auto (Hardware-Aware)", "AUTO", null, null),
    AV1("AV1 (AOMedia av01)", "AV1", "video/av01", "av01"),
    VP9("VP9 (vp09)", "VP9", "video/x-vnd.on2.vp9", "vp9"),
    HEVC("HEVC / H.265 (hvc1)", "HEVC", "video/hevc", "h265"),
    H264("AVC / H.264 (avc1)", "H.264", "video/avc", "h264")
}

enum class SealVideoQualityLimitPref(val label: String) {
    BEST("Best quality"),
    P2160("2160p"),
    P1440("1440p"),
    P1080("1080p"),
    P720("720p"),
    P480("480p"),
    P360("360p"),
    LOWEST("Lowest quality")
}

enum class SealAudioQualityPref(val label: String) {
    UNLIMITED("Unlimited / V0"),
    KBPS_320("320 kbps"),
    KBPS_256("256 kbps"),
    KBPS_192("192 kbps"),
    KBPS_128("128 kbps")
}

enum class SealVideoContainerPref(val label: String, val ext: String) {
    MP4("MP4 (Universal)", "mp4"),
    MKV("MKV (AV1 / VP9 + Opus + Subs)", "mkv"),
    WEBM("WebM (VP9 / Opus)", "webm")
}

enum class SealVideoResolutionPref(val label: String, val heightPx: Int) {
    RES_2160P("2160p (4K UHD)", 2160),
    RES_1440P("1440p (QHD)", 1440),
    RES_1080P("1080p (Full HD)", 1080),
    RES_720P("720p (HD)", 720),
    RES_480P("480p (SD)", 480)
}

enum class SealAudioFormatPref(val label: String, val ext: String) {
    NOT_SPECIFIED("Not specified (default)", "m4a"),
    OPUS("Opus (libopus)", "opus"),
    MP3("MP3 (libmp3lame)", "mp3"),
    M4A("M4A / AAC", "m4a"),
    FLAC("FLAC (Lossless)", "flac")
}

enum class SealFilenameTemplatePref(val label: String) {
    TITLE_ONLY("Title"),
    TITLE_ARTIST("Title - Artist"),
    ARTIST_TITLE("Artist - Title"),
    TITLE_TIMESTAMP("Title [Timestamp]")
}

enum class SealUserAgentPref(val label: String) {
    ANDROID_MOBILE("Android Mobile"),
    DESKTOP_CHROME("Desktop Chrome"),
    SEAL_ENGINE("Media Extractor UA")
}

enum class PhotoPrefixPref(val label: String, val prefix: String) {
    ECHO("ZeroPlay_", "ZeroPlay_"),
    MINIMO("Media_", "Media_"),
    COMPRESSED("Compressed_", "Compressed_")
}

enum class CacheLimitPref(val label: String, val maxMb: Int) {
    MB_256("256 MB", 256),
    MB_512("512 MB", 512),
    GB_1("1 GB", 1024),
    GB_2("2 GB", 2048),
    UNLIMITED("Unlimited", -1)
}

data class EchoSettings(
    // Appearance
    val themeMode: ThemeModePref = ThemeModePref.DARK,
    val pureBlackAmoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val showMiniProgressBar: Boolean = true,
    val fullBleedPlayer: Boolean = false,
    val playerBlurBackground: Boolean = true,
    val showQualityBadge: Boolean = true,
    val showSongDnaBadge: Boolean = true,

    // Playback, Audio & Lyrics
    val audioQuality: AudioQualityPref = AudioQualityPref.STUDIO,
    val defaultSpeed: Float = 1.0f,
    val crossfade: CrossfadePref = CrossfadePref.OFF,
    val skipSilence: Boolean = false,
    val normalizeVolume: Boolean = true,
    val gaplessFlow: Boolean = true,
    val pauseOnHeadsetDisconnect: Boolean = true,
    val keepScreenOn: Boolean = false,
    val wordByWordLyrics: Boolean = true,
    val lyricsPriority: LyricsPriorityPref = LyricsPriorityPref.SYNCED_FIRST,
    val chartRegion: ChartRegionPref = ChartRegionPref.SYSTEM_DEFAULT,
    val excludeExplicit: Boolean = false,
    val autoPipForVideo: Boolean = true,
    val persistentQueue: Boolean = true,

    // Media Downloader (Hybrid NewPipe + yt-dlp + FFmpegKit)
    val sealDownloadMode: SealDownloadModePref = SealDownloadModePref.VIDEO,
    val defaultDownloadPreset: DownloadFormatPreset = DownloadFormatPreset.VIDEO_MP4_HIGH,
    val sealVideoContainer: SealVideoContainerPref = SealVideoContainerPref.MP4,
    val preferredDownloadCodec: VideoCodecPreference = VideoCodecPreference.AUTO,
    val sealVideoResolution: SealVideoResolutionPref = SealVideoResolutionPref.RES_1080P,
    val sealAudioFormat: SealAudioFormatPref = SealAudioFormatPref.NOT_SPECIFIED,
    val sealPreferredVideoFormat: SealPreferredVideoFormatPref = SealPreferredVideoFormatPref.QUALITY,
    val sealVideoQualityLimit: SealVideoQualityLimitPref = SealVideoQualityLimitPref.BEST,
    val sealAudioQuality: SealAudioQualityPref = SealAudioQualityPref.UNLIMITED,
    val mp3Bitrate: com.example.data.model.Mp3BitrateOption = com.example.data.model.Mp3BitrateOption.KBPS_320,
    val saveDownloadThumbnail: Boolean = true,
    val convertSealAudio: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val concurrentDownloads: Int = 2,
    val embedDownloadMetadata: Boolean = true,
    val embedSubtitles: Boolean = false,
    val splitSubfolders: Boolean = true,
    val restrictFilenames: Boolean = true,
    val filenameTemplate: SealFilenameTemplatePref = SealFilenameTemplatePref.TITLE_ARTIST,
    val useCookiesAuth: Boolean = false,
    val sealUserAgent: SealUserAgentPref = SealUserAgentPref.ANDROID_MOBILE,
    val autoImportDownloads: Boolean = true,

    // Video Converter & Compressor
    val defaultVideoPreset: VideoCompressionPreset = VideoCompressionPreset.BALANCED_720P,
    val defaultVideoCodec: VideoCodecOption = VideoCodecOption.AUTO,
    val defaultVideoContainer: com.example.data.model.VideoContainerOption = com.example.data.model.VideoContainerOption.MP4,
    val defaultVideoBitrateMbps: Float = 3.0f,
    val stripVideoAudioByDefault: Boolean = false,
    val hardwareEncoderPriority: Boolean = true,
    val keepOriginalVideo: Boolean = true,

    // Photo Compressor
    val defaultPhotoFormat: PhotoTargetFormat = PhotoTargetFormat.WEBP,
    val defaultPhotoQuality: Int = 80,
    val defaultPhotoScalePercent: Int = 100,
    val stripPhotoExif: Boolean = true,
    val photoOutputPrefix: PhotoPrefixPref = PhotoPrefixPref.ECHO,
    val keepOriginalPhoto: Boolean = true,

    // Storage & Library
    val skipSmallCompressionSavings: Boolean = true,
    val autoScanOnLaunch: Boolean = true,
    val autoDownloadArtistImages: Boolean = true,
    val cacheLimit: CacheLimitPref = CacheLimitPref.GB_1,

    // 10-Band Equalizer & Audio Effects
    val eqEnabled: Boolean = false,
    val eqPreset: com.example.playback.EqualizerPreset = com.example.playback.EqualizerPreset.FLAT,
    val eqBandLevelsDb: List<Float> = com.example.playback.EqualizerPreset.FLAT.bandLevelsDb,
    val eqBassBoostEnabled: Boolean = false,
    val eqBassBoostStrength: Int = 0,
    val eqVirtualizerEnabled: Boolean = false,
    val eqVirtualizerStrength: Int = 0,

    // Auto Update (GitHub Releases / Direct Feed)
    val autoUpdateEnabled: Boolean = true,
    val updateChannel: UpdateChannelPref = UpdateChannelPref.STABLE,
    val updateRepoSlug: String = "tanmaydatta-15/Zero-play-",
    val directUpdateFeedUrl: String = ""
) {
    fun toEqualizerState(): com.example.playback.EqualizerState =
        com.example.playback.EqualizerState(
            enabled = eqEnabled,
            preset = eqPreset,
            bandLevelsDb = eqBandLevelsDb,
            bassBoostEnabled = eqBassBoostEnabled,
            bassBoostStrength = eqBassBoostStrength,
            virtualizerEnabled = eqVirtualizerEnabled,
            virtualizerStrength = eqVirtualizerStrength
        )
}
