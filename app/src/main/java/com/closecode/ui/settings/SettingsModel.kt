package com.closecode.ui.settings

import androidx.compose.runtime.Immutable

/**
 * One row of the settings screen.
 *
 * Every variant is either a control bound to a field of [Settings] — so it reads and writes a real
 * preference — or a plain read-out of something the app measures. There is no row here that merely
 * looks like a setting.
 */
sealed interface Row {
    val name: String
    val description: String?

    /** An on/off control bound to [key]. The screen resolves the current value from [Settings]. */
    data class Toggle(
        override val name: String,
        override val description: String? = null,
        val key: BoolSetting,
    ) : Row

    /** A fixed list of choices bound to [key]; [unit] is appended to the stored value on screen. */
    data class Options(
        override val name: String,
        override val description: String? = null,
        val key: OptionSetting,
        val choices: List<String>,
        val unit: String = "",
    ) : Row

    /** A measured fact — a path, a size, a version — with nothing to edit. */
    data class Value(
        override val name: String,
        override val description: String? = null,
        val value: String,
    ) : Row

    /** Something the screen can do rather than show. */
    data class Action(
        override val name: String,
        override val description: String? = null,
        val action: String,
        val id: SettingsAction,
        val danger: Boolean = false,
    ) : Row
}

/** The actions [Row.Action] can ask for. Named, so nothing matches on a row's display text. */
enum class SettingsAction {
    CLEAR_CACHES,
}

@Immutable
data class Group(val title: String, val rows: List<Row>)

@Immutable
data class Section(
    val id: String,
    val label: String,
    val icon: String,
    val title: String,
    val blurb: String,
    val groups: List<Group>,
)

/**
 * What the app measures for the settings screen, off the main thread.
 *
 * These are facts rather than preferences, so they are not part of [Settings] and never persisted.
 */
@Immutable
data class SettingsLive(
    val projectRoot: String = "…",
    val projectsSize: String = "…",
    val cachesSize: String = "…",
    val version: String = "1.0.0",
)

/**
 * The settings screen: sixteen rows across five sections, all of them live.
 *
 * Built as a function of [live] rather than a constant, because three of the rows report something
 * the app measured for this session.
 */
fun sections(live: SettingsLive): List<Section> = listOf(
    Section(
        id = "general",
        label = "General",
        icon = "◎",
        title = "General",
        blurb = "Configure how Closecode behaves.",
        groups = listOf(
            Group(
                "Startup",
                listOf(
                    Row.Value(
                        "Default project location",
                        "Where new projects are created.",
                        live.projectRoot,
                    ),
                    Row.Toggle(
                        "Open last project",
                        "Reopen the project you were in when Closecode starts.",
                        BoolSetting.OPEN_LAST_PROJECT,
                    ),
                    Row.Toggle(
                        "Auto save",
                        "Write the file a moment after you stop typing.",
                        BoolSetting.AUTO_SAVE,
                    ),
                    Row.Options(
                        "Theme",
                        "Follow the system, or pick one.",
                        OptionSetting.THEME,
                        ThemeChoices,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "editor",
        label = "Editor",
        icon = "⌘",
        title = "Editor",
        blurb = "Customize the Closecode code editor.",
        groups = listOf(
            Group(
                "Text Editor",
                listOf(
                    Row.Options(
                        "Font size",
                        "Editor text size.",
                        OptionSetting.FONT_SIZE,
                        FontSizeChoices,
                        unit = " px",
                    ),
                    Row.Options(
                        "Tab size",
                        "Spaces the Tab key inserts.",
                        OptionSetting.TAB_SIZE,
                        TabSizeChoices,
                    ),
                    Row.Toggle("Line numbers", "Show the number gutter.", BoolSetting.LINE_NUMBERS),
                    Row.Toggle(
                        "Auto indent",
                        "Carry the indentation of the line above onto a new one.",
                        BoolSetting.AUTO_INDENT,
                    ),
                    Row.Toggle(
                        "Auto close brackets",
                        "Type ( and get (), with the caret in between.",
                        BoolSetting.AUTO_CLOSE_BRACKETS,
                    ),
                    Row.Toggle(
                        "Format on save",
                        "Tidy a Kotlin file as it is written to disk.",
                        BoolSetting.FORMAT_ON_SAVE,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "intelligence",
        label = "Code Intelligence",
        icon = "✦",
        title = "Code Intelligence",
        blurb = "Configure autocomplete and diagnostics.",
        groups = listOf(
            Group(
                "Code Intelligence",
                listOf(
                    Row.Toggle(
                        "Autocomplete",
                        "Show code completion suggestions as you type.",
                        BoolSetting.AUTOCOMPLETE,
                    ),
                    Row.Toggle(
                        "Diagnostics",
                        "Flag problems in the gutter and offer quick fixes.",
                        BoolSetting.DIAGNOSTICS,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "storage",
        label = "Storage",
        icon = "▤",
        title = "Storage",
        blurb = "View and manage Closecode storage.",
        groups = listOf(
            Group(
                "Storage Usage",
                listOf(
                    Row.Value("Projects", "Everything Closecode has on this device.", live.projectsSize),
                    Row.Value("Cache", "Temporary files. Deleting them is always safe.", live.cachesSize),
                ),
            ),
            Group(
                "",
                listOf(
                    Row.Action(
                        "Clear caches",
                        "Delete every temporary file Closecode has written.",
                        "Clear",
                        SettingsAction.CLEAR_CACHES,
                        danger = true,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "about",
        label = "About",
        icon = "ⓘ",
        title = "About",
        blurb = "Information about Closecode.",
        groups = listOf(
            Group(
                "Closecode",
                listOf(
                    Row.Value("Version", "This build of Closecode.", live.version),
                ),
            ),
        ),
    ),
)

fun sectionById(id: String): Section = sections(SettingsLive()).firstOrNull { it.id == id }
    ?: sections(SettingsLive()).first()

/**
 * Every key a settings row edits, for the test that keeps the two lists honest: a field nobody can
 * reach from the screen is a setting that will never be changed, and a row with no key is a lie.
 */
fun usedBoolSettings(): Set<BoolSetting> = sections(SettingsLive())
    .flatMap { section -> section.groups.flatMap { it.rows } }
    .mapNotNull { row -> (row as? Row.Toggle)?.key }
    .toSet()

fun usedOptionSettings(): Set<OptionSetting> = sections(SettingsLive())
    .flatMap { section -> section.groups.flatMap { it.rows } }
    .mapNotNull { row -> (row as? Row.Options)?.key }
    .toSet()

/** Total rows on the screen, whatever section they sit in. */
fun rowCount(): Int = sections(SettingsLive())
    .sumOf { section -> section.groups.sumOf { it.rows.size } }
