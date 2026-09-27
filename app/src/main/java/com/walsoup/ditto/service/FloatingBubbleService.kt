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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import android.view.View
import kotlinx.coroutines.delay
import com.walsoup.ditto.data.BubbleShape
import com.walsoup.ditto.data.BubbleSize
import com.walsoup.ditto.data.BubbleTheme
import com.walsoup.ditto.MainActivity
import com.walsoup.ditto.R
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.AudioRecorderEngine
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.data.AppFilterPolicy
import com.walsoup.ditto.data.HistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FloatingBubbleService : Service() {

    companion object {
        const val EXTRA_AUTO_RECORD = "com.walsoup.ditto.EXTRA_AUTO_RECORD"

        var isRunning: Boolean = false
            private set

        var instance: FloatingBubbleService? = null
            private set

        fun toggleRecording(context: Context) {
            val current = instance
            if (current != null) {
                current.performToggleRecording()
            } else {
                if (android.provider.Settings.canDrawOverlays(context)) {
                    val intent = Intent(context, FloatingBubbleService::class.java).apply {
                        putExtra(EXTRA_AUTO_RECORD, true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

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
    private val isOverlayVisible = mutableStateOf(true)
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
        isRunning = true
        instance = this
        DittoTileService.updateTile(this)
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
                val isVisible by isOverlayVisible
                if (isVisible) {
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
        }

        windowManager.addView(overlayView, windowParams)
        observeForegroundApp()
    }

    private fun observeForegroundApp() {
        val initialPkg = DittoAccessibilityService.instance?.getActiveForegroundPackage()
        if (!initialPkg.isNullOrBlank()) {
            DittoAccessibilityService.updateForegroundPackage(initialPkg)
        }

        serviceScope.launch {
            combine(
                historyManager.isAppFilterEnabledFlow,
                historyManager.selectedAppPackagesFlow,
                DittoAccessibilityService.currentForegroundPackage
            ) { isFilterEnabled, selectedApps, currentPackage ->
                Triple(isFilterEnabled, selectedApps, currentPackage)
            }.collect { (isFilterEnabled, selectedApps, currentPackage) ->
                android.util.Log.d("DittoDebug", "Evaluating: filterEnabled=$isFilterEnabled, currentPkg=$currentPackage, selectedCount=${selectedApps.size}")
                evaluateOverlayVisibility(isFilterEnabled, selectedApps, currentPackage)
            }
        }
    }

    private fun evaluateOverlayVisibility(
        isFilterEnabled: Boolean,
        selectedApps: Set<String>,
        currentPackage: String?
    ) {
        val isServiceRunning = DittoAccessibilityService.instance != null
        val isOverlayActive = overlayState.value != OverlayUiState.COLLAPSED

        val shouldBeVisible = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = isFilterEnabled,
            selectedPackages = selectedApps,
            currentPackage = currentPackage,
            isAccessibilityServiceRunning = isServiceRunning,
            isOverlayActive = isOverlayActive
        )
        android.util.Log.d("DittoDebug", "shouldBeVisible=$shouldBeVisible, isOverlayActive=$isOverlayActive, isFilterEnabled=$isFilterEnabled, isServiceRunning=$isServiceRunning, currentPackage=$currentPackage")
        updateOverlayVisibility(shouldBeVisible)
    }

    private fun updateOverlayVisibility(visible: Boolean) {
        android.util.Log.d("DittoDebug", "updateOverlayVisibility: visible=$visible, previous=${isOverlayVisible.value}")
        isOverlayVisible.value = visible

        if (::overlayView.isInitialized) {
            if (visible) {
                windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                windowParams.alpha = 1f
            } else {
                windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                windowParams.alpha = 0f
            }
            try {
                windowManager.updateViewLayout(overlayView, windowParams)
                overlayView.requestLayout()
            } catch (e: Exception) {
                android.util.Log.e("DittoDebug", "Error updating view layout", e)
            }
        }
    }

    private fun collapseOverlay() {
        overlayState.value = OverlayUiState.COLLAPSED
        evaluateOverlayVisibility(
            historyManager.isAppFilterEnabled,
            historyManager.selectedAppPackages,
            DittoAccessibilityService.currentForegroundPackage.value
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_AUTO_RECORD, false) == true) {
            serviceScope.launch(Dispatchers.Main) {
                updateOverlayVisibility(true)
                startRecordingInternal()
            }
        }
        return START_STICKY
    }

    fun performToggleRecording() {
        serviceScope.launch(Dispatchers.Main) {
            when (overlayState.value) {
                OverlayUiState.COLLAPSED -> {
                    updateOverlayVisibility(true)
                    startRecordingInternal()
                }
                OverlayUiState.RECORDING -> {
                    stopRecordingInternal()
                }
                OverlayUiState.REVIEW_AND_FILTER -> {
                    collapseOverlay()
                }
            }
        }
    }

    private fun startRecordingInternal(): Boolean {
        val started = recorderEngine.startRecording()
        if (started) {
            overlayState.value = OverlayUiState.RECORDING
        }
        return started
    }

    private fun stopRecordingInternal() {
        val currentDuration = recorderEngine.durationMs.value
        recordedDurationMs.value = currentDuration
        val file = recorderEngine.stopRecording()
        if (file != null && file.exists()) {
            recordedWavFile.value = file
            selectedFilter.value = VoiceFilter.RAW
            activePlayableFile.value = file
            if (historyManager.isAutoPasteEnabled) {
                val targetFormat = if (DittoAccessibilityService.currentForegroundPackage.value?.contains("whatsapp") == true) {
                    AudioFormatType.M4A
                } else {
                    historyManager.selectedFormat
                }
                serviceScope.launch {
                    val fileToSend = withContext(Dispatchers.IO) {
                        AudioFileManager.generateFilteredAudio(
                            context = this@FloatingBubbleService,
                            inputFile = file,
                            filter = VoiceFilter.RAW,
                            targetFormat = targetFormat
                        )
                    }
                    withContext(Dispatchers.Main) {
                        AudioFileManager.copyAndRecordToHistory(
                            context = this@FloatingBubbleService,
                            file = fileToSend,
                            durationMs = currentDuration,
                            filter = VoiceFilter.RAW,
                            format = targetFormat,
                            historyManager = historyManager
                        )
                        collapseOverlay()
                    }
                }
            } else {
                overlayState.value = OverlayUiState.REVIEW_AND_FILTER
            }
        } else {
            collapseOverlay()
        }
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

    private fun snapToEdge(bubbleSizeDp: androidx.compose.ui.unit.Dp) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val bubblePx = (bubbleSizeDp.value * displayMetrics.density).toInt()
        val marginPx = (16 * displayMetrics.density).toInt()
        val midPoint = windowParams.x + bubblePx / 2
        val targetX = if (midPoint < screenWidth / 2) {
            marginPx
        } else {
            (screenWidth - bubblePx - marginPx).coerceAtLeast(marginPx)
        }
        windowParams.x = targetX
        try {
            windowManager.updateViewLayout(overlayView, windowParams)
        } catch (_: Exception) {}
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

        val bubbleSize by historyManager.bubbleSizeFlow.collectAsState()
        val bubbleShape by historyManager.bubbleShapeFlow.collectAsState()
        val bubbleTheme by historyManager.bubbleThemeFlow.collectAsState()
        val bubbleOpacity by historyManager.bubbleOpacityFlow.collectAsState()
        val bubbleIdleDim by historyManager.bubbleIdleDimFlow.collectAsState()
        val bubbleSnapToEdge by historyManager.bubbleSnapToEdgeFlow.collectAsState()

        var isInteracting by remember { mutableStateOf(false) }
        var isIdle by remember { mutableStateOf(false) }

        LaunchedEffect(state, isInteracting, bubbleIdleDim) {
            if (state == OverlayUiState.COLLAPSED && bubbleIdleDim && !isInteracting) {
                delay(3500)
                isIdle = true
            } else {
                isIdle = false
            }
        }

        val targetAlpha = if (state == OverlayUiState.COLLAPSED) {
            if (isIdle) {
                (bubbleOpacity * 0.38f).coerceAtLeast(0.18f)
            } else {
                bubbleOpacity
            }
        } else {
            1.0f
        }

        val animatedAlpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = 300),
            label = "bubbleAlpha"
        )

        Box(
            modifier = Modifier
                .padding(6.dp)
                .pointerInput(bubbleSnapToEdge, bubbleSize) {
                    detectDragGestures(
                        onDragStart = {
                            isInteracting = true
                        },
                        onDragEnd = {
                            isInteracting = false
                            if (bubbleSnapToEdge && overlayState.value == OverlayUiState.COLLAPSED) {
                                snapToEdge(bubbleSize.sizeDp)
                            }
                        },
                        onDragCancel = {
                            isInteracting = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            isInteracting = true
                            updateWindowPosition(dragAmount.x, dragAmount.y)
                        }
                    )
                }
        ) {
            when (state) {
                OverlayUiState.COLLAPSED -> {
                    val shape = bubbleShape.getShape(bubbleSize.sizeDp)
                    Surface(
                        shape = shape,
                        shadowElevation = (4.dp * animatedAlpha).coerceAtLeast(0.dp),
                        color = bubbleTheme.containerColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, bubbleTheme.borderColor),
                        modifier = Modifier
                            .size(bubbleSize.sizeDp)
                            .graphicsLayer { alpha = animatedAlpha }
                            .clip(shape)
                            .clickable {
                                isInteracting = true
                                startRecordingInternal()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record",
                                tint = bubbleTheme.iconColor,
                                modifier = Modifier.size(bubbleSize.iconSizeDp)
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
                                    stopRecordingInternal()
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
                                    collapseOverlay()
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
                                            collapseOverlay()
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
                                                val targetFormat = if (DittoAccessibilityService.currentForegroundPackage.value?.contains("whatsapp") == true) {
                                                    AudioFormatType.M4A
                                                } else {
                                                    historyManager.selectedFormat
                                                }
                                                scope.launch {
                                                    val filtered = withContext(Dispatchers.IO) {
                                                        AudioFileManager.generateFilteredAudio(
                                                            this@FloatingBubbleService,
                                                            original,
                                                            filter,
                                                            targetFormat
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

                            val currentPkg by DittoAccessibilityService.currentForegroundPackage.collectAsState()
                            val isWhatsApp = currentPkg?.contains("whatsapp") == true

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        playerHelper.stop()
                                        val original = recordedWavFile.value
                                        val existingPrepared = activePlayableFile.value
                                        if (original != null) {
                                            val targetFormat = if (isWhatsApp) AudioFormatType.M4A else historyManager.selectedFormat
                                            scope.launch {
                                                val fileToSend = if (existingPrepared != null &&
                                                    ((targetFormat == AudioFormatType.M4A && existingPrepared.name.endsWith(".m4a")) ||
                                                     (targetFormat == AudioFormatType.WAV && existingPrepared.name.endsWith(".wav")))) {
                                                    existingPrepared
                                                } else {
                                                    withContext(Dispatchers.IO) {
                                                        AudioFileManager.generateFilteredAudio(
                                                            context = this@FloatingBubbleService,
                                                            inputFile = original,
                                                            filter = selectedFilter.value,
                                                            targetFormat = targetFormat
                                                        )
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    if (isWhatsApp) {
                                                        try {
                                                            val shareIntent = AudioFileManager.createShareIntent(
                                                                this@FloatingBubbleService,
                                                                fileToSend
                                                            ).apply {
                                                                setPackage(currentPkg)
                                                            }
                                                            startActivity(shareIntent)
                                                            historyManager.addRecording(
                                                                file = fileToSend,
                                                                durationMs = recordedDurationMs.value,
                                                                filter = selectedFilter.value,
                                                                format = targetFormat
                                                            )
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    } else {
                                                        AudioFileManager.copyAndRecordToHistory(
                                                            context = this@FloatingBubbleService,
                                                            file = fileToSend,
                                                            durationMs = recordedDurationMs.value,
                                                            filter = selectedFilter.value,
                                                            format = targetFormat,
                                                            historyManager = historyManager
                                                        )
                                                    }
                                                    collapseOverlay()
                                                }
                                            }
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
                                        imageVector = if (isWhatsApp) Icons.Default.Share else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isWhatsApp) "Send to WhatsApp" else "Paste Audio",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        playerHelper.stop()
                                        val original = recordedWavFile.value
                                        val existingPrepared = activePlayableFile.value
                                        if (original != null) {
                                            val targetFormat = if (DittoAccessibilityService.currentForegroundPackage.value?.contains("whatsapp") == true) {
                                                AudioFormatType.M4A
                                            } else {
                                                historyManager.selectedFormat
                                            }
                                            scope.launch {
                                                val fileToSend = if (existingPrepared != null &&
                                                    ((targetFormat == AudioFormatType.M4A && existingPrepared.name.endsWith(".m4a")) ||
                                                     (targetFormat == AudioFormatType.WAV && existingPrepared.name.endsWith(".wav")))) {
                                                    existingPrepared
                                                } else {
                                                    withContext(Dispatchers.IO) {
                                                        AudioFileManager.generateFilteredAudio(
                                                            context = this@FloatingBubbleService,
                                                            inputFile = original,
                                                            filter = selectedFilter.value,
                                                            targetFormat = targetFormat
                                                        )
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    val shareIntent = AudioFileManager.createShareIntent(
                                                        this@FloatingBubbleService,
                                                        fileToSend
                                                    )
                                                    startActivity(shareIntent)
                                                    historyManager.addRecording(
                                                        file = fileToSend,
                                                        durationMs = recordedDurationMs.value,
                                                        filter = selectedFilter.value,
                                                        format = targetFormat
                                                    )
                                                    collapseOverlay()
                                                }
                                            }
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
        isRunning = false
        instance = null
        DittoTileService.updateTile(this)
        serviceJob.cancel()
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
