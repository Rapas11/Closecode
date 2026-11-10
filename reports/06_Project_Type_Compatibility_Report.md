# CloseCode — Project-Type Compatibility Report

**Source:** `check.me` PART 7 / PART 12 Report 6.  
**Method:** Read-only source audit.

## Project-type model

| Concept | Definition | File:line |
|---------|-----------|-----------|
| `TemplateTab` enum | `Web("Websites","Website")`, `App("Android apps","Android app")` | `ui/model/Templates.kt:5-8` |
| `ProjectRecord.tab` | `TemplateTab` of the project | `ui/project/ProjectRepository.kt:58` |
| `ProjectRecord.imported` | `Boolean`; true for cloned/imported projects (config read from files, not record) | `ui/project/ProjectRepository.kt:73` |
| `SelectedTemplate` | `tab` + `index` | `ui/model/Templates.kt:19` |
| Template trees | `projectTree()` emits Android spine (`build.gradle.kts`, `AndroidManifest.xml`, `app/src/main/...`) for `App`; `index.html`/CSS/JS spine for `Web` | `ui/model/Templates.kt:81-134` |
| Package source | `!imported && tab == App` → `record.config.packageName`; imported/other → read from files/AndroidManifest | `ui/project/ProjectRepository.kt:276-289` |

## Android-only vs website-only features

| Feature | Android (`App`) | Website (`Web`) | Notes |
|---------|----------------|----------------|-------|
| SDK Manager (Android platforms/aapt2) | ✅ | ❌ | `SettingsModel.kt:336-396` shows for all |
| Full Gradle (JDK 17 + Gradle) | ✅ | ❌ | `FULL_GRADLE` action has no row but page exists |
| Native tools (NDK/CMake) | ✅ | ❌ | `NATIVE_TOOLS` action has no row but page exists |
| Delete build tools | ✅ | ❌ | `DELETE_BUILD_TOOLS` row shown for all |
| Project settings: MinSdk/TargetSdk/CompileSdk, package, signing, app icon | ✅ | ❌ | `IdeProjectSettingsSheet` shows Android fields for all projects |
| Build / Run (APK) | ✅ | ❌ | `ProjectCheck.kt` hardcodes Android structure for every project |
| Shell tools (git, curl, Python, Node, zip, jq) | ✅ | ✅ | Correctly shown for both |
| OpenCode runtime / provider key | ✅ | ✅ | Correctly shown for both |
| Git/GitHub, editor, appearance, storage, security | ✅ | ✅ | Correctly shown for both |

## Where Android-only tools are shown for website projects

1. **App Settings "Build & Toolchain" section** — `SettingsModel.kt:336-396` lists `SDK_MANAGER`, `FULL_GRADLE`, `NATIVE_TOOLS`, `DELETE_BUILD_TOOLS` with no project filter. `SettingsScreen` is app-level: `MainActivity.kt:293-303` invokes it with **no project/tab parameter**. `IdeScreen` opens it via `onOpenSettings` → `nav.navigate("settings")` (`MainActivity.kt:324-326`) with no project context.
2. **Project settings sheet (screen 45)** — `IdeProjectSettingsSheet(imported, ...)` (`IdeProjectSettings.kt:64-84`) renders Android "Minimum SDK / Target SDK / Compile SDK / Package name / Signing / App icon" rows. Invoked at `IdeScreen.kt:3062-3093` with `imported = record?.imported == true` but **no `tab`**. A website project shows Android SDK fields.
3. **Entire `ui/ide` layer is project-type blind** — `grep TemplateTab` over `app/src/main/java/com/closecode/ui/ide` returns **zero matches**. `IdeScreen`, `IdeProjectSettings`, `IdeRunSheet`, `IdeRail`, `IdePanels`, `IdeMoreOverlay` never inspect `record.tab`.
4. **`ProjectCheck.check` hardcodes Android structure for all projects** — `ProjectCheck.kt:99-358` unconditionally requires `app/src/main/AndroidManifest.xml` (:105-131), `app/build.gradle.kts` (:134-144), and `.kt`/`.java` sources under `app/src/main` (:340-349). No `TemplateTab` parameter.

## Where opening them can crash / backend does not enforce type

**Run path:** `canRun = projectErrors == 0` (`IdeScreen.kt:3029`) uses the **Kotlin analyzer error count** (`projectErrors`, set at `:884/:1635`), **not** the `ProjectCheck` build report. For a website project with no `.kt` files, `sources.isEmpty()` → `projectErrors = 0` (`IdeScreen.kt:1606-1611`), so `canRun` is `true` and the Run button is **enabled** for a website project. Tapping Run calls `runProject()` (`:2121`) → `building = true` → `BuildEngine.build` → `BuildSetupFactory.create` (`:1678`) which reads Android build files via `GradleReader.read` and `ProjectCheck.check` — both assume Android project structure. The build is `Blocked` (not a clean rejection), and downstream code assuming `AndroidManifest.xml`/`build.gradle.kts`/`.kt` sources can crash. **This is the crash vector for website projects.**

## Root causes

1. **Zero project-type enforcement in `ui/ide`.** No `TemplateTab` reference anywhere in the IDE directory.
2. **Settings has no project context.** `SettingsScreen` opened without the open project or its `TemplateTab`.
3. **`ProjectCheck.check` applies Android file requirements to websites** with no type parameter.
4. **Build engine/settings/save paths accept a project but never inspect `record.tab == TemplateTab.App`** before Android-specific work.

## Required corrections

- Thread the open project's `TemplateTab` (and `imported`) into `SettingsScreen` (`MainActivity.kt:293`) and `IdeProjectSettingsSheet` (`IdeProjectSettings.kt:64`, invoked `IdeScreen.kt:3062`), so Android-only settings/panels are gated at the navigation destination.
- Add a project-type-aware `ProjectCheck` overload taking `TemplateTab`: for `Web`, skip `AndroidManifest.xml`/`app/build.gradle.kts`/`.kt` source requirements and report website-appropriate findings; for `App`, keep current behavior.
- Guard `BuildSetupFactory.create` / `BuildEngine.build` / `saveProjectSettings` / `applySdks`/`applyPackage` by `record.tab == TemplateTab.App` (and `!imported` where applicable); reject unsupported navigation with a toast/dialog explanation rather than attempting an Android operation on a website project.
- Make the Run button's `canRun` reflect the type-aware build check (use `projectReport.errors.isEmpty()` for Android projects), not just `projectErrors == 0`.
