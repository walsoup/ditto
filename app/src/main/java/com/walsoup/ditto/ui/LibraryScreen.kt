package com.walsoup.ditto.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.HistoryItem
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.linen.Linen
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ActionStyle
import com.walsoup.linen.EmptyState
import com.walsoup.linen.GroupLabel
import com.walsoup.linen.Hairline
import com.walsoup.linen.PageHeader
import com.walsoup.linen.RoundIconButton
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SettingLine
import com.walsoup.linen.SwitchLine
import com.walsoup.linen.TrackRow
import kotlinx.coroutines.launch
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
    else -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(createdAt))
}

@Composable
fun LibraryScreen(
    historyManager: HistoryManager,
    playerHelper: AudioPlayerHelper,
    snackbarHostState: SnackbarHostState
) {
    val c = Linen.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by historyManager.history.collectAsState()
    val isPlaying by playerHelper.isPlaying.collectAsState()
    var playingId by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var historyEnabled by remember { mutableStateOf(historyManager.isHistoryEnabled) }
    var confirmClear by remember { mutableStateOf(false) }
    val groups = remember(history) { history.groupBy { dayLabel(it.createdAt) }.toList() }

    LazyColumn(
        modifier = Modifier
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
                    historyEnabled = it
                    historyManager.isHistoryEnabled = it
                }
            )
            if (history.isEmpty()) {
                EmptyState(if (historyEnabled) "No notes yet. Tap Record to make your first one." else "History is off.")
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
                        showHairline = expandedId != item.id,
                        onClick = { expandedId = if (expandedId == item.id) null else item.id }
                    )
                    AnimatedVisibility(visible = expandedId == item.id) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 30.dp, bottom = 14.dp)
                            ) {
                                RoundIconButton(
                                    icon = if (playingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playingThis) "Pause" else "Play",
                                    container = c.primary,
                                    tint = c.onPrimary,
                                    onClick = {
                                        if (playingThis) {
                                            playerHelper.pause()
                                        } else {
                                            val file = File(item.filePath)
                                            if (file.exists()) {
                                                playingId = item.id
                                                playerHelper.playFile(file) { playingId = null }
                                            }
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
                                        if (playingId == item.id) {
                                            playerHelper.stop()
                                            playingId = null
                                        }
                                        expandedId = null
                                        historyManager.stageDeleteItem(item.id)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Note deleted",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                historyManager.restoreItem(item)
                                            } else {
                                                historyManager.permanentlyDeleteFile(item.filePath)
                                            }
                                        }
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
                    playerHelper.stop()
                    playingId = null
                    historyManager.clearHistory()
                    confirmClear = false
                }) { Text("Delete all", style = Linen.type.label, color = c.rec) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", style = Linen.type.label, color = c.primary) }
            },
            containerColor = c.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
