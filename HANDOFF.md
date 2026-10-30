# Handoff notes

Where the AI-tab work stands and how to pick it up on a fresh clone. Read this
after cloning if you are continuing the MuMu/phone testing of the OpenCode page.

## Resume point

- Branch `main`; Step 18 is build 40: build-cache metadata exclusion, stale
  pending-reservation cleanup, screenshot clearing on OpenCode project switch,
  and null `parentID` root sessions. Step 17 is build 39: stable project
  identity, permanent name/package reservations, per-project OpenCode sessions,
  unified-only diffs, long-press Rename/Delete, onboarding, private screenshots
  and the cursor-width fix. Step 16 is build 38: first-run onboarding, real
  project setup before the IDE opens, lazy commit-history diffs, Java-only app
  templates, and Gradle wrapper/AGP metadata. Step 15 is build 37: Shell tools now has a
  visible status/download/remove page under Settings → Terminal, and OpenCode
  only shows or opens a server owned by the current project. Step 14 is build
  36: editor horizontal-follow fix, Material icons and shared diff scrolling.
  Step 13 is build 35: readable accent labels and a real IDE Back stack.
  Step 12 is build 34: settings foundation, SDK Manager, signing keys,
  final-APK build cache, AI as a full page, More kept as its sheet, and a
  compact search-options menu. Step 11 is build 33: OpenCode's shell tool gets
  the optional Termux environment. Step 10 is build 32: the one-time manual
  `aapt2` is inside the APK. Step 9 is build 31: the optional on-demand Termux
  shell-tools pack. Step 8 is build 30: `closecode describe`, short command
  aliases, and `aapt2`/`d8`/`r8`/`kotlinc` wrappers. Device verification of
  Steps 5–16 is still required.
- Step 8 `00b1407` — project description, aliases and build-tool wrappers.
- Step 7 `5002de3` — install, launch, logcat, export, screenshot and optional
  accessibility input from `closecode`.
- Step 6 `c028923` — token-protected `closecode` terminal bridge and shared
  build coordinator.
- Step 5 `3e42589` — OpenCode 1.18.35, atomic binary replacement, Android CA
  export and a loopback proxy for the server and shell tools.
- Step 1 `c679fe5` — bind the OpenCode page to the real project directory.
- Step 2 `b457f15` — replace the AI tab with the OpenCode page surface.
- Step 3 `54dd4a3` — restore OpenCode's project route and repair stale tabs.
- Step 4 `d54f722` — keep the page session/tab state and fix viewport units.
- Untouched working rule: one commit per step, full unit suite green, bump
  `versionCode`, send an APK after each step, no force-push, keep the untracked
  `real opencode open pc/` folder out of the repo.

## Step 5 (build 27)

- `OpenCodeDefaults.VERSION` is pinned to `1.18.35`; downloads now come from
  `anomalyco/opencode`.
- Binary installs are staged and replaced atomically, with the old program kept
  until the new one is in place. A changed install clears
  `cache/opencode/models.json` once.
- `OpenCodeCa` exports Android's system roots to
  `files/opencode/ca.pem` and passes them to Node, curl and git through
  `SSL_CERT_FILE`, `NODE_EXTRA_CA_CERTS`, `CURL_CA_BUNDLE`, `GIT_SSL_CAINFO`
  and `NODE_USE_SYSTEM_CA`.
- `OpenCodeProxy` listens only on `127.0.0.1`, handles CONNECT and absolute-URL
  HTTP with full-duplex bodies, and exports `HTTP(S)_PROXY`/`NO_PROXY` for the
  server and the auth wrapper.
- Orphan cleanup reads the PID file and scans `/proc`, killing only this UID's
  `opencode ... web` processes. Port choice falls back through
  `4098..4118` and logs when it does.
- Unit tests cover the new version/URL, CA PEM/env, proxy env and forwarding,
  orphan command matching, and atomic replacement. `:app:testDebugUnitTest`
  and `:app:assembleDebug` are green.
- Still to check on a device: 1.18.35 page/model picker in the WebView, proxy
  CONNECT/HTTPS traffic, `opencode auth login` with the exported CA, orphan
  cleanup after force-stop, and port fallback.

## Step 6 (build 28)

- `CloseCodeControl` listens only on `127.0.0.1` with a fresh 32-byte token per
  app process. The token and port live in
  `files/opencode/control.token` / `control.port`; the executable
  `files/opencode/bin/closecode` reads them and speaks one JSON line per
  request. Commands are `ping`, `status`, `cancel` and `build`.
