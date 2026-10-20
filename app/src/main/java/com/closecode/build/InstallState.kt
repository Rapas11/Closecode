package com.closecode.build

/**
 * The install flow as a pure state machine so the whole dance is unit-tested:
 * Idle → Installing → Installed, with NeedsPermission when the system toggle is off
 * and Failed carrying Android's own status, translated once, in plain words. The UI
 * only renders states; [InstallHelper] performs them.
 *
 * The status codes are PackageInstaller's values, copied here as numbers so the
 * mapping stays testable on a plain JVM without an Android runtime.
 */
sealed interface InstallState {
    data object Idle : InstallState
    data class Installing(val sessionId: Int) : InstallState
    data class Installed(val packageName: String) : InstallState
    data object NeedsPermission : InstallState
    data class Failed(
        val message: String,
        val kind: FailureKind = FailureKind.Other,
    ) : InstallState

    /** What the panel can offer next; only Conflict gets an Uninstall button. */
    enum class FailureKind { Other, Cancelled, Conflict, Storage, Incompatible, Invalid }
}

internal const val STATUS_PENDING_USER_ACTION = -1
internal const val STATUS_SUCCESS = 0
internal const val STATUS_FAILURE = 1
internal const val STATUS_FAILURE_BLOCKED = 2
internal const val STATUS_FAILURE_ABORTED = 3
internal const val STATUS_FAILURE_INVALID = 4
internal const val STATUS_FAILURE_CONFLICT = 5
internal const val STATUS_FAILURE_STORAGE = 6
internal const val STATUS_FAILURE_INCOMPATIBLE = 7
internal const val STATUS_FAILURE_TIMEOUT = 8

internal fun nextInstallState(
    current: InstallState,
    event: InstallEvent,
): InstallState = when (event) {
    InstallEvent.StartDenied -> InstallState.NeedsPermission
    is InstallEvent.SessionOpened -> InstallState.Installing(event.sessionId)
    is InstallEvent.SystemStatus -> when (event.code) {
        STATUS_SUCCESS -> InstallState.Installed(event.packageName)
        STATUS_FAILURE_ABORTED -> InstallState.Failed(
            "Install cancelled.",
            InstallState.FailureKind.Cancelled,
        )
        STATUS_FAILURE_CONFLICT -> InstallState.Failed(
            "A different version of this app, signed with another key, is installed. " +
                "Uninstall it first.",
            InstallState.FailureKind.Conflict,
        )
        STATUS_FAILURE_STORAGE -> InstallState.Failed(
            "Not enough storage to install the app.",
            InstallState.FailureKind.Storage,
        )
        STATUS_FAILURE_INCOMPATIBLE -> InstallState.Failed(
            "This app needs a newer version of Android.",
            InstallState.FailureKind.Incompatible,
        )
        STATUS_FAILURE_INVALID, STATUS_FAILURE_BLOCKED -> InstallState.Failed(
            "Android refused this app: " +
                event.message.ifBlank { "it did not say why" },
            InstallState.FailureKind.Invalid,
        )
        STATUS_FAILURE_TIMEOUT -> InstallState.Failed(
            "The install timed out. Try again.",
        )
        STATUS_FAILURE -> InstallState.Failed(
            "Install failed: " + event.message.ifBlank { "Android did not say why" },
        )
        STATUS_PENDING_USER_ACTION -> current
        else -> InstallState.Failed(
            "Install failed (${event.code}): " +
                event.message.ifBlank { "Android did not say why" },
        )
    }
    InstallEvent.Reset -> InstallState.Idle
}

internal sealed interface InstallEvent {
    data object StartDenied : InstallEvent
    data class SessionOpened(val sessionId: Int) : InstallEvent
    data class SystemStatus(val code: Int, val packageName: String, val message: String) : InstallEvent
    data object Reset : InstallEvent
}
