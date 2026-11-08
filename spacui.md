CLOSECODE — COMPLETE OPENCODE V2 MIGRATION AND ANDROID IDE INTEGRATION

1. Project objective

I want to completely replace the existing OpenCode V1 integration in CloseCode with a clean, properly engineered integration using the official OpenCode V2 codebase and runtime.

This is a major migration, not a small update.

The current implementation is pinned to OpenCode 1.18.35. I want the new implementation to use the latest verified stable OpenCode V2 release available when development begins. The V2 release line is already established; use the official V2 documentation and release repository as the source of truth.

Do not create another AI engine, chatbot, assistant, custom AI workspace or alternative AI interface.

OpenCode itself is the AI coding agent. CloseCode is the Android IDE.

The official OpenCode Web UI must remain the AI interface. CloseCode must provide the Android development environment around it.

Main requirements

- Completely remove the old OpenCode V1 integration after a safe migration.
- Install and run the official OpenCode V2 runtime locally on Android.
- Use the official OpenCode V2 Web UI.
- Connect the selected CloseCode project automatically.
- Support OpenCode's native models, providers, sessions, agents, commands, tools, skills, plugins and MCP functionality where compatible.
- Provide reliable server startup, monitoring, recovery and updating.
- Integrate OpenCode with CloseCode's existing editor, terminal, files, build system, preview and Git tools.
- Preserve all unrelated working IDE functionality.
- Build a downloadable Android APK that installs and works without requiring the user to install Termux or another terminal application.

Do not claim that a feature works merely because its button or settings screen exists. Test actual behavior.

---

2. Official references

Use these resources throughout development:

- Official OpenCode V2 documentation: https://opencode.ai/v2/docs
- Official V2 migration guide: https://opencode.ai/v2/docs/migrate-v1/
- Official installation guide: https://opencode.ai/v2/docs
- Official server documentation: https://docs.opencode.ai/docs/server/
- Official SDK documentation: https://docs.opencode.ai/docs/sdk/
- Official V2 configuration: https://opencode.ai/v2/docs/config
- Official V2 tools: https://opencode.ai/v2/docs/tools
- Official V2 permissions: https://opencode.ai/v2/docs/permissions
- Official V2 MCP documentation: https://opencode.ai/v2/docs/mcp-servers
- Official V2 providers: https://opencode.ai/v2/docs/providers
- Official V2 skills: https://opencode.ai/v2/docs/skills
- Official V2 agents: https://opencode.ai/v2/docs/agents
- Official releases: https://github.com/anomalyco/opencode/releases
- Official source code: https://github.com/anomalyco/opencode
- AndroidIDE reference: https://play.google.com/store/apps/details?id=com.m4coding.ide

Additional Android compatibility research:

- https://github.com/DEAD1nsane/opencode2-termux-musl
- https://github.com/anomalyco/opencode/issues/47612

The community Android projects are research references, not substitutes for the official OpenCode implementation. Review their installation patches and compatibility findings. Do not copy code without checking its licence.

---

3. Phase 1 — Audit the existing CloseCode source code

Before changing anything, inspect the entire existing repository.

The uploaded source contains a Kotlin and Jetpack Compose Android application.

Inspect these OpenCode-related files in particular:

- "app/src/main/java/com/closecode/ai/OpenCode.kt"
- "ai/OpenCodeRuntime.kt"
- "ai/OpenCodeBinary.kt"
- "ai/OpenCodeService.kt"
- "ai/OpenCodeSessions.kt"
- "ai/OpenCodeModelChoices.kt"
- "ai/OpenCodeAiConfig.kt"
- "ai/OpenCodeConfig.kt"
- "ai/OpenCodePageLog.kt"
- "ui/ide/OpenCodeChatScreen.kt"
- "ui/ide/OpenCodeTab.kt"
- "control/CloseCodeControl.kt"
- "control/ToolBridge.kt"
- "control/ToolWrappers.kt"
- "build/ToolchainStore.kt"
- "tools/ShellToolsStore.kt"
- "app/build.gradle.kts"
- "app/src/main/AndroidManifest.xml"
- "app/src/main/assets/toolchain.json"
- "app/src/main/assets/shell-tools.json"

