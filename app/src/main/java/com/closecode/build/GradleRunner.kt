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
    /** What the project's wrapper asks for, even when the installed Gradle is the fallback. */
    val wrapperVersion: String? = null,
    val native: NativeToolchain? = null,
    /** The key used to post-sign a release APK that Gradle left unsigned. */
    val signer: SigningIdentity? = null,
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
    val durationMs: Long = 0L,
    val logPath: String? = null,
)

object GradlePlan {

    /**
     * The `gradle-launcher-*.jar` that holds `org.gradle.launcher.GradleMain`.
     *
     * Running the `bin/gradle` shell script on Android is the P0-1 defect: the script feeds its
     * JVM options through `xargs` to strip quotes, and toybox's xargs keeps them, so
     * `"-Xmx64m"` becomes the main class. Launching the same entry point directly removes the
     * dependency on xargs and on shell quoting. Null means the distribution has no launcher jar
     * (or a layout we do not know), and the caller falls back to the script.
     */
    fun launcherJar(toolchain: GradleToolchain): File? {
        val distribution = toolchain.gradle.parentFile?.parentFile ?: return null
        val lib = File(distribution, "lib")
        val candidates = lib.listFiles()
            ?.filter { it.isFile && it.name.startsWith("gradle-launcher-") && it.extension == "jar" }
            .orEmpty()
        if (candidates.isEmpty()) return null
        val exact = candidates.firstOrNull {
            it.name == "gradle-launcher-${toolchain.distributionVersion}.jar"
        }
        return exact ?: candidates.maxByOrNull { it.name }
    }

