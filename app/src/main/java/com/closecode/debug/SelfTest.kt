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
import com.closecode.build.DebugKey
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
            @Suppress("UNCHECKED_CAST")
            val checks: List<suspend () -> Check> = listOf(
                { check64Bit() },
                { checkFreeStorage(context) },
                { checkToolsInstalled(context) },
                { checkChecksums(context) },
                { checkAapt2() },
                { checkPendingTool(context, 6, "The Kotlin compiler") },
                { checkPendingTool(context, 7, "The Java compiler") },
                { checkPendingTool(context, 8, "The dex step") },
                { checkSigning(context) },
                { checkFullBuild() },
                { checkInstallPermission(context) },
                { checkOverlay(context) },
                { checkNotifications(context) },
                { checkNetwork(context) },
                { checkShell(context) },
                { checkGitHub(context) },
            )
            val results = ArrayList<Check>(checks.size)
            for ((index, check) in checks.withIndex()) {
                val result = try {
                    withTimeoutOrNull(CHECK_TIMEOUT_MS) { check() }
                        ?: Check(
                            index + 1,
                            names[index],
                            Status.Fail,
                            "The check did not finish within 30 seconds.",
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

    private fun toolsFolder(context: Context): File = File(context.filesDir, "toolchain")

    private fun isPresent(folder: File, component: com.closecode.build.ToolComponent): Boolean =
        File(folder, component.fileName).let { it.isFile && it.length() == component.sizeBytes }

    private fun checkToolsInstalled(context: Context): Check {
        val folder = toolsFolder(context)
        val components = ToolchainManifest.components
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
                "${versionsLine()} — the versions this app pins.",
            )
        }
    }

    /** `Kotlin 2.4.0, ECJ 3.46.0, ...` for the ids the manifest actually lists. */
    private fun versionsLine(): String = listOf("kotlin-compiler", "ecj", "r8", "apksig")
        .mapNotNull { id ->
            ToolchainManifest.components.firstOrNull { it.id == id }?.let { "${it.id} ${it.version}" }
        }
        .joinToString(", ")

    private fun checkChecksums(context: Context): Check {
        val folder = toolsFolder(context)
        val components = ToolchainManifest.components
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

    private fun checkAapt2(): Check = Check(
        5,
        names[4],
        Status.Skipped,
        "The app does not carry aapt2 yet — it arrives with the real build step.",
    )

    /**
     * Checks 6-8 ask the downloaded pack to run. The pack on the wire is desktop bytecode;
     * a phone runs dex, and the converted pack is not built yet. Saying that is the honest
     * answer — the alternative would be a check that passes without running anything.
     */
    private fun checkPendingTool(context: Context, number: Int, what: String): Check {
        val folder = toolsFolder(context)
        val allPresent = ToolchainManifest.components.all { isPresent(folder, it) }
        return if (!allPresent) {
            Check(
                number,
                names[number - 1],
                Status.Skipped,
                "Build tools are not installed yet, so there is nothing to run.",
            )
        } else {
            Check(
                number,
                names[number - 1],
                Status.Skipped,
                "$what is in the pack, but the pack is desktop bytecode. The phone-ready pack is not built yet.",
            )
        }
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

    private fun checkFullBuild(): Check = Check(
        10,
        names[9],
        Status.Skipped,
        "The full test build arrives with the real build step.",
    )

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
