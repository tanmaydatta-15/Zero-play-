package com.example.converter

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.data.local.VideoCodecPreference
import com.example.data.model.AudioTargetFormat
import com.example.data.model.ConversionJobUi
import com.example.data.model.ConversionType
import com.example.data.model.JobStatus
import com.example.data.model.MediaType
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.VideoContainerOption
import com.example.data.repository.EchoRepository
import com.example.diagnostics.DeviceCapabilityInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

enum class PhotoTargetFormat(val label: String, val mimeType: String, val ext: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
    WEBP("WEBP", "image/webp", "webp")
}

enum class VideoCompressionPreset(val label: String, val heightPx: Int, val bitrateBps: Int) {
    COMPACT_480P("480p Compact", 480, 1_500_000),
    BALANCED_720P("720p Balanced", 720, 3_000_000),
    HIGH_1080P("1080p High", 1080, 6_000_000)
}

enum class VideoCodecOption(val label: String, val shortBadge: String, val mimeType: String) {
    AUTO("Auto (Hardware)", "AUTO", MimeTypes.VIDEO_H264),
    AV1("AV1 (AOMedia)", "AV1", MimeTypes.VIDEO_AV1),
    HEVC("HEVC (H.265)", "HEVC", MimeTypes.VIDEO_H265),
    VP9("VP9", "VP9", MimeTypes.VIDEO_VP9),
    H264("H.264 (AVC)", "H.264", MimeTypes.VIDEO_H264)
}

