package com.example.downloader

import com.example.data.local.SealAudioFormatPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.VideoContainerOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpNewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val headersBuilder = Headers.Builder()
        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                headersBuilder.add(key, value)
            }
        }
        val body = request.dataToSend()?.toRequestBody(null)
        val okRequest = okhttp3.Request.Builder()
            .url(request.url())
            .headers(headersBuilder.build())
            .method(request.httpMethod(), body)
            .build()

        client.newCall(okRequest).execute().use { okResponse ->
            if (okResponse.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            val bodyString = okResponse.body?.string().orEmpty()
            return Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                bodyString,
                okResponse.request.url.toString()
            )
        }
    }
}

data class NewPipeExtractedStream(
    val title: String,
    val uploader: String,
    val serviceName: String,
    val durationSec: Long,
    val videoUrl: String?,
    val videoExtension: String?,
    val videoCodecBadge: String?,
    val audioUrl: String?,
    val audioExtension: String?,
    val audioCodecBadge: String?,
    val muxedFallbackUrl: String?,
    val subtitleUrl: String?
)

class NewPipeEngine(
    private val sharedClient: OkHttpClient? = null
) {

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    private val httpClient: OkHttpClient by lazy {
        sharedClient ?: OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun ensureInitialized(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (initialized) return@withContext Result.success(true)
        initMutex.withLock {
            if (initialized) return@withContext Result.success(true)
            runCatching {
                NewPipe.init(OkHttpNewPipeDownloader(httpClient))
                initialized = true
                true
            }
        }
    }

    fun isFastPathUrl(rawUrl: String): Boolean {
        val lower = rawUrl.lowercase()
        return lower.contains("youtube.com/") ||
            lower.contains("youtu.be/") ||
            lower.contains("music.youtube.com/") ||
            lower.contains("soundcloud.com/") ||
            lower.contains("bandcamp.com/") ||
            lower.contains("peertube") ||
            lower.contains("framatube.org/") ||
            lower.contains("tilvids.com/")
    }

    suspend fun extractStream(
        url: String,
        audioOnly: Boolean,
        maxVideoHeightPx: Int,
        preferredCodec: VideoCodecPreference,
        preferredContainer: VideoContainerOption,
        preferredAudioFormat: SealAudioFormatPref,
        includeSubtitles: Boolean
    ): Result<NewPipeExtractedStream> = withContext(Dispatchers.IO) {
        ensureInitialized().onFailure { return@withContext Result.failure(it) }
        runCatching {
            val service = runCatching { NewPipe.getServiceByUrl(url) }.getOrElse {
                when {
                    url.contains("soundcloud.com", true) -> ServiceList.SoundCloud
                    url.contains("bandcamp.com", true) -> ServiceList.Bandcamp
                    url.contains("youtube.com", true) || url.contains("youtu.be", true) -> ServiceList.YouTube
                    else -> error("URL not supported by NewPipeExtractor fast path")
                }
            }

            val info = StreamInfo.getInfo(service, url)
            val title = info.name?.takeIf { it.isNotBlank() } ?: "Zero Play Media"
            val uploader = info.uploaderName?.takeIf { it.isNotBlank() } ?: service.serviceInfo.name

            val audioStreams = info.audioStreams.orEmpty().filter { !it.content.isNullOrBlank() }
            val preferOpus = preferredAudioFormat == SealAudioFormatPref.OPUS ||
                (!audioOnly && preferredContainer != VideoContainerOption.MP4)

            val bestAudio = selectBestAudioStream(audioStreams, preferOpus)

            val subtitleUrl = if (includeSubtitles) {
                info.subtitles.orEmpty()
                    .firstOrNull { !it.content.isNullOrBlank() }
                    ?.content
            } else null

            if (audioOnly) {
                val chosenAudio = bestAudio ?: error("No audio stream extracted by NewPipeExtractor")
                return@runCatching NewPipeExtractedStream(
                    title = title,
                    uploader = uploader,
                    serviceName = service.serviceInfo.name,
                    durationSec = info.duration.coerceAtLeast(0L),
                    videoUrl = null,
                    videoExtension = null,
                    videoCodecBadge = null,
                    audioUrl = chosenAudio.content,
                    audioExtension = inferAudioExt(chosenAudio),
                    audioCodecBadge = inferAudioCodecBadge(chosenAudio),
                    muxedFallbackUrl = null,
                    subtitleUrl = subtitleUrl
                )
            }

            val videoOnly = info.videoOnlyStreams.orEmpty().filter { !it.content.isNullOrBlank() }
            val muxed = info.videoStreams.orEmpty().filter { !it.content.isNullOrBlank() }

            val bestMuxed = muxed
                .filter { parseHeight(it) <= maxVideoHeightPx || maxVideoHeightPx <= 0 }
                .maxByOrNull { parseHeight(it) }
                ?: muxed.maxByOrNull { parseHeight(it) }

            val bestAdaptive = selectBestVideoStream(
                streams = videoOnly,
                maxHeight = maxVideoHeightPx,
                preferredCodec = preferredCodec
            )

            if (bestAdaptive != null && bestAudio != null) {
                NewPipeExtractedStream(
                    title = title,
                    uploader = uploader,
                    serviceName = service.serviceInfo.name,
                    durationSec = info.duration.coerceAtLeast(0L),
                    videoUrl = bestAdaptive.content,
                    videoExtension = bestAdaptive.format?.suffix ?: "mp4",
                    videoCodecBadge = inferVideoCodecBadge(bestAdaptive),
                    audioUrl = bestAudio.content,
                    audioExtension = inferAudioExt(bestAudio),
                    audioCodecBadge = inferAudioCodecBadge(bestAudio),
                    muxedFallbackUrl = bestMuxed?.content,
                    subtitleUrl = subtitleUrl
                )
            } else if (bestMuxed != null) {
                NewPipeExtractedStream(
                    title = title,
                    uploader = uploader,
                    serviceName = service.serviceInfo.name,
                    durationSec = info.duration.coerceAtLeast(0L),
                    videoUrl = bestMuxed.content,
                    videoExtension = bestMuxed.format?.suffix ?: "mp4",
                    videoCodecBadge = inferVideoCodecBadge(bestMuxed),
                    audioUrl = null,
                    audioExtension = null,
                    audioCodecBadge = "AAC",
                    muxedFallbackUrl = bestMuxed.content,
                    subtitleUrl = subtitleUrl
                )
            } else {
                error("No playable video stream extracted by NewPipeExtractor")
            }
        }
    }

    private fun selectBestVideoStream(
        streams: List<VideoStream>,
        maxHeight: Int,
        preferredCodec: VideoCodecPreference
    ): VideoStream? {
        if (streams.isEmpty()) return null
        val bounded = streams.filter { parseHeight(it) in 1..maxHeight }.ifEmpty { streams }
        val codecFiltered = when (preferredCodec) {
            VideoCodecPreference.AV1 -> bounded.filter { isCodecMatch(it, "av01", "av1") }
            VideoCodecPreference.VP9 -> bounded.filter { isCodecMatch(it, "vp9", "vp09") }
            VideoCodecPreference.HEVC -> bounded.filter { isCodecMatch(it, "hev1", "hvc1", "hevc", "h265") }
            VideoCodecPreference.H264 -> bounded.filter { isCodecMatch(it, "avc1", "h264") }
            VideoCodecPreference.AUTO -> emptyList()
        }.ifEmpty { bounded }

        return codecFiltered.maxWithOrNull(
            compareBy<VideoStream> { parseHeight(it) }
                .thenBy { it.fps }
                .thenBy { it.bitrate }
        )
    }

    private fun selectBestAudioStream(
        streams: List<AudioStream>,
        preferOpus: Boolean
    ): AudioStream? {
        if (streams.isEmpty()) return null
        val preferred = if (preferOpus) {
            streams.filter {
                val codec = (it.codec.orEmpty() + " " + it.format?.name.orEmpty()).lowercase()
                codec.contains("opus") || codec.contains("webm")
            }
        } else {
            streams.filter {
                val codec = (it.codec.orEmpty() + " " + it.format?.name.orEmpty()).lowercase()
                codec.contains("mp4a") || codec.contains("m4a") || codec.contains("aac")
            }
        }.ifEmpty { streams }
        return preferred.maxByOrNull { it.averageBitrate }
    }

    private fun parseHeight(stream: VideoStream): Int {
        if (stream.height > 0) return stream.height
        val res = stream.resolution.orEmpty()
        return Regex("""(\d{3,4})p""").find(res)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun isCodecMatch(stream: VideoStream, vararg tokens: String): Boolean {
        val raw = "${stream.codec.orEmpty()} ${stream.format?.mimeType.orEmpty()}".lowercase()
        return tokens.any { raw.contains(it) }
    }

    private fun inferVideoCodecBadge(stream: VideoStream): String {
        val raw = "${stream.codec.orEmpty()} ${stream.format?.mimeType.orEmpty()}".lowercase()
        return when {
            raw.contains("av01") || raw.contains("av1") -> "AV1"
            raw.contains("vp9") || raw.contains("vp09") -> "VP9"
            raw.contains("hev") || raw.contains("hvc") || raw.contains("265") -> "HEVC"
            raw.contains("avc") || raw.contains("264") -> "H.264"
            else -> "VIDEO"
        }
    }

    private fun inferAudioExt(stream: AudioStream): String {
        val raw = "${stream.codec.orEmpty()} ${stream.format?.suffix.orEmpty()}".lowercase()
        return when {
            raw.contains("opus") -> "opus"
            raw.contains("mp3") -> "mp3"
            raw.contains("flac") -> "flac"
            raw.contains("webm") -> "webm"
            else -> "m4a"
        }
    }

    private fun inferAudioCodecBadge(stream: AudioStream): String {
        val raw = "${stream.codec.orEmpty()} ${stream.format?.name.orEmpty()}".lowercase()
        return when {
            raw.contains("opus") -> "OPUS"
            raw.contains("flac") -> "FLAC"
            raw.contains("mp3") -> "MP3"
            else -> "AAC"
        }
    }
}
