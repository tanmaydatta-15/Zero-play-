package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.R
import com.example.data.local.ConversionJobEntity
import com.example.data.local.EchoDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.TrackRecordEntity
import com.example.data.model.ConversionJobUi
import com.example.data.model.ConversionType
import com.example.data.model.JobStatus
import com.example.data.model.MediaSource
import com.example.data.model.MediaTrack
import com.example.data.model.MediaType
import com.example.data.model.PlaylistUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class EchoRepository(
    private val context: Context,
    private val dao: EchoDao
) {
    val allTracks: Flow<List<MediaTrack>> = dao.observeAllTracks().map { entities ->
        entities.map { it.toDomain() }
    }

    val playlists: Flow<List<PlaylistUi>> = combine(
        dao.observePlaylists(),
        dao.observePlaylistTrackRefs(),
        allTracks
    ) { playlistEntities, refs, tracks ->
        val trackMap = tracks.associateBy { it.id }
        val groupedRefs = refs.groupBy { it.playlistId }
        playlistEntities.map { entity ->
            val playlistTracks = groupedRefs[entity.id].orEmpty().mapNotNull { trackMap[it.trackId] }
            PlaylistUi(
                id = entity.id,
                name = entity.name,
                tracks = playlistTracks,
                coverResId = sanitizeDrawableId(
                    entity.coverResId,
                    fallback = playlistTracks.firstOrNull()?.artworkResId ?: R.drawable.img_cover_aurora_1794995218098
                ),
                createdAt = entity.createdAt
            )
        }
    }

    val conversionJobs: Flow<List<ConversionJobUi>> = dao.observeConversionJobs().map { entities ->
        entities.map { entity ->
            ConversionJobUi(
                id = entity.id,
                title = entity.title,
                type = runCatching { ConversionType.valueOf(entity.type) }.getOrDefault(ConversionType.PHOTO_COMPRESS),
                detailLabel = entity.detailLabel,
                progress = entity.progress,
                status = runCatching { JobStatus.valueOf(entity.status) }.getOrDefault(JobStatus.COMPLETED),
                inputUriString = entity.inputUriString,
                outputUriString = entity.outputUriString,
                sizeBeforeBytes = entity.sizeBeforeBytes,
                sizeAfterBytes = entity.sizeAfterBytes,
                errorMessage = entity.errorMessage,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun initializeSeedData() = withContext(Dispatchers.IO) {
        MediaCatalogSeed.cleanupLegacySeedFiles(context)
        dao.deleteSeededPlaylistRefs()
        dao.deleteSeededTracks()
        dao.deleteSeededPlaylists()
    }

    suspend fun scanDeviceMediaStore() = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<TrackRecordEntity>()

        // Scan Audio
        runCatching {
            val audioProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.RELATIVE_PATH
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

                var count = 0
                while (cursor.moveToNext() && count < 150) {
                    val mediaId = cursor.getLong(idCol)
                    val duration = cursor.getLong(durCol)
                    if (duration < 1000L) continue
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val albumId = cursor.getLong(albumIdCol)
                    val artUri = ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val source = when {
                        relPath.contains("ZeroPlay", ignoreCase = true) ||
                            relPath.contains("EchoMusic", ignoreCase = true) -> {
                            if (relPath.contains("Download", ignoreCase = true)) "DOWNLOADED" else "EXPORTED"
                        }
                        relPath.contains("Download", ignoreCase = true) -> "DOWNLOADED"
                        else -> "LOCAL"
                    }
                    discovered += TrackRecordEntity(
                        trackId = "local_audio_$mediaId",
                        title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Audio Track $mediaId",
                        artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Local Audio",
                        album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Device Audio",
                        durationMs = duration,
                        uriString = contentUri.toString(),
                        artworkResId = R.drawable.img_cover_aurora_1794995218098,
                        artworkUriString = artUri,
                        mediaType = "AUDIO",
                        source = source,
                        moodTag = "Feel good",
                        playCount = 0,
                        isLiked = false,
                        dateAdded = cursor.getLong(dateCol) * 1000L,
                        mimeType = mime,
                        sizeBytes = cursor.getLong(sizeCol)
                    )
                    count++
                }
            }
        }

        // Scan Video
        runCatching {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.RELATIVE_PATH
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Video.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val pathCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)

                var count = 0
                while (cursor.moveToNext() && count < 100) {
                    val mediaId = cursor.getLong(idCol)
                    val duration = cursor.getLong(durCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val source = when {
                        relPath.contains("ZeroPlay", ignoreCase = true) ||
                            relPath.contains("EchoMusic", ignoreCase = true) -> "EXPORTED"
                        relPath.contains("Download", ignoreCase = true) -> "DOWNLOADED"
                        else -> "LOCAL"
                    }
                    discovered += TrackRecordEntity(
                        trackId = "local_video_$mediaId",
                        title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) } ?: "Video Clip $mediaId",
                        artist = (if (artistCol >= 0) cursor.getString(artistCol) else null)
                            ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                            ?: "Local Video",
                        album = "Video Library",
                        durationMs = duration.coerceAtLeast(1000L),
                        uriString = contentUri.toString(),
                        artworkResId = R.drawable.img_cover_horizon_1794995219984,
                        artworkUriString = contentUri.toString(),
                        mediaType = "VIDEO",
                        source = source,
                        moodTag = "Energy",
                        playCount = 0,
                        isLiked = false,
                        dateAdded = cursor.getLong(dateCol) * 1000L,
                        mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                        sizeBytes = cursor.getLong(sizeCol)
                    )
                    count++
                }
            }
        }

        // Also scan Files table for MKV / Opus / FLAC / WebM exports in ZeroPlay folders that MediaStore Audio/Video may omit
        runCatching {
            val filesUri = MediaStore.Files.getContentUri("external")
            val fileProjection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
            context.contentResolver.query(
                filesUri,
                fileProjection,
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("%ZeroPlay%"),
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val fileId = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol).orEmpty()
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    if (ext !in setOf("mp4", "mkv", "webm", "mp3", "opus", "m4a", "flac")) continue
                    val isVideo = ext in setOf("mp4", "mkv", "webm")
                    val contentUri = ContentUris.withAppendedId(filesUri, fileId)
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val mime = (if (mimeCol >= 0) cursor.getString(mimeCol) else null) ?: when (ext) {
                        "mkv" -> "video/x-matroska"
                        "webm" -> "video/webm"
                        "opus" -> "audio/opus"
                        "flac" -> "audio/flac"
                        "mp3" -> "audio/mpeg"
                        "m4a" -> "audio/mp4"
                        else -> "video/mp4"
                    }
                    discovered += TrackRecordEntity(
                        trackId = "zeroplay_file_$fileId",
                        title = displayName.substringBeforeLast('.').ifBlank { displayName },
                        artist = if (isVideo) "Zero Play Video" else "Zero Play Audio",
                        album = if (relPath.contains("Download", ignoreCase = true)) "Downloads" else "Zero Play Exported",
                        durationMs = 0L,
                        uriString = contentUri.toString(),
                        artworkResId = if (isVideo) R.drawable.img_cover_horizon_1794995219984 else R.drawable.img_cover_velvet_1794995218473,
                        artworkUriString = if (isVideo) contentUri.toString() else null,
                        mediaType = if (isVideo) "VIDEO" else "AUDIO",
                        source = if (relPath.contains("Download", ignoreCase = true)) "DOWNLOADED" else "EXPORTED",
                        moodTag = "Feel good",
                        playCount = 0,
                        isLiked = false,
                        dateAdded = (if (dateCol >= 0) cursor.getLong(dateCol) else 0L) * 1000L,
                        mimeType = mime,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    )
                }
            }
        }

        if (discovered.isNotEmpty()) {
            dao.insertTracksIfAbsent(discovered)
        }
    }

    suspend fun toggleLike(trackId: String) {
        val existing = dao.getTrackById(trackId) ?: return
        dao.setLiked(trackId, !existing.isLiked)
    }

    suspend fun recordPlay(trackId: String) {
        dao.incrementPlayCount(trackId)
    }

    suspend fun markDownloaded(
        trackId: String,
        title: String,
        artist: String,
        uriString: String,
        mediaType: MediaType,
        mimeType: String,
        sizeBytes: Long = 0L
    ) {
        val existing = dao.getTrackById(trackId)
        if (existing != null) {
            dao.updateTrackSource(trackId, MediaSource.DOWNLOADED.name, uriString)
        } else {
            dao.upsertTrack(
                TrackRecordEntity(
                    trackId = trackId,
                    title = title,
                    artist = artist,
                    album = "Downloads",
                    durationMs = 0L,
                    uriString = uriString,
                    artworkResId = if (mediaType == MediaType.VIDEO) R.drawable.img_cover_horizon_1794995219984 else R.drawable.img_cover_midnight_1794995218900,
                    artworkUriString = if (mediaType == MediaType.VIDEO) uriString else null,
                    mediaType = mediaType.name,
                    source = MediaSource.DOWNLOADED.name,
                    moodTag = "Feel good",
                    playCount = 0,
                    isLiked = false,
                    dateAdded = System.currentTimeMillis(),
                    mimeType = mimeType,
                    sizeBytes = sizeBytes
                )
            )
        }
    }

    suspend fun registerExportedMedia(
        trackId: String,
        title: String,
        artist: String,
        uriString: String,
        mediaType: MediaType,
        mimeType: String,
        sizeBytes: Long
    ) {
        dao.upsertTrack(
            TrackRecordEntity(
                trackId = trackId,
                title = title,
                artist = artist,
                album = "Zero Play Exported",
                durationMs = 0L,
                uriString = uriString,
                artworkResId = if (mediaType == MediaType.VIDEO) R.drawable.img_cover_aurora_1794995218098 else R.drawable.img_cover_velvet_1794995218473,
                artworkUriString = if (mediaType == MediaType.VIDEO) uriString else null,
                mediaType = mediaType.name,
                source = MediaSource.EXPORTED.name,
                moodTag = "Feel good",
                playCount = 0,
                isLiked = false,
                dateAdded = System.currentTimeMillis(),
                mimeType = mimeType,
                sizeBytes = sizeBytes
            )
        )
    }

    suspend fun createPlaylist(name: String, initialTrackId: String? = null): Long {
        val id = dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim().ifEmpty { "New Playlist" },
                coverResId = R.drawable.img_cover_aurora_1794995218098
            )
        )
        if (initialTrackId != null) {
            dao.addTrackToPlaylist(PlaylistTrackCrossRef(id, initialTrackId))
        }
        return id
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun upsertConversionJob(job: ConversionJobUi) {
        dao.upsertConversionJob(
            ConversionJobEntity(
                id = job.id,
                title = job.title,
                type = job.type.name,
                detailLabel = job.detailLabel,
                progress = job.progress,
                status = job.status.name,
                inputUriString = job.inputUriString,
                outputUriString = job.outputUriString,
                sizeBeforeBytes = job.sizeBeforeBytes,
                sizeAfterBytes = job.sizeAfterBytes,
                errorMessage = job.errorMessage,
                createdAt = job.createdAt
            )
        )
    }

    suspend fun clearConversionHistory() {
        dao.clearConversionJobs()
    }

    private fun TrackRecordEntity.toDomain(): MediaTrack {
        val cleanArtist = artist.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            ?: if (mediaType == "VIDEO") "Local Video" else "Local Audio"
        val cleanAlbum = album.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            ?: if (mediaType == "VIDEO") "Video Library" else "Audio Library"
        val defaultCover = if (mediaType == "VIDEO") {
            R.drawable.img_cover_horizon_1794995219984
        } else {
            R.drawable.img_cover_midnight_1794995218900
        }
        val safeArtRes = sanitizeDrawableId(
            resId = MediaCatalogSeed.getArtworkResForTrack(trackId) ?: artworkResId,
            fallback = defaultCover
        )
        return MediaTrack(
            id = trackId,
            title = title,
            artist = cleanArtist,
            album = cleanAlbum,
            durationMs = durationMs,
            uriString = uriString,
            artworkResId = safeArtRes,
            artworkUriString = artworkUriString,
            mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.AUDIO),
            source = runCatching { MediaSource.valueOf(source) }.getOrDefault(MediaSource.CURATED),
            moodTag = moodTag,
            playCount = playCount,
            isLiked = isLiked,
            dateAdded = dateAdded,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            lyrics = MediaCatalogSeed.getLyricsForTrack(trackId)
        )
    }

    companion object {
        private val validCoverDrawables: Set<Int> = setOf(
            R.drawable.img_cover_midnight_1794995218900,
            R.drawable.img_cover_velvet_1794995218473,
            R.drawable.img_cover_aurora_1794995218098,
            R.drawable.img_cover_horizon_1794995219984
        )

        fun sanitizeDrawableId(resId: Int?, fallback: Int): Int {
            return if (resId != null && resId in validCoverDrawables) resId else fallback
        }
    }
}
