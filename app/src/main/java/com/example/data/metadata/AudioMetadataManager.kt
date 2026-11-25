package com.example.data.metadata

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.data.model.MediaTrack
import com.example.data.repository.EchoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

data class AudioTrackMetadata(
    val trackId: String,
    val fileName: String,
    val storageLocation: String,
    val title: String,
    val album: String,
    val artist: String,
    val albumArtist: String,
    val composer: String,
    val genre: String,
    val lyricist: String,
    val trackNumber: String,
    val discNumber: String,
    val year: String,
    val comment: String,
    val embeddedLyrics: String,
    val durationMs: Long,
    val bitrateBps: Long,
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val formatName: String,
    val encodingName: String,
    val channels: Int,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val dateLastPlayedMs: Long,
    val playCount: Int,
    val artworkUriString: String?
)

sealed interface CoverArtEditAction {
    data object Keep : CoverArtEditAction
    data class Replace(val imageUri: Uri) : CoverArtEditAction
    data object Remove : CoverArtEditAction
}

data class TagEditRequest(
    val title: String,
    val album: String,
    val artist: String,
    val albumArtist: String,
    val composer: String,
    val genre: String,
    val lyricist: String,
    val trackNumber: String,
    val discNumber: String,
    val year: String,
    val comment: String,
    val embeddedLyrics: String,
    val coverArtAction: CoverArtEditAction = CoverArtEditAction.Keep
)

sealed interface TagWriteResult {
    data class Success(
        val updatedTrack: MediaTrack,
        val wroteToFile: Boolean,
        val note: String
    ) : TagWriteResult

    data class NeedsWritePermission(
        val intentSender: IntentSender
    ) : TagWriteResult

    data class Error(
        val message: String
    ) : TagWriteResult
}