Also inspect every other reference to OpenCode, its server port, runtime, download manager, WebView, authentication, process handling and project association.

Produce an inventory showing:

1. Every old OpenCode source file.
2. Every OpenCode-specific dependency.
3. Every downloaded binary and runtime library.
4. Every configuration and preference key.
5. Every database or file used to store OpenCode state.
6. Every WebView route and server endpoint.
7. Every background service and receiver.
8. Every integration with the CloseCode editor, terminal, project manager and permission system.
9. Every old API request and response assumption.
10. Every feature that can be retained independently of V1.

Do not delete code until its dependencies and callers have been identified.

Create a migration branch and a rollback point before starting the replacement.

---

4. Phase 2 — Remove OpenCode V1 completely

I want the old OpenCode implementation removed, not left running in parallel with V2.

Remove the old V1-specific implementation, including:

- The old V1 binary download and installation logic.
- The old V1 version pin and release URL logic.
- V1-only runtime assumptions.
- V1-only configuration adapters.
- V1-only API clients and response parsing.
- V1-specific model and session workarounds.
- V1-specific WebView routing and startup assumptions.
- Old process wrappers that are no longer required.
- Old V1-specific plugins or integration code.
- Old temporary files and obsolete downloaded runtime packages.
- Old V1-only preferences, caches and state.

Do not simply change the version constant from "1.18.35" to "2.x".

That would not constitute a real migration.

Important migration rules

- Do not delete the entire "ai" package if it contains unrelated functionality.
- Do not delete the existing terminal.
- Do not delete the project manager.
- Do not delete the editor.
- Do not delete Git or GitHub support.
- Do not delete the existing Android build system.
- Do not delete the toolchain manager.
- Do not delete the local control bridge merely because it is not MCP.
- Do not delete user projects or source files.
- Do not silently delete authentication credentials.

Back up old OpenCode configuration and authentication data before migrating.

Start V2 with a clean configuration. Offer an explicit migration of compatible user credentials or settings where appropriate. Never copy V1 plugin code into V2 and assume it will work.

Remove the old V1 runtime binaries and libraries after V2 passes the required tests.

The final application must have one authoritative OpenCode installation, one server lifecycle manager and one active OpenCode integration.

---

5. Phase 3 — Implement the official OpenCode V2 runtime

This is the most important technical challenge.

OpenCode V2 must run locally on the Android device without requiring root access.

5.1 Verify Android compatibility first

The official OpenCode V2 downloads include Linux ARM64 and Linux musl ARM64 builds. Android is not listed as an officially supported download platform.

Do not assume that a Linux binary automatically works on Android.

Investigate:

- Android CPU architecture.
- Android's Bionic libc.
- Linux musl compatibility.
- Dynamic ELF interpreter requirements.
- PIE executable requirements.
- Node.js runtime requirements.
- Native Node dependencies.
- PTY support.
- File permissions.
- Process execution restrictions.
- DNS resolution.
- TLS certificate validation.
- Filesystem access.
- WebSocket and HTTP support.
- Android background process restrictions.

Primary device target: ARM64 Android.

Support x86_64 Android only if it can be verified. Do not pretend that ARM64 and x86_64 binaries are interchangeable.

5.2 Select the correct distribution strategy

Investigate the official V2 release assets and package architecture.

Preferred order:

1. Official prebuilt V2 binary that has been proven to work on Android.
2. Official V2 Linux musl ARM64 binary with a verified Android-compatible runtime and necessary compatibility adaptations.
3. A reproducible build of the official V2 source targeting a compatible Android/Linux musl environment.
4. A documented compatibility layer if upstream does not provide a directly usable Android build.

Do not use an old V1 binary, an unofficial modified AI engine or a different coding agent as a replacement.

Do not download the Windows desktop application and attempt to run it inside Android.

5.3 Runtime installation manager

Create a dedicated runtime installation manager.

Suggested package:

"com.closecode.opencode.runtime"

Responsibilities:

