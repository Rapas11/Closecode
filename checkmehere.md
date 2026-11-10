# CloseCode — On-Device Agent Platform

## Phased implementation spec for the CloseCode AI developer

**Version:** 1.0
**Date:** 2026-11-09
**Target build:** CloseCode 0.65.1507 (versionCode 65)
**Scope:** Make CloseCode a phone-only agent workstation that can build any Android app and website, run Node/Python backends, and control apps on the device. No PC, no adb, no Android Studio, no external systems.

**How to use this document:** every item has an ID, the problem, the evidence, the change, the files, and the acceptance test. Ship P0 before anything else — P0 is what currently blocks the agent. P1–P4 can be parallelised once P0 is green.

---

## 1. Hard constraints (do not design around them)

1. **Phone only.** No PC, no adb, no Android Studio, no macOS/Xcode, no Docker daemon, no root assumption.
2. **Everything the agent needs must be reachable from the `closecode` CLI.** The agent cannot tap Settings. Any capability that exists only in the UI is invisible to the agent and therefore does not exist.
3. **`targetSdk 28` is load-bearing.** Android 10+ (`targetSdk 29+`) blocks executing binaries from the app's private storage. Full Gradle executes a downloaded JDK from app storage. If `targetSdk` is ever raised, all downloaded executables must move to `nativeLibraryDir` (or another exec-allowed location) or Full Gradle breaks. Document this in the code and in the release checklist.
4. **All host tools must be arm64 Android builds.** aapt2, zipalign, apksigner, aidl, NDK, CMake, clang, ffmpeg, sqlite3, node, python. An x86_64 binary is a dead end.
5. **No adb.** APK install goes through `PackageInstaller`. Never let Gradle's `installDebug` call adb.
6. **Long work must survive the app going to background.** Foreground service + wake lock + progress + resume for downloads and builds.
7. **Assume low RAM and thermal limits.** Default Gradle to `-Xmx2g`, `org.gradle.workers.max=2`, `org.gradle.parallel=false`, `org.gradle.vfs.watch=false`, `kotlin.daemon.jvmargs=-Xmx1g`.
8. **Never silently change a project.** Toolchain and environment fixes belong in CloseCode, not in per-project hacks. A project should build unmodified.

---

## 2. Verified current state (evidence, 2026-11-09)

| # | Fact | Evidence |
|---|---|---|
| 1 | The phone engine cannot build a modern Compose/Media3 project | `closecode build` → `blocked`, 24 findings |
| 2 | Full Gradle exists but is not installed | `closecode gradle assembleDebug --gradle` → `"missing": ["Gradle tools","Android 35 platform","build-tools 34.0.4","Native tools"]` |
| 3 | No CLI way to install Full Gradle | `closecode tool` only accepts `aapt2|d8|r8|kotlinc`; no `setup`/`toolchain` command |
| 4 | Installed APK contains Full Gradle code | `fullGradle`, `GradleToolchain`, `GradleRunner` present in installed `classes*.dex` |
| 5 | Public `main` does **not** contain Full Gradle | Only `restore/build-59-2fb35a4` and `redesign/screens-10-21` have `GradleRunner.kt` / `GradleToolchain` |
| 6 | Compose compiler is set but reported missing | `app/build.gradle.kts:36` has `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }`; checker says it is not set |
| 7 | `ndk { abiFilters }` is treated as native C/C++ | `describe` → `"native": {"used": true, "reason": "the ndk block"}`; project has zero native sources |
| 8 | Build-tools pinned to 34.0.4 | `PlatformStore` and the Gradle init script force `34.0.4`; AGP 8.12 requires **35.0.0** |
| 9 | Node cannot start | `node -v` → `CANNOT LINK EXECUTABLE "node": library "libz.so.1" not found`; works with `LD_LIBRARY_PATH=$PREFIX/lib` → `v26.13.0` |
| 10 | npm/npx missing | `command -v npm` / `npx` → not found |
| 11 | pip missing and broken bootstrap | `python3 -m pip` → no module; `ensurepip` → missing bundled wheel; `get-pip.py --user` works |
| 12 | Python 3.14 has poor wheel coverage | `androguard` install fails building `cryptography` |
| 13 | Accessibility is off | `closecode accessibility` → `{"enabled": false}`; `closecode ui dump` → `{"needsAccessibility": true}` |
| 14 | `closecode app` only knows the current project | Returns only `com.rapas.redit`, not arbitrary packages |
| 15 | Subcommand help is broken | `closecode gradle --help` and `closecode tool --help` return errors |
| 16 | Missing everyday tools | `ssh`, `scp`, `rsync`, `openssl`, `xz`, `ffmpeg`, `sqlite3`, `clang`, `make`, `cmake` |
| 17 | Network and disk are fine | `curl` reaches Google/Gradle; 367 GB free |
| 18 | 478 packages are installed on the device | `pm list packages | wc -l` |

