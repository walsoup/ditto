package com.walsoup.linen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** `mm:ss`, always monospace in the UI. */
fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    return "${(totalSec / 60).twoDigits()}:${(totalSec % 60).twoDigits()}"
}

internal fun Long.twoDigits(): String = toString().padStart(2, '0')

/**
 * Thumb-zone dock: status dot and monospace timer, level meter, 44dp pill button.
 * Use it instead of a large round record button.
 *
 * [elapsedMs] and [level] are lambdas so per-frame values only redraw the timer and
 * meter: `val ms by recorder.elapsed.collectAsState()` then `elapsedMs = { ms }`.
 */
@Composable
fun RecordDock(
    elapsedMs: () -> Long,
    level: () -> Float,
    isRecording: Boolean,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Record",
    icon: ImageVector? = null,
) {
    val c = Linen.colors
    val shape = RoundedCornerShape(LinenDimens.DockRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(LinenDimens.DockHeight)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.hairline, shape)
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DockTimer(elapsedMs, isRecording)
        LevelMeter(level, isRecording)
        Button(
            onClick = onRecord,
            shape = RoundedCornerShape(LinenDimens.TouchTarget / 2),
            contentPadding = PaddingValues(horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.container, contentColor = c.primary),
            modifier = Modifier.height(LinenDimens.TouchTarget),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(label, style = Linen.type.label, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun DockTimer(elapsedMs: () -> Long, isRecording: Boolean) {
    val c = Linen.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (isRecording) c.rec else c.inactive)
        )
        Spacer(Modifier.width(9.dp))
        Text(if (isRecording) formatClock(elapsedMs()) else "00:00", style = Linen.type.monoDock, color = c.text)
    }
}

/** Vertical bars driven by a 0..1 input level, tallest in the middle. */
@Composable
fun LevelMeter(level: () -> Float, active: Boolean, modifier: Modifier = Modifier, bars: Int = 5) {
    val c = Linen.colors
    val amplitude = level()
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(22.dp)
            .semantics { contentDescription = "Input level" },
    ) {
        repeat(bars) { i ->
            val weight = 1f - abs(i - (bars - 1) / 2f) / bars
            val target = if (active) (6f + amplitude * 18f * weight * 1.6f).coerceIn(4f, 22f) else 6f
            val height by animateDpAsState(target.dp, tween(60), label = "meter$i")
            Box(
                Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (active) c.primary else c.inactive)
            )
        }
    }
}

/**
 * Bar waveform: 3dp bars, 2dp gaps. [levels] are 0..1 and get resampled to the width,
 * or, with [alignEnd], show the newest values on the right (live input).
 * Bars before [progress] (0..1) use [playedColor].
 */
@Composable
fun Waveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Linen.colors.inactive,
    playedColor: Color = Linen.colors.primary,
    progress: Float = 0f,
    alignEnd: Boolean = false,
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
        val offset = if (alignEnd) count - samples.size else 0
        for (i in 0 until count) {
            val v = samples.getOrNull(i - offset) ?: 0f
            val h = (minH + (size.height - minH) * v.coerceIn(0f, 1f)).coerceAtMost(size.height)
            val played = progress > 0f && i.toFloat() / count < progress
            drawRoundRect(
                color = if (played) playedColor else color,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
