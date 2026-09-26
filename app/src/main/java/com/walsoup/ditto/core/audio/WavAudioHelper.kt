package com.walsoup.ditto.core.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
}
