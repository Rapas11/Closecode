package com.closecode.ui.ide.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.closecode.ui.model.FileKind
import com.closecode.ui.theme.PageColors

@Immutable
data class Suggestion(
    val glyph: String,
    val name: String,
    val detail: String,
)

val KotlinSuggestions = listOf(
    Suggestion("ƒ", "setContent", "Compose"),
    Suggestion("K", "ClosecodeTheme", "function"),
    Suggestion("T", "ComponentActivity", "class"),
    Suggestion("M", "onCreate", "override"),
)

val WebSuggestions = listOf(
    Suggestion("T", "document", "interface"),
    Suggestion("F", "fetch", "function"),
    Suggestion("L", "addEventListener", "function"),
    Suggestion("V", "querySelector", "function"),
)

val CssSuggestions = listOf(
    Suggestion("P", "display", "property"),
    Suggestion("P", "grid-template-columns", "property"),
    Suggestion("F", "var", "function"),
    Suggestion("M", "margin", "property"),
)

fun suggestionsFor(language: String): List<Suggestion> = when {
    language.contains("HTML", true) -> WebSuggestions
    language.contains("CSS", true) -> CssSuggestions
    else -> KotlinSuggestions
}

fun kindColor(kind: FileKind, c: PageColors): Color = when (kind) {
    FileKind.Folder -> c.ac
    FileKind.Kotlin -> c.keyword
    FileKind.Xml, FileKind.Html -> c.string
    FileKind.Gradle, FileKind.Json, FileKind.Js, FileKind.Csv -> c.red
    else -> c.mu
}

fun kindGlyph(kind: FileKind): String = when (kind) {
    FileKind.Folder -> "▾"
    FileKind.Kotlin -> "K"
    FileKind.Xml -> "□"
    FileKind.Gradle -> "G"
    FileKind.Json -> "{ }"
    FileKind.Html -> "<>"
    FileKind.Css -> "#"
    FileKind.Js -> "JS"
    FileKind.Csv -> "≣"
    FileKind.Markdown, FileKind.Properties -> "◇"
}

val IdePanels = listOf("Search", "Source Control", "AI", "Run", "Terminal")

val AgentChips = listOf(
    "fix the null crashes",
    "add logging to the repository",
    "write unit tests",
    "rename cache to notesCache",
    "/files",
    "/help",
)

val AgentModes = listOf(
    "⏵ default mode (tap to switch)",
    "⏵⏵ accept edits on",
    "⏸ plan mode on",
)

sealed interface AgentLine {
    data class User(val text: String) : AgentLine
    data class Note(val text: String) : AgentLine
    data class Answer(val text: String) : AgentLine
    data class Tool(val name: String, val arg: String) : AgentLine
    data class ToolResult(val text: String) : AgentLine
    data class Todo(val items: List<Pair<String, Boolean>>) : AgentLine
    data class Diff(val rows: List<Pair<Char, String>>) : AgentLine
    data class Permission(val title: String, val options: List<String>) : AgentLine
}

val AgentIntro: List<AgentLine> = listOf(
    AgentLine.Answer("✻ Welcome. This is a terminal-style coding agent demo for an Android project."),
    AgentLine.Note("Type a request or tap a suggestion. Edits and commands ask for permission first."),
)

val AgentWelcome: List<AgentLine> = listOf(
    AgentLine.User("fix the null crashes"),
    AgentLine.Answer("Working through the task."),
    AgentLine.Todo(
        listOf(
            "Find unsafe !! calls" to true,
            "Replace with safe fallbacks" to false,
            "Run tests" to false,
        )
    ),
    AgentLine.Tool("Read", "NoteRepository.kt"),
    AgentLine.ToolResult("Read 17 lines"),
    AgentLine.Tool("Read", "MainActivity.kt"),
    AgentLine.ToolResult("Read 11 lines"),
    AgentLine.Tool("Edit", "NoteRepository.kt"),
    AgentLine.Diff(
        listOf(
            ' ' to "fun count(): Int {",
            '-' to "  return cache!!.size",
            '+' to "  return (cache ?: emptyList()).size",
            ' ' to "}",
            ' ' to "fun titles(): List<String> {",
            '-' to "  return cache!!.map { it.title }",
            '+' to "  return (cache ?: emptyList()).map { it.title }",
            ' ' to "}",
        )
    ),
    AgentLine.Permission(
        "Edit NoteRepository.kt?",
        listOf(
            "Yes",
            "Yes, and don't ask again this session",
            "No, and tell the agent what to do differently",
        )
    ),
)