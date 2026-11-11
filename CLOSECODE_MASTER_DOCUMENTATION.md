# CloseCode — Master Documentation

**One file containing all ten required documents.**

- Build: **80** (`0.80.1611`), working tree at commit `0d318ce` plus the uncommitted
  build-80 toolchain work.
- Unit suite: **1611 tests, 0 failures, 0 errors, 8 skipped**.
- Artifact: `build/CloseCode-build80-tests1611-debug.apk`,
  SHA-256 `eae8b48def5df9dc91ae3ad22994716c79e4e6962625651dba671404f67be465`.
- Source of truth for this round: the master specification `checkmehere.md`.
- Nothing in this document is claimed as working because a class, a configuration key or
  an endpoint exists. Every capability is marked as proven, code-verified, or missing.

**The ten documents**

| Part | Document | Answers |
|---|---|---|
| 1 | Current capabilities | What CloseCode can do today, with evidence |
| 2 | Missing capabilities | What it cannot do, what it refuses, and what is broken |
| 3 | Architecture audit | How the codebase is put together, module by module |
| 4 | Implementation roadmap | The phased plan, in dependency order |
| 5 | OpenCode integration | Version, lifecycle, transport, permissions, the MCP design |
| 6 | Tool catalogue | All 51 capabilities, with their permissions and exposure |
| 7 | Toolchain support | What runs on the phone, what needs a remote host |
| 8 | Security model | The two permission layers and what protects what |
| 9 | Test report | The 14 acceptance tests, with honest pass / fail / blocked |
| 10 | Known limitations | Everything CloseCode cannot do, and why |

---


---

# PART 1 — CURRENT CAPABILITIES

Status: audit snapshot for build **80** (versionName `0.80.1611`), working tree at commit
`0d318ce` plus uncommitted build-80 work.

How to read this file: every claim is either **Proven** (a test run, a device run, or a
host POC produced it) or **Code-verified** (the implementation is present and traced, but
runtime behaviour was not exercised in this environment). Nothing is listed as working on
the strength of a class name, a config key or an endpoint existing.

Source of truth for the build under test:
`app/build.gradle.kts:14` (`closecodeBuild = 80`), `app/build.gradle.kts:16`
(`closecodeTests = 1611`).

---

### 1. Verification basis for this snapshot

