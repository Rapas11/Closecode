# CloseCode Build 78 — Re-verification

**Build tested:** 78
**Project:** `com.rapas.redit` (AGP 8.12.0, Compose BOM 2024.09.03, Media3 + MediaPipe
+ Vosk, arm64)
**Method:** my local workarounds were **removed before testing** — the
`$GRADLE_USER_HOME/gradle.properties` AAPT2 line was deleted, and the wrapper was
deleted so the duplicate-download fix could be observed. Nothing was restored until
after the measurements.

---

## 1. Fixed in build 78

### P0-10 — `gradlew` now starts on Android ✅

The generated wrapper is no longer the stock `#!/usr/bin/env sh` script. It is a
shim:

```sh
#!/system/bin/sh
if [ -n "$CLOSECODE_GRADLE" ] && [ -x "$CLOSECODE_GRADLE" ]; then
  exec "$CLOSECODE_GRADLE" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Open Settings > Build and Toolchain > Full Gradle." >&2
exit 127
```

```
$ ./gradlew --version
Gradle 8.13
EXIT=0
```

### P0-12 — no duplicate Gradle download ✅

Deleted `$GRADLE_USER_HOME/wrapper` (276 MB) and ran the wrapper again:

```
$ du -sh files/gradle/home/wrapper
du: .../wrapper: No such file or directory
```

Nothing was re-downloaded. The wrapper now delegates to the installed distribution.

### P0-14 — `local.properties` is generated ✅

```
$ cat local.properties
sdk.dir=/data/user/0/com.closecode/files/sdk
```

### P1-8 — `check` follows the project's engine ✅

```
$ closecode check; echo $?
{"ok":true,"task":"check","engine":"gradle","errors":0,"warnings":1,...}
EXIT=0
```

The 21 false "not supported yet" errors are gone. `engine` is reported as `gradle`.

---

## 2. Still broken

### P0-11 — the Gradle menu path still gets no AAPT2 override ❌

**Important distinction — two different build paths exist in the app:**

| Path | Command | Build 78 |
|---|---|---|
| **Run / Build dialog** (the green ▶ button) | `closecode build` pipeline | ✅ **works** — user-built at 05:40, `BUILD SUCCESSFUL in 1m 5s` |
| **Gradle menu → `gradlew assembleDebug`** | `./gradlew assembleDebug` | ❌ fails without the workaround |

So the app's main Build button now works without the agent. The Gradle submenu still
does not.

With the workaround removed, the Gradle menu command fails:

```
$ ./gradlew assembleDebug
AAPT2 aapt2-8.12.0-13700139-linux Daemon #0: Unexpected error output:
  .../transformed/aapt2-8.12.0-13700139-linux/aapt2[64]: syntax error: unexpected ')'
AAPT2 aapt2-8.12.0-13700139-linux Daemon #1: Daemon startup failed
BUILD FAILED in 1m 28s
```

`closecode build` on the same project, same moment:

```
BUILD SUCCESSFUL in 97 s
```

So the difference is only the override flag. The init script CloseCode writes is
still at `$GRADLE_USER_HOME/init/closecode.gradle`, which Gradle loads **only** with
`-I`; the wrapper never passes it. It now sets `buildToolsVersion` and `ndkVersion`
but still **not** `android.aapt2FromMavenOverride`:

```groovy
allprojects {
    plugins.withId("com.android.application") {
        android.buildToolsVersion = "35.0.1"
        android.ndkVersion = "27.1.12297006"     // still no aapt2 override
    }
}
```

**Proven sufficient:** with only
`android.aapt2FromMavenOverride=/…/build-tools/35.0.1/aapt2` present in
`$GRADLE_USER_HOME/gradle.properties`, the same command succeeds:

```
$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 22s
```

That one line is the whole remaining gap between "works with the agent" and
"works without it".

### P0-9 — `--remove gradle` still deletes the cache ❌

```
$ closecode toolchain status
"gradleUserHome":"/data/user/0/com.closecode/files/gradle/home"
```

`GRADLE_USER_HOME` is still inside the directory that `setup --remove gradle`
deletes. Not re-tested destructively this round, but the layout is unchanged, so the
1.6 GB loss is still possible.

### P1-1 — `python3 -m venv` ❌

```
$ python3 -m venv .venv
EXIT=1
```

Unchanged.

---

## 3. Not verifiable from here

| Item | Why |
|---|---|
| **P0-13** editor JDK | Needs the JDK dialog on screen. Accessibility is off, so `ui dump` returns `needsAccessibility:true`. No JDK setting is exposed in any file I can read. |
| **P0-8** download resume | Would require interrupting a large download again. The previous run destroyed 37 MB of progress; I am not repeating that on this connection without a reason. |

