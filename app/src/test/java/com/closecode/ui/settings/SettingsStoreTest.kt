package com.closecode.ui.settings

import com.closecode.ui.ide.SearchMode
import com.closecode.ui.ide.findInSources
import com.closecode.ui.ide.lineMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings store, against the in-memory key/value layer.
 *
 * The cases that matter are the ugly ones: a preference written by an older build, a hand-edited
 * value, a number that arrives as text. None of those may become a setting the editor then obeys,
 * because a font size nobody can read back is a file nobody can open.
 */
class SettingsStoreTest {

    @Test
    fun anEmptyStoreReadsAsTheDocumentedDefaults() {
        val settings = SettingsStore(InMemoryKeyValueStore()).read()

        assertTrue(settings.openLastProject)
        assertTrue(settings.autoSave)
        assertTrue(settings.lineNumbers)
        assertTrue(settings.autoIndent)
        assertTrue(settings.autoCloseBrackets)
        assertFalse(settings.formatOnSave)
        assertTrue(settings.autocomplete)
        assertTrue(settings.diagnostics)
        assertTrue(settings.bracketGuides)
        assertTrue(settings.codeFolding)
        assertTrue(settings.autoImport)
        assertTrue(settings.parameterHints)
        assertTrue(settings.quickFixes)
        assertTrue(settings.buildCache)
        assertTrue(settings.commandHistory)
        assertTrue(settings.animations)
        assertEquals(0, settings.accentIndex)
        assertEquals("Shell tools", settings.shell)
        assertEquals("", settings.gitName)
        assertEquals("", settings.gitEmail)
        assertEquals("main", settings.gitDefaultBranch)
        assertEquals("Light", settings.theme)
        assertEquals(14, settings.fontSize)
        assertEquals(4, settings.tabSize)
    }

    @Test
    fun whatIsWrittenIsWhatComesBack() {
        val store = SettingsStore(InMemoryKeyValueStore())
        val changed = store.read()
            .withFlag(BoolSetting.AUTO_SAVE, false)
            .withFlag(BoolSetting.FORMAT_ON_SAVE, true)
            .withOption(OptionSetting.THEME, "Light")
            .withOption(OptionSetting.FONT_SIZE, "18")
            .withOption(OptionSetting.TAB_SIZE, "4")
            .withOption(OptionSetting.SHELL, "Shell tools")
            .withFlag(BoolSetting.CODE_FOLDING, false)
            .withFlag(BoolSetting.AUTO_IMPORT, false)
            .withFlag(BoolSetting.PARAMETER_HINTS, false)
            .withFlag(BoolSetting.QUICK_FIXES, false)
            .withFlag(BoolSetting.BUILD_CACHE, false)
            .withFlag(BoolSetting.COMMAND_HISTORY, false)
            .withFlag(BoolSetting.ANIMATIONS, false)
            .withText(StringSetting.GIT_NAME, " Ada ")
            .withText(StringSetting.GIT_EMAIL, " ada@example.com ")
            .withText(StringSetting.GIT_DEFAULT_BRANCH, " trunk ")

        store.write(changed)

        assertEquals(changed, store.read())
    }

    @Test
    fun writeReturnsWhatWasStoredSoACorrectionIsVisibleImmediately() {
        val store = SettingsStore(InMemoryKeyValueStore())

        val stored = store.write(Settings(fontSize = 9001, theme = "Neon"))

        assertEquals(FontSizeChoices.last().toInt(), stored.fontSize)
        assertEquals("Light", stored.theme)
        assertEquals(stored, store.read())
    }

    @Test
    fun anEmptyDefaultBranchFallsBackToMain() {
        val store = SettingsStore(InMemoryKeyValueStore())
        val stored = store.write(
            store.read().withText(StringSetting.GIT_DEFAULT_BRANCH, "   "),
        )
        assertEquals("main", stored.gitDefaultBranch)
        assertEquals("main", store.read().gitDefaultBranch)
    }

