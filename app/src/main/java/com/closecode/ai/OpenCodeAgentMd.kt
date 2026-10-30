package com.closecode.ai

import java.io.File

/**
 * AGENTS.md, the project file OpenCode always loads on its own, in every version — pinned
 * 1.18.35 included, where the newer `.opencode/skills` discovery may not fire yet.
 *
 * CloseCode seeds it with the phone's rules so the model can build, check, fix, install and
 * publish the project without being told once: the user's only tool here is talking. The
 * section is marker-stamped so a project the user or another tool has already taught keeps
 * its own text first; when our markers are missing the section is appended, and when they
 * are present only our section is replaced.
 */
object OpenCodeAgentMd {

    internal val MARKER_BEGIN = "<!-- closecode:start(b43) -->"
    internal val MARKER_END = "<!-- closecode:end -->"

    fun file(project: File): File = File(project, "AGENTS.md")

    /** The lesson itself, without the markers, so it can be placed under any headline. */
    fun section(): String = """
        ## CloseCode phone rules

        This project lives on an Android phone, built by CloseCode's own engine. There is no
        Gradle daemon here, no Python, no desktop browser, and nothing one could `./gradlew`.

        Everything is the `closecode` command. It is already on PATH. Run every command from
        this project root:

        - `closecode describe` — the project's facts (package, SDK levels, sources, toolchain).
        - `closecode check` — the static checks, no build.
        - `closecode build` — a debug APK with the phone's engine.
        - `closecode install`, `closecode launch` — put the APK on this phone, then open it.
        - `closecode logcat`, `closecode screenshot` — the installed app's log, and its screen.
        - `closecode export` — move a file or folder out of the project, when the user asks.
        - `closecode gradle` translates Gradle names it knows: assembleDebug (build),
          installDebug (build + install), launch (build + install + launch), and
          test, testDebugUnitTest, lint, lintDebug or check (static checks only).
        - `closecode github push|pull` — publish this project to its GitHub repository, or take
          what came back. CloseCode signs in by itself; you never need a token.
        - `closecode tool aapt2|d8|r8|kotlinc` — one toolchain tool at a time.

        The rules:
        - Never run `./gradlew`; the translated commands above are the only way to build.
        - When a build, check or the app's logcat shows an error, read it, fix the sources, and
          run the command again yourself until it passes. Do that without asking.
        - On this phone the user only talks to you. There is no terminal for them to use and
          nobody to click "allow" for a command. Build, check, fix and report by yourself.
        - When the app itself misbehaves, tell the user to open CloseCode → AI → Server log;
          the file lives at `files/opencode/logs/server.log` in the app's private storage.
    """.trimIndent() + "\n"

    /** Writes or rewrites the CloseCode section. Returns the file when it exists afterwards. */
    fun install(project: File): File? {
        val file = file(project)
        val next = if (file.isFile) {
            val existing = runCatching { file.readText() }.getOrNull() ?: return null
            merged(existing, section())
        } else {
            merged("", section())
        }
        return runCatching {
            file.writeText(next)
            file
        }.getOrNull()
    }

    /**
     * The file with our section rewritten in place, or appended to the end, [section] always
     * newest. A begin with no end means our old section ran past what we can see and is
     * replaced from the begin marker on; an end with no begin is unreadable, so the new
     * lesson is simply appended — a file that was taught before never loses text.
     */
    internal fun merged(existing: String, section: String): String {
        val begin = existing.indexOf(MARKER_BEGIN)
        val end = existing.indexOf(MARKER_END)
        return when {
            begin >= 0 && end > begin ->
                existing.take(begin) + ourBlock(section) + existing.drop(end + MARKER_END.length)
            begin >= 0 -> existing.take(begin) + ourBlock(section)
            else -> appended(existing, section)
        }
    }

    private fun ourBlock(section: String) = "$MARKER_BEGIN\n$section$MARKER_END"

    private fun appended(existing: String, section: String): String {
        val base = if (existing.isNotEmpty() && !existing.endsWith("\n")) "$existing\n" else existing
        val top = if (base.isEmpty()) "" else "\n"
        return "$base$top${ourBlock(section)}\n"
    }
}