**Consequence:** the agent can write code and run git/curl/python, but cannot build an Android APK, cannot run a Node project, cannot test a website, and cannot inspect or drive any app.

---

## 3. P0 — Foundation: unblock the agent

P0 is the only phase that is strictly ordered. Everything below is a blocker for real work.

### P0-1 — Fix the shell environment once, globally

**Problem:** tools are installed but not usable because the shell does not export the Termux prefix paths. `node` fails on `libz.so.1`; `java`/`gradle` will fail the same way after install.

**Change:** build the environment in one place (the shell/exec layer, not per command) and export it to every `closecode` subprocess:

```
PREFIX=/data/user/0/com.closecode/file/u
PATH=$PREFIX/bin:$HOME/.local/bin:$PREFIX/lib/jvm/java-17-openjdk/bin:$PATH
LD_LIBRARY_PATH=$PREFIX/lib:$LD_LIBRARY_PATH
JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk        # when installed
ANDROID_HOME=$FILES/sdk
ANDROID_SDK_ROOT=$FILES/sdk
GRADLE_USER_HOME=$FILES/gradle/home
TMPDIR=$FILES/tmp
SSL_CERT_FILE=$PREFIX/etc/tls/cert.pem
CURL_CA_BUNDLE=$PREFIX/etc/tls/cert.pem
```

Rules:
- Prepend, never replace, `LD_LIBRARY_PATH`.
- Only export `JAVA_HOME`/`ANDROID_HOME` when the path exists; otherwise export nothing so tools fail with a clear message instead of a confusing one.
- Do the same for the PTY/terminal and for `closecode exec`, so the user's interactive shell matches the agent's shell.

**Files:** the shell/exec layer used by `CloseCodeControl` and the terminal screen.

**Acceptance:**
```
node -v            # v26.x, no linker error
python3 -V         # works
echo $LD_LIBRARY_PATH   # contains $PREFIX/lib
```
and after Full Gradle install:
```
java -version      # openjdk 17
gradle -v          # Gradle 8.13
```

---

### P0-2 — Ship npm and npx

**Problem:** `node` exists but `npm`/`npx` do not. Without them no web project can install dependencies, which kills the "build any website" goal.

**Change:**
- Install the Termux `npm` package into the prefix (it depends on `nodejs`), **or** vendor the official npm tarball into the shell-tools pack and symlink `npm`/`npx` into `$PREFIX/bin`.
- Set `npm_config_prefix=$PREFIX` and `NODE_PATH=$PREFIX/lib/node_modules`.
- Add `corepack` so `pnpm`/`yarn` work from `packageManager` in `package.json`.
- Add the npm bin dir to `PATH` (already covered by `$PREFIX/bin`).

**Files:** shell-tools pack manifest + install step; shell env (P0-1).

**Acceptance:**
```
npm -v
npx -v
npm create vite@latest /tmp/probe -- --template react   # downloads, no linker error
```

---

### P0-3 — Ship pip, venv, and a wheel-friendly Python

**Problem:** `pip` is absent, `ensurepip` is broken (missing bundled wheel), and Python 3.14 has almost no prebuilt aarch64 wheels — `cryptography` already fails to build.

**Change:**
1. Ship `pip` in the shell-tools pack (run `get-pip.py` at pack-build time and include the wheel), or install the Termux `python-pip` package.
2. Make `python3 -m venv` work (the `venv` module must be present and `ensurepip` functional inside venvs).
3. **Ship Python 3.12 as the default interpreter**, not 3.14. 3.12 has broad aarch64 wheel coverage; 3.14 will keep failing on native packages. Keep 3.14 available behind `python3.14` if desired.
4. Add `uv` as an optional fast installer (`uv pip install`), since it resolves and installs much faster on a phone.

**Files:** shell-tools pack manifest; shell env (P0-1); Python package list.

**Acceptance:**
```
python3 -m pip --version
python3 -m venv /tmp/v && /tmp/v/bin/pip install requests
python3 -c "import requests; print(requests.__version__)"
```

---

