package com.closecode.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
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

    @Volatile
    private var lastPackage: String? = null

    @Volatile
    private var lastActivity: String? = null

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (packageName != null) lastPackage = packageName
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.className?.toString()?.takeIf { it.isNotBlank() }?.let { lastActivity = it }
        }
    }

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

    fun snapshot(limit: Int = 500): UiSnapshot {
        val max = limit.coerceIn(1, 2_000)
        val root = rootInActiveWindow
            ?: return UiSnapshot(lastPackage.orEmpty(), lastActivity, 0, 0, emptyList(), false)
        val nodes = ArrayList<UiNode>(max)
        val truncated = collect(root, 0, max, nodes)
        val metrics = resources.displayMetrics
        return UiSnapshot(
            packageName = root.packageName?.toString() ?: lastPackage.orEmpty(),
            activity = lastActivity,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            nodes = nodes,
            truncated = truncated,
        )
    }

    fun find(selector: UiSelector): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var seen = 0
        var found: AccessibilityNodeInfo? = null
        fun visit(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (selector.matches(toNode(node, depth))) {
                if (seen == selector.index.coerceAtLeast(0)) {
                    found = node
                    return true
                }
                seen++
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                if (visit(child, depth + 1)) return true
            }
            return false
        }
        visit(root, 0)
        return found
    }

    suspend fun tap(selector: UiSelector): Boolean {
        val node = find(selector) ?: return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        return tap(bounds.exactCenterX(), bounds.exactCenterY())
    }

    suspend fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(path, 650)
    }

    suspend fun scroll(direction: String, selector: UiSelector? = null): Boolean {
        val node = selector?.let { find(it) } ?: findScrollable(rootInActiveWindow)
            ?: return false
        val forward = direction.lowercase(Locale.US) != "up"
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (node.performAction(action)) return true
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val x = bounds.exactCenterX()
        val startY = bounds.top + bounds.height() * if (forward) 0.8f else 0.2f
        val endY = bounds.top + bounds.height() * if (forward) 0.2f else 0.8f
        return swipe(x, startY, x, endY, 350)
    }

    fun foregroundPackage(): String? =
        rootInActiveWindow?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: lastPackage?.takeIf { it.isNotBlank() }

    fun describe(node: AccessibilityNodeInfo): UiNode = toNode(node, 0)

    private fun collect(
        node: AccessibilityNodeInfo,
        depth: Int,
        limit: Int,
        output: MutableList<UiNode>,
    ): Boolean {
        if (output.size >= limit) return true
        output.add(toNode(node, depth))
        if (output.size >= limit) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (collect(child, depth + 1, limit, output)) return true
        }
        return false
    }

    private fun toNode(node: AccessibilityNodeInfo, depth: Int): UiNode {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val password = node.isPassword
        return UiNode(
            text = if (password) null else node.text?.toString()?.take(500),
            description = if (password) null else node.contentDescription?.toString()?.take(500),
            id = node.viewIdResourceName,
            className = node.className?.toString(),
            bounds = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            focused = node.isFocused,
            scrollable = node.isScrollable,
            visible = node.isVisibleToUser,
            password = password,
            depth = depth,
        )
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && node.isVisibleToUser) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findScrollable(child)
            if (found != null) return found
        }
        return null
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
