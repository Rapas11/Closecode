package com.closecode.build

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The real nine-step on-device build:
 *
 *   project files → check → libraries → resources → code → dex → pack → sign → output
 *
 * Every step is real local work with the downloaded tools. A failing step stops the
 * chain and returns the diagnostics it produced; the previously built APK is only
 * replaced after a new one is signed and verified, so a failed build can never leave
 * a half-written file behind the Open app button.
 *
 * The chain is plain JVM code: the phone calls it with the pack on disk, and the JVM
 * proof test calls it with the same originals and a desktop aapt2. Nothing here touches
 * Android APIs, so both runs exercise the same lines.
 */
object BuildChain {

    data class StepTime(val step: Step, val millis: Long)

    data class Failure(
        val step: Step,
        val message: String,
        val diagnostics: List<CompileDriver.Diagnostic> = emptyList(),
        val cancelled: Boolean = false,
    )

    data class Result(
        val apk: File?,
        val times: List<StepTime>,
        val diagnostics: List<CompileDriver.Diagnostic>,
        val failure: Failure?,
        val log: String,
    ) {
        val ok: Boolean get() = apk != null && failure == null
    }

    private class Stop(
        val step: Step,
        message: String,
        val diagnostics: List<CompileDriver.Diagnostic> = emptyList(),
        val cancelled: Boolean = false,
    ) : Exception(message)

    fun build(
        projectDir: File,
        config: BuildConfigValues,
        tools: BuildTools,
        onStep: (Step) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): Result {
        val run = Run(projectDir, config, tools, onStep, cancelled)
        return run.execute()
    }

