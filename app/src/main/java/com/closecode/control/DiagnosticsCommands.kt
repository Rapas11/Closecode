package com.closecode.control

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.closecode.build.BuildCoordinator
import com.closecode.build.BuildSetup
import com.closecode.build.GradleReader
import com.closecode.build.PlatformStore
import com.closecode.build.SdkPlatforms
import com.closecode.build.ToolchainDownloadService
import com.closecode.device.CloseCodeAccessibilityService
import com.closecode.tools.GradleToolsStore
import com.closecode.tools.NativeToolsStore
import com.closecode.tools.ShellEnvironment
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared shape for `artifact --version` probes used by doctor and toolchain status. */
internal object ToolProbe {
    fun run(executable: File, arguments: List<String>, environment: Map<String, String>): String {
        if (!executable.isFile) return ""
        return runCatching {
            val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(20, TimeUnit.SECONDS)) process.destroyForcibly()
            text.trim().lines().firstOrNull().orEmpty()
        }.getOrElse { it.message ?: it.javaClass.simpleName }
    }
}

/** `closecode toolchain status --json`: the real on-disk layout and what is missing. */
object ToolchainStatusCommand {

    fun run(context: Context): JsonObject {
        val env = ShellEnvironment.env(context, File(context.filesDir, "tmp"))
        val paths = ShellEnvironment.paths(context)
        val gradle = GradleToolsStore(context)
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        val native = NativeToolsStore(context)
        val platforms = JsonArray()
        for (item in SdkPlatforms) {
            val jar = platform.androidJar(item.api)
            platforms.add(JsonObject().apply {
                addProperty("api", item.api)
                addProperty("path", platform.platformDir(item.api).absolutePath)
                addProperty("ok", jar != null)
            })
        }
        val buildTools = JsonArray()
        val buildToolsRoot = File(platform.rootDir(), "build-tools")
        buildToolsRoot.listFiles()?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEach { dir ->
                buildTools.add(JsonObject().apply {
                    addProperty("version", dir.name)
                    addProperty("path", dir.absolutePath)
                    addProperty("shim", File(dir, ".closecode-shim").isFile)
                    addProperty("ok", File(dir, "aapt2").isFile)
                })
            }
        val missing = JsonArray()
        if (!File(gradle.javaHome, "bin/java").isFile) missing.add("jdk")
        if (!gradle.isReady()) missing.add("gradle")
        if (platforms.none { it.asJsonObject.get("ok")?.asBoolean == true }) missing.add("platform")
        if (buildTools.none { it.asJsonObject.get("ok")?.asBoolean == true }) missing.add("build-tools")
        if (!platform.platformToolsReady()) missing.add("platform-tools")
        if (!platform.licensesReady()) missing.add("licenses")
        // P1-3: NDK, CMake and the shell pack are optional. A Kotlin-only app builds fine
        // without them, so their absence must not turn `ok` false.
        val optionalMissing = JsonArray()
        if (!native.ndkReady()) optionalMissing.add("ndk")
        if (!native.cmakeReady()) optionalMissing.add("cmake")
        if (!ShellToolsStore(context, ShellToolsManifest.load(context)).isReady()) {
            optionalMissing.add("shell-tools")
        }
        return JsonObject().apply {
            addProperty("ok", missing.size() == 0)
            addProperty("prefix", paths.shellTools.absolutePath)
            add("jdk", JsonObject().apply {
                addProperty("path", gradle.javaHome.absolutePath)
                addProperty("version", ToolProbe.run(gradle.java, listOf("-version"), env))
                addProperty("ok", File(gradle.javaHome, "bin/java").isFile)
            })
            add("gradle", JsonObject().apply {
                addProperty("home", gradle.distributionDir.absolutePath)
                addProperty("distribution", gradle.manifest.distribution.version)
                add("installed", JsonArray().apply {
                    gradle.installedVersions().sorted().forEach { add(it) }
                })
                add("versions", JsonArray().apply {
                    gradle.manifest.distributions.keys.sorted().forEach { add(it) }
                })
                addProperty("ok", gradle.isReady())
            })
            addProperty("gradleUserHome", gradle.gradleHome.absolutePath)
            addProperty("sdkRoot", platform.rootDir().absolutePath)
            add("platforms", platforms)
            add("buildTools", buildTools)
            add("native", JsonObject().apply {
                addProperty("ndk", native.ndkHome.takeIf { native.ndkReady() }?.absolutePath)
                addProperty("cmake", native.cmakeHome.takeIf { native.cmakeReady() }?.absolutePath)
            })
            add("missing", missing)
            add("optionalMissing", optionalMissing)
            add("sizes", JsonObject().apply {
                addProperty("gradleCacheBytes", File(gradle.gradleHome, "caches")
                    .walkTopDown().filter { it.isFile }.sumOf { it.length() })
                addProperty("sdkBytes", platform.sizeBytes())
            })
        }
    }
}

