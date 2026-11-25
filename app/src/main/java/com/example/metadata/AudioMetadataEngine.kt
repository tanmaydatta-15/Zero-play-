package com.example.metadata

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.converter.FfmpegKitEngine
import com.example.data.model.LyricLine
import com.example.data.model.MediaTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class EditableAudioTags(
    val title: String = "",
    val album: String = "",
    val artist: String = "",
    val albumArtist: String = "",
    val composer: String = "",
    val genre: String = "",
    val lyricist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val year: String = "",
    val comment: String = "",
    val embeddedLyrics: String = ""
)

data class SongDetailedMetadata(
    val fileName: String,
    val storageLocation: String,
    val tags: EditableAudioTags,
    val durationMs: Long,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitsPerSample: Int?,
    val formatLabel: String,
    val encodingLabel: String,
    val channelsLabel: String,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val dateLastPlayedMs: Long,
    val playCount: Int
)

sealed class TagSaveResult {
    data class Success(
        val updatedTags: EditableAudioTags,
        val updatedArtworkUri: String?,
        val writtenToFile: Boolean
    ) : TagSaveResult()

    data class NeedsWritePermission(val intentSender: IntentSender) : TagSaveResult()
    data class Error(val message: String) : TagSaveResult()
}

object AudioMetadataEngine {

