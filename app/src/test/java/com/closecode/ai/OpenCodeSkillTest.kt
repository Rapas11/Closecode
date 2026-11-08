package com.closecode.ai

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * OpenCode learns what the phone can do from one project skill file. It must be written once,
 * must speak OpenCode's own skill format, and must never overwrite the file that is already
 * there.
 */
class OpenCodeSkillTest {

    private val project = Files.createTempDirectory("closecode-skill").toFile()

    @Test
    fun aFirstInstallWritesTheSkill() {
        assertNull(OpenCodeSkill.file(project).takeIf { it.isFile })

        val written = OpenCodeSkill.install(project)

        assertNotNull(written)
        assertTrue(OpenCodeSkill.file(project).isFile)
        assertEquals(OpenCodeSkill.script(), written?.readText())
    }

    @Test
    fun aSecondInstallNeverRewritesTheSkill() {
        val file = OpenCodeSkill.install(project)!!
        file.writeText("---\nname: closecode\ndescription: Mine\n---\nMine.\n")

        val written = OpenCodeSkill.install(project)

        assertEquals(file, written)
        assertEquals("---\nname: closecode\ndescription: Mine\n---\nMine.\n", file.readText())
    }

    @Test
    fun theSkillSpeaksOpenCodesSkillFormat() {
        val script = OpenCodeSkill.script()
        val name = Regex("""name: ([a-z0-9]+(-[a-z0-9]+)*)""").find(script)
            ?.groupValues?.get(1).orEmpty()
        assertEquals("closecode", name)
        val description = script.substringAfter("description: ").substringBefore("\n\n")
        assertTrue(description.length in 1..1024)
        assertTrue(script.contains("closecode build"))
        assertTrue(script.contains("closecode run"))
        assertTrue(script.contains("closecode ui dump"))
        assertTrue(script.contains("closecode gradle"))
        assertTrue(script.contains("--gradle"))
    }

    @Test
    fun aManagedSkillFromAnOlderBuildIsUpdated() {
        val file = OpenCodeSkill.install(project)!!
        file.writeText(
            "---\nname: closecode\ndescription: Old\n---\n" +
                "This phone has no Gradle daemon and closecode build was all it knew.\n",
        )

        OpenCodeSkill.install(project)

        assertEquals(OpenCodeSkill.script(), file.readText())
        assertTrue(file.readText().contains(OpenCodeSkill.MARKER))
    }

    @Test
    fun aBlockedProjectWritesNothing() {
        // The project is a link that pretends to be a directory. Making links is ordinary on
        // Linux, where the app runs; Windows refuses it without a privilege, so the test skips
        // there instead of failing on the sandbox.
        val dir = Files.createTempDirectory("closecode-skill-link-probe").toFile()
        try {
            val link = File(dir, "probe")
            val target = File(dir, "target").apply { writeText("x") }
            runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }
                .onFailure { Assume.assumeNoException(it) }
        } finally {
            dir.deleteRecursively()
        }

        val blocked = File(
            Files.createTempDirectory("closecode-skill-blocked").toFile(),
            "project",
        )
        Files.createSymbolicLink(
            blocked.toPath(),
            // A file where a directory must grow: mkdirs() fails, and nothing is written.
            File.createTempFile("opencode-skill-file", "").toPath().toFile().toPath(),
        )

        val written = OpenCodeSkill.install(blocked)

        assertNull(written)
        assertFalse(OpenCodeSkill.file(blocked).isFile)
    }
}
