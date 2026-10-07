package com.closecode.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("System", settings.theme)
        assertEquals(13, settings.fontSize)
        assertEquals(2, settings.tabSize)
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

        store.write(changed)

        assertEquals(changed, store.read())
    }

    @Test
    fun writeReturnsWhatWasStoredSoACorrectionIsVisibleImmediately() {
        val store = SettingsStore(InMemoryKeyValueStore())

        val stored = store.write(Settings(fontSize = 9001, theme = "Neon"))

        assertEquals(FontSizeChoices.last().toInt(), stored.fontSize)
        assertEquals("System", stored.theme)
        assertEquals(stored, store.read())
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
    fun aThemeThatIsNotAChoiceFallsBackToSystem() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("theme" to "Solarized")))
        assertEquals("System", store.read().theme)
    }

    @Test
    fun aNumberStoredAsTextStillCountsAsThatNumber() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("font_size" to "15")))
        assertEquals(15, store.read().fontSize)
    }

    @Test
    fun textThatIsNotANumberFallsBackRatherThanThrowing() {
        val store = SettingsStore(InMemoryKeyValueStore(mapOf("font_size" to "big")))
        assertEquals(13, store.read().fontSize)
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
        assertEquals(13, read.fontSize)
        assertFalse(read.diagnostics)
    }

    @Test
    fun everyKeyCanBeReachedFromAtLeastOneRow() {
        assertEquals(BoolSetting.entries.toSet(), usedBoolSettings())
        assertEquals(OptionSetting.entries.toSet(), usedOptionSettings())
    }

    @Test
    fun theScreenIsFiveSectionsAndSixteenRows() {
        assertEquals(5, sections(SettingsLive()).size)
        assertEquals(16, rowCount())
    }

    @Test
    fun eachFieldSurvivesBeingSwitchedOffAndBackOnThroughItsKey() {
        var settings = Settings()
        BoolSetting.entries.forEach { key ->
            settings = settings.withFlag(key, false)
            assertFalse(settings.flag(key))
            settings = settings.withFlag(key, true)
            assertTrue(settings.flag(key))
        }
    }

    @Test
    fun anOptionValueThatIsNotAChoiceIsRefusedAndTheCurrentOneKept() {
        val settings = Settings(theme = "Dark", fontSize = 15, tabSize = 4)

        assertEquals(settings, settings.withOption(OptionSetting.THEME, "Neon"))
        assertEquals(settings, settings.withOption(OptionSetting.FONT_SIZE, "14"))
        assertEquals(settings, settings.withOption(OptionSetting.TAB_SIZE, "5"))
    }
}
