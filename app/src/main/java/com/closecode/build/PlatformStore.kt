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
 * this SDK line. The zip is unpacked only far enough to keep `android.jar`.
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
 * index. The build-tools download is AndroidIDE's Android-native 34.0.4 archive; its aapt2 is
 * used when present and the APK's bundled libaapt2.so remains the fallback. Nothing is unpacked
 * into a directory the user can write: every path is derived here.
 */
class PlatformStore(
    private val context: Context,
    private val kv: KeyValueStore,
) {
    private val root: File get() = File(context.filesDir, "sdk")
    private val platforms: File get() = File(root, "platforms")
    private val buildTools: File get() = File(root, "build-tools")

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

    fun buildToolsDir(): File? {
        val dir = File(buildTools, BUILD_TOOLS_VERSION)
        return dir.takeIf { it.isDirectory }
    }

    fun sizeBytes(): Long = listOf(platforms, buildTools)
        .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    fun installPlatform(
        api: Int,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        if (!licenseAccepted()) {
            throw IOException("Accept the Android SDK license first.")
        }
        val platform = SdkPlatforms.firstOrNull { it.api == api }
            ?: throw IOException("Android $api is not available.")
        val part = File(context.cacheDir, "sdk-${platform.fileName}.part")
        download(platform.url, part, platform.sizeBytes, onProgress)
        val actual = sha1(part)
        if (!actual.equals(platform.sha1, ignoreCase = true)) {
            part.delete()
            throw IOException("The platform download failed its checksum.")
        }
        val temp = File(context.cacheDir, "sdk-android-$api")
        temp.deleteRecursively()
        temp.mkdirs()
        ZipFile(part).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { candidate ->
                !candidate.isDirectory &&
                    (candidate.name == "android.jar" || candidate.name.endsWith("/android.jar"))
            } ?: throw IOException("The platform archive has no android.jar.")
            val out = File(temp, "android.jar")
            zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        val target = platformDir(api)
        target.deleteRecursively()
        target.mkdirs()
        val jar = File(target, "android.jar")
        if (!File(temp, "android.jar").renameTo(jar)) {
            File(temp, "android.jar").copyTo(jar, overwrite = true)
        }
        temp.deleteRecursively()
        part.delete()
        jar
    }

    fun removePlatform(api: Int): Boolean {
        val dir = platformDir(api)
        return !dir.exists() || dir.deleteRecursively()
    }

    fun installBuildTools(
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
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

    fun removeBuildTools(): Boolean {
        val dir = File(buildTools, BUILD_TOOLS_VERSION)
        return !dir.exists() || dir.deleteRecursively()
    }

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

    private companion object {
        const val KEY_LICENSE = "sdk_license_accepted"
        const val BUILD_TOOLS_VERSION = "34.0.4"
        const val BUILD_TOOLS_URL =
            "https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/" +
                "v34.0.4/build-tools-34.0.4-aarch64.tar.xz"
        const val BUILD_TOOLS_SIZE = 0L
        const val BUILD_TOOLS_SHA256 =
            "4bbbdeee608ff8d3a2c1534d0a5270305a02b1f200403a536c75ac84c41e52fa"
    }
}