### P0-4 — Add a CLI installer for Full Gradle (the single most important item)

**Problem:** `closecode gradle ... --gradle` reports missing tools and exits. The agent cannot tap Settings, and the app's private paths are not documented. There is no way to become unblocked from the CLI.

**Change:** add a first-class setup command that drives the downloaders that already exist (`ToolchainStore`, `ToolchainDownloadService`, `PlatformStore`, `gradle-tools.json`).

```
closecode setup --gradle [--sdk 35] [--build-tools 35.0.0] [--ndk] [--cmake] [--json] [--yes] [--wifi-only]
closecode setup --web
closecode setup --python
closecode setup --native
closecode setup --all
```

Behaviour:
- Download **JDK 17** (`gradle-tools.json` → Termux `openjdk-17` packages) and **Gradle** (`gradle-8.13-bin.zip`).
- Download **platform-35** (`platform-35_r02.zip`) and **build-tools 35.0.0** (see P0-5).
- Verify SHA-256/SHA-1 from the manifests before use; resume with HTTP Range against a `.part` file; unpack safely (reject `..` paths).
- Run in a foreground service with a wake lock; keep going when the app is backgrounded.
- Emit line-delimited JSON events so the agent can follow progress:
  ```
  {"event":"start","components":["jdk","gradle","platform-35","build-tools-35.0.0"]}
  {"event":"download","component":"gradle","received":52428800,"total":136983045,"bytesPerSec":812000}
  {"event":"verify","component":"gradle","ok":true}
  {"event":"unpack","component":"jdk"}
  {"event":"done","ready":true}
  {"event":"needsUser","reason":"license","url":"..."}   // only if genuinely required
  ```
- Idempotent: re-running skips verified components.
- After install, run self-tests: `java -version`, `gradle -v`, `aapt2 version`, and report failures as structured errors.
- Add `closecode setup --remove gradle|sdk|native|web|python` to reclaim space.

**Files:** `CloseCodeControl.kt` (command dispatch), `ToolchainDownloadService.kt`, `ToolchainStore.kt`, `PlatformStore.kt`, `assets/gradle-tools.json`, `assets/toolchain.json`, a new `SetupCommand.kt`.

**Acceptance:**
```
closecode setup --gradle --json     # completes, emits done
java -version                        # openjdk 17
gradle -v                            # Gradle 8.13
closecode gradle assembleDebug --gradle   # proceeds to compilation, no "missing" list
```

---

### P0-5 — Ship arm64 build-tools 35.0.0, and stop hardcoding 34.0.4

**Problem:** AGP 8.12 requires SDK Build Tools **≥ 35.0.0** (official compatibility table: Gradle 8.13, Build Tools 35.0.0, JDK 17). CloseCode installs 34.0.4 and its Gradle init script forces `android.buildToolsVersion = "34.0.4"`. Gradle will fail with *"Build Tools revision 34.0.4 is too low; minimum 35.0.0"*.

AndroidIDE (the only arm64 source CloseCode uses) publishes up to **34.0.4**, so 35.0.0 must be produced or shimmed.

**Change (pick one, in order of preference):**

1. **Build/port arm64 build-tools 35.0.0.** Publish it as a CloseCode release asset with a SHA-256, add it to `PlatformStore`. This is the correct long-term fix.
2. **Compatibility shim (fast unblock):**
   - Copy `build-tools/34.0.4` → `build-tools/35.0.0`.
   - Edit `source.properties`: `Pkg.Revision=35.0.0`.
   - Keep the arm64 `aapt2` override (`android.aapt2FromMavenOverride`) — AGP otherwise downloads an x86_64 aapt2 from Maven that cannot run on the phone.
   - Verify `zipalign`, `apksigner`, `aidl` from 34.0.4 still work for AGP 8.12 output.
   - Mark it clearly as a shim in the UI and in `toolchain status`.
3. **Parameterise build-tools.** Remove the hardcoded `34.0.4` from the init script. Map **AGP version → minimum build-tools** in the checker/installer:
   - AGP ≤ 8.1 → 33.0.x
   - AGP 8.2–8.9 → 34.0.0
   - AGP 8.10–8.13 → 35.0.0
   - AGP 9.x → 36.0.0
   If the project's `com.android.application`/`com.android.library` version requires a newer build-tools than installed, install it or report the exact remedy.

Also: the checker must read the AGP version from the project's version catalog / build files (it already has `GradleReader`) and request the matching build-tools, instead of always demanding 34.0.4.