| Evidence | Result | Where |
|---|---|---|
| JVM unit suite | **1611 tests, 0 failures, 0 errors, 8 skipped** | `app/build/test-results/testDebugUnitTest/*.xml` (run after the last source edit) |
| Debug build | `BUILD SUCCESSFUL` for `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| Delivered artifact | `build/CloseCode-build80-tests1611-debug.apk`, SHA-256 `eae8b48def5df9dc91ae3ad22994716c79e4e6962625651dba671404f67be465` | `build/` |
| Device run | User-verified on phone for builds 79 and 80 | `CLOSECODE_BUILD78_RECHECK.md`, plus the build-80 table below |

---

### 2. A — Implemented and verified working

#### 2.1 OpenCode integration

| Capability | Evidence |
|---|---|
| OpenCode **2.2.5** pinned, SHA-256 verified npm tarball `@opencode/cli-linux-arm64-musl` | `ai/OpenCodeModel.kt:88`, `:91`, `:94-95`, `:101-102` |
| ARM64 musl runtime assembled from pinned Alpine packages (musl, libgcc, libstdc++, zlib) | `ai/OpenCodeRuntime.kt:24-150` |
| Server launched through the musl loader, bound to `127.0.0.1`, password per run | `ai/OpenCode.kt:577-663`; `ai/OpenCodeModel.kt:79`, `:111` |
| Supervision: health poll, auto-restart, orphan reaping by `/proc` scan | `ai/OpenCode.kt:252-510`, `:203-236` |
| Foreground service with a Stop action | `ai/OpenCodeService.kt:26-192` |
| Official OpenCode V2 Web UI in a WebView, authenticated with `?auth_token=` | `ui/ide/OpenCodeChatScreen.kt:528-532`; `docs/opencode-v2/stage-0-host-poc.md:102-112` |
| Egress through a local CONNECT proxy with the app's CA bundle | `ai/OpenCodeProxy.kt:263-283`; `ai/OpenCodeCa.kt:41-53` |
| **No model or provider is hardcoded**; the provider is configured inside OpenCode | `ai/OpenCodeConfig.kt:86-90`; `ai/OpenCodeAiConfig.kt:58-65` has no production caller (test-only) |

#### 2.2 Agent-facing command surface

| Capability | Evidence |
|---|---|
| **51 command tokens** on the control socket (`ping` … `ui`), including `test`, `lint`, `format`, `package`, `release` and `stop-all` | `control/CloseCodeControl.kt:187-318` (counted from the dispatch table) |
| **64 shell aliases** exposed through the `closecode` wrapper on the agent's PATH | `control/CloseCodeControl.kt:1858-1904`, `:1736-1979` |
| Token-authenticated loopback socket, one JSON object per line, 64 KiB cap | `control/CloseCodeControl.kt:59-153`, `:182-186` |
| One result envelope for every command (`ok/artifacts/errors/warnings/exitCode/durationMs/logPath`) | `control/CommandEnvelope.kt:10-46` |
| Exit codes reach the caller as a process exit status | `control/CloseCodeControl.kt:574-604` |
| Project-scoped agent instructions and a skill, written into the project | `ai/OpenCodeAgentMd.kt:20`, `ai/OpenCodeSkill.kt:16-18` |

#### 2.3 Build, test, debug

| Capability | Evidence |
|---|---|
| Custom on-device engine (aapt2 → kotlinc/ECJ → D8 → zip → apksig) | `build/BuildChain.kt:32`; `build/BuildEngine.kt:40`, `:164` |
| Full Gradle engine (downloaded JDK 17 + Gradle 8.13 + Android SDK) | `build/GradleRunner.kt:211`; `build/BuildEngine.kt:205` |
| Real project unit tests run through Gradle | `control/CloseCodeControl.kt:1520-1545` → `testDebugUnitTest` |
| Build artifacts located and reported with exact paths and sizes | `build/BuildReport.kt:9`, `:51`; `build/GradleRunner.kt:190-206` |
| One root cause + structured diagnostics + log path on failure | `build/GradleDiagnostics.kt`; `build/GradleRunner.kt:380-425` |
| Failed build exits non-zero | `control/CloseCodeControl.kt:574-604` (device-verified) |
| One signing key for every engine and build path | `build/SigningStore.kt`; device-verified `signature_mismatch` remedy |
| Build cancellation, 90-minute cap, whole-process-tree kill | `build/BuildCoordinator.kt:60-128`; `build/GradleRunner.kt:219`, `:317-334`; `build/ProcessTree.kt:12-27` |

#### 2.4 Toolchain installation (phone-first, AI-free)

| Capability | Evidence |
|---|---|
| Settings installs JDK 17, Gradle, platform 35, build-tools, platform-tools, licenses — one installer, two front ends (Settings + `closecode setup`) | `ui/settings/SettingsPages.kt:828-1120`; `control/SetupCommand.kt:60-190` |
| **AAPT2 override written by the app**, so `./gradlew` uses the arm64 binary | `build/GradleUserProperties.kt`; `build/AndroidProjectFiles.kt` (shim v2 writes `android.aapt2FromMavenOverride`); `build/PlatformStore.kt:207-247` |
| `./gradlew` shim is phone-safe, sets `GRADLE_USER_HOME` and the selected JDK, and refreshes itself | `build/AndroidProjectFiles.kt` (`GRADLEW`), `:96-118` |
| Resumable, checksum-verified downloads for every component | `tools/GradleTools.kt:272-330`; `tools/ShellToolsStore.kt:219-266`; `build/PlatformStore.kt:322-375` |
| A failed install keeps the previous working version | `tools/GradleTools.kt:151-250`; `tools/ShellToolsStore.kt:60-158` |
| Removing Gradle keeps the dependency cache; cache clearing is a separate action | `tools/GradleTools.kt:252-270`; `tools/GradleTools.kt` (`removeGradleRootExceptCache`); `control/CloseCodeControl.kt:884-905` |
| Python `venv` repaired by the installer (pinned, size- and SHA-verified pip wheel) | `tools/PythonPackRepair.kt`; `tools/ShellToolsStore.kt:135-143` |
| JDK discovery and selection; optional JDK 21 install/remove | `tools/JdkStore.kt`; `ui/settings/SettingsPages.kt` (`JdkSelectionCard`) |
| Storage sizes reported without following symlinks | `tools/DiskUsage.kt`; `tools/NativeTools.kt:115` |

#### 2.5 Android device capabilities (public APIs, no ADB)

| Capability | Evidence |
|---|---|
| Install APK (with signature pre-check), launch, stop, app list | `device/DeviceTools.kt:32`, `:117`, `:188`; `device/control/DeviceControl.kt:64`, `:128` |
| UI tree dump, tap, scroll, type, assert, UI scripts | `device/DeviceTools.kt:211-430`; `device/CloseCodeAccessibilityService.kt:86`; `device/control/UiScript.kt:18` |
| Screenshot and screen recording (MediaProjection) | `device/DeviceTools.kt:413`; `device/ScreenshotActivity.kt`; `device/control/ScreenControl.kt:16` |
| Notification list/dismiss/reply (NotificationListenerService) | `device/CloseCodeNotificationListenerService.kt:49` |
| Logcat of **this app** only — other apps' logcat is not readable on Android ≥ 4.1 | `build/LogcatReader.kt:5-11`, `:28` |
| Device facts: battery, network, storage, display, sensors | `device/control/DeviceControl.kt:315` |
| Scoped file sharing and shell exec with timeout, tree kill and audit | `device/control/DeviceControl.kt:449-538`, `:690-786` |
| `apps clear` is refused with a reason, not faked | `device/control/DeviceControl.kt:224-235` |

#### 2.6 Git

| Capability | Evidence |
|---|---|
| JGit-based status, diff, stage, commit, stash, branches, history | `ui/git/GitService.kt:81-423` |
| Clone / push / pull over HTTPS with token credentials | `ui/github/GitHubClone.kt:29-50`; `ui/github/GitHubRemote.kt:34-124` |
| Token in `EncryptedSharedPreferences`, never written into the repo | `ui/github/TokenStore.kt:109-132`; `ui/github/GitHubRemote.kt:148-178` |
| Push blocked when the project tracks generated or secret files | `ui/github/GitHubRemote.kt:75-81`; `ui/project/GeneratedPaths.kt:46-53` |

#### 2.7 Editor and code intelligence

| Capability | Evidence |
|---|---|
| Sora editor with syntax highlighting, completion, diagnostics, signature help, quick fixes | `ui/ide/SoraEditor.kt:1389`, `:1500`, `:628-695`, `:697-756` |
| A real Android API index generated at build time from `android.jar` | `app/build.gradle.kts` (`generateAndroidApiIndex` → `assets/android-api.txt`); `lang/AndroidApiIndex.kt` |
| Background parsing, debouncing, stale-job cancellation, windowed highlighting, 200 k-char limits | `ui/ide/SoraEditor.kt:213-214`, `:663`, `:673`, `:1313-1329`; `ui/ide/IdeScreen.kt:8143-8147` |
| Analysis performance bounded by a test | `app/src/test/java/com/closecode/lang/AnalysisPerformanceTest.kt:40` |

#### 2.8 Security and control

| Capability | Evidence |
|---|---|
| Capability consents; `shell`, `secrets`, `ui-automation`, `screen` are never self-granted | `device/control/ConsentStore.kt:150-168` |
| The app asks; only the user resolves | `device/control/ConsentStore.kt:98-108`, `:128-135` |
| Audit trail of device-control actions | `device/control/AuditLog.kt:15-31` |
| Secrets in an encrypted store, fallback only in memory | `control/SecretStore.kt:13-61`; `ui/github/TokenStore.kt:93-94` |

#### 2.9 Delivery

| Capability | Evidence |
|---|---|
| CI runs the unit suite, builds the debug APK, uploads it | `.github/workflows/build.yml:32`, `:35` |
| 146 JVM test files; one stock instrumentation test | `app/src/test/java/com/closecode/` |
| APK version carries the build number and the passing test count | `app/build.gradle.kts:36-37` |

---

### 3. B — Partially implemented or unreliable

| Area | What works | What does not | Evidence |
|---|---|---|---|
| Terminal | PTY and pipe engines, streamed output, exit codes | Output is only drained while the Terminal panel is composed; the comment at `ui/ide/IdeTerminal.kt:559-561` claims MainActivity drains it, and it does not | `ui/ide/IdeTerminal.kt:530`, `:559-561` |
| Terminal | Ctrl-C sends SIGINT in PTY mode | Pipe mode destroys the shell only; grandchildren can survive | `ui/ide/IdeTerminal.kt:373-378` |
| Terminal | — | No command timeout; a hung command needs the Stop button | `ui/ide/IdeTerminal.kt` (no timeout parameter) |
| Background jobs | Start, stream to a log file, stop with tree kill | A job that died with the app is marked `lost` and its PID is never reaped | `jobs/JobManager.kt:236`, `:260` |
| Deploy | SSH/rsync/scp deploy with key auth, dry-run, audit | Command output is discarded, so a failure reports only an exit code; the `if (ssh) "ssh" else "ssh"` branch is a no-op | `control/DeployCommand.kt:255`, `:271`, `:244` |
| Static check | Manifest/SDK/plugin checks without Gradle | `closecode gradle test` without `--gradle` explicitly reports that real unit tests do not run on the phone | `control/CloseCodeControl.kt:1259` |
| Build panel | Build and Build again | No "Run tests" action | `ui/ide/BuildPanelState.kt:19-91` |
| Permissions | Two independent layers exist | They do not compose; see `CLOSECODE_SECURITY_MODEL.md` | `ai/OpenCodeConfig.kt:51`; `device/control/ConsentStore.kt:161` |

---

### 4. Device-verified behaviour on build 80

Confirmed by the user on the phone, with local workarounds removed:

- Settings → Full Gradle shows JDK 17 and Gradle 8.13 with real paths, and a Remove button.
- Settings → SDK Manager lists Android 34/35/36, build-tools 35.0.1, platform-tools 34.0.4 and
  the SDK licenses, each with size and Install/Remove.
- Settings → Native tools lists NDK 27.1.12297006 and CMake 3.22.1 with a Remove button.
- `closecode build` → `BUILD SUCCESSFUL in 99 s`, exit 0.
- `closecode check` → `engine:"gradle"`, `errors:0`, exit 0.
- `local.properties` generated with `sdk.dir`.
- `./gradlew` starts through the shim.
- AAPT2 in `closecode build` uses the arm64 `.../build-tools/35.0.1/aapt2`.
- Platform-tools installed by `setup --gradle`; all four SDK license files recreated.
- A failed build exits 1 with one root cause and a log path.
- One signing key everywhere; `closecode install` reports `signature_mismatch` with both
  SHA-256 values and the remedy.
- Consent asks; nothing is granted automatically.

---

### 5. Related documents

- `CLOSECODE_MISSING_CAPABILITIES.md` — what is not there.
- `CLOSECODE_ARCHITECTURE_AUDIT.md` — module map and defects.
- `CLOSECODE_OPENCODE_INTEGRATION.md` — the OpenCode side in detail.
- `CLOSECODE_TOOL_CATALOG.md` — generated from the capability registry.
- `CLOSECODE_TOOLCHAIN_SUPPORT.md` — per-technology support matrix.
- `CLOSECODE_SECURITY_MODEL.md` — the two permission layers.
- `CLOSECODE_TEST_REPORT.md` — the 14 acceptance tests.
- `CLOSECODE_KNOWN_LIMITATIONS.md` — platform limits.
- `CLOSECODE_IMPLEMENTATION_ROADMAP.md` — the phased plan.


---

# PART 2 — MISSING CAPABILITIES

Status: audit snapshot for build **80** (working tree at `0d318ce` + uncommitted build-80 work).

This is the honest list of what CloseCode does **not** have yet, against the master
specification in `checkmehere.md`. Nothing here is a wishlist guess: each item states the
gap, the evidence that it is a gap, and what closing it would cost.

Classification follows the specification:

- **C — Missing**: not implemented at all.
- **D — Not feasible locally on Android**, but possible against a configured remote host.
- **E — Blocked** by a platform restriction, a permission, or a missing dependency.

---

### 1. C — Missing

#### 1.1 Unified capability registry and tool catalogue — **the root gap**

The specification requires "a unified capability/tool registry" that registers available
tools, publishes descriptions and input/output schemas, declares permissions and execution
environment, validates arguments, returns structured results, tracks running operations and
versions tool contracts.

**Evidence of absence** (repo-wide search over `app/src/main`):

| Search | Result |
|---|---|
| `ToolRegistry`, `ToolSpec` | 0 hits |
| `inputSchema`, `jsonSchema` | 0 hits |
| `mcp`, `MCP`, `jsonrpc` | 0 hits |
| `tools/list`, `tools/call` | 0 hits |

What exists instead: a `when` dispatch table of **51 command tokens**
(`control/CloseCodeControl.kt:187-318`), each with ad-hoc positional argument parsing
(`control/CloseCodeControl.kt:724-727`), a shared result envelope
(`control/CommandEnvelope.kt:10-46`) but **no** per-command description, schema, permission
declaration, timeout, cancellation handle or contract version.

**Consequence:** the agent cannot discover what CloseCode can do in a structured way, and
no automated check can tell whether a command's contract changed.

**Cost to close:** one registry table plus a dispatcher that references the existing
implementations. No behaviour change required.

#### 1.2 MCP exposure of CloseCode capabilities

OpenCode 2.2.5 supports MCP servers through `mcp.servers` in `opencode.json` (see
`CLOSECODE_OPENCODE_INTEGRATION.md` §5). CloseCode registers none: the only files written
into the OpenCode config directory are `opencode.json`, `opencode.v1-backup.json` and
`.closecode-permissions` (`ai/OpenCodeConfig.kt:98-134`).

The agent therefore reaches CloseCode only through OpenCode's own `bash` tool plus the
`closecode` CLI wrapper (`control/CloseCodeControl.kt:1736-1979`).

**Consequence:** no schemas, no structured results, no per-tool permission action, and
tool discovery is limited to prose in `AGENTS.md` and `SKILL.md`.

#### 1.3 Real code intelligence (language servers)

The specification lists syntax highlighting, autocomplete, diagnostics, go-to-definition,
find references, rename, hover, formatting, folding, bracket matching, import assistance,
semantic highlighting and refactoring, and states: *"Do not claim language-server support
until the server can be installed/launched, communicate with the editor, and return working
results."*

**What exists:** a hand-written Kotlin lexer, symbol table and heuristic analyser
(`lang/Tokens.kt`, `lang/Symbols.kt`, `lang/KotlinAnalysis.kt:53`, `lang/Diagnostics.kt:71`
— 11 heuristic rules), plus a build-time Android API index.

**What does not exist:** no LSP client, no LSP server, no JSON-RPC transport. A repo-wide
search for `lsp`, `language server`, `typescript-language-server`, `pyright`, `gopls`,
`clangd`, `rust-analyzer` finds only prose. The bundled OpenCode server is additionally
prevented from downloading one (`OPENCODE_DISABLE_LSP_DOWNLOAD=true`,
`ai/OpenCode.kt:626-629`).

**Specifically absent in the editor:** go-to-definition, find-references, rename symbol,
real hover documentation, semantic highlighting (`ui/ide/SoraEditor.kt`).

**Cost to close:** an LSP client plus a server install path. `node`, `npm` and `npx` are
already installed by the shell pack (`app/src/main/assets/shell-tools.json`), so
`typescript-language-server` and `pyright` are reachable on device; `gopls` and
`rust-analyzer` need a Go/Rust toolchain that CloseCode does not install today.

#### 1.4 General remote execution

The specification asks for remote execution as an optional, configurable extension with
task routing, streamed logs, cancellation and artifact retrieval.

**What exists:** `closecode deploy` — SSH/rsync/scp file transfer to a configured host
(`control/DeployCommand.kt`), gated by the `network` consent and an audit record.

**What does not exist:** no remote shell execution, no remote job tracking, no remote
artifact retrieval, no task router that decides local vs remote per task, no remote
environment status in the UI. Repo-wide, `ssh` appears only in the deploy path and in
prose; there is no SFTP client and no `RemoteHost` type.

#### 1.5 Task routing between local and remote

The specification requires, per task, a decision over OS, architecture, SDK, RAM, CPU,
storage, GPU and network, with local selected when supported and remote used only when a
configured and authorised environment exists.

No such decision layer exists. `control/CloseCodeControl.kt` dispatches straight to a
local implementation for every command.

#### 1.6 Extension ecosystem management

No MCP server management UI, no plugin API inside CloseCode, no skill discovery surface.
CloseCode writes one skill (`ai/OpenCodeSkill.kt:16-18`) and one agent document
(`ai/OpenCodeAgentMd.kt:20`) and never reads back what the user added.

#### 1.7 A "run tests" action in the app

The Build panel offers Build / Build again only (`ui/ide/BuildPanelState.kt:19-91`).
`closecode test` runs real Gradle unit tests (`control/CloseCodeControl.kt:1520-1545`), but
the user cannot start that from the editor, and the non-Gradle path says so plainly:
*"Real unit tests are not run on the phone."* (`control/CloseCodeControl.kt:1259`).

#### 1.8 Quality gates

No lint, detekt, ktlint or type-check task exists. `.github/workflows/build.yml:32`, `:35`
runs the unit suite and `assembleDebug` only. `closecodeTests` in `app/build.gradle.kts:16`
is a hand-maintained constant that can drift from the real result.

#### 1.9 Real device-level tests

`app/src/androidTest/` contains one stock template assertion
(`ExampleInstrumentedTest.kt`). All 1611 tests are JVM tests; nothing is exercised on a
real Android runtime in CI.

---

### 2. D — Not feasible locally on Android

| Requirement | Why | Where the limit bites |
|---|---|---|
| macOS / iOS builds | No Apple toolchain, no Xcode, no macOS SDK | `checkmehere.md:352` explicitly forbids claiming this |
| Windows-specific builds/tests | No Windows host, no .NET Framework toolchain | — |
| GPU-enabled ML workloads | Phones have no supported GPU runtime for training | — |
| Heavy desktop-Linux builds | A desktop toolchain may need glibc and desktop libraries | `build/ProjectCheck.kt` refuses unsupported setups rather than pretending |
| Docker / Kubernetes workloads | No privileged daemon on Android | — |
| Full desktop language servers for compiled languages | `gopls` needs a Go toolchain, `rust-analyzer` needs rustup/cargo | Both would need new installers first |

---

### 3. E — Blocked by a platform restriction

| Capability | Block | Evidence |
|---|---|---|
| Logcat of another app | Android ≥ 4.1 gives an app only its own log | `build/LogcatReader.kt:5-11` |
| Clearing another app's data | Needs root or adb | `device/control/DeviceControl.kt:224-235` (refused with a reason) |
| ADB over the network / wireless debugging | The app cannot run a privileged adb server; only platform-tools binaries are downloaded so AGP is satisfied | `build/PlatformStore.kt:81` |
| Uninstalling another app silently | Needs privileges; the app opens the system dialog instead | `device/control/DeviceControl.kt:199-222` |
| Executing binaries from `files/` on Android ≥ 10 | SELinux denies exec unless `targetSdk = 28`; CloseCode deliberately targets 28 | `app/build.gradle.kts:33-37` |
| Running glibc-linked Linux tools | Android has no glibc; OpenCode is shipped as a musl binary with its own loader | `ai/OpenCodeRuntime.kt:24-150` |
| Watching `/data`, `/etc`, `/` | Unreadable or very large on Android; OpenCode's watcher logs EACCES and continues | `docs/opencode-v2/stage-0-host-poc.md:237-249` |
| Background execution after the app is killed | Android lifecycle; a foreground service cannot be relied on to restart | `jobs/JobKeepAliveService.kt:42` (`START_NOT_STICKY`) |

---

### 4. Defects found by the audit (working, but unreliable)

Not "missing" — present but incorrect. These are tracked for the roadmap.

| # | Defect | Evidence | Impact |
|---|---|---|---|
| D1 | Terminal output is only drained while the Terminal panel is composed | `ui/ide/IdeTerminal.kt:530`, `:559-561` | Output accumulates in memory when the user is on another panel |
| D2 | No terminal command timeout | `ui/ide/IdeTerminal.kt` (no timeout parameter anywhere) | A hung command blocks until the user hits Stop |
| D3 | Pipe-mode Ctrl-C destroys the shell, not the tree | `ui/ide/IdeTerminal.kt:373-378` | Orphaned grandchildren can survive |
| D4 | Crashed background jobs are marked `lost`, never reaped | `jobs/JobManager.kt:236`, `:260` | A dev server started by `closecode serve` can outlive the app |
| D5 | Deploy discards rsync/scp output | `control/DeployCommand.kt:255`, `:271` | A failed deploy reports only an exit code |
| D6 | `if (ssh) "ssh" else "ssh"` | `control/DeployCommand.kt:244` | The `ssh` flag has no effect |
| D7 | `deletePath(".")` can resolve to the project root | `ui/project/ProjectStore.kt:55` | Deleting the whole project tree (reachable from the IDE UI) |
| D8 | `closecodeTests` is hand-maintained | `app/build.gradle.kts:16` | The version number can claim a test count that was never run |
| D9 | The completion path re-analyses the whole file synchronously | `ui/ide/SoraEditor.kt:1500-1519` | UI-thread cost per completion popup on large files |
| D10 | The editor subtree is destroyed on every panel switch | `ui/ide/IdeScreen.kt:2701`, `:2766-2768` | Returning to the editor is a cold resume |
| D11 | OpenCode's wildcard permission rule bypasses CloseCode's `shell` consent and the audit log | `ai/OpenCodeConfig.kt:51`; `device/control/ConsentStore.kt:161` | The two security layers do not compose |

---

### 5. What the roadmap closes, in order

1. Capability registry + generated catalogue (§1.1, §1.7 tooling)
2. MCP exposure of the registry (§1.2)
3. Permission convergence (§1.2, D11)
4. Reliability defects D1–D8
5. On-device LSP with the hand-written analyser as fallback (§1.3)
6. Remote execution and task routing (§1.4, §1.5)
7. Extension management (§1.6)
8. Quality gates and device tests (§1.8, §1.9)

See `CLOSECODE_IMPLEMENTATION_ROADMAP.md`.


---

# PART 3 — ARCHITECTURE AUDIT

Status: audit snapshot for build **80** (working tree at `0d318ce` + uncommitted build-80 work).

Scope: the whole application under `app/src/main/java/com/closecode`, plus the build
configuration and CI. Every statement points at a file and line.

---

### 1. Module map

```
com.closecode
├── MainActivity.kt              Single activity; Compose navigation host; startup repair
├── ai/                          OpenCode integration
│   ├── OpenCode.kt              Download, launch, supervise, authenticate, orphan-reap
│   ├── OpenCodeBinary.kt        Pinned npm tarball, SHA-256 verified
│   ├── OpenCodeRuntime.kt       Alpine musl runtime, per-arch pins
│   ├── OpenCodeConfig.kt        opencode.json: permissions, migration, stamps
│   ├── OpenCodeAgentMd.kt       Writes AGENTS.md into the project
│   ├── OpenCodeSkill.kt         Writes .opencode/skills/closecode/SKILL.md
│   ├── OpenCodeSessions.kt      /api/session
│   ├── OpenCodePrompts.kt       /api/session/:id/prompt
│   ├── OpenCodeProxy.kt         Local CONNECT proxy for egress
│   ├── OpenCodeCa.kt            CA bundle for the agent's TLS
│   └── OpenCodeServerLog.kt     Rotating server log
├── control/                     The agent-facing command surface
│   ├── CloseCodeControl.kt      Socket server, 51-token dispatch, CLI wrapper script
│   ├── CommandEnvelope.kt       The one result shape
│   ├── SetupCommand.kt          Toolchain installation/removal
│   ├── DiagnosticsCommands.kt   doctor, toolchain status
│   ├── ToolBridge.kt            aapt2 / d8 / r8 / kotlinc
│   ├── ToolWrappers.kt          Shell shims on the agent's PATH
│   ├── DeployCommand.kt         SSH/rsync/scp deploy
│   ├── EcosystemCommand.kt      npm / pytest / ctest / cargo …
│   ├── SecretsCommand.kt        Encrypted secret vault
│   ├── SelftestCommands.kt      On-device self-test (19 checks)
│   ├── ProjectDescription.kt    Project type, engine, toolchain report
│   └── SigningCommand.kt        Signing key management
├── build/                       Build engines and the SDK
│   ├── BuildEngine.kt           Chooses custom vs Gradle
│   ├── BuildChain.kt            Custom on-device pipeline
│   ├── GradleRunner.kt          Full Gradle invocation, diagnostics, cancellation
│   ├── BuildSetup.kt            Toolchain resolution for a project
│   ├── ProjectCheck.kt          Static project checks
│   ├── PlatformStore.kt         SDK platform/build-tools/platform-tools/licenses
│   ├── AndroidProjectFiles.kt   Phone-safe gradlew + local.properties
│   ├── GradleUserProperties.kt  Writes gradle.properties for wrapper builds
│   ├── ProcessTree.kt           /proc-based tree kill
│   ├── BuildCoordinator.kt      One build at a time
│   └── BuildService.kt          Foreground service + cancel notification
├── tools/                       Toolchain stores (all download + verify)
│   ├── ShellToolsStore.kt       Termux packs, resumable, checksum-verified
│   ├── GradleTools.kt           JDK + Gradle distribution
│   ├── NativeTools.kt           NDK + CMake
│   ├── JdkStore.kt              JDK discovery, selection, optional JDK 21
│   ├── PythonPackRepair.kt      Pinned pip wheel for `python3 -m venv`
│   ├── OptionalPacks.kt         Media and language packs
│   ├── ShellEnvironment.kt       One environment for every subprocess
│   ├── ShellToolsRepo.kt        Termux repository layout
│   ├── TermuxPrefix.kt          Prefix relocation (byte-length preserving)
│   └── DiskUsage.kt             Symlink-safe size measurement
├── device/                      Android device capabilities
│   ├── DeviceTools.kt           Install, launch, UI tree, input, screenshot
│   ├── DeviceControl.kt         The `ui`, `input`, `files`, `exec`, `device` commands
│   ├── control/                 ConsentStore, AuditLog, ScreenControl, UiScript
│   ├── CloseCodeAccessibilityService.kt
│   └── CloseCodeNotificationListenerService.kt
├── ui/                          Compose UI
│   ├── ide/                     Editor, terminal, build panel, git panel, AI tab
│   ├── project/                 ProjectStore, ProjectSafety, GeneratedPaths, scaffolding
│   ├── settings/                Settings master/detail, every toolchain page
│   ├── git/                     GitService (JGit)
│   ├── github/                  GitHub client, clone, push/pull, token store
│   └── onboarding/              First-run flow
├── lang/                        Hand-written Kotlin analysis
│   ├── Tokens.kt, Symbols.kt, KotlinAnalysis.kt, Diagnostics.kt
│   ├── Completion.kt, SignatureHelp (ui/ide), SourceEdits.kt
│   └── AndroidApiIndex.kt       Generated index of the public Android API
└── jobs/                        Background job registry
```

---

### 2. How the agent reaches CloseCode

```
OpenCode server (musl binary, 127.0.0.1:4098+)
      │  PATH includes files/opencode/bin
      ▼
