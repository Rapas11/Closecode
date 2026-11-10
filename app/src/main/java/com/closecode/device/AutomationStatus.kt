package com.closecode.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * True only while an accessibility command is actually touching another app. The IDE reads it
 * for the on-screen control banner; setting it is cheap and safe off the UI thread.
 */
object AutomationStatus {
    var active by mutableStateOf(false)
        private set
    var label by mutableStateOf("")
        private set
    var stopRequested by mutableStateOf(false)
        private set

    fun begin(action: String) {
        stopRequested = false
        label = action
        active = true
    }

    fun end() {
        active = false
        label = ""
    }

    fun stop() {
        stopRequested = true
        end()
        CloseCodeAccessibilityService.current()?.key("home")
    }

    fun shouldStop(): Boolean = stopRequested
}
