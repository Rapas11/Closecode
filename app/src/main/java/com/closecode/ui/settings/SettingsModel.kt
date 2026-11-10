package com.closecode.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.closecode.ui.model.TemplateTab

/**
 * One row of the settings screen.
 *
 * A row is either a control bound to a field of [Settings], a measured fact, or a destination.
 * Rows carrying a [BoolSetting] / [OptionSetting] key read and write real preferences; the rest
 * report something the app measured or open another page.
 */
sealed interface Row {
    val name: String
    val description: String?

    /** An on/off control. [fixedOn] draws the fixed On state used by auto-save. */
    data class Toggle(
        override val name: String,
        override val description: String? = null,
        val key: BoolSetting? = null,
        val default: Boolean = false,
        val fixedOn: Boolean = false,
    ) : Row

    /** A fixed list of choices, shown as the current value and a chevron. */
    data class Options(
        override val name: String,
        override val description: String? = null,
        val key: OptionSetting? = null,
        val choices: List<String> = emptyList(),
        val unit: String = "",
        val current: String = "",
    ) : Row

    /** A measured fact — a path, a size, a version — with nothing to edit. */
    data class Value(
        override val name: String,
        override val description: String? = null,
        val value: String,
    ) : Row

    /** A destination the row leads somewhere else to reach. */
    data class Nav(
        override val name: String,
        override val description: String? = null,
        val action: String,
        val id: SettingsAction? = null,
    ) : Row

    /** A fact with a health signal beside it: green when it is fine, amber when it is not. */
    data class Status(
        override val name: String,
        override val description: String? = null,
        val value: String,
        val ok: Boolean = true,
    ) : Row

    /** The signed-in account, shown as an avatar, a name and a way to manage it. */
    data class Account(
        val initials: String,
        override val name: String,
        val subtitle: String,
        val action: String,
    ) : Row {
        override val description: String? = null
    }

    /** The accent colour swatches. */
    data class Accents(override val name: String, val selected: Int = 0) : Row {
        override val description: String? = null
    }

    /** The three theme preview cards. */
    data class Themes(override val name: String, val current: String) : Row {
        override val description: String? = null
    }

    /** A live syntax-highlighted snippet under the code rows. */
    data class CodePreview(override val name: String = "") : Row {
        override val description: String? = null
    }