files/opencode/bin/closecode  ──►  TCP 127.0.0.1:<ephemeral>
                                     JSON {token, command, cwd, args}
                                     ▼
                              CloseCodeControl.dispatch (51 tokens)
                                     ▼
                       existing implementations (build, device, git, jobs, …)
```

- Socket: `control/CloseCodeControl.kt:59-153`; token compared in constant time
  (`:182-186`, `:1706-1712`); port and token written to
  `files/opencode/control.port` and `control.token` (`:1714-1718`).
- The wrapper script `controlCommandScript` (`:1736-1979`) streams the answer line by line
  through a FIFO (`:1935-1953`) and converts the `__CLOSECODE_EXIT__` marker into a
  process exit code (`:1943-1944`).
- Command groups: `ai/OpenCodeAgentMd.kt:81-111` and `ai/OpenCodeSkill.kt:20-119` document
  the verbs in prose.

**Audit conclusion:** the transport is sound, but the surface is *undocumented in machine
form*. Nothing tells the agent what a command accepts, what it returns, or what permission
it needs. See `CLOSECODE_MISSING_CAPABILITIES.md` §1.1.

---

### 3. Execution layer

| Concern | Implementation | Assessment |
|---|---|---|
| Terminal | PTY via JNI `libclosepty.so` (`ui/ide/Pty.kt:20-41`), pipe fallback (`ui/ide/IdeTerminal.kt:232-330`) | Works; output drain is tied to panel composition (defect D1) |
| Exit codes | Marker appended to every command (`ui/ide/IdeTerminal.kt:125`), parsed at `:133-137` | Works, but it is the *shell's* status, not `Process.exitValue()` |
| Cancellation | PTY: SIGINT via the master fd (`:226-236`); pipe: `destroy()` (`:373-378`) | Partial (defect D3) |
| Timeouts | None in the terminal; 90 min in Gradle (`build/GradleRunner.kt:219`); 120 s default in `exec` (`device/control/DeviceControl.kt:706`) | Missing for the terminal (defect D2) |
| Background jobs | `jobs/JobManager.kt:49` — log file, exit code, tree kill | Works; orphans are not reaped (defect D4) |
| Builds | One at a time (`build/BuildCoordinator.kt:60-128`), foreground service, cancel notification | Works |
| Main-thread safety | File walks off-main (`ui/ide/IdeScreen.kt:976`), analysis on `Dispatchers.Default` (`ui/ide/SoraEditor.kt:663`), reads capped at 8 MB (`ui/project/ProjectSafety.kt:95-101`) | Good |

---

### 4. Toolchain layer

One installer, two front ends: `ui/settings/SettingsPages.kt` (Settings pages) and
`control/SetupCommand.kt` (CLI) both drive the same stores in `tools/` and `build/`.

| Component | Store | Size / checksum | Resume |
|---|---|---|---|
| Termux packs (shell, media, languages) | `tools/ShellToolsStore.kt` | Repo SHA-256 | Yes (`:219-266`) |
| JDK 17 + Gradle distribution | `tools/GradleTools.kt` | Manifest SHA-256 | Yes (`:272-330`) |
| Android platform / build-tools / platform-tools | `build/PlatformStore.kt` | Google SHA-1/SHA-256 | Yes (`:322-375`) |
| NDK + CMake | `tools/NativeTools.kt` | Manifest SHA-256 | Yes |
| pip wheel for `venv` | `tools/PythonPackRepair.kt` | Pinned size + SHA-256 | Yes |
| OpenCode 2.2.5 + Alpine runtime | `ai/OpenCodeBinary.kt`, `ai/OpenCodeRuntime.kt` | Pinned SHA-256 | No (single file) |

All sizes reported to the user come from `tools/DiskUsage.kt`, which does not follow
symlinks — an earlier version walked NDK symlinked directories twice and reported roughly
double the real `du` figure.

---

### 5. Where the security layers sit

```
OpenCode's own permission ruleset   ai/OpenCodeConfig.kt:50-83
   (wildcard allow, ask for destructive shell, deny sudo/su/reboot)
                 │
                 │  NOT composed with
                 ▼
CloseCode ConsentStore               device/control/ConsentStore.kt:150-168
   (build/install/network default-granted; shell/secrets/ui/screen never self-granted)
                 │
                 ▼
AuditLog (JSONL)                     device/control/AuditLog.kt:15-31
```

Because the OpenCode ruleset allows `*`/`*` (`:51`), OpenCode's own `bash` tool can run
shell commands on the phone without passing through `ConsentStore`'s `shell` gate or the
audit log. This is defect D11 and is addressed in the roadmap, not silently.

---

### 6. Defects with file-level ownership

| ID | File | Owner area | Summary |
|---|---|---|---|
| D1 | `ui/ide/IdeTerminal.kt:530`, `:559-561` | terminal | Output drains only while the Terminal panel is composed |
| D2 | `ui/ide/IdeTerminal.kt` | terminal | No command timeout |
| D3 | `ui/ide/IdeTerminal.kt:373-378` | terminal | Pipe Ctrl-C kills the shell, not the tree |
| D4 | `jobs/JobManager.kt:236`, `:260` | jobs | Crashed jobs never reaped |
| D5 | `control/DeployCommand.kt:255`, `:271` | deploy | Deploy output discarded |
| D6 | `control/DeployCommand.kt:244` | deploy | No-op `ssh` branch |
| D7 | `ui/project/ProjectStore.kt:55` | project | `deletePath(".")` can target the project root |
| D8 | `app/build.gradle.kts:16` | build | `closecodeTests` is hand-maintained |
| D9 | `ui/ide/SoraEditor.kt:1500-1519` | editor | Completion re-analyses the whole file on the UI thread |
| D10 | `ui/ide/IdeScreen.kt:2701`, `:2766-2768` | editor | Editor destroyed on panel switch (cold resume) |
| D11 | `ai/OpenCodeConfig.kt:51` + `device/control/ConsentStore.kt:161` | security | The two permission layers do not compose |

---

### 7. Structural observations (not defects)

1. **`control/CloseCodeControl.kt` is the hub.** It owns the socket, the dispatch table, the
   CLI script and many command implementations. It is the natural place for the capability
   registry, and the natural first refactor target.
2. **`ui/ide/IdeScreen.kt` is 8297 lines** with a ~3450-line composable
   (`ui/ide/IdeScreen.kt:203-3651`). New editor features (LSP) must be added in separate
   files to avoid making this worse.
3. **The build layer is clean.** `build/` separates engines (`BuildEngine`), orchestration
   (`BuildCoordinator`, `BuildService`), toolchain resolution (`BuildSetup`) and process
   control (`ProcessTree`). The custom engine is isolated in `BuildChain.kt` and is not
   entangled with Gradle.
4. **Download verification is uniform.** Every store verifies a checksum before use; the
   pattern is repeated in `tools/` and `build/` rather than centralised, which is a
   candidate for a shared helper but is not a correctness problem today.
5. **The permission-aware surfaces are consistent**: `ProjectSafety` guards project files,
   `ConsentStore` guards device commands, `SecretStore` guards secrets, and the Git layer
   refuses to stage generated or secret paths.

---

### 8. Test and delivery infrastructure

- 146 JVM test files under `app/src/test/java/com/closecode/`; 1611 tests in the last run
  (0 failures, 0 errors, 8 skipped).
- One stock instrumentation test in `app/src/androidTest/` — no real device tests.
- CI: `.github/workflows/build.yml:32` (unit suite), `:35` (assemble + upload). No lint,
  no static analysis, no instrumentation.
- The build-time Android API index is wired into the JVM tests through a system property
  (`app/build.gradle.kts`, `tasks.withType<Test>`).

---

### 9. What this audit changes in the plan

1. The registry belongs in `control/` first (it describes the control surface) and moves to
   `com.closecode.capability` when the MCP bridge needs it.
2. The MCP bridge must reuse the same handlers; the dispatch `when` becomes a table lookup.
3. The security work is a *composition* problem, not a rewrite: OpenCode's ruleset decides
   whether the agent may call a tool at all; `ConsentStore` decides whether the phone
   performs the action.
4. Editor work must be additive, in new files, because `IdeScreen.kt` is already the
   largest file in the project.
5. Quality gates (lint, a real test-count check, instrumentation) belong before the
   feature phases, so later phases cannot quietly regress.


---

# PART 4 — IMPLEMENTATION ROADMAP

Status: build **80** (working tree at `0d318ce` + uncommitted build-80 work).

The plan for turning CloseCode into the phone-only development environment OpenCode can
use, in dependency order, with a fixed rule for every phase: **scope → files → implement →
test → record evidence → update documentation → preserve what works.** No phase may
remove a working capability to make a test pass.

Phases 0–9 follow `checkmehere.md:474-528`. Rounds A–E are how they are scheduled here.

---

### Round A — Audit, registry, MCP, permissions

#### Phase 0 — Audit and baseline

| Item | Status |
|---|---|
| Inspect the architecture, the custom engine, terminal, processes, files, Git, OpenCode, MCP, device features, builds, toolchains, permissions, UI | **Done** — `CLOSECODE_ARCHITECTURE_AUDIT.md` |
| Verify OpenCode launch and model/provider setup | **Done** — `CLOSECODE_OPENCODE_INTEGRATION.md` §1–4 |
| Inventory tools and capabilities | **Done** — `CLOSECODE_CURRENT_CAPABILITIES.md`, `CLOSECODE_TOOL_CATALOG.md` |
| Measure editor performance | **Partial** — `lang/AnalysisPerformanceTest.kt:40` bounds analysis time; editor resume time is not yet instrumented |
| Produce the audit documents | **Done** — this document set |

#### Phase 1 — The unified capability registry

**Why first:** everything after it depends on a machine-readable description of what
CloseCode can do.

**Files**

| New | Purpose |
|---|---|
| `app/src/main/java/com/closecode/capability/ToolSpec.kt` | Name, description, input/output JSON Schema, permission, target, timeout, cancellable, read-only, error codes, contract version, agent visibility |
| `app/src/main/java/com/closecode/capability/ToolResult.kt` | Typed result that maps 1:1 onto the existing `CommandEnvelope` |
| `app/src/main/java/com/closecode/capability/CapabilityRegistry.kt` | The single table: every current command token, alias, socket timeout, permission, schema, and a reference to the existing implementation |
| `app/src/main/java/com/closecode/capability/CapabilityDispatcher.kt` | Replaces the `when` in `control/CloseCodeControl.kt:187-318` with a registry lookup |
| `app/src/test/java/com/closecode/capability/CapabilityRegistryTest.kt` | Golden map (51 tokens, 64 aliases), schema validity, unknown-token behaviour |

**Rules**

- The registry **references** the existing implementations; no command body is rewritten.
- The dispatch behaviour, the JSON envelope, the socket timeouts and the exit-code
  semantics stay byte-identical.
- Every entry declares the `ConsentStore` capability it needs and whether the agent may
  see it. Internal and sensitive commands are not agent-visible by default.
- The catalogue document is generated from the registry, so it cannot drift.

**Exit criteria:** the golden map test passes, the full suite is green, and
`closecode tool list|schema|call` works.

#### Phase 2 — MCP exposure

**Files**

| New | Purpose |
|---|---|
| `app/src/main/java/com/closecode/mcp/McpServer.kt` | Loopback Streamable HTTP endpoint: one path, POST + GET, `Mcp-Session-Id`, `MCP-Protocol-Version`, `Origin` validation, bearer token |
| `app/src/main/java/com/closecode/mcp/McpMethods.kt` | `initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call` |
| `app/src/main/java/com/closecode/mcp/McpToolBridge.kt` | Registry → MCP tools; schemas in and out; result and error mapping |
| `app/src/main/java/com/closecode/mcp/McpPermissionBridge.kt` | Maps a tool's declared capability onto `ConsentStore`, and asks the user when it is missing |

**Changes**

- `ai/OpenCodeConfig.kt` — write `mcp.servers.closecode` without touching any other key,
  using the same one-time-stamp pattern as the permissions upgrade.
- Write the configuration **before** the OpenCode server starts, because OpenCode reads
  configuration at startup.

**Exit criteria:** the agent lists CloseCode's tools with schemas, calls one, receives a
structured result, and the permission rules behave as specified. Verified against the
**installed** OpenCode 2.2.5, using the host POC recorded in
`docs/opencode-v2/mcp-protocol-poc.md`.

#### Phase 3 — Permission convergence

- Narrow the OpenCode wildcard so agent shell work is consented and audited.
- Keep both layers: OpenCode decides whether the agent may attempt a tool; `ConsentStore`
  decides whether the phone performs it.
- Map each MCP tool's permission onto a `ConsentStore` capability.
- Add a self-test check that proves a denied capability is refused through both paths.

#### Phase 4 — Reliability defects

| Defect | File | Fix |
|---|---|---|
| D1 terminal drain | `ui/ide/IdeTerminal.kt:530` | Drain the session channel in the holder, not only in the composable |
| D2 terminal timeout | `ui/ide/IdeTerminal.kt` | Default and per-command timeout, then tree kill |
| D3 pipe Ctrl-C | `ui/ide/IdeTerminal.kt:373-378` | `ProcessTree.terminate` instead of `destroy()` |
| D4 orphan jobs | `jobs/JobManager.kt:236-260` | Reap PIDs of jobs that were `running`, then mark them lost |
| D5 deploy output | `control/DeployCommand.kt:271` | Capture stdout/stderr into the envelope and the log |
| D6 no-op branch | `control/DeployCommand.kt:244` | Delete the dead branch |
| D7 root delete | `ui/project/ProjectStore.kt:55` | Refuse a path that resolves to the project root |
| D8 test count | `app/build.gradle.kts:16` | A `checkTestCount` task that fails when the constant and the JUnit totals disagree |
| D9 completion cost | `ui/ide/SoraEditor.kt:1500-1519` | Reuse the cached analysis instead of re-analysing on the UI thread |
| D10 cold resume | `ui/ide/IdeScreen.kt:2701` | Keep the editor alive across panel switches |
| D11 permission bypass | `ai/OpenCodeConfig.kt:51` | Covered by Phase 3 |

#### Phase 5 — Code intelligence

- New package `com.closecode.lsp`: `LspClient` (JSON-RPC over stdio), `LspServers`
  (installable servers), `LspManager` (per-project, per-language).
- Install on demand, warn about size, keep the hand-written analyser as the fallback.
- Wire `publishDiagnostics`, `completion`, `definition`, `references`, `rename`, `hover`,
  `signatureHelp` and `formatting` into the editor, in **new files** — `IdeScreen.kt` is
  already 8297 lines.
- Measure editor resume time and memory before and after.

#### Phase 6 — Build, test, debug integration

- A "Run tests" action in the Build panel for Gradle projects.
- Build report that names the engine, the artifacts and the log path.
- Test results surfaced in the Problems panel.

---

### Round B — Android device integration

- Expose the verified device capabilities as first-class tools with schemas.
- Logcat, screenshots, install/launch, UI scripts — each with its declared permission.
- Document the platform limits in `CLOSECODE_KNOWN_LIMITATIONS.md`.
- Instrumentation tests for the parts that need a real runtime.

---

### Round C — Extensions

- MCP server management UI (add, remove, connection state via `/api/mcp`).
- Skill discovery for project and global skills.
- A `closecode` command manifest so custom commands can be added without a code change.
- No second plugin loader: OpenCode already provides plugins.

---

### Round D — Remote execution

| Item | Detail |
|---|---|
| `RemoteHost` | Host, user, port, key; stored in the encrypted `SecretStore` |
| `RemoteSession` | SSH through the Termux binaries; streamed logs, cancel, exit code |
| `RemoteWorkspace` | rsync up/down with excludes; artifact retrieval |
| `TaskRouter` | Per task decide local vs remote from OS, arch, SDK, RAM, storage and network needs |
| Rule | A task moves to a remote host **only** when the user configured and authorised that host, and the app explains why |
| Rule | No silent cost, no hidden remote assumption |

---

### Round E — Advanced workflows and final report

- Multi-agent workflows using OpenCode's own capabilities.
- Browser and API testing.
- Database tooling.
- Deployment integrations.
- The final report: implemented, reused, changed, tested, passed, failed, incomplete,
  needs-remote, cannot-run-on-Android, and the extra dependencies, storage, permissions
  and costs.

---

### Standing rules for every phase

1. Audit before major implementation.
2. Preserve working functionality — the custom Android engine and the build-80 toolchain
   work are not to be rewritten.
3. OpenCode stays the only agent.
4. Never confuse the model/provider with OpenCode.
5. Never invent APIs, tool names, configuration keys or compatibility.
6. Verify the installed OpenCode version before integrating with it.
7. Do not claim a capability works until it has been tested.
8. No mocked results as proof.
9. Do not assume desktop Linux tools run natively on Android.
10. Never silently move work to a remote service or incur a cost.
11. Never expose secrets to the model or the logs.
12. Never block the Android UI thread with builds or analysis.
13. Never remove features to make tests pass.
14. Document platform limitations and incomplete work.
15. Implement in small, reviewable phases; each ends with a green suite, a version bump, an
    APK and its SHA-256, and nothing is committed or pushed without permission.


---

# PART 5 — OPENCODE INTEGRATION

Status: audit snapshot for build **80** (working tree at `0d318ce` + uncommitted build-80 work).

This document describes exactly how CloseCode runs OpenCode, how the agent reaches
CloseCode's capabilities, what is missing, and what the integration becomes next.

The governing rule from `checkmehere.md`: **OpenCode is the coding agent. CloseCode is the
development environment and capability provider.** CloseCode must not build a second agent,
must not hardcode a model provider, and must not invent APIs or configuration keys.

CloseCode satisfies this today: there is no second chat agent, no model picker, and no
provider hardcoding. The "OpenCode AI" settings entry is a static information page
(`ui/settings/SettingsScreen.kt:216-225`).

---

### 1. Version and provenance

| Fact | Value | Evidence |
|---|---|---|
| OpenCode release | **2.2.5**, pinned | `ai/OpenCodeModel.kt:88` |
| Distribution channel | npm platform package `@opencode/cli-linux-arm64-musl` (not the GitHub release; GitHub "latest" is still V1) | `ai/OpenCodeModel.kt:91`; `docs/opencode-v2/stage-0-host-poc.md:52-55` |
| Tarball | `https://registry.npmjs.org/@opencode/cli-linux-arm64-musl/-/cli-linux-arm64-musl-2.2.5.tgz` | `ai/OpenCodeModel.kt:94-95` |
| Tarball SHA-256 | `ef26ea9a81b8adf4185a47b7d3aa10b94c2c5fc25` | `ai/OpenCodeModel.kt:101-102` |
| Binary inside | `package/bin/opencode` | `ai/OpenCodeModel.kt:98` |
| Install path | `files/opencode/opencode`, atomic replace, marker `files/opencode/program.txt` | `ai/OpenCodeBinary.kt:58-121` |
| CPU gate | arm64-v8a only; x86_64 emulators are refused with a clear message | `ai/OpenCodeBinary.kt:45-46`; `ai/OpenCode.kt:268-271` |

