package com.walsoup.ditto.service

import com.walsoup.linen.LinenPalette
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import com.walsoup.ditto.core.util.HapticFeedbackHelper
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.walsoup.ditto.core.audio.PastePolicy
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.data.AppFilterPolicy
import com.walsoup.ditto.data.HistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class FloatingBubbleService : Service() {

    companion object {
        const val EXTRA_AUTO_RECORD = "com.walsoup.ditto.EXTRA_AUTO_RECORD"
        const val ACTION_STOP_RECORDING = "com.walsoup.ditto.ACTION_STOP_RECORDING"
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "ditto_overlay_channel"

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

    private val isExporting = MutableStateFlow(false)
    private val exportProgress = MutableStateFlow(0f)

    private var stopInFlight = false
    @Volatile
    private var cancelStopRequested = false
    private var notifTicker: Job? = null

    private var micMuteRegistered = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val transientPreviewFiles = mutableSetOf<String>()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            if (overlayState.value == OverlayUiState.RECORDING && !recorderEngine.isPaused.value) {
                recorderEngine.pauseRecording()
                Toast.makeText(this, "Recording paused \u2014 audio interrupted", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val micMuteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_MICROPHONE_MUTE_CHANGED) return
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (am.isMicrophoneMute &&
                    overlayState.value == OverlayUiState.RECORDING &&
                    !recorderEngine.isPaused.value
                ) {
                    recorderEngine.pauseRecording()
                    Toast.makeText(
                        this@FloatingBubbleService,
                        "Microphone muted \u2014 recording paused",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.w("Ditto", "Mic-mute check failed", e)
            }
        }
    }

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
        registerMicMuteReceiver()
        observeLimitReached()

        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.onCreate()

        overlayView = ComposeView(this).apply {
            lifecycleOwner.attachToView(this)
            setContent {
                val isVisible by isOverlayVisible
                if (isVisible) {
                    MaterialTheme(
                        colorScheme = lightColorScheme(
                            primary = LinenPalette.Forest,
                            surface = LinenPalette.Linen
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
        if (intent?.action == ACTION_STOP_RECORDING) {
            stopRecordingInternal()
            return START_STICKY
        }
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
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isMicrophoneMute) {
            Toast.makeText(this, "Microphone is muted", Toast.LENGTH_SHORT).show()
            return false
        }
        cancelStopRequested = false
        requestMicAudioFocus()
        val started = recorderEngine.startRecording()
        if (started) {
            overlayState.value = OverlayUiState.RECORDING
            HapticFeedbackHelper.vibrateStart(this)
            startNotifTicker()
        } else {
            abandonMicAudioFocus()
            postIdleNotification()
        }
        return started
    }

    private fun stopRecordingInternal() {
        if (overlayState.value != OverlayUiState.RECORDING || stopInFlight) return
        stopInFlight = true
        HapticFeedbackHelper.vibrateStop(this)
        val currentDuration = recorderEngine.durationMs.value
        recordedDurationMs.value = currentDuration
        abandonMicAudioFocus()
        stopNotifTicker()
        serviceScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    try {
                        recorderEngine.stopRecordingAsync()
                    } catch (e: Exception) {
                        Log.w("Ditto", "stopRecording failed", e)
                        null
                    }
                }
                if (file != null && file.exists() && cancelStopRequested) {
                    // The user tapped Cancel while the take was being written: honour it now
                    // instead of silently keeping, committing and pasting the recording.
                    cancelStopRequested = false
                    withContext(Dispatchers.IO) { runCatching { file.delete() } }
                    collapseOverlay()
                } else if (file != null && file.exists()) {
                    recordedWavFile.value = file
                    selectedFilter.value = VoiceFilter.RAW
                    activePlayableFile.value = file
                    if (historyManager.isAutoPasteEnabled) {
                        val foregroundPkg = DittoAccessibilityService.currentForegroundPackage.value
                        val targetFormat = PastePolicy.targetFormatForPackage(
                            foregroundPkg,
                            historyManager.selectedFormat
                        )
                        val fileToSend = withContext(Dispatchers.IO) {
                            try {
                                AudioFileManager.exportFile(
                                    context = this@FloatingBubbleService,
                                    inputFile = file,
                                    filter = VoiceFilter.RAW,
                                    format = targetFormat
                                )
                            } catch (e: Exception) {
                                Log.w("Ditto", "Auto-paste export failed, sending raw take", e)
                                file
                            }
                        }
                        withContext(Dispatchers.IO) {
                            AudioFileManager.copyAndRecordToHistory(
                                context = this@FloatingBubbleService,
                                file = fileToSend,
                                durationMs = currentDuration,
                                filter = VoiceFilter.RAW,
                                format = targetFormat,
                                historyManager = historyManager,
                                targetPackage = PastePolicy.autoStopTargetPackage(foregroundPkg)
                            )
                        }
                        if (historyManager.isHistoryEnabled &&
                            file.absolutePath != fileToSend.absolutePath
                        ) {
                            runCatching { file.delete() }
                        }
                        collapseOverlay()
                    } else {
                        overlayState.value = OverlayUiState.REVIEW_AND_FILTER
                    }
                } else {
                    collapseOverlay()
                }
            } finally {
                stopInFlight = false
                postIdleNotification()
            }
        }
    }

    private fun cancelRecordingInternal() {
        if (stopInFlight) {
            // A stop is mid-flight (joining the writer and converting the take): the engine's
            // file refs are already nulled, so cancelling now would do nothing and the take
            // would still be saved and pasted. Defer the cancel to the stop coroutine.
            cancelStopRequested = true
            return
        }
        stopNotifTicker()
        abandonMicAudioFocus()
        try {
            recorderEngine.cancelRecording()
        } catch (e: Exception) {
            Log.w("Ditto", "Cancel recording failed", e)
        }
        postIdleNotification()
        collapseOverlay()
    }

    private fun requestMicAudioFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(
                        audioFocusChangeListener,
                        Handler(Looper.getMainLooper())
                    )
                    .build()
                audioFocusRequest = request
                if (am.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w("Ditto", "Mic audio focus request denied")
                }
            } else {
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w("Ditto", "Mic audio focus request denied")
                }
            }
        } catch (e: Exception) {
            Log.w("Ditto", "Audio focus request failed", e)
        }
    }

    private fun abandonMicAudioFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w("Ditto", "Abandon audio focus failed", e)
        }
    }

    private fun registerMicMuteReceiver() {
        if (micMuteRegistered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(micMuteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(micMuteReceiver, filter)
            }
            micMuteRegistered = true
        } catch (e: Exception) {
            Log.w("Ditto", "Mic-mute receiver registration failed", e)
        }
    }

    private fun unregisterMicMuteReceiver() {
        if (!micMuteRegistered) return
        micMuteRegistered = false
        try {
            unregisterReceiver(micMuteReceiver)
        } catch (e: Exception) {
            Log.w("Ditto", "Mic-mute receiver unregister failed", e)
        }
    }

    private fun observeLimitReached() {
        serviceScope.launch {
            recorderEngine.limitReached.collect { reached ->
                if (reached && overlayState.value == OverlayUiState.RECORDING) {
                    Toast.makeText(
                        this@FloatingBubbleService,
                        "Max recording length reached",
                        Toast.LENGTH_SHORT
                    ).show()
                    stopRecordingInternal()
                }
            }
        }
    }

    private fun startNotifTicker() {
        stopNotifTicker()
        updateRecordingNotification(recorderEngine.durationMs.value)
        notifTicker = serviceScope.launch {
            while (isActive) {
                delay(1000)
                updateRecordingNotification(recorderEngine.durationMs.value)
            }
        }
    }

    private fun stopNotifTicker() {
        notifTicker?.cancel()
        notifTicker = null
    }

    private fun trackPreview(file: File) {
        synchronized(transientPreviewFiles) {
            val it = transientPreviewFiles.iterator()
            while (it.hasNext()) {
                val old = it.next()
                if (old != file.absolutePath) {
                    runCatching { File(old).delete() }
                    it.remove()
                }
            }
            transientPreviewFiles.add(file.absolutePath)
        }
    }

    private fun cleanupTakeFiles(keepPath: String?) {
        try {
            recordedWavFile.value?.let { raw ->
                if (raw.absolutePath != keepPath && raw.exists()) {
                    runCatching { raw.delete() }
                }
            }
            synchronized(transientPreviewFiles) {
                val it = transientPreviewFiles.iterator()
                while (it.hasNext()) {
                    val path = it.next()
                    if (path != keepPath) {
                        runCatching { File(path).delete() }
                        it.remove()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("Ditto", "Take cleanup failed", e)
        }
    }

    private fun startForegroundNotification() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ditto Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = buildIdleNotification(channelId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun launchPendingIntent(): PendingIntent {
        val launchIntent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildIdleNotification(channelId: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ditto is active")
            .setContentText("Tap floating mic to record")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(launchPendingIntent())
            .setOngoing(true)
            .build()
    }

    private fun postIdleNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIF_ID, buildIdleNotification(CHANNEL_ID))
        } catch (e: Exception) {
            Log.w("Ditto", "Idle notification update failed", e)
        }
    }

    private fun updateRecordingNotification(elapsedMs: Long) {
        val totalSeconds = elapsedMs / 1000
        val elapsed = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording… $elapsed")
            .setContentText("Tap Stop to finish")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(launchPendingIntent())
            .addAction(R.drawable.ic_launcher_monochrome, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIF_ID, notification)
        } catch (e: Exception) {
            Log.w("Ditto", "Recording notification update failed", e)
        }
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
            color = LinenPalette.Slate,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }

    @Composable
    private fun OverlayContent() {
        val state by overlayState
        val isPlaying by playerHelper.isPlaying.collectAsState()
        val exporting by isExporting.collectAsState()
        val progress by exportProgress.collectAsState()
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
                    val isPaused by recorderEngine.isPaused.collectAsState()
                    // Pastel sage recording pill
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = LinenPalette.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LinenPalette.Hairline),
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
                                    .background(if (isPaused) LinenPalette.Muted else LinenPalette.Terracotta)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            RecordingBubbleTimer(recorderEngine.durationMs)

                            Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                onClick = {
                                    recorderEngine.togglePause()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(LinenPalette.Sage)
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isPaused) "Resume" else "Pause",
                                    tint = LinenPalette.Forest,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    stopRecordingInternal()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(LinenPalette.Sage)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = LinenPalette.Forest,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    cancelRecordingInternal()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = LinenPalette.Muted,
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
                        colors = CardDefaults.cardColors(containerColor = LinenPalette.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LinenPalette.Hairline),
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
                                    color = LinenPalette.Slate,
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
                                            .background(LinenPalette.Sage)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = LinenPalette.Forest,
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
                                            tint = LinenPalette.Muted,
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
                                                    val preview = withContext(Dispatchers.IO) {
                                                        try {
                                                            AudioFileManager.previewFile(
                                                                this@FloatingBubbleService,
                                                                original,
                                                                filter
                                                            )
                                                        } catch (e: Exception) {
                                                            Log.w("Ditto", "Preview failed for filter ${filter.name}", e)
                                                            null
                                                        }
                                                    }
                                                    if (preview != null) {
                                                        trackPreview(preview)
                                                        activePlayableFile.value = preview
                                                        playerHelper.playFile(preview)
                                                    } else {
                                                        Toast.makeText(
                                                            this@FloatingBubbleService,
                                                            "Couldn't preview this filter",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        label = {
                                            Text(filter.displayName, fontSize = 12.sp)
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = LinenPalette.Sage,
                                            selectedLabelColor = LinenPalette.Forest,
                                            containerColor = LinenPalette.Linen,
                                            labelColor = LinenPalette.Slate
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
                                        if (isExporting.value) return@Button
                                        playerHelper.stop()
                                        val original = recordedWavFile.value
                                        val existingPrepared = activePlayableFile.value
                                        if (original != null) {
                                            val targetFormat = PastePolicy.targetFormatForPackage(
                                                currentPkg,
                                                historyManager.selectedFormat
                                            )
                                            scope.launch {
                                                isExporting.value = true
                                                exportProgress.value = 0f
                                                try {
                                                    val fileToSend = if (existingPrepared != null &&
                                                        ((targetFormat == AudioFormatType.M4A && existingPrepared.name.endsWith(".m4a")) ||
                                                         (targetFormat == AudioFormatType.WAV && existingPrepared.name.endsWith(".wav")))) {
                                                        existingPrepared
                                                    } else {
                                                        withContext(Dispatchers.IO) {
                                                            AudioFileManager.exportFile(
                                                                context = this@FloatingBubbleService,
                                                                inputFile = original,
                                                                filter = selectedFilter.value,
                                                                format = targetFormat,
                                                                onProgress = { exportProgress.value = it }
                                                            )
                                                        }
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        if (isWhatsApp) {
                                                            try {
                                                                val committed = withContext(Dispatchers.IO) {
                                                                    historyManager.commitRecording(
                                                                        sourceFile = fileToSend,
                                                                        durationMs = recordedDurationMs.value,
                                                                        filter = selectedFilter.value,
                                                                        targetPackage = currentPkg
                                                                    )
                                                                }
                                                                val sendFile = committed
                                                                    ?.filePath
                                                                    ?.let { File(it) }
                                                                    ?.takeIf { it.exists() }
                                                                    ?: fileToSend
                                                                val shareIntent = AudioFileManager.createShareIntent(
                                                                    this@FloatingBubbleService,
                                                                    sendFile
                                                                ).apply {
                                                                    setPackage(currentPkg)
                                                                }
                                                                startActivity(shareIntent)
                                                                cleanupTakeFiles(committed?.filePath)
                                                            } catch (e: Exception) {
                                                                Log.w("Ditto", "Couldn't send audio to WhatsApp", e)
                                                                Toast.makeText(
                                                                    this@FloatingBubbleService,
                                                                    "Couldn't send audio",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.IO) {
                                                                AudioFileManager.copyAndRecordToHistory(
                                                                    context = this@FloatingBubbleService,
                                                                    file = fileToSend,
                                                                    durationMs = recordedDurationMs.value,
                                                                    filter = selectedFilter.value,
                                                                    format = targetFormat,
                                                                    historyManager = historyManager,
                                                                    targetPackage = DittoAccessibilityService.currentForegroundPackage.value
                                                                )
                                                            }
                                                            cleanupTakeFiles(fileToSend.absolutePath)
                                                        }
                                                        collapseOverlay()
                                                    }
                                                } finally {
                                                    isExporting.value = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !exporting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LinenPalette.Forest,
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
                                        if (isExporting.value) return@FilledTonalButton
                                        playerHelper.stop()
                                        val original = recordedWavFile.value
                                        val existingPrepared = activePlayableFile.value
                                        if (original != null) {
                                            val targetFormat = PastePolicy.targetFormatForPackage(
                                                DittoAccessibilityService.currentForegroundPackage.value,
                                                historyManager.selectedFormat
                                            )
                                            scope.launch {
                                                isExporting.value = true
                                                exportProgress.value = 0f
                                                try {
                                                    val fileToSend = if (existingPrepared != null &&
                                                        ((targetFormat == AudioFormatType.M4A && existingPrepared.name.endsWith(".m4a")) ||
                                                         (targetFormat == AudioFormatType.WAV && existingPrepared.name.endsWith(".wav")))) {
                                                        existingPrepared
                                                    } else {
                                                        withContext(Dispatchers.IO) {
                                                            AudioFileManager.exportFile(
                                                                context = this@FloatingBubbleService,
                                                                inputFile = original,
                                                                filter = selectedFilter.value,
                                                                format = targetFormat,
                                                                onProgress = { exportProgress.value = it }
                                                            )
                                                        }
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        val committed = withContext(Dispatchers.IO) {
                                                            historyManager.commitRecording(
                                                                sourceFile = fileToSend,
                                                                durationMs = recordedDurationMs.value,
                                                                filter = selectedFilter.value
                                                            )
                                                        }
                                                        val sendFile = committed
                                                            ?.filePath
                                                            ?.let { File(it) }
                                                            ?.takeIf { it.exists() }
                                                            ?: fileToSend
                                                        try {
                                                            val shareIntent = AudioFileManager.createShareIntent(
                                                                this@FloatingBubbleService,
                                                                sendFile
                                                            )
                                                            startActivity(shareIntent)
                                                        } catch (e: Exception) {
                                                            Log.w("Ditto", "Couldn't share audio", e)
                                                            Toast.makeText(
                                                                this@FloatingBubbleService,
                                                                "Couldn't share audio",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                        cleanupTakeFiles(committed?.filePath ?: fileToSend.absolutePath)
                                                        collapseOverlay()
                                                    }
                                                } finally {
                                                    isExporting.value = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !exporting,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = LinenPalette.Sage,
                                        contentColor = LinenPalette.Forest
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

                            if (exporting) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = LinenPalette.Forest,
                                    trackColor = LinenPalette.Sage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Preparing…",
                                    fontSize = 12.sp,
                                    color = LinenPalette.Muted
                                )
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
        stopNotifTicker()
        unregisterMicMuteReceiver()
        abandonMicAudioFocus()
        serviceJob.cancel()
        playerHelper.stop()
        try {
            recorderEngine.cancelRecording()
        } catch (e: Exception) {
            Log.w("Ditto", "Cancel recording during destroy failed", e)
        }
        try {
            historyManager.close()
        } catch (e: Exception) {
            Log.w("Ditto", "HistoryManager close failed", e)
        }
        lifecycleOwner.onDestroy()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