- The terminal PATH includes `files/opencode/bin`, so `closecode build` works
  from any terminal and from OpenCode's shell. `build` resolves the current
  directory against registered projects (or accepts a project slug), refuses
  unregistered folders, and streams `start`, `state`, `log` and `done` JSON
  lines. `status` reports the active state/log tail or the last result.
- `BuildCoordinator` owns the active/last build, starts the existing
  `BuildService`, registers `BuildCancel`, and shares `BuildEngine` instances
  per slug. `BuildSetupFactory` is the single project/toolchain setup path used
  by both the Build panel and the bridge. `BuildLock` still serializes UI and
  bridge builds.
- `BuildChain` now accepts `onLog` and writes `build/logs/build.log` plus
  timestamped `build-*.txt` logs on success and failure. `BuildReport` turns
  build state/outcomes into JSON and supplies the log tail.
- `TerminalEnvironment` exposes `envMap()`/`applyTo()` and adds the OpenCode
  `bin` directory to PATH; the pipe fallback in `IdeTerminal` uses it.
- `MainActivity` and `OpenCode.start` start the control server idempotently.
  `BuildService.finish` now clears its running flag, and a failed service start
  does not leave it stuck.
- Tests: `BuildReportTest` covers state/outcome/log JSON, and
  `CloseCodeControlTest` checks the generated command script with `sh -n`.
  `:app:testDebugUnitTest` and `:app:assembleDebug` are green.
- Still to check on a device: `nc` streaming/half-close behavior in the
  `closecode` script, token rejection, `status`/`cancel` while a build runs,
  build-from-terminal and build-from-OpenCode, foreground-service start from a
  background terminal, and whether a bridge-started build is reflected in the
  Build panel. A control build is tracked by `BuildCoordinator` but the panel
  still calls `BuildEngine.build` directly.

## Step 7 (build 29)

- `closecode` now accepts `install [apk]`, `launch [package]`,
  `logcat [package] [lines]`, `export [path]`, `screenshot [path]`,
  `input tap|swipe|text|key ...`, and `accessibility`. The command script sends
  the remaining shell arguments as a JSON `args` array.
- `install` defaults to `build/outputs/apk/debug/app-debug.apk`, reads the
  package name from the APK, and uses `PackageInstaller`. Android's own
  confirmation is still mandatory; without the per-source permission it returns
  `needsPermission`.
- `launch` uses the package manager's launch intent, with the package inferred
  from `build/build.json` or the project's Gradle files when omitted. `logcat`
  uses the existing `LogcatReader`; its note explains Android's cross-app log
  limit instead of pretending the log is complete.
- `export` copies a single file (for example an APK) or zips a directory into
  `files/exports`, skipping generated `build`, `.gradle` and `.git` roots.
- `screenshot` opens a transparent `ScreenshotActivity`, requests MediaProjection
  consent, captures one frame and writes `build/screenshots/screenshot-*.png`
  (or the path supplied). Consent is per request and can be cancelled.
- `input` uses the optional `CloseCodeAccessibilityService`. The user must
  enable CloseCode in Android's accessibility settings; `closecode accessibility`
  opens that screen. Gestures use `dispatchGesture`, text uses `ACTION_SET_TEXT`
  on the focused editable node, and keys map to the global back/home/recents/
  notifications actions.
- Tests: `ExportHelperTest` covers project zips, generated-output exclusions and
  non-overwriting file exports; the command-script test now validates the new
  commands and `args` payload. `:app:testDebugUnitTest` (1272 tests) and
  `:app:assembleDebug` are green.
- Still to check on a device: the installer consent screen and permission
  return, launch from a background terminal, real cross-app logcat visibility,
  screenshot consent/rotation and frame quality, accessibility enablement,
  tap/swipe/text on a real app, and the exported file path being reachable from
  the terminal.

## Step 8 (build 30)

- `closecode describe [project]` reports the registered project's package,
  version, SDK levels, Kotlin/Compose facts, source counts, existing APK,
  check findings, toolchain readiness/missing files and the last build result.
  It works before the build tools are installed.
