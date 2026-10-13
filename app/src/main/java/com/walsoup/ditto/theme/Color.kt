package com.walsoup.ditto.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Flat Pastel Material 3 Palette (60-30-10 Rule)
// 60% Base: Warm linen cream and clean white card backgrounds
val LinenCream = Color(0xFFFAF9F6)
val CleanWhite = Color(0xFFFFFFFF)

// 30% Structure: Soft sage containers, hairline borders, slate text
val SoftSage = Color(0xFFE8F1EC)
val SoftSageLow = Color(0xFFF1F5F2)
val HairlineBorder = Color(0xFFE0E6E2)
val SlateText = Color(0xFF1F2421)
val SlateSubtext = Color(0xFF526058)

// 10% Accent: Deep forest sage for primary actions, warm terracotta for recording/stop
val ForestSage = Color(0xFF2D6A4F)
val WarmTerracotta = Color(0xFFD47255)

// Neutral & Utility Tokens
val MutedIcon = Color(0xFF8A9A90)
val InactiveBar = Color(0xFFD0D7D3)
val TerracottaSoft = Color(0xFFFBF0EC)
val TerracottaLine = Color(0xFFF2DCD3)
val TerracottaTint = Color(0xFFF5ECE8)
val PreviewSurface = Color(0xFFF4F6F4)
val PreviewDivider = Color(0xFFDFE6E1)

@Immutable
data class DittoColors(
    val bg: Color,
    val surface: Color,
    val container: Color,
    val containerLow: Color,
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val rec: Color,
    val recSoft: Color,
    val inactive: Color,
    val scrim: Color,
    val isDark: Boolean
)

val LightDittoColors = DittoColors(
    bg = LinenCream,
    surface = CleanWhite,
    container = SoftSage,
    containerLow = SoftSageLow,
    hairline = HairlineBorder,
    text = SlateText,
    textSecondary = SlateSubtext,
    muted = MutedIcon,
    primary = ForestSage,
    onPrimary = CleanWhite,
    rec = WarmTerracotta,
    recSoft = TerracottaSoft,
    inactive = InactiveBar,
    scrim = SlateText.copy(alpha = 0.38f),
    isDark = false
)

val DarkDittoColors = DittoColors(
    bg = Color(0xFF121513),
    surface = Color(0xFF1A1E1B),
    container = Color(0xFF22302A),
    containerLow = Color(0xFF1D2520),
    hairline = Color(0xFF2A322D),
    text = Color(0xFFE8ECE9),
    textSecondary = Color(0xFFA3B0A8),
    muted = Color(0xFF6F7D75),
    primary = Color(0xFF7DBB9B),
    onPrimary = Color(0xFF0F1A14),
    rec = Color(0xFFE08A6E),
    recSoft = Color(0xFF33241F),
    inactive = Color(0xFF3A433E),
    scrim = Color.Black.copy(alpha = 0.5f),
    isDark = true
)