- Detect CPU architecture.
- Check Android version and ABI.
- Resolve the selected OpenCode V2 version.
- Download the correct release.
- Download required runtime dependencies.
- Verify file size and SHA-256.
- Extract safely.
- Set correct executable permissions.
- Validate the binary.
- Run "--version".
- Run "--help".
- Start a test server.
- Check server health.
- Record installation state.
- Support rollback.
- Support updates.
- Remove incomplete downloads.
- Recover from interrupted installation.

Never install an unverified executable.

Use HTTPS, pinned version metadata, SHA-256 checksums and atomic file replacement.

Store installed runtime files in an app-private directory. Do not require root.

5.4 First installation experience

When a user opens OpenCode for the first time:

1. Check whether a compatible V2 runtime is already installed.
2. Check whether its required libraries are present.
3. Show the detected architecture.
4. Show the OpenCode version being installed.
5. Show required download sizes where available.
6. Show download progress.
7. Verify downloaded files.
8. Extract and install.
9. Run a version test.
10. Run a server health test.
11. Open the official OpenCode Web UI.

If anything fails, show the exact error and a useful recovery action.

Do not display a fake successful installation.

---

6. Phase 4 — Runtime dependencies and downloadable components

I want CloseCode to have a proper component manager.

The app should be able to download the tools and dependencies required for development rather than expecting everything to be preinstalled.

However, separate the OpenCode runtime from the Android development toolchain.

6.1 OpenCode runtime components

Investigate and package only the components actually required by the selected V2 build:

- OpenCode V2 executable/runtime.
- Compatible Node.js runtime if required by the distribution.
- Compatible musl loader and libraries if required.
- Required C/C++ runtime libraries.
- Required native modules.
- Required PTY implementation.
- CA certificates.
- DNS/network compatibility components if needed.
- Web assets if they are not embedded in the release.
- Required executable helper scripts.

Do not download Node.js or Bun automatically if the selected standalone OpenCode build already contains everything it needs.

Verify this from the actual release package and dependency analysis.

6.2 Optional shell and development packages

Create an on-demand package manager for:

- Git.
- Curl.
- Wget.
- Python 3.
- Node.js.
- npm or compatible package manager.
- Zip and unzip.
- jq.
- CA certificates.
- Other commonly required shell utilities.

Support installing and removing packages individually.

Do not bundle every possible development package into the APK.

6.3 Package manifest

Use a machine-readable manifest for all downloadable components.

Each component should have:

- Component ID.
- Display name.
- Version.
- Description.
- Supported CPU architectures.
- Supported Android versions.
- Download URL.
- File size.
- SHA-256 checksum.
- Licence.
- Dependencies.
- Installation directory.
- Installation verification command.
- Update policy.
- Removal policy.

Never use an unverified third-party download as an official OpenCode release.

6.4 Component manager interface

Create a professional Downloadable Tools / Components page in CloseCode.

Display:

- Installed components.
- Available components.
- Version.
- Download size.
- Installed size.
- Update availability.
- Compatibility status.
- Download progress.
- Install / Update / Remove actions.
- Dependency information.
- Error details.

Do not make the user manually find random binaries on the internet.

---

7. Phase 5 — OpenCode V2 server and official Web UI

Use the real OpenCode V2 server.

The OpenCode server is the central engine. It exposes an OpenAPI 3.1 specification and can serve the official Web UI.

Use the official server documentation and inspect the actual V2 server API before implementing the client.

Server requirements

- Start the V2 server locally.
- Bind to loopback only by default.
- Use a stable, configurable port.
- Check whether the port is available before startup.
- Record the process ID and server generation.
- Verify the actual server version.
- Check "/global/health".
- Check "/doc".
- Detect startup failures.
- Detect unexpected process termination.
- Stop the server cleanly.
- Reclaim the port safely.
- Avoid killing unrelated processes.
- Recover after app restart where Android allows it.

Do not assume that the old port "4098" or the old URL paths are correct for V2. Choose a port after checking conflicts and validate all routes against the actual V2 server.

Official Web UI

The original OpenCode interface must be used.

Do not build a replacement chat interface.

Do not replace its model picker, session interface, provider interface or built-in tools with custom CloseCode screens.

The existing Android WebView should be audited and upgraded to load the actual V2 Web UI.

