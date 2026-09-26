package com.walsoup.ditto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.walsoup.ditto.MainActivity
import com.walsoup.ditto.R
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.AudioRecorderEngine
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.data.HistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private lateinit var lifecycleOwner: OverlayLifecycleOwner

    private lateinit var recorderEngine: AudioRecorderEngine
    private lateinit var playerHelper: AudioPlayerHelper
    private lateinit var historyManager: HistoryManager

    private val windowParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 40
        y = 350
    }

    private val overlayState = mutableStateOf(OverlayUiState.COLLAPSED)
    private val recordedWavFile = mutableStateOf<File?>(null)
    private val recordedDurationMs = mutableStateOf(0L)
    private val selectedFilter = mutableStateOf(VoiceFilter.RAW)
    private val activePlayableFile = mutableStateOf<File?>(null)

    enum class OverlayUiState {
        COLLAPSED,
        RECORDING,
        REVIEW_AND_FILTER
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        recorderEngine = AudioRecorderEngine(this)
        playerHelper = AudioPlayerHelper(this)
        historyManager = HistoryManager(this)

        startForegroundNotification()

        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.onCreate()

        overlayView = ComposeView(this).apply {
            lifecycleOwner.attachToView(this)
            setContent {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF2D6A4F),
                        surface = Color(0xFFFAF9F6)
                    )
                ) {
                    OverlayContent()
                }
            }
        }

        windowManager.addView(overlayView, windowParams)
    }

    private fun startForegroundNotification() {
        val channelId = "ditto_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ditto Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ditto is active")
            .setContentText("Tap floating mic to record")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun updateWindowPosition(dx: Float, dy: Float) {
        val newX = (windowParams.x + dx.toInt()).coerceAtLeast(0)
        val newY = (windowParams.y + dy.toInt()).coerceAtLeast(0)
        if (newX != windowParams.x || newY != windowParams.y) {
            windowParams.x = newX
            windowParams.y = newY
            try {
                windowManager.updateViewLayout(overlayView, windowParams)
            } catch (_: Exception) {}
        }
    }

    @Composable
    private fun RecordingBubbleTimer(durationFlow: kotlinx.coroutines.flow.StateFlow<Long>) {
        val durationMs by durationFlow.collectAsState()
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        Text(
            text = String.format("%02d:%02d", minutes, seconds),
            color = Color(0xFF1F2421),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }

    @Composable
    private fun OverlayContent() {
        val state by overlayState
        val isPlaying by playerHelper.isPlaying.collectAsState()
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .padding(6.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        updateWindowPosition(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            when (state) {
                OverlayUiState.COLLAPSED -> {
                    // Simple pastel sage circle
                    Surface(
                        shape = CircleShape,
                        shadowElevation = 5.dp,
                        color = Color(0xFFE8F1EC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E6E2)),
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable {
                                val started = recorderEngine.startRecording()
                                if (started) {
                                    overlayState.value = OverlayUiState.RECORDING
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record",
                                tint = Color(0xFF2D6A4F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                OverlayUiState.RECORDING -> {
                    // Pastel sage recording pill
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E6E2)),
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD47255))
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            RecordingBubbleTimer(recorderEngine.durationMs)

                            Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                onClick = {
                                    val currentDuration = recorderEngine.durationMs.value
                                    recordedDurationMs.value = currentDuration
                                    val file = recorderEngine.stopRecording()
                                    if (file != null && file.exists()) {
                                        recordedWavFile.value = file
                                        selectedFilter.value = VoiceFilter.RAW
                                        activePlayableFile.value = file
                                        // Auto-paste if enabled: stop recording and directly paste audio!
                                        if (historyManager.isAutoPasteEnabled) {
                                            AudioFileManager.copyAndRecordToHistory(
                                                context = this@FloatingBubbleService,
                                                file = file,
                                                durationMs = currentDuration,
                                                filter = VoiceFilter.RAW,
                                                format = historyManager.selectedFormat,
                                                historyManager = historyManager
                                            )
                                            overlayState.value = OverlayUiState.COLLAPSED
                                        } else {
                                            overlayState.value = OverlayUiState.REVIEW_AND_FILTER
                                        }
                                    } else {
                                        overlayState.value = OverlayUiState.COLLAPSED
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F1EC))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color(0xFF2D6A4F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    recorderEngine.cancelRecording()
                                    overlayState.value = OverlayUiState.COLLAPSED
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color(0xFF79747E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                OverlayUiState.REVIEW_AND_FILTER -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E6E2)),
                        modifier = Modifier
                            .width(310.dp)
                            .padding(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Filter & Auto-Paste",
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1F2421),
                                    fontSize = 14.sp
                                )

                                Row {
                                    IconButton(
                                        onClick = {
                                            val fileToPlay = activePlayableFile.value ?: recordedWavFile.value
                                            if (fileToPlay != null) {
                                                if (isPlaying) playerHelper.pause() else playerHelper.playFile(fileToPlay)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE8F1EC))
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFF2D6A4F),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = {
                                            playerHelper.stop()
                                            overlayState.value = OverlayUiState.COLLAPSED
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color(0xFF79747E),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Filter chips row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                VoiceFilter.values().forEach { filter ->
                                    val isSelected = selectedFilter.value == filter
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedFilter.value = filter
                                            val original = recordedWavFile.value
                                            if (original != null) {
                                                scope.launch {
                                                    val filtered = withContext(Dispatchers.IO) {
                                                        AudioFileManager.generateFilteredAudio(
                                                            this@FloatingBubbleService,
                                                            original,
                                                            filter,
                                                            historyManager.selectedFormat
                                                        )
                                                    }
                                                    activePlayableFile.value = filtered
                                                    playerHelper.playFile(filtered)
                                                }
                                            }
                                        },
                                        label = {
                                            Text(filter.displayName, fontSize = 12.sp)
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFE8F1EC),
                                            selectedLabelColor = Color(0xFF2D6A4F),
                                            containerColor = Color(0xFFFAF9F6),
                                            labelColor = Color(0xFF1F2421)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        playerHelper.stop()
                                        val fileToSend = activePlayableFile.value ?: recordedWavFile.value
                                        if (fileToSend != null) {
                                            AudioFileManager.copyAndRecordToHistory(
                                                context = this@FloatingBubbleService,
                                                file = fileToSend,
                                                durationMs = recordedDurationMs.value,
                                                filter = selectedFilter.value,
                                                format = historyManager.selectedFormat,
                                                historyManager = historyManager
                                            )
                                            overlayState.value = OverlayUiState.COLLAPSED
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2D6A4F),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste Audio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        playerHelper.stop()
                                        val fileToSend = activePlayableFile.value ?: recordedWavFile.value
                                        if (fileToSend != null) {
                                            val shareIntent = AudioFileManager.createShareIntent(
                                                this@FloatingBubbleService,
                                                fileToSend
                                            )
                                            startActivity(shareIntent)
                                            historyManager.addRecording(
                                                fileToSend,
                                                recordedDurationMs.value,
                                                selectedFilter.value,
                                                historyManager.selectedFormat
                                            )
                                            overlayState.value = OverlayUiState.COLLAPSED
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFFE8F1EC),
                                        contentColor = Color(0xFF2D6A4F)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerHelper.stop()
        recorderEngine.cancelRecording()
        lifecycleOwner.onDestroy()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
