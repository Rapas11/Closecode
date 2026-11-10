package com.closecode.control

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CloseCodeControlTest {

    @Test
    fun theCommandScriptIsValidShell() {
        val shell = File("/bin/sh")
        assumeTrue(shell.isFile)
        val script = File.createTempFile("closecode", ".sh")

        try {
            script.writeText(controlCommandScript("/data/user/0/com.closecode/files/opencode"))
            val process = ProcessBuilder(shell.absolutePath, "-n", script.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()

            assertEquals(output, 0, process.waitFor())
        } finally {
            script.delete()
        }
    }

    @Test
    fun helpWorksBeforeTheAppHasEverStarted() {
        val shell = File("/bin/sh")
        assumeTrue(shell.isFile)
        val script = File.createTempFile("closecode-help", ".sh")

        try {
            script.writeText(controlCommandScript("/data/user/0/com.closecode/files/opencode"))
            for (args in listOf(listOf("--help"), listOf("gradle", "--help"), listOf("help", "tool"))) {
                val process = ProcessBuilder(listOf(shell.absolutePath, script.absolutePath) + args)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(output, 0, process.waitFor())
                assertTrue(output, output.contains("usage: closecode"))
            }
            val gradle = ProcessBuilder(shell.absolutePath, script.absolutePath, "gradle", "--help")
                .redirectErrorStream(true)
                .start()
            val gradleOutput = gradle.inputStream.bufferedReader().readText()
            assertEquals(0, gradle.waitFor())
            assertTrue(gradleOutput, gradleOutput.contains("usage: closecode gradle"))
        } finally {
            script.delete()
        }
    }

    @Test
    fun theCommandScriptUsesTheInstalledTokenAndPort() {
        val script = controlCommandScript("/data/user/0/com.closecode/files/opencode")

        assertTrue(script.contains("BASE='/data/user/0/com.closecode/files/opencode'"))
        assertTrue(script.contains("control.token"))
        assertTrue(script.contains("control.port"))
        assertTrue(script.contains("/system/bin/nc 127.0.0.1"))
        assertTrue(script.contains("install|launch|logcat|export|screenshot|input|accessibility"))
        assertTrue(script.contains("\\\"args\\\":"))
    }

    /** P0-5: one marker for every command, and the wrapper exits with its code. */
    @Test
    fun theScriptTurnsTheExitMarkerIntoTheProcessExitCode() {
        val script = controlCommandScript("/data/user/0/com.closecode/files/opencode")

        assertTrue(script.contains("__CLOSECODE_EXIT__*__"))
        assertTrue(script.contains("exit \"\$CODE\""))
        // The build path is no longer handled as a special case; any command can carry a code.
        assertTrue(!script.contains("if [ \"\${CMD}\" = \"tool\" ]"))
    }

    /** P1-4: the wrapper prints each line as it arrives instead of buffering the whole run. */
    @Test
    fun theScriptStreamsTheAnswerLineByLine() {
        val script = controlCommandScript("/data/user/0/com.closecode/files/opencode")

        assertTrue(script.contains("mkfifo"))
        assertTrue(script.contains("while IFS= read -r LINE"))
        assertTrue(script.contains("done < \"\$FIFO\""))
    }
}
