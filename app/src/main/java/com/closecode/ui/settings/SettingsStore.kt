package com.closecode.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

/** Which on/off field of [Settings] a settings row edits. */
enum class BoolSetting {
    OPEN_LAST_PROJECT,
    AUTO_SAVE,
    LINE_NUMBERS,
    AUTO_INDENT,
    AUTO_CLOSE_BRACKETS,
    FORMAT_ON_SAVE,
    AUTOCOMPLETE,
    DIAGNOSTICS,
}

/** Which choice-based field of [Settings] a settings row edits. */
enum class OptionSetting {
    THEME,
    FONT_SIZE,
    TAB_SIZE,
}

/** The choices each [OptionSetting] accepts. Anything else read from storage is rejected. */
val ThemeChoices = listOf("System", "Dark", "Light")
val FontSizeChoices = listOf("11", "12", "13", "15", "18")
val TabSizeChoices = listOf("2", "3", "4", "8")

/**
 * Every knob the app actually honours.
 *
 * There is no row in the settings screen that is not a field here, and no field here that the app
 * ignores: a setting that reads back its own default forever is a lie about what the product does.
 * The two exceptions are the rows that show live facts instead — the project root, the sizes on
 * disk, the version — and those carry no key at all.
 */
data class Settings(
    val openLastProject: Boolean = true,
    val autoSave: Boolean = true,
    val lineNumbers: Boolean = true,
    val autoIndent: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val formatOnSave: Boolean = false,
    val autocomplete: Boolean = true,
    val diagnostics: Boolean = true,
    val theme: String = "System",
    val fontSize: Int = 13,
    val tabSize: Int = 2,
) {
    fun flag(key: BoolSetting): Boolean = when (key) {
        BoolSetting.OPEN_LAST_PROJECT -> openLastProject
        BoolSetting.AUTO_SAVE -> autoSave
        BoolSetting.LINE_NUMBERS -> lineNumbers
        BoolSetting.AUTO_INDENT -> autoIndent
        BoolSetting.AUTO_CLOSE_BRACKETS -> autoCloseBrackets
        BoolSetting.FORMAT_ON_SAVE -> formatOnSave
        BoolSetting.AUTOCOMPLETE -> autocomplete
        BoolSetting.DIAGNOSTICS -> diagnostics
    }

    fun withFlag(key: BoolSetting, value: Boolean): Settings = when (key) {
        BoolSetting.OPEN_LAST_PROJECT -> copy(openLastProject = value)
        BoolSetting.AUTO_SAVE -> copy(autoSave = value)
        BoolSetting.LINE_NUMBERS -> copy(lineNumbers = value)
        BoolSetting.AUTO_INDENT -> copy(autoIndent = value)
        BoolSetting.AUTO_CLOSE_BRACKETS -> copy(autoCloseBrackets = value)
        BoolSetting.FORMAT_ON_SAVE -> copy(formatOnSave = value)
        BoolSetting.AUTOCOMPLETE -> copy(autocomplete = value)
        BoolSetting.DIAGNOSTICS -> copy(diagnostics = value)
    }

    fun option(key: OptionSetting): String = when (key) {
        OptionSetting.THEME -> theme
        OptionSetting.FONT_SIZE -> fontSize.toString()
        OptionSetting.TAB_SIZE -> tabSize.toString()
    }

    /**
     * A value that is not one of [ThemeChoices] / [FontSizeChoices] / [TabSizeChoices] — a pref
     * written by an older build, or hand-edited — is refused and the current value kept, because
     * a corrupt preference must never be able to set the editor to an unusable size.
     */
    fun withOption(key: OptionSetting, value: String): Settings = when (key) {
        OptionSetting.THEME -> if (value in ThemeChoices) copy(theme = value) else this
        OptionSetting.FONT_SIZE -> if (value in FontSizeChoices) {
            copy(fontSize = value.toInt())
        } else {
            this
        }

        OptionSetting.TAB_SIZE -> if (value in TabSizeChoices) {
            copy(tabSize = value.toInt())
        } else {
            this
        }
    }
}

