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

/**
 * Colourizes [text] for the editor.
 *
 * This runs on the main thread, once per keystroke, so its allocations are the ones that matter:
 * the seven styles below used to be built *per token*, and every token used to be copied out of its
 * line before being appended. A two thousand line file was making tens of thousands of throwaway
 * objects on the typing path. The styles are now built once per call, and tokens are appended
 * straight from the line by range, so what is left is the one copy the string actually needs.
 *
 * It stays synchronous on purpose. Producing it off the main thread would mean painting the
 * uncoloured buffer for a frame on every keystroke — a colour flash while typing is worse than the
 * cost of not flashing — and the result must always match the text on screen, because the
 * [AnnotatedString] carries its own text.
 */
fun highlightCode(text: String, c: PageColors): AnnotatedString {
    val commentStyle = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
    val stringStyle = SpanStyle(color = c.string)
    val annotationStyle = SpanStyle(color = c.annotation)
    val keywordStyle = SpanStyle(color = c.keyword, fontWeight = FontWeight.Medium)
    val functionStyle = SpanStyle(color = c.fn)
    val typeStyle = SpanStyle(color = c.type)
    val numberStyle = SpanStyle(color = c.number)

    return buildAnnotatedString {
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
                        withStyle(commentStyle) {
                            append(raw, i, end)
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
                        withStyle(stringStyle) {
                            append(raw, i, end)
                        }
                        i = end
                    }

                    ch == '<' && raw.startsWith("<!--", i) -> {
                        val end = if (raw.contains("-->")) {
                            raw.indexOf("-->", i) + 3
                        } else {
                            n
                        }
                        withStyle(commentStyle) {
                            append(raw, i, end)
                        }
                        i = end
                    }

                    ch == '@' -> {
                        var j = i + 1
                        while (j < n && (raw[j].isLetterOrDigit() || raw[j] == '_' || raw[j] == '.')) j++
                        withStyle(annotationStyle) {
                            append(raw, i, j)
                        }
                        i = j
                    }

                    ch.isLetter() || ch == '_' -> {
                        var j = i
                        while (j < n && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                        // The one token still copied: it is the key of the keyword lookup and the
                        // thing the capitalisation test looks at, so it is needed either way.
                        val word = raw.substring(i, j)
                        val after = raw.getOrNull(j)
                        when {
                            word in KeywordTokens -> withStyle(keywordStyle) { append(raw, i, j) }
                            after == '(' -> withStyle(functionStyle) { append(raw, i, j) }
                            word.firstOrNull()?.isUpperCase() == true -> withStyle(typeStyle) {
                                append(raw, i, j)
                            }
                            else -> append(raw, i, j)
                        }
                        i = j
                    }

                    ch.isDigit() -> {
                        var j = i
                        while (j < n && (raw[j].isDigit() || raw[j] == '.')) j++
                        withStyle(numberStyle) {
                            append(raw, i, j)
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
}
