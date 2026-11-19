package com.walsoup.ditto

import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.core.audio.VoiceFilterProcessor
import com.walsoup.ditto.core.audio.WavAudioHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Guards the bounded-memory streaming path: every filter must produce the same samples as the
 * in-memory reference, including across the 32,768-sample block seams.
 */
class StreamingFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleRate = 8_000

    @Test
    fun streamingMatchesInMemoryForEveryFilter() {
        // 70,000 samples crosses the 32,768-sample block size twice.
        val input = writeSweep("sweep_70k.wav", samples = 70_000)
        for (filter in VoiceFilter.values()) {
            assertStreamingMatches(filter, input)
        }
    }

    @Test
    fun streamingMatchesInMemoryForShortTake() {
        // A take shorter than one block must not emit a truncated or padded block.
        val input = writeSweep("sweep_1s.wav", samples = sampleRate)
        val filters = listOf(
            VoiceFilter.RAW,
            VoiceFilter.STUDIO,
            VoiceFilter.RADIO,
            VoiceFilter.CHIPMUNK,
            VoiceFilter.ECHO
        )
        for (filter in filters) {
            assertStreamingMatches(filter, input)
        }
    }

    @Test
    fun trimSilenceRemovesLeadingAndTrailingSilence() {
        val padMs = 120
        val padSamples = sampleRate * padMs / 1000 // 960

        // 200 ms silence, 300 ms tone, 500 ms silence.
        val leadSilence = sampleRate / 5
        val toneSamples = sampleRate * 3 / 10
        val trailSilence = sampleRate / 2
        val input = File(tempFolder.root, "padded.wav")
        val samples = ShortArray(leadSilence + toneSamples + trailSilence)
        for (i in 0 until toneSamples) {
            samples[leadSilence + i] =
                (sin(2.0 * PI * 440.0 * i / sampleRate) * 12_000.0).toInt().toShort()
        }
        WavAudioHelper.writeSamplesToWav(samples, input, sampleRate, 1)

        val output = File(tempFolder.root, "trimmed.wav")
        val trimmed = VoiceFilterProcessor.trimSilence(
            input, output, sampleRate, threshold = 0.02f, padMs = padMs
        )
        assertTrue("trimSilence should report success", trimmed)
        assertTrue(output.exists())

        val trimmedSamples = WavAudioHelper.read16BitPcmSamples(output)
        assertEquals(44 + trimmedSamples.size * 2, output.length().toInt())

        // Expected: the audible window found by the same rule trimSilence uses, plus one pad.
        val limit = 0.02f * 32767f
        var firstAudible = -1
        var lastAudible = -1
        for (i in samples.indices) {
            if (abs(samples[i].toInt()) > limit) {
                if (firstAudible < 0) firstAudible = i
                lastAudible = i
            }
        }
        assertTrue(firstAudible >= 0)
        val expectedStart = (firstAudible - padSamples).coerceAtLeast(0)
        val expectedEnd = (lastAudible + 1 + padSamples).coerceAtMost(samples.size)
        assertEquals(expectedEnd - expectedStart, trimmedSamples.size)

        for (i in trimmedSamples.indices) {
            assertEquals(
                "sample $i of the trimmed take",
                samples[expectedStart + i],
                trimmedSamples[i]
            )
        }

        // It really did drop the silent head and tail.
        assertTrue("leading pad should be gone", firstAudible - expectedStart == padSamples)
        assertTrue("trimmed take is shorter than the input", trimmedSamples.size < samples.size)
        assertTrue(abs(trimmedSamples.first().toInt()) < 10)
        assertTrue(abs(trimmedSamples.last().toInt()) < 10)
        val peak = trimmedSamples.maxOf { abs(it.toInt()) }
        assertTrue("the tone survived the trim, peak was $peak", peak > 10_000)
    }

    @Test
    fun trimSilenceReturnsFalseForAllSilentTake() {
        val input = File(tempFolder.root, "silent.wav")
        WavAudioHelper.writeSamplesToWav(ShortArray(sampleRate) { 0 }, input, sampleRate, 1)

        val output = File(tempFolder.root, "silent_trimmed.wav")
        val result = VoiceFilterProcessor.trimSilence(input, output, sampleRate)

        assertFalse("an all-silent take must not be trimmed", result)
        assertFalse("no output should be written", output.exists())
        assertTrue("the input take must be left alone", input.exists())
    }

    @Test
    fun trimSilenceReturnsFalseForEmptyTake() {
        val input = File(tempFolder.root, "empty.wav")
        WavAudioHelper.writeSamplesToWav(ShortArray(0), input, sampleRate, 1)

        val output = File(tempFolder.root, "empty_trimmed.wav")
        assertFalse(VoiceFilterProcessor.trimSilence(input, output, sampleRate))
        assertFalse(output.exists())
    }

    // ---------------------------------------------------------------------------------------------

    private fun assertStreamingMatches(filter: VoiceFilter, input: File) {
        val reference = File(tempFolder.root, "ref_${filter.name}.wav")
        val streamed = File(tempFolder.root, "stream_${filter.name}.wav")

        VoiceFilterProcessor.processAudio(input, reference, filter, sampleRate)
        VoiceFilterProcessor.processFileStreaming(input, streamed, filter, sampleRate)

        assertTrue("$filter should produce a file", streamed.exists())
        assertTrue("$filter output must be larger than a header", streamed.length() > 44)

        val refSamples = WavAudioHelper.read16BitPcmSamples(reference)
        val outSamples = WavAudioHelper.read16BitPcmSamples(streamed)

        assertEquals(
            "header is patched with the real length for $filter",
            44 + outSamples.size * 2,
            streamed.length().toInt()
        )
        assertEquals("length mismatch for $filter", refSamples.size, outSamples.size)

        val common = minOf(refSamples.size, outSamples.size)
        var maxDelta = 0
        var mismatches = 0
        for (i in 0 until common) {
            val delta = abs(refSamples[i].toInt() - outSamples[i].toInt())
            if (delta > maxDelta) maxDelta = delta
            if (delta > 2) mismatches++
        }
        assertEquals(
            "$filter should match the reference sample-for-sample; max delta $maxDelta",
            0,
            mismatches
        )
    }

    /**
     * Deterministic 16-bit sweep: phase-accumulated so the signal is continuous, with an amplitude
     * envelope that keeps every filter well inside the clipping range.
     */
    private fun writeSweep(name: String, samples: Int): File {
        val file = File(tempFolder.root, name)
        val data = ShortArray(samples)
        var phase = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / samples
            val freq = 180.0 + 900.0 * t
            phase += 2.0 * PI * freq / sampleRate
            val envelope = sqrt(1.0 - t) // decays, still deterministic
            data[i] = (sin(phase) * 14_000.0 * envelope).toInt().toShort()
        }
        WavAudioHelper.writeSamplesToWav(data, file, sampleRate, 1)
        return file
    }
}