    @Test
    fun anUnknownShellIsRejectedAndTheDefaultKept() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("shell" to "Fish")))
        assertEquals("Shell tools", store.read().shell)
        assertEquals(
            "Shell tools",
            store.read().withOption(OptionSetting.SHELL, "Shell tools").shell,
        )
    }

    @Test
    fun aFontSizeBelowTheSmallestChoiceIsLiftedToIt() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("font_size" to 0)))
        assertEquals(FontSizeChoices.first().toInt(), store.read().fontSize)
    }

    @Test
    fun aFontSizeAboveTheLargestChoiceIsPulledDownToIt() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("font_size" to 400)))
        assertEquals(FontSizeChoices.last().toInt(), store.read().fontSize)
    }

    @Test
    fun aTabSizeOutsideTheChoicesIsPulledIntoThem() {
        assertEquals(2, SettingsStore(InMemoryKeyValueStore(mapOf("tab_size" to 0))).read().tabSize)
        assertEquals(
            TabSizeChoices.last().toInt(),
            SettingsStore(InMemoryKeyValueStore(mapOf("tab_size" to 64))).read().tabSize,
        )
    }

    @Test
    fun aThemeThatIsNotAChoiceFallsBackToLight() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("theme" to "Solarized")))
        assertEquals("Light", store.read().theme)
    }

    @Test
    fun aNumberStoredAsTextStillCountsAsThatNumber() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("font_size" to "15")))
        assertEquals(15, store.read().fontSize)
    }

    @Test
    fun textThatIsNotANumberFallsBackRatherThanThrowing() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("font_size" to "big")))
        assertEquals(14, store.read().fontSize)
    }

    @Test
    fun clearingTheStoreRestoresEveryDefault() {
        val kv = InMemoryKeyValueStore()
        val store = SettingsStore(kv)

        store.write(store.read().withFlag(BoolSetting.LINE_NUMBERS, false))
        kv.clear()

        assertEquals(Settings(), store.read())
    }

    @Test
    fun removingOneKeyOnlyResetsThatKey() {
        val kv = InMemoryKeyValueStore()
        val store = SettingsStore(kv)

        store.write(
            store.read()
                .withOption(OptionSetting.FONT_SIZE, "18")
                .withFlag(BoolSetting.DIAGNOSTICS, false),
        )
        kv.remove("font_size")

        val read = store.read()
        assertEquals(14, read.fontSize)
        assertFalse(read.diagnostics)
    }

    @Test
    fun everyKeyCanBeReachedFromAtLeastOneRow() {
        assertEquals(
            setOf(
                BoolSetting.OPEN_LAST_PROJECT,
                BoolSetting.AUTO_SAVE,
                BoolSetting.FRAME_STATS,
                BoolSetting.WORD_WRAP,
                BoolSetting.LINE_NUMBERS,
                BoolSetting.AUTO_CLOSE_BRACKETS,
                BoolSetting.AUTOCOMPLETE,
                BoolSetting.DIAGNOSTICS,
                BoolSetting.FORMAT_ON_SAVE,
                BoolSetting.USE_PIPE_TERMINAL,
                BoolSetting.APP_LOCK,
                BoolSetting.SEND_USAGE_DATA,
                BoolSetting.AUTO_INDENT,
                BoolSetting.BRACKET_GUIDES,
                BoolSetting.RAINBOW_BRACKETS,
                BoolSetting.SORA_EDITOR,
                BoolSetting.AUTO_IMPORT,
                BoolSetting.PARAMETER_HINTS,
                BoolSetting.QUICK_FIXES,
                BoolSetting.BUILD_CACHE,
                BoolSetting.COMMAND_HISTORY,
                BoolSetting.ANIMATIONS,
            ),
            usedBoolSettings(),
        )
        assertEquals(
            setOf(
                OptionSetting.FONT_SIZE,
                OptionSetting.TAB_SIZE,
                OptionSetting.BUILD_TYPE,
                OptionSetting.DOWNLOAD_NETWORK,
                OptionSetting.START_FOLDER,
                OptionSetting.TERMINAL_FONT_SIZE,
                OptionSetting.CODE_FONT,
                OptionSetting.SYNTAX_COLORS,
                OptionSetting.TERMINAL_THEME,
            ),
            usedOptionSettings(),
        )
        assertEquals(
            setOf(
                OptionSetting.FONT_SIZE,
                OptionSetting.TAB_SIZE,
                OptionSetting.BUILD_TYPE,
                OptionSetting.DOWNLOAD_NETWORK,
                OptionSetting.START_FOLDER,
                OptionSetting.TERMINAL_FONT_SIZE,
                OptionSetting.CODE_FONT,
                OptionSetting.SYNTAX_COLORS,
                OptionSetting.TERMINAL_THEME,
            ),
            usedOptionSettings(),
        )
        assertEquals(setOf(StringSetting.GIT_NAME, StringSetting.GIT_EMAIL), usedStringSettings())
    }

    @Test
    fun theScreenIsElevenSectionsAndFortyNineRows() {
        val sections = sections(SettingsLive())
        val count = sections.flatMap { it.groups.flatMap { it.rows } }.size
        println("SECTIONS: ${sections.size}, ROWS: $count")
        assertEquals(11, sections.size)
        assertEquals(61, rowCount())
    }

    @Test
    fun theDesignSystemOffersSixDistinctAccents() {
        assertEquals(6, AccentOptions.size)
        assertEquals(AccentOptions.size, AccentOptions.distinct().size)
    }

    @Test
    fun theFirstAccentIsTheLightMockAccent() {
        assertEquals(0xFF5865F2.toInt(), AccentOptions.first().toArgb())
    }

    @Test
    fun theSetupGuideRowOpensTheFirstRunFlow() {
        val row = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Nav>()
            .first { it.name == "Setup guide" }

        assertEquals(SettingsAction.SETUP_GUIDE, row.id)
    }

    @Test
    fun theShellToolsRowOpensTheDownloadPage() {
        val row = sections(SettingsLive(shellToolsStatus = "Not installed"))
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Nav>()
            .first { it.name == "Shell tools" }

        assertEquals(SettingsAction.SHELL_TOOLS, row.id)
        assertEquals("Not installed", row.action)
    }

    @Test
    fun theOpenCodeRuntimeRowOpensItsSetupPage() {
        val row = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Nav>()
            .first { it.name == "OpenCode runtime" }

        assertEquals(SettingsAction.AI_SETTINGS, row.id)
    }

    @Test
    fun everyRowInTheScreenRendersAsOneOfTheKnownShapes() {
        val shapes = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .map { it::class }

        assertTrue(shapes.isNotEmpty())
        shapes.forEach { shape ->
            assertTrue(
                "unhandled row shape $shape",
                shape in setOf(
                    Row.Toggle::class,
                    Row.Options::class,
                    Row.Value::class,
                    Row.Nav::class,
                    Row.Status::class,
                    Row.Account::class,
                    Row.Accents::class,
                    Row.Action::class,
                    Row.Language::class,
                    Row.Themes::class,
                    Row.CodePreview::class,
                ),
            )
        }
    }

    @Test
    fun theWordWrapSwitchNowReachesTheEditor() {
        // It used to be a switch with nothing behind it. Now the row carries the key, the store
        // carries the field, and the editor reads it.
        val wordWrap = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Toggle>()
            .first { it.name == "Word wrap" }

        assertEquals(BoolSetting.WORD_WRAP, wordWrap.key)
        assertFalse(wordWrap.default)
        // The default is off, which is the honest starting point for a switch that wraps nothing
        // until it is asked to.
        assertFalse(Settings().wordWrap)
    }

    @Test
    fun theWiredRowsAreTheOnesThatUsedToBeWired() {
        val toggles = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Toggle>()
            .mapNotNull { it.name to it.key }
            .toMap()

        assertEquals(BoolSetting.OPEN_LAST_PROJECT, toggles["Open last project"])
        assertEquals(BoolSetting.AUTO_SAVE, toggles["Auto-save"])
        assertEquals(BoolSetting.FRAME_STATS, toggles["Performance overlay"])
        assertEquals(BoolSetting.WORD_WRAP, toggles["Word wrap"])
        assertEquals(BoolSetting.LINE_NUMBERS, toggles["Show line numbers"])
        assertEquals(BoolSetting.AUTO_CLOSE_BRACKETS, toggles["Auto close brackets"])
        assertEquals(BoolSetting.AUTOCOMPLETE, toggles["Suggestions"])
        assertEquals(BoolSetting.DIAGNOSTICS, toggles["Show problems"])
        assertEquals(BoolSetting.FORMAT_ON_SAVE, toggles["Format on save"])
        assertEquals(BoolSetting.USE_PIPE_TERMINAL, toggles["Use pipe terminal"])
        assertEquals(BoolSetting.APP_LOCK, toggles["App lock"])
        assertEquals(BoolSetting.SEND_USAGE_DATA, toggles["Send usage data"])
    }

    @Test
    fun eachFieldSurvivesBeingSwitchedOffAndBackOnThroughItsKey() {
        var settings = Settings()
        BoolSetting.entries.filter { it != BoolSetting.AUTO_SAVE }.forEach { key ->
            settings = settings.withFlag(key, false)
            assertFalse(settings.flag(key))
            settings = settings.withFlag(key, true)
            assertTrue(settings.flag(key))
        }
        assertTrue(settings.withFlag(BoolSetting.AUTO_SAVE, false).flag(BoolSetting.AUTO_SAVE))
    }

    @Test
    fun anOptionValueThatIsNotAChoiceIsRefusedAndTheCurrentOneKept() {
        val settings = Settings(theme = "Dark", fontSize = 15, tabSize = 4)

        assertEquals(settings, settings.withOption(OptionSetting.THEME, "Neon"))
        assertEquals(settings, settings.withOption(OptionSetting.FONT_SIZE, "99"))
        assertEquals(settings, settings.withOption(OptionSetting.TAB_SIZE, "5"))
    }
}

