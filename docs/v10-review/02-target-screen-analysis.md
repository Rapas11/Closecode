# File 2 — Target Screen Analysis (V10 design target)

Sources: `guild.v10` (the designer's own implementation reference — treated as law), prior target mock renders (m01 welcome, m23 terminal session, m24 new command, m31 problems, m34 settings editor, m37 settings terminal). The target PDF `The current and target screens and target guilds.pdf` is image-only, so its content is taken from guild.v10 which the same author wrote as the implementation specification.

Navigation shell (applies to every panel)

- Sidebar = one surface, two parts side by side behind the editor card:
  - Rail: 66 dp wide, icons only. Tools in order: Files, Search, GitHub, Terminal, Logcat, Problems, then Settings at the bottom. Project avatar on top.
  - Panel: remaining 234 dp, rounded card, top-left AND top-right corners radius 26 (right corner is where the editor card starts). Shows the selected tool's content.
- Editor card slides right 300 dp on swipe, keeps a strip on the right edge, rounded left corners radius 26, dimmed. Tapping the card closes the sidebar. Sidebar has no own header.
- State machine (single source of truth): `project`, `sidebarOpen`, `sidebarTool`, `stack` (full screens), `sheet` (one at a time), `openFiles`, `activeFile`.
- Back order: sheet/dialog → pop full screen → close sidebar → double-back exit.
- Every full-screen header back button calls the same `onBack()` as system Back.
- No hamburger button; sidebar opens only by swipe.

1 Project avatar / project switcher (screen 03)
- Bottom sheet "Projects", opens when avatar tapped.
- Lists REAL projects; current one has a check.
- Each row: icon, name, relative sub line (last opened), trailing check for current.
- Buttons below: New project, Open folder, Clone.
- Empty state: "No projects yet / Start a project, clone one or open a folder."

2-9 Project management (clone, templates, welcome, start project) — restyled per same tokens.

10 Files panel (screen 11)
- Side panel "Files"; header actions New file / New folder.
- Filter field "Filter files".
- Real project tree; icon by real extension; indent `start = (8 + 16*depth).dp`; chevron rotates; one-line name 13.5 sp; git letter M=amber/A=green/else=red.
- Empty state "This folder is empty / Create a file or a folder."
- Tap file: open + close sidebar. Long-press: FileMenu sheet (New file, New folder, Rename, Duplicate, Delete-via-confirm, Copy path).
- Run card inside the panel.

11-13 Search (panel)
- Query field; chips match case / whole word / regex; include/exclude path fields; results grouped by file; tap opens file at line + closes sidebar; replace toggle shows second field + Replace all.

14-20 GitHub / Source control / History / Commit / Diff
- Clone a repository: URL (mono) + optional branch; real git clone → progress screen with real step text (Counting, Receiving, Resolving).
- History: commit list; tap commit → open it.
- Commit detail: files changed; tap file → diff.
- Diff: read only, diff token colours.

21-23 Terminal
- Terminal panel: list of sessions, new session, Run card.
- Terminal session (full screen): real shell; typing sends to shell; key row sends Tab/Ctrl/Esc/arrows; key row nine keys ESC/TAB/CTRL/ALT/|/~// /↑/↓.
- New command (screen 24): full page — Name + Command + Folder + single "Save command" button.

24 New command (full page) — target detail
- Header back chevron + "New command".
- Name field (optional), Command field (mono), Folder field (read-only, real cwd).
- One full-width accent "Save command" button; disabled until command entered.
- Tap saves; Back closes.

25-26 Run / Build finished
- Run screen starts real build; steps with spinner → green tick / red cross; progress bar real percent; Cancel stops.
- Build finished: Ready → green badge, time, Open app (green), Share. Failed → red badge, real error text, Problems button.

27-29 Website / Console / Compose previews — real data, empty states when none.

30 Logcat panel
- Real device log; level filter chips; each line time / level letter (coloured) / tag / message; mono 10.5 sp.

31 Problems panel
- Real errors/warnings grouped by file; tap row opens file at line + closes sidebar.

32-44 Settings pages (full screens)
- Settings rail (bottom) → categories screen → tapping a category pushes its page, header "Settings <Category>" + back.
- Pages: General, Editor, Code intelligence, Build and toolchain, Terminal, Git and GitHub, Appearance, Storage, Security, About, Developer.
- Every toggle/value writes to the real settings store at once (DataStore).
- Action rows (Clear cache, Delete build tools, Crash test) confirm via CcConfirmDialog first.
- Editor page: font size 14, auto-close brackets on.
- Terminal page: font size 12, "N commands" subtitle, no "Open".
- About: no wrapping of app name; footer = BuildInfo.label.
- Show only rows matching features the app has.

45+ Developer / Self-test / Icons
- Self-test full screen: header "Developer" bold + "Self-test" muted; summary card "N passed · M to check" + time + Run again; list of checks green tick / amber triangle / red cross + one-sentence result; Share report at bottom.
- 16 real checks; no invented checks.
- Icons feature (screens 52-57): new feature, build only after restyle; segmented control This project / Browse / Code; logo card radius 22; 84×84 icon radius 24; etc.

Design tokens (light theme as written in guild.v10; app runs dark — map accent #5865F2, keep dark theme tokens consistent):
- rail #E3E5EA, panel #F1F2F5, content #FAFAFB, field #E9EBEF, border #D5D8DE, divider #E3E5EA, text #1F2024, text2 #2E3036, muted #626772, faint #858A95, accent #5865F2, green #248046, amber #C98A0A, red #DA373C, blue #2F6FE0, purple #7B5CE0, yellow #D99A06, teal #1F9D6B, orange #D97706, pink #DB4C9B.
- dim rgba(30,35,50,0.30); shadow rgba(30,35,50,0.25) blur 30 y 12.
- Icon tile tint = icon colour at 13% opacity (alpha 22).
- Type: UI Inter, code/terminal JetBrains Mono. Hero 33sp/800; panel title 21-22sp/800; sheet title 19-20sp/800; screen title 16.5sp/700; item title 14.5sp/600; body 14sp; small 13-13.5sp; caption 12-12.5sp; section label 11sp/700 uppercase; code 11.5sp mono; terminal/logs 10.5-12sp mono.
- Spacing/shape: screen side padding 14-18; row padding 8×14; gap 8-12; radius tiles 13-15, buttons 12-16, cards 18-20, grouped settings 18.
- Animations: sidebar spring damping 0.85 stiffness 400; tool switch cross-fade 120ms; rail selected pill 160ms; full screen push 220ms FastOutSlowIn / pop 200ms; bottom sheet 250ms; popup 120ms; toggle 160ms.

Rule: "Shown in a picture must come from real data — no placeholder projects, demo files, sample repos, fake commits." Any screen not pictured must still be restyled with same colours/type/spacing/shapes/icons/navigation.


---

## ADDENDUM — verified from the target pages of the PDF (pages 33-89, image-only, rendered to /tmp/v10pdf/)

PDF page -> target scene number (from the page captions):
34=19 History · 35=03 Switch project · 36=11 Files menu · 37=11 Files panel · 38=12 Search · 39=09 Editor · 40=14 Source control · 41=08 Editor keyboard · 42=02 Start project · 43=15 GitHub not connected · 44=02 First-time setup · 45=14 GitHub panel · 46=16 GitHub sign-in · 47=16 Source control menu · 48=13 Search options · 49=01 Welcome · 50=04 Clone repo · 51=41 Settings storage · 52=05 Clone progress · 53=11 Rename tab · 54=44 Settings Developer · 55=55 Icon library · 56=50 Settings General · 57=30 Logcat · 58=37 Settings Terminal · 59=56 Icon detail · 60=48 Create key · 61=30 Run finished · 62=53 Icons empty · 63=46 App icon · 64=23 Terminal session · 65=40 Settings Appearance · 66=39 Settings Code intelligence · 67=29 Compose previews · 68=38 Settings Build/toolchain · 69=54? Icon download · 70=47 Signing · 71=36 Settings GitHub · 72=57 Icon compose · 73=32 Settings root · 74=42 Settings Security · 75=24 New command · 76=20 Commit detail · 77=34 Settings Editor · 78=48 Release key ready · 79=27 Website preview · 80=22 Terminal panel · 81=21 File diff · 82=25 Run build · 83=43 About · 84=31 Problems · 85=52 Icon project · 86=45 Project settings · 87=49 Signing import · 88=51 Self-test · 89=28 Console. Last page = pointer to the guide txt.

Per-area target corrections to apply (all numbers from the page captions above):

A. Project avatar / switcher (35): rows sub = "type · time" words, coloured status on welcome rows (49); badge bottom-right red/orange count; check on current row; footer buttons "+ New project" (accent) + "Templates".
B. Welcome (49): RECENT PROJECTS + accent "6 projects" count; three pill footer (Open source / On-device builds / Private by default).
C. Files (37/36/53): title + New file + more-menu; filter; OPEN tabs; FILES list; tree = folders with count ">"; long-press popup (New File/New Folder/Rename/Duplicate/Delete red); rename inline row with confirm/cancel; run card sub "branch · state".
D. Search (38/48): field + Case toggle + Replace; chips Match case/Whole word/Use regex; results grouped: file title row + per-match line with amber highlight of the match; tap opens editor.
E. Editor (39/41): title + player-task row with "Update All" accent button OR run; bracket-pair cursor highlight; keyboard Tab/{ [ ( " ' key row above keyboard.
F. Terminal session (64/23): CHAT-style transcript — chips Shell 1 (accent) Shell 2 +; "3:44 pm you *" / "3:44 pm shell *" bubble rows; quick chips; key row ESC { } ( ) [ ] " ' ; = ->; input "Type a command…" + accent send circle. KEEP real PTY: transcript contains real shell output lines (guild law).
G. Terminal panel (80/22): Shell rows with real state words ("4 files · running go", "idle"); saved commands grid; + Add command dashed (opens page 24 full screen).
H. New command (75/24): full page; Name/Command/Folder + "Save command" accent; caption "Name it so the command is easier to find later."
I. Source control (40/47): changes rows with M/A/D letter tiles + per-file sub ("11:03:PM · add @Override"), Commit (accent) + Commit and push (secondary); kebab menu: Pull/Push/Switch branch/History/Discard all changes(red).
J. History (34/19): rows "message / sha · date · N file changes +tag?"; commit detail (76/20): heading = real commit message, meta "Author · date · N files, +a -r", rows A/M/D/+counts, diff preview + "N unchanged lines".
K. Diff (81/21): mono diff rows red/green; footer actions Discard (red) + Stage commit (accent).
L. Problems (84/31): header sub "5 files · 2 errors · 1 warning"; chips All 5/Errors 2/Warn 1; flat rows: icon tile + message + "KotlinParser k · 4732:9" sub; run card under.
M. Logcat (57/30): chips Info/Warn/Err(+/First?); rows time · level letter coloured · tag · message mono 10.5; red error rows.
N. GitHub (43/15,46/16): not-connected = tile icon + "Connect GitHub" copy + accent "Sign in with GitHub" + "Or use a public repo instead"; sign-in = device code big mono (WDJ8-4K2P) + Copy code + Open github.com + "Waiting for you to approve…" + Cancel + expiry note.
O. Replace-all flow for Search; Case/Whole word/regex checkboxes.
P. Run/build (82/25,61/30): Build TYPE chips Debug(accent)/Release; "Open app after install" toggle; progress "Building… 63%" accent + Pause; install log lines; finished: green check circle "Built and installed / app-debug.apk · 52.3 MB · 14 s" + Open app (accent-green) + Save logs.
Q. Website preview (79/27) + Console (89/28): preview header with count badge; Desktop chips; white site canvas; Console: "Errors 1" red chip + "Warnings 1" accent chip + rows time · LEVEL · message · file:line.
R. Settings root (73/32): 11 rows w/ icon tiles, selected pill on current category; footer build line. Fix "Build and Toochain" -> "Build and Toolchain" wording.
S. Settings pages targets: General 56/50 (Default project location, Open last project ON, Auto-save Always-on ON, Search-guide, Performance overlay), Editor 77/34 (14 / wrap ON / line numbers ON / auto-save ON / brackets ON / tab 4), Terminal 58/37 (pipe toggle, Start folder Project root, font 12, saved commands sub+Open), Build 68/38 (real states + in-row download 66% + Pause/Skip), Code intel 66/39 (Suggestions ON, Show problems ON, Format on save OFF; languages Ready ✓), GitHub 71/36 (account name + Sign out red; author name/email), Appearance 65/40 (theme cards, accent dots, code font/size 14/syntax Default dark), Storage 51/41 (rows + Clear), Security 74/42 (App lock fingerprint off, usage data off, Saved sign-ins GitHub), About 83/43 (CloseCode / Developed without IDE + Source code + Licences), Developer 54/44 (Debug log, Self-test, Last crash, Share debug report, Clear build cache, Delete build tools, Crash test DANGER ZONE).
T. Signing (86/45 + 70/47,60/48,78/48!,87/49): Project settings SIGNING → Debug key Ready; Signing screen = Debug key card (sha rows), Release key card (Not yet amber → Ready green with full cert table), Create a new key accent + Import a key file; Create-key form (role/key name/alias/key-size chips 30 days/10 years/20 years/password/remember toggle); Release-ready card: Valid until/CN/O/OU/AKD / Valid 30 years, Backup + Replace buttons, red "Remove from this phone", amber warning strip "Back up this key now…".
U. Import key (87/49): KEY FILE row + browse, store password, key series rows, key password, "Same password as the key store…" note, accent "⬆ Import key".
V. Self-test (88/51): summary "15 passed · 1 to check · 20H ago?" + Run again accent; check rows with real sentences; bottom Share report accent.
W. Icon manager (63/46,55/55,56/59,62/53,69?,72/57,85/52) — NEW FEATURE; build AFTER restyle: This project/Browse/Code segmented; logo card; "ICONS IN THIS PROJECT · N" rows (tile+name+"Vector · N dp"); Add an SVG file; Browse=pack cards with Install/Get all + inline download; detail=Use this icon + colour dots + size 24/32/48 + Copy code + Save to project; compose-code tab with saved rows + Copy code for N.
X. First-time setup (44/02): hero Setup cards; Finish setup disabled until done.
Y. Clone (50/04,52/05): URL+Save in folder+Clone button; result rows (added/updated · time); progress: accent % bar + "Receiving objects 65%" + mono remote log + Cancel.
