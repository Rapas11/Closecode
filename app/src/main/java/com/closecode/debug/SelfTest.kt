package com.closecode.debug

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.closecode.build.BuildChain
import com.closecode.build.BuildConfigValues
import com.closecode.build.BuildTools
import com.closecode.build.CompileDriver
import com.closecode.build.DebugKey
import com.closecode.build.LibraryIndex
import com.closecode.build.SigningIdentity
import com.closecode.build.ToolExec
import com.closecode.build.ToolRunner
import com.closecode.build.Toolchain
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainManifest
import com.closecode.build.matchesSha256
import com.closecode.ui.github.TokenStore
import com.closecode.ui.ide.ShellProcess
import com.closecode.ui.settings.humanSize
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The self-test (spec, Debug tools, item 3): sixteen checks the phone answers about itself,
 * each with one plain sentence a person can act on. It exists because the developer cannot
 * run this app on the user's phone — this is how the phone states the facts instead.
 *
 * Results are checked against what the phone can actually do today: checks whose tools are
 * not built yet say exactly that rather than passing on a promise. Check nine is real now —
 * it generates the Keystore key and signs and verifies a test APK with apksig, on the phone.
 *
 * The 30-second limit per check is cooperative: a check that blocks a thread cannot be
 * interrupted mid-call, but every check here finishes long before its limit in practice.
 */
object SelfTest {

    enum class Status { Pass, Fail, Skipped }

    data class Check(
        val number: Int,
        val name: String,
        val status: Status,
        val detail: String,
    )

    /** The text of the last run, as it appeared on screen — for the debug report. */
    @Volatile
    var lastReport: String? = null
        private set

    private const val CHECK_TIMEOUT_MS = 30_000L

    /** The compilers' first run includes one-time system work; everything after is quick. */
    private const val TOOL_TIMEOUT_MS = 300_000L
    private const val MIN_FREE_BYTES = 1_500_000_000L

    private val names = listOf(
        "64-bit phone",
        "Free storage",
        "Build tools installed",
        "Tool file checksums",
        "aapt2 runs",
        "The Kotlin compiler compiles",
        "The Java compiler compiles",
        "D8 makes a dex",
        "Debug signing key",
        "Full test build",
        "Install permission",
        "Draw over other apps",
        "Notifications",
        "Network",
        "Shell",
        "GitHub sign-in",
    )

