package com.closecode.build

import com.google.gson.JsonParser
import java.io.File

/**
 * One library in the on-device cache: its classes, its Android resources and the
 * manifest it shipped with. The pack script extracts these from each AAR once, on the
 * developer's PC; the phone only reads them.
 */
data class CachedLibrary(
    val module: String,
    val version: String,
    val jar: File?,
    val res: File?,
    val manifest: File?,
) {
    /** "androidx.core:core-ktx:1.13.1", the form the project check compares. */
    val coordinate: String get() = "$module:$version"
}

/**
 * The library cache the build resolves declarations against. [modules] holds the
 * module coordinates the cache can satisfy, including the transitive libraries each
 * supported dependency needs. A declaration not in the cache is named by the project
 * check, never guessed at.
 *
 * `libs.json` is written by scripts/prepare-toolchain.ps1. A missing or damaged index
 * is an empty cache, which makes the build say "libraries are missing", not crash.
 */
class LibraryIndex(
    val root: File,
    val libraries: List<CachedLibrary>,
    /** The library classes, already dexed on the PC: classes.dex, classes2.dex, ... */
    val dexFiles: List<File> = emptyList(),
) {
    val jars: List<File> get() = libraries.mapNotNull { it.jar }
    val resDirs: List<File> get() = libraries.mapNotNull { it.res }

    /** Every "module:version" the check may accept from this cache. */
    val cacheModules: Set<String> get() = libraries.map { it.coordinate }.toSet()

    /** "androidx.compose.ui:ui" also matches the published "ui-android" module. */
    fun find(module: String, version: String?): CachedLibrary? {
        val names = listOf(module, "$module-android")
        return libraries.firstOrNull { it.module in names && (version == null || it.version == version) }
            ?: libraries.firstOrNull { it.module in names && version == null }
    }

    companion object {
        fun load(root: File): LibraryIndex? {
            val file = File(root, "libs.json")
            if (!file.isFile) return null
            return runCatching {
                val json = JsonParser.parseString(file.readText()).asJsonObject
                val modules = json.getAsJsonArray("modules").map { it.asJsonObject }.mapNotNull { obj ->
                    fun text(key: String): String? =
                        obj.get(key)?.takeIf { !it.isJsonNull }?.asString
                    fun entryFile(key: String): File? =
                        text(key)?.let { File(root, it) }?.takeIf { it.exists() }
                    val module = text("module") ?: return@mapNotNull null
                    CachedLibrary(
                        module = module,
                        version = text("version").orEmpty(),
                        jar = entryFile("jar"),
                        res = entryFile("res"),
                        manifest = entryFile("manifest"),
                    )
                }
                LibraryIndex(
                    root = root,
                    libraries = modules,
                    dexFiles = File(root, "dex").listFiles()
                        ?.filter { it.isFile && it.extension == "dex" }
                        ?.sortedBy { it.name }
                        .orEmpty(),
                )
            }.getOrNull()
        }
    }
}
