package com.walsoup.ditto.core.audio

enum class AudioFormatType(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val description: String
) {
    WAV("wav", "audio/wav", "WAV", "Uncompressed lossless audio (Universal)"),
    M4A("m4a", "audio/mp4", "M4A (AAC)", "Compressed high-efficiency audio (AI apps)")
}

enum class VoiceFilter(
    val displayName: String,
    val shortName: String,
    val description: String,
    val iconName: String
) {
    RAW(
        displayName = "Raw / Natural",
        shortName = "Raw",
        description = "Crisp, uncompressed pure audio note",
        iconName = "mic"
    ),
    STUDIO(
        displayName = "Studio Mic",
        shortName = "Studio",
        description = "Warm bass, presence boost & broadcast compression",
        iconName = "graphic_eq"
    ),
    ROBOT(
        displayName = "Cyber Robot",
        shortName = "Robot",
        description = "Ring-modulated futuristic synthesizer voice",
        iconName = "smart_toy"
    ),
    CHIPMUNK(
        displayName = "Helium Chipmunk",
        shortName = "Chipmunk",
        description = "High-pitched playful chipmunk voice",
        iconName = "sentiment_very_satisfied"
    ),
    DEEP_TITAN(
        displayName = "Deep Titan",
        shortName = "Titan",
        description = "Sub-bass pitched booming villain voice",
        iconName = "shield"
    ),
    RADIO(
        displayName = "Walkie-Talkie",
        shortName = "Lo-Fi",
        description = "Lo-fi bandpass filter with vintage drive & crackle",
        iconName = "radio"
    ),
    ECHO(
        displayName = "Cosmic Echo",
        shortName = "Echo",
        description = "Atmospheric delay feedback repeat",
        iconName = "waves"
    ),
    FAST_RANT(
        displayName = "Speed Voice (1.35x)",
        shortName = "Fast",
        description = "Compressed cadence for rapid-fire stream of thought",
        iconName = "fast_forward"
    )
}

data class HistoryItem(
    val id: String,
    val fileName: String,
    val filePath: String,
    val durationMs: Long,
    val format: AudioFormatType,
    val filter: VoiceFilter,
    val createdAt: Long = System.currentTimeMillis(),
    val targetPackage: String? = null
)
