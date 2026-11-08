package com.closecode.ui.project

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * The one zip a project travels in: out of the phone, and back.
 *
 * The shape is plain: every file under the project as it lies on disk, except build output,
 * plus one `closecode-project.json` at the root that carries the facts import needs (the
 * name to file the project under, and when it left the phone). Nothing about the zip speaks
 * of Android: a person can open it with any unzip tool and read the project.
 */
object ProjectZip {

    /** The manifest at the root of every export. */
    const val MANIFEST = "closecode-project.json"

    /** Top-level or nested folder named this is build output, never exported or imported. */
    internal val SKIP_DIRS = setOf("build")

    /** Sources travel; generated folders and secrets stay on the phone. */
    fun collectFiles(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val out = ArrayList<File>()
        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles() ?: return
            for (child in children.sortedBy { it.name }) {
                val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (GeneratedPaths.isProtectedRelative(relative)) continue
                if (child.isDirectory) walk(child, relative) else out.add(child)
            }
        }
        walk(root, "")
        return out
    }

    /** The manifest a fresh export carries: name, slug, when it left, which app wrote it. */
    fun manifest(name: String, slug: String, now: Long, appLabel: String): String = JSONObject()
        .put("name", name)
        .put("slug", slug)
        .put("exportedAt", now)
        .put("app", appLabel)
        .toString()

    /** The project name the manifest proposes, or null when there is no readable manifest. */
    fun nameOf(manifestJson: String?): String? = runCatching {
        JSONObject(manifestJson.orEmpty()).optString("name").takeIf { it.isNotBlank() }
    }.getOrNull()

    /** The manifest's text from a zip's start, or null when nothing was readable. */
    fun manifestFromZip(input: InputStream): String? = runCatching {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return@use null
                if (File(entry.name).name == MANIFEST) {
                    val text = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = zip.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > 1_000_000) return@use null
                        text.write(chunk, 0, read)
                    }
                    return@use text.toString(Charsets.UTF_8.name())
                }
                zip.closeEntry()
            }
            null
        }
    }.getOrNull()

    /**
     * The path a zip entry may extract to, relative to the target: null for anything that
     * tries to leave it — absolute paths, `..` climbs, empty names. This is the zip-slip
     * guard: one hostile entry must not turn one import into a breach.
     */
    internal fun safeEntryPath(entry: String): String? {
        if (entry.isBlank() || entry.startsWith("/")) return null
        val clean = entry.replace('\\', '/').split('/')
            .filter { it.isNotBlank() && it != "." }
        if (clean.isEmpty()) return null
        // Climbs out of the folder, generated folders and secrets of any depth are refused;
        // they are skipped on the way out too.
        if (clean.any { it == ".." || it in SKIP_DIRS }) return null
        val relative = clean.joinToString("/")
        if (GeneratedPaths.isProtectedRelative(relative)) return null
        return relative
    }

    /** Writes the project at [root] into [out]: the manifest first, then every collected file. */
    fun write(root: File, manifest: String, out: OutputStream): Int = out.use { stream ->
        val zip = ZipOutputStream(stream)
        var count = 0
        fun entry(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        entry(MANIFEST, manifest.toByteArray())
        for (file in collectFiles(root)) {
            val relative = file.relativeToOrNull(root)?.path ?: continue
            if (relative.endsWith(MANIFEST)) continue
            entry(relative, file.readBytes())
            count++
        }
        zip.finish()
        count
    }

    /** True for names the export cannot own. */
    private fun isManifest(name: String) = name == MANIFEST

    /**
     * Extracts every safe entry into [target] (only [safeEntryPath] ones make it) and returns
     * the count of files written; 0 for a valid zip that carried nothing; -1 when the input
     * was not a zip at all.
     */
    fun extract(input: InputStream, target: File): Int {
        target.mkdirs()
        if (!target.isDirectory) return -1
        var count = 0
        return try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val relative = safeEntryPath(entry.name)
                    if (relative == null || isManifest(File(entry.name).name)) {
                        zip.closeEntry()
                        continue
                    }
                    val destination = File(target, relative)
                    if (entry.isDirectory) {
                        destination.mkdirs()
                    } else if (runCatching {
                            destination.parentFile?.mkdirs()
                            destination.outputStream().use { zip.copyTo(it) }
                        }.isSuccess
                    ) {
                        count++
                    }
                    zip.closeEntry()
                }
                count
            }
        } catch (_: java.util.zip.ZipException) {
            -1
        } catch (_: java.io.EOFException) {
            -1
        }
    }
}
