package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Item 2: a build-tools folder that holds binaries for another processor is not installed,
 * it is broken. The app has to be able to tell the two apart before a build fails with
 * "not executable: 64-bit ELF file".
 */
class ElfArchitectureTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A file with the ELF magic and the given machine type. */
    private fun elf(machine: Int, name: String = "binary"): File {
        val file = tmp.newFile(name)
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
    fun anArm64BinaryIsRecognised() {
        val file = elf(183, "aapt2")

        assertEquals(ElfArchitecture.ARM64, ElfArchitecture.of(file))
        assertTrue(ElfArchitecture.isArm64(file))
        assertFalse(ElfArchitecture.isForeign(file))
    }

    @Test
    fun anX86BinaryIsRecognisedAsForeign() {
        val file = elf(62, "aapt2")

        assertEquals(ElfArchitecture.X86_64, ElfArchitecture.of(file))
        assertFalse(ElfArchitecture.isArm64(file))
        assertTrue(ElfArchitecture.isForeign(file))
    }

    @Test
    fun aFileThatIsNotAnElfIsNotCalledForeign() {
        val file = tmp.newFile("script").apply { writeText("#!/system/bin/sh\necho hi\n") }

        assertNull(ElfArchitecture.of(file))
        assertFalse(ElfArchitecture.isArm64(file))
        assertFalse(ElfArchitecture.isForeign(file))
    }

    /**
     * The rule the app removes folders by: only a binary that is *proven* to be for another
     * processor counts as foreign. A wrapper script may be perfectly runnable, and a working
     * install is never destroyed on a guess.
     */
    @Test
    fun onlyAProvenForeignBinaryCountsAsForeign() {
        val x86 = elf(62, "aapt2-x86")
        val arm = elf(183, "aapt2-arm")
        val script = tmp.newFile("aapt2-script").apply { writeText("#!/system/bin/sh\n") }

        assertFalse(ElfArchitecture.isRunnable(x86))
        assertTrue(ElfArchitecture.isRunnable(arm))
        assertTrue(ElfArchitecture.isRunnable(script))
    }

    @Test
    fun aMissingFileIsNotAnElf() {
        assertNull(ElfArchitecture.of(File(tmp.root, "missing")))
    }
}
