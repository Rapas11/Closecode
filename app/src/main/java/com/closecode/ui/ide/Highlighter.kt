package com.closecode.ui.ide

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.closecode.ui.theme.PageColors

private val KeywordTokens = setOf(
    "package", "import", "class", "object", "interface", "fun", "val", "var",
    "return", "if", "else", "when", "for", "while", "do", "override", "private",
    "public", "internal", "const", "suspend", "inline", "data", "companion",
    "const", "val", "lateinit", "constructor", "this", "super", "null", "true",
    "false", "function", "let", "const", "new", "async", "await", "export",
    "default", "extends", "implements", "def", "from", "import", "try", "catch",
    "finally", "throw", "with", "in", "is", "as", "typeof", "yield",
)

private val ErrorMarkers = listOf("!!", "TODO", "error(", "FIXME")

fun isErrorLine(line: String): Boolean =
    ErrorMarkers.any { line.contains(it) }

fun highlightCode(text: String, c: PageColors): AnnotatedString =
    buildAnnotatedString {
        text.lines().forEachIndexed { index, raw ->
            if (index > 0) append('\n')
            var i = 0
            val n = raw.length
            while (i < n) {
                val ch = raw[i]
                when {
                    ch == '/' && i + 1 < n && (raw[i + 1] == '/' || raw[i + 1] == '*') -> {
                        val end = if (raw[i + 1] == '/') n else {
                            val close = raw.indexOf("*/", i + 2)
                            if (close < 0) n else close + 2
                        }
                        withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) {
                            append(raw.substring(i, end))
                        }
                        i = end
                    }

                    ch == '"' || ch == '\'' -> {
                        var j = i + 1
                        while (j < n && raw[j] != ch) {
                            if (raw[j] == '\\') j++
                            j++
                        }
                        val end = if (j < n) j + 1 else n
                        withStyle(SpanStyle(color = c.string)) {
                            append(raw.substring(i, end))
                        }
                        i = end
                    }

                    ch == '<' && raw.startsWith("<!--", i) -> {
                        val end = if (raw.contains("-->")) {
                            raw.indexOf("-->", i) + 3
                        } else {
                            n
                        }
                        withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) {
                            append(raw.substring(i, end))
                        }
                        i = end
                    }

                    ch.isLetter() || ch == '_' -> {
                        var j = i
                        while (j < n && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                        val word = raw.substring(i, j)
                        val after = raw.getOrNull(j)
                        if (word in KeywordTokens) {
                            withStyle(SpanStyle(color = c.keyword, fontWeight = FontWeight.Medium)) {
                                append(word)
                            }
                        } else if (after == '(') {
                            withStyle(SpanStyle(color = c.fn)) { append(word) }
                        } else if (word.firstOrNull()?.isUpperCase() == true) {
                            withStyle(SpanStyle(color = c.type)) { append(word) }
                        } else {
                            append(word)
                        }
                        i = j
                    }

                    ch.isDigit() -> {
                        var j = i
                        while (j < n && (raw[j].isDigit() || raw[j] == '.')) j++
                        withStyle(SpanStyle(color = c.number)) {
                            append(raw.substring(i, j))
                        }
                        i = j
                    }

                    else -> {
                        append(ch)
                        i++
                    }
                }
            }
        }
    }