**Do not bump the version** without re-running the host POC
(`docs/opencode-v2/stage-0-host-poc.md`) and opening the official page on a real phone.

---

### 2. Runtime

OpenCode 2.2.5 is a musl-linked Linux ELF. Android has no musl, so CloseCode downloads a
pinned Alpine runtime and launches the binary through its loader.

| Fact | Value | Evidence |
|---|---|---|
| Alpine packages | `musl-1.2.6-r3`, `libgcc-15.2.0-r5`, `libstdc++-15.2.0-r5`, `zlib-1.3.2-r1`, per-arch SHA-256 | `ai/OpenCodeRuntime.kt:47-63` |
| Loader | `files/opencode/alpine/lib/ld-musl-<arch>.so.1` | `ai/OpenCodeRuntime.kt:69-70` |
| Library path | `<root>/lib:<root>/usr/lib` | `ai/OpenCodeRuntime.kt:73-76` |

Launch line (`ai/OpenCode.kt:591-602`):

```
ld-musl-aarch64.so.1 --library-path <root>/lib:<root>/usr/lib \
  files/opencode/opencode --print-logs serve --hostname 127.0.0.1 --port <port>
```

`targetSdk = 28` is deliberate: Android 10+ denies exec from an app's `files/` directory
unless the app targets 28 (`app/build.gradle.kts:33-37`).

---

### 3. Lifecycle and transport

| Concern | Implementation | Evidence |
|---|---|---|
| Bind address | `127.0.0.1` only, never `0.0.0.0` | `ai/OpenCodeModel.kt:111` |
| Preferred port | 4098, then 4099…4118 if taken; a stale holder is found via `/proc/net/tcp` and killed if it is ours | `ai/OpenCodeModel.kt:79`; `ai/OpenCode.kt:869-947` |
| Authentication | Fresh 192-bit random password per run; Basic auth for API calls | `ai/OpenCode.kt:669-673`; `ai/OpenCodeModel.kt:146-148` |
| Web UI bootstrap | `?auth_token=<base64url("opencode:"+password)>` | `ui/ide/OpenCodeChatScreen.kt:528-532`; `docs/opencode-v2/stage-0-host-poc.md:108-112` |
| Health | `GET /api/info`, 1 s poll, 120 s start timeout, 120 s silence limit, 3 s restart backoff | `ai/OpenCode.kt:538-564`; `ai/OpenCodeModel.kt:251`; `ai/OpenCode.kt:982-983` |
| Orphan reaping | `/proc` scan, UID + cmdline match, killed before a new launch | `ai/OpenCode.kt:203-236` |
| Foreground service | Notification with a Stop action; `START_NOT_STICKY` | `ai/OpenCodeService.kt:26-203` |
| Paths | Canonical paths everywhere — mixing `/data/user/0/…` and `/data/data/…` breaks snapshots | `docs/opencode-v2/stage-0-host-poc.md:179-193` |
| Egress | Local CONNECT proxy injected as `HTTP_PROXY`/`HTTPS_PROXY`/`ALL_PROXY`, `NO_PROXY` for loopback | `ai/OpenCodeProxy.kt:263-283` |
| Grounded env | `HOME`, `XDG_{CONFIG,DATA,CACHE}_HOME`, `TMPDIR`, `BROWSER=/system/bin/true`, CA bundle, `CLOSECODE_SHELL_TOOLS`, `JAVA_HOME`, `ANDROID_HOME` | `ai/OpenCode.kt:713-770` |

#### API endpoints CloseCode uses

| Endpoint | Use | Evidence |
|---|---|---|
| `GET /api/info` | Health | `ai/OpenCode.kt:540` |
| `GET/POST /api/session` | Session list / create | `ai/OpenCodeSessions.kt:29`, `:32` |
| `POST /api/session/:id/prompt` | Send a prompt | `ai/OpenCodePrompts.kt:20` |

Full endpoint inventory (138 paths) is in `docs/opencode-v2/stage-0-host-poc.md:119-148`.

---

### 4. Model and provider configuration

- CloseCode writes **no** model and **no** provider.
  `ai/OpenCodeConfig.kt:86-90` writes only `$schema`, `update: disable` and `permissions`.
- `ai/OpenCodeAiConfig.kt:58-65` (`model()`, `withModel()`) has **no production caller** —
  verified by repo-wide search; only `app/src/test/java/com/closecode/ai/OpenCodeAiConfigTest.kt`
  references it. It is dead code kept for a future in-app provider picker.
- Provider authentication is delegated to OpenCode: the app writes an `opencode auth login`
  wrapper sharing the server's `HOME`/`XDG_CONFIG_HOME`
  (`ai/OpenCode.kt:792-847`, `:1097-1146`) and only *shows* that command in Settings.
- The user's model is chosen in OpenCode's own UI or by editing `opencode.json`. This is the
  correct behaviour per `checkmehere.md:150-153` and `:162`.

---

### 5. What the agent can reach today

#### 5.1 The control surface

51 command tokens, 64 shell aliases, one token-authenticated loopback socket, one JSON
object per request, one shared result envelope.

| Property | Value | Evidence |
|---|---|---|
| Tokens | 51 (`ping` … `ui`, plus `test`/`lint`/`format`/`package`/`release`, `stop-all`, `test-ui`) | `control/CloseCodeControl.kt:187-318` |
| Aliases | 64 | `control/CloseCodeControl.kt:1858-1904` |
| Request | `{"token","command","cwd","args"}`, ≤ 64 KiB | `control/CloseCodeControl.kt:60`, `:155-164` |
| Response | JSON envelope + optional `__CLOSECODE_EXIT__<code>__` | `control/CloseCodeControl.kt:574-604`; `control/CommandEnvelope.kt:10-46` |
| Wrapper | `files/opencode/bin/closecode`, first on the agent's PATH | `control/CloseCodeControl.kt:1723-1733`; `ai/OpenCode.kt:743-751` |

#### 5.2 What is missing

| Missing | Consequence |
|---|---|
| No tool registry / catalogue | The agent cannot discover capabilities in a structured way |
| No schemas | Arguments are positional strings; nothing validates them |
| No MCP server | No machine-readable tool surface, no per-tool permission action, no tool refresh |
| Two disjoint permission layers | OpenCode's `bash` tool can act outside `ConsentStore`'s gate and audit trail |
| No server→client stream | Nothing can notify the agent of state changes |

---

### 6. The integration becomes: MCP over Streamable HTTP

OpenCode 2.2.5 supports MCP servers declared in `opencode.json`
(`mcp.servers`, V2 shape — server names live under `mcp.servers`, not directly under `mcp`).
Relevant documented fields for a **remote** server: `type: "remote"`, `url`, `headers`,
`oauth` (set `false` for header credentials), `disabled`, `codemode`, `timeout`,
`protocol`, `pool`.

Design decisions:

1. **Transport:** a Streamable HTTP endpoint inside the app, bound to `127.0.0.1` on an
   ephemeral port. One endpoint, POST and GET, per the MCP transport specification.
2. **Authentication:** `Authorization: Bearer <token>` with a 256-bit `SecureRandom` token
   generated per server start, written to an app-private file with `0600` permissions and
   never logged. `Origin` is validated; the server binds loopback only.
