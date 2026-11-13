package com.closecode.build

import android.content.Context
import com.closecode.debug.DebugLog
import com.closecode.tools.ResumableDownload
import com.closecode.tools.TarExtractor
import com.closecode.ui.settings.KeyValueStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.tukaani.xz.XZInputStream

/** One platform package the SDK Manager offers. Sizes and checksums are Google's own. */
data class SdkPlatform(
    val api: Int,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha1: String,
)

/**
 * The three platforms Closecode can compile against. API 34 is kept because imported projects
 * still name it; 35 is the template default; 36 is the newest platform Google publishes for
 * this SDK line. The zip is unpacked in full so the custom engine and Gradle both get
 * `android.jar`, `data/res`, and the other platform files.
 */
val SdkPlatforms = listOf(
    SdkPlatform(
        34,
        "platform-34-ext7_r03.zip",
        "https://dl.google.com/android/repository/platform-34-ext7_r03.zip",
        63_180_081,
        "1f2e9478d6a7601425ceaa553311dc43191f103d",
    ),
    SdkPlatform(
        35,
        "platform-35_r02.zip",
        "https://dl.google.com/android/repository/platform-35_r02.zip",
        64_273_788,
        "0bb560a90a7a2cbd0dd8348224d518b638fe7949",
    ),
    SdkPlatform(
        36,
        "platform-36_r02.zip",
        "https://dl.google.com/android/repository/platform-36_r02.zip",
        65_878_410,
        "2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576",
    ),
)

/**
 * Downloads and keeps Android SDK packages under `files/sdk/`.
 *
 * The platform download is the official Google zip, checked against the SHA-1 in the repository
 * index. The build-tools download is the AndroidIDE Rv2 Android-native 35.0.1 archive; the
 * APK's bundled libaapt2.so is preferred, and the downloaded aapt2 is the fallback. Nothing
 * is unpacked into a directory the user can write: every path is derived here.
 */