Verify:

- Initial loading.
- Authentication.
- WebSocket connections.
- Streaming responses.
- Tool execution.
- File changes.
- Session selection.
- Model selection.
- Provider management.
- Settings.
- Navigation.
- Browser back behavior.
- Project switching.
- Recovery after WebView reload.
- Recovery after server restart.

Use a controlled local origin and proper authentication. Do not expose the server to the public internet by default.

Use the official pairing/authentication mechanism where applicable, or a properly secured local authentication mechanism compatible with V2.

---

8. Phase 6 — Connect CloseCode to the V2 server API

Do not build a parallel AI backend.

Use the official V2 server API and, where suitable, its official SDK.

The SDK is documented as a TypeScript client for communicating with an existing OpenCode server.

For Android-native integration, either:

- Generate a Kotlin client from the server's OpenAPI specification; or
- Implement a small, typed HTTP/SSE client based on the exact V2 API contract.

Do not assume the V1 API response shapes remain valid.

Required API capabilities

Integrate and test the relevant official endpoints for:

- Health and version.
- Current project.
- Project listing.
- Configuration.
- Providers.
- Provider authentication.
- Models.
- Sessions.
- Messages.
- File operations.
- Commands.
- Tools.
- Agents.
- MCP status.
- LSP status.
- Formatter status.
- Events.
- Authentication.

Use the server's live "/doc" specification as the authoritative contract.

Streaming and events

Implement reliable event-stream handling.

Support:

- Streaming assistant responses.
- Tool-start events.
- Tool progress.
- Tool completion.
- Permission requests.
- Errors.
- Session updates.
- File changes.
- Connection loss.
- Reconnection.
- Duplicate event protection.

Do not use frequent polling as a substitute for a proper event stream where the API supports it.

---

9. Phase 7 — Project integration

OpenCode must automatically use the project currently selected in CloseCode.

When a user opens a project:

1. Resolve the project's actual directory.
2. Start or reuse the correct OpenCode server.
3. Associate the server with that project.
4. Open the official Web UI.
5. Restore the appropriate OpenCode session.
6. Keep the editor and file manager synchronized.

When the user switches projects:

1. Save the previous IDE state.
2. Save editor tabs and positions.
3. Preserve the previous OpenCode session.
4. Switch the active project context.
5. Open or restore the appropriate OpenCode session.
6. Ensure commands execute in the new project directory.
7. Never accidentally edit the previous project.

Support multiple projects and independent sessions.

Do not assume that changing the WebView URL alone changes the actual OpenCode working directory.

Verify the working directory through the server's supported project and path APIs.

---

10. Phase 8 — OpenCode permissions and project safety

Use OpenCode V2's native permission system.

My required behavior:

- Full read/write access inside the selected project for ordinary development.
- Allow normal code creation and modification.
- Allow routine project inspection.
- Allow ordinary development commands where safe.
- Ask before important file deletions.
- Ask before risky shell commands.
- Ask before accessing files outside the selected project.
- Never silently operate on a different project.

V2 uses its newer "permissions" configuration and actions such as "read", "edit", "shell" and "external_directory".

Do not copy V1 permission syntax such as "permission", "bash" or "task" into a new V2 configuration.

Create a project-aware configuration that:

- Allows appropriate read/edit operations inside the project.
- Uses an explicit "external_directory" approval rule.
- Requires approval for destructive commands.
- Uses a narrow allowlist for routine commands.
- Protects ".env", private keys and credentials.
- Makes permission requests visible in the official UI.
- Handles approval and denial correctly.

Important: OpenCode shell tools execute with the authority of the host process. A project working directory is not an operating-system security sandbox. Do not claim that a path rule alone prevents every possible escape through shell commands.

Use CloseCode-side checks and approval controls as an additional safety layer. Never silently elevate permissions.

---

11. Phase 9 — Providers, models and authentication

Use OpenCode's native provider system.

Do not create a second provider implementation in CloseCode.

The official OpenCode provider functionality should handle:

- Provider discovery.
- Provider connections.
- API key authentication.
- OAuth authentication where supported.
- Model discovery.
- Model selection.
- Default model selection.
- Provider switching.
- Authentication status.
- Authentication errors.
- Model availability.

