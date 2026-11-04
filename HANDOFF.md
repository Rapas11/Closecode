# Handoff note — CloseCode mockup restyle

**Repo:** `C:\Users\HomePC\Downloads\closecode apk\closecode`  
**Branch:** `main` (pushed to GitHub `origin/main`)  
**Last commit:** `f2be5af` — Screens 10–22 sidebar panels  
**Build:** `closecodeBuild = 47` (`app/build.gradle.kts`)

---

## Source of truth

- `CloseCode-developer-guide-by-screen.docx` (repo root) — screens 01–57, light + dark.
- Extracted spec text: `C:\Users\HomePC\AppData\Local\Temp\ccbs_docx_text.txt`
- Mockup PNGs: `C:\Users\HomePC\AppData\Local\Temp\ccbs_docx\word\media\*.png` (720 px wide = 360 dp; divide pixels by 2 for dp)

**Rule:** Read only the docx. Mockups are the law — exact spacing, sizes, icons, colours, corners, item order, text. No inventing, no Material defaults where the doc gives a value.

---

## Where we are (done + pushed)

| Screens | What | Commit |
|---------|------|--------|
| 01 | Welcome | `e06eeef` |
| 02 | Start a project | `49ec299` |
| 03 | Switch project | `bf66300` |
| 04 | Clone a repository | `ef22aeb` |
| 05 | Cloning progress | `e0363ef` |
| 06 | First-time setup | `b4f1324` |
| 07–08 | Editor + keyboard overlay | `124de5d` |
| 09 | Swipe sidebar (shell) | `ebfbdf9` |
| Build | closecodeBuild 46→47, ROADMAP updated | `1a2338c` |
| 10–22 | Sidebar panel layouts (Files, Search, GitHub x2, Terminal) | `f2be5af` |

**Next to do:** Wire `Panels.kt` into `SidebarHost` in `IdeScreen.kt` so the rail switches between Files/Search/GitHub/Terminal panels, then screens 23–30.

---

## Key files

| File | Purpose |
|------|---------|
| `ui/theme/CcTheme.kt` | Colour + type tokens — read from here, never loose hex |
| `ui/theme/CcSyntax.kt` | Screen 07 syntax colours |
| `ui/icons/CcIcons.kt` | `CcIcon(name, tint, size, strokeWidth)` — raw SVG path map |
| `ui/components/States.kt` | `EmptyState`, `LoadingList`, `ErrorCard`, `ProgressLine` |
| `ui/components/Shared.kt` | `MenuEntry`, `ChangeBadge`, `ChangedFileRow`, `SecLabel` |
| `ui/components/Header.kt` | `HeaderAction`, `ScreenHeader` |
| `ui/components/Basics.kt` | `OneLine`, `PrimaryButton`, `SecondaryButton`, `CcTextField`, `CcChip`, `CcToggle` |
| `ui/ide/Sidebar.kt` | `SidebarHost`, `ToolRail`, `SidePanel`, `RunCard` |
| `ui/ide/Panels.kt` | `FilesPanel`, `SearchPanel`, `GitHubPanel`, `GitHubSignedOutPanel`, `TerminalPanel`, `FileMenuOverlay`, `SearchOptionsOverlay` + data classes |
| `ui/ide/IdeScreen.kt` | Editor + sidebar wiring (screen 09 partially wired) |
| `ui/onboarding/OnboardingScreen.kt` | Screen 06 |
| `ui/screens/CloningScreen.kt` | Screen 05 |
| `ui/StartProjectSheet.kt` | Screen 02 |
| `ui/ProjectSwitcherSheet.kt` | Screen 03 |
| `ui/screens/CloneScreen.kt` | Screen 04 |
| `ui/GitHubCloneScreen.kt` | Clone flow delegate |
| `ROADMAP.md` | Live screen status tracker (bottom section) |

**Import tokens** — every screen must read colours/sizes from `CcTheme.colors` / `CcType` / `CcShape`. Never hard-code hex or dp values that the spec defines.

---

## Conventions (non-negotiable)

