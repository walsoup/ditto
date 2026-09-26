package com.walsoup.ditto.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Pastel Material 3 Color Schemes - Strictly No Purple, Solid Flat Surfaces
private val LightColorScheme = lightColorScheme(
    primary = ForestSage,
    onPrimary = CleanWhite,
    primaryContainer = SoftSage,
    onPrimaryContainer = ForestSage,
    secondary = SoftSage,
    onSecondary = SlateText,
    tertiary = WarmTerracotta,
    onTertiary = CleanWhite,
    background = LinenCream,
    onBackground = SlateText,
    surface = LinenCream,
    onSurface = SlateText,
    surfaceVariant = SoftSage,
    onSurfaceVariant = SlateSubtext,
    outline = HairlineBorder,
    outlineVariant = SoftSageLow,
    error = WarmTerracotta,
    onError = CleanWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestSage,
    onPrimary = CleanWhite,
    primaryContainer = SoftSage,
    onPrimaryContainer = ForestSage,
    secondary = SoftSage,
    onSecondary = SlateText,
    tertiary = WarmTerracotta,
    onTertiary = CleanWhite,
    background = LinenCream,
    onBackground = SlateText,
    surface = LinenCream,
    onSurface = SlateText,
    surfaceVariant = SoftSage,
    onSurfaceVariant = SlateSubtext,
    outline = HairlineBorder,
    outlineVariant = SoftSageLow,
    error = WarmTerracotta,
    onError = CleanWhite
)

@Composable
fun DittoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Default false to enforce strict Pastel Material 3 invariant
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
