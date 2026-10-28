package com.closecode.device

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports a project or a single file into a directory the app can share. Directories are
 * zipped without their generated build output, so an export is source, not a copy of the
 * toolchain's work. A file is copied as-is, which makes exporting a finished APK one step.
 */
object ExportHelper {

    private val skippedRoots = setOf("build", ".gradle", ".git")

    fun export(source: File, outputDir: File, name: String? = null): File {
        require(source.exists()) { "There is nothing to export at ${source.path}." }
        outputDir.mkdirs()
        val base = (name ?: source.name).ifBlank { "export" }
        if (source.isFile) {
            val target = unique(outputDir, base)
            source.copyTo(target)
            return target
        }
        val target = unique(outputDir, "$base.zip")
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            source.walkTopDown()
                .filter { it != source && !skipped(it, source) }
                .forEach { file ->
                    val relative = file.relativeTo(source).invariantSeparatorsPath
                    if (relative.isBlank()) return@forEach
                    if (file.isDirectory) {
                        zip.putNextEntry(ZipEntry("$relative/"))
                        zip.closeEntry()
                    } else if (file.isFile) {
                        zip.putNextEntry(ZipEntry(relative))
                        file.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
        }
        return target
    }

    private fun skipped(file: File, root: File): Boolean {
        val relative = file.relativeTo(root).invariantSeparatorsPath
        return relative.substringBefore('/') in skippedRoots
    }

    private fun unique(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = File(dir, "$stem-$index$suffix")
            if (!candidate.exists()) return candidate
            index++
        }
    }
}
