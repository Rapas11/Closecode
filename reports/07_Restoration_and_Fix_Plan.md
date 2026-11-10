# CloseCode — Restoration and Fix Plan (check.me PART 12 report 7)

Repo: `/storage/internal_new/project/closecode`  
Branch: `redesign/screens-10-21` (HEAD `153fd14`)  
Method: consolidation of reports 01–06 (static source audit only; device/emulator runtime not yet performed).  
Priority order follows `check.me` PART 13: crashes/stability → build/compile errors → project creation/config → GitHub persistence → build tools/downloads → missing settings → Android vs website separation → browser icons/project icon management → project management/deletion → other regressions.

---

## How each issue is tracked

For every issue: affected file & function, root cause, previously worked?, regression vs existing limitation, required correction, and how the correction will be tested.

---

## Priority 1 — Application crashes and stability (done in working tree, not yet committed)

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P1-1 | Editor `ArrayIndexOutOfBoundsException: length=493; index=629` | `ui/ide/IdeScreen.kt` (~5529, 5585, 6028, 6118); `ui/ide/FoldModel.kt:307-308` | `cursor` (display offset into `fieldValue.text`) indexes `foldMap.toSource` (sized `display.length + 1`); `foldMap` (`remember(sourceText, activeFolds)`) and `fieldValue` (`remember(active, language, sourceRevision)`) desync during typing/paste/fold/undo/file-switch | Yes | Regression | Build `foldMap` from the same `sourceText` snapshot as live `fieldValue.text` on every write path (`commit`, `commitSource`, `toggleFold`, undo/redo, file-switch reseed); never cross offsets through a stale remembered map; `sourceAt`/`displayAt` coordinate-space explicit with nullable bounds-safe variants | Unit tests in new `FoldMapTest.kt` (round-trip, fold clamps, typing/paste/file-switch/fold toggle/undo/redo/keyboard hide-show); runtime: type/paste/switch/fold/undo — no crash |
| P1-2 | Terminal `IllegalArgumentException: Unknown icon: chevron` | `ui/CcIcons.kt:81`; `ui/ide/IdeTerminal.kt:1035` | `"chevron"` missing from `CcIconPaths.all`; `requireNotNull` throws | Yes | Regression | Register `"chevron"` path alias; fallback placeholder instead of throwing (`no requireNotNull`/`!!`, `try/catch` around `PathParser`, log once via `DebugLog.crumb`) | `CcIconsTest.kt` (chevron resolves; unknown name does not throw; fuzz names); Terminal shell dropdown renders |

**Commit recommendation:** Commit P1-1 + P1-2 + associated tests before any feature work. APK and 1487 unit tests pass in working tree.

---

## Priority 2 — Build and compilation errors (needs Gradle run; read-only constraint prevented capture)

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P2-1 | "BUILD project check · 64 errors · 1 warnings" | `ui/ide/IdeScreen.kt` (`runProjectCheck`, ~1091); `ui/build/ProjectCheck.kt`; `ui/build/GradleReader.kt`; `ui/build/BuildEngine.kt`; `ui/build/ProjectPins.kt` | `ProjectCheck.check` hardcodes `AndroidManifest.xml`/`app/build.gradle.kts`/`.kt` sources for every project. Source inspection cannot determine which of the 64 errors are real Kotlin/Gradle errors vs config/template/analyzer false positives — Gradle was not run (read-only constraint). | Unknown | Unknown | Run `./gradlew :app:compileDebugKotlin`; capture the 64 errors; classify each as genuine compile error / missing dependency / wrong config or template / ProjectCheck false positive; fix code or template accordingly | Gradle build with 0 errors; `./gradlew :app:testDebugUnitTest` passes |

---

## Priority 3 — Project creation and configuration

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P3-1 | Package name blocked at creation ("Enter a package name in project settings") | `ui/StartProjectSheet.kt:75` (`packageName = ""` initial), `:104-105` (blocks); `ui/model/ProjectConfig.kt:67-73` (`defaultPackageName()` unused) | Config seeded empty; `createNow()` rejects empty package instead of deriving `defaultPackageName(name)` during creation | Yes (helper exists, unused) | Regression/oversight | Pre-fill `packageName` with `defaultPackageName(name)` when a name is entered; keep validation but don't block creation when a valid package can be derived | Create project → package pre-filled or creation proceeds; package saved, shown in Project Settings, persists across reopen/build |

---

## Priority 4 — GitHub authentication and repository persistence

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P4-1 | IDE Git panel stale after external sign-in/sign-out | `ui/ide/IdeScreen.kt:446,4535`; `ui/HeroScreen.kt:869-872` (partial fix applied in working tree); `ui/settings/SettingsScreen.kt:289` (unkeyed read still open) | `IdeGitPanel` re-reads token only when its `tokenEpoch` key changes; external sign-ins/sign-outs don't bump that panel's epoch; Settings never re-reads while composed | Yes (`TokenStore.kt` storage layer sound) | Regression | `IdeGitPanel` receives combined epoch (`gitTokenEpoch`); HeroScreen clone `onToken` calls `onGitHubChanged()`; `SettingsScreen` token read keyed by `tokenEpoch` | Open project unsigned → Settings sign in → return project → GitHub panel connected; sign out → panel disconnected |

