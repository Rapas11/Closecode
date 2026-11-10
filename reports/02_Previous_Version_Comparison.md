# Previous Version Comparison (check.me PART 11/12)

Repo: `/storage/internal_new/project/closecode`  
Baseline candidates: tag `v0.43.0-v1-final` (`main`, "Step 21: let OpenCode work hands-free…"), commit `2fb35a4` / branch `ui-backup-20261104` ("Backup: snapshot before target UI rebuild (build 59)").  
Current: HEAD `153fd14` (`redesign/screens-10-21`).  
Method: `git log`, `git diff <baseline> HEAD` on settings/build/GitHub/project/browser areas; read-only.

---

## 1. What existed before the redesign (v0.43 / 2fb35a4)

- Settings model `SettingsModel.kt` with full sections: General, Editor, Code Intelligence, Build & Toolchain, Terminal, Git & GitHub, Appearance, Storage, Security, About.
- `SettingsStore.kt` persisting ~12 Bool/Option settings (line numbers, autocomplete, diagnostics, code folding, quick fixes, build cache, terminal command history/pipe/font/theme, animations, app lock, etc.).
- Settings UI: `SettingsCategoryList` + `SettingsDetailPage` (category → detail drill-down), `SettingsLayout.kt` with tone-based tiles, section icons, per-row visuals.
- Settings actions wired: `SDK_MANAGER`, `FULL_GRADLE`, `NATIVE_TOOLS`, `CREDENTIALS`, `SIGNING_KEYS`, `BIOMETRIC_LOCK`, `STORAGE_PROJECTS`, `PRIVACY`, `DOWNLOAD_TOOLCHAIN`, `SAVED_COMMANDS`, `SAVED_SIGN_INS`, `AI_PROVIDER_KEY`, `UPDATE_RUNTIME`, `SOURCE_CODE`.
- GitHub Device Flow sign-in, clone, link, push, pull via `GitHubClient`/`GitHubRemote`/`TokenStore`; token epoch invalidation.
- Project creation writing `ProjectConfig` (package, SDKs, Gradle) into `.closecode.json`; `defaultPackageName(projectName)` helper.
- `RecentProjectsScreen` with long-press → Rename/Delete (complete flow, later displaced).
- Icon pack browser (`IdeIconsScreens.kt`) pack-based Get/Pick/save (substantially present).
- Full Gradle / Native Tools / SDK Manager download pages (`SettingsPages.kt`).

---

## 2. What changed during the redesign (v0.43 → HEAD)

### Settings model (`SettingsModel.kt` ~550 lines changed; `SettingsStore.kt` ~135 lines changed)

- The settings model survived `2fb35a4 → HEAD` almost intact (`git diff 2fb35a4 HEAD` = ~8 lines, mostly accent color hexes).
- The losses happened **v0.43.0-v1-final → HEAD** (the redesign). Rows/actions dropped from `sections()`:
  - Persisted-but-inaccessible store fields: `AUTO_INDENT`, `CODE_FOLDING`, `RAINBOW_BRACKETS`, `BRACKET_GUIDES`, `AUTO_IMPORT`, `PARAMETER_HINTS`, `QUICK_FIXES`, `BUILD_CACHE`, `COMMAND_HISTORY`, `ANIMATIONS`, `SHELL`, `TERMINAL_THEME` (all still read/written by `SettingsStore` but no UI row).
  - Removed rows: "Pinned versions", "Download build tools", "SDK Manager", "Default shell", "Terminal colours", "Biometric lock", "Credentials", "Signing keys", "Project export & import".
  - Orphaned actions (in enum + `handleAction` but no row): `SDK_MANAGER`, `FULL_GRADLE`, `NATIVE_TOOLS`, `CREDENTIALS`, `SIGNING_KEYS`, `BIOMETRIC_LOCK`, `STORAGE_PROJECTS`, `PRIVACY`.
  - No-op rows: "Build tools" (read-only status), "Project location" (dead-end info), Biometric lock ("coming soon").
