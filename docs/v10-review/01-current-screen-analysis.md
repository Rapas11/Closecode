# File 1 — Current Screen Analysis (V10 restyle audit)

Areas reviewed: Project Avatar, File Explorer, Search, Terminal, Logcat, Problems, Developer, Project Settings.
Sources: current source tree (branch `opencode-v2`), current screens `cur-01..32` (build 57), prior restyle work already merged.

## Navigation shell & state machine (covers all panels)

Current state:
- `IdeScreen.kt` holds navigation: `sidebarOpen`, `sidebarTool` (`String?`), `explorerOpen`, full-screen `stack`, sheets.
- Rail tools are compared as strings ("Files", "Search", "Terminal", ...), NOT a shared `Tool` enum.
- Sidebar is opened from the editor via swipe (Implementations below) and from the rail; tap a rail tool sets `sidebarTool` and keeps the sidebar open; tap a file closes the sidebar (`nav.closeSidebar()`).
- `setStatus` writes project status strings ("main · Build ok", "main · N problems", "… Build failed").
- System Back handling: sheets first, then stack pop, then sidebar close, then double-back-to-exit. Headers call the same `onBack()`.

Already matches guild.v10:
- `IdeRail.kt` `IdeWorkspace`: swipe right from anywhere on editor (Initial pass), mostly-horizontal rule, 40 dp left edge always opens, `canScrollLeft` hand-back to code, follow-finger with `translationX = 300dp*progress`, left-corner radius `26dp*progress`, scrim `0.30*progress`, release rule `progress > 0.4` or right fling `> 800dp/s`, spring(damping 0.85, stiffness 400), tap dimmed card to close, disabled while keyboard is up.

## 1. Project Avatar + project switcher (Screen 03)

