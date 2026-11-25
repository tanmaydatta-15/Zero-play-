package com.example.downloader

import android.content.Context
import com.example.converter.FfmpegKitEngine
import com.example.data.local.SealAudioFormatPref
import com.example.data.local.SealPreferredVideoFormatPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.AudioTargetFormat
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.VideoContainerOption
import com.example.diagnostics.DeviceCapabilityInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class ExtractionRoute(val badge: String) {
    NEWPIPE_FAST("NewPipe"),
    YTDLP_UNIVERSAL("yt-dlp"),
    DIRECT_STREAM("Direct")
}

data class HybridDownloadRequest(
    val taskId: String,
    val url: String,
    val workDir: File,
    val preset: DownloadFormatPreset,
    val maxVideoHeightPx: Int,
    val preferredVideoFormat: SealPreferredVideoFormatPref,
    val preferredCodec: VideoCodecPreference,
    val videoContainer: VideoContainerOption,
    val audioFormat: SealAudioFormatPref,
    val mp3Bitrate: Mp3BitrateOption,
    val embedMetadata: Boolean,
    val embedSubtitles: Boolean,
    val saveThumbnail: Boolean,
    val restrictFilenames: Boolean,
    val customCommandFlags: String?
)

data class HybridDownloadOutput(
    val file: File,
    val title: String?,
    val artist: String?,
    val extension: String,
    val isVideo: Boolean,
    val route: ExtractionRoute,
    val codecBadge: String?
)

class HybridExtractionRouter private constructor(private val context: Context) {

    companion object {
        internal const val PARALLEL_CONNECTIONS = 8
        private const val MIN_PARALLEL_BYTES = 512_000L
        internal const val MAX_RANGE_SLICE_BYTES = 4L * 1024L * 1024L // 4 MB slices bypass YouTube/CDN single-stream throttling
        private const val STREAM_BUFFER_BYTES = 256 * 1024

        private val DIRECT_MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mp3", "opus", "m4a", "flac", "ogg", "wav", "m3u8"
        )

        @Volatile
        private var instance: HybridExtractionRouter? = null

        fun getInstance(context: Context): HybridExtractionRouter {
            return instance ?: synchronized(this) {
                instance ?: HybridExtractionRouter(context.applicationContext).also { instance = it }
            }
        }

        fun isDirectStreamUrl(rawUrl: String): Boolean {
            val clean = rawUrl.substringBefore('?').substringBefore('#').lowercase()
            val ext = clean.substringAfterLast('.', "")
            return ext in DIRECT_MEDIA_EXTENSIONS
        }

        internal fun planParallelChunks(totalBytes: Long, maxSliceBytes: Long = MAX_RANGE_SLICE_BYTES): List<LongRange> {
            if (totalBytes <= 0L) return emptyList()
            val sliceSize = minOf(
                maxSliceBytes,
                maxOf(512L * 1024L, (totalBytes + PARALLEL_CONNECTIONS - 1) / PARALLEL_CONNECTIONS)
            )
            val ranges = mutableListOf<LongRange>()
            var start = 0L
            while (start < totalBytes) {
                val end = minOf(totalBytes - 1L, start + sliceSize - 1L)
                ranges += start..end
                start = end + 1L
            }
            return ranges
        }

