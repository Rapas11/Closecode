# File 3 — "guild.v10" Implementation Reference

Source: `/storage/internal_new/project/closecode/guild.v10` (4,883 lines), the designer-authored implementation guide. It contains actual Kotlin/reference code for the target screens, not just descriptions. Treat as law; reuse/adapt the existing components it names.

## How to use this reference

- guild.v10 is written against a LIGHT theme token set (rail #E3E5EA, panel #F1F2F5, content #FAFAFB, accent #5865F2, ...). The app runs a DARK theme with the SAME accent #5865F2 and dark variants of the tokens. Reuse the guild's STRUCTURE, SIZING, RADIUS, TYPOGRAPHY and ANIMATION specs; keep the app's existing dark theme tokens (already aligned: accent strictly #5865F2).
- Every size is dp on a 360 dp wide phone; text in sp; line height 1.4 unless noted.

## Reusable component signatures from guild.v10

`SidePanel(title, sub, actions, runCard, content)` — wraps every side panel (Files, Search, Terminal, Logcat, Problems).
`TreeRow(n, onClick, onLongClick)` — file tree row: depth-based indent `start = (8 + 16*depth).dp`, rotating chevron, extension icon, name 13.5 sp, git letter.
`SettingRow(icon, color, title, onClick)` — settings category row.
`PanelRow(icon, color, title, sub, selected, onClick, trailing)` — project switcher row.
`RunCard(project, branch, state, onRun)` — build card shown inside panels.
`ScreenHeader(origin, title, onBack)` — full-screen header with back button.
`EmptyState(icon, title, sub, actionLabel, onAction)` — empty states.
`CcBottomSheet(title, onDismiss) { ... }` — sheets layer 4.
`CcConfirmDialog` — confirm before destructive actions.
`CcPopupMenu` anchored at row — file menu.
`CcTextField(value, onChange, modifier, hint, leadingIcon)` — text fields.
`ProgressLine(step, percent, label)` — build progress.
`Check(title, passText, run)` + `CheckResult` sealed — self-test checks.

## Navigation / state machine (guild `MainScreen.kt`, lines ~2227-2331)

Single `AppNav` owns:
- `project`, `sidebarOpen`, `sidebarTool`, `stack: List<Route>`, `sheet`, `openFiles`, `activeFile`.
- `SidebarHost(open, onOpenChange, keyboardVisible, sidebar, canScrollLeft, editor, overlays)`.
- Layer 3 full screens slide in from right `tween(220, FastOutSlowIn)` over everything including the sidebar.
- Layer 4 sheets: `Sheet.ProjectSwitcher`, `Sheet.FileMenu`, `Sheet.NewCommand`.
- Back order: sheet → pop stack → close sidebar → double-back exit.

Current app already implements this shape in `IdeScreen.kt`/`IdeRail.kt` but uses `sidebarTool: String?` instead of a `Tool` enum — align to the guild's `Tool` enum (`Files, Search, GitHub, Terminal, Logcat, Problems`) for consistency.

## Swipe (exact behaviour, guild lines 415-440) — already implemented in `IdeRail.kt`

- Start anywhere on editor; counts as "open sidebar" when mostly horizontal (|dx| > 2·|dy|) after 10 dp slop AND the editor under the finger cannot scroll further left (or finger on tab row/header/empty area).
- If code scrolled sideways, right swipe first scrolls the code back.
- Left 40 dp edge always opens sidebar.
- Vertical drags / scrolling never taken by sidebar; left drag while closed does nothing.
- Follow finger: `translationX = 300dp*progress`, left-corner radius `26dp*progress`, scrim `0.30*progress`.
- Release: open if `progress > 0.4` or right fling `> 800dp/s`; spring(damping 0.85, stiffness 400).
- Open: card ignores content taps; tap card closes. Disabled while keyboard up.

## Per-area target code notes

**Project switcher (guild lines 2490-2534):**
- `CcBottomSheet("Projects")`; real projects; current row has trailing check accent 18dp; relativeTime sub line; buttons New project / Open folder / Clone; empty state.

**File tree (guild lines 2760-2848):**
- `SidePanel("Files", project?.name, actions={New file, New folder}, runCard=RunCard(...))`.
- Filter field "Filter files"; empty state; `TreeRow` per node; long-press → `Sheet.FileMenu(path)`.
- File menu: New file, New folder, Rename, Duplicate, Delete (red, confirm), Copy path.
- `fileIconFor(n)` by real extension; dir = folder yellow; code files purple; else blue.

**Search (guild lines 2850-2920):**
- Type after 300 ms pause; chips match case/whole word/regex; include/exclude path fields; results grouped by file; replace toggle shows 2nd field + Replace all.

**Terminal session (guild ~3085-3148):**
- Real shell; input field + send button (44 dp accent, icon 18dp); key row Tab/Ctrl/Esc/arrows; saved commands typed at prompt.

**New command (guild `Sheet.NewCommand` → rebuilt as full page mock 24):**
- Name optional, Command mono, Folder read-only cwd, single Save command.

**Logcat (guild line 3212-3217):** real device log, level chips, time/level/tag/message mono 10.5 sp.

**Problems (guild line 310 area + current rebuild):** real errors/warnings grouped by file; tap row opens file at line + closes sidebar.

**Settings pages (guild lines 3219-3268):** categories → pages; header "Settings <Category>"; toggles write to DataStore at once; action rows confirm via CcConfirmDialog; pages list General…Developer.

**Developer / Self-test (guild lines 3521-3537):** summary card "N passed · M to check" + time + Run again; check rows green tick/amber triangle/red cross + one-sentence result; Share report; 16 real checks; sealed `CheckResult { Pass, Warn(msg), Fail(msg) }`.

**Project tile colours:** per-project colour from set #2563EB, #16A34A, #EA580C, #0E7490, #7C3AED, #BE185D; white letter weight 800.

## Data rule (guild lines 3820-3859)

Everything shown must be REAL: real project folder, real git status/log/diff, real signed-in account, real build, real log stream, real shells/saved commands, real settings, real project config, real templates. If a value does not exist yet, show the empty state — never placeholders/demo/fake.

## Files that contain reusable current code (to adapt, not rewrite from scratch)

- `ui/ide/IdeRail.kt` — swipe + rail (already correct).
- `ui/ide/IdeTerminal.kt` + `TerminalSession.kt` — terminal + commands + new command page (just rebuilt).
- `ui/ide/IdeSidePanels.kt` — Problems panel (rebuilt), Logcat.
- `ui/ProjectsSheet.kt` — project switcher (needs badge/sub-line/branch per File 1).
- `ui/settings/*.kt` — settings pages (per-page mock comparison pending).
- `ui/ide/IdeScreen.kt` — navigation shell, Files/Search panels.

## What remains before implementation is "complete"

1. Project avatar: badge BottomEnd, coloured `type · branch · status` sub line, real `.git/HEAD` branch (started; needs wiring check).
2. Per-page settings mock comparison (screens 33-44) — verify row icons, colours, values against target.
3. GitHub / History / Commit / Diff detail pass (screens 14-20) — verify real data + tokens.
4. Bump `closecodeBuild`→58 / `closecodeTests`→1469 (done) and rebuild APK (done: `closecode-0.58.1469-debug.apk`).


---

## ADDENDUM — guild.v10 code excerpts verified per area (with v10 image page cross-checks)

Terminal session (guild 3088-3142, cross-check PDF p64/23):
- `TerminalSessionScreen(origin, id, name, onBack)`; output LazyColumn mono 12 sp, err=red, scroll-to-end on line append.
- Key row = 40 dp LazyRow of 32 dp r9 field chips: Tab/Esc/Ctrl/↑/↓/←/→ (send escape sequences).
- Input row: mono CcTextField "Type a command" + 44 dp r13 accent send button (icon 18).
- PDF p64 additionally shows chat bubbles with timestamps + session chips + quick-command chips: keep real PTY output but render each line with time prefix like shell/you rows per the mock; guild says lines come from vm.terminal.output(id) — real transcript is required.

Run screen (guild 3161-3184, PDF p82/82-page): ProgressLine + steps spinner→tick/cross; Done→push BuildFinished.

Switcher (guild 2490-2534, PDF p35): CcBottomSheet rows PanelRow(icon,color,title,relativeTime,selected,check trailing) + New project/Open folder/Clone.

Files (guild 2760-2848, PDF p36/37): SidePanel actions New file/New folder; CcTextField filter; rows TreeRow depth-indent (8+16*depth), rotating chevron 120ms, icon by ext, 13.5sp name, git letter amber/green/red; long-press FileMenu sheet: New file/New folder/Rename/Duplicate/Delete(red)/Copy path.

Settings (guild 3228-3268, PDF p73): SettingsScreen categories list; SettingRow(icon,color,title) push pages; toggles write DataStore at once; CcConfirmDialog for Clear cache/Delete build tools/Crash test.

Self-test (guild 3519-3539, PDF p88): CheckResult sealed Pass/Warn/Fail; 16 real checks; summary + Run again + Share report.

Design numbers token tables (guild 3881-3999): light hexes; dark mappings already in app theme; accent #5865F2 only; Inter + JetBrains Mono; hero 33/800 etc.

Adjust implementation notes:
- current settings root "Build and Toochain" wording -> "Build and Toolchain" (typo fix).
- Appearance "Code size" default 12 -> 14 (matches p65/40 + editor 14 rule).
- Commit-detail files lists: real per-file +a -r counts (no fake +0 -0).
- Diff (p81/21): Discard red + Stage commit accent footer actions.
- History rows show "N file changes"; date as relative ("Oct 22"? target shows "Oct 02 ago · 1MB" — keep relative time wording).
- Signing pages 47/48/49 (p70/78/87) + Project settings SIGNING group (p86) + Debug key Ready chip.
- New: Icons manager feature (p55/59/62/63/69/72/85/86) deferred until the restyle is finished (guild 3541-3574).