    /**
     * Runs every check in order, handing each result to [onCheck] on the main thread as it
     * lands, so the screen fills in while the work continues. The whole run happens on
     * [Dispatchers.IO] — the checks hash files and start processes.
     */
    suspend fun run(context: Context, onCheck: (Check) -> Unit): List<Check> =
        withContext(Dispatchers.IO) {
            // Each entry is (how long the check may take, the check itself). The three
            // compilers get much longer than everything else: their first run on a phone
            // includes the system's one-time conversion of the tool jars, which can take
            // minutes; every later run is fast.
            val checks: List<Pair<Long, suspend () -> Check>> = listOf(
                CHECK_TIMEOUT_MS to { check64Bit() },
                CHECK_TIMEOUT_MS to { checkFreeStorage(context) },
                CHECK_TIMEOUT_MS to { checkToolsInstalled(context) },
                CHECK_TIMEOUT_MS to { checkChecksums(context) },
                CHECK_TIMEOUT_MS to { checkAapt2(context) },
                TOOL_TIMEOUT_MS to { checkKotlinCompiler(context) },
                TOOL_TIMEOUT_MS to { checkEcj(context) },
                TOOL_TIMEOUT_MS to { checkD8(context) },
                CHECK_TIMEOUT_MS to { checkSigning(context) },
                600_000L to { checkFullBuild(context) },
                CHECK_TIMEOUT_MS to { checkInstallPermission(context) },
                CHECK_TIMEOUT_MS to { checkOverlay(context) },
                CHECK_TIMEOUT_MS to { checkNotifications(context) },
                CHECK_TIMEOUT_MS to { checkNetwork(context) },
                CHECK_TIMEOUT_MS to { checkShell(context) },
                CHECK_TIMEOUT_MS to { checkGitHub(context) },
            )
            val results = ArrayList<Check>(checks.size)
            for ((index, entry) in checks.withIndex()) {
                val (timeoutMs, check) = entry
                val result = try {
                    withTimeoutOrNull(timeoutMs) { check() }
                        ?: Check(
                            index + 1,
                            names[index],
                            Status.Fail,
                            "The check did not finish within ${timeoutLabel(timeoutMs)}.",
                        )
                } catch (error: Exception) {
                    Check(
                        index + 1,
                        names[index],
                        Status.Fail,
                        "The check stopped with: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
                results.add(result)
                withContext(Dispatchers.Main) { onCheck(result) }
            }
            lastReport = reportText(facts(context), results)
            results
        }

    /** The block above the list: which APK, on what phone, with how much room left. */
    internal fun facts(context: Context): String = buildString {
        appendLine(BuildInfo.label)
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Processor: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Storage: ${humanSize(StatFs(context.filesDir.path).availableBytes)} free")
        appendLine("Memory: ${humanSize(freeMemory(context))} free")
    }

    /** The report the Copy report button copies: facts, then one line per check. */
    internal fun reportText(facts: String, checks: List<Check>): String = buildString {
        append(facts)
        appendLine()
        checks.forEach { check ->
            appendLine("${check.number}. ${check.name} — ${check.status} — ${check.detail}")
        }
    }

    private fun freeMemory(context: Context): Long {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return 0L
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.availMem
    }

    // --- the checks -----------------------------------------------------------------

    private fun check64Bit(): Check = Build.SUPPORTED_ABIS.let { abis ->
        val number = 1
        if (abis.contains("arm64-v8a")) {
            Check(number, names[0], Status.Pass, "This phone is arm64-v8a (${abis.joinToString(", ")}).")
        } else {
            Check(
                number,
                names[0],
                Status.Fail,
                "This phone reports ${abis.joinToString(", ")}; the build tools need a 64-bit arm64 phone.",
            )
        }
    }

    private fun checkFreeStorage(context: Context): Check {
        val free = StatFs(context.filesDir.path).availableBytes
        return if (free >= MIN_FREE_BYTES) {
            Check(
                2,
                names[1],
                Status.Pass,
                "${humanSize(free)} free; ${humanSize(MIN_FREE_BYTES)} is the least a build needs.",
            )
        } else {
            Check(
                2,
                names[1],
                Status.Fail,
                "Only ${humanSize(free)} free; a build needs about ${humanSize(MIN_FREE_BYTES)}.",
            )
        }
    }

    private fun toolsFolder(context: Context): File = ToolchainHolder.store(context).directory

    private fun isPresent(folder: File, component: com.closecode.build.ToolComponent): Boolean =
        File(folder, component.fileName).let { it.isFile && it.length() == component.sizeBytes }

    private fun checkToolsInstalled(context: Context): Check {
        val folder = toolsFolder(context)
        val components = ToolchainManifest.load(context).files
        val present = components.count { isPresent(folder, it) }
        return when {
            present == 0 -> Check(
                3,
                names[2],
                Status.Skipped,
                "Not installed yet — download them from Settings > Build & Toolchain.",
            )
            present < components.size -> Check(
                3,
                names[2],
                Status.Fail,
                "$present of ${components.size} files are installed. " +
                    "Download the pack again from Settings > Build & Toolchain.",
            )
            else -> Check(
                3,
                names[2],
                Status.Pass,
                "${versionsLine(ToolchainManifest.load(context))} — the versions this app pins.",
            )
        }
    }

    /** `Kotlin 1.9.24, ECJ 3.46.0, R8 8.13.25` for the ids the pack actually lists. */
    private fun versionsLine(toolchain: Toolchain): String =
        listOf("kotlin-compiler" to "Kotlin", "ecj" to "ECJ", "r8" to "R8")
            .mapNotNull { (id, name) -> toolchain.byId(id)?.let { "$name ${it.version}" } }
            .joinToString(", ")

    /** `30 seconds` / `5 minutes` for the timeout line of a check that ran too long. */
    private fun timeoutLabel(timeoutMs: Long): String =
        if (timeoutMs % 60_000 == 0L) "${timeoutMs / 60_000} minutes" else "${timeoutMs / 1000} seconds"

    private fun checkChecksums(context: Context): Check {
        val folder = toolsFolder(context)
        val components = ToolchainManifest.load(context).files
        val installed = components.filter { isPresent(folder, it) }
        if (installed.size < components.size) {
            return Check(4, names[3], Status.Skipped, "Not all files are installed yet, so there is nothing to compare.")
        }
        val bad = installed.firstOrNull { !matchesSha256(File(folder, it.fileName), it.sha256) }
        return if (bad == null) {
            Check(4, names[3], Status.Pass, "All ${installed.size} files match their SHA-256.")
        } else {
            Check(
                4,
                names[3],
                Status.Fail,
                "${bad.fileName} does not match its SHA-256. Delete the tools in Settings and download them again.",
            )
        }
    }

    /**
     * The real thing: run the aapt2 that ships inside this APK as a lib file and read its
     * version line. A binary that cannot start is a plain Fail with the reason.
     */
    private fun checkAapt2(context: Context): Check {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
        if (!binary.isFile) {
            return Check(
                5,
                names[4],
                Status.Skipped,
                "No aapt2 inside this APK build — the shipped APK carries it.",
            )
        }
        val output = try {
            val process = ProcessBuilder(binary.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            text
        } catch (error: Exception) {
            return Check(
                5,
                names[4],
                Status.Fail,
                "aapt2 could not start: ${error.message ?: error.javaClass.simpleName}",
            )
        }
        val line = output.lines().firstOrNull { it.contains("Android Asset Packaging Tool") }
        return if (line != null) {
            Check(5, names[4], Status.Pass, line.trim().take(120))
        } else {
            Check(5, names[4], Status.Fail, "aapt2 ran but said: ${output.take(160)}")
        }
    }

    /**
     * The real thing: compile a tiny Kotlin file with the downloaded compiler and check that
     * a class file appeared. This is the check the whole build depends on — a compiler that
     * cannot run on the phone makes every build step a lie.
     */
    private fun checkKotlinCompiler(context: Context): Check {
        val toolchain = ToolchainManifest.load(context)
        val folder = toolsFolder(context)
        val jars = ToolchainManifest.KOTLINC_LAUNCH_IDS.mapNotNull { id ->
            toolchain.byId(id)?.let { File(folder, it.fileName) }?.takeIf { it.isFile }
        }
        if (jars.size < ToolchainManifest.KOTLINC_LAUNCH_IDS.size) {
            return Check(
                6,
                names[5],
                Status.Skipped,
                "Build tools are not installed yet, so there is nothing to run.",
            )
        }
        val dir = File(context.cacheDir, "selftest-tools").apply { deleteRecursively(); mkdirs() }
        val source = File(dir, "Hello.kt").apply { writeText("fun hello(): String = \"hello\"\n") }
        val classes = File(dir, "classes").apply { mkdirs() }
        val stdlib = toolchain.byId("kotlin-stdlib")?.let { File(folder, it.fileName) }
        val args = CompileDriver.kotlincArgs(
            sources = listOf(source),
            classpath = listOfNotNull(stdlib),
            outputDir = classes,
            composePluginJar = null,
        )
        val result = ToolExec.kotlinc(jars, args, dexOptDir = dexOptDir(context))
        return if (result.code == 0 && File(classes, "HelloKt.class").isFile) {
            Check(6, names[5], Status.Pass, "Compiled a test file with Kotlin ${toolchain.kotlin}.")
        } else {
            Check(6, names[5], Status.Fail, toolFailure("The Kotlin compiler did not finish", result))
        }
    }

    /** The real thing: ECJ compiles a tiny Java file against the downloaded platform jar. */
    private fun checkEcj(context: Context): Check {
        val toolchain = ToolchainManifest.load(context)
        val folder = toolsFolder(context)
        val ecj = toolchain.byId("ecj")?.let { File(folder, it.fileName) }?.takeIf { it.isFile }
            ?: return Check(
                7,
                names[6],
                Status.Skipped,
                "Build tools are not installed yet, so there is nothing to run.",
            )
        val androidJar = toolchain.byId("android-jar")?.let { File(folder, it.fileName) }?.takeIf { it.isFile }
            ?: return Check(
                7,
                names[6],
                Status.Skipped,
                "The Android platform file is not installed yet, so there is nothing to compile against.",
            )
        val dir = File(context.cacheDir, "selftest-tools").apply { mkdirs() }
        val source = File(dir, "Hello.java").apply {
            writeText("class Hello { static String hello() { return \"hello\"; } }\n")
        }
        val classes = File(dir, "java-classes").apply { deleteRecursively(); mkdirs() }
        val args = CompileDriver.ecjArgs(
            sources = listOf(source),
            classpath = emptyList(),
            outputDir = classes,
            platformJar = androidJar,
        )
        val result = ToolExec.ecj(listOf(ecj), args, dexOptDir = dexOptDir(context))
        return if (result.code == 0 && File(classes, "Hello.class").isFile) {
            Check(7, names[6], Status.Pass, "Compiled a test file with ECJ ${toolchain.byId("ecj")?.version}.")
        } else {
            Check(7, names[6], Status.Fail, toolFailure("The Java compiler did not finish", result))
        }
    }

    /**
     * The real thing: D8 turns the class file the Kotlin check produced into classes.dex.
     * Without a class to dex it says so, rather than pretending.
     */
    private fun checkD8(context: Context): Check {
        val toolchain = ToolchainManifest.load(context)
        val folder = toolsFolder(context)
        val r8 = toolchain.byId("r8")?.let { File(folder, it.fileName) }?.takeIf { it.isFile }
            ?: return Check(
                8,
                names[7],
                Status.Skipped,
                "Build tools are not installed yet, so there is nothing to run.",
            )
        val klass = File(context.cacheDir, "selftest-tools/classes/HelloKt.class")
        if (!klass.isFile) {
            return Check(8, names[7], Status.Skipped, "The Kotlin step above did not produce a class file to dex.")
        }
        val androidJar = toolchain.byId("android-jar")?.let { File(folder, it.fileName) }?.takeIf { it.isFile }
            ?: return Check(8, names[7], Status.Skipped, "The Android platform file is not installed.")
        val dexOut = File(context.cacheDir, "selftest-tools/dex").apply { deleteRecursively(); mkdirs() }
        val args = CompileDriver.d8Args(listOf(klass), listOf(androidJar), dexOut)
        val result = ToolExec.d8(listOf(r8), args, dexOptDir = dexOptDir(context))
        return if (result.code == 0 && File(dexOut, "classes.dex").isFile) {
            Check(8, names[7], Status.Pass, "D8 turned the class into classes.dex.")
        } else {
            Check(8, names[7], Status.Fail, toolFailure("D8 did not finish", result))
        }
    }

    /** Where the system is left to convert tool dex; the conversion is cached here. */
    private fun dexOptDir(context: Context): File = File(context.codeCacheDir, "toolchain-dex")

    /** `What did not finish (exit N): the last lines it printed.` — the real error text. */
    private fun toolFailure(what: String, result: ToolRunner.Result): String {
        val tail = result.output.trim().lines().filter { it.isNotBlank() }
            .takeLast(4).joinToString(" ").take(300)
        return if (tail.isEmpty()) "$what (exit ${result.code})." else "$what (exit ${result.code}): $tail"
    }

    /**
     * The real thing: the Keystore debug key signs a tiny APK with v2 and v3 schemes, and
     * apksig — the same library the app will sign with — verifies the result on the phone.
     */
    private fun checkSigning(context: Context): Check {
        val pair = DebugKey.ensure(context)
        val dir = File(context.cacheDir, "selftest").apply { mkdirs() }
        val unsigned = File(dir, "unsigned.apk")
        val signed = File(dir, "signed.apk")
        signed.delete()
        ZipOutputStream(unsigned.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest package=\"com.closecode.selftest\"/>".toByteArray())
            zip.closeEntry()
        }
        val signer = ApkSigner.SignerConfig.Builder("closecode-debug", pair.key, pair.chain).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(29)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
        val verification = ApkVerifier.Builder(signed)
            .setMinCheckedPlatformVersion(29)
            .build()
            .verify()
        dir.deleteRecursively()
        return if (verification.isVerified) {
            Check(
                9,
                names[8],
                Status.Pass,
                "The debug key signed a test APK and apksig verified it (v2 + v3).",
            )
        } else {
            val first = verification.errors.firstOrNull()?.toString()?.take(140) ?: "unknown"
            Check(9, names[8], Status.Fail, "Signing finished but verification failed: $first")
        }
    }

    /**
     * The real thing: a tiny Compose project is written to the cache, built through the
     * exact chain the Build panel uses (resources, Compose, dex, pack, sign), and the APK
     * is verified with apksig. This is the check that proves the phone can build, not just
     * run the individual tools.
     */
    private fun checkFullBuild(context: Context): Check {
        val store = ToolchainHolder.store(context)
        if (!store.isReady()) {
            return Check(
                10, names[9], Status.Skipped,
                "Build tools are not installed yet, so there is nothing to build with.",
            )
        }
        val librariesDir = store.librariesDir()
            ?: return Check(
                10, names[9], Status.Fail,
                "The library cache is missing. Open Build tools and download the tools again.",
            )
        val libraries = LibraryIndex.load(librariesDir)
            ?: return Check(10, names[9], Status.Fail, "The library cache has no readable index.")
        val aapt2 = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
        if (!aapt2.isFile) {
            return Check(10, names[9], Status.Fail, "aapt2 is missing from this build of the app.")
        }
        val androidJar = store.jar("android-jar")
            ?: return Check(10, names[9], Status.Fail, "android.jar is missing from the tools.")
        val stdlib = store.jar("kotlin-stdlib")
            ?: return Check(10, names[9], Status.Fail, "The Kotlin stdlib is missing from the tools.")
        val ids = ToolchainManifest.KOTLINC_LAUNCH_IDS + listOf("ecj", "r8")
        val toolFiles = ids.distinct()
            .mapNotNull { id -> store.jar(id)?.let { id to it } }
            .toMap()
        if (toolFiles.size != ids.distinct().size) {
            return Check(10, names[9], Status.Fail, "A build tool file is missing. Download the tools again.")
        }
        val signer = runCatching { DebugKey.ensure(context) }.getOrElse { error ->
            return Check(
                10, names[9], Status.Fail,
                "The debug key is not available: ${error.message ?: "the Keystore refused it"}.",
            )
        }
        val project = File(context.cacheDir, "self-test/build-app")
        return try {
            project.deleteRecursively()
            writeSelfTestProject(project)
            val started = System.currentTimeMillis()
            val result = BuildChain.build(
                projectDir = project,
                config = BuildConfigValues(
                    packageName = "com.closecode.selftest",
                    minSdk = 29,
                    targetSdk = 35,
                    versionCode = 1,
                    versionName = "1.0",
                    useCompose = true,
                ),
                tools = BuildTools(
                    aapt2 = aapt2,
                    androidJar = androidJar,
                    stdlib = stdlib,
                    libraries = libraries,
                    signer = SigningIdentity(signer.key, signer.chain),
                    toolFiles = toolFiles,
                    composePlugin = store.jar("kotlin-compiler"),
                    dexOptDir = File(context.codeCacheDir, "self-test-dex"),
                ),
            )
            val seconds = (System.currentTimeMillis() - started) / 1000
            val apk = result.apk
            if (result.ok && apk != null) {
                val verification = ApkVerifier.Builder(apk).build().verify()
                if (!verification.isVerified) {
                    Check(
                        10, names[9], Status.Fail,
                        "The full build made an APK but its signature did not verify.",
                    )
                } else {
                    Check(
                        10, names[9], Status.Pass,
                        "Built, signed and verified a Compose app with the on-phone chain " +
                            "in ${seconds}s (${apk.length() / 1024} KB).",
                    )
                }
            } else {
                val where = result.failure?.step?.label ?: "Build"
                val message = result.failure?.message ?: "it made no APK"
                Check(10, names[9], Status.Fail, "$where: $message".take(300))
            }
        } catch (error: Exception) {
            Check(
                10, names[9], Status.Fail,
                "The full build stopped: ${error.message ?: error.javaClass.simpleName}".take(300),
            )
        }
    }

    /** The smallest real Compose app: a screen, a theme and a preview. */
    private fun writeSelfTestProject(project: File) {
        File(project, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText(
                """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "com.closecode.selftest"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "com.closecode.selftest"
                        minSdk = 29
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0"
                    }
                }
                """.trimIndent() + "\n",
            )
        }
        File(project, "app/src/main/AndroidManifest.xml").apply {
            parentFile?.mkdirs()
            writeText(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:label="@string/app_name">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent() + "\n",
            )
        }
        File(project, "app/src/main/res/values/strings.xml").apply {
            parentFile?.mkdirs()
            writeText(
                """
                <resources>
                    <string name="app_name">Self test</string>
                </resources>
                """.trimIndent() + "\n",
            )
        }
        File(project, "app/src/main/java/com/closecode/selftest/MainActivity.kt").apply {
            parentFile?.mkdirs()
            writeText(
                """
                package com.closecode.selftest

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview

                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent { MaterialTheme { Hello() } }
                    }
                }

                @Composable
                fun Hello() {
                    Text("CloseCode built me")
                }

                @Preview(showBackground = true)
                @Composable
                fun HelloPreview() {
                    MaterialTheme { Hello() }
                }
                """.trimIndent() + "\n",
            )
        }
    }

    private fun checkInstallPermission(context: Context): Check {
        val granted = context.packageManager.canRequestPackageInstalls()
        return if (granted) {
            Check(11, names[10], Status.Pass, "Allowed. Android still shows its own screen for each install.")
        } else {
            Check(11, names[10], Status.Skipped, "Not granted yet — CloseCode asks for it at the first install.")
        }
    }

    private fun checkOverlay(context: Context): Check {
        val granted = Settings.canDrawOverlays(context)
        return if (granted) {
            Check(12, names[11], Status.Pass, "Allowed. The Back to editor button can show over another app.")
        } else {
            Check(12, names[11], Status.Skipped, "Not granted yet — needed when CloseCode opens another app.")
        }
    }

    private fun checkNotifications(context: Context): Check {
        if (Build.VERSION.SDK_INT < 33) {
            return Check(13, names[12], Status.Pass, "Not asked for before Android 13; nothing to check.")
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            Check(13, names[12], Status.Pass, "Allowed. A build can show progress in the shade.")
        } else {
            Check(13, names[12], Status.Skipped, "Not granted yet — CloseCode asks when a build needs to show progress.")
        }
    }

    private fun checkNetwork(context: Context): Check {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
                Check(14, names[13], Status.Pass, "Connected over Wi-Fi.")
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ->
                Check(14, names[13], Status.Pass, "Connected over mobile data.")
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true ->
                Check(14, names[13], Status.Pass, "Connected over Ethernet.")
            else ->
                Check(14, names[13], Status.Skipped, "No network right now — downloads need Wi-Fi or mobile data.")
        }
    }

    private suspend fun checkShell(context: Context): Check {
        val dir = File(context.cacheDir, "selftest-shell").apply { mkdirs() }
        val heard = java.util.Collections.synchronizedList(ArrayList<String>())
        val shell = ShellProcess(directory = dir, onLine = { line -> heard.add(line) })
        return try {
            shell.start()
            shell.run("echo closecode-selftest-ok")
            val echoed = withTimeoutOrNull(8_000) {
                while (heard.none { it.contains("closecode-selftest-ok") }) delay(100)
                true
            } == true
            when {
                !shell.isRunning && !echoed ->
                    Check(15, names[14], Status.Fail, "The shell did not start.")
                echoed -> Check(
                    15,
                    names[14],
                    Status.Skipped,
                    "The pipe terminal echoed the test word. The real PTY is not built yet.",
                )
                else ->
                    Check(15, names[14], Status.Fail, "The shell started but did not answer within 8 seconds.")
            }
        } finally {
            runCatching { shell.close() }
        }
    }

    private fun checkGitHub(context: Context): Check {
        val signedIn = runCatching { TokenStore(context).read() != null }.getOrDefault(false)
        return if (signedIn) {
            Check(16, names[15], Status.Pass, "Signed in (the token itself is never shown or sent).")
        } else {
            Check(16, names[15], Status.Skipped, "Not signed in yet — the Git panel signs in when needed.")
        }
    }
}
