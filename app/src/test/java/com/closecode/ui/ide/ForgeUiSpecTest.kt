package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec coverage for the pieces added after the engine work: the bottom bar, the tool row order,
 * the log cap, image downscaling and the preview formatters.
 */
class ForgeUiSpecTest {

    // -- bottom bar ---------------------------------------------------------------------

    @Test
    fun theBottomBarIsThreeEqualDoorsInOrder() {
        assertEquals(
            listOf("Search", "AI", "More"),
            com.closecode.ui.ide.model.IdeNavItems,
        )
    }

    @Test
    fun theBottomBarKeepsThreeItemsBecauseOnlyThreeArePlaces() {
        assertEquals(3, com.closecode.ui.ide.model.IdeNavItems.size)
    }

    @Test
    fun noLongLivedPanelIsStillOnTheBottomBar() {
        // The editor stays on screen; Preview moved to the path bar and Logcat and Build moved to
        // More, so none of them can occupy a bottom-bar item.
        listOf("Code", "Preview", "Logcat", "Build", "Problems").forEach { item ->
            assertTrue(
                "$item must not be a bottom bar item",
                item !in com.closecode.ui.ide.model.IdeNavItems,
            )
        }
    }

    @Test
    fun searchOpensFirstSoTheLeftmostItemIsTheOneUsersReachFor() {
        assertEquals("Search", com.closecode.ui.ide.model.IdeNavItems.first())
    }

    // -- explorer drawer ---------------------------------------------------------------

    @Test
    fun githubLeadsTheDrawerAndIsTheTabThatOpens() {
        // Declared once so the strip's order and the default cannot drift apart: the account,
        // clone and publish controls all live behind GitHub, and none of them need a repository.
        assertEquals(listOf("GitHub", "Files"), ExplorerTabs)
        assertEquals("GitHub", ExplorerTabs.first())
    }

    @Test
    fun theDrawerOffersExactlyTwoDistinctTabs() {
        assertEquals(2, ExplorerTabs.size)
        assertEquals(2, ExplorerTabs.distinct().size)
    }

    // -- path bar -----------------------------------------------------------------------

    @Test
    fun previewIsOfferedForEveryKindThePreviewPanelCanRender() {
        assertTrue(isPreviewable(FileKind.Kotlin, "MainActivity.kt"))
        assertTrue(isPreviewable(FileKind.Html, "index.html"))
        assertTrue(isPreviewable(FileKind.Json, "data.json"))
        assertTrue(isPreviewable(FileKind.Xml, "AndroidManifest.xml"))
        assertTrue(isPreviewable(FileKind.Csv, "rows.csv"))
        assertTrue(isPreviewable(FileKind.Markdown, "README.md"))
        // Images are recognised by extension, because the decoder never sees the FileKind first.
        assertTrue(isPreviewable(null, "screenshot.png"))
        assertTrue(isPreviewable(FileKind.Unknown, "photo.webp"))
    }

    @Test
    fun previewIsNeverOfferedForThingsItCannotDraw() {
        listOf(
            FileKind.Gradle to "build.gradle",
            FileKind.Properties to "gradle.properties",
            FileKind.Unknown to "LICENSE",
            FileKind.Folder to "src",
            // The panel renders a whole web project from its entry HTML, never a loose stylesheet
            // or script — offering them only ever produced plain text.
            FileKind.Css to "app.css",
            FileKind.Js to "app.js",
        ).forEach { (kind, name) ->
            assertFalse("preview must not be offered for $name", isPreviewable(kind, name))
        }
    }

    // -- find in project ----------------------------------------------------------------

    @Test
    fun findInSourcesMatchesCaseInsensitivelyAndKeepsLineNumbers() {
        val files = listOf(
            file("MainActivity.kt", "src/MainActivity.kt") to "fun onCreate() {\n  setContentView()\n}",
            file("Theme.kt", "src/Theme.kt") to "val panel = 1",
        )
        val hits = findInSources("setcontentview", files)
        assertEquals(1, hits.size)
        assertEquals("MainActivity.kt", hits[0].fileName)
        assertEquals(2, hits[0].line)
        assertEquals("setContentView()", hits[0].text)
    }

