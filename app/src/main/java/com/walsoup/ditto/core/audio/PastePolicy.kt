package com.walsoup.ditto.core.audio

/**
 * Pure paste rules shared by the bubble, the review sheet and [AudioFileManager].
 * No Android framework access so the rules stay unit-testable.
 */
object PastePolicy {

    const val PASTE_FAILED_MESSAGE = "Copied. Long-press the chat field to paste"

    fun targetFormatForPackage(
        foregroundPackage: String?,
        selected: AudioFormatType
    ): AudioFormatType {
        return if (foregroundPackage?.contains("whatsapp") == true) {
            AudioFormatType.M4A
        } else {
            selected
        }
    }

    fun pasteFailureMessage(pasted: Boolean): String? {
        return if (pasted) null else PASTE_FAILED_MESSAGE
    }

    fun autoStopTargetPackage(foregroundPackage: String?): String? {
        return foregroundPackage?.ifBlank { null }
    }
}
