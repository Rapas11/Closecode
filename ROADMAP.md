# CloseCode — Roadmap & Resume Notes

_Updated 2026-11-01, after build 45 (light redesign). This file is the
single artifact to pick the work up from — status, device-test resume steps, the
full improvement backlog, and what NOT to do._

## 1. Where things stand

- Remote: `https://github.com/Rapas11/Closecode.git`. Step 21 / build 43 is on
  `main`; build 45 is on the uncommitted `opencode-v2` branch. Conventions: one
  commit per step, unit suite green, bump `closecodeBuild` in
  `app/build.gradle.kts`, build + send APK each step, **no force-push**, never
  commit `real opencode open pc/` or `test-output*.txt`.
- **Build 45 (light redesign, uncommitted on `opencode-v2`):**
  - Full light visual system: `MockColors`, rail/panel shell, rounded cards and
    sheets, light Settings master/detail, Recents, Welcome, project setup.
  - OpenCode keeps its rail entry after Problems; More and all existing actions
    remain reachable. Default theme is Light.
  - `versionName` is `0.45.1466` (build 45, 1466 passing tests), versionCode 45.
    Unit suite: **1466 tests, 0 failures, 0 errors, 8 skipped**. Debug APK is
    40,497,679 B, SHA-256
    `7335b2b12fd948ef612154a8dc4a802ef97c464ddac5e9c38dcc88d339c030ba`.
  - Still not device-tested; the PDF design handoff could not be read here, so
    remaining pixel-level matching needs visual QA against the PNGs.
- **Build 44 (Plans 1–7, uncommitted on `opencode-v2`):**
  - Device loop: `closecode run/app/stop/restart/ui/input` with foreground waits,
    accessibility find/tap/scroll, UI assertions and install polling.
  - Build correctness: aapt2 35.x preference, release signing/paths, `clean`,
    build types, generated BuildConfig fields.
  - Git safety: generated/secret path detection, explorer hiding,
    `.git/info/exclude`, stage/pre-push guards, export/import filters.
  - UI inspector: `IdeUiInspector` for the running CloseCode screen.
  - Optional Full Gradle: resumable Gradle 8.13 + Termux/JDK pack, full SDK
    platform extraction, build-tools 34.0.4, real `assembleDebug/Release`.
  - Optional Native tools: NDK r27b + CMake 3.22.1 installed into the CloseCode
    SDK, detected only for C/C++ projects, wired into Full Gradle.
  - Gradle depth: real `bundleDebug/Release`, `test`, `lint`, `check`, `tasks`,
    flavour tasks such as `assembleFreeDebug`, APK/AAB discovery, and R8 via the
    project's own release config.
  - `versionName` is `0.44.1461` (build 44, 1461 passing tests), versionCode 44.
    Unit suite: **1461 tests, 0 failures, 0 errors, 8 skipped**. Debug APK
    (`closecode-0.44.1461-debug.apk`) is 40,416,471 B, SHA-256
    `ba7f3acef41270fcfabbe3dcfacb9972f212a94cca3e8d8b10e8a994a9dccf23`.
  - Still not device-tested: Full Gradle/NDK execution, install confirmation,
    and the device flows listed below.
- **Build 43 (Step 21 — "hands-free OpenCode"):**
  - AGENTS.md seeding: `OpenCodeAgentMd` writes/merges a marked block
    (`<!-- closecode:start(b43) -->`) into the project and OpenCode home
    `AGENTS.md`, teaching only real `closecode` verbs (no `closecode git`).
  - Permissions: `OpenCodeConfig` adds `permission: edit/bash/webfetch = allow`
    and upgrades an existing config once (stamp `.closecode-permissions`), so
    the agent never stops to ask on the phone.
  - Self-test: checks 17–19 cover OpenCode permissions, the server log and the
    server health endpoint; the developer sheet now says nineteen checks.
  - Ask AI: build diagnostics and problems carry an Ask AI action that posts the
    failure to the running session (`POST /session/:id/message`) and opens the
    chat; clipboard fallback when the server is not running.
  - Server log: read-only viewer in the AI tab (Older/Current, Refresh, Share).
  - Reports: the crash dialog and Share now bundle both server logs via
    `DebugReport` (last 300 lines of each).
  - Projects: export/import as one zip (`ProjectZip`, `StorageProjectsPage`)
    with a `closecode-project.json` manifest and a zip-slip guard; wired under
    Settings → Projects.
  - CI: `.github/workflows/build.yml` runs the unit suite and builds/uploads the
    debug APK (attaches it to a release for `v*` tags).
  - `versionName` is now `0.43.0`. The AI settings page was removed: OpenCode
    runs everything by itself, and Settings shows a "Coming soon" stub.
