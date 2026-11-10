package com.closecode.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.closecode.build.GradleReader
import com.closecode.build.InstallHelper
import com.closecode.build.LogcatReader
import com.closecode.device.control.InstallChecks
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * The device actions exposed by the local `closecode` bridge. Every action uses a public
 * Android API and every one can say no: install still shows the system's own confirmation,
 * screenshots need MediaProjection consent, and input needs the user to enable CloseCode's
 * accessibility service. Nothing here silently reaches another app.
 */
object DeviceTools {

    private const val DEFAULT_APK = "build/outputs/apk/debug/app-debug.apk"

    fun install(
        context: Context,
        project: File,
        argument: String?,
        waitMs: Long = 0,
        openSettings: Boolean = false,
    ): JsonObject {
        val apk = if (argument.isNullOrBlank()) {
            val custom = File(project, DEFAULT_APK).takeIf { it.isFile }
            custom ?: com.closecode.build.GradlePlan.findAnyApk(project)
                ?: com.closecode.build.GradlePlan.findApk(project, "debug")
                ?: com.closecode.build.GradlePlan.findApk(project, "release")
        } else {
            resolve(project, argument)?.takeIf { it.isFile }
        } ?: return error("No APK to install. Build the project first or pass an APK path.")
        val packageName = packageNameOfApk(context, apk)

        // P0-7 / P1-7: a package signed with a different key can never be updated in place.
        // Detect it before opening a session, and hand back the exact recovery commands.
        val apkCertificates = certificatesOf(context, apk = apk)
        val installedCertificates = packageName
            ?.let { certificatesOf(context, packageName = it) }
            .orEmpty()
        if (packageName != null && installedCertificates.isNotEmpty() &&
            apkCertificates.isNotEmpty() &&
            !InstallChecks.certificatesMatch(installedCertificates, apkCertificates)
        ) {
            return InstallChecks.signatureMismatch(
                packageName = packageName,
                installed = installedCertificates,
                incoming = apkCertificates,
            )
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            if (openSettings) {
                openInstallSettings(context)
                // The user is now on the exact system screen. If they grant it while we wait,
                // continue the install instead of making them run the command again.
                val deadline = System.currentTimeMillis() + 60_000
                while (!context.packageManager.canRequestPackageInstalls() &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(500)
                }
            }
            if (!context.packageManager.canRequestPackageInstalls()) {
                return InstallChecks.permissionNeeded(context.packageName, openSettings)
            }
        }
        val before = packageName?.let { packageStamp(context, it) }
        return runCatching {
            val session = InstallHelper.install(context, apk, packageName.orEmpty())
            if (session < 0) {
                InstallChecks.permissionNeeded(context.packageName, openedSettings = false)
            } else {
                val installed = if (waitMs > 0 && packageName != null) {
                    waitForInstall(context, packageName, before, waitMs)
                } else {
                    false
                }
                ok().apply {
                    addProperty("session", session)
                    addProperty("apk", apk.absolutePath)
                    addProperty("package", packageName.orEmpty())
                    if (apkCertificates.isNotEmpty()) {
                        addProperty("signature", apkCertificates.first())
                    }
                    if (installed) {
                        addProperty("installed", true)
                    } else if (waitMs > 0) {
                        addProperty("needsUser", true)
                        addProperty(
                            "note",
                            "Android is still showing its install confirmation. " +
                                "Ask the user to confirm, then run closecode app.",
                        )
                    } else {
                        addProperty("note", "Android is showing its install confirmation.")
                    }
                }
            }
        }.getOrElse { error(it.message ?: it.javaClass.simpleName) }
    }

