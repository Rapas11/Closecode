package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Test

class PtyOutputBufferTest {

    private fun collect(vararg chunks: String): List<String> {
        val lines = ArrayList<String>()
        val buffer = PtyOutputBuffer { lines.add(it) }
        chunks.forEach { buffer.feed(it) }
        buffer.flushAll()
        return lines
    }

    @Test
    fun newlineCommitsOneLine() {
        assertEquals(listOf("total 4", "file.txt"), collect("total 4\nfile.txt\n"))
    }

    @Test
    fun crlfKeepsTheTextAndDoesNotAddAnEmptyLine() {
        assertEquals(listOf("one", "two"), collect("one\r\ntwo\r\n"))
    }

    @Test
    fun aCarriageReturnRewritesTheProgressLine() {
        assertEquals(listOf("100 %"), collect("10 %\r50 %\r100 %\n"))
    }

    @Test
    fun aCarriageReturnSplitAcrossChunksStillRewrites() {
        assertEquals(listOf("100 %"), collect("10 %\r", "50 %\r100 %\n"))
    }

    @Test
    fun backspaceEditsTheLineAndNeverCutsBelowEmpty() {
        assertEquals(listOf("ab"), collect("abc\b\n"))
        assertEquals(listOf(""), collect("\b\b\n"))
    }

    @Test
    fun escapeSequencesStayForTheAnsiRenderer() {
        val color = "\u001b[31mred\u001b[0m"
        assertEquals(listOf(color), collect("$color\n"))
    }

    @Test
    fun idleFlushEmitsTheUnfinishedLine() {
        val lines = ArrayList<String>()
        val buffer = PtyOutputBuffer { lines.add(it) }
        buffer.feed("partial")
        buffer.flushIfIdle(now = System.currentTimeMillis() + 1_000)
        assertEquals(listOf("partial"), lines)
        buffer.flushIfIdle(now = System.currentTimeMillis() + 1_000)
        assertEquals(listOf("partial"), lines)
    }
}