data class VideoFileMetrics(
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

class MediaConverterEngine(
    private val context: Context,
    private val repository: EchoRepository
) {
    suspend fun inspectVideoMetrics(uri: Uri): VideoFileMetrics = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri) ?: "Selected Video"
        val size = queryFileSize(uri)
        var duration = 0L
        var width = 0
        var height = 0
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        }
        runCatching { retriever.release() }
        VideoFileMetrics(name, size, duration, width, height)
    }

    suspend fun batchCompressPhotos(
        uris: List<Uri>,
        format: PhotoTargetFormat,
        quality: Int,
        scalePercent: Int,
        skipIfNotSmaller: Boolean = false,
        filenamePrefix: String = "ZeroPlay_"
    ) = withContext(Dispatchers.IO) {
        uris.forEachIndexed { index, uri ->
            val jobId = "photo_${UUID.randomUUID()}"
            val name = queryDisplayName(uri) ?: "Photo_${index + 1}"
            val sizeBefore = queryFileSize(uri)
            val baseJob = ConversionJobUi(
                id = jobId,
                title = "${name.substringBeforeLast('.')} -> ${format.label}",
                type = ConversionType.PHOTO_COMPRESS,
                detailLabel = "${format.label} • Q$quality • ${scalePercent}%",
                progress = 0.1f,
                status = JobStatus.RUNNING,
                inputUriString = uri.toString(),
                sizeBeforeBytes = sizeBefore
            )
            repository.upsertConversionJob(baseJob)

            runCatching {
                val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to open image stream")
                val original = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
                    ?: error("Unsupported image format")

                val targetW = ((original.width * scalePercent) / 100f).roundToInt().coerceAtLeast(64)
                val targetH = ((original.height * scalePercent) / 100f).roundToInt().coerceAtLeast(64)
                val scaled = if (scalePercent < 100) {
                    Bitmap.createScaledBitmap(original, targetW, targetH, true)
                } else {
                    original
                }

                val outStream = ByteArrayOutputStream()
                val compressFormat = when (format) {
                    PhotoTargetFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    PhotoTargetFormat.PNG -> Bitmap.CompressFormat.PNG
                    PhotoTargetFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                scaled.compress(compressFormat, quality.coerceIn(10, 100), outStream)
                val compressedBytes = outStream.toByteArray()
                if (scaled !== original) scaled.recycle()
                original.recycle()

                val sizeAfter = compressedBytes.size.toLong()
                if (skipIfNotSmaller && sizeBefore > 0L && sizeAfter > (sizeBefore * 0.90).toLong()) {
                    repository.upsertConversionJob(
                        baseJob.copy(
                            progress = 1f,
                            status = JobStatus.COMPLETED,
                            detailLabel = "Skipped (<10% space savings)",
                            outputUriString = uri.toString(),
                            sizeAfterBytes = sizeBefore
                        )
                    )
                    return@runCatching
                }

                val outName = "${filenamePrefix}${name.substringBeforeLast('.')}_${System.currentTimeMillis()}.${format.ext}"
                val savedUri = saveBytesToMediaStoreImages(outName, format.mimeType, compressedBytes)

                repository.upsertConversionJob(
                    baseJob.copy(
                        progress = 1f,
                        status = JobStatus.COMPLETED,
                        outputUriString = savedUri.toString(),
                        sizeAfterBytes = sizeAfter
                    )
                )
            }.onFailure { err ->
                repository.upsertConversionJob(
                    baseJob.copy(
                        status = JobStatus.FAILED,
                        errorMessage = err.message ?: "Photo conversion failed"
                    )
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun compressVideo(
        uri: Uri,
        preset: VideoCompressionPreset,
        codec: VideoCodecOption = VideoCodecOption.AUTO,
        container: VideoContainerOption = VideoContainerOption.MP4,
        bitrateMbps: Float = preset.bitrateBps / 1_000_000f,
        skipIfNotSmaller: Boolean = false,
        removeAudio: Boolean = false,
        remuxOnly: Boolean = false
    ) {
        val jobId = "video_${UUID.randomUUID()}"
        val name = queryDisplayName(uri) ?: "ZeroPlayVideo"
        val metrics = inspectVideoMetrics(uri)
        val sizeBefore = metrics.sizeBytes
        val effectiveCodec = if (codec == VideoCodecOption.AUTO) {
            when (DeviceCapabilityInspector.recommendedAutoVideoCodec(context)) {
                VideoCodecPreference.AV1 -> VideoCodecOption.AV1
                VideoCodecPreference.HEVC -> VideoCodecOption.HEVC
                VideoCodecPreference.VP9 -> VideoCodecOption.VP9
                else -> VideoCodecOption.H264
            }
        } else {
            codec
        }

        val modeDesc = if (remuxOnly) {
            "Lossless Remux -> ${container.ext.uppercase()}"
        } else {
            buildString {
                append("${preset.label} • ${String.format("%.1f", bitrateMbps)} Mbps ${effectiveCodec.shortBadge} • ${container.ext.uppercase()}")
                if (removeAudio) append(" • Muted")
            }
        }

        val baseJob = ConversionJobUi(
            id = jobId,
            title = "${name.substringBeforeLast('.')} (${if (remuxOnly) container.ext.uppercase() else "${preset.heightPx}p ${effectiveCodec.shortBadge}"})",
            type = ConversionType.VIDEO_COMPRESS,
            detailLabel = modeDesc,
            progress = 0.05f,
            status = JobStatus.RUNNING,
            inputUriString = uri.toString(),
            sizeBeforeBytes = sizeBefore
        )
        repository.upsertConversionJob(baseJob)

        val tempInput = File(context.cacheDir, "conv_in_${System.currentTimeMillis()}.${name.substringAfterLast('.', "mp4")}")
        val tempOutput = File(context.cacheDir, "conv_out_${System.currentTimeMillis()}.${container.ext}")
        try {
            val useFfmpeg = remuxOnly ||
                container != VideoContainerOption.MP4 ||
                effectiveCodec == VideoCodecOption.AV1 ||
                effectiveCodec == VideoCodecOption.VP9

            coroutineScope {
                var lastEmitMs = 0L
                val emitProgress: (Float) -> Unit = { p ->
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 250L || p >= 0.96f) {
                        lastEmitMs = now
                        launch(Dispatchers.IO) {
                            repository.upsertConversionJob(baseJob.copy(progress = p.coerceIn(0.05f, 0.96f)))
                        }
                    }
                }
                if (useFfmpeg) {
                    copyUriToFile(uri, tempInput)
                    if (remuxOnly) {
                        FfmpegKitEngine.remuxContainer(
                            taskId = jobId,
                            inputFile = tempInput,
                            outputFile = tempOutput,
                            targetContainer = container,
                            expectedDurationMs = metrics.durationMs,
                            onProgress = emitProgress
                        ).getOrThrow()
                    } else {
                        FfmpegKitEngine.transcodeVideo(
                            taskId = jobId,
                            inputFile = tempInput,
                            outputFile = tempOutput,
                            targetHeightPx = preset.heightPx,
                            bitrateMbps = bitrateMbps,
                            codec = effectiveCodec,
                            container = container,
                            removeAudio = removeAudio,
                            preferOpusAudio = container != VideoContainerOption.MP4,
                            expectedDurationMs = metrics.durationMs,
                            onProgress = emitProgress
                        ).getOrThrow()
                    }
                } else {
                    val targetBitrateBps = (bitrateMbps * 1_000_000f).roundToInt().coerceIn(500_000, 20_000_000)
                    val transformerResult = runCatching {
                        runTransformerOnMain(
                            inputUri = uri,
                            outputFile = tempOutput,
                            removeVideo = false,
                            removeAudio = removeAudio,
                            targetHeight = preset.heightPx,
                            targetBitrate = targetBitrateBps,
                            videoMimeType = effectiveCodec.mimeType,
                            onProgress = { p ->
                                repository.upsertConversionJob(baseJob.copy(progress = p.coerceIn(0.05f, 0.96f)))
                            }
                        )
                    }
                    if (transformerResult.isFailure || !tempOutput.exists() || tempOutput.length() <= 512L) {
                        copyUriToFile(uri, tempInput)
                        FfmpegKitEngine.transcodeVideo(
                            taskId = jobId,
                            inputFile = tempInput,
                            outputFile = tempOutput,
                            targetHeightPx = preset.heightPx,
                            bitrateMbps = bitrateMbps,
                            codec = effectiveCodec,
                            container = container,
                            removeAudio = removeAudio,
                            expectedDurationMs = metrics.durationMs,
                            onProgress = emitProgress
                        ).getOrThrow()
                    }
                }
            }

            val sizeAfter = tempOutput.length()
            if (!remuxOnly && skipIfNotSmaller && sizeBefore > 0L && sizeAfter > (sizeBefore * 0.90).toLong()) {
                repository.upsertConversionJob(
                    baseJob.copy(
                        progress = 1f,
                        status = JobStatus.COMPLETED,
                        detailLabel = "Skipped (<10% space savings)",
                        outputUriString = uri.toString(),
                        sizeAfterBytes = sizeBefore
                    )
                )
                return
            }

            val suffix = if (remuxOnly) "remux" else "${preset.heightPx}p_${effectiveCodec.shortBadge.replace(".", "")}"
            val outName = "ZeroPlay_${name.substringBeforeLast('.')}_$suffix.${container.ext}"
            val savedUri = withContext(Dispatchers.IO) {
                saveFileToMediaStoreVideo(outName, container.mimeType, tempOutput)
            }
            val badgeLabel = if (remuxOnly) {
                val probed = FfmpegKitEngine.probeMedia(tempOutput)
                listOfNotNull(probed.videoCodecBadge, container.ext.uppercase()).distinct().joinToString(" • ")
            } else {
                "${effectiveCodec.shortBadge} • ${container.ext.uppercase()}"
            }
            repository.registerExportedMedia(
                trackId = "exp_vid_${System.currentTimeMillis()}",
                title = outName.substringBeforeLast('.'),
                artist = "Exported Video • $badgeLabel",
                uriString = savedUri.toString(),
                mediaType = MediaType.VIDEO,
                mimeType = container.mimeType,
                sizeBytes = sizeAfter
            )
            repository.upsertConversionJob(
                baseJob.copy(
                    progress = 1f,
                    status = JobStatus.COMPLETED,
                    outputUriString = savedUri.toString(),
                    sizeAfterBytes = sizeAfter
                )
            )
        } catch (t: Throwable) {
            repository.upsertConversionJob(
                baseJob.copy(
                    status = JobStatus.FAILED,
                    errorMessage = t.message ?: "Video conversion failed"
                )
            )
        } finally {
            runCatching { tempInput.delete() }
            runCatching { tempOutput.delete() }
        }
    }

    suspend fun extractAudioFromVideo(
        uri: Uri,
        customTitle: String? = null,
        targetFormat: AudioTargetFormat = AudioTargetFormat.OPUS,
        mp3Bitrate: Mp3BitrateOption = Mp3BitrateOption.KBPS_320
    ) {
        val jobId = "audio_${UUID.randomUUID()}"
        val name = customTitle?.takeIf { it.isNotBlank() } ?: queryDisplayName(uri) ?: "ExtractedAudio"
        val metrics = inspectVideoMetrics(uri)
        val sizeBefore = metrics.sizeBytes
        val detail = if (targetFormat == AudioTargetFormat.MP3) {
            "MP3 (${mp3Bitrate.label}) via libmp3lame"
        } else {
            "${targetFormat.label} via FFmpegKit (${targetFormat.ffmpegCodec})"
        }
        val baseJob = ConversionJobUi(
            id = jobId,
            title = "${name.substringBeforeLast('.')} -> ${targetFormat.ext.uppercase()}",
            type = ConversionType.AUDIO_EXTRACT,
            detailLabel = detail,
            progress = 0.05f,
            status = JobStatus.RUNNING,
            inputUriString = uri.toString(),
            sizeBeforeBytes = sizeBefore
        )
        repository.upsertConversionJob(baseJob)

        val tempIn = File(context.cacheDir, "aud_in_${System.currentTimeMillis()}.${name.substringAfterLast('.', "mp4")}")
        val tempOut = File(context.cacheDir, "aud_out_${System.currentTimeMillis()}.${targetFormat.ext}")
        try {
            copyUriToFile(uri, tempIn)
            coroutineScope {
                var lastEmitMs = 0L
                FfmpegKitEngine.convertOrExtractAudio(
                    taskId = jobId,
                    inputFile = tempIn,
                    outputFile = tempOut,
                    targetFormat = targetFormat,
                    mp3Bitrate = mp3Bitrate,
                    title = name.substringBeforeLast('.'),
                    artist = "Zero Play Exported",
                    expectedDurationMs = metrics.durationMs
                ) { p ->
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 250L || p >= 0.96f) {
                        lastEmitMs = now
                        launch(Dispatchers.IO) {
                            repository.upsertConversionJob(baseJob.copy(progress = p.coerceIn(0.05f, 0.96f)))
                        }
                    }
                }.getOrThrow()
            }

            val outName = "ZeroPlay_${name.substringBeforeLast('.')}_audio.${targetFormat.ext}"
            val savedUri = withContext(Dispatchers.IO) {
                saveFileToMediaStoreAudio(outName, targetFormat.mimeType, tempOut)
            }
            val sizeAfter = tempOut.length()
            repository.registerExportedMedia(
                trackId = "exp_aud_${System.currentTimeMillis()}",
                title = outName.substringBeforeLast('.'),
                artist = "Exported Audio • ${targetFormat.ext.uppercase()}",
                uriString = savedUri.toString(),
                mediaType = MediaType.AUDIO,
                mimeType = targetFormat.mimeType,
                sizeBytes = sizeAfter
            )
            repository.upsertConversionJob(
                baseJob.copy(
                    progress = 1f,
                    status = JobStatus.COMPLETED,
                    outputUriString = savedUri.toString(),
                    sizeAfterBytes = sizeAfter
                )
            )
        } catch (t: Throwable) {
            repository.upsertConversionJob(
                baseJob.copy(
                    status = JobStatus.FAILED,
                    errorMessage = t.message ?: "Audio conversion failed"
                )
            )
        } finally {
            runCatching { tempIn.delete() }
            runCatching { tempOut.delete() }
        }
    }

    private suspend fun copyUriToFile(uri: Uri, dest: File) = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val src = File(uri.path.orEmpty())
            if (src.exists()) {
                src.copyTo(dest, overwrite = true)
                return@withContext
            }
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected media file")
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnMain(
        inputUri: Uri,
        outputFile: File,
        removeVideo: Boolean,
        removeAudio: Boolean,
        targetHeight: Int,
        targetBitrate: Int,
        videoMimeType: String,
        onProgress: suspend (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        val mediaItem = MediaItem.fromUri(inputUri)
        val editedBuilder = EditedMediaItem.Builder(mediaItem)
        if (removeAudio) editedBuilder.setRemoveAudio(true)
        if (removeVideo) {
            editedBuilder.setRemoveVideo(true)
        } else if (targetHeight > 0) {
            editedBuilder.setEffects(
                Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight)))
            )
        }
        val editedMediaItem = editedBuilder.build()

        val transformerBuilder = Transformer.Builder(context)
        if (removeVideo) {
            transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
        } else {
            transformerBuilder.setVideoMimeType(videoMimeType)
            if (targetBitrate > 0) {
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrate)
                            .build()
                    )
                    .build()
                transformerBuilder.setEncoderFactory(encoderFactory)
            }
        }

        var finished = false
        var exportError: Throwable? = null

        val transformer = transformerBuilder
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    exportError = exportException
                    finished = true
                }
            })
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)
        val progressHolder = ProgressHolder()
        while (!finished) {
            val state = transformer.getProgress(progressHolder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                withContext(Dispatchers.IO) {
                    onProgress(progressHolder.progress / 100f)
                }
            }
            delay(250L)
        }
        exportError?.let { throw it }
    }

    private fun saveBytesToMediaStoreImages(filename: String, mimeType: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ZeroPlay")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create MediaStore image entry")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun saveFileToMediaStoreVideo(filename: String, mimeType: String, source: File): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ZeroPlay")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val inserted = runCatching { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            if (inserted != null) {
                runCatching {
                    resolver.openOutputStream(inserted)?.use { out ->
                        FileInputStream(source).use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(inserted, values, null, null)
                }.onSuccess { return inserted }
            }
        }
        val outDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "ZeroPlay"
        ).apply { mkdirs() }
        val outFile = File(outDir, filename)
        FileInputStream(source).use { input -> FileOutputStream(outFile).use { output -> input.copyTo(output) } }
        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf(mimeType), null)
        return Uri.fromFile(outFile)
    }

    private fun saveFileToMediaStoreAudio(filename: String, mimeType: String, source: File): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/ZeroPlay")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val inserted = runCatching { resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            if (inserted != null) {
                runCatching {
                    resolver.openOutputStream(inserted)?.use { out ->
                        FileInputStream(source).use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(inserted, values, null, null)
                }.onSuccess { return inserted }
            }
        }
        val outDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
            "ZeroPlay"
        ).apply { mkdirs() }
        val outFile = File(outDir, filename)
        FileInputStream(source).use { input -> FileOutputStream(outFile).use { output -> input.copyTo(output) } }
        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf(mimeType), null)
        return Uri.fromFile(outFile)
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).name
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun queryFileSize(uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).length()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }
}
