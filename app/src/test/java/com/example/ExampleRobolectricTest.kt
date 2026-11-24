package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.EchoRepository
import com.example.diagnostics.DeviceCapabilityInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Zero Play", appName)
    }

    @Test
    fun `stale room drawable resource id is safely sanitized to valid cover`() {
        val staleId = 0x7f05005d
        val fallback = R.drawable.img_cover_midnight_1794995218900
        assertEquals(fallback, EchoRepository.sanitizeDrawableId(staleId, fallback))
        assertEquals(
            R.drawable.img_cover_aurora_1794995218098,
            EchoRepository.sanitizeDrawableId(R.drawable.img_cover_aurora_1794995218098, fallback)
        )
    }

    @Test
    fun `device capability inspector returns full codec diagnostics and detects nextlib av1 decoder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = DeviceCapabilityInspector.inspect(context)
        val mimeTypes = report.codecs.map { it.mimeType }.toSet()
        assertTrue(mimeTypes.contains("video/av01"))
        assertTrue(mimeTypes.contains("video/x-vnd.on2.vp9"))
        assertTrue(mimeTypes.contains("video/hevc"))
        assertTrue(mimeTypes.contains("video/avc"))
        assertTrue(mimeTypes.contains("audio/opus"))
        assertTrue(DeviceCapabilityInspector.isMedia3Av1DecoderPresent())
        assertTrue(report.media3Av1ExtensionAvailable)
    }
}
