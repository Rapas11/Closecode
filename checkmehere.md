CLOSECODE MASTER TECHNICAL SPECIFICATION OpenCode-Powered Android
Development Workstation

DOCUMENT PURPOSE This document defines the intended direction,
architecture, requirements, implementation phases, and acceptance tests
for CloseCode. It is written for the AI developer working on the
CloseCode codebase.

IMPORTANT PROJECT CLARIFICATION CloseCode does NOT need a separate
built-in AI assistant or a second AI agent.

The intended arrangement is: - OpenCode is the coding
agent/orchestrator. - The AI model/provider configured in OpenCode
supplies the model intelligence. - CloseCode is the Android development
environment and capability provider. - CloseCode provides tools, local
execution, project access, Android device capabilities, and optional
remote execution. - OpenCode uses those capabilities through supported
tool interfaces, MCP where appropriate, or a stable CloseCode
integration API.

Do not build a duplicate AI chat/agent system unless the existing
product architecture explicitly requires a small interface component.
The goal is to make the existing OpenCode agent useful and powerful
inside CloseCode.

The goal is to let a user work from an Android phone, give OpenCode a
development task, and have OpenCode use CloseCode’s available
capabilities to implement, build, test, debug, and report the work. This
must be achieved honestly within Android, hardware, operating-system,
licensing, and remote-service constraints.

============================================================ 1. OVERALL
OBJECTIVE ============================================================

Turn CloseCode into an extensible AI development workstation for
Android.

OpenCode should be able to use CloseCode to work on real software
projects, not merely generate code in a chat window.

Target project categories include: - Android applications - Websites and
frontend applications - Backend services and APIs - Databases and data
tools - Desktop applications where a suitable build environment exists -
Command-line tools - Automation scripts - Cross-platform applications -
Game projects where the required engine and hardware are available - AI
and machine-learning projects where suitable compute is available -
DevOps and deployment projects

Do not claim that every technology can run directly on Android. The
system must distinguish: 1. Supported locally on the Android device. 2.
Supported locally with limitations or compatibility layers. 3. Supported
through a configured remote environment. 4. Not currently supported.

A capability is not implemented merely because a UI button, class,
interface, or placeholder exists. It must work end-to-end and be tested.

============================================================ 2. FIRST
ACTION: AUDIT THE EXISTING PROJECT
============================================================

Before making architectural changes or writing new systems, inspect the
actual current codebase and establish what is already implemented.

Inspect: - Application architecture and module boundaries. - Existing
custom Android engine. - Terminal and shell implementation. - Process
and background-job management. - File-system and project access. - Git
integration. - OpenCode installation, launch, configuration, and
lifecycle. - Model/provider configuration path used by OpenCode. -
Existing tool integrations and tool schemas. - Existing MCP support, if
any. - Android SDK, ADB, Logcat, screenshot, and device-control
features. - Build and test workflows. - Language-server or
code-intelligence integration. - Toolchain installation and package
management. - Remote execution or SSH support. - Permissions, credential
storage, and security controls. - UI integration with the editor,
terminal, file explorer, Logcat, Problems, and Project Settings. -
Existing performance issues, especially editor resume/navigation delays.

For each capability, classify it as: A. Implemented and verified
working. B. Partially implemented or unreliable. C. Missing. D. Not
feasible locally on Android but potentially possible remotely. E.
Blocked by a missing dependency, permission, service, or platform
restriction.

Inspect actual implementations and call paths. Do not infer that a
feature works from its name or the existence of a class.

Do not replace functioning systems simply because a different
architecture is preferred. Reuse and improve existing code where
appropriate.

AUDIT DELIVERABLES Create: - CLOSECODE_CURRENT_CAPABILITIES.md -
CLOSECODE_MISSING_CAPABILITIES.md - CLOSECODE_ARCHITECTURE_AUDIT.md -
CLOSECODE_IMPLEMENTATION_ROADMAP.md -
CLOSECODE_OPENCODE_INTEGRATION.md - CLOSECODE_TOOL_CATALOG.md -
CLOSECODE_TEST_REPORT.md

The reports must identify source files/modules and provide evidence for
important findings. Clearly separate verified facts from assumptions and
recommendations.

Do not begin a large rewrite before the audit and proposed plan are
reviewed.

============================================================ 3. REQUIRED
ARCHITECTURE
============================================================

Use a modular architecture with clear separation of responsibilities.