File: `ui/ProjectsSheet.kt` (current bottom sheet, NOT the guild's `CcBottomSheet`); called from `HeroScreen.kt` and `IdeScreen.kt`.

Current behaviour:
- Bottom sheet over Welcome or Files; lists recent projects; each row: letter tile (42 dp), name, sub line = `project.status.ifBlank { projectAge(lastOpened) }`, badge.
- Badge drawn at **TopEnd** with offset(3, -2) — guild wants BottomEnd offset(3,3).
- Sub line shows raw status text (e.g. "main · Build ok") uncoloured; guild wants `type · branch · status` with status coloured.
- No `branchOf`/real `.git/HEAD` branch; uses stored `status` string only.
- Open row shows a check icon (20 dp) at the right.

Needs correction:
- Badge → BottomEnd offset(3, 3), red/orange count, hidden at 0.
- Sub line → `type · branch · status`, status coloured (Build failed red / N problems amber / Build ok green / else muted), one line ellipsis.
- Real branch from `.git/HEAD` via `ProjectRepository.headBranch(slug)`, wired through `branchOf`.

## 2. File Explorer (Files panel) — screen 11

File: `IdeScreen.kt` `FilesPanel` (~2760 area logic in guild; current built inline).

Current behaviour (restyled earlier, cur-14):
- Side panel "Files" with header actions New file / New folder.
- Filter text field ("Filter files").
- `LazyVerticalGrid` 2 columns of field-bg tiles (36 dp) with ellipsis file names + git letter.
- Empty state when no rows.
- Long-press → FileMenu sheet.

Already matches guild.v10 closely:
- Indent per depth, chevron rotate, file icons by real extension, one-line name 13.5 sp, git letter M=amber/A=green/else=red.
- Tap file opens + closes sidebar; long-press → FileMenu sheet.

Remaining check vs guild:
- Guild tree row padding is `start = (8 + 16*depth).dp`; current uses a grid — verify spacing/size matches mock 14.
- Run card inside Files panel (RunCard) present.

## 3. Search (panel) — screens 12/13

File: `IdeScreen.kt` `SearchPanel`.

Current behaviour:
- Query field, options chips (match case, whole word, regex, include/exclude paths).
- Results grouped by file; tap opens file at line + closes sidebar.
- Loading/empty/failed states.

Status: already restyled to match mock. No open issues identified this session.

## 4. Terminal (panel + session + New command)

Files: `ui/ide/IdeTerminal.kt`, `ui/ide/TerminalSession.kt`, `ui/ide/TerminalKeyBar.kt`.

Current behaviour (updated this session):
- Panel: list of sessions, "New session", Run card.
- Full session screen: real PTY shell, echo as prompt+command, cursor, Tab-completion popup, key row (ESC/TAB/CTRL/ALT/|/~// /↑/↓ — matches guild part-2 reference).
- Commands panel: saved commands as 2-column field tiles; tap types at prompt; long-press removes; "+ Add command".

**New command — rebuilt this round to mock 24 (full page):**
- Top bar: 38×38 r12 chevron close + "New command" title.
- Fields: Name (optional), Command (mono, hint "git log --oneline"), Folder (read-only, real session cwd).
- Single accent "Save command" button, disabled until command non-blank.
- Back closes; sheet replaced by full page.
- Storage: alias entry `name\u0000command`; card shows name, prompt receives command; `splitAlias()` helper added to TerminalSession.kt.

Already matches guild.v10:
- Key row nine keys in spec order; typing sends to shell; Enter runs.
- Saved commands are real strings from DataStore.

## 5. Logcat (panel) — screens 30/31

File: `ui/ide/IdeSidePanels.kt` `IdeLogcatSidePanel` (and `IdeGitPanel.kt` relativeTime).

Current behaviour:
- Live device log, filter chips by level.
- Each line: time, level letter (coloured), tag, message, mono 10.5 sp.

Already matches guild.v10. `relativeTime` grammar fixed this session ("1 hour ago"/"1 day ago", "hours ago").

## 6. Problems (panel) — screen 31

File: `ui/ide/IdeSidePanels.kt` `IdeProblemsSidePanel`.

**Rewritten this session:**
- Subtitle "N in M files".
- Filter chips: All N / Errors N / Warn N (accent-selected, new `ProblemChip`).
- Per-file groups with accent `<>` header (`CcIcons.Logo`).
- 30 dp r9 red/amber icon tiles (`CcIcons.Cross`/`CcIcons.Warning` on dangerBg/warningBg).
- Mono accent `file:line:column` sub, message maxLines/minLines 1 lineHeight 16.
- Empty: "No problems found".
- `SideCountPill` removed.

Already matches mock 31.

## 7. Developer (full screen) — screen ~44/45

File: `ui/settings/SettingsDeveloperPage.kt`.

Current behaviour:
- Self-test full screen: header "Developer" (bold) + "Self-test" (muted), summary card "N passed · M to check" + time + Run again button, list of checks with green tick / amber triangle / red cross, Share report at bottom.
- 16 real checks (Pass/Warn/Fail), no invented checks.

Matches guild.v10 screen 51. No open issues identified this session.

## 8. Project Settings (full screen + pages)

Files: `ui/settings/Settings*.kt`, `ui/ide/IdeProjectSettings.kt`.

Current behaviour (updated this session):
- Settings root: categories (General, Editor, Code intelligence, Build and toolchain, Terminal, Git and GitHub, Appearance, Storage, Security, About, Developer).
- Each category a full screen; header "Settings <Category>" with back button.
- Editor page: font size default **14**, auto-close brackets on, tile icon colours section-aware (terminal → green, editor → purple).
- Terminal page: font size 12, "4 commands" subtitle no "Open".
- About: name wrap fixed (maxLines 1), footer = BuildInfo.label auto from versionCode/versionName.
- "Manage saved commands" row subtitle "${count} commands", value "".

Already matches mocks 34/37. Remaining: compare each settings page to its target mock (33–44) for exact row/icon/token alignment.

## Summary of current-vs-target gaps remaining

| Area | Status |
|------|--------|
| Navigation shell / swipe | ✓ matches guild |
| Project avatar + switcher | ⚠ badge position, coloured sub line, real branch |
| File Explorer | ✓ matches (verify grid spacing) |
| Search | ✓ matches |
| Terminal / session / New command | ✓ rebuilt to mock 24 |
| Logcat | ✓ matches |
| Problems | ✓ rebuilt to mock 31 |
| Developer | ✓ matches screen 51 |
| Project Settings | ⚠ per-page mock comparison pending |


---

## ADDENDUM — verified page-by-page against the PDF (pages 1-32 = current build 57)

Page map (current): 01 welcome · 02 projects sheet · 03 start a project · 04 GitHub changes · 05 commits 477 · 06 editor gradle-wrapper · 07 terminal session (raw PTY) · 08 terminal shells+commands · 09 terminal panel · 10 logcat · 11 new command SHEET · 12 saved commands · 13 project settings · 14 files panel · 15 problems (empty) · 16 settings editor (font 12, brackets off) · 17 settings root · 18 editor+developer sheet · 19/24 settings terminal (font 13, "7 saved commands"+Open, pipe ON) · 20 build/toolchain (all Ready) · 21 settings code intelligence (suggest/prob off, format on) · 22 OpenCode AI · 23 git/github (Sign in, authors Not set) · 25 security · 26 storage (Projects 491 MB…) · 27 appearance (Code size 12, Default light) · 28 history commit detail (A rows +0 -0) · 29 GitHub panel w/ history See all · 30 About ("CloseCo de" wrap) · 31 diff view · 32 commit detail M rows.

Findings needing correction (vs target):
1. Welcome: sub lines already "type · text" but status not coloured (Build failed prints plain); badge = small dot bottom-right ✓ but needs red/orange COUNT badge per target row style; "N projects" count already at right ✓.
2. Projects sheet (02 vs target 35/03): rows show bare status ("Build failed") not coloured; sub line needs type · branch · status with colours; bottom buttons "+ New project" + "Templates" ✓.
3. Files panel (14 vs 37/11): very close (title+2 actions, filter, OPEN tabs, FILES count, tree, indents, selected chip). Gaps: tree row git letter/current uses "U" col at far right edge (target: letters M/amber etc. to the right of name, tighter); rename flow exists; run card present ✓ but sub shows "% main · not built" (target "Blank app / Kotlin · 1 min ago" style sub = branch · state words).
4. Terminal session (07 vs 23/62/64 target): BIG GAP — target is a chat-style transcript: session chips (Shell 1 accent-selected, Shell 2, +), "N:MM pm you …" / "N:MM pm shell …" bubbles with timestamps, quick-command chips row, key row ESC { } ( ) [ ] " ' ; = →, bottom input "Type a command…" + accent send circle. Current is raw PTY log + key row ESC/TAB/CTRL/ALT/|/~//.
5. Terminal panel (09 vs 22/80 target): close; target Shell row sub "4 files · running go" — needs per-session state words; saved-command grid ✓; + Add command dashed ✓ (now opens full page ✓ matching 75).
6. New command (11 current = SHEET vs 75 target = FULL PAGE): the sheet form (single field, Cancel/Create) is the old design; full page rebuilt this session ✓ (Name/Command/Folder/Save command); target header reads "Terminal | New command"; ours "New command" — acceptable, keep chevron per guild ScreenHeader pattern.
7. Logcat (10 vs 57/30 target): target chips = All (accent pill), Info, Warn, Error (+First 1?) and rows = time + colored level letter + tag + message mono; current chips All I V D W E ✓ close; empty state "Waiting for logs." ok.
8. Problems (15 vs 84/31 target): current empty state fine; the CHIPS row in target: "All 5" accent, "Errors 2", "Warn 1"; rows show message (item title) + "file · line:col" sub; per-file grouping NOT shown in target — rows are flat, grouped implicitly by file order? Target row sub includes file+location. Our rebuilt panel matches chips; verify flat-row style vs per-file-group → target shows per-ROW file sub, not group headers. Adjust: keep file header groups only if sub does not repeat file.
9. Project settings (13 vs 86/45 target): current shows "—" placeholders + "This build does not pass them to this screen yet." note. Target: real values rows with icon tiles: App name (word), Package name (accent mono value), App icon, NEW icons row "Icons / Project icon, project and icon packs" >, Version name 1.0 >, Version code 1 >, Minimum SDK 24 >, Target SDK 34 >, SIGNING Debug key Ready. Must show REAL values and add SIGNING group + Icons row.
10. Settings pages targets (34/37/38/39/40/41/42/50/44/36): editor font 14, word wrap ON, auto-close brackets ON (current: 12/off/off → partially fixed: font 14 done, wrap/brackets state must match saved settings, not mock state); terminal font 12/13 current=13 → target 37 shows 12; "Manage saved commands" sub "7 saved commands" value Open (current built "N commands" no Open — target 75? shows Open > on p19 current, mock 37 shows no Open; keep "Open >" per target p75/19? — mock 37 (dark1) had no Open; PDF p58 target shows "7 saved commands ℹ"?; keep value Open > as in target 19).
11. Build/toolchain target 38: shell tools show REAL states (Ready ✓ / Not installed amber with inline download progress 66% accent bar + 2.2 MB/s + Pause/Skip for now); current shows all Ready — keep real, add in-row download progress UI.
12. Appearance target 40: THEME cards; ACCENT 6 dots; Code font/size/syntax; current ✓ (code size 12 → 14? target 40 shows 14 for editor? mock 34 is editor font 14; appearance Code size target p65 shows 14) — wait p65 target appearance shows "Code size 14"? p27 current shows 12. Target p65 value "14". Fix default 12 → 14 for Code size too (Appearance page).
13. History/Commit/Diff (28/29/31/32 vs 19/20/21/04): current commit rows show "+0 -0" everywhere (fake sums) — target rows show real per-file +/- counts (e.g. M OpenCode.kt +31 -8 with red -13) and real commit body text + date "Oct 22" instead of "1 days ago"; target history rows: message · sha · date · "N file changes" (current hides counts); diff target 21 has Discard (red) + Stage commit (accent) buttons.
14. GitHub panel (04/29 vs 14/40/45): target CHANGES rows show M/A/D letter tiles with per-change sub ("1) add @Override…"), commit row buttons "Commit" accent + "Commit and push" secondary; author line "JosePedro92 · main" with avatar; current shows Pull/Push + green tiles list ✓ but rows lack letters-sub; finish styling.
15. GitHub not connected (43/15) + sign in (46/16): device code card "WDJ8-4K2P" + Copy code + Open github.com + "Waiting for you to approve…" + Cancel + "Code expires in 15 min".
16. Run/build (25/26 vs 82/61): Debug/Release chips, "Open app after install" toggle, install log, progress %, Cancel + Run buttons; finished: green check circle "Built and installed / app-debug.apk · 52.3 MB · 14 s" + Open app + Save logs.
17. Website preview (27/79) + Console (28/89): address pill, Desktop/tablet chips, red error count badge, console errors red rows + logs green/blue, chips "Errors 1 / Warnings 1".
18. First-time setup (02/44): Setup title 33sp hero "Get up your build environment", cards Build tools Ready / Shell tools downloading 66% + Pause/Skip, Finish setup disabled grey at bottom.
19. Icon manager (46/52-57) NEW FEATURE — deferred per guild (build after restyle).
20. Signing (47/48/49/60): Debug/Release key cards, Create key chips (30 days/10 years/20 years), password fields, Import key file, Release-key Ready card with details + Backup/Replace + red "Remove from this phone" + warning strip.
21. About target 43: rows "CloseCode / Developed without IDE" blue C tile, Source code github.com/… >, Licences > — current ✓ after wrap fix; keep.
22. Settings root target 32: rows in ORDER General, Editor, Code intelligence, Build and Toolchain (fix typo "Toochain" in current p17!), Terminal, Git and GitHub, OpenCode AI, Appearance, Storage, Security, About; footer build info ✓.
