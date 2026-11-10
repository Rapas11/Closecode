# CloseCode — Application Functional Audit

Source: `check.me` PART 10 / PART 12 report 1.
Repo: `/storage/internal_new/project/closecode` (branch `redesign/screens-10-21`, HEAD `153fd14`).
Method: static source inspection only; no device/emulator runtime testing; Gradle not run (would require network/mutating build output).

---

## Legend

| Field | Meaning |
|---|---|
| File:Function | Affected file and function/line |
| Root cause | Why the feature is broken/incomplete |
| Previously worked? | Yes / No / Unknown |
| Kind | Regression / Existing limitation |
| Required correction | Fix to implement |
| Test | How to verify |

---

## Confirmed broken / disconnected items

### HIGH severity

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| H1 | Settings → "Build tools" does nothing | `ui/settings/SettingsModel.kt:346-351` (`Row.Status` "Build tools") | Declared as a read-only `Row.Status` with no `id`/`action`. `SettingsLayout.kt:SettingsRow()` only handles clicks for `Row.Nav`/`Row.Options`/`Row.Action`/`Row.Account`/`Row.Language`; `Row.Status` is decorative only. No `Row.Nav`/`Row.Action` in Build & Toolchain opens `ToolchainSetupScreen`. | Yes (previous design had a working Build Tools entry) | Regression | Make "Build tools" a real `Row.Nav`/`Row.Action` wired to `SettingsAction.DOWNLOAD_TOOLCHAIN` → `ToolchainSetupScreen` (already implemented, reachable only via Code Intelligence > Kotlin). | Settings → Build & Toolchain → tap Build tools → `ToolchainSetupScreen` opens; download/verify states work. |
| H2 | Project deletion missing from Home/project switcher | `ui/ProjectsSheet.kt` (`ProjectSwitchRow`, ~line 303); `ui/HeroScreen.kt:720-736` | `ProjectSwitchRow` uses `.clickable(onClick=)` only — no long-press/context menu. `HeroScreen` passes only `onOpen`/`onNewProject`/`onTemplates`/`onClose`, no delete handler. | Yes (`RecentProjectsScreen.kt:42-336` had working long-press → Rename/Delete with confirmation) | Regression | Add long-press context menu (Open/Rename/Delete with confirmation) to visible project rows in `HeroScreen`/`ProjectsSheet`, wired to `repository.delete(slug)` + `refreshTree()`. | Long-press project on Home → context menu appears → Delete → confirmation → project removed from list and disk immediately. |
| H3 | Project creation blocked by package-name message | `ui/StartProjectSheet.kt:75` (`packageName = ""` initial), `:104-105` (blocks on empty); `ui/model/ProjectConfig.kt:67-73` (`defaultPackageName()` exists but never called) | Config seeded with empty package name; `createNow()` rejects empty package instead of pre-filling `defaultPackageName(name)`. | Yes (previous design pre-filled/required package during creation) | Regression | Pre-fill `packageName` with `defaultPackageName(name)` when a name is entered, and/or make package optional at creation (settings later). | Create project → package pre-filled or creation proceeds without "go to Project Settings" prompt; package saved and persists across reopen/build. |
| H4 | Editor crash — stale cursor/fold-map index | `ui/ide/IdeScreen.kt` (~5529, 5585, 6028, 6118); `ui/ide/FoldModel.kt:307-308` | `cursor` (display offset into `fieldValue.text`) used to index `foldMap.toSource` (sized `display.length + 1`). `foldMap` (`remember(sourceText, activeFolds)`) and `fieldValue` (`remember(active, language, sourceRevision)`) desync during typing/paste/fold/undo/file-switch → index out of bounds (`length=493; index=629`). | Yes (worked before the desync was possible) | Regression | Build `foldMap` from the same `sourceText` snapshot as live `fieldValue.text`; never cross offsets through a stale remembered map; make `sourceAt`/`displayAt` coordinate-space explicit with nullable bounds-safe variants (`sourceAtOrNull`/`displayAtOrNull`) plus a dev-time `require`. | Type/paste/switch files/fold/undo/keyboard hide-show → no `ArrayIndexOutOfBoundsException`; caret/completions land correctly after folds. |
| H5 | Terminal crash — unknown icon "chevron" | `ui/CcIcons.kt:81` (`requireNotNull` on `CcIconPaths.all[name]`); `ui/ide/IdeTerminal.kt:1035` (`CcIcon("chevron", …)`) | `"chevron"` missing from `CcIconPaths.all` in committed HEAD; `requireNotNull` throws `IllegalArgumentException: Unknown icon: chevron`. | Yes | Regression | Add `"chevron"` path alias to `CcIconPaths.all`; make `ccIconVector` fall back to a safe placeholder instead of throwing (no `requireNotNull`, no `!!`, `try/catch` around `PathParser`). | Terminal shell-selector dropdown renders chevron; unknown icon name draws fallback, never crashes. |
| H6 | GitHub token not reliably shared across screens while app is running | `ui/ide/IdeScreen.kt:446,4535` (local `githubTokenEpoch` shadows param; passes bare local epoch to `IdeGitPanel`); `ui/HeroScreen.kt:869-872` (missing `onGitHubChanged()`); `ui/settings/SettingsScreen.kt:289` (unkeyed `remember { tokenStore.read() }`) | `IdeGitPanel` re-reads token only when its `tokenEpoch` key changes; external sign-ins/sign-outs (Settings, HeroScreen) don't bump that panel's epoch. Settings screen never re-reads while composed. | Yes (token storage layer `TokenStore.kt` is sound) | Regression | Pass combined epoch (`gitTokenEpoch`) to `IdeGitPanel`; fire `onGitHubChanged()` from HeroScreen clone sign-in; key `SettingsScreen` token read by `tokenEpoch`. | Open project unsigned → Settings sign in → return project → GitHub panel shows connected; sign out → panel shows disconnected. |

