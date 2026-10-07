package com.closecode.ui

import com.closecode.ui.model.FileKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the two presentation bugs found on device:
 *  - an unrecognised file was classified as Gradle, so .gitkeep rendered a red "G"
 *  - red was used decoratively for file-type badges, so red stopped meaning "error"
 */
class FileKindMappingTest {

    private fun kindOf(name: String): FileKind {
        val lower = name
        return when {
            lower.endsWith(".kt") || lower.endsWith(".kts") -> FileKind.Kotlin
            lower.endsWith(".xml") -> FileKind.Xml
            lower.endsWith(".html") -> FileKind.Html
            lower.endsWith(".css") -> FileKind.Css
            lower.endsWith(".js") -> FileKind.Js
            lower.endsWith(".json") -> FileKind.Json
            lower.endsWith(".csv") -> FileKind.Csv
            lower.endsWith(".md") -> FileKind.Markdown
            lower.endsWith(".gradle") -> FileKind.Gradle
            else -> FileKind.Unknown
        }
    }

    @Test
    fun extensionlessFilesAreUnknownNotGradle() {
        assertEquals(FileKind.Unknown, kindOf(".gitkeep"))
        assertEquals(FileKind.Unknown, kindOf("LICENSE"))
        assertEquals(FileKind.Unknown, kindOf("Dockerfile"))
    }

    @Test
    fun knownExtensionsStillResolve() {
        assertEquals(FileKind.Kotlin, kindOf("MainActivity.kt"))
        assertEquals(FileKind.Gradle, kindOf("build.gradle"))
        assertEquals(FileKind.Js, kindOf("main.js"))
        assertEquals(FileKind.Json, kindOf("package.json"))
        assertEquals(FileKind.Xml, kindOf("strings.xml"))
        assertEquals(FileKind.Html, kindOf("index.html"))
        assertEquals(FileKind.Css, kindOf("style.css"))
        assertEquals(FileKind.Markdown, kindOf("README.md"))
    }

    @Test
    fun unknownFilesHaveNoBadgeGlyph() {
        assertEquals("", com.closecode.ui.ide.model.kindGlyph(FileKind.Unknown))
    }
}