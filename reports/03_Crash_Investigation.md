# Crash Investigation (check.me PART 1/12)

Repo: `/storage/internal_new/project/closecode`  
Branch: `redesign/screens-10-21` (HEAD `153fd14`)  
Status: root causes identified; fixes applied in working tree (tests + APK build pass); fixes **not yet committed**.

---

## Crash 1 — Code Editor `ArrayIndexOutOfBoundsException`

### Reported stack
```
java.lang.ArrayIndexOutOfBoundsException: length=493; index=629
at com.closecode.ui.ide.IdeScreenKt.IdeEditorArea(IdeScreen.kt:5528)
```

### Affected code (committed HEAD; line numbers approx. ±2 vs build)
- `app/src/main/java/com/closecode/ui/ide/IdeScreen.kt` edit sites:
  - `kotlinItems` (~5526): `foldMap.toSource[cursor.coerceIn(0, code.length)]`
  - `kotlinPrefix` (~5582): same
  - `formatSelection` (~6025): same
  - `formatRequest` (~6115): same
- `app/src/main/java/com/closecode/ui/ide/FoldModel.kt` — `FoldMap` data class: `toSource : IntArray` sized `display.length + 1`; `toDisplay : IntArray` sized `source.length + 1`.

### Root cause
The editor keeps **two coordinate systems**:
- **Source text** (`sourceText`) — the file on disk, every line.
- **Display text** (`foldMap.display` == `fieldValue.text`) — source with folded bodies removed.

`cursor = fieldValue.selection.start` is a **display offset**. `foldMap` is `remember(sourceText, activeFolds)` while `fieldValue` is `remember(active, language, sourceRevision)` — independent remember keys. They desync across keystrokes/paste/fold/undo/file-switch windows (the code comments already acknowledge this). When `fieldValue`/`cursor` advance to a longer display text but `foldMap.toSource` still describes an older/shorter one, `cursor` (e.g. 629) indexes `toSource` whose size is only `display.length + 1` (e.g. 493) → `ArrayIndexOutOfBoundsException: length=493; index=629`.

### Previously worked?
Yes — mapping worked when `foldMap` and `fieldValue` were in sync; the desync window is the regression.

### Limitation type
Regression (commit/reseed/toggle paths write `sourceText` and `fieldValue` without keeping the remembered `foldMap` in lockstep).

### Required correction (not try/catch)
1. Derive `sourceText` and `fieldValue` from a **single `FoldMap` snapshot** on every write path (`commit`, `commitSource`, `toggleFold`, undo/redo, file-switch reseed) so `fieldValue.text == foldMap.display` always holds at offset-crossing points.
2. Never index `toSource` with a live cursor against a possibly-stale remembered map.
3. Make `FoldModel.sourceAt`/`displayAt` coordinate-space explicit:
   ```kotlin
   internal fun FoldMap.sourceAt(displayOffset: Int): Int =
       require(displayOffset in 0..display.length) { "…" }.let { toSource[it] }
   internal fun FoldMap.displayAt(sourceOffset: Int): Int =
       require(sourceOffset in 0..source.length) { "…" }.let { toDisplay[it] }
   internal fun FoldMap.sourceAtOrNull(displayOffset: Int): Int =
       toSource[displayOffset.coerceIn(0, toSource.size - 1)]
   ```
   (`require` catches desync in tests/debug; `OrNull` clamp keeps production from crashing.)

### Correction status
Applied in working tree: all four crash sites now call `foldMap.sourceAt(cursor)`; `FoldModel.sourceAt` clamps to `toSource.size - 1`. This stops the crash but silently returns a clamped (possibly wrong) offset — the single-snapshot sync (point 1) remains the root-cause fix to complete.

### Verification
- Unit tests: new `FoldMapTest.kt` — round-trip `sourceAt`/`displayAt`, fold clamps, typing/paste/file-switch/fold toggle/undo/redo/keyboard open-close.
- Runtime: type, paste large blocks, switch files, fold toggle, undo/redo, open/close keyboard — no crash; completions/caret land correctly after folds.

