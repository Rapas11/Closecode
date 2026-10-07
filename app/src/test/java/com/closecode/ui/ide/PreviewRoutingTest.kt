package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewRoutingTest {

    private fun file(path: String, kind: FileKind): ProjectFile =
        ProjectFile(
            name = path.substringAfterLast('/'),
            kind = kind,
            language = kind.name,
            depth = path.count { it == '/' },
            path = path,
        )

    @Test
    fun prefersARootIndexHtmlEntry() {
        val tree = listOf(
            file("index.html", FileKind.Html),
            file("about.html", FileKind.Html),
            file("MainActivity.kt", FileKind.Kotlin),
        )
        assertEquals("index.html", webPreviewEntry(tree, "about.html"))
    }

    @Test
    fun fallsBackToTheOpenHtmlFile() {
        val tree = listOf(
            file("about.html", FileKind.Html),
            file("MainActivity.kt", FileKind.Kotlin),
        )
        assertEquals("about.html", webPreviewEntry(tree, "about.html"))
    }

    @Test
    fun kotlinProjectsHaveNoWebEntry() {
        val tree = listOf(file("MainActivity.kt", FileKind.Kotlin))
        assertNull(webPreviewEntry(tree, "MainActivity.kt"))
    }

    @Test
    fun extractsComposeTextAndButtonLabels() {
        val source = """
            @Composable fun Notes() {
              Column {
                Text("My Notes")
                Text("3 notes saved")
                Button(onClick = {}) { Text("New note") }
              }
            }
        """.trimIndent()
        val content = extractComposePreview(source, "NotesScreen.kt")
        assertEquals("My Notes", content.title)
        assertEquals("3 notes saved", content.subtitle)
        assertEquals("New note", content.button)
    }

    @Test
    fun ignoresTextInsideCommentsAndStrings() {
        val source = """
            // Text("not ui")
            val label = "Text(\"not ui\")"
            @Composable fun Screen() { Text("Real") }
        """.trimIndent()
        val content = extractComposePreview(source, "Screen.kt")
        assertEquals("Real", content.title)
    }
}