**Files:** `PlatformStore.kt` (URL + checksum + version), `GradleRunner.kt` (init script, `android.buildToolsVersion`), `ProjectCheck.kt`/`GradleReader.kt` (AGP→build-tools mapping), `assets/toolchain.json`.

**Acceptance:**
```
ls $ANDROID_HOME/build-tools/35.0.0/{aapt2,zipalign,apksigner}
closecode gradle assembleDebug --gradle   # no "Build Tools revision is too low"
```

---

### P0-6 — Fix the Compose compiler false positive

**Problem:** the checker reports *"The Compose compiler version is not set"* although it is set in one line:

```kotlin
composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
```

The detector apparently requires the block and the property on separate lines, so a valid, common form is rejected. This is misleading and hides the real blocker.

**Change:** parse the block structurally (or with a regex that tolerates newlines/whitespace), accepting:
- `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }`
- multi-line form
- `kotlinCompilerExtensionVersion = "1.5.14"` inside `android { composeOptions { ... } }`
- version from a version catalog (`libs.versions.toml`) and `buildConfigField`-style indirection where resolvable

Also accept the modern Kotlin 2.x Compose plugin (`org.jetbrains.kotlin.plugin.compose`) as satisfying the requirement.

**Files:** `ProjectCheck.kt` / `GradleReader.kt` (Compose detection).

**Acceptance:** `closecode check` on this project no longer emits the Compose finding, and a deliberately missing `composeOptions` still produces it.

---

### P0-7 — Fix the native-code false positive

**Problem:** `ndk { abiFilters += "arm64-v8a" }` makes CloseCode report `"native": {"used": true, "reason": "the ndk block"}` and demand the ~1.5 GB NDK/CMake pack. This project has zero native sources. `abiFilters` is packaging config, not C/C++.

**Change:** flag native only when there is real native build input:
- `externalNativeBuild { ... }`
- `CMakeLists.txt`, `Android.mk`, `Application.mk`
- `*.c`, `*.cc`, `*.cpp`, `*.cxx`, `*.h`, `*.hpp` under `src/`
- `jni/` or `cpp/` source directories
- an `ndkVersion` pin **plus** one of the above

`ndk { abiFilters }`, `ndk { debugSymbolLevel }`, `packaging { jniLibs { ... } }` and prebuilt `.so` files alone must **not** require NDK/CMake. Prebuilt `.so` needs only packaging.

**Files:** `ProjectCheck.kt` / `GradleReader.kt` (native detection), `BuildSetup.kt` (`missing` list).

**Acceptance:** `closecode describe` reports `native.used: false` for this project and `missing` no longer contains `"Native tools"`. A project with a real `CMakeLists.txt` still reports native.

---

### P0-8 — Accessibility onboarding and a real enable path

**Problem:** `closecode ui dump` returns `{"needsAccessibility": true}` and `closecode accessibility` reports `enabled: false`. Every UI-control capability is dead until a human taps through Android settings. The agent cannot do that.

**Change:**
1. Add `closecode accessibility enable` that opens the exact Android accessibility-settings deep link for CloseCode (`Settings.ACTION_ACCESSIBILITY_SETTINGS`, ideally with the service highlighted). Return `{"ok":false,"needsUser":true,"url":"..."}` so the agent can ask the user once.
2. Add a first-run onboarding card: "Let the agent see and control apps" with a single button, plus a persistent status chip.
3. Expose the state everywhere: `closecode app`, `closecode ui`, `closecode status --json` (`"accessibility": true|false`).
4. Add a visible control banner while automation is active, with a **Stop** button.
5. Do **not** require accessibility for `closecode app` metadata (package, version, installed, foreground) — that can use `UsageStats`/`ActivityManager` where possible.

**Files:** accessibility service registration, `CloseCodeControl.kt` (`accessibility` command), onboarding screen, status payload.

**Acceptance:**
```
closecode accessibility enable     # returns needsUser with a deep link, then enabled:true after the tap
closecode ui dump                  # returns a JSON tree
closecode ui assert --text "..."   # works
```

---

### P0-9 — `closecode doctor` and `closecode toolchain status --json`

**Problem:** diagnostics say *what* is missing but not *where* it belongs, so the agent cannot diagnose or repair. The Gradle root/home paths are not documented anywhere the agent can read.

**Change:** add two commands.

