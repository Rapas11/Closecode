package com.closecode.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * Optional input bridge. The service only exists after the user enables CloseCode in
 * Android's accessibility settings; until then the bridge answers "needsAccessibility"
 * instead of pretending a tap happened. Gestures are real dispatchGesture calls.
 */
class CloseCodeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    suspend fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(path, 0)
    }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return gesture(path, duration)
    }

    fun text(value: String): Boolean {
        val node = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value,
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun key(name: String): Boolean {
        val action = when (name.lowercase(Locale.US)) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent" -> GLOBAL_ACTION_RECENTS
            "notifications", "notification" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick", "settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return performGlobalAction(action)
    }

    private suspend fun gesture(path: Path, duration: Long): Boolean {
        val answer = CompletableDeferred<Boolean>()
        val description = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(1)))
            .build()
        val started = dispatchGesture(
            description,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    answer.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    answer.complete(false)
                }
            },
            null,
        )
        if (!started) return false
        return runCatching { withTimeout(5_000) { answer.await() } }.getOrDefault(false)
    }

    companion object {
        @Volatile
        private var instance: CloseCodeAccessibilityService? = null

        fun current(): CloseCodeAccessibilityService? = instance

        fun connected(): Boolean = instance != null
    }
}
