package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLogTest {

    @Test
    fun outputLinesAfterOneCommandShareTheShellHeader() {
        val lines = listOf("~/proj $ ls", "app", "build", "~/proj $ pwd")
        val meta = listOf(
            TermMeta(1000, TermTone.User),
            TermMeta(1100, TermTone.Output),
            TermMeta(1200, TermTone.Output),
            TermMeta(1300, TermTone.User),
        )
        val rows = buildTermMessages(lines, meta)
        assertEquals(4, rows.size)
        assertTrue(rows[0].showHeader)
        assertTrue(rows[1].showHeader)
        assertFalse(rows[2].showHeader)
        assertTrue(rows[3].showHeader)
        assertEquals(TermWho.You, rows[0].who)
        assertEquals(TermWho.Shell, rows[1].who)
        assertEquals(TermTone.Error, buildTermMessages(
            listOf("sh: nope: not found"),
            listOf(TermMeta(1, TermTone.Error)),
        ).first().tone)
    }

    @Test
    fun anEchoCarriesItsPromptAndCommandApart() {
        val row = buildTermMessages(
            listOf("~/proj $ ls -la"),
            listOf(TermMeta(5, TermTone.User)),
        ).first()
        assertEquals("~/proj", row.prompt)
        assertEquals("ls -la", row.text)
    }

    @Test
    fun aMissingMetaFallsBackToPlainOutput() {
        val rows = buildTermMessages(listOf("one", "two"), listOf(TermMeta(9, TermTone.Output)))
        assertEquals(TermTone.Output, rows[1].tone)
        assertEquals(0L, rows[1].at)
    }

    @Test
    fun appendLineKeepsTheMetaListAlignedWhenTheScrollbackTrims() {
        val session = TerminalSession(1)
        repeat(TERMINAL_MAX_LINES + 3) { session.appendLine("line $it") }
        assertEquals(TERMINAL_MAX_LINES, session.lines.size)
        assertEquals(session.lines.size, session.lineMeta.size)
        assertEquals("line 3", session.lines.first())
        session.clearLines()
        assertEquals(0, session.lines.size)
        assertEquals(0, session.lineMeta.size)
    }

    @Test
    fun everyToneIsDistinct() {
        assertEquals(TermTone.values().toList().distinct(), TermTone.values().toList())
    }
}