    fun launch(context: Context, project: File?, packageArgument: String?): JsonObject {
        val packageName = packageArgument?.takeIf { it.isNotBlank() }
            ?: project?.let { packageNameOfProject(it) }
            ?: return error("No package name. Open a built app or pass one to launch.")
        val intent = InstallHelper.launchIntent(context, packageName)
            ?: return error("$packageName is not installed.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ok().apply {
                addProperty("package", packageName)
                addProperty("activity", intent.component?.flattenToShortString().orEmpty())
            }
        }.getOrElse { error("Android did not open $packageName: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun packageName(project: File?, argument: String?): String? =
        argument?.takeIf { it.isNotBlank() } ?: project?.let { packageNameOfProject(it) }

    fun appStatus(context: Context, project: File?, packageArgument: String?): JsonObject {
        val packageName = packageName(project, packageArgument)
        val service = CloseCodeAccessibilityService.current()
        val snapshot = service?.snapshot()
        return ok().apply {
            addProperty("package", packageName.orEmpty())
            if (packageName != null) {
                addProperty("installed", InstallHelper.isInstalled(context, packageName))
            }
            addProperty("accessibility", service != null)
            if (snapshot != null) {
                addProperty("foreground", snapshot.packageName)
                addProperty("activity", snapshot.activity)
                addProperty("width", snapshot.width)
                addProperty("height", snapshot.height)
                addProperty("nodes", snapshot.nodes.size)
                addProperty("truncated", snapshot.truncated)
            }
        }
    }

    fun waitForForeground(
        project: File?,
        packageArgument: String?,
        timeoutMs: Long,
    ): JsonObject {
        val packageName = packageName(project, packageArgument)
            ?: return error("No package name. Open a built app or pass one to wait for.")
        val service = CloseCodeAccessibilityService.current()
            ?: return error("Enable CloseCode in Settings > Accessibility, then try again.")
                .apply { addProperty("needsAccessibility", true) }
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(1_000, 120_000)
        var foreground = service.foregroundPackage()
        while (System.currentTimeMillis() < deadline) {
            foreground = service.foregroundPackage()
            if (foreground == packageName) {
                val snapshot = service.snapshot()
                return ok().apply {
                    addProperty("package", packageName)
                    addProperty("foreground", foreground)
                    addProperty("activity", snapshot.activity)
                }
            }
            Thread.sleep(250)
        }
        return error("$packageName did not come to the foreground.")
            .apply {
                addProperty("package", packageName)
                addProperty("foreground", foreground.orEmpty())
            }
    }

    fun stop(context: Context, project: File?, packageArgument: String?): JsonObject {
        val packageName = packageName(project, packageArgument)
            ?: return error("No package name. Open a built app or pass one to stop.")
        val service = CloseCodeAccessibilityService.current()
        val home = service?.key("home") == true
        val killed = runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.killBackgroundProcesses(packageName)
            true
        }.getOrDefault(false)
        return ok().apply {
            addProperty("package", packageName)
            addProperty("home", home)
            addProperty("killed", killed)
            if (!home) {
                addProperty(
                    "note",
                    "The app was sent to the background. Enable accessibility to press Home first.",
                )
            }
        }
    }

    fun ui(arguments: List<String>): JsonObject {
        val service = CloseCodeAccessibilityService.current()
            ?: return error("Enable CloseCode in Settings > Accessibility, then try again.")
                .apply { addProperty("needsAccessibility", true) }
        val action = arguments.firstOrNull()?.lowercase(Locale.US) ?: "dump"
        val rest = arguments.drop(1)
        AutomationStatus.begin("ui $action")
        return try {
            when (action) {
            "dump", "tree" -> {
                val limit = optionInt(rest, "--limit") ?: 500
                val snapshot = service.snapshot(limit)
                ok().apply {
                    addProperty("action", "dump")
                    add("snapshot", snapshotJson(snapshot))
                }
            }
            "tap" -> {
                val selector = UiSelector.parse(rest)
                    ?: return error("Use: ui tap --text TEXT|--id ID|--desc DESC [--index N]")
                val node = service.find(selector)
                    ?: return error("No visible node matched.")
                        .apply { addProperty("foreground", service.foregroundPackage().orEmpty()) }
                val done = runBlocking { service.tap(selector) }
                if (done) {
                    ok().apply {
                        addProperty("action", "tap")
                        add("node", nodeJson(service.describe(node)))
                    }
                } else {
                    error("Tap was refused.")
                }
            }
            "wait" -> {
                var timeoutMs = 5_000L
                var gone = false
                val selectorArgs = ArrayList<String>()
                var index = 0
                while (index < rest.size) {
                    when (rest[index].lowercase(Locale.US)) {
                        "--timeout" -> {
                            timeoutMs = rest.getOrNull(index + 1)?.toLongOrNull()
                                ?: return error("Use: ui wait ... --timeout MILLISECONDS")
                            index++
                        }
                        "--gone", "--absent" -> gone = true
                        else -> selectorArgs.add(rest[index])
                    }
                    index++
                }
                val selector = UiSelector.parse(selectorArgs)
                    ?: return error("Use: ui wait --text TEXT|--id ID|--desc DESC [--gone]")
                val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(100, 60_000)
                var matched = false
                while (System.currentTimeMillis() < deadline) {
                    val present = service.snapshot().find(selector) != null
                    if (present != gone) {
                        matched = true
                        break
                    }
                    runBlocking { delay(150) }
                }
                JsonObject().apply {
                    addProperty("ok", matched)
                    addProperty("action", "wait")
                    addProperty("found", !gone && matched)
                    addProperty("gone", gone && matched)
                    addProperty("timedOut", !matched)
                    if (!matched) {
                        addProperty(
                            "error",
                            if (gone) "The node was still visible." else "The node was not found.",
                        )
                    }
                    addProperty("foreground", service.foregroundPackage().orEmpty())
                }
            }
            "assert" -> {
                val selector = UiSelector.parse(rest)
                    ?: return error("Use: ui assert --text TEXT|--id ID|--desc DESC")
                val node = service.snapshot().find(selector)
                if (node == null) {
                    error("No visible node matched.")
                        .apply {
                            addProperty("action", "assert")
                            addProperty("found", false)
                            addProperty("foreground", service.foregroundPackage().orEmpty())
                        }
                } else {
                    ok().apply {
                        addProperty("action", "assert")
                        addProperty("found", true)
                        add("node", nodeJson(node))
                    }
                }
            }
            "type", "text" -> {
                val text = rest.joinToString(" ")
                if (text.isEmpty()) return error("Use: ui type --text TEXT or ui type TEXT")
                val value = if (text.startsWith("--text=")) text.removePrefix("--text=") else text
                if (service.text(value)) {
                    ok().apply {
                        addProperty("action", "type")
                        addProperty("characters", value.length)
                    }
                } else {
                    error("No focused text field accepted the text.")
                }
            }
            "scroll" -> {
                val direction = rest.firstOrNull()?.lowercase(Locale.US)
                    ?.takeIf { it == "up" || it == "down" || it == "forward" || it == "back" }
                    ?: "down"
                val selectorArgs = if (direction == rest.firstOrNull()?.lowercase(Locale.US)) {
                    rest.drop(1)
                } else {
                    rest
                }
                val selector = selectorArgs.takeIf { it.isNotEmpty() }?.let { UiSelector.parse(it) }
                val done = runBlocking { service.scroll(direction, selector) }
                if (done) {
                    ok().apply {
                        addProperty("action", "scroll")
                        addProperty("direction", direction)
                    }
                } else {
                    error("There is nothing to scroll.")
                }
            }
            else -> error("Use: ui dump|tap|wait|assert|scroll ...")
            }
        } finally {
            AutomationStatus.end()
        }
    }

    private fun snapshotJson(snapshot: UiSnapshot): JsonObject = JsonObject().apply {
        addProperty("package", snapshot.packageName)
        addProperty("activity", snapshot.activity)
        addProperty("width", snapshot.width)
        addProperty("height", snapshot.height)
        addProperty("truncated", snapshot.truncated)
        add("nodes", JsonArray().apply { snapshot.nodes.forEach { add(nodeJson(it)) } })
    }

    private fun nodeJson(node: UiNode): JsonObject = JsonObject().apply {
        addProperty("text", if (node.password) "[redacted]" else node.text)
        addProperty("description", if (node.password) "" else node.description)
        addProperty("id", node.id)
        addProperty("class", node.className)
        add("bounds", JsonArray().apply { node.bounds.forEach { add(it) } })
        addProperty("clickable", node.clickable)
        addProperty("enabled", node.enabled)
        addProperty("focused", node.focused)
        addProperty("scrollable", node.scrollable)
        addProperty("visible", node.visible)
        addProperty("password", node.password)
        addProperty("depth", node.depth)
    }

    private fun optionInt(arguments: List<String>, name: String): Int? {
        val index = arguments.indexOf(name)
        return if (index >= 0) arguments.getOrNull(index + 1)?.toIntOrNull() else null
    }

    fun logcat(project: File?, packageArgument: String?, limit: Int): JsonObject {
        val packageName = packageArgument?.takeIf { it.isNotBlank() }
            ?: project?.let { packageNameOfProject(it) }.orEmpty()
        val snapshot = LogcatReader.read(packageName, limit.coerceIn(1, 5000))
        return ok().apply {
            addProperty("package", packageName)
            add("lines", JsonArray().apply {
                snapshot.lines.forEach { line ->
                    add(JsonObject().apply {
                        addProperty("index", line.index)
                        addProperty("level", line.level.name.lowercase(Locale.US))
                        addProperty("text", line.text)
                    })
                }
            })
            snapshot.note?.let { addProperty("note", it) }
        }
    }

    fun export(context: Context, project: File, argument: String?): JsonObject {
        val source = if (argument.isNullOrBlank()) {
            project
        } else {
            resolve(project, argument) ?: return error("That path is outside the project.")
        }
        if (!source.exists()) return error("There is nothing to export at ${source.path}.")
        val outputDir = File(context.filesDir, "exports")
        return runCatching {
            val target = ExportHelper.export(source, outputDir)
            ok().apply {
                addProperty("path", target.absolutePath)
                addProperty("bytes", target.length())
                addProperty("kind", if (source.isDirectory) "project" else "file")
            }
        }.getOrElse { error("Export failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun screenshot(context: Context, project: File, argument: String?): JsonObject {
        // Captures are private and short-lived. The project argument is kept for the bridge's
        // signature, but the image never goes into the project or its Git history.
        val output = ScreenshotStore.newFile(context)
        val result = runBlocking { ScreenshotController.capture(context, output) }
        return if (result.ok) {
            ok().apply {
                addProperty("path", output.absolutePath)
                addProperty("bytes", output.length())
                addProperty("expiresInSeconds", ScreenshotStore.MAX_AGE_MS / 1_000)
                addProperty("note", "The screenshot is private and is deleted after 10 minutes.")
            }
        } else {
            error(result.message)
        }
    }

    fun input(arguments: List<String>): JsonObject {
        val action = arguments.firstOrNull()?.lowercase(Locale.US)
            ?: return error("Use: input tap|swipe|text|key ...")
        val service = CloseCodeAccessibilityService.current()
            ?: return error("Enable CloseCode in Settings > Accessibility, then try again.")
                .apply { addProperty("needsAccessibility", true) }
        AutomationStatus.begin("input $action")
        return try {
            when (action) {
            "tap" -> {
                val x = arguments.getOrNull(1)?.toFloatOrNull()
                val y = arguments.getOrNull(2)?.toFloatOrNull()
                if (x == null || y == null || !x.isFinite() || !y.isFinite()) {
                    return error("Use: input tap X Y")
                }
                val done = runBlocking { service.tap(x, y) }
                if (done) ok().apply { addProperty("action", action) } else error("Tap was refused.")
            }
            "swipe" -> {
                val x1 = arguments.getOrNull(1)?.toFloatOrNull()
                val y1 = arguments.getOrNull(2)?.toFloatOrNull()
                val x2 = arguments.getOrNull(3)?.toFloatOrNull()
                val y2 = arguments.getOrNull(4)?.toFloatOrNull()
                if (x1 == null || y1 == null || x2 == null || y2 == null ||
                    !x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite()
                ) {
                    return error("Use: input swipe X1 Y1 X2 Y2 [milliseconds]")
                }
                val duration = arguments.getOrNull(5)?.toLongOrNull()?.coerceIn(50, 10_000) ?: 300
                val done = runBlocking { service.swipe(x1, y1, x2, y2, duration) }
                if (done) ok().apply { addProperty("action", action) } else error("Swipe was refused.")
            }
            "longpress", "long-press" -> {
                val x = arguments.getOrNull(1)?.toFloatOrNull()
                val y = arguments.getOrNull(2)?.toFloatOrNull()
                if (x == null || y == null || !x.isFinite() || !y.isFinite()) {
                    return error("Use: input longpress X Y")
                }
                val done = runBlocking { service.longPress(x, y) }
                if (done) ok().apply { addProperty("action", "longpress") }
                else error("Long press was refused.")
            }
            "text" -> {
                val text = arguments.drop(1).joinToString(" ")
                if (text.isEmpty()) return error("Use: input text TEXT")
                if (service.text(text)) ok().apply { addProperty("action", action) }
                else error("No focused text field accepted the text.")
            }
            "key" -> {
                val key = arguments.getOrNull(1)?.lowercase(Locale.US)
                    ?: return error("Use: input key back|home|recents|notifications")
                if (service.key(key)) ok().apply { addProperty("action", action); addProperty("key", key) }
                else error("Android refused the $key key.")
            }
            else -> error("Use: input tap|swipe|longpress|text|key ...")
            }
        } finally {
            AutomationStatus.end()
        }
    }
    private fun packageStamp(context: Context, packageName: String): Long? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }.lastUpdateTime
    }.getOrNull()

    private fun waitForInstall(
        context: Context,
        packageName: String,
        before: Long?,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(1_000, 120_000)
        while (System.currentTimeMillis() < deadline) {
            val after = packageStamp(context, packageName)
            if (after != null && (before == null || after > before)) return true
            Thread.sleep(400)
        }
        return false
    }

    /**
     * SHA-256 of every signing certificate, for either an installed package or an APK on disk.
     * The same digest both engines' post-signing produces, so the comparison is exact.
     */
    private fun certificatesOf(
        context: Context,
        packageName: String? = null,
        apk: File? = null,
    ): List<String> = runCatching {
        val flag = PackageManager.GET_SIGNING_CERTIFICATES
        val info = when {
            apk != null -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(flag.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apk.absolutePath, flag)
            }
            packageName != null -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flag.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, flag)
            }
            else -> null
        } ?: return@runCatching emptyList()
        val signing = info.signingInfo ?: return@runCatching emptyList()
        val signers = if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners
        } else {
            signing.signingCertificateHistory
        }
        signers.orEmpty().mapNotNull { certificate ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(certificate.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** Opens the exact "install unknown apps" screen for CloseCode, with a details fallback. */
    private fun openInstallSettings(context: Context): Boolean {
        val uri = Uri.parse("package:${context.packageName}")
        val direct = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (direct) return true
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    private fun packageNameOfProject(project: File): String? {
        val fromBuild = runCatching {
            val json = com.google.gson.JsonParser.parseString(
                File(project, "build/build.json").readText(),
            ).asJsonObject
            json.get("packageName")?.asString
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return fromBuild ?: GradleReader.read(project).packageName?.takeIf { it.isNotBlank() }
    }

    private fun packageNameOfApk(context: Context, apk: File): String? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        }
        info?.packageName
    }.getOrNull()

    private fun resolve(project: File, raw: String): File? {
        val candidate = if (File(raw).isAbsolute) File(raw) else File(project, raw)
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        val root = runCatching { project.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf {
            canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)
        }
    }

    private fun ok(): JsonObject = JsonObject().apply { addProperty("ok", true) }

    private fun error(message: String): JsonObject = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", message)
    }
}
