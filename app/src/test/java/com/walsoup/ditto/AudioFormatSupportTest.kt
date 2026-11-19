package com.walsoup.ditto

import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.core.audio.isEncodedContainerExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.1 review findings:
 *
 * 1. Re-export corruption: an already-encoded M4A must never be treated as raw PCM again.
 *    The export/encode pipeline guards on [isEncodedContainerExtension].
 * 2. AAC-fallback mislabelling: a failed AAC export falls back to WAV, so the committed
 *    format is derived from the actual file extension via [AudioFormatType.forFileName]
 *    instead of trusting the requested format.
 */
class AudioFormatSupportTest {

    @Test
    fun `forFileName maps m4a extension to M4A`() {
        assertEquals(AudioFormatType.M4A, AudioFormatType.forFileName("take.m4a"))
        assertEquals(AudioFormatType.M4A, AudioFormatType.forFileName("Ditto 2026-11-19 14-32.m4a"))
    }

    @Test
    fun `forFileName is case insensitive`() {
        assertEquals(AudioFormatType.M4A, AudioFormatType.forFileName("take.M4A"))
        assertEquals(AudioFormatType.WAV, AudioFormatType.forFileName("take.WAV"))
    }

    @Test
    fun `forFileName maps wav and unknown extensions to WAV`() {
        assertEquals(AudioFormatType.WAV, AudioFormatType.forFileName("take.wav"))
        assertEquals(AudioFormatType.WAV, AudioFormatType.forFileName("take.pcm"))
        assertEquals(AudioFormatType.WAV, AudioFormatType.forFileName("noextension"))
        // A failed AAC export keeps the .wav name: it must be recorded as WAV.
        assertEquals(AudioFormatType.WAV, AudioFormatType.forFileName("take_raw_preview.wav"))
    }

    @Test
    fun `encoded container extensions are rejected`() {
        assertTrue(isEncodedContainerExtension("m4a"))
        assertTrue(isEncodedContainerExtension(".m4a"))
        assertTrue(isEncodedContainerExtension("M4A"))
        assertTrue(isEncodedContainerExtension("aac"))
        assertTrue(isEncodedContainerExtension("mp4"))
        assertTrue(isEncodedContainerExtension("mp3"))
        assertTrue(isEncodedContainerExtension("ogg"))
        assertTrue(isEncodedContainerExtension("opus"))
        assertTrue(isEncodedContainerExtension("flac"))
    }

    @Test
    fun `pcm and wav extensions stay encodable`() {
        assertFalse(isEncodedContainerExtension("wav"))
        assertFalse(isEncodedContainerExtension(".wav"))
        assertFalse(isEncodedContainerExtension("WAV"))
        assertFalse(isEncodedContainerExtension("pcm"))
        assertFalse(isEncodedContainerExtension("raw"))
        assertFalse(isEncodedContainerExtension(""))
    }
}
