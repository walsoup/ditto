package com.walsoup.ditto.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.walsoup.ditto.service.FloatingBubbleService
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.walsoup.ditto.TerracottaDot
import com.walsoup.ditto.theme.PreviewDivider
import com.walsoup.ditto.theme.PreviewSurface
import com.walsoup.ditto.theme.TerracottaSoft
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.data.BubbleShape
import com.walsoup.ditto.data.BubbleSize
import com.walsoup.ditto.data.BubbleTheme
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.ditto.isAccessibilityServiceEnabled
import kotlin.math.roundToInt


@Composable
fun SettingsScreen(
    historyManager: HistoryManager
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(historyManager.selectedFormat) }
    var isAutoPasteEnabled by remember { mutableStateOf(historyManager.isAutoPasteEnabled) }
    var isAppFilterEnabled by remember { mutableStateOf(historyManager.isAppFilterEnabled) }
    val selectedPackages by historyManager.selectedAppPackagesFlow.collectAsState()
    val bubbleOpacity by historyManager.bubbleOpacityFlow.collectAsState()
    val bubbleSize by historyManager.bubbleSizeFlow.collectAsState()
    val bubbleShape by historyManager.bubbleShapeFlow.collectAsState()
    val bubbleTheme by historyManager.bubbleThemeFlow.collectAsState()
    val bubbleIdleDim by historyManager.bubbleIdleDimFlow.collectAsState()
    val bubbleSnapToEdge by historyManager.bubbleSnapToEdgeFlow.collectAsState()
    val volumeKeyShortcutEnabled by historyManager.volumeKeyShortcutEnabledFlow.collectAsState()
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var showMitLicenseDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SageBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Settings",
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = SageText
        )
        Text(
            text = "Audio preferences & app info",
            fontSize = 12.sp,
            color = SageSubtext
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 1. Audio Export Format Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SageCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = SagePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Default Audio Format",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = SageText
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select format for exports, clipboard copying, and sharing.",
                    fontSize = 12.sp,
                    color = SageSubtext
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AudioFormatType.values().forEach { format ->
                        val isSelected = selectedFormat == format
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFormat = format
                                historyManager.selectedFormat = format
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
                                    text = format.displayName,
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = selectedFormat.description,
                    fontSize = 12.sp,
                    color = SageSubtext
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Automatic Direct Paste Card
        Card(
            shape = RoundedCornerShape(18.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SettingsAccessibility,
                                contentDescription = null,
                                tint = SagePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Automatic Paste",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = SageText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Directly injects audio clip into the focused chat field when copying",
                            fontSize = 12.sp,
                            color = SageSubtext
                        )
                    }

                    Switch(
                        checked = isAutoPasteEnabled,
                        onCheckedChange = { enabled ->
                            isAutoPasteEnabled = enabled
                            historyManager.isAutoPasteEnabled = enabled
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SagePrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Accessibility Service", fontSize = 12.sp, color = SagePrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Target Apps Filter Card
        Card(
            shape = RoundedCornerShape(18.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = SagePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Target Apps Filter",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = SageText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Only show the floating bubble inside your chosen apps",
                            fontSize = 12.sp,
                            color = SageSubtext
                        )
                    }

                    Switch(
                        checked = isAppFilterEnabled,
                        onCheckedChange = { enabled ->
                            isAppFilterEnabled = enabled
                            historyManager.isAppFilterEnabled = enabled
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SagePrimary
                        )
                    )
                }

                if (isAppFilterEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SageContainerLow)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Active Whitelist",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = SageText
                            )
                            Text(
                                text = "${selectedPackages.size} apps configured",
                                fontSize = 11.sp,
                                color = SageSubtext
                            )
                        }

                        OutlinedButton(
                            onClick = { showAppSelectionDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Configure Apps", fontSize = 12.sp, color = SagePrimary)
                        }
                    }

                    val isAccessibilityOn = isAccessibilityServiceEnabled(context)
                    if (!isAccessibilityOn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TerracottaSoft)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsAccessibility,
                                contentDescription = null,
                                tint = TerracottaDot,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Accessibility service required to detect open apps",
                                fontSize = 11.sp,
                                color = SageText,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Enable", fontSize = 11.sp, color = SagePrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Floating Bubble Appearance Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SageCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = SagePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Floating Bubble Appearance",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = SageText
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Customize transparency, size, shape, and colors.",
                    fontSize = 12.sp,
                    color = SageSubtext
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Live Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PreviewSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .padding(14.dp)
                    ) {
                        // Simulated background content (chat bubbles/lines)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .align(Alignment.CenterStart)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PreviewDivider)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PreviewDivider)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PreviewDivider)
                            )
                        }

                        // The floating bubble preview floating over simulated text
                        val previewShape = bubbleShape.getShape(bubbleSize.sizeDp)
                        Surface(
                            shape = previewShape,
                            shadowElevation = (4.dp * bubbleOpacity).coerceAtLeast(0.dp),
                            color = bubbleTheme.containerColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, bubbleTheme.borderColor),
                            modifier = Modifier
                                .size(bubbleSize.sizeDp)
                                .align(Alignment.CenterEnd)
                                .graphicsLayer { alpha = bubbleOpacity }
                                .clip(previewShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = bubbleTheme.iconColor,
                                    modifier = Modifier.size(bubbleSize.iconSizeDp)
                                )
                            }
                        }

                        // Live badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SageCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
                            modifier = Modifier.align(Alignment.BottomStart)
                        ) {
                            Text(
                                text = "Live Preview • ${(bubbleOpacity * 100).roundToInt()}% opacity",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = SageSubtext,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Transparency / Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transparency / Opacity",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SageText
                    )
                    Text(
                        text = "${(bubbleOpacity * 100).roundToInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SagePrimary
                    )
                }

                Slider(
                    value = bubbleOpacity,
                    onValueChange = { historyManager.bubbleOpacity = it },
                    valueRange = 0.20f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = SagePrimary,
                        activeTrackColor = SagePrimary,
                        inactiveTrackColor = SageContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Controls bubble translucency while reading or chatting in background apps.",
                    fontSize = 11.sp,
                    color = SageSubtext
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Bubble Size
                Text(
                    text = "Bubble Size",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SageText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BubbleSize.values().forEach { size ->
                        val isSelected = bubbleSize == size
                        FilterChip(
                            selected = isSelected,
                            onClick = { historyManager.bubbleSize = size },
                            label = {
                                Text(
                                    text = "${size.displayName} (${size.sizeDp.value.toInt()}dp)",
                                    fontSize = 12.sp,
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Bubble Shape
                Text(
                    text = "Bubble Shape",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SageText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BubbleShape.values().forEach { shape ->
                        val isSelected = bubbleShape == shape
                        FilterChip(
                            selected = isSelected,
                            onClick = { historyManager.bubbleShape = shape },
                            label = {
                                Text(
                                    text = shape.displayName,
                                    fontSize = 12.sp,
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Color Style Palette
                Text(
                    text = "Color Palette",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SageText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BubbleTheme.values().forEach { theme ->
                        val isSelected = bubbleTheme == theme
                        FilterChip(
                            selected = isSelected,
                            onClick = { historyManager.bubbleTheme = theme },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(theme.iconColor)
                                )
                            },
                            label = {
                                Text(
                                    text = theme.displayName,
                                    fontSize = 12.sp,
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Behaviors: Auto-Dim when Idle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Dim when Idle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SageText
                        )
                        Text(
                            text = "Fades the bubble to subtle opacity when untouched",
                            fontSize = 11.sp,
                            color = SageSubtext
                        )
                    }
                    Switch(
                        checked = bubbleIdleDim,
                        onCheckedChange = { historyManager.isBubbleIdleDimEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SagePrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. Behaviors: Snap to Screen Edge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Snap to Screen Edge",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SageText
                        )
                        Text(
                            text = "Docks neatly to left or right margin upon drag release",
                            fontSize = 11.sp,
                            color = SageSubtext
                        )
                    }
                    Switch(
                        checked = bubbleSnapToEdge,
                        onCheckedChange = { historyManager.isBubbleSnapToEdgeEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SagePrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Quick Triggers & Hardware Shortcuts Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SageCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = SagePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick Triggers & Shortcuts",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = SageText
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Control recording without touching the screen.",
                    fontSize = 12.sp,
                    color = SageSubtext
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Volume Key Double-Tap
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Double-Tap Volume Down",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SageText
                        )
                        Text(
                            text = "Quickly tap Volume Down twice to start or stop recording",
                            fontSize = 11.sp,
                            color = SageSubtext
                        )
                    }
                    Switch(
                        checked = volumeKeyShortcutEnabled,
                        onCheckedChange = { historyManager.isVolumeKeyShortcutEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SagePrimary
                        )
                    )
                }

                val isAccessibilityOn = isAccessibilityServiceEnabled(context)
                if (volumeKeyShortcutEnabled && !isAccessibilityOn) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TerracottaSoft)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsAccessibility,
                            contentDescription = null,
                            tint = TerracottaDot,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Accessibility service required to detect volume button presses",
                            fontSize = 11.sp,
                            color = SageText,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Enable", fontSize = 11.sp, color = SagePrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Settings Tile guide
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SageContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Quick Settings Tile",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SagePrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Swipe down your Android notification shade, tap Edit, and add 'Ditto Recorder' for 1-tap toggling anytime.",
                            fontSize = 11.sp,
                            color = SageText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "ABOUT & LEGAL",
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = SageSubtext,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Developer & License Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SageCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Developer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Developer",
                            fontSize = 14.sp,
                            color = SageText
                        )
                    }
                    Text(
                        text = "walsoup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = SagePrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // License Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "License",
                            fontSize = 14.sp,
                            color = SageText
                        )
                    }
                    TextButton(onClick = { showMitLicenseDialog = true }) {
                        Text("MIT License", fontSize = 13.sp, color = SagePrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Terms of Service Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = SagePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Terms of Service",
                            fontSize = 14.sp,
                            color = SageText
                        )
                    }
                    TextButton(onClick = { showTermsDialog = true }) {
                        Text("View Terms", fontSize = 13.sp, color = SagePrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // MIT License Dialog
    if (showMitLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showMitLicenseDialog = false },
            title = {
                Text("MIT License", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """
                            Copyright (c) 2026 walsoup

                            Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

                            The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

                            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
                        """.trimIndent(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        color = SageText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMitLicenseDialog = false }) {
                    Text("Close", color = SagePrimary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = SageCard
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Text("Terms of Service", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """
                            Ditto Terms of Service

                            1. On-Device Processing
                            All audio capture, DSP filtering, and format encoding are conducted 100% locally on your device. Audio files are not uploaded to any remote server.

                            2. Privacy & Telemetry
                            walsoup does not collect, monitor, sell, or transmit any user recordings, usage statistics, or telemetry data. 

                            3. User Content & Sharing
                            You retain sole ownership of all audio notes recorded with Ditto. Audio files are only shared when you explicitly tap Copy, Share, or use the automated paste function.

                            4. Accessibility Service Usage
                            The optional accessibility service is strictly used to perform the Android standard paste action into the currently focused text field at your command. It does not inspect, read, or harvest screen contents.
                        """.trimIndent(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = SageText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close", color = SagePrimary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = SageCard
        )
    }

    if (showAppSelectionDialog) {
        AppSelectionDialog(
            historyManager = historyManager,
            onDismissRequest = { showAppSelectionDialog = false }
        )
    }
}
