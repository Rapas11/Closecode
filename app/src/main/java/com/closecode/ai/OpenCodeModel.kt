package com.closecode.ai

/** What the OpenCode card in the AI tab is doing right now. */
enum class OpenCodePhase {
    /** Nothing has been started. The card offers the Start button. */
    Idle,

    /** Starting: the binary is being fetched if needed, then the server is launched. */
    Starting,

    /** The server answered. The status card shows the URL and the buttons. */
    Running,

    /** The server stopped answering. The app is restarting it by itself. */
    ConnectionLost,

    /** Something went wrong and the app cannot go on by itself. The reason is on the card. */
    Failed,
}

/** One of the four steps on the Starting screen. */
enum class OpenCodeStepState { Pending, Working, Done, Failed }

/**
 * One line of Build Info.
 *
 * [warning] marks the lines that say the app is still waiting — they are drawn in amber, the
 * way the mockup shows them.
 */
data class OpenCodeLogLine(val text: String, val warning: Boolean = false)


/**
 * One line of the Starting screen: what the app is doing, and how it went.
 *
 * [note] is the short word on the right — "Done", "4 s", "Waiting" — so the row reads the same
 * way the mockup does.
 */
data class OpenCodeStep(
    val label: String,
    val state: OpenCodeStepState,
    val note: String = "",
)

/**
 * Everything the AI tab's OpenCode screens show.
 *
 * One value, held by [OpenCode.state], so the screens read the same truth: the phase, the four
 * steps, the log that goes into Build Info, the URL, the version and the countdown.
 */
data class OpenCodeStatus(
    val phase: OpenCodePhase = OpenCodePhase.Idle,
    val projectName: String = "",
    val projectFolder: String = "",
    val url: String = "",
    val version: String = "",
    val port: Int = OpenCodeDefaults.PORT,
    val steps: List<OpenCodeStep> = emptyList(),
    /** The Build Info lines, oldest first. */
    val log: List<OpenCodeLogLine> = emptyList(),
    /** Seconds until the next automatic retry. Zero when nothing is being retried. */
    val retryInSeconds: Int = 0,
    /** Why it stopped, when it stopped by itself. */
    val error: String? = null,
    /** True while the binary is being downloaded for the first time. */
    val downloading: Boolean = false,
    /** Percent of the first download, zero when nothing is downloading. */
    val downloadPercent: Int = 0,
) {
    /** The four steps of the Starting screen, in the order the mockup shows them. */
    val stepRows: List<OpenCodeStep> get() = steps

    /** The Build Info box, as one monospaced block of text. */
    val buildInfo: String get() = log.joinToString("\n") { it.text }
}

/** The numbers the app and the server agree on. */
object OpenCodeDefaults {
    /** The port the spec names. */
    const val PORT: Int = 4098

    /** The pinned OpenCode release the app downloads. */
    const val VERSION: String = "1.19.3"

    /** The release tag, which is the version with a v in front. */
    const val TAG: String = "v$VERSION"

    /** The host the server is bound to. Never 0.0.0.0: the page must not be reachable from the network. */
    const val HOST: String = "127.0.0.1"

    /** The launch line, exactly as it is written into Build Info. */
    fun launchLine(port: Int = PORT): String =
        "Launch: opencode web --hostname $HOST --port $port"
}

/**
 * The countdown on the Connection lost screen: "Retrying in 3 s".
 *
 * The app never asks the user to press anything to try again, so this line is the whole promise
 * the screen makes.
 */
fun retryCountdownText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "Retrying in ${safe} s"
}

/**
 * The four steps of the Starting screen, with what has happened so far.
 *
 * Pure, so the exact wording and the order are held by a test rather than by a screenshot.
 */
fun openCodeSteps(
    filesChecked: Boolean = false,
    projectConnected: Boolean = false,
    serverStarted: Boolean = false,
    answered: Boolean = false,
    failed: Boolean = false,
): List<OpenCodeStep> = listOf(
    OpenCodeStep(
        "Checking files",
        when {
            failed -> OpenCodeStepState.Failed
            filesChecked -> OpenCodeStepState.Done
            else -> OpenCodeStepState.Working
        },
        if (filesChecked) "Done" else "",
    ),
    OpenCodeStep(
        "Connecting your project",
        when {
            failed -> OpenCodeStepState.Failed
            projectConnected -> OpenCodeStepState.Done
            else -> OpenCodeStepState.Pending
        },
        if (projectConnected) "Done" else "",
    ),
    OpenCodeStep(
        "Starting the server",
        when {
            failed -> OpenCodeStepState.Failed
            serverStarted -> OpenCodeStepState.Done
            else -> OpenCodeStepState.Pending
        },
        if (serverStarted) "Done" else "",
    ),
    OpenCodeStep(
        "Waiting for it to answer",
        when {
            failed -> OpenCodeStepState.Failed
            answered -> OpenCodeStepState.Done
            else -> OpenCodeStepState.Working
        },
        when {
            answered -> "Done"
            else -> "Waiting"
        },
    ),
)
