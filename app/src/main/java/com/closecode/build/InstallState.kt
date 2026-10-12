package com.closecode.build

/**
 * Phase 3 install flow, as a pure state machine so the whole dance is unit-tested:
 * Idle → Installing → Installed, with NeedsPermission when the system toggle is off
 * and Failed carrying the system's own status message. The UI only renders states;
 * [InstallHelper] performs them.
 */
sealed interface InstallState {
    data object Idle : InstallState
    data class Installing(val sessionId: Int) : InstallState
    data class Installed(val packageName: String) : InstallState
    data object NeedsPermission : InstallState
    data class Failed(val message: String) : InstallState
}

internal fun nextInstallState(
    current: InstallState,
    event: InstallEvent,
): InstallState = when (event) {
    InstallEvent.StartDenied -> InstallState.NeedsPermission
    is InstallEvent.SessionOpened -> InstallState.Installing(event.sessionId)
    is InstallEvent.SystemStatus -> when (event.code) {
        0 -> InstallState.Installed(event.packageName)
        else -> InstallState.Failed(event.message.ifBlank { "Install failed (${event.code})." })
    }
    InstallEvent.Reset -> InstallState.Idle
}

internal sealed interface InstallEvent {
    data object StartDenied : InstallEvent
    data class SessionOpened(val sessionId: Int) : InstallEvent
    data class SystemStatus(val code: Int, val packageName: String, val message: String) : InstallEvent
    data object Reset : InstallEvent
}