3. **Registration:** the `opencode.json` entry is written **before** the OpenCode server
   starts, because OpenCode reads its configuration at startup. The app owns only its own
   `mcp.servers.closecode` object and leaves every other key alone, using the same
   one-time-stamp pattern as the permissions upgrade
   (`ai/OpenCodeConfig.kt:120-134`).
4. **Tool visibility:** only registry entries explicitly marked as agent-visible are
   exposed. Internal and sensitive commands (`secrets`, `files`, `ui`, `input`, `screen`,
   `accessibility`, `stop-all`, `restart`, `consent`, `audit`) are not exposed by default.
5. **No duplicate implementation:** MCP tool handlers call the same registry entries that
   the CLI calls, which call the same existing implementations.
6. **Notifications:** if the endpoint does not implement an SSE stream, it must not
   advertise server→client notifications that depend on it. Tool refresh is therefore
   pull-based (`tools/list`), never a `notifications/tools/list_changed` push.

#### 6.1 Version and protocol verification — required before implementation

`checkmehere.md:171` requires verifying the integration mechanism against the version in
use, and the user's instruction for this round requires the **exact negotiated protocol
version**, not an inference from documentation.

OpenCode's MCP client in 2.2.5 supports the classic `initialize` handshake and, by
default, a `legacy` protocol mode; `protocol: "auto"` additionally probes
`server/discover` for the 2026-07-28 revision.

**Planned verification (host POC, aarch64, this machine):**

1. Download the pinned 2.2.5 tarball, verify SHA-256 against `ai/OpenCodeModel.kt:101-102`.
2. Fetch the pinned Alpine runtime, run `opencode serve` through the musl loader.
3. Point it at a throwaway reference MCP server and record:
   - the `protocolVersion` sent in `initialize` and the one returned,
   - the `Accept` and `MCP-Protocol-Version` headers the client sends,
   - whether it issues `GET` on the endpoint, and how it reacts to `405`,
   - whether it requires `Mcp-Session-Id` on subsequent requests,
   - the exact permission request/reply JSON (`/api/permission/request`,
     `POST /api/session/:id/permission/:requestID/reply` with `once|always|reject`,
     per `docs/opencode-v2/stage-0-host-poc.md:149-154`).
4. Record raw evidence in `docs/opencode-v2/mcp-protocol-poc.md` and implement the server
   against the observed behaviour, not against the documentation alone.

#### 6.2 Protocol requirements the server must meet (from the MCP transport specification)

- One endpoint path supporting POST and GET.
- POST body is a single JSON-RPC request, notification or response.
- `Accept: application/json, text/event-stream` on requests; responses may be
  `application/json` (one JSON object) or `text/event-stream`.
- Notifications and responses from the client get `202 Accepted` with no body.
- `Mcp-Session-Id` may be issued at initialize; clients then send it on every request.
- `MCP-Protocol-Version` header on subsequent requests; absent means assume `2025-03-26`;
  an unsupported version must be answered with `400`.
- `GET` must return an SSE stream or `405`; `DELETE` may return `405`.
- Loopback binding, `Origin` validation and authentication are required for a local server.

---

### 7. Permission layers (summary; details in `CLOSECODE_SECURITY_MODEL.md`)

Two independent layers, deliberately kept separate:

| Layer | Owns | Evidence |
|---|---|---|
| OpenCode's `permissions` ruleset in `opencode.json` | Whether the agent may attempt a tool at all | `ai/OpenCodeConfig.kt:50-83` |
| CloseCode's `ConsentStore` | Whether the phone performs the action, and its audit trail | `device/control/ConsentStore.kt:150-168` |

Neither may bypass the other. Today the OpenCode wildcard (`ai/OpenCodeConfig.kt:51`)
allows OpenCode's own `bash` tool to act without passing the `shell` consent gate. The
roadmap narrows the wildcard and keeps CloseCode's layer authoritative for device
actions, with an audit record for every call.

---

### 8. Agent documentation shipped to projects

| File | Written by | Content |
|---|---|---|
| `<project>/AGENTS.md` | `ai/OpenCodeAgentMd.kt:20`, `:114-126` | A marked block (`<!-- closecode:start(b43) -->`) listing the real `closecode` verbs and the phone rules; foreign text is preserved (`:134-151`) |
| `<project>/.opencode/skills/closecode/SKILL.md` | `ai/OpenCodeSkill.kt:16-18` | YAML front matter plus the same rules; only rewritten while it still carries the CloseCode marker (`:139-143`) |

Both are refreshed on every successful server start (`ai/OpenCode.kt:413-426`).

---

### 9. Known risks in the current integration

| Risk | Detail | Evidence |
|---|---|---|
| Watcher ancestor subscriptions | OpenCode watches `/data`, `/etc`, `/`; EACCES is expected but is a battery/performance risk | `docs/opencode-v2/stage-0-host-poc.md:237-249` |
| Port reuse after a crash | A stale listener can survive; the app reclaims it by `/proc` inspection | `ai/OpenCode.kt:869-947` |
| Two permission layers | See §7 | `ai/OpenCodeConfig.kt:51` |
| No LSP from OpenCode | `OPENCODE_DISABLE_LSP_DOWNLOAD=true` prevents OpenCode from fetching a server | `ai/OpenCode.kt:626-629` |
| Nothing is claimed without a device run | The V2 Web UI has been verified on an ARM64 host, not on a phone | `docs/opencode-v2/stage-0-host-poc.md:1-4`, `:199-205` |


---

# PART 6 — TOOL CATALOGUE

Status: build **80**.

Every capability the agent and the user can reach through CloseCode's command surface:
**51 command tokens**, **64 shell aliases**, one shared result envelope.

This catalogue is the human-readable view of the capability registry. From the registry
phase onward it is **generated from the registry itself**, so it cannot drift from the
code. Until then it is verified against the dispatch table in
`control/CloseCodeControl.kt:187-318`.

---

### 1. How a capability is described

| Field | Meaning |
|---|---|
| Command | The token the agent sends and the user types |
| Aliases | The shorter spellings the wrapper accepts |
| Purpose | What it does, in one line |
| Permission | The `ConsentStore` capability it needs, if any (`device/control/ConsentStore.kt:150-168`) |
| Agent | Whether the capability is offered to the agent by default, and why |
| Output | The shape it returns |

**Result envelope** — every command returns the same object (`control/CommandEnvelope.kt:10-46`):

```json
{ "ok": true, "artifacts": [], "errors": [], "warnings": [],
  "exitCode": 0, "durationMs": 0, "logPath": "…" }
```

Failures add `error`, `code`, and optionally `remedy` and `installCommand`.

**Exposure policy.** Commands marked *no* are not offered to the agent by default because
they act on the device, touch credentials, or control the app itself. They stay fully
available to the user through the CLI and the app. Every command states the reason.

---

### 2. Build and project

| Command | Aliases | Purpose | Permission | Agent | Notes |
|---|---|---|---|---|---|
| `build` | `b` | Build the project with the default engine | — | **yes** | Custom engine or Full Gradle; one at a time (`build/BuildCoordinator.kt:60-128`) |
| `run` | `r` | Build, then install and launch | install (granted) | **yes** | Same signing key as `build` |
| `clean` | — | Remove build outputs or caches | — | **yes** | `--gradle-caches` and `--sdk` are separate, explicit actions (`control/CloseCodeControl.kt:884-905`) |
| `check` | — | Static project check: manifest, SDK, package, plugin versions | — | **yes** | Reports `engine` and `errors` |
| `gradle` | `g` | Run a Gradle task (`assembleDebug`, `testDebugUnitTest`, `lint`, …) | — | **yes** | Without `--gradle` it runs static checks only (`control/CloseCodeControl.kt:1046-1053`) |
| `test`, `lint`, `format`, `package`, `release` | — | Ecosystem tasks: npm, pytest, ctest, cargo, … | — | **yes** | Routed by project type (`control/EcosystemCommand.kt:18`) |
| `init` | — | Scaffold a project from a template | — | **yes** | Templates: android-compose, web-vite, web-static, python-fastapi, python-cli, node-express |
| `describe` | — | Describe the project: type, engine, toolchain, missing parts | — | **yes** | The agent's first call for any new project |
| `doctor` | `doc` | Diagnostics with remedies | — | **yes** | — |
| `toolchain` | — | Toolchain status (`--json`) | — | **yes** | Lists installed components and missing ones |
| `setup` | — | Install or remove toolchain components | — | **yes** | One installer, two front ends with Settings (`control/SetupCommand.kt:60-190`) |
| `tool` | — | Run a build tool: `aapt2`, `d8`, `r8`, `kotlinc` | — | **yes** | 600 s timeout (`control/ToolBridge.kt:40`) |
| `tools` | — | List the available build tools | — | **yes** | — |
| `signing` | `sign` | Signing key status, generate, import, select, delete | — | **yes** | One key for every engine and build path |
| `selftest` | — | Run the on-device self-test | — | **yes** | 19 checks today; the registry adds one |

### 3. Device

| Command | Aliases | Purpose | Permission | Agent | Notes |
|---|---|---|---|---|---|
| `device` | — | Battery, network, storage, display, sensors | — | **yes** | Read-only facts |
| `logcat` | — | This app's logcat | — | **yes** | Another app's logcat is not readable on Android (`build/LogcatReader.kt:5-11`) |
| `screenshot` | — | Capture a screenshot | screen | **no** | MediaProjection; needs the user's consent (`device/control/ScreenControl.kt:18-22`) |
| `screen` | — | Start/stop screen recording | screen | **no** | Same consent |
| `notifications` | — | List, dismiss, reply to notifications | notifications | **no** | NotificationListenerService |
| `ui` | — | UI tree, tap, scroll, wait, assert | ui-automation | **no** | Accessibility-driven; asks first (`device/control/DeviceControl.kt:41-62`) |
| `test-ui` | — | Run a JSON UI script | ui-automation | **no** | Same gate |
| `input` | — | Tap, swipe, long-press, type, key | ui-automation | **no** | Same gate |
| `accessibility` | `a` | Accessibility service status | — | **no** | Diagnostic for the service itself |
| `apps` | — | List apps, app info, launch, stop, uninstall, clear | ui-automation for automation | **no** | `apps clear` is refused with a reason (`device/control/DeviceControl.kt:224-235`) |
| `app` | — | Status of the running app | — | **yes** | — |
| `install` | — | Install an APK | install (granted) | **yes** | Signature pre-check; the system dialog still appears |
| `launch` | — | Launch an app | ui-automation | **no** | — |
| `stop` | — | Stop the app | — | **yes** | — |
| `restart` | — | Restart the app | — | **no** | Controls the app the agent is running in |
| `stop-all` | — | Stop every running device job | — | **no** | Can cancel the agent's own work |
| `intent` | — | Open, start, broadcast, share an intent | ui-automation | **no** | — |
| `export` | — | Export project files | files | **no** | — |
| `files` | — | List, copy, share files in the shared folder | files | **no** | Scoped to `getExternalFilesDir(null)/shared` (`device/control/DeviceControl.kt:449-538`) |

### 4. Process and environment

| Command | Aliases | Purpose | Permission | Agent | Notes |
|---|---|---|---|---|---|
| `exec` | — | Run a shell command | **shell** (never self-granted) | **no** | 120 s default timeout, tree kill, destructive patterns need `--approve` (`device/control/DeviceControl.kt:690-786`) |
| `jobs` | — | List, stop, tail background jobs | — | **yes** | Logs in `files/jobs/<id>.log` |
| `serve` | — | Run a development server | — | **yes** | Background job; logs streamed |
| `preview` | — | Serve a static preview | — | **yes** | Local HTTP server, loopback only (`ui/project/PreviewServer.kt`) |
| `cancel` | — | Cancel the running build | — | **yes** | — |
| `status` | — | Server and app status | — | **yes** | — |
| `ping` | — | Liveness check | — | **yes** | — |

### 5. Git and GitHub

| Command | Aliases | Purpose | Permission | Agent | Notes |
|---|---|---|---|---|---|
| `github` | — | Clone, push, pull, repository info | network (granted) | **yes** | HTTPS only; the token never enters the repo (`ui/github/GitHubRemote.kt:148-178`) |
| `git` | — | — | — | — | **Not a command.** Git is used through the shell and the app's Git panel; the agent docs list only real verbs (`ai/OpenCodeAgentMd.kt:81`) |

### 6. Secrets, consent and audit

| Command | Aliases | Purpose | Permission | Agent | Notes |
|---|---|---|---|---|---|
| `secrets` | — | Encrypted secret vault | **secrets** (never self-granted) | **no** | `EncryptedSharedPreferences`-backed (`control/SecretStore.kt:13-61`) |
| `consent` | — | Ask about a capability | — | **yes** | Asks only; it never grants (`device/control/DeviceControl.kt:812-859`) |
| `audit` | — | Read the audit trail | — | **no** | JSONL at `files/audit/audit.jsonl` |
| `config` | — | Read and write `.closecode.json` | — | **yes** | Project settings |

### 7. Remote

| Command | Aliases | Purpose | Permission | Agent | Notes |
|---|---|---|---|---|---|
| `deploy` | — | Deploy over SSH (rsync/scp) | network (granted) | **no** | `--dry-run` or `--yes` required; audited; output currently discarded (defect D5) |

### 8. Coverage summary

| Group | Commands | Agent-visible by default | Not exposed, and why |
|---|---|---|---|
| Build and project | 20 | 20 | — |
| Device | 19 | 5 | Device automation, screen capture, notifications and file export act on the phone and need an explicit grant |
| Process and environment | 6 | 6 | `exec` is excluded until the permission layers are composed (defect D11) |
| Git and GitHub | 1 | 1 | — |
| Secrets, consent, audit | 4 | 2 | Secrets and the audit trail are not the agent's to read |
| Remote | 1 | 0 | A deploy reaches an external host |
| **Total** | **51** | **34** | **17** not exposed by default |

Every excluded command remains fully available to the user through the CLI and the app.
Exclusion is a default, not a removal: the registry records the reason next to each entry.

### 9. Adding a capability

1. Implement it once, in the place that owns the behaviour.
2. Add one registry entry: name, description, input and output schema, permission, target,
   timeout, cancellation, error codes, contract version, agent visibility and the reason.
3. The dispatcher, the CLI listing and this catalogue pick it up automatically.
4. Add a test that pins its schema and its result shape.

No command may be added only to the dispatch table, and no capability may be implemented
twice.


---

