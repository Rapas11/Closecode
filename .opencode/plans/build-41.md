# Build 41 — approved plan (2026-10-30)

Approved by the user in a recorded plan session. Implement in this exact order, keeping the
full test suite green after every phase.

## Goal
Ship the translation layer for the AI plus all approved OpenCode upgrades in one build.

## Phase list

1. **A. OpenCode page fallback bug** (`app/src/main/java/com/closecode/ui/ide/OpenCodeChatScreen.kt`)
   - `pageTarget()` must never fall back to OpenCode's home page `/` when there is no session
     yet. If no `sessionId` and no saved page path, open the project route supplied in
     `projectUrl` (which is `projectPageUrl(...)`, `/<base64url(dir)>/session`).
   - Extract a pure helper `targetPagePath(baseUrl, projectUrl, savedPath, sessionId)` so a unit
     test can pin that no-session always selects the project route, and blank `projectUrl`
     selects `baseUrl`.
   - Add test in `app/src/test/java/com/closecode/ai/OpenCodeTest.kt`.

2. **B. Gradle translation layer** (`app/src/main/java/com/closecode/control/CloseCodeControl.kt`,
   `ProjectDescription.kt`)
   - New `gradle <task…>` and `check` commands.
   - Mapping: `assembleDebug` → build; `installDebug` → build + install; `launch` → install +
     launch; `test` / `testDebugUnitTest` / `lint` → `ProjectCheck.check`; unknown task → a
     clear error listing the supported mapping.
   - Advertise mapping in `ProjectDescription` `commands` object.
   - Tests must cover each mapping and the unknown-task error.

3. **C. Web search + better default config** (`app/src/main/java/com/closecode/ai/OpenCode.kt`,
   new `OpenCodeConfig.kt`)
   - `OPENCODE_ENABLE_EXA=1` (and `OPENCODE_ENABLE_PARALLEL=1` fallback) in server env.
   - Write a default `XDG_CONFIG_HOME/opencode/opencode.json` only when missing; never
     overwrite an existing file. Cache/config must stay grounded by `ground()`.

4. **D. Skills scaffold** — on project open, if missing, create
   `.opencode/skills/closecode/SKILL.md` describing the `closecode` CLI.

5. **E. GitHub bridge** — new `closecode github push|pull` commands. Drive the repo with
   `GitService` and the stored `TokenStore`; token never written to shell env or logs.

6. **F. Full AI settings page** (new `AiSettingsScreen.kt`, `AiSettingsModel.kt`,
   `OpenCodeConfig.kt`)
   - Provider/model picker via `GET /model`, per-tool permission pickers
     (`read` `edit` `bash` `webfetch` `websearch` `skill` `task` `external_directory`
     `doom_loop`), skill Markdown editor with preview/name validation, danger-zone wipe.
   - Wire to the existing `SettingsAction.AI_SETTINGS`.

## Rules
- `closecodeBuild` bumped to 41 (version code 41, APK `closecode-41-debug.apk`).
- Never overwrite an existing file without explicit user approval.
- Token never exposed in shell env, logs or the AI conversation.
- Real Gradle + real unit tests on the phone are a **future** build, not this one.
- Run `JAVA_HOME=/opt/java/jdk-17.0.20.1+1 ANDROID_HOME=/opt/android_sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain --no-daemon -Dkotlin.compiler.execution.strategy=in-process`
  before delivery.