A. CloseCode user interface - Editor - File Explorer - Terminal - Git
interface - Logcat - Problems - Build output - Project Settings - Device
and execution status - Remote environment status, when configured

B. OpenCode integration - Launch and manage the existing OpenCode
agent. - Preserve OpenCode’s supported configuration and model-provider
workflow. - Allow OpenCode to discover and use CloseCode tools. - Stream
tool output and status back to OpenCode. - Report errors and exit codes
accurately. - Support cancellation and timeouts. - Avoid creating a
competing agent implementation.

C. Unified capability/tool registry - Register available CloseCode
tools. - Publish descriptions and input/output schemas. - Declare
permissions and execution environment. - Make tool availability
discoverable. - Validate arguments. - Return structured results and
errors. - Track running operations. - Support cancellation where
possible. - Version tool contracts to avoid breaking integrations.

D. Execution layer - Local Android-compatible execution. - Remote Linux
execution where configured. - Specialized remote environments, such as
macOS or Windows, when configured and required.

E. Toolchain and project environment manager - Detect project type. -
Detect available SDKs, compilers, runtimes, and package managers. -
Install/configure supported tools. - Manage versions and project
environment variables. - Explain unsupported or unavailable
requirements.

F. Security and permission layer - Enforce file, terminal, network,
device, and remote-operation permissions. - Protect credentials. -
Require confirmation for destructive or sensitive actions.

Prefer direct application APIs for CloseCode functionality. Do not rely
on fragile screen tapping or UI automation when a stable internal API
can perform the action. UI automation may be used only when direct
programmatic access is unavailable and the action is permitted.

============================================================ 4. OPENCODE
INTEGRATION — CRITICAL
============================================================

OpenCode is the coding agent. The configured model/provider is the
source of model intelligence. CloseCode is the working environment and
tool provider.

The integration must: - Use documented, supported OpenCode integration
mechanisms. - Preserve normal OpenCode model/provider configuration. -
Avoid hardcoding a single model provider. - Avoid assuming OpenCode
itself is an AI model. - Avoid duplicating OpenCode’s agent,
conversation, planning, or model-routing responsibilities. - Expose
CloseCode capabilities through supported custom tools and/or MCP. -
Support tool discovery and schema validation. - Return actual tool
results, not fabricated success messages. - Preserve streaming output
where supported. - Support long-running operations and cancellation. -
Surface permission requests to the user. - Handle OpenCode restarts and
connection loss safely. - Document any OpenCode version or compatibility
requirements.

Investigate the current OpenCode version and its official supported
interfaces before implementation. Do not invent configuration keys,
command-line flags, APIs, or MCP behavior. Verify the exact integration
mechanism against the version used by the project.

If OpenCode runs locally inside CloseCode, inspect its process/runtime
constraints and how it communicates with the Android application.

If OpenCode runs as a separate process or server, define and test the
connection protocol, authentication, lifecycle, and reconnection
behavior.

The user should not need to manually copy and paste tool results between
OpenCode and CloseCode.

============================================================ 5. UNIFIED
TOOL CATALOG
============================================================

Create a central, documented tool catalog. Tool names below are proposed
semantic names; adapt them to the existing architecture and supported
OpenCode/MCP naming rules. Do not create duplicate tools where an
equivalent reliable tool already exists.

Each tool must define: - Name and description. - Input schema. - Output
schema. - Required permission. - Execution target: local, remote, or
either. - Timeout and cancellation behavior. - Error codes and useful
error messages. - Whether the tool is read-only or modifies state.

A. FILE AND PROJECT TOOLS - read_file - write_file - create_file -
edit_file - delete_file - move_file - copy_file - rename_file -
list_files - search_files - search_code - find_symbol - apply_patch -
compare_files - inspect_project - inspect_project_configuration

B. TERMINAL AND PROCESS TOOLS - execute_command - execute_script -
start_process - stop_process - get_process_status - get_process_output -
run_in_background - list_processes - manage_environment -
set_working_directory - install_package - run_build - run_tests

The terminal implementation must correctly support: - Standard output
and standard error. - Exit codes. - Working directory. - Environment
variables. - Long-running processes. - Interactive input where
supported. - Process cancellation. - Timeouts. - Background jobs. -
Process cleanup after crashes or app restarts where feasible. -
Streaming output without blocking the Android UI thread.

C. GIT TOOLS - git_clone - git_status - git_diff - git_log -
git_branch - git_checkout - git_commit - git_merge - git_rebase -
git_stash - git_push - git_pull - git_fetch - git_resolve_conflict

