package com.closecode.build

import com.closecode.debug.DebugLog
import com.closecode.tools.NativeToolchain
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GradleToolchain(
    val javaHome: File,
    val gradle: File,
    val gradleHome: File,
    val sdkRoot: File,
    val platformDir: File,
    val buildToolsDir: File,
    val buildToolsVersion: String,
    val aapt2: File?,
    val aapt2Env: Map<String, String>,
    val distributionVersion: String,
    val native: NativeToolchain? = null,
    /** Set when the project has not pinned an NDK version and the installed one should be used. */
    val ndkVersionOverride: String? = null,
)

data class GradleInvocation(
    val command: List<String>,
    val environment: Map<String, String>,
    val workingDir: File,
)

data class GradleRunResult(
    val apk: File?,
    val output: String,
    val code: Int,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
)

object GradlePlan {

    fun build(
        toolchain: GradleToolchain,
        projectDir: File,
        buildType: String,
        task: String,
        initScript: File,
    ): GradleInvocation {
        val shell = if (File("/system/bin/sh").isFile) "/system/bin/sh" else "/bin/sh"
        val command = mutableListOf(
            shell,
            toolchain.gradle.absolutePath,
            "--project-dir",
            projectDir.absolutePath,
            "--gradle-user-home",
            toolchain.gradleHome.absolutePath,
            "--init-script",
            initScript.absolutePath,
            "--console=plain",
            "--no-daemon",
            "--stacktrace",
            "-Dorg.gradle.java.home=${toolchain.javaHome.absolutePath}",
            "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8",
            "-Psdk.dir=${toolchain.sdkRoot.absolutePath}",
            "-Pandroid.suppressUnsupportedCompileSdk=35",
        )
        toolchain.aapt2?.let {
            command += "-Pandroid.aapt2FromMavenOverride=${it.absolutePath}"
        }
        command += task
        val path = buildList {
            toolchain.native?.let { add(it.bin.absolutePath) }
            add(File(toolchain.javaHome, "bin").absolutePath)
            add("/system/bin")
            add("/system/xbin")
            add("/usr/bin")
            add("/bin")
        }.joinToString(":")
        val environment = linkedMapOf(
            "JAVA_HOME" to toolchain.javaHome.absolutePath,
            "PATH" to path,
            "ANDROID_HOME" to toolchain.sdkRoot.absolutePath,
            "ANDROID_SDK_ROOT" to toolchain.sdkRoot.absolutePath,
            "GRADLE_USER_HOME" to toolchain.gradleHome.absolutePath,
            "HOME" to toolchain.gradleHome.absolutePath,
            "TMPDIR" to File(toolchain.gradleHome, "tmp").absolutePath,
            "LANG" to "en_US.UTF-8",
            "LC_ALL" to "en_US.UTF-8",
        )
        toolchain.native?.let { native ->
            environment["ANDROID_NDK_HOME"] = native.ndkHome.absolutePath
            environment["ANDROID_NDK_ROOT"] = native.ndkHome.absolutePath
            environment["NDK_HOME"] = native.ndkHome.absolutePath
            environment["CMAKE"] = native.cmake.absolutePath
        }
        val termuxLib = toolchain.javaHome.parentFile?.parentFile
            ?.takeIf { it.name == "lib" }
        val javaLibs = listOfNotNull(
            File(toolchain.javaHome, "lib"),
            File(toolchain.javaHome, "lib/jli"),
            termuxLib,
        ).filter { it.isDirectory }.map { it.absolutePath }
        val javaLib = javaLibs.ifEmpty {
            listOf(File(toolchain.javaHome, "lib").absolutePath)
        }.joinToString(":")
        val extraLd = toolchain.aapt2Env["LD_LIBRARY_PATH"]
        environment["LD_LIBRARY_PATH"] = if (extraLd.isNullOrBlank()) {
            javaLib
        } else {
            "$javaLib:$extraLd"
        }
        toolchain.aapt2Env.forEach { (key, value) ->
            if (key != "LD_LIBRARY_PATH") environment[key] = value
        }
        return GradleInvocation(command, environment, projectDir)
    }

    fun initScript(buildToolsVersion: String, ndkVersion: String? = null): String {
        val setNdk = ndkVersion?.let { "android.ndkVersion = \"$it\"" }.orEmpty()
        return """
        allprojects {
            plugins.withId("com.android.application") {
                def android = project.extensions.findByName("android")
                if (android != null) {
                    android.buildToolsVersion = "$buildToolsVersion"
                    $setNdk
                }
            }
            plugins.withId("com.android.library") {
                def android = project.extensions.findByName("android")
                if (android != null) {
                    android.buildToolsVersion = "$buildToolsVersion"
                    $setNdk
                }
            }
        }
        """.trimIndent() + "\n"
    }