    private class Run(
        val projectDir: File,
        val config: BuildConfigValues,
        val tools: BuildTools,
        val onStep: (Step) -> Unit,
        val cancelled: () -> Boolean,
    ) {
        private val buildDir = File(projectDir, "build")
        private val resFlatDir = File(buildDir, "res_flat")
        private val genDir = File(buildDir, "gen")
        private val classesDir = File(buildDir, "classes")
        private val dexDir = File(buildDir, "dex")
        private val apkDir = File(buildDir, "apk")
        private val logsDir = File(buildDir, "logs")
        private val times = mutableListOf<StepTime>()
        private val diagnostics = mutableListOf<CompileDriver.Diagnostic>()
        private val log = StringBuilder()

        fun execute(): Result {
            try {
                prepare()
                timed(Step.CheckProject) { checkProject() }
                val libraries = timed(Step.FindLibraries) { findLibraries() }
                val preparedManifest = timed(Step.CompileResources) { resources(libraries) }
                timed(Step.CompileCode) { code(libraries) }
                timed(Step.MakeDex) { dex() }
                val unsigned = timed(Step.PackApp) { pack(preparedManifest) }
                val apk = timed(Step.Sign) { sign(unsigned) }
                return Result(apk, times.toList(), diagnostics.toList(), null, log.toString())
            } catch (stop: Stop) {
                diagnostics += stop.diagnostics
                log.appendLine("FAILED at ${stop.step.label}: ${stop.message}")
                writeLog()
                return Result(
                    apk = null,
                    times = times.toList(),
                    diagnostics = diagnostics.distinct(),
                    failure = Failure(
                        stop.step,
                        stop.message ?: "The build stopped.",
                        stop.diagnostics,
                        stop.cancelled,
                    ),
                    log = log.toString(),
                )
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                log.appendLine("FAILED at ${times.lastOrNull()?.step?.label ?: "build"}: $message")
                writeLog()
                return Result(
                    apk = null,
                    times = times.toList(),
                    diagnostics = diagnostics.toList(),
                    failure = Failure(times.lastOrNull()?.step ?: Step.CheckProject, message),
                    log = log.toString(),
                )
            }
        }

        private fun <T> timed(step: Step, block: () -> T): T {
            if (cancelled()) throw Stop(step, "Build cancelled.", cancelled = true)
            onStep(step)
            log.appendLine("== ${step.label}")
            val start = System.currentTimeMillis()
            try {
                return block()
            } finally {
                times += StepTime(step, System.currentTimeMillis() - start)
            }
        }

        private fun prepare() {
            resFlatDir.deleteRecursively()
            genDir.deleteRecursively()
            classesDir.deleteRecursively()
            dexDir.deleteRecursively()
            for (dir in listOf(resFlatDir, genDir, classesDir, dexDir, apkDir, logsDir)) dir.mkdirs()
        }

        /** A last, cheap guard: the engine runs the full project check before this. */
        private fun checkProject() {
            val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
            if (!manifest.isFile) throw Stop(Step.CheckProject, "Missing app/src/main/AndroidManifest.xml.")
            if (!File(projectDir, "app/build.gradle.kts").isFile) {
                throw Stop(Step.CheckProject, "Missing app/build.gradle.kts.")
            }
            val sources = File(projectDir, "app/src/main")
                .walkTopDown()
                .any { it.isFile && (it.extension == "kt" || it.extension == "java") }
            if (!sources) throw Stop(Step.CheckProject, "No sources. Add at least one .kt file.")
        }

        /**
         * Resolve the declarations against the cache. The cache is the supported set;
         * anything the project asks for that it does not hold is already an error in the
         * project check. Every cached module is linked, because the cache is exactly the
         * transitive closure of what it supports.
         */
        private fun findLibraries(): List<CachedLibrary> {
            val libraries = tools.libraries.libraries
            if (libraries.isEmpty() || tools.libraries.dexFiles.isEmpty()) {
                throw Stop(
                    Step.FindLibraries,
                    "The library cache is missing. Open Build tools and download the tools again.",
                )
            }
            log.appendLine("libraries: ${libraries.size} modules, ${tools.libraries.dexFiles.size} dex files")
            return libraries
        }

        // --- resources -------------------------------------------------------------

        private fun resources(libraries: List<CachedLibrary>): File {
            val manifest = File(apkDir, "AndroidManifest.xml")
            manifest.writeText(preparedManifest())
            val projectRes = File(projectDir, "app/src/main/res")
            if (projectRes.isDirectory) {
                compileRes(projectRes, File(resFlatDir, "project"))
            } else {
                log.appendLine("no res folder; linking without project resources")
            }
            libraries.forEachIndexed { index, library ->
                val res = library.res ?: return@forEachIndexed
                compileRes(res, File(resFlatDir, "lib-${index.toString().padStart(3, '0')}"))
            }
            val flat = resFlatDir.walkTopDown().filter { it.isFile && it.extension == "flat" }
                .sortedBy { it.path }.toList()
            val output = File(apkDir, "resources.ap_")
            output.delete()
            val args = buildList {
                add("link")
                add("-o"); add(output.absolutePath)
                add("-I"); add(tools.androidJar.absolutePath)
                add("--manifest"); add(manifest.absolutePath)
                add("--java"); add(genDir.absolutePath)
                add("--auto-add-overlay")
                add("--min-sdk-version"); add(config.minSdk.toString())
                add("--target-sdk-version"); add(config.targetSdk.toString())
                add("--version-code"); add(config.versionCode.toString())
                add("--version-name"); add(config.versionName)
                // Relative names keep the command line far under Windows' 32K limit and
                // Android's argv limit: the pool is hundreds of flat files, and their
                // absolute paths alone would overflow the argument list.
                flat.forEach { add(it.relativeTo(resFlatDir).path) }
            }
            val result = DriverExec.runNative(tools.aapt2, args, resFlatDir, timeoutMs = 180_000)
            log.append(result.output)
            if (result.timedOut) throw Stop(Step.CompileResources, "aapt2 did not finish in time.")
            if (result.code != 0 || !output.isFile) {
                val parsed = parseResourceErrors(result.output)
                diagnostics += parsed
                throw Stop(
                    Step.CompileResources,
                    parsed.firstOrNull()?.let { "${it.path}:${it.line}: ${it.message}" }
                        ?: "The resources did not compile. See the build log.",
                    parsed,
                )
            }
            return manifest
        }

        private fun compileRes(directory: File, output: File) {
            output.mkdirs()
            val args = listOf("compile", "--dir", directory.absolutePath, "-o", output.absolutePath)
            val result = DriverExec.runNative(tools.aapt2, args, projectDir, timeoutMs = 120_000)
            log.append(result.output)
            if (result.timedOut) throw Stop(Step.CompileResources, "aapt2 did not finish in time.")
            if (result.code != 0) {
                val parsed = parseResourceErrors(result.output)
                diagnostics += parsed
                throw Stop(
                    Step.CompileResources,
                    parsed.firstOrNull()?.let { "${it.path}:${it.line}: ${it.message}" }
                        ?: "The resources in ${directory.name} did not compile.",
                    parsed,
                )
            }
        }

        /**
         * The manifest aapt2 links: package and uses-sdk injected (templates omit both),
         * ${applicationId} replaced, and the package the build uses forced in place of
         * any stale one. Imported projects keep their own package; that package is what
         * the caller passed, never a rewrite of the project's files.
         */
        private fun preparedManifest(): String {
            val source = File(projectDir, "app/src/main/AndroidManifest.xml").readText()
            var text = source.replace("\${applicationId}", config.packageName)
            val packageAttribute = Regex("(<manifest\\b[^>]*\\bpackage\\s*=\\s*\")[^\"]*(\")")
            text = if (packageAttribute.containsMatchIn(text)) {
                packageAttribute.replace(text) { "${it.groupValues[1]}${config.packageName}${it.groupValues[2]}" }
            } else {
                Regex("<manifest\\b").replaceFirst(text, "<manifest package=\"${config.packageName}\"")
            }
            text = text.replace(Regex("\\s*<uses-sdk\\b[^>]*/>"), "")
            val usesSdk = "\n    <uses-sdk android:minSdkVersion=\"${config.minSdk}\" " +
                "android:targetSdkVersion=\"${config.targetSdk}\" />"
            text = Regex("(<manifest\\b[^>]*>)").replaceFirst(text, "$1$usesSdk")
            if (!text.contains("android:versionCode")) {
                // aapt2 --version-code/--version-name inject these; nothing else to do.
                log.appendLine("version ${config.versionCode} (${config.versionName}) from the project")
            }
            return text
        }

        private fun parseResourceErrors(output: String): List<CompileDriver.Diagnostic> =
            output.lineSequence().mapNotNull { line ->
                val match = Regex("^(.+?):(\\d+):\\s*(error|warning|warn):\\s*(.*)$")
                    .find(line.trim()) ?: return@mapNotNull null
                val path = normalizePath(match.groupValues[1])
                CompileDriver.Diagnostic(
                    path = path,
                    line = match.groupValues[2].toIntOrNull() ?: 1,
                    kind = if (match.groupValues[3] == "error") {
                        CompileDriver.Diagnostic.Kind.Error
                    } else {
                        CompileDriver.Diagnostic.Kind.Warning
                    },
                    message = match.groupValues[4].trim(),
                )
            }.toList()

        // --- code ------------------------------------------------------------------

        private fun code(libraries: List<CachedLibrary>) {
            val sourceRoot = File(projectDir, "app/src/main/java")
            val kotlin = sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
            val java = sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "java" }.sortedBy { it.path }.toList()
            val buildConfig = writeBuildConfig()
            val rJava = genDir.walkTopDown().filter { it.isFile && it.name == "R.java" }.toList()
            if (kotlin.isEmpty() && java.isEmpty()) {
                throw Stop(Step.CompileCode, "No sources to compile.")
            }
            val classpath = listOf(tools.androidJar, tools.stdlib) + libraries.mapNotNull { it.jar }
            val javaForKotlin = java + rJava + listOf(buildConfig)

            if (kotlin.isNotEmpty()) {
                val plugin = if (config.useCompose) tools.composePlugin else null
                val args = CompileDriver.kotlincArgs(
                    sources = kotlin + javaForKotlin,
                    classpath = classpath,
                    outputDir = classesDir,
                    composePluginJar = plugin,
                )
                val result = ToolExec.kotlinc(
                    ToolchainManifest.KOTLINC_LAUNCH_IDS.map { id ->
                        toolFor(id) ?: throw Stop(Step.CompileCode, "A build tool file is missing: $id.")
                    },
                    args,
                    dexOptDir = tools.dexOptDir,
                )
                log.append(result.output)
                val parsed = result.output.lineSequence().mapNotNull { CompileDriver.parseDiagnostic(it) }
                    .map { it.copy(path = normalizePath(it.path)) }
                    .toList()
                diagnostics += parsed
                val errors = parsed.filter { it.kind == CompileDriver.Diagnostic.Kind.Error }
                if (result.code != 0 || errors.isNotEmpty()) {
                    throw Stop(
                        Step.CompileCode,
                        errors.firstOrNull()?.let { "${it.path}:${it.line}: ${it.message}" }
                            ?: toolFailure("The Kotlin compiler did not finish", result),
                        errors,
                    )
                }
            }

            // ECJ compiles the Java sources plus the generated R and BuildConfig.
            val javaInputs = java + rJava + listOf(buildConfig)
            val ecj = toolFor("ecj") ?: throw Stop(Step.CompileCode, "The Java compiler is missing.")
            val ecjArgs = CompileDriver.ecjArgs(
                sources = javaInputs,
                classpath = listOf(tools.stdlib) + libraries.mapNotNull { it.jar } + classesDir,
                outputDir = classesDir,
                platformJar = tools.androidJar,
            )
            val ecjResult = ToolExec.ecj(listOf(ecj), ecjArgs, dexOptDir = tools.dexOptDir)
            log.append(ecjResult.output)
            val ecjParsed = ecjResult.output.lineSequence().mapNotNull { CompileDriver.parseDiagnostic(it) }
                .map { it.copy(path = normalizePath(it.path)) }.toList()
            diagnostics += ecjParsed
            val ecjErrors = ecjParsed.filter { it.kind == CompileDriver.Diagnostic.Kind.Error }
            if (ecjResult.code != 0 || ecjErrors.isNotEmpty()) {
                throw Stop(
                    Step.CompileCode,
                    ecjErrors.firstOrNull()?.let { "${it.path}:${it.line}: ${it.message}" }
                        ?: toolFailure("The Java compiler did not finish", ecjResult),
                    ecjErrors,
                )
            }
        }

