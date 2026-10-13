package com.walsoup.ditto.ui.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.theme.Ditto
import kotlinx.coroutines.flow.StateFlow

val ScreenMargin = 24.dp

fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    return String.format("%02d:%02d", totalSec / 60, totalSec % 60)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Ditto.colors.hairline)
    )
}

@Composable
fun Wordmark(text: String, modifier: Modifier = Modifier) {
    val c = Ditto.colors
    Text(
        text = buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = c.primary)) { append(".") }
        },
        style = Ditto.type.display,
        color = c.text,
        modifier = modifier
    )
}

@Composable
fun PageHeader(overline: String, title: String, lede: String? = null) {
    val c = Ditto.colors
    Column(Modifier.padding(top = 16.dp)) {
        Text(overline.uppercase(), style = Ditto.type.overline, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        Wordmark(title)
        if (lede != null) {
            Spacer(Modifier.height(6.dp))
            Text(lede, style = Ditto.type.bodySecondary, color = c.textSecondary)
        }
        Spacer(Modifier.height(18.dp))
        Hairline()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Ditto.type.title,
        color = Ditto.colors.text,
        modifier = modifier.padding(top = 28.dp, bottom = 4.dp)
    )
}

@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = Ditto.type.overline,
        color = Ditto.colors.muted,
        modifier = modifier.padding(top = 20.dp, bottom = 2.dp)
    )
}

@Composable
private fun LineScaffold(
    onClick: (() -> Unit)?,
    showHairline: Boolean,
    role: Role? = null,
    content: @Composable RowScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        if (showHairline) Hairline()
    }
}

@Composable
fun LineLabel(label: String, subtitle: String?, modifier: Modifier = Modifier, labelColor: Color = Ditto.colors.text) {
    Column(modifier) {
        Text(label, style = Ditto.type.body, color = labelColor)
        if (subtitle != null) {
            Text(subtitle, style = Ditto.type.caption, color = Ditto.colors.textSecondary)
        }
    }
}

/** Label on the left, value and chevron on the right. */
@Composable
fun SettingLine(
    label: String,
    value: String? = null,
    subtitle: String? = null,
    emphasis: Boolean = false,
    warning: Boolean = false,
    showHairline: Boolean = true,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val c = Ditto.colors
    LineScaffold(onClick, showHairline, Role.Button) {
        LineLabel(label, subtitle, Modifier.weight(1f), if (warning) c.rec else c.text)
        if (value != null) {
            Text(
                text = value,
                style = if (emphasis || warning) Ditto.type.label else Ditto.type.bodySecondary,
                color = when {
                    warning -> c.rec
                    emphasis -> c.primary
                    else -> c.textSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).widthIn(max = 180.dp)
            )
        }
        if (showChevron && onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.padding(start = 2.dp).size(20.dp)
            )
        }
    }
}

@Composable
fun SwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    showHairline: Boolean = true
) {
    LineScaffold({ onCheckedChange(!checked) }, showHairline, Role.Switch) {
        LineLabel(label, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        DittoSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DittoSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val c = Ditto.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            checkedBorderColor = c.primary,
            uncheckedThumbColor = c.surface,
            uncheckedTrackColor = c.inactive,
            uncheckedBorderColor = c.inactive
        )
    )
}

/** Numbered tracklist row: 01  Title / subtitle  00:42 */
@Composable
fun TrackRow(
    index: Int,
    title: String,
    subtitle: String,
    durationMs: Long,
    highlighted: Boolean = false,
    showHairline: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val c = Ditto.colors
    LineScaffold(onClick, showHairline, Role.Button) {
        Text(
            text = String.format("%02d", index),
            style = Ditto.type.monoTiny,
            color = c.muted,
            modifier = Modifier.width(30.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (highlighted) Ditto.type.label.copy(fontSize = Ditto.type.body.fontSize) else Ditto.type.body,
                color = if (highlighted) c.primary else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, style = Ditto.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatClock(durationMs), style = Ditto.type.monoSmall, color = c.textSecondary)
    }
}

/** Thumb-zone dock: mono timer, level meter, 44dp pill button. */
@Composable
fun RecordDock(
    durationFlow: StateFlow<Long>,
    amplitudeFlow: StateFlow<Float>,
    isRecording: Boolean,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Ditto.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(32.dp))
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DockTimer(durationFlow, isRecording)
        LevelMeter(amplitudeFlow, isRecording)
        Button(
            onClick = onRecord,
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.container, contentColor = c.primary),
            modifier = Modifier.height(44.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Record", style = Ditto.type.label, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun DockTimer(durationFlow: StateFlow<Long>, isRecording: Boolean) {
    val c = Ditto.colors
    val ms by durationFlow.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (isRecording) c.rec else c.inactive)
        )
        Spacer(Modifier.width(9.dp))
        Text(if (isRecording) formatClock(ms) else "00:00", style = Ditto.type.monoDock, color = c.text)
    }
}

