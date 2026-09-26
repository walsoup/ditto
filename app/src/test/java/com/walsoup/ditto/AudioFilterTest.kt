package com.walsoup.ditto

import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.core.audio.VoiceFilterProcessor
import com.walsoup.ditto.core.audio.WavAudioHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class AudioFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWavHeaderAndSampleReadWrite() {
        val sampleRate = 44100
        val testFile = tempFolder.newFile("test_sine.wav")

        // Generate a 1-second 440Hz sine wave (A4 note) in 16-bit PCM
        val samples = ShortArray(sampleRate) { i ->
            val angle = 2.0 * PI * 440.0 * i / sampleRate
            (sin(angle) * 16000.0).toInt().toShort()
        }

        // Write to WAV
        WavAudioHelper.writeSamplesToWav(samples, testFile, sampleRate, 1)
        assertTrue(testFile.exists())
        assertEquals(44 + (sampleRate * 2), testFile.length().toInt())

        // Read back
        val readSamples = WavAudioHelper.read16BitPcmSamples(testFile)
        assertEquals(samples.size, readSamples.size)
        // Verify first 100 samples match exactly
        for (i in 0 until 100) {
            assertEquals(samples[i], readSamples[i])
        }
    }

    @Test
    fun testAllVoiceFiltersProcessSuccessfully() {
        val sampleRate = 44100
        val inputFile = tempFolder.newFile("raw_audio.wav")

        // 0.5s test tone
        val samples = ShortArray(sampleRate / 2) { i ->
            (sin(2.0 * PI * 300.0 * i / sampleRate) * 12000.0).toInt().toShort()
        }
        WavAudioHelper.writeSamplesToWav(samples, inputFile, sampleRate, 1)

        // Process all 8 voice filters
        for (filter in VoiceFilter.values()) {
            val outputFile = File(tempFolder.root, "filter_${filter.name}.wav")
            VoiceFilterProcessor.processAudio(
                inputFile = inputFile,
                outputFile = outputFile,
                filter = filter,
                sampleRate = sampleRate
            )

            assertTrue("Filter ${filter.name} should generate output file", outputFile.exists())
            assertTrue("Output file for ${filter.name} must be > 44 bytes", outputFile.length() > 44)

            val outputSamples = WavAudioHelper.read16BitPcmSamples(outputFile)
            assertTrue("Output samples for ${filter.name} should not be empty", outputSamples.isNotEmpty())
        }
    }

    @Test
    fun testVoiceFilterMetadata() {
        assertEquals(8, VoiceFilter.values().size)
        for (filter in VoiceFilter.values()) {
            assertTrue(filter.displayName.isNotBlank())
            assertTrue(filter.description.isNotBlank())
            assertTrue(filter.iconName.isNotBlank())
        }
    }
}
