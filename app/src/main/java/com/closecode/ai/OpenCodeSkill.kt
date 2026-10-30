package com.closecode.ai

import java.io.File

/**
 * The one skill OpenCode gets inside CloseCode: what this phone can do.
 *
 * The file lives in the project at `.opencode/skills/closecode/SKILL.md`, the location
 * OpenCode documents for project skills. It is written once, when it is missing — a project
 * the user has already taught must never lose its lesson.
 */
object OpenCodeSkill {

    fun directory(project: File): File = File(project, ".opencode/skills/closecode")

    fun file(project: File): File = File(directory(project), "SKILL.md")

    fun script(): String = """
        ---
        name: closecode
        description: What the CloseCode phone app does for OpenCode — build, install and launch
          the app on the phone, screenshot it, read its own log, and run static checks when the
          phone has no Gradle daemon.
        ---

        Everything about the phone is the `closecode` command. Run it inside the project.

        - `closecode describe` — the project's facts (package, SDK levels, sources, toolchain).
        - `closecode check` — static project checks, no build.
        - `closecode build` — build a debug APK with the phone's own engine.
        - `closecode install` — install the built APK on this phone.
        - `closecode launch` — open the installed app on this phone.
        - `closecode logcat` — the installed app's own log.
        - `closecode screenshot` — a screenshot of what the app shows.
        - `closecode export` — export a file or folder out of the project.
        - `closecode tool aapt2|d8|r8|kotlinc` — run one of the toolchain tools.
        - `closecode github push|pull` — publish the project to its GitHub repository, or take
          what came back. It signs in through CloseCode itself; the token stays in the app.

        This phone has no Gradle daemon and cannot run `./gradlew`. `closecode gradle`
        translates the Gradle names it knows: assembleDebug (build), installDebug (build +
        install), launch (build + install + launch), and test, testDebugUnitTest, lint,
        lintDebug or check (static checks only — real unit tests do not run on the phone).
    """.trimIndent() + "\n"

    /** Writes the skill when it is missing. Returns the file when it exists afterwards. */
    fun install(project: File): File? {
        val file = file(project)
        if (file.isFile) return file
        if (!ensureDirectory(directory(project))) return null
        return runCatching {
            file.writeText(script())
            file
        }.getOrNull()
    }

    private fun ensureDirectory(directory: File): Boolean =
        directory.isDirectory || directory.mkdirs()
}
