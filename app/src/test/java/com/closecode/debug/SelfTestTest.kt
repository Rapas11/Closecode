package com.closecode.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTestTest {

    @Test
    fun theReportNamesEveryCheckWithItsStatusAndDetail() {
        val checks = listOf(
            SelfTest.Check(1, "64-bit phone", SelfTest.Status.Pass, "arm64-v8a"),
            SelfTest.Check(3, "Build tools installed", SelfTest.Status.Skipped, "Not installed yet."),
            SelfTest.Check(4, "Tool file checksums", SelfTest.Status.Fail, "Two files do not match."),
        )
        val text = SelfTest.reportText(
            "CloseCode 1.0 (build 3) · abc1234 · debug\nAndroid 16 (API 36)\n",
            checks,
        )
        assertTrue(text.startsWith("CloseCode 1.0 (build 3) · abc1234 · debug\n"))
        assertTrue(text.contains("1. 64-bit phone — Pass — arm64-v8a"))
        assertTrue(text.contains("3. Build tools installed — Skipped — Not installed yet."))
        assertTrue(text.contains("4. Tool file checksums — Fail — Two files do not match."))
    }

    @Test
    fun theStatusNamesAreTheOnesTheSpecUses() {
        assertEquals("Pass", SelfTest.Status.Pass.name)
        assertEquals("Fail", SelfTest.Status.Fail.name)
        assertEquals("Skipped", SelfTest.Status.Skipped.name)
    }
}
