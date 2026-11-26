package com.example.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.awxkee.jxlcoder.JxlDecodingSpeed
import com.awxkee.jxlcoder.JxlEffort
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import okio.BufferedSource

/**
 * Local next-generation image codec bridge for AVIF (`image/avif`) and JPEG XL (`image/jxl`),
 * providing both encoding for [MediaConverterEngine] and a Coil 2 [Decoder.Factory] so
 * converted `.avif` and `.jxl` assets preview cleanly in Before/After comparison and Library.
 */
object NextGenImageCodec {

    @Volatile
    private var coilConfigured = false

    fun installCoilDecoders(context: Context) {
        if (coilConfigured) return
        synchronized(this) {
            if (coilConfigured) return
            val appContext = context.applicationContext
            val imageLoader = ImageLoader.Builder(appContext)
                .components {
                    add(NextGenImageDecoderFactory())
                }
                .build()
            Coil.setImageLoader(imageLoader)
            coilConfigured = true
        }
    }

    fun isJxlBytes(header: ByteArray): Boolean {
        if (header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0x0A.toByte()) {
            return true
        }
        if (header.size >= 12 &&
            header[0] == 0x00.toByte() &&
            header[1] == 0x00.toByte() &&
            header[2] == 0x00.toByte() &&
            header[3] == 0x0C.toByte() &&
            header[4] == 0x4A.toByte() && // 'J'
            header[5] == 0x58.toByte() && // 'X'
            header[6] == 0x4C.toByte() && // 'L'
            header[7] == 0x20.toByte()
        ) {
            return true
        }
        return false
    }

    fun isAvifBytes(header: ByteArray): Boolean {
        if (header.size < 12) return false
        val isFtyp = header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        if (!isFtyp) return false
        val brand = String(header, 8, 4, Charsets.US_ASCII)
        return brand == "avif" || brand == "avis" || brand == "mif1"
    }

    fun decodeImage(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        if (isJxlBytes(bytes)) {
            return runCatching { JxlCoder.decode(bytes) }.getOrNull()
        }
        if (isAvifBytes(bytes)) {
            val nativeDecoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            } else {
                null
            }
            return nativeDecoded ?: runCatching { HeifCoder().decode(bytes) }.getOrNull()
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: runCatching { HeifCoder().decode(bytes) }.getOrNull()
            ?: runCatching { JxlCoder.decode(bytes) }.getOrNull()
    }

    fun encodeAvif(bitmap: Bitmap, quality: Int): ByteArray {
        val safeBitmap = ensureArgb8888(bitmap)
        val clampedQuality = quality.coerceIn(10, 100)
        return try {
            HeifCoder().encodeAvif(
                bitmap = safeBitmap,
                quality = clampedQuality
            )
        } finally {
            if (safeBitmap !== bitmap) safeBitmap.recycle()
        }
    }

    fun encodeJxl(bitmap: Bitmap, quality: Int): ByteArray {
        val safeBitmap = ensureArgb8888(bitmap)
        val clampedQuality = quality.coerceIn(10, 100)
        val compression = if (clampedQuality >= 100) {
            JxlCompressionOption.LOSSLESS
        } else {
            JxlCompressionOption.LOSSY
        }
        return try {
            JxlCoder.encode(
                bitmap = safeBitmap,
                compressionOption = compression,
                effort = JxlEffort.SQUIRREL,
                decodingSpeed = JxlDecodingSpeed.SLOW,
                quality = clampedQuality
            )
        } finally {
            if (safeBitmap !== bitmap) safeBitmap.recycle()
        }
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private class NextGenImageDecoderFactory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val mime = result.mimeType?.lowercase().orEmpty()
            val peeked = peekHeader(result.source.source())
            val isJxl = mime == "image/jxl" || isJxlBytes(peeked)
            val isAvif = mime == "image/avif" || isAvifBytes(peeked)
            if (!isJxl && !isAvif) return null
            return Decoder {
                val bytes = result.source.source().use { it.readByteArray() }
                val bitmap = decodeImage(bytes) ?: error("Unable to decode next-gen image")
                DecodeResult(
                    drawable = BitmapDrawable(options.context.resources, bitmap),
                    isSampled = false
                )
            }
        }

        private fun peekHeader(source: BufferedSource): ByteArray {
            return runCatching {
                val peek = source.peek()
                val buf = ByteArray(16)
                val read = peek.read(buf, 0, buf.size)
                if (read > 0) buf.copyOf(read) else ByteArray(0)
            }.getOrDefault(ByteArray(0))
        }
    }
}
