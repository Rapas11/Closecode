package com.closecode.device

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportHelperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aProjectIsZippedWithoutGeneratedOutput() {
        val project = File(tmp.root, "project").apply { mkdirs() }
        File(project, "app/src/main").mkdirs()
        File(project, "app/src/main/Main.kt").writeText("fun main() {}\n")
        File(project, "build/outputs").mkdirs()
        File(project, "build/outputs/app.apk").writeText("apk")
        File(project, ".git").mkdirs()
        File(project, ".git/config").writeText("secret")
        val output = File(tmp.root, "exports").apply { mkdirs() }

        val zip = ExportHelper.export(project, output)

        ZipFile(zip).use { archive ->
            assertTrue(archive.getEntry("app/src/main/Main.kt") != null)
            assertTrue(archive.getEntry("build/") == null)
            assertTrue(archive.getEntry(".git/config") == null)
        }
    }

    @Test
    fun aSingleFileIsCopiedAndNeverOverwrites() {
        val source = File(tmp.root, "app-debug.apk").apply { writeText("first") }
        val output = File(tmp.root, "exports").apply { mkdirs() }

        val first = ExportHelper.export(source, output)
        val second = ExportHelper.export(source, output)

        assertEquals("first", first.readText())
        assertEquals("first", second.readText())
        assertFalse(first == second)
    }
}
