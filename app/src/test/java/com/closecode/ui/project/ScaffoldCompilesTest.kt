package com.closecode.ui.project

import com.closecode.lang.DiagnosticCodes
import com.closecode.lang.KotlinAnalyzer
import com.closecode.lang.Severity
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A template that does not compile is a broken product: every new project would fail its
 * first build.
 *
 * The Compose template once used `Text("Hello, world")` without importing
 * `androidx.compose.material3.Text`, and the error only appeared on the user's phone, in
 * their first build. These tests run the app's own analyser over every generated Kotlin
 * file, so a missing import is caught here — in seconds — instead of on the phone.
 */
class ScaffoldCompilesTest {

    private val config = ProjectConfig(packageName = "com.closecode.freshapp")

    @Test
    fun theComposeTemplateImportsWhatItUses() {
        val activity = kotlinFiles(TemplateTab.App, 0)
            .first { it.path.endsWith("MainActivity.kt") }

        assertTrue(
            "the template uses Text but never imports it",
            activity.content.contains("import androidx.compose.material3.Text"),
        )
        assertTrue(activity.content.contains("Text("))
    }

    @Test
    fun everyGeneratedKotlinFileHasNoUnresolvedReference() {
        val files = kotlinFiles(TemplateTab.App, 0)
        assertTrue("the template generated no Kotlin files", files.isNotEmpty())

        // The project symbols are what the editor passes too: a function the project
        // defines is not an unresolved reference.
        val projectSymbols = KotlinAnalyzer.projectSymbols(files.map { it.content })

        val problems = files.flatMap { file ->
            val analysis = KotlinAnalyzer.analyze(file.content, projectSymbols = projectSymbols)
            analysis.diagnostics
                .filter { it.severity == Severity.ERROR }
                .map { "${file.path}: ${it.message}" }
        }

        assertEquals(
            "the generated project does not compile:\n" + problems.joinToString("\n"),
            emptyList<String>(),
            problems,
        )
    }

    @Test
    fun everyAndroidTemplateGeneratesTheSameWorkingFiles() {
        val templates = com.closecode.ui.model.Templates[TemplateTab.App].orEmpty()
        for (index in templates.indices) {
            val files = kotlinFiles(TemplateTab.App, index)
            val projectSymbols = KotlinAnalyzer.projectSymbols(files.map { it.content })
            val activity = files.first { it.path.endsWith("MainActivity.kt") }
            val analysis = KotlinAnalyzer.analyze(activity.content, projectSymbols = projectSymbols)
            val unresolved = analysis.diagnostics.filter {
                it.code == DiagnosticCodes.E0101_UNRESOLVED
            }
            assertEquals(
                "template $index has unresolved references",
                emptyList<Any>(),
                unresolved.toList(),
            )
        }
    }

    /**
     * The check has to be able to fail. Without the material3 import the very same file is
     * reported as an unresolved reference — which is the bug this test exists for.
     */
    @Test
    fun theCheckWouldCatchAMissingImport() {
        val files = kotlinFiles(TemplateTab.App, 0)
        val projectSymbols = KotlinAnalyzer.projectSymbols(files.map { it.content })
        val broken = files.first { it.path.endsWith("MainActivity.kt") }.content
            .replace("import androidx.compose.material3.Text\n", "")

        val unresolved = KotlinAnalyzer.analyze(broken, projectSymbols = projectSymbols)
            .diagnostics
            .filter { it.code == DiagnosticCodes.E0101_UNRESOLVED }

        assertTrue(
            "the check did not notice the missing import",
            unresolved.any { it.message.contains("Text") },
        )
    }

    /** The Kotlin files of one Android template, or every web template's scripts. */
    private fun kotlinFiles(tab: TemplateTab, index: Int): List<ScaffoldFile> {
        val selection = SelectedTemplate(tab, index)
        return Scaffold.filesFor("freshapp", selection, config).filter { it.path.endsWith(".kt") }
    }
}