Use the actual Git implementation and report authentication, network,
and conflict failures accurately.

D. ANDROID DEVICE AND APP TOOLS - list_devices - get_device_info -
install_apk - launch_app - stop_app - take_screenshot - get_logcat -
get_crash_report - inspect_ui - tap_screen - swipe_screen - input_text -
run_android_tests - collect_app_diagnostics

Respect Android platform permissions and restrictions. Do not promise
device-control functions that the current Android version, app
permissions, ADB configuration, or device policy does not allow.

E. BUILD, TEST, AND DEBUG TOOLS - detect_project_type -
detect_toolchain - check_toolchain_requirements - install_toolchain -
compile_project - build_apk - build_aab - build_web - run_application -
debug_application - get_compiler_errors - get_build_report -
profile_application - analyze_performance

Tools must return real logs, build status, artifact locations, and test
results.

F. NETWORK, RESEARCH, AND SERVICE TOOLS Where configured and
permitted: - Search documentation. - Retrieve package metadata. -
Download project dependencies. - Access APIs. - Inspect HTTP
responses. - Search GitHub repositories. - Read GitHub issues and pull
requests. - Connect to configured Git hosting services. - Access remote
development services.

Do not create a general unrestricted network tool without appropriate
permission and safety controls.

G. CLOSECODE APPLICATION TOOLS Expose useful app capabilities through
direct APIs: - open_file_explorer - open_terminal - open_logcat -
open_problems - open_project_settings - open_file - switch_editor_tab -
inspect_current_file - inspect_open_tabs - inspect_project_structure -
get_build_history - get_execution_status

Only expose operations that are genuinely implemented. These tools
should not manipulate the UI by pretending an action occurred.

============================================================ 6.
LANGUAGES, TOOLCHAINS, AND PACKAGE MANAGERS
============================================================

The system should be extensible to support the following technologies.
This is a target compatibility list, not a claim that every item must
run natively on Android.

MOBILE: - Kotlin - Java - Android SDK - Android Build Tools - Android
Gradle Plugin - Gradle - Android NDK - CMake - Jetpack Compose -
Flutter - Dart - React Native - Expo

WEB: - HTML - CSS - JavaScript - TypeScript - React - Next.js - Vue -
Angular - Svelte - Astro - Tailwind CSS - Bootstrap - Vite - Webpack -
Three.js - WebAssembly

BACKEND: - Node.js - Python - Go - Rust - PHP - Java - Kotlin - C# -
Ruby - Elixir - .NET - Spring Boot - Django - Flask - FastAPI -
Express - NestJS - Laravel

SYSTEMS AND OTHER: - C - C++ - Zig - Swift - Objective-C - R -
Solidity - SQL - Bash - PowerShell - Lua

PACKAGE MANAGERS AND BUILD TOOLS: - npm, pnpm, Yarn - pip, uv, Poetry -
Gradle, Maven - Pub - Cargo, rustup - Go modules - Composer - RubyGems,
Bundler - GCC, Clang, CMake, Ninja - NuGet - Swift Package Manager -
Emscripten and WebAssembly tools

For each technology, document: - Runtime/compiler. - Required SDK. -
Package manager. - Build system. - Supported execution environment. -
Installation size and storage needs where known. - Compatibility
limitations. - Verification command or test.

Do not download every SDK automatically. Provide on-demand installation
and version management. Large toolchains should be optional and should
warn about storage and network usage.

============================================================ 7. ANDROID
LOCAL EXECUTION
============================================================

Use Android for tasks that are technically supported and practical on
the device.

Potential local capabilities: - Editing and project file operations. -
Git operations. - Shell scripts. - Python and Node.js where compatible
runtimes are installed. - Lightweight development servers. - Supported
native command-line compilers. - SQLite and compatible local database
tools. - ADB and Android device diagnostics where available. - Android
builds where the complete compatible toolchain is installed and resource
limits permit.

Important Android constraints: - Android is not an unrestricted desktop
Linux distribution. - App sandboxing and scoped storage affect file
access. - Background execution is restricted by Android lifecycle and
battery policies. - Native binaries may require compatible Android/ARM
builds and libraries. - Some tools expect glibc or desktop Linux
features unavailable in Android’s normal environment. - Some build tools
may need a compatibility environment or remote machine. - Device control
through ADB may require a configured debugging connection and
permissions. - RAM, storage, thermal limits, and battery affect build
feasibility.

