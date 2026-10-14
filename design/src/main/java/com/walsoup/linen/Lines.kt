package com.walsoup.linen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
private fun LineScaffold(
    onClick: (() -> Unit)?,
    showHairline: Boolean,
    role: Role? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LinenDimens.RowMinHeight)
                .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (showHairline) Hairline()
    }
}

@Composable
fun LineLabel(label: String, subtitle: String?, modifier: Modifier = Modifier, labelColor: Color = Linen.colors.text) {
    Column(modifier) {
        Text(label, style = Linen.type.body, color = labelColor)
        if (subtitle != null) {
            Text(subtitle, style = Linen.type.caption, color = Linen.colors.textSecondary)
        }
    }
}

/**
 * Settings row: label on the left, value and chevron on the right.
 * [warning] paints it terracotta for "needs attention" rows such as missing permissions.
 */
@Composable
fun SettingLine(
    label: String,
    value: String? = null,
    subtitle: String? = null,
    emphasis: Boolean = false,
    warning: Boolean = false,
    showHairline: Boolean = true,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = Linen.colors
    LineScaffold(onClick, showHairline, Role.Button) {
        LineLabel(label, subtitle, Modifier.weight(1f), if (warning) c.rec else c.text)
        if (value != null) {
            Text(
                text = value,
                style = if (emphasis || warning) Linen.type.label else Linen.type.bodySecondary,
                color = when {
                    warning -> c.rec
                    emphasis -> c.primary
                    else -> c.textSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).widthIn(max = 180.dp),
            )
        }
        if (showChevron && onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.padding(start = 2.dp).size(20.dp),
            )
        }
    }
}

/** Settings row with a switch; the whole row toggles. */
@Composable
fun SwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    showHairline: Boolean = true,
) {
    LineScaffold({ onCheckedChange(!checked) }, showHairline, Role.Switch) {
        LineLabel(label, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        LinenSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun LinenSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val c = Linen.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            checkedBorderColor = c.primary,
            uncheckedThumbColor = c.surface,
            uncheckedTrackColor = c.inactive,
            uncheckedBorderColor = c.inactive,
        ),
    )
}

/** Numbered list row: `01  Title / subtitle  00:42`. Numbers are monospace. */
@Composable
fun TrackRow(
    index: Int,
    title: String,
    subtitle: String,
    durationMs: Long,
    highlighted: Boolean = false,
    showHairline: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = Linen.colors
    LineScaffold(onClick, showHairline, Role.Button) {
        Text(
            text = index.toLong().twoDigits(),
            style = Linen.type.monoTiny,
            color = c.muted,
            modifier = Modifier.width(30.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (highlighted) Linen.type.label.copy(fontSize = Linen.type.body.fontSize) else Linen.type.body,
                color = if (highlighted) c.primary else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = Linen.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatClock(durationMs), style = Linen.type.monoSmall, color = c.textSecondary)
    }
}
