package com.walsoup.ditto.core.audio

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val WAV_HEADER_BYTES = 44
private const val PCM_BITS_PER_SAMPLE = 16
private const val PCM_READ_CHUNK_BYTES = 8192

object WavAudioHelper {

    /**
     * Converts a raw 16-bit PCM file into a standard WAV file by writing the 44-byte RIFF header.
     */
    fun convertPcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int = 44100,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ) {
        val pcmDataLength = pcmFile.length()
        val totalDataLen = pcmDataLength + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8)
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        FileOutputStream(wavFile).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF chunk descriptor
            header.put("RIFF".toByteArray())
            header.putInt(totalDataLen.toInt())
            header.put("WAVE".toByteArray())

            // "fmt " sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1) // AudioFormat 1 = PCM
            header.putShort(channels)
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign)
            header.putShort(bitsPerSample)

            // "data" sub-chunk
            header.put("data".toByteArray())
            header.putInt(pcmDataLength.toInt())

            out.write(header.array())

            // Stream PCM payload
            FileInputStream(pcmFile).use { input ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    /**
     * Reads 16-bit PCM samples (little-endian shorts) from a WAV file or raw PCM file.
     */
    fun read16BitPcmSamples(wavOrPcmFile: File): ShortArray {
        val totalBytes = wavOrPcmFile.length().toInt()
        val hasWavHeader = if (totalBytes >= 44) {
            val probe = ByteArray(4)
            FileInputStream(wavOrPcmFile).use { it.read(probe) }
            String(probe) == "RIFF"
        } else false

        val headerOffset = if (hasWavHeader) 44 else 0
        val pcmByteCount = totalBytes - headerOffset
        val sampleCount = pcmByteCount / 2
        val samples = ShortArray(sampleCount)

        FileInputStream(wavOrPcmFile).use { input ->
            if (headerOffset > 0) {
                input.skip(headerOffset.toLong())
            }
            val byteBuffer = ByteArray(4096)
            var sampleIndex = 0
            var bytesRead: Int
            while (input.read(byteBuffer).also { bytesRead = it } != -1 && sampleIndex < sampleCount) {
                for (i in 0 until bytesRead - 1 step 2) {
                    if (sampleIndex < sampleCount) {
                        val low = byteBuffer[i].toInt() and 0xFF
                        val high = byteBuffer[i + 1].toInt()
                        samples[sampleIndex++] = ((high shl 8) or low).toShort()
                    }
                }
            }
        }
        return samples
    }

    /**
     * Writes 16-bit PCM samples into a complete, standard WAV file.
     */
    fun writeSamplesToWav(
        samples: ShortArray,
        wavFile: File,
        sampleRate: Int = 44100,
        channels: Short = 1
    ) {
        val bitsPerSample: Short = 16
        val pcmDataLength = samples.size * 2
        val totalDataLen = pcmDataLength + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8)
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        FileOutputStream(wavFile).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray())

            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(channels)
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign)
            header.putShort(bitsPerSample)

            header.put("data".toByteArray())
            header.putInt(pcmDataLength)

            out.write(header.array())

            // Write samples in chunks
            val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                if (buffer.remaining() < 2) {
                    out.write(buffer.array(), 0, buffer.position())
                    buffer.clear()
                }
                buffer.putShort(sample)
            }
            if (buffer.position() > 0) {
                out.write(buffer.array(), 0, buffer.position())
            }
        }
    }

    /**
     * Writes a 44-byte WAV header with a zero-length data chunk and hands back the still-open
     * stream, positioned right after the header. Append PCM bytes, close, then call
     * [patchHeader] so the sizes describe the finished file.
     */
    fun writeHeaderOnly(file: File, sampleRate: Int, channels: Int): FileOutputStream {
        val out = FileOutputStream(file)
        out.write(buildHeader(sampleRate, channels, 0L))
        return out
    }

    /**
     * Rewrites the 44-byte header of [file] from its current length. Safe to call twice; does
     * nothing when the file is too short to hold a header.
     */
    fun patchHeader(file: File, sampleRate: Int, channels: Int) {
        val length = file.length()
        if (length < WAV_HEADER_BYTES) return
        val dataLength = (length - WAV_HEADER_BYTES).coerceAtLeast(0L)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0L)
            raf.write(buildHeader(sampleRate, channels, dataLength))
        }
    }

    private fun buildHeader(sampleRate: Int, channels: Int, dataLengthBytes: Long): ByteArray {
        val channelCount = channels.coerceAtLeast(1)
        val byteRate = sampleRate * channelCount * PCM_BITS_PER_SAMPLE / 8
        val blockAlign = channelCount * PCM_BITS_PER_SAMPLE / 8
        val dataLength = dataLengthBytes.coerceIn(0L, 0xFFFF_FFFFL)

        val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((dataLength + 36L).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))

        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channelCount.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(PCM_BITS_PER_SAMPLE.toShort())

        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataLength.toInt())
        return header.array()
    }

    /**
     * Streams 16-bit little-endian samples out of a WAV or raw PCM file in bounded memory.
     *
     * [totalSamples] counts interleaved samples across all channels. [read] fills the caller's
     * array and returns the number of samples written, or -1 once the payload is exhausted.
     */
    class PcmBlockReader(
        file: File,
        val sampleRate: Int,
        val channels: Int
    ) : Closeable {

        private val channelCount = channels.coerceAtLeast(1)
        private val blockAlign = (channelCount * PCM_BITS_PER_SAMPLE / 8).coerceAtLeast(2)
        private val byteBuffer = ByteArray(PCM_READ_CHUNK_BYTES)
        private val input: FileInputStream

        val totalSamples: Long

        private var remainingBytes: Long
        private var pendingOddByte: Int = -1

        init {
            val length = file.length()
            val offset = resolvePcmDataOffset(file).coerceIn(0L, length)
            val payloadBytes = (length - offset).coerceAtLeast(0L)
            remainingBytes = payloadBytes - (payloadBytes % blockAlign)
            totalSamples = remainingBytes / blockAlign * channelCount

            input = FileInputStream(file)
            try {
                input.channel.position(offset)
            } catch (_: IOException) {
                runCatching { input.skip(offset) }
            }
        }

        fun read(into: ShortArray, maxSamples: Int): Int {
            val target = minOf(maxSamples, into.size)
            if (target <= 0) return 0

            var produced = 0
            while (produced < target) {
                val wantedSamples = minOf(target - produced, byteBuffer.size / 2)
                val bytes = fill(byteBuffer, wantedSamples * 2)
                if (bytes < 2) break
                val samples = bytes / 2
                for (i in 0 until samples) {
                    val low = byteBuffer[i * 2].toInt() and 0xFF
                    val high = byteBuffer[i * 2 + 1].toInt()
                    into[produced + i] = ((high shl 8) or low).toShort()
                }
                produced += samples
            }
            return if (produced == 0) -1 else produced
        }

        private fun fill(buffer: ByteArray, count: Int): Int {
            var filled = 0
            if (pendingOddByte >= 0) {
                buffer[0] = pendingOddByte.toByte()
                pendingOddByte = -1
                filled = 1
            }
            while (filled < count && remainingBytes > 0L) {
                val request = minOf((count - filled).toLong(), remainingBytes).toInt()
                val readCount = try {
                    input.read(buffer, filled, request)
                } catch (_: IOException) {
                    -1
                }
                if (readCount <= 0) {
                    remainingBytes = 0L
                    break
                }
                filled += readCount
                remainingBytes -= readCount.toLong()
            }
            if (filled % 2 == 1) {
                pendingOddByte = buffer[filled - 1].toInt() and 0xFF
                filled -= 1
            }
            return filled
        }

        override fun close() {
            try {
                input.close()
            } catch (_: IOException) {
                // Nothing useful to do while tearing down a read stream.
            }
        }
    }
}

/** Returns the byte offset where PCM data starts, honouring RIFF chunks when present. */
private fun resolvePcmDataOffset(file: File): Long {
    val length = file.length()
    if (length < 12L) return 0L

    return try {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(12)
            raf.readFully(riff)
            val isWav = String(riff, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(riff, 8, 4, Charsets.US_ASCII) == "WAVE"
            if (!isWav) return@use 0L

            var position = 12L
            val chunk = ByteArray(8)
            while (position + 8L <= length) {
                raf.seek(position)
                if (raf.read(chunk) < 8) break
                val id = String(chunk, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(chunk, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .toLong() and 0xFFFF_FFFFL
                val body = position + 8L
                if (body >= length) break
                if (id == "data") return@use body
                if (size <= 0L) break
                val next = body + size + (size and 1L)
                if (next <= position) break
                position = next
            }
            minOf(WAV_HEADER_BYTES.toLong(), length)
        }
    } catch (_: IOException) {
        0L
    }
}
