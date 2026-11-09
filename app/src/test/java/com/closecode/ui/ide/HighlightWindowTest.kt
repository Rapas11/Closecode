package com.closecode.ui.ide

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.closecode.ui.theme.DarkColors
import com.closecode.ui.theme.PageColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor colourizes only the slice of the file that can be on screen, so colourizing that slice
 * has to give the same colours those rows would have had from the whole buffer.
 *
 * [highlightCode] carries lexical state across lines through [HighlightContext], so a window that
 * starts inside a block comment or a multi-line string must produce the same colours as the whole
 * file would have given those rows.
 */
class HighlightWindowTest {

    private val colors: PageColors = DarkColors

    private val source = """
        package com.closecode

        import android.os.Bundle

        /**
         * A block comment that
         * spans several rows.
         */
        @androidx.compose.Composable
        fun main() {
            val name = "world"
            // A line comment.
            val open = 'a'
            val count = 42
            println("Hello, " + name + count)
        }
    """.trimIndent()

    /** The style a single character ends up drawn with, or null for the plain text colour. */
    private fun styleAt(text: AnnotatedString, index: Int): SpanStyle? =
        text.spanStyles.lastOrNull { it.start <= index && index < it.end }?.item

    @Test
    fun aSliceCarriesExactlyTheColoursTheWholeFileGaveThoseRows() {
        val language = codeLanguageOf(source)
        val contexts = highlightContexts(source, language)
        val full = highlightCode(source, colors, language)
        assertEquals(source, full.text)
        val lines = source.lines()
        for (from in lines.indices) {
            val to = (from + 4).coerceAtMost(lines.lastIndex)
            val slice = sliceOfLines(lines, from..to)
            val highlighted = highlightCode(
                slice,
                colors,
                language,
                initialContext = contexts[from],
            )
            assertEquals(slice, highlighted.text)

            val startOffset = offsetOfLineColumn(source, from + 1, 1)
            for (i in highlighted.text.indices) {
                assertEquals(
                    "row ${from + 1}, column $i",
                    styleAt(full, startOffset + i),
                    styleAt(highlighted, i),
                )
            }
        }
    }

    @Test
    fun oneRowOnItsOwnIsColouredTheSameAsInsideTheWholeFile() {
        // The window is often a handful of rows in the middle of a file, so the single-row case is
        // the one that runs most often while typing.
        val language = codeLanguageOf(source)
        val contexts = highlightContexts(source, language)
        val full = highlightCode(source, colors, language)
        val lines = source.lines()
        for (row in lines.indices) {
            val highlighted = highlightCode(
                sliceOfLines(lines, row..row),
                colors,
                language,
                initialContext = contexts[row],
            )
            val startOffset = offsetOfLineColumn(source, row + 1, 1)
            for (i in highlighted.text.indices) {
                assertEquals(
                    "row ${row + 1}, column $i",
                    styleAt(full, startOffset + i),
                    styleAt(highlighted, i),
                )
            }
        }
    }

    @Test
    fun aSliceFromTheWholeBufferIsTheWholeBuffer() {
        val lines = source.lines()
        val whole = sliceOfLines(lines, 0..lines.lastIndex)
        assertEquals(source, whole)
        val language = codeLanguageOf(source)
        val full = highlightCode(source, colors, language)
        val highlighted = highlightCode(whole, colors, language)
        assertEquals(full.text, highlighted.text)
        assertEquals(full.spanStyles, highlighted.spanStyles)
    }

    @Test
    fun anEmptySliceColoursToNothing() {
        val highlighted = highlightCode("", colors, CodeLanguage.PROGRAMMING)
        assertEquals("", highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty())
    }


    private fun assertSliceMatchesFull(
        source: String,
        language: CodeLanguage,
        from: Int,
        to: Int,
    ) {
        val lines = source.lines()
        val contexts = highlightContexts(source, language)
        val full = highlightCode(source, colors, language)
        val slice = sliceOfLines(lines, from..to)
        val highlighted = highlightCode(
            slice,
            colors,
            language,
            initialContext = contexts[from],
        )
        assertEquals(slice, highlighted.text)
        val startOffset = offsetOfLineColumn(source, from + 1, 1)
        for (i in highlighted.text.indices) {
            assertEquals(
                "row ${from + 1}, column $i",
                styleAt(full, startOffset + i),
                styleAt(highlighted, i),
            )
        }
    }

    @Test
    fun aBlockCommentThatStartedAboveTheWindowStaysAComment() {
        val text = """
            package demo

            /*
             * This comment began on row three.
             * The window starts in its middle.
             */
            fun main() {
                println("done")
            }
        """.trimIndent()
        assertSliceMatchesFull(text, CodeLanguage.PROGRAMMING, 3, 7)
    }

    @Test
    fun aTripleQuotedStringThatStartedAboveTheWindowStaysAString() {
        val text = listOf(
            "fun message() {",
            "    val text = \"\"\"first row",
            "second row",
            "third row\"\"\"",
            "    println(text)",
            "}",
        ).joinToString("\n")
        assertSliceMatchesFull(text, CodeLanguage.PROGRAMMING, 2, 4)
    }

    @Test
    fun aBacktickStringThatStartedAboveTheWindowStaysAString() {
        val text = """
            const message = `first
            second
            third`;
            console.log(message);
        """.trimIndent()
        assertSliceMatchesFull(text, CodeLanguage.PROGRAMMING, 1, 3)
    }

    @Test
    fun anHtmlCommentThatStartedAboveTheWindowStaysAComment() {
        val text = """
            <div>
            <!-- hidden
              still hidden
              finally visible -->
            <p>shown</p>
        """.trimIndent()
        assertSliceMatchesFull(text, CodeLanguage.MARKUP, 2, 4)
    }
}