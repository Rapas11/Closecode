package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildReportTest {

    @Test
    fun aRunningBuildReportsItsStepAndProgress() {
        val json = BuildReport.state(BuildState.Running(Step.CompileCode, 0.5f))

        assertEquals("running", json["state"].asString)
        assertEquals("Compile code", json["step"].asString)
        assertEquals(0.5f, json["progress"].asFloat, 0.001f)
    }

    @Test
    fun aBuiltAppReportsItsApk() {
        val apk = File("/tmp/app.apk")
        val json = BuildReport.outcome(
            BuildOutcome.Built(apk, sizeBytes = 1234, seconds = 7),
        )

        assertEquals("succeeded", json["state"].asString)
        // The report carries the path the build handed it, byte for byte — no re-rooting, no
        // re-separation. (A File("/tmp/app.apk") on Windows is itself "\tmp\app.apk"; the point
        // is that the report never rewrites what it was given.)
        assertEquals(apk.toString(), json["apk"].asString)
        assertEquals(1234L, json["sizeBytes"].asLong)
        assertEquals(7L, json["seconds"].asLong)
    }

    @Test
    fun aFailedBuildKeepsTheCompilerDiagnostics() {
        val diagnostic = CompileDriver.Diagnostic(
            path = "app/src/main/Main.kt",
            line = 12,
            kind = CompileDriver.Diagnostic.Kind.Error,
            message = "unresolved reference",
        )

        val json = BuildReport.state(
            BuildState.Failed(Step.CompileCode, "Compilation failed.", listOf(diagnostic)),
        )

        assertEquals("failed", json["state"].asString)
        assertEquals("Compile code", json["step"].asString)
        assertEquals(
            "unresolved reference",
            json["diagnostics"].asJsonArray[0].asJsonObject["message"].asString,
        )
    }

    @Test
    fun theNewestBuildLogIsTheOneTheBridgeReads() {
        val dir = File(System.getProperty("java.io.tmpdir"), "closecode-log-${System.nanoTime()}")
        val logs = File(dir, "build/logs").apply { mkdirs() }
        File(logs, "build-20260101-000000.txt").writeText("old\n")
        File(logs, "build.log").writeText("new\n")

        try {
            assertEquals(File(logs, "build.log"), BuildReport.latestLog(dir))
            assertEquals(listOf("new"), BuildReport.tail(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theSummaryNamesTheResult() {
        assertTrue(
            BuildReport.summary(BuildOutcome.Built(File("app.apk"), 10, 2))
                .startsWith("Built app.apk"),
        )
        assertEquals("Build cancelled.", BuildReport.summary(BuildOutcome.Cancelled))
    }
}