    fun build(
        toolchain: GradleToolchain,
        projectDir: File,
        buildType: String,
        task: String,
        initScript: File,
        options: GradleBuildOptions = GradleBuildOptions(),
    ): GradleInvocation {
        val launcher = launcherJar(toolchain)
        val command = if (launcher != null) {
            // Exactly the invocation verified by hand on the phone: no shell, no xargs, no
            // -javaagent line. The JVM options are separate argv entries, so nothing can
            // reinterpret a quote.
            mutableListOf(
                File(toolchain.javaHome, "bin/java").absolutePath,
                // The client JVM only parses arguments and forks the build; Gradle's own
                // launcher gives it 64 MB. The build heap is set separately through
                // -Dorg.gradle.jvmargs (sized to the phone's RAM).
                "-Xmx512m",
                "-Dfile.encoding=UTF-8",
                "-Dorg.gradle.appname=gradle",
                "-classpath",
                launcher.absolutePath,
                "org.gradle.launcher.GradleMain",
            )
        } else {
            val shell = if (File("/system/bin/sh").isFile) "/system/bin/sh" else "/bin/sh"
            mutableListOf(shell, toolchain.gradle.absolutePath)
        }
        command += mutableListOf(
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
            "-Dorg.gradle.jvmargs=${options.jvmArgs()}",
            "-Psdk.dir=${toolchain.sdkRoot.absolutePath}",
            "-Pandroid.suppressUnsupportedCompileSdk=35",
        )
        toolchain.aapt2?.let {
            command += "-Pandroid.aapt2FromMavenOverride=${it.absolutePath}"
        }
        command += options.gradleProperties()
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

    /** A javac-style error line: `path:line: error: message`. */
    private val COMPILER_ERROR = Regex(
        "^.+?\\.(?:kt|kts|java):\\d+(?::\\d+)?:\\s*(?:error|Error):\\s*(.+)$",
    )

    private const val MAX_OUTPUT_CHARS = 64_000
    private const val TIMEOUT_MS = 90 * 60 * 1000L

    fun run(
        projectDir: File,
        buildType: String,
        toolchain: GradleToolchain,
        onLog: (String) -> Unit = {},
        cancelled: () -> Boolean = { false },
        options: GradleBuildOptions = GradleBuildOptions.forProject(projectDir),
    ): GradleRunResult = execute(
        projectDir = projectDir,
        buildType = buildType,
        task = GradlePlan.taskFor(buildType),
        toolchain = toolchain,
        onLog = onLog,
        cancelled = cancelled,
        findApk = true,
        options = options,
    )

    /** Runs any Gradle task without expecting an APK: test, lint, check or a bundle. */
    fun runTask(
        projectDir: File,
        task: String,
        toolchain: GradleToolchain,
        onLog: (String) -> Unit = {},
        cancelled: () -> Boolean = { false },
        options: GradleBuildOptions = GradleBuildOptions.forProject(projectDir),
    ): GradleRunResult = execute(
        projectDir = projectDir,
        buildType = "debug",
        task = task,
        toolchain = toolchain,
        onLog = onLog,
        cancelled = cancelled,
        findApk = false,
        options = options,
    )

    private fun execute(
        projectDir: File,
        buildType: String,
        task: String,
        toolchain: GradleToolchain,
        onLog: (String) -> Unit,
        cancelled: () -> Boolean,
        findApk: Boolean,
        options: GradleBuildOptions,
    ): GradleRunResult {
        GradleUserProperties.ensure(toolchain.gradleHome, toolchain.aapt2, toolchain.javaHome)
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
            options,
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
                    ProcessTree.terminate(process)
                    break
                }
                if (System.currentTimeMillis() - started > TIMEOUT_MS) {
                    timedOut = true
                    ProcessTree.terminate(process)
                    break
                }
                Thread.sleep(500)
            }
            process.waitFor()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            wasCancelled = true
            ProcessTree.terminate(process)
        } finally {
            reader.join(5_000)
        }
        val durationMs = System.currentTimeMillis() - started
        val text = synchronized(output) { output.toString() }
        val code = runCatching { process.exitValue() }.getOrDefault(-1)
        val log = runCatching {
            val dir = File(projectDir, "build/logs")
            dir.mkdirs()
            File(dir, "build.log").apply {
                writeText(
                    buildString {
                        append("CloseCode Gradle build\n")
                        append("Java ")
                        append(javaVersion(toolchain.javaHome))
                        append(" (")
                        append(toolchain.javaHome.absolutePath)
                        append(")\n")
                        append("Gradle ")
                        append(toolchain.distributionVersion)
                        append("\n\n")
                        append(text)
                    },
                )
            }
        }.getOrNull()
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
            durationMs = durationMs,
            logPath = log?.absolutePath,
        )
    }

    fun failureMessage(result: GradleRunResult): String {
        if (result.cancelled) return "Build cancelled."
        if (result.timedOut) return "Gradle did not finish in time."
        val cause = rootCause(result.output)
        return if (cause == null) {
            "Gradle failed (exit ${result.code})."
        } else {
            "Gradle failed (exit ${result.code}): $cause"
        }
    }

    /**
     * The line Gradle itself calls the root cause.
     *
     * A compiler error is the real cause: `e: file:///work/Main.kt:9:5: Unresolved reference`
     * names the file, the line and the message. When the log has no compiler error, the cause
     * is the block between `* What went wrong:` and `* Try:` — the deepest `>` line is the
     * specific exception. Falls back to the last `Caused by:` line (P0-6).
     */
    fun rootCause(output: String): String? {
        compilerError(output)?.let { return it }
        val lines = causeBlock(output)
        if (lines.isNotEmpty()) return lines.last()
        return causedBy(output).lastOrNull()
    }

    /** The first compiler error in the log, without its `e:` / `error:` marker. */
    private fun compilerError(output: String): String? = output.lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.startsWith("e: ") || line.startsWith("error: ") || COMPILER_ERROR.containsMatchIn(line)
        }
        ?.removePrefix("e: ")
        ?.removePrefix("error: ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /** Every cause Gradle lists, deepest last, ready for the structured diagnostics array. */
    fun causedBy(output: String): List<String> {
        val block = causeBlock(output)
        val explicit = output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("Caused by:") }
            .map { it.removePrefix("Caused by:").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (explicit.isNotEmpty()) return explicit
        return block.toList()
    }

    private fun causeBlock(output: String): List<String> {
        val marker = "* What went wrong:"
        val start = output.indexOf(marker)
        if (start < 0) return emptyList()
        var block = output.substring(start + marker.length)
        val end = listOf("* Try:", "BUILD FAILED", "* Exception is:")
            .map { block.indexOf(it) }
            .filter { it > 0 }
            .minOrNull()
        if (end != null) block = block.substring(0, end)
        return block.lineSequence()
            .map { it.trim().removePrefix(">").trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}

/**
 * The version of a JDK, read from the `release` file it ships.
 *
 * The build log names the Java that ran the build, so "which JDK was this?" is answered by
 * the log instead of by guessing from the path.
 */
private fun javaVersion(javaHome: File): String {
    val release = File(javaHome, "release")
    if (!release.isFile) return "unknown"
    val text = runCatching { release.readText() }.getOrNull() ?: return "unknown"
    val match = Regex("JAVA_VERSION=\"([^\"]+)\"").find(text) ?: return "unknown"
    return match.groupValues.getOrNull(1) ?: "unknown"
}