```
closecode doctor [--json]
```
Checks and reports, each with `ok`, `path`, `version`, `remedy`, `installCommand`:
- shell env (`PATH`, `LD_LIBRARY_PATH`, `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME`, `TMPDIR`)
- JDK, Gradle, SDK platforms, build-tools, NDK, CMake
- node/npm, python/pip/venv, git, curl, zip, jq
- accessibility, notification permission, battery-optimisation exemption
- free disk (require ≥ 3 GB for Gradle + deps), network reachability
- installed project package, last build result

```
closecode toolchain status --json
```
Returns the exact layout:
```json
{
  "prefix": ".../file/u",
  "jdk": {"path":".../lib/jvm/java-17-openjdk","version":"17","ok":true},
  "gradle": {"home":"...","distribution":"8.13","ok":true},
  "gradleUserHome": "...",
  "sdkRoot": ".../files/sdk",
  "platforms": [{"api":35,"path":".../platforms/android-35","ok":true}],
  "buildTools": [{"version":"35.0.0","path":"...","shim":true,"ok":true}],
  "native": {"ndk":null,"cmake":null},
  "missing": [],
  "sizes": {"gradleCacheBytes":0,"sdkBytes":0}
}
```
This is also the contract the agent uses to populate files manually if a download is impossible.

**Files:** new `DoctorCommand.kt` / `ToolchainStatusCommand.kt`; reuse `PlatformStore`, `ToolchainStore`, `BuildSetup`.

**Acceptance:** `closecode doctor --json` returns every field above; `closecode toolchain status --json` matches the real on-disk layout.

---

### P0-10 — Make `--help` work for every subcommand

**Problem:** `closecode gradle --help` returns an error about having no phone equivalent; `closecode tool --help` returns `unknown tool '--help'`. Agents rely on `--help` for discovery.

**Change:** intercept `--help`/`-h` before argument validation for every subcommand and print usage. Add `closecode help <command>` as an alias. Keep the JSON contract for `--json` help if useful.

**Acceptance:**
```
closecode gradle --help      # usage, exit 0
closecode tool --help        # usage, exit 0
closecode help ui            # usage, exit 0
```

---

### P0 definition of done

Run this exact sequence on a fresh install, with no manual Settings taps:

```
closecode setup --gradle --json
closecode doctor --json
closecode build --gradle
closecode install
closecode run
closecode ui dump
```

`build --gradle` must reach compilation (not "tools missing"), `install`/`run` must find the Gradle APK output, and `ui dump` must return a tree.

---

## 4. P1 — Reliable build/run loop

### P1-1 — Check the toolchain before the project check
Today 24 engine findings print before the real blocker ("tools missing"). Order: toolchain readiness → project check → build. If tools are missing, return `needsSetup` with the exact `closecode setup ...` command and nothing else.

### P1-2 — Separate engine-only findings from project errors
Media3, MediaPipe, Vosk, Compose BOM, `vectorDrawables`, unsupported-library findings are **engine compatibility** notes, not project errors. Under `--gradle` they are irrelevant and must be suppressed (or shown as info). Keep them for the custom engine.

### P1-3 — Auto-select the engine
`closecode build` / `closecode run` should choose Gradle when the project needs it and Gradle is ready, and the custom engine otherwise. If Gradle is needed but not ready, return `needsSetup` with the install command. The agent should not have to guess `--gradle`.

### P1-4 — `install` / `run` must consume Gradle output
After a Gradle build, locate `app/build/outputs/apk/{debug,release}/*.apk` (and flavour paths), then install via `PackageInstaller`, then launch. Never call Gradle's `installDebug` (it needs adb).

### P1-5 — Structured Gradle diagnostics
Return `{file, line, column, severity, message, code}` for each diagnostic, plus `exitCode`, `durationMs`, and a path to the full log. A 64 KB text tail is not actionable. Parse Kotlin, Java, AAPT2, R8, and manifest merger errors.

### P1-6 — Cancel must kill the whole process tree
`closecode cancel` must terminate Gradle, the Kotlin daemon, aapt2, R8, and any child JVMs — not just the parent. Track the process group.

### P1-7 — Long builds survive
60–90 minute timeout, foreground service, wake lock, progress events, and resume where possible. First Gradle builds on a phone commonly take 10–30+ minutes.

### P1-8 — Memory/CPU defaults per device
Detect RAM and set `-Xmx` (1.5–3 GB), `org.gradle.workers.max=2`, `org.gradle.parallel=false`, `org.gradle.vfs.watch=false`, `kotlin.daemon.jvmargs=-Xmx1g`. Make them overridable in `.closecode.json`.