- The command resolver accepts short aliases: `b`/`build`, `s`/`status`,
  `c`/`cancel`, `p`/`ping`, `i`/`install`, `l`/`launch`, `log`/`logcat`,
  `x`/`export`, `shot`/`screenshot`, `in`/`input`, `a`/`accessibility`,
  `d`/`describe`, `t`/`tool`.
- `files/opencode/bin` now also gets `aapt2`, `d8`, `r8`, `kotlinc` and a `cc`
  alias. Each tool wrapper calls `closecode tool <name> ...`; the bridge runs
  the bundled native aapt2 or the no-exit kotlinc/D8/R8 entry points and
  forwards the real exit code. Tool output uses a final
  `__CLOSECODE_TOOL_EXIT__<code>__` line so the wrapper can print normal output
  and exit correctly.
- The wrappers refuse to run while a build is active, require a registered
  project directory, and report that build tools must be installed first.
  `kotlinc` adds `-no-jdk` and `-kotlin-home` unless the caller supplied them.
- Tests cover project description, wrapper scripts, alias resolution and
  kotlinc argument defaults. `:app:testDebugUnitTest` (1280 tests) and
  `:app:assembleDebug` are green.
- Still to check on a device: wrapper execution from the PTY/OpenCode shell,
  aapt2 library loading, kotlinc/D8/R8 argument paths and exit codes, `cc`
  alias, and `describe` against real imported projects.

## Step 9 (build 31)

- `closecode tools [status|install|remove]` manages an optional shell-tools
  pack. Nothing is bundled in the APK and nothing runs as root; the pack is
  downloaded only when the user asks for it.
- `install` reads the current Termux `Packages` index for the phone's ABI,
  resolves the roots (`git`, `curl`, `python`, `nodejs`, `zip`, `jq`,
  `ca-certificates`), verifies every `.deb` against its SHA-256 and size,
  extracts `data.tar.xz`/`data.tar.gz`, and rejects paths outside the Termux
  prefix. The current root closure is about 40,057,328 bytes compressed; the
  installed tree is larger.
- Packages are extracted under `file/u.tmp`, patched, checked for the required
  commands and `bin/sh`, then swapped into `file/u`; home is `file/hh`. The
  replacement is exactly 31/32 bytes, so Termux binaries are rewritten in
  place from `/data/data/com.termux/files/usr` and `.../home`. The app package
  must remain `com.closecode` for the path to fit.
- Absolute symlinks are rewritten to the final install root (not the temporary
  directory), relative symlinks are preserved, and deletion does not follow
  symlinked directories. `bin/sh` and `bin/env` link to `/system/bin` when the
  packages do not provide them.
- The terminal adds `file/u/bin` to PATH and exports `PREFIX`,
  `TERMUX_PREFIX`, `LD_LIBRARY_PATH`, `TMPDIR`, `SHELL` and the CA variables
  when the pack is present. `closecode describe` now includes a `shellTools`
  object with readiness, installed bytes and prefix.
- Tests cover the manifest, dependency resolution, ar/deb reading, tar
  extraction (including absolute symlinks), prefix replacement and the new
  command script. `:app:testDebugUnitTest` (1295 tests, 8 skipped) and
  `:app:assembleDebug` are green; versionCode 31 was verified.
- Still to check on a device: `tools install` download/progress, disk use,
  running `git`/`python3`/`node`/`zip`/`jq`/`curl` from the PTY, CA trust,
  `LD_LIBRARY_PATH`, symlinks, `tools remove` and reinstall, interruption
  during install, and whether any package script needs `termux-exec`.

## Step 10 (build 32)

- `libaapt2.so` is now placed manually at
  `app/src/main/jniLibs/arm64-v8a/libaapt2.so` and packaged into the APK. It
  comes from `build-tools-34.0.4-aarch64.tar.xz` from
  AndroidIDEOfficial/androidide-tools (SHA-256
  `4bbbdeee608ff8d3a2c1534d0a5270305a02b1f200403a536c75ac84c41e52fa`).
- The file is deliberately gitignored and not committed. This is the one-time
  local copy; a fresh clone or another machine must place it again. The current
  `aapt2` is an ARM64, statically linked executable, so no
  `libc++_shared.so` is required.
- The APK contains `lib/arm64-v8a/libaapt2.so`; versionCode 32 and package
  `com.closecode` were verified. `:app:testDebugUnitTest` passed 1295 tests,
  8 skipped, 0 failures/errors, and `:app:assembleDebug` is green.
