package com.closecode.ui.project

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zip a project travels in must come back as the same project, with build output left
 * out on both sides, and no entry may write outside the folder the import chose.
 */
class ProjectZipTest {

    private val root = Files.createTempDirectory("zip-src").toFile()

    private fun writeProject(): File {
        val app = File(root, "app/src/main/java").apply { mkdirs() }
        File(root, "app/src/main/AndroidManifest.xml").parentFile?.mkdirs()
        File(root, "app/build/classes").apply { mkdirs() }
        File(app, "Main.kt").writeText("fun main() {}\n")
        File(app, "Hello.java").writeText("class Hello {}\n")
        File(root, "app/src/main/AndroidManifest.xml").writeText("<manifest/>\n")
        File(root, "app/build/classes/Main.class").writeText("junk")
        File(root, "README.md").writeText("# Hi\n")
        return root
    }

    @Test
    fun collectKeepsSourcesAndSkipsBuildOutput() {
        writeProject()
        val files = ProjectZip.collectFiles(root)
            .map { it.relativeTo(root).path }

        assertTrue("app/src/main/java/Main.kt" in files)
        assertTrue("app/src/main/java/Hello.java" in files)
        assertTrue("app/src/main/AndroidManifest.xml" in files)
        assertTrue("README.md" in files)
        assertFalse(files.any { it.startsWith("app/build") })
        assertFalse(files.any { it.endsWith(".class") })
    }

    @Test
    fun aRoundTripGivesTheSameFiles() {
        writeProject()
        val manifest = ProjectZip.manifest("My App", "my-app", 1234, "label")
        val bytes = ByteArrayOutputStream().use { ProjectZip.write(root, manifest, it); it.toByteArray() }

        val target = Files.createTempDirectory("zip-dst").toFile()
        val written = ProjectZip.extract(ByteArrayInputStream(bytes), target)

        assertEquals(4, written) // 3 sources + README; build output never left the phone
        assertEquals("fun main() {}\n", File(target, "app/src/main/java/Main.kt").readText())
        assertEquals("<manifest/>\n", File(target, "app/src/main/AndroidManifest.xml").readText())
        assertFalse(File(target, "app/build").exists())
    }

    @Test
    fun theManifestRenamesTheProjectAndSkipsItself() {
        writeProject()
        val bytes = ByteArrayOutputStream().use {
            ProjectZip.write(root, ProjectZip.manifest("Imported", "x", 99, "CloseCode"), it)
            it.toByteArray()
        }

        val target = Files.createTempDirectory("zip-manifest").toFile()
        ProjectZip.extract(ByteArrayInputStream(bytes), target)

        assertFalse(File(target, ProjectZip.MANIFEST).exists())
    }

    @Test
    fun theManifestNameIsReadAsDataOrNotAtAll() {
        assertEquals("Imported", ProjectZip.nameOf("{\"name\":\"Imported\"}"))
        assertNull(ProjectZip.nameOf(null))
        assertNull(ProjectZip.nameOf("not json"))
        assertNull(ProjectZip.nameOf("{ \"name\": \"\" }"))
    }

    @Test
    fun theManifestIsFindableInsideTheWrittenZip() {
        writeProject()
        val manifest = ProjectZip.manifest("From the zip", "from-zip", 12, "CloseCode")
        val bytes = ByteArrayOutputStream().use {
            ProjectZip.write(root, manifest, it)
            it.toByteArray()
        }

        assertEquals(manifest, ProjectZip.manifestFromZip(ByteArrayInputStream(bytes)))
        assertEquals("From the zip", ProjectZip.nameOf(ProjectZip.manifestFromZip(ByteArrayInputStream(bytes))))
    }

    @Test
    fun entryNamesThatWouldEscapeAreRefused() {
        assertNull(ProjectZip.safeEntryPath(""))
        assertNull(ProjectZip.safeEntryPath("/etc/passwd"))
        assertNull(ProjectZip.safeEntryPath("../../etc/passwd"))
        assertNull(ProjectZip.safeEntryPath("a/../../b"))
        assertNull(ProjectZip.safeEntryPath("build/classes/Main.class"))
        assertEquals("app/src/Main.kt", ProjectZip.safeEntryPath("app\\src\\Main.kt"))
        assertEquals("app/src/Main.kt", ProjectZip.safeEntryPath("./app/src/Main.kt"))
    }

    @Test
    fun aHostileEntryWritesNothingOutsideTheTarget() {
        val out = ByteArrayOutputStream().use { zip ->
            java.util.zip.ZipOutputStream(zip).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
                z.write("gotcha".toByteArray())
                z.closeEntry()
                z.putNextEntry(java.util.zip.ZipEntry("Deep/Nested/File.kt"))
                z.write("real".toByteArray())
                z.closeEntry()
            }
            zip.toByteArray()
        }
        val target = Files.createTempDirectory("zip-hostile").toFile()
        val outside = target.parentFile!!

        val written = ProjectZip.extract(ByteArrayInputStream(out), target)

        assertEquals(1, written)
        assertEquals("real", File(target, "Deep/Nested/File.kt").readText())
        assertFalse(File(target, "../escape.txt").exists())
        assertFalse(File(outside, "escape.txt").exists())
    }

    @Test
    fun notAZipIsAnAnswerNotACrash() {
        val target = Files.createTempDirectory("zip-junk").toFile()
        val result = ProjectZip.extract(ByteArrayInputStream("plain text".toByteArray()), target)
        assertEquals(0, result)
        assertEquals(0, target.listFiles()?.size ?: -0)
        val emptyImport = ProjectZip.extract(
            ByteArrayInputStream(
                ByteArrayOutputStream().use { z ->
                    java.util.zip.ZipOutputStream(z).use { it.finish() }
                    z.toByteArray()
                },
            ),
            target,
        )
        assertEquals(0, emptyImport)
        // -1 is reserved for a store the phone has no room in: the target cannot exist.
        val blockedParent = File(Files.createTempDirectory("zip-dead").toFile(), "file")
        blockedParent.createNewFile()
        assertEquals(-1, ProjectZip.extract(ByteArrayInputStream(emptyList<Byte>().toByteArray()), File(blockedParent, "out")))
    }
}
