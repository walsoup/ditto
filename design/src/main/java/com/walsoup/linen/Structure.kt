package com.walsoup.linen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** 1dp rule. Sections are separated by hairlines, never by cards or shadows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Linen.colors.hairline)
    )
}

/** Display-size title ending in a primary-colored period: "Ditto." */
@Composable
fun Wordmark(text: String, modifier: Modifier = Modifier) {
    val c = Linen.colors
    Text(
        text = buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = c.primary)) { append(".") }
        },
        style = Linen.type.display,
        color = c.text,
        modifier = modifier,
    )
}

/** Top of a screen: uppercase overline (often the date), wordmark, optional lede, hairline. */
@Composable
fun PageHeader(overline: String, title: String, lede: String? = null) {
    val c = Linen.colors
    Column(Modifier.padding(top = 16.dp)) {
        Text(overline.uppercase(), style = Linen.type.overline, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        Wordmark(title)
        if (lede != null) {
            Spacer(Modifier.height(6.dp))
            Text(lede, style = Linen.type.bodySecondary, color = c.textSecondary)
        }
        Spacer(Modifier.height(18.dp))
        Hairline()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Linen.type.title,
        color = Linen.colors.text,
        modifier = modifier.padding(top = 28.dp, bottom = 4.dp),
    )
}

/** Small uppercase label above a group of rows, e.g. a day in a list. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = Linen.type.overline,
        color = Linen.colors.muted,
        modifier = modifier.padding(top = 20.dp, bottom = 2.dp),
    )
}

/** One line of secondary text that says what's missing and how to fill it. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Linen.type.bodySecondary,
        color = Linen.colors.textSecondary,
        modifier = modifier.padding(vertical = 16.dp),
    )
}