/**
 * Reads and writes [Settings] through a [KeyValueStore].
 *
 * Writing is the whole story: there is no cache in front of the store, so a value read back after
 * a write is the value that was written, on any thread of the app that opens its own store.
 */
class SettingsStore(private val kv: KeyValueStore) {

    fun read(): Settings = Settings(
        openLastProject = kv.getBoolean(KEY_OPEN_LAST_PROJECT, Settings().openLastProject),
        autoSave = kv.getBoolean(KEY_AUTO_SAVE, Settings().autoSave),
        lineNumbers = kv.getBoolean(KEY_LINE_NUMBERS, Settings().lineNumbers),
        autoIndent = kv.getBoolean(KEY_AUTO_INDENT, Settings().autoIndent),
        autoCloseBrackets = kv.getBoolean(KEY_AUTO_CLOSE, Settings().autoCloseBrackets),
        formatOnSave = kv.getBoolean(KEY_FORMAT_ON_SAVE, Settings().formatOnSave),
        autocomplete = kv.getBoolean(KEY_AUTOCOMPLETE, Settings().autocomplete),
        diagnostics = kv.getBoolean(KEY_DIAGNOSTICS, Settings().diagnostics),
        theme = kv.getString(KEY_THEME, Settings().theme)
            ?.takeIf { it in ThemeChoices }
            ?: Settings().theme,
        fontSize = kv.getInt(KEY_FONT_SIZE, Settings().fontSize)
            .coerceIn(FontSizeChoices.first().toInt(), FontSizeChoices.last().toInt()),
        tabSize = kv.getInt(KEY_TAB_SIZE, Settings().tabSize)
            .coerceIn(TabSizeChoices.first().toInt(), TabSizeChoices.last().toInt()),
    )

    /**
     * Stores [settings] and returns what was actually stored. Returning the value rather than Unit
     * means the caller can hold the sanitised settings — a corrupt font size is clamped here, not
     * on the next launch — and it keeps the on-screen state and the persisted state the same.
     */
    fun write(settings: Settings): Settings {
        kv.putBoolean(KEY_OPEN_LAST_PROJECT, settings.openLastProject)
        kv.putBoolean(KEY_AUTO_SAVE, settings.autoSave)
        kv.putBoolean(KEY_LINE_NUMBERS, settings.lineNumbers)
        kv.putBoolean(KEY_AUTO_INDENT, settings.autoIndent)
        kv.putBoolean(KEY_AUTO_CLOSE, settings.autoCloseBrackets)
        kv.putBoolean(KEY_FORMAT_ON_SAVE, settings.formatOnSave)
        kv.putBoolean(KEY_AUTOCOMPLETE, settings.autocomplete)
        kv.putBoolean(KEY_DIAGNOSTICS, settings.diagnostics)
        kv.putString(KEY_THEME, settings.theme)
        kv.putInt(KEY_FONT_SIZE, settings.fontSize)
        kv.putInt(KEY_TAB_SIZE, settings.tabSize)
        return read()
    }

    private companion object {
        const val KEY_OPEN_LAST_PROJECT = "open_last_project"
        const val KEY_AUTO_SAVE = "auto_save"
        const val KEY_LINE_NUMBERS = "line_numbers"
        const val KEY_AUTO_INDENT = "auto_indent"
        const val KEY_AUTO_CLOSE = "auto_close_brackets"
        const val KEY_FORMAT_ON_SAVE = "format_on_save"
        const val KEY_AUTOCOMPLETE = "autocomplete"
        const val KEY_DIAGNOSTICS = "diagnostics"
        const val KEY_THEME = "theme"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_TAB_SIZE = "tab_size"
    }
}

/**
 * The settings in force right now, wherever the app is drawing.
 *
 * The editor reads it directly rather than taking eleven more parameters, so a row changed on the
 * settings screen is in force the moment the screen is left — no restart, no reload.
 */
val LocalSettings = staticCompositionLocalOf { Settings() }

@Composable
fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current
    return remember(context) { SettingsStore(SharedPreferencesKeyValueStore(context)) }
}
