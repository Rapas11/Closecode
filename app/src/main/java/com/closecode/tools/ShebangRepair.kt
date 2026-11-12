package com.closecode.tools

import java.io.File

/**
 * Makes shell scripts in a build-tools tree runnable on Android.
 *
 * Google's build-tools ship scripts that start with `#!/bin/bash`, and Android has no bash:
 * `apksigner` then fails with "No such file or directory". The scripts are plain POSIX
 * shell, so the interpreter line is rewritten to the one Android has. Every installed
 * version is repaired, not only the one the app installed, so a build-tools directory left
 * behind by an older build stops breaking the build.
 */
object ShebangRepair {

    private const val BASH = "bash"
    private val ANDROID_SHELL = "#!/system/bin/sh"

    /** Rewrites every script that asks for bash. Returns how many files were changed. */
    fun repair(root: File): Int {
        if (!root.isDirectory) return 0
        var fixed = 0
        root.walkTopDown()
            .filter { it.isFile && it.length() < 64_000L }
            .forEach { file ->
                if (rewrite(file)) fixed++
            }
        return fixed
    }

    /** Rewrites one file's interpreter line, if it asks for bash. */
    fun rewrite(file: File): Boolean {
        if (!file.isFile) return false
        // The caller only offers small files, so the whole script can be read at once and the
        // body kept exactly as it was.
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        val newline = bytes.indexOf('\n'.code.toByte())
        val firstLine = if (newline >= 0) {
            String(bytes, 0, newline, Charsets.ISO_8859_1)
        } else {
            String(bytes, Charsets.ISO_8859_1)
        }
        if (!firstLine.startsWith("#!")) return false
        if (!firstLine.contains(BASH)) return false
        val rest = if (newline >= 0) bytes.copyOfRange(newline + 1, bytes.size) else ByteArray(0)
        return runCatching {
            val out = java.io.ByteArrayOutputStream()
            out.write(ANDROID_SHELL.toByteArray(Charsets.ISO_8859_1))
            out.write('\n'.code)
            out.write(rest)
            file.writeBytes(out.toByteArray())
            file.setExecutable(true, false)
            true
        }.getOrDefault(false)
    }
}
