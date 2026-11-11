package com.closecode.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun removingGradleKeepsTheUserHomeCache() {
        val root = tmp.newFolder("gradle")
        val cache = File(root, "home/caches/modules/dependency.bin").apply {
            parentFile?.mkdirs()
            writeText("cached dependency")
        }
        File(root, "gradle-8.13/bin").mkdirs()
        File(root, "gradle-8.13/bin/gradle").writeText("gradle")
        File(root, "gradle-8.13.zip.part").writeText("partial")

        assertTrue(removeGradleRootExceptCache(root, "home"))

        assertTrue(cache.isFile)
        assertEquals("cached dependency", cache.readText())
        assertFalse(File(root, "gradle-8.13").exists())
        assertFalse(File(root, "gradle-8.13.zip.part").exists())
    }

    @Test
    fun aMissingGradleRootIsAlreadyRemoved() {
        assertTrue(removeGradleRootExceptCache(File(tmp.root, "missing"), "home"))
    }
}