    fun taskFor(buildType: String): String =
        if (buildType == "release") "assembleRelease" else "assembleDebug"

    fun findApk(projectDir: File, buildType: String): File? {
        val type = if (buildType == "release") "release" else "debug"
        val root = File(projectDir, "app/build/outputs/apk/$type")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .maxByOrNull { it.lastModified() }
    }

    fun findAnyApk(projectDir: File): File? =
        File(projectDir, "app/build/outputs/apk")
            .walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .maxByOrNull { it.lastModified() }

    fun findBundle(projectDir: File): File? =
        File(projectDir, "app/build/outputs/bundle")
            .walkTopDown()
            .filter { it.isFile && it.extension == "aab" }
            .maxByOrNull { it.lastModified() }
}

object GradleRunner {

    private const val MAX_OUTPUT_CHARS = 64_000
    private const val TIMEOUT_MS = 30 * 60 * 1000L

    fun run(
        projectDir: File,
        buildType: String,
        toolchain: GradleToolchain,
        onLog: (String) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): GradleRunResult = execute(
        projectDir = projectDir,
        buildType = buildType,
        task = GradlePlan.taskFor(buildType),
        toolchain = toolchain,
        onLog = onLog,
        cancelled = cancelled,
        findApk = true,
    )

    /** Runs any Gradle task without expecting an APK: test, lint, check or a bundle. */
    fun runTask(
        projectDir: File,
        task: String,
        toolchain: GradleToolchain,
        onLog: (String) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): GradleRunResult = execute(
        projectDir = projectDir,
        buildType = "debug",
        task = task,
        toolchain = toolchain,
        onLog = onLog,
        cancelled = cancelled,
        findApk = false,
    )

    private fun execute(
        projectDir: File,
        buildType: String,
        task: String,
        toolchain: GradleToolchain,
        onLog: (String) -> Unit,
        cancelled: () -> Boolean,
        findApk: Boolean,
    ): GradleRunResult {
        val initScript = File(toolchain.gradleHome, "init/closecode.gradle")
        initScript.parentFile?.mkdirs()
        initScript.writeText(
            GradlePlan.initScript(toolchain.buildToolsVersion, toolchain.ndkVersionOverride),
        )
        File(toolchain.gradleHome, "tmp").mkdirs()
        val invocation = GradlePlan.build(
            toolchain,
            projectDir,
            buildType,
            task,
            initScript,
        )
        val output = StringBuilder()
        val builder = ProcessBuilder(invocation.command)
            .directory(invocation.workingDir)
            .redirectErrorStream(true)
        builder.environment().putAll(invocation.environment)
        DebugLog.crumb("BUILD", "gradle ${toolchain.distributionVersion} $task")
        val process = try {
            builder.start()
        } catch (error: IOException) {
            return GradleRunResult(null, "Could not start Gradle: ${error.message}", 1)
        }
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            output.appendLine(line)
                            if (output.length > MAX_OUTPUT_CHARS) {
                                output.delete(0, output.length - MAX_OUTPUT_CHARS)
                            }
                        }
                        runCatching { onLog(line) }
                    }
                }
            }
        }.apply {
            name = "gradle-output"
            isDaemon = true
            start()
        }
        val started = System.currentTimeMillis()
        var timedOut = false
        var wasCancelled = false
        try {
            while (process.isAlive) {
                if (cancelled()) {
                    wasCancelled = true
                    process.destroy()
                    if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
                    break
                }
                if (System.currentTimeMillis() - started > TIMEOUT_MS) {
                    timedOut = true
                    process.destroyForcibly()
                    break
                }
                Thread.sleep(500)
            }
            process.waitFor()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            wasCancelled = true
            process.destroyForcibly()
        } finally {
            reader.join(5_000)
        }
        val text = synchronized(output) { output.toString() }
        val code = runCatching { process.exitValue() }.getOrDefault(-1)
        return GradleRunResult(
            apk = if (findApk && code == 0 && !timedOut && !wasCancelled) {
                GradlePlan.findApk(projectDir, buildType)
            } else {
                null
            },
            output = text,
            code = code,
            timedOut = timedOut,
            cancelled = wasCancelled,
        )
    }

    fun failureMessage(result: GradleRunResult): String {
        if (result.cancelled) return "Build cancelled."
        if (result.timedOut) return "Gradle did not finish in time."
        val tail = result.output.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeLast(6)
            .joinToString(" ")
            .take(500)
        return if (tail.isEmpty()) {
            "Gradle failed (exit ${result.code})."
        } else {
            "Gradle failed (exit ${result.code}): $tail"
        }
    }
}