@Composable
fun LevelMeter(amplitudeFlow: StateFlow<Float>, active: Boolean, bars: Int = 5) {
    val c = Ditto.colors
    val amplitude by amplitudeFlow.collectAsState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(22.dp)
            .semantics { contentDescription = "Input level" }
    ) {
        repeat(bars) { i ->
            val shape = 1f - kotlin.math.abs(i - (bars - 1) / 2f) / bars
            val target = if (active) (6f + amplitude * 18f * shape * 1.6f).coerceIn(4f, 22f) else 6f
            val h by animateDpAsState(target.dp, tween(60), label = "meter$i")
            Box(
                Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (active) c.primary else c.inactive)
            )
        }
    }
}

/** Underlined text tabs. */
@Composable
fun <T> TextTabs(tabs: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Ditto.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSel = tab == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label(tab),
                    style = if (isSel) Ditto.type.label.copy(fontSize = Ditto.type.caption.fontSize.times(1.1f)) else Ditto.type.bodySecondary.copy(fontSize = Ditto.type.caption.fontSize.times(1.1f)),
                    color = if (isSel) c.text else c.muted
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSel) c.primary else Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val c = Ditto.colors
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.container else c.surface)
            .border(1.dp, if (selected) c.primary else c.hairline, RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = if (selected) Ditto.type.label.copy(fontSize = Ditto.type.caption.fontSize.times(1.08f)) else Ditto.type.bodySecondary.copy(fontSize = Ditto.type.caption.fontSize.times(1.08f)),
            color = if (selected) c.primary else c.text,
            maxLines = 1
        )
    }
}

@Composable
fun VoicePills(selected: VoiceFilter, onSelect: (VoiceFilter) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        items(VoiceFilter.entries) { f ->
            Pill(text = f.shortName, selected = f == selected, onClick = { onSelect(f) })
        }
    }
}

enum class ActionStyle { Primary, Tonal, Ghost, Record }

/** 46dp pill action button. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ActionStyle = ActionStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val c = Ditto.colors
    val shape = RoundedCornerShape(23.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = Ditto.type.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    val padding = PaddingValues(horizontal = 18.dp)
    val h = Modifier.height(46.dp)
    when (style) {
        ActionStyle.Ghost -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, c.hairline),
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
            modifier = modifier.then(h),
            content = content
        )
        else -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = when (style) {
                ActionStyle.Primary -> ButtonDefaults.buttonColors(c.primary, c.onPrimary, c.containerLow, c.muted)
                ActionStyle.Record -> ButtonDefaults.buttonColors(c.rec, Color.White, c.containerLow, c.muted)
                else -> ButtonDefaults.buttonColors(c.container, c.primary, c.containerLow, c.muted)
            },
            modifier = modifier.then(h),
            content = content
        )
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = Ditto.colors.container,
    tint: Color = Ditto.colors.primary
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = tint),
        modifier = modifier.size(size)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.45f))
    }
}

/**
 * Bar waveform. [levels] are 0..1; they are resampled to fit the width.
 * Bars before [progress] (0..1) use [playedColor].
 */
@Composable
fun Waveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Ditto.colors.inactive,
    playedColor: Color = Ditto.colors.primary,
    progress: Float = 0f,
    alignEnd: Boolean = false
) {
    Canvas(modifier) {
        val barW = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val count = ((size.width + gap) / (barW + gap)).toInt().coerceAtLeast(1)
        val minH = 3.dp.toPx()
        val samples: List<Float> = when {
            levels.isEmpty() -> emptyList()
            alignEnd -> levels.takeLast(count)
            else -> List(count) { i ->
                val from = i * levels.size / count
                val to = ((i + 1) * levels.size / count).coerceAtLeast(from + 1).coerceAtMost(levels.size)
                levels.subList(from, to).max()
            }
        }
        val offset = if (alignEnd) (count - samples.size) else 0
        for (i in 0 until count) {
            val v = samples.getOrNull(i - offset) ?: 0f
            val h = (minH + (size.height - minH) * v.coerceIn(0f, 1f)).coerceAtMost(size.height)
            val x = i * (barW + gap)
            val played = progress > 0f && i.toFloat() / count < progress
            drawRoundRect(
                color = if (played) playedColor else color,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text = text,
        style = Ditto.type.bodySecondary,
        color = Ditto.colors.textSecondary,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}