# Handoff notes

Where the AI-tab work stands and how to pick it up on a fresh clone. Read this
after cloning if you are continuing the MuMu/phone testing of the OpenCode page.

## Resume point

- Branch `main` at `54dd4a3`: Steps 1-3 of the AI-tab rework are committed and
  pushed; `versionCode` 25 ships them. Nothing was pushed after that.
- Step 1 `c679fe5` — bind the OpenCode page to the real project directory.
- Step 2 `b457f15` — replace the AI tab with the OpenCode page surface.
- Step 3 `54dd4a3` — restore OpenCode's project route and repair stale tabs.
- Untouched working rule: one commit per step, full unit suite green, bump
  `versionCode`, send an APK after each step, no force-push, keep the untracked
  `real opencode open pc/` folder out of the repo.

## Open bug (next work item)

On phones the Android WebView resolves **every vertical viewport unit to
`0px`** (`100vh`, `100dvh`, `100svh` all probe as `0px` while
`window.innerHeight` is correct). `OpenCodeChatScreen.kt` calls this out and
already patches OpenCode's root element inline
(`installViewportFix` / `PAGE_VIEWPORT_SCRIPT`).

That fix is not enough for the page's dialogs: OpenCode's *Select model* and
*Connect provider* sheets size themselves with `max-h-[calc(100vh - 16px)]`,
so the sheet opens with height 0 — the header renders, the model list is
invisible, and taps fall through. This is the "settings/model list does not
open" report and most likely also the "text disappears / page restarts"
symptom reported while typing, so it must be fixed *before* re-testing
anything else.

Planned fix: extend the viewport patch from only `#root` to the dialog
containers (inject CSS overrides that use the measured `window.innerHeight`
instead of viewport units), verify on MuMu, then re-verify the model picker,
provider sheet and typing.

## OpenCode upgrade request

The user asked to upgrade from the pinned 1.18.31 to 1.19.4 (or 2.0).
`OpenCodeDefaults.VERSION` in `OpenCodeModel.kt` pins the download tag; the
comment there warns 1.19.4's page crashed on older WebViews when the viewport
units collapse — which is the same root bug above. Do not bump the pin until
the viewport fix is proven on a device.

## Environment on a new machine

1. JDK 21, exported as `JAVA_HOME` (this machine used
   `C:\Users\HomePC\AppData\Local\Temp\opencode\jdk21`; that path is machine
   local and not required on other machines).
2. Android SDK with `platform-tools`, a recent platform and build-tools;
   `ANDROID_HOME` set or `local.properties` pointing at it.
3. Android Studio's NDK only if rebuilding the PTY bridge
   (`scripts/build-pty.ps1`). `libclosepty.so` is already committed for normal
   builds.
4. Run `scripts/prepare-toolchain.ps1` once — it produces `libaapt2.so`,
   `libc++_shared.so` and the other toolchain files that are deliberately not
   committed.
5. First build: `.\gradlew.bat :app:assembleDebug --console=plain`
   (and `:app:testDebugUnitTest --console=plain` before any commit).
6. Emulator testing: MuMu Player, ADB reachable on `127.0.0.1:16384`; output is
   on a secondary display, find the id from `adb shell dumpsys SurfaceFlinger
   --display-id` and pass it to `adb shell input`. Screenshots only render for
   display 0 plus the WebView's own CDP page — capture page state over CDP, not
   `screencap`.

## Latest deliverable

`closecode-25-debug.apk` for Step 3 (debug, since `assembleRelease` is blocked
by an unrelated Gradle lint issue on this machine).