    @Test
    fun findInSourcesStopsAtTheLimitSoOneLetterQueriesStillAnswer() {
        val many = (1..(MAX_SEARCH_HITS + 50)).joinToString("\n") { "line $it todo" }
        val hits = findInSources("todo", listOf(file("A.kt", "a/A.kt") to many))
        assertEquals(MAX_SEARCH_HITS, hits.size)
    }

    @Test
    fun findInSourcesReturnsNothingForABlankQuery() {
        assertTrue(findInSources("   ", listOf(file("A.kt", "a/A.kt") to "hello")).isEmpty())
    }

    private fun file(name: String, path: String) =
        com.closecode.ui.model.ProjectFile(
            name = name,
            kind = FileKind.Kotlin,
            language = "Kotlin",
            depth = 0,
            path = path,
        )

    // -- Spec 6.8 tool row --------------------------------------------------------------

    @Test
    fun theToolRowKeepsTheSpecOrder() {
        assertEquals(
            listOf(
                ToolAction.TAB,
                ToolAction.COMMENT,
                ToolAction.LINE_UP,
                ToolAction.LINE_DOWN,
                ToolAction.COPY,
                ToolAction.PROBLEMS,
                ToolAction.OPEN_BRACE,
                ToolAction.CLOSE_BRACE,
                ToolAction.OPEN_PAREN,
                ToolAction.CLOSE_PAREN,
                ToolAction.SEMICOLON,
                ToolAction.EQUALS,
            ),
            ToolRowOrder,
        )
    }

    @Test
    fun everyInsertButtonTypesItsCharacter() {
        assertEquals("{", ToolRowInserts[ToolAction.OPEN_BRACE])
        assertEquals("}", ToolRowInserts[ToolAction.CLOSE_BRACE])
        assertEquals("(", ToolRowInserts[ToolAction.OPEN_PAREN])
        assertEquals(")", ToolRowInserts[ToolAction.CLOSE_PAREN])
        assertEquals(";", ToolRowInserts[ToolAction.SEMICOLON])
        assertEquals("=", ToolRowInserts[ToolAction.EQUALS])
    }

    @Test
    fun nonInsertActionsHaveNoCharacter() {
        for (action in listOf(ToolAction.TAB, ToolAction.COPY, ToolAction.LINE_UP)) {
            assertTrue(action.name, ToolRowInserts[action] == null)
        }
    }

    // -- Spec 7 log cap ----------------------------------------------------------------

    @Test
    fun theLogIsTrimmedBackToOneThousandOnceItOvershoots() {
        val log = ArrayList<LogLine>()
        repeat(1_260) { log.appendCapped(LogLine(LogLevel.INFO, "T", "line $it")) }
        assertEquals(1_000, log.size)
        // The newest lines survive; the oldest are dropped.
        assertEquals("line 1259", log.last().message)
    }

    @Test
    fun theLogNeverExceedsOneThousandOverALongRun() {
        val log = ArrayList<LogLine>()
        repeat(5_000) { log.appendCapped(LogLine(LogLevel.DEBUG, "T", "m$it")) }
        assertTrue(log.size <= 1_000)
        assertEquals("m4999", log.last().message)
    }

    @Test
    fun aLogLineFormatsTheWayTheSpecShowsIt() {
        val line = LogLine(LogLevel.ERROR, "Forge", "boom", time = "10:20:30.400")
        assertEquals("10:20:30.400 E/Forge: boom", line.formatted)
    }

    @Test
    fun theBuildTaskListIsTheSpecList() {
        assertEquals(
            listOf(
                "preBuild",
                "compileDebugKotlin",
                "mergeDebugResources",
                "dexBuilderDebug",
                "packageDebug",
                "assembleDebug",
            ),
            BuildTasks,
        )
    }

    // -- Spec item 5, image downscaling -------------------------------------------------

    @Test
    fun aSmallImageIsNotDownscaled() {
        assertEquals(1, sampleSizeFor(800, 600))
    }

    @Test
    fun anImageExactlyAtTheLimitIsNotDownscaled() {
        assertEquals(1, sampleSizeFor(2048, 1365))
    }

    @Test
    fun aLargeImageIsHalvedUntilItFits() {
        assertEquals(2, sampleSizeFor(4096, 3072))
        assertEquals(4, sampleSizeFor(8192, 6144))
        assertEquals(8, sampleSizeFor(16384, 12288))
    }

