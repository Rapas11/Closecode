package com.closecode.ui.ide
import com.closecode.lang.DiagnosticCodes

import androidx.compose.ui.unit.IntOffset
import com.closecode.lang.AndroidApiIndex
import com.closecode.lang.KotlinAnalyzer
import com.closecode.lang.Severity
import com.closecode.lang.SourceEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillPlacementTest {

    private val lineHeight = 22f

    @Test
    fun theFirstLineSitsAtTheTop() {
        assertEquals(0f, pillTopPx(1, lineHeight), 0.01f)
    }

    @Test
    fun eachLineAdvancesByExactlyOneLineHeight() {
        // The bug this pins: stacking rows made every problem consume two code lines, so labels
        // drifted further away from their code the more problems a file had.
        assertEquals(22f, pillTopPx(2, lineHeight), 0.01f)
        assertEquals(44f, pillTopPx(3, lineHeight), 0.01f)
        assertEquals(198f, pillTopPx(10, lineHeight), 0.01f)
        assertEquals(308f, pillTopPx(15, lineHeight), 0.01f)
    }

    @Test
    fun spacingStaysProportionalForAnyLineHeight() {
        assertEquals(0f, pillTopPx(1, 30f), 0.01f)
        assertEquals(60f, pillTopPx(3, 30f), 0.01f)
    }

    @Test
    fun invalidLineNumbersDoNotProduceNegativeOffsets() {
        assertEquals(0f, pillTopPx(0, lineHeight), 0.01f)
        assertEquals(0f, pillTopPx(-5, lineHeight), 0.01f)
    }

    @Test
    fun aLabelAnchorsAfterTheLinesTextNeverOnItsNewline() {
        // The bug this pins: the newline draws at the start of the *next* row, so anchoring on it
        // gave x of zero and the label landed on top of the code it was describing.
        val source = "one\ntwo\nthree\n\nfive"
        for (line in intArrayOf(1, 2, 3, 5)) {
            val anchor = endOfLineAnchorOffset(source, line)
            assertTrue("line $line anchored on a newline", source[anchor] != '\n')
        }
        assertEquals(2, endOfLineAnchorOffset(source, 1))
        assertEquals(6, endOfLineAnchorOffset(source, 2))
        assertEquals(12, endOfLineAnchorOffset(source, 3))
        assertEquals(18, endOfLineAnchorOffset(source, 5))
    }

    @Test
    fun aBlankLineAnchorsAtItsOwnStart() {
        // There is no last character to sit after, so the label starts where its text would.
        assertEquals(2, endOfLineAnchorOffset("a\n\nb", 2))
        assertEquals(20, endOfLineAnchorOffset("val x = 1\nval y = 2\n", 3))
    }

    @Test
    fun aPillStartsJustPastTheEndOfItsLine() {
        assertEquals(100f, pillAnchorX(90f, 10f, 300f, 124f), 0.01f)
    }

    @Test
    fun aLongLineWrapsThePillBackToWhereItStillFits() {
        // The line ends past the screen, so the pill comes back far enough to keep its own icon,
        // code label and badge visible instead of running off the right edge.
        val x = pillAnchorX(400f, 0f, 300f, 124f)
        assertEquals(176f, x, 0.01f)
        assertTrue("pill must fit", x + 124f <= 300f)
    }

    @Test
    fun aPillNeverStartsLeftOfTheGapItNeeds() {
        assertEquals(8f, pillAnchorX(0f, 8f, 300f, 124f), 0.01f)
        assertEquals(0f, pillAnchorX(-40f, 0f, 300f, 124f), 0.01f)
    }

    @Test
    fun aViewportNarrowerThanThePillDoesNotGoNegative() {
        assertEquals(0f, pillAnchorX(500f, 0f, 100f, 124f), 0.01f)
    }

    @Test
    fun thePillOffsetCarriesTheXAndNotJustTheTop() {
        // Regression: the layer computed x from the line's real right edge and then handed the
        // offset an IntOffset(0, top), painting every pill over the first characters of its own
        // line. The offset is the only place that number reaches the screen.
        assertEquals(IntOffset(100, 19), pillOffsetOf(100f, 19f))
        assertEquals(IntOffset(0, -4), pillOffsetOf(0.4f, -4f))
        assertEquals(IntOffset(641, 0), pillOffsetOf(640.6f, 0.1f))
    }

    @Test
    fun thePillStaysInsideAHorizontallyScrolledWindow() {
        // The layer scrolls with the text, so the right edge the pill must stay left of is
        // hScroll + visible width, not the visible width alone.
        val scrolledRight = 300f + 200f
        val x = pillAnchorX(500f, 0f, scrolledRight, 124f)
        assertTrue("pill must fit the scrolled window", x + 124f <= scrolledRight)
        assertEquals(376f, x, 0.01f)
    }
}

