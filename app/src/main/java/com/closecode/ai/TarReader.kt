package com.closecode.ai

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

/**
 * A tar reader, just enough of one.
 *
 * OpenCode ships as a `.tar.gz`, and Android has a gunzip but no untar. The format is simple: a
 * stream of 512-byte headers, each followed by the file's bytes padded to a 512-byte boundary.
 * The header carries the name, the size in octal and the type flag; a name longer than 100 bytes
 * is continued in a GNU long-name entry, which this reader follows.
 *
 * Three ways of reading, because the callers want different things:
 *
 *  - [extractFirstFile] writes the first file straight to disk through a small buffer. The
 *    program it unpacks is close to two hundred megabytes, which is more than the app may hold
 *    in memory, so nothing is ever assembled in a byte array. This is the path the app runs on
 *    for the program itself.
 *  - [extractInto] writes *every* file into a directory, which is what a package of libraries
 *    needs: a symlink's name has to end up there under that exact name, so a link becomes a
 *    copy of the file it points at.
 *  - [entries] keeps every file in memory, which is only right for the small archives the tests
 *    build. It is kept for exactly that.
 *
 * Only regular files are taken. Directories, symlinks and everything else are skipped, so a
 * tarball with a stray symlink cannot put a link where a program is expected.
 */
object TarReader {

    private const val BLOCK = 512
    private const val BUFFER = 64 * 1024

    /** The suffix a file wears while it is being written, before it is real. */
    private const val PART = ".part"

    /** A long-name entry holds one path. More than this is not a path, and is skipped. */
    private const val MAX_NAME = 64 * 1024

    /** End of a header string. Written as a value rather than an escape, to keep the source plain. */
    private val NUL = 0.toChar()

    /** One regular file inside the archive. */
    data class Entry(val name: String, val size: Long)

    /**
     * The first regular file in a gzipped tar, written into [into] as it arrives.
     *
     * The bytes go to `name.part` first and are renamed into place when they are all there, so
     * an interrupted unpack leaves no half-written program that a later start would run.
     *
     * The entry that was written, or null when the archive holds no regular file.
     */
    fun extractFirstFile(gzip: InputStream, into: File): Entry? {
        GZIPInputStream(gzip.buffered()).use { gz ->
            val header = ByteArray(BLOCK)
            var longName: String? = null
            while (true) {
                if (!readFully(gz, header)) break
                if (isAllZero(header)) break

                val name = longName ?: readString(header, 0, 100)
                val size = readOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                longName = null

                when (type) {
                    'L' -> longName = readLongName(gz, size)

                    '0', NUL, '7' -> {
                        if (size > 0) {
                            val directory = into.parentFile
                            directory?.mkdirs()
                            val part = File(directory, into.name + PART)
                            FileOutputStream(part).use { out -> copyExactly(gz, size, out) }
                            if (into.exists()) into.delete()
                            if (!part.renameTo(into)) {
                                part.copyTo(into, overwrite = true)
                                part.delete()
                            }
                            return Entry(name, size)
                        }
                        skipRaw(gz, blocksFor(size))
                    }

                    else -> skipRaw(gz, blocksFor(size))
                }
            }
        }
        return null
    }

    /**
     * Every file in a gzipped tar, written into the directory [into].
     *
     * This is how a package of libraries is unpacked. An Alpine package is a tar of `lib/` and
     * `usr/lib/`, and its symlink names matter — `libstdc++.so.6` has to be there under that
     * exact name or the loader will not find it — so a link becomes a copy of the file it points
     * at, and nothing depends on the phone's file system allowing links at all.
     *
     * A name that tries to get out of [into] is refused, a file is written to a `.part` and
     * renamed into place, and the package's own metadata (`.PKGINFO`, `.SIGN.*`) is passed by —
     * it is about the package, not about the file system.
     *
     * The names that ended up there, files and links alike.
     */
    fun extractInto(gzip: InputStream, into: File): List<String> {
        val names = ArrayList<String>()
        val links = ArrayList<Pair<String, String>>()
        into.mkdirs()
        GZIPInputStream(gzip.buffered()).use { gz ->
            val header = ByteArray(BLOCK)
            var longName: String? = null
            while (true) {
                if (!readFully(gz, header)) break
                if (isAllZero(header)) break

                val name = longName ?: readString(header, 0, 100)
                val size = readOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                longName = null

                when (type) {
                    'L' -> longName = readLongName(gz, size)

                    '5' -> {
                        val directory = inside(into, File(into, name))
                        if (directory != null && !name.startsWith(".")) directory.mkdirs()
                        skipRaw(gz, blocksFor(size))
                    }

                    '0', NUL, '7' -> {
                        val file = inside(into, File(into, name))
                        if (file == null || name.startsWith(".")) {
                            skipRaw(gz, blocksFor(size))
                        } else {
                            file.parentFile?.mkdirs()
                            val part = File(file.parentFile, file.name + PART)
                            FileOutputStream(part).use { out -> copyExactly(gz, size, out) }
                            // The file's bytes are padded to a whole block, and that padding is
                            // not part of the file — reading on has to step over it, or the next
                            // header is read from the wrong place.
                            skipRaw(gz, blocksFor(size) - size)
                            if (file.exists()) file.delete()
                            if (!part.renameTo(file)) {
                                part.copyTo(file, overwrite = true)
                                part.delete()
                            }
                            names += name
                        }
                    }

                    // A symlink's target is stored where the file's bytes would be, at 157.
                    '2' -> {
                        links += name to readString(header, 157, 100)
                        skipRaw(gz, blocksFor(size))
                    }

                    else -> skipRaw(gz, blocksFor(size))
                }
            }
        }

        // A link is only a name, so it becomes a copy of whatever it points at — which by now
        // is on the disk, wherever in the archive it came.
        for ((name, target) in links) {
            if (name.startsWith(".") || target.isBlank()) continue
            val link = inside(into, File(into, name)) ?: continue
            val points = inside(into, File(link.parentFile, target)) ?: continue
            if (points.path == link.path || !points.isFile) continue
            link.parentFile?.mkdirs()
            points.copyTo(link, overwrite = true)
            names += name
        }
        return names
    }

