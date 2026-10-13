package com.walsoup.ditto.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.data.BubbleShape
import com.walsoup.ditto.data.BubbleSize
import com.walsoup.ditto.data.BubbleTheme
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.ditto.isAccessibilityServiceEnabled
import com.walsoup.ditto.theme.Ditto
import com.walsoup.ditto.ui.design.Hairline
import com.walsoup.ditto.ui.design.PageHeader
import com.walsoup.ditto.ui.design.Pill
import com.walsoup.ditto.ui.design.ScreenMargin
import com.walsoup.ditto.ui.design.SectionTitle
import com.walsoup.ditto.ui.design.SettingLine
import com.walsoup.ditto.ui.design.SwitchLine
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(historyManager: HistoryManager) {
    val c = Ditto.colors
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(historyManager.selectedFormat) }
    var autoPaste by remember { mutableStateOf(historyManager.isAutoPasteEnabled) }
    val appFilterEnabled by historyManager.isAppFilterEnabledFlow.collectAsState()
    val selectedPackages by historyManager.selectedAppPackagesFlow.collectAsState()
    val bubbleOpacity by historyManager.bubbleOpacityFlow.collectAsState()
    val bubbleSize by historyManager.bubbleSizeFlow.collectAsState()
    val bubbleShape by historyManager.bubbleShapeFlow.collectAsState()
    val bubbleTheme by historyManager.bubbleThemeFlow.collectAsState()
    val bubbleIdleDim by historyManager.bubbleIdleDimFlow.collectAsState()
    val bubbleSnapToEdge by historyManager.bubbleSnapToEdgeFlow.collectAsState()
    val volumeKey by historyManager.volumeKeyShortcutEnabledFlow.collectAsState()
    var showAppSelection by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    val accessibilityOn = isAccessibilityServiceEnabled(context)
    val openAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenMargin)
    ) {
        PageHeader(overline = "Preferences", title = "Settings")

        SectionTitle("Pasting")
        SwitchLine(
            label = "Automatic paste",
            subtitle = "Drops the clip into the chat field you're in",
            checked = autoPaste,
            onCheckedChange = {
                autoPaste = it
                historyManager.isAutoPasteEnabled = it
            }
        )
        SettingLine(
            label = "Accessibility service",
            subtitle = "Only taps Paste. It never reads your screen.",
            value = if (accessibilityOn) "On" else "Off",
            emphasis = accessibilityOn,
            warning = !accessibilityOn,
            onClick = openAccessibility
        )

        SectionTitle("Bubble")
        SwitchLine(
            label = "Only in chosen apps",
            subtitle = "Hidden everywhere else, including the home screen",
            checked = appFilterEnabled,
            onCheckedChange = { historyManager.isAppFilterEnabled = it }
        )
        if (appFilterEnabled) {
            SettingLine(
                label = "Chosen apps",
                value = if (selectedPackages.size == 1) "1 app" else "${selectedPackages.size} apps",
                onClick = { showAppSelection = true }
            )
            if (!accessibilityOn) {
                SettingLine(
                    label = "Needs the accessibility service",
                    subtitle = "Used to see which app is open",
                    value = "Turn on",
                    warning = true,
                    onClick = openAccessibility
                )
            }
        }

        BubblePreview(bubbleOpacity, bubbleSize, bubbleShape, bubbleTheme)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Text("Opacity", style = Ditto.type.body, color = c.text, modifier = Modifier.weight(1f))
            Text("${(bubbleOpacity * 100).roundToInt()}%", style = Ditto.type.monoSmall, color = c.primary)
        }
        Slider(
            value = bubbleOpacity,
            onValueChange = { historyManager.bubbleOpacity = it },
            valueRange = 0.20f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.container,
                activeTickColor = c.primary,
                inactiveTickColor = c.container
            )
        )
        Hairline()

        ChoiceGroup("Size") {
            BubbleSize.entries.forEach { size ->
                Pill(size.displayName, bubbleSize == size, { historyManager.bubbleSize = size }, Modifier.weight(1f))
            }
        }
        ChoiceGroup("Shape") {
            BubbleShape.entries.forEach { shape ->
                Pill(shape.displayName, bubbleShape == shape, { historyManager.bubbleShape = shape }, Modifier.weight(1f))
            }
        }
        ChoiceGroup("Color") {
            BubbleTheme.entries.forEach { theme ->
                Pill(
                    text = theme.displayName,
                    selected = bubbleTheme == theme,
                    onClick = { historyManager.bubbleTheme = theme },
                    modifier = Modifier.weight(1f),
                    leading = {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(theme.iconColor)
                        )
                    }
                )
            }
        }
        SwitchLine(
            label = "Dim when idle",
            subtitle = "Fades the bubble when you're not using it",
            checked = bubbleIdleDim,
            onCheckedChange = { historyManager.isBubbleIdleDimEnabled = it }
        )
        SwitchLine(
            label = "Snap to edge",
            subtitle = "Docks to the left or right side when you let go",
            checked = bubbleSnapToEdge,
            onCheckedChange = { historyManager.isBubbleSnapToEdgeEnabled = it }
        )

        SectionTitle("Triggers")
        SwitchLine(
            label = "Double-tap Volume Down",
            subtitle = "Starts or stops recording without touching the screen",
            checked = volumeKey,
            onCheckedChange = { historyManager.isVolumeKeyShortcutEnabled = it }
        )
        if (volumeKey && !accessibilityOn) {
            SettingLine(
                label = "Needs the accessibility service",
                subtitle = "Used to detect volume button presses",
                value = "Turn on",
                warning = true,
                onClick = openAccessibility
            )
        }
        SettingLine(
            label = "Quick Settings tile",
            subtitle = "Pull down the notification shade, tap Edit and add \"Ditto Recorder\"",
            showChevron = false
        )

        SectionTitle("Format")
        AudioFormatType.entries.forEach { format ->
            SettingLine(
                label = if (format == AudioFormatType.M4A) "M4A" else "WAV",
                subtitle = if (format == AudioFormatType.M4A) "Small files, works in WhatsApp" else "Lossless, larger files",
                value = if (format == selectedFormat) "Selected" else null,
                emphasis = true,
                showChevron = false,
                onClick = {
                    selectedFormat = format
                    historyManager.selectedFormat = format
                }
            )
        }

        SectionTitle("About")
        SettingLine(label = "Developer", value = "walsoup", showChevron = false)
        SettingLine(label = "License", value = "MIT", onClick = { showLicense = true })
        SettingLine(label = "Terms of Service", onClick = { showTerms = true }, showHairline = false)
        Spacer(Modifier.height(28.dp))
    }

    if (showLicense) {
        TextDialog(
            title = "MIT License",
            body = """
                Copyright (c) 2026 walsoup

                Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

                The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
            """.trimIndent(),
            mono = true,
            onDismiss = { showLicense = false }
        )
    }

    if (showTerms) {
        TextDialog(
            title = "Terms of Service",
            body = """
                1. On-device processing
                All recording, voice filters and encoding happen on your phone. Audio is never uploaded.

                2. Privacy
                walsoup does not collect, sell or transmit recordings, usage statistics or telemetry.

                3. Your content
                You own every note you record. Notes are only shared when you tap Copy, Share or Paste.

                4. Accessibility service
                The optional accessibility service only performs the standard paste action in the focused text field when you ask it to. It does not read or collect what's on your screen.
            """.trimIndent(),
            mono = false,
            onDismiss = { showTerms = false }
        )
    }

    if (showAppSelection) {
        AppSelectionDialog(historyManager = historyManager, onDismissRequest = { showAppSelection = false })
    }
}