class LineNumberRowCountTest {

    @Test
    fun trailingBlankLinesAreNotNumbered() {
        // Opening a file must not invent numbers past its content.
        assertEquals(2, gutterRowCount(listOf("a", "b", "")))
        assertEquals(2, gutterRowCount(listOf("a", "b", "", "")))
        assertEquals(2, gutterRowCount(listOf("a", "b", "", "", "")))
    }

    @Test
    fun contentWithNoTrailingBlankIsUnchanged() {
        assertEquals(3, gutterRowCount(listOf("a", "b", "c")))
    }

    @Test
    fun blankRowsBelowTheCaretAreNotNumbered() {
        // Numbering runs up to the caret, never past it. Walking down through blank rows used to
        // produce numbered lines with nothing on them; the rows below where the user is must
        // stay unnumbered whether or not a caret line is passed in.
        assertEquals(1, gutterRowCount(listOf("a", "", "", "")))
        assertEquals(2, gutterRowCount(listOf("a", "b", "", "", "", "")))
        assertEquals(1, gutterRowCount(listOf("a", "", "", ""), caretLine = 1))
        assertEquals(2, gutterRowCount(listOf("a", "", "", "", ""), caretLine = 2))
        assertEquals(3, gutterRowCount(listOf("a", "", "", "", ""), caretLine = 3))
    }

    @Test
    fun theCaretLineIsNumberedBeforeAnythingIsTypedOnIt() {
        // Pressing Enter lands the caret on a blank row. Until that row gets its number the
        // reader cannot tell it exists — which is exactly "the line number only shows up once
        // I type on the line".
        assertEquals(2, gutterRowCount(listOf("a", ""), caretLine = 2))
        assertEquals(3, gutterRowCount(listOf("a", "", ""), caretLine = 3))
        assertEquals(2, gutterRowCount(listOf("a", "b", ""), caretLine = 2))
        // A caret on a row that already has code changes nothing.
        assertEquals(3, gutterRowCount(listOf("a", "b", "c"), caretLine = 3))
    }

    @Test
    fun aCaretPastTheBufferIsClampedToIt() {
        assertEquals(3, gutterRowCount(listOf("a", "b", "c"), caretLine = 99))
        assertEquals(1, gutterRowCount(emptyList(), caretLine = 5))
        assertEquals(1, gutterRowCount(listOf(""), caretLine = 99))
    }

    @Test
    fun anEmptyFileStillHasOneRow() {
        assertEquals(1, gutterRowCount(emptyList()))
        assertEquals(1, gutterRowCount(listOf("")))
    }

    @Test
    fun linesOfInvisibleCharactersCountAsEmpty() {
        // Zero-width space, zero-width non-joiner/joiner, BOM and word joiner all draw nothing, so a
        // line made only of them must not be numbered or treated as the last line of code.
        val invisible = listOf('\u200B', '\u200C', '\u200D', '\uFEFF', '\u2060')
        for (ch in invisible) {
            val line = ch.toString().repeat(24)
            assertFalse("$ch draws nothing", hasVisibleContent(line))
            assertEquals("a line of $ch must not be numbered", 1, gutterRowCount(listOf("a", line)))
        }
    }

    @Test
    fun aNoBreakSpaceIsStillTreatedAsBlank() {
        // U+00A0 renders as a space, so it is blank like any other space.
        assertFalse(hasVisibleContent("\u00A0".repeat(24)))
        assertEquals(1, gutterRowCount(listOf("a", "\u00A0".repeat(24))))
    }

    @Test
    fun aLineWithOneRealCharacterAmongInvisibleOnesIsContent() {
        assertTrue(hasVisibleContent("".repeat(10) + "x"))
        assertEquals(2, gutterRowCount(listOf("a", "".repeat(10) + "x")))
    }

