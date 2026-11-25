package com.example.updater

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * Pure-Kotlin RFC 3284 VCDIFF (xdelta3) delta patch decoder and SHA-256 verifier.
 * Reconstructs a new signed APK on-device by applying an xdelta3 patch against the
 * installed base APK without requiring any external native binary.
 */
object VcdiffPatchApplier {

    private const val MAGIC_0 = 0xD6
    private const val MAGIC_1 = 0xC3
    private const val MAGIC_2 = 0xC4
    private const val MAGIC_3 = 0x00

    private const val HDR_VCD_DECOMPRESS = 0x01
    private const val HDR_VCD_CODETABLE = 0x02
    private const val HDR_VCD_APPHEADER = 0x04

    private const val WIN_VCD_SOURCE = 0x01
    private const val WIN_VCD_TARGET = 0x02
    private const val WIN_VCD_ADLER32 = 0x04

    private const val TYPE_NOOP = 0
    private const val TYPE_ADD = 1
    private const val TYPE_RUN = 2
    private const val TYPE_COPY = 3

    private data class CodeEntry(
        val type1: Int,
        val size1: Int,
        val mode1: Int,
        val type2: Int = TYPE_NOOP,
        val size2: Int = 0,
        val mode2: Int = 0
    )

    // RFC 3284 Section 5.6 Default Code Table (256 entries)
    private val defaultCodeTable: Array<CodeEntry> by lazy { buildDefaultCodeTable() }

    private fun buildDefaultCodeTable(): Array<CodeEntry> {
        val table = Array(256) { CodeEntry(TYPE_NOOP, 0, 0) }
        var idx = 0
        // 0: RUN size 0
        table[idx++] = CodeEntry(TYPE_RUN, 0, 0)
        // 1..18: ADD size 0..17
        for (size in 0..17) {
            table[idx++] = CodeEntry(TYPE_ADD, size, 0)
        }
        // 19..162: COPY mode 0..8, sizes 0, 4..18 (16 sizes * 9 modes = 144)
        for (mode in 0..8) {
            table[idx++] = CodeEntry(TYPE_COPY, 0, mode)
            for (size in 4..18) {
                table[idx++] = CodeEntry(TYPE_COPY, size, mode)
            }
        }
        // 163..234: ADD (size 1..4) + COPY (mode 0..5, size 4..6) => 6 * 4 * 3 = 72
        for (mode in 0..5) {
            for (addSize in 1..4) {
                for (copySize in 4..6) {
                    table[idx++] = CodeEntry(TYPE_ADD, addSize, 0, TYPE_COPY, copySize, mode)
                }
            }
        }
        // 235..246: ADD (size 1..4) + COPY (mode 6..8, size 4) => 3 * 4 = 12
        for (mode in 6..8) {
            for (addSize in 1..4) {
                table[idx++] = CodeEntry(TYPE_ADD, addSize, 0, TYPE_COPY, 4, mode)
            }
        }
        // 247..255: COPY (mode 0..8, size 4) + ADD (size 1) => 9
        for (mode in 0..8) {
            table[idx++] = CodeEntry(TYPE_COPY, 4, mode, TYPE_ADD, 1, 0)
        }
        check(idx == 256) { "Invalid VCDIFF code table size: $idx" }
        return table
    }

    private class AddressCache(
        private val sNear: Int = 4,
        private val sSame: Int = 3
    ) {
        private val near = IntArray(sNear)
        private val same = IntArray(sSame * 256)
        private var nextSlot = 0

        fun decode(here: Int, mode: Int, addrStream: InputStream): Int {
            val addr = when {
                mode == 0 -> readVarInt(addrStream) // VCD_SELF
                mode == 1 -> here - readVarInt(addrStream) // VCD_HERE
                mode - 2 < sNear -> near[mode - 2] + readVarInt(addrStream)
                else -> {
                    val m = mode - (2 + sNear)
                    val b = addrStream.read()
                    if (b < 0) throw EOFException("Unexpected EOF reading SAME address byte")
                    same[m * 256 + b]
                }
            }
            update(addr)
            return addr
        }

        private fun update(addr: Int) {
            if (sNear > 0) {
                near[nextSlot] = addr
                nextSlot = (nextSlot + 1) % sNear
            }
            if (sSame > 0) {
                same[addr % (sSame * 256)] = addr
            }
        }
    }

