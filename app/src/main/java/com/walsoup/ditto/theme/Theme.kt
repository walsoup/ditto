package com.walsoup.ditto.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDittoColors = staticCompositionLocalOf { LightDittoColors }

object Ditto {
    val colors: DittoColors
        @Composable @ReadOnlyComposable get() = LocalDittoColors.current
    val type: DittoTypography
        get() = DittoType
}

@Composable
fun DittoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkDittoColors else LightDittoColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            primaryContainer = c.container, onPrimaryContainer = c.primary,
            secondary = c.primary, onSecondary = c.onPrimary,
            secondaryContainer = c.container, onSecondaryContainer = c.primary,
            tertiary = c.rec, onTertiary = c.onPrimary,
            background = c.bg, onBackground = c.text,
            surface = c.bg, onSurface = c.text,
            surfaceVariant = c.containerLow, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.container,
            surfaceContainerLow = c.surface, surfaceContainerLowest = c.bg,
            inverseSurface = c.text, inverseOnSurface = c.bg, inversePrimary = c.primary,
            outline = c.hairline, outlineVariant = c.hairline,
            error = c.rec, onError = c.onPrimary, scrim = c.scrim
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            primaryContainer = c.container, onPrimaryContainer = c.primary,
            secondary = c.primary, onSecondary = c.onPrimary,
            secondaryContainer = c.container, onSecondaryContainer = c.primary,
            tertiary = c.rec, onTertiary = c.onPrimary,
            background = c.bg, onBackground = c.text,
            surface = c.bg, onSurface = c.text,
            surfaceVariant = c.containerLow, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.container,
            surfaceContainerLow = c.surface, surfaceContainerLowest = c.bg,
            inverseSurface = c.text, inverseOnSurface = c.bg, inversePrimary = c.primary,
            outline = c.hairline, outlineVariant = c.hairline,
            error = c.rec, onError = c.onPrimary, scrim = c.scrim
        )
    }

    CompositionLocalProvider(LocalDittoColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
