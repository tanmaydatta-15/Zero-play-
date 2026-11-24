package com.example.downloader

import android.content.Context
import com.example.data.local.SealAudioFormatPref
import com.example.data.local.SealPreferredVideoFormatPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.VideoContainerOption
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class YtDlpOutput(
    val file: File,
    val title: String?,
    val artist: String?,
    val extension: String,
    val isVideo: Boolean
)

data class YtDlpDirectStreams(
    val title: String?,
    val artist: String?,
    val durationSec: Long,
    val videoUrl: String?,
    val videoExtension: String?,
    val videoHeaders: Map<String, String>,
    val videoCodecBadge: String?,
    val audioUrl: String?,
    val audioExtension: String?,
    val audioHeaders: Map<String, String>
)

data class TransferProgress(
    val fraction: Float,
    val speedLabel: String? = null,
    val etaLabel: String? = null
)

class YtDlpEngine(private val context: Context) {

    companion object {
        private val SPEED_REGEX = Regex("""at\s+([0-9.]+[KMG]i?B/s)""", RegexOption.IGNORE_CASE)

        internal fun tokenizeCommandFlags(raw: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quoteChar: Char? = null
            for (ch in raw.trim()) {
                when {
                    quoteChar != null -> {
                        if (ch == quoteChar) quoteChar = null
                        else current.append(ch)
                    }
                    ch == '"' || ch == '\'' -> quoteChar = ch
                    ch.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            tokens += current.toString()
                            current.clear()
                        }
                    }
                    else -> current.append(ch)
                }
            }
            if (current.isNotEmpty()) tokens += current.toString()
            return tokens
        }

        internal fun buildFormatSelector(
            preset: DownloadFormatPreset,
            maxVideoHeightPx: Int,
            preferredVideoFormat: SealPreferredVideoFormatPref,
            preferredCodec: VideoCodecPreference = VideoCodecPreference.AUTO,
            container: VideoContainerOption = VideoContainerOption.MP4,
            audioFormat: SealAudioFormatPref = SealAudioFormatPref.NOT_SPECIFIED
        ): String {
            if (!preset.isVideo) {
                return when (audioFormat) {
                    SealAudioFormatPref.OPUS -> "bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best"
                    SealAudioFormatPref.M4A -> "bestaudio[ext=m4a]/bestaudio/best"
                    else -> "bestaudio/best"
                }
            }
            val height = maxVideoHeightPx.coerceAtLeast(360)
            val audioSel = if (container != VideoContainerOption.MP4 || audioFormat == SealAudioFormatPref.OPUS) {
                "ba[acodec^=opus]/ba"
            } else {
                "ba[acodec^=mp4a]/ba[ext=m4a]/ba"
            }
            val codecSelector = when (preferredCodec) {
                VideoCodecPreference.AV1 -> "bv*[height<=${height}][vcodec^=av01]+$audioSel/"
                VideoCodecPreference.VP9 -> "bv*[height<=${height}][vcodec^=vp9]+$audioSel/bv*[height<=${height}][vcodec^=vp09]+$audioSel/"
                VideoCodecPreference.HEVC -> "bv*[height<=${height}][vcodec^=hev1]+$audioSel/bv*[height<=${height}][vcodec^=hvc1]+$audioSel/"
                VideoCodecPreference.H264 -> "bv*[height<=${height}][vcodec^=avc1]+$audioSel/"
                VideoCodecPreference.AUTO -> ""
            }
            return if (preferredVideoFormat == SealPreferredVideoFormatPref.LEGACY && preferredCodec == VideoCodecPreference.AUTO) {
                "bv*[height<=${height}][vcodec^=avc1]+ba[acodec^=mp4a]/" +
                    "bv*[height<=${height}][ext=mp4]+ba[ext=m4a]/" +
                    "b[height<=${height}][ext=mp4]/" +
                    "bv*[height<=${height}]+ba/b[height<=${height}]/best"
            } else {
                "${codecSelector}bv*[height<=${height}]+$audioSel/b[height<=${height}]/bv*+ba/best"
            }
        }

        internal fun formatEtaSeconds(etaSec: Long): String? {
            if (etaSec <= 0L || etaSec > 86_400L) return null
            val mins = etaSec / 60L
            val secs = etaSec % 60L
            return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        }
    }

    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    @Volatile
    private var ytDlpReady = false

    @Volatile
    private var autoUpdatedOnce = false

    suspend fun ensureInitialized(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (ytDlpReady) return@withContext Result.success(true)
        initMutex.withLock {
            if (ytDlpReady) return@withContext Result.success(true)
            runCatching {
                YoutubeDL.getInstance().init(appContext)
                ytDlpReady = true
                true
            }
        }
    }

    fun isFfmpegAvailable(): Boolean = true

    suspend fun getVersionLabel(): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        val ytVer = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()
        val ffLabel = "FFmpegKit full-gpl"
        if (!ytVer.isNullOrBlank()) {
            "NewPipe + yt-dlp $ytVer • $ffLabel"
        } else {
            "NewPipe + yt-dlp • $ffLabel"
        }
    }

    suspend fun updateYtDlp(): Result<String> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized()
        if (initRes.isFailure) {
            return@withContext Result.failure(
                initRes.exceptionOrNull() ?: IllegalStateException("yt-dlp unavailable in this environment")
            )
        }
        runCatching {
            val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel._STABLE)
            val ver = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()
            when (status?.name) {
                "DONE" -> "Updated yt-dlp${ver?.let { " to $it" } ?: ""}"
                "ALREADY_UP_TO_DATE" -> "yt-dlp is already up to date${ver?.let { " ($it)" } ?: ""}"
                else -> "yt-dlp check complete${ver?.let { " ($it)" } ?: ""}"
            }
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /**
     * Resolves direct CDN media URLs + HTTP headers via yt-dlp without downloading through Python,
     * allowing HybridExtractionRouter to pull the streams at full speed via 8x parallel OkHttp Range workers
     * and mux/convert them natively with FFmpegKit full-gpl.
     */
    suspend fun extractDirectStreams(
        url: String,
        preset: DownloadFormatPreset,
        maxVideoHeightPx: Int,
        preferredVideoFormat: SealPreferredVideoFormatPref,
        preferredCodec: VideoCodecPreference,
        videoContainer: VideoContainerOption,
        audioFormat: SealAudioFormatPref
    ): Result<YtDlpDirectStreams> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized()
        if (initRes.isFailure) {
            return@withContext Result.failure(
                initRes.exceptionOrNull() ?: IllegalStateException("yt-dlp initialization failed")
            )
        }

        runCatching {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-check-formats")
            request.addOption("--socket-timeout", "10")
            val selector = buildFormatSelector(
                preset = preset,
                maxVideoHeightPx = maxVideoHeightPx,
                preferredVideoFormat = preferredVideoFormat,
                preferredCodec = preferredCodec,
                container = videoContainer,
                audioFormat = audioFormat
            )
            request.addOption("-f", selector)
            if (preset.isVideo) {
                preferredCodec.ytDlpSortCodec?.let { sortCodec ->
                    request.addOption("-S", "vcodec:$sortCodec,res:${maxVideoHeightPx.coerceAtLeast(360)}")
                }
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val baseHeaders = info.httpHeaders?.filterValues { it.isNotBlank() }.orEmpty()
            val requested = info.requestedFormats.orEmpty()

            fun isDirectHttpMedia(streamUrl: String?): Boolean {
                if (streamUrl.isNullOrBlank()) return false
                val lower = streamUrl.lowercase()
                if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
                if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("manifest")) return false
                return true
            }

            var videoUrl: String? = null
            var videoExt: String? = null
            var videoHeaders: Map<String, String> = baseHeaders
            var videoCodecBadge: String? = null
            var audioUrl: String? = null
            var audioExt: String? = null
            var audioHeaders: Map<String, String> = baseHeaders

            if (requested.isNotEmpty()) {
                for (fmt in requested) {
                    val fmtUrl = fmt.url
                    if (!isDirectHttpMedia(fmtUrl)) continue
                    val vcodec = fmt.vcodec.orEmpty().lowercase()
                    val acodec = fmt.acodec.orEmpty().lowercase()
                    val hdrs = fmt.httpHeaders?.filterValues { it.isNotBlank() }?.ifEmpty { baseHeaders } ?: baseHeaders
                    val hasVideo = vcodec.isNotBlank() && vcodec != "none"
                    val hasAudio = acodec.isNotBlank() && acodec != "none"
                    if (hasVideo && videoUrl == null) {
                        videoUrl = fmtUrl
                        videoExt = fmt.ext?.lowercase()?.ifBlank { "mp4" }
                        videoHeaders = hdrs
                        videoCodecBadge = com.example.converter.FfmpegKitEngine.normalizeCodecBadge(vcodec)
                    } else if (hasAudio && audioUrl == null) {
                        audioUrl = fmtUrl
                        audioExt = fmt.ext?.lowercase()?.ifBlank { "m4a" }
                        audioHeaders = hdrs
                    }
                }
            } else if (isDirectHttpMedia(info.url)) {
                if (preset.isVideo) {
                    videoUrl = info.url
                    videoExt = info.ext?.lowercase()?.ifBlank { "mp4" }
                    videoHeaders = baseHeaders
                } else {
                    audioUrl = info.url
                    audioExt = info.ext?.lowercase()?.ifBlank { "m4a" }
                    audioHeaders = baseHeaders
                }
            }

            if (preset.isVideo && videoUrl == null) {
                error("No direct downloadable video stream returned by yt-dlp")
            }
            if (!preset.isVideo && audioUrl == null && videoUrl == null) {
                error("No direct downloadable audio stream returned by yt-dlp")
            }

            YtDlpDirectStreams(
                title = info.title?.trim()?.takeIf { it.isNotBlank() },
                artist = info.uploader?.trim()?.takeIf { it.isNotBlank() } ?: inferSiteLabel(url),
                durationSec = info.duration.toLong().coerceAtLeast(0L),
                videoUrl = videoUrl,
                videoExtension = videoExt,
                videoHeaders = videoHeaders,
                videoCodecBadge = videoCodecBadge,
                audioUrl = audioUrl ?: if (!preset.isVideo) videoUrl else null,
                audioExtension = audioExt ?: if (!preset.isVideo) videoExt else null,
                audioHeaders = audioHeaders
            )
        }
    }

    suspend fun downloadMedia(
        processId: String,
        url: String,
        workDir: File,
        preset: DownloadFormatPreset,
        maxVideoHeightPx: Int,
        preferredVideoFormat: SealPreferredVideoFormatPref,
        preferredCodec: VideoCodecPreference,
        videoContainer: VideoContainerOption,
        audioFormat: SealAudioFormatPref,
        mp3Bitrate: Mp3BitrateOption,
        embedMetadata: Boolean,
        embedSubtitles: Boolean,
        saveThumbnail: Boolean,
        restrictFilenames: Boolean,
        customCommandFlags: String?,
        onProgress: (TransferProgress) -> Unit
    ): Result<YtDlpOutput> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized()
        if (initRes.isFailure) {
            return@withContext Result.failure(
                initRes.exceptionOrNull() ?: IllegalStateException("yt-dlp initialization failed")
            )
        }

        if (!workDir.exists()) workDir.mkdirs()

        val firstAttempt = executeRequest(
            processId = processId,
            url = url,
            workDir = workDir,
            preset = preset,
            maxVideoHeightPx = maxVideoHeightPx,
            preferredVideoFormat = preferredVideoFormat,
            preferredCodec = preferredCodec,
            videoContainer = videoContainer,
            audioFormat = audioFormat,
            mp3Bitrate = mp3Bitrate,
            embedMetadata = embedMetadata,
            embedSubtitles = embedSubtitles,
            saveThumbnail = saveThumbnail,
            restrictFilenames = restrictFilenames,
            customCommandFlags = customCommandFlags,
            extraPlayerClients = null,
            onProgress = onProgress
        )

        if (firstAttempt.isSuccess) return@withContext firstAttempt

        val errMsg = firstAttempt.exceptionOrNull()?.message.orEmpty()
        val isExtractorOr403 = errMsg.contains("403", ignoreCase = true) ||
            errMsg.contains("Signature", ignoreCase = true) ||
            errMsg.contains("nsig", ignoreCase = true) ||
            errMsg.contains("Unable to extract", ignoreCase = true) ||
            errMsg.contains("Sign in", ignoreCase = true)

        if (isExtractorOr403) {
            if (!autoUpdatedOnce) {
                autoUpdatedOnce = true
                runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel._STABLE)
                }
            }
            workDir.listFiles()?.forEach { runCatching { it.delete() } }
            val retryAttempt = executeRequest(
                processId = "${processId}_r2",
                url = url,
                workDir = workDir,
                preset = preset,
                maxVideoHeightPx = maxVideoHeightPx,
                preferredVideoFormat = preferredVideoFormat,
                preferredCodec = preferredCodec,
                videoContainer = videoContainer,
                audioFormat = audioFormat,
                mp3Bitrate = mp3Bitrate,
                embedMetadata = embedMetadata,
                embedSubtitles = embedSubtitles,
                saveThumbnail = saveThumbnail,
                restrictFilenames = restrictFilenames,
                customCommandFlags = customCommandFlags,
                extraPlayerClients = "youtube:player_client=android_vr,tv,web_embedded",
                onProgress = onProgress
            )
            if (retryAttempt.isSuccess) return@withContext retryAttempt
        }

        firstAttempt
    }

    private fun executeRequest(
        processId: String,
        url: String,
        workDir: File,
        preset: DownloadFormatPreset,
        maxVideoHeightPx: Int,
        preferredVideoFormat: SealPreferredVideoFormatPref,
        preferredCodec: VideoCodecPreference,
        videoContainer: VideoContainerOption,
        audioFormat: SealAudioFormatPref,
        mp3Bitrate: Mp3BitrateOption,
        embedMetadata: Boolean,
        embedSubtitles: Boolean,
        saveThumbnail: Boolean,
        restrictFilenames: Boolean,
        customCommandFlags: String?,
        extraPlayerClients: String?,
        onProgress: (TransferProgress) -> Unit
    ): Result<YtDlpOutput> {
        return runCatching {
            val request = YoutubeDLRequest(url)
            val hasCustomCommands = !customCommandFlags.isNullOrBlank()

            if (hasCustomCommands) {
                val tokens = tokenizeCommandFlags(customCommandFlags!!)
                var i = 0
                var hasOutputTemplate = false
                while (i < tokens.size) {
                    val tok = tokens[i]
                    if (tok == "-o" || tok == "--output") hasOutputTemplate = true
                    if (tok.startsWith("-") && i + 1 < tokens.size && !tokens[i + 1].startsWith("-")) {
                        request.addOption(tok, tokens[i + 1])
                        i += 2
                    } else {
                        request.addOption(tok)
                        i += 1
                    }
                }
                if (!hasOutputTemplate) {
                    request.addOption("-o", "${workDir.absolutePath}/%(title).80s.%(ext)s")
                }
            } else {
                request.addOption("-o", "${workDir.absolutePath}/%(title).80s.%(ext)s")
                request.addOption("--no-mtime")
                request.addOption("--no-playlist")
                request.addOption("--no-part")
                request.addOption("--no-check-formats")
                request.addOption("--concurrent-fragments", "8")
                request.addOption("--http-chunk-size", "4M")
                request.addOption("--buffer-size", "128K")
                request.addOption("--socket-timeout", "12")
                request.addOption("--retries", "3")
                request.addOption("--fragment-retries", "3")
                request.addOption("--skip-unavailable-fragments")

                if (restrictFilenames) {
                    request.addOption("--restrict-filenames")
                }

                if (!preset.isVideo) {
                    request.addOption(
                        "-f",
                        buildFormatSelector(
                            preset = preset,
                            maxVideoHeightPx = maxVideoHeightPx,
                            preferredVideoFormat = preferredVideoFormat,
                            preferredCodec = preferredCodec,
                            container = videoContainer,
                            audioFormat = audioFormat
                        )
                    )
                } else {
                    val height = maxVideoHeightPx.coerceAtLeast(360)
                    // Without bundled youtubedl-ffmpeg, prefer muxed single-file video streams in fallback mode
                    val fallbackVideoSelector = "b[height<=${height}][ext=mp4]/b[height<=${height}]/best"
                    request.addOption("-f", fallbackVideoSelector)
                    preferredCodec.ytDlpSortCodec?.let { sortCodec ->
                        request.addOption("-S", "vcodec:$sortCodec,res:${height}")
                    }
                }

                if (embedSubtitles) {
                    request.addOption("--write-subs")
                }
            }

            if (!extraPlayerClients.isNullOrBlank()) {
                request.addOption("--extractor-args", extraPlayerClients)
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val speed = line?.let { SPEED_REGEX.find(it)?.groupValues?.getOrNull(1) }
                val eta = formatEtaSeconds(etaInSeconds)
                if (progress >= 0f) {
                    onProgress(
                        TransferProgress(
                            fraction = (progress / 100f).coerceIn(0f, 0.99f),
                            speedLabel = speed,
                            etaLabel = eta
                        )
                    )
                }
            }

            val ignoredExts = setOf("part", "ytdl", "tmp", "jpg", "jpeg", "png", "webp", "vtt", "srt", "ass", "json")
            val producedFile = workDir.listFiles()
                ?.filter { it.isFile && it.length() > 512L && it.extension.lowercase() !in ignoredExts }
                ?.maxByOrNull { it.length() }
                ?: error("yt-dlp finished without writing an output media file")

            val ext = producedFile.extension.lowercase().ifBlank {
                if (preset.isVideo) videoContainer.ext else "m4a"
            }
            val inferredTitle = producedFile.nameWithoutExtension
                .replace('_', ' ')
                .trim()
                .takeIf { it.isNotBlank() }

            val isOutVideo = ext in setOf("mp4", "mkv", "webm", "mov", "avi")
            YtDlpOutput(
                file = producedFile,
                title = inferredTitle,
                artist = inferSiteLabel(url),
                extension = ext,
                isVideo = if (hasCustomCommands) isOutVideo else preset.isVideo
            )
        }
    }

    private fun inferSiteLabel(url: String): String? {
        val low = url.lowercase()
        return when {
            low.contains("youtube") || low.contains("youtu.be") -> "YouTube"
            low.contains("instagram") -> "Instagram"
            low.contains("tiktok") -> "TikTok"
            low.contains("x.com") || low.contains("twitter.com") -> "X"
            low.contains("facebook") || low.contains("fb.watch") -> "Facebook"
            low.contains("reddit") -> "Reddit"
            low.contains("soundcloud") -> "SoundCloud"
            low.contains("bandcamp") -> "Bandcamp"
            else -> null
        }
    }
}