    private val LRC_LINE_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    /**
     * Parses embedded lyrics (either synced LRC `[mm:ss.xx]` or plain multiline text) into LyricLines.
     */
    fun parseLyricsText(raw: String, durationMs: Long): List<LyricLine> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val lrcLines = mutableListOf<LyricLine>()
        for (line in lines) {
            val match = LRC_LINE_REGEX.matchEntire(line)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = match.groupValues[3]
                val ms = when (fracRaw.length) {
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    3 -> fracRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lrcLines += LyricLine((min * 60L + sec) * 1000L + ms, text)
                }
            }
        }
        if (lrcLines.isNotEmpty()) {
            return lrcLines.sortedBy { it.timeMs }
        }

        // Plain unsynced lyrics: space evenly across track duration
        val totalDur = durationMs.coerceAtLeast(lines.size * 3000L)
        val step = (totalDur / lines.size.coerceAtLeast(1)).coerceAtLeast(1500L)
        return lines.mapIndexed { idx, text ->
            LyricLine(idx * step, text)
        }
    }

    /**
     * Builds the lossless FFmpegKit argument list to update all 11 metadata tags, embedded lyrics,
     * and embedded front-cover artwork without re-encoding the audio stream (`-c:a copy`).
     */
    internal fun buildTagWriteArgs(
        inputPath: String,
        outputPath: String,
        tags: EditableAudioTags,
        coverImagePath: String?,
        removeArtwork: Boolean,
        targetExtension: String
    ): List<String> {
        val ext = targetExtension.lowercase()
        val supportsAttachedPic = ext in setOf("mp3", "m4a", "mp4", "flac", "mkv")
        val args = mutableListOf("-y", "-i", inputPath)

        val attachCover = !removeArtwork && !coverImagePath.isNullOrBlank() && supportsAttachedPic
        if (attachCover) {
            args += listOf("-i", coverImagePath!!)
            args += listOf("-map", "0:a:0", "-map", "1:v:0", "-c:a", "copy", "-c:v", "mjpeg", "-disposition:v:0", "attached_pic")
        } else if (removeArtwork) {
            args += listOf("-map", "0:a:0", "-vn", "-c:a", "copy")
        } else {
            args += listOf("-map", "0", "-c", "copy")
        }

        if (ext == "mp3") {
            args += listOf("-id3v2_version", "3")
        }

        fun addMeta(key: String, value: String) {
            args += listOf("-metadata", "$key=${value.trim()}")
        }

        addMeta("title", tags.title)
        addMeta("album", tags.album)
        addMeta("artist", tags.artist)
        addMeta("album_artist", tags.albumArtist)
        addMeta("composer", tags.composer)
        addMeta("genre", tags.genre)
        addMeta("lyricist", tags.lyricist)
        addMeta("track", tags.trackNumber)
        addMeta("disc", tags.discNumber)
        addMeta("date", tags.year)
        addMeta("comment", tags.comment)
        if (tags.embeddedLyrics.isNotBlank()) {
            args += listOf("-metadata", "lyrics=${tags.embeddedLyrics}")
        } else {
            args += listOf("-metadata", "lyrics=")
        }

        args += outputPath
        return args
    }

    suspend fun inspectTrack(context: Context, track: MediaTrack): SongDetailedMetadata = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(track.uriString) }.getOrNull()
        var fileName = "${track.title}.${guessExtension(track)}"
        var storageLocation = track.folderPath.ifBlank { "Internal Storage" }
        var sizeBytes = track.sizeBytes
        var dateModifiedMs = track.dateModified
        var localProbeFile: File? = null
        var tempCopyForProbe: File? = null

        if (uri != null) {
            when (uri.scheme) {
                "file" -> {
                    val f = File(uri.path.orEmpty())
                    if (f.exists()) {
                        fileName = f.name
                        storageLocation = f.parent ?: storageLocation
                        sizeBytes = f.length()
                        if (dateModifiedMs <= 0L) dateModifiedMs = f.lastModified()
                        localProbeFile = f
                    }
                }
                "content" -> {
                    runCatching {
                        val cols = mutableListOf(
                            OpenableColumns.DISPLAY_NAME,
                            OpenableColumns.SIZE,
                            MediaStore.MediaColumns.DATE_MODIFIED,
                            MediaStore.MediaColumns.RELATIVE_PATH
                        )
                        context.contentResolver.query(uri, cols.toTypedArray(), null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIdx >= 0) c.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { fileName = it }
                                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIdx >= 0 && c.getLong(sizeIdx) > 0L) sizeBytes = c.getLong(sizeIdx)
                                val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                                if (modIdx >= 0 && c.getLong(modIdx) > 0L && dateModifiedMs <= 0L) {
                                    dateModifiedMs = c.getLong(modIdx) * 1000L
                                }
                                val relIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                                if (relIdx >= 0) {
                                    c.getString(relIdx)?.takeIf { it.isNotBlank() }?.let {
                                        storageLocation = "/storage/emulated/0/${it.trimEnd('/')}"
                                    }
                                }
                            }
                        }
                    }

                    // Copy to tiny temp file for FFprobe when audio file is reasonable size
                    if (sizeBytes in 1L..(80L * 1024L * 1024L)) {
                        runCatching {
                            val ext = fileName.substringAfterLast('.', guessExtension(track))
                            val tmpDir = File(context.cacheDir, "tag_probe").apply { mkdirs() }
                            val tmp = File(tmpDir, "probe_${track.id.hashCode()}.$ext")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(tmp).use { out -> input.copyTo(out) }
                            }
                            if (tmp.exists() && tmp.length() > 0L) {
                                localProbeFile = tmp
                                tempCopyForProbe = tmp
                            }
                        }
                    }
                }
            }
        }

        // Gather FFprobe metadata + technical audio specs
        var durationMs = track.durationMs
        var bitrateKbps: Int? = null
        var sampleRateHz: Int? = null
        var bitsPerSample: Int? = null
        var formatLabel = fileName.substringAfterLast('.', guessExtension(track)).uppercase()
        var encodingLabel = track.mimeType.substringAfter('/').uppercase()
        var channelsLabel = "Stereo (2 ch)"

        val tagMap = mutableMapOf<String, String>()
        localProbeFile?.let { file ->
            runCatching {
                val info = FFprobeKit.getMediaInformation(file.absolutePath).mediaInformation
                if (info != null) {
                    val durSec = info.duration?.toDoubleOrNull() ?: 0.0
                    if (durSec > 0.0) durationMs = (durSec * 1000.0).toLong()
                    info.bitrate?.toLongOrNull()?.let { bps ->
                        if (bps > 0L) bitrateKbps = (bps / 1000L).toInt()
                    }
                    info.format?.takeIf { it.isNotBlank() }?.let {
                        formatLabel = it.substringBefore(',').uppercase()
                    }
                    info.tags?.let { jsonTags ->
                        val keys = jsonTags.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = jsonTags.optString(k, "").trim()
                            if (v.isNotEmpty()) tagMap[k.lowercase()] = v
                        }
                    }
                    val audioStream = info.streams?.firstOrNull { it.type.equals("audio", ignoreCase = true) }
                    if (audioStream != null) {
                        audioStream.codec?.takeIf { it.isNotBlank() }?.let {
                            encodingLabel = FfmpegKitEngine.normalizeCodecBadge(it)
                        }
                        audioStream.sampleRate?.toIntOrNull()?.let { sampleRateHz = it }
                        audioStream.bitrate?.toLongOrNull()?.let { bps ->
                            if (bps > 0L) bitrateKbps = (bps / 1000L).toInt()
                        }
                        val props = audioStream.allProperties
                        val ch = props?.optInt("channels", 0) ?: 0
                        if (ch > 0) {
                            channelsLabel = when (ch) {
                                1 -> "Mono (1 ch)"
                                2 -> "Stereo (2 ch)"
                                6 -> "5.1 Surround (6 ch)"
                                else -> "$ch channels"
                            }
                        }
                        val bpsSample = props?.optInt("bits_per_raw_sample", 0)?.takeIf { it > 0 }
                            ?: props?.optInt("bits_per_sample", 0)?.takeIf { it > 0 }
                        if (bpsSample != null) {
                            bitsPerSample = bpsSample
                        } else {
                            val sampleFmt = audioStream.sampleFormat.orEmpty().lowercase()
                            bitsPerSample = when {
                                sampleFmt.contains("32") || sampleFmt.contains("flt") -> 32
                                sampleFmt.contains("24") -> 24
                                sampleFmt.contains("16") -> 16
                                sampleFmt.contains("u8") -> 8
                                else -> null
                            }
                        }
                        audioStream.tags?.let { streamTags ->
                            val keys = streamTags.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val v = streamTags.optString(k, "").trim()
                                if (v.isNotEmpty() && !tagMap.containsKey(k.lowercase())) {
                                    tagMap[k.lowercase()] = v
                                }
                            }
                        }
                    }
                }
            }
        }
        tempCopyForProbe?.delete()

        // Fallback to MediaMetadataRetriever for any missing audio specs or tags
        if (uri != null && (bitrateKbps == null || sampleRateHz == null || tagMap.isEmpty())) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                if (durationMs <= 0L) {
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                        durationMs = it
                    }
                }
                if (bitrateKbps == null) {
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                        if (it > 0L) bitrateKbps = (it / 1000L).toInt()
                    }
                }
                if (sampleRateHz == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let {
                        sampleRateHz = it
                    }
                }
                if (bitsPerSample == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let {
                        bitsPerSample = it
                    }
                }
                fun fallbackTag(key: String, mmrKey: Int) {
                    if (!tagMap.containsKey(key)) {
                        mmr.extractMetadata(mmrKey)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            tagMap[key] = it
                        }
                    }
                }
                fallbackTag("title", MediaMetadataRetriever.METADATA_KEY_TITLE)
                fallbackTag("album", MediaMetadataRetriever.METADATA_KEY_ALBUM)
                fallbackTag("artist", MediaMetadataRetriever.METADATA_KEY_ARTIST)
                fallbackTag("album_artist", MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                fallbackTag("composer", MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                fallbackTag("genre", MediaMetadataRetriever.METADATA_KEY_GENRE)
                fallbackTag("lyricist", MediaMetadataRetriever.METADATA_KEY_WRITER)
                fallbackTag("track", MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                fallbackTag("disc", MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                fallbackTag("date", MediaMetadataRetriever.METADATA_KEY_YEAR)
            } catch (_: Throwable) {
            } finally {
                runCatching { mmr.release() }
            }
        }

        // Compute bitrate estimate from size & duration if still unavailable
        if (bitrateKbps == null && sizeBytes > 0L && durationMs > 1000L) {
            bitrateKbps = ((sizeBytes * 8L) / durationMs).toInt().coerceAtLeast(32)
        }

        val mergedTags = EditableAudioTags(
            title = track.title.ifBlank { tagMap["title"].orEmpty() },
            album = track.album.ifBlank { tagMap["album"].orEmpty() },
            artist = track.artist.ifBlank { tagMap["artist"].orEmpty() },
            albumArtist = track.albumArtist.ifBlank { tagMap["album_artist"] ?: tagMap["albumartist"].orEmpty() },
            composer = track.composer.ifBlank { tagMap["composer"].orEmpty() },
            genre = track.genre.ifBlank { tagMap["genre"].orEmpty() },
            lyricist = track.lyricist.ifBlank { tagMap["lyricist"] ?: tagMap["writer"].orEmpty() },
            trackNumber = track.trackNumber.ifBlank { tagMap["track"].orEmpty() },
            discNumber = track.discNumber.ifBlank { tagMap["disc"] ?: tagMap["tpos"].orEmpty() },
            year = track.year.ifBlank { (tagMap["date"] ?: tagMap["year"].orEmpty()).take(4) },
            comment = track.comment.ifBlank { tagMap["comment"] ?: tagMap["description"].orEmpty() },
            embeddedLyrics = track.embeddedLyrics.ifBlank {
                tagMap["lyrics"]
                    ?: tagMap["lyrics-eng"]
                    ?: tagMap["uslt"]
                    ?: tagMap["unsyncedlyrics"]
                    ?: track.lyrics.joinToString("\n") { it.text }
            }
        )

        SongDetailedMetadata(
            fileName = fileName,
            storageLocation = storageLocation,
            tags = mergedTags,
            durationMs = durationMs,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            bitsPerSample = bitsPerSample,
            formatLabel = formatLabel,
            encodingLabel = encodingLabel,
            channelsLabel = channelsLabel,
            sizeBytes = sizeBytes,
            dateAddedMs = track.dateAdded,
            dateModifiedMs = if (dateModifiedMs > 0L) dateModifiedMs else track.dateAdded,
            dateLastPlayedMs = track.lastPlayedAt,
            playCount = track.playCount
        )
    }

    /**
     * Writes updated tags, embedded lyrics, and embedded cover art back into the audio file
     * using FFmpegKit lossless stream copy (`-c:a copy`), with Scoped Storage write permission
     * handling (`MediaStore.createWriteRequest` / `RecoverableSecurityException`).
     */
    suspend fun saveTags(
        context: Context,
        track: MediaTrack,
        tags: EditableAudioTags,
        newArtworkUri: Uri?,
        removeArtwork: Boolean
    ): TagSaveResult = withContext(Dispatchers.IO) {
        val trackUri = runCatching { Uri.parse(track.uriString) }.getOrNull()
        val ext = guessExtension(track)
        val workDir = File(context.cacheDir, "tag_editor").apply { mkdirs() }
        val srcFile = File(workDir, "src_${System.currentTimeMillis()}.$ext")
        val outFile = File(workDir, "out_${System.currentTimeMillis()}.$ext")
        var coverTempFile: File? = null

        // Persist custom artwork locally in app filesDir so library & player update instantaneously
        val artworkDir = File(context.filesDir, "track_artwork").apply { mkdirs() }
        val safeTrackKey = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val persistedArtFile = File(artworkDir, "$safeTrackKey.jpg")
        var resolvedArtworkUri: String? = track.artworkUriString

        try {
            if (removeArtwork) {
                if (persistedArtFile.exists()) persistedArtFile.delete()
                resolvedArtworkUri = null
            } else if (newArtworkUri != null) {
                coverTempFile = File(workDir, "cover_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(newArtworkUri)?.use { input ->
                    FileOutputStream(coverTempFile).use { out -> input.copyTo(out) }
                }
                if (coverTempFile.exists() && coverTempFile.length() > 0L) {
                    coverTempFile.copyTo(persistedArtFile, overwrite = true)
                    resolvedArtworkUri = Uri.fromFile(persistedArtFile).toString()
                }
            }

            // Copy source audio file to scratch workspace
            val copied = when (trackUri?.scheme) {
                "file" -> {
                    val f = File(trackUri.path.orEmpty())
                    if (f.exists() && f.canRead()) {
                        f.copyTo(srcFile, overwrite = true)
                        true
                    } else false
                }
                "content" -> {
                    runCatching {
                        context.contentResolver.openInputStream(trackUri)?.use { input ->
                            FileOutputStream(srcFile).use { out -> input.copyTo(out) }
                        }
                        srcFile.exists() && srcFile.length() > 0L
                    }.getOrDefault(false)
                }
                else -> false
            }

            if (!copied) {
                // Still succeed updating Room + local artwork even for non-writable streams
                return@withContext TagSaveResult.Success(
                    updatedTags = tags,
                    updatedArtworkUri = resolvedArtworkUri,
                    writtenToFile = false
                )
            }

            val args = buildTagWriteArgs(
                inputPath = srcFile.absolutePath,
                outputPath = outFile.absolutePath,
                tags = tags,
                coverImagePath = coverTempFile?.absolutePath,
                removeArtwork = removeArtwork,
                targetExtension = ext
            )
            val cmd = args.joinToString(" ") { FfmpegKitEngine.quoteArg(it) }
            val session = FFmpegKit.execute(cmd)
            if (!ReturnCode.isSuccess(session.returnCode) || !outFile.exists() || outFile.length() == 0L) {
                return@withContext TagSaveResult.Error(
                    "FFmpeg could not write tags to .$ext file."
                )
            }

            // Write patched audio file back to storage
            var wroteBack = false
            when (trackUri?.scheme) {
                "file" -> {
                    val target = File(trackUri.path.orEmpty())
                    outFile.copyTo(target, overwrite = true)
                    wroteBack = true
                }
                "content" -> {
                    try {
                        context.contentResolver.openOutputStream(trackUri, "wt")?.use { out ->
                            outFile.inputStream().use { input -> input.copyTo(out) }
                        } ?: error("Output stream unavailable")
                        // Also sync basic MediaStore index columns so Android reflects updates immediately
                        runCatching {
                            val cv = ContentValues().apply {
                                put(MediaStore.Audio.Media.TITLE, tags.title.trim())
                                if (tags.artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, tags.artist.trim())
                                if (tags.album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, tags.album.trim())
                            }
                            context.contentResolver.update(trackUri, cv, null, null)
                        }
                        wroteBack = true
                    } catch (sec: SecurityException) {
                        val intentSender = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                runCatching {
                                    MediaStore.createWriteRequest(context.contentResolver, listOf(trackUri)).intentSender
                                }.getOrNull()
                            }
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException -> {
                                sec.userAction.actionIntent.intentSender
                            }
                            else -> null
                        }
                        if (intentSender != null) {
                            return@withContext TagSaveResult.NeedsWritePermission(intentSender)
                        }
                        // If OS cannot grant write access, keep Room DB update graceful
                        wroteBack = false
                    }
                }
            }

            TagSaveResult.Success(
                updatedTags = tags,
                updatedArtworkUri = resolvedArtworkUri,
                writtenToFile = wroteBack
            )
        } catch (t: Throwable) {
            TagSaveResult.Error(t.message ?: "Failed to save audio tags")
        } finally {
            srcFile.delete()
            outFile.delete()
            coverTempFile?.delete()
        }
    }

    private fun guessExtension(track: MediaTrack): String {
        val cleanUri = track.uriString.substringBefore('?').lowercase()
        val fromUri = cleanUri.substringAfterLast('.', "")
        if (fromUri in setOf("mp3", "m4a", "flac", "opus", "ogg", "wav", "aac", "mkv", "mp4", "webm")) {
            return fromUri
        }
        val mime = track.mimeType.lowercase()
        return when {
            mime.contains("mpeg") -> "mp3"
            mime.contains("flac") -> "flac"
            mime.contains("opus") -> "opus"
            mime.contains("ogg") -> "ogg"
            mime.contains("wav") -> "wav"
            mime.contains("matroska") -> "mkv"
            mime.contains("mp4") && track.mediaType.name == "AUDIO" -> "m4a"
            mime.contains("mp4") -> "mp4"
            else -> "mp3"
        }
    }
}
