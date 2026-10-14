package com.walsoup.linen

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Raw brand colors for code outside Compose themes (Views, notifications, overlays).
 * Inside Compose, read [Linen.colors] so dark mode works.
 */
object LinenPalette {
    // 60% base
    val Linen = Color(0xFFFAF9F6)
    val White = Color(0xFFFFFFFF)

    // 30% structure
    val Sage = Color(0xFFE8F1EC)
    val SageLow = Color(0xFFF1F5F2)
    val Hairline = Color(0xFFE0E6E2)
    val Slate = Color(0xFF1F2421)
    val SlateSoft = Color(0xFF526058)
    val Muted = Color(0xFF8A9A90)
    val Inactive = Color(0xFFD0D7D3)

    // 10% accent
    val Forest = Color(0xFF2D6A4F)
    val Terracotta = Color(0xFFD47255)
    val TerracottaSoft = Color(0xFFFBF0EC)
}

/**
 * Semantic color roles. `primary` is for the main action, `rec` for live, destructive
 * or "stop" states; never use them for decoration.
 */
@Immutable
data class LinenColors(
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
    val isDark: Boolean,
)

val LightLinenColors = LinenColors(
    bg = LinenPalette.Linen,
    surface = LinenPalette.White,
    container = LinenPalette.Sage,
    containerLow = LinenPalette.SageLow,
    hairline = LinenPalette.Hairline,
    text = LinenPalette.Slate,
    textSecondary = LinenPalette.SlateSoft,
    muted = LinenPalette.Muted,
    primary = LinenPalette.Forest,
    onPrimary = LinenPalette.White,
    rec = LinenPalette.Terracotta,
    recSoft = LinenPalette.TerracottaSoft,
    inactive = LinenPalette.Inactive,
    scrim = LinenPalette.Slate.copy(alpha = 0.38f),
    isDark = false,
)

val DarkLinenColors = LinenColors(
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
    isDark = true,
)
