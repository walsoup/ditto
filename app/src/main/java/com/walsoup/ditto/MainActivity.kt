package com.walsoup.ditto

import android.Manifest
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.walsoup.ditto.theme.Ditto
import com.walsoup.ditto.theme.DittoTheme
import com.walsoup.ditto.ui.AppSelectionDialog
import com.walsoup.ditto.ui.LibraryScreen
import com.walsoup.ditto.ui.RecordSheetHost
import com.walsoup.ditto.ui.RecordStage
import com.walsoup.ditto.ui.SettingsScreen
import com.walsoup.ditto.ui.design.EmptyNote
import com.walsoup.ditto.ui.design.PageHeader
import com.walsoup.ditto.ui.design.RecordDock
import com.walsoup.ditto.ui.design.ScreenMargin
import com.walsoup.ditto.ui.design.SectionTitle
import com.walsoup.ditto.ui.design.SettingLine
import com.walsoup.ditto.ui.design.SwitchLine
import com.walsoup.ditto.ui.design.TextTabs
import com.walsoup.ditto.ui.design.TrackRow
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

    private lateinit var recorderEngine: AudioRecorderEngine
    private lateinit var playerHelper: AudioPlayerHelper
    private lateinit var historyManager: HistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )

        recorderEngine = AudioRecorderEngine(this)
        playerHelper = AudioPlayerHelper(this)
        historyManager = HistoryManager(this)

        setContent {
            DittoTheme {
                AppRoot(recorderEngine, playerHelper, historyManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerHelper.stop()
        recorderEngine.cancelRecording()
    }
}

@Composable
fun AppRoot(
    recorderEngine: AudioRecorderEngine,
    playerHelper: AudioPlayerHelper,
    historyManager: HistoryManager
) {
    val c = Ditto.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableStateOf(ScreenTab.RECORD) }
    var stage by remember { mutableStateOf<RecordStage>(RecordStage.Idle) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isRecording by recorderEngine.isRecording.collectAsState()
    val history by historyManager.history.collectAsState()
    val lastTargetPackage = history.firstOrNull { it.targetPackage != null }?.targetPackage
    val permissions = rememberPermissions(historyManager)

    BackHandler(enabled = selectedTab != ScreenTab.RECORD && stage is RecordStage.Idle) {
        selectedTab = ScreenTab.RECORD
    }

    val startRecording = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!permissions.mic) {
            permissions.requestMic()
        } else {
            playerHelper.stop()
            if (recorderEngine.startRecording()) {
                stage = RecordStage.Recording
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
                    RecordDock(
                        durationFlow = recorderEngine.durationMs,
                        amplitudeFlow = recorderEngine.amplitude,
                        isRecording = isRecording,
                        onRecord = startRecording
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
                            permissions = permissions,
                            onOpenLibrary = { selectedTab = ScreenTab.LIBRARY }
                        )
                        ScreenTab.LIBRARY -> LibraryScreen(historyManager, playerHelper, snackbarHostState)
                        ScreenTab.SETTINGS -> SettingsScreen(historyManager)
                    }
                }
            }
        }

        RecordSheetHost(
            stage = stage,
            onStageChange = { stage = it },
            recorderEngine = recorderEngine,
            playerHelper = playerHelper,
            historyManager = historyManager,
            lastTargetPackage = lastTargetPackage
        )
    }
}

@Composable
fun HomeScreen(
    historyManager: HistoryManager,
    playerHelper: AudioPlayerHelper,
    permissions: PermissionsState,
    onOpenLibrary: () -> Unit
) {
    val c = Ditto.colors
    val context = LocalContext.current
    val history by historyManager.history.collectAsState()
    val isPlaying by playerHelper.isPlaying.collectAsState()
    val isAppFilterEnabled by historyManager.isAppFilterEnabledFlow.collectAsState()
    val selectedPackages by historyManager.selectedAppPackagesFlow.collectAsState()
    var playingId by remember { mutableStateOf<String?>(null) }
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
                                style = if (f == defaultFilter) Ditto.type.label else Ditto.type.body,
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
            EmptyNote("No notes yet. Tap Record to make your first one.")
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
                        } else {
                            val file = File(item.filePath)
                            if (file.exists()) {
                                playingId = item.id
                                playerHelper.playFile(file) { playingId = null }
                            }
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
        bubbleOn = FloatingBubbleService.isRunning || historyManager.isFloatingServiceEnabled
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
