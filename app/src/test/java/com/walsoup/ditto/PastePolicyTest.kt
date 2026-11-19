package com.walsoup.ditto

import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.core.audio.PastePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastePolicyTest {

    @Test
    fun testPasteFailedMessageMatchesSpec() {
        assertEquals(
            "Copied. Long-press the chat field to paste",
            PastePolicy.PASTE_FAILED_MESSAGE
        )
    }

    @Test
    fun testPasteFailureMessageOnlyOnFailure() {
        assertNull(PastePolicy.pasteFailureMessage(true))
        assertEquals(
            PastePolicy.PASTE_FAILED_MESSAGE,
            PastePolicy.pasteFailureMessage(false)
        )
    }

    @Test
    fun testWhatsAppForcesM4a() {
        assertEquals(
            AudioFormatType.M4A,
            PastePolicy.targetFormatForPackage("com.whatsapp", AudioFormatType.WAV)
        )
        assertEquals(
            AudioFormatType.M4A,
            PastePolicy.targetFormatForPackage("com.whatsapp.w4b", AudioFormatType.WAV)
        )
        assertEquals(
            AudioFormatType.WAV,
            PastePolicy.targetFormatForPackage("org.telegram.messenger", AudioFormatType.WAV)
        )
        assertEquals(
            AudioFormatType.WAV,
            PastePolicy.targetFormatForPackage(null, AudioFormatType.WAV)
        )
        assertEquals(
            AudioFormatType.M4A,
            PastePolicy.targetFormatForPackage(null, AudioFormatType.M4A)
        )
    }

    @Test
    fun testAutoStopStoresForegroundPackage() {
        assertEquals(
            "org.telegram.messenger",
            PastePolicy.autoStopTargetPackage("org.telegram.messenger")
        )
        assertNull(PastePolicy.autoStopTargetPackage(null))
        assertNull(PastePolicy.autoStopTargetPackage(""))
        assertNull(PastePolicy.autoStopTargetPackage("   "))
    }
}