- `closecode build` should no longer report `missing: ["aapt2"]`, but no device
  is connected here, so the on-device build/install/launch check is still
  pending. Step 11 covers the OpenCode shell-tools environment.

## Step 11 (build 33)

- The OpenCode server now puts `file/u/bin` on PATH and exports
  `CLOSECODE_SHELL_TOOLS`. The generated `bash` shim reads that variable on
  every invocation; when `file/u/bin/sh` exists it exports `PREFIX`,
  `TERMUX_PREFIX`, `PATH`, `LD_LIBRARY_PATH`, `TMPDIR` and `SHELL` for the
  command. The Termux libraries are therefore not put into the OpenCode
  process environment while its musl loader starts.
- Because the shim checks at invocation time, installing the optional pack
  after the server has started still makes its commands available to
  OpenCode's shell tool without restarting the server.
- The `opencode auth login` wrapper gets the shell-tools PATH and
  `PREFIX`/`TERMUX_PREFIX` when the pack is installed. `closecode` remains
  available through `files/opencode/bin`.
- Tests cover the auth wrapper and bash shim environment.
  `:app:testDebugUnitTest` passed 1297 tests, 8 skipped, 0 failures/errors, and
  `:app:assembleDebug` is green; versionCode 33.
- Still to check on a device: install the shell tools, then run
  `git --version`, `python3 --version`, `node --version`, `curl`, `zip` and
  `jq` from OpenCode's shell tool; verify commands still work when the pack is
  absent; check `opencode auth login`; and confirm the server's own startup is
  unaffected by Termux libraries.

## Fixed in build 26 (pending device check)

### Session and tab restore

- Removed the per-launch cookie/local-storage wipe and the destructive
  `installProjectStateFix` that deleted session tabs and reloaded the page
  after 800 ms. OpenCode's own tab state is now left intact.
- `OpenCodeChatScreen` now receives `baseUrl`, `projectUrl` and
  `projectDirectory`. It saves the last restorable page path per project in
  `closecode_open_code_page` and loads it on the next launch or reconnect.
  Session paths are rebased to the current server key so a port change does
  not break them.
- On a first launch with no saved path, the page loads `/` with a
  `?closecode_resume=1` marker. An injected script reads `tabs`,
  `tabs.recent`, `tabs.info` and legacy `lastProjectSession`, scopes the
  candidates to the project directory, and redirects to the chosen tab.
  Draft tabs whose server key changed are rewritten before navigation. If
  nothing matches, it falls back to `projectUrl`.
- The page bar now refreshes, copies and opens the current page URL rather
  than the original project URL.

### Viewport units

- `PAGE_VIEWPORT_SCRIPT` now sets `--closecode-vh` from
  `window.innerHeight` and rewrites `vh`, `dvh`, `svh` and `lvh` in
  same-origin stylesheets to `calc(var(--closecode-vh) * N)`.
- It walks nested `@media`/`@supports`/`@keyframes`, adopted stylesheets and
  inline styles, and observes new stylesheets and style attributes.
- The existing `#root` inline-height patch is kept. This should fix the
  settings/model dialog height as well as the page-restart symptom.

### Verification

- `:app:testDebugUnitTest` is green after rerunning one flaky performance
  test.
- `:app:assembleDebug` is green.
- Still to check on MuMu/phone: relaunch restores the same session/draft and
  keeps typed text; settings and model picker open with a visible list;
  typing no longer restarts the page.

## OpenCode upgrade request

The user asked to upgrade from the pinned 1.18.31 to 1.19.4 (or 2.0).
Step 5 moves the pin to 1.18.35, the latest 1.18.x release. The comment in
`OpenCodeModel.kt` warns 1.19.4's page crashed on older WebViews when the
viewport units collapse — the same root bug above. Do not move to 1.19.4 until
the 1.18.35 page is proven on a device.

## Step 15 (build 37)

- Settings → Terminal now has a real Shell tools row and page. It reports
  installed/not-installed, shows the installed size and package list, downloads
  the existing Termux pack with progress, and removes it again. The page says
  plainly that this is an optional Termux pack, not an Android system install:
  no root and no apt, and pip/npm only work for packages that do not need
  extra native libraries.
- The terminal still uses `/system/bin/sh` through the arm64 PTY. Once the
  pack is installed, `TerminalEnvironment` adds `file/u/bin` to PATH and
  exports `PREFIX`, `TERMUX_PREFIX`, `LD_LIBRARY_PATH`, `TMPDIR`, `SHELL` and
  the CA variables; existing shells need to be reopened. Without the pack the
  terminal has only Android's own commands.
