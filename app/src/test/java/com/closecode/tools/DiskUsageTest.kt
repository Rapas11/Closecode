package com.closecode.tools

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskUsageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun symlinkedDirectoriesAreNotCountedTwice() {
        val root = tmp.newFolder("ndk")
        val real = File(root, "toolchains/llvm").apply { mkdirs() }
        File(real, "clang").writeText("1234567890")
        File(real, "libc++.so").writeText("12345")
        Files.createSymbolicLink(
            File(root, "sysroot").toPath(),
            real.toPath(),
        )

        assertEquals(15L, DiskUsage.bytes(root))
    }
}