        private fun writeBuildConfig(): File {
            val packagePath = config.packageName.replace('.', '/')
            val directory = File(genDir, packagePath).apply { mkdirs() }
            val file = File(directory, "BuildConfig.java")
            file.writeText(
                """
                package ${config.packageName};

                public final class BuildConfig {
                    public static final boolean DEBUG = true;
                    public static final String APPLICATION_ID = "${config.packageName}";
                    public static final String BUILD_TYPE = "debug";
                    public static final String VERSION_NAME = "${config.versionName}";
                    public static final int VERSION_CODE = ${config.versionCode};
                }
                """.trimIndent() + "\n",
            )
            return file
        }

        // --- dex -------------------------------------------------------------------

        private fun dex() {
            val classes = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }
                .sortedBy { it.path }.toList()
            if (classes.isEmpty()) throw Stop(Step.MakeDex, "No classes to turn into dex.")
            val r8 = toolFor("r8") ?: throw Stop(Step.MakeDex, "D8 is missing.")
            // Only the project's own classes are dexed. The libraries (and the Kotlin
            // stdlib) were dexed once by the pack script and ride in as classes2.dex
            // onward; they are here as --lib so D8 can resolve the types the project uses.
            val args = CompileDriver.d8Args(
                classes = classes,
                libs = listOf(tools.androidJar, tools.stdlib),
                outputDir = dexDir,
                minApi = config.minSdk,
            )
            val result = ToolExec.d8(listOf(r8), args, dexOptDir = tools.dexOptDir)
            log.append(result.output)
            if (result.code != 0) {
                val duplicate = Regex("(?:Program type already present|Duplicate class):?\\s*([\\w.$]+)")
                    .find(result.output)
                val plain = when {
                    duplicate != null ->
                        "Two libraries contain the same class ${duplicate.groupValues[1]}. " +
                            "Remove one of them."
                    result.output.contains("OutOfMemoryError", ignoreCase = true) ||
                        result.output.contains("Java heap space", ignoreCase = true) ->
                        "The build ran out of memory. Close other apps and try again."
                    else -> toolFailure("D8 did not finish", result)
                }
                throw Stop(Step.MakeDex, plain)
            }
            if (dexDir.listFiles()?.any { it.extension == "dex" } != true) {
                throw Stop(Step.MakeDex, "D8 finished but made no dex file.")
            }
        }

        // --- pack and sign ---------------------------------------------------------

        private fun pack(manifest: File): File {
            val resources = File(apkDir, "resources.ap_")
            val projectDex = dexDir.listFiles()?.filter { it.extension == "dex" }?.sortedBy { it.name }
                .orEmpty()
            // The project's dex first (classes.dex), then the pre-dexed libraries. If the
            // project itself needed multidex, the library files shift right but keep order.
            val allDex = projectDex + tools.libraries.dexFiles
            if (projectDex.isEmpty() || allDex.isEmpty()) {
                throw Stop(Step.PackApp, "There is no dex to put in the APK.")
            }
            val named = allDex.mapIndexed { index, file ->
                (if (index == 0) "classes.dex" else "classes${index + 1}.dex") to file
            }
            val unsigned = File(apkDir, "app-unsigned.apk")
            unsigned.delete()
            AlignedApkWriter.write(resources, named, unsigned)
            log.appendLine("packed ${unsigned.length()} bytes · ${named.size} dex files")
            return unsigned
        }

        private fun sign(unsigned: File): File {
            val output = File(projectDir, "build/outputs/apk/debug/app-debug.apk")
            output.parentFile?.mkdirs()
            val temp = File(output.parentFile, "${output.name}.tmp")
            temp.delete()
            val signer = ApkSigner.SignerConfig.Builder(
                "closecode-debug",
                tools.signer.key,
                tools.signer.chain,
            ).build()
            ApkSigner.Builder(listOf(signer))
                .setInputApk(unsigned)
                .setOutputApk(temp)
                .setMinSdkVersion(config.minSdk)
                .setV1SigningEnabled(config.minSdk < 24)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()
            val verification = ApkVerifier.Builder(temp)
                .setMinCheckedPlatformVersion(config.minSdk)
                .build()
                .verify()
            if (!verification.isVerified) {
                temp.delete()
                throw Stop(
                    Step.Sign,
                    "The APK was signed but did not verify. If an older build is installed, " +
                        "uninstall it once and build again.",
                )
            }
            output.delete()
            if (!temp.renameTo(output)) {
                temp.copyTo(output, overwrite = true)
                temp.delete()
            }
            log.appendLine("signed ${output.name} · ${output.length()} bytes")
            writeBuildJson(output)
            return output
        }

        private fun writeBuildJson(apk: File) {
            val json = JsonObject().apply {
                addProperty("time", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
                addProperty("durationMs", times.sumOf { it.millis })
                addProperty("sizeBytes", apk.length())
                addProperty("sha256", sha256(apk))
                addProperty("status", "success")
                addProperty("packageName", config.packageName)
                addProperty("versionCode", config.versionCode)
                addProperty("versionName", config.versionName)
            }
            File(buildDir, "build.json").writeText(
                GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
            )
        }

        // --- helpers ---------------------------------------------------------------

        private fun toolFor(id: String): File? = tools.toolFiles[id]

        private fun normalizePath(path: String): String {
            val file = File(path)
            val absolute = if (file.isAbsolute) file else File(projectDir, path)
            val root = projectDir.absolutePath + File.separator
            return if (absolute.absolutePath.startsWith(root)) {
                absolute.absolutePath.removePrefix(root).replace('\\', '/')
            } else {
                path.replace('\\', '/')
            }
        }

        private fun toolFailure(what: String, result: ToolRunner.Result): String {
            val tail = result.output.trim().lines().filter { it.isNotBlank() }
                .takeLast(5).joinToString(" ").take(400)
            return if (tail.isEmpty()) "$what (exit ${result.code})." else "$what (exit ${result.code}): $tail"
        }

        private fun writeLog() {
            runCatching {
                logsDir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                File(logsDir, "build-$stamp.txt").writeText(log.toString())
                logsDir.listFiles()?.filter { it.isFile }
                    ?.sortedByDescending { it.name }
                    ?.drop(10)
                    ?.forEach { it.delete() }
            }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Writes a zip with resources.arsc stored and aligned to 4 bytes, which Android
     * requires from API 30 on and which every device accepts. All other entries are
     * deflated normally.
     */
    private object AlignedApkWriter {
        fun write(resourcesAp: File, dexEntries: List<Pair<String, File>>, output: File) {
            ZipFile(resourcesAp).use { source ->
                CountingOutputStream(output.outputStream().buffered()).use { counter ->
                    ZipOutputStream(counter).use { zip ->
                        // Sort: manifest and resources first, then res, then dex. Order is
                        // not required; determinism makes two builds comparable.
                        val entries = source.entries().toList()
                            .filter { !it.isDirectory && it.name != "META-INF/MANIFEST.MF" }
                            .sortedWith(
                                compareBy(
                                    { it.name != "AndroidManifest.xml" },
                                    { it.name != "resources.arsc" },
                                ),
                            )
                        for (entry in entries) {
                            val bytes = source.getInputStream(entry).use { it.readBytes() }
                            put(zip, counter, entry.name, bytes, stored = entry.name == "resources.arsc")
                        }
                        for ((entryName, file) in dexEntries) {
                            put(zip, counter, entryName, file.readBytes(), stored = false)
                        }
                    }
                }
            }
        }

        private fun put(
            zip: ZipOutputStream,
            counter: CountingOutputStream,
            name: String,
            bytes: ByteArray,
            stored: Boolean,
        ) {
            val entry = ZipEntry(name)
            entry.method = if (stored) ZipEntry.STORED else ZipEntry.DEFLATED
            if (stored) {
                entry.size = bytes.size.toLong()
                entry.compressedSize = bytes.size.toLong()
                entry.crc = java.util.zip.CRC32().apply { update(bytes) }.value
                val nameBytes = name.toByteArray(Charsets.UTF_8).size
                // header 30 + name + extra; choose padding so the data starts aligned.
                val base = counter.count + 30 + nameBytes + 4
                val pad = ((4 - (base % 4)) % 4).toInt()
                val extra = ByteArray(4 + pad)
                extra[0] = 0xFE.toByte()
                extra[1] = 0xCA.toByte()
                extra[2] = pad.toByte()
                extra[3] = 0
                entry.extra = extra
            }
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    private class CountingOutputStream(private val delegate: java.io.OutputStream) :
        java.io.OutputStream() {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            count += len
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
}
