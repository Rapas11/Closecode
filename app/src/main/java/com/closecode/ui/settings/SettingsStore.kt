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
    BRACKET_GUIDES,
    WORD_WRAP,
    RAINBOW_BRACKETS,
    FRAME_STATS,
    CODE_FOLDING,
    AUTO_IMPORT,
    PARAMETER_HINTS,
    QUICK_FIXES,
    BUILD_CACHE,
    COMMAND_HISTORY,
    ANIMATIONS,
    PERFORMANCE_OVERLAY,
    USE_PIPE_TERMINAL,
    APP_LOCK,
    SEND_USAGE_DATA,
}

/** Which choice-based field of [Settings] a settings row edits. */
enum class OptionSetting {
    THEME,
    FONT_SIZE,
    TAB_SIZE,
    TERMINAL_THEME,
    TERMINAL_FONT_SIZE,
    SHELL,
    BUILD_TYPE,
    DOWNLOAD_NETWORK,
    START_FOLDER,
    CODE_FONT,
    SYNTAX_COLORS,
}

/** Which free-text field of [Settings] a settings row edits. */
enum class StringSetting {
    GIT_NAME,
    GIT_EMAIL,
    GIT_DEFAULT_BRANCH,
}

/**
 * The colours a terminal can draw.
 *
 * "Closecode" is the default because it is the one that already agrees with everything else on
 * screen; the other four are the schemes a reader is likely to already know. Each is a palette the
 * ANSI parser resolves its sixteen colours against, so a program that asks for red gets that
 * theme's red rather than the editor's error colour.
 */
val TerminalThemeChoices = listOf("Closecode", "Dracula", "One Dark", "Monokai", "Matrix")

/** Terminal font sizes in points. Small enough to fit more of a line, large enough to read on glass. */
val TerminalFontSizeChoices = listOf("9", "10", "11", "12", "13", "14", "16", "18")