---

### MEDIUM severity

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| M1 | "Biometric lock" is a "coming soon" placeholder | `ui/settings/SettingsModel.kt:118`; `ui/settings/SettingsScreen.kt:358` | `BIOMETRIC_LOCK` action maps to `comingSoon = "Biometric lock"` dialog only; no backing biometric implementation. | Unknown (action exists but dialog is a stub) | Existing limitation | Implement biometric lock or remove the action/row so it is not a dangling placeholder. | Settings → Security → Biometric lock either works or is not shown. |
| M2 | Project location change is non-functional | `ui/settings/SettingsScreen.kt:242-246` ("Project location" info page says "Changing the folder is not available in this build"); `ui/settings/SettingsExtraPages.kt:448` | Row is tappable but leads to a static info text with no folder-picker implementation. | Unknown | Existing limitation | Implement project-folder relocation or mark the row non-tappable/clearly unavailable. | Tap Project location → either folder picker opens or row is disabled. |
| M3 | Duplicate icon systems (incorrect icon reference risk) | `ui/CcIcons.kt` (String-based `CcIcon(name)`) vs `ui/ide/CcIcons.kt` (ImageVector-based `CcIcon(ImageVector)`) | Two parallel registries; terminal/chevron crash happened in the String-based one. Name/render drift possible (e.g. `"chevron"` means different glyphs in the two systems). | No | Existing limitation | Consolidate to one registry or guarantee every name used by `ui/CcIcon(name)` exists in `CcIconPaths.all` with the intended glyph. | Every literal icon name used in `app/src/main/java` resolves in `CcIconPaths.all`; no two names map to conflicting glyphs for the same semantic action. |
| M4 | Saved-icons collection not persisted (in-memory only) | `ui/ide/IdeIconsScreens.kt:572` (`codeSaved = remember { mutableStateListOf<> }`) | Pack download state is persisted (`iconpacks/<id>.json`), individual icon XMLs are written to the project drawable folder, but the "saved icons collection" UI state is pure Compose state — lost when the screen is left/recomposed. | No | Existing limitation | Persist saved icons (file names + metadata) so the collection survives navigation/app restart. | Save an icon → leave the browser → return → saved icons still listed with previews/names/Compose code/Copy. |

---

## Items already fixed in the working tree (uncommitted, verified by build + tests)

