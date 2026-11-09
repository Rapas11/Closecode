package com.closecode.ui.ide

import com.closecode.lang.Diagnostic
import com.closecode.lang.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class SoraStage3Test {

    @Test
    fun windowPrefetchesAroundTheVisibleRows() {
        val window = soraHighlightWindow(1000, 1020, 5000, IntRange.EMPTY)
        assertEquals(800, window.first)
        assertEquals(1420, window.last)
    }

    @Test
    fun windowStaysPutWhileTheVisibleRowsRemainInside() {
        val current = 800..1420
        assertEquals(current, soraHighlightWindow(1000, 1020, 5000, current))
    }

    @Test
    fun windowRefreshesNearTheTopEdge() {
        val current = 800..1420
        assertEquals(650..1280, soraHighlightWindow(850, 880, 5000, current))
    }

    @Test
    fun windowRefreshesNearTheBottomEdge() {
        val current = 800..1420
        assertEquals(1180..1820, soraHighlightWindow(1380, 1420, 5000, current))
    }

    @Test
    fun windowIsClampedToTheFile() {
        assertEquals(0..420, soraHighlightWindow(0, 20, 5000, IntRange.EMPTY))
        assertEquals(4790..4999, soraHighlightWindow(4990, 4999, 5000, IntRange.EMPTY))
        assertEquals(0..0, soraHighlightWindow(10, 20, 0, IntRange.EMPTY))
    }

    @Test
    fun diagnosticsMapToSourceOffsetsAndSeverities() {
        val marks = diagnosticMarks(
            20,
            listOf(
                Diagnostic(Severity.ERROR, "bad", 10, 0, 2, 3, "E1"),
                Diagnostic(Severity.WARNING, "warn", 15, 99, 2, 8, "W1"),
            ),
        )
        assertEquals(2, marks.size)
        assertEquals(10, marks[0].start)
        assertEquals(11, marks[0].end)
        assertEquals("bad", marks[0].message)
        assertEquals(15, marks[1].start)
        assertEquals(20, marks[1].end)
    }


    @Test
    fun htmlDiagnosticsBecomeSoraMarks() {
        val source = "<div>\n  <p>hello</p>"
        val marks = diagnosticMarks(source.length, htmlTagDiagnostics(source))
        assertEquals(1, marks.size)
        assertEquals(0, marks[0].start)
        assertEquals("<div".length, marks[0].end)
        assertEquals("Unclosed <div>", marks[0].message)
    }

    @Test
    fun diagnosticsOutsideTheCurrentTextAreDropped() {
        val marks = diagnosticMarks(
            10,
            listOf(
                Diagnostic(Severity.ERROR, "gone", 20, 3, 3, 1, "E1"),
            ),
        )
        assertEquals(emptyList<DiagnosticMark>(), marks)
    }
}
