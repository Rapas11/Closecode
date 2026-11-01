package com.closecode.tools

import java.io.File
import java.nio.file.Files

/**
 * Termux binaries are built with `/data/data/com.termux/files/usr` compiled into
 * them. CloseCode installs the same packages under its own data directory, so the
 * strings are rewritten in place. The replacement has exactly the same byte length:
 * `/data/data/com.closecode/file/u` is the same 31 bytes as the Termux prefix.
 */
class TermuxPrefix(
    packageName: String,
    prefixName: String = "u",
    homeName: String = "hh",
) {
    val oldPrefix = "/data/data/com.termux/files/usr"
    val newPrefix = ShellToolsRepo.prefixFor(packageName, prefixName)
    val oldHome = "/data/data/com.termux/files/home"
    val newHome = ShellToolsRepo.homeFor(packageName, homeName)

    init {
        require(oldPrefix.length == newPrefix.length) {
            "The app's data path cannot replace the Termux prefix without changing binary offsets."
        }
        require(oldHome.length == newHome.length) {
            "The app's home path cannot replace the Termux home without changing binary offsets."
        }
    }

    fun patch(root: File) {
        root.walkTopDown()
            .onEnter { !Files.isSymbolicLink(it.toPath()) }
            .forEach { file ->
                if (!file.isFile || Files.isSymbolicLink(file.toPath())) return@forEach
                if (file.length() > MAX_PATCH_BYTES) return@forEach
                val before = file.readBytes()
                val after = patchBytes(before)
                if (!after.contentEquals(before)) {
                    val executable = file.canExecute()
                    file.writeBytes(after)
                    if (executable) file.setExecutable(true, false)
                }
            }
    }

    fun patchBytes(input: ByteArray): ByteArray {
        val bytes = input.copyOf()
        val prefix = oldPrefix.toByteArray(Charsets.UTF_8)
        val home = oldHome.toByteArray(Charsets.UTF_8)
        val replacement = newPrefix.toByteArray(Charsets.UTF_8)
        val homeReplacement = newHome.toByteArray(Charsets.UTF_8)
        var index = 0
        while (index <= bytes.size - prefix.size) {
            if (matches(bytes, index, prefix)) {
                replacement.copyInto(bytes, index)
                index += prefix.size
            } else {
                index++
            }
        }
        index = 0
        while (index <= bytes.size - home.size) {
            if (matches(bytes, index, home)) {
                homeReplacement.copyInto(bytes, index)
                index += home.size
            } else {
                index++
            }
        }
        return bytes
    }

    private fun matches(bytes: ByteArray, offset: Int, needle: ByteArray): Boolean {
        if (offset + needle.size > bytes.size) return false
        for (i in needle.indices) if (bytes[offset + i] != needle[i]) return false
        return true
    }

    private companion object {
        const val MAX_PATCH_BYTES = 32L * 1024 * 1024
    }
}
