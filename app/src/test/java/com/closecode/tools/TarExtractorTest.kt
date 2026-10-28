package com.closecode.tools

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TarExtractorTest {

    @Test
    fun extractsFilesDirectoriesSymlinksAndModes() {
        val root = Files.createTempDirectory("tar-extract").toFile()
        try {
            val tar = tar(
                entry("./data/data/com.termux/files/usr/", type = '5'),
                entry("./data/data/com.termux/files/usr/bin/", type = '5'),
                entry(
                    "./data/data/com.termux/files/usr/bin/hello",
                    "#!/bin/sh\necho /data/data/com.termux/files/usr\n",
                    mode = 0b111101101,
                ),
                entry(
                    "./data/data/com.termux/files/usr/bin/hello-link",
                    "",
                    type = '2',
                    link = "hello",
                ),
            )

            TarExtractor("data/data/com.termux/files/usr", root)
                .extract(ByteArrayInputStream(tar))

            val hello = File(root, "bin/hello")
            assertEquals("#!/bin/sh\necho /data/data/com.termux/files/usr\n", hello.readText())
            assertTrue(hello.canExecute())
            assertTrue(Files.isSymbolicLink(File(root, "bin/hello-link").toPath()))
            assertEquals("hello", Files.readSymbolicLink(File(root, "bin/hello-link").toPath()).toString())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rewritesAbsoluteSymlinksToTheFinalInstallRoot() {
        val root = Files.createTempDirectory("tar-extract").toFile()
        val final = Files.createTempDirectory("tar-final").toFile()
        try {
            val tar = tar(
                entry(
                    "./data/data/com.termux/files/usr/bin/hello-link",
                    "",
                    type = '2',
                    link = "/data/data/com.termux/files/usr/bin/hello",
                ),
            )

            TarExtractor("data/data/com.termux/files/usr", root, final)
                .extract(ByteArrayInputStream(tar))

            assertEquals(
                File(final, "bin/hello").absolutePath,
                Files.readSymbolicLink(File(root, "bin/hello-link").toPath()).toString(),
            )
        } finally {
            root.deleteRecursively()
            final.deleteRecursively()
        }
    }

    @Test
    fun ignoresFilesOutsideTheTermuxTree() {
        val root = Files.createTempDirectory("tar-outside").toFile()
        try {
            val tar = tar(entry("./data/data/other/file", "no"))
            TarExtractor("data/data/com.termux/files/usr", root)
                .extract(ByteArrayInputStream(tar))

            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesAPathThatEscapesTheTarget() {
        val root = Files.createTempDirectory("tar-escape").toFile()
        try {
            val tar = tar(entry("./data/data/com.termux/files/usr/../../evil", "no"))
            assertThrows(IllegalArgumentException::class.java) {
                TarExtractor("data/data/com.termux/files/usr", root)
                    .extract(ByteArrayInputStream(tar))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun tar(vararg entries: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEach { out.write(it) }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    private fun entry(
        name: String,
        content: String = "",
        type: Char = '0',
        mode: Int = 0b110100100,
        link: String = "",
    ): ByteArray {
        val data = content.toByteArray()
        val header = ByteArray(512)
        put(header, 0, name)
        put(header, 100, "%07o\u0000".format(mode))
        put(header, 108, "0000000\u0000")
        put(header, 116, "0000000\u0000")
        put(header, 124, "%011o\u0000".format(data.size))
        put(header, 136, "00000000000\u0000")
        for (i in 148..155) header[i] = ' '.code.toByte()
        header[156] = type.code.toByte()
        put(header, 157, link)
        put(header, 257, "ustar\u0000")
        put(header, 263, "00")
        val checksum = header.sumOf { it.toInt() and 0xFF }
        put(header, 148, "%06o\u0000 ".format(checksum))
        val out = ByteArrayOutputStream()
        out.write(header)
        out.write(data)
        val padding = (512 - data.size % 512) % 512
        out.write(ByteArray(padding))
        return out.toByteArray()
    }

    private fun put(target: ByteArray, offset: Int, value: String) {
        value.toByteArray().copyInto(target, offset, endIndex = minOf(value.length, target.size - offset))
    }
}
