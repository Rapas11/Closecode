package com.closecode.tools

import android.content.Context
import com.google.gson.JsonParser
import java.io.File

/**
 * The optional shell-tools pack. Unlike the build toolchain it is not bundled or
 * re-hosted: the app resolves the current Termux packages for its ABI and installs
 * them under its own files directory. The manifest only fixes the source and the
 * commands the app promises to provide.
 */
data class ShellToolsManifest(
    val version: String,
    val repo: String,
    val roots: List<String>,
    val required: List<String>,
) {
    companion object {
        fun load(context: Context): ShellToolsManifest =
            runCatching {
                context.assets.open("shell-tools.json").bufferedReader().use { it.readText() }
            }.mapCatching { parse(it) }.getOrElse { empty() }

        fun parse(json: String): ShellToolsManifest {
            val root = JsonParser.parseString(json).asJsonObject
            return ShellToolsManifest(
                version = root.get("version")?.asString ?: "0",
                repo = root.get("repo")?.asString?.trimEnd('/') ?: "",
                roots = root.getAsJsonArray("roots")?.mapNotNull {
                    runCatching { it.asString }.getOrNull()
                }.orEmpty(),
                required = root.getAsJsonArray("required")?.mapNotNull {
                    runCatching { it.asString }.getOrNull()
                }.orEmpty(),
            )
        }

        fun empty(): ShellToolsManifest = ShellToolsManifest("0", "", emptyList(), emptyList())
    }
}

/** The Termux repository layout for an Android ABI. */
object ShellToolsRepo {
    fun architecture(abis: List<String>): String? = abis.firstNotNullOfOrNull { abi ->
        when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "arm"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "i686"
            else -> null
        }
    }

    fun indexUrl(repo: String, architecture: String): String =
        "$repo/dists/stable/main/binary-$architecture/Packages"

    fun packageUrl(repo: String, fileName: String): String =
        "$repo/$fileName"

    fun prefixFor(packageName: String): String = "/data/data/$packageName/file/u"

    fun homeFor(packageName: String): String = "/data/data/$packageName/file/hh"

    fun workingPrefix(dataDir: File): File = File(dataDir, "file/u")

    fun workingHome(dataDir: File): File = File(dataDir, "file/hh")
}
