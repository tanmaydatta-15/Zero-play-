package com.example.downloader

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ResolvedMediaStream(
    val primaryUrl: String,
    val title: String?,
    val artist: String?,
    val mimeType: String,
    val extension: String,
    val headers: Map<String, String> = emptyMap(),
    val companionAudioUrl: String? = null,
    val companionAudioHeaders: Map<String, String> = emptyMap(),
    val fallbackMuxedUrl: String? = null,
    val fallbackMuxedHeaders: Map<String, String> = emptyMap(),
    val needsAudioExtraction: Boolean = false
)

internal class MediaUrlResolver {

    companion object {
        private const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val CHROME_DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val WHATSAPP_CRAWLER_UA = "WhatsApp/2.24.18.78 A"
        private const val FB_CRAWLER_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
        private const val IG_MOBILE_UA =
            "Instagram 317.0.0.34.109 Android (34/14; 420dpi; 1080x2400; Google/google; Pixel 8; shiba; shiba; en_US; 564229237)"
        private const val IG_APP_ID = "936619743392459"
        private const val IG_SHORTCODE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private const val MAX_TEXT_READ_BYTES = 1_500_000
    }

    fun resolve(
        rawUrl: String,
        preferAudio: Boolean,
        maxVideoHeightPx: Int
    ): Result<ResolvedMediaStream> {
        val cleanUrl = rawUrl.trim()

        // 1. Check YouTube directly first
        extractYouTubeVideoId(cleanUrl)?.let { videoId ->
            return resolveYouTube(videoId, preferAudio, maxVideoHeightPx)
        }

        // 2. Check Instagram (including /share/... links via redirect resolution)
        if (isInstagramUrl(cleanUrl)) {
            return resolveInstagram(cleanUrl, preferAudio)
        }

        // 3. Follow redirects in case of URL shorteners pointing to YT/IG/direct files
        val probe = probeUrlFollowingRedirects(cleanUrl)
        if (probe.isFailure) {
            return Result.failure(probe.exceptionOrNull() ?: IllegalArgumentException("Unable to reach URL"))
        }
        val probed = probe.getOrThrow()

        // Re-check redirected target URL for YouTube or Instagram
        extractYouTubeVideoId(probed.finalUrl)?.let { videoId ->
            return resolveYouTube(videoId, preferAudio, maxVideoHeightPx)
        }
        if (isInstagramUrl(probed.finalUrl)) {
            return resolveInstagram(probed.finalUrl, preferAudio)
        }

        val ct = probed.contentType.lowercase()
        val pathLower = probed.finalUrl.substringBefore('?').lowercase()
        if (ct.startsWith("video/") ||
            ct.startsWith("audio/") ||
            ct.contains("mp4") ||
            ct.contains("webm") ||
            (!ct.contains("text/html") && !ct.contains("application/xhtml") &&
                (ct.contains("octet-stream") || hasKnownMediaExtension(pathLower)))
        ) {
            val ext = guessExtensionFromMimeOrUrl(ct, probed.finalUrl, preferAudio)
            val mime = if (ct.startsWith("video/") || ct.startsWith("audio/")) ct.substringBefore(';')
            else if (preferAudio) "audio/$ext" else "video/mp4"
            return Result.success(
                ResolvedMediaStream(
                    primaryUrl = probed.finalUrl,
                    title = null,
                    artist = null,
                    mimeType = mime,
                    extension = ext,
                    headers = mapOf("User-Agent" to CHROME_MOBILE_UA)
                )
            )
        }

        if (ct.contains("text/html") || ct.contains("application/xhtml")) {
            val extracted = extractDirectMediaFromHtml(probed.bodyText)
            if (extracted != null) {
                val ext = guessExtensionFromMimeOrUrl("", extracted, preferAudio)
                return Result.success(
                    ResolvedMediaStream(
                        primaryUrl = extracted,
                        title = extractHtmlTitle(probed.bodyText),
                        artist = null,
                        mimeType = if (preferAudio && !isVideoUrl(extracted)) "audio/$ext" else "video/mp4",
                        extension = if (preferAudio) "m4a" else ext,
                        headers = mapOf("User-Agent" to CHROME_MOBILE_UA, "Referer" to probed.finalUrl),
                        needsAudioExtraction = preferAudio && isVideoUrl(extracted)
                    )
                )
            }
            return Result.failure(
                IllegalArgumentException("Webpage returned HTML with no downloadable video or audio stream.")
            )
        }

        return Result.failure(
            IllegalArgumentException("URL did not return a playable video or audio stream ($ct).")
        )
    }

    // -------------------------------------------------------------------------
    // YouTube Resolution (InnerTube Clients + Piped / Invidious Fallbacks)
    // -------------------------------------------------------------------------

    internal fun extractYouTubeVideoId(rawUrl: String): String? {
        val normalized = rawUrl.trim()
        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return null
        val host = (uri.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val path = uri.path.orEmpty()

        if (host == "youtu.be") {
            val id = path.trimStart('/').substringBefore('/')
            return id.takeIf { isValidYouTubeId(it) }
        }
        if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            val query = uri.rawQuery.orEmpty()
            val vParam = query.split('&')
                .firstOrNull { it.startsWith("v=") }
                ?.substringAfter("v=")
                ?.substringBefore('&')
            if (vParam != null && isValidYouTubeId(vParam)) return vParam

            val segs = path.split('/').filter { it.isNotBlank() }
            if (segs.size >= 2 && segs[0] in setOf("shorts", "live", "embed", "v", "reels")) {
                val candidate = segs[1]
                if (isValidYouTubeId(candidate)) return candidate
            }
        }
        return null
    }

    private fun isValidYouTubeId(id: String): Boolean =
        Regex("^[A-Za-z0-9_-]{11}$").matches(id)

    private data class InnerTubeProfile(
        val name: String,
        val clientName: String,
        val clientVersion: String,
        val clientId: String,
        val userAgent: String,
        val deviceMake: String,
        val deviceModel: String,
        val osName: String,
        val osVersion: String,
        val androidSdkVersion: Int? = null,
        val isEmbedded: Boolean = false
    )

