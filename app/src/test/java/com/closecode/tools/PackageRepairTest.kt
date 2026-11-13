package com.closecode.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Item 1 of the build-88 brief: the NDK has to work on the phone.
 *
 * Two things went wrong when it was unpacked:
 *
 * 1. Its symlinks arrived as plain text files — `clang` was an eight byte file that said
 *    `clang-18` — so every command in the NDK failed with "No such file or directory".
 * 2. Its 123 wrappers started with `#!/usr/bin/env bash`, and Android has no bash.
 *
 * The test builds that broken state and checks that the repair turns it back into a
 * working NDK.
 */
class PackageRepairTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** An NDK bin folder as it arrives: the links are text files, the wrappers want bash. */
    private fun brokenNdk(): File {
        val root = tmp.newFolder("ndk")
        val bin = File(root, "toolchains/llvm/prebuilt/linux-x86_64/bin")
        bin.mkdirs()
        // The real compiler.
        File(bin, "clang-18").writeText("#!/system/bin/sh\necho clang 18\n")
        File(bin, "clang-18").setExecutable(true, false)
        // A link that arrived as a text file.
        File(bin, "clang").writeText("clang-18")
        File(bin, "clang").setExecutable(true, false)
        // A wrapper that asks for bash.
        val wrapper = File(bin, "aarch64-linux-android21-clang")
        wrapper.writeText("#!/usr/bin/env bash\nexec clang-18 \"$@\"\n")
        wrapper.setExecutable(true, false)
        return root
    }

    @Test
    fun aTextFileThatNamesItsSiblingBecomesALinkAgain() {
        val root = brokenNdk()
        val bin = File(root, "toolchains/llvm/prebuilt/linux-x86_64/bin")
        val link = File(bin, "clang")

        assertFalse("the link is a plain file", java.nio.file.Files.isSymbolicLink(link.toPath()))
        assertEquals("clang-18", link.readText())

        val report = PackageRepair.repair(root)

        assertEquals(1, report.symlinks)
        assertTrue(
            "clang is still a plain file",
            java.nio.file.Files.isSymbolicLink(link.toPath()),
        )
        assertEquals("clang-18", java.nio.file.Files.readSymbolicLink(link.toPath()).toString())
    }

    @Test
    fun aWrapperThatAsksForBashGetsTheShellAndroidHas() {
        val root = brokenNdk()
        val wrapper = File(
            root,
            "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang",
        )

        assertTrue(wrapper.readText().startsWith("#!/usr/bin/env bash"))

        val report = PackageRepair.repair(root)

        assertEquals(1, report.shebangs)
        assertTrue(
            "the wrapper still asks for bash",
            wrapper.readText().startsWith("#!/system/bin/sh"),
        )
        // The body of the script survives.
        assertTrue(wrapper.readText().contains("exec clang-18"))
    }

    @Test
    fun aRealFileThatIsNotALinkIsLeftAlone() {
        val root = tmp.newFolder("plain")
        val file = File(root, "notes.txt")
        file.writeText("clang-18")
        file.setExecutable(true, false)

        val report = PackageRepair.repair(root)

        assertEquals(0, report.symlinks)
        assertEquals("clang-18", file.readText())
    }

    @Test
    fun aFileThatNamesSomethingThatIsNotThereIsLeftAlone() {
        val root = tmp.newFolder("dangling")
        val file = File(root, "clang")
        file.writeText("clang-18")
        file.setExecutable(true, false)

        val report = PackageRepair.repair(root)

        assertEquals(0, report.symlinks)
        assertTrue(file.isFile)
    }

    @Test
    fun theWholeNdkIsRepairedInOnePass() {
        val root = brokenNdk()

        val report = PackageRepair.repair(root)

        assertEquals(1, report.symlinks)
        assertEquals(1, report.shebangs)
        assertTrue(report.changed)
    }
}
