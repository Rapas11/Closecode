package com.closecode.ui.settings

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decisions the AI settings page makes around OpenCode's own rules. */
class AiSettingsTest {

    @Test
    fun thePickerWalksDefaultAskDenyDefault() {
        assertEquals("ask", AiSettings.actionAfter(""))
        assertEquals("deny", AiSettings.actionAfter("ask"))
        assertEquals("", AiSettings.actionAfter("deny"))
        // An unknown value is treated as untouched, never written back wrongly.
        assertEquals("ask", AiSettings.actionAfter("wat"))
    }

    @Test
    fun labelsMatchOpenCodesWords() {
        assertEquals("Default", AiSettings.actionLabel(""))
        assertEquals("Allow", AiSettings.actionLabel("allow"))
        assertEquals("Ask", AiSettings.actionLabel("ask"))
        assertEquals("Deny", AiSettings.actionLabel("deny"))
    }

    @Test
    fun aValidSkillPassesTheRules() {
        val text = """
            ---
            name: closecode
            description: What the CloseCode phone app does for OpenCode
            ---

            Build and run.
        """.trimIndent()
        assertNull(AiSettings.skillProblem(text))
        assertEquals("closecode", AiSettings.skillName(text))
        assertEquals("What the CloseCode phone app does for OpenCode", AiSettings.skillDescription(text))
    }

    @Test
    fun foldedDescriptionLinesFoldWithSpaces() {
        val text = """
            ---
            name: closecode
            description: What the phone
              does for OpenCode
            ---
        """.trimIndent()

        assertEquals(
            "What the phone does for OpenCode",
            AiSettings.skillDescription(text),
        )
    }

    @Test
    fun aNameWithWrongCaseIsRejected() {
        val text = """
            ---
            name: CloseCode
            description: ok
            ---
        """.trimIndent()
        assertEquals(
            "The name must be lowercase and single hyphens only (like closecode).",
            AiSettings.skillProblem(text),
        )
    }

    @Test
    fun aMissingDescriptionIsRejected() {
        val text = """
            ---
            name: closecode
            ---
        """.trimIndent()
        assertEquals(
            "The skill has no description in its frontmatter.",
            AiSettings.skillProblem(text),
        )
    }

    @Test
    fun skillsComeFromEveryProjectAndTheApp() {
        val root = Files.createTempDirectory("closecode-skills").toFile()
        val first = File(root, "p1")
        val second = File(root, "p2")
        val configHome = File(root, "config")
        write(File(first, ".opencode/skills/build/SKILL.md"))
        write(File(second, ".opencode/skills/rel/SKILL.md"))
        write(File(configHome, "opencode/skills/global/SKILL.md"))

        val files = AiSettings.skillFiles(listOf(first, second), configHome)

        assertEquals("build", files[0].id)
        assertEquals("global", files[1].id)
        assertEquals("rel", files[2].id)
    }

    @Test
    fun aFolderWithoutTheSkillFileIsNotASkill() {
        val root = Files.createTempDirectory("closecode-skill-less").toFile()
        File(root, ".opencode/skills/empty").mkdirs()

        assertTrue(AiSettings.skillFiles(listOf(root), null).isEmpty())
    }

    @Test
    fun theSkillFolderIsTheProjectOpencodeFolder() {
        val project = File(Files.createTempDirectory("closecode-skill-dir").toFile(), "p")
        assertEquals(
            File(project, ".opencode/skills/buildmyapp"),
            AiSettings.skillDirectory(project, "buildmyapp"),
        )
    }

    private fun write(file: File) {
        file.parentFile.mkdirs()
        file.writeText("---\nname: ${file.parentFile.name}\ndescription: x\n---\n")
    }
}
