package com.closecode.ui.project

import com.closecode.lang.SourceEdits
import java.io.File

/**
 * Repairs the projects CloseCode generated, so an old template cannot fail a build forever.
 *
 * The Compose template once used `Text("Hello, world")` without importing
 * `androidx.compose.material3.Text`. Every project created before that was fixed still had
 * the file, and every one of them failed its first build with an error in a source file.
 *
 * The repair is deliberately small: it only ever **adds** an import that the source uses
 * and does not have, and only for projects CloseCode generated itself. A project the user
 * brought in is never touched.
 */
object GeneratedProjectRepair {

    /** The import a generated Compose screen needs when it uses `Text(`. */
    const val MATERIAL3_TEXT = "androidx.compose.material3.Text"

    /**
     * Adds the imports a generated screen uses but does not have.
     *
     * Returns how many files were changed.
     */
    fun repair(projectDir: File, imported: Boolean): Int {
        // A project the user brought in is theirs; the app does not edit it.
        if (imported) return 0
        val sources = projectDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .take(40)
            .toList()
        var changed = 0
        sources.forEach { file ->
            val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
            if (!text.contains("Text(")) return@forEach
            if (text.contains("import $MATERIAL3_TEXT")) return@forEach
            // The import must belong to the package the file declares.
            val pkg = text.lineSequence()
                .firstOrNull { it.trimStart().startsWith("package ") }
                ?.trim()
                ?.removePrefix("package ")
                ?.trim()
                ?: return@forEach
            val repaired = SourceEdits.addImport(text, MATERIAL3_TEXT)
            if (repaired != text) {
                runCatching { file.writeText(repaired) }
                    .onSuccess { changed++ }
            }
        }
        return changed
    }
}