    @Test
    fun numberingStartsAtOneAndEndsOnTheLastLineWithCode() {
        // The scaffolded files all end with a newline, so the raw text has a 17th, empty, line.
        val source = "package a\n}\n"
        assertEquals(11, caretOffsetAtEndOfCode(source))
        assertEquals(2, gutterRowCount(source.lines()))
    }
}

class OpeningCaretTest {

    @Test
    fun theCaretOpensAtTheEndOfTheLastLineThatHasCode() {
        assertEquals(1, caretOffsetAtEndOfCode("}"))
        assertEquals(3, caretOffsetAtEndOfCode("a\nb"))
        assertEquals(3, caretOffsetAtEndOfCode("a\nb\n"))
    }

    @Test
    fun trailingBlankLinesAreSkipped() {
        // Two scaffolded files' worth of trailing newlines: the caret belongs after "b".
        assertEquals(3, caretOffsetAtEndOfCode("a\nb\n\n"))
        assertEquals(3, caretOffsetAtEndOfCode("a\nb\n\n\n\n"))
    }

    @Test
    fun aBlankLineBetweenCodeLinesIsNotTreatedAsTheEnd() {
        // "a", blank, "bb": the blank line in the middle is not the end of the file.
        assertEquals(5, caretOffsetAtEndOfCode("a\n\nbb\n"))
    }

    @Test
    fun trailingSpacesOnTheLastCodeLineAreKeptAsColumnSpace() {
        assertEquals(4, caretOffsetAtEndOfCode("}   \n"))
    }

    @Test
    fun aFileWithNoCodeAtAllOpensAtTheStart() {
        assertEquals(0, caretOffsetAtEndOfCode(""))
        assertEquals(0, caretOffsetAtEndOfCode("\n"))
        assertEquals(0, caretOffsetAtEndOfCode("\n\n\n"))
        assertEquals(0, caretOffsetAtEndOfCode("   \n"))
    }

    @Test
    fun aFirstLineWithCodeOpensAtItsEnd() {
        assertEquals(3, caretOffsetAtEndOfCode("abc\n"))
    }

    @Test
    fun theTextItselfIsNeverRewritten() {
        // The trailing newline stays in the buffer so saving does not churn the file on disk.
        val source = "a\nb\n"
        caretOffsetAtEndOfCode(source)
        assertEquals("a\nb\n", source)
    }

    @Test
    fun theCaretSkipsLinesOfInvisibleCharacters() {
        // The reported symptom: lines 11 to 19 were numbered with nothing drawn on them, and the
        // caret opened on line 19 instead of the last line with code. Those lines held characters
        // that draw nothing, so they are skipped like blank lines.
        val source = "val x = 1\n" + "".repeat(3) + "\n"
        assertEquals("val x = 1".length, caretOffsetAtEndOfCode(source))
        assertEquals(1, gutterRowCount(source.lines()))
    }

    @Test
    fun theCaretStopsAtTheLastLineThatHasCode() {
        val source = "a\nb\n" + "".repeat(8) + "\n"
        val caret = caretOffsetAtEndOfCode(source)
        assertEquals("just after the b", "a\nb".length, caret)
    }
}

class LineColumnOffsetTest {

    private val src = "package com.example\n\nfun demo(spinner: Spinner) {}\n"

    @Test
    fun mapsLineAndColumnToACharacterOffset() {
        assertEquals(0, offsetOfLineColumn(src, 1, 1))
        assertEquals(8, offsetOfLineColumn(src, 1, 9))
        assertEquals(20, offsetOfLineColumn(src, 2, 1))
        assertEquals(21, offsetOfLineColumn(src, 3, 1))
    }

    @Test
    fun pointsAtTheNameInsideAParameterList() {
        val offset = offsetOfLineColumn(src, 3, 19)
        assertEquals("Spinner", src.substring(offset, offset + 7))
    }

    @Test
    fun clampsRatherThanThrowing() {
        assertEquals(0, offsetOfLineColumn(src, 0, 1))
        assertEquals(0, offsetOfLineColumn(src, 1, 0))
        assertTrue(offsetOfLineColumn(src, 999, 1) <= src.length)
        assertTrue(offsetOfLineColumn(src, 1, 999) <= src.length)
        assertEquals(src.length, offsetOfLineColumn(src, 999, 999).coerceAtMost(src.length))
    }

