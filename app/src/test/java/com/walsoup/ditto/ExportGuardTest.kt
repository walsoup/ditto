package com.walsoup.ditto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.core.audio.M4aEncoder
import com.walsoup.ditto.core.audio.VoiceFilter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression tests for the re-export corruption finding: an already-encoded container
 * must pass through the export pipeline unchanged (never re-encoded as if it were raw
 * PCM), and format/MIME selection keeps following the file's real extension.
 */
@RunWith(RobolectricTestRunner::class)
class ExportGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun tempAudio(name: String, size: Int): File =
        File(context.cacheDir, name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(size) { (it * 31).toByte() })
        }

    @Test
    fun `exportFile returns already-encoded m4a unchanged`() {
        val m4a = tempAudio("take_raw.m4a", 64)
        val out = AudioFileManager.exportFile(context, m4a, VoiceFilter.RAW, AudioFormatType.M4A)
        assertEquals("already-encoded input must not be re-encoded", m4a.absolutePath, out.absolutePath)
        assertArrayEquals(m4a.readBytes(), out.readBytes())
        assertEquals("take_raw.m4a", out.name) // extension preserved -> MIME selection unaffected
    }

    @Test
    fun `exportFile with wav request returns wav input`() {
        val wav = tempAudio("take.wav", 128)
        val out = AudioFileManager.exportFile(context, wav, VoiceFilter.RAW, AudioFormatType.WAV)
        assertEquals(wav.absolutePath, out.absolutePath)
        assertArrayEquals(wav.readBytes(), out.readBytes())
    }

    @Test
    fun `encoder refuses encoded container input`() {
        val m4a = tempAudio("already_encoded.m4a", 64)
        val dest = File(context.cacheDir, "should_not_exist.m4a")
        assertFalse(M4aEncoder.encodePcmToM4a(inputFile = m4a, outputFile = dest))
        assertFalse("no output may be produced from an encoded container", dest.exists())
    }
}
