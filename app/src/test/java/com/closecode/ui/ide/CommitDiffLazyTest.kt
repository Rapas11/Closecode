package com.closecode.ui.ide

import com.closecode.ui.git.splitDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The commit page and the full-screen diff flatten collapsed rows into LazyColumn items.
 * These pin the shape that keeps a large commit from composing every hidden context line.
 */
class CommitDiffLazyTest {

    @Test
    fun newFileStartsWithSixRowsAndOneGap() {
        val added = (1..20).joinToString("\n") { "line$it" }
        val rows = unify(splitDiff("", added))
        val items = flattenUnifiedDiff(rows, isNew = true, revealed = emptyMap())

        assertEquals(6, items.filterIsInstance<FullDiffItem.Line>().size)
        val gap = items.filterIsInstance<FullDiffItem.Gap>().single()
        assertEquals(14, gap.hidden)
    }

    @Test
    fun revealingAGapExpandsThatFile() {
        val added = (1..20).joinToString("\n") { "line$it" }
        val rows = unify(splitDiff("", added))
        val items = flattenUnifiedDiff(rows, isNew = true, revealed = mapOf(-1 to 100))

        assertEquals(20, items.filterIsInstance<FullDiffItem.Line>().size)
        assertTrue(items.none { it is FullDiffItem.Gap })
    }

    @Test
    fun changedFileFlattensContextGaps() {
        val old = (1..20).joinToString("\n") { "line$it" }
        val new = (1..20).joinToString("\n") { if (it == 10) "CHANGED" else "line$it" }
        val rows = unify(splitDiff(old, new))
        val items = flattenUnifiedDiff(rows, isNew = false, revealed = emptyMap())

        val gaps = items.filterIsInstance<FullDiffItem.Gap>()
        assertEquals(2, gaps.size)
        assertTrue(items.filterIsInstance<FullDiffItem.Line>().size < rows.size)
    }

    @Test
    fun emptyDiffHasNoItems() {
        assertTrue(flattenUnifiedDiff(emptyList(), isNew = false, revealed = emptyMap()).isEmpty())
    }

    @Test
    fun lineNumberColumnsFollowTheFileKind() {
        val added = unify(splitDiff("", "a\nb\n"))
        val addedItems = flattenUnifiedDiff(added, isNew = true, revealed = emptyMap())
            .filterIsInstance<FullDiffItem.Line>()
        assertTrue(addedItems.all { !it.showOld && it.showNew })

        val deleted = unify(splitDiff("a\nb\n", ""))
        val deletedItems = flattenUnifiedDiff(deleted, isNew = false, revealed = emptyMap())
            .filterIsInstance<FullDiffItem.Line>()
        assertTrue(deletedItems.all { it.showOld && !it.showNew })

        val changed = unify(splitDiff("a\n", "b\n"))
        val changedItems = flattenUnifiedDiff(changed, isNew = false, revealed = emptyMap())
            .filterIsInstance<FullDiffItem.Line>()
        assertTrue(changedItems.all { it.showOld && it.showNew })
        assertFalse(changedItems.isEmpty())
    }
}
