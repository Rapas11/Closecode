package com.closecode.control

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * `closecode github push|pull` talks to the remote from inside the app process. The token is
 * handed to the client it belongs to and is never printed, logged or put into the shell.
 */
class GithubBridgeTest {

    @Test
    fun theVerbsArePushAndPull() {
        assertEquals(setOf("push", "pull"), CloseCodeControl.GITHUB_ACTIONS)
    }

    @Test
    fun anEmptyVerbIsNotAccepted() {
        // A command called with no arguments is neither a push nor a pull: the caller gets the
        // usage instead of a guess.
        assertFalse(CloseCodeControl.GITHUB_ACTIONS.contains(""))
    }

    @Test
    fun theUsageNamesTheForceFlag() {
        assertTrue(CloseCodeControl.GITHUB_USAGE.contains("--force"))
    }

    @Test
    fun theCommandScriptAcceptsGithub() {
        val shell = File("/bin/sh")
        assumeTrue(shell.isFile)
        val written = File.createTempFile("closecode", ".sh")

        try {
            written.writeText(controlCommandScript("/data/user/0/com.closecode/files/opencode"))
            val process = ProcessBuilder(shell.absolutePath, "-n", written.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
            val text = written.readText()
            assertTrue(text.contains("gh|github) CMD=github"))
            assertTrue(text.contains("|github"))
        } finally {
            written.delete()
        }
    }
}
