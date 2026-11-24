package com.example.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.R
import com.example.data.model.MediaTrack
import com.example.data.model.MediaType
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

data class PlaybackUiState(
    val currentTrack: MediaTrack? = null,
    val queue: List<MediaTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isVideoContent: Boolean = false
)

class PlaybackController(
    private val context: Context,
    private val onTrackStarted: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var playbackLaunchJob: Job? = null
    private var pendingQueue: Pair<List<MediaTrack>, Int>? = null
    private val artworkByteCache = ConcurrentHashMap<String, ByteArray>()

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    val playerOrNull: Player?
        get() = mediaController

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, EchoMediaSessionService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                mediaController = controller
                attachListener(controller)
                pendingQueue?.let { (tracks, startIndex) ->
                    pendingQueue = null
                    playQueueInternal(controller, tracks, startIndex)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun attachListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressPolling() else stopProgressPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = controller.duration.coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = if (dur > 0) dur else it.currentTrack?.durationMs ?: 0L
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = controller.currentMediaItemIndex
                val queue = _uiState.value.queue
                val track = queue.getOrNull(idx)
                val dur = controller.duration.coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        currentIndex = idx,
                        currentTrack = track ?: it.currentTrack,
                        durationMs = if (dur > 0) dur else track?.durationMs ?: 0L,
                        positionMs = 0L,
                        isVideoContent = track?.mediaType == MediaType.VIDEO
                    )
                }
                if (track != null) {
                    onTrackStarted(track.id)
                    if (!artworkByteCache.containsKey(track.id)) {
                        scope.launch {
                            val bytes = withContext(Dispatchers.IO) { loadArtworkBytes(track) }
                            if (bytes != null && controller.currentMediaItemIndex == idx) {
                                val updatedItem = buildMediaItem(track, bytes)
                                runCatching { controller.replaceMediaItem(idx, updatedItem) }
                            }
                        }
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }
            }
        })
    }

    fun playTrack(track: MediaTrack, queue: List<MediaTrack> = listOf(track)) {
        val index = queue.indexOfFirst { it.id == track.id }.let { if (it >= 0) it else 0 }
        val finalQueue = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val controller = mediaController
        if (controller == null) {
            pendingQueue = finalQueue to index
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    queue = finalQueue,
                    currentIndex = index,
                    durationMs = track.durationMs,
                    isVideoContent = track.mediaType == MediaType.VIDEO
                )
            }
            connect()
        } else {
            playQueueInternal(controller, finalQueue, index)
        }
    }

    private fun playQueueInternal(controller: MediaController, tracks: List<MediaTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        val selected = tracks[safeIdx]
        _uiState.update {
            it.copy(
                currentTrack = selected,
                queue = tracks,
                currentIndex = safeIdx,
                durationMs = selected.durationMs,
                positionMs = 0L,
                isVideoContent = selected.mediaType == MediaType.VIDEO
            )
        }
        playbackLaunchJob?.cancel()
        playbackLaunchJob = scope.launch {
            val selectedArt = artworkByteCache[selected.id] ?: withTimeoutOrNull(450L) {
                withContext(Dispatchers.IO) { loadArtworkBytes(selected) }
            }
            val mediaItems = tracks.mapIndexed { idx, track ->
                val artBytes = if (idx == safeIdx) selectedArt else artworkByteCache[track.id]
                buildMediaItem(track, artBytes)
            }
            controller.setMediaItems(mediaItems, safeIdx, 0L)
            controller.prepare()
            controller.play()
            onTrackStarted(selected.id)
            startProgressPolling()

            // Pre-warm artwork bytes for neighboring queue items
            launch(Dispatchers.IO) {
                val neighborIndices = listOf(safeIdx + 1, safeIdx - 1, safeIdx + 2)
                    .filter { it in tracks.indices && !artworkByteCache.containsKey(tracks[it].id) }
                for (nIdx in neighborIndices) {
                    val neighbor = tracks[nIdx]
                    val bytes = loadArtworkBytes(neighbor)
                    if (bytes != null) {
                        withContext(Dispatchers.Main) {
                            if (_uiState.value.queue.getOrNull(nIdx)?.id == neighbor.id) {
                                runCatching {
                                    controller.replaceMediaItem(nIdx, buildMediaItem(neighbor, bytes))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun playNextInQueue(track: MediaTrack) {
        val controller = mediaController ?: return
        val currentQueue = _uiState.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            playTrack(track)
            return
        }
        val insertIndex = (_uiState.value.currentIndex + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, track)
        val mediaItem = buildMediaItem(track, artworkByteCache[track.id])
        controller.addMediaItem(insertIndex, mediaItem)
        _uiState.update { it.copy(queue = currentQueue) }
        if (!artworkByteCache.containsKey(track.id)) {
            scope.launch(Dispatchers.IO) {
                val bytes = loadArtworkBytes(track)
                if (bytes != null) {
                    withContext(Dispatchers.Main) {
                        if (_uiState.value.queue.getOrNull(insertIndex)?.id == track.id) {
                            runCatching {
                                controller.replaceMediaItem(insertIndex, buildMediaItem(track, bytes))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildMediaItem(track: MediaTrack, artworkBytes: ByteArray?): MediaItem {
        val cleanArtist = sanitizeArtist(track.artist, track.mediaType)
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(cleanArtist)
            .setAlbumTitle(track.album.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "Zero Play")
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metaBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        } else if (!track.artworkUriString.isNullOrBlank()) {
            runCatching { Uri.parse(track.artworkUriString) }.getOrNull()?.let { artUri ->
                metaBuilder.setArtworkUri(artUri)
            }
        }
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.uriString))
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private fun sanitizeArtist(artist: String?, mediaType: MediaType): String {
        val trimmed = artist?.trim().orEmpty()
        return if (trimmed.isEmpty() || trimmed.equals("<unknown>", ignoreCase = true) || trimmed.equals("unknown", ignoreCase = true)) {
            if (mediaType == MediaType.VIDEO) "Zero Play Video" else "Zero Play Artist"
        } else {
            trimmed
        }
    }

    private fun loadArtworkBytes(track: MediaTrack): ByteArray? {
        artworkByteCache[track.id]?.let { return it }
        var bitmap: Bitmap? = null

        // 1. Try explicit artwork URI (MediaStore album art or content URI)
        if (!track.artworkUriString.isNullOrBlank()) {
            val artUri = runCatching { Uri.parse(track.artworkUriString) }.getOrNull()
            if (artUri != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && artUri.scheme == "content") {
                    bitmap = runCatching {
                        context.contentResolver.loadThumbnail(artUri, Size(320, 320), null)
                    }.getOrNull()
                }
                if (bitmap == null) {
                    bitmap = runCatching {
                        context.contentResolver.openInputStream(artUri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }.getOrNull()
                }
            }
        }

        // 2. Try MediaStore / MediaMetadataRetriever for local video frame or audio cover
        if (bitmap == null) {
            val mediaUri = runCatching { Uri.parse(track.uriString) }.getOrNull()
            if (mediaUri != null && (mediaUri.scheme == "content" || mediaUri.scheme == "file")) {
                if (track.mediaType == MediaType.VIDEO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaUri.scheme == "content") {
                    bitmap = runCatching {
                        context.contentResolver.loadThumbnail(mediaUri, Size(360, 360), null)
                    }.getOrNull()
                }
                if (bitmap == null) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, mediaUri)
                        if (track.mediaType == MediaType.VIDEO) {
                            bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?: retriever.getFrameAtTime(-1L)
                        } else {
                            retriever.embeddedPicture?.let { picBytes ->
                                bitmap = BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size)
                            }
                        }
                    } catch (_: Throwable) {
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
                if (bitmap == null && track.mediaType == MediaType.VIDEO && mediaUri.scheme == "content") {
                    runCatching { ContentUris.parseId(mediaUri) }.getOrNull()?.let { id ->
                        @Suppress("DEPRECATION")
                        bitmap = runCatching {
                            MediaStore.Video.Thumbnails.getThumbnail(
                                context.contentResolver,
                                id,
                                MediaStore.Video.Thumbnails.MINI_KIND,
                                null
                            )
                        }.getOrNull()
                    }
                }
            }
        }

        // 3. Fallback to bundled cover drawable or Zero Play brand logo
        if (bitmap == null && track.artworkResId != null) {
            bitmap = runCatching {
                BitmapFactory.decodeResource(context.resources, track.artworkResId)
            }.getOrNull()
        }
        if (bitmap == null) {
            bitmap = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.ic_zero_play_logo)
            }.getOrNull()
        }

        val raw = bitmap ?: return null
        val maxDim = max(raw.width, raw.height).coerceAtLeast(1)
        val scaled = if (maxDim > 320) {
            val ratio = 320f / maxDim.toFloat()
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * ratio).toInt().coerceAtLeast(1),
                (raw.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            raw
        }

        return runCatching {
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        }.getOrNull()?.also { bytes ->
            if (bytes.isNotEmpty()) {
                artworkByteCache[track.id] = bytes
            }
            if (scaled !== raw) scaled.recycle()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && _uiState.value.currentTrack != null) {
                playTrack(_uiState.value.currentTrack!!, _uiState.value.queue)
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (controller.mediaItemCount > 0) {
            controller.seekTo(0, 0L)
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun updateCurrentTrackMetadata(updated: MediaTrack) {
        _uiState.update { state ->
            val updatedQueue = state.queue.map { if (it.id == updated.id) updated else it }
            state.copy(
                currentTrack = if (state.currentTrack?.id == updated.id) updated else state.currentTrack,
                queue = updatedQueue
            )
        }
    }

    fun applyAudioEngineSettings(skipSilence: Boolean, pauseOnHeadsetDisconnect: Boolean) {
        val controller = mediaController ?: return
        controller.sendCustomCommand(
            SessionCommand(EchoMediaSessionService.CMD_SET_SKIP_SILENCE, Bundle.EMPTY),
            Bundle().apply { putBoolean(EchoMediaSessionService.EXTRA_ENABLED, skipSilence) }
        )
        controller.sendCustomCommand(
            SessionCommand(EchoMediaSessionService.CMD_SET_NOISY_PAUSE, Bundle.EMPTY),
            Bundle().apply { putBoolean(EchoMediaSessionService.EXTRA_ENABLED, pauseOnHeadsetDisconnect) }
        )
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _uiState.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = if (dur > 0) dur else it.durationMs,
                            isPlaying = controller.isPlaying
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressPolling()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