Ensure the user can access the original provider and model settings through the Web UI.

Preserve credentials securely.

Do not put API keys in source code, plain-text logs, URLs or project files.

Do not promise unlimited free AI usage. Model access and usage limits are determined by the selected provider and its account.

---

12. Phase 10 — MCP, plugins, skills and external tools

OpenCode V2 supports extensions. Integrate its native systems instead of building a separate extension engine.

MCP

Support:

- Remote MCP servers.
- Local MCP servers where compatible.
- MCP connection status.
- Authentication.
- Add/remove.
- Enable/disable.
- Error reporting.
- Tool discovery.
- Permission requests.

Remote MCP servers are particularly useful on Android because they do not necessarily require installing another local runtime.

Local MCP servers may require Node.js, Python, Bun or other runtimes. Detect these dependencies and offer installation through the component manager.

Do not install dozens of MCP servers automatically. Let users install the ones they need.

Plugins

Use the V2 plugin API only.

V1 plugins must not be assumed compatible.

Inspect the official V2 plugin documentation and examples before adding any plugin.

Skills and agents

Support OpenCode's native skills and agent configuration.

Allow project-specific and global skills according to OpenCode's documented directory conventions.

REA

Investigate REA separately as a development/research tool.

Determine whether it is best integrated as:

- An MCP server.
- A local executable tool.
- A project-specific command.
- A developer-only integration.

Do not assume REA is an OpenCode plugin or MCP server without verifying its actual interface.

Do not make REA a second AI assistant.

---

13. Phase 11 — Android development toolchain

This is separate from OpenCode itself.

CloseCode must remain a capable Android IDE.

Do not remove the existing toolchain just because OpenCode V1 is being replaced.

Audit the current toolchain manifest and implement a reliable component manager.

Required Android build components

Investigate and support the appropriate versions of:

1. Java runtime / JDK 17 or a verified compatible version.
2. Gradle distribution.
3. Gradle Wrapper.
4. Android SDK command-line tools where usable.
5. Android SDK platform.
6. Android SDK Build Tools.
7. Android platform "android.jar".
8. AAPT2.
9. D8.
10. R8.
11. ZIP alignment tools.
12. APK signing tools.
13. AndroidX and other project dependencies.
14. Kotlin compiler.
15. Kotlin standard library.
16. Kotlin reflection where required.
17. Compose compiler/plugin compatibility.
18. Java compiler / ECJ where required.
19. Required repositories and dependency metadata.

NDK and CMake

I also want you to investigate Android NDK and CMake support.

These are native development tools:

- Android NDK: compiles C and C++ code for Android.
- CMake: configures and builds native C/C++ projects.
- Ninja: a fast native build executor commonly used with CMake.
- LLDB: native debugging.

They are not required for every ordinary Kotlin/Java Android project.

Install them on demand when a project uses native code or explicitly requires them.

Do not download the full NDK automatically for every user.

Build workflows

Support the relevant Gradle tasks, including:

- "./gradlew tasks"
- "./gradlew assembleDebug"
- "./gradlew assembleRelease"
- "./gradlew bundleRelease"
- "./gradlew test"
- "./gradlew lint"
- "./gradlew clean"

Use the project's own Gradle Wrapper where appropriate.

Do not force a random Gradle version onto a project.

Read the project's Gradle wrapper, Android Gradle Plugin, Kotlin and Java compatibility requirements before building.

Show actual build output and errors.

Important compatibility issue

The current CloseCode project intentionally targets Android API 28 because it executes Linux-compatible binaries from app-private storage. The current source also uses legacy JNI library packaging for executable AAPT2 handling.

Do not casually raise the target SDK or change native library packaging.

Investigate the consequences first and test the complete OpenCode runtime and build toolchain after any change.

---

14. Phase 12 — Terminal and package management

Keep the existing CloseCode terminal, but audit and improve it.

Support:

- Persistent terminal sessions.
- Multiple terminal tabs.
- Command history.
- Autocomplete where practical.
- Correct working directory.
- Background processes.
- Process cancellation.
- Environment variables.
- Readable output.
- Error handling.
- Long-running Gradle tasks.
- Package installation.
- Runtime selection.

