package com.walsoup.ditto.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.HistoryItem
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ActionStyle
import com.walsoup.linen.EmptyState
import com.walsoup.linen.GroupLabel
import com.walsoup.linen.Hairline
import com.walsoup.linen.Linen
import com.walsoup.linen.LinenDimens
import com.walsoup.linen.PageHeader
import com.walsoup.linen.Pill
import com.walsoup.linen.RoundIconButton
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SettingLine
import com.walsoup.linen.SwitchLine
import com.walsoup.linen.TrackRow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun trackTitle(context: Context, item: HistoryItem): String =
    AudioFileManager.appLabel(context, item.targetPackage) ?: "Voice note"

fun trackSubtitle(item: HistoryItem, includeDay: Boolean): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.createdAt))
    val day = when {
        !includeDay -> null
        DateUtils.isToday(item.createdAt) -> null
        DateUtils.isToday(item.createdAt + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(item.createdAt))
    }
    return listOfNotNull(day, time, item.filter.shortName).joinToString(" · ")
}

private fun dayLabel(createdAt: Long): String = when {
    DateUtils.isToday(createdAt) -> "Today"
    DateUtils.isToday(createdAt + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(createdAt))
}

private enum class LibrarySort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LONGEST("Longest")
}

@Composable
fun LibraryScreen(
    historyManager: HistoryManager,
    playerHelper: AudioPlayerHelper,
    activePlayingId: StateFlow<String?>,
    onPlayItem: (HistoryItem) -> Unit,
    onStopPlayback: () -> Unit,
    onDeleteItem: (HistoryItem) -> Unit,
    onOpenRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Linen.colors
    val context = LocalContext.current
    val history by historyManager.history.collectAsState()
    val isPlaying by playerHelper.isPlaying.collectAsState()
    val playingId by activePlayingId.collectAsState()
    val progressMs by playerHelper.progressMs.collectAsState()
    val playDurationMs by playerHelper.durationMs.collectAsState()
    var expandedId by remember { mutableStateOf<String?>(null) }
    var historyEnabled by remember { mutableStateOf(historyManager.isHistoryEnabled) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDisableHistory by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.NEWEST) }

    val visible = remember(history, query, sort) {
        val filtered = if (query.isBlank()) {
            history
        } else {
            history.filter { it.fileName.contains(query, ignoreCase = true) }
        }
        when (sort) {
            LibrarySort.NEWEST -> filtered.sortedByDescending { it.createdAt }
            LibrarySort.OLDEST -> filtered.sortedBy { it.createdAt }
            LibrarySort.LONGEST -> filtered.sortedByDescending { it.durationMs }
        }
    }
    val groups = remember(visible) { visible.groupBy { dayLabel(it.createdAt) }.toList() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenMargin)
    ) {
        item {
            PageHeader(
                overline = if (history.size == 1) "1 note" else "${history.size} notes",
                title = "Library"
            )
            SwitchLine(
                label = "Keep history",
                subtitle = if (historyEnabled) "Notes stay on this phone" else "Notes are deleted after use",
                checked = historyEnabled,
                onCheckedChange = {
                    if (!it) {
                        confirmDisableHistory = true
                    } else {
                        historyEnabled = true
                        historyManager.isHistoryEnabled = true
                    }
                }
            )
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LibrarySearchRow(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibrarySort.entries.forEach { s ->
                        Pill(
                            text = s.label,
                            selected = sort == s,
                            onClick = { sort = s }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (history.isEmpty()) {
                EmptyState(if (historyEnabled) "No notes yet. Tap Record to make your first one." else "History is off.")
                if (historyEnabled) {
                    Spacer(Modifier.height(8.dp))
                    ActionButton(text = "Record a note", onClick = onOpenRecord)
                }
            } else if (visible.isEmpty()) {
                EmptyState("No notes match \"$query\".")
            }
        }

        groups.forEach { (label, items) ->
            item(key = "group-$label") { GroupLabel(label) }
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                val playingThis = isPlaying && playingId == item.id
                Column {
                    TrackRow(
                        index = i + 1,
                        title = trackTitle(context, item),
                        subtitle = trackSubtitle(item, includeDay = false),
                        durationMs = item.durationMs,
                        highlighted = playingThis || expandedId == item.id,
                        showHairline = !playingThis && expandedId != item.id,
                        onClick = { expandedId = if (expandedId == item.id) null else item.id }
                    )
                    if (playingThis) {
                        val total = if (playDurationMs > 0) playDurationMs else item.durationMs.toInt()
                        val fraction = if (total > 0) (progressMs.toFloat() / total).coerceIn(0f, 1f) else 0f
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(c.hairline)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(2.dp)
                                    .background(c.primary)
                            )
                        }
                    }
                    AnimatedVisibility(visible = expandedId == item.id) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 30.dp, bottom = 14.dp, top = 10.dp)
                            ) {
                                RoundIconButton(
                                    icon = if (playingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playingThis) "Pause" else "Play",
                                    container = c.primary,
                                    tint = c.onPrimary,
                                    onClick = {
                                        if (playingThis) {
                                            playerHelper.pause()
                                        } else if (playingId == item.id && progressMs > 0) {
                                            playerHelper.resume()
                                        } else {
                                            onPlayItem(item)
                                        }
                                    }
                                )
                                ActionButton(
                                    text = "Copy",
                                    icon = Icons.Default.ContentCopy,
                                    style = ActionStyle.Tonal,
                                    onClick = {
                                        val file = File(item.filePath)
                                        if (file.exists()) AudioFileManager.copyAudioToClipboard(context, file, autoPaste = false)
                                    }
                                )
                                RoundIconButton(
                                    icon = Icons.Default.Share,
                                    contentDescription = "Share",
                                    onClick = {
                                        val file = File(item.filePath)
                                        if (file.exists()) context.startActivity(AudioFileManager.createShareIntent(context, file))
                                    }
                                )
                                RoundIconButton(
                                    icon = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    container = c.containerLow,
                                    tint = c.textSecondary,
                                    onClick = {
                                        expandedId = null
                                        onDeleteItem(item)
                                    }
                                )
                            }
                            Hairline()
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                SettingLine(
                    label = "Delete all notes",
                    warning = true,
                    showHairline = false,
                    showChevron = false,
                    onClick = { confirmClear = true }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all notes?", style = Linen.type.title, color = c.text) },
            text = {
                Text(
                    "This removes ${history.size} notes from this phone. It can't be undone.",
                    style = Linen.type.bodySecondary,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onStopPlayback()
                    historyManager.clearHistory()
                    confirmClear = false
                }) { Text("Delete all", style = Linen.type.label, color = c.rec) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", style = Linen.type.label, color = c.primary) }
            },
            containerColor = c.surface,
            shape = RoundedCornerShape(LinenDimens.DialogRadius)
        )
    }

    if (confirmDisableHistory) {
        AlertDialog(
            onDismissRequest = { confirmDisableHistory = false },
            title = { Text("Turn off history?", style = Linen.type.title, color = c.text) },
            text = {
                Text(
                    "This deletes ${history.size} saved notes from this phone. New notes won't be kept.",
                    style = Linen.type.bodySecondary,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onStopPlayback()
                    historyEnabled = false
                    historyManager.isHistoryEnabled = false
                    confirmDisableHistory = false
                }) { Text("Delete & turn off", style = Linen.type.label, color = c.rec) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisableHistory = false }) { Text("Keep", style = Linen.type.label, color = c.primary) }
            },
            containerColor = c.surface,
            shape = RoundedCornerShape(LinenDimens.DialogRadius)
        )
    }
}

@Composable
private fun LibrarySearchRow(query: String, onQueryChange: (String) -> Unit) {
    val c = Linen.colors
    val shape = RoundedCornerShape(LinenDimens.CardRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.hairline, shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search notes...",
                        style = Linen.type.bodySecondary,
                        color = c.textSecondary
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = Linen.type.body.fontSize,
                        color = c.text
                    ),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Search notes" }
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = c.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