---

## Priority 5 — Build Tools and compiler downloads

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P5-1 | Settings → "Build tools" does nothing | `ui/settings/SettingsModel.kt:346-351`; `ui/settings/SettingsLayout.kt:SettingsRow` | Row is read-only `Row.Status` with no `id`/`action`; click chain skips `Row.Status` | Yes (previous design had working entry) | Regression | Convert "Build tools" row to `Row.Nav`/`Row.Action` wired to `SettingsAction.DOWNLOAD_TOOLCHAIN` → `ToolchainSetupScreen` (already implemented, reachable only via Code Intelligence > Kotlin) | Settings → Build & Toolchain → tap Build tools → `ToolchainSetupScreen` opens |
| P5-2 | Kotlin compiler 1.9.24 download may fail ("Could not get … Dex jar") | `ui/build/ToolchainStore.kt:150-170`; `assets/toolchain.json` | Download is real (resumable, SHA-256 verified); failure depends on remote asset URLs/network/permissions at runtime — unverifiable from source | Unknown | Existing limitation (environmental) | Verify all 13 URLs + SHA-256 in `assets/toolchain.json` against the actual release; improve error text to surface HTTP status/hash mismatch; reset `started` flag in `ToolchainDownloadService.onDestroy()` so "Try again" reliably restarts | Run Settings → Build & Toolchain / Code Intelligence > Kotlin download; confirm completion or clear error; "Try again" restarts after failure |

---

## Priority 6 — Missing Settings functionality

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P6-1 | Persisted-but-inaccessible settings fields (12 fields) | `ui/settings/SettingsStore.kt:13-29`; `ui/settings/SettingsModel.kt sections()` | Fields persist (`BoolSetting`/`OptionSetting`) but `sections()` emits no rows for them | Yes | Regression | Add rows for `AUTO_INDENT`, `CODE_FOLDING`, `RAINBOW_BRACKETS`, `BRACKET_GUIDES`, `AUTO_IMPORT`, `PARAMETER_HINTS`, `QUICK_FIXES`, `BUILD_CACHE`, `COMMAND_HISTORY`, `ANIMATIONS`, `SHELL`, `TERMINAL_THEME` into appropriate section groups | Settings screen shows each field; toggling writes to `SettingsStore` |
| P6-2 | Removed rows/actions (SettingsManager pages orphaned) | `ui/settings/SettingsModel.kt` sections(); `ui/settings/SettingsPages.kt` pages exist (`SdkManagerPage`, `GradleToolsPage`, `NativeToolsPage`, `CredentialsPage`, `SigningKeysPage`, `StorageProjectsPage`) | Rows removed; `handleAction` handlers exist but no row triggers them (`SDK_MANAGER`, `FULL_GRADLE`, `NATIVE_TOOLS`, `CREDENTIALS`, `SIGNING_KEYS`, `STORAGE_PROJECTS`, `PRIVACY`) | Yes | Regression | Add rows reconnecting each action, or remove the enum entries/pages if not to be restored | Each action row opens its page |
| P6-3 | "Biometric lock" placeholder | `ui/settings/SettingsScreen.kt:358` (`comingSoon`) | Action exists but only shows dialog | Unknown | Existing limitation | Implement biometric lock or remove action/row | Settings → Security → Biometric lock works or is absent |
| P6-4 | Project location dead-end | `ui/settings/SettingsScreen.kt:242-246` | Row tappable → static "not available" info text | Unknown | Existing limitation | Implement folder picker or mark non-tappable | Tap Project location → folder picker opens or row disabled |

---