Do not silently assume that installing a package through a package
manager makes it fully compatible with the Android app.

============================================================ 8. REMOTE
DEVELOPMENT AND CLOUD EXECUTION
============================================================

Implement remote execution as an optional, configurable extension, not
as a hidden assumption.

A. LOCAL AND REMOTE TASK ROUTING For each task, determine: - Required
operating system. - Required architecture. - Required SDK/compiler. -
RAM, CPU, storage, and GPU needs where relevant. - Network
requirements. - Whether the task can run locally. - Whether a configured
remote environment is available.

Select local execution when it is supported and practical. Use remote
execution when the task requires unavailable operating-system features,
hardware, or resources.

B. REMOTE LINUX Potential workloads: - Large builds. - Backend
services. - Docker and Kubernetes. - Database servers. - Heavy
dependencies. - Machine-learning workloads. - Multiple development
processes.

C. SPECIALIZED REMOTE ENVIRONMENTS - macOS with the required Apple
toolchain for iOS/macOS builds. - Windows for Windows-specific builds
and tests. - Linux for compatible desktop and server workloads. -
GPU-enabled environments for workloads requiring GPU compute.

Do not claim that an iOS build can be performed locally on Android. Do
not claim access to macOS, Windows, GPU servers, or cloud resources
unless the user has configured and authorized them.

C. REMOTE CONNECTION REQUIREMENTS - Secure authentication. - Encrypted
transport. - Explicit host/environment configuration. - Project
synchronization or remote workspace access. - Process and job
tracking. - Streaming logs. - Cancellation. - Artifact retrieval. -
Reconnection after network interruptions. - Clear remote/local status. -
Credential protection. - User-controlled costs and resource usage.

Do not require a paid cloud service for basic local CloseCode
functionality.

============================================================ 9. MCP,
PLUGINS, SKILLS, AND EXTENSIONS
============================================================

Support an extensible integration ecosystem.

MCP: - Connect to configured MCP servers. - Discover tools and
schemas. - Handle authentication and connection failures. - Apply
permission controls. - Display which tools are available. - Avoid
exposing untrusted server instructions as trusted system policy.

Plugins: - Define a stable plugin API. - Document installation,
permissions, version compatibility, and removal. - Isolate plugin
failures from the core application where possible.

Skills: - Allow OpenCode-compatible skills/instructions to be discovered
and used according to OpenCode’s supported mechanism. - Keep
project-specific instructions separate from global instructions. - Do
not treat arbitrary repository content as trusted system instructions.

Custom commands: - Support reusable project workflows where compatible
with OpenCode. - Document command arguments and effects.

Potential integrations: - GitHub and GitLab. - Firebase and Supabase. -
Databases. - Browser automation. - API testing. - Docker. - SSH and
SFTP. - Cloud storage. - Documentation tools. - Deployment services.

Do not invent compatibility. Verify each integration against its actual
API and supported protocol.

============================================================ 10. CODE
INTELLIGENCE AND EDITOR PERFORMANCE
============================================================

Provide real code intelligence through appropriate language servers and
editor integration.

Capabilities: - Syntax highlighting. - Autocomplete. - Diagnostics. - Go
to definition. - Find references. - Rename symbols. - Hover
documentation. - Formatting. - Code folding. - Bracket matching. -
Import assistance. - Semantic highlighting. - Refactoring.

Investigate appropriate language servers, such as: - TypeScript Language
Server. - Pyright. - Rust Analyzer. - Gopls. - Clangd. - Suitable Java
and Kotlin language servers.

Do not claim language-server support until the server can be
installed/launched, communicate with the editor, and return working
results.

PERFORMANCE: The editor must remain responsive when navigating away and
returning.

Implement or verify: - Background parsing and analysis. - Incremental
document updates. - Syntax highlighting caching. - Lazy
initialization. - Lifecycle-aware editor state. - Cancellation of
obsolete jobs. - Avoidance of duplicate language-server processes. -
Memory and process cleanup. - Large-file handling. - Main-thread
protection.

Measure editor resume time and memory use before and after changes. Do
not solve performance problems by silently removing useful editor
features.

============================================================ 11. BUILD,
TEST, DEBUG, AND DELIVERY
============================================================

A successful development workflow should be:

1.  OpenCode receives the user’s task.
2.  OpenCode inspects the project using CloseCode tools.
3.  OpenCode identifies the project type and requirements.
4.  OpenCode checks available toolchains.
5.  Missing dependencies are identified.
6.  The user is informed before large downloads, paid remote usage, or
    sensitive changes.
7.  OpenCode creates or modifies project files.
8.  OpenCode runs builds and tests.
9.  OpenCode reads actual errors and logs.
10. OpenCode fixes issues.
11. OpenCode repeats relevant tests.
12. OpenCode verifies outputs and artifacts.
13. OpenCode reports what succeeded, failed, or remains unverified.
14. The user can access the resulting files and artifacts.

Build outputs must be stored in known, accessible locations. Report
exact paths and provide working access to generated artifacts.

Never report a successful build or test based only on an expected result
or mocked response.

============================================================ 12.
SECURITY AND USER CONTROL
============================================================

Implement: - Project-scoped file permissions. - Read-only and write
access levels. - Terminal command permission policies. - Confirmation
for destructive operations. - Network permission controls. - Secure
API-key and credential storage. - No secrets in ordinary logs or
model-visible output. - Remote host authentication. - Process and
resource limits. - Audit logs for important operations. - Backups or Git
checkpoints before risky changes. - Cancellation and recovery
mechanisms.

The agent should be able to work efficiently without asking for approval
for every harmless file operation, while sensitive and destructive
actions remain protected.

============================================================ 13.
IMPLEMENTATION PHASES
============================================================

Implement in dependency order. Do not attempt a massive rewrite in one
step.

PHASE 0 — AUDIT AND BASELINE - Inspect current architecture. - Verify
OpenCode launch and model/provider setup. - Inventory existing tools and
capabilities. - Test terminal, file, Git, Android, build, and process
functions. - Measure editor performance. - Produce audit reports and
roadmap.

PHASE 1 — RELIABLE CORE EXECUTION - Stabilize file operations. -
Stabilize terminal execution. - Add robust process management. - Add
output streaming and exit codes. - Add cancellation and timeouts. -
Verify Git workflows.

PHASE 2 — OPENCODE TOOL ACCESS - Implement the chosen supported OpenCode
integration. - Register existing CloseCode capabilities. - Define tool
schemas and permissions. - Verify tool discovery and real execution. -
Test errors, cancellation, and reconnection.

PHASE 3 — TOOLCHAIN MANAGEMENT - Detect project types. - Detect
available runtimes and SDKs. - Add on-demand
installation/configuration. - Add version and environment management. -
Verify representative builds.

PHASE 4 — BUILD, TEST, AND DEBUG - Integrate real build workflows. -
Capture structured build results. - Integrate test execution. - Improve
diagnostics and debugging. - Verify artifact delivery.

PHASE 5 — ANDROID DEVICE INTEGRATION - Expose verified ADB/device
capabilities. - Integrate Logcat, screenshots, app installation, and
tests. - Enforce Android permissions and platform limitations.

PHASE 6 — CODE INTELLIGENCE AND PERFORMANCE - Integrate language
servers. - Improve editor responsiveness. - Test navigation and large
files. - Prevent duplicate background processes.

PHASE 7 — MCP AND EXTENSIONS - Integrate MCP. - Add plugin and skill
compatibility. - Add documentation and integration examples.

PHASE 8 — REMOTE EXECUTION - Add optional remote Linux support. - Add
secure remote process and workspace management. - Add specialized
environments when configured. - Verify artifact retrieval and failure
recovery.

PHASE 9 — ADVANCED WORKFLOWS - Multi-agent workflows using supported
OpenCode capabilities. - Browser and API testing. - Database tools. -
Deployment integrations. - Advanced profiling and automation.

For each phase: - Define scope. - Identify files/modules. - Implement. -
Run tests. - Record evidence. - Update documentation. - Preserve working
functionality.

============================================================ 14.
ACCEPTANCE TESTS
============================================================

The project is not complete merely because it compiles.

At minimum, verify these end-to-end tests:

TEST 1 — OPENCODE CONNECTION OpenCode starts correctly, uses its
configured model/provider, and can access the CloseCode tool
integration.

TEST 2 — TOOL DISCOVERY OpenCode can discover available CloseCode tools
and their valid input schemas.

TEST 3 — FILE WORKFLOW Ask OpenCode to inspect a project, create a file,
modify it, read it back, and report the exact result.

TEST 4 — TERMINAL WORKFLOW Ask OpenCode to run a command, stream output,
return the exit code, and cancel a long-running process.

