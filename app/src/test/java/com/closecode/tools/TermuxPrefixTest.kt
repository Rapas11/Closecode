package com.closecode.tools

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxPrefixTest {

    @Test
    fun replacesThePrefixAndHomeAtTheSameLength() {
        val prefix = TermuxPrefix("com.closecode")
        val before = "run ${prefix.oldPrefix}/bin/sh from ${prefix.oldHome}".toByteArray()

        val after = String(prefix.patchBytes(before))

        assertEquals("run ${prefix.newPrefix}/bin/sh from ${prefix.newHome}", after)
        assertEquals(prefix.oldPrefix.length, prefix.newPrefix.length)
        assertEquals(prefix.oldHome.length, prefix.newHome.length)
    }

    @Test
    fun patchesFilesInPlace() {
        val root = Files.createTempDirectory("termux-prefix").toFile()
        try {
            val script = File(root, "bin/tool")
            script.parentFile?.mkdirs()
            script.writeText("#!/bin/sh\nexec ${TermuxPrefix("com.closecode").oldPrefix}/bin/tool\n")
            script.setExecutable(true)

            TermuxPrefix("com.closecode").patch(root)

            assertTrue(script.readText().contains("/data/data/com.closecode/file/u/bin/tool"))
            assertTrue(script.canExecute())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aDifferentPackageNameCannotFitTheTermuxPrefix() {
        assertThrows(IllegalArgumentException::class.java) {
            TermuxPrefix("com.other")
        }
    }
}
