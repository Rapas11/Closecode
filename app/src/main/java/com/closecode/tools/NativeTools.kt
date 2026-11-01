package com.closecode.tools

import android.content.Context
import android.os.Build
import android.system.Os
import com.closecode.build.matchesSha256
import com.closecode.debug.DebugLog
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class NativePackage(
    val version: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class NativeToolsManifest(
    val ndk: NativePackage,
    val cmake: NativePackage,
) {
    companion object {
        fun load(context: Context): NativeToolsManifest =
            runCatching {
                context.assets.open("native-tools.json").bufferedReader().use { it.readText() }
            }.mapCatching { parse(it) }.getOrElse { empty() }

        fun parse(json: String): NativeToolsManifest {
            val root = JsonParser.parseString(json).asJsonObject
            fun packageOf(name: String): NativePackage {
                val part = root.getAsJsonObject(name)
                return NativePackage(
                    version = part?.get("version")?.asString.orEmpty(),
                    fileName = part?.get("fileName")?.asString.orEmpty(),
                    url = part?.get("url")?.asString.orEmpty(),
                    sizeBytes = part?.get("sizeBytes")?.asLong ?: 0L,
                    sha256 = part?.get("sha256")?.asString.orEmpty(),
                )
            }
            return NativeToolsManifest(ndk = packageOf("ndk"), cmake = packageOf("cmake"))
        }

        fun empty(): NativeToolsManifest = NativeToolsManifest(
            ndk = NativePackage("", "", "", 0L, ""),
            cmake = NativePackage("", "", "", 0L, ""),
        )
    }
}

/** The installed native pair, placed inside the SDK so the Android Gradle plugin finds it. */
data class NativeToolchain(
    val ndkHome: File,
    val cmakeHome: File,
    val ndkVersion: String,
    val cmakeVersion: String,
) {
    val cmake: File get() = File(cmakeHome, "bin/cmake")
    val ninja: File get() = File(cmakeHome, "bin/ninja")
    val bin: File get() = File(cmakeHome, "bin")
}

/**
 * Optional NDK and CMake for projects with C/C++. The SDK manager cannot install these:
 * Google's NDK packages are built for desktop Linux, not for Android. These are the
 * AndroidIDE-NDK aarch64 builds, installed side by side in `files/sdk/ndk` and
 * `files/sdk/cmake` where the Android Gradle plugin already looks.
 *
 * Nothing is downloaded until the user asks. Both packages are large, especially the NDK.
 */
class NativeToolsStore(
    private val context: Context,
    val manifest: NativeToolsManifest = NativeToolsManifest.load(context),
    private val sdkRoot: File = File(context.filesDir, "sdk"),
) {
    val ndkRoot: File get() = File(sdkRoot, "ndk")
    val cmakeRoot: File get() = File(sdkRoot, "cmake")
    val ndkHome: File get() = File(ndkRoot, manifest.ndk.version)
    val cmakeHome: File get() = File(cmakeRoot, manifest.cmake.version)
    private val downloads: File get() = File(context.cacheDir, "native-tools")

    fun ndkReady(): Boolean =
        manifest.ndk.version.isNotBlank() &&
            File(ndkHome, "source.properties").isFile &&
            File(ndkHome, "toolchains/llvm/prebuilt").isDirectory

    fun cmakeReady(): Boolean =
        manifest.cmake.version.isNotBlank() &&
            File(cmakeHome, "bin/cmake").isFile &&
            File(cmakeHome, "bin/ninja").isFile

    fun isReady(): Boolean = ndkReady() && cmakeReady()

    fun toolchain(): NativeToolchain? =
        if (isReady()) {
            NativeToolchain(
                ndkHome = ndkHome,
                cmakeHome = cmakeHome,
                ndkVersion = manifest.ndk.version,
                cmakeVersion = manifest.cmake.version,
            )
        } else {
            null
        }

    fun missing(): List<String> = buildList {
        if (!ndkReady()) add("NDK")
        if (!cmakeReady()) add("CMake")
    }

    fun installedBytes(): Long = listOf(ndkHome, cmakeHome)
        .filter { it.exists() }
        .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    fun install(progress: (ShellToolsStore.Progress) -> Unit): ShellToolsStore.Result {
        if (isReady()) return ShellToolsStore.Result(true)
        try {
            if (!ndkReady()) {
                installPackage(
                    packageInfo = manifest.ndk,
                    root = ndkRoot,
                    target = ndkHome,
                    label = "NDK",
                    stage = "ndk",
                    progress = progress,
                ) { source, target ->
                    val top = source.findNdkRoot()
                    top.copyOrMoveTo(target)
                    ensureNdkLinks(target)
                }
            }
            if (!cmakeReady()) {
                installPackage(
                    packageInfo = manifest.cmake,
                    root = cmakeRoot,
                    target = cmakeHome,
                    label = "CMake",
                    stage = "cmake",
                    progress = progress,
                ) { source, target ->
                    val top = source.listFiles()
                        ?.firstOrNull { File(it, "bin/cmake").isFile }
                        ?: source
                    top.copyOrMoveTo(target)
                    File(target, "bin").listFiles()?.forEach { it.setExecutable(true, false) }
                }
            }
            if (!isReady()) throw IOException("The native tools did not install completely.")
            DebugLog.crumb("TOOLS", "Native tools installed")
            return ShellToolsStore.Result(true)
        } catch (error: Throwable) {
            DebugLog.crumb(
                "TOOLS",
                "Native tools install failed: ${error.message ?: error.javaClass.simpleName}",
            )
            return ShellToolsStore.Result(false, error.message ?: error.javaClass.simpleName)
        }
    }

    fun remove(): Boolean {
        val ndk = !ndkHome.exists() || ndkHome.deleteRecursively()
        val cmake = !cmakeHome.exists() || cmakeHome.deleteRecursively()
        downloads.deleteRecursively()
        DebugLog.crumb("TOOLS", "Native tools removed")
        return ndk && cmake
    }

    private fun installPackage(
        packageInfo: NativePackage,
        root: File,
        target: File,
        label: String,
        stage: String,
        progress: (ShellToolsStore.Progress) -> Unit,
        unpack: (source: File, target: File) -> Unit,
    ) {
        if (packageInfo.version.isBlank() || packageInfo.url.isBlank()) {
            throw IOException("The $label download is missing from the manifest.")
        }
        root.mkdirs()
        downloads.mkdirs()
        val zip = File(downloads, packageInfo.fileName)
        val part = File(downloads, packageInfo.fileName + ".part")
        if (!zip.isFile || zip.length() != packageInfo.sizeBytes) {
            download(part, packageInfo, label, stage, progress)
            if (!part.renameTo(zip)) {
                part.copyTo(zip, overwrite = true)
                part.delete()
            }
        }
        if (packageInfo.sha256.isNotBlank() && !matchesSha256(zip, packageInfo.sha256)) {
            zip.delete()
            throw IOException("The $label download failed its checksum.")
        }
        progress(
            ShellToolsStore.Progress(
                "$stage-unpack",
                packageInfo.sizeBytes,
                packageInfo.sizeBytes,
                null,
                "Unpacking $label ${packageInfo.version}...",
            ),
        )
        val temp = File(root, "${packageInfo.version}.tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        unzip(zip, temp)
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        unpack(temp, target)
        temp.deleteRecursively()
        zip.delete()
        File(target, INSTALLED).writeText("${packageInfo.version}\n${packageInfo.sha256}\n")
        progress(
            ShellToolsStore.Progress(
                "done",
                packageInfo.sizeBytes,
                packageInfo.sizeBytes,
                null,
                "$label ${packageInfo.version} installed.",
            ),
        )
    }

    private fun download(
        part: File,
        packageInfo: NativePackage,
        label: String,
        stage: String,
        progress: (ShellToolsStore.Progress) -> Unit,
    ) {
        var done = if (part.isFile) part.length() else 0L
        if (done > packageInfo.sizeBytes) {
            part.delete()
            done = 0L
        }
        if (done == packageInfo.sizeBytes && done > 0L) return
        val connection = (URL(packageInfo.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            if (done > 0L) setRequestProperty("Range", "bytes=$done-")
            setRequestProperty("User-Agent", "CloseCode/${Build.VERSION.SDK_INT}")
        }
        try {
            val code = connection.responseCode
            if (done > 0L && code != HttpURLConnection.HTTP_PARTIAL) {
                part.delete()
                done = 0L
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("The $label server answered HTTP $code.")
                }
            } else if (code !in 200..299) {
                throw IOException("The $label server answered HTTP $code.")
            }
            part.parentFile?.mkdirs()
            java.io.FileOutputStream(part, done > 0L).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        progress(
                            ShellToolsStore.Progress(
                                "$stage-download",
                                done,
                                packageInfo.sizeBytes,
                                packageInfo.fileName,
                                "Downloading $label ${packageInfo.version}...",
                            ),
                        )
                    }
                }
            }
            if (done != packageInfo.sizeBytes) {
                throw IOException(
                    "Short $label download: $done of ${packageInfo.sizeBytes} bytes.",
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unzip(zip: File, target: File) {
        ZipFile(zip).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                if (name.isEmpty()) continue
                val out = File(target, name).canonicalFile
                if (!out.path.startsWith(target.canonicalPath + File.separator)) {
                    throw IOException("Unsafe path in the native archive: ${entry.name}")
                }
                out.parentFile?.mkdirs()
                archive.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun File.findNdkRoot(): File {
        listFiles()?.firstOrNull { File(it, "source.properties").isFile }?.let { return it }
        return listFiles()?.singleOrNull()?.takeIf { it.isDirectory } ?: this
    }

    private fun File.copyOrMoveTo(target: File): Unit {
        if (!renameTo(target)) {
            copyRecursively(target, overwrite = true)
            deleteRecursively()
        }
    }

    private fun ensureNdkLinks(ndk: File) {
        link(File(ndk, "prebuilt"), "linux-aarch64")
        link(File(ndk, "toolchains/llvm/prebuilt"), "linux-aarch64")
        link(File(ndk, "shader-tools"), "linux-aarch64")
        val executables = listOf(
            File(ndk, "toolchains/llvm/prebuilt/linux-x86_64/bin"),
            File(ndk, "prebuilt/linux-x86_64/bin"),
        )
        executables.filter { it.isDirectory }.forEach { dir ->
            dir.listFiles()?.forEach { it.setExecutable(true, false) }
        }
        listOf("ndk-build", "ndk-gdb", "ndk-stack", "ndk-which", "ndk-lldb")
            .map { File(ndk, it) }
            .filter { it.isFile }
            .forEach { it.setExecutable(true, false) }
    }

    private fun link(parent: File, name: String) {
        val target = File(parent, "linux-x86_64")
        if (!target.isDirectory) return
        val link = File(parent, name)
        if (link.isDirectory) return
        if (link.exists()) link.delete()
        runCatching { Os.symlink("linux-x86_64", link.absolutePath) }
    }

    private companion object {
        const val INSTALLED = ".installed"
    }
}