    /**
     * Where [file] really is inside [into], or null when the name would get out.
     *
     * Canonical paths, so `..`, a name that is absolute and a link already on the phone are all
     * answered the same way: a package cannot write outside the directory it was handed.
     */
    private fun inside(into: File, file: File): File? {
        val base = runCatching { into.canonicalFile }.getOrNull() ?: return null
        val path = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (path.path != base.path && !path.path.startsWith(base.path + File.separator)) return null
        return path
    }

    /**
     * The regular files in a gzipped tar, in archive order, held in memory.
     *
     * Only for archives small enough to hold: the OpenCode release is not one of them, and
     * [extractFirstFile] is what takes that apart.
     */
    fun entries(gzip: InputStream): List<Pair<Entry, ByteArray>> {
        val out = ArrayList<Pair<Entry, ByteArray>>()
        GZIPInputStream(gzip.buffered()).use { gz ->
            val header = ByteArray(BLOCK)
            var longName: String? = null
            while (true) {
                if (!readFully(gz, header)) break
                if (isAllZero(header)) break

                val name = longName ?: readString(header, 0, 100)
                val size = readOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                longName = null

                val data = ByteArray(blocksFor(size).toInt())
                readFully(gz, data)

                when (type) {
                    'L' -> longName = String(data, 0, size.toInt().coerceAtMost(data.size), Charsets.UTF_8)
                        .trimEnd(NUL)

                    '0', NUL, '7' -> {
                        if (size > 0) {
                            out += Entry(name, size) to data.copyOf(size.toInt())
                        }
                    }

                    else -> Unit
                }
            }
        }
        return out
    }

    /**
     * The first regular file in a gzipped tar, held in memory, or null.
     *
     * The OpenCode tarball holds the program itself, so the first regular file is the one to
     * run — but it is far too big to hold, which is why the app unpacks it with
     * [extractFirstFile] instead. This one exists so the format has a simple reader to test.
     */
    fun firstFile(gzip: InputStream): Pair<Entry, ByteArray>? = entries(gzip).firstOrNull()

    /** Reads exactly [count] bytes, or fails. */
    private fun readFully(input: InputStream, into: ByteArray): Boolean {
        var read = 0
        while (read < into.size) {
            val n = input.read(into, read, into.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    /** Copies exactly [size] bytes, in pieces no larger than the buffer. */
    private fun copyExactly(input: InputStream, size: Long, out: OutputStream) {
        var left = size
        val buffer = ByteArray(BUFFER)
        while (left > 0) {
            val want = minOf(left, buffer.size.toLong()).toInt()
            val n = input.read(buffer, 0, want)
            if (n < 0) break
            out.write(buffer, 0, n)
            left -= n
        }
    }

    /** Reads past the rest of an entry, its padding included, without keeping it. */
    private fun skipRaw(input: InputStream, count: Long) {
        var left = count
        val buffer = ByteArray(BUFFER)
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (n < 0) return
            left -= n
        }
    }

    /**
     * A GNU long name: the name of the next entry, in this entry's bytes, padded to a block.
     *
     * Guarded by [MAX_NAME] so a header that claims an enormous name cannot ask for an
     * enormous allocation; such an entry is skipped instead of being read.
     */
    private fun readLongName(input: InputStream, size: Long): String? {
        val total = blocksFor(size)
        if (size <= 0 || total > MAX_NAME + BLOCK) {
            skipRaw(input, total)
            return null
        }
        val bytes = ByteArray(total.toInt())
        if (!readFully(input, bytes)) return null
        return String(bytes, 0, size.toInt().coerceAtMost(bytes.size), Charsets.UTF_8).trimEnd(NUL)
    }

    /** Bytes an entry takes in the archive: its size, padded up to a whole block. */
    private fun blocksFor(size: Long): Long = ((size + BLOCK - 1) / BLOCK) * BLOCK

    private fun isAllZero(header: ByteArray): Boolean = header.all { it.toInt() == 0 }

    private fun readString(header: ByteArray, offset: Int, length: Int): String =
        String(header, offset, length, Charsets.UTF_8).trimEnd(NUL).substringBefore(NUL)

    private fun readOctal(header: ByteArray, offset: Int, length: Int): Long {
        val text = String(header, offset, length, Charsets.UTF_8).trim(NUL, ' ')
        if (text.isEmpty()) return 0
        return text.toLongOrNull(8) ?: 0
    }
}

/** Not used at runtime; kept so the gzip path has one obvious entry point in tests. */
internal fun gzipBytes(data: ByteArray): ByteArray =
    ByteArrayOutputStream().use { out ->
        java.util.zip.GZIPOutputStream(out).use { it.write(data) }
        out.toByteArray()
    }