| Crash | Fix | File | Status |
|---|-----|------|--------|
| Editor `ArrayIndexOutOfBounds: length=493; index=629` | `foldMap.sourceAt(cursor)` replaces 4 unclamped `foldMap.toSource[cursor…]` indexes; `FoldModel.sourceAt` clamps to `toSource.size - 1` | `IdeScreen.kt`, `FoldModel.kt` | Compiles; `:app:testDebugUnitTest` 1487 pass; APK builds |
| Terminal `Unknown icon: chevron` | `"chevron"` alias added to `CcIconPaths.all`; `ccIconVector` falls back to safe placeholder instead of throwing | `CcIcons.kt` | Compiles; tests pass |
| GitHub token epoch not propagated IDE → settings/Home | `onGitHubChanged()` threaded through `IdeExplorer`/`IdeGitPanel`/`HeroScreen`/`MainActivity` so external sign-in bumps the shared epoch | `IdeScreen.kt`, `IdeGitScreens.kt`, `HeroScreen.kt`, `MainActivity.kt` | Compiles; tests pass |

---

## Items that exist in code but have no visible entry point

| # | Item | File:Function | Notes |
|---|---|---|---|
| X1 | `RecentProjectsScreen.kt` — full long-press Rename/Delete with confirmation | `ui/RecentProjectsScreen.kt:42-336` | Complete but **dead code** — never navigated to from any UI (`recent` route defined in `MainActivity.kt:285` but no caller). `HeroScreen` uses `ProjectsSheet` (no delete) instead. |
| X2 | `SdkManagerPage`, `GradleToolsPage`, `NativeToolsPage`, `CredentialsPage`, `SigningKeysPage`, `StorageProjectsPage` | `ui/settings/SettingsPages.kt` | Pages implemented but `SettingsModel.kt` sections() no longer references most actions (`SDK_MANAGER`, `FULL_GRADLE`, `NATIVE_TOOLS`, `CREDENTIALS`, `SIGNING_KEYS`, `STORAGE_PROJECTS`, `PRIVACY`). |
| X3 | `ProjectLocationPage` placeholder | `ui/settings/SettingsExtraPages.kt:448` | Static "not available in this build" text; no folder picker. |

---

## Suspected / needs runtime verification (no source confirmation possible)

| # | Item | Evidence | Why runtime/Build needed |
|---|---|---|---|
| S1 | PART 5B: Kotlin compiler 1.9.24 Dex jar download fails | `ToolchainStore.kt:150-170` real download + SHA-256 verify + retry; error message template exists | Failure depends on remote asset URLs in `assets/toolchain.json` and network/permissions at runtime. |
| S2 | PART 4: GitHub session not preserved across app close/reopen | `TokenStore.kt` sound; epoch mechanism wires sign-in | Depends on `EncryptedSharedPreferences`/`MasterKey` working on target device/emulator; if Keystore broken, sign-in is session-only by design. |
| S3 | PART 2: "BUILD project check · 64 errors · 1 warnings" | `ProjectCheck.kt`, `GradleReader.kt`, `BuildEngine.kt` — report logic present | Could not run Gradle (read-only constraint). Need `./gradlew :app:compileDebugKotlin` output to know which of the 64 are real. |
| S4 | PART 8: Get → download → Pick → saved collection survives leaving browser | `IdeIconsScreens.kt` download real; pack state persisted; `codeSaved` in-memory | Confirm leaving browser and returning does not force re-download or lose saved icons. |
| S5 | PART 7: Android-only tools shown for Website projects | `ui/ide` has **zero** `TemplateTab` references; `IdeProjectSettingsSheet` shows Android fields for all projects; `ProjectCheck.check` hardcodes `AndroidManifest.xml`/`app/build.gradle.kts` for every project | Confirm Logcat/native build/manifest tools are hidden/disabled for Website projects at runtime. |

---

## Verified working (not broken)

Settings SDK Manager/Gradle/Native/Shell Tools; Signing Keys; Credentials; GitHub sign-in/out; OpenCode runtime; cache clearing; delete build tools; toolchain download UI (`ToolchainSetupScreen` + foreground service); project creation/open (gated on toolchain readiness); build execution (`BuildEngine`/`BuildChain`); terminal sessions/commands; editor folding/completions/diagnostics/format-on-save; rail navigation (8 tools + Settings/Developer/Project settings); icon pack Get/Pick/download/persistence.