    fun applyPatch(
        baseApk: File,
        patchFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = runCatching {
        require(baseApk.exists() && baseApk.canRead()) { "Base APK not readable: ${baseApk.absolutePath}" }
        require(patchFile.exists() && patchFile.length() > 4L) { "Delta patch file is missing or empty" }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val totalPatchBytes = patchFile.length().coerceAtLeast(1L)
        val countingIn = CountingInputStream(BufferedInputStream(FileInputStream(patchFile), 64 * 1024))

        countingIn.use { input ->
            val m0 = input.read()
            val m1 = input.read()
            val m2 = input.read()
            val m3 = input.read()
            require(m0 == MAGIC_0 && m1 == MAGIC_1 && m2 == MAGIC_2 && m3 == MAGIC_3) {
                "Invalid VCDIFF/xdelta3 patch magic header"
            }

            val hdrIndicator = input.read()
            require(hdrIndicator >= 0) { "Truncated VCDIFF header" }
            if ((hdrIndicator and HDR_VCD_DECOMPRESS) != 0) {
                val secondaryId = input.read()
                require(secondaryId == 0) {
                    "Unsupported VCDIFF secondary compressor id=$secondaryId (generate patch with xdelta3 -S none)"
                }
            }
            if ((hdrIndicator and HDR_VCD_CODETABLE) != 0) {
                val customLen = readVarInt(input)
                if (customLen > 0) {
                    error("Custom VCDIFF code tables are not supported")
                }
            }
            if ((hdrIndicator and HDR_VCD_APPHEADER) != 0) {
                val appHdrLen = readVarInt(input)
                skipFully(input, appHdrLen.toLong())
            }

            RandomAccessFile(baseApk, "r").use { sourceRaf ->
                RandomAccessFile(outputFile, "rw").use { targetRaf ->
                    var totalOutputWritten = 0L

                    while (true) {
                        val winIndicator = input.read()
                        if (winIndicator < 0) break

                        val hasSource = (winIndicator and WIN_VCD_SOURCE) != 0
                        val hasTarget = (winIndicator and WIN_VCD_TARGET) != 0
                        val hasAdler32 = (winIndicator and WIN_VCD_ADLER32) != 0
                        require(!(hasSource && hasTarget)) { "Invalid VCDIFF window with both SOURCE and TARGET" }

                        var srcLen = 0
                        var srcPos = 0L
                        if (hasSource || hasTarget) {
                            srcLen = readVarInt(input)
                            srcPos = readVarLong(input)
                        }

                        // Length of delta encoding section
                        readVarInt(input)
                        val targetWindowLen = readVarInt(input)
                        val deltaIndicator = input.read()
                        require(deltaIndicator == 0) {
                            "Compressed VCDIFF delta sections are not supported (deltaIndicator=$deltaIndicator)"
                        }

                        val dataLen = readVarInt(input)
                        val instLen = readVarInt(input)
                        val addrLen = readVarInt(input)

                        val expectedAdler32 = if (hasAdler32) readIntBE(input) else 0

                        val dataBytes = readExactBytes(input, dataLen)
                        val instBytes = readExactBytes(input, instLen)
                        val addrBytes = readExactBytes(input, addrLen)

                        val sourceSegment = if (srcLen > 0) {
                            ByteArray(srcLen).also { buf ->
                                val raf = if (hasSource) sourceRaf else targetRaf
                                raf.seek(srcPos)
                                raf.readFully(buf)
                            }
                        } else {
                            ByteArray(0)
                        }

                        val targetWindow = ByteArray(targetWindowLen)
                        decodeWindow(
                            sourceSegment = sourceSegment,
                            targetWindow = targetWindow,
                            dataBytes = dataBytes,
                            instBytes = instBytes,
                            addrBytes = addrBytes
                        )

                        if (hasAdler32) {
                            val adler = Adler32().apply { update(targetWindow) }
                            val actualAdler = adler.value.toInt()
                            require(actualAdler == expectedAdler32) {
                                "VCDIFF window Adler-32 mismatch (expected=$expectedAdler32 actual=$actualAdler)"
                            }
                        }

                        targetRaf.seek(totalOutputWritten)
                        targetRaf.write(targetWindow)
                        totalOutputWritten += targetWindow.size
                        onProgress((countingIn.bytesRead.toFloat() / totalPatchBytes.toFloat()).coerceIn(0f, 0.99f))
                    }
                }
            }
        }

        require(outputFile.length() > 0L) { "Patched output file is empty" }
        onProgress(1f)
        outputFile
    }

    private fun decodeWindow(
        sourceSegment: ByteArray,
        targetWindow: ByteArray,
        dataBytes: ByteArray,
        instBytes: ByteArray,
        addrBytes: ByteArray
    ) {
        val dataIn = ByteArrayInputStream(dataBytes)
        val instIn = ByteArrayInputStream(instBytes)
        val addrIn = ByteArrayInputStream(addrBytes)
        val cache = AddressCache()

        val srcLen = sourceSegment.size
        var targetPos = 0

        fun executeOp(type: Int, inlineSize: Int, mode: Int) {
            if (type == TYPE_NOOP) return
            val size = if (inlineSize == 0) readVarInt(instIn) else inlineSize
            when (type) {
                TYPE_ADD -> {
                    var remaining = size
                    while (remaining > 0) {
                        val read = dataIn.read(targetWindow, targetPos, remaining)
                        if (read <= 0) throw EOFException("Unexpected EOF in VCDIFF data section")
                        targetPos += read
                        remaining -= read
                    }
                }
                TYPE_RUN -> {
                    val b = dataIn.read()
                    if (b < 0) throw EOFException("Unexpected EOF in VCDIFF RUN byte")
                    val byteVal = b.toByte()
                    for (i in 0 until size) {
                        targetWindow[targetPos++] = byteVal
                    }
                }
                TYPE_COPY -> {
                    val here = srcLen + targetPos
                    var addr = cache.decode(here, mode, addrIn)
                    for (i in 0 until size) {
                        val value = if (addr < srcLen) {
                            sourceSegment[addr]
                        } else {
                            targetWindow[addr - srcLen]
                        }
                        targetWindow[targetPos++] = value
                        addr++
                    }
                }
            }
        }

        while (true) {
            val code = instIn.read()
            if (code < 0) break
            val entry = defaultCodeTable[code]
            executeOp(entry.type1, entry.size1, entry.mode1)
            executeOp(entry.type2, entry.size2, entry.mode2)
        }
        require(targetPos == targetWindow.size) {
            "VCDIFF decoded size mismatch: decoded=$targetPos expected=${targetWindow.size}"
        }
    }

    /**
     * Generates a valid RFC 3284 VCDIFF delta patch for deterministic unit testing and verification.
     */
    internal fun encodeSimpleTestPatch(source: ByteArray, target: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MAGIC_0)
        out.write(MAGIC_1)
        out.write(MAGIC_2)
        out.write(MAGIC_3)
        out.write(0) // hdr_indicator

        // Build simple instruction stream: copy common prefix from source if present, then ADD remaining target bytes
        var prefixLen = 0
        val maxPrefix = minOf(source.size, target.size)
        while (prefixLen < maxPrefix && source[prefixLen] == target[prefixLen]) {
            prefixLen++
        }

        val dataSec = ByteArrayOutputStream()
        val instSec = ByteArrayOutputStream()
        val addrSec = ByteArrayOutputStream()

        if (prefixLen > 0) {
            // Code 19 = COPY size=0 (explicit varint), mode=0 (VCD_SELF)
            instSec.write(19)
            writeVarInt(instSec, prefixLen)
            writeVarInt(addrSec, 0)
        }

        val remain = target.size - prefixLen
        if (remain > 0) {
            // Code 1 = ADD size=0 (explicit varint)
            instSec.write(1)
            writeVarInt(instSec, remain)
            dataSec.write(target, prefixLen, remain)
        }

        val adler = Adler32().apply { update(target) }.value.toInt()
        val deltaBody = ByteArrayOutputStream()
        writeVarInt(deltaBody, target.size)
        deltaBody.write(0) // delta_indicator
        writeVarInt(deltaBody, dataSec.size())
        writeVarInt(deltaBody, instSec.size())
        writeVarInt(deltaBody, addrSec.size())
        deltaBody.write((adler ushr 24) and 0xFF)
        deltaBody.write((adler ushr 16) and 0xFF)
        deltaBody.write((adler ushr 8) and 0xFF)
        deltaBody.write(adler and 0xFF)
        deltaBody.write(dataSec.toByteArray())
        deltaBody.write(instSec.toByteArray())
        deltaBody.write(addrSec.toByteArray())

        val winInd = (if (source.isNotEmpty()) WIN_VCD_SOURCE else 0) or WIN_VCD_ADLER32
        out.write(winInd)
        if (source.isNotEmpty()) {
            writeVarInt(out, source.size)
            writeVarInt(out, 0)
        }
        val deltaBytes = deltaBody.toByteArray()
        writeVarInt(out, deltaBytes.size)
        out.write(deltaBytes)
        return out.toByteArray()
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        BufferedInputStream(FileInputStream(file), 64 * 1024).use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readVarInt(input: InputStream): Int = readVarLong(input).toInt()

    private fun readVarLong(input: InputStream): Long {
        var result = 0L
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Unexpected EOF reading VCDIFF varint")
            result = (result shl 7) or (b and 0x7F).toLong()
            if ((b and 0x80) == 0) return result
        }
    }

    private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
        val bytes = ByteArray(5)
        var v = value
        var count = 0
        bytes[count++] = (v and 0x7F).toByte()
        v = v ushr 7
        while (v > 0) {
            bytes[count++] = ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        for (i in count - 1 downTo 0) {
            out.write(bytes[i].toInt())
        }
    }

    private fun readIntBE(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if ((b0 or b1 or b2 or b3) < 0) throw EOFException("Unexpected EOF reading Adler-32")
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readExactBytes(input: InputStream, len: Int): ByteArray {
        val arr = ByteArray(len)
        var offset = 0
        while (offset < len) {
            val read = input.read(arr, offset, len - offset)
            if (read <= 0) throw EOFException("Unexpected EOF reading $len bytes")
            offset += read
        }
        return arr
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped <= 0L) {
                if (input.read() < 0) throw EOFException("Unexpected EOF skipping bytes")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private class CountingInputStream(private val delegate: InputStream) : InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) bytesRead += n
            return n
        }

        override fun close() = delegate.close()
    }
}
