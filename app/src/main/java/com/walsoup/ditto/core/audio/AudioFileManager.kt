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

            val mimeTypes = if (file.name.endsWith(".m4a")) {
                arrayOf("audio/mp4", "audio/aac", "audio/m4a", "audio/x-m4a", "audio/*")
            } else {
                arrayOf("audio/wav", "audio/x-wav", "audio/*")
            }

            val clipData = ClipData(
                ClipDescription(label, mimeTypes),
                ClipData.Item(uri)
            )
            clipboard.setPrimaryClip(clipData)

            // Explicitly grant read URI permission to the target foreground app to avoid permission errors
            val activePkg = DittoAccessibilityService.currentForegroundPackage.value
            if (!activePkg.isNullOrBlank()) {
                try {
                    context.grantUriPermission(activePkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            try {
                context.grantUriPermission("com.whatsapp", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.grantUriPermission("com.whatsapp.w4b", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}

            if (autoPaste) {
                // Allow clipboard buffer to settle then trigger direct paste (or direct share if in WhatsApp)
                Handler(Looper.getMainLooper()).postDelayed({
                    val activePkg = DittoAccessibilityService.currentForegroundPackage.value
                    if (activePkg?.contains("whatsapp") == true) {
                        try {
                            val shareIntent = createShareIntent(context, file).apply {
                                setPackage(activePkg)
                            }
                            context.startActivity(shareIntent)
                            Toast.makeText(context, "Audio ready to send in WhatsApp", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Audio copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val pasted = DittoAccessibilityService.instance?.performDirectPaste() ?: false
                        if (pasted) {
                            Toast.makeText(context, "Audio pasted automatically!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Audio copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, 200)
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
        // If target format is M4A but input is WAV, transcode before sending
        val targetFile = if (format == AudioFormatType.M4A && file.name.endsWith(".wav")) {
            generateFilteredAudio(context, file, filter, AudioFormatType.M4A)
        } else {
            file
        }

        val success = copyAudioToClipboard(
            context = context,
            file = targetFile,
            autoPaste = historyManager.isAutoPasteEnabled
        )
        if (success) {
            historyManager.addRecording(
                file = targetFile,
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
