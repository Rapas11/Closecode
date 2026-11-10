# CloseCode — Build Tools and Download Investigation

**Source:** `check.me` PART 5 / PART 12 Report 4.  
**Method:** Read-only source audit. The actual "64 errors" report could not be reproduced because running Gradle was out of scope (read-only); the source of the count is unknown without a build.

## A. Build Tools settings row does nothing (PART 5.A)

### Root cause
`app/src/main/java/com/closecode/ui/settings/SettingsModel.kt:346-351` declares the "Build tools" row as `Row.Status("Build tools", "Kotlin, Java, dex and signer", live.toolchainStatus, ok = ...)` — a read-only fact with **no `id`/`action`**. `SettingsLayout.kt:SettingsRow()` only makes `Row.Nav`, `Row.Options`, `Row.Action`, `Row.Account`, `Row.Language` clickable; `Row.Status` is excluded. Tapping it opens nothing.

### Required correction
Convert to `Row.Nav("Build tools", ..., id = SettingsAction.DOWNLOAD_TOOLCHAIN)` or add a sibling nav row. `SettingsScreen.handleAction()` already maps `DOWNLOAD_TOOLCHAIN → setupOpen = true` (`SettingsScreen.kt:344`) → `ToolchainSetupScreen` (functional, real download/verify states).

## B. Kotlin compiler 1.9.24 Dex jar download failure (PART 5.B)

### Root cause
The error `"Could not get ${component.fileName}. Try again later."` is produced by `ToolchainStore.ensureAll()` (`ToolchainStore.kt:153, 168`) when `MAX_TRIES = 3` retries are exhausted for component `kotlin-compiler-1.9.24-dex.jar`. Failure modes:
1. **HTTP 404** — the GitHub release `tools-v1` or asset was never uploaded. `download()` throws, retries 3×, then fails.
2. **SHA-256 mismatch** — downloaded bytes don't match the pinned hash in `assets/toolchain.json` line 19; `.part` deleted, retries exhaust, then fails.
3. **No network** — `noInternet()` fails immediately ("No internet connection.", bypassing retries).

The download machinery itself is **real code, not a placeholder**: resumable `.part` files, Range headers, SHA-256 verification, foreground service, pause/cancel, Wi-Fi/mobile gate. The failure is likely the remote assets at the pinned URLs (`github.com/Rapas11/Closecode/releases/download/tools-v1/...`) being missing or hash-mismatched — which cannot be confirmed from source alone.

### Required correction
- Verify every URL and SHA-256 in `assets/toolchain.json` against the actual release (13 components). Upload/correct assets if missing or mismatched — release-engineering, not code.
- Improve the error to surface the HTTP status / hash mismatch so users know *why* it failed.
- Make "Try again" reliably restart: `ToolchainDownloadService.onStartCommand()` sets `started = true` and never resets, so a second `startForegroundService` while the service is alive returns `START_NOT_STICKY` without re-invoking `ensureAll()`. Reset `started` in `onDestroy()` or re-launch `ensureAll()` when state is `Failed`/`NotStarted`.

### Entry points (PART 5.C) — three disjoint paths, none from Build & Toolchain
| # | Location | Reaches `ToolchainSetupScreen`? |
|---|----------|---------------------------------|
| 1 | Settings > Build & Toolchain > "Build tools" `Row.Status` | **No** (no-op) |
| 2 | Settings > Code Intelligence > Languages > Kotlin (not ready) | Yes, via `DOWNLOAD_TOOLCHAIN` |
| 3 | `ProjectSetupScreen` post-scaffold | Yes, via `ToolchainDownloadService.start()` |
| 4 | Settings > Build & Toolchain > "Delete build tools" | No — deletes only |
| 5 | `ToolchainSetupScreen` itself | Yes — is the screen |

`ToolchainSetupScreen` exists and is complete (NotStarted/Downloading/Verifying/WaitingForWifi/Ready/Failed states, progress bar, Pause/Cancel, Try again, Check files, Check for updates, Delete, mobile-data toggle, size/free-space facts). It just isn't reachable from the Build & Toolchain section.

## Tests needed (none exist currently)
- `ToolchainStoreDownloadTest`: `ensureAll()` with mock network/temp dir — all missing → Ready; wrong hash → retries then Failed; `.part` at full size + correct hash → promotes without re-download; network loss → `WaitingForWifi`, resume when allowed.
- `ToolchainDownloadServiceTest`: PAUSE/CANCEL actions; second start doesn't duplicate download (current) / restarts after fix.
- `SettingsScreenNavigationTest`: Build tools row → `setupOpen = true`.
- `BuildToolsUrlVerificationTest`: every `toolchain.json` URL non-blank, SHA-256 64-hex, `sizeBytes > 0`; network variant asserts 200, `Content-Length == sizeBytes`, body SHA-256 == pinned hash.
- `BuildSetupMissingToolsTest`: zero jars → `tools == null`, `missing` lists absent ids; all present → `tools != null`.

## 64 errors
The reported `BUILD project check · 64 errors · 1 warnings` could not be classified from source. Run `./gradlew :app:compileDebugKotlin`, capture output, and classify each error as: genuine Kotlin/Gradle compile error, missing dependency, wrong config/template, or ProjectCheck analyzer false positive. `ProjectCheck.kt:99-358` hardcodes `AndroidManifest.xml`/`app/build.gradle.kts`/`.kt` sources for *every* project — websites always fail this contract, which may contribute to the count.