class PlatformStore(
    private val context: Context,
    private val kv: KeyValueStore,
) {
    private val root: File get() = File(context.filesDir, "sdk")
    private val platforms: File get() = File(root, "platforms")
    private val buildTools: File get() = File(root, "build-tools")
    private val licenses: File get() = File(root, "licenses")

    fun rootDir(): File = root

    fun platformToolsDir(): File = File(root, "platform-tools")

    /**
     * AGP validates platform-tools even for a pure Kotlin assembleDebug, and its own SDK
     * loader refuses to continue when the package is absent (P0-3). The archive contains
     * `platform-tools/` itself, `source.properties` and `package.xml`.
     */
    fun platformToolsReady(): Boolean {
        val dir = platformToolsDir()
        return File(dir, "adb").isFile && File(dir, "source.properties").isFile
    }

    fun buildToolsVersion(): String = BUILD_TOOLS_VERSION

    /**
     * The minimum build-tools AGP asks for. CloseCode ships 35.0.1, which satisfies the
     * 35.0.0 floor for AGP 8.10-8.13; an AGP 9 project asks for 36.0.0 and is reported
     * missing instead of being silently built with an older revision.
     */
    fun requiredBuildToolsVersion(agpVersion: String?): String =
        requiredBuildToolsForAgp(agpVersion)

    fun licenseAccepted(): Boolean = kv.getBoolean(KEY_LICENSE, false)

    /**
     * Records the user's acceptance and writes the files AGP actually looks for. The flag
     * alone is not enough: without `<sdkRoot>/licenses/android-sdk-license` every package
     * check fails (P0-4).
     */
    fun acceptLicense() {
        kv.putBoolean(KEY_LICENSE, true)
        runCatching { installLicenses() }
    }

    /** Writes the accepted license hashes; idempotent and safe to call on every setup. */
    fun installLicenses(): List<File> {
        licenses.mkdirs()
        return LicenseFiles.map { (name, hashes) ->
            File(licenses, name).apply {
                writeText(hashes.joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun licensesReady(): Boolean = LicenseFiles.keys.all { File(licenses, it).isFile }

    fun installedPlatforms(): List<Int> = SdkPlatforms
        .filter { androidJar(it.api) != null }
        .map { it.api }

    fun androidJar(api: Int): File? =
        File(File(platforms, "android-$api"), "android.jar").takeIf { it.isFile }

    fun platformDir(api: Int): File = File(platforms, "android-$api")

    /** The AndroidIDE aapt2, when its archive was installed and unpacked. */
    fun buildToolsAapt2(): File? = buildToolsDir()?.let { dir ->
        File(dir, "aapt2").takeIf { it.isFile } ?: File(dir, "aapt2.exe").takeIf { it.isFile }
    }

    fun buildToolsDir(): File? = buildToolsDir(BUILD_TOOLS_VERSION)

    fun buildToolsDir(version: String): File? =
        File(buildTools, version).takeIf { it.isDirectory }

    /**
     * True when a build-tools folder holds binaries this phone can actually execute.
     *
     * A folder that came from a desktop package holds x86-64 binaries. They are present, they
     * have the right names, and they cannot run here — so the folder is not "installed".
     * A binary that is not an ELF file at all is a wrapper script, which the shebang repair
     * makes runnable; it is left alone.
     */
    fun buildToolsUsable(dir: File): Boolean {
        val aapt2 = File(dir, "aapt2")
        if (!aapt2.isFile) return false
        return ElfArchitecture.isRunnable(aapt2)
    }

    /** True when the folder's binaries are known to be for another processor. */
    fun buildToolsForeign(dir: File): Boolean =
        ElfArchitecture.isForeign(File(dir, "aapt2"))

    /** The processor of a build-tools folder's aapt2, for the status output. */
    fun buildToolsArchitecture(dir: File): String? =
        ElfArchitecture.of(File(dir, "aapt2"))

    /**
     * Removes build-tools folders whose binaries are proven to be for another processor.
     *
     * Only a folder that is *known* to be foreign is removed. A folder whose binaries cannot
     * be read is left alone: it may be a wrapper script that works, and a working install is
     * never destroyed.
     */
    fun removeUnusableBuildTools(keep: File? = null): Int {
        var removed = 0
        buildTools.listFiles()
            ?.filter { it.isDirectory && it != keep && buildToolsForeign(it) }
            ?.forEach { dir ->
                if (dir.deleteRecursively()) removed++
            }
        if (removed > 0) {
            DebugLog.crumb("SDK", "removed $removed build-tools folders that cannot run here")
        }
        return removed
    }

    /**
     * Makes sure a build-tools version exists and can run on this phone.
     *
     * A project may pin a version the app did not install. The working ARM64 build-tools are
     * copied under that name, so the project builds instead of failing with "Build Tools
     * revision not found" — and never with binaries that cannot execute here.
     */
    fun ensureBuildToolsVersion(version: String): File? {
        if (version.isBlank()) return null
        val existing = buildToolsDir(version)
        if (existing != null && buildToolsUsable(existing)) return existing
        val source = buildToolsDir()?.takeIf { buildToolsUsable(it) } ?: return null
        if (version == source.name) return source
        val target = File(buildTools, version)
        // A copy that is not finished must never be visible at its final path: a build that
        // finds a half-copied build-tools fails in a way the user cannot act on.
        val staging = File(buildTools, ".$version.incoming")
        staging.deleteRecursively()
        runCatching {
            source.copyRecursively(staging, overwrite = true)
            File(staging, ".closecode-shim").writeText(
                "Compatibility copy of build-tools ${source.name} for $version\n",
            )
            // AGP expects the version it asked for, so the copy has to claim it.
            for (name in listOf("source.properties", "package.xml")) {
                val file = File(staging, name)
                if (!file.isFile) continue
                val major = version.substringBefore('.').trim()
                val minor = version.substringAfter('.', "").substringBefore('.').trim()
                val micro = version.substringAfterLast('.', "0").trim()
                file.writeText(
                    file.readText()
                        .replace(Regex("Pkg\\.Revision=.*"), "Pkg.Revision=$version")
                        .replace(
                            Regex(
                                "<revision>\\s*<major>(\\d+)</major>\\s*<minor>(\\d+)</minor>" +
                                    "\\s*<micro>(\\d+)</micro>\\s*</revision>",
                            ),
                            "<revision><major>$major</major><minor>$minor</minor>" +
                                "<micro>$micro</micro></revision>",
                        ),
                )
            }
            // Only a copy that can actually run is moved into place.
            if (!buildToolsUsable(staging)) {
                staging.deleteRecursively()
                return null
            }
            target.deleteRecursively()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
        }.onFailure {
            DebugLog.crumb("SDK", "build-tools alias failed: ${it.javaClass.simpleName}")
            runCatching { staging.deleteRecursively() }
        }
        return target.takeIf { buildToolsUsable(it) }
    }

    /**
     * Removes the leftovers of an interrupted install.
     *
     * A half-downloaded package leaves a `.installer` folder behind. A build that finds one
     * decides the package is unusable and fetches it itself — which is how a build ends up
     * downloading an x86-64 package on an ARM phone.
     */
    fun removeIncompleteInstalls(): Int {
        var removed = 0
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { area ->
                area.walkTopDown()
                    .filter { it.isDirectory && it.name == ".installer" }
                    .forEach { marker ->
                        if (marker.deleteRecursively()) removed++
                    }
            }
        if (removed > 0) {
            DebugLog.crumb("SDK", "removed $removed half-finished installs")
        }
        return removed
    }

    /** The highest installed build-tools at or above [minimum], with its directory name. */
    fun bestBuildTools(minimum: String): Pair<String, File>? =
        buildTools.listFiles()
            ?.filter { it.isDirectory && buildToolsUsable(it) }
            ?.map { it.name to it }
            ?.filter { (version, _) -> compareVersions(version, minimum) >= 0 }
            ?.maxByOrNull { (version, _) -> compareVersions(version, "0") }

    fun sizeBytes(): Long = listOf(platforms, buildTools, platformToolsDir())
        .sumOf { dir -> com.closecode.tools.DiskUsage.bytes(dir) }

    fun installPlatform(
        api: Int,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        if (!licenseAccepted()) {
            throw IOException("Accept the Android SDK license first.")
        }
        androidJar(api)?.let { return@runCatching it }
        val platform = SdkPlatforms.firstOrNull { it.api == api }
            ?: throw IOException("Android $api is not available.")
        val part = File(context.cacheDir, "sdk-${platform.fileName}.part")
        download(platform.url, part, platform.sizeBytes, onProgress)
        val actual = sha1(part)
        if (!actual.equals(platform.sha1, ignoreCase = true)) {
            part.delete()
            throw IOException("The platform download failed its checksum.")
        }
        val temp = File(context.cacheDir, "sdk-android-$api.tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        var jar: File? = null
        ZipFile(part).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                if (name.isEmpty()) continue
                val out = File(temp, name).canonicalFile
                if (!out.path.startsWith(temp.canonicalPath + File.separator)) {
                    throw IOException("Unsafe path in the platform archive: ${entry.name}")
                }
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                if (out.name == "android.jar") jar = out
            }
        }
        val source = jar?.parentFile
            ?: throw IOException("The platform archive has no android.jar.")
        val target = platformDir(api)
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
        temp.deleteRecursively()
        part.delete()
        if (licenseAccepted()) installLicenses()
        File(target, "android.jar")
    }

    fun removePlatform(api: Int): Boolean {
        val dir = platformDir(api)
        return !dir.exists() || dir.deleteRecursively()
    }

    fun installBuildTools(
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        buildToolsDir()?.let { dir ->
            // Only a folder whose binaries can run here counts as installed. A folder with
            // x86-64 binaries is not an install; it is a broken one, and it is replaced.
            if (buildToolsUsable(dir)) {
                GradleUserProperties.ensure(
                    File(context.filesDir, "gradle/home"),
                    buildToolsAapt2(),
                    null,
                )
                return@runCatching dir
            }
            DebugLog.crumb("SDK", "replacing build-tools that cannot run on this phone")
            dir.deleteRecursively()
        }
        val part = File(context.cacheDir, "build-tools-$BUILD_TOOLS_VERSION.tar.xz.part")
        download(BUILD_TOOLS_URL, part, BUILD_TOOLS_SIZE, onProgress)
        if (!sha256(part).equals(BUILD_TOOLS_SHA256, ignoreCase = true)) {
            part.delete()
            throw IOException("The build-tools download failed its checksum.")
        }
        val temp = File(context.cacheDir, "build-tools-$BUILD_TOOLS_VERSION.tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        XZInputStream(part.inputStream().buffered()).use { tar ->
            TarExtractor("", temp).extract(tar)
        }
        val extracted = temp.listFiles()?.singleOrNull()?.takeIf { it.isDirectory } ?: temp
        val aapt2 = extracted.walkTopDown().firstOrNull { it.isFile && it.name == "aapt2" }
        val sourceDir = aapt2?.parentFile ?: extracted
        // The package is checked before it is moved into place: a package whose binaries
        // are for another processor is never installed, not even for a moment.
        if (aapt2 == null) {
            throw IOException("The build-tools archive has no aapt2.")
        }
        val architecture = ElfArchitecture.of(aapt2)
        if (architecture != ElfArchitecture.ARM64) {
            throw IOException(
                "The build-tools package holds ${architecture ?: "unknown"} binaries, which " +
                    "cannot run on this phone. Nothing was installed.",
            )
        }
        val target = File(buildTools, BUILD_TOOLS_VERSION)
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!sourceDir.renameTo(target)) {
            sourceDir.copyRecursively(target, overwrite = true)
            sourceDir.deleteRecursively()
        }
        temp.deleteRecursively()
        part.delete()
        if (buildToolsAapt2() == null) {
            throw IOException("The build-tools archive has no aapt2.")
        }
        if (licenseAccepted()) installLicenses()
        repairBuildToolsScripts()
        // A build-tools folder whose binaries are for another processor can never run on this
        // phone. Leaving it next to the working one only gives the user a broken choice.
        removeUnusableBuildTools(keep = target)
        GradleUserProperties.ensure(
            File(context.filesDir, "gradle/home"),
            buildToolsAapt2(),
            null,
        )
        target
    }

    /**
     * Makes the build-tools scripts runnable on Android.
     *
     * Google's build-tools ship shell scripts that start with `#!/bin/bash`, and Android has
     * no bash: `apksigner` then fails with "No such file or directory". Every installed
     * version is repaired, so a directory left behind by an older build stops breaking the
     * build. Returns how many files were fixed.
     */
    fun repairBuildToolsScripts(): Int {
        val fixed = com.closecode.tools.ShebangRepair.repair(buildTools)
        if (fixed > 0) {
            DebugLog.crumb("SDK", "repaired $fixed build-tools scripts for Android")
        }
        return fixed
    }

    fun removeBuildTools(version: String = BUILD_TOOLS_VERSION): Boolean {
        val dir = File(buildTools, version)
        return !dir.exists() || dir.deleteRecursively()
    }

    fun installPlatformTools(
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        if (!licenseAccepted()) {
            throw IOException("Accept the Android SDK license first.")
        }
        if (platformToolsReady()) {
            installLicenses()
            return@runCatching platformToolsDir()
        }
        val part = File(context.cacheDir, "$PLATFORM_TOOLS_FILE.part")
        download(PLATFORM_TOOLS_URL, part, PLATFORM_TOOLS_SIZE, onProgress)
        if (!sha256(part).equals(PLATFORM_TOOLS_SHA256, ignoreCase = true)) {
            part.delete()
            throw IOException("The platform-tools download failed its checksum.")
        }
        val temp = File(context.cacheDir, "platform-tools.tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        XZInputStream(part.inputStream().buffered()).use { tar ->
            TarExtractor("", temp).extract(tar)
        }
        val extracted = temp.walkTopDown()
            .firstOrNull { it.isDirectory && File(it, "source.properties").isFile }
            ?: temp.listFiles()?.firstOrNull { it.isDirectory }
            ?: throw IOException("The platform-tools archive has no source.properties.")
        val target = platformToolsDir()
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!extracted.renameTo(target)) {
            extracted.copyRecursively(target, overwrite = true)
            extracted.deleteRecursively()
        }
        temp.deleteRecursively()
        part.delete()
        if (!platformToolsReady()) {
            throw IOException("The platform-tools archive has no adb.")
        }
        installLicenses()
        target
    }

    fun removePlatformTools(): Boolean {
        val dir = platformToolsDir()
        return !dir.exists() || dir.deleteRecursively()
    }

    fun removeAllLicenses(): Boolean =
        !licenses.exists() || licenses.deleteRecursively()

    fun removeAllBuildTools(): Boolean =
        !buildTools.exists() || buildTools.deleteRecursively()

    fun removeAllPlatforms(): Boolean =
        !platforms.exists() || platforms.deleteRecursively()

    /**
     * Resumable download: bytes already in the `.part` file are a Range request, a server that
     * ignores the range restarts instead of appending garbage, and the result is checked against
     * the pinned size before it can be promoted.
     */
    private fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit,
    ) = ResumableDownload.download(url, target, expectedSize, onProgress)

    private fun sha1(file: File): String = digest(file, "SHA-1")
    private fun sha256(file: File): String = digest(file, "SHA-256")

    private fun digest(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Numeric dotted-version comparison; missing parts count as zero. */
    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    companion object {
        const val KEY_LICENSE = "sdk_license_accepted"
        const val BUILD_TOOLS_VERSION = "35.0.1"
        const val BUILD_TOOLS_URL =
            "https://github.com/AndroidIDE-Rv2Official/androidide-rv2-tools/releases/" +
                "download/v35.0.1/rv2ide-buildtools-3501.tar-aarch64.xz"
        const val BUILD_TOOLS_SIZE = 14_975_324L
        const val BUILD_TOOLS_SHA256 =
            "f2af3b2533584827e749806942be127ac523c88b0e795c82b06f9c8bc1a707d2"

        /** P0-3: AGP validates platform-tools even when the build does not use adb. */
        const val PLATFORM_TOOLS_VERSION = "34.0.4"
        const val PLATFORM_TOOLS_FILE = "platform-tools-$PLATFORM_TOOLS_VERSION-aarch64.tar.xz"
        const val PLATFORM_TOOLS_URL =
            "https://github.com/AndroidIDEOfficial/androidide-tools/releases/" +
                "download/v$PLATFORM_TOOLS_VERSION/$PLATFORM_TOOLS_FILE"
        const val PLATFORM_TOOLS_SIZE = 4_354_052L
        const val PLATFORM_TOOLS_SHA256 =
            "08b5b5c9899201081586f8f8682882784458ff67e6421cef65b1d6a0da877236"

        /** P0-4: the exact hashes AGP's package checks look for, keyed by license file. */
        val LicenseFiles: Map<String, List<String>> = linkedMapOf(
            "android-sdk-license" to listOf(
                "8933bad161af4178b1185d1a37fbf41ea5269c55",
                "d56f5187479451eabf01fb78af6dfcb131a6481e",
                "24333f8a63b6825ea9c5514f83c2829b004d1fee",
            ),
            "android-sdk-preview-license" to listOf(
                "84831b9409646a918e30573bab4c9c91346d8abd",
            ),
            "android-sdk-arm-dbt-license" to listOf(
                "859f317696f67ef3d7f30a50a5560e7834b43903",
            ),
            "android-googletv-license" to listOf(
                "601085b94cd77f0b54ff86406957099ebe79c4d6",
            ),
        )

        /** AGP compatibility table from checkmehere P0-5; callable without a Context. */
        fun requiredBuildToolsForAgp(agpVersion: String?): String {
            val parts = agpVersion.orEmpty().split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return BUILD_TOOLS_VERSION
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return when {
                major >= 9 -> "36.0.0"
                major == 8 && minor >= 10 -> "35.0.0"
                major == 8 && minor >= 2 -> "34.0.0"
                major == 8 -> "33.0.2"
                else -> BUILD_TOOLS_VERSION
            }
        }
    }

}