- The AI tab now checks which project owns the running server. A server for
  another project shows that project's name with Stop and “Stop and start this
  project”; it no longer displays the other project's Running card or opens its
  page. Auto-open only fires for the current project.
- The OpenCode page resume script no longer force-adopts pending tabs from the
  same server when no tab carries the current project directory. It falls back
  to this project's own URL/session instead. Back always leaves the page rather
  than walking WebView router history, so Back cannot land on a new-session
  route. A one-time page-route migration clears saved routes written by build
  36, which could already have a cross-project session stored under the wrong
  project; the session itself remains in the page's storage.
- Settings now has 49 rows (the new Shell tools row) and a test pins its
  action. Tests: `:app:testDebugUnitTest` passed 1308 tests, 8 skipped, 0
  failures; `:app:assembleDebug` is green. `closecode-37-debug.apk` is 38 MB
  (39,591,991 bytes, SHA-256
  `4bd4bd57bba6872fd5484f258e3bb163cfd1a7c588537df1c4b0461ec1cc8423`),
  package `com.closecode`, versionCode 37, v2-signed, with
  `lib/arm64-v8a/libaapt2.so` (SHA-256
  `be2cea61814678f7a9e61bf818a6666e6097a7d67d6c19498a4d7aa690bc4151`) and
  `lib/arm64-v8a/libclosepty.so`.
- Still to check on a device: download and remove the shell-tools pack, then
  run `git`, `python3`, `node`, `curl`, `zip` and `jq` from a new PTY tab;
  confirm CA trust and `LD_LIBRARY_PATH`; switch between two projects with
  OpenCode running and check Stop, Stop-and-start, session resume and system
  Back. No adb/device is connected here.

## Step 16 (build 38)

- First run now opens a real onboarding page before Home. Build tools, shell
  tools and OpenCode each have their own skippable step with real downloads:
  build tools use `ToolchainDownloadService`, shell tools use
  `ShellToolsStore.install`, and OpenCode pre-downloads its binary plus Alpine
  runtime. Finishing or skipping marks `first_run_completed` in the separate
  `FirstRunStore`; Settings → General → Startup → Setup guide reopens the same
  flow without making onboarding reappear. Settings now has 50 rows; the new
  Setup guide row is pinned by a test.
- Creating a project now scaffolds immediately and, for Android projects,
  opens `ProjectSetupScreen` before the IDE. It runs the real
  `BuildSetupFactory` check, shows missing tools, starts the existing toolchain
  download with progress/pause/cancel, re-checks when the download finishes,
  and only opens the editor when the toolchain is actually ready. Closing it
  leaves the project in Recents. The old fake `ProvisioningScreen` is gone.
- Commit history loads file paths first; each file's diff is fetched only when
  its header is expanded. Diffs are collapsed by default, use a `LazyColumn`
  with bounded horizontal scrolling per file, and share `flattenUnifiedDiff`
  with the full-screen diff. Very large diffs show a notice instead of loading.
  `CommitDiffLazyTest` covers the flattening/gap/column rules.
- Java is now a real app-template language. Java projects get
  `MainActivity.java`, `res/layout/activity_main.xml`, framework Views and a
  platform theme; they contain no Kotlin or Compose files and their Gradle
  files omit the Kotlin plugin and Compose dependencies. All six app templates
  have Java extras. `ProjectCheck` only requires a Kotlin version when the
  project actually uses Kotlin.
- Gradle selection now writes `gradle/wrapper/gradle-wrapper.properties` and
  maps the selected Gradle version to AGP 8.12.0 / 8.10.1 / 8.7.3 / 8.3.2 for
  8.13 / 8.12 / 8.9 / 8.5. The config screen labels Gradle as desktop/export
  metadata and warns when `compileSdk` is newer than the mapped AGP supports.
  No wrapper jar/scripts are written and Gradle still does not run on-device.