@Composable
private fun ChoiceGroup(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(label, style = Ditto.type.body, color = Ditto.colors.text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), content = content)
        Spacer(Modifier.height(14.dp))
        Hairline()
    }
}

@Composable
private fun BubblePreview(opacity: Float, size: BubbleSize, shape: BubbleShape, theme: BubbleTheme) {
    val c = Ditto.colors
    val outline = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .height(108.dp)
            .clip(outline)
            .background(c.containerLow)
            .border(1.dp, c.hairline, outline)
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .align(Alignment.CenterStart)
        ) {
            listOf(0.85f, 0.6f, 0.95f).forEach { w ->
                Box(
                    Modifier
                        .fillMaxWidth(w)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.hairline)
                )
            }
        }
        val bubble = shape.getShape(size.sizeDp)
        Surface(
            shape = bubble,
            color = theme.containerColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderColor),
            modifier = Modifier
                .size(size.sizeDp)
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = opacity }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Mic, contentDescription = "Bubble preview", tint = theme.iconColor, modifier = Modifier.size(size.iconSizeDp))
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, body: String, mono: Boolean, onDismiss: () -> Unit) {
    val c = Ditto.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Ditto.type.title, color = c.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(body, style = if (mono) Ditto.type.monoTiny else Ditto.type.bodySecondary, color = c.text)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", style = Ditto.type.label, color = c.primary) }
        },
        containerColor = c.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
