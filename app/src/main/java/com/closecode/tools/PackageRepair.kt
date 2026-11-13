package com.closecode.tools

import java.io.File
import java.nio.file.Files

/**
 * Makes an unpacked package runnable on this phone.
 *
 * Two things go wrong when a package built for a desktop is unpacked on Android:
 *
 * 1. **A zip cannot always carry a symlink.** The link then arrives as a small file whose
 *    whole content is the name of the file it points at. The NDK's `clang` is one of these:
 *    it is an eight byte text file that says `clang-18`. Left alone, every command in the
 *    NDK fails with "No such file or directory".
 * 2. **The wrappers ask for bash.** `#!/usr/bin/env bash` cannot run on Android, which has
 *    no bash. The NDK ships 123 such wrappers.
 *
 * Both are fixed here, so every SDK component gets the same treatment instead of each one
 * remembering its own.
 */
object PackageRepair {

    data class Report(val symlinks: Int, val shebangs: Int) {
        val changed: Boolean get() = symlinks > 0 || shebangs > 0
    }

    /** Repairs a package that has been unpacked. */
    fun repair(root: File): Report {
        val symlinks = restoreSymlinks(root)
        val shebangs = ShebangRepair.repair(root)
        return Report(symlinks, shebangs)
    }

    /**
     * Turns the files that stand in for symlinks back into symlinks.
     *
     * A file is one of those when it is executable, small, holds a single line, and that
     * line names a file next to it. Anything else is left exactly as it is.
     */
    fun restoreSymlinks(root: File): Int {
        if (!root.isDirectory) return 0
        var fixed = 0
        root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    !Files.isSymbolicLink(file.toPath()) &&
                    file.canExecute() &&
                    file.length() in 1L..256L
            }
            .forEach { file ->
                val text = runCatching { file.readText() }.getOrNull()?.trim() ?: return@forEach
                if (text.isEmpty() || text.contains('\n') || text.contains('/')) return@forEach
                if (text == file.name) return@forEach
                val target = File(file.parentFile, text)
                if (!target.exists()) return@forEach
                runCatching {
                    file.delete()
                    // The link is relative, the way the archive had it: the package can be
                    // moved and the links still work.
                    Files.createSymbolicLink(file.toPath(), java.nio.file.Path.of(text))
                    fixed++
                }
            }
        return fixed
    }
}