- Unit suite: **1412 tests, 0 failures, 8 skipped**. Debug APK 40,061,987 B
  (`closecode-43-debug.apk`, sha256
  `5a15ba9bec1527a19113a71ddc25a9cc1c2e4c0e88c2fd143aa10b94c2c5fc25`),
  versionCode 43, v2-signed, native libs intact.
- **Still NOT done:** on-device run of build 43 (no device was attached to this
  workspace), and the build-42 device flows in §2. Self-test checks 17–19 now
  cover part of the OpenCode health check on the phone.
- **Build 42 (Step 20 — "AndroidIDE parity", from REA decompile of
  `com.m4coding.ide` 1.3.1):**
  - Fixed-port reclaim: before **every** launch (first + every retry), find the
    port-4098 holder via `/proc/net/tcp{,6}` LISTEN inodes → `/proc/<pid>/fd`
    `socket:[inode]` → same-UID + `opencode … web` cmdline gate (`killOrphanPid`)
    → kill. A foreign holder → fallback `4099..4118` as before, now logged.
  - Persistent server log: `OpenCodeServerLog` writes
    `files/opencode/logs/server.log` (previous run in `server.1.log`, rotated at
    512 KB via `Files.move` ATOMIC + copy fallback). `log()` mirrors there; the
    failure paths that appended straight to state now route through `log()`.
  - Also: `BuildReport` echoes the given path verbatim; Windows-only symlink
    tests guarded with `Assume.assumeNoException`.
- Unit suite: **1397 tests, 0 failures, 11 ignored** (3 legit Windows symlink
  skips). Debug APK 41,616,903 B built **and installed on MuMu** (versionCode 42).
- **Still NOT done:** on-device behavior verification of build 42 — the two test
  flows in §2. Blocked mid-way by MuMu input remapping (see §2.2), **not** by any
  app defect.

## 2. Resume: device verification of build 42 on MuMu

### 2.1 Environment cheat-sheet

- ADB: `C:\Users\HomePC\AppData\Local\Android\Sdk\platform-tools\adb.exe`,
  device `127.0.0.1:16384` (also shows `emulator-5554`). MuMu = Android 15,
  **x86_64**, `/system/bin/nc` available.
- Gradle: set `$env:JAVA_HOME="$env:LOCALAPPDATA\Jdks\jdk-17"` first; then
  `.\gradlew.bat :app:testDebugUnitTest` / `:app:assembleDebug`. Cold full suite
  ~13 min, warm ~2 min. `test-output*.txt` are local Gradle logs — never commit.
- App data: `adb -s 127.0.0.1:16384 shell run-as com.closecode …`
  (`files/`, `shared_prefs/`).
- OpenCode 1.18.35 archive is **pre-placed** at
  `files/opencode/opencode-linux-x64-musl-1.18.35.tar.gz` (63,034,319 B), so the
  app skips its download. Stale `files/opencode/server.pid` contains dead PID
  2544; port 4098 was free at last check.
- Emulator app-data residue from old testing (safe to leave or clean):
  `files/opencode/{home-copy,home-test,home-test2,server-test*.log,program.txt}`.
- Projects already on the emulator: `landing-page`, `paitestage`, and
  `269b1b8e-…` (see `restore_tabs_*` / `restore_active_*` keys in
  `shared_prefs/closecode_settings.xml`).
- Onboarding is **already bypassed**:
  `first_run_completed=true` lives in `shared_prefs/closecode_settings.xml`, so
  the app starts to the hero screen.

