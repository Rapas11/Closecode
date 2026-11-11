package com.closecode.tools

import java.io.File
import java.nio.file.Files

/**
 * Bytes used by a directory tree, without following symlinks.
 *
 * Android NDK and CMake trees contain links such as `sysroot/usr/lib -> ...`. Counting the
 * links as directories walks the same files twice, which made Native tools report 2x its
 * real `du` size.
 */
object DiskUsage {
    fun bytes(root: File): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown()
            .onEnter { !Files.isSymbolicLink(it.toPath()) }
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .sumOf { it.length() }
    }
}
