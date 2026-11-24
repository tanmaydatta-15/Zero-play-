package com.example

import com.example.downloader.MediaUrlResolver
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaUrlResolverTest {

    private val resolver = MediaUrlResolver()

    @Test
    fun extractYouTubeVideoId_handlesAllFormats() {
        assertEquals("ezxqKElqUAO", resolver.extractYouTubeVideoId("https://youtu.be/ezxqKElqUAO?si=xyz123"))
        assertEquals("ezxqKElqUAO", resolver.extractYouTubeVideoId("https://www.youtube.com/watch?v=ezxqKElqUAO&feature=share"))
        assertEquals("ezxqKElqUAO", resolver.extractYouTubeVideoId("https://youtube.com/shorts/ezxqKElqUAO"))
        assertEquals("ezxqKElqUAO", resolver.extractYouTubeVideoId("https://music.youtube.com/watch?v=ezxqKElqUAO"))
        assertEquals("ezxqKElqUAO", resolver.extractYouTubeVideoId("https://m.youtube.com/live/ezxqKElqUAO"))
        assertNull(resolver.extractYouTubeVideoId("https://www.instagram.com/reel/Df1nQiWToL1/"))
    }

    @Test
    fun parseInnerTubeJson_resolvesAdaptiveHdVideoAndAudioOrMuxed() {
        val root = JSONObject().apply {
            put("playabilityStatus", JSONObject().put("status", "OK"))
            put("videoDetails", JSONObject().put("title", "Test Song").put("author", "Test Artist"))
            put(
                "streamingData",
                JSONObject().apply {
                    put(
                        "formats",
                        JSONArray().put(
                            JSONObject()
                                .put("url", "https://cdn.example.com/muxed_360.mp4")
                                .put("mimeType", "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"")
                                .put("height", 360)
                                .put("bitrate", 450000)
                        )
                    )
                    put(
                        "adaptiveFormats",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("url", "https://cdn.example.com/video_1080.mp4")
                                    .put("mimeType", "video/mp4; codecs=\"avc1.640028\"")
                                    .put("height", 1080)
                                    .put("bitrate", 2500000)
                            )
                            .put(
                                JSONObject()
                                    .put("url", "https://cdn.example.com/audio_140.m4a")
                                    .put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                                    .put("bitrate", 129000)
                            )
                    )
                }
            )
        }

        val videoResult = resolver.parseInnerTubeJson(root, preferAudio = false, maxVideoHeightPx = 1080)
        assertTrue(videoResult.isSuccess)
        val videoStream = videoResult.getOrThrow()
        assertEquals("https://cdn.example.com/video_1080.mp4", videoStream.primaryUrl)
        assertEquals("https://cdn.example.com/audio_140.m4a", videoStream.companionAudioUrl)
        assertEquals("https://cdn.example.com/muxed_360.mp4", videoStream.fallbackMuxedUrl)
        assertEquals("Test Song", videoStream.title)
        assertEquals("Test Artist", videoStream.artist)

        val audioResult = resolver.parseInnerTubeJson(root, preferAudio = true, maxVideoHeightPx = 1080)
        assertTrue(audioResult.isSuccess)
        val audioStream = audioResult.getOrThrow()
        assertEquals("https://cdn.example.com/audio_140.m4a", audioStream.primaryUrl)
        assertEquals("m4a", audioStream.extension)
    }

    @Test
    fun instagramShortcodeAndPayloads_extractCleanUnescapedUrls() {
        val igUrl = "https://www.instagram.com/reel/Df1nQiWToL1/?igsh=abc123"
        assertTrue(resolver.isInstagramUrl(igUrl))
        val shortcode = resolver.extractInstagramShortcode(igUrl)
        assertEquals("Df1nQiWToL1", shortcode)
        assertNotNull(resolver.shortcodeToMediaId(shortcode!!))

        val escapedEmbedHtml = """
            <html><title>Cool Reel</title>
            <script>{"video_url":"https:\/\/scontent.cdninstagram.com\/v\/t50\/video.mp4?_nc_ht=scontent\u0026oe=1234"}</script>
            </html>
        """.trimIndent()

        val parsedHtml = resolver.parseInstagramHtmlOrJson(escapedEmbedHtml, igUrl, preferAudio = false)
        assertTrue(parsedHtml.isSuccess)
        assertEquals(
            "https://scontent.cdninstagram.com/v/t50/video.mp4?_nc_ht=scontent&oe=1234",
            parsedHtml.getOrThrow().primaryUrl
        )

        val audioFromIg = resolver.parseInstagramHtmlOrJson(escapedEmbedHtml, igUrl, preferAudio = true)
        assertTrue(audioFromIg.isSuccess)
        assertTrue(audioFromIg.getOrThrow().needsAudioExtraction)
        assertEquals("m4a", audioFromIg.getOrThrow().extension)
    }

    @Test
    fun unescapeMediaUrl_stripsTrailingBackslashesAndUnescapesTokens() {
        val raw = "https:\\/\\/scontent.cdninstagram.com\\/video.mp4?a=1\\u0026b\\u003D2\\"
        assertEquals(
            "https://scontent.cdninstagram.com/video.mp4?a=1&b=2",
            resolver.unescapeMediaUrl(raw)
        )
        assertEquals(
            "Df1nQiWToL1",
            resolver.extractInstagramShortcode("https://www.instagram.com/someuser/reel/Df1nQiWToL1/?igsh=xyz")
        )
    }

    @Test
    fun ytDlpEngine_tokenizesFlagsAndBuildsFormatSelectors() {
        val tokens = com.example.downloader.YtDlpEngine.tokenizeCommandFlags(
            "-f \"bv*+ba/best\" --extractor-args 'youtube:player_client=android_vr' --write-subs"
        )
        assertEquals(
            listOf("-f", "bv*+ba/best", "--extractor-args", "youtube:player_client=android_vr", "--write-subs"),
            tokens
        )

        val legacySelector = com.example.downloader.YtDlpEngine.buildFormatSelector(
            preset = com.example.data.model.DownloadFormatPreset.VIDEO_MP4_STANDARD,
            maxVideoHeightPx = 720,
            preferredVideoFormat = com.example.data.local.SealPreferredVideoFormatPref.LEGACY
        )
        assertTrue(legacySelector.contains("height<=720"))
        assertTrue(legacySelector.contains("avc1"))

        val audioSelector = com.example.downloader.YtDlpEngine.buildFormatSelector(
            preset = com.example.data.model.DownloadFormatPreset.AUDIO_M4A_HIGH,
            maxVideoHeightPx = 1080,
            preferredVideoFormat = com.example.data.local.SealPreferredVideoFormatPref.QUALITY
        )
        assertTrue(audioSelector.startsWith("bestaudio"))
    }
}
