package com.walsoup.ditto.ui

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.walsoup.ditto.SageBg
import com.walsoup.ditto.SageBorder
import com.walsoup.ditto.SageCard
import com.walsoup.ditto.SageContainer
import com.walsoup.ditto.SageContainerLow
import com.walsoup.ditto.SagePrimary
import com.walsoup.ditto.SageSubtext
import com.walsoup.ditto.SageText
import com.walsoup.ditto.TerracottaDot
import com.walsoup.ditto.data.AppFilterHelper
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.ditto.data.InstalledApp
import com.walsoup.ditto.isAccessibilityServiceEnabled

@Composable
fun AppSelectionDialog(
    historyManager: HistoryManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val selectedPackages by historyManager.selectedAppPackagesFlow.collectAsState()

    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlySelected by remember { mutableStateOf(false) }

    val isAccessibilityEnabled = remember(context) {
        isAccessibilityServiceEnabled(context)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        installedApps = AppFilterHelper.getInstalledLaunchableApps(context)
        isLoading = false
    }

    val filteredApps = remember(installedApps, searchQuery, showOnlySelected, selectedPackages) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = !showOnlySelected || selectedPackages.contains(app.packageName)
            matchesSearch && matchesFilter
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SageCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SageBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Target Apps",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = SageText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedPackages.size} of ${installedApps.size} apps selected",
                            fontSize = 12.sp,
                            color = SageSubtext
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SageContainerLow)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SageText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SageBg)
                        .border(1.dp, SageBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SageSubtext,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search apps...",
                                    fontSize = 13.sp,
                                    color = SageSubtext
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = SageText,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(SagePrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = SageSubtext,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter & Batch Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !showOnlySelected,
                            onClick = { showOnlySelected = false },
                            label = { Text("All (${installedApps.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SageContainer,
                                selectedLabelColor = SagePrimary,
                                containerColor = SageCard,
                                labelColor = SageSubtext
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!showOnlySelected) SagePrimary else SageBorder
                            )
                        )
                        FilterChip(
                            selected = showOnlySelected,
                            onClick = { showOnlySelected = true },
                            label = { Text("Selected (${selectedPackages.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SageContainer,
                                selectedLabelColor = SagePrimary,
                                containerColor = SageCard,
                                labelColor = SageSubtext
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (showOnlySelected) SagePrimary else SageBorder
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val targets = filteredApps.map { it.packageName }
                                historyManager.selectAllApps(targets)
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Select All", fontSize = 11.sp, color = SagePrimary)
                        }
                        TextButton(
                            onClick = {
                                historyManager.clearAllSelectedApps()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp, color = TerracottaDot)
                        }
                    }
                }

                // Accessibility Service Banner if not enabled
                if (!isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF0EC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2DCD3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsAccessibility,
                                contentDescription = null,
                                tint = TerracottaDot,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accessibility service required",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SageText
                                )
                                Text(
                                    text = "Needed to detect when you enter target apps",
                                    fontSize = 11.sp,
                                    color = SageSubtext
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, color = SagePrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = SagePrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showOnlySelected) "No apps selected yet" else "No matching apps found",
                                fontSize = 13.sp,
                                color = SageSubtext
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = filteredApps,
                                key = { it.packageName }
                            ) { app ->
                                val isChecked = selectedPackages.contains(app.packageName)
                                AppItemRow(
                                    app = app,
                                    isChecked = isChecked,
                                    onToggle = { checked ->
                                        historyManager.toggleAppPackage(app.packageName, checked)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Done Button
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SagePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Done (${selectedPackages.size} Selected)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItemRow(
    app: InstalledApp,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val bitmap = remember(app.packageName) {
        try {
            app.icon?.toBitmap(width = 80, height = 80, config = Bitmap.Config.ARGB_8888)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isChecked) SageContainerLow else Color.Transparent)
            .clickable { onToggle(!isChecked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SageContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = SagePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = SageText,
                maxLines = 1
            )
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = SageSubtext,
                maxLines = 1
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle(it) },
            colors = CheckboxDefaults.colors(
                checkedColor = SagePrimary,
                checkmarkColor = Color.White,
                uncheckedColor = SageBorder
            )
        )
    }
}
