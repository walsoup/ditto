package com.walsoup.ditto

import com.walsoup.ditto.core.audio.WavAudioHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAudioHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleRate = 16000
    private val channels = 1
    private val sampleCount = 1000

    private fun testSamples(): ShortArray = ShortArray(sampleCount) { i ->
        ((i * 37) % 20000 - 10000).toShort()
    }

    private fun writePcm(file: File, samples: ShortArray) {
        val payload = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) payload.putShort(sample)
        FileOutputStream(file).use { it.write(payload.array()) }
    }

    private fun readHeader(file: File): ByteArray {
        val header = ByteArray(44)
        FileInputStream(file).use { assertEquals(44, it.read(header)) }
        return header
    }

    private fun intAt(header: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(header, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    @Test
    fun convertsPcmToWavWithCorrectHeaderAndStreamsEverySample() {
        val samples = testSamples()
        val pcm = tempFolder.newFile("take.pcm")
        writePcm(pcm, samples)
        val wav = File(tempFolder.root, "take.wav")

        WavAudioHelper.convertPcmToWav(
            pcmFile = pcm,
            wavFile = wav,
            sampleRate = sampleRate,
            channels = channels.toShort(),
            bitsPerSample = 16
        )

        val payloadBytes = samples.size * 2
        assertEquals((payloadBytes + 44).toLong(), wav.length())

        val header = readHeader(wav)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))
        assertEquals(payloadBytes + 36, intAt(header, 4))          // RIFF chunk size
        assertEquals(16, intAt(header, 16))                        // fmt chunk size (PCM)
        assertEquals(sampleRate, intAt(header, 24))
        assertEquals(sampleRate * 2, intAt(header, 28))            // byte rate
        assertEquals(payloadBytes, intAt(header, 40))              // data chunk size

        val reader = WavAudioHelper.PcmBlockReader(wav, sampleRate, channels)
        try {
            assertEquals(samples.size.toLong(), reader.totalSamples)

            // Deliberately choose a block size that does not divide the payload so we cross
            // several refills, including an odd byte boundary.
            val block = ShortArray(97)
            val collected = ShortArray(samples.size)
            var total = 0
            while (true) {
                val read = reader.read(block, block.size)
                if (read < 0) break
                assertTrue("read() must not exceed the requested block", read <= block.size)
                for (i in 0 until read) {
                    collected[total + i] = block[i]
                }
                total += read
            }
            assertEquals(samples.size, total)
            for (i in samples.indices) {
                assertEquals("sample $i", samples[i], collected[i])
            }
        } finally {
            reader.close()
        }
    }

    @Test
    fun readsRawPcmWithoutHeader() {
        val samples = testSamples()
        val raw = tempFolder.newFile("raw.pcm")
        writePcm(raw, samples)

        val reader = WavAudioHelper.PcmBlockReader(raw, sampleRate, channels)
        try {
            assertEquals(samples.size.toLong(), reader.totalSamples)
            val out = ShortArray(samples.size)
            val block = ShortArray(128)
            var offset = 0
            while (offset < out.size) {
                val read = reader.read(block, block.size)
                if (read < 0) break
                for (i in 0 until read) {
                    out[offset + i] = block[i]
                }
                offset += read
            }
            assertEquals(samples.size, offset)
            for (i in samples.indices) {
                assertEquals(samples[i], out[i])
            }
        } finally {
            reader.close()
        }
    }

    @Test
    fun writeHeaderOnlyThenPatchHeaderDescribesTheFinishedFile() {
        val samples = testSamples()
        val wav = File(tempFolder.root, "streamed.wav")

        val out = WavAudioHelper.writeHeaderOnly(wav, sampleRate, channels)
        try {
            val payload = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) payload.putShort(sample)
            out.write(payload.array())
        } finally {
            out.close()
        }
        assertEquals((samples.size * 2 + 44).toLong(), wav.length())

        // Header still claims zero data until it is patched.
        assertEquals(0, intAt(readHeader(wav), 40))

        WavAudioHelper.patchHeader(wav, sampleRate, channels)

        val header = readHeader(wav)
        assertEquals(samples.size * 2, intAt(header, 40))
        assertEquals(samples.size * 2 + 36, intAt(header, 4))
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))

        val reader = WavAudioHelper.PcmBlockReader(wav, sampleRate, channels)
        try {
            assertEquals(samples.size.toLong(), reader.totalSamples)
            val out2 = ShortArray(samples.size)
            val read = reader.read(out2, out2.size)
            assertEquals(samples.size, read)
            for (i in samples.indices) {
                assertEquals(samples[i], out2[i])
            }
            assertEquals(-1, reader.read(out2, out2.size))
        } finally {
            reader.close()
        }
    }

    @Test
    fun emptyPayloadReadsImmediatelyAtEof() {
        val empty = tempFolder.newFile("empty.pcm")
        val reader = WavAudioHelper.PcmBlockReader(empty, sampleRate, channels)
        try {
            assertEquals(0L, reader.totalSamples)
            assertEquals(-1, reader.read(ShortArray(16), 16))
        } finally {
            reader.close()
        }
    }
}
