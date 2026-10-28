package com.closecode.tools

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import org.tukaani.xz.XZInputStream

/**
 * A Debian package is an ar archive. The shell-tools installer only needs its
 * `data.tar.*` member; control scripts are not run, because this is not a system
 * package manager and an app cannot execute maintainer scripts as root.
 */
object DebArchive {

    fun dataTar(deb: ByteArray): InputStream {
        val member = findMember(deb, "data.tar")
            ?: throw IllegalArgumentException("The package has no data.tar member.")
        val raw = ByteArrayInputStream(deb, member.first, member.second)
        return when {
            member.third.endsWith(".xz") -> XZInputStream(raw)
            member.third.endsWith(".gz") -> GZIPInputStream(raw)
            member.third.endsWith(".tar") -> raw
            else -> throw IllegalArgumentException("Unsupported package compression: ${member.third}")
        }
    }

    /** (offset, length, name) of the first ar member whose name starts with [prefix]. */
    internal fun findMember(deb: ByteArray, prefix: String): Triple<Int, Int, String>? {
        if (deb.size < 8 || String(deb, 0, 8, Charsets.US_ASCII) != "!<arch>\n") {
            throw IllegalArgumentException("The file is not a Debian package.")
        }
        var offset = 8
        while (offset + 60 <= deb.size) {
            val name = String(deb, offset, 16, Charsets.US_ASCII).trim().trimEnd('/')
            val sizeText = String(deb, offset + 48, 10, Charsets.US_ASCII).trim()
            val size = sizeText.toIntOrNull()
                ?: throw IllegalArgumentException("The package has a broken member header.")
            val start = offset + 60
            if (start + size > deb.size) {
                throw IllegalArgumentException("The package member is truncated.")
            }
            if (name.startsWith(prefix)) return Triple(start, size, name)
            offset = start + size + (size and 1)
        }
        return null
    }
}