/** `closecode doctor --json`: one place the agent can ask what is wrong and how to fix it. */
object DoctorCommand {

    fun run(context: Context, project: BuildSetup.Project?): JsonObject {
        val paths = ShellEnvironment.paths(context)
        val env = ShellEnvironment.env(context, File(context.filesDir, "tmp"))
        val gradle = GradleToolsStore(context)
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        val native = NativeToolsStore(context)
        val shell = ShellToolsStore(context, ShellToolsManifest.load(context))
        val checks = JsonArray()
        val prefix = paths.shellTools
        fun addCheck(
            name: String,
            ok: Boolean,
            path: File? = null,
            version: String = "",
            remedy: String = "",
            install: String = "",
            required: Boolean = true,
        ) {
            checks.add(JsonObject().apply {
                addProperty("name", name)
                addProperty("ok", ok)
                if (!required) addProperty("optional", true)
                addProperty("path", path?.absolutePath)
                addProperty("version", version)
                addProperty("remedy", remedy)
                addProperty("installCommand", install)
            })
        }
        addCheck(
            "shell.prefix",
            File(prefix, "bin/sh").exists(),
            prefix,
            remedy = "Install the shell pack.",
            install = "closecode setup --web",
            required = false,
        )
        addCheck(
            "shell.path",
            env["PATH"].orEmpty().contains(File(prefix, "bin").absolutePath),
            version = env["PATH"].orEmpty(),
            required = false,
        )
        addCheck(
            "shell.ld_library_path",
            env["LD_LIBRARY_PATH"].orEmpty().contains(File(prefix, "lib").absolutePath),
            version = env["LD_LIBRARY_PATH"].orEmpty(),
            required = false,
        )
        val javaHome = ShellEnvironment.javaHome(paths)
        addCheck("jdk", javaHome != null, javaHome, install = "closecode setup --gradle")
        addCheck(
            "gradle",
            gradle.isReady(),
            gradle.gradle,
            gradle.manifest.distribution.version,
            remedy = "Install JDK 17 and Gradle 8.13.",
            install = "closecode setup --gradle",
        )
        addCheck(
            "sdk.platform.35",
            platform.androidJar(35) != null,
            platform.platformDir(35),
            install = "closecode setup --gradle --sdk 35",
        )
        val required = platform.requiredBuildToolsVersion(readAgp(project))
        val buildTools = platform.bestBuildTools(required)
        addCheck(
            "sdk.build_tools",
            buildTools != null,
            buildTools?.second,
            buildTools?.first.orEmpty(),
            remedy = "Install build-tools $required or newer.",
            install = "closecode setup --gradle",
        )
        // P0-3 / P0-4: AGP checks both before it runs a single task.
        addCheck(
            "sdk.platform_tools",
            platform.platformToolsReady(),
            platform.platformToolsDir(),
            PlatformStore.PLATFORM_TOOLS_VERSION,
            remedy = "AGP refuses to build without the platform-tools package.",
            install = "closecode setup --gradle",
        )
        addCheck(
            "sdk.licenses",
            platform.licensesReady(),
            File(platform.rootDir(), "licenses"),
            "${PlatformStore.LicenseFiles.size} licence files",
            remedy = "Accept the Android SDK licence in Settings > SDK Manager.",
            install = "closecode setup --gradle",
        )
        addCheck(
            "native.ndk",
            native.ndkReady(),
            native.ndkHome,
            install = "closecode setup --native",
            required = false,
        )
        addCheck(
            "native.cmake",
            native.cmakeReady(),
            native.cmakeHome,
            install = "closecode setup --native",
            required = false,
        )
        addMeasure("node", File(prefix, "bin/node"), listOf("-v"), env, checks, "closecode setup --web")
        addMeasure("npm", File(prefix, "bin/npm"), listOf("-v"), env, checks, "closecode setup --web")
        addMeasure("python", File(prefix, "bin/python3"), listOf("-V"), env, checks, "closecode setup --python")
        addMeasure("pip", File(prefix, "bin/pip"), listOf("--version"), env, checks, "closecode setup --python")
        addMeasure("git", File(prefix, "bin/git"), listOf("--version"), env, checks, "closecode setup --web")
        addMeasure("curl", File(prefix, "bin/curl"), listOf("--version"), env, checks, "closecode setup --web")
        addMeasure("zip", File(prefix, "bin/zip"), listOf("-v"), env, checks, "closecode setup --web")
        addMeasure("jq", File(prefix, "bin/jq"), listOf("--version"), env, checks, "closecode setup --web")
        // The rest are user-environment states, not install gaps: a build does not need them.
        addCheck(
            "accessibility",
            CloseCodeAccessibilityService.connected(),
            remedy = "Run closecode accessibility enable and tap the switch.",
            required = false,
        )
        addCheck(
            "notifications",
            ToolchainDownloadService.notificationsAllowed(context),
            remedy = "Allow notifications for CloseCode.",
            required = false,
        )
        val power = context.getSystemService(PowerManager::class.java)
        val battery = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        addCheck(
            "battery",
            battery,
            remedy = "Allow CloseCode to run without battery optimisation.",
            required = false,
        )
        val free = context.filesDir.usableSpace
        addCheck(
            "disk",
            free >= 3L * 1024 * 1024 * 1024,
            version = free.toString(),
            remedy = "Free at least 3 GB for Gradle, the SDK and dependency caches.",
            required = false,
        )
        addCheck("network", networkAvailable(context), remedy = "Connect to Wi-Fi or allow mobile data.", required = false)
        if (project != null) {
            val facts = GradleReader.read(project.directory)
            addCheck(
                "project.package",
                (project.savedPackage ?: facts.packageName) != null,
                project.directory,
                project.savedPackage ?: facts.packageName.orEmpty(),
                remedy = "Set namespace in app/build.gradle.kts or use a template.",
            )
            val last = BuildCoordinator.last()?.takeIf { it.id == project.id }
            addCheck(
                "project.last_build",
                last != null,
                project.directory,
                last?.summary.orEmpty(),
                remedy = "Run closecode build --gradle.",
                required = false,
            )
        }
        // Only the checks the build needs decide `ok`; the optional ones are listed apart.
        val requiredChecks = checks.filter { it.asJsonObject.get("optional") == null }
        val optionalChecks = checks.filter { it.asJsonObject.get("optional") != null }
        val ok = DiagnosticRules.overallOk(checks.map { it.asJsonObject })
        return JsonObject().apply {
            addProperty("ok", ok)
            addProperty("build", com.closecode.BuildConfig.VERSION_CODE)
            add("env", JsonObject().apply {
                env.toSortedMap().forEach { (key, value) -> addProperty(key, value) }
            })
            add("checks", checks)
            add("optionalChecks", JsonArray().apply { optionalChecks.forEach { add(it) } })
            add("toolchain", ToolchainStatusCommand.run(context))
        }
    }

    private fun addMeasure(
        name: String,
        executable: File,
        arguments: List<String>,
        environment: Map<String, String>,
        checks: JsonArray,
        install: String,
    ) {
        val version = ToolProbe.run(executable, arguments, environment)
        checks.add(JsonObject().apply {
            addProperty("name", name)
            // The shell pack is optional: a Kotlin app builds without it.
            addProperty("optional", true)
            addProperty("ok", executable.isFile && version.isNotBlank() &&
                !version.contains("not found", ignoreCase = true) &&
                !version.contains("CANNOT LINK", ignoreCase = true))
            addProperty("path", executable.absolutePath)
            addProperty("version", version.take(300))
            addProperty("remedy", if (executable.isFile) "" else "Install the shell pack.")
            addProperty("installCommand", install)
        })
    }

    private fun readAgp(project: BuildSetup.Project?): String? =
        project?.let {
            GradleReader.read(it.directory).plugins
                .firstOrNull { plugin ->
                    plugin.id == "com.android.application" || plugin.id == "com.android.library"
                }
                ?.version
        }

    private fun networkAvailable(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 23) {
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        @Suppress("DEPRECATION")
        return manager.activeNetworkInfo?.isConnected == true
    }
}