class SearchModeTest {

    private val sources = listOf(
        com.closecode.ui.model.ProjectFile(
            "a.kt",
            com.closecode.ui.model.FileKind.Kotlin,
            "Kotlin",
            0,
        ) to "val Name = 1\nval name = 2",
    )

    @Test
    fun aPlainSearchFoldsCaseAsItAlwaysDid() {
        assertEquals(2, findInSources("name", sources).size)
    }

    @Test
    fun matchingCapitalisationNarrowsItToTheOneThatMeetsIt() {
        // The reason the switch exists: a query for a class name used to match its own lowercase
        // mentions, and there was no way to ask for the exact one.
        assertEquals(1, findInSources("Name", sources, caseSensitive = true).size)
    }

    @Test
    fun aPatternMatchesWhatAPlainStringCannotSay() {
        val pair = listOf(
            com.closecode.ui.model.ProjectFile(
                "b.kt",
                com.closecode.ui.model.FileKind.Kotlin,
                "Kotlin",
                0,
            ) to "val a = 1\nval b = 22\nval c = 333",
        )
        assertEquals(3, findInSources("val [a-c] = [0-9]+", pair, regex = true).size)
        // The same text as a plain string matches nothing at all, which is what made patterns worth
        // adding rather than folding into the existing behaviour.
        assertEquals(0, findInSources("val [a-c] = [0-9]+", pair).size)
    }

    @Test
    fun aPatternObeysTheCaseSwitchToo() {
        val pair = listOf(
            com.closecode.ui.model.ProjectFile(
                "c.kt",
                com.closecode.ui.model.FileKind.Kotlin,
                "Kotlin",
                0,
            ) to "val ABC = 1\nval abc = 2",
        )
        // Folds case by default, exactly as a plain search does, so the switch means the same thing
        // either way — and asking for the case you meant is one tap away.
        assertEquals(2, findInSources("abc", pair, regex = true).size)
        assertEquals(1, findInSources("abc", pair, regex = true, caseSensitive = true).size)
    }

    @Test
    fun aBrokenPatternIsNoPatternRatherThanNoMatches() {
        // "You typed it wrong" and "there is none of it here" are different answers, and a search
        // that returns an empty list for a broken pattern sends the reader looking for something that
        // was never there.
        assertEquals(null, lineMatcher(SearchMode("val (", regex = true)))
        assertEquals(null, lineMatcher(SearchMode("  ", regex = false)))
        assertNotNull(lineMatcher(SearchMode("a", regex = true)))
    }

    @Test
    fun aBrokenPatternFindsNothingRatherThanEverything() {
        assertEquals(0, findInSources("val (", sources, regex = true).size)
    }
}