/** The choices each [OptionSetting] accepts. Anything else read from storage is rejected. */
val ThemeChoices = listOf("Light", "Dark", "System")
val ShellChoices = listOf("System", "Shell tools")
val FontSizeChoices = (10..22).map { it.toString() }
val TabSizeChoices = listOf("2", "4", "8")
val BuildTypeChoices = listOf("Debug", "Release")
val DownloadNetworkChoices = listOf("Wi-Fi only", "Wi-Fi and mobile")
val StartFolderChoices = listOf("Project root", "Home")
val CodeFontChoices = listOf("JetBrains Mono")
val SyntaxColorChoices = listOf("Default light", "Default dark")

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
    val bracketGuides: Boolean = true,
    val wordWrap: Boolean = false,
    val rainbowBrackets: Boolean = false,
    val frameStats: Boolean = false,
    val codeFolding: Boolean = true,
    val autoImport: Boolean = true,
    val parameterHints: Boolean = true,
    val quickFixes: Boolean = true,
    val buildCache: Boolean = true,
    val commandHistory: Boolean = true,
    val animations: Boolean = true,
    val performanceOverlay: Boolean = false,
    val usePipeTerminal: Boolean = false,
    val appLock: Boolean = false,
    val sendUsageData: Boolean = false,
    val accentIndex: Int = 0,
    val shell: String = "Shell tools",
    val buildType: String = "Debug",
    val downloadNetwork: String = "Wi-Fi and mobile",
    val startFolder: String = "Project root",
    val codeFont: String = "JetBrains Mono",
    val syntaxColors: String = "Default light",
    val gitName: String = "",
    val gitEmail: String = "",
    val gitDefaultBranch: String = "main",
    val theme: String = "Light",
    val fontSize: Int = 14,
    val tabSize: Int = 4,
    val terminalTheme: String = "Closecode",
    val terminalFontSize: Int = 12,
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
        BoolSetting.BRACKET_GUIDES -> bracketGuides
        BoolSetting.WORD_WRAP -> wordWrap
        BoolSetting.RAINBOW_BRACKETS -> rainbowBrackets
        BoolSetting.FRAME_STATS -> frameStats
        BoolSetting.CODE_FOLDING -> codeFolding
        BoolSetting.AUTO_IMPORT -> autoImport
        BoolSetting.PARAMETER_HINTS -> parameterHints
        BoolSetting.QUICK_FIXES -> quickFixes
        BoolSetting.BUILD_CACHE -> buildCache
        BoolSetting.COMMAND_HISTORY -> commandHistory
        BoolSetting.ANIMATIONS -> animations
        BoolSetting.PERFORMANCE_OVERLAY -> performanceOverlay
        BoolSetting.USE_PIPE_TERMINAL -> usePipeTerminal
        BoolSetting.APP_LOCK -> appLock
        BoolSetting.SEND_USAGE_DATA -> sendUsageData
    }

    fun withFlag(key: BoolSetting, value: Boolean): Settings = when (key) {
        BoolSetting.OPEN_LAST_PROJECT -> copy(openLastProject = value)
        BoolSetting.AUTO_SAVE -> copy(autoSave = true)
        BoolSetting.LINE_NUMBERS -> copy(lineNumbers = value)
        BoolSetting.AUTO_INDENT -> copy(autoIndent = value)
        BoolSetting.AUTO_CLOSE_BRACKETS -> copy(autoCloseBrackets = value)
        BoolSetting.FORMAT_ON_SAVE -> copy(formatOnSave = value)
        BoolSetting.AUTOCOMPLETE -> copy(autocomplete = value)
        BoolSetting.DIAGNOSTICS -> copy(diagnostics = value)
        BoolSetting.BRACKET_GUIDES -> copy(bracketGuides = value)
        BoolSetting.WORD_WRAP -> copy(wordWrap = value)
        BoolSetting.RAINBOW_BRACKETS -> copy(rainbowBrackets = value)
        BoolSetting.FRAME_STATS -> copy(frameStats = value)
        BoolSetting.CODE_FOLDING -> copy(codeFolding = value)
        BoolSetting.AUTO_IMPORT -> copy(autoImport = value)
        BoolSetting.PARAMETER_HINTS -> copy(parameterHints = value)
        BoolSetting.QUICK_FIXES -> copy(quickFixes = value)
        BoolSetting.BUILD_CACHE -> copy(buildCache = value)
        BoolSetting.COMMAND_HISTORY -> copy(commandHistory = value)
        BoolSetting.ANIMATIONS -> copy(animations = value)
        BoolSetting.PERFORMANCE_OVERLAY -> copy(performanceOverlay = value)
        BoolSetting.USE_PIPE_TERMINAL -> copy(usePipeTerminal = value)
        BoolSetting.APP_LOCK -> copy(appLock = value)
        BoolSetting.SEND_USAGE_DATA -> copy(sendUsageData = value)
    }

    fun option(key: OptionSetting): String = when (key) {
        OptionSetting.THEME -> theme
        OptionSetting.FONT_SIZE -> fontSize.toString()
        OptionSetting.TAB_SIZE -> tabSize.toString()
        OptionSetting.TERMINAL_THEME -> terminalTheme
        OptionSetting.TERMINAL_FONT_SIZE -> terminalFontSize.toString()
        OptionSetting.SHELL -> shell
        OptionSetting.BUILD_TYPE -> buildType
        OptionSetting.DOWNLOAD_NETWORK -> downloadNetwork
        OptionSetting.START_FOLDER -> startFolder
        OptionSetting.CODE_FONT -> codeFont
        OptionSetting.SYNTAX_COLORS -> syntaxColors
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
        OptionSetting.TERMINAL_THEME -> if (value in TerminalThemeChoices) {
            copy(terminalTheme = value)
        } else {
            this
        }

        OptionSetting.TERMINAL_FONT_SIZE -> if (value in TerminalFontSizeChoices) {
            copy(terminalFontSize = value.toInt())
        } else {
            this
        }
        OptionSetting.SHELL -> if (value in ShellChoices) copy(shell = value) else this
        OptionSetting.BUILD_TYPE -> if (value in BuildTypeChoices) copy(buildType = value) else this
        OptionSetting.DOWNLOAD_NETWORK -> if (value in DownloadNetworkChoices) {
            copy(downloadNetwork = value)
        } else {
            this
        }
        OptionSetting.START_FOLDER -> if (value in StartFolderChoices) {
            copy(startFolder = value)
        } else {
            this
        }
        OptionSetting.CODE_FONT -> if (value in CodeFontChoices) copy(codeFont = value) else this
        OptionSetting.SYNTAX_COLORS -> if (value in SyntaxColorChoices) {
            copy(syntaxColors = value)
        } else {
            this
        }
    }

    fun text(key: StringSetting): String = when (key) {
        StringSetting.GIT_NAME -> gitName
        StringSetting.GIT_EMAIL -> gitEmail
        StringSetting.GIT_DEFAULT_BRANCH -> gitDefaultBranch
    }

    fun withText(key: StringSetting, value: String): Settings = when (key) {
        StringSetting.GIT_NAME -> copy(gitName = value.trim())
        StringSetting.GIT_EMAIL -> copy(gitEmail = value.trim())
        StringSetting.GIT_DEFAULT_BRANCH -> copy(
            gitDefaultBranch = value.trim().ifEmpty { "main" },
        )
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
        // Auto-save is part of the editor, not a preference: the row shows On and the store
        // always reports it on, so a stale older value cannot turn it off behind the screen.
        autoSave = true,
        lineNumbers = kv.getBoolean(KEY_LINE_NUMBERS, Settings().lineNumbers),
        autoIndent = kv.getBoolean(KEY_AUTO_INDENT, Settings().autoIndent),
        autoCloseBrackets = kv.getBoolean(KEY_AUTO_CLOSE, Settings().autoCloseBrackets),
        formatOnSave = kv.getBoolean(KEY_FORMAT_ON_SAVE, Settings().formatOnSave),
        autocomplete = kv.getBoolean(KEY_AUTOCOMPLETE, Settings().autocomplete),
        diagnostics = kv.getBoolean(KEY_DIAGNOSTICS, Settings().diagnostics),
        bracketGuides = kv.getBoolean(KEY_BRACKET_GUIDES, Settings().bracketGuides),
        wordWrap = kv.getBoolean(KEY_WORD_WRAP, Settings().wordWrap),
        rainbowBrackets = kv.getBoolean(KEY_RAINBOW_BRACKETS, Settings().rainbowBrackets),
        frameStats = kv.getBoolean(KEY_FRAME_STATS, Settings().frameStats),
        codeFolding = kv.getBoolean(KEY_CODE_FOLDING, Settings().codeFolding),
        autoImport = kv.getBoolean(KEY_AUTO_IMPORT, Settings().autoImport),
        parameterHints = kv.getBoolean(KEY_PARAMETER_HINTS, Settings().parameterHints),
        quickFixes = kv.getBoolean(KEY_QUICK_FIXES, Settings().quickFixes),
        buildCache = kv.getBoolean(KEY_BUILD_CACHE, Settings().buildCache),
        commandHistory = kv.getBoolean(KEY_COMMAND_HISTORY, Settings().commandHistory),
        animations = kv.getBoolean(KEY_ANIMATIONS, Settings().animations),
        performanceOverlay = kv.getBoolean(KEY_PERFORMANCE_OVERLAY, Settings().performanceOverlay),
        // The terminal reads the historical integer key. Read it first so a change made
        // from the terminal's own menu is what this screen reports, then fall back to the
        // settings key when the legacy key has never been written.
        usePipeTerminal = kv.getInt(
            KEY_TERMINAL_USE_PIPE_LEGACY,
            if (kv.getBoolean(KEY_USE_PIPE_TERMINAL, Settings().usePipeTerminal)) 1 else 0,
        ) == 1,
        appLock = kv.getBoolean(KEY_APP_LOCK, Settings().appLock),
        sendUsageData = kv.getBoolean(KEY_SEND_USAGE_DATA, Settings().sendUsageData),
        accentIndex = kv.getInt(KEY_ACCENT, Settings().accentIndex)
            .coerceIn(0, AccentOptions.lastIndex),
        shell = kv.getString(KEY_SHELL, Settings().shell)
            ?.takeIf { it in ShellChoices }
            ?: Settings().shell,
        buildType = kv.getString(KEY_BUILD_TYPE, Settings().buildType)
            ?.takeIf { it in BuildTypeChoices }
            ?: Settings().buildType,
        downloadNetwork = kv.getString(KEY_DOWNLOAD_NETWORK, Settings().downloadNetwork)
            ?.takeIf { it in DownloadNetworkChoices }
            ?: Settings().downloadNetwork,
        startFolder = kv.getString(KEY_START_FOLDER, Settings().startFolder)
            ?.takeIf { it in StartFolderChoices }
            ?: Settings().startFolder,
        codeFont = kv.getString(KEY_CODE_FONT, Settings().codeFont)
            ?.takeIf { it in CodeFontChoices }
            ?: Settings().codeFont,
        syntaxColors = kv.getString(KEY_SYNTAX_COLORS, Settings().syntaxColors)
            ?.takeIf { it in SyntaxColorChoices }
            ?: Settings().syntaxColors,
        gitName = kv.getString(KEY_GIT_NAME, Settings().gitName).orEmpty(),
        gitEmail = kv.getString(KEY_GIT_EMAIL, Settings().gitEmail).orEmpty(),
        gitDefaultBranch = kv.getString(KEY_GIT_DEFAULT_BRANCH, Settings().gitDefaultBranch)
            ?.takeIf { it.isNotBlank() }
            ?: Settings().gitDefaultBranch,
        theme = kv.getString(KEY_THEME, Settings().theme)
            ?.takeIf { it in ThemeChoices }
            ?: Settings().theme,
        fontSize = kv.getInt(KEY_FONT_SIZE, Settings().fontSize)
            .coerceIn(FontSizeChoices.first().toInt(), FontSizeChoices.last().toInt()),
        tabSize = kv.getInt(KEY_TAB_SIZE, Settings().tabSize)
            .coerceIn(TabSizeChoices.first().toInt(), TabSizeChoices.last().toInt()),
        terminalTheme = kv.getString(KEY_TERMINAL_THEME, Settings().terminalTheme)
            ?.takeIf { it in TerminalThemeChoices }
            ?: Settings().terminalTheme,
        terminalFontSize = kv.getInt(KEY_TERMINAL_FONT_SIZE, Settings().terminalFontSize)
            .coerceIn(TerminalFontSizeChoices.first().toInt(), TerminalFontSizeChoices.last().toInt()),
    )

    /**
     * Stores [settings] and returns what was actually stored. Returning the value rather than Unit
     * means the caller can hold the sanitised settings — a corrupt font size is clamped here, not
     * on the next launch — and it keeps the on-screen state and the persisted state the same.
     */
    fun write(settings: Settings): Settings {
        kv.putBoolean(KEY_OPEN_LAST_PROJECT, settings.openLastProject)
        kv.putBoolean(KEY_AUTO_SAVE, true)
        kv.putBoolean(KEY_LINE_NUMBERS, settings.lineNumbers)
        kv.putBoolean(KEY_AUTO_INDENT, settings.autoIndent)
        kv.putBoolean(KEY_AUTO_CLOSE, settings.autoCloseBrackets)
        kv.putBoolean(KEY_FORMAT_ON_SAVE, settings.formatOnSave)
        kv.putBoolean(KEY_AUTOCOMPLETE, settings.autocomplete)
        kv.putBoolean(KEY_DIAGNOSTICS, settings.diagnostics)
        kv.putBoolean(KEY_BRACKET_GUIDES, settings.bracketGuides)
        kv.putBoolean(KEY_WORD_WRAP, settings.wordWrap)
        kv.putBoolean(KEY_RAINBOW_BRACKETS, settings.rainbowBrackets)
        kv.putBoolean(KEY_FRAME_STATS, settings.frameStats)
        kv.putBoolean(KEY_CODE_FOLDING, settings.codeFolding)
        kv.putBoolean(KEY_AUTO_IMPORT, settings.autoImport)
        kv.putBoolean(KEY_PARAMETER_HINTS, settings.parameterHints)
        kv.putBoolean(KEY_QUICK_FIXES, settings.quickFixes)
        kv.putBoolean(KEY_BUILD_CACHE, settings.buildCache)
        kv.putBoolean(KEY_COMMAND_HISTORY, settings.commandHistory)
        kv.putBoolean(KEY_ANIMATIONS, settings.animations)
        kv.putBoolean(KEY_PERFORMANCE_OVERLAY, settings.performanceOverlay)
        kv.putBoolean(KEY_USE_PIPE_TERMINAL, settings.usePipeTerminal)
        kv.putInt(KEY_TERMINAL_USE_PIPE_LEGACY, if (settings.usePipeTerminal) 1 else 0)
        kv.putBoolean(KEY_APP_LOCK, settings.appLock)
        kv.putBoolean(KEY_SEND_USAGE_DATA, settings.sendUsageData)
        kv.putInt(KEY_ACCENT, settings.accentIndex)
        kv.putString(KEY_SHELL, settings.shell)
        kv.putString(KEY_BUILD_TYPE, settings.buildType)
        kv.putString(KEY_DOWNLOAD_NETWORK, settings.downloadNetwork)
        kv.putString(KEY_START_FOLDER, settings.startFolder)
        kv.putString(KEY_CODE_FONT, settings.codeFont)
        kv.putString(KEY_SYNTAX_COLORS, settings.syntaxColors)
        kv.putString(KEY_GIT_NAME, settings.gitName)
        kv.putString(KEY_GIT_EMAIL, settings.gitEmail)
        kv.putString(KEY_GIT_DEFAULT_BRANCH, settings.gitDefaultBranch)
        kv.putString(KEY_THEME, settings.theme)
        kv.putInt(KEY_FONT_SIZE, settings.fontSize)
        kv.putInt(KEY_TAB_SIZE, settings.tabSize)
        kv.putString(KEY_TERMINAL_THEME, settings.terminalTheme)
        kv.putInt(KEY_TERMINAL_FONT_SIZE, settings.terminalFontSize)
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
        const val KEY_BRACKET_GUIDES = "bracket_guides"
        const val KEY_WORD_WRAP = "word_wrap"
        const val KEY_RAINBOW_BRACKETS = "rainbow_brackets"
        const val KEY_FRAME_STATS = "frame_stats"
        const val KEY_CODE_FOLDING = "code_folding"
        const val KEY_AUTO_IMPORT = "auto_import"
        const val KEY_PARAMETER_HINTS = "parameter_hints"
        const val KEY_QUICK_FIXES = "quick_fixes"
        const val KEY_BUILD_CACHE = "build_cache"
        const val KEY_COMMAND_HISTORY = "command_history"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_PERFORMANCE_OVERLAY = "performance_overlay"
        const val KEY_USE_PIPE_TERMINAL = "use_pipe_terminal"
        const val KEY_TERMINAL_USE_PIPE_LEGACY = "terminal_use_pipe"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_SEND_USAGE_DATA = "send_usage_data"
        const val KEY_ACCENT = "accent"
        const val KEY_BUILD_TYPE = "build_type"
        const val KEY_DOWNLOAD_NETWORK = "download_network"
        const val KEY_START_FOLDER = "start_folder"
        const val KEY_CODE_FONT = "code_font"
        const val KEY_SYNTAX_COLORS = "syntax_colors"
        const val KEY_SHELL = "shell"
        const val KEY_GIT_NAME = "git_name"
        const val KEY_GIT_EMAIL = "git_email"
        const val KEY_GIT_DEFAULT_BRANCH = "git_default_branch"
        const val KEY_THEME = "theme"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_TAB_SIZE = "tab_size"
        const val KEY_TERMINAL_THEME = "terminal_theme"
        const val KEY_TERMINAL_FONT_SIZE = "terminal_font_size"
    }
}

/**
 * The settings in force right now, wherever the app is drawing.
 *
 * The editor reads it directly rather than taking eleven more parameters, so a row changed on the
 * settings screen is in force the moment the screen is left — no restart, no reload.
 */
val LocalSettings = staticCompositionLocalOf { Settings() }

/**
 * Whether motion is wanted this composition. Screens read this instead of asking the settings
 * store directly so one switch reaches every panel, sheet and control at once.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current
    return remember(context) {
        val prefs = context.applicationContext
            .getSharedPreferences("closecode_settings", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("design_light_v3", false)) {
            prefs.edit()
                .putString("theme", "Light")
                .putInt("font_size", 14)
                .putInt("tab_size", 4)
                .putBoolean("word_wrap", false)
                .putBoolean("design_light_v3", true)
                .apply()
        }
        SettingsStore(SharedPreferencesKeyValueStore(context))
    }
}