- Tests: `:app:testDebugUnitTest` passed 1323 tests, 8 skipped, 0 failures;
  `:app:assembleDebug` is green. `closecode-38-debug.apk` is 38 MB
  (39,796,055 bytes, SHA-256
  `f627a97c402076f7ce893c1ac330a0de557c496a38b6a462c34d4a42afc48526`),
  package `com.closecode`, versionCode 38, v2-signed, with
  `lib/arm64-v8a/libaapt2.so` (SHA-256
  `be2cea61814678f7a9e61bf818a6666e6097a7d67d6c19498a4d7aa690bc4151`) and
  `lib/arm64-v8a/libclosepty.so`.
- Still to check on a device: first-run downloads/skips and notification
  permission; create an Android project with missing tools, pause/cancel and
  resume; open the same setup from Settings; expand/collapse commit diffs on a
  large history; create Java projects from each app template and build one;
  switch Gradle versions and confirm wrapper/AGP output. No adb/device is
  connected here.

## Step 17 (build 39)

- Projects now have a permanent UUID identity that survives rename, folder move
  and package changes. `ProjectCodec` writes the seven fields
  `id`, `slug`, `name`, `tab`, `templateIndex`, `templateName` and `config`,
  and still decodes the old six-field route.
  `ProjectRegistry` reserves every name, package and slug case-insensitively
  forever, including deleted projects; duplicate packages are an error with
  “Another project uses this package. Choose a different package name.”
- Create and import claim the identity before scaffolding and cancel on failure.
  Rename updates metadata only; delete keeps the reservation. Build, terminal,
  IDE and OpenCode session state are keyed by UUID, not the display slug.
- OpenCode now discovers the project's root session with
  `GET /session?directory=…&roots=true&limit=100`, creates one only when needed,
  verifies the returned directory, prefers the remembered session, and opens
  `/server/<key>/session/<id>` directly. Deleting a project forgets its session.
- Diff view is unified only: the side-by-side toggle, wide breakpoint, rotation
  hint and second renderer are gone. `unify` now emits a removed row then an
  added row for replacements. Recent projects use long-press for Rename/Delete;
  there is no three-dot button.
- Onboarding adds the welcome panel, focused download panels and Finish gating.
  Screenshots are stored privately under `cache/screenshots`, hidden from the
  project tree and cleared on app start, service destroy, Stop and tab Stop;
  image files get a real preview. The editor cursor-width calculation now uses
  the measured code width plus the left inset.
- Tests: `:app:testDebugUnitTest` passed 1340 tests, 8 skipped, 0 failures;
  `:app:assembleDebug` is green. `closecode-39-debug.apk` is 42 MB
  (43,916,717 bytes, SHA-256
  `b3d6207024f562f37eb058e1cc78d570955efbb8dc04136f56eddebd3d9c1ed8`),
  package `com.closecode`, versionCode 39, v2-signed.
- Still to check on a device: rename/move/package changes keeping identity and
  history; duplicate-name/package errors; OpenCode session resume across two
  projects; side-by-side removal and long-press menus; onboarding downloads;
  image preview; private screenshots; cursor alignment when the keyboard opens.

## Step 18 (build 40)

- The final-APK cache key now ignores the project's root `.closecode.json`, but
  does not ignore matching files elsewhere in the project. Opening or updating
  metadata no longer invalidates a valid APK; actual build-config changes are
  still supplied separately and keep affecting the key.
- Pending project reservations from an earlier process are discarded the first
  time `ProjectRepository` is used; claims made in the current process survive.
  A crash while scaffolding no longer permanently blocks a name or package.
- The OpenCode project switch dialog now uses the same stop-and-start path as
  the retry card, so switching projects also clears the private screenshot
  cache before the new server starts.
- OpenCode session selection tolerates a JSON `parentID: null`; it now
  identifies the session as a root rather than treating the string "null" as a
  parent.
- Tests: `:app:testDebugUnitTest` passed 1343 tests, 8 skipped, 0 failures;
  `:app:assembleDebug` is green. `closecode-40-debug.apk` is 40 MB
  (40,439,249 bytes, SHA-256
  `2dd8270e0b8f7cc7f3a91d734081ce4eed93016cbaf373f44cf257c15d61f3b8`),
  package `com.closecode`, versionCode 40, v2-signed, with
  `lib/arm64-v8a/libaapt2.so` and `lib/arm64-v8a/libclosepty.so`.
- Still to check on a device: a project build before and after metadata
  refresh, stale/slow scaffolding crash recovery, switching OpenCode projects
  and screenshot visibility, and session listing with `parentID: null`.

## Environment on a new machine

