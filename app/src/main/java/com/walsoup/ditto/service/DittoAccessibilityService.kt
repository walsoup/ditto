package com.walsoup.ditto.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import com.walsoup.ditto.data.HistoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DittoAccessibilityService : AccessibilityService() {

    companion object {
        var instance: DittoAccessibilityService? = null
            private set

        private val _currentForegroundPackage = MutableStateFlow<String?>(null)
        val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

        fun updateForegroundPackage(pkg: String) {
            _currentForegroundPackage.value = pkg
        }
    }

    fun getActiveForegroundPackage(): String? {
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        if (!rootPkg.isNullOrBlank() && !isIgnoredPackage(rootPkg)) {
            return rootPkg
        }
        return null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val initialPackage = getActiveForegroundPackage()
        if (!initialPackage.isNullOrBlank()) {
            _currentForegroundPackage.value = initialPackage
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            val eventPkg = event.packageName?.toString()
            val activePkg = if (!eventPkg.isNullOrBlank() && !isIgnoredPackage(eventPkg)) {
                eventPkg
            } else {
                getActiveForegroundPackage()
            }

            if (!activePkg.isNullOrBlank() && !isIgnoredPackage(activePkg)) {
                if (_currentForegroundPackage.value != activePkg) {
                    android.util.Log.d("DittoDebug", "Foreground package detected: $activePkg")
                    _currentForegroundPackage.value = activePkg
                }
            }
        }
    }

    private var lastVolumeDownTime = 0L
    private val doubleClickThresholdMs = 450L

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val historyManager = HistoryManager(this)
        if (!historyManager.isVolumeKeyShortcutEnabled) {
            return super.onKeyEvent(event)
        }

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime <= doubleClickThresholdMs) {
                lastVolumeDownTime = 0L
                android.util.Log.d("DittoDebug", "Volume down double press triggered recording toggle")
                FloatingBubbleService.toggleRecording(this)
                return true
            } else {
                lastVolumeDownTime = now
            }
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun isIgnoredPackage(pkg: String): Boolean {
        if (pkg == "com.android.systemui") return true
        if (pkg.contains("permissioncontroller")) return true

        // Ignore active keyboards so typing doesn't switch the detected app
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val imeList = imm?.enabledInputMethodList
            if (imeList != null) {
                for (ime in imeList) {
                    if (ime.packageName == pkg) return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Finds the active focused editable text node and performs ACTION_PASTE.
     */
    fun performDirectPaste(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && (focusedNode.isEditable || focusedNode.isFocused)) {
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            return result
        }

        // Fallback: breadth-first search for focused editable node
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isFocused) {
                return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }
}