    @Test
    fun theLongestEdgeIsWhatDecidesTheSample() {
        // Very wide but short images still need shrinking.
        assertEquals(4, sampleSizeFor(8192, 100))
    }

    @Test
    fun anUnknownImageSizeDoesNotLoopForever() {
        assertEquals(1, sampleSizeFor(0, 0))
        assertEquals(1, sampleSizeFor(-5, -5))
    }

    // -- Spec item 4, preview formatters ------------------------------------------------

    @Test
    fun jsonIsIndentedWithoutReorderingKeys() {
        val out = prettyFormat("""{"b":1,"a":[1,2],"c":{}}""", isJson = true)
        assertEquals(
            """
            {
              "b": 1,
              "a": [
                1,
                2
              ],
              "c": {}
            }
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun anEmptyJsonObjectStaysOnOneLine() {
        assertEquals("{}", prettyFormat("{}", isJson = true))
        assertEquals("[]", prettyFormat("[]", isJson = true))
    }

    @Test
    fun bracesInsideJsonStringsDoNotChangeTheIndentation() {
        val out = prettyFormat("""{"s":"a}b","t":"c{d"}""", isJson = true)
        assertTrue(out.contains("\"a}b\""))
        assertTrue(out.contains("\"c{d\""))
    }

    @Test
    fun xmlIsIndentedByItsTags() {
        val out = prettyFormat("<a><b>text</b></a>", isJson = false)
        assertTrue(out, out.contains("<a>"))
        assertTrue(out, out.contains("</a>"))
        // The child sits on its own line, one level deeper.
        assertTrue(out, out.lines().any { it.trimStart().startsWith("<b>") })
    }

    @Test
    fun markdownHeadingsKeepTheirLevel() {
        val blocks = parseMarkdown("# One\n\n## Two\n\nbody text")
        val headings = blocks.filterIsInstance<MarkdownBlock.Heading>()
        assertEquals(listOf(1, 2), headings.map { it.level })
        assertEquals("One", headings[0].text)
        assertEquals("Two", headings[1].text)
    }

    @Test
    fun markdownFencedCodeIsCapturedWhole() {
        val blocks = parseMarkdown("before\n```kotlin\nval a = 1\nval b = 2\n```\nafter")
        val code = blocks.filterIsInstance<MarkdownBlock.Code>().single()
        assertTrue(code.text.contains("val a = 1"))
        assertTrue(code.text.contains("val b = 2"))
    }

    @Test
    fun markdownBulletsBecomeBulletBlocks() {
        val bullets = parseMarkdown("- one\n* two\n+ three").filterIsInstance<MarkdownBlock.Bullet>()
        assertEquals(listOf("one", "two", "three"), bullets.map { it.text })
    }

    @Test
    fun markdownRulesBecomeRuleBlocks() {
        assertTrue(parseMarkdown("a\n\n---\n\nb").any { it is MarkdownBlock.Rule })
    }

    @Test
    fun csvQuotedCellsWithCommasStayInOneField() {
        val rows = parseCsv("""a,b,c""" + "\n" + """"x,y",z,3""")
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("x,y", "z", "3"), rows[1])
    }

    @Test
    fun csvHandlesDoubledQuotesInsideQuotedCells() {
        val rows = parseCsv("\"say \"\"hi\"\"\",b")
        assertEquals("say \"hi\"", rows[0][0])
    }

    @Test
    fun emptyCsvRowsAreDropped() {
        assertEquals(1, parseCsv("\n\na,b\n\n").size)
    }

    // -- Spec 5 palette ----------------------------------------------------------------

    @Test
    fun anEmptyPaletteQueryKeepsTheDeclaredOrder() {
        val commands = listOf(
            PaletteCommand("Save file", "Ctrl S") {},
            PaletteCommand("Show logcat", null) {},
        )
        assertEquals(commands, paletteMatch(commands, ""))
    }

    @Test
    fun thePaletteMatchesSubsequencesNotJustPrefixes() {
        val commands = listOf(
            PaletteCommand("Open MainActivity", null) {},
            PaletteCommand("Save file", null) {},
        )
        assertEquals(1, paletteMatch(commands, "oma").size)
        assertEquals(0, paletteMatch(commands, "zzz").size)
    }

    @Test
    fun thePaletteRejectsCharactersThatAreOutOfOrder() {
        val commands = listOf(PaletteCommand("abcdef", null) {})
        assertEquals(1, paletteMatch(commands, "abc").size)
        assertEquals(0, paletteMatch(commands, "cba").size)
    }

    // -- Spec 3 typography -------------------------------------------------------------

    @Test
    fun theCodeLineHeightIsTheFixedTwentyTheSpecDemands() {
        assertEquals(20, CodeLineHeight.value.toInt())
    }

    @Test
    fun theGutterIsSixtyTwoDpWide() {
        assertEquals(62, GutterWidth.value.toInt())
    }

    @Test
    fun theGutterDotAndNumberPaddingMatchTheSpec() {
        assertEquals(7, GutterDotSize.value.toInt())
        assertEquals(11, GutterDotLeft.value.toInt())
        assertEquals(7, GutterDotTop.value.toInt())
        assertEquals(26, GutterNumberPadding.value.toInt())
    }

    @Test
    fun theCodeColumnStartsFourteenDpPastTheGutterDivider() {
        assertEquals(14, CodeLeftInset.value.toInt())
        assertEquals(76, CodeColumnStart.value.toInt())
    }

    // -- Spec 4.1 header -----------------------------------------------------------------

    @Test
    fun theHeaderHoldsExactlyTheSpecItemsInOrder() {
        // The explorer button is not here: the file drawer opens from the chevron at the left of the
        // tab strip, which is the only way in on a phone.
        assertEquals(
            listOf(
                HeaderItem.LOGO,
                HeaderItem.PROJECT_NAME,
                HeaderItem.SAVE,
                HeaderItem.UNDO,
                HeaderItem.REDO,
                HeaderItem.SEARCH,
                HeaderItem.RUN,
                HeaderItem.OVERFLOW,
            ),
            HeaderItems,
        )
    }

    @Test
    fun theHeaderHasNoMenuAndNoFormatButton() {
        // Spec 4.1: no menu button and no Format button in the header. Format lives in the command
        // palette and the overflow menu instead, and the file drawer opens from the tab strip's
        // chevron. Asserted as an exact list, so adding any button back fails here.
        assertEquals(8, HeaderItems.size)
        assertEquals(HeaderItems.distinct(), HeaderItems)
    }

    @Test
    fun searchIsStillReachableFromTheHeader() {
        assertTrue(HeaderItem.SEARCH in HeaderItems)
    }

    @Test
    fun theTabStripHasABoundedHeight() {
        // The explorer cell stretches to the full strip height. A Row in a Column hands its children
        // all the height left over, so without a stated strip height that cell swallowed the editor:
        // the code area collapsed and the tabs floated in the middle of the screen.
        assertEquals(50, TAB_STRIP_HEIGHT.value.toInt())
        assertEquals(34, TAB_HEIGHT.value.toInt())
    }

    // -- Spec 6.6 lightbulb placement ---------------------------------------------------

    @Test
    fun theBulbHangsOffTheLineAboveTheCaretLine() {
        // Spec 6.6: the bulb's bottom edge sits at the vertical centre of the line above, which is
        // lineTop - lineHeight/2, so the top is that minus the bulb's own height. With the caret on
        // the sixth row its top is at 100 dp and the row above it is centred at 90 dp.
        val top = bulbTopPx(caretLineTopPx = 100f, lineHeightPx = 20f, bulbHeightPx = 33f, minTopPx = 2f)
        assertEquals(57f, top, 0.01f)
        // Its bottom edge lands exactly on the centre of the row above.
        assertEquals(90f, top + 33f, 0.01f)
    }

    @Test
    fun theBulbNeverRisesAboveTheEditorTop() {
        val top = bulbTopPx(caretLineTopPx = 0f, lineHeightPx = 20f, bulbHeightPx = 33f, minTopPx = 2f)
        assertEquals(2f, top, 0.01f)
        // Even a caret line close to the top is clamped, never negative.
        val nearTop = bulbTopPx(caretLineTopPx = 30f, lineHeightPx = 20f, bulbHeightPx = 33f, minTopPx = 2f)
        assertEquals(2f, nearTop, 0.01f)
    }

    @Test
    fun theBulbScalesWithTheLineGrid() {
        val atOneX = bulbTopPx(240f, 20f, 33f, 2f)
        val atTwoX = bulbTopPx(480f, 40f, 33f, 2f)
        // Zooming doubles the row height: the same line moves down 240 dp and the rule's own half
        // row term grows by another 10 dp.
        assertEquals(197f, atOneX, 0.01f)
        assertEquals(427f, atTwoX, 0.01f)
        assertEquals(230f, atTwoX - atOneX, 0.01f)
    }

    @Test
    fun fileKindsThatGetTheirOwnIconTileAreDistinct() {
        // Kotlin and XML are the two the spec gives a dedicated tile to.
        assertTrue(FileKind.Kotlin != FileKind.Xml)
        assertTrue(FileKind.Kotlin != FileKind.Folder)
    }

    // -- path bar ---------------------------------------------------------------------------

    @Test
    fun aPathThatFitsTheBarIsShownInFullBecauseThatIsThePointOfABreadcrumb() {
        assertEquals(
            listOf("app", "build.gradle.kts"),
            breadcrumbOf("app/build.gradle.kts"),
        )
        assertEquals(
            listOf("src", "main", "AndroidManifest.xml"),
            breadcrumbOf("src/main/AndroidManifest.xml"),
        )
    }

    @Test
    fun aFileAtTheProjectRootShowsOnlyItsOwnName() {
        assertEquals(listOf("README.md"), breadcrumbOf("README.md"))
    }

    @Test
    fun aPathTooLongToReadIsShortenedFromTheLeftSoThePackageSurvives() {
        val crumbs = breadcrumbOf("app/src/main/java/com/example/myapp/MainActivity.kt")
        assertEquals("MainActivity.kt", crumbs.last())
        assertEquals("myapp", crumbs[crumbs.size - 2])
        assertEquals("…", crumbs.first())
        assertTrue(crumbs.size <= 3)
        // Shortening never invents or drops the file itself.
        assertTrue(crumbs.contains("MainActivity.kt"))
    }

    @Test
    fun aPathThatIsOnlyWhitespaceOrSeparatorsHasNoCrumbs() {
        assertEquals(emptyList<String>(), breadcrumbOf(""))
        assertEquals(emptyList<String>(), breadcrumbOf("   "))
        assertEquals(emptyList<String>(), breadcrumbOf("///"))
    }

    @Test
    fun theTruncationLimitIsHonouredWhateverThePathLooksLike() {
        val deep = (1..20).joinToString("/") { "part$it" } + "/File.kt"
        val crumbs = breadcrumbOf(deep, maxCrumbs = 4)
        assertEquals(4, crumbs.size)
        assertEquals("File.kt", crumbs.last())
        assertEquals("…", crumbs.first())
    }
}

class RetainTabsTest {

    private fun tab(name: String) =
        ProjectFile(name = name, kind = FileKind.Kotlin, language = "Kotlin", depth = 0)

    private val nothingDirty: (String) -> Boolean = { false }

    @Test
    fun aStripUnderTheCapIsLeftAlone() {
        val tabs = listOf(tab("a"), tab("b"))
        assertEquals(tabs, retainTabs(tabs, active = "a", isDirty = nothingDirty))
    }

    @Test
    fun openingAFourthFileDropsTheOldestOneThatIsNeitherActiveNorDirty() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "d", isDirty = nothingDirty)
        assertEquals(listOf("b", "c", "d"), kept.map { it.name })
    }

    @Test
    fun theActiveTabIsNeverDroppedEvenWhenItIsTheOldest() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "a", isDirty = nothingDirty)
        assertEquals(listOf("a", "c", "d"), kept.map { it.name })
    }

    @Test
    fun unsavedWorkIsNeverDropped() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "d", isDirty = { it == "a" })
        assertEquals(listOf("a", "c", "d"), kept.map { it.name })
    }

    @Test
    fun aStripOfUnsavedTabsKeepsThemAllRatherThanLosingWork() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "d", isDirty = { true })
        assertEquals(4, kept.size)
    }
}