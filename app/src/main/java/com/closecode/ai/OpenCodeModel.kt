package com.closecode.ai

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

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
    /** UUID of the project this server is bound to. Empty while idle. */
    val projectId: String = "",
    /** Absolute path of the project this server is bound to. Empty while idle. */
    val projectDirectory: String = "",
    val url: String = "",
    /** The server URL that opens this project, not the home screen. Empty until the port is known. */
    val pageUrl: String = "",
    /** The root OpenCode session reserved for this project. Empty when it could not be opened. */
    val sessionId: String = "",
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
    /** The loopback proxy port, or zero when the server is running without one. */
    val proxyPort: Int = 0,
    /** URL-safe base64 of `opencode:<password>`, handed to the official V2 Web UI once. */
    val authToken: String = "",
    /** Standard base64 of `opencode:<password>`, used for the V2 HTTP API. */
    val basicAuth: String = "",
)

/** The numbers the app and the server agree on. */
object OpenCodeDefaults {
    /** The port the app tries first. A taken port moves the server to the next free one. */
    const val PORT: Int = 4098

    /**
     * The pinned OpenCode V2 release the app downloads.
     *
     * V2 is distributed as npm platform packages, not as the V1 GitHub release archives.
     * Do not bump this without running the host POC and opening the official page on a
     * real phone.
     */
    const val VERSION: String = "2.2.5"

    /** The npm platform package that holds the ARM64 musl program. */
    const val NPM_PACKAGE: String = "@opencode/cli-linux-arm64-musl"

    /** The pinned npm tarball, whose hash is checked before anything is unpacked. */
    const val NPM_TARBALL: String =
        "https://registry.npmjs.org/@opencode/cli-linux-arm64-musl/-/cli-linux-arm64-musl-$VERSION.tgz"

    /** The program inside the npm tarball. */
    const val NPM_BINARY_PATH: String = "package/bin/opencode"

    /** The SHA-256 of the pinned tarball. */
    const val NPM_TARBALL_SHA256: String =
        "ef26ea9a81b8adf4185a47b7d3aa5bb3a2a69bc4297cbdaaff9641881339b594"

    /** The release tag, kept for the historical tests and logs. */
    const val TAG: String = "v$VERSION"

    /**
     * The host the server is bound to. Never 0.0.0.0: the page must not be reachable from the
     * network. V2 still requires a password on this loopback, and the app generates one.
     */
    const val HOST: String = "127.0.0.1"

    /** The launch line, exactly as it is written into the log. */
    fun launchLine(port: Int = PORT): String =
        "Launch: opencode --print-logs serve --hostname $HOST --port $port"
}

/**
 * The fallback page URL when no project session could be opened.
 *
 * V2's router has no base64 directory route. The server itself is started with the project as
 * its working directory, so the official home page is the project-aware fallback. The app
 * normally opens a real session route instead.
 */
fun projectPageUrl(baseUrl: String, projectRoot: File): String =
    projectPageUrl(baseUrl, projectRoot.absolutePath)

/** The same URL for a directory already in the form the server reports. */
fun projectPageUrl(baseUrl: String, directory: String): String {
    if (baseUrl.isBlank()) return ""
    return baseUrl.trimEnd('/') + "/"
}

/**
 * The `auth_token` the official V2 Web UI reads from its query string.
 *
 * It is base64url of `opencode:<password>`, with no padding, exactly as the V2 bundle's
 * `encode` helper writes it. The UI removes the parameter from the address after boot.
 */
internal fun openCodeAuthToken(password: String): String =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString("opencode:$password".toByteArray(StandardCharsets.UTF_8))

/** The standard Basic-auth value for the V2 HTTP API. */
internal fun openCodeBasicAuth(password: String): String =
    Base64.getEncoder()
        .encodeToString("opencode:$password".toByteArray(StandardCharsets.UTF_8))

/** The password inside a Basic-auth value, or null when it is not the V2 form. */
internal fun openCodePasswordFromBasicAuth(basicAuth: String): String? {
    if (basicAuth.isBlank()) return null
    val decoded = runCatching {
        String(Base64.getDecoder().decode(basicAuth), StandardCharsets.UTF_8)
    }.getOrNull() ?: return null
    if (!decoded.startsWith("opencode:")) return null
    return decoded.removePrefix("opencode:")
}

