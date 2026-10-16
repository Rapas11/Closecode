package com.closecode.debug

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {

    @After
    fun reset() {
        DebugLog.resetForTests()
    }

    @Test
    fun offBuildsNothingAndRecordsNothing() {
        var built = false
        DebugLog.line("BUILD") {
            built = true
            "this text must never be built while the switch is off"
        }
        assertFalse("the message text must not be built while the log is off", built)
        assertEquals(emptyList<String>(), DebugLog.snapshot())
    }

    @Test
    fun onRecordsTimeAreaAndMessage() {
        DebugLog.setEnabled(true)
        DebugLog.line("BACK") { "pressed in the editor" }
        val lines = DebugLog.snapshot()
        assertEquals(1, lines.size)
        assertTrue(
            "expected HH:mm:ss.SSS BACK … but was ${lines.first()}",
            Regex("""^\d{2}:\d{2}:\d{2}\.\d{3} BACK pressed in the editor$""").matches(lines.first()),
        )
    }

    @Test
    fun theRingKeepsTheNewestLinesOnly() {
        DebugLog.setEnabled(true)
        repeat(DebugLog.MAX_LINES + 20) { index -> DebugLog.line("SCROLL") { "line $index" } }
        val lines = DebugLog.snapshot()
        assertEquals(DebugLog.MAX_LINES, lines.size)
        assertTrue(lines.last().endsWith("SCROLL line ${DebugLog.MAX_LINES + 19}"))
        assertFalse(lines.any { it.endsWith("SCROLL line 0") })
    }

    @Test
    fun breadcrumbsAreAlwaysKeptEvenWhileOff() {
        DebugLog.crumb("INSTALL", "session opened id=7")
        DebugLog.crumb("BUILD", "checks started")
        val crumbs = DebugLog.crumbs()
        assertEquals(2, crumbs.size)
        assertTrue(crumbs.last().endsWith("BUILD checks started"))
        assertEquals("crumbs are not log lines", emptyList<String>(), DebugLog.snapshot())
    }

    @Test
    fun theCrumbRingKeepsTheNewestHundred() {
        repeat(DebugLog.MAX_CRUMBS + 10) { index -> DebugLog.crumb("BACK", "press $index") }
        val crumbs = DebugLog.crumbs()
        assertEquals(DebugLog.MAX_CRUMBS, crumbs.size)
        assertTrue(crumbs.last().endsWith("BACK press ${DebugLog.MAX_CRUMBS + 9}"))
    }

    @Test
    fun theFileStartsWithTheVersionLineThenTheEvents() {
        val dir = Files.createTempDirectory("debuglog").toFile()
        DebugLog.attach(dir, "CloseCode 1.0 (build 3) · abc1234 · debug")
        DebugLog.setEnabled(true)
        DebugLog.line("TOOLS") { "stored kotlin-compiler.jar" }
        val text = File(dir, "debug/debug.log").readText()
        assertTrue(text.startsWith("CloseCode 1.0 (build 3) · abc1234 · debug\n"))
        assertTrue(text.contains("TOOLS stored kotlin-compiler.jar"))
    }

    @Test
    fun theFileNeverGrowsPastTheCap() {
        val dir = Files.createTempDirectory("debuglog").toFile()
        val file = File(dir, "debug/debug.log")
        file.parentFile?.mkdirs()
        file.writeText("x".repeat((DebugLog.MAX_FILE_BYTES + 100_000).toInt()))
        DebugLog.attach(dir, "label")
        DebugLog.setEnabled(true)
        DebugLog.line("BUILD") { "small" }
        assertTrue("file is ${file.length()} bytes", file.length() < DebugLog.MAX_FILE_BYTES)
        assertTrue(file.readText().contains("BUILD small"))
    }

    @Test
    fun clearForgetsTheLinesAndKeepsTheVersionLine() {
        val dir = Files.createTempDirectory("debuglog").toFile()
        DebugLog.attach(dir, "label")
        DebugLog.setEnabled(true)
        DebugLog.line("BUILD") { "gone soon" }
        DebugLog.clear()
        assertEquals(emptyList<String>(), DebugLog.snapshot())
        assertEquals("label\n", File(dir, "debug/debug.log").readText())
    }
}
