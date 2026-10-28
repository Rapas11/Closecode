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
    fun theCommandScriptUsesTheInstalledTokenAndPort() {
        val script = controlCommandScript("/data/user/0/com.closecode/files/opencode")

        assertTrue(script.contains("BASE='/data/user/0/com.closecode/files/opencode'"))
        assertTrue(script.contains("control.token"))
        assertTrue(script.contains("control.port"))
        assertTrue(script.contains("/system/bin/nc 127.0.0.1"))
    }
}
