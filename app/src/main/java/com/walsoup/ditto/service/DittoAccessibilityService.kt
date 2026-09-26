package com.walsoup.ditto.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DittoAccessibilityService : AccessibilityService() {

    companion object {
        var instance: DittoAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active event stream
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
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