    private val innerTubeProfiles = listOf(
        InnerTubeProfile(
            name = "ANDROID_VR",
            clientName = "ANDROID_VR",
            clientVersion = "1.62.27",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            osName = "Android",
            osVersion = "12L",
            androidSdkVersion = 32
        ),
        InnerTubeProfile(
            name = "ANDROID_VR_LEGACY",
            clientName = "ANDROID_VR",
            clientVersion = "1.43.32",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; quest-user) gzip",
            deviceMake = "Oculus",
            deviceModel = "Quest 2",
            osName = "Android",
            osVersion = "12",
            androidSdkVersion = 32
        ),
        InnerTubeProfile(
            name = "ANDROID_CREATOR",
            clientName = "ANDROID_CREATOR",
            clientVersion = "24.30.100",
            clientId = "14",
            userAgent = "com.google.android.apps.youtube.creator/24.30.100 (Linux; U; Android 14; en_US) gzip",
            deviceMake = "Google",
            deviceModel = "Pixel 8",
            osName = "Android",
            osVersion = "14",
            androidSdkVersion = 34
        ),
        InnerTubeProfile(
            name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = "85",
            userAgent = CHROME_DESKTOP_UA,
            deviceMake = "",
            deviceModel = "",
            osName = "Windows",
            osVersion = "10.0",
            isEmbedded = true
        ),
        InnerTubeProfile(
            name = "ANDROID_EMBEDDED_PLAYER",
            clientName = "ANDROID_EMBEDDED_PLAYER",
            clientVersion = "1.0",
            clientId = "55",
            userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip",
            deviceMake = "Google",
            deviceModel = "Pixel 8",
            osName = "Android",
            osVersion = "14",
            androidSdkVersion = 34
        ),
        InnerTubeProfile(
            name = "IOS",
            clientName = "IOS",
            clientVersion = "20.10.4",
            clientId = "5",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            osName = "iPhone",
            osVersion = "18.3.2.22D82"
        )
    )

    private fun resolveYouTube(
        videoId: String,
        preferAudio: Boolean,
        maxVideoHeightPx: Int
    ): Result<ResolvedMediaStream> {
        var lastReason: String? = null
        val visitorData = fetchYouTubeVisitorData()
        val oembedTitle = fetchYouTubeOembedTitle(videoId)

        for (profile in innerTubeProfiles) {
            val result = fetchYouTubeInnerTube(videoId, profile, visitorData, preferAudio, maxVideoHeightPx)
            if (result.isSuccess) {
                val verified = verifyYouTubeCandidate(result.getOrThrow(), preferAudio)
                if (verified != null) {
                    return Result.success(
                        if (verified.title.isNullOrBlank() && !oembedTitle.isNullOrBlank()) {
                            verified.copy(title = oembedTitle)
                        } else verified
                    )
                }
                lastReason = "YouTube CDN rejected ${profile.name} stream (HTTP 403)"
            } else {
                lastReason = result.exceptionOrNull()?.message
            }
        }

        // Public Piped & Invidious fallback instances (each probed for HTTP 200 before accepting)
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.private.coffee",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.r4fo.com"
        )
        for (base in pipedInstances) {
            val res = fetchPipedStreams("$base/streams/$videoId", preferAudio, maxVideoHeightPx)
            if (res.isSuccess) {
                val verified = verifyYouTubeCandidate(res.getOrThrow(), preferAudio)
                if (verified != null) return Result.success(verified)
            }
        }

        val invidiousInstances = listOf(
            "https://inv.nadeko.net",
            "https://yewtu.be",
            "https://invidious.nerdvpn.de",
            "https://iv.melmac.space",
            "https://invidious.protokolla.fi"
        )
        val proxyItags = when {
            preferAudio -> listOf(140)
            maxVideoHeightPx >= 720 -> listOf(22, 18)
            else -> listOf(18)
        }
        for (base in invidiousInstances) {
            val res = fetchInvidiousStreams("$base/api/v1/videos/$videoId", preferAudio, maxVideoHeightPx)
            if (res.isSuccess) {
                val verified = verifyYouTubeCandidate(res.getOrThrow(), preferAudio)
                if (verified != null) return Result.success(verified)
            }
            // Server-proxied Invidious local=true stream bypasses carrier IPv4/IPv6 & CDN 403 blocks
            for (itag in proxyItags) {
                val proxyUrl = "$base/latest_version?id=$videoId&itag=$itag&local=true"
                val proxyHeaders = mapOf("User-Agent" to CHROME_MOBILE_UA)
                if (probeMediaUrlOk(proxyUrl, proxyHeaders)) {
                    return Result.success(
                        ResolvedMediaStream(
                            primaryUrl = proxyUrl,
                            title = oembedTitle,
                            artist = "YouTube",
                            mimeType = if (preferAudio) "audio/mp4" else "video/mp4",
                            extension = if (preferAudio) "m4a" else "mp4",
                            headers = proxyHeaders
                        )
                    )
                }
            }
        }

        return Result.failure(
            IllegalArgumentException(
                lastReason
                    ?: "YouTube blocked stream extraction for this video (private, age-restricted, or CDN 403)."
            )
        )
    }

    private fun fetchYouTubeInnerTube(
        videoId: String,
        profile: InnerTubeProfile,
        visitorData: String?,
        preferAudio: Boolean,
        maxVideoHeightPx: Int
    ): Result<ResolvedMediaStream> {
        return runCatching {
            val clientObj = JSONObject().apply {
                put("clientName", profile.clientName)
                put("clientVersion", profile.clientVersion)
                if (profile.deviceMake.isNotEmpty()) put("deviceMake", profile.deviceMake)
                if (profile.deviceModel.isNotEmpty()) put("deviceModel", profile.deviceModel)
                put("osName", profile.osName)
                put("osVersion", profile.osVersion)
                profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
                if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                put("hl", "en")
                put("gl", "US")
            }
            val contextObj = JSONObject().apply {
                put("client", clientObj)
                if (profile.isEmbedded) {
                    put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/"))
                }
            }
            val payload = JSONObject().apply {
                put("context", contextObj)
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            // Native clients must NOT attach web Origin/Referer headers on InnerTube or googlevideo.com
            val reqHeaders = buildMap {
                put("User-Agent", profile.userAgent)
                put("Content-Type", "application/json")
                put("X-YouTube-Client-Name", profile.clientId)
                put("X-YouTube-Client-Version", profile.clientVersion)
                if (!visitorData.isNullOrBlank()) {
                    put("X-Goog-Visitor-Id", visitorData)
                }
                if (profile.isEmbedded) {
                    put("Origin", "https://www.youtube.com")
                    put("Referer", "https://www.youtube.com/watch?v=$videoId")
                }
            }
            val streamHeaders = if (profile.isEmbedded) {
                mapOf(
                    "User-Agent" to profile.userAgent,
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                )
            } else {
                mapOf("User-Agent" to profile.userAgent)
            }

            val (code, body) = httpPostString(
                urlString = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                body = payload.toString(),
                headers = reqHeaders
            )
            if (code !in 200..299 || body.isBlank()) {
                error("InnerTube ${profile.name} returned HTTP $code")
            }

            val root = JSONObject(body)
            parseInnerTubeJson(
                root = root,
                preferAudio = preferAudio,
                maxVideoHeightPx = maxVideoHeightPx,
                streamHeaders = streamHeaders
            ).getOrThrow()
        }
    }

    private fun verifyYouTubeCandidate(
        candidate: ResolvedMediaStream,
        preferAudio: Boolean
    ): ResolvedMediaStream? {
        val bareHeaders = mapOf("User-Agent" to (candidate.headers["User-Agent"] ?: CHROME_MOBILE_UA))
        val headerVariants = listOf(candidate.headers, bareHeaders).distinct()

        for (headersToTry in headerVariants) {
            val primaryOk = probeMediaUrlOk(candidate.primaryUrl, headersToTry)
            if (!primaryOk) continue
            val companionUrl = candidate.companionAudioUrl
            if (companionUrl == null) {
                return candidate.copy(headers = headersToTry)
            }
            val companionHeaders = candidate.companionAudioHeaders.ifEmpty { headersToTry }
            val companionDirectOk = probeMediaUrlOk(companionUrl, companionHeaders)
            val companionBareOk = !companionDirectOk &&
                companionHeaders != bareHeaders &&
                probeMediaUrlOk(companionUrl, bareHeaders)
            if (companionDirectOk || companionBareOk) {
                return candidate.copy(
                    headers = headersToTry,
                    companionAudioHeaders = if (companionDirectOk) companionHeaders else bareHeaders
                )
            }
        }

        // If adaptive 1080p/audio returned 403, try the muxed progressive stream (itag 22/18) from the same response
        val muxedUrl = candidate.fallbackMuxedUrl
        if (!preferAudio && muxedUrl != null) {
            val muxVariants = listOf(
                candidate.fallbackMuxedHeaders.ifEmpty { candidate.headers },
                bareHeaders
            ).distinct()
            for (muxHeaders in muxVariants) {
                if (probeMediaUrlOk(muxedUrl, muxHeaders)) {
                    return candidate.copy(
                        primaryUrl = muxedUrl,
                        headers = muxHeaders,
                        companionAudioUrl = null,
                        companionAudioHeaders = emptyMap(),
                        fallbackMuxedUrl = null
                    )
                }
            }
        }
        return null
    }

    private fun fetchYouTubeVisitorData(): String? {
        val (code, body) = httpGetString(
            urlString = "https://www.youtube.com/sw.js_data",
            headers = mapOf("User-Agent" to CHROME_DESKTOP_UA),
            timeoutMs = 5000
        )
        if (code in 200..299 && body.isNotBlank()) {
            val match = Regex(""""(Cg[A-Za-z0-9_%-]{16,})"""").find(body)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun fetchYouTubeOembedTitle(videoId: String): String? {
        val (code, body) = httpGetString(
            urlString = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json",
            headers = mapOf("User-Agent" to CHROME_DESKTOP_UA),
            timeoutMs = 5000
        )
        if (code in 200..299 && body.trimStart().startsWith("{")) {
            return runCatching {
                JSONObject(body).optString("title").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
        return null
    }

    private fun probeMediaUrlOk(urlString: String, headers: Map<String, String>): Boolean {
        var currentUrl = urlString
        var redirects = 0
        while (redirects < 5) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc.isNullOrBlank()) return false
                    currentUrl = URL(URL(currentUrl), loc).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299 && code != 206) {
                    return false
                }
                val ct = conn.contentType.orEmpty().lowercase()
                if (ct.contains("text/html") || ct.contains("application/xhtml")) {
                    return false
                }
                val buf = ByteArray(64)
                val read = conn.inputStream.use { it.read(buf) }
                if (read <= 0) return false
                val head = String(buf, 0, read, Charsets.UTF_8).trimStart().lowercase()
                if (head.startsWith("<!doctype html") || head.startsWith("<html")) {
                    return false
                }
                return true
            } catch (_: Throwable) {
                return false
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
        return false
    }

    internal fun parseInnerTubeJson(
        root: JSONObject,
        preferAudio: Boolean,
        maxVideoHeightPx: Int,
        streamHeaders: Map<String, String> = emptyMap()
    ): Result<ResolvedMediaStream> {
        val playability = root.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (status.isNotBlank() && status != "OK") {
            val reason = playability?.optString("reason")
                ?.takeIf { it.isNotBlank() }
                ?: "YouTube playback unavailable ($status)"
            return Result.failure(IllegalArgumentException(reason))
        }

        val details = root.optJSONObject("videoDetails")
        val title = details?.optString("title")?.takeIf { it.isNotBlank() }
        val author = details?.optString("author")?.takeIf { it.isNotBlank() }

        val streamingData = root.optJSONObject("streamingData")
            ?: return Result.failure(IllegalArgumentException("No streamingData returned by YouTube"))

        val muxed = parseInnerTubeFormatArray(streamingData.optJSONArray("formats"))
        val adaptive = parseInnerTubeFormatArray(streamingData.optJSONArray("adaptiveFormats"))

        val bestM4aAudio = adaptive
            .filter { it.mimeType.startsWith("audio/mp4") && !it.mimeType.contains("ec-3") && !it.mimeType.contains("ac-3") }
            .maxByOrNull { it.bitrate }
        val bestAnyAudio = bestM4aAudio
            ?: adaptive.filter { it.mimeType.startsWith("audio/") }.maxByOrNull { it.bitrate }

        if (preferAudio) {
            val chosenAudio = bestAnyAudio
                ?: muxed.firstOrNull { it.url.isNotBlank() }
                ?: return Result.failure(IllegalArgumentException("No direct audio stream found in YouTube response"))
            val isM4a = chosenAudio.mimeType.startsWith("audio/mp4")
            val isWebm = chosenAudio.mimeType.startsWith("audio/webm")
            val ext = when {
                isM4a -> "m4a"
                isWebm -> "webm"
                chosenAudio.mimeType.startsWith("video/") -> "m4a"
                else -> "m4a"
            }
            return Result.success(
                ResolvedMediaStream(
                    primaryUrl = chosenAudio.url,
                    title = title,
                    artist = author,
                    mimeType = if (chosenAudio.mimeType.startsWith("video/")) "audio/mp4" else chosenAudio.mimeType.substringBefore(';'),
                    extension = ext,
                    headers = streamHeaders,
                    needsAudioExtraction = chosenAudio.mimeType.startsWith("video/")
                )
            )
        }

        // Video requested: pick muxed MP4 and adaptive HD MP4 + M4A audio
        val mp4MuxedCandidates = muxed.filter { it.mimeType.startsWith("video/mp4") }
        val bestMuxed = mp4MuxedCandidates
            .filter { it.height <= maxVideoHeightPx || maxVideoHeightPx <= 0 }
            .maxByOrNull { it.height * 10_000L + it.bitrate }
            ?: mp4MuxedCandidates.minByOrNull { it.height }
            ?: muxed.maxByOrNull { it.height * 10_000L + it.bitrate }

        val mp4AdaptiveVideos = adaptive.filter {
            it.mimeType.startsWith("video/mp4") &&
                (it.mimeType.contains("avc1", ignoreCase = true) ||
                    it.mimeType.contains("mp4v", ignoreCase = true) ||
                    it.mimeType.contains("hvc1", ignoreCase = true) ||
                    it.mimeType.contains("hev1", ignoreCase = true) ||
                    !it.mimeType.contains("av01", ignoreCase = true))
        }
        val bestAdaptiveVideo = mp4AdaptiveVideos
            .filter { it.height in 1..maxVideoHeightPx }
            .maxByOrNull { it.height * 10_000L + it.bitrate }
            ?: mp4AdaptiveVideos.maxByOrNull { it.height }

        if (bestAdaptiveVideo != null && bestM4aAudio != null &&
            (bestMuxed == null || bestAdaptiveVideo.height > bestMuxed.height)
        ) {
            return Result.success(
                ResolvedMediaStream(
                    primaryUrl = bestAdaptiveVideo.url,
                    title = title,
                    artist = author,
                    mimeType = "video/mp4",
                    extension = "mp4",
                    headers = streamHeaders,
                    companionAudioUrl = bestM4aAudio.url,
                    companionAudioHeaders = streamHeaders,
                    fallbackMuxedUrl = bestMuxed?.url,
                    fallbackMuxedHeaders = streamHeaders
                )
            )
        }

        if (bestMuxed != null) {
            return Result.success(
                ResolvedMediaStream(
                    primaryUrl = bestMuxed.url,
                    title = title,
                    artist = author,
                    mimeType = "video/mp4",
                    extension = "mp4",
                    headers = streamHeaders
                )
            )
        }

        if (bestAdaptiveVideo != null) {
            return Result.success(
                ResolvedMediaStream(
                    primaryUrl = bestAdaptiveVideo.url,
                    title = title,
                    artist = author,
                    mimeType = "video/mp4",
                    extension = "mp4",
                    headers = streamHeaders,
                    companionAudioUrl = bestM4aAudio?.url,
                    companionAudioHeaders = streamHeaders
                )
            )
        }

        return Result.failure(IllegalArgumentException("YouTube returned encrypted/cipher-only streams for this client"))
    }

    private data class YtFormatEntry(
        val url: String,
        val mimeType: String,
        val height: Int,
        val bitrate: Int
    )

    private fun parseInnerTubeFormatArray(array: JSONArray?): List<YtFormatEntry> {
        if (array == null) return emptyList()
        val list = ArrayList<YtFormatEntry>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val directUrl = obj.optString("url", "")
            if (directUrl.isBlank()) continue
            list.add(
                YtFormatEntry(
                    url = directUrl,
                    mimeType = obj.optString("mimeType", "").lowercase(),
                    height = obj.optInt("height", 0),
                    bitrate = obj.optInt("bitrate", obj.optInt("averageBitrate", 0))
                )
            )
        }
        return list
    }

    private fun fetchPipedStreams(
        apiUrl: String,
        preferAudio: Boolean,
        maxVideoHeightPx: Int
    ): Result<ResolvedMediaStream> {
        return runCatching {
            val (code, body) = httpGetString(apiUrl, mapOf("User-Agent" to CHROME_MOBILE_UA), timeoutMs = 6000)
            if (code !in 200..299 || body.isBlank()) error("Piped HTTP $code")
            val json = JSONObject(body)
            if (json.has("error")) error(json.optString("message", "Piped error"))

            val title = json.optString("title").takeIf { it.isNotBlank() }
            val uploader = json.optString("uploader").takeIf { it.isNotBlank() }
            val audioStreams = json.optJSONArray("audioStreams")
            val videoStreams = json.optJSONArray("videoStreams")

            val bestAudio = selectBestPipedStream(audioStreams) { obj ->
                val mime = obj.optString("mimeType", "").lowercase()
                if (mime.contains("mp4") || mime.contains("m4a")) 2_000_000 + obj.optInt("bitrate", 0)
                else obj.optInt("bitrate", 0)
            }

            if (preferAudio) {
                val audioObj = bestAudio ?: error("No piped audio")
                val url = audioObj.optString("url")
                val mime = audioObj.optString("mimeType", "audio/mp4").lowercase()
                return@runCatching ResolvedMediaStream(
                    primaryUrl = url,
                    title = title,
                    artist = uploader,
                    mimeType = mime.substringBefore(';'),
                    extension = if (mime.contains("webm")) "webm" else "m4a",
                    headers = mapOf("User-Agent" to CHROME_MOBILE_UA)
                )
            }

            // Prefer muxed MP4 first, or adaptive MP4 video + M4A audio
            val muxedMp4 = selectBestPipedStream(videoStreams) { obj ->
                val videoOnly = obj.optBoolean("videoOnly", true)
                val mime = obj.optString("mimeType", "").lowercase()
                val h = parseHeight(obj.optString("quality", ""))
                if (!videoOnly && mime.contains("mp4") && h in 1..maxVideoHeightPx) h * 10_000 + obj.optInt("bitrate", 0)
                else -1
            }

            val adaptiveMp4 = selectBestPipedStream(videoStreams) { obj ->
                val mime = obj.optString("mimeType", "").lowercase()
                val codec = obj.optString("codec", "").lowercase()
                val h = parseHeight(obj.optString("quality", ""))
                if (mime.contains("mp4") && !codec.startsWith("av01") && h in 1..maxVideoHeightPx) {
                    h * 10_000 + obj.optInt("bitrate", 0)
                } else -1
            }

            val audioUrl = bestAudio?.optString("url")?.takeIf { it.isNotBlank() }
            val muxedUrl = muxedMp4?.optString("url")?.takeIf { it.isNotBlank() }
            if (adaptiveMp4 != null && audioUrl != null && adaptiveMp4.optBoolean("videoOnly", false)) {
                return@runCatching ResolvedMediaStream(
                    primaryUrl = adaptiveMp4.optString("url"),
                    title = title,
                    artist = uploader,
                    mimeType = "video/mp4",
                    extension = "mp4",
                    headers = mapOf("User-Agent" to CHROME_MOBILE_UA),
                    companionAudioUrl = audioUrl,
                    companionAudioHeaders = mapOf("User-Agent" to CHROME_MOBILE_UA),
                    fallbackMuxedUrl = muxedUrl
                )
            }
            if (muxedUrl != null) {
                return@runCatching ResolvedMediaStream(
                    primaryUrl = muxedUrl,
                    title = title,
                    artist = uploader,
                    mimeType = "video/mp4",
                    extension = "mp4",
                    headers = mapOf("User-Agent" to CHROME_MOBILE_UA)
                )
            }
            error("No compatible piped stream")
        }
    }

    private fun selectBestPipedStream(arr: JSONArray?, scorer: (JSONObject) -> Int): JSONObject? {
        if (arr == null) return null
        var best: JSONObject? = null
        var bestScore = -1
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("url").isBlank()) continue
            val score = scorer(obj)
            if (score > bestScore) {
                bestScore = score
                best = obj
            }
        }
        return best
    }

    private fun fetchInvidiousStreams(
        apiUrl: String,
        preferAudio: Boolean,
        maxVideoHeightPx: Int
    ): Result<ResolvedMediaStream> {
        return runCatching {
            val (code, body) = httpGetString(apiUrl, mapOf("User-Agent" to CHROME_MOBILE_UA), timeoutMs = 6000)
            if (code !in 200..299 || body.isBlank()) error("Invidious HTTP $code")
            val json = JSONObject(body)
            val title = json.optString("title").takeIf { it.isNotBlank() }
            val author = json.optString("author").takeIf { it.isNotBlank() }

            val formatStreams = json.optJSONArray("formatStreams")
            val adaptiveFormats = json.optJSONArray("adaptiveFormats")

            if (preferAudio) {
                var bestUrl: String? = null
                var bestBitrate = -1
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val obj = adaptiveFormats.optJSONObject(i) ?: continue
                        val type = obj.optString("type", "").lowercase()
                        val url = obj.optString("url")
                        if (type.startsWith("audio/mp4") && url.isNotBlank()) {
                            val br = obj.optString("bitrate", "0").toIntOrNull() ?: 0
                            if (br > bestBitrate) {
                                bestBitrate = br
                                bestUrl = url
                            }
                        }
                    }
                }
                val resolvedAudio = bestUrl ?: error("No Invidious audio")
                return@runCatching ResolvedMediaStream(
                    primaryUrl = resolvedAudio,
                    title = title,
                    artist = author,
                    mimeType = "audio/mp4",
                    extension = "m4a",
                    headers = mapOf("User-Agent" to CHROME_MOBILE_UA)
                )
            }

            var bestMuxedUrl: String? = null
            var bestHeight = -1
            if (formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                    val obj = formatStreams.optJSONObject(i) ?: continue
                    val container = obj.optString("container", "").lowercase()
                    val url = obj.optString("url")
                    val h = parseHeight(obj.optString("resolution", obj.optString("qualityLabel", "")))
                    if (container == "mp4" && url.isNotBlank() && (h <= maxVideoHeightPx || bestMuxedUrl == null)) {
                        if (h > bestHeight) {
                            bestHeight = h
                            bestMuxedUrl = url
                        }
                    }
                }
            }
            val finalUrl = bestMuxedUrl ?: error("No Invidious MP4 stream")
            ResolvedMediaStream(
                primaryUrl = finalUrl,
                title = title,
                artist = author,
                mimeType = "video/mp4",
                extension = "mp4",
                headers = mapOf("User-Agent" to CHROME_MOBILE_UA)
            )
        }
    }

    private fun parseHeight(label: String): Int {
        return Regex("(\\d{3,4})p").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    // -------------------------------------------------------------------------
    // Instagram Resolution (Reels, Posts, TV, Share Links)
    // -------------------------------------------------------------------------

    internal fun isInstagramUrl(rawUrl: String): Boolean {
        val host = runCatching { java.net.URI(rawUrl.trim()).host?.lowercase() }.getOrNull().orEmpty()
        return host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am"
    }

    internal fun extractInstagramShortcode(rawUrl: String): String? {
        val match = Regex("""/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""").find(rawUrl)
        return match?.groupValues?.getOrNull(1)
    }

    internal fun shortcodeToMediaId(shortcode: String): String? {
        if (shortcode.isBlank()) return null
        var id = BigInteger.ZERO
        val base = BigInteger.valueOf(64)
        for (ch in shortcode) {
            val idx = IG_SHORTCODE_ALPHABET.indexOf(ch)
            if (idx < 0) return null
            id = id.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        return id.toString()
    }

    private fun resolveInstagram(rawUrl: String, preferAudio: Boolean): Result<ResolvedMediaStream> {
        var canonicalUrl = rawUrl.trim()
        var shortcode = extractInstagramShortcode(canonicalUrl)

        // Follow /share/reel/... redirects or canonical link if shortcode isn't directly visible
        if (shortcode == null) {
            val redirected = resolveRedirectOnly(canonicalUrl)
            if (redirected != null) {
                canonicalUrl = redirected
                shortcode = extractInstagramShortcode(canonicalUrl)
            }
            if (shortcode == null) {
                val (_, shareBody) = runCatching {
                    httpGetString(canonicalUrl, mapOf("User-Agent" to CHROME_DESKTOP_UA), timeoutMs = 6000)
                }.getOrElse { 0 to "" }
                shortcode = extractInstagramShortcode(shareBody)
            }
        }

        if (shortcode == null) {
            return Result.failure(
                IllegalArgumentException("Could not find an Instagram Reel or Post ID in this link.")
            )
        }

        var sawPhotoOnly = false

        // Strategy 1: Instagram Embed Captioned HTML (/p/<shortcode>/embed/captioned/ & /reel/<shortcode>/embed/captioned/)
        val embedUrls = listOf(
            "https://www.instagram.com/reel/$shortcode/embed/captioned/",
            "https://www.instagram.com/p/$shortcode/embed/captioned/",
            "https://www.instagram.com/p/$shortcode/embed/"
        )
        for (embedUrl in embedUrls) {
            val res = fetchInstagramFromHtmlPage(embedUrl, CHROME_DESKTOP_UA, preferAudio)
            if (res.isSuccess) {
                val stream = res.getOrThrow()
                if (probeMediaUrlOk(stream.primaryUrl, stream.headers)) return Result.success(stream)
                if (probeMediaUrlOk(stream.primaryUrl, mapOf("User-Agent" to CHROME_DESKTOP_UA))) {
                    return Result.success(stream.copy(headers = mapOf("User-Agent" to CHROME_DESKTOP_UA)))
                }
            }
            if (res.exceptionOrNull()?.message?.contains("photo-only") == true) sawPhotoOnly = true
        }

        // Strategy 2: Instagram GraphQL endpoint (PolarisPostActionLoadPostQueryQuery)
        val gqlRes = fetchInstagramGraphQl(shortcode, preferAudio)
        if (gqlRes.isSuccess) {
            val stream = gqlRes.getOrThrow()
            if (probeMediaUrlOk(stream.primaryUrl, stream.headers)) return Result.success(stream)
            if (probeMediaUrlOk(stream.primaryUrl, mapOf("User-Agent" to CHROME_DESKTOP_UA))) {
                return Result.success(stream.copy(headers = mapOf("User-Agent" to CHROME_DESKTOP_UA)))
            }
        }
        if (gqlRes.exceptionOrNull()?.message?.contains("photo-only") == true) sawPhotoOnly = true

        // Strategy 3: Instagram Mobile API v1 (/api/v1/media/{mediaId}/info/) & ?__a=1&__d=dis
        val mediaId = shortcodeToMediaId(shortcode)
        if (mediaId != null) {
            val apiRes = fetchInstagramMobileApi(mediaId, shortcode, preferAudio)
            if (apiRes.isSuccess) {
                val stream = apiRes.getOrThrow()
                if (probeMediaUrlOk(stream.primaryUrl, stream.headers)) return Result.success(stream)
                if (probeMediaUrlOk(stream.primaryUrl, mapOf("User-Agent" to CHROME_DESKTOP_UA))) {
                    return Result.success(stream.copy(headers = mapOf("User-Agent" to CHROME_DESKTOP_UA)))
                }
            }
            if (apiRes.exceptionOrNull()?.message?.contains("photo-only") == true) sawPhotoOnly = true
        }

        // Strategy 4: OpenGraph Crawler User-Agents (WhatsApp / Facebook crawler)
        val pageUrl = "https://www.instagram.com/reel/$shortcode/"
        for (ua in listOf(WHATSAPP_CRAWLER_UA, FB_CRAWLER_UA)) {
            val res = fetchInstagramFromHtmlPage(pageUrl, ua, preferAudio)
            if (res.isSuccess) {
                val stream = res.getOrThrow()
                if (probeMediaUrlOk(stream.primaryUrl, stream.headers)) return Result.success(stream)
                if (probeMediaUrlOk(stream.primaryUrl, mapOf("User-Agent" to CHROME_DESKTOP_UA))) {
                    return Result.success(stream.copy(headers = mapOf("User-Agent" to CHROME_DESKTOP_UA)))
                }
            }
            if (res.exceptionOrNull()?.message?.contains("photo-only") == true) sawPhotoOnly = true
        }

        return Result.failure(
            IllegalArgumentException(
                if (sawPhotoOnly) {
                    "This Instagram post appears to be photo-only or private (requires login)."
                } else {
                    "Instagram blocked anonymous extraction for this Reel/Post (may be private, age-gated, or rate-limited)."
                }
            )
        )
    }

    private fun fetchInstagramFromHtmlPage(
        pageUrl: String,
        userAgent: String,
        preferAudio: Boolean
    ): Result<ResolvedMediaStream> {
        return runCatching {
            val (code, body) = httpGetString(
                urlString = pageUrl,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Referer" to "https://www.instagram.com/"
                )
            )
            if (code !in 200..299 || body.isBlank()) error("Instagram returned HTTP $code")
            parseInstagramHtmlOrJson(body, pageUrl, preferAudio).getOrThrow()
        }
    }

    private fun fetchInstagramGraphQl(shortcode: String, preferAudio: Boolean): Result<ResolvedMediaStream> {
        return runCatching {
            val postUrl = "https://www.instagram.com/reel/$shortcode/"
            var csrfToken = "missing"
            var lsdToken = "AVqbxe3J_YA"

            // Grab initial cookies & LSD token cleanly
            val conn = (URL(postUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", CHROME_DESKTOP_UA)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            val cookieHeader = StringBuilder()
            try {
                conn.connect()
                val setCookies = conn.headerFields["Set-Cookie"].orEmpty()
                for (cookie in setCookies) {
                    val pair = cookie.substringBefore(';')
                    if (cookieHeader.isNotEmpty()) cookieHeader.append("; ")
                    cookieHeader.append(pair)
                    if (pair.startsWith("csrftoken=")) {
                        csrfToken = pair.substringAfter("csrftoken=")
                    }
                }
                val html = readConnectionText(conn)
                Regex(""""LSD",\[\],\{"token":"([^"]+)"""").find(html)?.groupValues?.getOrNull(1)?.let {
                    lsdToken = it
                }
                Regex(""""csrf_token":"([^"]+)"""").find(html)?.groupValues?.getOrNull(1)?.let {
                    if (csrfToken == "missing") csrfToken = it
                }
                // Check if the HTML itself already gave us a live playable video_url
                val inlineResult = parseInstagramHtmlOrJson(html, postUrl, preferAudio)
                if (inlineResult.isSuccess) {
                    val inlineStream = inlineResult.getOrThrow()
                    if (probeMediaUrlOk(inlineStream.primaryUrl, inlineStream.headers) ||
                        probeMediaUrlOk(inlineStream.primaryUrl, mapOf("User-Agent" to CHROME_DESKTOP_UA))
                    ) {
                        return@runCatching inlineStream
                    }
                }
            } finally {
                runCatching { conn.disconnect() }
            }

            val variablesJson = JSONObject().apply {
                put("shortcode", shortcode)
                put("fetch_tagged_user_count", JSONObject.NULL)
                put("hoisted_comment_id", JSONObject.NULL)
                put("hoisted_reply_id", JSONObject.NULL)
            }.toString()

            val docIds = listOf("8845758582119845", "10015901848480474", "24368985919464652")
            for (docId in docIds) {
                val formBody = buildString {
                    append("av=0")
                    append("&__d=www")
                    append("&__user=0")
                    append("&__a=1")
                    append("&__req=3")
                    append("&dpr=1")
                    append("&__ccg=UNKNOWN")
                    append("&__rev=1016278042")
                    append("&lsd=").append(URLEncoder.encode(lsdToken, "UTF-8"))
                    append("&fb_api_caller_class=RelayModern")
                    append("&fb_api_req_friendly_name=PolarisPostActionLoadPostQueryQuery")
                    append("&variables=").append(URLEncoder.encode(variablesJson, "UTF-8"))
                    append("&server_timestamps=true")
                    append("&doc_id=").append(docId)
                }

                val headers = buildMap {
                    put("User-Agent", CHROME_DESKTOP_UA)
                    put("Content-Type", "application/x-www-form-urlencoded")
                    put("X-IG-App-ID", IG_APP_ID)
                    put("X-FB-LSD", lsdToken)
                    put("X-CSRFToken", csrfToken)
                    put("X-ASBD-ID", "129477")
                    put("X-FB-Friendly-Name", "PolarisPostActionLoadPostQueryQuery")
                    put("Origin", "https://www.instagram.com")
                    put("Referer", postUrl)
                    if (cookieHeader.isNotEmpty()) put("Cookie", cookieHeader.toString())
                }

                val (code, resp) = httpPostString("https://www.instagram.com/graphql/query", formBody, headers)
                if (code in 200..299 && resp.isNotBlank()) {
                    val parsed = parseInstagramGraphQlResponse(resp, preferAudio)
                    if (parsed.isSuccess) return@runCatching parsed.getOrThrow()
                }
            }
            error("Instagram GraphQL returned no video URL")
        }
    }

    private fun fetchInstagramMobileApi(
        mediaId: String,
        shortcode: String,
        preferAudio: Boolean
    ): Result<ResolvedMediaStream> {
        return runCatching {
            val endpoints = listOf(
                "https://i.instagram.com/api/v1/media/$mediaId/info/",
                "https://www.instagram.com/p/$shortcode/?__a=1&__d=dis"
            )
            for (ep in endpoints) {
                val (code, body) = httpGetString(
                    urlString = ep,
                    headers = mapOf(
                        "User-Agent" to IG_MOBILE_UA,
                        "X-IG-App-ID" to IG_APP_ID,
                        "Accept" to "application/json",
                        "Accept-Language" to "en-US,en;q=0.9"
                    )
                )
                if (code in 200..299 && body.trimStart().startsWith("{")) {
                    val parsed = parseInstagramJsonPayload(JSONObject(body), preferAudio)
                    if (parsed.isSuccess) return@runCatching parsed.getOrThrow()
                }
            }
            error("Instagram mobile API unavailable")
        }
    }

    internal fun parseInstagramGraphQlResponse(rawJson: String, preferAudio: Boolean): Result<ResolvedMediaStream> {
        return runCatching {
            val root = JSONObject(rawJson)
            val data = root.optJSONObject("data") ?: error("Missing data")
            val media = data.optJSONObject("xdt_shortcode_media")
                ?: data.optJSONObject("shortcode_media")
                ?: error("Missing shortcode media")

            val title = media.optJSONObject("edge_media_to_caption")
                ?.optJSONArray("edges")
                ?.optJSONObject(0)
                ?.optJSONObject("node")
                ?.optString("text")
                ?.lineSequence()?.firstOrNull()?.take(80)
            val author = media.optJSONObject("owner")?.optString("username")?.takeIf { it.isNotBlank() }

            var videoUrl = media.optString("video_url").takeIf { it.isNotBlank() }
            if (videoUrl == null) {
                val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
                if (edges != null) {
                    for (i in 0 until edges.length()) {
                        val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                        val childUrl = node.optString("video_url")
                        if (childUrl.isNotBlank()) {
                            videoUrl = childUrl
                            break
                        }
                    }
                }
            }
            if (videoUrl == null) {
                if (media.has("is_video") && !media.optBoolean("is_video", true)) {
                    error("This Instagram post appears to be photo-only")
                }
                error("No video_url in GraphQL payload")
            }

            ResolvedMediaStream(
                primaryUrl = unescapeMediaUrl(videoUrl),
                title = title,
                artist = author?.let { "@$it" },
                mimeType = if (preferAudio) "audio/mp4" else "video/mp4",
                extension = if (preferAudio) "m4a" else "mp4",
                headers = mapOf("User-Agent" to CHROME_DESKTOP_UA, "Referer" to "https://www.instagram.com/"),
                needsAudioExtraction = preferAudio
            )
        }
    }

    internal fun parseInstagramJsonPayload(root: JSONObject, preferAudio: Boolean): Result<ResolvedMediaStream> {
        return runCatching {
            val items = root.optJSONArray("items")
            val item = items?.optJSONObject(0)
                ?: root.optJSONObject("graphql")?.optJSONObject("shortcode_media")
                ?: error("No media items")

            val title = item.optJSONObject("caption")?.optString("text")
                ?.lineSequence()?.firstOrNull()?.take(80)
            val user = item.optJSONObject("user")?.optString("username")?.takeIf { it.isNotBlank() }

            var videoUrl: String? = item.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url")
                ?.takeIf { it.isNotBlank() }
                ?: item.optString("video_url").takeIf { it.isNotBlank() }

            if (videoUrl == null) {
                val carousel = item.optJSONArray("carousel_media")
                if (carousel != null) {
                    for (i in 0 until carousel.length()) {
                        val v = carousel.optJSONObject(i)
                            ?.optJSONArray("video_versions")
                            ?.optJSONObject(0)
                            ?.optString("url")
                        if (!v.isNullOrBlank()) {
                            videoUrl = v
                            break
                        }
                    }
                }
            }
            if (videoUrl == null) error("This Instagram post appears to be photo-only")

            ResolvedMediaStream(
                primaryUrl = unescapeMediaUrl(videoUrl),
                title = title,
                artist = user?.let { "@$it" },
                mimeType = if (preferAudio) "audio/mp4" else "video/mp4",
                extension = if (preferAudio) "m4a" else "mp4",
                headers = mapOf("User-Agent" to CHROME_DESKTOP_UA, "Referer" to "https://www.instagram.com/"),
                needsAudioExtraction = preferAudio
            )
        }
    }

    internal fun parseInstagramHtmlOrJson(
        body: String,
        sourceUrl: String,
        preferAudio: Boolean
    ): Result<ResolvedMediaStream> {
        val candidates = listOf(
            Regex(""""video_url"\s*:\s*"((?:https?:|https?:\\/)[^"]+)""""),
            Regex(""""video_versions"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"((?:https?:|https?:\\/)[^"]+)""""),
            Regex(""""contentUrl"\s*:\s*"((?:https?:|https?:\\/)[^"]+\.mp4[^"]*)""""),
            Regex("""<meta[^>]+property=["']og:video:secure_url["'][^>]+content=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["'](https?://[^"']+)["'][^>]+property=["']og:video:secure_url["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+property=["']og:video["'][^>]+content=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["'](https?://[^"']+)["'][^>]+property=["']og:video["']""", RegexOption.IGNORE_CASE),
            Regex("""<video[^>]+src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(https?(?::\\/|://)[^\s"'<>]+?\.mp4[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
        )
        for (regex in candidates) {
            val rawMatch = regex.find(body)?.groupValues?.getOrNull(1) ?: continue
            val clean = unescapeMediaUrl(rawMatch)
            if (clean.startsWith("http") && !clean.contains("static.cdninstagram.com/rsrc.php")) {
                val title = extractHtmlTitle(body)
                return Result.success(
                    ResolvedMediaStream(
                        primaryUrl = clean,
                        title = title,
                        artist = null,
                        mimeType = if (preferAudio) "audio/mp4" else "video/mp4",
                        extension = if (preferAudio) "m4a" else "mp4",
                        headers = mapOf("User-Agent" to CHROME_DESKTOP_UA, "Referer" to sourceUrl),
                        needsAudioExtraction = preferAudio
                    )
                )
            }
        }
        val hasOgImage = body.contains("og:image", ignoreCase = true) || body.contains("display_url")
        return Result.failure(
            IllegalArgumentException(
                if (hasOgImage && !body.contains("video_url")) {
                    "This Instagram post appears to be photo-only or login-restricted."
                } else {
                    "No direct Instagram video URL found in page."
                }
            )
        )
    }

    // -------------------------------------------------------------------------
    // Generic Webpage Media & Network Helpers
    // -------------------------------------------------------------------------

    private data class ProbedResponse(
        val finalUrl: String,
        val contentType: String,
        val bodyText: String
    )

    private fun probeUrlFollowingRedirects(urlString: String): Result<ProbedResponse> {
        var conn: HttpURLConnection? = null
        return try {
            var currentUrl = urlString
            var redirects = 0
            while (redirects < 6) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 9000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", CHROME_MOBILE_UA)
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc.isNullOrBlank()) break
                    currentUrl = URL(URL(currentUrl), loc).toString()
                    redirects++
                } else {
                    break
                }
            }
            val active = conn ?: error("Connection failed")
            val finalUrl = active.url?.toString() ?: currentUrl
            val ct = active.contentType.orEmpty()
            val bodyText = if (ct.contains("text/html", ignoreCase = true) ||
                ct.contains("application/xhtml", ignoreCase = true) ||
                ct.contains("json", ignoreCase = true)
            ) {
                readConnectionText(active)
            } else {
                ""
            }
            Result.success(ProbedResponse(finalUrl, ct, bodyText))
        } catch (t: Throwable) {
            Result.failure(IllegalArgumentException(t.message ?: "Unable to verify media URL"))
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun resolveRedirectOnly(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            var current = urlString
            repeat(6) {
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", CHROME_DESKTOP_UA)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
                }
                val code = conn!!.responseCode
                val loc = conn!!.getHeaderField("Location")
                conn!!.disconnect()
                if (code in 300..399 && !loc.isNullOrBlank()) {
                    current = URL(URL(current), loc).toString()
                } else {
                    return current
                }
            }
            current
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun httpGetString(
        urlString: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 9000
    ): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            val text = readConnectionText(conn, isError = code !in 200..299)
            code to text
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun httpPostString(
        urlString: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 9000
    ): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "POST"
                doOutput = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                setFixedLengthStreamingMode(bodyBytes.size)
            }
            conn.outputStream.use { it.write(bodyBytes) }
            val code = conn.responseCode
            val text = readConnectionText(conn, isError = code !in 200..299)
            code to text
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readConnectionText(conn: HttpURLConnection, isError: Boolean = false): String {
        val stream = (if (isError) conn.errorStream else runCatching { conn.inputStream }.getOrNull())
            ?: return ""
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (total < MAX_TEXT_READ_BYTES) {
                val n = input.read(buf)
                if (n <= 0) break
                val allowed = minOf(n, MAX_TEXT_READ_BYTES - total)
                out.write(buf, 0, allowed)
                total += allowed
            }
            out.toString("UTF-8")
        }
    }

    internal fun extractDirectMediaFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val patterns = listOf(
            Regex("""<meta[^>]+property=["']og:video:secure_url["'][^>]+content=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["'](https?://[^"']+)["'][^>]+property=["']og:video:secure_url["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+property=["']og:video["'][^>]+content=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["'](https?://[^"']+)["'][^>]+property=["']og:video["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+name=["']twitter:player:stream["'][^>]+content=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex(""""contentUrl"\s*:\s*"((?:https?:|https?:\\/)[^"]+\.(?:mp4|m4a|mp3|webm)[^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""<(?:video|source)[^>]+src=["'](https?://[^"']+\.(?:mp4|m4a|mp3|webm)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""(https?(?::\\/|://)[^\s"'<>\\]+\.(?:mp4|m4a|mp3|webm)(?:\?[^\s"'<>\\]*)?)""", RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val match = regex.find(html)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) {
                val cleaned = unescapeMediaUrl(match)
                if (cleaned.startsWith("http")) return cleaned
            }
        }
        return null
    }

    private fun extractHtmlTitle(html: String): String? {
        val ogTitle = Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        val raw = ogTitle ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        return raw?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")
            ?.trim()?.takeIf { it.isNotBlank() && !it.equals("Instagram", ignoreCase = true) }
    }

    internal fun unescapeMediaUrl(raw: String): String {
        return raw.trim()
            .trimEnd('\\')
            .replace("""\/""", "/")
            .replace("""\u0026""", "&")
            .replace("""\u002F""", "/", ignoreCase = true)
            .replace("""\u003D""", "=", ignoreCase = true)
            .replace("&amp;", "&")
    }

    private fun hasKnownMediaExtension(pathLower: String): Boolean {
        return listOf(".mp4", ".m4a", ".mp3", ".webm", ".mkv", ".wav", ".aac", ".ogg", ".flac")
            .any { pathLower.endsWith(it) }
    }

    private fun isVideoUrl(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv")
    }

    private fun guessExtensionFromMimeOrUrl(mimeLower: String, url: String, preferAudio: Boolean): String {
        val pathLower = url.substringBefore('?').lowercase()
        return when {
            pathLower.endsWith(".mp4") -> "mp4"
            pathLower.endsWith(".m4a") -> "m4a"
            pathLower.endsWith(".mp3") -> "mp3"
            pathLower.endsWith(".webm") -> "webm"
            pathLower.endsWith(".mkv") -> "mkv"
            pathLower.endsWith(".flac") -> "flac"
            pathLower.endsWith(".ogg") -> "ogg"
            mimeLower.contains("audio/mp4") || mimeLower.contains("aac") -> "m4a"
            mimeLower.contains("mpeg") -> "mp3"
            mimeLower.contains("webm") -> "webm"
            preferAudio -> "m4a"
            else -> "mp4"
        }
    }
}