TEST 5 — GIT WORKFLOW Ask OpenCode to inspect Git status, create a
branch, make a controlled change, and show the diff.

TEST 6 — PROJECT DETECTION Give OpenCode representative projects and
verify it correctly identifies their technology and required toolchain.

TEST 7 — BUILD WORKFLOW Build at least one supported project using the
actual compiler/build system and retrieve the resulting artifact.

TEST 8 — ERROR DIAGNOSIS Introduce a controlled build error. Verify
OpenCode receives the real error, fixes it, and reruns the build.

TEST 9 — ANDROID DEVICE WORKFLOW Where permissions and configuration
allow, list a device, capture a screenshot, retrieve Logcat, and
install/launch a test APK.

TEST 10 — MCP Connect a test MCP server, discover its tool, invoke it,
and handle a connection failure.

TEST 11 — PERMISSIONS Verify that restricted and destructive operations
are blocked or require approval according to policy.

TEST 12 — PROCESS RECOVERY Test app navigation, background execution,
interruption, cancellation, and recovery without leaving orphaned
processes.

TEST 13 — EDITOR PERFORMANCE Measure opening and returning to the
editor, including a representative project with syntax highlighting and
language intelligence enabled.

TEST 14 — REMOTE EXECUTION Only if remote execution is configured: run a
task remotely, stream logs, cancel it, and retrieve an artifact.

For every test, record: - Environment and device details. - Exact
steps. - Expected result. - Actual result. - Logs or other evidence. -
Pass, fail, or blocked status. - Known limitations.

============================================================ 15.
REQUIRED DOCUMENTATION AND FINAL REPORT
============================================================

Maintain these documents:

1.  CLOSECODE_CURRENT_CAPABILITIES.md
2.  CLOSECODE_MISSING_CAPABILITIES.md
3.  CLOSECODE_ARCHITECTURE_AUDIT.md
4.  CLOSECODE_IMPLEMENTATION_ROADMAP.md
5.  CLOSECODE_OPENCODE_INTEGRATION.md
6.  CLOSECODE_TOOL_CATALOG.md
7.  CLOSECODE_TOOLCHAIN_SUPPORT.md
8.  CLOSECODE_SECURITY_MODEL.md
9.  CLOSECODE_TEST_REPORT.md
10. CLOSECODE_KNOWN_LIMITATIONS.md

The final report must state: - What was implemented. - What was
reused. - What was changed. - What was tested. - What passed. - What
failed. - What remains incomplete. - Which features require remote
services. - Which features cannot run locally on Android. - Any
additional dependencies, storage, permissions, or costs.

============================================================ 16.
NON-NEGOTIABLE DEVELOPER RULES
============================================================

1.  Audit before major implementation.
2.  Preserve working CloseCode functionality.
3.  Do not build a second AI agent; OpenCode is the agent.
4.  Do not confuse the AI model/provider with OpenCode.
5.  Do not invent APIs, tool names, configuration keys, or
    compatibility.
6.  Verify the actual OpenCode version and supported integration method.
7.  Do not claim a capability works until it has been tested.
8.  Do not use mocked results as proof of real functionality.
9.  Do not assume all desktop Linux tools run natively on Android.
10. Do not silently move work to a remote service or incur costs.
11. Do not expose secrets to the model or logs.
12. Do not block the Android UI thread with builds or code analysis.
13. Do not remove features merely to make tests pass.
14. Document platform limitations and incomplete work.
15. Implement in small, reviewable phases.

============================================================ 17. FINAL
PRODUCT VISION
============================================================

The intended user experience is:

The user opens CloseCode on an Android phone, opens OpenCode,
selects/configures an available model provider, and gives OpenCode a
real development task.

OpenCode should be able to inspect the project, use CloseCode’s tools,
edit files, run commands, install supported dependencies, build, test,
debug, and verify the result.

When a task exceeds Android’s local capabilities, CloseCode should
explain the requirement and use a configured remote environment if one
is available and authorized.

The user should receive an honest completion report and access to the
actual generated files or build artifacts.

The ultimate objective:

MAKE CLOSECODE THE DEVELOPMENT ENVIRONMENT THAT OPENCODE CAN USE TO DO
REAL SOFTWARE DEVELOPMENT FROM AN ANDROID PHONE.

Start with the existing codebase. Audit it. Prove what already works.
Identify the gaps. Then implement the roadmap systematically without
unnecessary rewrites or unsupported claims.
