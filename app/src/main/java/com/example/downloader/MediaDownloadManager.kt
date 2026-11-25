package com.example.downloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.SealAudioFormatPref
import com.example.data.local.SealFilenameTemplatePref
import com.example.data.local.SealPreferredVideoFormatPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.DownloadTaskUi
import com.example.data.model.JobStatus
import com.example.data.model.Mp3BitrateOption
import com.example.data.model.VideoContainerOption
import com.example.data.repository.EchoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MediaDownloadManager(
    private val context: Context,
    @Suppress("unused") private val repository: EchoRepository
) {
    companion object {
        fun defaultHeightForPreset(preset: DownloadFormatPreset): Int = when (preset) {
            DownloadFormatPreset.VIDEO_MP4_4K -> 2160
            DownloadFormatPreset.VIDEO_MKV_HEVC,
            DownloadFormatPreset.VIDEO_MKV_AV1,
            DownloadFormatPreset.VIDEO_MKV_HIGH,
            DownloadFormatPreset.VIDEO_MP4_HIGH -> 1080
            DownloadFormatPreset.VIDEO_MP4_STANDARD -> 720
            DownloadFormatPreset.VIDEO_MP4_COMPACT -> 480
            else -> 1080
        }
    }

    private val router = HybridExtractionRouter.getInstance(context)
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workIdsByTaskId = mutableMapOf<String, UUID>()
    private val observerJobs = mutableMapOf<String, Job>()

    private val _downloads = MutableStateFlow<List<DownloadTaskUi>>(emptyList())
    val downloads: StateFlow<List<DownloadTaskUi>> = _downloads.asStateFlow()

    init {
        // Warm up NewPipeExtractor, yt-dlp-android, and FFmpegKit on background coroutine
        scope.launch {
            router.initializeEngines()
        }
    }

    suspend fun getYtDlpVersionLabel(): String = router.ytDlpEngine.getVersionLabel()

    suspend fun updateYtDlp(): Result<String> = router.ytDlpEngine.updateYtDlp()

    fun enqueueDownload(
        rawUrl: String,
        customTitle: String?,
        preset: DownloadFormatPreset,
        maxVideoHeightPx: Int? = null,
        wifiOnly: Boolean = false,
        restrictFilenames: Boolean = true,
        @Suppress("UNUSED_PARAMETER") splitSubfolders: Boolean = true,
        filenameTemplate: SealFilenameTemplatePref = SealFilenameTemplatePref.TITLE_ARTIST,
        preferredVideoFormat: SealPreferredVideoFormatPref = SealPreferredVideoFormatPref.QUALITY,
        preferredCodec: VideoCodecPreference = VideoCodecPreference.AUTO,
        videoContainer: VideoContainerOption = VideoContainerOption.MP4,
        audioFormat: SealAudioFormatPref = SealAudioFormatPref.NOT_SPECIFIED,
        mp3Bitrate: Mp3BitrateOption = Mp3BitrateOption.KBPS_320,
        embedMetadata: Boolean = true,
        embedSubtitles: Boolean = false,
        saveThumbnail: Boolean = true,
        customCommandFlags: String? = null,
        autoImportToLibrary: Boolean = true,
        onCompleted: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Result<DownloadTaskUi> {
        val trimmed = rawUrl.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid URL"))
        if (uri.scheme != "http" && uri.scheme != "https") {
            return Result.failure(IllegalArgumentException("Enter a valid http:// or https:// media URL"))
        }
        if (wifiOnly && !isOnUnmeteredOrWifi()) {
            return Result.failure(IllegalArgumentException("Wi-Fi only downloads enabled in Settings"))
        }

        val initialName = customTitle?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: "ZeroPlay_${System.currentTimeMillis() / 1000}"

        val taskId = UUID.randomUUID().toString()
        val maxHeight = maxVideoHeightPx ?: defaultHeightForPreset(preset)
        val initialBadge = if (preset.isVideo) {
            "${if (preferredCodec != VideoCodecPreference.AUTO) "${preferredCodec.shortBadge} • " else ""}${videoContainer.ext.uppercase()}"
        } else {
            preset.extension.uppercase()
        }

        val pendingTask = DownloadTaskUi(
            id = taskId,
            downloadManagerId = System.currentTimeMillis(),
            url = trimmed,
            title = initialName,
            preset = preset,
            progress = 0.03f,
            status = JobStatus.RUNNING,
            engineLabel = if (HybridExtractionRouter.isDirectStreamUrl(trimmed)) "Direct" else "Hybrid",
            formatBadge = initialBadge
        )
        _downloads.update { listOf(pendingTask) + it }

        val inputData = Data.Builder()
            .putString(MediaDownloadWorker.KEY_TASK_ID, taskId)
            .putString(MediaDownloadWorker.KEY_URL, trimmed)
            .putString(MediaDownloadWorker.KEY_CUSTOM_TITLE, customTitle)
            .putString(MediaDownloadWorker.KEY_PRESET, preset.name)
            .putInt(MediaDownloadWorker.KEY_MAX_HEIGHT, maxHeight)
            .putString(MediaDownloadWorker.KEY_PREFERRED_VIDEO_FMT, preferredVideoFormat.name)
            .putString(MediaDownloadWorker.KEY_PREFERRED_CODEC, preferredCodec.name)
            .putString(MediaDownloadWorker.KEY_VIDEO_CONTAINER, videoContainer.name)
            .putString(MediaDownloadWorker.KEY_AUDIO_FORMAT, audioFormat.name)
            .putString(MediaDownloadWorker.KEY_MP3_BITRATE, mp3Bitrate.name)
            .putBoolean(MediaDownloadWorker.KEY_EMBED_METADATA, embedMetadata)
            .putBoolean(MediaDownloadWorker.KEY_EMBED_SUBS, embedSubtitles)
            .putBoolean(MediaDownloadWorker.KEY_SAVE_THUMB, saveThumbnail)
            .putBoolean(MediaDownloadWorker.KEY_RESTRICT_NAMES, restrictFilenames)
            .putString(MediaDownloadWorker.KEY_FILENAME_TEMPLATE, filenameTemplate.name)
            .putString(MediaDownloadWorker.KEY_CUSTOM_FLAGS, customCommandFlags)
            .putBoolean(MediaDownloadWorker.KEY_AUTO_IMPORT, autoImportToLibrary)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("zero_play_dl_$taskId")
            .build()

        workIdsByTaskId[taskId] = workRequest.id
        workManager.enqueue(workRequest)

        val observer = scope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> {
                        _downloads.update { list ->
                            list.map { if (it.id == taskId) it.copy(status = JobStatus.QUEUED) else it }
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        val p = info.progress.getFloat(MediaDownloadWorker.KEY_PROGRESS, 0.05f)
                        val speed = info.progress.getString(MediaDownloadWorker.KEY_SPEED)
                        val eta = info.progress.getString(MediaDownloadWorker.KEY_ETA)
                        val engine = info.progress.getString(MediaDownloadWorker.KEY_ENGINE)
                        _downloads.update { list ->
                            list.map {
                                if (it.id == taskId) {
                                    it.copy(
                                        status = JobStatus.RUNNING,
                                        progress = p.coerceIn(0.03f, 0.99f),
                                        speedLabel = speed ?: it.speedLabel,
                                        etaLabel = eta ?: it.etaLabel,
                                        engineLabel = engine ?: it.engineLabel
                                    )
                                } else it
                            }
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val finalTitle = info.outputData.getString(MediaDownloadWorker.KEY_TITLE) ?: initialName
                        val outputUri = info.outputData.getString(MediaDownloadWorker.KEY_OUTPUT_URI)
                        val engine = info.outputData.getString(MediaDownloadWorker.KEY_ENGINE)
                        val badge = info.outputData.getString(MediaDownloadWorker.KEY_BADGE)
                        _downloads.update { list ->
                            list.map {
                                if (it.id == taskId) {
                                    it.copy(
                                        title = finalTitle,
                                        progress = 1f,
                                        status = JobStatus.COMPLETED,
                                        localUriString = outputUri,
                                        speedLabel = null,
                                        etaLabel = null,
                                        engineLabel = engine ?: it.engineLabel,
                                        formatBadge = badge ?: it.formatBadge
                                    )
                                } else it
                            }
                        }
                        onCompleted?.invoke(finalTitle)
                        workIdsByTaskId.remove(taskId)
                        observerJobs.remove(taskId)?.cancel()
                    }
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED -> {
                        val err = info.outputData.getString(MediaDownloadWorker.KEY_ERROR)
                            ?: if (info.state == WorkInfo.State.CANCELLED) "Download cancelled" else "Download failed"
                        _downloads.update { list ->
                            list.map {
                                if (it.id == taskId) {
                                    it.copy(
                                        status = JobStatus.FAILED,
                                        progress = 0f,
                                        speedLabel = null,
                                        etaLabel = null,
                                        errorMessage = err
                                    )
                                } else it
                            }
                        }
                        if (info.state == WorkInfo.State.FAILED) {
                            onError?.invoke(err)
                        }
                        workIdsByTaskId.remove(taskId)
                        observerJobs.remove(taskId)?.cancel()
                    }
                }
            }
        }
        observerJobs[taskId] = observer
        return Result.success(pendingTask)
    }

    fun dismissTask(taskId: String) {
        workIdsByTaskId.remove(taskId)?.let { uuid ->
            runCatching { workManager.cancelWorkById(uuid) }
        }
        router.cancel(taskId)
        observerJobs.remove(taskId)?.cancel()
        _downloads.update { list -> list.filterNot { it.id == taskId } }
    }

    private fun isOnUnmeteredOrWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
