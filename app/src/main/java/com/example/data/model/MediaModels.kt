package com.example.data.model

import androidx.annotation.DrawableRes

enum class MediaType {
    AUDIO,
    VIDEO
}

enum class MediaSource {
    CURATED,
    LOCAL,
    DOWNLOADED,
    EXPORTED,
    CACHED
}

enum class VideoContainerOption(val label: String, val ext: String, val mimeType: String) {
    MP4("MP4", "mp4", "video/mp4"),
    MKV("MKV (Matroska)", "mkv", "video/x-matroska"),
    WEBM("WebM", "webm", "video/webm")
}

enum class AudioTargetFormat(val label: String, val ext: String, val mimeType: String, val ffmpegCodec: String) {
    OPUS("Opus", "opus", "audio/opus", "libopus"),
    MP3("MP3", "mp3", "audio/mpeg", "libmp3lame"),
    M4A("M4A (AAC)", "m4a", "audio/mp4", "aac"),
    FLAC("FLAC (Lossless)", "flac", "audio/flac", "flac")
}

enum class Mp3BitrateOption(val label: String, val kbps: Int?, val ytDlpQuality: String, val ffmpegArg: List<String>) {
    KBPS_320("320 kbps", 320, "320K", listOf("-b:a", "320k")),
    KBPS_256("256 kbps", 256, "256K", listOf("-b:a", "256k")),
    KBPS_192("192 kbps", 192, "192K", listOf("-b:a", "192k")),
    KBPS_128("128 kbps", 128, "128K", listOf("-b:a", "128k")),
    V0("V0 (VBR ~245k)", null, "0", listOf("-q:a", "0"))
}

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class MediaTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    @DrawableRes val artworkResId: Int? = null,
    val artworkUriString: String? = null,
    val mediaType: MediaType = MediaType.AUDIO,
    val source: MediaSource = MediaSource.CURATED,
    val moodTag: String = "Feel good",
    val playCount: Int = 0,
    val isLiked: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "audio/wav",
    val sizeBytes: Long = 0L,
    val lyrics: List<LyricLine> = emptyList(),
    val albumArtist: String = "",
    val composer: String = "",
    val genre: String = "",
    val lyricist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val year: String = "",
    val comment: String = "",
    val embeddedLyrics: String = "",
    val folderPath: String = ""
) {
    /**
     * Derives accurate format/codec badges (`AV1`, `OPUS`, `MKV`, `MP3`, `MP4`, `FLAC`, `HEVC`, `VP9`, `M4A`)
     * from codec hints in artist/album metadata, MIME type, and file extension.
     */
    val formatBadges: List<String>
        get() {
            val badges = linkedSetOf<String>()
            val meta = "$title $artist $album".uppercase()
            val lowerMime = mimeType.lowercase()
            val cleanUri = uriString.substringBefore('?').lowercase()

            when {
                meta.contains("AV1") || meta.contains("AV01") || lowerMime.contains("av01") -> badges += "AV1"
                meta.contains("HEVC") || meta.contains("H.265") || meta.contains("H265") -> badges += "HEVC"
                meta.contains("VP9") || meta.contains("VP09") -> badges += "VP9"
                meta.contains("H.264") || meta.contains("AVC") -> badges += "H.264"
            }

            when {
                lowerMime.contains("opus") || cleanUri.endsWith(".opus") || meta.contains("OPUS") -> badges += "OPUS"
                lowerMime.contains("flac") || cleanUri.endsWith(".flac") || meta.contains("FLAC") -> badges += "FLAC"
                lowerMime.contains("mpeg") || cleanUri.endsWith(".mp3") || meta.contains("MP3") -> badges += "MP3"
                lowerMime.contains("matroska") || cleanUri.endsWith(".mkv") || meta.contains("MKV") -> badges += "MKV"
                lowerMime.contains("webm") || cleanUri.endsWith(".webm") || meta.contains("WEBM") -> badges += "WEBM"
                cleanUri.endsWith(".m4a") || meta.contains("M4A") || lowerMime == "audio/mp4" -> badges += "M4A"
                lowerMime.contains("video/mp4") || cleanUri.endsWith(".mp4") || meta.contains("MP4") -> badges += "MP4"
                lowerMime.contains("wav") || cleanUri.endsWith(".wav") -> badges += "WAV"
            }
            return badges.take(2)
        }
}