# PART 7 — TOOLCHAIN SUPPORT

Status: build **80**.

What CloseCode can install, run and build **on the phone**, what it can only do with help,
and what it must not claim. Sizes are the installed size on an arm64 device unless noted.

Three support levels are used throughout, as required by `checkmehere.md:51-53`:

- **Local** — runs on the device today.
- **Local with limits** — runs, with a documented restriction.
- **Remote-only** — needs a configured, authorised remote host.

---

### 1. Mobile

| Technology | Level | Runtime / compiler | Required SDK | Package manager | Build system | Installed size | Limits | Verification |
|---|---|---|---|---|---|---|---|---|
| Kotlin | Local | OpenJDK 17 + `kotlinc` from the toolchain store | — | — | — | ~250 MB (JDK 17) | — | `closecode toolchain status`; `kotlinc -version` |
| Java | Local | OpenJDK 17 | — | — | — | (same JDK) | — | `closecode selftest` check "java" |
| Android SDK | Local | Platform 34/35/36, build-tools 35.0.1, platform-tools 34.0.4 | platform 35 | Google's SDK archives | — | ~1.2 GB | Licences must be accepted; the app writes the four Google licence files | `closecode toolchain status --json` |
| Android Build Tools | Local | `aapt2`, `d8`, `r8` from build-tools | — | — | — | (with build-tools) | `aapt2` must be the arm64 build; the app writes `android.aapt2FromMavenOverride` so wrapper builds use it | `closecode tool aapt2 --version` |
| Android Gradle Plugin | Local (with Gradle) | AGP runs inside Gradle | — | — | Gradle | (with Gradle) | Project must declare a supported AGP/Kotlin/Compose version | `closecode check` |
| Gradle | Local | Gradle 8.13 (8.3–8.13 in the catalogue) | platform 35 + build-tools | — | Gradle | ~140 MB download, ~1.9 GB with the JDK and cache | The dependency cache lives in `files/gradle/home`; removing Gradle keeps it | `closecode toolchain status`; `gradle -v` |
| Android NDK | Local (optional) | NDK r27b, AndroidIDE aarch64 build | — | — | CMake | ~1.7 GB | C/C++ only; the custom engine cannot compile native code | `closecode setup --native`; `closecode check` |
| CMake | Local (optional) | CMake 3.22.1 with Ninja | — | — | CMake | ~73 MB | Paired with the NDK | `closecode setup --native` |
| Jetpack Compose | Local | Compose compiler plugin from the toolchain store | — | — | — | — | Kotlin and Compose versions are pinned and checked (`build/ProjectCheck.kt:255-299`) | `closecode check` |
| Flutter | **Remote-only** | Flutter SDK needs a desktop toolchain | — | — | Flutter | — | Not installed or supported on device | — |
| Dart | Local | Termux `dart` package | — | `pub` | — | ~600 MB with the pack | Optional pack; no Android app build from it | `closecode setup --dart` |
| React Native / Expo | **Remote-only** | Node toolchain plus the Android SDK; the RN build needs a desktop-class Gradle/Node stack | — | npm | Metro/Gradle | — | Web bundling is possible on device; the Android build is not claimed | — |

### 2. Web

| Technology | Level | Runtime | Package manager | Build system | Notes |
|---|---|---|---|---|---|
| HTML / CSS / JS | Local | Node 20+ (Termux `nodejs`), Python, `busybox` | — | — | Static previews served by `ui/project/PreviewServer.kt` |
| TypeScript | Local | Node + `tsc` via npm | npm | tsc | Node and npm are installed by the shell pack |
| React / Vue / Svelte / Angular / Astro / Next.js / Nuxt | Local with limits | Node | npm / pnpm / Yarn | Vite, webpack, Next | The **web** build and preview run on device; Next.js/Nuxt SSR servers run in the background job manager (`jobs/JobManager.kt:49`) |
| Tailwind / Bootstrap | Local with limits | Node | npm | PostCSS | Needs the CLI and a Node build |
| Vite / webpack | Local | Node | npm | — | `closecode build` routes through the ecosystem runner |
| Three.js / WebAssembly | Local with limits | Node or a browser | npm | — | Needs a bundler; the app's WebView can preview it |

### 3. Backend

| Technology | Level | Runtime | Package manager | Verification |
|---|---|---|---|---|
| Node.js | Local | Termux `nodejs` | npm, pnpm, Yarn | `closecode run`, `closecode build` |
| Python | Local | Termux `python` + repaired `ensurepip` | pip, uv, Poetry | `python3 -m venv` works after the installer's repair (`tools/PythonPackRepair.kt`) |
| Go | Local (optional) | Termux `golang` pack | Go modules | `closecode setup --go` |
| Rust | Local (optional) | Termux `rust` pack | cargo | `closecode setup --rust` |
| PHP | Local (optional) | Termux `php` pack | Composer | `closecode setup --php` |
| Ruby | Local (optional) | Termux `ruby` pack | RubyGems, Bundler | `closecode setup --ruby` |
| Java / Kotlin | Local | OpenJDK 17 | Gradle, Maven | Gradle is installed; Maven is not |
| C# / .NET | **Remote-only** | .NET SDK is not packaged for Android | NuGet | — |
| Elixir | **Remote-only** | BEAM toolchain | Hex | — |
| Spring Boot / Django / Flask / FastAPI / Express / NestJS / Laravel | Local with limits | The language runtime is local; the framework's own toolchain may not be | — | The framework runs; a full framework build may need a remote host |

### 4. Systems and other

| Technology | Level | Notes |
|---|---|---|
| C / C++ | Local with limits | Needs the NDK and CMake; only the Gradle engine can compile native code |
| Zig | **Remote-only** | Not packaged for Android |
| Swift / Objective-C | **Remote-only** | No Swift toolchain for Android; an Apple toolchain needs macOS |
| R | **Remote-only** | Not packaged for Android |
| Solidity | **Remote-only** | Foundry/solc are desktop toolchains |
| SQL / SQLite | Local | `sqlite3` ships in the media pack (`tools/OptionalPacks.kt`) |
| Bash / Shell | Local | `/system/bin/sh` plus the Termux shell pack |
| PowerShell / Lua | **Remote-only** | Not packaged for Android |

### 5. Package managers and build tools

| Tool | Level | Notes |
|---|---|---|
| npm, pnpm, Yarn | Local | Node and npm are installed by the shell pack |
| pip, uv, Poetry | Local | `uv` and `pip` are installed; `python3 -m venv` is repaired by the installer |
| Gradle | Local | Optional download, 8.13 default |
| Maven | **Remote-only** | Not installed |
| Pub | Local with limits | With the Dart pack |
| Cargo, rustup | Local with limits | With the Rust pack |
| Go modules | Local with limits | With the Go pack |
| Composer | Local with limits | With the PHP pack |
| RubyGems, Bundler | Local with limits | With the Ruby pack |
| GCC, Clang, CMake, Ninja | Local with limits | Clang comes with the NDK; CMake and Ninja with the CMake package |
| NuGet | **Remote-only** | Needs .NET |
| Swift Package Manager | **Remote-only** | Needs a Swift toolchain |
| Emscripten / WebAssembly tools | **Remote-only** | Desktop toolchain; plain `.wasm` builds are not claimed |

### 6. Installers CloseCode ships

| Installer | Size (download) | Size (installed) | Verification |
|---|---|---|---|
| Shell tools (git, curl, python, node, npm, zip, jq, openssh, rsync, tar, openssl, xz, uv) | ~120 MB | ~700 MB | `closecode setup --web`; `closecode selftest` |
| Media tools (ffmpeg, sqlite3, ImageMagick) | ~90 MB | ~350 MB | `closecode setup --media` |
| Language packs (go, rust, dart, php, ruby, deno, bun) | 20–700 MB each | larger installed | `closecode setup --<lang>` |
| JDK 17 + Gradle 8.13 | ~140 MB + ~90 MB | ~1.9 GB with the cache | `closecode setup --gradle` |
| JDK 21 (optional) | ~106 MB | ~250 MB | `closecode setup --java21` |
| Android platform 35 | ~63 MB | ~1.2 GB with the rest of the SDK | `closecode setup --gradle` |
| Build-tools 35.0.1 | ~55 MB | included above | `closecode setup --gradle` |
| Platform-tools 34.0.4 | ~7 MB | ~30 MB | `closecode setup --gradle` |
| NDK r27b | ~560 MB | ~1.7 GB | `closecode setup --native` |
| CMake 3.22.1 | ~50 MB | ~73 MB | `closecode setup --native` |
| OpenCode 2.2.5 + Alpine musl runtime | ~115 MB + ~15 MB | ~245 MB | `opencode --version` through the app |

Every installer verifies a SHA-256 checksum before use and resumes an interrupted download
from the byte it stopped at.

### 7. Rules CloseCode keeps

1. Nothing is downloaded automatically. The user asks; the app confirms before a large
   download.
2. The AI never installs, never writes a licence file by hand, and never needs `curl`,
   `tar` or `chmod` to set the phone up.
3. Sizes shown in Settings are measured from the files on disk, without following
   symlinks, so they match `du`.
4. A capability is never claimed because a class, a package or a button exists. Where this
   document says "Local", the verification column names the command that proves it.


---

# PART 8 — SECURITY MODEL

Status: audit snapshot for build **80** (working tree at `0d318ce` + uncommitted build-80 work).

Scope: how CloseCode protects the phone, the user's projects and the user's credentials.

Governing rule from `checkmehere.md:456-472` and the instruction for this round: **OpenCode's
permission ruleset and CloseCode's `ConsentStore` are two separate security layers. Neither
may bypass the other.**

---

### 1. The layers

```
┌────────────────────────────────────────────────────────────────┐
│ Layer 1 — OpenCode permissions (ai/OpenCodeConfig.kt:50-83)     │
│ Decides whether the AGENT may attempt a tool at all.            │
│ Rules: {action, resource, effect} → allow | ask | deny          │
└───────────────────────────────┬────────────────────────────────┘
                                │  (today: NOT composed — see §6)
┌───────────────────────────────▼────────────────────────────────┐
│ Layer 2 — CloseCode ConsentStore                                │
│ (device/control/ConsentStore.kt:150-168)                        │
│ Decides whether the PHONE performs the action, and records it.  │
└───────────────────────────────┬────────────────────────────────┘
                                │
┌───────────────────────────────▼────────────────────────────────┐
│ Layer 3 — AuditLog (device/control/AuditLog.kt:15-31)           │
│ Append-only JSONL: files/audit/audit.jsonl                      │
└────────────────────────────────────────────────────────────────┘
```

Layer 1 is OpenCode's own mechanism and lives in `opencode.json`. Layer 2 and 3 are
CloseCode's and live in the app. Both are required; neither replaces the other.

---

### 2. Layer 1 — OpenCode permissions

Written into `$XDG_CONFIG_HOME/opencode/opencode.json`, which the app grounds at
`files/opencode/home/config/opencode/` (`ai/OpenCode.kt:724-725`).

| Rule | Effect | Purpose |
|---|---|---|
| `{"action":"*","resource":"*","effect":"allow"}` | allow | Routine work on a phone must not stop for a dialog (`ai/OpenCodeConfig.kt:51`) |
| `external_directory` | ask | Anything outside the project |
| `read` on `*.env`, `*.env.*`, `**/.env`, `**/.env.*` | ask | Environment files hold secrets |
| `read`/`edit` on `**/*.pem`, `**/*.key`, `**/*.jks`, `**/*.keystore`, `**/*.p12`, `**/id_rsa`, `**/id_ed25519`, `**/.ssh/*`, `**/.gnupg/*`, `**/credentials*`, `**/secrets*` | ask | Credentials (`ai/OpenCodeConfig.kt:35-47`, `:58-61`) |
| `shell` with `rm *`, `rmdir *`, `shred *`, `truncate *`, `git clean *`, `git reset *`, `git push *`, `find * -delete*`, `chmod *`, `chown *`, `dd *`, `mkfs*`, `kill *`, `pkill *`, `killall *` | ask | Destructive work (`ai/OpenCodeConfig.kt:66-78`) |
| `shell` with `sudo *`, `su *`, `reboot *`, `shutdown *` | **deny** | Never available (`ai/OpenCodeConfig.kt:79-82`) |

Upgrade path: an existing V1 config is copied to `opencode.v1-backup.json` and its V1
`permission` map is replaced by the V2 `permissions` array; every other key is kept
(`ai/OpenCodeConfig.kt:136-175`). The upgrade runs once, stamped by
`files/opencode/home/config/opencode/.closecode-permissions` (`:120-134`); after the stamp
the file belongs to the user.

---

### 3. Layer 2 — CloseCode `ConsentStore`

| Capability | Default | Self-grant | Gate site |
|---|---|---|---|
| `build` | granted | allowed | `ai`/`control` build paths |
| `install` | granted | allowed | `build/InstallHelper.kt` |
| `network` | granted | allowed | `control/DeployCommand.kt:21-26` |
| `shell` | denied | **never** | `device/control/DeviceControl.kt:697-704` |
| `files` | denied | never | `device/control/DeviceControl.kt:449-457` |
| `secrets` | denied | **never** | `control/SecretsCommand.kt:14-20` |
| `ui-automation` | denied | **never** | `device/control/DeviceControl.kt:41-62` (protected-looking packages) |
| `screen` | denied | **never** | `device/control/ScreenControl.kt:18-22` |
| `notifications` | denied | never | `device/CloseCodeNotificationListenerService.kt:53-57` |

Mechanics (`device/control/ConsentStore.kt`):

- `request(capability, scope)` (`:98-108`) records an **ask**. It never grants.
- `resolve(request, approved)` (`:128-135`) is the only grant path, and only the app's own
  permissions screen calls it (`ui/settings/SettingsPages.kt:210-217`).
- `isGrantedFor` supports per-package scoping (`:46-72`).
- In the CLI, `grant`, `allow`, `request` and `ask` all funnel to `request()` plus a
  notification and return `needs_consent` (`device/control/DeviceControl.kt:812-859`).

**Consent is per capability and per package where relevant** — a grant to automate one app
is not a grant to automate another.

---

### 4. Layer 3 — the audit trail

`device/control/AuditLog.kt:15-31` appends one JSON record per device-control call to
`files/audit/audit.jsonl`. Readable with `closecode audit tail` and `closecode audit egress`
(`device/control/DeviceControl.kt:888-909`). `exec` and `files share` are recorded
(`:784`, `:528-536`); deploy records `egress deploy` with the host
(`control/DeployCommand.kt:197-203`).

