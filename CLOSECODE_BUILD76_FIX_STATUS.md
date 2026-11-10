# CloseCode Build 76 — Fix status (P0-1…P0-7, P1-1…P1-7)

Branch: `main` · APK: `app/build/outputs/apk/debug/app-debug.apk`

The specification is `CLOSECODE_BUILD76_FIX_REPORT.md`. This file records what was changed, how
it was verified, and what could not be verified on the machine that made the change (a Windows
host with no arm64 phone attached).

## P0

| ID | Change | Files |
|----|--------|-------|
| P0-1 | The Gradle build no longer runs `bin/gradle`. `GradlePlan.launcherJar` finds `gradle-launcher-<version>.jar` in the distribution and the build runs `java -classpath <jar> org.gradle.launcher.GradleMain …` directly, so no shell, no xargs and no `"-Xmx64m"` main class. `bin/gradle` is also patched at unpack and on every setup attempt by `GradleLauncher.patchText`, which flattens the quoted `DEFAULT_JVM_OPTS` (keeping `$APP_HOME` expandable) so toybox xargs cannot keep a quote. | `build/GradleRunner.kt`, `build/GradleLauncher.kt` (new), `tools/GradleTools.kt` |
| P0-2 | The AAPT2 override always ends in the file name `aapt2`. `selectAapt2` prefers the downloaded build-tools `aapt2` and otherwise copies the bundled `libaapt2.so` to `<filesDir>/toolchain/aapt2`. | `build/BuildSetup.kt` |
| P0-3 | `closecode setup --gradle` installs platform-tools 34.0.4 (arm64, 4 354 052 bytes, SHA-256 `08b5b5c9…236`) with a resumable `.part` download and a checksum check, and verifies `adb version` in the self-test. `BuildSetup` refuses to build while platform-tools is missing. Settings → SDK Manager has the same row with Install and Remove. | `build/PlatformStore.kt`, `control/SetupCommand.kt`, `build/BuildSetup.kt`, `ui/settings/SettingsPages.kt` |
| P0-4 | Accepting the licence now writes the four SDK licence files with the exact hashes AGP checks (`android-sdk-license`, preview, arm-dbt, googletv). They are re-asserted after every install and can be removed. | `build/PlatformStore.kt`, `control/SetupCommand.kt`, `ui/settings/SettingsPages.kt` |
| P0-5 | Every command that can fail now ends with `__CLOSECODE_EXIT__<code>__`; the shell wrapper turns it into the process exit code. `build`, `gradle`, `install`, `run`, `test`, `lint`, `package`, `release`, `setup` and `check` report `ok:false` and exit non-zero. | `control/CloseCodeControl.kt` |
| P0-6 | A failed build reports one root-cause line (`* What went wrong:` → deepest cause, else the last `Caused by:`), the structured diagnostics array and the log path, both in the done event and in `result`. | `build/GradleRunner.kt`, `build/BuildReport.kt`, `build/BuildEngine.kt` |
| P0-7 | One debug key for both engines: every Gradle artifact (debug and release) is post-signed with the selected CloseCode key, so the custom engine and Full Gradle produce the same certificate. `closecode install` compares the installed certificate with the APK's before it opens a session and returns a structured `signature_mismatch` with the remedy and the two commands to fix it. | `build/BuildEngine.kt`, `build/ApkPostSigner.kt`, `device/DeviceTools.kt` |

## P1

| ID | Change | Files |
|----|--------|-------|
| P1-1 | The installer puts a pinned, checksummed pip wheel (pip 25.2) into the pack's empty `ensurepip/_bundled`, so `python3 -m venv` works. `setup --python` self-tests `python3 -m venv DIR && DIR/bin/pip -V`. | `tools/PythonPackRepair.kt` (new), `tools/ShellToolsStore.kt`, `control/SetupCommand.kt` |
| P1-2 | Flags are parsed before positionals: `config show --json` reads the settings, `app --json` is not a package name, unknown options are rejected with `unknown_option`. | `control/ConfigCommand.kt`, `control/CloseCodeControl.kt` |
| P1-3 | `toolchain status` reports `ok` from the required parts only; NDK, CMake and the shell pack moved to `optionalMissing`. | `control/DiagnosticsCommands.kt` |
| P1-4 | The CLI wrapper streams the answer line by line (FIFO) instead of buffering the whole run, so `setup --gradle` shows progress while it downloads. | `control/CloseCodeControl.kt` |
| P1-5 | The setup self-test now requires exit code 0, non-empty output, no error text and a per-tool version pattern, and reports `exitCode` and `error`. | `control/SetupCommand.kt` |
| P1-6 | `closecode accessibility` answers with a structured `needs_accessibility` result (permission, url, remedy) and `accessibility enable` opens the exact service screen. | `control/CloseCodeControl.kt` |
| P1-7 | `closecode install` returns `code: needs_permission` with `permission`, `url`, `package`, `remedy` and `actions`; `closecode install --open-settings` opens the unknown-apps screen and retries automatically once the grant arrives. | `device/DeviceTools.kt`, `control/CloseCodeControl.kt` |

## Follow-ups found while checking against §9 and §10

- `closecode doctor --json` now checks the two new required packages (`sdk.platform_tools`,
  `sdk.licenses`) and its `ok` is decided by the required checks only; everything optional
  (shell pack, NDK, CMake, accessibility, notifications, battery, disk, network) moved to
  `optionalChecks`.
- The root-cause line now names the compiler error itself
  (`file:///…/Main.kt:9:5: Unresolved reference: nope`) instead of the generic
  "Compilation error. See log for more details".
- §10 asked for the local workarounds to be removed once the real fixes landed: the installer
  now deletes the stale `GRADLE_USER_HOME/init.d/zz-closecode-*.gradle` script, and the Gradle
  launcher is patched properly at unpack time instead of by hand.

## Still open (not part of P0/P1)

The report contains more than P0 and P1. §12 lists two more items that are **not** done:

- **P2-1 — consent can be self-granted.** `closecode consent grant shell` still flips the grant
  itself. The report wants `{"ok":false,"code":"needs_confent","needsUser":true}`, a user
  confirmation in the UI, no self-grant for secrets / ui-automation / screen / shell, an audit
  entry for granted *and* denied attempts, and `closecode consent request <capability>`.
- **P3 — test gaps.** The commands in the P3 table (`selftest --build`, `init --template
  android-compose`, `test/lint/format/package/release`, `test-ui`, `secrets`, `deploy --dry-run`,
  `screen`, `notifications`, `files`, `signing`) are not covered end to end.

The device acceptance run (§9) is also still outstanding — see below.

## Verified here

- `:app:testDebugUnitTest` — all green.
- `:app:assembleDebug` — BUILD SUCCESSFUL.
- The direct `GradleMain` invocation was run against a real Gradle 8.13 distribution and printed
  `Gradle 8.13`.
- The launcher patch was applied to the real Gradle 8.13 `bin/gradle` and the resulting
  `DEFAULT_JVM_OPTS` was tokenised the way toybox xargs tokenises it (quotes kept): the original
  yields `"-Xmx64m"` as the first argument, the patched one yields `-Xmx64m`.
- The CLI wrapper was run against a stub server: it prints the answer while it arrives, prints
  no marker, and exits with the marker's code.

## Not verified here

The phone acceptance run (`closecode setup --gradle --json`, `gradle -v`, `aapt2 version`,
`closecode build`, `closecode install`, `closecode run`, `closecode ui dump`, and the two
engines' certificate comparison) needs an arm64 phone. The machine that made these changes is
Windows with no arm64 device attached, so those commands still have to be run on the phone.
