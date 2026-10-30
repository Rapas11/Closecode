package com.closecode.control

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The phone has no Gradle daemon: `closecode gradle` translates Gradle's task names into this
 * phone's own builder and answers unknown names with the supported list. This pins the mapping
 * and the message so an agent never has to guess.
 */
class GradlePassthroughTest {

    @Test
    fun theSupportedTasksCoverBuildInstallLaunchAndChecks() {
        val supported = CloseCodeControl.GRADLE_TASKS
        listOf(
            "assembleDebug", "installDebug", "launch",
            "test", "testDebugUnitTest", "lint", "lintDebug", "check",
        ).forEach { task -> assertTrue("$task must be supported", task in supported) }
    }

    @Test
    fun anUnknownTaskIsRejectedWithTheSupportedList() {
        val message = CloseCodeControl.gradleTaskMessage("phaseUpdate")
        assertTrue(message.contains("no Gradle daemon"))
        assertTrue(message.contains("assembleDebug"))
        assertTrue(message.contains("installDebug"))
        assertTrue(message.contains("launch"))
        assertTrue(message.contains("test"))
        assertTrue(message.contains("lint"))
        assertTrue(message.contains("check"))
    }

    @Test
    fun aMissingTaskIsRejectedWithTheSameList() {
        val message = CloseCodeControl.gradleTaskMessage("")
        assertTrue(message.contains("no Gradle daemon"))
    }

    @Test
    fun theCommandScriptAcceptsGradleAndCheck() {
        val script = File("/bin/sh")
        assumeTrue(script.isFile)
        val written = File.createTempFile("closecode", ".sh")
        try {
            written.writeText(controlCommandScript("/data/user/0/com.closecode/files/opencode"))
            val process = ProcessBuilder(script.absolutePath, "-n", written.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
            val text = written.readText()
            assertTrue(text.contains("g|gradle) CMD=gradle"))
            assertTrue(text.contains("chk|check) CMD=check"))
            assertTrue(text.contains("|gradle|check"))
        } finally {
            written.delete()
        }
    }
}