### P1-9 — Disk checks and cache management
Require ~3 GB free before a Gradle build; show SDK/Gradle-cache sizes; add `closecode clean --gradle-caches` and `closecode clean --sdk`.

### P1-10 — Network gate for dependency downloads
Wi-Fi-only setting, retry with backoff, proxy support, and a clear error when Maven/Google is unreachable — not just for the toolchain pack.

### P1-11 — Read `gradle-wrapper.properties`
Install the Gradle version the project asks for (wrapper first, then a compatible fallback), instead of always 8.13. Report a mismatch clearly.

### P1-12 — Release signing
`assembleRelease` currently produces an unsigned APK. Inject CloseCode's keystore via `signingConfigs` or post-sign with `apksigner`, and expose `closecode signing status|import|generate`.

---

## 5. P2 — Web, Python, and generic project adapters

This is what turns "Android build tool" into "build any app and website".

### P2-1 — Project type detection
Detect and report in `describe`/`status`: Gradle Android, npm/pnpm/yarn, static HTML, Python (requirements/pyproject), CMake, mixed. Store the choice in `.closecode.json` with an override.

### P2-2 — `closecode serve`
```
closecode serve [--port 3000] [--host 0.0.0.0] [--json]
closecode serve status|stop
```
Start a dev/static server as a managed background job, print the LAN URL and a local URL, stream logs, hot-reload where the ecosystem supports it. Works for Vite/Next/Astro/Flask/FastAPI/`python -m http.server`.

### P2-3 — `closecode preview`
Build a static site and open it in the in-app WebView (or Chrome), then screenshot and expose the DOM for assertions. This is the website equivalent of `closecode run`.

### P2-4 — Standard build result
Every adapter returns the same shape: `{ok, artifacts[], errors[], warnings[], exitCode, durationMs, logPath}`. The agent should never parse five different formats.

### P2-5 — Per-ecosystem commands
`closecode test`, `lint`, `format`, `package`, `release`, `clean` dispatch to the right tool (Gradle, npm, pytest/ruff, CMake).

### P2-6 — `closecode jobs`
List/stop/logs for background servers and builds: `{id, kind, command, status, pid, startedAt, logPath}`. Survives app backgrounding.

### P2-7 — `closecode init`
Templates: `android-compose`, `web-vite`, `web-static`, `python-fastapi`, `python-cli`, `node-express`. Each template must build on the phone with the installed toolchains, out of the box.

### P2-8 — Per-project pinning
`.closecode.json`: `{engine, sdk, buildTools, gradleVersion, nodeVersion, pythonVersion, env, ports}`. The agent can pin without touching build files.

---

## 6. P3 — Device control ("control my app and many other things")

Today only the current project's package is known and accessibility is off. Add a general, package-targeted API.

### P3-1 — `closecode apps`
```
closecode apps list [--json]
closecode apps launch <pkg> [--activity ...]
closecode apps stop <pkg>
closecode apps info <pkg>          # version, install time, permissions, foreground
closecode apps install <apk>
closecode apps uninstall <pkg>     # confirm
closecode apps clear <pkg>         # confirm
```

### P3-2 — General UI automation
```
closecode ui dump   --package <pkg>
closecode ui tap    --package <pkg> --text|--id|--desc [--index N]
closecode ui wait   --package <pkg> --text ... [--gone] [--timeout MS]
closecode ui assert --package <pkg> --text|--id|--desc
closecode ui scroll --package <pkg> down|up [selector]
closecode ui type   --package <pkg> --text ... 
```
Default `--package` to the current project when omitted, so existing scripts keep working.

### P3-3 — `closecode intent`
Start activities, open deep links, send broadcasts, share files. Return the resolved component and result.

### P3-4 — `closecode notifications`
`list`, `dismiss`, `reply` (via `NotificationListenerService` + `RemoteInput`), with per-app consent.

### P3-5 — `closecode files`
Read/write/list shared storage through SAF or `MediaStore`, with a scoped root and explicit user-granted URIs. Never blanket access.

### P3-6 — `closecode screen`
Screenshot and screen recording via `MediaProjection`, with a foreground-service notification. `closecode screenshot` already exists — extend it and make it work for any app.

### P3-7 — `closecode device`
Battery, charging, network type/IP, storage, display, orientation, sensors, Do Not Disturb, volume, brightness, clipboard (read/write with consent).

