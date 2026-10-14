package com.walsoup.linen

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** System sans for words, system monospace for every number (timers, counts, durations). */
@Immutable
data class LinenTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySecondary: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val label: TextStyle,
    val monoHero: TextStyle,
    val monoDock: TextStyle,
    val monoSmall: TextStyle,
    val monoTiny: TextStyle,
)

private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

val LinenType = LinenTypography(
    display = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-2).sp),
    headline = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1.2).sp),
    title = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.4).sp),
    body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySecondary = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    overline = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    monoHero = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 54.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    monoDock = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    monoSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    monoTiny = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

internal val LinenMaterialTypography = Typography(
    titleLarge = LinenType.title,
    bodyLarge = LinenType.body,
    bodyMedium = LinenType.bodySecondary,
    bodySmall = LinenType.caption,
    labelLarge = LinenType.label,
)