    /** A language support row: a green tick when ready, otherwise a Download action. */
    data class Language(
        override val name: String,
        override val description: String? = null,
        val ready: Boolean,
        val id: SettingsAction? = null,
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

/** The actions [Row.Action] and [Row.Nav] can ask for. */
enum class SettingsAction {
    CLEAR_CACHES,
    DOWNLOAD_TOOLCHAIN,
    SETUP_GUIDE,
    CONNECT_GITHUB,
    DISCONNECT_GITHUB,
    SDK_MANAGER,
    SHELL_TOOLS,
    FULL_GRADLE,
    NATIVE_TOOLS,
    CREDENTIALS,
    SIGNING_KEYS,
    AGENT_PERMISSIONS,
    LICENSES,
    PRIVACY,
    AI_SETTINGS,
    BIOMETRIC_LOCK,
    STORAGE_PROJECTS,
    EDIT_GIT_NAME,
    EDIT_GIT_EMAIL,
    EDIT_GIT_DEFAULT_BRANCH,
    PROJECT_LOCATION,
    DELETE_BUILD_TOOLS,
    SAVED_COMMANDS,
    SAVED_SIGN_INS,
    AI_PROVIDER_KEY,
    UPDATE_RUNTIME,
    SOURCE_CODE,
    STORAGE_INSPECT_PROJECTS,
    STORAGE_INSPECT_BUILD_TOOLS,
    STORAGE_INSPECT_SHELL_TOOLS,
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

/** An absolute app path with its private prefix folded into "...", as the design shows it. */
fun relativePath(path: String): String {
    val marker = "/files/"
    val index = path.indexOf(marker)
    return if (index > 0) "..." + path.substring(index) else path
}

/** The six accent swatches offered by the design: blurple, blue, green, amber, red, pink. */
val AccentOptions = listOf(
    Color(0xFF5865F2),
    Color(0xFF2563EB),
    Color(0xFF248046),
    Color(0xFFC98A0A),
    Color(0xFFDA373C),
    Color(0xFFEC4899),
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
    val buildToolsSize: String = "…",
    val shellToolsSize: String = "…",
    val debugFilesSize: String = "…",
    val freeSpace: String = "…",
    val version: String = "1.0.0",
    val toolchainStatus: String = "…",
    val toolchainVersions: String = "…",
    val shellToolsStatus: String = "…",
    val gradleToolsStatus: String = "…",
    val nativeToolsStatus: String = "…",
    val openCodeRuntimeStatus: String = "…",
    val openCodeVersion: String = "…",
    val providerKeySet: Boolean = false,
    val savedCommandCount: Int = 0,
)

/**
 * The settings screen: every row, across every section, built as a function of [live].
 *
 * [githubLogin] is the account this phone is signed in to, or null when it is signed out. The
 * token itself never reaches this function; only the name it resolved to.
 */
fun sections(
    live: SettingsLive,
    githubLogin: String? = null,
    settings: Settings = Settings(),
    templateTab: TemplateTab? = null,
): List<Section> = listOf(
    Section(
        id = "general",
        label = "General",
        icon = "⚙",
        title = "General",
        blurb = "Configure how CloseCode behaves.",
        groups = listOf(
            Group(
                "Startup",
                listOf(
                    Row.Nav(
                        "Default project location",
                        null,
                        relativePath(live.projectRoot),
                        id = SettingsAction.PROJECT_LOCATION,
                    ),
                    Row.Toggle(
                        "Open last project",
                        "Start where you stopped",
                        BoolSetting.OPEN_LAST_PROJECT,
                    ),
                    Row.Toggle(
                        "Auto-save",
                        "Always on. Every change is saved.",
                        BoolSetting.AUTO_SAVE,
                        fixedOn = true,
                    ),
                    Row.Nav(
                        "Setup guide",
                        "Downloads and first-time setup",
                        "Open",
                        id = SettingsAction.SETUP_GUIDE,
                    ),
                ),
            ),
            Group(
                "Performance",
                listOf(
                    Row.Toggle(
                        "Performance overlay",
                        "Frame times and line counts in the editor",
                        BoolSetting.FRAME_STATS,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "editor",
        label = "Editor",
        icon = "‹›",
        title = "Editor",
        blurb = "Customize the CloseCode code editor.",
        groups = listOf(
            Group(
                "Text",
                listOf(
                    Row.Options(
                        "Font size",
                        null,
                        OptionSetting.FONT_SIZE,
                        FontSizeChoices,
                    ),
                    Row.Toggle("Word wrap", "Wrap long lines", BoolSetting.WORD_WRAP),
                    Row.Toggle("Show line numbers", null, BoolSetting.LINE_NUMBERS),
                    Row.Toggle("Auto indent", null, BoolSetting.AUTO_INDENT),
                    Row.Toggle("Auto close brackets", null, BoolSetting.AUTO_CLOSE_BRACKETS),
                    Row.Toggle("Bracket guides", null, BoolSetting.BRACKET_GUIDES),
                    Row.Toggle("Rainbow brackets", null, BoolSetting.RAINBOW_BRACKETS),
                    Row.Toggle("Sora editor", "Fast editor, recommended for large files", BoolSetting.SORA_EDITOR),
                ),
            ),
            Group(
                "Typing",
                listOf(
                    Row.Options(
                        "Tab size",
                        null,
                        OptionSetting.TAB_SIZE,
                        TabSizeChoices,
                        unit = " spaces",
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
        blurb = "Configure suggestions, problems and language support.",
        groups = listOf(
            Group(
                "Language help",
                listOf(
                    Row.Toggle(
                        "Suggestions",
                        "Show while typing",
                        BoolSetting.AUTOCOMPLETE,
                    ),
                    Row.Toggle(
                        "Show problems",
                        "Errors and warnings in code",
                        BoolSetting.DIAGNOSTICS,
                    ),
                    Row.Toggle(
                        "Format on save",
                        null,
                        BoolSetting.FORMAT_ON_SAVE,
                    ),
                    Row.Toggle("Auto import", null, BoolSetting.AUTO_IMPORT),
                    Row.Toggle("Parameter hints", null, BoolSetting.PARAMETER_HINTS),
                    Row.Toggle("Quick fixes", null, BoolSetting.QUICK_FIXES),
                ),
            ),
            Group(
                "Languages",
                listOf(
                    Row.Language(
                        "Kotlin",
                        if (live.toolchainStatus == "Ready") "Ready" else "Build tools needed",
                        ready = live.toolchainStatus == "Ready",
                        id = SettingsAction.DOWNLOAD_TOOLCHAIN,
                    ),
                    Row.Language("TypeScript", "Ready", ready = true),
                    Row.Language("HTML and CSS", "Ready", ready = true),
                ),
            ),
        ),
    ),
    Section(
        id = "build",
        label = "Build and Toolchain",
        icon = "▷",
        title = "Build and Toolchain",
        blurb = "Manage the tools CloseCode uses to build Android projects.",
        groups = listOf(
            Group(
                "Toolchain",
                buildList {
                    add(
                        Row.Nav(
                            "Build tools",
                            live.toolchainStatus,
                            "Open",
                            id = SettingsAction.DOWNLOAD_TOOLCHAIN,
                        )
                    )
                    add(Row.Toggle("Build cache", null, BoolSetting.BUILD_CACHE))
                    add(
                        Row.Nav(
                            "Shell tools",
                            "Git, Python, Node, curl, zip, jq",
                            live.shellToolsStatus,
                            id = SettingsAction.SHELL_TOOLS,
                        )
                    )
                    add(
                        Row.Nav(
                            "OpenCode runtime",
                            "Linux libraries it runs on",
                            live.openCodeRuntimeStatus,
                            id = SettingsAction.AI_SETTINGS,
                        )
                    )
                    if (templateTab == TemplateTab.App) {
                        add(
                            Row.Nav(
                                "SDK manager",
                                "Android platforms and build tools",
                                "Open",
                                id = SettingsAction.SDK_MANAGER,
                            )
                        )
                        add(
                            Row.Nav(
                                "Full Gradle",
                                "JDK 17 and Gradle for Gradle builds",
                                "Open",
                                id = SettingsAction.FULL_GRADLE,
                            )
                        )
                        add(
                            Row.Nav(
                                "Native tools",
                                "NDK and CMake for native code",
                                "Open",
                                id = SettingsAction.NATIVE_TOOLS,
                            )
                        )
                    }
                },
            ),
            Group(
                "Defaults",
                listOf(
                    Row.Options(
                        "Default build type",
                        null,
                        OptionSetting.BUILD_TYPE,
                        BuildTypeChoices,
                    ),
                    Row.Options(
                        "Download over",
                        null,
                        OptionSetting.DOWNLOAD_NETWORK,
                        DownloadNetworkChoices,
                    ),
                ),
            ),
            Group(
                "Remove",
                listOfNotNull(
                    if (templateTab == TemplateTab.App) {
                        Row.Action(
                            "Delete build tools",
                            "You can download them again",
                            "Delete",
                            SettingsAction.DELETE_BUILD_TOOLS,
                            danger = true,
                        )
                    } else {
                        null
                    }
                ),
            ),
        ),
    ),
    Section(
        id = "terminal",
        label = "Terminal",
        icon = ">_",
        title = "Terminal",
        blurb = "Configure the integrated terminal.",
        groups = listOf(
            Group(
                "Shell",
                listOf(
                    Row.Toggle(
                        "Use pipe terminal",
                        "Fallback if PTY is not available",
                        BoolSetting.USE_PIPE_TERMINAL,
                    ),
                    Row.Toggle("Command history", null, BoolSetting.COMMAND_HISTORY),
                    Row.Options(
                        "Start folder",
                        null,
                        OptionSetting.START_FOLDER,
                        StartFolderChoices,
                    ),
                    Row.Options(
                        "Font size",
                        null,
                        OptionSetting.TERMINAL_FONT_SIZE,
                        TerminalFontSizeChoices,
                    ),
                    Row.Options(
                        "Terminal theme",
                        null,
                        OptionSetting.TERMINAL_THEME,
                        TerminalThemeChoices,
                    ),
                ),
            ),
            Group(
                "Saved commands",
                listOf(
                    Row.Nav(
                        "Manage saved commands",
                        "${live.savedCommandCount} commands",
                        "",
                        id = SettingsAction.SAVED_COMMANDS,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "git",
        label = "Git and GitHub",
        icon = "⑂",
        title = "Git and GitHub",
        blurb = "Manage Git identity and GitHub connections.",
        groups = listOf(
            Group(
                "Account",
                listOf(
                    if (githubLogin == null) {
                        Row.Nav(
                            "GitHub account",
                            "Sign in to clone, pull and push",
                            "Sign in",
                            id = SettingsAction.CONNECT_GITHUB,
                        )
                    } else {
                        Row.Account(
                            initials = githubLogin.take(1).uppercase(),
                            name = githubLogin,
                            subtitle = "Signed in with GitHub",
                            action = "Sign out",
                        )
                    },
                ),
            ),
            Group(
                "Commits",
                listOf(
                    Row.Nav(
                        "Author name",
                        null,
                        settings.gitName.ifBlank { "Not set" },
                        id = SettingsAction.EDIT_GIT_NAME,
                    ),
                    Row.Nav(
                        "Author email",
                        null,
                        settings.gitEmail.ifBlank { "Not set" },
                        id = SettingsAction.EDIT_GIT_EMAIL,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "ai",
        label = "OpenCode AI",
        icon = "✧",
        title = "OpenCode AI",
        blurb = "Set up the OpenCode runtime. Chat lives in the OpenCode tool.",
        groups = listOf(
            Group(
                "OpenCode",
                listOf(
                    Row.Status(
                        "OpenCode runtime",
                        "Installed",
                        live.openCodeRuntimeStatus,
                        ok = live.openCodeRuntimeStatus == "Installed",
                    ),
                    Row.Nav(
                        "Provider key",
                        "Used by OpenCode only",
                        if (live.providerKeySet) "Set" else "Not set",
                        id = SettingsAction.AI_PROVIDER_KEY,
                    ),
                    Row.Nav(
                        "Update runtime",
                        "Version ${live.openCodeVersion}",
                        "Update",
                        id = SettingsAction.UPDATE_RUNTIME,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "appearance",
        label = "Appearance",
        icon = "◐",
        title = "Appearance",
        blurb = "Customize the look and feel of CloseCode.",
        groups = listOf(
            Group(
                "Theme",
                listOf(Row.Themes("Theme", settings.theme)),
            ),
            Group(
                "Accent color",
                listOf(Row.Accents("Accent color", settings.accentIndex)),
            ),
            Group(
                "Code",
                listOf(
                    Row.Options(
                        "Code font",
                        null,
                        OptionSetting.CODE_FONT,
                        CodeFontChoices,
                    ),
                    Row.Options(
                        "Code size",
                        null,
                        OptionSetting.FONT_SIZE,
                        FontSizeChoices,
                    ),
                    Row.Options(
                        "Syntax colors",
                        null,
                        OptionSetting.SYNTAX_COLORS,
                        SyntaxColorChoices,
                    ),
                    Row.Toggle("Animations", null, BoolSetting.ANIMATIONS),
                    Row.CodePreview(),
                ),
            ),
        ),
    ),
    Section(
        id = "storage",
        label = "Storage",
        icon = "▤",
        title = "Storage",
        blurb = "View and manage CloseCode storage.",
        groups = listOf(
            Group(
                "Used by CloseCode",
                listOf(
                    Row.Nav(
                        "Projects",
                        null,
                        live.projectsSize,
                        id = SettingsAction.STORAGE_INSPECT_PROJECTS,
                    ),
                    Row.Nav(
                        "Build tools",
                        null,
                        live.buildToolsSize,
                        id = SettingsAction.STORAGE_INSPECT_BUILD_TOOLS,
                    ),
                    Row.Nav(
                        "Shell tools",
                        null,
                        live.shellToolsSize,
                        id = SettingsAction.STORAGE_INSPECT_SHELL_TOOLS,
                    ),
                    Row.Action(
                        "Caches",
                        live.cachesSize,
                        "Clear",
                        SettingsAction.CLEAR_CACHES,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "security",
        label = "Security",
        icon = "◇",
        title = "Security",
        blurb = "Manage privacy and saved sign-ins.",
        groups = listOf(
            Group(
                "Privacy",
                listOf(
                    Row.Toggle(
                        "App lock",
                        "Ask for fingerprint",
                        BoolSetting.APP_LOCK,
                    ),
                    Row.Toggle(
                        "Send usage data",
                        "Off by default",
                        BoolSetting.SEND_USAGE_DATA,
                    ),
                    Row.Nav(
                        "Saved sign-ins",
                        "GitHub",
                        "Manage",
                        id = SettingsAction.SAVED_SIGN_INS,
                    ),
                    Row.Nav(
                        "Credentials",
                        "GitHub access token",
                        "Open",
                        id = SettingsAction.CREDENTIALS,
                    ),
                    Row.Nav(
                        "Signing keys",
                        "Release signing key",
                        "Open",
                        id = SettingsAction.SIGNING_KEYS,
                    ),
                    Row.Nav(
                        "Agent permissions",
                        "What the agent may do",
                        "Open",
                        id = SettingsAction.AGENT_PERMISSIONS,
                    ),
                    Row.Nav(
                        "Privacy",
                        "OpenCode privacy policy",
                        "Open",
                        id = SettingsAction.PRIVACY,
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
        blurb = "Information about CloseCode.",
        groups = listOf(
            Group(
                "App",
                listOf(
                    Row.Value("CloseCode", "Open source Android IDE", live.version),
                    Row.Nav(
                        "Source code",
                        "github.com/closecode",
                        "Open",
                        id = SettingsAction.SOURCE_CODE,
                    ),
                    Row.Nav(
                        "Licences",
                        null,
                        "Open",
                        id = SettingsAction.LICENSES,
                    ),
                ),
            ),
        ),
    ),
)

fun sectionById(id: String): Section = sections(SettingsLive()).firstOrNull { it.id == id }
    ?: sections(SettingsLive()).first()

/** Every key a settings row edits, for the test that keeps the two lists honest. */
fun usedBoolSettings(): Set<BoolSetting> = sections(SettingsLive())
    .flatMap { section -> section.groups.flatMap { it.rows } }
    .mapNotNull { row -> (row as? Row.Toggle)?.key }
    .toSet()

fun usedOptionSettings(): Set<OptionSetting> = sections(SettingsLive())
    .flatMap { section -> section.groups.flatMap { it.rows } }
    .mapNotNull { row -> (row as? Row.Options)?.key }
    .toSet()

fun usedStringSettings(): Set<StringSetting> = sections(SettingsLive())
    .flatMap { section -> section.groups.flatMap { it.rows } }
    .mapNotNull { row ->
        (row as? Row.Nav)?.id?.let { action ->
            when (action) {
                SettingsAction.EDIT_GIT_NAME -> StringSetting.GIT_NAME
                SettingsAction.EDIT_GIT_EMAIL -> StringSetting.GIT_EMAIL
                SettingsAction.EDIT_GIT_DEFAULT_BRANCH -> StringSetting.GIT_DEFAULT_BRANCH
                else -> null
            }
        }
    }
    .toSet()

/** Total rows on the screen, whatever section they sit in. */
fun rowCount(): Int = sections(SettingsLive())
    .sumOf { section -> section.groups.sumOf { it.rows.size } }
