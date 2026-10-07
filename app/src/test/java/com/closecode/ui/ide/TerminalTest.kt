package com.closecode.ui.ide

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The terminal: how a command reaches the shell, how its status comes back, and how much of it the
 * panel is allowed to keep. The live-shell test is the only one that needs a host shell, and it
 * stands down where there is none rather than failing.
 */
class TerminalTest {

    @Test
    fun aCommandIsWrittenWithTheMarkerThatCarriesItsStatus() {
        assertEquals("ls ; echo \"cc-exit:$?\"", shellLine("ls"))
    }

    @Test
    fun theMarkerIsParsedBackIntoASingleExitStatus() {
        assertEquals(0, parseExitMarker("cc-exit:0"))
        assertEquals(127, parseExitMarker("cc-exit:127"))
        assertEquals(130, parseExitMarker("  cc-exit:130  "))
    }

    @Test
    fun ordinaryOutputNeverReadsAsAStatusLine() {
        assertNull(parseExitMarker(""))
        assertNull(parseExitMarker("cc-exit:"))
        assertNull(parseExitMarker("cc-exit:not-a-number"))
        assertNull(parseExitMarker("build failed: cc-exit:1"))
    }

    @Test
    fun aPasteIsSentAsOneCommandAndNeverAsSeveral() {
        assertEquals("rm -rf build", sanitizeCommand("rm -rf build\nmake\nsleep 1"))
        assertEquals("ls", sanitizeCommand("   ls   \n"))
        assertEquals("", sanitizeCommand("\n\n"))
        assertEquals("", sanitizeCommand(""))
    }

    @Test
    fun theTerminalKeepsItsOutputBounded() {
        val lines = mutableListOf<String>()
        repeat(TERMINAL_MAX_LINES + 500) { lines.appendTerminalLine("line $it") }
        assertEquals(TERMINAL_MAX_LINES, lines.size)
        assertEquals("line 500", lines.first())
        assertEquals("line ${TERMINAL_MAX_LINES + 499}", lines.last())
    }

    @Test
    fun aMissingShellNeverReportsItselfAsRunning() {
        val shell = ShellProcess(directory = null, onLine = {}, shellPath = "/does/not/exist")
        shell.start()
        assertFalse(shell.isRunning)
        shell.close()
    }

    @Test
    fun aLiveShellKeepsStateBetweenCommandsAndReportsItsStatus() {
        val output = CopyOnWriteArrayList<String>()
        val shell = ShellProcess(directory = File("."), onLine = output::add, shellPath = "/bin/sh")
        shell.start()
        if (!shell.isRunning) return

        try {
            shell.run("MYVAR=persisted")
            shell.run("echo \$MYVAR")
            shell.run("false")

            val codes = awaitExitCodes(output, 3)
            assertEquals(listOf(0, 0, 1), codes.takeLast(3))
            assertTrue(output.any { it.trim() == "persisted" })
            // The status arrives on the same stream as the output, and the shell prints it: the
            // panel is what decides not to show it.
            assertTrue(output.any { it.startsWith(EXIT_MARKER) })
        } finally {
            shell.close()
        }
        assertFalse(shell.isRunning)
    }

    @Test
    fun terminalCoversTheEditorLikeTheOtherToolPanels() {
        // Back remembers what was underneath, so every full-screen tool panel has to be registered
        // or back would land on Code and lose the Preview that was open.
        assertEquals(setOf("Logcat", "Build", "Terminal"), ToolPanels)
    }

    @Test
    fun theHeaderTerminalButtonIsItsOwnGlyph() {
        assertEquals("terminal", CcIcons.Terminal.name)
        assertFalse(CcIcons.Terminal.name == CcIcons.Search.name)
    }

    private fun awaitExitCodes(output: List<String>, count: Int): List<Int> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val codes = output.mapNotNull { parseExitMarker(it) }
            if (codes.size >= count) return codes
            Thread.sleep(20)
        }
        return output.mapNotNull { parseExitMarker(it) }
    }
}