## Priority 7 — Android vs website project separation

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P7-1 | Android-only tools shown for Website projects | `ui/settings/SettingsModel.kt:336-396`; `ui/settings/SettingsScreen.kt:293-303` (no project/tab context); `ui/ide` (zero `TemplateTab` references) | Settings app-level; Android-only rows (`SDK_MANAGER`, `FULL_GRADLE`, `NATIVE_TOOLS`, `DELETE_BUILD_TOOLS`, Build tools) shown for Website projects | Unknown | Regression | Thread open project's `TemplateTab` into `SettingsScreen`; gate Android-only rows to `tab == App`; hide/disable for Web with explanation | Open settings on Website project → Android-only rows hidden/disabled |
| P7-2 | Run button can enable for Website project (crash risk) | `ui/ide/IdeScreen.kt:3029` (`canRun = projectErrors == 0`); `ui/build/ProjectCheck.kt:99-358` (hardcodes Android structure) | `projectErrors` counts Kotlin analyzer errors only; Website with no `.kt` files → 0 errors → Run enabled → `BuildEngine.build` assumes Android structure | Unknown | Regression | Type-aware `ProjectCheck` overload taking `TemplateTab`; guard `BuildSetupFactory.create`/`BuildEngine.build` by `tab == App`; make `canRun` use `projectReport.errors.isEmpty()` for Android | Open Website project → Run disabled or shows Android-only message |
| P7-3 | Project settings shows Android fields for Website projects | `ui/ide/IdeProjectSettings.kt:64-84`; `ui/ide/IdeScreen.kt:3062-3093` | Sheet renders Android SDK/package/signing/icon fields regardless of `tab` | Unknown | Regression | Pass `tab` to `IdeProjectSettingsSheet`; show Android fields only for `App` | Open Website project settings → no MinSdk/TargetSdk/CompileSdk/package/signing fields |

---

## Priority 8 — Browser icon downloads and project icon management

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P8-1 | Saved-icons collection not persisted | `ui/ide/IdeIconsScreens.kt:572` | Pack download state persisted; icon XML written to project drawable; saved-icons UI state pure Compose state — lost on navigation/restart | No | Existing limitation | Persist saved icon names/metadata; reload on screen open | Save icon → leave screen → return → collection intact |
| P8-2 | Get → Pick → per-icon flow is pack-level, not per-icon (design mismatch) | `ui/ide/IdeIconsScreens.kt:628-655` (pack download), `:941-977` (Pick dropdown lists packs not icon types) | PART 8 design describes per-icon Get/Pick/type dropdown; implementation is pack-level | Existing architecture | Existing limitation | If per-icon flow is required: introduce per-icon state keyed by icon name; change Pick dropdown to list icon types within selected pack | Browser Get shows progress; Pick shows icon types; saved icon appears in project collection |

---

## Priority 9 — Project management and deletion

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P9-1 | Long-press on project does nothing (no delete/rename) | `ui/ProjectsSheet.kt` (`ProjectSwitchRow`, ~line 303); `ui/HeroScreen.kt:720-736` | Rows tap-only; no context menu. `RecentProjectsScreen.kt:42-336` has complete flow but is dead code (no `navigate("recent")` caller) | Yes (`RecentProjectsScreen` implemented long-press → Rename/Delete) | Regression | Add long-press context menu (Open/Rename/Delete with confirmation) to visible project rows; Delete calls `repository.delete(slug)` + `refreshTree()` | Long-press project → menu; Delete → confirmation → removed from list and disk immediately |

---

## Priority 10 — Other discovered functional regressions

| # | Issue | File:Function | Root cause | Previously worked? | Kind | Required correction | Test |
|---|---|---|---|---|---|---|---|
| P10-1 | Duplicate icon systems | `ui/CcIcons.kt` vs `ui/ide/CcIcons.kt` | Two registries; name/render drift possible | No | Existing limitation | Consolidate or statically verify every string name resolves | Build/test asserts every `CcIcon(name)` literal resolves |
| P10-2 | Settings navigation rewrite dropped tone tiles/visuals (cosmetic) | `ui/settings/SettingsLayout.kt` | Rewritten from `SettingsCategoryList`/`SettingsDetailPage` to panel style | Yes | Existing limitation | Restore tone tiles if desired (cosmetic, not functional) | — |

---

## Suggested commit order

1. **Crashes first** (P1-1, P1-2 + tests) — `IdeScreen.kt`, `FoldModel.kt`, `CcIcons.kt`, `CcIconsTest.kt` — commit with tests; verify APK + unit tests.
2. **GitHub epoch** (P4-1) — `IdeScreen.kt`, `IdeGitScreens.kt`, `HeroScreen.kt`, `MainActivity.kt` — commit; verify runtime if a device/emulator is available.
3. **Project deletion** (P9-1) — `ProjectsSheet.kt`/`HeroScreen.kt` — commit; verify runtime.
4. **Project creation package** (P3-1) — `StartProjectSheet.kt` — commit; verify runtime.
5. **Build tools row** (P5-1) — `SettingsModel.kt` — commit; verify runtime.
6. **Missing settings** (P6-1..P6-4) — `SettingsModel.kt`/`SettingsScreen.kt` — commit; verify runtime.
7. **Android vs website gating** (P7-1..P7-3) — `IdeScreen.kt`, `IdeProjectSettings.kt`, `SettingsModel.kt`, `ProjectCheck.kt` — commit; verify runtime.
8. **Saved icons persistence** (P8-1) — `IdeIconsScreens.kt` — commit; verify runtime.
9. **Duplicate icons** (P10-1) — `CcIcons.kt` — commit; verify build/test.

Build/compile errors (P2-1) block everything else — run Gradle as soon as possible and classify the 64 errors before declaring any "build healthy" milestone.
