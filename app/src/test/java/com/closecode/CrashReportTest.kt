package com.closecode

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {

    private fun tempDir(): File = Files.createTempDirectory("crash").toFile()

    @Test
    fun theReportCarriesTheVersionLineTheTraceAndTheBreadcrumbs() {
        val dir = tempDir()
        CrashReport.write(
            filesDir = dir,
            threadName = "main",
            throwable = IllegalStateException("boom"),
            label = "CloseCode 1.0 (build 3) · abc1234 · debug",
            crumbs = listOf("12:00:00.001 BACK press", "12:00:01.002 BUILD started"),
        )
        val text = CrashReport.readLast(dir).orEmpty()
        assertTrue(text.startsWith("CloseCode 1.0 (build 3) · abc1234 · debug\n"))
        assertTrue(text.contains("crashed at") && text.contains("thread main"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("--- the last 2 events ---"))
        assertTrue(text.contains("BACK press"))
    }

    @Test
    fun aReportWithNoBreadcrumbsKeepsNoEmptySection() {
        val dir = tempDir()
        CrashReport.write(dir, "main", RuntimeException("x"), "label", emptyList())
        val text = CrashReport.readLast(dir).orEmpty()
        assertFalse(text.contains("--- the last"))
    }

    @Test
    fun clearForgetsBothNewAndOldReports() {
        val dir = tempDir()
        CrashReport.write(dir, "main", RuntimeException("x"), "label", emptyList())
        CrashReport.clear(dir)
        assertNull(CrashReport.readLast(dir))
    }

    @Test
    fun aCrashFromAnOlderApkIsStillReadable() {
        val dir = tempDir()
        File(dir, "last_crash.txt").writeText("old trace")
        assertEquals("old trace", CrashReport.readLast(dir))
        CrashReport.clear(dir)
        assertNull(CrashReport.readLast(dir))
    }

    @Test
    fun theNewFileWinsOverTheLegacyOne() {
        val dir = tempDir()
        File(dir, "last_crash.txt").writeText("old trace")
        CrashReport.write(dir, "main", RuntimeException("x"), "new label", emptyList())
        assertTrue(CrashReport.readLast(dir).orEmpty().startsWith("new label"))
    }
}
