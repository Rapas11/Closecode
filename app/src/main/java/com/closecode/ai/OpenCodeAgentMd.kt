package com.closecode.ai

import java.io.File

/**
 * AGENTS.md, the project file OpenCode always loads on its own, in every version — including
 * the pinned V2 runtime, where the newer `.opencode/skills` discovery may not fire yet.
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
        Python, no desktop browser, and no `./gradlew`; a downloaded Gradle is optional.

        Everything is the `closecode` command. It is already on PATH. Run every command from
        this project root:

        - `closecode describe` — the project's facts (package, SDK levels, sources, toolchain).
        - `closecode check` — the static checks, no build.
        - `closecode build` — a debug APK with the phone's engine; add `--release` for release.
        - `closecode clean [--gradle-caches|--sdk]` — delete build output, Gradle dependency
          caches, or the downloaded SDK.
        - `closecode init --template android-compose|web-vite|web-static|python-fastapi|python-cli|node-express [name]`
          — create a project.
        - `closecode serve [--port N] [--host H]` — start the project's dev/static server;
          `serve status|stop` manages it.
        - `closecode preview [--screenshot] [--dom]` — build and serve a static site.
        - `closecode test|lint|format|package|release` — per-ecosystem tasks.
        - `closecode jobs list|stop <id>|logs <id>|stop-all` — background jobs.
        - `closecode config show|set <key> <value>` — pin versions and ports in `.closecode.json`.
        - `closecode run` — build, install, launch, and wait until the app is in front.
        - `closecode install`, `closecode launch` — put the APK on this phone, then open it.
        - `closecode signing status|generate|import` — the key used for release APKs.
        - `closecode app`, `closecode stop`, `closecode restart` — inspect, stop or relaunch it.
        - `closecode ui dump` — the current screen as JSON; `closecode ui tap --text TEXT`,
          `ui wait`, `ui assert` and `ui scroll` drive and check it.
        - `closecode logcat`, `closecode screenshot` — the installed app's log, and its screen.
        - `closecode export` — move a file or folder out of the project, when the user asks.
        - `closecode gradle` accepts assembleDebug/assembleRelease, installDebug/installRelease,
          launch/launchRelease, bundleDebug/bundleRelease, test/testDebugUnitTest, lint/lintDebug,
          check, tasks, and flavour tasks such as assembleFreeDebug. Add `--gradle` for real
          Gradle: dependencies, variants, R8 and App Bundles. Without it, test/lint/check run
          the phone's static checks and bundles are refused.
        - Native C/C++ projects need Full Gradle plus Native tools (NDK/CMake) in Settings;
          build them with `closecode build --gradle`.
        - `closecode github push|pull` — publish this project to its GitHub repository, or take
          what came back. CloseCode signs in by itself; you never need a token.
        - `closecode tool aapt2|d8|r8|kotlinc` — one toolchain tool at a time.

        The rules:
        - Never run `./gradlew`; use the commands above. `closecode build` selects Full Gradle
          automatically when it is installed, and says `needsSetup` with the exact
          `closecode setup --gradle` command when a project needs Gradle but it is missing.
        - When a build, check or the app's logcat shows an error, read it, fix the sources, and
          run the command again yourself until it passes. Do that without asking.
        - Test the running app with `closecode run`, then `ui dump` and `ui assert`. Fix what
          is broken and run again. Stop after five failed fixes and tell the user what remains.
        - Only inspect and drive the app you just built. Do not tap system dialogs or other
          apps. Android still shows its own install confirmation; if `run` says `needsUser`,
          ask the user to confirm and then run `closecode app`.
        - On this phone the user only talks to you. Routine build, check and fix commands run
          by themselves. Destructive shell work and access outside the project ask in the
          OpenCode page; wait for the user's answer instead of trying to bypass it.
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
