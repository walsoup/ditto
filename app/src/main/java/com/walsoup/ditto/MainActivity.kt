package com.walsoup.ditto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.AudioRecorderEngine
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.ditto.service.FloatingBubbleService
import com.walsoup.ditto.theme.CleanWhite
import com.walsoup.ditto.theme.ForestSage
import com.walsoup.ditto.theme.HairlineBorder
import com.walsoup.ditto.theme.LinenCream
import com.walsoup.ditto.theme.SlateSubtext
import com.walsoup.ditto.theme.SlateText
import com.walsoup.ditto.theme.SoftSage
import com.walsoup.ditto.theme.SoftSageLow
import com.walsoup.ditto.theme.DittoTheme
import com.walsoup.ditto.theme.WarmTerracotta
import com.walsoup.ditto.ui.AppSelectionDialog
import com.walsoup.ditto.ui.HistoryScreen
import com.walsoup.ditto.ui.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Pastel Material 3 Theme Palette (60-30-10) aliases
val SageBg = LinenCream
val SageCard = CleanWhite
val SageContainer = SoftSage
val SageContainerLow = SoftSageLow
val SagePrimary = ForestSage
val SageText = SlateText
val SageSubtext = SlateSubtext
val SageBorder = HairlineBorder
val TerracottaDot = WarmTerracotta

enum class ScreenTab(val title: String, val icon: ImageVector) {
    RECORDER("Recorder", Icons.Default.Mic),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var recorderEngine: AudioRecorderEngine
    private lateinit var playerHelper: AudioPlayerHelper
    private lateinit var historyManager: HistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        recorderEngine = AudioRecorderEngine(this)
        playerHelper = AudioPlayerHelper(this)
        historyManager = HistoryManager(this)

