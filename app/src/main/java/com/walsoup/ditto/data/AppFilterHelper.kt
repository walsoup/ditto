package com.walsoup.ditto.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Metadata representation of an installed launcher application.
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

/**
 * Pure policy rules for overlay visibility.
 */
object AppFilterPolicy {
    fun shouldShowOverlay(
        isFilterEnabled: Boolean,
        selectedPackages: Set<String>,
        currentPackage: String?,
        isAccessibilityServiceRunning: Boolean,
        isOverlayActive: Boolean
    ): Boolean {
        // Keep overlay visible if user is actively recording or reviewing audio
        if (isOverlayActive) return true

        // If filter is disabled, show globally across all apps
        if (!isFilterEnabled) return true

        // Fallback: If accessibility service is not active, remain visible so bubble is not lost
        if (!isAccessibilityServiceRunning) return true

        // Before any window state has been detected, keep visible
        if (currentPackage.isNullOrBlank()) return true

        // Show only if the foreground app is in the user's checked list
        return selectedPackages.contains(currentPackage)
    }
}

/**
 * Utility helper to query user-facing launchable applications on the device.
 */
object AppFilterHelper {

    suspend fun getInstalledLaunchableApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

        resolveInfos.mapNotNull { resolveInfo ->
            try {
                val pkgName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                InstalledApp(
                    packageName = pkgName,
                    appName = label,
                    icon = icon
                )
            } catch (_: Exception) {
                null
            }
        }.distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
    }
}
