package com.closecode.tools

import android.content.Context
import com.closecode.build.GradleUserProperties
import com.closecode.build.PlatformStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import java.io.File

data class InstalledJdk(
    val home: File,
    val version: String,
    val major: Int,
    val source: String,
) {
    val supported: Boolean get() = major >= 17
}

/**
 * The JDKs CloseCode can actually run Gradle with.
 *
 * JDK 17 comes with Full Gradle. JDK 21 is optional and installed through the same verified
 * Termux package path as the shell tools. Whatever is selected here is written to
 * `$GRADLE_USER_HOME/gradle.properties` as `org.gradle.java.home`, so the editor, the CLI
 * and the project's own `./gradlew` all use the same real directory.
 */
class JdkStore(
    private val context: Context,
    private val prefs: SharedPreferencesKeyValueStore = SharedPreferencesKeyValueStore(context),
) {
    private val jdk21 = ShellToolsStore(
        context = context,
        manifest = ShellToolsManifest(
            version = "1",
            repo = "https://packages.termux.dev/apt/termux-main",
            roots = listOf("openjdk-21"),
            required = listOf("lib/jvm/java-21-openjdk/bin/java"),
        ),
        prefixName = "j",
        homeName = "hj",
        label = "JDK 21",
        cachePrefix = "jdk-21",
    )

    val jdk21Home: File get() = File(jdk21.directory, "lib/jvm/java-21-openjdk")
    val jdk21Java: File get() = File(jdk21Home, "bin/java")

    fun jdk21Ready(): Boolean = jdk21.isReady() && jdk21Java.isFile

    fun jdk21Bytes(): Long = if (jdk21Ready()) DiskUsage.bytes(jdk21.directory) else 0L

    fun installed(): List<InstalledJdk> {
        val roots = buildList {
            File(context.dataDir, "file").listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { add(File(it, "lib/jvm")) }
            add(File(context.filesDir, "jdk"))
        }
        val seen = mutableSetOf<String>()
        return roots.flatMap { root ->
            root.listFiles().orEmpty().toList()
        }.mapNotNull { home ->
            if (!File(home, "bin/java").isFile) return@mapNotNull null
            val canonical = runCatching { home.canonicalFile }.getOrDefault(home)
            if (!seen.add(canonical.absolutePath)) return@mapNotNull null
            val version = readVersion(canonical)
            InstalledJdk(
                home = canonical,
                version = version.first,
                major = version.second,
                source = sourceLabel(canonical),
            )
        }.sortedWith(
            compareByDescending<InstalledJdk> { it.supported }
                .thenByDescending { it.major }
                .thenBy { it.home.absolutePath },
        )
    }

    fun selected(): InstalledJdk? {
        val all = installed()
        val saved = prefs.getString(KEY, null)
        val match = saved?.let { path ->
            all.firstOrNull {
                it.supported && it.home.absolutePath == path
            }
        }
        return match ?: default(all)
    }

    fun select(jdk: InstalledJdk): Boolean {
        if (!jdk.supported || !File(jdk.home, "bin/java").isFile) return false
        prefs.putString(KEY, jdk.home.absolutePath)
        writeGradleProperties(jdk.home)
        return true
    }

    /**
     * Selects a JDK by path, the way the command line names it.
     *
     * The path must be a JDK this phone really has: a directory with a working `bin/java`.
     * AGP 8.x needs Java 17 or newer, so anything older is refused instead of failing
     * later inside a build.
     */
    fun selectPath(path: String): InstalledJdk? {
        val wanted = path.trim().trimEnd('/')
        if (wanted.isEmpty()) return null
        val match = installed().firstOrNull {
            it.home.absolutePath == wanted || it.home.path == wanted
        } ?: return null
        if (!match.supported) return null
        if (!select(match)) return null
        return match
    }

    /** Forgets the choice and falls back to the JDK that comes with Full Gradle. */
    fun clearSelection(): InstalledJdk? {
        prefs.remove(KEY)
        val fallback = default(installed())
        fallback?.let { writeGradleProperties(it.home) }
        return fallback
    }

    fun install21(progress: (ShellToolsStore.Progress) -> Unit): ShellToolsStore.Result {
        val result = jdk21.install(progress)
        if (result.ok) {
            val jdk = installed().firstOrNull {
                it.home.absolutePath == jdk21Home.canonicalFile.absolutePath
            }
            if (jdk != null && selected()?.home == jdk.home) writeGradleProperties(jdk.home)
        }
        return result
    }

    fun remove21(): Boolean {
        if (isSelected(jdk21Home)) prefs.remove(KEY)
        val removed = jdk21.remove()
        if (removed) default(installed())?.let { writeGradleProperties(it.home) }
        return removed
    }

    fun isSelected(home: File): Boolean =
        selected()?.home?.absolutePath == runCatching { home.canonicalFile }
            .getOrDefault(home).absolutePath

    private fun default(all: List<InstalledJdk>): InstalledJdk? =
        all.firstOrNull { it.supported && it.source == "Gradle" && it.major == 17 }
            ?: all.firstOrNull { it.supported }

    private fun writeGradleProperties(javaHome: File) {
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        GradleUserProperties.ensure(
            File(context.filesDir, "gradle/home"),
            platform.buildToolsAapt2(),
            javaHome.takeIf { File(it, "bin/java").isFile },
        )
    }

    private fun sourceLabel(home: File): String {
        val path = home.absolutePath
        return when {
            path.startsWith(File(context.dataDir, "file/g").absolutePath) -> "Gradle"
            path.startsWith(File(context.dataDir, "file/j").absolutePath) -> "Optional"
            path.startsWith(File(context.dataDir, "file/u").absolutePath) -> "Shell tools"
            else -> "Installed"
        }
    }

    private fun readVersion(home: File): Pair<String, Int> {
        val release = File(home, "release")
        val version = if (release.isFile) {
            runCatching {
                Regex("JAVA_VERSION=\"([^\"]+)\"").find(release.readText())
                    ?.groupValues?.getOrNull(1)
            }.getOrNull()
        } else {
            null
        } ?: Regex("java-(\\d+)").find(home.name)?.groupValues?.getOrNull(1).orEmpty()
        val major = if (version.startsWith("1.")) {
            version.removePrefix("1.").substringBefore('.').toIntOrNull()
        } else {
            version.substringBefore('.').toIntOrNull()
        } ?: 0
        return version.ifBlank { "unknown" } to major
    }

    companion object {
        private const val KEY = "selected_jdk_path"

        fun selected(context: Context): InstalledJdk? = JdkStore(context).selected()
    }
}
