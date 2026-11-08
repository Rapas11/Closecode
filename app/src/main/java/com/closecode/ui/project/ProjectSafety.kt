package com.closecode.ui.project

import java.io.File
import java.io.IOException

internal object ProjectSafety {

    private val SecretPatterns = listOf(
        "local.properties",
        ".env",
        "*.jks",
        "*.keystore",
        "*.pem",
        "google-services.json",
    )

    /** Ceiling on a single file read. Past this the load would block the UI thread long enough to
     *  trip the ANR watchdog, and the editor cannot render it anyway. */
    private const val MAX_READ_BYTES = 8L * 1024 * 1024

    /** Images are decoded for Preview; a 32 MB source is already far past a phone-sized bitmap. */
    const val MAX_IMAGE_BYTES = 32L * 1024 * 1024

    fun isSecretName(name: String): Boolean {
        val base = File(name).name
        return SecretPatterns.any { glob(it, base) }
    }

    fun isHiddenName(name: String): Boolean = GeneratedPaths.isAlwaysHiddenName(name)

    /**
     * Resolves [relative] against [root] and refuses anything that escapes it,
     * including via symlink. Mirrors the safe_path check in the Python agent.
     */
    fun resolveInProject(root: File, relative: String): File {
        if (relative.isBlank()) throw ProjectPathException("Empty path.")
        if (relative.startsWith("/")) throw ProjectPathException("Absolute paths are not allowed: $relative")
        if (relative.any { it.isISOControl() }) {
            throw ProjectPathException("Illegal character in path.")
        }

        val candidate = File(root, relative)
        val canonicalRoot = root.canonicalFile
        val canonical = candidate.canonicalFile

        if (canonical != canonicalRoot && !isUnder(canonicalRoot, canonical)) {
            throw ProjectPathException("Path is outside the project: $relative")
        }
        return canonical
    }

    /**
     * A slug is a single directory name, never a path. Projects normally get one from `slugify`,
     * but a reopened project reads its slug back out of a decoded record, so it is checked before
     * it is ever used as a root — a slug of `..` would otherwise point `deleteRecursively` at the
     * whole files directory, and every containment check below is relative to that root.
     */
    fun sanitizeSlug(slug: String): String {
        if (slug.isBlank()) throw ProjectPathException("Empty project id.")
        val illegal = slug == "." || slug == ".." ||
            '/' in slug || '\\' in slug || slug.any { it.isISOControl() }
        if (illegal) throw ProjectPathException("Illegal project id.")
        return slug
    }

    fun ensureInProject(root: File, file: File) {
        val canonicalRoot = root.canonicalFile
        val canonical = file.canonicalFile
        if (canonical != canonicalRoot && !isUnder(canonicalRoot, canonical)) {
            throw ProjectPathException("Path is outside the project: ${file.name}")
        }
    }

    fun writeAtomic(file: File, content: String) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(content)
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) {
                    throw IOException("Could not replace ${file.name}")
                }
            }
        } catch (error: IOException) {
            throw IOException("Write failed for ${file.name}: ${error.message}", error)
        }
    }

    /**
     * Reads a project file, refusing anything too large to load on the main thread. `File.readText`
     * on a multi-hundred-megabyte file is an ANR with no way out, and a source file that size is
     * not something this editor can render anyway — so the caller gets a sentence instead of a hang.
     */
    fun readTextOrThrow(file: File): String {
        val size = file.length()
        if (size > MAX_READ_BYTES) {
            throw IOException("${file.name} is ${size / 1_048_576} MB. Files over 8 MB are not opened.")
        }
        return file.readText()
    }

    fun ensureWritable(target: File) {
        val name = target.name
        if (isSecretName(name)) {
            throw ProjectPathException("Refused: $name looks like a secrets file.")
        }
    }

    private fun isUnder(root: File, other: File): Boolean {
        var current: File? = other.parentFile
        while (current != null) {
            if (current == root) return true
            current = current.parentFile
        }
        return false
    }

    private fun glob(pattern: String, name: String): Boolean {
        val regex = buildString {
            append('^')
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '.' -> append("\\.")
                    else -> append(ch)
                }
            }
            append('$')
        }
        return Regex(regex).matches(name)
    }
}

class ProjectPathException(message: String) : Exception(message)