---

### 5. Filesystem and secret protection

| Control | Behaviour | Evidence |
|---|---|---|
| Project path confinement | Rejects blank, absolute and control-character paths; compares canonical paths so a symlink cannot escape the project | `ui/project/ProjectSafety.kt:35-50` |
| Slug sanitisation | Rejects `.`, `..`, `/`, `\` so `deleteRecursively` cannot target the whole `filesDir` | `ui/project/ProjectSafety.kt:52-64` |
| Secret files are not writable | `local.properties`, `.env`, `*.jks`, `*.keystore`, `*.pem`, `google-services.json` | `ui/project/ProjectSafety.kt:8-15`, `:103-108` |
| Generated paths never staged or pushed | `.git`, `build`, `.gradle`, `node_modules`, `*.apk`, `*.jks`, … | `ui/project/GeneratedPaths.kt:7-17`, `:26-33`, `:46-53` |
| Read ceiling | 8 MB text, 32 MB image | `ui/project/ProjectSafety.kt:17-19`, `:22`, `:95-101` |
| Atomic writes | Temp file + rename | `ui/project/ProjectSafety.kt:74-88` |
| Token storage | `EncryptedSharedPreferences` (AES256-GCM/SIV) with a master key; a legacy plaintext store is adopted once and wiped; if the keystore fails the token lives only in process memory | `ui/github/TokenStore.kt:38-56`, `:62-78`, `:93-94`, `:109-132` |
| Secrets vault | `EncryptedSharedPreferences`-backed, in-process fallback | `control/SecretStore.kt:13-61` |
| Tokens never in the repo | Passed to JGit as a credentials provider, never written into `.git/config` | `ui/github/GitHubRemote.kt:148-178` |
| Deploy keys | Written to cache with owner-only permissions and deleted in a `finally` | `control/DeployCommand.kt:167-172`, `:83-85` |
| Destructive shell patterns | `rm -rf /`, `mkfs`, `dd if=`, `reboot`, `pm uninstall`, fork bomb, writes to `/dev/block` require `--approve` | `device/control/DeviceControl.kt:922-928`, `:728-734` |

---

### 6. Known weakness: the layers do not compose

OpenCode's wildcard rule (`ai/OpenCodeConfig.kt:51`) allows OpenCode's **own** `bash` tool to
run commands on the phone. Those commands do not pass through
`device/control/DeviceControl.kt:697-704` (the `shell` consent gate) and are not written to
`files/audit/audit.jsonl`.

Consequences:

1. An action the user would have to approve through `closecode exec` can be performed by
   OpenCode's shell tool without an ask.
2. The audit trail is incomplete for agent-driven shell work.

**Planned fix (roadmap phase 3), not applied in this round:**

- Narrow the wildcard so routine reads/edits inside the project stay allowed while the
  destructive patterns keep asking.
- Route agent shell work through CloseCode's own command surface wherever possible, so it
  is consented and audited.
- Keep OpenCode's ruleset as the first gate and `ConsentStore` as the second; a tool the
  agent may call is still refused by the phone unless the capability is granted.
- Add a self-test check that asserts a denied capability is refused through both paths.

No permission rule is changed in the Step 0/Step 1 round; this document records the current
state honestly.

---

### 7. Network policy

| Layer | Rule | Evidence |
|---|---|---|
| Android permissions | `INTERNET`, `ACCESS_NETWORK_STATE` | `app/src/main/AndroidManifest.xml` |
| Mobile data gate | Wi-Fi/Ethernet always; mobile data only when the user opted in | `build/AndroidNetworkGate.kt:13-26`; `tools/ToolchainStore` `KEY_MOBILE_ALLOWED` |
| Loopback only | The OpenCode server and the control socket bind `127.0.0.1` | `ai/OpenCodeModel.kt:111`; `control/CloseCodeControl.kt:91` |
| Token-authenticated control socket | 32-byte hex token, constant-time comparison | `control/CloseCodeControl.kt:182-186`, `:1706-1712` |
| HTTPS only for Git | `require(url.startsWith("https://"))` | `ui/github/GitHubRemote.kt:36-38`; `ui/github/GitHubClone.kt:36-38` |
| No cleartext exception | Only the loopback preview server and the OpenCode server use plain HTTP, both on `127.0.0.1` | `ui/project/PreviewServer.kt:106-122` |
| Agent egress | Everything the agent fetches goes through the local CONNECT proxy with the app's CA bundle | `ai/OpenCodeProxy.kt:263-283`; `ai/OpenCodeCa.kt:41-53` |
| Remote deploy | `network` consent + `--dry-run`/`--yes` + audit record | `control/DeployCommand.kt:21-27`, `:50-60`, `:197-203` |

**Planned for the MCP phase:** the MCP server binds loopback only, uses a fresh 256-bit
token per start, validates the `Origin` header, keeps the token in an app-private `0600`
file, and never logs it.

---

### 8. What the agent may never do

| Never | Why | Enforcement |
|---|---|---|
| Grant itself a capability | The app asks, the user answers | `device/control/ConsentStore.kt:98-108`, `:128-135` |
| Write a license file by hand | Installation is the app's job | `build/PlatformStore.kt:107-114` (the app writes the four Google license files) |
| Need `curl`, `tar`, `chmod` or a hand-written license to set the phone up | The installers download and verify everything | `tools/`, `build/PlatformStore.kt` |
| Use root, `sudo`, `su`, `reboot` or `shutdown` | Platform restriction and policy | `ai/OpenCodeConfig.kt:79-82` |
| Destroy a working install to install a new one | Installation is additive and resumable | `tools/GradleTools.kt:151-250`; `tools/ShellToolsStore.kt:60-158` |
| See a secret in ordinary output or logs | Secrets stay in the encrypted stores | `control/SecretStore.kt`; `ui/github/TokenStore.kt` |

---

### 9. Open items

1. Compose the two permission layers (defect D11) — roadmap phase 3.
2. Give every agent-visible tool a declared permission requirement, so a tool cannot be
   exposed without saying what it needs.
3. Record an audit entry for **every** agent-initiated action, not only device-control calls.
4. Re-verify the OpenCode permission API against the installed 2.2.5 build (host POC) before
   relying on the request/reply contract.


---

# PART 9 — TEST REPORT

Status: build **80** (working tree at `0d318ce` + uncommitted build-80 work).

The 14 acceptance tests from `checkmehere.md:530-586`, each with the environment, the
steps, the expected result, the actual result, the evidence, and a status of **Pass**,
**Fail** or **Blocked**.

**Status vocabulary**
- **Pass** — the behaviour was observed and matched the expectation.
- **Fail** — the behaviour was observed and did not match.
- **Blocked** — the test could not be run in this environment; nothing is assumed.

---

### 0. Environment

| Item | Value |
|---|---|
| Workspace | `/storage/internal_new/project/closecode`, commit `0d318ce` plus uncommitted build-80 work |
| Host | aarch64 Linux, 2 cores, 11 GB RAM |
| JDK | 17.0.20.1+1 at `/opt/java/jdk-17.0.20.1+1` |
| Android SDK | `/opt/android_sdk`, platform 35 |
| Gradle | 8.13 (wrapper) |
| Unit suite | **1611 tests, 0 failures, 0 errors, 8 skipped** — `app/build/test-results/testDebugUnitTest/*.xml` |
| Build | `:app:assembleDebug` BUILD SUCCESSFUL |
| Artifact | `build/CloseCode-build80-tests1611-debug.apk`, SHA-256 `eae8b48def5df9dc91ae3ad22994716c79e4e6962625651dba671404f67be465` |
| Device | The user's phone (arm64, Android). Runs quoted as "device run" are the user's own verification of builds 79 and 80. |
| OpenCode | 2.2.5, pinned, SHA-256 verified (`ai/OpenCodeModel.kt:88`, `:101-102`) |
| No Android device or emulator is attached to this workspace | — |

---

### TEST 1 — OpenCode connection

*OpenCode starts correctly, uses its configured model/provider, and can access the CloseCode
tool integration.*

| | |
|---|---|
| **Environment** | ARM64 host POC (pinned 2.2.5 through the musl loader) and the user's phone |
| **Steps** | 1. Launch CloseCode. 2. Open the AI tab. 3. The OpenCode server starts; the official V2 Web UI loads. 4. Send a prompt. 5. Run a `closecode` command from the agent. |
| **Expected** | The server starts, the UI loads, the prompt is answered by the user's configured provider, and a CloseCode command runs from the agent |
| **Actual** | Server start, health poll, auth bootstrap and the Web UI are implemented and host-verified (`docs/opencode-v2/stage-0-host-poc.md:91-112`, `:197-205`). The agent reaches CloseCode's 51 commands through the `closecode` wrapper on its PATH (`control/CloseCodeControl.kt:1723-1733`) |
| **Evidence** | `docs/opencode-v2/stage-0-host-poc.md`; `control/CloseCodeControl.kt:1736-1979` |
| **Status** | **Pass** for the connection and the command surface. **Blocked** for the Web UI rendering on a phone in this workspace — it was verified by the user on the device, not by CI |

### TEST 2 — Tool discovery

*OpenCode can discover available CloseCode tools and their valid input schemas.*

| | |
|---|---|
| **Expected** | The agent lists CloseCode's tools with schemas |
| **Actual** | **There is no machine-readable tool surface.** No MCP server, no tool registry, no schemas. The agent discovers the commands from prose in `AGENTS.md` and `SKILL.md` (`ai/OpenCodeAgentMd.kt:81-111`, `ai/OpenCodeSkill.kt:20-119`) |
| **Evidence** | Repo-wide search: `mcp`, `jsonrpc`, `tools/list`, `ToolRegistry`, `inputSchema` → 0 hits. `CLOSECODE_MISSING_CAPABILITIES.md` §1.1–1.2 |
| **Status** | **Fail** — this is the gap the capability registry and the MCP phase close |

### TEST 3 — File workflow

*Ask OpenCode to inspect a project, create a file, modify it, read it back, and report the
exact result.*

| | |
|---|---|
| **Environment** | Project workspace; JVM suite + device |
| **Steps** | `closecode init`, then create/modify/read a file, then check the project tree |
| **Expected** | The file is created inside the project, modified, read back, and reported with its real path |
| **Actual** | The file layer exists with path confinement, secret-file protection, atomic writes and read ceilings (`ui/project/ProjectSafety.kt:35-108`), scaffolding (`ui/project/ProjectStore.kt`), and a project description command (`control/ProjectDescription.kt:81`). The agent's own `read`/`write`/`edit` tools work inside the project directory |
| **Evidence** | `app/src/test/java/com/closecode/ui/project/`, `app/src/test/java/com/closecode/control/ProjectDescriptionTest.kt`; `CLOSECODE_CURRENT_CAPABILITIES.md` §2.6–2.7 |
| **Status** | **Pass** for the mechanisms, tested at the unit level. A full agent-driven round trip was not scripted in this workspace — **pending device verification** |

### TEST 4 — Terminal workflow

*Ask OpenCode to run a command, stream output, return the exit code, and cancel a
long-running process.*

| | |
|---|---|
| **Expected** | Output streams, the exit code is correct, a long process can be cancelled |
| **Actual** | Streaming and exit codes work (`ui/ide/IdeTerminal.kt:125`, `:133-137`, `:530`). Cancellation works in PTY mode (SIGINT) and partially in pipe mode (`ui/ide/IdeTerminal.kt:373-378`). **There is no command timeout** (defect D2) |
| **Evidence** | `ui/ide/IdeTerminal.kt`; `CLOSECODE_MISSING_CAPABILITIES.md` §4 (D1–D3) |
| **Status** | **Pass** for streaming and exit codes; **Fail** for the timeout requirement |

### TEST 5 — Git workflow

*Ask OpenCode to inspect Git status, create a branch, make a controlled change, and show the
diff.*

| | |
|---|---|
| **Expected** | Status, branch, change and diff are reported accurately |
| **Actual** | JGit provides status, diff, stage, commit, stash, branch and history (`ui/git/GitService.kt:81-423`); clone/push/pull live in `ui/github/` with encrypted credentials. Generated and secret paths are refused for staging and pushing (`ui/project/GeneratedPaths.kt:46-53`) |
| **Evidence** | `app/src/test/java/com/closecode/ui/`, `CLOSECODE_CURRENT_CAPABILITIES.md` §2.6 |
| **Status** | **Pass** |

### TEST 6 — Project detection

*Give OpenCode representative projects and verify it correctly identifies their technology
and required toolchain.*

| | |
|---|---|
| **Expected** | The technology, engine and missing toolchain are identified |
| **Actual** | `build/ProjectTypeDetector.kt`, `build/GradleReader.kt`, `build/ProjectCheck.kt:101` and `control/ProjectDescription.kt:81` report the project type, the engine that will be used, and the missing components; `closecode check` returns `engine:"gradle"`, `errors:0` (device-verified) |
| **Evidence** | `app/src/test/java/com/closecode/build/ProjectTypeDetectorTest.kt`, `ProjectCheckTest.kt`; device run |
| **Status** | **Pass** |

### TEST 7 — Build workflow

*Build at least one supported project using the actual compiler/build system and retrieve
the resulting artifact.*

| | |
|---|---|
| **Expected** | A real build with an exact artifact path |
| **Actual** | Two engines: the custom on-device pipeline (`build/BuildChain.kt:32`) and Full Gradle (`build/GradleRunner.kt:211`). Artifacts are located and reported with sizes (`build/BuildReport.kt:51`). The user's project built successfully: `BUILD SUCCESSFUL in 99 s`, exit 0 |
| **Evidence** | Device run; `app/src/test/java/com/closecode/build/BuildChainTest.kt`, `BuildReportTest.kt` |
| **Status** | **Pass** |

### TEST 8 — Error diagnosis

*Introduce a controlled build error. Verify OpenCode receives the real error, fixes it, and
reruns the build.*

| | |
|---|---|
| **Expected** | One root cause, diagnostics and a log path; a non-zero exit |
| **Actual** | `build/GradleDiagnostics.kt` extracts the root cause and its cause chain; `build/GradleRunner.kt:380-425` reports it; the build exits 1 (device-verified). The agent receives the same text through the command envelope and can fix and rerun |
| **Evidence** | `app/src/test/java/com/closecode/build/GradleDiagnosticsTest.kt`; device run |
| **Status** | **Pass** |

### TEST 9 — Android device workflow

*Where permissions and configuration allow, list a device, capture a screenshot, retrieve
Logcat, and install/launch a test APK.*

| | |
|---|---|
| **Expected** | The listed device operations work within the platform's limits |
| **Actual** | App list, app info, install (with signature pre-check), launch, stop, UI tree, tap, scroll, type, screenshot, screen recording, notifications, device facts and the app's own logcat are implemented (`device/DeviceTools.kt`, `device/control/DeviceControl.kt`). Logcat of **other** apps is impossible on Android; `apps clear` is refused with a reason |
| **Evidence** | `CLOSECODE_CURRENT_CAPABILITIES.md` §2.5; `app/src/test/java/com/closecode/device/` |
| **Status** | **Pass** for the implemented set; **Blocked** for other apps' logcat and `apps clear` (platform restriction, refused by design) |

### TEST 10 — MCP

*Connect a test MCP server, discover its tool, invoke it, and handle a connection failure.*

| | |
|---|---|
| **Expected** | A working MCP round trip |
| **Actual** | Not implemented. CloseCode registers no MCP server and the app contains no MCP client |
| **Evidence** | Repo-wide search → 0 hits for `mcp`, `jsonrpc`, `tools/list` |
| **Status** | **Blocked** — this is the next phase. The protocol details will be verified against the installed OpenCode 2.2.5 before implementation (`docs/opencode-v2/mcp-protocol-poc.md` is the planned evidence file) |

### TEST 11 — Permissions

*Verify that restricted and destructive operations are blocked or require approval.*

| | |
|---|---|
| **Expected** | Restricted operations are refused or asked |
| **Actual** | `ConsentStore` never self-grants `shell`, `secrets`, `ui-automation` or `screen`; the app asks and the user resolves (`device/control/ConsentStore.kt:98-135`). OpenCode's ruleset denies `sudo`/`su`/`reboot`/`shutdown` and asks for destructive shell patterns (`ai/OpenCodeConfig.kt:66-82`). Device control is audited (`device/control/AuditLog.kt:15-31`). **However**, the two layers do not compose: OpenCode's wildcard rule lets its own shell tool act without the `shell` gate or an audit entry (`ai/OpenCodeConfig.kt:51`) |
| **Evidence** | `CLOSECODE_SECURITY_MODEL.md` §6; `CLOSECODE_MISSING_CAPABILITIES.md` §4 (D11) |
| **Status** | **Pass** for CloseCode's own command surface; **Fail** for the composed system |

### TEST 12 — Process recovery

*Test app navigation, background execution, interruption, cancellation, and recovery without
leaving orphaned processes.*

| | |
|---|---|
| **Expected** | No orphaned processes |
| **Actual** | Builds are cancelled and their process tree killed (`build/GradleRunner.kt:317-334`, `build/ProcessTree.kt:12-27`); background jobs are stopped as a tree (`jobs/JobManager.kt:176-200`). **A job that died with the app is marked `lost` and its PID is never reaped** (defect D4). The OpenCode server *does* have a `/proc` reaper (`ai/OpenCode.kt:203-236`) |
| **Evidence** | `jobs/JobManager.kt:236-260`; `CLOSECODE_MISSING_CAPABILITIES.md` §4 |
| **Status** | **Fail** for orphaned background jobs; **Pass** for builds and for the OpenCode server |

### TEST 13 — Editor performance

*Measure opening and returning to the editor, including a representative project with syntax
highlighting and language intelligence enabled.*

| | |
|---|---|
| **Expected** | A measured resume time and memory figure |
| **Actual** | Analysis time is bounded by a test (a 190 k-character file analyses in under 1200 ms, JVM) (`lang/AnalysisPerformanceTest.kt:40`). Background parsing, debouncing, stale-job cancellation, windowed highlighting and lazy API-index loading are implemented. **Editor resume time is not instrumented**, and the editor subtree is destroyed on a panel switch, so returning is a cold resume (defect D10) |
| **Evidence** | `ui/ide/SoraEditor.kt:213-214`, `:663`, `:673`, `:1313-1329`; `ui/ide/IdeScreen.kt:8143-8147` |
| **Status** | **Partial** — analysis is measured; resume time is not |

### TEST 14 — Remote execution

*Only if remote execution is configured: run a task remotely, stream logs, cancel it, and
retrieve an artifact.*

| | |
|---|---|
| **Expected** | A remote run with streamed logs, cancellation and an artifact |
| **Actual** | No remote host is configured. `closecode deploy` transfers files over SSH/rsync and is gated by the `network` consent, with a dry-run mode and an audit record, but it does not run a task remotely, and its output is discarded today (defect D5) |
| **Evidence** | `control/DeployCommand.kt`; `CLOSECODE_MISSING_CAPABILITIES.md` §1.4 |
| **Status** | **Blocked** — no host is configured or authorised |

---

### Summary

| Test | Status |
|---|---|
| 1 OpenCode connection | Pass (phone-rendered UI verified by the user) |
| 2 Tool discovery | **Fail** — registry + MCP phase |
| 3 File workflow | Pass (unit level; device round trip pending) |
| 4 Terminal workflow | Pass for stream/exit code; **Fail** for timeout |
| 5 Git workflow | Pass |
| 6 Project detection | Pass |
| 7 Build workflow | Pass |
| 8 Error diagnosis | Pass |
| 9 Device workflow | Pass for the implemented set; Blocked for platform limits |
| 10 MCP | Blocked — next phase |
| 11 Permissions | Pass for CloseCode's own surface; **Fail** composed |
| 12 Process recovery | **Fail** — orphaned jobs |
| 13 Editor performance | Partial |
| 14 Remote execution | Blocked |

**5 of 14 fully pass, 4 are partial or fail for known reasons, 4 are blocked by a platform
limit or by work not yet done.** Every failure and block above maps to a numbered defect in
`CLOSECODE_MISSING_CAPABILITIES.md` §4 or to a phase in `CLOSECODE_IMPLEMENTATION_ROADMAP.md`.

### Supporting evidence in this repository

| File | What it records |
|---|---|
| `CLOSECODE_BUILD76_FIX_REPORT.md` | The build-76 fix round, in full |
| `CLOSECODE_BUILD76_FIX_STATUS.md` | The build-76 status summary |
| `CLOSECODE_BUILD78_RECHECK.md` | The user's independent recheck of build 78, including the failures that drove the build-80 work |
| `docs/opencode-v2/stage-0-host-poc.md` | The OpenCode V2 host POC: install, launch, auth, API surface, model inference, config shape, watcher behaviour |
| `docs/v10-review/` | Screen-by-screen analysis of the current and target UI |
| `ROADMAP.md` | The working backlog and resume notes |
| `HANDOFF.md` | The step-by-step build history |


---

# PART 10 — KNOWN LIMITATIONS

Status: build **80**.

Everything CloseCode cannot do, and why. Written so that no capability is ever claimed
because a button, a class or a package exists.

---

### 1. Android platform limits

| Limit | Consequence | Evidence |
|---|---|---|
| An app may only read **its own** logcat | Logcat of any other app is impossible; CloseCode reads its own | `build/LogcatReader.kt:5-11`, `:28` |
| No privileged API access | Another app's data cannot be cleared; `apps clear` is refused with a reason | `device/control/DeviceControl.kt:224-235` |
| Uninstalling another app needs the system dialog | CloseCode opens it rather than pretending | `device/control/DeviceControl.kt:199-222` |
| SELinux denies exec from `files/` on Android 10+ | CloseCode targets `minSdk/targetSdk = 28` on purpose | `app/build.gradle.kts:33-37` |
| No glibc | Desktop Linux binaries do not run; OpenCode ships as a musl build with its own loader | `ai/OpenCodeRuntime.kt:24-150` |
| Background execution is lifecycle-bound | A foreground service is `START_NOT_STICKY`; the app cannot be relied on to restart itself | `jobs/JobKeepAliveService.kt:42` |
| Read-only directories | `/data`, `/etc` and `/` cannot be watched; OpenCode logs EACCES and continues | `docs/opencode-v2/stage-0-host-poc.md:237-249` |
| Storage and RAM are finite | Large toolchains are optional and warn before downloading; a build can be slowed or killed by the platform | `tools/`, `build/PlatformStore.kt` |
| Battery and thermal limits | Long Gradle builds are the most likely thing to be interrupted | `build/GradleRunner.kt:219` (90-minute cap) |
| Only arm64 | OpenCode and the NDK builds are arm64-v8a only; x86_64 emulators are refused | `ai/OpenCodeBinary.kt:45-46`; `ai/OpenCode.kt:268-271` |

### 2. Capabilities CloseCode deliberately refuses

| Refused | Why | Evidence |
|---|---|---|
| `apps clear` | Needs root or adb | `device/control/DeviceControl.kt:224-235` |
| `sudo`, `su`, `reboot`, `shutdown` | Denied by policy | `ai/OpenCodeConfig.kt:79-82` |
| Granting a consent capability on the user's behalf | The app asks; the user answers | `device/control/ConsentStore.kt:98-108`, `:128-135` |
| Writing an SDK licence file by hand from the agent | Installation is the app's job | `build/PlatformStore.kt:107-114` |
| Destroying a working install to install a new one | Installation is additive and resumable | `tools/GradleTools.kt:151-250` |

### 3. Build and toolchain limits

| Limit | Consequence | Evidence |
|---|---|---|
| The custom on-device engine cannot compile native code | C/C++ projects need Full Gradle plus the NDK | `ai/OpenCodeSkill.kt:111-113` |
| The custom engine does not run the project's unit tests | `closecode gradle test` without `--gradle` says so plainly | `control/CloseCodeControl.kt:1259` |
| Groovy-only app modules are refused | `app/build.gradle` alone is not supported | `build/ProjectCheck.kt:141-151`; `build/GradleReader.kt:64` |
| Pinned Kotlin, Compose, compiler and plugin versions | An unsupported combination is refused rather than mis-built | `build/ProjectCheck.kt:255-299` |
| The Gradle cache lives on the phone | A cold cache makes the first build slow (minutes); the cache survives a Gradle removal but not a cache clear | `tools/GradleTools.kt` (`removeGradleRootExceptCache`) |
| `aapt2` must be the arm64 build | The app writes `android.aapt2FromMavenOverride`; a project that overrides it itself can break its own build | `build/GradleUserProperties.kt`; `build/AndroidProjectFiles.kt` |
| Signing uses one debug key | Release-grade signing is out of scope for the current build | `build/SigningStore.kt` |

### 4. Code-intelligence limits

CloseCode has **no language server**. What it has is a hand-written Kotlin analyser.

| Available | Not available |
|---|---|
| Syntax highlighting | Go to definition |
| Scope- and member-based completion | Find references |
| Heuristic diagnostics (11 rules, name- and type-literal based) | Rename symbol |
| Signature help from a generated Android API index | Real hover documentation |
| Import add/remove, re-indent | Semantic highlighting |
| | Refactoring |
| | Type checking (the analyser is lexical, not a compiler) |

Evidence: `lang/KotlinAnalysis.kt:53`, `lang/Diagnostics.kt:71`, `lang/Completion.kt:17`,
`ui/ide/SoraEditor.kt:1500`, `:628-695`. No LSP client exists anywhere in the project, and
the bundled OpenCode server is prevented from downloading one
(`OPENCODE_DISABLE_LSP_DOWNLOAD=true`, `ai/OpenCode.kt:626-629`).

### 5. Security limits

| Limit | Consequence | Evidence |
|---|---|---|
| Two permission layers that do not compose | OpenCode's own shell tool can act without the `shell` consent gate and without an audit record | `ai/OpenCodeConfig.kt:51`; `device/control/DeviceControl.kt:697-704` |
| `network` is granted by default | The agent may reach the network unless the user changes it | `device/control/ConsentStore.kt:161` |
| The keystore can fail | A GitHub token then lives only in process memory and is lost on restart | `ui/github/TokenStore.kt:93-94` |
| The deploy key is written to disk temporarily | It is deleted in a `finally`, but it exists on disk during the transfer | `control/DeployCommand.kt:167-172` |

### 6. Remote execution limits

| Limit | Consequence | Evidence |
|---|---|---|
| Only file transfer is remote today | `closecode deploy` moves files; it does not run a remote build or test | `control/DeployCommand.kt` |
| No remote task routing | Nothing decides local vs remote per task | no router exists |
| Password authentication is not supported for deploy | A key is required | `control/DeployCommand.kt:34-37` |
| Deploy output is discarded today | A failed deploy reports only an exit code | `control/DeployCommand.kt:271` (defect D5) |
| No macOS, Windows or GPU host is claimed | None is configured or tested | — |

### 7. Editor limits

| Limit | Consequence | Evidence |
|---|---|---|
| The editor subtree is destroyed on a panel switch | Returning to the editor is a cold resume (defect D10) | `ui/ide/IdeScreen.kt:2701`, `:2766-2768` |
| Completion re-analyses the whole file synchronously | Cost per popup on large files (defect D9) | `ui/ide/SoraEditor.kt:1500-1519` |
| Analysis is capped | Files above 200 k characters skip diagnostics; the project sweep stops at 60 files | `ui/ide/SoraEditor.kt:120`, `:657`; `ui/ide/IdeScreen.kt:8147` |
| `IdeScreen.kt` is 8297 lines | New editor features must be added in separate files | `ui/ide/IdeScreen.kt` |

### 8. Testing limits

| Limit | Consequence | Evidence |
|---|---|---|
| All 1611 tests are JVM tests | Android-runtime behaviour is not covered by CI | `app/src/test/java/com/closecode/` |
| One stock instrumentation test | No real device test exists | `app/src/androidTest/java/com/closecode/ExampleInstrumentedTest.kt` |
| No lint or static analysis task | Style and some correctness issues are not machine-checked | `app/build.gradle.kts`; `.github/workflows/build.yml:32`, `:35` |
| The test-count constant is hand-maintained | The version number could claim a count that was never run (defect D8) | `app/build.gradle.kts:16` |
| Device behaviour needs the user's phone | Claims about the phone are backed by the user's runs, not by CI | `CLOSECODE_BUILD78_RECHECK.md` |

### 9. What is planned to remove from this list

| Limitation | Planned removal |
|---|---|
| No tool catalogue | Registry phase |
| No MCP surface | MCP phase |
| Permission layers do not compose | Permission phase |
| Terminal drain, timeout, Ctrl-C, orphan jobs, deploy output, root delete | Reliability phase |
| No language server | Code-intelligence phase (hand-written analyser stays as the fallback) |
| No remote execution or task routing | Remote-execution phase |
| No lint task, drifting test count, no instrumentation | Quality phase |
