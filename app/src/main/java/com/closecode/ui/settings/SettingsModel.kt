package com.closecode.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

sealed interface Row {
    val name: String
    val description: String?

    data class Toggle(
        override val name: String,
        override val description: String? = null,
        val default: Boolean,
    ) : Row

    data class Value(
        override val name: String,
        override val description: String? = null,
        val value: String,
    ) : Row

    data class Nav(
        override val name: String,
        override val description: String? = null,
        val action: String,
    ) : Row

    data class Status(
        override val name: String,
        override val description: String? = null,
        val value: String,
        val ok: Boolean = true,
    ) : Row

    data class Options(
        override val name: String,
        val current: String,
    ) : Row {
        override val description: String? = null
    }

    data class Accents(override val name: String) : Row {
        override val description: String? = null
    }

    data class Action(
        override val name: String,
        override val description: String? = null,
        val action: String,
        val danger: Boolean = false,
    ) : Row

    data class Account(
        val initials: String,
        override val name: String,
        val subtitle: String,
        val action: String,
    ) : Row {
        override val description: String? = null
    }
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

val AccentOptions = listOf(
    Color(0xFFF97316),
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
)

val Sections = listOf(
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
                    Row.Nav("Default project location", "Where new projects are created.", "Closecode Projects"),
                    Row.Toggle("Open last project", "Automatically reopen your last project.", true),
                    Row.Toggle("Auto save", "Save changes automatically while editing.", true),
                ),
            ),
            Group(
                "Account",
                listOf(Row.Account("RC", "Closecode Account", "Signed in", "Manage")),
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
                    Row.Value("Font size", "Editor text size.", "14 px"),
                    Row.Value("Tab size", "Number of spaces per tab.", "4"),
                    Row.Toggle("Line numbers", null, true),
                    Row.Toggle("Word wrap", null, false),
                    Row.Toggle("Code folding", null, true),
                    Row.Toggle("Bracket guides", null, true),
                ),
            ),
            Group(
                "Behavior",
                listOf(
                    Row.Toggle("Auto indent", null, true),
                    Row.Toggle("Auto close brackets", null, true),
                    Row.Toggle("Format on save", null, false),
                ),
            ),
        ),
    ),
    Section(
        id = "intelligence",
        label = "Code Intelligence",
        icon = "✦",
        title = "Code Intelligence",
        blurb = "Configure autocomplete, diagnostics and coding assistance.",
        groups = listOf(
            Group(
                "Code Intelligence",
                listOf(
                    Row.Toggle("Autocomplete", "Show code completion suggestions.", true),
                    Row.Toggle("Auto import", null, true),
                    Row.Toggle("Parameter hints", null, true),
                    Row.Toggle("Quick fixes", null, true),
                    Row.Toggle("Diagnostics", null, true),
                ),
            ),
        ),
    ),
    Section(
        id = "build",
        label = "Build & Toolchain",
        icon = "▣",
        title = "Build & Toolchain",
        blurb = "Manage the tools Closecode uses to build Android projects.",
        groups = listOf(
            Group(
                "Toolchain",
                listOf(
                    Row.Status("JDK", "Java runtime used for Android builds.", "Java 17"),
                    Row.Status("Android SDK", null, "SDK 35"),
                    Row.Status("Build Tools", null, "35.0.0"),
                    Row.Value("Gradle", null, "Managed"),
                ),
            ),
            Group(
                "Build Behavior",
                listOf(
                    Row.Toggle("Offline mode", null, false),
                    Row.Toggle("Build cache", null, true),
                    Row.Nav("SDK Manager", "Manage installed Android SDK packages.", "Open"),
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
                "Terminal",
                listOf(
                    Row.Value("Default shell", null, "System"),
                    Row.Value("Font size", null, "13 px"),
                    Row.Toggle("Command history", null, true),
                ),
            ),
        ),
    ),
    Section(
        id = "git",
        label = "Git & GitHub",
        icon = "⑂",
        title = "Git & GitHub",
        blurb = "Manage Git identity and GitHub connections.",
        groups = listOf(
            Group(
                "Git Identity",
                listOf(
                    Row.Value("Name", null, "Not configured"),
                    Row.Value("Email", null, "Not configured"),
                    Row.Value("Default branch", null, "main"),
                ),
            ),
            Group(
                "GitHub",
                listOf(Row.Nav("GitHub account", null, "Connect")),
            ),
        ),
    ),
    Section(
        id = "ai",
        label = "AI",
        icon = "✧",
        title = "AI",
        blurb = "Configure the AI assistant and agent permissions.",
        groups = listOf(
            Group(
                "AI Assistant",
                listOf(
                    Row.Value("Provider", null, "Not configured"),
                    Row.Value("Model", null, "Default"),
                ),
            ),
            Group(
                "Agent Permissions",
                listOf(
                    Row.Value("File access", "Allow AI to read and modify project files.", "Ask"),
                    Row.Value("Terminal access", null, "Ask"),
                    Row.Value("Build access", null, "Allow"),
                    Row.Value("Git access", null, "Ask"),
                ),
            ),
        ),
    ),
    Section(
        id = "appearance",
        label = "Appearance",
        icon = "◐",
        title = "Appearance",
        blurb = "Customize the look and feel of Closecode.",
        groups = listOf(
            Group(
                "Theme",
                listOf(
                    Row.Options("Theme", "Dark"),
                    Row.Accents("Accent color"),
                    Row.Toggle("Animations", null, true),
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
                    Row.Value("Projects", null, "1.4 GB"),
                    Row.Value("Android SDK", null, "2.2 GB"),
                    Row.Value("Gradle cache", null, "640 MB"),
                    Row.Value("Build cache", null, "420 MB"),
                ),
            ),
            Group(
                "",
                listOf(
                    Row.Action(
                        "Clear caches",
                        "Remove temporary build and Gradle cache files.",
                        "Clear",
                        danger = true,
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
        blurb = "Manage credentials, signing keys and app security.",
        groups = listOf(
            Group(
                "Security",
                listOf(
                    Row.Toggle(
                        "Biometric lock",
                        "Require biometric authentication when opening Closecode.",
                        false,
                    ),
                    Row.Nav("Credentials", null, "Manage"),
                    Row.Nav("Signing keys", null, "Manage"),
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
                    Row.Value("Version", null, "1.0.0"),
                    Row.Nav("Open source licenses", null, "View"),
                    Row.Nav("Privacy", null, "View"),
                ),
            ),
        ),
    ),
)

fun sectionById(id: String): Section = Sections.first { it.id == id }