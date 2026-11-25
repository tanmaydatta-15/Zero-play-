package com.example.converter

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.data.model.AudioTargetFormat
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.VideoContainerOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ProbedMediaInfo(
    val durationMs: Long = 0L,
    val videoCodecBadge: String? = null,
    val audioCodecBadge: String? = null,
    val containerFormat: String? = null
)

object FfmpegKitEngine {

    private val activeSessions = ConcurrentHashMap<String, Long>()

    fun ensureInitialized(): Boolean {
        return runCatching {
            FFmpegKitConfig.getFFmpegVersion().isNotBlank()
        }.getOrDefault(false)
    }

    fun cancelTask(taskId: String) {
        val sessionId = activeSessions.remove(taskId) ?: return
        runCatching { FFmpegKit.cancel(sessionId) }
    }

    fun probeMedia(file: File): ProbedMediaInfo {
        return runCatching {
            val info = FFprobeKit.getMediaInformation(file.absolutePath).mediaInformation
                ?: return ProbedMediaInfo()
            val durationMs = ((info.duration?.toDoubleOrNull() ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
            var vCodec: String? = null
            var aCodec: String? = null
            info.streams?.forEach { stream ->
                val type = stream.type.orEmpty().lowercase()
                val codec = stream.codec.orEmpty().lowercase()
                if (type == "video" && vCodec == null) {
                    vCodec = normalizeCodecBadge(codec)
                } else if (type == "audio" && aCodec == null) {
                    aCodec = normalizeCodecBadge(codec)
                }
            }
            ProbedMediaInfo(
                durationMs = durationMs,
                videoCodecBadge = vCodec,
                audioCodecBadge = aCodec,
                containerFormat = info.format
            )
        }.getOrDefault(ProbedMediaInfo())
    }

    internal fun normalizeCodecBadge(rawCodec: String): String {
        val c = rawCodec.lowercase()
        return when {
            c.contains("av1") || c.contains("av01") || c.contains("dav1d") || c.contains("svtav1") -> "AV1"
            c.contains("hevc") || c.contains("h265") || c.contains("hvc1") || c.contains("hev1") -> "HEVC"
            c.contains("vp9") || c.contains("vp09") -> "VP9"
            c.contains("h264") || c.contains("avc") -> "H.264"
            c.contains("opus") -> "OPUS"
            c.contains("mp3") || c.contains("lame") -> "MP3"
            c.contains("flac") -> "FLAC"
            c.contains("aac") || c.contains("mp4a") -> "AAC"
            else -> rawCodec.uppercase().take(6)
        }
    }

    internal fun quoteArg(arg: String): String {
        if (arg.isEmpty()) return "\"\""
        val escaped = arg.replace("\"", "\\\"")
        return "\"$escaped\""
    }

    internal fun buildArgsCommand(args: List<String>): String = args.joinToString(" ") { arg ->
        if (arg.any { it.isWhitespace() || it == '"' || it == '\'' || it == '(' || it == ')' || it == '&' }) {
            quoteArg(arg)
        } else {
            arg
        }
    }

    internal fun buildMuxArgs(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        container: VideoContainerOption,
        subtitleFile: File? = null,
        title: String? = null,
        artist: String? = null,
        reencodeVideoToH264: Boolean = false
    ): List<String> {
        val hasSubtitle = subtitleFile != null && subtitleFile.exists()
        val args = mutableListOf(
            "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath
        )
        if (hasSubtitle) {
            args += listOf("-i", subtitleFile!!.absolutePath)
        }
        // Explicitly map primary video from input 0 and primary audio from input 1
        args += listOf("-map", "0:v:0", "-map", "1:a:0")
        if (hasSubtitle) {
            args += listOf("-map", "2:s:0")
        }

        when (container) {
            VideoContainerOption.MKV -> {
                // Matroska natively holds AV1/VP9/HEVC/H.264 + Opus/AAC/FLAC + soft subtitles without re-encoding
                args += listOf("-c:v", "copy", "-c:a", "copy")
                if (hasSubtitle) {
                    args += listOf("-c:s", "srt")
                }
            }
            VideoContainerOption.WEBM -> {
                args += listOf("-c:v", "copy", "-c:a", "copy")
            }
            VideoContainerOption.MP4 -> {
                if (reencodeVideoToH264) {
                    args += listOf("-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p")
                } else {
                    args += listOf("-c:v", "copy")
                }
                // Always encode audio to AAC 192k for universal MP4 playback compatibility
                args += listOf("-c:a", "aac", "-b:a", "192k")
                if (hasSubtitle) {
                    args += listOf("-c:s", "mov_text")
                }
                args += listOf("-movflags", "+faststart")
            }
        }

        if (!title.isNullOrBlank()) args += listOf("-metadata", "title=$title")
        if (!artist.isNullOrBlank()) args += listOf("-metadata", "artist=$artist")
        args += outputFile.absolutePath
        return args
    }

    suspend fun muxStreams(
        taskId: String,
        videoFile: File,
        audioFile: File,
        outputFile: File,
        container: VideoContainerOption,
        subtitleFile: File? = null,
        title: String? = null,
        artist: String? = null,
        expectedDurationMs: Long = 0L,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val primaryArgs = buildMuxArgs(
            videoFile = videoFile,
            audioFile = audioFile,
            outputFile = outputFile,
            container = container,
            subtitleFile = subtitleFile,
            title = title,
            artist = artist,
            reencodeVideoToH264 = false
        )
        val primary = executeCommand(taskId, primaryArgs, expectedDurationMs, onProgress, outputFile)
        if (primary.isSuccess || container != VideoContainerOption.MP4) {
            return@withContext primary
        }
        val errMsg = primary.exceptionOrNull()?.message.orEmpty()
        if (errMsg.contains("Cancelled", ignoreCase = true)) {
            return@withContext primary
        }

        // Fallback if MP4 muxer rejected stream-copying an exotic video codec: keep both streams mapped and AAC 192k audio
        val fallbackArgs = buildMuxArgs(
            videoFile = videoFile,
            audioFile = audioFile,
            outputFile = outputFile,
            container = VideoContainerOption.MP4,
            subtitleFile = subtitleFile,
            title = title,
            artist = artist,
            reencodeVideoToH264 = true
        )
        executeCommand(taskId, fallbackArgs, expectedDurationMs, onProgress, outputFile)
    }

    suspend fun remuxContainer(
        taskId: String,
        inputFile: File,
        outputFile: File,
        targetContainer: VideoContainerOption,
        expectedDurationMs: Long = 0L,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (targetContainer == VideoContainerOption.MP4) {
            val mp4Args = listOf(
                "-y",
                "-i", inputFile.absolutePath,
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "192k",
                "-movflags", "+faststart",
                outputFile.absolutePath
            )
            val mp4Res = executeCommand(taskId, mp4Args, expectedDurationMs, onProgress, outputFile)
            if (mp4Res.isSuccess) return@withContext mp4Res
            val errMsg = mp4Res.exceptionOrNull()?.message.orEmpty()
            if (errMsg.contains("Cancelled", ignoreCase = true)) return@withContext mp4Res

            val h264Fallback = listOf(
                "-y",
                "-i", inputFile.absolutePath,
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "192k",
                "-movflags", "+faststart",
                outputFile.absolutePath
            )
            return@withContext executeCommand(taskId, h264Fallback, expectedDurationMs, onProgress, outputFile)
        }

        val copyArgs = listOf(
            "-y",
            "-i", inputFile.absolutePath,
            "-map", "0:v:0",
            "-map", "0:a:0?",
            "-c:v", "copy",
            "-c:a", "copy",
            outputFile.absolutePath
        )
        val copyRes = executeCommand(taskId, copyArgs, expectedDurationMs, onProgress, outputFile)
        if (copyRes.isSuccess) return@withContext copyRes

        val fallbackAudioCodec = if (targetContainer == VideoContainerOption.MKV) "libopus" else "aac"
        val fallbackArgs = listOf(
            "-y",
            "-i", inputFile.absolutePath,
            "-map", "0:v:0",
            "-map", "0:a:0?",
            "-c:v", "copy",
            "-c:a", fallbackAudioCodec,
            "-b:a", "192k",
            outputFile.absolutePath
        )
        executeCommand(taskId, fallbackArgs, expectedDurationMs, onProgress, outputFile)
    }

    suspend fun transcodeVideo(
        taskId: String,
        inputFile: File,
        outputFile: File,
        targetHeightPx: Int,
        bitrateMbps: Float,
        codec: VideoCodecOption,
        container: VideoContainerOption,
        removeAudio: Boolean,
        preferOpusAudio: Boolean = container == VideoContainerOption.MKV,
        expectedDurationMs: Long = 0L,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val bitrateKbps = (bitrateMbps * 1000f).toInt().coerceIn(400, 25_000)
        val args = mutableListOf(
            "-y",
            "-i", inputFile.absolutePath
        )

        if (targetHeightPx > 0) {
            args += listOf("-vf", "scale=-2:min(ih\\,$targetHeightPx)")
        }

        val resolvedCodec = if (codec == VideoCodecOption.AUTO) VideoCodecOption.H264 else codec
        when (resolvedCodec) {
            VideoCodecOption.AV1 -> {
                // Fast SVT-AV1 or AOMedia libaom-av1
                args += listOf(
                    "-c:v", "libsvtav1",
                    "-b:v", "${bitrateKbps}k",
                    "-preset", "8",
                    "-pix_fmt", "yuv420p"
                )
            }
            VideoCodecOption.HEVC -> {
                args += listOf(
                    "-c:v", "libx265",
                    "-b:v", "${bitrateKbps}k",
                    "-preset", "fast",
                    "-tag:v", "hvc1",
                    "-pix_fmt", "yuv420p"
                )
            }
            VideoCodecOption.VP9 -> {
                args += listOf(
                    "-c:v", "libvpx-vp9",
                    "-b:v", "${bitrateKbps}k",
                    "-deadline", "realtime",
                    "-cpu-used", "4"
                )
            }
            VideoCodecOption.H264,
            VideoCodecOption.AUTO -> {
                args += listOf(
                    "-c:v", "libx264",
                    "-b:v", "${bitrateKbps}k",
                    "-preset", "veryfast",
                    "-pix_fmt", "yuv420p"
                )
            }
        }

        if (removeAudio) {
            args += "-an"
        } else if (preferOpusAudio && container != VideoContainerOption.MP4) {
            args += listOf("-c:a", "libopus", "-b:a", "160k")
        } else {
            args += listOf("-c:a", "aac", "-b:a", "160k")
        }

        if (container == VideoContainerOption.MP4) {
            args += listOf("-movflags", "+faststart")
        }
        args += outputFile.absolutePath

        val primary = executeCommand(taskId, args, expectedDurationMs, onProgress, outputFile)
        if (primary.isSuccess || resolvedCodec != VideoCodecOption.AV1) {
            return@withContext primary
        }

        // If libsvtav1 isn't enabled on this ABI, fall back cleanly to libaom-av1
        val aomArgs = args.map { if (it == "libsvtav1") "libaom-av1" else it }
            .filterNot { it == "-preset" || it == "8" }
            .toMutableList()
        val outIdx = aomArgs.lastIndex
        aomArgs.addAll(outIdx, listOf("-cpu-used", "6", "-row-mt", "1"))
        executeCommand(taskId, aomArgs, expectedDurationMs, onProgress, outputFile)
    }

    suspend fun convertOrExtractAudio(
        taskId: String,
        inputFile: File,
        outputFile: File,
        targetFormat: AudioTargetFormat,
        mp3Bitrate: Mp3BitrateOption = Mp3BitrateOption.KBPS_320,
        title: String? = null,
        artist: String? = null,
        expectedDurationMs: Long = 0L,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val args = mutableListOf(
            "-y",
            "-i", inputFile.absolutePath,
            "-vn"
        )

        when (targetFormat) {
            AudioTargetFormat.OPUS -> {
                args += listOf("-c:a", "libopus", "-b:a", "160k", "-vbr", "on")
            }
            AudioTargetFormat.MP3 -> {
                args += listOf("-c:a", "libmp3lame")
                args += mp3Bitrate.ffmpegArg
            }
            AudioTargetFormat.M4A -> {
                args += listOf("-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart")
            }
            AudioTargetFormat.FLAC -> {
                args += listOf("-c:a", "flac", "-compression_level", "5")
            }
        }

        if (!title.isNullOrBlank()) args += listOf("-metadata", "title=$title")
        if (!artist.isNullOrBlank()) args += listOf("-metadata", "artist=$artist")
        args += outputFile.absolutePath

        executeCommand(taskId, args, expectedDurationMs, onProgress, outputFile)
    }

    suspend fun remuxHlsToFile(
        taskId: String,
        hlsUrl: String,
        outputFile: File,
        container: VideoContainerOption,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val args = mutableListOf(
            "-y",
            "-i", hlsUrl,
            "-c", "copy"
        )
        if (container == VideoContainerOption.MP4) {
            args += listOf("-bsf:a", "aac_adtstoasc", "-movflags", "+faststart")
        }
        args += outputFile.absolutePath
        executeCommand(taskId, args, 0L, onProgress, outputFile)
    }

    private fun executeCommand(
        taskId: String,
        args: List<String>,
        expectedDurationMs: Long,
        onProgress: (Float) -> Unit,
        outputFile: File
    ): Result<File> {
        return runCatching {
            val cmd = buildArgsCommand(args)
            val session = FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                null,
                null
            ) { stats ->
                if (expectedDurationMs > 0L && stats.time > 0) {
                    val frac = (stats.time.toFloat() / expectedDurationMs.toFloat()).coerceIn(0.02f, 0.98f)
                    onProgress(frac)
                }
            }
            activeSessions[taskId] = session.sessionId
            // Wait synchronously on IO dispatcher while allowing FFmpegKit.cancel(sessionId)
            while (!session.state.name.equals("COMPLETED", ignoreCase = true) &&
                !session.state.name.equals("FAILED", ignoreCase = true)
            ) {
                Thread.sleep(120L)
            }
            activeSessions.remove(taskId)

            val rc = session.returnCode
            if (ReturnCode.isCancel(rc)) {
                runCatching { outputFile.delete() }
                error("Cancelled")
            }
            if (!ReturnCode.isSuccess(rc) || !outputFile.exists() || outputFile.length() <= 512L) {
                val tailLog = session.failStackTrace?.take(220)
                    ?: session.output?.takeLast(260)
                    ?: "FFmpegKit exited with code ${rc?.value}"
                runCatching { outputFile.delete() }
                error("FFmpegKit failed ($cmd): $tailLog")
            }
            onProgress(1f)
            outputFile
        }
    }
}