        setContent {
            DittoTheme {
                AppRoot(
                    recorderEngine = recorderEngine,
                    playerHelper = playerHelper,
                    historyManager = historyManager
                )
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
    var selectedTab by remember { mutableStateOf(ScreenTab.RECORDER) }

    Scaffold(
        containerColor = SageBg,
        bottomBar = {
            NavigationBar(
                containerColor = SageCard,
                tonalElevation = 6.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                ScreenTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SagePrimary,
                            selectedTextColor = SagePrimary,
                            indicatorColor = SageContainer,
                            unselectedIconColor = SageSubtext,
                            unselectedTextColor = SageSubtext
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                ScreenTab.RECORDER -> RecorderScreen(recorderEngine, playerHelper, historyManager)
                ScreenTab.HISTORY -> HistoryScreen(historyManager, playerHelper)
                ScreenTab.SETTINGS -> SettingsScreen(historyManager)
            }
        }
    }
}

@Composable
fun RecorderScreen(
    recorderEngine: AudioRecorderEngine,
    playerHelper: AudioPlayerHelper,
    historyManager: HistoryManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasNotificationPermission by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var isServiceRunning by remember {
        mutableStateOf(FloatingBubbleService.isRunning || historyManager.isFloatingServiceEnabled)
    }
    var showAppSelectionDialog by remember { mutableStateOf(false) }

    val isAppFilterEnabled by historyManager.isAppFilterEnabledFlow.collectAsState()
    val selectedPackages by historyManager.selectedAppPackagesFlow.collectAsState()

    val isRecording by recorderEngine.isRecording.collectAsState()
    val isPlaying by playerHelper.isPlaying.collectAsState()

    var recordedAudioFile by remember { mutableStateOf<File?>(null) }
    var lastRecordedDuration by remember { mutableStateOf(0L) }
    var selectedFilter by remember { mutableStateOf(VoiceFilter.RAW) }
    var activeAudioFile by remember { mutableStateOf<File?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
                hasNotificationPermission = isNotificationPermissionGranted(context)

                if (historyManager.isFloatingServiceEnabled && hasOverlayPermission && hasMicPermission && !FloatingBubbleService.isRunning) {
                    val serviceIntent = Intent(context, FloatingBubbleService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
                isServiceRunning = FloatingBubbleService.isRunning || historyManager.isFloatingServiceEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allPermissionsGranted = hasMicPermission && hasOverlayPermission && hasAccessibilityPermission && hasNotificationPermission
    val grantedCount = listOf(hasMicPermission, hasOverlayPermission, hasAccessibilityPermission, hasNotificationPermission).count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SageBg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header (topright Active tab removed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SageContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = SagePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Ditto",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = SageText
                    )
                    Text(
                        text = "Voice notes & filters",
                        fontSize = 12.sp,
                        color = SageSubtext
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Permission status boxes: visible whenever any permission is missing
            if (!allPermissionsGranted) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permissions Setup",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = SageText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap any missing permission to enable it",
                                    fontSize = 12.sp,
                                    color = SageSubtext
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (grantedCount == 4) SageContainerLow else Color(0xFFFBF0EC),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (grantedCount == 4) SageBorder else Color(0xFFF2DCD3)
                                )
                            ) {
                                Text(
                                    text = "$grantedCount/4 Ready",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (grantedCount == 4) SagePrimary else TerracottaDot,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 1. Microphone
                            PermissionBox(
                                title = "Microphone",
                                description = "Required to record voice notes",
                                isGranted = hasMicPermission,
                                icon = Icons.Default.Mic,
                                onClick = {
                                    if (!hasMicPermission) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            )

                            // 2. Display Overlay
                            PermissionBox(
                                title = "Floating Overlay",
                                description = "Enables quick-record bubble across all apps",
                                isGranted = hasOverlayPermission,
                                icon = Icons.Default.Layers,
                                onClick = {
                                    if (!hasOverlayPermission) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    }
                                }
                            )

                            // 3. Auto-Paste (Accessibility)
                            PermissionBox(
                                title = "Auto-Paste (Accessibility)",
                                description = "Directly pastes audio into active chat fields",
                                isGranted = hasAccessibilityPermission,
                                icon = Icons.Default.SettingsAccessibility,
                                onClick = {
                                    if (!hasAccessibilityPermission) {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                            )

                            // 4. Notifications
                            PermissionBox(
                                title = "Notifications",
                                description = "Allows foreground recording service indicator",
                                isGranted = hasNotificationPermission,
                                icon = Icons.Default.Notifications,
                                onClick = {
                                    if (!hasNotificationPermission) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Floating Quick-Record Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SageCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SageContainerLow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = SagePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Floating Quick-Record",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = SageText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAppFilterEnabled) {
                                        "Overlay active only in ${selectedPackages.size} selected app(s)"
                                    } else {
                                        "Overlay button sits on screen edge across all apps"
                                    },
                                    fontSize = 12.sp,
                                    color = SageSubtext,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (!hasOverlayPermission) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                        return@Switch
                                    }
                                    if (!hasMicPermission) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        return@Switch
                                    }

                                    historyManager.isFloatingServiceEnabled = true
                                    val serviceIntent = Intent(context, FloatingBubbleService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                    isServiceRunning = true
                                } else {
                                    historyManager.isFloatingServiceEnabled = false
                                    val serviceIntent = Intent(context, FloatingBubbleService::class.java)
                                    context.stopService(serviceIntent)
                                    isServiceRunning = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SagePrimary
                            )
                        )
                    }

                    if (isAppFilterEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SageContainerLow)
                                .clickable { showAppSelectionDialog = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = SagePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${selectedPackages.size} Target Apps Configured",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SageText
                                )
                            }
                            Text(
                                text = "Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SagePrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Voice Filter Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VOICE FILTER",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = SageSubtext,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Tap to apply",
                        fontSize = 12.sp,
                        color = Color(0xFF8A9A90)
                    )
                }
                Text(
                    text = "${VoiceFilter.values().size} Profiles",
                    fontSize = 12.sp,
                    color = SageSubtext
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(VoiceFilter.values()) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedFilter = filter
                            val fileToProcess = recordedAudioFile
                            if (fileToProcess != null) {
                                scope.launch {
                                    val filtered = withContext(Dispatchers.IO) {
                                        AudioFileManager.generateFilteredAudio(
                                            context,
                                            fileToProcess,
                                            filter,
                                            historyManager.selectedFormat
                                        )
                                    }
                                    activeAudioFile = filtered
                                    playerHelper.playFile(filtered)
                                }
                            }
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SagePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        label = {
                            Text(
                                text = filter.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SageContainer,
                            selectedLabelColor = SagePrimary,
                            containerColor = SageCard,
                            labelColor = SageText
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SagePrimary else SageBorder
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Player & Export Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SageCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SageContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = SagePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val activeName = activeAudioFile?.name ?: (recordedAudioFile?.name ?: "note_take_01.${historyManager.selectedFormat.extension}")
                            Text(
                                text = activeName,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = SageText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedFilter.displayName} • ${historyManager.selectedFormat.displayName}",
                                fontSize = 12.sp,
                                color = SageSubtext
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val target = activeAudioFile ?: recordedAudioFile
                                if (target != null) {
                                    if (isPlaying) playerHelper.pause() else playerHelper.playFile(target)
                                } else {
                                    Toast.makeText(context, "Record audio first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SagePrimary)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SageContainer)
                        ) {
                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(6.dp)
                                        .background(SagePrimary)
                                )
                            }
                        }

                        Text(
                            text = if (isPlaying) "Playing" else "Ready",
                            fontSize = 12.sp,
                            color = SageSubtext,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val target = activeAudioFile ?: recordedAudioFile
                                if (target != null) {
                                    AudioFileManager.copyAndRecordToHistory(
                                        context = context,
                                        file = target,
                                        durationMs = lastRecordedDuration,
                                        filter = selectedFilter,
                                        format = historyManager.selectedFormat,
                                        historyManager = historyManager
                                    )
                                } else {
                                    Toast.makeText(context, "Record audio first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SagePrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy & Paste", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                val target = activeAudioFile ?: recordedAudioFile
                                if (target != null) {
                                    val shareIntent = AudioFileManager.createShareIntent(context, target)
                                    context.startActivity(shareIntent)
                                    historyManager.addRecording(
                                        target,
                                        lastRecordedDuration,
                                        selectedFilter,
                                        historyManager.selectedFormat
                                    )
                                } else {
                                    Toast.makeText(context, "Record audio first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SageContainer,
                                contentColor = SagePrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tactile Recording Dock in Thumb Zone
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            shape = RoundedCornerShape(32.dp),
            color = SageCard,
            shadowElevation = 5.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Timer (isolated to prevent parent recomposition)
                DockTimer(durationFlow = recorderEngine.durationMs, isRecording = isRecording)

                // Center: Level Meter (isolated to prevent parent recomposition)
                DockAmplitudeMeter(amplitudeFlow = recorderEngine.amplitude, isRecording = isRecording)

                // Right: Pill Button
                if (!isRecording) {
                    Button(
                        onClick = {
                            if (!hasMicPermission) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@Button
                            }
                            playerHelper.stop()
                            val started = recorderEngine.startRecording()
                            if (!started) {
                                Toast.makeText(context, "Microphone unavailable", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SageContainer,
                            contentColor = SagePrimary
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .width(120.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Record",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val duration = recorderEngine.durationMs.value
                            lastRecordedDuration = duration
                            val file = recorderEngine.stopRecording()
                            if (file != null && file.exists()) {
                                recordedAudioFile = file
                                activeAudioFile = file
                                selectedFilter = VoiceFilter.RAW
                                // Auto save to history
                                historyManager.addRecording(
                                    file,
                                    duration,
                                    VoiceFilter.RAW,
                                    historyManager.selectedFormat
                                )
                                // If auto-paste is enabled, copy and paste automatically!
                                if (historyManager.isAutoPasteEnabled) {
                                    AudioFileManager.copyAudioToClipboard(
                                        context = context,
                                        file = file,
                                        autoPaste = true
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TerracottaDot,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .width(120.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Done",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    if (showAppSelectionDialog) {
        AppSelectionDialog(
            historyManager = historyManager,
            onDismissRequest = { showAppSelectionDialog = false }
        )
    }
}

@Composable
fun DockTimer(
    durationFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    isRecording: Boolean
) {
    val durationMs by durationFlow.collectAsState()
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 1000) / 60
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isRecording) TerracottaDot else Color(0xFFB0BEC5))
        )
        Text(
            text = String.format("%02d:%02d", minutes, seconds),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            color = SageText
        )
    }
}

@Composable
fun DockAmplitudeMeter(
    amplitudeFlow: kotlinx.coroutines.flow.StateFlow<Float>,
    isRecording: Boolean
) {
    val amplitude by amplitudeFlow.collectAsState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        repeat(5) { i ->
            val barHeight = if (isRecording) {
                (6 + (amplitude * (i + 1) * 6)).coerceIn(4f, 20f)
            } else 6f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isRecording) SagePrimary else Color(0xFFD0D7D3))
            )
        }
    }
}

@Composable
fun PermissionBox(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isGranted) SageContainerLow else Color(0xFFFDFBF9),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isGranted) SageBorder else Color(0xFFF0DDD6)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) SageContainer else Color(0xFFF5ECE8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) SagePrimary else TerracottaDot,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = SageText
                    )
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        color = SageSubtext,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SageContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Enabled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SagePrimary
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TerracottaDot
                ) {
                    Text(
                        text = "Enable →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
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