### P3-8 — `closecode exec` / PTY
Arbitrary command with streaming stdout/stderr, env, cwd, timeout, and an approval gate for destructive patterns. This is the escape hatch that makes the platform complete.

### P3-9 — Consent, audit, and redaction (non-negotiable)
- Per-capability grants: build, install, ui-automation, screen, files, network, shell, secrets.
- Per-app consent for UI control; default-deny for banking/password-manager/authenticator packages (configurable list).
- Redact password/OTP fields from `ui dump` and screenshots on request.
- Append-only audit log: `{ts, action, package, args, result}`; `closecode audit tail`.
- Confirmation required for uninstall, clear-data, delete, deploy, publish.
- **Secrets vault:** GitHub token, signing keys, deploy tokens; used by commands, never printed to the agent.
- Egress log so nothing leaves silently.

### P3-10 — Automation safety rails
Timeouts, rate limits, loop detection, and a global `closecode stop-all` that cancels jobs and disables automation.

---

## 7. P4 — Deployment, extra toolchains, and testing

### P4-1 — Deployment
`ssh`, `scp`, `rsync`, `openssl`, `xz` in the shell pack. `closecode deploy` presets for static sites and Node/Python servers, with the secret vault supplying credentials. `closecode github push|pull` already exists — keep it.

### P4-2 — Media and data
`ffmpeg`, `sqlite3`, `imagemagick` (optional). These unblock Redit-style projects and most backend work.

### P4-3 — Native
`clang`, `make`, `cmake`, arm64 NDK. Gate behind `closecode setup --native`. Prebuilt `.so` projects must not need it (P0-7).

### P4-4 — Optional languages, on demand
Go, Rust, Dart/Flutter, PHP, Ruby, Deno, Bun. Each is a separate `closecode setup --<lang>` pack so the base install stays small. Do not ship them by default.

### P4-5 — Browser testing
A WebView/CDP harness: navigate, evaluate JS, read the DOM, click, screenshot, assert. `closecode preview --test` runs a script against the page.

### P4-6 — Multi-app UI scripts
A small step language or JSON script: launch A → wait → tap → switch to B → assert. Run with `closecode test-ui <file>`.

### P4-7 — Self-test projects
Ship `closecode selftest` that builds and runs:
- `android-compose` with AGP 8.12 + Compose BOM 2024.09.03 + Media3 + Vosk (the exact combination that broke here)
- `web-vite`
- `python-fastapi`
- a native CMake project
- a prebuilt-`.so` project (must not require NDK)

Run it on an arm64 API 35 phone in CI/release checks. This catches every regression above.

---

## 8. Cross-cutting requirements

1. **One JSON contract for every command.** `{ok, ...}` on success, `{ok:false, error, code, remedy, installCommand}` on failure. Never a bare stack trace.
2. **Streaming for long work.** Builds, downloads, servers, and `exec` emit line-delimited JSON.
3. **Idempotent installs.** Every `setup`/`install` can be re-run safely.
4. **Checksums everywhere.** No downloaded binary is executed before its hash matches.
5. **Safe unpack.** Reject absolute paths and `..` in zip/tar/xz.
6. **No hidden network.** A single egress log and a Wi-Fi-only switch.
7. **Docs generated from code.** The `closecode` skill/AGENTS text must list `closecode setup --gradle`, `closecode doctor`, `closecode serve`, and the device-control commands, so every agent discovers them.
8. **Update the public `main` branch.** The installed build contains Full Gradle; `main` does not. Merge `restore/build-59-2fb35a4` (or the current Full Gradle work) into `main` so the source matches the shipped APK.

---

## 9. What a phone genuinely cannot do (do not overpromise)

- **iOS apps** — need macOS/Xcode. Impossible.
- **Windows/macOS desktop apps** — no native build/run; cross-compile only.
- **Docker/containers** — not realistic without root/kernel support.
- **x86 emulators** — no hardware virtualisation on most phones; unusably slow.
- **Android Studio / Xcode** — will not run.
- **Large ML training / huge monorepos** — RAM, thermal, and storage limits.
- **Some system settings and app data** — require root or adb.
- **Play Store automation** — possible via API but policy-sensitive.

Everything else in this document is feasible on the phone.

---

## 10. End-to-end acceptance (the whole platform)

After P0–P2:

```
closecode setup --gradle --web --python
closecode doctor --json                 # all ok
closecode build --gradle                # Redit compiles
closecode install && closecode run
closecode ui assert --text "..."        # app is controllable

closecode init web-vite /tmp/site
closecode serve /tmp/site --port 3000   # prints LAN URL
closecode preview /tmp/site             # screenshot + DOM

closecode init python-fastapi /tmp/api
closecode run /tmp/api                  # server starts
```