The terminal should work with the same project directory used by the editor and OpenCode.

Avoid running multiple competing shells or duplicate package managers without a clear reason.

Keep the OpenCode process lifecycle separate from ordinary user terminal processes.

---

15. Phase 13 — Editor and IDE integration

Keep CloseCode's existing IDE functionality and improve it.

Required areas:

Code editor

- Better syntax highlighting.
- Autocomplete.
- Code formatting.
- Diagnostics.
- Go to definition.
- Find references.
- Rename symbols.
- Project-wide search.
- Multiple tabs.
- Split editing where practical.
- Large-file performance.

File manager

- Preserve its working functionality.
- Synchronize file changes made by OpenCode.
- Refresh after external modifications.
- Handle create, rename, delete and move operations correctly.

Build system

- Reliable Gradle execution.
- Progress indicators.
- Clear errors.
- Build history.
- Cancellation.
- Recovery.

APK testing

- Debug and release APK generation.
- Installation.
- Launch.
- Uninstall.
- Logs and crash reports.

Preview

- Live preview where technically supported.
- Appropriate Android screen sizes.
- Portrait and landscape.
- Web project preview.
- Clear unsupported-feature messages.

Git and GitHub

- Commit.
- Push.
- Pull.
- Branches.
- Diffs.
- Merge conflict handling.
- Repository cloning.
- GitHub authentication.
- Issues and pull requests where supported.

OpenCode changes must appear in the editor and file manager without requiring a full app restart.

---

16. Phase 14 — Server lifecycle and Android reliability

Implement a dedicated V2 lifecycle manager.

Suggested responsibilities:

- Install.
- Start.
- Stop.
- Restart.
- Health checks.
- Version verification.
- Port management.
- Process tracking.
- Log collection.
- Automatic recovery.
- WebView reconnection.
- Project switching.
- App restart recovery.

Use Android-compatible background execution.

Use a foreground service and notification for supported long-running tasks where necessary. Respect Android background restrictions.

Do not assume an ordinary background service can keep a process alive forever.

Recovery procedure

When the server stops:

1. Detect that the server is unavailable.
2. Check whether the process is still alive.
3. Check whether the port is occupied.
4. Determine whether the process belongs to CloseCode.
5. Attempt a safe restart.
6. Verify the new version and health.
7. Reconnect the WebView.
8. Restore the correct project and session.
9. If recovery fails, show controls and diagnostic logs.

Never kill an unrelated process just because it owns the old port.

Save useful logs to an app-private location.

---

17. Phase 15 — Updates and version management

Do not rely on OpenCode's automatic self-update mechanism to manage an embedded Android runtime.

CloseCode should own the runtime installation lifecycle.

Create a version manager that can:

- Check official releases.
- Identify the latest compatible V2 version.
- Display installed and available versions.
- Download updates.
- Verify checksums.
- Install atomically.
- Preserve user configuration.
- Back up important data.
- Roll back to the previous working version.
- Remove obsolete runtime files.

Use a tested release version rather than blindly following the development branch.

The version should be pinned in a central manifest and updated through a controlled release process.

Keep the OpenCode version independent from the CloseCode APK version.

---

18. Phase 16 — Security and privacy

Required protections:

- App-private runtime and configuration directories.
- No embedded API keys.
- Secure credential storage.
- Localhost-only server binding by default.
- Proper server authentication.
- No public network exposure by default.
- WebView navigation restrictions.
- No unnecessary JavaScript bridge.
- No release-build WebView debugging.
- Scoped permissions.
- Safe archive extraction.
- Download checksums.
- No arbitrary executable installation.
- No secrets in logs.
- Safe project switching.
- Approval for external directories and risky operations.

Remember that the OpenCode server and shell tools run with the Android application's process permissions. Treat the runtime as powerful code, not as an isolated virtual machine.

---

19. Phase 17 — Testing requirements

Do not mark this migration complete until the following tests have passed.

Runtime tests

