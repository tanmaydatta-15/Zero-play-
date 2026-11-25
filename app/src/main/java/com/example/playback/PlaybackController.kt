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
import androidx.media3.common.PlaybackException
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
    private var artworkWarmupJob: Job? = null
    private var pendingQueue: Pair<List<MediaTrack>, Int>? = null
    private var pendingTrackId: String? = null
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
                val activeMediaId = controller.currentMediaItem?.mediaId
                val expectedId = pendingTrackId
                if (expectedId != null && activeMediaId != null && activeMediaId != expectedId) {
                    // Ignore stale pause/stop events from the previous track while switching
                    return
                }
                if (activeMediaId == expectedId && isPlaying) {
                    pendingTrackId = null
                }
                _uiState.update { state ->
                    if (state.currentTrack == null) state
                    else state.copy(
                        isPlaying = isPlaying,
                        isBuffering = if (isPlaying) false else state.isBuffering
                    )
                }
                if (isPlaying) startProgressPolling() else stopProgressPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val activeMediaId = controller.currentMediaItem?.mediaId
                val expectedId = pendingTrackId
                if (expectedId != null && activeMediaId != null && activeMediaId != expectedId) {
                    return
                }
                if (playbackState == Player.STATE_READY) {
                    pendingTrackId = null
                }
                val dur = controller.duration.takeIf { it > 0L } ?: 0L
                _uiState.update { state ->
                    if (state.currentTrack == null) state
                    else state.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = if (dur > 0L) dur else state.currentTrack.durationMs
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                pendingTrackId = null
                stopProgressPolling()
                _uiState.update { state ->
                    state.copy(isPlaying = false, isBuffering = false)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val transitionedId = mediaItem?.mediaId
                val expectedId = pendingTrackId
                if (expectedId != null && transitionedId != null && transitionedId != expectedId) {
                    // Ignore intermediate transition emitted from the outgoing track
                    return
                }
                if (transitionedId != null && transitionedId == expectedId) {
                    pendingTrackId = null
                }

                val queue = _uiState.value.queue
                if (queue.isEmpty() && mediaItem == null) return

                val idx = controller.currentMediaItemIndex
                val track = transitionedId?.let { id -> queue.firstOrNull { it.id == id } }
                    ?: queue.getOrNull(idx)
                    ?: return
                val resolvedIdx = queue.indexOfFirst { it.id == track.id }.let { if (it >= 0) it else idx }
                val dur = controller.duration.takeIf { it > 0L } ?: 0L

                val previousTrackId = _uiState.value.currentTrack?.id
                _uiState.update {
                    it.copy(
                        currentIndex = resolvedIdx,
                        currentTrack = track,
                        durationMs = if (dur > 0L) dur else track.durationMs,
                        positionMs = 0L,
                        isVideoContent = track.mediaType == MediaType.VIDEO
                    )
                }
                if (track.id != previousTrackId) {
                    onTrackStarted(track.id)
                }
                // Warm artwork cache in background ONLY for next session/neighbors without calling replaceMediaItem on active playback
                prewarmNeighborArtwork(queue, resolvedIdx)
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
            pendingTrackId = track.id
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    queue = finalQueue,
                    currentIndex = index,
                    durationMs = track.durationMs,
                    positionMs = 0L,
                    isBuffering = true,
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

        // Immediately stop stale progress polling so the outgoing track's position never overwrites the new track
        stopProgressPolling()
        artworkWarmupJob?.cancel()
        pendingTrackId = selected.id

        val currentState = _uiState.value
        val sameQueue = currentState.queue.size == tracks.size &&
            controller.mediaItemCount == tracks.size &&
            currentState.queue.indices.all { currentState.queue[it].id == tracks[it].id }

        _uiState.update {
            it.copy(
                currentTrack = selected,
                queue = tracks,
                currentIndex = safeIdx,
                durationMs = selected.durationMs,
                positionMs = 0L,
                isBuffering = true,
                isVideoContent = selected.mediaType == MediaType.VIDEO
            )
        }

        if (sameQueue && safeIdx in 0 until controller.mediaItemCount) {
            // Instant zero-rebuild seek within the already loaded queue
            controller.seekToDefaultPosition(safeIdx)
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            controller.play()
        } else {
            // Build and start playback immediately (0ms delay) without blocking on bitmap decoding
            val mediaItems = tracks.map { track ->
                buildMediaItem(track, artworkByteCache[track.id])
            }
            controller.setMediaItems(mediaItems, safeIdx, 0L)
            controller.prepare()
            controller.play()
        }

        onTrackStarted(selected.id)
        prewarmNeighborArtwork(tracks, safeIdx)
    }

    private fun prewarmNeighborArtwork(tracks: List<MediaTrack>, currentIdx: Int) {
        artworkWarmupJob?.cancel()
        artworkWarmupJob = scope.launch(Dispatchers.IO) {
            val indices = listOf(currentIdx, currentIdx + 1, currentIdx - 1, currentIdx + 2)
                .filter { it in tracks.indices && !artworkByteCache.containsKey(tracks[it].id) }
            for (idx in indices) {
                if (!isActive) break
                loadArtworkBytes(tracks[idx])
            }
        }
    }

    fun dismissPlayback() {
        pendingQueue = null
        pendingTrackId = null
        artworkWarmupJob?.cancel()
        artworkWarmupJob = null
        stopProgressPolling()
        mediaController?.let { controller ->
            runCatching {
                controller.pause()
                controller.stop()
                controller.clearMediaItems()
            }
        }
        _uiState.value = PlaybackUiState()
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
                loadArtworkBytes(track)
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
        val queue = _uiState.value.queue
        if (controller.hasNextMediaItem()) {
            val nextIdx = controller.nextMediaItemIndex
            queue.getOrNull(nextIdx)?.let { nextTrack ->
                stopProgressPolling()
                pendingTrackId = nextTrack.id
                _uiState.update {
                    it.copy(
                        currentTrack = nextTrack,
                        currentIndex = nextIdx,
                        durationMs = nextTrack.durationMs,
                        positionMs = 0L,
                        isBuffering = true,
                        isVideoContent = nextTrack.mediaType == MediaType.VIDEO
                    )
                }
            }
            controller.seekToNextMediaItem()
            if (!controller.playWhenReady) controller.play()
        } else if (controller.mediaItemCount > 0) {
            queue.firstOrNull()?.let { firstTrack ->
                stopProgressPolling()
                pendingTrackId = firstTrack.id
                _uiState.update {
                    it.copy(
                        currentTrack = firstTrack,
                        currentIndex = 0,
                        durationMs = firstTrack.durationMs,
                        positionMs = 0L,
                        isBuffering = true,
                        isVideoContent = firstTrack.mediaType == MediaType.VIDEO
                    )
                }
            }
            controller.seekTo(0, 0L)
            if (!controller.playWhenReady) controller.play()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        val queue = _uiState.value.queue
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
            _uiState.update { it.copy(positionMs = 0L) }
        } else if (controller.hasPreviousMediaItem()) {
            val prevIdx = controller.previousMediaItemIndex
            queue.getOrNull(prevIdx)?.let { prevTrack ->
                stopProgressPolling()
                pendingTrackId = prevTrack.id
                _uiState.update {
                    it.copy(
                        currentTrack = prevTrack,
                        currentIndex = prevIdx,
                        durationMs = prevTrack.durationMs,
                        positionMs = 0L,
                        isBuffering = true,
                        isVideoContent = prevTrack.mediaType == MediaType.VIDEO
                    )
                }
            }
            controller.seekToPreviousMediaItem()
            if (!controller.playWhenReady) controller.play()
        } else {
            controller.seekTo(0L)
            _uiState.update { it.copy(positionMs = 0L) }
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
        artworkByteCache.remove(updated.id)
        _uiState.update { state ->
            val updatedQueue = state.queue.map { if (it.id == updated.id) updated else it }
            state.copy(
                currentTrack = if (state.currentTrack?.id == updated.id) updated else state.currentTrack,
                queue = updatedQueue
            )
        }
        // Only replace non-active queue items so active playback never stutters or restarts
        val controller = mediaController ?: return
        val idx = _uiState.value.queue.indexOfFirst { it.id == updated.id }
        if (idx in 0 until controller.mediaItemCount && idx != controller.currentMediaItemIndex) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { loadArtworkBytes(updated) }
                if (idx != controller.currentMediaItemIndex && _uiState.value.queue.getOrNull(idx)?.id == updated.id) {
                    runCatching { controller.replaceMediaItem(idx, buildMediaItem(updated, bytes)) }
                }
            }
        }
    }

    fun applyEqualizerState(state: EqualizerState) {
        val controller = mediaController ?: return
        controller.sendCustomCommand(
            SessionCommand(EchoMediaSessionService.CMD_APPLY_AUDIO_EFFECTS, Bundle.EMPTY),
            state.toBundle()
        )
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
                if (controller != null && _uiState.value.currentTrack != null && pendingTrackId == null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0L } ?: 0L
                    _uiState.update { state ->
                        if (state.currentTrack == null) state
                        else state.copy(
                            positionMs = pos,
                            durationMs = if (dur > 0L) dur else state.durationMs,
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
