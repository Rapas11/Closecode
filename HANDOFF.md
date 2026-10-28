# Handoff notes

Where the AI-tab work stands and how to pick it up on a fresh clone. Read this
after cloning if you are continuing the MuMu/phone testing of the OpenCode page.

## Resume point

- Branch `main`; Step 4 fixes the two open bugs below and raises
  `versionCode` to 26. Device verification of Step 4 is still required.
- Step 1 `c679fe5` — bind the OpenCode page to the real project directory.
- Step 2 `b457f15` — replace the AI tab with the OpenCode page surface.
- Step 3 `54dd4a3` — restore OpenCode's project route and repair stale tabs.
- Untouched working rule: one commit per step, full unit suite green, bump
  `versionCode`, send an APK after each step, no force-push, keep the untracked
  `real opencode open pc/` folder out of the repo.

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
`OpenCodeDefaults.VERSION` in `OpenCodeModel.kt` pins the download tag; the
comment there warns 1.19.4's page crashed on older WebViews when the viewport
units collapse — which is the same root bug above. Do not bump the pin until
the viewport fix is proven on a device.

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
   use `./gradlew` with `JAVA_HOME` and `ANDROID_HOME` exported.
6. Emulator testing: MuMu Player, ADB reachable on `127.0.0.1:16384`; output is
   on a secondary display, find the id from `adb shell dumpsys SurfaceFlinger
   --display-id` and pass it to `adb shell input`. Screenshots only render for
   display 0 plus the WebView's own CDP page — capture page state over CDP, not
   `screencap`.

## Latest deliverable

`closecode-26-debug.apk` for Step 4 (debug, since `assembleRelease` is blocked
by an unrelated Gradle lint issue on this machine).
