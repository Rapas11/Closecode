package com.closecode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class OpenCodeServerLogTest {

    private fun tempDir(): File =
        File(
            System.getProperty("java.io.tmpdir"),
            "opencodeserverlog-" + UUID.randomUUID(),
        ).apply { mkdirs() }

    @Test
    fun aRunIsNamedAtItsStartAndTheEarlierOneMovesAside() {
        val dir = tempDir()
        val first = OpenCodeServerLog(dir)
        first.runStarted("start one")
        first.appLine("first run line")
        first.appLine("a failing line", warning = true)
        val firstFileText = first.file().readText()

        val second = OpenCodeServerLog(dir)
        second.runStarted("start two")
        second.appLine("second run line")

        val previous = File(dir, "server.1.log").readText()
        assertEquals(firstFileText, previous)
        val current = File(dir, "server.log").readText()
        assertTrue(current.contains("start two"))
        assertTrue(current.contains("second run line"))
        assertFalse(current.contains("first run line"))
    }

    @Test
    fun aFileThatGrowsPastItsCapMovesAsideSoTheCurrentOneStaysSmall() {
        val dir = tempDir()
        val log = OpenCodeServerLog(dir, maxBytes = 40)
        log.appLine("x".repeat(100))

        log.appLine("after rotation")

        val previous = File(dir, "server.1.log").readText()
        assertTrue(previous.contains("x".repeat(100)))
        val current = File(dir, "server.log").readText()
        assertTrue(current.contains("after rotation"))
        assertFalse(current.contains("x".repeat(100)))
    }

    @Test
    fun aBlankLineIsNotRecorded() {
        val dir = tempDir()
        val log = OpenCodeServerLog(dir)

        log.appLine("   ")
        log.appLine("")
        log.appLine("one line")

        assertEquals(1, File(dir, "server.log").readLines().size)
    }

    @Test
    fun theFileIsWhereTheDirectoryWasAskedFor() {
        val dir = tempDir()
        val log = OpenCodeServerLog(dir)

        log.appLine("hello")

        assertEquals(File(dir, "server.log"), log.file())
        assertTrue(log.file().isFile)
    }
}
