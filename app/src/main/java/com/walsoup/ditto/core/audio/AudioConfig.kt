package com.walsoup.ditto.core.audio

/**
 * Single source of truth for the capture/encode parameters shared by the audio pipeline.
 *
 * Anything that captures, filters or encodes audio should read its sample rate, channel count and
 * bit rate from here so a recording stays consistent from the microphone to the exported file.
 */
object AudioConfig {
    const val SAMPLE_RATE = 44100
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = 2
    const val AAC_BIT_RATE = 64_000
    const val AAC_FALLBACK_BIT_RATE = 32_000
    const val MAX_DURATION_MS = 3_600_000L      // 1 hour hard cap
    const val LEVELS_PER_TAKE = 64
    const val WAV_HEADER_BYTES = 44
}
