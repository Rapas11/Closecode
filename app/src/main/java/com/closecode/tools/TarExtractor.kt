package com.closecode.tools

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Extracts the `usr` tree from a Termux package's data tar. It understands the
 * GNU/pax headers the repository actually emits (long names, pax path/size, symlinks
 * and hardlinks) and refuses any entry that would land outside the prefix.
 */
class TarExtractor(
    private val sourcePrefix: String,
    private val target: File,
    linkRoot: File = target,
) {
    private val source = sourcePrefix.trimStart('/').trimEnd('/')
    private val targetRoot = target.canonicalFile
    private val linkTarget = linkRoot.canonicalFile

    fun extract(input: InputStream) {
        targetRoot.mkdirs()
        var pendingLongName: String? = null
        var pendingLongLink: String? = null
        var pendingPax: Map<String, String> = emptyMap()

        while (true) {
            val header = readBlock(input) ?: return
            if (header.size < 512) throw IOException("The package tar has a truncated header.")
            if (header.all { it == 0.toByte() }) return

            val type = header[156].toInt().toChar()
            var name = string(header, 0, 100)
            val prefix = string(header, 345, 155)
            if (prefix.isNotEmpty()) name = "$prefix/$name"
            var link = string(header, 157, 100)
            var size = octal(header, 124, 12)
            val mode = octal(header, 100, 8)

            when (type) {
                'L' -> {
                    pendingLongName = readText(input, size)
                    skipPadding(input, size)
                    continue
                }
                'K' -> {
                    pendingLongLink = readText(input, size)
                    skipPadding(input, size)
                    continue
                }
                'x' -> {
                    pendingPax = readPax(readText(input, size))
                    skipPadding(input, size)
                    continue
                }
                'g' -> {
                    skipFully(input, padded(size))
                    continue
                }
            }

            pendingLongName?.let { name = it; pendingLongName = null }
            pendingLongLink?.let { link = it; pendingLongLink = null }
            pendingPax["path"]?.let { name = it }
            pendingPax["linkpath"]?.let { link = it }
            pendingPax["size"]?.toLongOrNull()?.let { size = it }
            pendingPax = emptyMap()

            val relative = relativePath(name)
            if (relative == null) {
                skipFully(input, padded(size))
                continue
            }
            val output = File(targetRoot, relative).canonicalFile
            if (output != targetRoot &&
                !output.path.startsWith(targetRoot.path + File.separator)
            ) {
                throw IllegalArgumentException("Unsafe path in package: $name")
            }

            when (type) {
                '5' -> {
                    output.mkdirs()
                    skipFully(input, padded(size))
                }
                '2' -> {
                    output.parentFile?.mkdirs()
                    if (output.exists() || Files.isSymbolicLink(output.toPath())) output.delete()
                    Files.createSymbolicLink(output.toPath(), File(rewriteLink(link)).toPath())
                    skipFully(input, padded(size))
                }
                '1' -> {
                    val sourceFile = File(targetRoot, relativePath(link) ?: link).canonicalFile
                    output.parentFile?.mkdirs()
                    if (output.exists()) output.delete()
                    runCatching {
                        Files.createLink(output.toPath(), sourceFile.toPath())
                    }.onFailure {
                        sourceFile.copyTo(output, overwrite = true)
                    }
                    skipFully(input, padded(size))
                }
                '0', '\u0000', '7' -> {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { sink -> copy(input, sink, size) }
                    skipPadding(input, size)
                    if (mode and 0b001001001L != 0L) output.setExecutable(true, false)
                }
                else -> skipFully(input, padded(size))
            }
        }
    }

    /** Null for an entry outside the package's `usr` tree. */
    private fun relativePath(name: String): String? {
        val normalized = name.replace('\\', '/').trimStart('/').removePrefix("./")
        // An empty prefix means the caller wants the whole archive, not a Termux tree.
        if (source.isEmpty()) return normalized
        return when {
            normalized == source -> ""
            normalized.startsWith("$source/") -> normalized.removePrefix("$source/")
            else -> null
        }
    }

    private fun rewriteLink(link: String): String {
        val normalized = link.trimStart('/')
        val relative = when {
            normalized == source -> ""
            normalized.startsWith("$source/") -> normalized.removePrefix("$source/")
            else -> return link
        }
        return File(linkTarget, relative).absolutePath
    }

    private fun readBlock(input: InputStream): ByteArray? {
        val block = ByteArray(512)
        var read = 0
        while (read < block.size) {
            val count = input.read(block, read, block.size - read)
            if (count < 0) return if (read == 0) null else block.copyOf(read)
            read += count
        }
        return block
    }

    private fun string(block: ByteArray, offset: Int, length: Int): String =
        String(block, offset, length, StandardCharsets.UTF_8).trimEnd('\u0000', ' ')

    private fun octal(block: ByteArray, offset: Int, length: Int): Long {
        val text = String(block, offset, length, StandardCharsets.US_ASCII)
            .trim { it == ' ' || it == '\u0000' }
        return text.toLongOrNull(8) ?: 0L
    }

    private fun readText(input: InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        var read = 0
        while (read < bytes.size) {
            val count = input.read(bytes, read, bytes.size - read)
            if (count < 0) throw IOException("The package tar is truncated.")
            read += count
        }
        return String(bytes, 0, read, StandardCharsets.UTF_8).trimEnd('\u0000', '\n')
    }

    private fun readPax(text: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        var index = 0
        while (index < text.length) {
            val space = text.indexOf(' ', index)
            if (space < 0) break
            val length = text.substring(index, space).toIntOrNull() ?: break
            if (length <= 0 || index + length > text.length) break
            val record = text.substring(space + 1, index + length).trimEnd('\n')
            val equals = record.indexOf('=')
            if (equals > 0) values[record.substring(0, equals)] = record.substring(equals + 1)
            index += length
        }
        return values
    }

    private fun padded(size: Long): Long = (size + 511L) and 511L.inv()

    private fun skipPadding(input: InputStream, size: Long) {
        skipFully(input, padded(size) - size)
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else if (input.read() < 0) {
                throw IOException("The package tar is truncated.")
            } else {
                left--
            }
        }
    }

    private fun copy(input: InputStream, output: java.io.OutputStream, size: Long) {
        var left = size
        val buffer = ByteArray(64 * 1024)
        while (left > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (count < 0) throw IOException("The package tar is truncated.")
            output.write(buffer, 0, count)
            left -= count
        }
    }
}
