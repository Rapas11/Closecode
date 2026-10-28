# Handoff notes

Where the AI-tab work stands and how to pick it up on a fresh clone. Read this
after cloning if you are continuing the MuMu/phone testing of the OpenCode page.

## Resume point

- Branch `main`; Step 6 is build 28: the token-protected `closecode` terminal
  bridge and shared build coordinator. Device verification is still required.
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
4. Run `scripts/prepare-toolchain.ps1` once — it produces `libaapt2.so`,
   `libc++_shared.so` and the other toolchain files that are deliberately not
   committed.
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

`closecode-28-debug.apk` for Step 6 (debug, since `assembleRelease` is blocked
by an unrelated Gradle lint issue on this machine).