    @Test
    fun everyLineColumnPairStaysInsideTheSource() {
        var line = 1
        var column = 1
        while (line <= 5) {
            val offset = offsetOfLineColumn(src, line, column)
            assertTrue("offset $offset out of range", offset in 0..src.length)
            column += 7
            if (column > 60) {
                column = 1
                line++
            }
        }
    }
}

class ProblemsByLineTest {

    @Test
    fun groupsProblemsAndTracksSeverity() {
        val src = """
            package com.example

            fun demo() {
                val x = notDefinedAnywhere
                use(x)
            }
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src)
        val problems = ProblemsByLine.from(analysis.diagnostics)

        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        assertEquals(listOf(unresolved), problems.on(unresolved.line))
        assertTrue(unresolved.line in problems.linesWithErrors)
        assertEquals(unresolved, problems.worstOn(unresolved.line))
    }

    @Test
    fun emptyDiagnosticsProduceNoProblems() {
        val clean = ProblemsByLine.from(emptyList())
        assertTrue(clean.byLine.isEmpty())
        assertTrue(clean.all.isEmpty())
        assertEquals(null, clean.worstOn(1))
    }

    @Test
    fun errorsWinOverWarningsOnTheSameLine() {
        val src = "package com.example\n\nfun demo() { val x = nope }\n"
        val analysis = KotlinAnalyzer.analyze(src)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val worst = problems.worstOn(problems.all.first().line)
        assertEquals(Severity.ERROR, worst?.severity)
    }
}

class QuickFixActionTest {

    private val index = AndroidApiIndex.parse(
        """
        C|android.widget.Spinner|Spinner|android.widget.AbsSpinner
        C|android.widget.MotionLayout|MotionLayout|android.view.ViewGroup
        """.trimIndent(),
    )

    /** `Spinner` sits in a parameter-type position, so it is not a call and gets checked. */
    private val missingImportSource = """
        package com.example

        fun demo(spinner: Spinner) {}
    """.trimIndent()

    @Test
    fun offersImportForAnUnresolvedClassTheIndexKnows() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)

        val import = actions.single { it.kind == QuickFixKind.ADD_IMPORT }
        assertEquals("android.widget.Spinner", import.target)
        assertTrue(import.label.startsWith("Import"))
    }

    @Test
    fun offersTheLineActionsAlongsideTheFixes() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)

        // Spec 6.6 puts the token removal first so tapping the bulb deletes the bad token; the import
        // suggestion follows it, then the plain code actions.
        assertEquals(
            listOf(
                QuickFixKind.REMOVE_TOKEN,
                QuickFixKind.ADD_IMPORT,
                QuickFixKind.TOGGLE_COMMENT,
                QuickFixKind.MOVE_LINE_UP,
                QuickFixKind.MOVE_LINE_DOWN,
            ),
            actions.map { it.kind },
        )
    }

    @Test
    fun aProblemWithNoImportOfferStillGetsTheLineActions() {
        val src = """
            package com.example

            fun demo(thing: ZzUnknownThing) {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, src)

        // Nothing to import, but the token can still be deleted and the line commented or moved.
        assertTrue(actions.none { it.kind == QuickFixKind.ADD_IMPORT })
        assertEquals(
            listOf(
                QuickFixKind.REMOVE_TOKEN,
                QuickFixKind.TOGGLE_COMMENT,
                QuickFixKind.MOVE_LINE_UP,
                QuickFixKind.MOVE_LINE_DOWN,
            ),
            actions.map { it.kind },
        )
    }