After P3:

```
closecode apps list
closecode ui dump --package <any-app>
closecode ui tap --package <any-app> --text "..."
closecode audit tail
```

If all of that passes, the agent can build any Android app, any website, run a backend, and control apps on the phone — with no PC.

---

## 11. Appendix — exact values and paths

**Toolchain manifests**
- `assets/gradle-tools.json`:
  - repo `https://packages.termux.dev/apt/termux-main`, root `openjdk-17`
  - required `lib/jvm/java-17-openjdk/bin/java`
  - Gradle `8.13`, `gradle-8.13-bin.zip`, 136,983,045 bytes,
    SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`
- `assets/toolchain.json`: Kotlin `1.9.24`, Compose compiler `1.5.14`, compileSdk `35`, aapt2 `34.0.4` (arm64, AndroidIDE).

**SDK**
- Root: `<filesDir>/sdk`
- Platform 35: `https://dl.google.com/android/repository/platform-35_r02.zip` → `<sdk>/platforms/android-35`
- Build-tools 34.0.4 (current): `https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/build-tools-34.0.4-aarch64.tar.xz` → `<sdk>/build-tools/34.0.4`
- Build-tools 35.0.0: must be produced or shimmed (P0-5).

**JDK / Gradle**
- JDK: `$PREFIX/lib/jvm/java-17-openjdk` (Termux `openjdk-17` package)
- Gradle: unpack `gradle-8.13-bin.zip`; expose `gradleHome`, `gradleRoot`, `GRADLE_USER_HOME` via `closecode toolchain status --json` (P0-9). Do not guess these paths in docs — publish them.

**AGP compatibility (official)**
| AGP | Gradle | Build Tools | JDK |
|---|---|---|---|
| 8.12 | 8.13 | 35.0.0 | 17 |
| 9.5 | 9.6.0 | 36.0.0 | 17 |

**Code locations to change**
- `app/src/main/java/com/closecode/control/CloseCodeControl.kt` — command dispatch (`build`, `gradle`, `tool`, `ui`, `app`, `accessibility`, new `setup`, `doctor`, `toolchain`, `serve`, `apps`, `intent`, `jobs`)
- `app/src/main/java/com/closecode/build/ToolchainStore.kt` — downloads, hashing, `.part` resume
- `app/src/main/java/com/closecode/build/ToolchainDownloadService.kt` — foreground download service
- `app/src/main/java/com/closecode/build/PlatformStore.kt` — SDK platform/build-tools URLs, checksums, paths
- `app/src/main/java/com/closecode/build/GradleRunner.kt` — `GradleToolchain`, `GradlePlan`, init script, aapt2 override, build-tools pinning
- `app/src/main/java/com/closecode/build/ProjectCheck.kt` / `GradleReader.kt` — findings, Compose detection, native detection, AGP→build-tools mapping
- `app/src/main/java/com/closecode/build/BuildSetup.kt` — `missing` list and readiness
- `app/src/main/java/com/closecode/build/BuildEngine.kt` / `BuildService.kt` — build state, cancel, logs
- `app/src/main/java/com/closecode/ui/settings/ToolchainSetupScreen.kt` — mirror every CLI action in the UI
- `assets/gradle-tools.json`, `assets/toolchain.json` — manifests

**Branch note:** Full Gradle exists in the installed APK but not on `main`; it is present on `restore/build-59-2fb35a4` and `redesign/screens-10-21`. Merge it into `main` before starting P0-4.

---

## 12. Suggested execution order

1. **P0-4 + P0-5** (installer + build-tools 35) — unblocks Android builds.
2. **P0-1 + P0-2 + P0-3** (shell env, npm, pip) — unblocks web and Python.
3. **P0-6 + P0-7 + P0-10** (false positives, help) — makes diagnostics trustworthy.
4. **P0-8 + P0-9** (accessibility, doctor/status) — makes the agent self-sufficient.
5. **P1** — reliable build/run loop.
6. **P2** — web/Python/adapters (`serve`, `preview`, `jobs`, `init`).
7. **P3** — device control + consent/audit.
8. **P4** — deployment, extra toolchains, browser/UI testing, self-tests.

P0 is days of work; P1–P2 is what makes it feel like a workstation; P3 is what makes "control anything" real.
