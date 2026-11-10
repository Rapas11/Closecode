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

    internal const val MARKER = "<!-- closecode:skill(b46) -->"

    fun directory(project: File): File = File(project, ".opencode/skills/closecode")

    fun file(project: File): File = File(directory(project), "SKILL.md")

    fun script(): String = """
        ---
        name: closecode
        description: What the CloseCode phone app does for OpenCode — build, install, launch
          and inspect the app on the phone, drive its screen, read its own log, run static
          checks, and optionally build with a downloaded Gradle.
        ---

        $MARKER

        Everything about the phone is the `closecode` command. Run it inside the project.

        - `closecode describe` — the project's facts (package, SDK levels, sources, toolchain).
        - `closecode doctor` — what is missing and the exact command that fixes it.
        - `closecode toolchain status --json` — JDK, Gradle, SDK and cache paths and sizes.
        - `closecode check` — static project checks, no build.
        - `closecode setup --gradle` — install Full Gradle, the SDK and build-tools.
        - `closecode build` — build a debug APK; add `--release` for a release APK. Gradle is
          selected automatically when it is installed, the phone's engine otherwise.
        - `closecode clean [--gradle-caches|--sdk]` — delete build output, Gradle dependency
          caches, or the downloaded SDK.
        - `closecode init --template android-compose|web-vite|web-static|python-fastapi|python-cli|node-express [name]`
          — create a project that the installed phone toolchains can build.
        - `closecode serve [--port N] [--host H]` — run the project's Vite/Next/Astro/Flask/
          FastAPI/Python static server as a background job; `serve status|stop` manages it.
        - `closecode preview` — build and serve a static site, open it, and optionally take a
          screenshot (`--screenshot`) or read the served HTML (`--dom`).
        - `closecode test|lint|format|package|release` — the same task names for Gradle, npm,
          Python and CMake projects.
        - `closecode jobs list|stop <id>|logs <id>|stop-all` — every server or long task.
        - `closecode config show|set <key> <value>` — pin engine, sdk, buildTools,
          gradleVersion, nodeVersion, pythonVersion, env.* and ports.* in `.closecode.json`.
        - `closecode run` — build, install, launch, and wait until the app is in front.
        - `closecode install` — install the built APK on this phone.
        - `closecode apps list|launch|stop|info|install|uninstall|clear` — package-targeted app control.
        - `closecode ui dump|tap|wait|assert|scroll|type --package PKG` — drive any app (defaults to this project).
        - `closecode intent open|start|broadcast|share` — start activities, deep links, broadcasts and shares.
        - `closecode device battery|network|storage|display|volume|brightness|dnd|clipboard|sensors`.
        - `closecode files list|read|write|delete` — the scoped shared folder.
        - `closecode exec [--cwd DIR] [--timeout MS] [--approve] <command>` — run a shell command with streamed output.
        - `closecode consent status|grant|revoke <capability>` and `closecode audit tail` — grants and the audit log.
        - `closecode stop-all` — stop every job and cancel the running build.

        - `closecode signing status|generate|import` — the key used for release APKs.
        - `closecode launch` — open the installed app on this phone.
        - `closecode app` — installed/foreground state, screen size, and UI tree availability.
        - `closecode stop`, `closecode restart` — send the app home and stop it, or launch again.
        - `closecode ui dump` — the current screen as a JSON tree of visible nodes.
        - `closecode ui tap --text TEXT|--id ID|--desc DESC [--index N]` — tap a node.
        - `closecode ui wait --text TEXT [--gone] [--timeout MS]` — wait for a node.
        - `closecode ui assert --text TEXT|--id ID|--desc DESC` — fail if a node is absent.
        - `closecode ui scroll down|up [selector]` — scroll the screen.
        - `closecode input tap|swipe|longpress|text|key` — raw touch, typing and keys.
        - `closecode accessibility status|enable` — the service that drives other UI.
        - `closecode describe` also reports the detected project type (Gradle Android, npm,
          static HTML, Python, CMake or mixed).
        - `closecode logcat` — the installed app's own log.
        - `closecode screenshot` — a screenshot of what the app shows.
        - `closecode export` — export a file or folder out of the project.
        - `closecode tool aapt2|d8|r8|kotlinc` — run one of the toolchain tools.
        - `closecode github push|pull` — publish the project to its GitHub repository, or take
          what came back. It signs in through CloseCode itself; the token stays in the app.

        CloseCode never runs `./gradlew`. When Full Gradle is installed it is the default
        build engine; otherwise the phone's own engine is used. `closecode build --gradle`
        forces Gradle, and `closecode gradle` runs a named task with the downloaded JDK and
        Gradle distribution. `closecode gradle` accepts assembleDebug/assembleRelease
        (build an APK), installDebug/installRelease and launch/launchRelease (build and
        install or open), bundleDebug/bundleRelease (App Bundle), test/testDebugUnitTest,
        lint/lintDebug, check and tasks, plus flavour tasks such as assembleFreeDebug or
        bundleFreeRelease. Real Gradle handles the project's dependencies, variants and R8.
        Without `--gradle`, test, lint and check run the phone's static checks and bundles
        are refused.

        Projects with C/C++ sources need Full Gradle plus the optional Native tools (NDK
        and CMake) in Settings. Build them with `closecode build --gradle`; the custom
        engine cannot compile native code.

        Testing an app: run `closecode run`, use `closecode ui dump` and `ui assert` to check
        the screen, fix the sources, then run again. Stop after five failed fixes and report
        what still fails. Android shows its own install confirmation; ask the user to confirm
        when `run` reports `needsUser`.
    """.trimIndent() + "\n"

    fun install(project: File): File? {
        val file = file(project)
        if (file.isFile) {
            val existing = runCatching { file.readText() }.getOrNull() ?: return file
            if (!managed(existing)) return file
            if (existing == script()) return file
            return runCatching {
                file.writeText(script())
                file
            }.getOrNull()
        }
        if (!ensureDirectory(directory(project))) return null
        return runCatching {
            file.writeText(script())
            file
        }.getOrNull()
    }

    private fun managed(text: String): Boolean =
        text.contains(MARKER) ||
            (text.contains("name: closecode") &&
                text.contains("closecode build") &&
                text.contains("This phone has no Gradle daemon"))

    private fun ensureDirectory(directory: File): Boolean =
        directory.isDirectory || directory.mkdirs()
}
