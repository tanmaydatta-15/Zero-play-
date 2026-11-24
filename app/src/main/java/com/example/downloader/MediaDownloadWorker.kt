package com.example.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.EchoDatabase
import com.example.data.local.SealAudioFormatPref
import com.example.data.local.SealFilenameTemplatePref
import com.example.data.local.SealPreferredVideoFormatPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.MediaType
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.VideoContainerOption
import com.example.data.repository.EchoRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MediaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "zero_play_downloads"
        const val KEY_TASK_ID = "task_id"
        const val KEY_URL = "url"
        const val KEY_CUSTOM_TITLE = "custom_title"
        const val KEY_PRESET = "preset"
        const val KEY_MAX_HEIGHT = "max_height"
        const val KEY_PREFERRED_VIDEO_FMT = "pref_video_fmt"
        const val KEY_PREFERRED_CODEC = "pref_codec"
        const val KEY_VIDEO_CONTAINER = "video_container"
        const val KEY_AUDIO_FORMAT = "audio_format"
        const val KEY_MP3_BITRATE = "mp3_bitrate"
        const val KEY_EMBED_METADATA = "embed_metadata"
        const val KEY_EMBED_SUBS = "embed_subs"
        const val KEY_SAVE_THUMB = "save_thumb"
        const val KEY_RESTRICT_NAMES = "restrict_names"
        const val KEY_FILENAME_TEMPLATE = "filename_template"
        const val KEY_CUSTOM_FLAGS = "custom_flags"
        const val KEY_AUTO_IMPORT = "auto_import"

        const val KEY_PROGRESS = "progress"
        const val KEY_SPEED = "speed"
        const val KEY_ETA = "eta"
        const val KEY_ENGINE = "engine"
        const val KEY_TITLE = "title"
        const val KEY_BADGE = "badge"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_ERROR = "error"
    }

    private val router = HybridExtractionRouter.getInstance(appContext)
    private val notificationId = (inputData.getString(KEY_TASK_ID)?.hashCode() ?: id.hashCode()).let {
        2000 + (it and 0x0FFF)
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: id.toString()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val customTitle = inputData.getString(KEY_CUSTOM_TITLE)
        val preset = parseEnum(inputData.getString(KEY_PRESET), DownloadFormatPreset.VIDEO_MP4_HIGH)
        val maxHeight = inputData.getInt(KEY_MAX_HEIGHT, 1080)
        val prefVideoFmt = parseEnum(inputData.getString(KEY_PREFERRED_VIDEO_FMT), SealPreferredVideoFormatPref.QUALITY)
        val prefCodec = parseEnum(inputData.getString(KEY_PREFERRED_CODEC), VideoCodecPreference.AUTO)
        val container = parseEnum(inputData.getString(KEY_VIDEO_CONTAINER), VideoContainerOption.MP4)
        val audioFormat = parseEnum(inputData.getString(KEY_AUDIO_FORMAT), SealAudioFormatPref.NOT_SPECIFIED)
        val mp3Bitrate = parseEnum(inputData.getString(KEY_MP3_BITRATE), Mp3BitrateOption.KBPS_320)
        val embedMetadata = inputData.getBoolean(KEY_EMBED_METADATA, true)
        val embedSubs = inputData.getBoolean(KEY_EMBED_SUBS, false)
        val saveThumb = inputData.getBoolean(KEY_SAVE_THUMB, true)
        val restrictNames = inputData.getBoolean(KEY_RESTRICT_NAMES, true)
        val filenameTemplate = parseEnum(inputData.getString(KEY_FILENAME_TEMPLATE), SealFilenameTemplatePref.TITLE_ARTIST)
        val customFlags = inputData.getString(KEY_CUSTOM_FLAGS)
        val autoImport = inputData.getBoolean(KEY_AUTO_IMPORT, true)

        val initialTitle = customTitle?.takeIf { it.isNotBlank() } ?: "Zero Play Download"
        var activeRouteBadge = "Preparing"
        var lastNotifTime = 0L

        ensureNotificationChannel()
        runCatching {
            setForeground(createForegroundInfo(initialTitle, 2, null, null, activeRouteBadge))
        }

        val workDir = File(applicationContext.cacheDir, "dl_work/wm_$taskId").apply { mkdirs() }
        return try {
            val req = HybridDownloadRequest(
                taskId = taskId,
                url = url,
                workDir = workDir,
                preset = preset,
                maxVideoHeightPx = maxHeight,
                preferredVideoFormat = prefVideoFmt,
                preferredCodec = prefCodec,
                videoContainer = container,
                audioFormat = audioFormat,
                mp3Bitrate = mp3Bitrate,
                embedMetadata = embedMetadata,
                embedSubtitles = embedSubs,
                saveThumbnail = saveThumb,
                restrictFilenames = restrictNames,
                customCommandFlags = customFlags
            )

            val outputResult = router.execute(
                req = req,
                onRouteSelected = { route ->
                    activeRouteBadge = route.badge
                },
                onProgress = { tp ->
                    if (isStopped) {
                        router.cancel(taskId)
                        return@execute
                    }
                    val pct = (tp.fraction * 100f).toInt().coerceIn(0, 99)
                    setProgressAsync(
                        Data.Builder()
                            .putFloat(KEY_PROGRESS, tp.fraction)
                            .putString(KEY_SPEED, tp.speedLabel)
                            .putString(KEY_ETA, tp.etaLabel)
                            .putString(KEY_ENGINE, activeRouteBadge)
                            .build()
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastNotifTime >= 450L) {
                        lastNotifTime = now
                        updateNotification(initialTitle, pct, tp.speedLabel, tp.etaLabel, activeRouteBadge)
                    }
                }
            )

            if (isStopped) {
                router.cancel(taskId)
                return Result.failure(Data.Builder().putString(KEY_ERROR, "Cancelled").build())
            }

            if (outputResult.isFailure) {
                val err = outputResult.exceptionOrNull()?.message ?: "Download failed"
                return Result.failure(Data.Builder().putString(KEY_ERROR, err).build())
            }

            val out = outputResult.getOrThrow()
            val rawBase = customTitle?.takeIf { it.isNotBlank() }
                ?: out.title?.takeIf { it.isNotBlank() }
                ?: "ZeroPlay_${System.currentTimeMillis() / 1000}"
            val cleanBase = formatDisplayName(rawBase, restrictNames, filenameTemplate)
            val ext = out.extension.lowercase().ifBlank { if (out.isVideo) "mp4" else "m4a" }
            val mimeType = mimeForExtension(ext, out.isVideo)
            val fileName = "$cleanBase.$ext"

            val exportedUri = exportToMediaLibrary(
                sourceFile = out.file,
                fileName = fileName,
                mimeType = mimeType,
                isVideo = out.isVideo
            )

            val badgeText = listOfNotNull(
                out.codecBadge?.takeIf { it.isNotBlank() },
                ext.uppercase()
            ).distinct().joinToString(" • ")

            if (autoImport) {
                val dao = EchoDatabase.getInstance(applicationContext).echoDao()
                val repo = EchoRepository(applicationContext, dao)
                val platform = out.artist?.takeIf { it.isNotBlank() } ?: out.route.badge
                repo.markDownloaded(
                    trackId = "dl_${taskId.take(12)}",
                    title = cleanBase,
                    artist = "$platform • $badgeText",
                    uriString = exportedUri.toString(),
                    mediaType = if (out.isVideo) MediaType.VIDEO else MediaType.AUDIO,
                    mimeType = mimeType,
                    sizeBytes = out.file.length()
                )
            }

            Result.success(
                Data.Builder()
                    .putString(KEY_TITLE, cleanBase)
                    .putString(KEY_OUTPUT_URI, exportedUri.toString())
                    .putString(KEY_ENGINE, out.route.badge)
                    .putString(KEY_BADGE, badgeText)
                    .build()
            )
        } catch (t: Throwable) {
            Result.failure(Data.Builder().putString(KEY_ERROR, t.message ?: "Download failed").build())
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun exportToMediaLibrary(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        isVideo: Boolean
    ): Uri {
        val resolver = applicationContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val relativeDir = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/ZeroPlay"
            } else {
                "${Environment.DIRECTORY_MUSIC}/ZeroPlay"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val inserted = runCatching { resolver.insert(collection, values) }.getOrNull()
            if (inserted != null) {
                runCatching {
                    resolver.openOutputStream(inserted)?.use { out ->
                        FileInputStream(sourceFile).use { input -> input.copyTo(out, 256 * 1024) }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(inserted, values, null, null)
                }.onSuccess {
                    return inserted
                }
            }
            // Fallback to MediaStore.Downloads if container MIME (e.g. .mkv / .opus) is rejected by Audio/Video table on an OEM
            val dlValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ZeroPlay")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val dlUri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, dlValues) }.getOrNull()
            if (dlUri != null) {
                resolver.openOutputStream(dlUri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out, 256 * 1024) }
                }
                dlValues.clear()
                dlValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(dlUri, dlValues, null, null)
                return dlUri
            }
        }

        val dirType = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val outDir = File(
            applicationContext.getExternalFilesDir(dirType) ?: applicationContext.filesDir,
            "ZeroPlay"
        ).apply { mkdirs() }
        val outFile = File(outDir, fileName)
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output, 256 * 1024) }
        }
        MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(outFile.absolutePath),
            arrayOf(mimeType),
            null
        )
        return Uri.fromFile(outFile)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zero Play Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active media downloads, stream muxing, and codec transcoding"
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(
        title: String,
        progressPct: Int,
        speed: String?,
        eta: String?,
        routeBadge: String
    ): ForegroundInfo {
        val notification = buildNotification(title, progressPct, speed, eta, routeBadge)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotification(
        title: String,
        progressPct: Int,
        speed: String?,
        eta: String?,
        routeBadge: String
    ) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            nm.notify(notificationId, buildNotification(title, progressPct, speed, eta, routeBadge))
        }
    }

    private fun buildNotification(
        title: String,
        progressPct: Int,
        speed: String?,
        eta: String?,
        routeBadge: String
    ): Notification {
        val cancelPendingIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val details = buildList {
            add(routeBadge)
            add("$progressPct%")
            if (!speed.isNullOrBlank()) add(speed)
            if (!eta.isNullOrBlank()) add("ETA $eta")
        }.joinToString(" • ")

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(details)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPct, progressPct <= 2)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun formatDisplayName(
        base: String,
        restrictFilenames: Boolean,
        template: SealFilenameTemplatePref
    ): String {
        val templated = when (template) {
            SealFilenameTemplatePref.TITLE_ONLY -> base
            SealFilenameTemplatePref.TITLE_ARTIST -> "$base - ZeroPlay"
            SealFilenameTemplatePref.ARTIST_TITLE -> "ZeroPlay - $base"
            SealFilenameTemplatePref.TITLE_TIMESTAMP -> "${base}_${System.currentTimeMillis() / 1000}"
        }
        return if (restrictFilenames) {
            templated.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(56)
                .ifBlank { "ZeroPlay_${System.currentTimeMillis() / 1000}" }
        } else {
            templated.replace("/", "_").take(56)
        }
    }

    private fun mimeForExtension(ext: String, isVideo: Boolean): String = when (ext.lowercase()) {
        "mp4" -> if (isVideo) "video/mp4" else "audio/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> if (isVideo) "video/webm" else "audio/webm"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> if (isVideo) "video/mp4" else "audio/mp4"
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