### 2.2 MuMu input traps (why the automation stalled — not app bugs)

1. **Taps in the lower ~70% of the screen (empirically y ≳ 1200) throw the
   emulator to the Lawnchair launcher** — MuMu's touch remapping eats the click.
   Verified by A/B taps: `(540,300)` keeps the app foreground; `(393,1308)` on
   "Start a project" → launcher. In-place `input swipe … 250` is hijacked too.
2. Top-right corner taps (≈`(963,127)` "Skip all") open the MuMu store search
   widget instead. If it takes the screen:
   `am force-stop com.mumu.store` + `am force-stop com.mumu.shared.sdk`, then
   `am start -n com.closecode/.MainActivity`.
3. The logcat `Fatal signal 11 … in tid … (Shutdown thread)` lines come from
   MuMu's own uiautomator process, not the app (verified: no `FATAL EXCEPTION`,
   no `Force finishing` for `com.closecode`).
4. **Recommended workaround:** drive the UI with keyboard events
   (`input keyevent KEYCODE_TAB` / DPAD arrows / `KEYCODE_ENTER`) and confirm
   progress via `focus="true"` nodes in `uiautomator dump` — or have the user
   tap manually on the emulator.

### 2.3 Test A — persistent server log

1. App (hero screen) → "Start a project" → sheet's **RECENT** row → open
   `landing-page` → IDE bottom bar → **AI** → "Open OpenCode".
2. Wait for Running (health probe, ≤120 s timeout).
3. Verify:
   `adb shell run-as com.closecode cat files/opencode/logs/server.log` shows the
   run header (`start project=… version=1.18.35`) and stamped lines.
4. Stop, start again → the first run's content must move to
   `files/opencode/logs/server.1.log`.

### 2.4 Test B — fixed-port reclaim (AndroidIDE-style)

