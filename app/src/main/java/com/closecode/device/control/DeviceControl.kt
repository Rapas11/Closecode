package com.closecode.device.control

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import com.closecode.build.ProcessTree
import com.closecode.control.CommandEnvelope
import com.closecode.device.AutomationStatus
import com.closecode.device.CloseCodeAccessibilityService
import com.closecode.device.DeviceTools
import com.closecode.jobs.JobManager
import com.closecode.tools.ShellEnvironment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * P3 device control: package-targeted app actions, intents, device facts, a scoped file root,
 * and `exec`. Sensitive capabilities are granted per capability and every call is audited.
 */
internal object DeviceControl {

    private val protectedHints = listOf(
        "bank", "banco", "password", "passwd", "authenticator", "otp", "2fa",
        "wallet", "crypto", "keychain", "vault", "paypal", "venmo", "revolut",
    )

    fun isProtected(packageName: String?): Boolean {
        val name = packageName?.lowercase() ?: return false
        return protectedHints.any { name.contains(it) }
    }

    fun checkUiConsent(context: Context, packageName: String?): JsonObject? {
        if (!isProtected(packageName)) return null
        if (ConsentStore(context).isScopedGranted("ui-automation", packageName)) return null
        return CommandEnvelope.failed(
            "UI control of $packageName is blocked by default because it looks like a sensitive app.",
            code = "needs_consent",
            remedy = "Run closecode consent grant ui-automation --package $packageName only if you really want this.",
        ).apply {
            addProperty("package", packageName.orEmpty())
            addProperty("scope", packageName.orEmpty())
        }
    }

    fun apps(context: Context, projectDir: File?, arguments: List<String>): JsonObject {
        val action = arguments.firstOrNull()?.lowercase(Locale.US) ?: "list"
        val rest = arguments.drop(1)
        val audit = AuditLog(context)
        val consent = ConsentStore(context)
        val result = when (action) {
            "list", "ls" -> listApps(context, rest)
            "launch", "open" -> {
                val pkg = rest.firstOrNull()?.takeIf { !it.startsWith("-") }
                val activity = option(rest, "--activity")
                if (activity != null && pkg != null) {
                    launchComponent(context, pkg, activity)
                } else {
                    DeviceTools.launch(context, projectDir, pkg)
                }
            }
            "stop", "kill" -> DeviceTools.stop(context, projectDir, rest.firstOrNull())
            "info", "show" -> appInfo(context, projectDir, rest.firstOrNull())
            "install" -> {
                val apk = rest.firstOrNull()
                if (apk == null) {
                    CommandEnvelope.failed("usage: closecode apps install <apk>", code = "usage")
                } else {
                    installApk(context, File(apk))
                }
            }
            "uninstall", "remove" -> uninstall(context, rest)
            "clear", "clear-data" -> clearData(context, rest)
            else -> CommandEnvelope.failed(
                "usage: closecode apps list|launch|stop|info|install|uninstall|clear",
                code = "usage",
            )
        }
        audit.record("apps $action", rest.firstOrNull(), rest, result.ok(), result.get("error")?.asString)
        return result
    }

