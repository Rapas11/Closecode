package com.closecode.ui.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the side-by-side alignment: equal lines pair up, changed blocks pair first, leftovers
 * go one-sided, and counts can never escape the line lists.
 */
class SplitDiffTest {

    @Test
    fun identicalTextsPairEveryLine() {
        val rows = splitDiff("a\nb\n", "a\nb\n")
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.oldLine == it.newLine && it.oldLine != null })
    }

    @Test
    fun aOneWordEditReadsAsOneRow() {
        val rows = splitDiff("line1\nline2\nline3\n", "line1\nLINE2\nline3\n")
        val changed = rows.filter { it.oldLine != it.newLine }
        assertEquals(listOf(DiffRow("line2", "LINE2")), changed)
    }

    @Test
    fun addedLinesAreNewSideOnly() {
        val rows = splitDiff("a\nc\n", "a\nb\nc\n")
        assertTrue(rows.contains(DiffRow(null, "b")))
        assertTrue(rows.none { it.oldLine == "b" })
    }

    @Test
    fun deletedLinesAreOldSideOnly() {
        val rows = splitDiff("a\nb\nc\n", "a\nc\n")
        assertTrue(rows.contains(DiffRow("b", null)))
        assertTrue(rows.none { it.newLine == "b" })
    }

    @Test
    fun unevenReplacementsPairFirst() {
        val rows = splitDiff("a\nx\ny\nc\n", "a\nz\nc\n")
        // x pairs with z; y has no partner and goes old-side only.
        assertTrue(rows.contains(DiffRow("x", "z")))
        assertTrue(rows.contains(DiffRow("y", null)))
    }

    @Test
    fun emptyOldSideIsAllAdded() {
        val rows = splitDiff("", "a\nb\n")
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.oldLine == null && it.newLine != null })
    }

    @Test
    fun everythingIsCapped() {
        val big = (1..5000).joinToString("\n") { "line$it" }
        assertTrue(splitDiff(big, big + "x").size <= 2000)
    }
}
