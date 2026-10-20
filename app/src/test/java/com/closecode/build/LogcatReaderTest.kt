package com.closecode.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatReaderTest {

    private val lines = listOf(
        LogcatReader.line(1, "I/ActivityManager( 100): Start proc com.closecode.buildtest"),
        LogcatReader.line(2, "E/AndroidRuntime( 200): FATAL EXCEPTION: main"),
        LogcatReader.line(3, "W/Example( 300): something odd in com.closecode.buildtest"),
        LogcatReader.line(4, "D/Other( 400): unrelated"),
    )

    @Test
    fun levelsComeFromTheBriefPrefix() {
        assertEquals(LogcatReader.Level.Info, lines[0].level)
        assertEquals(LogcatReader.Level.Error, lines[1].level)
        assertEquals(LogcatReader.Level.Warning, lines[2].level)
        assertEquals(LogcatReader.Level.Other, lines[3].level)
        // A continuation line (a stack trace) is not an error of its own.
        assertEquals(LogcatReader.Level.Other, LogcatReader.line(9, "    at android.app.Foo").level)
    }

    @Test
    fun errorsFilterKeepsOnlyErrorLines() {
        val visible = LogcatReader.visible(lines, LogcatReader.Level.Error, "")
        assertEquals(listOf(lines[1]), visible)
    }

    @Test
    fun searchIgnoresCaseAndKeepsTheLevelFilter() {
        val matching = LogcatReader.visible(lines, null, "BUILDTEST")
        assertEquals(2, matching.size)

        val errorsOnly = LogcatReader.visible(lines, LogcatReader.Level.Error, "buildtest")
        assertTrue(errorsOnly.isEmpty())

        val fatal = LogcatReader.visible(lines, LogcatReader.Level.Error, "fatal")
        assertEquals(listOf(lines[1]), fatal)
    }

    @Test
    fun blankPackageSaysWhatToDoInsteadOfPretendingToRead() {
        val snapshot = LogcatReader.read("")
        assertTrue(snapshot.lines.isEmpty())
        assertTrue(snapshot.note!!.contains("Open an app first"))
    }
}
