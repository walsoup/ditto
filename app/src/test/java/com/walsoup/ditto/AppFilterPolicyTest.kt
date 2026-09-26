package com.walsoup.ditto

import com.walsoup.ditto.data.AppFilterPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterPolicyTest {

    private val selectedApps = setOf("com.whatsapp", "com.google.android.talk", "org.telegram.messenger")

    @Test
    fun testOverlayAlwaysVisibleWhileRecordingOrReviewing() {
        // Even if the current package is not in the whitelist, if recording is active, do not hide
        val shouldShow = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = true,
            selectedPackages = selectedApps,
            currentPackage = "com.unrelated.game",
            isAccessibilityServiceRunning = true,
            isOverlayActive = true
        )
        assertTrue("Overlay must stay visible during recording/review", shouldShow)
    }

    @Test
    fun testOverlayVisibleGloballyWhenFilterDisabled() {
        val shouldShow = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = false,
            selectedPackages = selectedApps,
            currentPackage = "com.unrelated.game",
            isAccessibilityServiceRunning = true,
            isOverlayActive = false
        )
        assertTrue("Overlay should show globally when filter is off", shouldShow)
    }

    @Test
    fun testFallbackToVisibleWhenAccessibilityServiceNotRunning() {
        // If accessibility service is disabled, we fallback to showing overlay so user is not stranded
        val shouldShow = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = true,
            selectedPackages = selectedApps,
            currentPackage = "com.unrelated.game",
            isAccessibilityServiceRunning = false,
            isOverlayActive = false
        )
        assertTrue("Overlay should fallback to visible if accessibility service is down", shouldShow)
    }

    @Test
    fun testFallbackToVisibleWhenCurrentPackageUnknown() {
        val shouldShow = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = true,
            selectedPackages = selectedApps,
            currentPackage = null,
            isAccessibilityServiceRunning = true,
            isOverlayActive = false
        )
        assertTrue("Overlay should stay visible before any window state is captured", shouldShow)
    }

    @Test
    fun testShowsOnlyWhenInsideSelectedApps() {
        // Inside selected app -> TRUE
        val showInWhatsapp = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = true,
            selectedPackages = selectedApps,
            currentPackage = "com.whatsapp",
            isAccessibilityServiceRunning = true,
            isOverlayActive = false
        )
        assertTrue("Overlay must show inside selected app (WhatsApp)", showInWhatsapp)

        val showInTelegram = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = true,
            selectedPackages = selectedApps,
            currentPackage = "org.telegram.messenger",
            isAccessibilityServiceRunning = true,
            isOverlayActive = false
        )
        assertTrue("Overlay must show inside selected app (Telegram)", showInTelegram)

        // Inside unselected app -> FALSE
        val showInGame = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = true,
            selectedPackages = selectedApps,
            currentPackage = "com.unrelated.game",
            isAccessibilityServiceRunning = true,
            isOverlayActive = false
        )
        assertFalse("Overlay must hide inside unselected app", showInGame)

        // Inside home launcher (not selected) -> FALSE
        val showInLauncher = AppFilterPolicy.shouldShowOverlay(
            isFilterEnabled = true,
            selectedPackages = selectedApps,
            currentPackage = "com.google.android.apps.nexuslauncher",
            isAccessibilityServiceRunning = true,
            isOverlayActive = false
        )
        assertFalse("Overlay must hide on home launcher if launcher not selected", showInLauncher)
    }
}