- Fresh installation.
- Existing installation detection.
- ARM64 compatibility.
- Version reporting.
- Help command.
- Required native libraries.
- Certificate handling.
- DNS resolution.
- Server startup.
- Server health.
- Server shutdown.
- Restart.
- Port recovery.
- Interrupted download.
- Checksum failure.
- Update.
- Rollback.

OpenCode tests

- Official Web UI loads.
- Authentication works.
- Models load.
- Providers load.
- Provider login works.
- Session creation works.
- Existing session restoration works.
- Streaming responses work.
- Tool calls work.
- Permission requests work.
- File edits work.
- MCP connection works.
- Plugin compatibility is verified.
- Skills load correctly.
- Server event stream reconnects.

CloseCode integration tests

- Open project.
- Open OpenCode.
- Automatically select current project.
- Switch project.
- Restore the correct session.
- Edit file in OpenCode.
- See changes in editor.
- Build modified project.
- Run terminal command.
- Install generated APK.
- Recover after server crash.
- Recover after force-stop.
- Recover after Android restart where supported.

Android device tests

Test on a real Android phone, not only an emulator.

Record:

- Android version.
- CPU architecture.
- Available RAM.
- Available storage.
- Battery usage.
- Server startup time.
- Runtime download size.
- Memory usage.
- Build time.
- Crash logs.

---

20. Implementation order

Work in these phases. Do not attempt to replace everything in one uncontrolled change.

Phase A — Source audit and migration plan

- Inspect the repository.
- Create backup and migration branch.
- Identify V1 dependencies.
- Verify the official V2 release and Android compatibility.

Phase B — V2 runtime proof of concept

- Obtain the correct binary.
- Test execution on Android.
- Resolve native dependency issues.
- Prove server startup and health.

Phase C — Runtime installation manager

- Downloads.
- Checksums.
- Extraction.
- Installation.
- Updates.
- Recovery.

Phase D — Official V2 server and Web UI

- Server process management.
- Authentication.
- WebView.
- Streaming.
- Reconnection.

Phase E — Project and API integration

- Project selection.
- Session restoration.
- Typed API client.
- Events.
- Providers and models.

Phase F — MCP and extensions

- MCP manager.
- Plugins.
- Skills.
- Agents.
- Optional REA integration.

Phase G — Android toolchain and IDE

- Gradle.
- SDK.
- Build Tools.
- AAPT2.
- D8/R8.
- Optional NDK/CMake.
- Terminal.
- Editor and Git integration.

Phase H — Cleanup and security

- Remove V1 completely.
- Remove obsolete files and packages.
- Audit permissions.
- Protect configuration and credentials.

Phase I — QA and APK release

- Run automated tests.
- Run real-device tests.
- Produce release APK.
- Provide test report.

---

21. Required final deliverables

At completion, provide:

1. Updated CloseCode source code.
2. New OpenCode V2 runtime manager.
3. Downloadable component manager.
4. Official OpenCode V2 Web UI integration.
5. Project-aware server management.
6. API integration.
7. MCP and extension integration where compatible.
8. Android development toolchain improvements.
9. Updated configuration and manifests.
10. Automated tests.
11. A working debug APK.
12. A working release APK.
13. A list of all downloaded dependencies and their licences.
14. A list of all removed V1 files.
15. A list of all retained IDE components.
16. A technical report showing tests passed and failed.
17. Remaining limitations and manual setup steps.

Final developer instructions

Think through this as if you are the engineer responsible for designing, implementing, testing and shipping CloseCode.

Do not stop after creating the architecture or adding UI screens.

Do not replace OpenCode with another AI implementation.

Do not claim that OpenCode V2 runs on Android until the exact runtime has been tested on a real device.

Do not delete unrelated IDE functionality.

Do not download every possible development dependency into the APK. Use a reliable, secure, on-demand component manager.

Do not leave the old V1 implementation secretly active in the background.

Do not make a large refactor without a migration plan and regression tests.

If a required component is not compatible with Android, investigate the actual technical obstacle, compare official and reproducible alternatives, and report the limitation rather than silently substituting something else.

The final result must be a complete Android IDE called CloseCode, powered by the real OpenCode V2 engine, with its original Web UI and a proper Android development toolchain.