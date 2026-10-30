package com.closecode.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.closecode.build.GradleReader
import com.closecode.build.InstallHelper
import com.closecode.build.LogcatReader
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * The device actions exposed by the local `closecode` bridge. Every action uses a public
 * Android API and every one can say no: install still shows the system's own confirmation,
 * screenshots need MediaProjection consent, and input needs the user to enable CloseCode's
 * accessibility service. Nothing here silently reaches another app.
 */
object DeviceTools {

    private const val DEFAULT_APK = "build/outputs/apk/debug/app-debug.apk"

    fun install(context: Context, project: File, argument: String?): JsonObject {
        val apk = if (argument.isNullOrBlank()) {
            File(project, DEFAULT_APK).takeIf { it.isFile }
        } else {
            resolve(project, argument)?.takeIf { it.isFile }
        } ?: return error("No APK to install. Build the project first or pass an APK path.")
        if (!context.packageManager.canRequestPackageInstalls()) {
            return error("Allow CloseCode to install apps in Settings, then try again.")
                .apply { addProperty("needsPermission", true) }
        }
        val packageName = packageNameOfApk(context, apk)
        return runCatching {
            val session = InstallHelper.install(context, apk, packageName.orEmpty())
            if (session < 0) {
                error("Allow CloseCode to install apps in Settings, then try again.")
                    .apply { addProperty("needsPermission", true) }
            } else {
                ok().apply {
                    addProperty("session", session)
                    addProperty("apk", apk.absolutePath)
                    addProperty("package", packageName.orEmpty())
                    addProperty("note", "Android is showing its install confirmation.")
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
        return when (action) {
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
            else -> error("Use: input tap|swipe|text|key ...")
        }
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
