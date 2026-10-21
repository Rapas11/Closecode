package com.closecode.build

import com.closecode.ui.settings.InMemoryKeyValueStore
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolchainStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    /**
     * A zip written by a Windows archiver carries "\\" in its entry names. Linux treats that
     * as part of the name, so an unpacker that trusts it creates one flat file called
     * `dex\classes.dex` and the library index finds no dex at all. The unpacker must turn
     * both separators into real folders before the path check.
     */
    @Test
    fun windowsStyleZipEntriesUnpackIntoFolders() {
        val zip = File(tmp.root, "libraries.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("libs.json"))
            out.write("""{"version":1,"modules":[]}""".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("dex\\classes.dex"))
            out.write(byteArrayOf(0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00))
            out.closeEntry()
            out.putNextEntry(ZipEntry("jars\\001.jar"))
            out.write(byteArrayOf(0x50, 0x4b))
            out.closeEntry()
        }
        val component = ToolComponent(
            id = "libraries",
            version = "1",
            fileName = "libraries.zip",
            url = "https://example.invalid/libraries.zip",
            sizeBytes = zip.length(),
            sha256 = sha256(zip),
            license = "",
        )
        val toolchain = Toolchain(
            version = "test",
            kotlin = "1.9.24",
            composeCompiler = "1.5.14",
            compileSdk = 35,
            aapt2 = Aapt2Info("", "", ""),
            files = listOf(component),
        )
        val store = ToolchainStore(toolchain, tmp.root, InMemoryKeyValueStore())

        val cache = store.librariesDir()!!

        assertTrue("libs.json at the root", File(cache, "libs.json").isFile)
        assertTrue("dex in a folder", File(cache, "dex/classes.dex").isFile)
        assertTrue("jar in a folder", File(cache, "jars/001.jar").isFile)
        assertEquals(1, LibraryIndex.load(cache)?.dexFiles?.size)
        // The broken layout must not survive beside the good one.
        assertTrue(
            "no entry kept a backslash in its name",
            cache.walkTopDown().none { it.name.contains('\\') },
        )
    }

    /** A cache already unpacked by the old code is rebuilt instead of trusted. */
    @Test
    fun anOlderCacheLayoutIsRebuilt() {
        val root = tmp.root
        val old = File(root, "libraries").apply { mkdirs() }
        File(old, ".packed").writeText("stale")
        val zip = File(root, "libraries.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("libs.json"))
            out.write("""{"version":1,"modules":[]}""".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("dex\\classes.dex"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }
        val component = ToolComponent(
            id = "libraries",
            version = "1",
            fileName = "libraries.zip",
            url = "https://example.invalid/libraries.zip",
            sizeBytes = zip.length(),
            sha256 = sha256(zip),
            license = "",
        )
        val toolchain = Toolchain("test", "1.9.24", "1.5.14", 35, Aapt2Info("", "", ""), listOf(component))
        val store = ToolchainStore(toolchain, root, InMemoryKeyValueStore())

        val cache = store.librariesDir()!!

        assertTrue(File(cache, "dex/classes.dex").isFile)
        assertEquals(1, LibraryIndex.load(cache)?.dexFiles?.size)
    }
}
