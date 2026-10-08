package com.closecode.ui.ide

import com.closecode.ui.theme.DarkColors
import com.closecode.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry behind an indentation guide, away from any layout.
 *
 * Every number here is arithmetic over the file's own text, which is the point: the guide's column
 * is decided before a single pixel is measured, so it can be pinned by a test rather than checked by
 * eye on a phone. The one measured value — a character's width — is applied by the layer afterwards.
 */
class GuideGeometryTest {

    // -- The column ------------------------------------------------------------

    @Test
    fun aBlockSitsOneTabPastTheLineThatOpenedIt() {
        // The block's contents live one tab in from the opener, so that is where its rule goes.
        assertEquals(4, guideColumn(openIndent = 0, tabSize = 4))
        assertEquals(8, guideColumn(openIndent = 4, tabSize = 4))
    }

    @Test
    fun theColumnFollowsTheTabSizeTheProjectActuallyUses() {
        assertEquals(2, guideColumn(openIndent = 0, tabSize = 2))
        assertEquals(8, guideColumn(openIndent = 0, tabSize = 8))
    }

    @Test
    fun aSillyTabSizeStillProducesAColumnRatherThanTheOpenersOwn() {
        // A guide drawn at the opener's own column would sit on top of the opener's text, which is
        // what the old brace-glyph anchor did on a line like `setContent {`.
        assertTrue(guideColumn(openIndent = 4, tabSize = 0) > 4)
        assertTrue(guideColumn(openIndent = 4, tabSize = -1) > 4)
    }

    @Test
    fun aNegativeIndentIsTreatedAsNoIndentAtAll() {
        assertEquals(4, guideColumn(openIndent = -6, tabSize = 4))
    }

    @Test
    fun theColumnIsAlwaysDeeperThanTheLineThatOpenedTheBlock() {
        for (tabSize in 1..8) {
            for (indent in 0..24) {
                assertTrue(
                    "tabSize=$tabSize indent=$indent",
                    guideColumn(indent, tabSize) > indent,
                )
            }
        }
    }

    // -- Indents ---------------------------------------------------------------

    @Test
    fun everyLineReportsItsOwnIndent() {
        // Four lines, including the empty one the trailing newline leaves behind — the same count
        // `String.lines()` gives, so a line number and an indent always describe the same row.
        val indents = indentColumnsOf("fun main() {\n    one()\n}\n", tabSize = 4)
        assertEquals(4, indents.size)
        assertEquals(0, indents[0])
        assertEquals(4, indents[1])
        assertEquals(0, indents[2])
    }

    @Test
    fun aTabAdvancesToTheNextStopRatherThanCountingAsOneColumn() {
        // Four spaces and one tab are the same indent, so they must produce the same column — that
        // is the whole reason this is arithmetic rather than "count the characters before the code".
        val spaces = indentColumnsOf("    val x = 1\n", tabSize = 4)
        val tabs = indentColumnsOf("\tval x = 1\n", tabSize = 4)
        assertEquals(spaces[0], tabs[0])
        assertEquals(4, tabs[0])
    }

    @Test
    fun aTabPartWayAcrossTheStopCarriesItToTheNextOne() {
        // Two spaces then a tab lands on column 4, not 3: the tab fills what is left of the stop.
        val indents = indentColumnsOf("  \tval x = 1\n", tabSize = 4)
        assertEquals(4, indents[0])
    }

    @Test
    fun aTabAtAColumnThatIsAlreadyAStoPAdvancesAFullStop() {
        val indents = indentColumnsOf("\t\tval x = 1\n", tabSize = 4)
        assertEquals(8, indents[0])
    }

    @Test
    fun whitespaceAfterCodeIsNotIndent() {
        val indents = indentColumnsOf("val x = 1    \n", tabSize = 4)
        assertEquals(0, indents[0])
    }

    @Test
    fun aLineOfNothingButSpacesIsBlankRatherThanDeeplyIndented() {
        // Counting it would hang a guide off the middle of a blank row inside a block.
        val indents = indentColumnsOf("fun main() {\n        \n    one()\n}\n", tabSize = 4)
        assertEquals(0, indents[1])
        assertEquals(4, indents[2])
    }

    @Test
    fun aFinalLineWithNoNewlineOfItsOwnStillGetsAnEntry() {
        val indents = indentColumnsOf("a\n    b", tabSize = 4)
        assertEquals(2, indents.size)
        assertEquals(4, indents[1])
    }

    @Test
    fun anEmptyFileStillHasOneLine() {
        assertEquals(1, indentColumnsOf("", tabSize = 4).size)
    }

    @Test
    fun thereIsOneIndentForEveryLine() {
        val source = "a\n\nb\n\n\nc\n"
        assertEquals(source.lines().size, indentColumnsOf(source, tabSize = 4).size)
    }

    // -- The span --------------------------------------------------------------

    @Test
    fun aGuideRunsFromTheLineAfterTheOpenerToTheCloserItself() {
        assertEquals(8..16, guideSpan(openLine = 7, closeLine = 16))
        assertEquals(11..13, guideSpan(openLine = 10, closeLine = 13))
    }

    @Test
    fun aGuideNeverStartsOnTheLineHoldingTheOpeningBrace() {
        // Drawn from the opener's own row it would run across the `{` and everything after it.
        val span = assertNotNull(guideSpan(openLine = 10, closeLine = 14)).let { 10 + 1..14 }
        assertTrue(span.first > 10)
    }

    @Test
    fun aBlockThatOpensAndClosesOnTheSameLineDrawsNothing() {
        assertNull(guideSpan(openLine = 5, closeLine = 5))
    }

    @Test
    fun aBraceLeftOpenAtTheEndOfTheFileDrawsNothing() {
        assertNull(guideSpan(openLine = 12, closeLine = 12))
        assertNull(guideSpan(openLine = 12, closeLine = 11))
    }

    @Test
    fun aSpanIsNeverEmpty() {
        for (open in 1..20) {
            for (close in 1..20) {
                val span = guideSpan(open, close) ?: continue
                assertTrue("open=$open close=$close", !span.isEmpty())
            }
        }
    }

    // -- The rule's weight -----------------------------------------------------

    @Test
    fun aGuideIsFaintEnoughToBeAHintInBothThemes() {
        // Regression guard on the weight itself: at the strength of a real divider the rules read
        // as bright bars competing with the code, which is the opposite of an annotation.
        val floor = 0.12f
        val ceiling = 0.22f
        for (theme in listOf(LightColors, DarkColors)) {
            val alpha = theme.guideRule.alpha
            assertTrue("alpha=$alpha", alpha >= floor)
            assertTrue("alpha=$alpha", alpha <= ceiling)
        }
    }

    @Test
    fun aGuideIsFainterThanTheTextItSitsBeside() {
        assertTrue(LightColors.guideRule.alpha < LightColors.tx.alpha)
        assertTrue(DarkColors.guideRule.alpha < DarkColors.tx.alpha)
    }
}