---

## 4. No regressions

| Check | Result |
|---|---|
| `closecode setup --gradle --json` | `ready:true`; all four self-tests pass |
| `setup --remove platform-tools` → `setup --gradle` | removed, then re-downloaded and verified (`adb` 1.0.41) |
| `closecode build` | `BUILD SUCCESSFUL in 97 s`, exit 0 |
| APK | 77,145,483 bytes |
| Signature | `CN=Closecode Debug`, `30fae35d64645323304340c4f570a58377de8153477a5e334dc62c036a87f979` |
| `closecode install` | `signature_mismatch` with both SHA-256 values and the remedy |
| `closecode check` | `ok:true`, exit 0 |

P0-1 … P0-7 all still pass.

---

## 5. Current local workaround

One line, restored so the editor is usable while P0-11 is open:

```
$GRADLE_USER_HOME/gradle.properties
android.aapt2FromMavenOverride=/data/user/0/com.closecode/files/sdk/build-tools/35.0.1/aapt2
```

Remove it once CloseCode writes the override itself.




---

## What I want

CloseCode must be a complete phone-only workstation that works **without the AI**.

A person opens the app, taps through Settings, everything downloads, and a project
builds. The AI is only an option on top of that. It is never the thing that repairs
the setup.

I test the installed app on my phone with my own project (`com.rapas.redit`,
AGP 8.12.0, Kotlin 1.9.24, Compose BOM 2024.09.03, Media3 + MediaPipe + Vosk,
minSdk 26, targetSdk 35, arm64).

---

## Confirmed working — do not break it

Checked on build 78, with my local workarounds removed first.

| Confirmed | Behaviour |
|---|---|
| Gradle launcher | fresh install runs: `gradle -v` → `Welcome to Gradle 8.13!` |
| `./gradlew` | **starts correctly** via the shim; `./gradlew --version` exits 0 |
| No duplicate Gradle | wrapper cache deleted → not re-downloaded |
| `local.properties` | **generated** with `sdk.dir` |
| AAPT2 | `closecode build` passes the arm64 `.../build-tools/35.0.1/aapt2` |
| Platform-tools | `setup --gradle` downloads and installs it; `adb` 1.0.41 |
| SDK licenses | `setup --gradle` recreates all four files |
| Failed build | exits **1**, not 0 |
| Error reporting | one root-cause line + structured diagnostics + log path |
| Signing | APK and `signing status` share one key: `CN=Closecode Debug` |
| `--json` | parsed as a flag, not as a package name |
| Toolchain status | `ok:true` when only optional NDK/CMake are missing |
| `closecode check` | **follows the project's engine**: `engine:"gradle"`, `ok:true`, exit 0 |
| Install | clear `signature_mismatch` with both SHA-256 values and the remedy |
| Consent | `consent request` asks the user; nothing is granted automatically |

**End-to-end:** `closecode setup --gradle` → `closecode build` →
`BUILD SUCCESSFUL in 97 s`, APK signed `CN=Closecode Debug`. No `curl`, no `tar`,
no `chmod`, no hand-written license file.

---

## What is still broken — fix in this order

### 1. The editor's build still gets no AAPT2 override — the last blocker

`closecode build` works. The app's own Build button does not:

```
$ ./gradlew assembleDebug
AAPT2 aapt2-8.12.0-13700139-linux Daemon #0: Unexpected error output:
  .../aapt2-8.12.0-13700139-linux/aapt2[64]: syntax error: unexpected ')'
AAPT2 aapt2-8.12.0-13700139-linux Daemon #1: Daemon startup failed
BUILD FAILED in 1m 28s
```

AGP downloads the **x86-64** aapt2 from Maven and tries to run it on the ARM phone.

CloseCode writes its init script to `$GRADLE_USER_HOME/init/closecode.gradle`. Gradle
loads that folder **only** when it is passed with `-I`, which `closecode build` does
and the wrapper does not. The script also sets `buildToolsVersion` and `ndkVersion`
but **not** `android.aapt2FromMavenOverride`.

**Proven fix:** with only
`android.aapt2FromMavenOverride=/…/build-tools/35.0.1/aapt2` present in
`$GRADLE_USER_HOME/gradle.properties`, the identical command gives
`BUILD SUCCESSFUL in 22s`.

*Why it matters:* this is the difference between "CloseCode works when the AI runs it"
and "CloseCode works". Everything else on this list is secondary.

*Done when:* `./gradlew assembleDebug` succeeds with no hand-written property file.

### 2. Removing Gradle still deletes the build cache

`GRADLE_USER_HOME` is `files/gradle/home`, inside the folder `setup --remove gradle`
deletes. Removing the distribution therefore destroys the dependency cache — on this
project the next build went from **107 s to 22 min 42 s**.

