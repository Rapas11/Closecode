package com.closecode.control

import com.google.gson.JsonObject
import java.io.File

/**
 * `closecode speed`: the build settings that make a project faster on a phone.
 *
 * A new project gets these from the start. An existing one can be given them here, so the
 * user does not have to edit a file by hand — which is the whole point: the app owns the
 * setup, the AI never has to patch a project.
 *
 * | Setting | Why |
 * |---|---|
 * | `org.gradle.parallel=true` | a phone has several cores, and Gradle uses one by default |
 * | `org.gradle.caching=true` | an unchanged module is reused instead of compiled again |
 * | `kotlin.compiler.execution.strategy=in-process` | the Kotlin compiler runs inside the Gradle daemon instead of starting a second JVM, which on a phone is also the "Could not connect to Kotlin compile daemon" failure |
 */
object SpeedCommand {

    /** The settings a fast build needs. */
    val SPEED_SETTINGS: List<Pair<String, String>> = listOf(
        "org.gradle.parallel" to "true",
        "org.gradle.caching" to "true",
        "kotlin.compiler.execution.strategy" to "in-process",
    )

    /** Deprecated: it rewrites old support-library dependencies on every build. */
    const val JETIFIER = "android.enableJetifier"

    data class Result(
        val path: String,
        val added: List<String>,
        val updated: List<String>,
        val removed: List<String>,
        val jetifier: Boolean,
    ) {
        val changed: Boolean get() = added.isNotEmpty() || updated.isNotEmpty() || removed.isNotEmpty()
    }

    /**
     * Adds the speed settings to the project's `gradle.properties`.
     *
     * Every other line in the file is kept exactly as it is. The deprecated Jetifier line is
     * only removed when the user asks for it: a project that still uses the old support
     * library needs it.
     */
    fun apply(projectDir: File, dropJetifier: Boolean = false): Result {
        val file = File(projectDir, "gradle.properties")
        val existing = if (file.isFile) {
            runCatching { file.readLines() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val added = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val lines = existing.toMutableList()

        for ((key, value) in SPEED_SETTINGS) {
            val wanted = "$key=$value"
            val index = lines.indexOfFirst { it.trimStart().startsWith("$key=") }
            if (index < 0) {
                lines += wanted
                added += wanted
            } else if (lines[index].trim() != wanted) {
                updated += wanted
                lines[index] = wanted
            }
        }

        var jetifier = false
        if (dropJetifier) {
            val before = lines.size
            lines.removeAll { it.trimStart().startsWith("$JETIFIER=") }
            if (lines.size != before) removed += JETIFIER
        } else {
            jetifier = lines.any { it.trimStart().startsWith("$JETIFIER=") }
        }

        if (added.isNotEmpty() || updated.isNotEmpty() || removed.isNotEmpty()) {
            file.parentFile?.mkdirs()
            file.writeText(lines.joinToString("\n", postfix = "\n"))
        }
        return Result(file.absolutePath, added, updated, removed, jetifier)
    }

    /** The answer the command and the screen both show. */
    fun report(result: Result): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("path", result.path)
        addProperty("changed", result.changed)
        add("added", com.google.gson.JsonArray().apply { result.added.forEach { add(it) } })
        add("updated", com.google.gson.JsonArray().apply { result.updated.forEach { add(it) } })
        add("removed", com.google.gson.JsonArray().apply { result.removed.forEach { add(it) } })
        addProperty(
            "message",
            when {
                !result.changed -> "This project already has the fast build settings."
                else -> "The fast build settings were added to ${result.path}."
            },
        )
        if (result.jetifier) {
            addProperty(
                "note",
                "$JETIFIER is deprecated and slows every build. " +
                    "Run closecode speed --drop-jetifier to remove it.",
            )
        }
    }
}