data class ArtistChartItem(
    val rank: Int,
    val name: String,
    val subtitle: String,
    val monthlyListeners: String,
    @DrawableRes val artworkResId: Int
)

data class PlaylistUi(
    val id: Long,
    val name: String,
    val tracks: List<MediaTrack>,
    @DrawableRes val coverResId: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ConversionType(val label: String) {
    PHOTO_COMPRESS("Photo Convert & Compress"),
    VIDEO_COMPRESS("Video Convert & Compress"),
    AUDIO_EXTRACT("Audio Extract & Convert")
}

enum class JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

data class ConversionJobUi(
    val id: String,
    val title: String,
    val type: ConversionType,
    val detailLabel: String,
    val progress: Float,
    val status: JobStatus,
    val inputUriString: String? = null,
    val outputUriString: String? = null,
    val sizeBeforeBytes: Long = 0L,
    val sizeAfterBytes: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class DownloadFormatPreset(
    val label: String,
    val subtitle: String,
    val extension: String,
    val isVideo: Boolean
) {
    VIDEO_MKV_HEVC("Video MKV • H.265 + Opus", "HEVC (H.265) + Opus lossless remux • .mkv", "mkv", true),
    VIDEO_MKV_AV1("Video MKV • AV1 + Opus", "AOMedia AV1 + Opus lossless mux • .mkv", "mkv", true),
    VIDEO_MKV_HIGH("Video MKV • 1080p Universal", "VP9 / HEVC / H.264 + Opus • .mkv", "mkv", true),
    VIDEO_MP4_4K("Video MP4 • 2160p 4K", "Ultra HD AV1 / HEVC / H.264 • .mp4", "mp4", true),
    VIDEO_MP4_HIGH("Video MP4 • 1080p HD", "1080p Hardware-Aware • .mp4", "mp4", true),
    VIDEO_MP4_STANDARD("Video MP4 • 720p", "720p Balanced • .mp4", "mp4", true),
    VIDEO_MP4_COMPACT("Video MP4 • 480p", "480p Data Saver • .mp4", "mp4", true),
    AUDIO_OPUS_HIGH("Audio Opus • Studio", "160 kbps libopus • .opus", "opus", false),
    AUDIO_MP3_STANDARD("Audio MP3 • Portable", "320/192 kbps libmp3lame • .mp3", "mp3", false),
    AUDIO_M4A_HIGH("Audio M4A • AAC", "256 kbps AAC • .m4a", "m4a", false),
    AUDIO_FLAC_LOSSLESS("Audio FLAC • Lossless", "24-bit Lossless • .flac", "flac", false)
}

data class DownloadTaskUi(
    val id: String,
    val downloadManagerId: Long,
    val url: String,
    val title: String,
    val preset: DownloadFormatPreset,
    val progress: Float,
    val status: JobStatus,
    val localUriString: String? = null,
    val errorMessage: String? = null,
    val speedLabel: String? = null,
    val etaLabel: String? = null,
    val engineLabel: String? = null,
    val formatBadge: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class LibraryQuickCategory(val title: String) {
    LIKED("Liked"),
    DOWNLOADED("Downloaded"),
    EXPORTED("Exported"),
    CACHED("Cached"),
    TOP_50("My top 50"),
    BOTTOM_50("My bottom 50"),
    LOCAL("Local")
}

enum class LibrarySortOption(val label: String) {
    DATE_ADDED("Date added"),
    TITLE("Title"),
    PLAY_COUNT("Most played")
}
