package com.walsoup.ditto.core.audio

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.walsoup.ditto.service.DittoAccessibilityService
import java.io.File

object AudioFileManager {

    fun getAudioContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    /**
     * Copies audio to system clipboard and triggers automatic paste via AccessibilityService if active.
     */
    fun copyAudioToClipboard(
        context: Context,
        file: File,
        label: String = "Ditto Audio",
        autoPaste: Boolean = true
    ): Boolean {
        return try {
            val uri = getAudioContentUri(context, file)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val mime = if (file.name.endsWith(".m4a")) "audio/mp4" else "audio/wav"
            val mimeTypes = arrayOf(mime, "audio/*")

            val clipData = ClipData(
                ClipDescription(label, mimeTypes),
                ClipData.Item(uri)
            )
            clipboard.setPrimaryClip(clipData)

            if (autoPaste) {
                // Allow clipboard buffer to settle then trigger direct paste
                Handler(Looper.getMainLooper()).postDelayed({
                    val pasted = DittoAccessibilityService.instance?.performDirectPaste() ?: false
                    if (pasted) {
                        Toast.makeText(context, "Audio pasted automatically!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Audio copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }, 150)
            } else {
                Toast.makeText(context, "Audio copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to copy audio: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Unified helper: copies audio to system clipboard and records entry into HistoryManager.
     */
    fun copyAndRecordToHistory(
        context: Context,
        file: File,
        durationMs: Long,
        filter: VoiceFilter,
        format: AudioFormatType,
        historyManager: com.walsoup.ditto.data.HistoryManager
    ): Boolean {
        val success = copyAudioToClipboard(
            context = context,
            file = file,
            autoPaste = historyManager.isAutoPasteEnabled
        )
        if (success) {
            historyManager.addRecording(
                file = file,
                durationMs = durationMs,
                filter = filter,
                format = format
            )
        }
        return success
    }

    /**
     * Creates an ACTION_SEND Intent with read permissions granted for the target app.
     */
    fun createShareIntent(context: Context, file: File): Intent {
        val uri = getAudioContentUri(context, file)
        val mime = if (file.name.endsWith(".m4a")) "audio/mp4" else "audio/wav"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ditto Audio Note")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Applies the chosen VoiceFilter and encodes into the target AudioFormatType (WAV or M4A).
     */
    fun generateFilteredAudio(
        context: Context,
        inputFile: File,
        filter: VoiceFilter,
        targetFormat: AudioFormatType = AudioFormatType.WAV
    ): File {
        val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
        val baseName = inputFile.nameWithoutExtension
        val tempWavFile = if (filter == VoiceFilter.RAW) {
            inputFile
        } else {
            val filteredWav = File(audioDir, "${baseName}_${filter.name.lowercase()}.wav")
            VoiceFilterProcessor.processAudio(
                inputFile = inputFile,
                outputFile = filteredWav,
                filter = filter
            )
            filteredWav
        }

        return if (targetFormat == AudioFormatType.M4A) {
            val m4aFile = File(audioDir, "${baseName}_${filter.name.lowercase()}.m4a")
            val encoded = M4aEncoder.encodePcmToM4a(tempWavFile, m4aFile)
            if (encoded && m4aFile.exists()) {
                m4aFile
            } else {
                tempWavFile
            }
        } else {
            tempWavFile
        }
    }
}
