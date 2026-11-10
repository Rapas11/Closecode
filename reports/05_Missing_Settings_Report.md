# CloseCode — Missing Settings Report

**Source:** `check.me` PART 6 / PART 12 Report 5.  
**Baseline:** `2fb35a4` / `ui-backup-20261104` and tag `v0.43.0-v1-final` vs `HEAD = 153fd14`.

## Settings that persist in `SettingsStore` but have NO UI row (inaccessible)

`SettingsStore.kt` still reads/writes these fields, but `SettingsModel.kt sections()` references none of them. Data survives; UI is gone.

| # | Setting (store field) | `SettingsStore.kt` line | Previous behavior (v0.43) | Current gap | Proposed placement |
|---|-----------------------|-------------------------|---------------------------|-------------|--------------------|
| 1 | `BoolSetting.AUTO_INDENT` | :13 | Editor > Text: "Auto indent" toggle | 0 references | Editor > Text |
| 2 | `BoolSetting.CODE_FOLDING` | :22 | Editor > Text: "Code folding" toggle | 0 references | Editor > Text |
| 3 | `BoolSetting.RAINBOW_BRACKETS` | :20 | Editor > Text: "Rainbow brackets" toggle | 0 references | Editor > Text |
| 4 | `BoolSetting.BRACKET_GUIDES` | :18 | Editor > Text: "Bracket guides" toggle | 0 references | Editor > Text |
| 5 | `BoolSetting.AUTO_IMPORT` | :23 | Code Intelligence: "Auto import" toggle | 0 references | Code Intelligence > Language help |
| 6 | `BoolSetting.PARAMETER_HINTS` | :24 | Code Intelligence: "Parameter hints" toggle | 0 references | Code Intelligence > Language help |
| 7 | `BoolSetting.QUICK_FIXES` | :25 | Code Intelligence: "Quick fixes" toggle | 0 references | Code Intelligence > Language help |
| 8 | `BoolSetting.BUILD_CACHE` | :26 | Build & Toolchain > "Build cache" toggle | 0 references | Build & Toolchain > Defaults |
| 9 | `BoolSetting.COMMAND_HISTORY` | :27 | Terminal: "Command history" toggle | 0 references | Terminal > Shell |
| 10 | `BoolSetting.ANIMATIONS` | :28 | Appearance > Theme: "Animations" toggle | 0 references | Appearance |
| 11 | `OptionSetting.SHELL` | :42 | Terminal: "Default shell" options | 0 references | Terminal > Shell |
| 12 | `OptionSetting.TERMINAL_THEME` | :40 | Terminal: "Terminal colours" options | 0 references | Terminal |

## Rows removed entirely from `sections()` (UI + function gone)

| # | Removed row (v0.43) | Previous location | Current status | Proposed placement |
|---|---------------------|-------------------|----------------|--------------------|
| 13 | "Pinned versions" `Row.Value` | Build > Toolchain | Removed; `toolchainVersions()` still computed in `SettingsScreen.kt:114` but never shown | Build & Toolchain > Toolchain |
| 14 | "Download build tools" `Row.Action` (`DOWNLOAD_TOOLCHAIN`) | Build > Toolchain | Row gone; action reachable only indirectly via Kotlin `Row.Language` "Download" | Build & Toolchain > Toolchain |
| 15 | "SDK Manager" `Row.Nav` (`SDK_MANAGER`) | Build > Toolchain | Removed; `SdkManagerPage` exists but no row triggers it | Build & Toolchain > Toolchain |
| 16 | "Default shell" `Row.Options` (`SHELL`) | Terminal > Terminal | Removed | Terminal > Shell |
| 17 | "Terminal colours" `Row.Options` (`TERMINAL_THEME`) | Terminal > Terminal | Removed | Terminal > Shell |
| 18 | "Biometric lock" `Row.Nav` (`BIOMETRIC_LOCK`) | Security > Security | Removed; now only `comingSoon` dialog | Security > Privacy |
| 19 | "Credentials" `Row.Nav` (`CREDENTIALS`) | Security > Security | Removed; page exists but no row triggers it | Security > Privacy |
| 20 | "Signing keys" `Row.Nav` (`SIGNING_KEYS`) | Security > Security | Removed; page exists but no row triggers it | Security > Privacy |
| 21 | "Project export & import" `Row.Nav` (`STORAGE_PROJECTS`) | Storage | Removed; page exists but no row triggers it | Storage |

## Orphaned actions — present in enum + `handleAction`, but NO settings row triggers them

Confirmed `grep -c "id = SettingsAction.X"` in `SettingsModel.kt sections() = 0` for each:

| Action | Enum line | `handleAction` line | Wired to | Gap |
|--------|-----------|---------------------|----------|-----|
| `SDK_MANAGER` | `SettingsModel.kt:109` | `SettingsScreen.kt:348` → `sdkOpen = true` | `SdkManagerPage` (`SettingsPages.kt:299`) | No row |
| `FULL_GRADLE` | :111 | :350 → `gradleToolsOpen = true` | `GradleToolsPage` (`SettingsPages.kt:698`) | No row |
| `NATIVE_TOOLS` | :112 | :351 → `nativeToolsOpen = true` | `NativeToolsPage` (`SettingsPages.kt:907`) | No row |
| `CREDENTIALS` | :113 | :353 → `credentialsOpen = true` | `CredentialsPage` (`SettingsPages.kt:206`) | No row |
| `SIGNING_KEYS` | :114 | :352 → `signingOpen = true` | `SigningKeysPage` (`SettingsPages.kt:407`) | No row |
| `BIOMETRIC_LOCK` | :118 | :358 → `comingSoon = "Biometric lock"` | Placeholder only | No row |
| `STORAGE_PROJECTS` | :119 | :357 → `storageProjectsOpen = true` | `StorageProjectsPage` (`SettingsScreen.kt:211`) | No row |
| `PRIVACY` | :116 | :355 → `infoPage = "privacy"` | `SettingsInfoPage` | No row (About shows Source code / Licences only) |

## No-op / placeholder rows

| # | Row | File:line | Behavior | Correction |
|---|-----|-----------|----------|------------|
| 22 | "Build tools" `Row.Status` | `SettingsModel.kt:346-351` | Read-only status; tapping does nothing | Convert to `Row.Nav` with `DOWNLOAD_TOOLCHAIN` (opens `ToolchainSetupScreen`) |
| 23 | "Default project location" `Row.Nav` | `SettingsModel.kt:212-217` → `infoPage = "location"` | Static "not available in this build" message | Implement picker or mark clearly unavailable |
| 24 | Biometric lock | `SettingsScreen.kt:358` | `comingSoon` dialog | Implement or remove |

## Recommended actions
1. Restore rows 1–12 into the appropriate groups (fields already persisted — UI only).
2. Reconnect orphaned actions 13–21 with rows, or remove the enum entries/pages if not to be restored.
3. Fix no-op rows 22–24 (Build tools → `ToolchainSetupScreen`; Project location → picker or disabled; Biometric lock → implement or remove).
4. Add a settings row/action coverage test: every `SettingsAction` (except pure placeholders) referenced by at least one `Row`; every persisted `BoolSetting`/`OptionSetting` has a UI row (`usedBoolSettings()`/`usedOptionSettings()` parity).
