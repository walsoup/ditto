package com.walsoup.ditto

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.viewModels
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.AudioRecorderEngine
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.ditto.service.FloatingBubbleService
import com.walsoup.linen.Linen
import com.walsoup.linen.LinenTheme
import com.walsoup.ditto.ui.AppSelectionDialog
import com.walsoup.ditto.ui.LibraryScreen
import com.walsoup.ditto.ui.RecordSheetHost
import com.walsoup.ditto.ui.RecordStage
import com.walsoup.ditto.ui.SettingsScreen
import com.walsoup.linen.EmptyState
import com.walsoup.linen.PageHeader
import com.walsoup.linen.RecordDock
import com.walsoup.linen.ScreenMargin
import com.walsoup.linen.SectionTitle
import com.walsoup.linen.SettingLine
import com.walsoup.linen.SwitchLine
import com.walsoup.linen.TextTabs
import com.walsoup.linen.TrackRow
import com.walsoup.ditto.ui.trackSubtitle
import com.walsoup.ditto.ui.trackTitle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScreenTab(val title: String) {
    RECORD("Record"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    private lateinit var historyManager: HistoryManager
    private val recordStageModel: RecordStageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )

        historyManager = HistoryManager(this)

        setContent {
            LinenTheme {
                AppRoot(
                    recorderEngine = recordStageModel.recorderEngine,
                    playerHelper = recordStageModel.playerHelper,
                    historyManager = historyManager,
                    stageModel = recordStageModel
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Rotation recreates the activity without finishing: keep the take, the mic and the
        // player alive (they live in the ViewModel). Only tear down when the user really leaves.
        if (isFinishing) {
            recordStageModel.playerHelper.stop()
            recordStageModel.recorderEngine.cancelRecording()
            historyManager.close()
        }
    }
}

/**
 * Survives configuration changes so a rotation keeps the take, the review sheet and the live
 * microphone. The recorder and player live here on purpose: an activity-scoped recorder leaks an
 * open AudioRecord on rotation while the new activity starts a second one.
 */
class RecordStageViewModel(app: Application) : AndroidViewModel(app) {
    val recorderEngine = AudioRecorderEngine(app)
    val playerHelper = AudioPlayerHelper(app)
    var stage: RecordStage by mutableStateOf<RecordStage>(RecordStage.Idle)
}

/**
 * Single source of truth for which library item is loaded in the player.
 * Lives in AppRoot (above the tab switch) so leaving the tab can't orphan audio.
 */
class SharedPlayback {
    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    fun track(id: String?, playerHelper: AudioPlayerHelper, file: File) {
        if (!file.exists()) return
        _playingId.value = id
        try {
            playerHelper.playFile(file) { _playingId.value = null }
        } catch (_: Exception) {
            _playingId.value = null
        }
    }

    fun clear(playerHelper: AudioPlayerHelper) {
        playerHelper.stop()
        _playingId.value = null
    }
}

@Composable
fun AppRoot(
    recorderEngine: AudioRecorderEngine,
    playerHelper: AudioPlayerHelper,
    historyManager: HistoryManager,
    stageModel: RecordStageViewModel
) {
    val c = Linen.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableStateOf(ScreenTab.RECORD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val sharedPlayback = remember { SharedPlayback() }
    val isRecording by recorderEngine.isRecording.collectAsState()
    val history by historyManager.history.collectAsState()
    val lastTargetPackage = history.firstOrNull { it.targetPackage != null }?.targetPackage
    val permissions = rememberPermissions(historyManager)

    // Undoable delete lives in AppRoot's scope so a tab switch can't orphan the file:
    // HistoryManager's own IO scope does the list/file work off the main thread.
    fun deleteWithUndo(item: com.walsoup.ditto.core.audio.HistoryItem) {
        sharedPlayback.clear(playerHelper)
        historyManager.stageDeleteItem(item.id)
        appScope.launch {
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

    BackHandler(enabled = selectedTab != ScreenTab.RECORD && stageModel.stage is RecordStage.Idle) {
        selectedTab = ScreenTab.RECORD
    }

    val startRecording = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!permissions.mic) {
            permissions.requestMic()
        } else {
            playerHelper.stop()
            sharedPlayback.clear(playerHelper)
            if (recorderEngine.startRecording()) {
                stageModel.stage = RecordStage.Recording
            } else {
                Toast.makeText(context, "Microphone unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Scaffold(
            containerColor = c.bg,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = c.text,
                        contentColor = c.bg,
                        actionColor = if (c.isDark) c.onPrimary else c.container
                    )
                }
            },
            bottomBar = {
                Column(
                    Modifier
                        .background(c.bg)
                        .navigationBarsPadding()
                ) {
                    val elapsedMs by recorderEngine.durationMs.collectAsState()
                    val level by recorderEngine.amplitude.collectAsState()
                    RecordDock(
                        elapsedMs = { elapsedMs },
                        level = { level },
                        isRecording = isRecording,
                        onRecord = startRecording,
                        icon = Icons.Default.Mic
                    )
                    TextTabs(
                        tabs = ScreenTab.entries,
                        selected = selectedTab,
                        label = { it.title },
                        onSelect = { selectedTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "tabCrossfade"
                ) { tab ->
                    when (tab) {
                        ScreenTab.RECORD -> HomeScreen(
                            historyManager = historyManager,
                            playerHelper = playerHelper,
                            sharedPlayback = sharedPlayback,
                            permissions = permissions,
                            onOpenLibrary = { selectedTab = ScreenTab.LIBRARY }
                        )
                        ScreenTab.LIBRARY -> LibraryScreen(
                            historyManager = historyManager,
                            playerHelper = playerHelper,
                            activePlayingId = sharedPlayback.playingId,
                            onPlayItem = { item ->
                                sharedPlayback.track(item.id, playerHelper, File(item.filePath))
                            },
                            onStopPlayback = { sharedPlayback.clear(playerHelper) },
                            onDeleteItem = ::deleteWithUndo,
                            onOpenRecord = { selectedTab = ScreenTab.RECORD }
                        )
                        ScreenTab.SETTINGS -> SettingsScreen(historyManager)
                    }
                }
            }
        }

        RecordSheetHost(
            stage = stageModel.stage,
            onStageChange = { stageModel.stage = it },
            recorderEngine = recorderEngine,
            playerHelper = playerHelper,
            historyManager = historyManager,
            lastTargetPackage = lastTargetPackage,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun HomeScreen(
    historyManager: HistoryManager,
    playerHelper: AudioPlayerHelper,
    sharedPlayback: SharedPlayback,
    permissions: PermissionsState,
    onOpenLibrary: () -> Unit
) {
    val c = Linen.colors
    val context = LocalContext.current
    val history by historyManager.history.collectAsState()
    val isPlaying by playerHelper.isPlaying.collectAsState()
    val playingId by sharedPlayback.playingId.collectAsState()
    val progressMs by playerHelper.progressMs.collectAsState()
    val isAppFilterEnabled by historyManager.isAppFilterEnabledFlow.collectAsState()
    val selectedPackages by historyManager.selectedAppPackagesFlow.collectAsState()
    var setupExpanded by rememberSaveable { mutableStateOf(false) }
    var voiceMenuOpen by remember { mutableStateOf(false) }
    var defaultFilter by remember { mutableStateOf(historyManager.defaultFilter) }
    var format by remember { mutableStateOf(historyManager.selectedFormat) }
    var showAppSelection by remember { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenMargin)
    ) {
        PageHeader(overline = today, title = "Ditto", lede = "Record a note. It lands in your chat.")

        AnimatedVisibility(
            visible = !permissions.allGranted,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                SettingLine(
                    label = "Finish setup",
                    value = "${permissions.grantedCount}/4",
                    warning = true,
                    onClick = { setupExpanded = !setupExpanded }
                )
                AnimatedVisibility(visible = setupExpanded) {
                    Column(Modifier.padding(start = 12.dp)) {
                        PermissionLine("Microphone", "To record notes", permissions.mic) { permissions.requestMic() }
                        PermissionLine("Display over apps", "Shows the bubble in your chats", permissions.overlay) { permissions.openOverlay() }
                        PermissionLine("Auto-paste", "Accessibility service that taps Paste for you", permissions.accessibility) { permissions.openAccessibility() }
                        PermissionLine("Notifications", "Keeps the recorder running", permissions.notifications) { permissions.requestNotifications() }
                    }
                }
            }
        }

        SwitchLine(
            label = "Bubble",
            subtitle = when {
                !permissions.bubbleOn -> "Tap it inside any chat to record"
                isAppFilterEnabled && selectedPackages.size == 1 -> "Shows in 1 app"
                isAppFilterEnabled -> "Shows in ${selectedPackages.size} apps"
                else -> "Shows over every app"
            },
            checked = permissions.bubbleOn,
            onCheckedChange = { permissions.setBubble(it) }
        )
        if (permissions.bubbleOn && isAppFilterEnabled) {
            SettingLine(
                label = "Bubble apps",
                value = "${selectedPackages.size} chosen",
                onClick = { showAppSelection = true }
            )
        }
        Box {
            SettingLine(
                label = "Voice",
                value = defaultFilter.shortName,
                onClick = { voiceMenuOpen = true }
            )
            DropdownMenu(
                expanded = voiceMenuOpen,
                onDismissRequest = { voiceMenuOpen = false },
                containerColor = c.surface
            ) {
                VoiceFilter.entries.forEach { f ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                f.displayName,
                                style = if (f == defaultFilter) Linen.type.label else Linen.type.body,
                                color = if (f == defaultFilter) c.primary else c.text
                            )
                        },
                        onClick = {
                            defaultFilter = f
                            historyManager.defaultFilter = f
                            voiceMenuOpen = false
                        }
                    )
                }
            }
        }
        SettingLine(
            label = "Format",
            value = if (format == AudioFormatType.M4A) "M4A" else "WAV",
            subtitle = if (format == AudioFormatType.M4A) "Small files, works in WhatsApp" else "Lossless, larger files",
            onClick = {
                format = if (format == AudioFormatType.M4A) AudioFormatType.WAV else AudioFormatType.M4A
                historyManager.selectedFormat = format
            }
        )

        SectionTitle("Recent")
        if (history.isEmpty()) {
            EmptyState("No notes yet. Tap Record to make your first one.")
        } else {
            history.take(3).forEachIndexed { i, item ->
                TrackRow(
                    index = i + 1,
                    title = trackTitle(context, item),
                    subtitle = trackSubtitle(item, includeDay = true),
                    durationMs = item.durationMs,
                    highlighted = isPlaying && playingId == item.id,
                    onClick = {
                        if (isPlaying && playingId == item.id) {
                            playerHelper.pause()
                        } else if (playingId == item.id && progressMs > 0) {
                            playerHelper.resume()
                        } else {
                            sharedPlayback.track(item.id, playerHelper, File(item.filePath))
                        }
                    }
                )
            }
            if (history.size > 3) {
                SettingLine(label = "All notes", value = "${history.size}", showHairline = false, onClick = onOpenLibrary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showAppSelection) {
        AppSelectionDialog(historyManager = historyManager, onDismissRequest = { showAppSelection = false })
    }
}

@Composable
private fun PermissionLine(label: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    SettingLine(
        label = label,
        subtitle = subtitle,
        value = if (granted) "On" else "Allow",
        emphasis = granted,
        warning = !granted,
        showChevron = !granted,
        onClick = if (granted) null else onClick
    )
}

@Stable
class PermissionsState(private val context: Context, private val historyManager: HistoryManager) {
    var mic by mutableStateOf(false); private set
    var overlay by mutableStateOf(false); private set
    var accessibility by mutableStateOf(false); private set
    var notifications by mutableStateOf(false); private set
    var bubbleOn by mutableStateOf(false); private set

    internal var micLauncher: (() -> Unit)? = null
    internal var notificationLauncher: (() -> Unit)? = null

    val grantedCount: Int get() = listOf(mic, overlay, accessibility, notifications).count { it }
    val allGranted: Boolean get() = grantedCount == 4

    fun refresh() {
        mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        overlay = Settings.canDrawOverlays(context)
        accessibility = isAccessibilityServiceEnabled(context)
        notifications = isNotificationPermissionGranted(context)
        if (historyManager.isFloatingServiceEnabled && overlay && mic && !FloatingBubbleService.isRunning) {
            startBubbleService(context)
        }
        // isFloatingServiceEnabled stays true after the system kills the service;
        // the switch must reflect whether the bubble is actually running.
        bubbleOn = FloatingBubbleService.isRunning
    }

    internal fun onMicResult(granted: Boolean) { mic = granted }
    internal fun onNotificationResult(granted: Boolean) { notifications = granted }

    fun requestMic() { micLauncher?.invoke() }

    fun openOverlay() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        )
    }

    fun openAccessibility() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher?.invoke()
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    fun setBubble(enable: Boolean) {
        if (enable) {
            if (!overlay) { openOverlay(); return }
            if (!mic) { requestMic(); return }
            historyManager.isFloatingServiceEnabled = true
            startBubbleService(context)
        } else {
            historyManager.isFloatingServiceEnabled = false
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
        bubbleOn = enable
    }
}

@Composable
fun rememberPermissions(historyManager: HistoryManager): PermissionsState {
    val context = LocalContext.current
    val state = remember { PermissionsState(context, historyManager).also { it.refresh() } }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state.onMicResult(it)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state.onNotificationResult(it)
    }
    state.micLauncher = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        state.notificationLauncher = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

private fun startBubbleService(context: Context) {
    val intent = Intent(context, FloatingBubbleService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (com.walsoup.ditto.service.DittoAccessibilityService.instance != null) return true
    val expectedServiceName = "${context.packageName}/${com.walsoup.ditto.service.DittoAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentName = colonSplitter.next()
        if (componentName.equals(expectedServiceName, ignoreCase = true) ||
            componentName.equals("${context.packageName}/.service.DittoAccessibilityService", ignoreCase = true) ||
            componentName.contains("DittoAccessibilityService")) {
            return true
        }
    }
    return false
}

fun isNotificationPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