1. **One commit per screen** — never bundle multiple screens in one commit.
2. **Suite green** — run `.\gradlew.bat :app:testDebugUnitTest` before committing; 0 failures.
3. **Bump `closecodeBuild`** in `app/build.gradle.kts` per batch (currently 47).
4. **Build + install APK** after each batch (`.\gradlew.bat :app:assembleDebug`).
5. **No force-push.**
6. **Never commit** `real opencode open pc/` or `test-output*.txt`.
7. **Real data only** — every list from real stores; empty/loading/error states everywhere.
8. **OpenCode must not be touched** (visual only if ever).
9. **No new features** beyond the Icon manager (screens 52–57).
10. **Work in batches of 5 screens per cycle.**

**Gradle:** `$env:JAVA_HOME="$env:LOCALAPPDATA\Jdks\jdk-17"` then `.\gradlew.bat :app:compileDebugKotlin` / `:app:testDebugUnitTest` / `:app:assembleDebug`.

---

## Screen 09 sidebar wiring (what remains in IdeScreen.kt)

In `IdeScreen.kt`, the `SidebarHost.sidebar` lambda currently renders a placeholder. Replace it with a `when (selectedTool)` that renders:

- `"files"` → `FilesPanel(...)` (screen 10)
- `"search"` → `SearchPanel(...)` (screen 12)
- `"github"` → connected → `GitHubPanel(...)` (screen 14), not connected → `GitHubSignedOutPanel(...)` (screen 15)
- `"terminal"` → `TerminalPanel(...)` (screen 22)
- `"problems"` → (screen 31, not yet built)
- `"settings"` → `onOpenSettings()`

The rail badge counts come from real git status + problem counts. `selectedTool` state lives in `IdeScreen.kt`.

---

## Spec screens 23–30 (next batch)

Read these sections from `ccbs_docx_text.txt` (search for the screen title):

| Screen | Title | Key elements |
|--------|-------|--------------|
| 23 | Terminal session | Chat-style log, shell tabs, key row, input bar, send |
| 24 | New command | Saved commands sheet |
| 25 | Run / build | Build panel, progress, output |
| 26 | Build finished | Success/failure state, APK output |
| 27 | Website preview | Preview panel, device frame |
| 28 | Console | Console output |
| 29 | Compose previews | Preview gallery |
| 30 | Logcat / Problems | Log list, problem list |

## Spec screens 31–44 (batch after 23–30)

| Screen | Title | Key elements |
|--------|-------|--------------|
| 31 | Settings list | Sections, rows, toggles, chevrons, EmptyState/LoadingList/ErrorCard |
| 32 | Settings: Storage | Storage info, clear cache |
| 33 | Settings: Editor | Font size, tab size, line numbers, rainbow brackets |
| 34 | Settings: Theme | Light/dark/system toggle |
| 35 | Settings: AI | OpenCode settings |
| 36 | Settings: Terminal | Shell tools, saved commands |
| 37 | Settings: GitHub | Account connect/disconnect |
| 38 | Settings: Build | Build tools download |
| 39 | Settings: Notifications | Notification settings |
| 40 | Settings: About | Version, app info |
| 41–44 | Settings pages | Individual setting pages with real data |

## Spec screens 45–51 (batch after 31–44)

| Screen | Title | Key elements |
|--------|-------|--------------|
| 45 | Project settings | Project-specific config |
| 46 | App icon | Icon selection/preview |
| 47 | Keys / Release key ready | Signing keys |
| 48 | Create key | Create new signing key |
| 49 | Import key | Import existing key |
| 50 | Import key | Import existing key |
| 51 | Self-test | Self-test results |

## Spec screens 52–57 (Icon manager — new feature)

| Screen | Title | Key elements |
|--------|-------|--------------|
| 52 | Icons in this project | Icon list, browse |
| 53 | Icon groups | Grouped icons |
| 54 | Icons: Browse | Browse all icons |
| 55 | Icons: getting a pack | Download icon packs |
| 56 | Icon detail | Individual icon view |
| 57 | Icon manager summary | Overview |

Each screen has a `Code.` block in the spec with exact composable signatures and numbers. Follow them exactly.

---

## Forbidden

- Do not read or use any file other than the docx as the design source.
- Do not copy AndroidIDE source/assets.
- Do not modify OpenCode code.
- Do not commit `real opencode open pc/` or `test-output*.txt`.
- Do not add features beyond screens 01–57 + Icon manager (52–57).

---

If anything is unclear, re-read the corresponding section in `ccbs_docx_text.txt` before writing code. Mockups are the law.