*Done when:* removing Gradle leaves the cache intact, with a separate action to clear it.

### 3. A failed download still loses its progress

In build 77 `setup --gradle` stopped at 37,597,769 of 136,983,045 bytes with
`{"error":"timeout"}` and left no partial file, so the retry started from zero. I have
not repeated the test, because it destroys 37 MB of progress on a slow connection —
but nothing I can see indicates it changed.

*Done when:* a killed download resumes from where it stopped, and a failed install
leaves the previous working version in place.

### 4. The editor's JDK is still unverified

The dialog showed `Current JDK: Default` with `/opt/java/jdk1.8.0_212` (a path that
does not exist) while JDK 21 was selected. My project needs JDK 17. I could not check
this round because accessibility is off and I cannot see the dialog.

*Done when:* the dialog shows the real JDK 17 path and version, and the build uses it.

### 5. `python3 -m venv` still fails

```
$ python3 -m venv .venv
EXIT=1
```

`ensurepip` bundled wheels are missing. `uv` and global `pip 26.3` work.

*Done when:* `python3 -m venv .venv && .venv/bin/pip --version` exits 0.

### 6. Settings still does not list the Full Gradle or native components

This is the screen a user actually sees. **Settings → Build and Toolchain** contains:

| Shown | Not shown |
|---|---|
| Build tools (Kotlin 1.9.24, 77.4 MB) — the **phone engine** | JDK 17 |
| Build cache (toggle) | Gradle 8.13 |
| Shell tools — Installed | Android platform 35 |
| Media tools → Optional packs | Build-tools 35.0.1 |
| Language packs → Optional packs | Platform-tools |
| OpenCode runtime — Installed | SDK licenses |
| Default build type, Download over | **NDK 27.1** (installed, 1.8 GB) |
| | **CMake 3.22.1** (installed, 73 MB) |

The "Optional packs" screen proves the plumbing already exists — it says it uses
*"the same downloader, checksums and Remove action as the CLI: closecode setup --media,
--go, --rust"*. But the components that `closecode setup --gradle` and
`closecode setup --native` install are not wired into any screen.

Consequence today: NDK and CMake are installed on the phone (1.9 GB) and **cannot be
seen or removed from the UI at all**.

*Done when:* Settings → Build and Toolchain lists every component from Rule 7, each
with version, size, Install / Remove, using the same cards as Optional packs.

---

## Rules for every fix

1. **One installer, two front ends.** Everything in Settings must also be reachable
   from `closecode setup`, and the reverse.
2. **The app installs, the user consents.** The AI must never need `curl`, `tar`,
   `chmod`, or a hand-written license file. If it does, the app is incomplete.
3. **Do not restrict the AI.** It may build, run, fix source, drive apps and use
   `closecode exec`. Only installation and consent stay with the app and me.
4. **Never destroy a working install** to install a new one.
5. **One signing key** for every engine and every build path.
6. **A failed build exits non-zero** with one root cause, diagnostics and a log path.
7. **Settings must list every component the app can install**, with version, download
   size, Install / Remove and the same progress display as the CLI:

   | Component | For |
   |---|---|
   | JDK 17 | Gradle, Kotlin, aapt2 |
   | Gradle | every Gradle build |
   | Android platform 34 / 35 / 36 | `compileSdk` |
   | Build-tools 35.0.1 | aapt2, zipalign, apksigner, aidl |
   | Platform-tools | AGP SDK validation, `adb` |
   | SDK licenses | required before any SDK package installs |
   | NDK 27.1.12297006 | native C/C++ |
   | CMake 3.22.1 | native C/C++ |
   | Media pack | ffmpeg, sqlite3, imagemagick |
   | Web / Python / Go / Rust / Dart / PHP / Ruby / Deno / Bun | those project types |

   If a component can be installed by `closecode setup`, it must be visible and
   removable in Settings. Nothing may exist only in the CLI.

8. **Reject unknown flags.** `closecode setup --native --help` starts a 564 MB NDK
   download instead of printing help. Unknown or malformed arguments must print usage
   and exit with no side effects.

---

## Definition of done

With the AI switched off, on a fresh phone:

1. Settings installs JDK 17, Gradle, platform 35, build-tools, platform-tools,
   licenses, NDK and CMake — each with its size and a Remove button.
2. A new Compose project builds from the editor's own Build button.
3. The same project builds with `closecode build`.
4. Both APKs have the same signature.
5. No AAPT2, license, platform-tools or launcher error appears.
6. Nothing is downloaded twice, and a failed download resumes.
7. Removing a component does not delete unrelated caches.
8. A failed build tells me the real cause in one line.

When those eight are true, the app works without the AI — which is the whole point.