- `SettingsLayout.kt` rewritten: removed `SettingsCategoryList`/`SettingsDetailPage`/tone tiles; added `SettingsPage`/`SetGroup`/`SettingsRow` panel style.
- `AiSettings.kt` (134 lines) deleted in commit `ba3e0307` (the v0.43 tag itself).

### Navigation structure

- Settings: category list/detail drill-down → single panel + section pages; `SettingsScreen` invoked from `MainActivity` with **no project/tab context** (app-level, not project-aware).
- Home: `RecentProjectsScreen` (long-press delete/rename) displaced by `HeroScreen` inline rows + `ProjectsSheet` (tap only, no delete).
- Icon browser: pack-based flow present; per-icon Get/Pick described in design but implemented as pack-level.

### Functionality removed/replaced

| Area | Before (v0.43) | After (HEAD) |
|------|---------------|--------------|
| Settings navigation | Category list → detail pages | Panel + sections |
| "Build tools" entry | `DOWNLOAD_TOOLCHAIN` action row (presumed) | `Row.Status` read-only, taps nothing |
| Project deletion | `RecentProjectsScreen` long-press menu | Not present on Home/switcher |
| Package name default | `defaultPackageName(name)` helper | Helper unused; sheet starts empty |
| GitHub epoch | Sign-in invalidation | Incomplete wiring (IDE panel got local epoch, not combined) |
| Icon systems | One registry | Two (String `ui/CcIcons.kt` + ImageVector `ui/ide/CcIcons.kt`) |

---

## 3. What stopped working (regressions)

1. **"Build tools" opens nothing** — row lost its action.
2. **Project delete/rename from Home** — `RecentProjectsScreen` displaced; `ProjectsSheet` has no long-press.
3. **Project creation package flow** — empty default blocks with "go to Project Settings"; `defaultPackageName()` unused.
4. **GitHub panel staleness** — IDE `IdeGitPanel` did not re-read token after external sign-in (epoch wired to local var only). Fixed in working tree, not committed.
5. **Settings rows/actions orphaned** — `SDK_MANAGER`/`FULL_GRADLE`/`NATIVE_TOOLS`/etc. have pages but no rows.
6. **Android/Website not gated** — `grep TemplateTab` over `ui/ide` = 0; Android-only settings/tools shown for Website projects; `ProjectCheck` hardcodes Android structure for all projects.

---

## 4. What was implemented differently / newly

- New `SettingsPage`/`SetGroup`/`SettingsRow` component set; `AppearancePage`, `SettingsChoiceDialog`, `StorageProjectsPage`, `ToolchainSetupScreen`.
- `IdeTerminal.kt` redesign: tabs, saved-command panel, key bar, `ShellsHome`, chat-style log (partially replaced by newer tab design in working tree).
- `FoldModel.kt` folding map with `toSource`/`toDisplay` (crash source).
- `ToolchainStore.kt`/`ToolchainDownloadService.kt` resumable SHA-256-verified download manager.
- `ProjectFiles.kt` (`applyPackage`/`applySdks`/`applyVersion`) for project settings file rewrites.

---

## 5. What was accidentally lost

- `AiSettings.kt` deleted at v0.43 tag (OpenCode AI settings page).
- `RecentProjectsScreen` delete/rename UX (functionality survives in code but unreachable).
- `defaultPackageName()` helper (exists but unused).
- Complete per-row settings visuals/tone tiles from `SettingsLayout.kt` (replaced by plainer panel style — cosmetic, not functional).

---

## 6. Baseline for restoration

- Restore settings rows/actions from `SettingsModel.kt` at `v0.43.0-v1-final` (or `2fb35a4`) into current `SettingsModel.kt` sections(), keyed to existing `SettingsStore` fields and `SettingsPages.kt` page implementations.
- Restore project long-press delete/rename from `RecentProjectsScreen.kt` into `ProjectsSheet.kt`/`HeroScreen.kt`.
- Pre-fill package via `ProjectConfig.defaultPackageName(name)` in `StartProjectSheet.kt`.
- Reconnect orphaned `SettingsAction`s or remove them if not to be restored.
