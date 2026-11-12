package com.closecode.control

import java.io.File

/**
 * A path the user typed.
 *
 * "A path means what the user typed": a relative path belongs to the folder the command was
 * run from, and an absolute path is exactly that path. Every command that takes a path goes
 * through here, so the next command cannot get it wrong — the bug this replaces was the same
 * relative-path mistake in three commands at once.
 */
object CommandPaths {

    /**
     * Resolves [raw] against [cwd], the folder the command was run from.
     *
     * [cwd] is what the shell reported. When it is missing — a command run from somewhere
     * that cannot report a folder — the process's own folder is used.
     */
    fun resolve(raw: String, cwd: String?): File {
        val text = raw.trim()
        val file = File(text)
        if (file.isAbsolute) return file.absoluteFile
        val base = cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }
            ?: File(System.getProperty("user.dir") ?: "/")
        return File(base, text).absoluteFile
    }
}