        /**
         * Preserves H.265 (HEVC) video + Opus audio without transcoding by routing to `.mkv`
         * (`-map 0:v:0 -map 1:a:0 -c:v copy -c:a copy`), so Opus plays natively in Media3 ExoPlayer.
         */
        internal fun resolveOutputContainer(
            requestedContainer: VideoContainerOption,
            preset: DownloadFormatPreset,
            videoCodecBadge: String?,
            audioCodecBadge: String?,
            audioExtension: String? = null
        ): VideoContainerOption {
            if (preset.extension.equals("mkv", ignoreCase = true) || requestedContainer == VideoContainerOption.MKV) {
                return VideoContainerOption.MKV
            }
            val isHevcVideo = videoCodecBadge.equals("HEVC", ignoreCase = true) ||
                videoCodecBadge.equals("H.265", ignoreCase = true)
            val isOpusAudio = audioCodecBadge.equals("OPUS", ignoreCase = true) ||
                audioExtension.equals("opus", ignoreCase = true) ||
                audioExtension.equals("webm", ignoreCase = true)
            if (isHevcVideo && isOpusAudio) {
                return VideoContainerOption.MKV
            }
            return requestedContainer
        }
    }

    private val streamClient: OkHttpClient by lazy {
        val highThroughputDispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20 // Default OkHttp limit is 5, which severely throttles parallel Range + A/V chunks
        }
        OkHttpClient.Builder()
            .dispatcher(highThroughputDispatcher)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val newPipeEngine by lazy { NewPipeEngine(streamClient) }
    val ytDlpEngine = YtDlpEngine(context)
    private val legacyResolver = MediaUrlResolver()

    private val cancelledTasks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun initializeEngines() = withContext(Dispatchers.IO) {
        coroutineScope {
            val np = async { runCatching { newPipeEngine.ensureInitialized() } }
            val yt = async { runCatching { ytDlpEngine.ensureInitialized() } }
            val ff = async { runCatching { FfmpegKitEngine.ensureInitialized() } }
            awaitAll(np, yt, ff)
        }
    }

    fun cancel(taskId: String) {
        cancelledTasks.add(taskId)
        ytDlpEngine.cancel(taskId)
        ytDlpEngine.cancel("${taskId}_mux")
        ytDlpEngine.cancel("${taskId}_aud")
        ytDlpEngine.cancel("${taskId}_r2")
        ytDlpEngine.cancel("${taskId}_r2_mux")
        ytDlpEngine.cancel("${taskId}_r2_aud")
        FfmpegKitEngine.cancelTask(taskId)
    }

    suspend fun execute(
        req: HybridDownloadRequest,
        onRouteSelected: (ExtractionRoute) -> Unit,
        onProgress: (TransferProgress) -> Unit
    ): Result<HybridDownloadOutput> = withContext(Dispatchers.IO) {
        cancelledTasks.remove(req.taskId)
        if (!req.workDir.exists()) req.workDir.mkdirs()

        // Resolve AUTO codec using hardware capability inspector
        val effectiveCodec = if (req.preferredCodec == VideoCodecPreference.AUTO) {
            runCatching { DeviceCapabilityInspector.recommendedAutoVideoCodec(context) }
                .getOrDefault(VideoCodecPreference.H264)
        } else {
            req.preferredCodec
        }

        val effectiveContainer = when {
            req.preset.extension == "mkv" -> VideoContainerOption.MKV
            else -> req.videoContainer
        }

        // Route 3 (Direct Stream fast check): raw .mp4/.mkv/.webm/.mp3/.opus/.m4a/.flac/.m3u8 URLs
        if (req.customCommandFlags.isNullOrBlank() && isDirectStreamUrl(req.url)) {
            onRouteSelected(ExtractionRoute.DIRECT_STREAM)
            val directRes = downloadDirectStream(req, effectiveContainer, onProgress)
            if (directRes.isSuccess) return@withContext directRes
        }

        // Route 1 (Fast Path): NewPipeExtractor + multi-connection parallel Range download + concurrent A/V fetch
        if (req.customCommandFlags.isNullOrBlank() && newPipeEngine.isFastPathUrl(req.url)) {
            onRouteSelected(ExtractionRoute.NEWPIPE_FAST)
            val fastRes = executeNewPipeFastPath(req, effectiveCodec, effectiveContainer, onProgress)
            if (fastRes.isSuccess) return@withContext fastRes
        }

        if (cancelledTasks.contains(req.taskId)) {
            return@withContext Result.failure(IllegalStateException("Cancelled"))
        }

        // Route 2A (Accelerated Universal): extract direct CDN URLs via yt-dlp, then fetch with 8x parallel Range workers + FFmpegKit muxing
        onRouteSelected(ExtractionRoute.YTDLP_UNIVERSAL)
        if (req.customCommandFlags.isNullOrBlank()) {
            val accelYtRes = executeYtDlpAcceleratedPath(req, effectiveCodec, effectiveContainer, onProgress)
            if (accelYtRes.isSuccess) return@withContext accelYtRes
            if (cancelledTasks.contains(req.taskId)) {
                return@withContext Result.failure(IllegalStateException("Cancelled"))
            }
        }

        // Route 2B (Universal Fallback): yt-dlp-android with 8 concurrent fragments + 4M HTTP chunks
        val ytRes = ytDlpEngine.downloadMedia(
            processId = req.taskId,
            url = req.url,
            workDir = req.workDir,
            preset = req.preset,
            maxVideoHeightPx = req.maxVideoHeightPx,
            preferredVideoFormat = req.preferredVideoFormat,
            preferredCodec = effectiveCodec,
            videoContainer = effectiveContainer,
            audioFormat = req.audioFormat,
            mp3Bitrate = req.mp3Bitrate,
            embedMetadata = req.embedMetadata,
            embedSubtitles = req.embedSubtitles,
            saveThumbnail = req.saveThumbnail,
            restrictFilenames = req.restrictFilenames,
            customCommandFlags = req.customCommandFlags,
            onProgress = onProgress
        )
        if (ytRes.isSuccess) {
            val ytOut = ytRes.getOrThrow()
            if (!req.preset.isVideo && req.customCommandFlags.isNullOrBlank()) {
                val targetAudioFormat = resolveTargetAudioFormat(req.preset, req.audioFormat)
                val curExt = ytOut.extension.lowercase()
                val alreadyMatches = curExt == targetAudioFormat.ext ||
                    (targetAudioFormat == AudioTargetFormat.M4A && curExt == "mp4")
                val finalFile = if (!alreadyMatches) {
                    val convertedOut = File(req.workDir, "yt_audio_${req.taskId}.${targetAudioFormat.ext}")
                    FfmpegKitEngine.convertOrExtractAudio(
                        taskId = req.taskId,
                        inputFile = ytOut.file,
                        outputFile = convertedOut,
                        targetFormat = targetAudioFormat,
                        mp3Bitrate = req.mp3Bitrate,
                        title = if (req.embedMetadata) ytOut.title else null,
                        artist = if (req.embedMetadata) ytOut.artist else null
                    ) { frac ->
                        onProgress(TransferProgress(0.84f + frac * 0.14f, "FFmpegKit", null))
                    }.getOrDefault(ytOut.file)
                } else {
                    ytOut.file
                }
                val outExt = finalFile.extension.lowercase().ifBlank { targetAudioFormat.ext }
                return@withContext Result.success(
                    HybridDownloadOutput(
                        file = finalFile,
                        title = ytOut.title,
                        artist = ytOut.artist,
                        extension = outExt,
                        isVideo = false,
                        route = ExtractionRoute.YTDLP_UNIVERSAL,
                        codecBadge = outExt.uppercase()
                    )
                )
            }
            if (ytOut.isVideo && ytOut.audioFile != null) {
                val videoProbe = FfmpegKitEngine.probeMedia(ytOut.file)
                val audioProbe = FfmpegKitEngine.probeMedia(ytOut.audioFile)
                val resolvedVideoBadge = videoProbe.videoCodecBadge
                    ?: if (effectiveCodec != VideoCodecPreference.AUTO) effectiveCodec.shortBadge else null
                val muxContainer = resolveOutputContainer(
                    requestedContainer = effectiveContainer,
                    preset = req.preset,
                    videoCodecBadge = resolvedVideoBadge,
                    audioCodecBadge = audioProbe.audioCodecBadge,
                    audioExtension = ytOut.audioFile.extension
                )
                val muxedOut = File(req.workDir, "yt_muxed_${req.taskId}.${muxContainer.ext}")
                val muxed = FfmpegKitEngine.muxStreams(
                    taskId = req.taskId,
                    videoFile = ytOut.file,
                    audioFile = ytOut.audioFile,
                    outputFile = muxedOut,
                    container = muxContainer,
                    subtitleFile = ytOut.subtitleFile,
                    title = if (req.embedMetadata) ytOut.title else null,
                    artist = if (req.embedMetadata) ytOut.artist else null,
                    expectedDurationMs = maxOf(videoProbe.durationMs, audioProbe.durationMs)
                ) { frac ->
                    onProgress(TransferProgress(0.84f + frac * 0.14f, "Muxing ${muxContainer.ext.uppercase()}", null))
                }.getOrThrow()
                val probed = FfmpegKitEngine.probeMedia(muxed)
                val badge = probed.videoCodecBadge ?: resolvedVideoBadge ?: muxContainer.ext.uppercase()
                return@withContext Result.success(
                    HybridDownloadOutput(
                        file = muxed,
                        title = ytOut.title,
                        artist = ytOut.artist,
                        extension = muxContainer.ext,
                        isVideo = true,
                        route = ExtractionRoute.YTDLP_UNIVERSAL,
                        codecBadge = badge
                    )
                )
            }
            var finalVideoFile = ytOut.file
            var finalExt = ytOut.extension
            if (ytOut.isVideo && req.customCommandFlags.isNullOrBlank()) {
                val probe = FfmpegKitEngine.probeMedia(finalVideoFile)
                val targetContainer = resolveOutputContainer(
                    requestedContainer = effectiveContainer,
                    preset = req.preset,
                    videoCodecBadge = probe.videoCodecBadge,
                    audioCodecBadge = probe.audioCodecBadge,
                    audioExtension = finalExt
                )
                if (targetContainer == VideoContainerOption.MKV && !finalExt.equals("mkv", ignoreCase = true)) {
                    val mkvOut = File(req.workDir, "yt_mkv_${req.taskId}.mkv")
                    val mkvRes = FfmpegKitEngine.remuxContainer(
                        taskId = req.taskId,
                        inputFile = finalVideoFile,
                        outputFile = mkvOut,
                        targetContainer = VideoContainerOption.MKV,
                        expectedDurationMs = probe.durationMs
                    ) { frac ->
                        onProgress(TransferProgress(0.86f + frac * 0.12f, "Remuxing MKV", null))
                    }
                    if (mkvRes.isSuccess) {
                        finalVideoFile = mkvRes.getOrThrow()
                        finalExt = "mkv"
                    }
                } else if (targetContainer == VideoContainerOption.MP4) {
                    val needsMp4Remux = !finalExt.equals("mp4", ignoreCase = true) ||
                        (probe.audioCodecBadge != null && probe.audioCodecBadge != "AAC")
                    if (needsMp4Remux) {
                        val remuxedOut = File(req.workDir, "yt_mp4_${req.taskId}.mp4")
                        val remuxRes = FfmpegKitEngine.remuxContainer(
                            taskId = req.taskId,
                            inputFile = finalVideoFile,
                            outputFile = remuxedOut,
                            targetContainer = VideoContainerOption.MP4,
                            expectedDurationMs = probe.durationMs
                        ) { frac ->
                            onProgress(TransferProgress(0.86f + frac * 0.12f, "Encoding AAC MP4", null))
                        }
                        if (remuxRes.isSuccess) {
                            finalVideoFile = remuxRes.getOrThrow()
                            finalExt = "mp4"
                        }
                    }
                }
            }
            val badge = if (effectiveCodec != VideoCodecPreference.AUTO && ytOut.isVideo) {
                effectiveCodec.shortBadge
            } else {
                finalExt.uppercase()
            }
            return@withContext Result.success(
                HybridDownloadOutput(
                    file = finalVideoFile,
                    title = ytOut.title,
                    artist = ytOut.artist,
                    extension = finalExt,
                    isVideo = ytOut.isVideo,
                    route = ExtractionRoute.YTDLP_UNIVERSAL,
                    codecBadge = badge
                )
            )
        }

        if (cancelledTasks.contains(req.taskId)) {
            return@withContext Result.failure(IllegalStateException("Cancelled"))
        }

        // Route 3 Fallback: Pure-Kotlin resolver + parallel Range downloader + FFmpegKit mux
        onRouteSelected(ExtractionRoute.DIRECT_STREAM)
        val fallbackRes = executeLegacyFallback(req, effectiveContainer, onProgress)
        if (fallbackRes.isSuccess) return@withContext fallbackRes

        val msg = ytRes.exceptionOrNull()?.message
            ?: fallbackRes.exceptionOrNull()?.message
            ?: "All extraction routes failed for this URL"
        Result.failure(IllegalStateException(msg))
    }

    private suspend fun executeYtDlpAcceleratedPath(
        req: HybridDownloadRequest,
        effectiveCodec: VideoCodecPreference,
        effectiveContainer: VideoContainerOption,
        onProgress: (TransferProgress) -> Unit
    ): Result<HybridDownloadOutput> {
        return runCatching {
            onProgress(TransferProgress(0.03f, "Resolving streams", null))
            val streams = ytDlpEngine.extractDirectStreams(
                url = req.url,
                preset = req.preset,
                maxVideoHeightPx = req.maxVideoHeightPx,
                preferredVideoFormat = req.preferredVideoFormat,
                preferredCodec = effectiveCodec,
                videoContainer = effectiveContainer,
                audioFormat = req.audioFormat
            ).getOrThrow()

            val targetAudioFormat = resolveTargetAudioFormat(req.preset, req.audioFormat)

            if (!req.preset.isVideo) {
                val audioUrl = streams.audioUrl ?: error("No direct audio URL")
                val rawAudio = File(req.workDir, "yt_accel_audio_${req.taskId}.${streams.audioExtension ?: "m4a"}")
                downloadFileAccelerated(req.taskId, audioUrl, streams.audioHeaders, rawAudio) { tp ->
                    onProgress(tp.copy(fraction = 0.06f + tp.fraction * 0.74f))
                }.getOrThrow()

                val rawExt = rawAudio.extension.lowercase()
                val alreadyMatches = rawExt == targetAudioFormat.ext ||
                    (targetAudioFormat == AudioTargetFormat.M4A && rawExt == "mp4")
                val finalAudio = if (!alreadyMatches) {
                    val out = File(req.workDir, "yt_accel_final_${req.taskId}.${targetAudioFormat.ext}")
                    FfmpegKitEngine.convertOrExtractAudio(
                        taskId = req.taskId,
                        inputFile = rawAudio,
                        outputFile = out,
                        targetFormat = targetAudioFormat,
                        mp3Bitrate = req.mp3Bitrate,
                        title = if (req.embedMetadata) streams.title else null,
                        artist = if (req.embedMetadata) streams.artist else null,
                        expectedDurationMs = streams.durationSec * 1000L
                    ) { frac ->
                        onProgress(TransferProgress(0.80f + frac * 0.17f, "FFmpegKit", null))
                    }.getOrThrow()
                } else {
                    rawAudio
                }

                return@runCatching HybridDownloadOutput(
                    file = finalAudio,
                    title = streams.title,
                    artist = streams.artist,
                    extension = targetAudioFormat.ext,
                    isVideo = false,
                    route = ExtractionRoute.YTDLP_UNIVERSAL,
                    codecBadge = targetAudioFormat.ext.uppercase()
                )
            }

            val videoUrl = streams.videoUrl ?: error("No direct video URL")
            val rawVideo = File(req.workDir, "yt_accel_video_${req.taskId}.${streams.videoExtension ?: "mp4"}")

            if (streams.audioUrl != null) {
                val rawAudio = File(req.workDir, "yt_accel_audio_${req.taskId}.${streams.audioExtension ?: "m4a"}")
                var videoFrac = 0f
                var audioFrac = 0f
                var latestSpeed: String? = null
                var latestEta: String? = null
                val progressLock = Any()

                fun emitCombined() {
                    synchronized(progressLock) {
                        val combined = (videoFrac * 0.78f + audioFrac * 0.22f).coerceIn(0f, 1f)
                        onProgress(TransferProgress(0.05f + combined * 0.79f, latestSpeed, latestEta))
                    }
                }

                coroutineScope {
                    val vJob = async(Dispatchers.IO) {
                        downloadFileAccelerated(req.taskId, videoUrl, streams.videoHeaders, rawVideo) { tp ->
                            synchronized(progressLock) {
                                videoFrac = tp.fraction
                                if (!tp.speedLabel.isNullOrBlank()) latestSpeed = tp.speedLabel
                                if (!tp.etaLabel.isNullOrBlank()) latestEta = tp.etaLabel
                            }
                            emitCombined()
                        }.getOrThrow()
                    }
                    val aJob = async(Dispatchers.IO) {
                        downloadFileAccelerated(req.taskId, streams.audioUrl, streams.audioHeaders, rawAudio) { tp ->
                            synchronized(progressLock) {
                                audioFrac = tp.fraction
                            }
                            emitCombined()
                        }.getOrThrow()
                    }
                    awaitAll(vJob, aJob)
                }

                val videoBadge = streams.videoCodecBadge ?: FfmpegKitEngine.probeMedia(rawVideo).videoCodecBadge
                val audioBadge = streams.audioCodecBadge ?: FfmpegKitEngine.probeMedia(rawAudio).audioCodecBadge
                val muxContainer = resolveOutputContainer(
                    requestedContainer = effectiveContainer,
                    preset = req.preset,
                    videoCodecBadge = videoBadge,
                    audioCodecBadge = audioBadge,
                    audioExtension = rawAudio.extension
                )
                val muxedOut = File(req.workDir, "yt_accel_muxed_${req.taskId}.${muxContainer.ext}")
                val muxed = FfmpegKitEngine.muxStreams(
                    taskId = req.taskId,
                    videoFile = rawVideo,
                    audioFile = rawAudio,
                    outputFile = muxedOut,
                    container = muxContainer,
                    title = if (req.embedMetadata) streams.title else null,
                    artist = if (req.embedMetadata) streams.artist else null,
                    expectedDurationMs = streams.durationSec * 1000L
                ) { frac ->
                    onProgress(TransferProgress(0.84f + frac * 0.14f, "Muxing ${muxContainer.ext.uppercase()}", null))
                }.getOrThrow()

                HybridDownloadOutput(
                    file = muxed,
                    title = streams.title,
                    artist = streams.artist,
                    extension = muxContainer.ext,
                    isVideo = true,
                    route = ExtractionRoute.YTDLP_UNIVERSAL,
                    codecBadge = videoBadge ?: muxContainer.ext.uppercase()
                )
            } else {
                downloadFileAccelerated(req.taskId, videoUrl, streams.videoHeaders, rawVideo) { tp ->
                    onProgress(tp.copy(fraction = 0.05f + tp.fraction * 0.85f))
                }.getOrThrow()
                var finalFile = rawVideo
                var outExt = rawVideo.extension.lowercase().ifBlank { effectiveContainer.ext }
                val probe = FfmpegKitEngine.probeMedia(rawVideo)
                val targetContainer = resolveOutputContainer(
                    requestedContainer = effectiveContainer,
                    preset = req.preset,
                    videoCodecBadge = streams.videoCodecBadge ?: probe.videoCodecBadge,
                    audioCodecBadge = streams.audioCodecBadge ?: probe.audioCodecBadge,
                    audioExtension = outExt
                )
                if (targetContainer == VideoContainerOption.MKV && outExt != "mkv") {
                    val mkvOut = File(req.workDir, "yt_accel_mkv_${req.taskId}.mkv")
                    val mkvRes = FfmpegKitEngine.remuxContainer(
                        taskId = req.taskId,
                        inputFile = rawVideo,
                        outputFile = mkvOut,
                        targetContainer = VideoContainerOption.MKV,
                        expectedDurationMs = streams.durationSec * 1000L
                    ) { frac ->
                        onProgress(TransferProgress(0.90f + frac * 0.08f, "Remuxing MKV", null))
                    }
                    if (mkvRes.isSuccess) {
                        finalFile = mkvRes.getOrThrow()
                        outExt = "mkv"
                    }
                } else if (targetContainer == VideoContainerOption.MP4) {
                    val needsMp4Remux = outExt != "mp4" || (probe.audioCodecBadge != null && probe.audioCodecBadge != "AAC")
                    if (needsMp4Remux) {
                        val remuxedOut = File(req.workDir, "yt_accel_mp4_${req.taskId}.mp4")
                        val remuxRes = FfmpegKitEngine.remuxContainer(
                            taskId = req.taskId,
                            inputFile = rawVideo,
                            outputFile = remuxedOut,
                            targetContainer = VideoContainerOption.MP4,
                            expectedDurationMs = streams.durationSec * 1000L
                        ) { frac ->
                            onProgress(TransferProgress(0.90f + frac * 0.08f, "Encoding AAC MP4", null))
                        }
                        if (remuxRes.isSuccess) {
                            finalFile = remuxRes.getOrThrow()
                            outExt = "mp4"
                        }
                    }
                }
                HybridDownloadOutput(
                    file = finalFile,
                    title = streams.title,
                    artist = streams.artist,
                    extension = outExt,
                    isVideo = true,
                    route = ExtractionRoute.YTDLP_UNIVERSAL,
                    codecBadge = streams.videoCodecBadge ?: probe.videoCodecBadge ?: outExt.uppercase()
                )
            }
        }
    }

    private suspend fun executeNewPipeFastPath(
        req: HybridDownloadRequest,
        effectiveCodec: VideoCodecPreference,
        effectiveContainer: VideoContainerOption,
        onProgress: (TransferProgress) -> Unit
    ): Result<HybridDownloadOutput> {
        return runCatching {
            val extracted = newPipeEngine.extractStream(
                url = req.url,
                audioOnly = !req.preset.isVideo,
                maxVideoHeightPx = req.maxVideoHeightPx,
                preferredCodec = effectiveCodec,
                preferredContainer = effectiveContainer,
                preferredAudioFormat = req.audioFormat,
                includeSubtitles = req.embedSubtitles
            ).getOrThrow()

            val targetAudioFormat = resolveTargetAudioFormat(req.preset, req.audioFormat)

            if (!req.preset.isVideo) {
                val audioUrl = extracted.audioUrl ?: error("No audio URL returned")
                val rawAudio = File(req.workDir, "np_audio_${req.taskId}.${extracted.audioExtension ?: "webm"}")
                downloadFileAccelerated(req.taskId, audioUrl, emptyMap(), rawAudio) { tp ->
                    onProgress(tp.copy(fraction = 0.06f + tp.fraction * 0.74f))
                }.getOrThrow()

                val rawExt = rawAudio.extension.lowercase()
                val alreadyMatches = rawExt == targetAudioFormat.ext ||
                    (targetAudioFormat == AudioTargetFormat.M4A && rawExt == "mp4")
                val finalAudio = if (!alreadyMatches) {
                    val out = File(req.workDir, "np_final_${req.taskId}.${targetAudioFormat.ext}")
                    FfmpegKitEngine.convertOrExtractAudio(
                        taskId = req.taskId,
                        inputFile = rawAudio,
                        outputFile = out,
                        targetFormat = targetAudioFormat,
                        mp3Bitrate = req.mp3Bitrate,
                        title = if (req.embedMetadata) extracted.title else null,
                        artist = if (req.embedMetadata) extracted.uploader else null,
                        expectedDurationMs = extracted.durationSec * 1000L
                    ) { frac ->
                        onProgress(TransferProgress(0.80f + frac * 0.17f, "FFmpegKit", null))
                    }.getOrThrow()
                } else {
                    rawAudio
                }

                return@runCatching HybridDownloadOutput(
                    file = finalAudio,
                    title = extracted.title,
                    artist = extracted.uploader,
                    extension = targetAudioFormat.ext,
                    isVideo = false,
                    route = ExtractionRoute.NEWPIPE_FAST,
                    codecBadge = targetAudioFormat.ext.uppercase()
                )
            }

            // Video flow: download video, companion audio, and optional subtitle CONCURRENTLY
            val videoUrl = extracted.videoUrl ?: extracted.muxedFallbackUrl ?: error("No video URL returned")
            val rawVideo = File(req.workDir, "np_video_${req.taskId}.${extracted.videoExtension ?: "mp4"}")

            if (extracted.audioUrl != null) {
                val rawAudio = File(req.workDir, "np_audio_${req.taskId}.${extracted.audioExtension ?: "webm"}")
                var videoFrac = 0f
                var audioFrac = 0f
                var latestSpeed: String? = null
                var latestEta: String? = null
                val progressLock = Any()

                fun emitCombined() {
                    synchronized(progressLock) {
                        val combined = (videoFrac * 0.78f + audioFrac * 0.22f).coerceIn(0f, 1f)
                        onProgress(TransferProgress(0.05f + combined * 0.79f, latestSpeed, latestEta))
                    }
                }

                val subFile = coroutineScope {
                    val videoJob = async(Dispatchers.IO) {
                        downloadFileAccelerated(req.taskId, videoUrl, emptyMap(), rawVideo) { tp ->
                            synchronized(progressLock) {
                                videoFrac = tp.fraction
                                if (!tp.speedLabel.isNullOrBlank()) latestSpeed = tp.speedLabel
                                if (!tp.etaLabel.isNullOrBlank()) latestEta = tp.etaLabel
                            }
                            emitCombined()
                        }.getOrThrow()
                    }
                    val audioJob = async(Dispatchers.IO) {
                        downloadFileAccelerated(req.taskId, extracted.audioUrl, emptyMap(), rawAudio) { tp ->
                            synchronized(progressLock) {
                                audioFrac = tp.fraction
                            }
                            emitCombined()
                        }.getOrThrow()
                    }
                    val subJob = async(Dispatchers.IO) {
                        extracted.subtitleUrl?.let { subUrl ->
                            val sf = File(req.workDir, "np_sub_${req.taskId}.vtt")
                            if (downloadFileSingleStream(req.taskId, subUrl, emptyMap(), sf) {}.isSuccess) sf else null
                        }
                    }
                    videoJob.await()
                    audioJob.await()
                    subJob.await()
                }

                val muxContainer = resolveOutputContainer(
                    requestedContainer = effectiveContainer,
                    preset = req.preset,
                    videoCodecBadge = extracted.videoCodecBadge,
                    audioCodecBadge = extracted.audioCodecBadge,
                    audioExtension = rawAudio.extension
                )
                val muxedOut = File(req.workDir, "np_muxed_${req.taskId}.${muxContainer.ext}")
                val muxed = FfmpegKitEngine.muxStreams(
                    taskId = req.taskId,
                    videoFile = rawVideo,
                    audioFile = rawAudio,
                    outputFile = muxedOut,
                    container = muxContainer,
                    subtitleFile = subFile,
                    title = if (req.embedMetadata) extracted.title else null,
                    artist = if (req.embedMetadata) extracted.uploader else null,
                    expectedDurationMs = extracted.durationSec * 1000L
                ) { frac ->
                    onProgress(TransferProgress(0.84f + frac * 0.14f, "Muxing ${muxContainer.ext.uppercase()}", null))
                }.getOrThrow()

                val badge = extracted.videoCodecBadge ?: muxContainer.ext.uppercase()
                HybridDownloadOutput(
                    file = muxed,
                    title = extracted.title,
                    artist = extracted.uploader,
                    extension = muxContainer.ext,
                    isVideo = true,
                    route = ExtractionRoute.NEWPIPE_FAST,
                    codecBadge = badge
                )
            } else {
                downloadFileAccelerated(req.taskId, videoUrl, emptyMap(), rawVideo) { tp ->
                    onProgress(tp.copy(fraction = 0.05f + tp.fraction * 0.90f))
                }.getOrThrow()
                val outExt = rawVideo.extension.lowercase().ifBlank { effectiveContainer.ext }
                HybridDownloadOutput(
                    file = rawVideo,
                    title = extracted.title,
                    artist = extracted.uploader,
                    extension = outExt,
                    isVideo = true,
                    route = ExtractionRoute.NEWPIPE_FAST,
                    codecBadge = extracted.videoCodecBadge ?: outExt.uppercase()
                )
            }
        }
    }

    private suspend fun downloadDirectStream(
        req: HybridDownloadRequest,
        effectiveContainer: VideoContainerOption,
        onProgress: (TransferProgress) -> Unit
    ): Result<HybridDownloadOutput> {
        return runCatching {
            val clean = req.url.substringBefore('?').substringBefore('#')
            val ext = clean.substringAfterLast('.', "mp4").lowercase()
            val baseName = clean.substringAfterLast('/').substringBeforeLast('.').ifBlank { "ZeroPlay_Stream" }

            if (ext == "m3u8") {
                val outFile = File(req.workDir, "hls_${req.taskId}.${effectiveContainer.ext}")
                val remuxed = FfmpegKitEngine.remuxHlsToFile(
                    taskId = req.taskId,
                    hlsUrl = req.url,
                    outputFile = outFile,
                    container = effectiveContainer
                ) { frac ->
                    onProgress(TransferProgress(0.1f + frac * 0.85f, "HLS Stream", null))
                }.getOrThrow()
                return@runCatching HybridDownloadOutput(
                    file = remuxed,
                    title = baseName,
                    artist = "HLS Direct",
                    extension = effectiveContainer.ext,
                    isVideo = true,
                    route = ExtractionRoute.DIRECT_STREAM,
                    codecBadge = effectiveContainer.ext.uppercase()
                )
            }

            val dest = File(req.workDir, "direct_${req.taskId}.$ext")
            downloadFileAccelerated(req.taskId, req.url, emptyMap(), dest, onProgress).getOrThrow()
            val isVideo = ext in setOf("mp4", "mkv", "webm", "mov")
            HybridDownloadOutput(
                file = dest,
                title = baseName,
                artist = "Direct Stream",
                extension = ext,
                isVideo = isVideo,
                route = ExtractionRoute.DIRECT_STREAM,
                codecBadge = ext.uppercase()
            )
        }
    }

    private suspend fun executeLegacyFallback(
        req: HybridDownloadRequest,
        effectiveContainer: VideoContainerOption,
        onProgress: (TransferProgress) -> Unit
    ): Result<HybridDownloadOutput> {
        return runCatching {
            val resolved = legacyResolver.resolve(
                rawUrl = req.url,
                preferAudio = !req.preset.isVideo,
                maxVideoHeightPx = req.maxVideoHeightPx
            ).getOrThrow()

            val primaryFile = File(req.workDir, "fb_primary_${req.taskId}.${resolved.extension.ifBlank { "mp4" }}")

            if (resolved.companionAudioUrl != null && req.preset.isVideo) {
                val audioFile = File(req.workDir, "fb_audio_${req.taskId}.m4a")
                coroutineScope {
                    val vJob = async(Dispatchers.IO) {
                        downloadFileAccelerated(req.taskId, resolved.primaryUrl, resolved.headers, primaryFile) { tp ->
                            onProgress(tp.copy(fraction = 0.08f + tp.fraction * 0.72f))
                        }.getOrThrow()
                    }
                    val aJob = async(Dispatchers.IO) {
                        downloadFileAccelerated(
                            req.taskId,
                            resolved.companionAudioUrl,
                            resolved.companionAudioHeaders.ifEmpty { resolved.headers },
                            audioFile
                        ) {}.getOrThrow()
                    }
                    awaitAll(vJob, aJob)
                }
                val muxed = File(req.workDir, "fb_muxed_${req.taskId}.${effectiveContainer.ext}")
                val out = FfmpegKitEngine.muxStreams(
                    taskId = req.taskId,
                    videoFile = primaryFile,
                    audioFile = audioFile,
                    outputFile = muxed,
                    container = effectiveContainer,
                    title = resolved.title,
                    artist = resolved.artist
                ) { frac ->
                    onProgress(TransferProgress(0.84f + frac * 0.14f, "Muxing", null))
                }.getOrThrow()
                return@runCatching HybridDownloadOutput(
                    file = out,
                    title = resolved.title,
                    artist = resolved.artist,
                    extension = effectiveContainer.ext,
                    isVideo = true,
                    route = ExtractionRoute.DIRECT_STREAM,
                    codecBadge = effectiveContainer.ext.uppercase()
                )
            }

            downloadFileAccelerated(req.taskId, resolved.primaryUrl, resolved.headers, primaryFile) { tp ->
                onProgress(tp.copy(fraction = 0.08f + tp.fraction * 0.87f))
            }.getOrThrow()

            HybridDownloadOutput(
                file = primaryFile,
                title = resolved.title,
                artist = resolved.artist,
                extension = resolved.extension.ifBlank { if (req.preset.isVideo) "mp4" else "m4a" },
                isVideo = req.preset.isVideo,
                route = ExtractionRoute.DIRECT_STREAM,
                codecBadge = resolved.extension.uppercase().ifBlank { "MEDIA" }
            )
        }
    }

    private fun resolveTargetAudioFormat(
        preset: DownloadFormatPreset,
        audioPref: SealAudioFormatPref
    ): AudioTargetFormat = when (preset) {
        DownloadFormatPreset.AUDIO_OPUS_HIGH -> AudioTargetFormat.OPUS
        DownloadFormatPreset.AUDIO_MP3_STANDARD -> AudioTargetFormat.MP3
        DownloadFormatPreset.AUDIO_FLAC_LOSSLESS -> AudioTargetFormat.FLAC
        else -> when (audioPref) {
            SealAudioFormatPref.OPUS -> AudioTargetFormat.OPUS
            SealAudioFormatPref.MP3 -> AudioTargetFormat.MP3
            SealAudioFormatPref.FLAC -> AudioTargetFormat.FLAC
            SealAudioFormatPref.M4A,
            SealAudioFormatPref.NOT_SPECIFIED -> AudioTargetFormat.M4A
        }
    }

    /**
     * High-speed multi-connection Range chunk downloader.
     * Probes total length + byte-range support; downloads 8 parallel 4 MB slices into
     * a pre-allocated RandomAccessFile via thread-safe positional FileChannel.write().
     * Automatically falls back to fast 256 KB single-stream download when Range is unsupported.
     */
    private suspend fun downloadFileAccelerated(
        taskId: String,
        url: String,
        headers: Map<String, String>,
        destFile: File,
        onProgress: (TransferProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val probe = probeRangeSupport(url, headers)
        if (probe != null && probe.supportsRanges && probe.totalBytes >= MIN_PARALLEL_BYTES) {
            val multiRes = downloadParallelRanges(taskId, url, headers, destFile, probe.totalBytes, onProgress)
            if (multiRes.isSuccess) return@withContext multiRes
            if (cancelledTasks.contains(taskId)) return@withContext multiRes
            runCatching { destFile.delete() }
        }
        downloadFileSingleStream(taskId, url, headers, destFile, onProgress)
    }

    private data class RangeProbe(val totalBytes: Long, val supportsRanges: Boolean)

    private fun probeRangeSupport(url: String, headers: Map<String, String>): RangeProbe? {
        return runCatching {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            streamClient.newCall(reqBuilder.build()).execute().use { resp ->
                val cType = resp.header("Content-Type").orEmpty().lowercase()
                if (cType.contains("text/html") || cType.contains("application/xhtml")) {
                    return null
                }
                if (resp.code == 206) {
                    // Content-Range: bytes 0-0/12345678
                    val cr = resp.header("Content-Range").orEmpty()
                    val total = cr.substringAfter('/', "").trim().toLongOrNull() ?: -1L
                    if (total > 0L) return RangeProbe(total, supportsRanges = true)
                }
                val acceptRanges = resp.header("Accept-Ranges").orEmpty().contains("bytes", ignoreCase = true)
                val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                if (len > 0L && acceptRanges) RangeProbe(len, supportsRanges = true) else null
            }
        }.getOrNull()
    }

    private suspend fun downloadParallelRanges(
        taskId: String,
        url: String,
        headers: Map<String, String>,
        destFile: File,
        totalBytes: Long,
        onProgress: (TransferProgress) -> Unit
    ): Result<File> = runCatching {
        val chunks = planParallelChunks(totalBytes)
        val queue = ConcurrentLinkedQueue(chunks)
        val downloadedBytes = AtomicLong(0L)
        val startTimeMs = System.currentTimeMillis()
        val lastReportMs = AtomicLong(startTimeMs)
        val failedFlag = AtomicBoolean(false)

        RandomAccessFile(destFile, "rw").use { raf ->
            raf.setLength(totalBytes)
            val channel = raf.channel

            coroutineScope {
                val workerCount = minOf(PARALLEL_CONNECTIONS, chunks.size)
                val jobs = (0 until workerCount).map {
                    async(Dispatchers.IO) {
                        val buf = ByteArray(STREAM_BUFFER_BYTES)
                        while (!failedFlag.get() && !cancelledTasks.contains(taskId)) {
                            val range = queue.poll() ?: break
                            var attempt = 0
                            var chunkOk = false
                            while (attempt < 3 && !chunkOk && !failedFlag.get() && !cancelledTasks.contains(taskId)) {
                                attempt++
                                try {
                                    val reqBuilder = Request.Builder()
                                        .url(url)
                                        .header("Range", "bytes=${range.first}-${range.last}")
                                    headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                                    streamClient.newCall(reqBuilder.build()).execute().use { resp ->
                                        if (resp.code != 206 && resp.code != 200) {
                                            error("HTTP ${resp.code} for range ${range.first}-${range.last}")
                                        }
                                        val body = resp.body ?: error("Empty range body")
                                        var pos = range.first
                                        body.byteStream().use { input ->
                                            while (true) {
                                                if (cancelledTasks.contains(taskId) || failedFlag.get()) {
                                                    error("Cancelled")
                                                }
                                                val read = input.read(buf)
                                                if (read <= 0) break
                                                val bb = ByteBuffer.wrap(buf, 0, read)
                                                while (bb.hasRemaining()) {
                                                    pos += channel.write(bb, pos)
                                                }
                                                val done = downloadedBytes.addAndGet(read.toLong())
                                                val now = System.currentTimeMillis()
                                                val prev = lastReportMs.get()
                                                if (now - prev >= 180L && lastReportMs.compareAndSet(prev, now)) {
                                                    val elapsedSec = ((now - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                                                    val bps = done / elapsedSec
                                                    val speedStr = formatSpeed(bps)
                                                    val etaStr = if (totalBytes > done && bps > 100.0) {
                                                        YtDlpEngine.formatEtaSeconds(((totalBytes - done) / bps).toLong())
                                                    } else null
                                                    val frac = (done.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                                    onProgress(TransferProgress(frac, speedStr, etaStr))
                                                }
                                            }
                                        }
                                    }
                                    chunkOk = true
                                } catch (e: Throwable) {
                                    if (attempt >= 3 || cancelledTasks.contains(taskId)) {
                                        failedFlag.set(true)
                                        throw e
                                    }
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        if (cancelledTasks.contains(taskId)) {
            runCatching { destFile.delete() }
            error("Cancelled")
        }
        if (!destFile.exists() || destFile.length() < totalBytes * 0.98) {
            error("Incomplete parallel download")
        }
        onProgress(TransferProgress(1f))
        destFile
    }

    private fun downloadFileSingleStream(
        taskId: String,
        url: String,
        headers: Map<String, String>,
        destFile: File,
        onProgress: (TransferProgress) -> Unit
    ): Result<File> {
        return runCatching {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            streamClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Stream server returned HTTP ${resp.code}")
                }
                val cType = resp.header("Content-Type").orEmpty().lowercase()
                if (cType.contains("text/html") || cType.contains("application/xhtml")) {
                    error("Stream URL returned HTML instead of media")
                }
                val body = resp.body ?: error("Empty stream response")
                val total = body.contentLength()
                val buf = ByteArray(STREAM_BUFFER_BYTES)
                var downloaded = 0L
                val startTimeMs = System.currentTimeMillis()
                var lastReportMs = startTimeMs

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        while (true) {
                            if (cancelledTasks.contains(taskId)) error("Cancelled")
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= 220L) {
                                lastReportMs = now
                                val elapsedSec = ((now - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                                val bytesPerSec = downloaded / elapsedSec
                                val speedStr = formatSpeed(bytesPerSec)
                                val etaStr = if (total > downloaded && bytesPerSec > 100.0) {
                                    YtDlpEngine.formatEtaSeconds(((total - downloaded) / bytesPerSec).toLong())
                                } else null
                                val frac = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f) else 0.45f
                                onProgress(TransferProgress(frac, speedStr, etaStr))
                            }
                        }
                    }
                }
            }
            if (!destFile.exists() || destFile.length() <= 0L) {
                error("Downloaded stream file was empty")
            }
            onProgress(TransferProgress(1f))
            destFile
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String = when {
        bytesPerSec >= 1_048_576.0 -> String.format("%.1f MB/s", bytesPerSec / 1_048_576.0)
        bytesPerSec >= 1_024.0 -> String.format("%.0f KB/s", bytesPerSec / 1_024.0)
        else -> "${bytesPerSec.toInt()} B/s"
    }
}