1. JDK 21, exported as `JAVA_HOME` (this machine used
   `C:\Users\HomePC\AppData\Local\Temp\opencode\jdk21`; that path is machine
   local and not required on other machines). The Linux workspace uses JDK 17
   at `/opt/java/jdk-17.0.20.1+1` and the SDK at `/opt/android_sdk`.
2. Android SDK with `platform-tools`, a recent platform and build-tools;
   `ANDROID_HOME` set or `local.properties` pointing at it.
3. Android Studio's NDK only if rebuilding the PTY bridge
   (`scripts/build-pty.ps1`). `libclosepty.so` is already committed for normal
   builds.
4. Run `scripts/prepare-toolchain.ps1` once — it produces `libaapt2.so` and
   the other toolchain files that are deliberately not committed. The current
   ARM64 `aapt2` is statically linked; `libc++_shared.so` is not required.
5. First build: `.\gradlew.bat :app:assembleDebug --console=plain`
   (and `:app:testDebugUnitTest --console=plain` before any commit). On Linux
   use `./gradlew` with `JAVA_HOME` and `ANDROID_HOME` exported. If the Kotlin
   daemon hangs, use
   `--no-daemon -Dkotlin.compiler.execution.strategy=in-process`.
6. Emulator testing: MuMu Player, ADB reachable on `127.0.0.1:16384`; output is
   on a secondary display, find the id from `adb shell dumpsys SurfaceFlinger
   --display-id` and pass it to `adb shell input`. Screenshots only render for
   display 0 plus the WebView's own CDP page — capture page state over CDP, not
   `screencap`.

## Latest deliverable

`closecode-40-debug.apk` for Step 18 (debug, since `assembleRelease` is blocked
by an unrelated Gradle lint issue on this machine).

## Step 12 (build 34)

- Navigation: the AI tab is now a full page with its own `IdeFullPageBar`
  header and Back; the bottom bar stays visible. More is deliberately still the
  anchored `IdeMoreSheet` — only AI was promoted to a page. Active AI taps do
  nothing; `panelBeforeAi` remembers the panel underneath.
- The search sheet's options are a fixed-width 200 dp Material3 `DropdownMenu`
  anchored to the sliders button. The old full-width dismiss layer and
  `SpecMenu` import are gone; an outside tap dismisses the menu.
- Settings now has 48 rows: 19 wired toggles, 0 unwired toggles, 6 option rows
  and 3 free-text Git rows. Removed the fictional account group and the five
  unwired AI rows; AI settings and biometric lock are single “Coming soon”
  destinations.
- New settings: code folding, auto import, parameter hints, quick fixes, build
  cache, command history, animations, accent index, shell choice, Git name,
  email and default branch. Accent is persisted by index and applied through
  `ClosecodeTheme(accent = …)` to every accent-bearing `PageColors` token.
  The default shell is “Shell tools”, which keeps the old PTY terminal; choosing
  “System” switches to the plain pipe shell.
- `SettingsPages.kt` adds the SDK Manager, Signing keys, Credentials, Privacy
  and Licenses pages. `PlatformStore` downloads official Google platforms
  34/35/36 (SHA-1 verified) and AndroidIDE build-tools 34.0.4 (SHA-256
  verified), extracting `android.jar` and `aapt2`. The bundled
  `libaapt2.so` remains the fallback.
- `SigningStore` supports the debug key, imported PKCS#12/JKS keys and a
  device-bound generated release key. Imported passwords use
  `EncryptedSharedPreferences`; generated keys cannot be exported for Play.
- `ProjectCheck` accepts any installed compile SDK and points missing ones at
  Settings → Build & Toolchain → SDK Manager. `BuildSetupFactory` reads the
  project's compile SDK, uses the downloaded platform jar/aapt2 when present,
  and signs through `SigningStore`.
- `BuildCache` caches only the final signed APK under
  `cache/build-cache/<slug>/<sha-256>.apk`. The key covers project files, build
  config, tool files, platform jar, aapt2, libraries, Compose plugin, stdlib and
  signer certificate. Cache errors fall back to a full build; the Build cache
  toggle disables it.
- Editor wiring: code folding is identity when off; parameter hints, quick
  fixes, command-history persistence and auto-import are gated; the new
  `motion()` helper makes the editor, drawer, popups, scrollbar and settings
  switches snap when Animations is off. Git commits/repositories use the
  configured identity/default branch, and the terminal uses the selected shell.
