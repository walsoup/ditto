package com.walsoup.linen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Primary: the one main action on a screen. Tonal: secondary actions.
 * Ghost: dismiss or destructive-but-safe actions. Record: live or "stop" actions.
 */
enum class ActionStyle { Primary, Tonal, Ghost, Record }

/** 46dp pill button. Labels never wrap. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ActionStyle = ActionStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Linen.colors
    val shape = RoundedCornerShape(LinenDimens.ButtonRadius)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = Linen.type.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    val padding = PaddingValues(horizontal = 18.dp)
    val height = Modifier.height(LinenDimens.ButtonHeight)
    when (style) {
        ActionStyle.Ghost -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, c.hairline),
            contentPadding = padding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
            modifier = modifier.then(height),
            content = content,
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
            modifier = modifier.then(height),
            content = content,
        )
    }
}

/** Round tonal icon button, 44dp by default. [contentDescription] is required. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = LinenDimens.TouchTarget,
    container: Color = Linen.colors.container,
    tint: Color = Linen.colors.primary,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = tint),
        modifier = modifier.size(size),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.45f))
    }
}

/** Selectable chip for picking one option from a short set. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Linen.colors
    val shape = RoundedCornerShape(LinenDimens.PillRadius)
    val textStyle = (if (selected) Linen.type.label else Linen.type.bodySecondary)
        .copy(fontSize = Linen.type.caption.fontSize.times(1.08f))
    Row(
        modifier = modifier
            .height(LinenDimens.PillHeight)
            .clip(shape)
            .background(if (selected) c.container else c.surface)
            .border(1.dp, if (selected) c.primary else c.hairline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = textStyle, color = if (selected) c.primary else c.text, maxLines = 1)
    }
}

/** Bottom navigation as underlined words. */
@Composable
fun <T> TextTabs(
    tabs: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Linen.colors
    val size = Linen.type.caption.fontSize.times(1.1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LinenDimens.TouchTarget),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label(tab),
                    style = (if (isSelected) Linen.type.label else Linen.type.bodySecondary).copy(fontSize = size),
                    color = if (isSelected) c.text else c.muted,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSelected) c.primary else Color.Transparent)
                )
            }
        }
    }
}
