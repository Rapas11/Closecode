package com.closecode.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportTest {

    @Test
    fun theReportStartsWithTheVersionLineAndNamesEverySection() {
        val text = DebugReport.build(
            header = "CloseCode 1.0 (build 3) · abc1234 · debug",
            selfTest = "1. 64-bit phone — Pass — arm64-v8a",
            logLines = listOf("12:00:00.000 BACK press"),
            crash = "stack of the last stop",
        )
        assertTrue(text.startsWith("CloseCode 1.0 (build 3) · abc1234 · debug\n"))
        assertTrue(text.contains("--- last self-test ---"))
        assertTrue(text.contains("1. 64-bit phone — Pass — arm64-v8a"))
        assertTrue(text.contains("--- last 1 log lines ---"))
        assertTrue(text.contains("12:00:00.000 BACK press"))
        assertTrue(text.contains("--- last crash ---"))
        assertTrue(text.contains("stack of the last stop"))
    }

    @Test
    fun missingPartsAreNamedInsteadOfLeftBlank() {
        val text = DebugReport.build("label", null, emptyList(), null)
        assertTrue(text.contains("(no self-test has run yet)"))
        assertTrue(text.contains("(the debug log is off — no lines)"))
        assertTrue(text.contains("(none)"))
    }

    @Test
    fun theLogSectionKeepsTheNewestTwoHundredLines() {
        val lines = (1..250).map { "line $it" }
        val text = DebugReport.build("label", null, lines, null)
        assertTrue(text.contains("--- last 200 log lines ---"))
        assertFalse(text.contains("line 1\n"))
        assertTrue(text.contains("line 51\n"))
        assertTrue(text.contains("line 250\n"))
    }
}