- Tests: `:app:testDebugUnitTest` passed 1304 tests, 8 skipped, 0 failures;
  `:app:assembleDebug` is green. `closecode-34-debug.apk` is 39 MB, package
  `com.closecode`, versionCode 34, v2-signed and contains
  `lib/arm64-v8a/libaapt2.so` (SHA-256
  `d62f2e925bdf90c91c97b7d18cd7990347a3ca9b35c62c6bc75f42212b4afbc4`).
- Still to check on a device: AI full-page Back chain, More sheet return paths,
  search menu placement, every new settings subpage, SDK license/download/
  extraction, build-tools aapt2 execution, imported and generated signing keys,
  a cache hit followed by an edited rebuild, Git identity/default branch,
  shell choice, and all animation-off paths. No adb/device is connected here.

## Step 13 (build 35)

- Accent colours no longer make their own labels disappear. The selected accent
  is used for the solid primary fill, while `primaryTint` stays translucent and
  `link` stays a readable foreground; `af` is chosen black or white from the
  accent's luminance. This restores the “Copy example” outline button, the
  tinted terminal keys (ESC/TAB/CTRL/ALT), and the Git/GitHub tinted icons.
- The primary button now uses the same on-accent colour instead of a hardcoded
  white label, so orange and green accents stay legible.
- IDE Back is now a real history stack, not a fixed “Code is underneath” rule.
  Terminal → Logcat → Back returns to Terminal; AI → Terminal → Back returns to
  AI; Preview → Terminal → Back returns to Preview. The stack is capped at 32
  entries and resets with the project. Search-result return and the existing
  sheet/dialog ordering still win over the panel stack.
- The system navigation bar is painted with the page background instead of the
  white platform default, and its icon appearance follows the light/dark theme.
  The theme XML sets the same colour for the first frame.
- Tests: `:app:testDebugUnitTest` passed 1304 tests, 8 skipped, 0 failures;
  `:app:assembleDebug` is green. `closecode-35-debug.apk` is 38 MB, package
  `com.closecode`, versionCode 35, v2-signed and contains
  `lib/arm64-v8a/libaapt2.so` (SHA-256
  `85ea983eceb39f0e77db68b5f79ade464bd336bab0af165e2b135a584d84339e`).
- Still to check on a device: the two reported invisible-label spots, other
  accent-tinted surfaces, the white bottom strip in both themes, and the
  Terminal/Logcat/AI/Preview Back chains.

## Step 14 (build 36)

- The editor no longer jumps sideways when a tap opens the keyboard. The
  horizontal follow now waits until the measured text layout belongs to the
  current code window and contains the caret, instead of clamping the caret to
  the end of a stale slice. `targetHScroll` also refuses to move an unmeasured
  viewport and caps its margin at half the viewport, so a zero-width layout pass
  cannot scroll the code by one margin.
- Added regression tests for the unmeasured and narrow-viewport cases.
- Replaced the prototype icon paths with Material Design 24 x 24 vectors behind
  the existing `CcIcons` facade. The top bar, terminal, explorer and Git panel
  now use the same outlined/filled family: Menu, Save, Undo/Redo, PlayArrow,
  Terminal, AccountTree, History, CloudDownload, Download/Upload, FolderOpen,
  Description, NoteAdd, CreateNewFolder, UnfoldLess, and the standard chevrons.
  No new icon dependency was added, so the APK does not carry the 30 MB
  `material-icons-extended` dex.
- The GitHub history row now uses a History glyph instead of a clock.
- Unified commit diffs now have one horizontal scroll axis per file card. All
  rows in the card pan together, including when the card is shown under
  “CHANGED FILES”, instead of each row owning a private scroll state. Wrapped
  diffs still bypass horizontal scrolling.
- Tests: `:app:testDebugUnitTest` passed 1307 tests, 8 skipped, 0 failures;
  `:app:assembleDebug` is green. `closecode-36-debug.apk` is 38 MB, package
  `com.closecode`, versionCode 36, v2-signed and contains
  `lib/arm64-v8a/libaapt2.so` (SHA-256
  `2428c68b568e46d8d9d648488140385edbc9fe5c2f8a3d70d32f7a7aef08a528`).
- Still to check on a device: tap near the start and end of a long line with the
  keyboard opening/closing, horizontal pan on a multi-file commit diff, and the
  new icon weight/size at 15–20 dp in both themes.
