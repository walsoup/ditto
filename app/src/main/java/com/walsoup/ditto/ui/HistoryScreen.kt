package com.walsoup.ditto.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walsoup.ditto.SageBg
import com.walsoup.ditto.SageBorder
import com.walsoup.ditto.SageCard
import com.walsoup.ditto.SageContainer
import com.walsoup.ditto.SageContainerLow
import com.walsoup.ditto.SagePrimary
import com.walsoup.ditto.SageSubtext
import com.walsoup.ditto.SageText
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.HistoryItem
import com.walsoup.ditto.data.HistoryManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    historyManager: HistoryManager,
    playerHelper: AudioPlayerHelper
) {
    val context = LocalContext.current
    val historyItems by historyManager.history.collectAsState()
    val isPlaying by playerHelper.isPlaying.collectAsState()
    var currentPlayingId by remember { mutableStateOf<String?>(null) }
    var isHistoryEnabled by remember { mutableStateOf(historyManager.isHistoryEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SageBg)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "History",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = SageText
                )
                Text(
                    text = "${historyItems.size} saved recordings",
                    fontSize = 12.sp,
                    color = SageSubtext
                )
            }

            if (historyItems.isNotEmpty()) {
                IconButton(
                    onClick = {
                        playerHelper.stop()
                        historyManager.clearHistory()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear all",
                        tint = SageSubtext
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Toggle Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SageCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Save History (Privacy)",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = SageText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isHistoryEnabled)
                            "Audio notes are retained locally on your phone"
                        else
                            "History disabled; files cleared after use",
                        fontSize = 12.sp,
                        color = SageSubtext
                    )
                }

                Switch(
                    checked = isHistoryEnabled,
                    onCheckedChange = { enabled ->
                        isHistoryEnabled = enabled
                        historyManager.isHistoryEnabled = enabled
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SagePrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFFB0BEC5),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isHistoryEnabled) "No recordings yet" else "History is paused for privacy",
                        fontSize = 14.sp,
                        color = SageSubtext
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(historyItems, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        isPlaying = isPlaying && currentPlayingId == item.id,
                        onPlayToggle = {
                            val file = File(item.filePath)
                            if (file.exists()) {
                                if (isPlaying && currentPlayingId == item.id) {
                                    playerHelper.pause()
                                } else {
                                    currentPlayingId = item.id
                                    playerHelper.playFile(file) {
                                        currentPlayingId = null
                                    }
                                }
                            }
                        },
                        onCopy = {
                            val file = File(item.filePath)
                            if (file.exists()) {
                                AudioFileManager.copyAudioToClipboard(
                                    context,
                                    file,
                                    autoPaste = historyManager.isAutoPasteEnabled
                                )
                            }
                        },
                        onShare = {
                            val file = File(item.filePath)
                            if (file.exists()) {
                                val shareIntent = AudioFileManager.createShareIntent(context, file)
                                context.startActivity(shareIntent)
                            }
                        },
                        onDelete = {
                            if (currentPlayingId == item.id) {
                                playerHelper.stop()
                            }
                            historyManager.deleteItem(item.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    item: HistoryItem,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SageCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SageContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = onPlayToggle,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = SagePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = item.fileName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = SageText
                        )
                        val seconds = (item.durationMs / 1000) % 60
                        val minutes = (item.durationMs / 1000) / 60
                        val dateFormatted = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            .format(Date(item.createdAt))
                        Text(
                            text = String.format("%02d:%02d • %s", minutes, seconds, dateFormatted),
                            fontSize = 12.sp,
                            color = SageSubtext,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badge: format & filter
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SageContainerLow,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "${item.format.displayName} • ${item.filter.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SagePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row: Copy & Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCopy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SagePrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SageContainer,
                        contentColor = SagePrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
