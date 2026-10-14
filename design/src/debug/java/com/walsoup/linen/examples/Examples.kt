package com.walsoup.linen.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ActionStyle
import com.walsoup.linen.EmptyState
import com.walsoup.linen.GroupLabel
import com.walsoup.linen.Linen
import com.walsoup.linen.LinenDimens
import com.walsoup.linen.LinenTheme
import com.walsoup.linen.PageHeader
import com.walsoup.linen.Pill
import com.walsoup.linen.RecordDock
import com.walsoup.linen.RoundIconButton
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SectionTitle
import com.walsoup.linen.SettingLine
import com.walsoup.linen.SwitchLine
import com.walsoup.linen.TextTabs
import com.walsoup.linen.TrackRow
import com.walsoup.linen.Waveform
import com.walsoup.linen.formatClock

// Reference screens built only from Linen. They compile in debug builds so they stay
// correct; open this file in Android Studio to see each one in light and dark.

private val sampleLevels = List(120) { i -> (0.15f + 0.75f * kotlin.math.abs(kotlin.math.sin(i * 0.37f))) * (0.6f + 0.4f * ((i * 7) % 5) / 4f) }

/** Main screen: header, grouped list, bottom tabs and the record dock in the thumb zone. */
@Composable
fun ListScreenExample() {
    var tab by remember { mutableStateOf("Notes") }
    var recording by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Linen.colors.bg)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenMargin)
        ) {
            PageHeader(overline = "Tuesday 14 October", title = "Notes", lede = "3 notes, 2 min 41 s")
            GroupLabel("Today")
            TrackRow(index = 1, title = "Groceries for the weekend", subtitle = "09:12 · WhatsApp", durationMs = 42_000, highlighted = true)
            TrackRow(index = 2, title = "Call back about the flat", subtitle = "08:40", durationMs = 71_000)
            GroupLabel("Yesterday")
            TrackRow(index = 3, title = "Idea for the logo", subtitle = "22:03", durationMs = 48_000, showHairline = false)
        }
        RecordDock(
            elapsedMs = { 12_000 },
            level = { 0.6f },
            isRecording = recording,
            onRecord = { recording = !recording },
            label = if (recording) "Stop" else "Record",
        )
        TextTabs(tabs = listOf("Notes", "Settings"), selected = tab, label = { it }, onSelect = { tab = it })
    }
}

/** Settings: sections of switch and value rows, a warning row and a single-choice pill group. */
@Composable
fun SettingsScreenExample() {
    var autoPaste by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("Standard") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Linen.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenMargin)
    ) {
        PageHeader(overline = "Ditto 1.0.1", title = "Settings")
        SectionTitle("Recording")
        SwitchLine("Automatic paste", checked = autoPaste, onCheckedChange = { autoPaste = it }, subtitle = "Drop the note into the chat when you stop")
        SettingLine("Microphone access", value = "Not granted", warning = true, onClick = {})
        Spacer(Modifier.height(14.dp))
        Text("Quality", style = Linen.type.label, color = Linen.colors.text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Small", "Standard", "Lossless").forEach {
                Pill(text = it, selected = it == quality, onClick = { quality = it })
            }
        }
        SectionTitle("Library")
        SwitchLine("Keep history", checked = history, onCheckedChange = { history = it })
        SettingLine("Storage used", value = "12 MB", onClick = {})
        SettingLine("Version", value = "1.0.1", showChevron = false, showHairline = false)
    }
}

/** Review sheet: big mono timer, waveform with playback progress, one primary action. */
@Composable
fun ReviewSheetExample() {
    val c = Linen.colors
    Box(Modifier.fillMaxSize().background(c.scrim), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(topStart = LinenDimens.SheetRadius, topEnd = LinenDimens.SheetRadius))
                .padding(ScreenMargin)
        ) {
            Text("NEW NOTE", style = Linen.type.overline, color = c.textSecondary)
            Text(formatClock(42_000), style = Linen.type.monoHero, color = c.text)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Default.PlayArrow, contentDescription = "Play", onClick = {})
                Spacer(Modifier.width(12.dp))
                Waveform(sampleLevels, Modifier.weight(1f).height(40.dp), progress = 0.35f)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Discard", onClick = {}, style = ActionStyle.Ghost, icon = Icons.Default.Delete)
                ActionButton("Share", onClick = {}, style = ActionStyle.Tonal, icon = Icons.Default.Share)
                ActionButton("Send", onClick = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Every button style and state side by side, plus the empty state. */
@Composable
fun ControlsExample() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Linen.colors.bg)
            .padding(ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionButton("Save note", onClick = {}, style = ActionStyle.Primary)
        ActionButton("Rename", onClick = {}, style = ActionStyle.Tonal)
        ActionButton("Cancel", onClick = {}, style = ActionStyle.Ghost)
        ActionButton("Stop recording", onClick = {}, style = ActionStyle.Record)
        ActionButton("Disabled", onClick = {}, enabled = false)
        EmptyState("No notes yet. Tap Record to make your first one.")
    }
}

@Composable
private fun Example(dark: Boolean, content: @Composable () -> Unit) {
    LinenTheme(darkTheme = dark) { content() }
}

@Preview(name = "List · light", heightDp = 720) @Composable private fun ListLight() = Example(false) { ListScreenExample() }
@Preview(name = "List · dark", heightDp = 720) @Composable private fun ListDark() = Example(true) { ListScreenExample() }
@Preview(name = "Settings · light", heightDp = 720) @Composable private fun SettingsLight() = Example(false) { SettingsScreenExample() }
@Preview(name = "Settings · dark", heightDp = 720) @Composable private fun SettingsDark() = Example(true) { SettingsScreenExample() }
@Preview(name = "Review · light", heightDp = 720) @Composable private fun ReviewLight() = Example(false) { ReviewSheetExample() }
@Preview(name = "Review · dark", heightDp = 720) @Composable private fun ReviewDark() = Example(true) { ReviewSheetExample() }
@Preview(name = "Controls · light") @Composable private fun ControlsLight() = Example(false) { ControlsExample() }
@Preview(name = "Controls · dark") @Composable private fun ControlsDark() = Example(true) { ControlsExample() }
