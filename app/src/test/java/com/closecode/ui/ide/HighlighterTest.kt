package com.closecode.ui.ide

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rainbow brackets: off by default, cycling by depth, and depth that carries across lines. */
class RainbowBracketTest {

    private val one = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta)

    private fun bracketColorsIn(code: String): List<Color?> {
        val out = ArrayList<Color?>()
        val marked = highlightCode(code, com.closecode.ui.theme.DarkColors, CodeLanguage.PROGRAMMING)
        marked.spanStyles.forEach { span ->
            var i = span.start
            while (i < span.end) {
                if (marked.text[i] in "([{}])") out.add(span.item.color)
                i++
            }
        }
        return out
    }

    @Test
    fun nothingIsColouredUntilTheFeatureIsOn() {
        RainbowBracketColors = emptyList()
        assertEquals(null, rainbowBracketColor(0))
        assertEquals(
            "every bracket keeps the base text colour",
            emptyList<Color?>(),
            bracketColorsIn("fun a() { }"),
        )
    }

    @Test
    fun depthCyclesThroughTheCycle() {
        RainbowBracketColors = one
        try {
            assertEquals(Color.Red, rainbowBracketColor(0))
            assertEquals(Color.Green, rainbowBracketColor(1))
            assertEquals(Color.Magenta, rainbowBracketColor(4))
            // Past the end it wraps rather than running out of colours.
            assertEquals(Color.Red, rainbowBracketColor(5))
            // A closing bracket reports the depth it closes, which is one less than the opening.
            assertEquals(Color.Red, rainbowBracketColor(-1))
        } finally {
            RainbowBracketColors = emptyList()
        }
    }

    @Test
    fun nestedBracketsGetDifferentColours() {
        RainbowBracketColors = one
        try {
            val colors = bracketColorsIn("fun a() { if (b) { c() } }")
            assertTrue("expected some brackets to be coloured", colors.isNotEmpty())
            assertTrue("nesting should produce more than one colour", colors.distinct().size > 1)
        } finally {
            RainbowBracketColors = emptyList()
        }
    }

    @Test
    fun depthCarriesAcrossLines() {
        // A block spans lines, so a counter reset per line gave every line's first bracket the same
        // colour no matter how deep in the file it sat — the one thing the feature exists to show.
        RainbowBracketColors = one
        try {
            val flat = bracketColorsIn("fun a() { fun b() { c() } }")
            val acrossLines = bracketColorsIn(
                "fun a() {\n    fun b() {\n        c()\n    }\n}",
            )
            assertEquals(flat.size, acrossLines.size)
            assertEquals(flat, acrossLines)
        } finally {
            RainbowBracketColors = emptyList()
        }
    }
}
