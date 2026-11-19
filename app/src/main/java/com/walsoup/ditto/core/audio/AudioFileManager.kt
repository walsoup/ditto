package com.walsoup.ditto.core.audio

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.walsoup.ditto.service.DittoAccessibilityService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        autoPaste: Boolean = true,
        showToast: Boolean = true
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
                            Log.w("Ditto", "AudioFileManager: WhatsApp share failed: $e")
                            Toast.makeText(context, "Audio copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val pasted = DittoAccessibilityService.instance?.performDirectPaste() ?: false
                        if (pasted) {
                            Toast.makeText(context, "Audio pasted automatically!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, PastePolicy.PASTE_FAILED_MESSAGE, Toast.LENGTH_LONG).show()
                        }
                    }
                }, 200)
            } else if (showToast) {
                Toast.makeText(context, "Audio copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            Log.w("Ditto", "AudioFileManager.copyAudioToClipboard failed: $e")
            Toast.makeText(context, "Failed to copy audio: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Unified helper: exports the take into [format], copies it to the system clipboard
     * and commits it to history via `HistoryManager.commitRecording`. When [file] is already
     * in [format] (e.g. the caller exported it itself) [exportFile] returns it unchanged —
     * re-encoding an M4A as if it were raw PCM corrupts it. After a successful commit the
     * raw take and any unused preview file are deleted, unless they are the committed file.
     * Function name kept for existing callers.
     */
    fun copyAndRecordToHistory(
        context: Context,
        file: File,
        durationMs: Long,
        filter: VoiceFilter,
        format: AudioFormatType,
        historyManager: com.walsoup.ditto.data.HistoryManager,
        targetPackage: String? = null
    ): Boolean {
        val exported = try {
            exportFile(context, file, filter, format)
        } catch (e: Exception) {
            Log.w("Ditto", "AudioFileManager.copyAndRecordToHistory: export failed: $e")
            file
        }

        // Commit before handing the URI to the clipboard: commitRecording MOVES the file into
        // filesDir, so a clipboard/share URI built from the cache path would go dead before the
        // receiving app reads it.
        // commitRecording derives the stored format from the file on disk: a failed AAC
        // export falls back to WAV and must not be recorded or renamed as M4A.
        val committed = historyManager.commitRecording(
            sourceFile = exported,
            durationMs = durationMs,
            filter = filter,
            targetPackage = targetPackage
        )
        val sendFile = committed?.filePath?.let { File(it) }?.takeIf { it.exists() } ?: exported

        val success = copyAudioToClipboard(
            context = context,
            file = sendFile,
            autoPaste = historyManager.isAutoPasteEnabled
        )
        if (success && committed != null) {
            deleteIfNotCommitted(file, committed.filePath)
            deleteIfNotCommitted(previewCacheFile(context, file, filter), committed.filePath)
        }
        return success
    }

    /**
     * Copies the clip, opens [packageName] and pastes into its focused field once it is in front.
     * WhatsApp gets a share intent instead, since it rejects pasted audio URIs.
     */
    fun pasteIntoApp(context: Context, file: File, packageName: String, autoPaste: Boolean): Boolean {
        if (!copyAudioToClipboard(context, file, autoPaste = false, showToast = false)) return false
        val uri = getAudioContentUri(context, file)
        try {
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}

        if (packageName.contains("whatsapp")) {
            return try {
                context.startActivity(createShareIntent(context, file).setPackage(packageName))
                true
            } catch (_: Exception) {
                false
            }
        }

        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        if (autoPaste) {
            Handler(Looper.getMainLooper()).postDelayed({
                val pasted = DittoAccessibilityService.instance?.performDirectPaste() ?: false
                if (!pasted) {
                    Toast.makeText(context, PastePolicy.PASTE_FAILED_MESSAGE, Toast.LENGTH_LONG).show()
                }
            }, 900)
        }
        return true
    }

    fun appLabel(context: Context, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            when {
                packageName.startsWith("com.whatsapp") -> "WhatsApp"
                packageName.startsWith("org.telegram") -> "Telegram"
                packageName == "org.thoughtcrime.securesms" -> "Signal"
                packageName == "com.discord" -> "Discord"
                packageName == "com.Slack" -> "Slack"
                else -> null
            }
        }
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
     * Returns the filtered audio as a WAV for playback or filter-pill taps.
     * Never touches the AAC encoder. Results are cached under cacheDir/audio as
     * `<base>_<filter>_preview.wav` and reused while up to date with the input.
     */
    fun previewFile(context: Context, inputFile: File, filter: VoiceFilter): File {
        if (filter == VoiceFilter.RAW || !inputFile.exists()) return inputFile
        val preview = previewCacheFile(context, inputFile, filter)
        if (preview.exists() && preview.length() > 0 && preview.lastModified() >= inputFile.lastModified()) {
            return preview
        }
        applyFilter(inputFile, preview, filter)
        if (preview.exists() && preview.length() > 0) return preview
        Log.w("Ditto", "AudioFileManager.previewFile: filter output missing, returning input")
        return inputFile
    }

    /**
     * Produces the real target file in [format] and reports encode progress via [onProgress].
     * This is the only AAC entry point. On AAC failure it retries once at
     * [AudioConfig.AAC_FALLBACK_BIT_RATE], then returns the filtered WAV and logs a warning.
     * Never returns a missing file: worst case the (existing) input file is returned.
     * Callers must derive the stored format from the returned file's extension, since a
     * failed AAC export falls back to WAV.
     */
    fun exportFile(
        context: Context,
        inputFile: File,
        filter: VoiceFilter,
        format: AudioFormatType,
        onProgress: ((Float) -> Unit)? = null
    ): File {
        if (!inputFile.exists()) {
            Log.w("Ditto", "AudioFileManager.exportFile: input missing: ${inputFile.absolutePath}")
            return inputFile
        }
        // Already exported: re-encoding an encoded container as raw PCM corrupts it.
        if (isEncodedContainerExtension(inputFile.extension)) {
            return inputFile
        }
        val wav = previewFile(context, inputFile, filter)
        if (format == AudioFormatType.WAV) return wav
        if (!wav.exists()) {
            Log.w("Ditto", "AudioFileManager.exportFile: filtered WAV missing, returning input")
            return inputFile
        }
        val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
        val dest = File(audioDir, "${inputFile.nameWithoutExtension}_${filter.name.lowercase()}.m4a")
        if (dest.exists() && dest.length() > 0 && dest.lastModified() >= wav.lastModified()) {
            return dest
        }
        if (encodeToM4a(wav, dest, AudioConfig.AAC_BIT_RATE, onProgress)) return dest
        Log.w("Ditto", "AudioFileManager.exportFile: AAC encode failed, retrying at fallback bit rate")
        if (encodeToM4a(wav, dest, AudioConfig.AAC_FALLBACK_BIT_RATE, onProgress)) return dest
        Log.w("Ditto", "AudioFileManager.exportFile: AAC encode failed twice, returning WAV: ${wav.absolutePath}")
        try {
            if (dest.exists() && dest.length() == 0L) dest.delete()
        } catch (_: Exception) {}
        return wav
    }

    private fun encodeToM4a(wav: File, dest: File, bitRate: Int, onProgress: ((Float) -> Unit)?): Boolean {
        return try {
            val ok = M4aEncoder.encodePcmToM4a(
                inputFile = wav,
                outputFile = dest,
                sampleRate = AudioConfig.SAMPLE_RATE,
                channelCount = AudioConfig.CHANNELS,
                bitRate = bitRate,
                onProgress = onProgress
            )
            ok && dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            Log.w("Ditto", "AudioFileManager: AAC encode failed at $bitRate bps: $e")
            false
        }
    }

    /** Bounded-memory streaming filter; falls back to the in-memory path if streaming is unusable. */
    private fun applyFilter(inputFile: File, outputFile: File, filter: VoiceFilter) {
        try {
            VoiceFilterProcessor.processFileStreaming(
                inputFile = inputFile,
                outputFile = outputFile,
                filter = filter
            )
        } catch (e: Exception) {
            Log.w("Ditto", "AudioFileManager: streaming filter failed for $filter, falling back to processAudio: $e")
            try {
                VoiceFilterProcessor.processAudio(
                    inputFile = inputFile,
                    outputFile = outputFile,
                    filter = filter
                )
            } catch (e2: Exception) {
                Log.w("Ditto", "AudioFileManager: filter fallback also failed for $filter: $e2")
            }
        }
    }

    private fun previewCacheFile(context: Context, inputFile: File, filter: VoiceFilter): File {
        return File(
            File(context.cacheDir, "audio").apply { mkdirs() },
            "${inputFile.nameWithoutExtension}_${filter.name.lowercase()}_preview.wav"
        )
    }

    /**
     * Applies the chosen VoiceFilter and returns the file in [targetFormat].
     * Thin delegate kept for existing callers: WAV goes through [previewFile],
     * M4A through [exportFile]. Click handlers must call those directly
     * (preview taps -> previewFile, send/share -> exportFile).
     */
    fun generateFilteredAudio(
        context: Context,
        inputFile: File,
        filter: VoiceFilter,
        targetFormat: AudioFormatType = AudioFormatType.WAV
    ): File {
        return if (targetFormat == AudioFormatType.M4A) {
            exportFile(context, inputFile, filter, targetFormat)
        } else {
            previewFile(context, inputFile, filter)
        }
    }

    /**
     * Display name for a committed take, e.g. "Ditto 2026-11-19 14-32.m4a".
     * Digits are locale-independent (Locale.US); the wall-clock zone is the device default.
     */
    fun displayNameFor(createdAt: Long, extension: String): String {
        val ext = extension.removePrefix(".").ifBlank { AudioFormatType.WAV.extension }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date(createdAt))
        return "Ditto $stamp.$ext"
    }

    private fun deleteIfNotCommitted(candidate: File, committedPath: String) {
        try {
            if (!candidate.exists()) return
            if (candidate.canonicalPath != File(committedPath).canonicalPath) {
                candidate.delete()
            }
        } catch (e: Exception) {
            Log.w("Ditto", "AudioFileManager: cleanup delete failed for ${candidate.absolutePath}: $e")
        }
    }
}
