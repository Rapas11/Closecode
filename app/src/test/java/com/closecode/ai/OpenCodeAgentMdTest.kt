package com.closecode.ai

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AGENTS.md is the file OpenCode loads by itself in every version the app pins. It must turn
 * up at every server start, teach only commands the app really has, and a project that was
 * taught before must never lose what it was taught.
 */
class OpenCodeAgentMdTest {

    private val project = Files.createTempDirectory("agentsmd").toFile()

    private val fullFirst =
        OpenCodeAgentMd.MARKER_BEGIN + "\n" + OpenCodeAgentMd.section() + OpenCodeAgentMd.MARKER_END + "\n"

    @Test
    fun theFileLivesAtTheProjectRoot() {
        assertEquals(File(project, "AGENTS.md"), OpenCodeAgentMd.file(project))
    }

    @Test
    fun aFirstProjectGetsTheLesson() {
        assertNull(OpenCodeAgentMd.file(project).takeIf { it.isFile })

        val written = OpenCodeAgentMd.install(project)

        assertNotNull(written)
        assertEquals(fullFirst, written?.readText())
    }

    @Test
    fun theLessonTeachesOnlyCommandsTheAppReallyHas() {
        val text = OpenCodeAgentMd.section()
        // Real verbs, from the shell script's own usage line.
        for (verb in listOf(
            "describe", "check", "build", "run", "install", "launch", "app", "stop",
            "restart", "ui", "logcat", "screenshot", "export", "gradle", "github",
        )) {
            assertTrue("should mention [$verb]", text.contains("`closecode $verb") || text.contains("closecode $verb"))
        }
        // There is no `closecode git` on the phone: teaching it would break hands-free work.
        assertFalse(Regex("closecode git\\b").containsMatchIn(text))
        // The file must be clear that Gradle cannot be run directly.
        assertTrue(text.contains("Never run `./gradlew`"))
    }

    @Test
    fun aSecondInstallKeepsTheLessonStable() {
        OpenCodeAgentMd.install(project)
        val first = OpenCodeAgentMd.file(project).readText()

        OpenCodeAgentMd.install(project)

        assertEquals(first, OpenCodeAgentMd.file(project).readText())
    }

    @Test
    fun anExistingAgentsMdKeepsItsOwnTextFirst() {
        val file = OpenCodeAgentMd.file(project)
        file.writeText("# My project\n\nBe prompt.\n")

        OpenCodeAgentMd.install(project)

        val text = file.readText()
        assertTrue(text.startsWith("# My project"))
        val own = text.indexOf("Be prompt.")
        val ours = text.indexOf("## CloseCode phone rules")
        assertTrue(own in 0..ours)
        assertTrue(text.contains(OpenCodeAgentMd.MARKER_BEGIN))
        assertTrue(text.contains(OpenCodeAgentMd.MARKER_END))
    }

    @Test
    fun anOursSectionIsRewrittenInPlaceNotAppendedTwice() {
        val file = OpenCodeAgentMd.file(project)
        file.writeText(
            "# Old project\n${OpenCodeAgentMd.MARKER_BEGIN}\nstale lesson\n${OpenCodeAgentMd.MARKER_END}\nEpilogue\n",
        )

        OpenCodeAgentMd.install(project)

        val text = file.readText()
        assertFalse(text.contains("stale lesson"))
        assertEquals(1, count(text, OpenCodeAgentMd.MARKER_BEGIN))
        assertEquals(1, count(text, OpenCodeAgentMd.MARKER_END))
        assertTrue(text.startsWith("# Old project\n"))
        assertTrue(text.trimEnd().endsWith("Epilogue"))
        assertTrue(text.contains(OpenCodeAgentMd.section()))
    }

    @Test
    fun aMarkerFileWithoutTheEndLosesOnlyOurPart() {
        val file = OpenCodeAgentMd.file(project)
        file.writeText("# Top\n${OpenCodeAgentMd.MARKER_BEGIN}\njunk\n")

        OpenCodeAgentMd.install(project)

        val text = file.readText()
        assertTrue(text.startsWith("# Top\n"))
        assertFalse(text.contains("junk"))
        assertEquals(1, count(text, OpenCodeAgentMd.MARKER_BEGIN))
        assertTrue(text.contains(OpenCodeAgentMd.section()))
    }

    @Test
    fun aDanglingEndMarkerKeepsHeadAndTail() {
        val merged = OpenCodeAgentMd.merged("head\n" + OpenCodeAgentMd.MARKER_END + "\ntail\n", OpenCodeAgentMd.section())

        // An end with no begin is unreadable: the new lesson is appended, nothing is dropped.
        assertTrue(merged.startsWith("head\n"))
        assertTrue(merged.contains("\ntail\n"))
        assertEquals(1, count(merged, OpenCodeAgentMd.MARKER_BEGIN))
    }

    @Test
    fun theBlockIsRebuiltAroundTheMarkers() {
        val existing = "User text.\n" + OpenCodeAgentMd.MARKER_BEGIN + "\nold\n" + OpenCodeAgentMd.MARKER_END + "\ntail\n"
        val merged = OpenCodeAgentMd.merged(existing, "new lesson")

        assertTrue(merged.startsWith("User text.\n"))
        assertTrue(merged.contains("new lesson"))
        assertFalse(merged.contains("old\n"))
        assertEquals(1, count(merged, OpenCodeAgentMd.MARKER_BEGIN))
        assertTrue(merged.trimEnd().endsWith("tail"))
    }

    @Test
    fun aProjectPathBlockedByAFileWritesNothing() {
        val blocked = File(Files.createTempDirectory("agentsmd-blocked").toFile(), "project")
        blocked.createNewFile()

        assertNull(OpenCodeAgentMd.install(blocked))
    }

    private fun count(text: String, needle: String): Int = text.split(needle).size - 1
}
