package com.closecode.tools

import android.content.Context
import android.os.Build
import com.closecode.build.GradleLauncher
import com.closecode.build.GradleUserProperties
import com.closecode.build.PlatformStore
import com.closecode.build.matchesSha256
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.closecode.debug.DebugLog
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
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
    val distributions: Map<String, GradleDistribution> = mapOf(distribution.version to distribution),
) {
    fun forVersion(version: String): GradleDistribution? = distributions[version]

    companion object {
        fun load(context: Context): GradleToolsManifest =
            runCatching {
                context.assets.open("gradle-tools.json").bufferedReader().use { it.readText() }
            }.mapCatching { parse(it) }.getOrElse { empty() }

        fun parse(json: String): GradleToolsManifest {
            val root = JsonParser.parseString(json).asJsonObject
            val distribution = root.getAsJsonObject("distribution")
            val fallback = GradleDistribution(
                version = distribution?.get("version")?.asString.orEmpty(),
                fileName = distribution?.get("fileName")?.asString.orEmpty(),
                url = distribution?.get("url")?.asString.orEmpty(),
                sizeBytes = distribution?.get("sizeBytes")?.asLong ?: 0L,
                sha256 = distribution?.get("sha256")?.asString.orEmpty(),
            )
            val catalog = LinkedHashMap<String, GradleDistribution>()
            if (fallback.version.isNotBlank()) catalog[fallback.version] = fallback
            root.getAsJsonArray("distributions")?.forEach { element ->
                runCatching {
                    val item = element.asJsonObject
                    val entry = GradleDistribution(
                        version = item.get("version")?.asString.orEmpty(),
                        fileName = item.get("fileName")?.asString.orEmpty(),
                        url = item.get("url")?.asString.orEmpty(),
                        sizeBytes = item.get("sizeBytes")?.asLong ?: 0L,
                        sha256 = item.get("sha256")?.asString.orEmpty(),
                    )
                    if (entry.version.isNotBlank() && entry.url.isNotBlank()) {
                        catalog[entry.version] = entry
                    }
                }
            }
            return GradleToolsManifest(
                tools = ShellToolsManifest.parse(json),
                distribution = fallback,
                distributions = catalog,
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
        get() = distributionDir(manifest.distribution.version)
    val gradle: File get() = gradleFor(manifest.distribution.version)

    fun distributionDir(version: String): File = File(gradleRoot, "gradle-$version")
    fun gradleFor(version: String): File = File(distributionDir(version), "bin/gradle")

    fun installedVersions(): List<String> = gradleRoot.listFiles().orEmpty()
        .filter { it.isDirectory && File(it, INSTALLED).isFile }
        .mapNotNull { it.name.removePrefix("gradle-").takeIf { name -> name != it.name } }
    val defaultJavaHome: File get() = File(directory, "lib/jvm/java-17-openjdk")
    val javaHome: File get() = JdkStore.selected(context)?.home ?: defaultJavaHome
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

    fun isReady(version: String = manifest.distribution.version): Boolean =
        version.isNotBlank() &&
            termux.isReady() &&
            java.isFile &&
            gradleFor(version).isFile &&
            File(distributionDir(version), INSTALLED).isFile

    fun installedBytes(): Long = termux.installedBytes() + DiskUsage.bytes(gradleRoot)

    /**
     * Removes the hand-placed workarounds from an earlier test run.
     *
     * The report lists them: an init script that forced a valid aapt2
     * (`init.d/zz-closecode-aapt2-fix.gradle`). P0-2 is fixed in the build itself now, so a
     * second override in the Gradle home would only fight it. The files live in the app's own
     * storage, so the app may remove them.
     */
    private fun removeStaleWorkarounds() {
        val initD = File(gradleHome, "init.d")
        initD.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("zz-closecode-")) {
                DebugLog.crumb("GRADLE", "removed stale workaround ${file.name}")
                file.delete()
            }
        }
    }

    fun installedPackages(): List<String> = termux.installedPackages()

    private fun patchUserProperties() {
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        GradleUserProperties.ensure(
            gradleHome,
            platform.buildToolsAapt2(),
            javaHome.takeIf { java.isFile },
        )
    }

    fun install(
        progress: (ShellToolsStore.Progress) -> Unit,
        version: String? = null,
    ): ShellToolsStore.Result {
        val wanted = version?.takeIf { it.isNotBlank() } ?: manifest.distribution.version
        // A distribution installed before the xargs fix is still on disk. Patch it on every
        // install attempt, including the early-return "already ready" path, so re-running
        // `closecode setup --gradle` repairs the launcher instead of keeping the broken one.
        GradleLauncher.patch(gradleFor(wanted))
        removeStaleWorkarounds()
        patchUserProperties()
        if (isReady(wanted)) {
            val repaired = termux.repairPython(progress)
            return if (repaired.ok) {
                ShellToolsStore.Result(true)
            } else {
                ShellToolsStore.Result(false, repaired.error)
            }
        }
        if (!termux.isReady()) {
            val result = termux.install(progress)
            if (!result.ok) return result
        }
        if (!java.isFile) {
            return ShellToolsStore.Result(false, "The Gradle JDK has no java binary.")
        }
        val distribution = manifest.forVersion(wanted)
            ?: return ShellToolsStore.Result(
                false,
                "Gradle $wanted is not in the download catalog. " +
                    "Installed: ${installedVersions().joinToString().ifBlank { "none" }}.",
            )
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
                    part.delete()
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
            GradleLauncher.patch(executable)
            File(extracted, INSTALLED).writeText(
                "${distribution.version}\n${distribution.sha256}\n",
            )
            val target = distributionDir(distribution.version)
            target.deleteRecursively()
            if (!extracted.renameTo(target)) {
                extracted.copyRecursively(target, overwrite = true)
                extracted.deleteRecursively()
            }
            temp.deleteRecursively()
            zip.delete()
            patchUserProperties()
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
            DebugLog.crumb("TOOLS", "Gradle install failed: ${error.message ?: error.javaClass.simpleName}")
            return ShellToolsStore.Result(false, error.message ?: error.javaClass.simpleName)
        }
    }

    fun remove(): Boolean {
        val tools = termux.remove()
        // GRADLE_USER_HOME holds the dependency cache. Removing the distribution and JDK
        // must not throw away a cache that took a full build to fill; clean --gradle-caches
        // is the explicit action for that.
        val gradle = removeGradleRootExceptCache(gradleRoot, "home")
        context.cacheDir.listFiles()
            ?.filter {
                it.name.startsWith("gradle-tools-") ||
                    it.name.endsWith(".zip.part")
            }
            ?.forEach { it.deleteRecursively() }
        DebugLog.crumb("TOOLS", "Gradle tools removed")
        return tools && gradle
    }

    private fun download(
        part: File,
        distribution: GradleDistribution,
        progress: (ShellToolsStore.Progress) -> Unit,
    ) {
        var resumed = false
        ResumableDownload.download(
            distribution.url,
            part,
            distribution.sizeBytes,
        ) { done, total ->
            val size = if (total > 0) total else distribution.sizeBytes
            val message = if (!resumed && done > 0) {
                resumed = true
                "Continuing from $done of $size bytes…"
            } else {
                "Downloading Gradle ${distribution.version}…"
            }
            progress(
                ShellToolsStore.Progress(
                    "gradle-download",
                    done,
                    size,
                    distribution.fileName,
                    message,
                ),
            )
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

/** Deletes the Gradle install but keeps `gradleRoot/home`, the user's dependency cache. */
internal fun removeGradleRootExceptCache(root: File, cacheName: String): Boolean {
    if (!root.exists()) return true
    val cache = File(root, cacheName).canonicalFile
    return root.listFiles().orEmpty().all { entry ->
        entry.canonicalFile == cache || entry.deleteRecursively()
    }
}
