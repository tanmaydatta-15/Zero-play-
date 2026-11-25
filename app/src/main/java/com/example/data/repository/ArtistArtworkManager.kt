package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.data.local.ArtistArtworkEntity
import com.example.data.local.EchoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Offline-first artist portrait cache backed by local files (`filesDir/artist_artwork/<name>.jpg`)
 * and Room (`artist_artwork`), with automatic public Deezer artist lookup when online and enabled,
 * plus user-selected manual overrides.
 */
class ArtistArtworkManager(
    private val context: Context,
    private val dao: EchoDao
) {
    companion object {
        private const val TAG = "ArtistArtworkManager"
        private const val RETRY_COOLDOWN_MS = 24L * 60L * 60L * 1000L // 24 hours for unmatched artists

        private val PLACEHOLDER_ARTISTS = setOf(
            "unknown",
            "<unknown>",
            "unknown artist",
            "local audio",
            "local video",
            "zero play audio",
            "zero play video",
            "zero play artist",
            "various artists",
            "various"
        )

        fun normalizeArtistKey(artistName: String): String =
            artistName.trim().lowercase(Locale.US)

        fun sanitizeArtistFileName(artistName: String): String {
            val cleaned = artistName.trim()
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(64)
            return if (cleaned.isBlank()) "artist_unknown" else cleaned
        }

        fun isPlaceholderArtist(artistName: String): Boolean {
            val key = normalizeArtistKey(artistName)
            return key.isBlank() || key in PLACEHOLDER_ARTISTS
        }
    }

    private val fetchSemaphore = Semaphore(3)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val artworkDir: File by lazy {
        File(context.filesDir, "artist_artwork").apply { mkdirs() }
    }

    /**
     * Emits map of `normalizedArtistKey -> localFileAbsolutePath` for all existing cached portraits.
     */
    val artistArtworks: Flow<Map<String, String>> = dao.observeArtistArtworks().map { rows ->
        buildMap {
            for (row in rows) {
                val path = row.localFilePath
                if (!path.isNullOrBlank() && File(path).exists()) {
                    put(row.artistKey, path)
                }
            }
        }
    }

    /**
     * Checks local disk cache first; for any uncached non-placeholder artist when online and
     * `autoDownloadEnabled == true`, fetches their portrait from Deezer's public artist search API.
     */
    suspend fun prefetchMissingArtistImages(
        artistNames: Collection<String>,
        autoDownloadEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val uniqueNames = artistNames.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { normalizeArtistKey(it) }
        val online = autoDownloadEnabled && isNetworkAvailable()

        for (artistName in uniqueNames) {
            val key = normalizeArtistKey(artistName)
            val diskFile = File(artworkDir, "${sanitizeArtistFileName(artistName)}.jpg")
            val existing = dao.getArtistArtwork(key)

            // 1. Instant offline hit on disk
            if (diskFile.exists() && diskFile.length() > 512L) {
                if (existing == null || existing.localFilePath != diskFile.absolutePath) {
                    dao.upsertArtistArtwork(
                        ArtistArtworkEntity(
                            artistKey = key,
                            artistName = artistName,
                            localFilePath = diskFile.absolutePath,
                            source = existing?.source ?: "DISK_CACHE",
                            lastAttemptMs = System.currentTimeMillis()
                        )
                    )
                }
                continue
            }

            if (!online || isPlaceholderArtist(artistName)) continue

            // Respect manual removal or recent failed attempt cooldown
            if (existing?.source == "MANUAL_REMOVED") continue
            if (existing != null && existing.localFilePath == null &&
                (System.currentTimeMillis() - existing.lastAttemptMs) < RETRY_COOLDOWN_MS
            ) {
                continue
            }

            fetchSemaphore.withPermit {
                fetchAndCacheFromDeezer(artistName, diskFile)
                if (diskFile.exists() && diskFile.length() > 512L) {
                    dao.upsertArtistArtwork(
                        ArtistArtworkEntity(
                            artistKey = key,
                            artistName = artistName,
                            localFilePath = diskFile.absolutePath,
                            source = "DEEZER",
                            lastAttemptMs = System.currentTimeMillis()
                        )
                    )
                } else {
                    dao.upsertArtistArtwork(
                        ArtistArtworkEntity(
                            artistKey = key,
                            artistName = artistName,
                            localFilePath = null,
                            source = "NOT_FOUND",
                            lastAttemptMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun fetchAndCacheFromDeezer(artistName: String, destFile: File) {
        runCatching {
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val req = Request.Builder()
                .url("https://api.deezer.com/search/artist?q=$encoded&limit=3")
                .header("User-Agent", "ZeroPlay-Android/2.1")
                .build()
            val imageUrl: String? = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val data = JSONObject(body).optJSONArray("data") ?: return@use null
                if (data.length() == 0) return@use null
                // Prefer exact name match, otherwise top Deezer hit
                var chosen = data.getJSONObject(0)
                for (i in 0 until data.length()) {
                    val candidate = data.getJSONObject(i)
                    if (candidate.optString("name", "").equals(artistName, ignoreCase = true)) {
                        chosen = candidate
                        break
                    }
                }
                chosen.optString("picture_big", "").takeIf { it.startsWith("http") }
                    ?: chosen.optString("picture_medium", "").takeIf { it.startsWith("http") }
                    ?: chosen.optString("picture", "").takeIf { it.startsWith("http") }
            }

            if (imageUrl.isNullOrBlank()) return

            val imgReq = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "ZeroPlay-Android/2.1")
                .build()
            httpClient.newCall(imgReq).execute().use { imgResp ->
                if (!imgResp.isSuccessful) return
                val bytes = imgResp.body?.bytes() ?: return
                if (bytes.size < 512) return
                val tmp = File(artworkDir, "${destFile.name}.tmp")
                FileOutputStream(tmp).use { it.write(bytes) }
                if (!tmp.renameTo(destFile)) {
                    tmp.copyTo(destFile, overwrite = true)
                    tmp.delete()
                }
            }
        }.onFailure {
            Log.w(TAG, "Deezer artist lookup skipped for $artistName: ${it.message}")
        }
    }

    suspend fun saveManualArtistImage(artistName: String, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = normalizeArtistKey(artistName)
            val destFile = File(artworkDir, "${sanitizeArtistFileName(artistName)}.jpg")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(destFile).use { out -> input.copyTo(out) }
            } ?: error("Unable to read selected image")
            dao.upsertArtistArtwork(
                ArtistArtworkEntity(
                    artistKey = key,
                    artistName = artistName,
                    localFilePath = destFile.absolutePath,
                    source = "MANUAL",
                    lastAttemptMs = System.currentTimeMillis()
                )
            )
            destFile.absolutePath
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }
}