    @Test
    fun lowerCaseNamesNeverOfferAnImport() {
        val src = """
            package com.example

            fun demo(thing: someUnknownValue) {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        assertTrue(
            buildQuickFixActions(unresolved.line, problems, analysis, src)
                .none { it.kind == QuickFixKind.ADD_IMPORT },
        )
    }

    @Test
    fun theCommentActionIsOfferedOnTheProblemLine() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val actions = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)
        assertEquals("Comment Line", actions.single { it.kind == QuickFixKind.TOGGLE_COMMENT }.label)
    }

    @Test
    fun commentingOutTheProblemRemovesItAndTheActions() {
        val commented = SourceEdits.toggleLineComment(missingImportSource, 3)
        val analysis = KotlinAnalyzer.analyze(commented, index)
        assertTrue(analysis.diagnostics.none { it.code == DiagnosticCodes.E0101_UNRESOLVED })
        assertEquals(
            emptyList<QuickFixAction>(),
            buildQuickFixActions(3, ProblemsByLine.from(analysis.diagnostics), analysis, commented),
        )
    }

    @Test
    fun aCleanLineHasNoActionsAtAll() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        assertEquals(
            emptyList<QuickFixAction>(),
            buildQuickFixActions(2, ProblemsByLine.from(analysis.diagnostics), analysis, missingImportSource),
        )
    }

    @Test
    fun theAddImportActionActuallyFixesTheFile() {
        val analysis = KotlinAnalyzer.analyze(missingImportSource, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val unresolved = analysis.diagnostics.first { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        val action = buildQuickFixActions(unresolved.line, problems, analysis, missingImportSource)
            .single { it.kind == QuickFixKind.ADD_IMPORT }

        val fixed = SourceEdits.addImport(missingImportSource, action.target)
        val after = KotlinAnalyzer.analyze(fixed, index)
        assertEquals(
            emptyList<String>(),
            after.errors.map { "${it.line}: ${it.message}" },
        )
    }

    @Test
    fun kotlinBuiltinsAreNeverFlaggedAsMissingImports() {
        val src = """
            package com.example

            fun demo(text: String, count: Int) {
                val items: List<String> = listOf(text)
            }
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        assertEquals(
            emptyList<String>(),
            analysis.errors.map { "${it.line}: ${it.message}" },
        )
    }


    @Test
    fun offersRemovalForAnUnusedImport() {
        val src = """
            package com.example

            import android.widget.Spinner

            fun demo() {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val warning = analysis.diagnostics.first { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT }
        val actions = buildQuickFixActions(warning.line, problems, analysis, src)

        val removal = actions.single { it.kind == QuickFixKind.REMOVE_IMPORT }
        assertEquals("android.widget.Spinner", removal.target)
    }

    @Test
    fun noActionsForALineWithNoProblems() {
        val analysis = KotlinAnalyzer.analyze("package com.example\n")
        assertEquals(
            emptyList<QuickFixAction>(),
            buildQuickFixActions(1, ProblemsByLine.from(analysis.diagnostics), analysis, "x"),
        )
        assertEquals(emptyList<QuickFixAction>(), buildQuickFixActions(null, ProblemsByLine(), null, ""))
    }

    @Test
    fun theRemoveImportActionActuallyFixesTheWarning() {
        val src = """
            package com.example

            import android.widget.Spinner

            fun demo() {}
        """.trimIndent()
        val analysis = KotlinAnalyzer.analyze(src, index)
        val problems = ProblemsByLine.from(analysis.diagnostics)
        val warning = analysis.diagnostics.first { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT }
        val action = buildQuickFixActions(warning.line, problems, analysis, src)
            .single { it.kind == QuickFixKind.REMOVE_IMPORT }

        val fixed = SourceEdits.removeImport(src, action.target)
        val after = KotlinAnalyzer.analyze(fixed, index)
        assertEquals(
            emptyList<String>(),
            after.diagnostics.filter { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT }.map { it.message },
        )
    }
}

class BulbAnchorTest {

    @Test
    fun theBulbSitsOnTheCaret() {
        assertEquals(120f, bulbAnchorX(caretXPx = 120f, bulbWidthPx = 38f, viewportWidthPx = 400f), 0.01f)
    }

    @Test
    fun theBulbIsNeverPulledPastTheRightEdge() {
        // The bug this pins: a fixed 3 dp inset always drew the bulb at the gutter, so it never
        // travelled with the caret and sat on top of the code at the far left of the column.
        assertEquals(
            362f,
            bulbAnchorX(caretXPx = 5_000f, bulbWidthPx = 38f, viewportWidthPx = 400f),
            0.01f,
        )
    }

    @Test
    fun aCaretAtColumnOneStaysAtTheLeftEdge() {
        assertEquals(0f, bulbAnchorX(caretXPx = -4f, bulbWidthPx = 38f, viewportWidthPx = 400f), 0.01f)
    }

    @Test
    fun aViewportNarrowerThanTheBulbDoesNotGoNegative() {
        assertEquals(0f, bulbAnchorX(caretXPx = 10f, bulbWidthPx = 38f, viewportWidthPx = 12f), 0.01f)
    }
}