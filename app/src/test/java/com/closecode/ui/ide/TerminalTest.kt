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
    fun aCommandThatFloodsStandardErrorStillFinishesAndReports() {
        // Regression: standard error had its own pipe and nothing read it. A pipe holds roughly
        // 64 KB and the shell blocks the instant it fills, so a command writing more than that to
        // stderr stopped dead — no output, no prompt, no error, and no way back. 3000 lines is about
        // 150 KB, comfortably past the point where an unread stderr wedges the shell for good.
        //
        // Newline separated on purpose. The exit marker is printed straight after whatever the
        // command emitted, so a flood with no newlines in it swallows the marker into its last line
        // and this test would be measuring its own fixture rather than the shell.
        val flood = "i=0; while [ \$i -lt 3000 ]; do " +
            "echo stderr-filler-012345678901234567890123456789 \$i >&2; i=\$((i + 1)); done"
        val output = CopyOnWriteArrayList<String>()
        val shell = ShellProcess(directory = null, onLine = output::add, shellPath = "/bin/sh")
        shell.start()
        if (!shell.isRunning) return

        try {
            shell.run(flood)
            // With stderr undrained this never arrives and the list comes back empty, so the failure
            // is "no status at all" rather than a wrong value — which is exactly the bug.
            val codes = awaitExitCodes(output, 1)
            assertEquals(listOf(0), codes.takeLast(1))
            val flooded = output.filter { it.startsWith("stderr-filler-") }.sumOf { it.length }
            assertTrue("only $flooded bytes reached the panel", flooded > 64 * 1024)
        } finally {
            shell.close()
        }
        assertFalse("the shell must not still be wedged", shell.isRunning)
    }

    @Test
    fun aFloodedErrorStillReachesThePanel() {
        // Merging the streams must not mean discarding one of them: the text a command complained
        // about is the text the reader needs to see.
        val output = CopyOnWriteArrayList<String>()
        val shell = ShellProcess(directory = null, onLine = output::add, shellPath = "/bin/sh")
        shell.start()
        if (!shell.isRunning) return

        try {
            shell.run("echo complaint >&2")
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && output.none { it.trim() == "complaint" }) {
                Thread.sleep(20)
            }
            assertTrue("stderr was dropped: $output", output.any { it.trim() == "complaint" })
        } finally {
            shell.close()
        }
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

    @Test
    fun killingTheShellDoesNotBrickTheTab() {
        // The exact failure from the phone: Stop kills the shell, and every later command was
        // silently swallowed — echoed, but never run, with no output and no error. A dead shell
        // restarts on the next command instead.
        val output = CopyOnWriteArrayList<String>()
        val shell = ShellProcess(directory = null, onLine = output::add, shellPath = "/bin/sh")
        shell.start()
        if (!shell.isRunning) return

        try {
            shell.run("echo before")
            assertEquals(listOf(0), awaitExitCodes(output, 1).takeLast(1))
            shell.killRunning()
            assertFalse(shell.isRunning)
            shell.run("echo after")
            assertEquals(listOf(0, 0), awaitExitCodes(output, 2).takeLast(2))
            assertTrue(output.any { it.trim() == "after" })
        } finally {
            shell.close()
        }
    }

    @Test
    fun aCommandEchoesOnceWithTheLivePrompt() {
        val store = TerminalHistoryStore(
            com.closecode.ui.settings.InMemoryKeyValueStore(),
        )
        val session = TerminalSession(1)
        session.submit("ls -la", "slug", store, "~/Blank app $ ")
        assertEquals(listOf("~/Blank app $ ls -la"), session.lines.toList())
        session.close()
    }
}