    private fun listApps(context: Context, arguments: List<String>): JsonObject {
        val query = option(arguments, "--query")?.lowercase()
        val includeSystem = arguments.contains("--all")
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(0)
        val apps = packages.asSequence()
            .filter { includeSystem || ((it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { query == null || it.packageName.lowercase().contains(query) }
            .take(arguments.let { option(it, "--limit")?.toIntOrNull() } ?: 500)
            .map { info ->
                JsonObject().apply {
                    addProperty("package", info.packageName)
                    addProperty("label", runCatching { pm.getApplicationLabel(info.applicationInfo ?: return@runCatching "?").toString() }.getOrDefault(""))
                    addProperty("versionName", info.versionName.orEmpty())
                    addProperty("versionCode", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
                    addProperty("updatedAt", info.lastUpdateTime)
                    addProperty("system", ((info.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                }
            }
            .toList()
        return CommandEnvelope.of(ok = true).apply {
            addProperty("count", apps.size)
            add("apps", JsonArray().apply { apps.forEach { add(it) } })
        }
    }

    private fun appInfo(context: Context, projectDir: File?, argument: String?): JsonObject {
        val packageName = DeviceTools.packageName(projectDir, argument)
            ?: return CommandEnvelope.failed("Pass a package name.", code = "usage")
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }.getOrNull() ?: return CommandEnvelope.failed("$packageName is not installed.", code = "not_installed")
        val snapshot = CloseCodeAccessibilityService.current()?.snapshot()
        return CommandEnvelope.of(ok = true).apply {
            addProperty("package", packageName)
            addProperty("label", runCatching { pm.getApplicationLabel(info.applicationInfo ?: return@runCatching "?").toString() }.getOrDefault(""))
            addProperty("versionName", info.versionName.orEmpty())
            addProperty("versionCode", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
            addProperty("firstInstallTime", info.firstInstallTime)
            addProperty("lastUpdateTime", info.lastUpdateTime)
            addProperty("targetSdk", info.applicationInfo?.targetSdkVersion ?: 0)
            addProperty("foreground", snapshot?.packageName.orEmpty())
            add("permissions", JsonArray().apply { info.requestedPermissions?.forEach { add(it) } })
            add("activities", JsonArray().apply {
                runCatching { pm.getLaunchIntentForPackage(packageName)?.component?.flattenToShortString() }
                    .getOrNull()?.let { add(it) }
            })
        }
    }

    private fun launchComponent(context: Context, packageName: String, activity: String): JsonObject {
        val component = if ('/' in activity) activity else "$packageName/$activity"
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(packageName, activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            CommandEnvelope.of(ok = true).apply {
                addProperty("package", packageName)
                addProperty("activity", component)
            }
        }.getOrElse {
            CommandEnvelope.failed(
                "Android did not open $component: ${it.message}",
                code = "launch_failed",
            )
        }
    }

    private fun installApk(context: Context, apk: File): JsonObject {
        if (!apk.isFile) return CommandEnvelope.failed("APK not found: ${apk.path}", code = "not_found")
        if (!context.packageManager.canRequestPackageInstalls()) {
            return CommandEnvelope.failed(
                "Allow CloseCode to install unknown apps first.",
                code = "needs_permission",
                remedy = "Open Settings > Apps > CloseCode > Install unknown apps and allow it.",
            )
        }
        val session = com.closecode.build.InstallHelper.install(context, apk)
        return if (session < 0) {
            CommandEnvelope.failed("Android refused the install session.", code = "install_failed")
        } else {
            CommandEnvelope.of(ok = true).apply {
                addProperty("session", session)
                addProperty("apk", apk.absolutePath)
                addProperty("note", "Android is showing its install confirmation.")
            }
        }
    }

    private fun uninstall(context: Context, arguments: List<String>): JsonObject {
        val packageName = arguments.firstOrNull { !it.startsWith("-") }
            ?: return CommandEnvelope.failed("usage: closecode apps uninstall <pkg> --yes", code = "usage")
        if (!arguments.contains("--yes")) {
            return CommandEnvelope.failed(
                "Uninstalling $packageName needs confirmation.",
                code = "needs_confirmation",
                remedy = "Run closecode apps uninstall $packageName --yes",
            )
        }
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            CommandEnvelope.of(ok = true).apply {
                addProperty("package", packageName)
                addProperty("needsUser", true)
                addProperty("note", "Android is showing its uninstall confirmation.")
            }
        }.getOrElse {
            CommandEnvelope.failed(it.message ?: "Could not ask Android to uninstall.", code = "uninstall_failed")
        }
    }

    private fun clearData(context: Context, arguments: List<String>): JsonObject {
        val packageName = arguments.firstOrNull { !it.startsWith("-") }
            ?: return CommandEnvelope.failed("usage: closecode apps clear <pkg>", code = "usage")
        return CommandEnvelope.failed(
            "Android does not let one app clear another app's data without root or adb.",
            code = "needs_user",
            remedy = "Open Settings > Apps > $packageName > Storage > Clear data on the phone.",
        ).apply {
            addProperty("package", packageName)
            addProperty("settingsAction", Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    fun intent(context: Context, projectDir: File?, arguments: List<String>): JsonObject {
        val action = arguments.firstOrNull()?.lowercase(Locale.US) ?: "open"
        val rest = arguments.drop(1)
        val audit = AuditLog(context)
        val result = runCatching {
            when (action) {
                "open", "view" -> {
                    val uri = rest.firstOrNull() ?: return CommandEnvelope.failed("usage: closecode intent open <uri>", code = "usage")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    resolved(context, intent, uri)
                }
                "start", "activity" -> {
                    val component = rest.firstOrNull() ?: return CommandEnvelope.failed(
                        "usage: closecode intent start <package/activity>",
                        code = "usage",
                    )
                    val intent = if ('/' in component) {
                        val pkg = component.substringBefore('/')
                        Intent().setClassName(pkg, component)
                    } else {
                        context.packageManager.getLaunchIntentForPackage(component)
                            ?: return CommandEnvelope.failed("$component is not installed.", code = "not_installed")
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    resolved(context, intent, component)
                }
                "broadcast", "send" -> {
                    val name = rest.firstOrNull() ?: return CommandEnvelope.failed(
                        "usage: closecode intent broadcast <action>",
                        code = "usage",
                    )
                    val intent = Intent(name)
                    context.sendBroadcast(intent)
                    resolved(context, intent, name)
                }
                "share", "send-file" -> share(context, rest.firstOrNull())
                else -> CommandEnvelope.failed("usage: closecode intent open|start|broadcast|share", code = "usage")
            }
        }.getOrElse {
            CommandEnvelope.failed(it.message ?: "The intent failed.", code = "intent_failed")
        }
        audit.record("intent $action", null, rest, result.ok(), result.get("error")?.asString)
        return result
    }

    private fun resolved(context: Context, intent: Intent, target: String): JsonObject {
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return CommandEnvelope.of(ok = true).apply {
            addProperty("target", target)
            addProperty("action", intent.action.orEmpty())
            addProperty("component", intent.component?.flattenToShortString()
                ?: info?.activityInfo?.let { "${it.packageName}/${it.name}" }.orEmpty())
        }
    }

    private fun share(context: Context, path: String?): JsonObject {
        if (path.isNullOrBlank()) return CommandEnvelope.failed("usage: closecode intent share <file>", code = "usage")
        val file = File(path)
        if (!file.isFile) return CommandEnvelope.failed("File not found: $path", code = "not_found")
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return CommandEnvelope.of(ok = true).apply {
            addProperty("artifact", file.absolutePath)
            addProperty("uri", uri.toString())
        }
    }

    fun device(context: Context, arguments: List<String>): JsonObject {
        val section = arguments.firstOrNull()?.lowercase(Locale.US) ?: "all"
        val rest = arguments.drop(1)
        val result = CommandEnvelope.of(ok = true)
        when (section) {
            "battery", "all" -> battery(context, result)
            "network", "all" -> network(context, result)
            "storage", "all" -> storage(context, result)
            "display", "all" -> display(context, result)
            "volume", "all" -> volume(context, result, rest)
            "brightness", "all" -> brightness(context, result, rest)
            "dnd", "all" -> dnd(context, result)
            "clipboard" -> clipboard(context, result, rest)
            "sensors", "all" -> sensors(context, result)
            "orientation", "all" -> {
                result.addProperty("orientation",
                    if (context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
            }
            else -> return CommandEnvelope.failed(
                "usage: closecode device battery|network|storage|display|volume|brightness|dnd|clipboard|sensors|orientation|all",
                code = "usage",
            )
        }
        return result
    }

    private fun battery(context: Context, out: JsonObject) {
        val manager = context.getSystemService(BatteryManager::class.java)
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        out.add("battery", JsonObject().apply {
            addProperty("level", manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1)
            addProperty("charging", intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING)
            addProperty("plugged", intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)
            addProperty("temperatureC", (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0)
        })
    }

    private fun network(context: Context, out: JsonObject) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val caps = network?.let { manager.getNetworkCapabilities(it) }
        val addresses = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
        }.getOrDefault(emptyList())
        out.add("network", JsonObject().apply {
            addProperty("wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
            addProperty("cellular", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
            addProperty("metered", manager?.isActiveNetworkMetered ?: false)
            add("ip", JsonArray().apply { addresses.forEach { add(it) } })
        })
    }

    private fun storage(context: Context, out: JsonObject) {
        val stats = StatFs(context.filesDir.absolutePath)
        out.add("storage", JsonObject().apply {
            addProperty("totalBytes", stats.totalBytes)
            addProperty("freeBytes", stats.availableBytes)
        })
    }

    private fun display(context: Context, out: JsonObject) {
        val metrics = context.resources.displayMetrics
        out.add("display", JsonObject().apply {
            addProperty("width", metrics.widthPixels)
            addProperty("height", metrics.heightPixels)
            addProperty("density", metrics.density)
            addProperty("densityDpi", metrics.densityDpi)
        })
    }

    private fun volume(context: Context, out: JsonObject, arguments: List<String>) {
        val manager = context.getSystemService(AudioManager::class.java)
        val desired = arguments.firstOrNull()?.toIntOrNull()
        if (desired != null && manager != null) {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, desired, 0)
        }
        out.add("volume", JsonObject().apply {
            addProperty("music", manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1)
            addProperty("max", manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1)
            addProperty("ring", manager?.getStreamVolume(AudioManager.STREAM_RING) ?: -1)
        })
    }

    private fun brightness(context: Context, out: JsonObject, arguments: List<String>) {
        val desired = arguments.firstOrNull()?.toIntOrNull()
        if (desired != null) {
            if (!Settings.System.canWrite(context)) {
                out.addProperty("ok", false)
                out.addProperty("error", "CloseCode cannot change brightness without the system permission.")
                out.addProperty("code", "needs_permission")
                out.addProperty("remedy", "Open Settings > Apps > CloseCode > Modify system settings and allow it.")
                return
            }
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, desired.coerceIn(0, 255))
        }
        out.addProperty("brightness", runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(-1))
    }

    private fun dnd(context: Context, out: JsonObject) {
        val manager = context.getSystemService(NotificationManager::class.java)
        out.add("dnd", JsonObject().apply {
            addProperty("access", manager?.isNotificationPolicyAccessGranted ?: false)
            addProperty("filter", manager?.currentInterruptionFilter ?: -1)
        })
    }

    private fun clipboard(context: Context, out: JsonObject, arguments: List<String>) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = arguments.joinToString(" ").takeIf { it.isNotBlank() }
        if (text != null) manager?.setPrimaryClip(ClipData.newPlainText("CloseCode", text))
        out.add("clipboard", JsonObject().apply {
            addProperty("text", runCatching { manager?.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull().orEmpty())
        })
    }

    private fun sensors(context: Context, out: JsonObject) {
        val manager = context.getSystemService(SensorManager::class.java)
        out.add("sensors", JsonArray().apply {
            manager?.getSensorList(android.hardware.Sensor.TYPE_ALL)?.forEach { sensor ->
                add(JsonObject().apply {
                    addProperty("name", sensor.name)
                    addProperty("type", sensor.type)
                    addProperty("vendor", sensor.vendor)
                })
            }
        })
    }

    fun files(context: Context, arguments: List<String>): JsonObject {
        val consent = ConsentStore(context)
        if (!consent.isGranted("files")) {
            return CommandEnvelope.failed(
                "The files capability is not granted.",
                code = "needs_consent",
                remedy = "Run closecode consent grant files",
            )
        }
        val root = File(context.getExternalFilesDir(null), "shared").apply { mkdirs() }
        val action = arguments.firstOrNull()?.lowercase(Locale.US) ?: "list"
        val rest = arguments.drop(1)
        val result = when (action) {
            "list", "ls" -> {
                val target = resolve(root, rest.firstOrNull()) ?: root
                val files = target.listFiles().orEmpty().map { file ->
                    JsonObject().apply {
                        addProperty("name", file.name)
                        addProperty("path", file.absolutePath)
                        addProperty("directory", file.isDirectory)
                        addProperty("bytes", if (file.isFile) file.length() else 0L)
                    }
                }
                CommandEnvelope.of(ok = true).apply {
                    addProperty("root", root.absolutePath)
                    add("files", JsonArray().apply { files.forEach { add(it) } })
                }
            }
            "read", "cat" -> {
                val file = resolve(root, rest.firstOrNull())
                    ?: return CommandEnvelope.failed("usage: closecode files read <path>", code = "usage")
                if (!file.isFile) return CommandEnvelope.failed("Not a file: ${file.path}", code = "not_found")
                CommandEnvelope.of(ok = true).apply {
                    addProperty("path", file.absolutePath)
                    addProperty("content", file.readText().take(1_000_000))
                }
            }
            "write" -> {
                val file = resolve(root, rest.firstOrNull())
                    ?: return CommandEnvelope.failed("usage: closecode files write <path> <text>", code = "usage")
                file.parentFile?.mkdirs()
                file.writeText(rest.drop(1).joinToString(" "))
                CommandEnvelope.of(ok = true).apply {
                    addProperty("path", file.absolutePath)
                    addProperty("bytes", file.length())
                }
            }
            "share", "publish", "media-save" -> shareToMediaStore(context, root, rest)
            "media", "media-list" -> listMediaStore(context, rest)
            "uri", "media-read" -> readMediaStore(context, rest)
            "delete", "rm" -> {
                val file = resolve(root, rest.firstOrNull())
                    ?: return CommandEnvelope.failed("usage: closecode files delete <path> --yes", code = "usage")
                if (!arguments.contains("--yes")) {
                    return CommandEnvelope.failed(
                        "Deleting ${file.path} needs confirmation.",
                        code = "needs_confirmation",
                        remedy = "Run closecode files delete ${rest.firstOrNull()} --yes",
                    )
                }
                CommandEnvelope.of(ok = file.deleteRecursively()).apply {
                    addProperty("path", file.absolutePath)
                }
            }
            else -> CommandEnvelope.failed(
                "usage: closecode files list|read|write|delete|share <path>|media|uri <content-uri>",
                code = "usage",
            )
        }
        AuditLog(context).record("files $action", null, rest, result.ok(), result.get("error")?.asString)
        return result
    }

    private fun shareToMediaStore(context: Context, root: File, arguments: List<String>): JsonObject {
        val source = resolve(root, arguments.firstOrNull())
            ?: return CommandEnvelope.failed("usage: closecode files share <path> [--name NAME]", code = "usage")
        if (!source.isFile) return CommandEnvelope.failed("Not a file: ${source.path}", code = "not_found")
        val name = option(arguments, "--name") ?: source.name
        val mime = mimeType(name)
        val collection = when {
            mime.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> if (Build.VERSION.SDK_INT >= 29) {
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                android.provider.MediaStore.Files.getContentUri("external")
            }
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, if (mime.startsWith("image/") || mime.startsWith("video/")) "Pictures/CloseCode" else "Download/CloseCode")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return runCatching {
            val uri = context.contentResolver.insert(collection, values)
                ?: return CommandEnvelope.failed("The shared media store refused the file.", code = "file_failed")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: throw java.io.IOException("Could not open the destination.")
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.update(
                    uri,
                    android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    },
                    null,
                    null,
                )
            }
            CommandEnvelope.of(ok = true).apply {
                addProperty("path", source.absolutePath)
                addProperty("uri", uri.toString())
                addProperty("name", name)
                addProperty("mime", mime)
                addProperty("bytes", source.length())
            }
        }.getOrElse {
            CommandEnvelope.failed(
                "Could not write to the shared media store: ${it.message}",
                code = if (it is SecurityException) "needs_permission" else "file_failed",
                remedy = "Grant storage permission to CloseCode in system settings, then retry.",
            )
        }
    }

    private fun listMediaStore(context: Context, arguments: List<String>): JsonObject {
        val limit = option(arguments, "--limit")?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Files.getContentUri("external")
        }
        return runCatching {
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.SIZE,
                android.provider.MediaStore.MediaColumns.MIME_TYPE,
            )
            val rows = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC",
            )
            val items = JsonArray()
            rows?.use { cursor ->
                while (cursor.moveToNext() && items.size() < limit) {
                    items.add(JsonObject().apply {
                        addProperty("uri", android.content.ContentUris.withAppendedId(
                            collection,
                            cursor.getLong(0),
                        ).toString())
                        addProperty("name", cursor.getString(1).orEmpty())
                        addProperty("bytes", cursor.getLong(2))
                        addProperty("mime", cursor.getString(3).orEmpty())
                    })
                }
            }
            CommandEnvelope.of(ok = true).apply { add("media", items) }
        }.getOrElse {
            CommandEnvelope.failed(
                "Reading shared media needs storage permission: ${it.message}",
                code = if (it is SecurityException) "needs_permission" else "file_failed",
                remedy = "Grant storage/media permission to CloseCode in system settings, then retry.",
            )
        }
    }

    private fun readMediaStore(context: Context, arguments: List<String>): JsonObject {
        val raw = arguments.firstOrNull()
            ?: return CommandEnvelope.failed("usage: closecode files uri <content-uri> [--output PATH]", code = "usage")
        val uri = android.net.Uri.parse(raw)
        val output = option(arguments, "--output")
        return runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: return CommandEnvelope.failed("Could not open $raw", code = "not_found")
            input.use { stream ->
                if (output != null) {
                    val file = File(output).takeIf { it.isAbsolute }
                        ?: File(context.getExternalFilesDir(null), "shared/$output")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { stream.copyTo(it) }
                    CommandEnvelope.of(ok = true).apply {
                        addProperty("uri", raw)
                        addProperty("path", file.absolutePath)
                        addProperty("bytes", file.length())
                    }
                } else {
                    val text = stream.bufferedReader().use { it.readText() }.take(1_000_000)
                    CommandEnvelope.of(ok = true).apply {
                        addProperty("uri", raw)
                        addProperty("content", text)
                    }
                }
            }
        }.getOrElse {
            CommandEnvelope.failed(
                "Could not read $raw: ${it.message}",
                code = if (it is SecurityException) "needs_permission" else "file_failed",
            )
        }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "txt", "log", "md" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun exec(
        context: Context,
        projectDir: File,
        settingsEnvironment: Map<String, String>,
        arguments: List<String>,
        onEvent: (JsonObject) -> Unit,
    ): JsonObject {
        val consent = ConsentStore(context)
        if (!consent.isGranted("shell")) {
            return CommandEnvelope.failed(
                "The shell capability is not granted.",
                code = "needs_consent",
                remedy = "Run closecode consent grant shell",
            )
        }
        var cwd = projectDir
        var timeoutMs = 120_000L
        var approved = false
        val command = mutableListOf<String>()
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "--approve", "--yes" -> approved = true
                "--cwd" -> {
                    arguments.getOrNull(index + 1)?.let { cwd = File(projectDir, it) }
                    index++
                }
                "--timeout" -> {
                    timeoutMs = arguments.getOrNull(index + 1)?.toLongOrNull()?.coerceIn(1_000, 3_600_000)
                        ?: timeoutMs
                    index++
                }
                else -> command += argument
            }
            index++
        }
        if (command.isEmpty()) return CommandEnvelope.failed("usage: closecode exec <command...>", code = "usage")
        val line = command.joinToString(" ")
        if (destructive(line) && !approved) {
            return CommandEnvelope.failed(
                "This command looks destructive and needs approval.",
                code = "needs_approval",
                remedy = "Re-run the same command with --approve after checking it.",
            ).apply { addProperty("command", line) }
        }
        val log = File(projectDir, "build/logs/exec.log").apply { parentFile?.mkdirs() }
        val started = System.currentTimeMillis()
        val builder = ProcessBuilder(listOf("/system/bin/sh", "-c", line))
            .directory(cwd)
            .redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(ShellEnvironment.env(context, cwd).apply { putAll(settingsEnvironment) })
        val process = try {
            builder.start()
        } catch (error: Throwable) {
            return CommandEnvelope.failed("Could not start: ${error.message}", code = "exec_failed")
        }
        val writer = log.bufferedWriter()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { outputLine ->
                        synchronized(output) { if (output.length < 400_000) output.appendLine(outputLine) }
                        runCatching { writer.appendLine(outputLine); writer.flush() }
                        onEvent(JsonObject().apply {
                            addProperty("ok", true)
                            addProperty("event", "log")
                            addProperty("text", outputLine)
                        })
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!finished) ProcessTree.terminate(process)
        reader.join(5_000)
        runCatching { writer.close() }
        val code = runCatching { process.exitValue() }.getOrDefault(if (finished) -1 else 124)
        val duration = System.currentTimeMillis() - started
        val result = CommandEnvelope.of(
            ok = finished && code == 0,
            errors = if (finished && code == 0) emptyList() else listOf(
                if (!finished) "Command timed out." else "Command exited with $code.",
            ),
            exitCode = code,
            durationMs = duration,
            logPath = log.absolutePath,
        ).apply {
            addProperty("command", line)
            addProperty("cwd", cwd.absolutePath)
            if (!finished) addProperty("code", "timeout")
            else if (code != 0) addProperty("code", "exec_failed")
        }
        AuditLog(context).record("exec", null, arguments, result.ok(), result.get("error")?.asString)
        return result
    }

    fun screen(context: Context, arguments: List<String>): JsonObject =
        ScreenControl.run(context, arguments)

    fun notifications(context: Context, arguments: List<String>): JsonObject {
        val result = com.closecode.device.NotificationControl.run(context, arguments)
        return result
    }

    fun consent(context: Context, arguments: List<String>): JsonObject {
        val store = ConsentStore(context)
        val action = arguments.firstOrNull()?.lowercase(Locale.US) ?: "status"
        return when (action) {
            "status", "list" -> CommandEnvelope.of(ok = true).apply {
                add("grants", JsonObject().apply {
                    store.grants().forEach { (name, granted) -> addProperty(name, granted) }
                })
                add("scopes", JsonObject().apply {
                    store.scopedGrants().forEach { (name, scopes) ->
                        add(name, JsonArray().apply { scopes.sorted().forEach { add(it) } })
                    }
                })
            }
            "grant", "allow" -> {
                val capability = arguments.getOrNull(1)
                    ?: return CommandEnvelope.failed("usage: closecode consent grant <capability> [--package PKG]", code = "usage")
                val scope = option(arguments, "--package") ?: option(arguments, "--app")
                val done = if (scope != null) store.grantFor(capability, scope) else store.grant(capability)
                CommandEnvelope.of(ok = done).apply {
                    addProperty("capability", capacityName(capability))
                    if (scope != null) addProperty("package", scope)
                    if (!done) {
                        addProperty("error", "Unknown capability.")
                        addProperty("code", "unknown_capability")
                        add("capabilities", JsonArray().apply {
                            ConsentStore.CAPABILITIES.forEach { add(it) }
                        })
                    }
                }
            }
            "revoke", "deny" -> {
                val capability = arguments.getOrNull(1)
                    ?: return CommandEnvelope.failed("usage: closecode consent revoke <capability> [--package PKG]", code = "usage")
                val scope = option(arguments, "--package") ?: option(arguments, "--app")
                val done = if (scope != null) store.revokeFor(capability, scope) else store.revoke(capability)
                CommandEnvelope.of(ok = done).apply {
                    addProperty("capability", capacityName(capability))
                    if (scope != null) addProperty("package", scope)
                }
            }
            else -> CommandEnvelope.failed("usage: closecode consent status|grant|revoke [--package PKG]", code = "usage")
        }
    }

    private fun capacityName(raw: String): String = ConsentStore.normalize(raw) ?: raw.lowercase()

    fun audit(context: Context, arguments: List<String>): JsonObject {
        val action = arguments.firstOrNull()?.lowercase(Locale.US) ?: "tail"
        if (action != "tail" && action != "list") {
            return CommandEnvelope.failed("usage: closecode audit tail [lines]", code = "usage")
        }
        val lines = arguments.getOrNull(1)?.toIntOrNull() ?: 50
        return CommandEnvelope.of(ok = true).apply {
            add("entries", AuditLog(context).tail(lines))
        }
    }

    fun stopAll(context: Context): JsonObject {
        val stopped = JobManager.stopAll(context)
        val build = com.closecode.build.BuildCoordinator.cancel()
        runCatching { AutomationStatus.end() }
        return CommandEnvelope.of(ok = true).apply {
            addProperty("buildCancelled", build)
            add("stoppedJobs", stopped)
        }
    }


    private fun destructive(command: String): Boolean {
        val text = " $command "
        return listOf(
            " rm -rf /", " rm -rf ~", " rm -rf \$HOME", " mkfs", " dd if=",
            " reboot", " shutdown", " pm uninstall", ":(){", " > /dev/block",
        ).any { text.contains(it) }
    }

    private fun resolve(root: File, raw: String?): File? {
        if (raw.isNullOrBlank()) return root
        val candidate = if (File(raw).isAbsolute) File(raw) else File(root, raw)
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        val rootPath = runCatching { root.canonicalPath }.getOrDefault(root.path)
        return canonical.takeIf {
            it.path == rootPath || it.path.startsWith(rootPath + File.separator)
        }
    }

    private fun option(arguments: List<String>, name: String): String? {
        val index = arguments.indexOf(name)
        return if (index >= 0) arguments.getOrNull(index + 1) else null
    }

    private fun JsonObject.ok(): Boolean = runCatching { get("ok")?.asBoolean == true }.getOrDefault(false)
}