class AudioMetadataManager(
    private val context: Context,
    private val repository: EchoRepository
) {
    private val trackCoverDir: File by lazy {
        File(context.filesDir, "track_covers").apply { mkdirs() }
    }

    suspend fun inspectTrack(track: MediaTrack): AudioTrackMetadata = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(track.uriString) }.getOrNull()

        var fileName = "${track.title}.${inferExtension(track)}"
        var storageLocation = track.relativePath.ifBlank {
            when (uri?.scheme) {
                "file" -> File(uri.path.orEmpty()).parent ?: "Internal Storage"
                "content" -> "MediaStore Library"
                else -> "Stream / App Storage"
            }
        }
        var sizeBytes = track.sizeBytes
        var dateModifiedMs = track.dateModified
        var dateAddedMs = track.dateAdded

        // 1. Query ContentResolver for display name, size, relative path, modified timestamp
        if (uri != null && uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { idx ->
                            cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { fileName = it }
                        }
                        cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { idx ->
                            val sz = cursor.getLong(idx)
                            if (sz > 0L) sizeBytes = sz
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH).takeIf { it >= 0 }?.let { idx ->
                                cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { rel ->
                                    storageLocation = "/storage/emulated/0/${rel.trimEnd('/')}"
                                }
                            }
                        }
                        cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED).takeIf { it >= 0 }?.let { idx ->
                            val modSec = cursor.getLong(idx)
                            if (modSec > 0L) dateModifiedMs = modSec * 1000L
                        }
                        cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED).takeIf { it >= 0 }?.let { idx ->
                            val addSec = cursor.getLong(idx)
                            if (addSec > 0L && dateAddedMs <= 0L) dateAddedMs = addSec * 1000L
                        }
                    }
                }
            }
        } else if (uri != null && uri.scheme == "file") {
            val file = File(uri.path.orEmpty())
            if (file.exists()) {
                fileName = file.name
                storageLocation = file.parent ?: storageLocation
                if (file.length() > 0L) sizeBytes = file.length()
                if (file.lastModified() > 0L && dateModifiedMs <= 0L) dateModifiedMs = file.lastModified()
            }
        }

        // 2. Inspect via MediaMetadataRetriever
        var mmrTitle = ""
        var mmrAlbum = ""
        var mmrArtist = ""
        var mmrAlbumArtist = ""
        var mmrComposer = ""
        var mmrGenre = ""
        var mmrTrackNum = ""
        var mmrDiscNum = ""
        var mmrYear = ""
        var mmrDurationMs = track.durationMs
        var mmrBitrateBps = 0L
        var mmrSampleRateHz = 0
        var mmrBitsPerSample = 0

        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                mmrTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
                mmrAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
                mmrArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
                mmrAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty()
                mmrComposer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER).orEmpty()
                mmrGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty()
                mmrTrackNum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).orEmpty()
                mmrDiscNum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).orEmpty()
                mmrYear = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR).orEmpty()
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    if (it > 0L) mmrDurationMs = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                    if (it > 0L) mmrBitrateBps = it
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let {
                        if (it > 0) mmrSampleRateHz = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let {
                        if (it > 0) mmrBitsPerSample = it
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { retriever.release() }
            }
        }

        // 3. Probe full tags & audio stream specs via FFprobeKit
        var probeFormatName = ""
        var probeEncoding = ""
        var probeChannels = 0
        var probeSampleRate = 0
        var probeBitsPerSample = 0
        var probeBitrate = 0L
        val probeTags = mutableMapOf<String, String>()

        val tempProbeFile = if (uri != null && uri.scheme == "file") {
            File(uri.path.orEmpty()).takeIf { it.exists() }
        } else if (uri != null && uri.scheme == "content") {
            copyUriToTempFile(uri, "probe_${track.id.hashCode()}.${inferExtension(track, fileName)}")
        } else {
            null
        }
        val shouldDeleteTemp = tempProbeFile != null && uri?.scheme != "file"

        if (tempProbeFile != null && tempProbeFile.exists()) {
            runCatching {
                val info = FFprobeKit.getMediaInformation(tempProbeFile.absolutePath).mediaInformation
                if (info != null) {
                    probeFormatName = info.format.orEmpty()
                    info.bitrate?.toLongOrNull()?.let { if (it > 0L) probeBitrate = it }
                    collectTagsFromJson(info.tags, probeTags)

                    val audioStream = info.streams?.firstOrNull { it.type.equals("audio", ignoreCase = true) }
                    if (audioStream != null) {
                        probeEncoding = audioStream.codec.orEmpty()
                        audioStream.sampleRate?.toIntOrNull()?.let { if (it > 0) probeSampleRate = it }
                        audioStream.bitrate?.toLongOrNull()?.let { if (it > 0L) probeBitrate = it }
                        val props = audioStream.allProperties
                        if (props != null) {
                            val ch = props.optInt("channels", 0)
                            if (ch > 0) probeChannels = ch
                            val bps = props.optInt("bits_per_raw_sample", 0)
                                .takeIf { it > 0 }
                                ?: props.optInt("bits_per_sample", 0)
                            if (bps > 0) probeBitsPerSample = bps
                            collectTagsFromJson(props.optJSONObject("tags"), probeTags)
                        }
                    }
                }
            }
            if (shouldDeleteTemp) {
                runCatching { tempProbeFile.delete() }
            }
        }

        fun pickTag(storedValue: String, probeKeys: List<String>, fallback: String = ""): String {
            if (storedValue.isNotBlank()) return storedValue
            for (k in probeKeys) {
                val found = probeTags[k.lowercase(Locale.US)]
                if (!found.isNullOrBlank()) return found
            }
            return fallback
        }

        val resolvedFormat = formatDisplayName(probeFormatName, fileName, track.mimeType)
        val resolvedEncoding = encodingDisplayName(probeEncoding, track.mimeType, fileName)
        val resolvedBitrate = when {
            probeBitrate > 0L -> probeBitrate
            mmrBitrateBps > 0L -> mmrBitrateBps
            sizeBytes > 0L && mmrDurationMs > 0L -> (sizeBytes * 8L * 1000L) / mmrDurationMs
            else -> 0L
        }
        val resolvedSampleRate = if (probeSampleRate > 0) probeSampleRate else mmrSampleRateHz
        val resolvedBitsPerSample = if (probeBitsPerSample > 0) probeBitsPerSample else mmrBitsPerSample

        AudioTrackMetadata(
            trackId = track.id,
            fileName = fileName,
            storageLocation = storageLocation,
            title = pickTag(track.title, listOf("title"), mmrTitle),
            album = pickTag(track.album, listOf("album"), mmrAlbum),
            artist = pickTag(track.artist, listOf("artist"), mmrArtist),
            albumArtist = pickTag(track.albumArtist, listOf("album_artist", "albumartist", "aart"), mmrAlbumArtist),
            composer = pickTag(track.composer, listOf("composer", "tcom"), mmrComposer),
            genre = pickTag(track.genre, listOf("genre", "tcon"), mmrGenre),
            lyricist = pickTag(track.lyricist, listOf("lyricist", "text", "writer"), ""),
            trackNumber = pickTag(track.trackNumber, listOf("track", "tracknumber"), mmrTrackNum),
            discNumber = pickTag(track.discNumber, listOf("disc", "discnumber", "tpos"), mmrDiscNum),
            year = pickTag(track.year, listOf("date", "year", "tyer", "tdrc"), mmrYear),
            comment = pickTag(track.comment, listOf("comment", "description"), ""),
            embeddedLyrics = pickTag(
                track.embeddedLyrics,
                listOf("lyrics", "lyrics-eng", "lyrics-xxx", "uslt", "unsyncedlyrics"),
                ""
            ),
            durationMs = mmrDurationMs,
            bitrateBps = resolvedBitrate,
            sampleRateHz = resolvedSampleRate,
            bitsPerSample = resolvedBitsPerSample,
            formatName = resolvedFormat,
            encodingName = resolvedEncoding,
            channels = probeChannels.takeIf { it > 0 } ?: 2,
            sizeBytes = sizeBytes,
            dateAddedMs = dateAddedMs,
            dateModifiedMs = dateModifiedMs.takeIf { it > 0L } ?: dateAddedMs,
            dateLastPlayedMs = track.lastPlayedAt,
            playCount = track.playCount,
            artworkUriString = track.artworkUriString
        )
    }

    suspend fun saveTrackTags(
        track: MediaTrack,
        request: TagEditRequest
    ): TagWriteResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(track.uriString) }.getOrNull()
        val ext = inferExtension(track)
        val stageDir = File(context.cacheDir, "tag_editor").apply { mkdirs() }
        val inputTemp = File(stageDir, "in_${System.currentTimeMillis()}.$ext")
        val outputTemp = File(stageDir, "out_${System.currentTimeMillis()}.$ext")
        var coverTemp: File? = null
        var newArtworkUriString: String? = track.artworkUriString
        var wroteToFile = false
        var finalSizeBytes = track.sizeBytes

        try {
            // Prepare cover art replacement image if requested
            when (val artAction = request.coverArtAction) {
                is CoverArtEditAction.Keep -> Unit
                is CoverArtEditAction.Remove -> {
                    newArtworkUriString = null
                }
                is CoverArtEditAction.Replace -> {
                    val persistedCover = saveCoverArtLocally(track.id, artAction.imageUri)
                    if (persistedCover != null) {
                        coverTemp = persistedCover
                        newArtworkUriString = Uri.fromFile(persistedCover).toString()
                    }
                }
            }

            // Copy source file to temp staging area for FFmpegKit metadata remux
            val copiedSource = uri != null && copySourceUriToFile(uri, inputTemp)
            if (copiedSource && inputTemp.exists() && inputTemp.length() > 0L) {
                val primaryArgs = buildTagWriteArgs(
                    inputFile = inputTemp,
                    outputFile = outputTemp,
                    request = request,
                    coverImageFile = coverTemp,
                    containerExt = ext
                )
                var ffmpegOk = runFfmpegSync(primaryArgs, outputTemp)

                // If cover-art stream mapping isn't supported by container (e.g. opus/wav), retry metadata-only remux
                if (!ffmpegOk && request.coverArtAction is CoverArtEditAction.Replace) {
                    runCatching { outputTemp.delete() }
                    val fallbackArgs = buildTagWriteArgs(
                        inputFile = inputTemp,
                        outputFile = outputTemp,
                        request = request.copy(coverArtAction = CoverArtEditAction.Keep),
                        coverImageFile = null,
                        containerExt = ext
                    )
                    ffmpegOk = runFfmpegSync(fallbackArgs, outputTemp)
                }

                if (ffmpegOk && outputTemp.exists() && outputTemp.length() > 0L) {
                    finalSizeBytes = outputTemp.length()
                    val writeAttempt = writeFileBackToUri(uri!!, outputTemp)
                    when (writeAttempt) {
                        is WriteBackOutcome.Success -> {
                            wroteToFile = true
                            updateMediaStoreDisplayFields(uri, request)
                        }
                        is WriteBackOutcome.NeedsPermission -> {
                            return@withContext TagWriteResult.NeedsWritePermission(writeAttempt.intentSender)
                        }
                        is WriteBackOutcome.Unsupported -> {
                            wroteToFile = false
                        }
                    }
                }
            }

            // Always persist updated metadata to Room so UI, Lyrics, and Library refresh immediately
            val updatedTrack = repository.updateTrackMetadata(
                trackId = track.id,
                title = request.title.trim().ifEmpty { track.title },
                album = request.album.trim().ifEmpty { track.album },
                artist = request.artist.trim().ifEmpty { track.artist },
                albumArtist = request.albumArtist.trim(),
                composer = request.composer.trim(),
                genre = request.genre.trim(),
                lyricist = request.lyricist.trim(),
                trackNumber = request.trackNumber.trim(),
                discNumber = request.discNumber.trim(),
                year = request.year.trim(),
                comment = request.comment.trim(),
                embeddedLyrics = request.embeddedLyrics.trim(),
                artworkUriString = newArtworkUriString,
                sizeBytes = finalSizeBytes
            ) ?: return@withContext TagWriteResult.Error("Track record not found in library")

            val note = if (wroteToFile) {
                "Tags written to audio file and library refreshed"
            } else {
                "Tags saved to Zero Play library"
            }
            TagWriteResult.Success(
                updatedTrack = updatedTrack,
                wroteToFile = wroteToFile,
                note = note
            )
        } catch (t: Throwable) {
            TagWriteResult.Error(t.message ?: "Failed to save audio tags")
        } finally {
            runCatching { inputTemp.delete() }
            runCatching { outputTemp.delete() }
        }
    }

    private sealed interface WriteBackOutcome {
        data object Success : WriteBackOutcome
        data class NeedsPermission(val intentSender: IntentSender) : WriteBackOutcome
        data object Unsupported : WriteBackOutcome
    }

    private fun writeFileBackToUri(targetUri: Uri, sourceFile: File): WriteBackOutcome {
        return when (targetUri.scheme) {
            "file" -> {
                val dest = File(targetUri.path.orEmpty())
                if (!dest.exists() || !dest.canWrite()) return WriteBackOutcome.Unsupported
                runCatching {
                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(dest, false).use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                    MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                    WriteBackOutcome.Success
                }.getOrDefault(WriteBackOutcome.Unsupported)
            }
            "content" -> {
                try {
                    val os = context.contentResolver.openOutputStream(targetUri, "wt")
                        ?: context.contentResolver.openOutputStream(targetUri, "w")
                        ?: return WriteBackOutcome.Unsupported
                    os.use { output ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                    WriteBackOutcome.Success
                } catch (security: SecurityException) {
                    val intentSender = extractWritePermissionSender(targetUri, security)
                    if (intentSender != null) {
                        WriteBackOutcome.NeedsPermission(intentSender)
                    } else {
                        WriteBackOutcome.Unsupported
                    }
                } catch (_: Throwable) {
                    WriteBackOutcome.Unsupported
                }
            }
            else -> WriteBackOutcome.Unsupported
        }
    }

    private fun extractWritePermissionSender(uri: Uri, security: SecurityException): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaWriteSender = runCatching {
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
            }.getOrNull()
            if (mediaWriteSender != null) return mediaWriteSender
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && security is RecoverableSecurityException) {
            return security.userAction.actionIntent.intentSender
        }
        return null
    }

    private fun updateMediaStoreDisplayFields(uri: Uri, request: TagEditRequest) {
        if (uri.scheme != "content") return
        runCatching {
            val cv = ContentValues().apply {
                if (request.title.isNotBlank()) put(MediaStore.Audio.Media.TITLE, request.title.trim())
                if (request.artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, request.artist.trim())
                if (request.album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, request.album.trim())
                request.year.trim().toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
                if (request.composer.isNotBlank()) put(MediaStore.Audio.Media.COMPOSER, request.composer.trim())
            }
            if (cv.size() > 0) {
                context.contentResolver.update(uri, cv, null, null)
            }
        }
    }

    private fun saveCoverArtLocally(trackId: String, imageUri: Uri): File? {
        val raw = runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null

        val maxDim = max(raw.width, raw.height).coerceAtLeast(1)
        val scaled = if (maxDim > 1000) {
            val ratio = 1000f / maxDim.toFloat()
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * ratio).toInt().coerceAtLeast(1),
                (raw.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            raw
        }

        val safeId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val destFile = File(trackCoverDir, "${safeId}_${System.currentTimeMillis()}.jpg")
        val ok = runCatching {
            FileOutputStream(destFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
            true
        }.getOrDefault(false)

        if (scaled !== raw) scaled.recycle()
        raw.recycle()
        return if (ok && destFile.exists()) destFile else null
    }

    private fun copyUriToTempFile(uri: Uri, filename: String): File? {
        val dir = File(context.cacheDir, "tag_probe").apply { mkdirs() }
        val file = File(dir, filename)
        return if (copySourceUriToFile(uri, file)) file else null
    }

    private fun copySourceUriToFile(uri: Uri, dest: File): Boolean {
        return runCatching {
            when (uri.scheme) {
                "file" -> {
                    val src = File(uri.path.orEmpty())
                    if (!src.exists()) return false
                    src.copyTo(dest, overwrite = true)
                    true
                }
                "content" -> {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return false
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun runFfmpegSync(args: List<String>, outputFile: File): Boolean {
        return runCatching {
            val session = FFmpegKit.executeWithArguments(args.toTypedArray())
            ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 128L
        }.getOrDefault(false)
    }

    private fun collectTagsFromJson(json: JSONObject?, out: MutableMap<String, String>) {
        if (json == null) return
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key, "").trim()
            if (value.isNotBlank()) {
                out.putIfAbsent(key.lowercase(Locale.US), value)
            }
        }
    }

    companion object {
        private val ATTACHED_PIC_SUPPORTED_EXTS = setOf("mp3", "m4a", "mp4", "flac", "mkv")

        internal fun inferExtension(track: MediaTrack, fileNameHint: String? = null): String {
            val hintExt = fileNameHint?.substringAfterLast('.', "")?.lowercase(Locale.US)
            if (!hintExt.isNullOrBlank() && hintExt.length in 2..5) return hintExt

            val uriExt = track.uriString.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.US)
            if (uriExt in setOf("mp3", "m4a", "flac", "opus", "ogg", "wav", "mp4", "mkv", "webm")) {
                return uriExt
            }
            val mime = track.mimeType.lowercase(Locale.US)
            return when {
                mime.contains("flac") -> "flac"
                mime.contains("opus") -> "opus"
                mime.contains("ogg") -> "ogg"
                mime.contains("wav") -> "wav"
                mime.contains("matroska") -> "mkv"
                mime.contains("webm") -> "webm"
                mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
                else -> "mp3"
            }
        }

        internal fun formatDisplayName(probeFormat: String, fileName: String, mimeType: String): String {
            val ext = fileName.substringAfterLast('.', "").uppercase(Locale.US)
            if (ext in setOf("MP3", "FLAC", "M4A", "OPUS", "OGG", "WAV", "MKV", "MP4", "WEBM")) {
                return ext
            }
            val lower = probeFormat.lowercase(Locale.US)
            return when {
                lower.contains("mp3") -> "MP3"
                lower.contains("flac") -> "FLAC"
                lower.contains("matroska") -> "MKV"
                lower.contains("ogg") -> "OGG"
                lower.contains("wav") -> "WAV"
                lower.contains("mp4") || lower.contains("m4a") -> "M4A / MP4"
                else -> mimeType.substringAfter('/').uppercase(Locale.US).ifBlank { "AUDIO" }
            }
        }

        internal fun encodingDisplayName(probeCodec: String, mimeType: String, fileName: String): String {
            val c = probeCodec.lowercase(Locale.US)
            return when {
                c.contains("mp3") -> "MPEG-1 Layer 3 (MP3)"
                c.contains("aac") -> "AAC-LC"
                c.contains("opus") -> "Opus"
                c.contains("flac") -> "FLAC (Lossless)"
                c.contains("vorbis") -> "Vorbis"
                c.startsWith("pcm_") -> "PCM (${c.uppercase(Locale.US)})"
                c.isNotBlank() -> c.uppercase(Locale.US)
                else -> formatDisplayName("", fileName, mimeType)
            }
        }

        internal fun buildTagWriteArgs(
            inputFile: File,
            outputFile: File,
            request: TagEditRequest,
            coverImageFile: File?,
            containerExt: String
        ): List<String> {
            val ext = containerExt.lowercase(Locale.US)
            val supportsAttachedPic = ext in ATTACHED_PIC_SUPPORTED_EXTS
            val args = mutableListOf("-y", "-i", inputFile.absolutePath)

            val shouldEmbedNewCover = request.coverArtAction is CoverArtEditAction.Replace &&
                coverImageFile != null &&
                supportsAttachedPic

            if (shouldEmbedNewCover) {
                args += listOf("-i", coverImageFile!!.absolutePath)
                args += listOf(
                    "-map", "0:a:0",
                    "-map", "1:v:0",
                    "-c:a", "copy",
                    "-c:v", "mjpeg",
                    "-disposition:v:0", "attached_pic"
                )
            } else if (request.coverArtAction is CoverArtEditAction.Remove) {
                args += listOf("-map", "0:a:0", "-c:a", "copy")
            } else {
                args += listOf("-map", "0", "-c", "copy")
            }

            if (ext == "mp3") {
                args += listOf("-id3v2_version", "3")
            }

            fun putMeta(key: String, value: String) {
                args += listOf("-metadata", "$key=${value.trim()}")
            }

            putMeta("title", request.title)
            putMeta("album", request.album)
            putMeta("artist", request.artist)
            putMeta("album_artist", request.albumArtist)
            putMeta("composer", request.composer)
            putMeta("genre", request.genre)
            putMeta("lyricist", request.lyricist)
            putMeta("track", request.trackNumber)
            putMeta("disc", request.discNumber)
            putMeta("date", request.year)
            putMeta("comment", request.comment)
            if (request.embeddedLyrics.isNotBlank()) {
                putMeta("lyrics", request.embeddedLyrics.trim())
            } else {
                putMeta("lyrics", "")
            }

            if (ext == "m4a" || ext == "mp4") {
                args += listOf("-movflags", "+faststart")
            }

            args += outputFile.absolutePath
            return args
        }
    }
}