Positive path (holder is the app's own stale server):

1. With the app stopped, spawn a decoy holder that passes the UID + cmdline
   gate, using the app's own runtime:
   `run-as com.closecode` executing the app's launch equivalent
   (`files/alpine/lib/ld-musl-x86_64.so.1 files/opencode web --hostname
   127.0.0.1 --port 4098`), background it, confirm LISTEN on hex `:1002` in
   `/proc/net/tcp`.
2. Also `am force-stop com.closecode`, then (optional) delete
   `files/opencode/server.pid` via run-as. Relaunch + start OpenCode.
3. Expect the AI-tab log lines (now also in `server.log`):
   `Removed a server left by the last run (PID …)` /
   `Removed a server still holding port 4098 (PID …)`, and the page URL stays
   `127.0.0.1:4098` — **not** 4099.

Negative path (foreign holder must not die):

- Start `/system/bin/nc -l 4098` as a shell uid listener, then start the app's
  server: it must **not** kill `nc`, must log
  `Port 4098 is busy; using 4099`, and `nc` must survive.

## 3. Reference material (AndroidIDE evidence, durable)

- Registry evidence: `C:\Users\HomePC\AppData\Local\Temp\opencode\androidide-assets\assets\acp_registry.json`
  (their pin = 1.18.31).
- Their `startOpenCodeWeb.sh` recipe: HOME/XDG setup → port cleanup (lsof/ss →
  kill -9 → re-verify) → `chmod +x` → `opencode web --hostname 127.0.0.1
  --port $PORT`. Their 567 MB asset pack is the Debian rootfs — **deliberately
  not copied** (proot container loses to native musl+Alpine on size and speed;
  recorded in this backlog's stop list).
- OpenCode pin rationale: `OpenCodeDefaults.VERSION = "1.18.35"` in
  `app/src/main/java/com/closecode/ai/OpenCodeModel.kt`; **1.19.4 crashes older
  WebViews** — never bump casually.

## 4. Improvement backlog (41 items)

Checkbox list; the suggested working order is §5.

### P0 — finish first

- [ ] 1. **Device-verify build 42** (§2.3 + §2.4). After green, the commit
       is "done done" and everything below is new steps.

### A. High-value, user-visible

- [ ] 2. **Log viewer in the AI tab** — open/share `files/opencode/logs/server.log`
       in-app; deep-link from the Failed card. Makes the Step 20 file a feature.
- [ ] 3. **Sessions drawer** — surface per-project sessions from
       `OpenCodeSessions`: list / resume / rename / delete; LauncherCard gets a
       "Continue <session>" chip + "New session" underneath.
- [ ] 4. **Provider status + one-tap auth screen** — configured providers from
       server config; Sign in button wraps `opencode auth login` (today it is
       terminal-only via `authenticateOpenCode()`).
- [ ] 5. **OpenCode update flow** — check GitHub releases for newer
       `opencode-linux-*-musl.tar.gz`; WebView-capability pre-check (warn, don't
       crash on old WebView); prune superseded binaries/archives after the new
       one passes the health probe.
- [ ] 6. **"Report a problem" bundler** — one share file: DebugLog crumbs +
       `server.log` + last `CrashReport`. Highest-leverage debugging feature for
       a solo maintainer.

### B. AI ↔ IDE bridge (ide polish)

- [ ] 7. **Ask AI from compile diagnostics** — problem sheet row → pre-filled
       prompt with file/line/diagnostic.
- [ ] 8. **Ask AI from terminal errors** — attach a traceback to the agent.
- [ ] 9. **Attach project files to chat** — project-recent-files picker sheet
       instead of the generic Android file input.
- [ ] 10. **Chat → code hand-off** — open the exact file the AI just cited.

### C. Data safety

- [ ] 11. **Project export/import via SAF** — projects live in private storage
       today; uninstall = data loss. Zip export + import; a Settings → Storage
       card doubles as a view of `files/` with residue cleanup (`home-test*`).
- [ ] 12. **Old-version cleanup after OpenCode updates** (shared with #5's prune
       half; keep whichever formulation lands).
- [ ] 13. **Crash dialog offers the report file** (ties into #6).

### D. Resilience

- [ ] 14. **Restart-storm protection** — 3 failed starts in 60 s → exponential
       backoff + "OpenCode keeps dying — see server.log" card.
- [ ] 15. **Cancel on the Starting card** — control channel already supports
       cancel; the 120 s start poll is unbounded from the UI side.
- [ ] 16. **Name the foreign port holder** — fallback note names the process
       (`com.foo`), not "Port 4098 is busy".

### E. Build & release

- [ ] 17. **GitHub Actions** — debug APK artifact per pushed step + ubuntu test
       matrix (keeps the 3 symlink tests exercised; Windows skips via Assume).
- [ ] 18. **Signed release + R8** — routine `assembleRelease` with the
       SigningStore key as the shippable artifact (debug APK is 41.6 MB).
- [ ] 19. **In-app app-update check** — compare `versionCode` against GitHub
       releases API; deep-link the APK so the phone update loop needs no PC.

### F. Runtime trust

- [ ] 20. **Pinned warning: `targetSdk 28` is load-bearing** (exec from private
       storage). Visible note in Settings so a future refactor doesn't raise it
       without re-validation.
- [ ] 21. **"Runtime details" card** — installed OpenCode version, Alpine pins,
       port in use, binary + log paths.

### G. UX polish

- [ ] 22. **"What's new" on upgrade + real `versionName`** (e.g. `0.42.0`, not
       `1.0`).
- [ ] 23. **Persist model pick per project** (if not already stored).
- [ ] 24. **Global project search** — file/line jump beyond the in-file find.
- [ ] 25. **Command palette** — Build / Run / Logcat / Ask AI / Save / Open.
- [ ] 26. **Build/AI state notifications** — progress + Cancel so off-screen
       builds don't die to doze (CloseCodeControl carries the commands).
- [ ] 27. **WebView version gate on first AI run** — too old → card with a Play
       Store link instead of a black page.
- [ ] 28. **Repair-the-build skill** — extend the shipped `closecode` skill with
       the five common failure modes (keystore, metadata, Compose runtime,
       toolchain download, Gradle wrapper).

### H. Quality

- [ ] 29. **TalkBack labels** — ui dumps show effectively no `content-desc`;
       label the controls.
- [ ] 30. **String resources** — move user-facing literals to `strings.xml` so
       localization is cheap later.
- [ ] 31. **Git panel add-ons** — branch switcher, stash, visible push/pull
       status badge.
- [ ] 32. **One full-flow run on a real arm64 phone** — everything this cycle
       ran on MuMu x86_64; one real-device run retires the biggest untested
       branch (musl loader, shell-tools repo, health probe on arm64).

### I. Last layer (appended 2026-10-30)

- [ ] 33. **Refresh `README.md` + add `LICENSE`/attribution.** The README is a
       one-line stub ("# Closecode"): give it what the app is, screenshots, the
       sideload-install drill, build instructions (JDK 17 + Gradle), and the
       feature story from HANDOFF. No LICENSE file exists at all — decide
       proprietary vs open, and add an attribution note: **not affiliated with**
       AndroidIDE or the OpenCode (sst/opencode) project, which provides the
       hosted web page.
- [ ] 34. **Backup rules for the big binaries.** Verify `android:allowBackup`
       in the manifest: 200 MB OpenCode binaries + the Alpine tree must never
       ride Android's cloud auto-backup (`dataExtractionRules` on 31+ excludes
       `files/opencode/opencode*` and `alpine/`; project sources and prefs stay
       included). Rule out backup timeouts, keep the user's actual work safe.
- [ ] 35. **SHA-256 pinning for every download.** OpenCode tarball edits, Alpine
       packages, and the shell-tools (git/python/node) repo downloads all come
       over the network today; pin expected hashes (and log the computed one in
       `server.log` on mismatch) so a bad mirror or MITM fails loudly.
- [ ] 36. **Skill version marker.** `OpenCodeSkill.install` writes once and
       never overwrites — good for user edits, but projects keep a stale skill
       forever after an app update improves it. Store the installed skill
       version in the project; on app-update mismatch offer a one-tap refresh.
- [ ] 37. **Throttle the health probe while backgrounded.** The one-second
       `/version` poll runs the whole time the app process lives; drop to
       10 s when the activity is backgrounded (a running-notification card from
       #26 keeps it alive) and restore 1 s on resume.
- [ ] 38. **Close out the build-41 pending device checks.** Still open from
       HANDOFF's list: 1.18.35 page/model picker in the WebView, proxy
       CONNECT/HTTPS traffic, `opencode auth login` with the exported CA.
       Fold into one device checklist along with the build-42 runs in §2.
- [ ] 39. **Launcher app shortcuts.** Long-press of the icon gets "Continue
       last project" / "New project" (static shortcuts, no server work) —
       removes the hero-screen round trip this session fought through.
- [ ] 40. **`.gitignore` hygiene.** `test-output*.txt` and the local
       `real opencode open pc/` folder show up as untracked noise after every
       checkout; ignore them explicitly so `git status` is clean and nobody
       accidental-commits them.
- [ ] 41. **Unit-test seam for the reclaim chain.** Step 20's `freePort()` →
       `clearStalePortHolders()` path is private and device-bound; inject the
       `/proc` rows (table text + fd dir) so the find-inode → match-PID → gate
       chain gets a real end-to-end unit test instead of parser-only coverage.

### Stop list (decisions to keep)

- **No analytics/telemetry.** The failure story is device-local logs + manual
  sharing.
- **No proot / container for OpenCode hosting.** REA-measured: 567 MB + ptrace
  tax for capabilities the pinned musl+Alpine stack never needs.
- **Don't rewrite OpenCode's web page in Compose.**
- **Don't raise `targetSdk` casually.**
- **Keep one-server-per-app**; surface the identity rule, don't add a queue.
- **No ACP native chat** unless a concrete gap appears (WebView page is the
  chosen chat surface; AndroidIDE-parity is otherwise complete).

## 5. Suggested order after verification

Step 21 = **#11 project export/import** (data safety first) → Step 22 =
**#7 + #8** AI-diagnostic hand-off → then **#2 + #3** (log viewer + sessions
drawer pair) → then the release chain (**#17 → #18 → #19**). Items #14–#16 slots
naturally into whichever step touches `OpenCode.kt` next.
