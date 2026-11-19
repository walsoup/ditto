package com.walsoup.ditto.core.audio

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Voice filters for Ditto.
 *
 * Two entry points exist on purpose:
 *
 * - [processAudio] loads the whole take into memory. It is the reference implementation and is
 *   kept for unit tests only.
 * - [processFileStreaming] is what production code calls. It processes the take in 32,768-sample
 *   blocks (~64 KB of PCM in flight) and carries every filter's state across block boundaries, so
 *   memory stays flat no matter how long the recording is.
 *
 * Both paths share the per-sample math through the small `*State` classes below, which is what
 * keeps their output identical.
 */
object VoiceFilterProcessor {

    /** One streaming block: 32,768 samples = 64 KB of 16-bit PCM. */
    private const val BLOCK_SAMPLES = 32_768

    /** Sentinel returned by the streaming sample cursor when the take is exhausted. */
    private const val EOF = Int.MIN_VALUE

    // ---------------------------------------------------------------------------------------------
    // Reference (in-memory) path — used by the unit tests.
    // ---------------------------------------------------------------------------------------------

    fun processAudio(
        inputFile: File,
        outputFile: File,
        filter: VoiceFilter,
        sampleRate: Int = AudioConfig.SAMPLE_RATE
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

    // ---------------------------------------------------------------------------------------------
    // Streaming path — bounded memory, state carried across blocks.
    // ---------------------------------------------------------------------------------------------

    /**
     * Applies [filter] to [inputFile] and writes [outputFile] block by block.
     *
     * Peak heap use is a couple of 32,768-sample buffers (a few hundred KB) regardless of the
     * length of the take. The result matches [processAudio] within float rounding.
     */
    fun processFileStreaming(
        inputFile: File,
        outputFile: File,
        filter: VoiceFilter,
        sampleRate: Int = AudioConfig.SAMPLE_RATE
    ) {
        PcmWavSink(outputFile, sampleRate).use { sink ->
            WavAudioHelper.PcmBlockReader(inputFile, sampleRate, AudioConfig.CHANNELS).use { source ->
                when (filter) {
                    VoiceFilter.RAW -> streamCopy(source, sink)
                    VoiceFilter.STUDIO -> streamStudio(source, sink)
                    VoiceFilter.ROBOT -> streamRobot(source, sink, sampleRate)
                    VoiceFilter.CHIPMUNK -> streamResample(source, sink, source.totalSamples, 1.45f)
                    VoiceFilter.DEEP_TITAN ->
                        streamResample(source, sink, source.totalSamples, 0.72f)
                    VoiceFilter.FAST_RANT ->
                        streamResample(source, sink, source.totalSamples, 1.35f)
                    VoiceFilter.RADIO -> streamRadio(source, sink)
                    VoiceFilter.ECHO -> streamEcho(source, sink, sampleRate)
                }
            }
        }
    }

    /**
     * Trims leading and trailing near-silence from [inputFile] into [outputFile].
     *
     * Two streaming passes: the first locates the first and last sample louder than
     * `threshold * 32767`, the second copies that range plus `padMs` of padding on each side.
     *
     * @return `false` when the entire take is below the threshold. In that case nothing is written
     *   and the caller should keep the untrimmed file.
     */
    fun trimSilence(
        inputFile: File,
        outputFile: File,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        threshold: Float = 0.02f,
        padMs: Int = 120
    ): Boolean {
        val limit = threshold.coerceIn(0f, 1f) * 32767f
        val range = audibleRange(inputFile, sampleRate, limit) // pass 1
        val first = range[0]
        val last = range[1]
        if (first < 0L || last < first) return false

        val pad = padMs.coerceAtLeast(0).toLong() * sampleRate / 1000L
        val start = (first - pad).coerceAtLeast(0L)
        val endExclusive = (last + 1L + pad).coerceAtMost(range[2])
        val count = endExclusive - start
        if (count <= 0L) return false

        // pass 2: copy the window (plus padding) into a fresh WAV.
        val buffer = ShortArray(BLOCK_SAMPLES)
        PcmWavSink(outputFile, sampleRate).use { sink ->
            WavAudioHelper.PcmBlockReader(inputFile, sampleRate, AudioConfig.CHANNELS).use { source ->
                discardSamples(source, start, buffer)
                var remaining = count
                while (remaining > 0L) {
                    val want = minOf(remaining, BLOCK_SAMPLES.toLong()).toInt()
                    val read = source.read(buffer, want)
                    if (read <= 0) break
                    sink.write(buffer, read)
                    remaining -= read
                }
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------
    // Per-sample filter math (shared by both paths).
    // ---------------------------------------------------------------------------------------------

    /**
     * Studio vocal polish: low-end warmth, presence boost, and dynamic compression.
     * Stateful: the two one-pole followers must survive block boundaries.
     */
    private class StudioState {
        private var prevLow = 0.0
        private var prevHigh = 0.0

        fun process(sample: Short): Short {
            val s = sample.toDouble() / 32768.0

            // Low-frequency warmth boost
            prevLow += 0.05 * (s - prevLow)
            val warm = s + (prevLow * 0.4)

            // High-frequency presence
            val diff = s - prevHigh
            prevHigh += 0.3 * diff
            val present = warm + (diff * 0.35)

            // Soft-knee dynamic compression / limiting
            val compressed = tanh(present * 1.3) * 0.85
            return (compressed * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
    }

    /**
     * Cyber Robot: ring-modulation carrier + metallic bitcrush.
     * Stateful: the carrier phase comes from the running sample index so it never restarts at a
     * block seam.
     */
    private class RobotState(sampleRate: Int) {
        private val twoPiF = 2.0 * PI * CARRIER_FREQ / sampleRate
        private var index = 0L

        fun process(sample: Short): Short {
            val carrier = sin(twoPiF * index.toDouble())
            index++

            val s = sample.toDouble() / 32768.0
            val modulated = s * (0.35 + 0.65 * carrier)

            // 6-bit quantization / bitcrusher effect for lo-fi robot crunch
            val quantized = (modulated * 32.0).toInt() / 32.0

            return (quantized * 32767.0 * 1.2).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
    }

    /**
     * Walkie-talkie bandpass + drive + crackle.
     *
     * The three one-pole filter states must survive block boundaries. The crackle is a fixed-seed
     * xorshift sequence rather than `Math.random()`, so a preview and an export of the same take
     * sound identical (and so the filter can be block-processed at all).
     */
    private class RadioState {
        private var lp1 = 0.0
        private var lp2 = 0.0
        private var hp = 0.0
        private var noiseState = 0x2545F491

        private fun crackle(): Double {
            var x = noiseState
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            noiseState = x
            val unit = (x ushr 8) / 16_777_216.0 // [0, 1)
            return (unit - 0.5) * 0.03
        }

        fun process(sample: Short): Short {
            val s = sample.toDouble() / 32768.0

            // High pass filter (remove bass under 400Hz)
            hp += 0.08 * (s - hp)
            val bassCut = s - hp

            // Low pass filter (remove treble above 3.2kHz)
            lp1 += 0.28 * (bassCut - lp1)
            lp2 += 0.28 * (lp1 - lp2)
            val bandpassed = lp2

            // Hard/soft overdrive distortion typical of small walkie-talkie speaker
            val overdriven = tanh(bandpassed * 3.2) * 0.75

            val mixed = overdriven + crackle()

            return (mixed * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
    }

    /**
     * Cosmic Echo: circular delay buffer with feedback decay.
     * Stateful: the delay buffer and its write index are the filter, so they persist across blocks.
     */
    private class EchoState(sampleRate: Int) {
        val delaySamples = ((sampleRate * ECHO_DELAY_MS) / 1000).coerceAtLeast(1)
        private val buffer = DoubleArray(delaySamples)
        private var bufferIndex = 0

        /** Extra samples the caller must push through [process] with a dry value of 0.0. */
        val tailSamples: Int get() = delaySamples * 2

        fun process(dry: Double): Short {
            val delayed = buffer[bufferIndex]
            val mixed = dry + delayed * ECHO_WET
            buffer[bufferIndex] = dry + delayed * ECHO_FEEDBACK
            bufferIndex = (bufferIndex + 1) % delaySamples
            return (mixed * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
    }

    private fun applyStudioFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val state = StudioState()
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            out[i] = state.process(samples[i])
        }
        return out
    }

    private fun applyRobotFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val state = RobotState(sampleRate)
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            out[i] = state.process(samples[i])
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

    private fun applyRadioFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val state = RadioState()
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            out[i] = state.process(samples[i])
        }
        return out
    }

    private fun applyEchoFilter(samples: ShortArray, sampleRate: Int): ShortArray {
        val state = EchoState(sampleRate)
        val extraTail = state.tailSamples
        val out = ShortArray(samples.size + extraTail)
        for (i in out.indices) {
            val dry = if (i < samples.size) samples[i].toDouble() / 32768.0 else 0.0
            out[i] = state.process(dry)
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // Streaming loops (one per filter shape).
    // ---------------------------------------------------------------------------------------------

    private fun streamCopy(source: WavAudioHelper.PcmBlockReader, sink: PcmWavSink) {
        val buffer = ShortArray(BLOCK_SAMPLES)
        while (true) {
            val read = source.read(buffer, BLOCK_SAMPLES)
            if (read <= 0) break
            sink.write(buffer, read)
        }
    }

    private fun streamStudio(source: WavAudioHelper.PcmBlockReader, sink: PcmWavSink) {
        val state = StudioState()
        val buffer = ShortArray(BLOCK_SAMPLES)
        while (true) {
            val read = source.read(buffer, BLOCK_SAMPLES)
            if (read <= 0) break
            for (i in 0 until read) {
                buffer[i] = state.process(buffer[i])
            }
            sink.write(buffer, read)
        }
    }

    private fun streamRobot(
        source: WavAudioHelper.PcmBlockReader,
        sink: PcmWavSink,
        sampleRate: Int
    ) {
        val state = RobotState(sampleRate)
        val buffer = ShortArray(BLOCK_SAMPLES)
        while (true) {
            val read = source.read(buffer, BLOCK_SAMPLES)
            if (read <= 0) break
            for (i in 0 until read) {
                buffer[i] = state.process(buffer[i])
            }
            sink.write(buffer, read)
        }
    }

    private fun streamRadio(source: WavAudioHelper.PcmBlockReader, sink: PcmWavSink) {
        val state = RadioState()
        val buffer = ShortArray(BLOCK_SAMPLES)
        while (true) {
            val read = source.read(buffer, BLOCK_SAMPLES)
            if (read <= 0) break
            for (i in 0 until read) {
                buffer[i] = state.process(buffer[i])
            }
            sink.write(buffer, read)
        }
    }

    private fun streamEcho(
        source: WavAudioHelper.PcmBlockReader,
        sink: PcmWavSink,
        sampleRate: Int
    ) {
        val state = EchoState(sampleRate)
        val buffer = ShortArray(BLOCK_SAMPLES)
        while (true) {
            val read = source.read(buffer, BLOCK_SAMPLES)
            if (read <= 0) break
            for (i in 0 until read) {
                buffer[i] = state.process(buffer[i].toDouble() / 32768.0)
            }
            sink.write(buffer, read)
        }
        // Flush the echo tail exactly like applyEchoFilter does (2 * delaySamples with dry 0.0).
        var remaining = state.tailSamples
        while (remaining > 0) {
            val n = minOf(remaining, BLOCK_SAMPLES)
            for (i in 0 until n) {
                buffer[i] = state.process(0.0)
            }
            sink.write(buffer, n)
            remaining -= n
        }
    }

    /**
     * Resamples with the same arithmetic as [applyPitchResample], but walks the source with a
     * one-sample lookahead instead of materialising it, so the fractional source position and the
     * interpolation neighbour survive block boundaries.
     */
    private fun streamResample(
        source: WavAudioHelper.PcmBlockReader,
        sink: PcmWavSink,
        totalSamples: Long,
        factor: Float
    ) {
        if (factor <= 0f) {
            streamCopy(source, sink)
            return
        }
        val outLength = (totalSamples / factor).toInt()
        if (outLength <= 0) return

        val cursor = SampleCursor(source)
        val buffer = ShortArray(BLOCK_SAMPLES)
        var filled = 0

        var current = cursor.next()
        var currentIndex = 0L
        var next = if (current == EOF) EOF else cursor.next()

        for (i in 0 until outLength) {
            val srcIndex = i * factor
            val base = srcIndex.toInt()
            val fraction = srcIndex - base

            while (currentIndex < base) {
                if (next == EOF) break
                current = next
                currentIndex++
                next = cursor.next()
            }

            val sample: Short = when {
                current == EOF -> 0
                next == EOF -> current.toShort()
                else -> {
                    val s0 = current.toFloat()
                    val s1 = next.toFloat()
                    (s0 + fraction * (s1 - s0)).coerceIn(-32768f, 32767f).toInt().toShort()
                }
            }

            buffer[filled++] = sample
            if (filled == BLOCK_SAMPLES) {
                sink.write(buffer, filled)
                filled = 0
            }
        }
        if (filled > 0) sink.write(buffer, filled)
    }

    /**
     * Single streaming pass that returns `[firstAudible, lastAudible, totalSamples]`, or `-1` for
     * the first two when every sample is at or below [limit].
     */
    private fun audibleRange(inputFile: File, sampleRate: Int, limit: Float): LongArray {
        val buffer = ShortArray(BLOCK_SAMPLES)
        var first = -1L
        var last = -1L
        var index = 0L
        WavAudioHelper.PcmBlockReader(inputFile, sampleRate, AudioConfig.CHANNELS).use { source ->
            while (true) {
                val read = source.read(buffer, BLOCK_SAMPLES)
                if (read <= 0) break
                for (i in 0 until read) {
                    if (abs(buffer[i].toInt()) > limit) {
                        if (first < 0L) first = index + i
                        last = index + i
                    }
                }
                index += read
            }
        }
        return longArrayOf(first, last, index)
    }

    /** Reads and throws away [count] samples; keeps the caller's block buffer, no seeking. */
    private fun discardSamples(
        source: WavAudioHelper.PcmBlockReader,
        count: Long,
        buffer: ShortArray
    ) {
        var remaining = count
        while (remaining > 0L) {
            val want = minOf(remaining, BLOCK_SAMPLES.toLong()).toInt()
            val read = source.read(buffer, want)
            if (read <= 0) break
            remaining -= read
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Streaming plumbing.
    // ---------------------------------------------------------------------------------------------

    /** Sequential sample access with one sample of lookahead, on top of a block reader. */
    private class SampleCursor(private val source: WavAudioHelper.PcmBlockReader) {
        private val block = ShortArray(BLOCK_SAMPLES)
        private var pos = 0
        private var len = 0

        /** Next sample as an Int in -32768..32767, or [EOF]. */
        fun next(): Int {
            if (pos >= len) {
                val read = source.read(block, BLOCK_SAMPLES)
                pos = 0
                len = if (read > 0) read else 0
                if (len == 0) return EOF
            }
            return block[pos++].toInt()
        }
    }

    /**
     * Incremental WAV writer: appends PCM blocks and patches the RIFF/data lengths on close, so the
     * header always matches the samples actually produced.
     */
    private class PcmWavSink(private val file: File, private val sampleRate: Int) : Closeable {
        private val output: FileOutputStream =
            WavAudioHelper.writeHeaderOnly(file, sampleRate, AudioConfig.CHANNELS)
        private val byteBuffer = ByteArray(BLOCK_SAMPLES * 2)
        private var bytePos = 0
        private var closed = false

        fun write(samples: ShortArray, count: Int) {
            for (i in 0 until count) {
                if (bytePos + 2 > byteBuffer.size) flushBytes()
                val value = samples[i].toInt()
                byteBuffer[bytePos++] = (value and 0xFF).toByte()
                byteBuffer[bytePos++] = ((value shr 8) and 0xFF).toByte()
            }
        }

        private fun flushBytes() {
            if (bytePos > 0) {
                output.write(byteBuffer, 0, bytePos)
                bytePos = 0
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            flushBytes()
            output.flush()
            output.close()
            WavAudioHelper.patchHeader(file, sampleRate, AudioConfig.CHANNELS)
        }
    }

    private const val CARRIER_FREQ = 58.0
    private const val ECHO_DELAY_MS = 230
    private const val ECHO_FEEDBACK = 0.48
    private const val ECHO_WET = 0.42
}
