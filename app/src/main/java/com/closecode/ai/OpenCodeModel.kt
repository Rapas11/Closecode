package com.closecode.ai

/** What the AI tab's OpenCode screens are doing right now. */
enum class OpenCodePhase {
    /** Nothing has been started. The screen offers the Open button. */
    Idle,

    /** Starting: the binary is being fetched if needed, then the server is launched. */
    Starting,

    /** The server answered its health check. The web page is opened. */
    Running,

    /** The server stopped answering. The app is restarting it by itself. */
    ConnectionLost,

    /** Something went wrong and the app cannot go on by itself. The reason is on the screen. */
    Failed,
}

/**
 * One line of the log the AI tab shows.
 *
 * [warning] marks the lines that say the app is still waiting or that something went wrong.
 */
data class OpenCodeLogLine(val text: String, val warning: Boolean = false)

/**
 * Everything the AI tab shows.
 *
 * One value, held by [OpenCode.state], so the screen, the notification and the log read the
 * same truth: the phase, the URL, the version, the log, and how many times a server has come
 * up. [generation] changes every time a fresh server answers, which is what tells the open web
 * page to reload after a reconnect.
 */
data class OpenCodeStatus(
    val phase: OpenCodePhase = OpenCodePhase.Idle,
    val projectName: String = "",
    val projectFolder: String = "",
    val url: String = "",
    val version: String = "",
    val port: Int = OpenCodeDefaults.PORT,
    /** The log, oldest first. */
    val log: List<OpenCodeLogLine> = emptyList(),
    /** Seconds until the next automatic retry. Zero when nothing is being retried. */
    val retryInSeconds: Int = 0,
    /** Why it stopped, when it stopped by itself. */
    val error: String? = null,
    /** True while the binary or its runtime is being downloaded. */
    val downloading: Boolean = false,
    /** Percent of the current download, zero when nothing is downloading. */
    val downloadPercent: Int = 0,
    /** Raised once for every server that answered. The web page reloads when it changes. */
    val generation: Int = 0,
)

/** The numbers the app and the server agree on. */
object OpenCodeDefaults {
    /** The port the app tries first. A taken port moves the server to the next free one. */
    const val PORT: Int = 4098

    /**
     * The pinned OpenCode release the app downloads.
     *
     * 1.18.31 is the release the AndroidIDE app uses on these phones, and its web page runs in
     * the phone's WebView. 1.19.4's page crashes on an older WebView before the session view
     * renders, which is the black page with only the top bar. Do not bump this without opening
     * the page on a real phone.
     */
    const val VERSION: String = "1.18.31"

    /** The release tag, which is the version with a v in front. */
    const val TAG: String = "v$VERSION"

    /**
     * The host the server is bound to. Never 0.0.0.0: the page must not be reachable from the
     * network. The server is unsecured on this loopback, which OpenCode documents as fine for
     * local use.
     */
    const val HOST: String = "127.0.0.1"

    /** The launch line, exactly as it is written into the log. */
    fun launchLine(port: Int = PORT): String =
        "Launch: opencode web --hostname $HOST --port $port"
}

/**
 * The countdown on the reconnecting screen: "Retrying in 3 s".
 *
 * The app never asks the user to press anything to try again, so this line is the whole promise
 * the screen makes.
 */
fun retryCountdownText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "Retrying in ${safe} s"
}

/**
 * What the AI tab says when this phone's CPU is not one OpenCode ships a build for.
 *
 * The list is the phone's own, in its own order, so a report of the message says which ABIs the
 * phone actually reported instead of leaving the reason a guess.
 */
fun unsupportedCpuMessage(abis: List<String>): String {
    val reported = abis.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "no ABI" }
    return "This phone reports $reported. OpenCode needs a 64-bit ARM (arm64-v8a) or " +
        "Intel (x86_64) CPU."
}

/**
 * The version in an OpenCode `/global/health` answer, or null when the server is not healthy.
 *
 * The endpoint is documented as `{ healthy: true, version: string }`. An empty string is a
 * healthy answer with no version in it — still up — which is why this does not return null for
 * it. Parsed with a regex rather than a mapped class because the app only needs these two
 * fields and this runs once a second.
 */
fun healthyVersion(json: String): String? {
    if (!Regex("\"healthy\"\\s*:\\s*true").containsMatchIn(json)) return null
    return Regex("\"version\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1).orEmpty()
}
