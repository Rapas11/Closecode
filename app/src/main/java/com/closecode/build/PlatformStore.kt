package com.closecode.build

import android.content.Context
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

    fun rootDir(): File = root

    fun buildToolsVersion(): String = BUILD_TOOLS_VERSION

    /**
     * The minimum build-tools AGP asks for. CloseCode ships 35.0.1, which satisfies the
     * 35.0.0 floor for AGP 8.10-8.13; an AGP 9 project asks for 36.0.0 and is reported
     * missing instead of being silently built with an older revision.
     */
    fun requiredBuildToolsVersion(agpVersion: String?): String =
        requiredBuildToolsForAgp(agpVersion)

    fun licenseAccepted(): Boolean = kv.getBoolean(KEY_LICENSE, false)

    fun acceptLicense() {
        kv.putBoolean(KEY_LICENSE, true)
    }

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

    /** The highest installed build-tools at or above [minimum], with its directory name. */
    fun bestBuildTools(minimum: String): Pair<String, File>? =
        buildTools.listFiles()
            ?.filter { it.isDirectory && File(it, "aapt2").isFile }
            ?.map { it.name to it }
            ?.filter { (version, _) -> compareVersions(version, minimum) >= 0 }
            ?.maxByOrNull { (version, _) -> compareVersions(version, "0") }

    fun sizeBytes(): Long = listOf(platforms, buildTools)
        .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

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
            if (File(dir, "aapt2").isFile) return@runCatching dir
        }
        val part = File(context.cacheDir, "build-tools-$BUILD_TOOLS_VERSION.tar.xz")
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
        target
    }

    fun removeBuildTools(version: String = BUILD_TOOLS_VERSION): Boolean {
        val dir = File(buildTools, version)
        return !dir.exists() || dir.deleteRecursively()
    }

    fun removeAllBuildTools(): Boolean =
        !buildTools.exists() || buildTools.deleteRecursively()

    fun removeAllPlatforms(): Boolean =
        !platforms.exists() || platforms.deleteRecursively()

    private fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "CloseCode/${android.os.Build.VERSION.SDK_INT}")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("The package server returned HTTP $code.")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            target.outputStream().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        onProgress(done, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

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
