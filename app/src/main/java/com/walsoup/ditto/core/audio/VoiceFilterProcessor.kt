package com.walsoup.ditto.core.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

object VoiceFilterProcessor {

    fun processAudio(
        inputFile: File,
        outputFile: File,
        filter: VoiceFilter,
        sampleRate: Int = 44100
    ) {
        val inputSamples = WavAudioHelper.read16BitPcmSamples(inputFile)
        val filteredSamples = when (filter) {
            VoiceFilter.RAW -> inputSamples
            VoiceFilter.STUDIO -> applyStudioFilter(inputSamples, sampleRate)
            VoiceFilter.ROBOT -> applyRobotFilter(inputSamples, sampleRate)
            VoiceFilter.CHIPMUNK -> applyPitchResample(inputSamples, 1.45f)
            VoiceFilter.DEEP_TITAN -> applyPitchResample(inputSamples, 0.72f)
            VoiceFilter.RADIO -> applyRadioFilter(inputSamples, sampleRate)
            VoiceFilter.ECHO -> applyEchoFilter(inputSamples, sampleRate)
            VoiceFilter.FAST_RANT -> applyPitchResample(inputSamples, 1.35f)
        }
        WavAudioHelper.writeSamplesToWav(filteredSamples, outputFile, sampleRate)
    }

    /**
     * Studio vocal polish: Low-end warmth, presence boost, and dynamic compression.
     */
    private fun applyStudioFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val out = ShortArray(samples.size)
        // Simple IIR low-shelf and high-shelf simulation
        var prevLow = 0.0
        var prevHigh = 0.0

        for (i in samples.indices) {
            val s = samples[i].toDouble() / 32768.0

            // Low-frequency warmth boost
            prevLow += 0.05 * (s - prevLow)
            val warm = s + (prevLow * 0.4)

            // High-frequency presence
            val diff = s - prevHigh
            prevHigh += 0.3 * diff
            val present = warm + (diff * 0.35)

            // Soft-knee dynamic compression / limiting
            val compressed = tanh(present * 1.3) * 0.85
            val clamped = (compressed * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
            out[i] = clamped
        }
        return out
    }

    /**
     * Cyber Robot: Ring modulation carrier oscillator + metallic bitcrush.
     */
    private fun applyRobotFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val out = ShortArray(samples.size)
        val carrierFreq = 58.0 // 58 Hz carrier wave for robotic metallic timbre
        val twoPiF = 2.0 * PI * carrierFreq / sampleRate

        for (i in samples.indices) {
            val carrier = sin(twoPiF * i)
            val s = samples[i].toDouble() / 32768.0

            // Ring modulation
            val modulated = s * (0.35 + 0.65 * carrier)

            // 6-bit quantization / bitcrusher effect for lo-fi robot crunch
            val quantized = (modulated * 32.0).toInt() / 32.0

            val clamped = (quantized * 32767.0 * 1.2).coerceIn(-32768.0, 32767.0).toInt().toShort()
            out[i] = clamped
        }
        return out
    }

    /**
     * Helium / Titan / Speed: Linear interpolation resampling.
     * factor > 1.0 = higher pitch & faster (chipmunk / fast rant)
     * factor < 1.0 = lower pitch & slower (deep titan)
     */
    private fun applyPitchResample(samples: ShortArray, factor: Float): ShortArray {
        if (samples.isEmpty() || factor <= 0f) return samples
        val newLength = (samples.size / factor).toInt()
        val out = ShortArray(newLength)

        for (i in 0 until newLength) {
            val srcIndex = i * factor
            val baseIndex = srcIndex.toInt()
            val fraction = srcIndex - baseIndex

            if (baseIndex + 1 < samples.size) {
                val s0 = samples[baseIndex].toFloat()
                val s1 = samples[baseIndex + 1].toFloat()
                val interpolated = s0 + fraction * (s1 - s0)
                out[i] = interpolated.coerceIn(-32768f, 32767f).toInt().toShort()
            } else if (baseIndex < samples.size) {
                out[i] = samples[baseIndex]
            }
        }
        return out
    }

    /**
     * Walkie-Talkie / Vintage Radio: Bandpass filter (cutting sub-bass < 400Hz and treble > 3.4kHz),
     * overdrive saturation, and subtle noise.
     */
    private fun applyRadioFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val out = ShortArray(samples.size)
        var lp1 = 0.0
        var lp2 = 0.0
        var hp = 0.0

        for (i in samples.indices) {
            val s = samples[i].toDouble() / 32768.0

            // High pass filter (remove bass under 400Hz)
            hp += 0.08 * (s - hp)
            val bassCut = s - hp

            // Low pass filter (remove treble above 3.2kHz)
            lp1 += 0.28 * (bassCut - lp1)
            lp2 += 0.28 * (lp1 - lp2)
            val bandpassed = lp2

            // Hard/soft overdrive distortion typical of small walkie-talkie speaker
            val overdriven = tanh(bandpassed * 3.2) * 0.75

            // Subtle crackle
            val noise = (Math.random() - 0.5) * 0.03
            val mixed = overdriven + noise

            val clamped = (mixed * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
            out[i] = clamped
        }
        return out
    }

    /**
     * Cosmic Echo: Circular delay buffer with feedback decay.
     */
    private fun applyEchoFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val delayMs = 230
        val delaySamples = (sampleRate * delayMs) / 1000
        val feedback = 0.48
        val wet = 0.42

        val buffer = DoubleArray(delaySamples)
        var bufferIndex = 0

        // Extend output slightly to capture echo tail
        val extraTail = delaySamples * 2
        val out = ShortArray(samples.size + extraTail)

        for (i in out.indices) {
            val dry = if (i < samples.size) samples[i].toDouble() / 32768.0 else 0.0
            val delayed = buffer[bufferIndex]

            val mixed = dry + delayed * wet
            buffer[bufferIndex] = dry + delayed * feedback

            bufferIndex = (bufferIndex + 1) % delaySamples

            val clamped = (mixed * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
            out[i] = clamped
        }
        return out
    }
}
