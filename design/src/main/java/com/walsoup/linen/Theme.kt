package com.walsoup.linen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalLinenColors = staticCompositionLocalOf { LightLinenColors }

/** Entry point for tokens inside a [LinenTheme]: `Linen.colors.primary`, `Linen.type.title`. */
object Linen {
    val colors: LinenColors
        @Composable @ReadOnlyComposable get() = LocalLinenColors.current
    val type: LinenTypography
        get() = LinenType
}

/** Spacing and shape tokens. */
object LinenDimens {
    val ScreenMargin = 24.dp
    val RowMinHeight = 52.dp
    val TouchTarget = 44.dp
    val ButtonHeight = 46.dp
    val ButtonRadius = 23.dp
    val PillHeight = 36.dp
    val PillRadius = 10.dp
    val DockHeight = 64.dp
    val DockRadius = 32.dp
    val CardRadius = 12.dp
    val DialogRadius = 24.dp
    val SheetRadius = 28.dp
    val KeycapRadius = 7.dp
}

val ScreenMargin = LinenDimens.ScreenMargin

/**
 * Applies Linen colors and type, and maps them onto Material 3 so stock components
 * (dialogs, menus, sliders, snackbars) match without extra styling.
 */
@Composable
fun LinenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    lightColors: LinenColors = LightLinenColors,
    darkColors: LinenColors = DarkLinenColors,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkColors else lightColors
    CompositionLocalProvider(LocalLinenColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = LinenMaterialTypography,
            content = content,
        )
    }
}

fun LinenColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = container, onPrimaryContainer = primary,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = container, onSecondaryContainer = primary,
        tertiary = rec, onTertiary = onPrimary,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = containerLow, onSurfaceVariant = textSecondary,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = container,
        surfaceContainerLow = surface, surfaceContainerLowest = bg,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = primary,
        outline = hairline, outlineVariant = hairline,
        error = rec, onError = onPrimary, scrim = scrim,
    )
}
