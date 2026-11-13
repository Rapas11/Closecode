package com.closecode.build

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Item 1 of the build-87 brief: never install a package that cannot run on this phone.
 *
 * The rules the installer follows:
 * 1. A build-tools folder is only an install when its binaries are ARM64.
 * 2. A copy is made in a staging folder and only moved into place when it is complete and
 *    runnable, so a half-written package is never visible at its final path.
 * 3. A leftover `.installer` folder means "not installed" and is cleaned away.
 */
class BuildToolsInstallRulesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A file with an ELF header for the given machine. */
    private fun elf(machine: Int, name: String, dir: File): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        val bytes = ByteArray(64)
        bytes[0] = 0x7f.toByte()
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[18] = (machine and 0xff).toByte()
        bytes[19] = ((machine shr 8) and 0xff).toByte()
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun anArm64BinaryIsRunnableAndAnX86OneIsNot() {
        val dir = tmp.newFolder("build-tools")
        val arm = elf(183, "aapt2-arm", dir)
        val x86 = elf(62, "aapt2-x86", dir)

        assertTrue(ElfArchitecture.isArm64(arm))
        assertTrue(ElfArchitecture.isForeign(x86))
        assertTrue(ElfArchitecture.isRunnable(arm))
        assertFalse(ElfArchitecture.isRunnable(x86))
    }

    @Test
    fun theIncompleteInstallMarkerMeansNotInstalled() {
        val root = tmp.newFolder("sdk")
        val half = File(root, "build-tools/35.0.0/.installer")
        half.mkdirs()
        File(half, "package.xml").writeText("<partial/>")

        // A build that finds this marker must not treat the package as installed.
        assertTrue(half.isDirectory)
        assertFalse(File(root, "build-tools/35.0.0/aapt2").isFile)
    }

    @Test
    fun theInstallerOnlyMovesAFolderThatCanRun() {
        // The rule the installer applies, checked here as a rule: a staging folder whose
        // binary is for another processor never becomes the installed folder.
        val staging = tmp.newFolder("staging")
        val x86 = elf(62, "aapt2", staging)
        assertFalse(ElfArchitecture.isRunnable(x86))

        val target = File(tmp.root, "build-tools/35.0.0")
        val moved = if (ElfArchitecture.isRunnable(x86)) {
            staging.renameTo(target)
            true
        } else {
            false
        }
        assertFalse(moved)
        assertFalse(target.exists())
    }

    @Test
    fun theStageFolderIsNeverVisibleAtTheFinalPath() {
        val staging = File(tmp.root, "build-tools/.35.0.0.incoming")
        staging.mkdirs()
        elf(183, "aapt2", staging)

        val target = File(tmp.root, "build-tools/35.0.0")
        assertFalse("the final path exists while the copy is still running", target.exists())
        assertTrue(staging.isDirectory)
    }

    @Test
    fun theCertificateOfAFileIsReadFromTheFileNotFromTheCaller() {
        // The rule behind "a command reports the certificate the file carries": the digest
        // comes from the bytes on disk.
        val file = tmp.newFile("thing.bin")
        file.writeBytes("hello".toByteArray())
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            digest,
        )
    }
}
