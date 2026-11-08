package com.closecode.tools

import android.content.Context
import android.os.Build
import com.closecode.build.matchesSha256
import com.closecode.debug.DebugLog
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class GradleDistribution(
    val version: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class GradleToolsManifest(
    val tools: ShellToolsManifest,
    val distribution: GradleDistribution,
) {
    companion object {
        fun load(context: Context): GradleToolsManifest =
            runCatching {
                context.assets.open("gradle-tools.json").bufferedReader().use { it.readText() }
            }.mapCatching { parse(it) }.getOrElse { empty() }

        fun parse(json: String): GradleToolsManifest {
            val root = JsonParser.parseString(json).asJsonObject
            val distribution = root.getAsJsonObject("distribution")
            return GradleToolsManifest(
                tools = ShellToolsManifest.parse(json),
                distribution = GradleDistribution(
                    version = distribution?.get("version")?.asString.orEmpty(),
                    fileName = distribution?.get("fileName")?.asString.orEmpty(),
                    url = distribution?.get("url")?.asString.orEmpty(),
                    sizeBytes = distribution?.get("sizeBytes")?.asLong ?: 0L,
                    sha256 = distribution?.get("sha256")?.asString.orEmpty(),
                ),
            )
        }

        fun empty(): GradleToolsManifest = GradleToolsManifest(
            tools = ShellToolsManifest.empty(),
            distribution = GradleDistribution("", "", "", 0L, ""),
        )
    }
}

class GradleToolsStore(
    private val context: Context,
    val manifest: GradleToolsManifest = GradleToolsManifest.load(context),
) {
    val directory: File get() = termux.directory
    val home: File get() = termux.home
    val gradleRoot: File get() = File(context.filesDir, "gradle")
    val distributionDir: File
        get() = File(gradleRoot, "gradle-${manifest.distribution.version}")
    val gradle: File get() = File(distributionDir, "bin/gradle")
    val javaHome: File get() = File(directory, "lib/jvm/java-17-openjdk")
    val java: File get() = File(javaHome, "bin/java")
    val gradleHome: File get() = File(gradleRoot, "home")

    private val termux = ShellToolsStore(
        context = context,
        manifest = manifest.tools,
        prefixName = PREFIX,
        homeName = HOME,
        label = "Gradle tools",
        cachePrefix = "gradle-tools",
    )

    fun isReady(): Boolean =
        manifest.distribution.version.isNotBlank() &&
            termux.isReady() &&
            java.isFile &&
            gradle.isFile &&
            File(distributionDir, INSTALLED).isFile

    fun installedBytes(): Long = termux.installedBytes() + gradleRoot.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }

    fun installedPackages(): List<String> = termux.installedPackages()

    fun install(progress: (ShellToolsStore.Progress) -> Unit): ShellToolsStore.Result {
        if (isReady()) return ShellToolsStore.Result(true)
        if (!termux.isReady()) {
            val result = termux.install(progress)
            if (!result.ok) return result
        }
        if (!java.isFile) {
            return ShellToolsStore.Result(false, "The Gradle JDK has no java binary.")
        }
        val distribution = manifest.distribution
        if (distribution.version.isBlank() || distribution.url.isBlank()) {
            return ShellToolsStore.Result(false, "The Gradle manifest is missing.")
        }
        val zip = File(gradleRoot, distribution.fileName)
        val part = File(gradleRoot, distribution.fileName + ".part")
        try {
            gradleRoot.mkdirs()
            if (!zip.isFile || !matchesSha256(zip, distribution.sha256)) {
                download(part, distribution, progress)
                if (!matchesSha256(part, distribution.sha256)) {
                    throw IOException("The Gradle download failed its checksum.")
                }
                if (!part.renameTo(zip)) {
                    part.copyTo(zip, overwrite = true)
                    part.delete()
                }
            }
            progress(
                ShellToolsStore.Progress(
                    "gradle-unpack",
                    distribution.sizeBytes,
                    distribution.sizeBytes,
                    null,
                    "Unpacking Gradle ${distribution.version}...",
                ),
            )
            val temp = File(gradleRoot, "gradle-${distribution.version}.tmp")
            temp.deleteRecursively()
            temp.mkdirs()
            unzip(zip, temp)
            val extracted = temp.listFiles()
                ?.singleOrNull()
                ?.takeIf { File(it, "bin/gradle").isFile }
                ?: temp
            val executable = File(extracted, "bin/gradle")
            if (!executable.isFile) throw IOException("The Gradle archive has no bin/gradle.")
            File(extracted, "bin").listFiles()?.forEach { it.setExecutable(true, false) }
            executable.setExecutable(true, false)
            File(extracted, INSTALLED).writeText(
                "${distribution.version}\n${distribution.sha256}\n",
            )
            distributionDir.deleteRecursively()
            if (!extracted.renameTo(distributionDir)) {
                extracted.copyRecursively(distributionDir, overwrite = true)
                extracted.deleteRecursively()
            }
            temp.deleteRecursively()
            zip.delete()
            DebugLog.crumb("TOOLS", "Gradle ${distribution.version} installed")
            progress(
                ShellToolsStore.Progress(
                    "done",
                    distribution.sizeBytes,
                    distribution.sizeBytes,
                    null,
                    "Gradle ${distribution.version} installed.",
                ),
            )
            return ShellToolsStore.Result(true)
        } catch (error: Throwable) {
            part.delete()
            DebugLog.crumb("TOOLS", "Gradle install failed: ${error.message ?: error.javaClass.simpleName}")
            return ShellToolsStore.Result(false, error.message ?: error.javaClass.simpleName)
        }
    }

    fun remove(): Boolean {
        val tools = termux.remove()
        val gradle = !gradleRoot.exists() || gradleRoot.deleteRecursively()
        DebugLog.crumb("TOOLS", "Gradle tools removed")
        return tools && gradle
    }

    private fun download(
        part: File,
        distribution: GradleDistribution,
        progress: (ShellToolsStore.Progress) -> Unit,
    ) {
        var done = if (part.isFile) part.length() else 0L
        if (done > distribution.sizeBytes) {
            part.delete()
            done = 0L
        }
        if (done == distribution.sizeBytes && done > 0L) return
        val connection = (URL(distribution.url).openConnection() as HttpURLConnection).apply {
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
                    throw IOException("The Gradle server answered HTTP $code.")
                }
            } else if (code !in 200..299) {
                throw IOException("The Gradle server answered HTTP $code.")
            }
            part.parentFile?.mkdirs()
            part.outputStream().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        progress(
                            ShellToolsStore.Progress(
                                "gradle-download",
                                done,
                                distribution.sizeBytes,
                                distribution.fileName,
                                "Downloading Gradle ${distribution.version}...",
                            ),
                        )
                    }
                }
            }
            if (done != distribution.sizeBytes) {
                throw IOException(
                    "Short Gradle download: $done of ${distribution.sizeBytes} bytes.",
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
                    throw IOException("Unsafe path in the Gradle archive: ${entry.name}")
                }
                out.parentFile?.mkdirs()
                archive.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private companion object {
        const val PREFIX = "g"
        const val HOME = "gh"
        const val INSTALLED = ".installed"
    }
}
