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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.ditto.isAccessibilityServiceEnabled

@Composable
fun SettingsScreen(
    historyManager: HistoryManager
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(historyManager.selectedFormat) }
    var isAutoPasteEnabled by remember { mutableStateOf(historyManager.isAutoPasteEnabled) }
    var isAppFilterEnabled by remember { mutableStateOf(historyManager.isAppFilterEnabled) }
    val selectedPackages by historyManager.selectedAppPackagesFlow.collectAsState()
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
                        imageVector = Icons.Default.VolumeUp,
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
                                .background(Color(0xFFFBF0EC))
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
