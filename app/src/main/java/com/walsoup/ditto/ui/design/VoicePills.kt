package com.walsoup.ditto.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.linen.Pill

@Composable
fun VoicePills(selected: VoiceFilter, onSelect: (VoiceFilter) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        items(VoiceFilter.entries) { f ->
            Pill(text = f.shortName, selected = f == selected, onClick = { onSelect(f) })
        }
    }
}