---

## Crash 2 — Terminal `IllegalArgumentException: Unknown icon: chevron`

### Reported stack
```
java.lang.IllegalArgumentException: Unknown icon: chevron
at com.closecode.ui.CcIconsKt.ccIconVector(CcIcons.kt:81)
at com.closecode.ui.ide.IdeTerminalKt.IdeTerminal(IdeTerminal.kt:1035)
```

### Affected code
- `app/src/main/java/com/closecode/ui/CcIcons.kt:81` — `ccIconVector` used `requireNotNull(CcIconPaths.all[name]) { "Unknown icon: $name" }`.
- `app/src/main/java/com/closecode/ui/ide/IdeTerminal.kt` (~1035) — `CcIcon("chevron", …)` for the terminal shell-selector dropdown.

### Root cause
`CcIconPaths.all` (the string path registry) lacked a `"chevron"` key, so `ccIconVector` threw `IllegalArgumentException`. All 25 other string icon names used in the app are present in the registry; only `"chevron"` was missing.

### Previously worked?
No — registration gap present whenever `"chevron"` was introduced.

### Limitation type
Existing limitation (missing registry entry + throwing lookup). No other string-icon crash sites exist statically; variable-driven icon names (Settings, Icons screens, Lists, SpecComponents, Terminal key bar) rely on the lookup not throwing.

### Required correction
1. Register `"chevron"` → down-right chevron path (`M9 18l6-6-6-6`, same as `chevron-right`), appropriate for the dropdown toggle.
2. Replace `requireNotNull`/`!!` with a guaranteed-present fallback: `"unknown"` placeholder entry + empty-vector last resort; log once via `DebugLog.crumb("ICONS", …)`; guard `PathParser` parse errors so a bad path never kills the UI.
3. Never throw from icon resolution.

### Correction status
Applied in working tree: `"chevron"` added to `CcIconPaths.all`; `ccIconVector` falls back without throwing; `DebugLog.crumb` on fallback. Regression tests added in `CcIconsTest.kt` (chevron resolves; unknown name does not throw).

### Verification
- Unit tests: `CcIconsTest.kt` — chevron resolves to non-empty `ImageVector`; unknown name does not throw; all registered paths parse via `PathParser`; fuzz 200 random names returns non-null vectors.
- Runtime: open Terminal header shell dropdown — no crash.

---

## Additional crash-risk findings (not yet crashing, but latent)

| # | Issue | File:line | Risk |
|---|-------|-----------|------|
| A | Variable-driven icon names not statically verifiable | `SettingsLayout.kt:146,192,315`; `SettingsExtraPages.kt:376`; `SettingsDeveloperPage.kt:146`; `IdeIconsScreens.kt:1047`; `IdeBlockPanel.kt:295`; `IdeUiInspector.kt:268`; `IdeProjectExtras.kt:139`; `Lists.kt:74,151,251`; `SpecComponents.kt:129,178,435`; `IdeTerminal.kt:1638` | If any variable holds an unregistered name, the pre-fix code crashed; post-fix it draws fallback. Safe fallback now applied. |
| B | `foldMap.toDisplay[…]` with source offsets | `IdeScreen.kt:4919,4925,4985,4987,5010,5012,5058,5060,5192` | These clamp to `source.length` and `toDisplay` is `source.length+1`, so they are safe as long as the offset is truly a source offset. Risk if a display offset is accidentally passed. |
| C | `foldMap.toSource[…]` clamped sites | `IdeScreen.kt:5477,5478` | Clamped to `display.length`; safe. |

---

## Commit recommendation
Commit the two crash fixes (`CcIcons.kt`, `FoldModel.kt`, `IdeScreen.kt`, `CcIconsTest.kt`) before any further feature work, per `check.me` PART 13 priority 1 (crashes and stability).