internal fun openCodePagePath(baseUrl: String, pageUrl: String): String? {
    if (baseUrl.isBlank() || pageUrl.isBlank()) return null
    val base = runCatching { URI(baseUrl.trimEnd('/')) }.getOrNull() ?: return null
    val page = runCatching { URI(pageUrl) }.getOrNull() ?: return null
    if (page.scheme != base.scheme || page.host != base.host || page.port != base.port) return null
    val path = page.rawPath ?: return null
    if (!path.startsWith("/") || path.startsWith("//")) return null
    return buildString {
        append(path)
        page.rawQuery?.takeIf { it.isNotEmpty() }?.let { append('?').append(it) }
        page.rawFragment?.takeIf { it.isNotEmpty() }?.let { append('#').append(it) }
    }
}

internal fun openCodePageUrl(baseUrl: String, path: String): String =
    baseUrl.trimEnd('/') + "/" + path.trimStart('/')

internal fun openCodeServerKey(baseUrl: String): String =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(baseUrl.trimEnd('/').toByteArray(StandardCharsets.UTF_8))

internal fun rebaseOpenCodeServerPath(path: String, baseUrl: String): String {
    if (baseUrl.isBlank()) return path
    val prefix = "/server/"
    if (!path.startsWith(prefix)) return path
    val end = path.indexOf('/', prefix.length)
    if (end <= prefix.length) return path
    val encoded = path.substring(prefix.length, end)
    val decoded = runCatching {
        String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
    }.getOrNull() ?: return path
    val server = runCatching { URI(decoded) }.getOrNull() ?: return path
    val host = server.host ?: return path
    if (server.scheme != "http" || host !in setOf("127.0.0.1", "localhost", "::1")) return path
    return prefix + openCodeServerKey(baseUrl) + path.substring(end)
}

internal fun isRestorableOpenCodePage(path: String): Boolean {
    if (!path.startsWith("/") || path.startsWith("//")) return false
    val target = path.substringBefore('#').substringBefore('?')
    if (target.isBlank() || target == "/") return false
    if (target == "/new-session") {
        return path.substringAfter('?', "")
            .split('&')
            .any {
                it.substringBefore('=') == "draftId" && it.substringAfter('=', "").isNotBlank()
            }
    }
    val parts = target.trim('/').split('/')
    return when {
        parts.size >= 4 && parts[0] == "server" && parts[2] == "session" -> parts[3].isNotBlank()
        parts.size >= 3 && parts[1] == "session" -> parts[2].isNotBlank()
        else -> false
    }
}

/**
 * Whether two project paths name the same folder.
 *
 * Android exposes the same app directory as both `/data/user/0/...` and `/data/data/...`; the
 * canonical path resolves that alias. Paths that are blank are never the same project: an idle
 * server has no project to compare.
 */
fun sameProjectPath(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    val left = runCatching { File(first).canonicalPath }.getOrDefault(first)
    val right = runCatching { File(second).canonicalPath }.getOrDefault(second)
    return left == right
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
 * How long a running server may stay silent before it is treated as gone.
 *
 * OpenCode's health endpoint can stop answering while the server bootstraps a project: the
 * first request touches the project, starts watchers and reads configuration. On a phone that
 * work can take a minute, especially while OpenCode is timing out on its own network calls.
 * Killing the process after a few seconds turns that slow first request into a restart loop, so
 * the same budget as the initial start is allowed before the supervisor gives up.
 */
internal const val SERVER_SILENCE_LIMIT_MS = 120_000L

/**
 * Whether the server should be restarted after [silentForMs] without a health answer.
 *
 * A dead process is always restarted at once. A live process gets the full grace period, which
 * is what lets a slow project bootstrap finish instead of being mistaken for a crash.
 */
internal fun serverNeedsRestart(processAlive: Boolean, silentForMs: Long): Boolean =
    !processAlive || silentForMs >= SERVER_SILENCE_LIMIT_MS

/**
 * What the AI tab says when this phone's CPU is not one OpenCode ships a build for.
 *
 * The list is the phone's own, in its own order, so a report of the message says which ABIs the
 * phone actually reported instead of leaving the reason a guess.
 */
fun unsupportedCpuMessage(abis: List<String>): String {
    val reported = abis.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "no ABI" }
    return "This phone reports $reported. CloseCode V2 needs a 64-bit ARM (arm64-v8a) CPU."
}

/**
 * The version in an OpenCode V2 `/api/info` answer, or null when the server is not healthy.
 *
 * V2 answers with `{ version, pid, urls, paths }`; it has no `healthy` flag. A 200 answer with
 * a version is healthy. An empty version is still a live server, which is why this returns an
 * empty string rather than null. Parsed with a regex because this runs once a second and the
 * app needs one field.
 */
fun healthyVersion(json: String): String? {
    if (Regex("\"healthy\"\\s*:\\s*false").containsMatchIn(json)) return null
    return Regex("\"version\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
}
