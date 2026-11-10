CloseCode Build 76 — Verification Report and Fix Specification
Prepared for: CloseCode AI developer Date: 2026-11-10 Build tested: CloseCode 0.65.1507 (versionCode 65 / build 76) Project under test: Redit (com.rapas.redit), AGP 8.12.0, Gradle 8.13, Kotlin 1.9.24, compileSdk 35, Compose BOM 2024.09.03, Media3 1.4.1, MediaPipe 0.10.35, Vosk 0.3.75 Environment: Android phone only. No PC, no adb, no Android Studio.

1. Purpose
This document records every defect found while taking a real, modern Android project from "cannot build" to "builds with Full Gradle" on the phone. Each item has:

Symptom — what the agent sees.
Evidence — the exact command and output.
Root cause — verified in code or bytecode, not guessed.
Fix — the precise change.
Files — where to change it.
Acceptance — the test that proves it is fixed.
Items are ordered by severity. P0 items block the build; P1 items break the agent loop; P2 are security; P3 are test gaps.

1.1 Design principle — the app must be self-sufficient without the AI
CloseCode is a standalone product. The AI is an optional layer on top. Therefore:

Every component the AI needs must be installable by a human from Settings, using the same code path as closecode setup.
The CLI must never be the only way to install something. If a capability exists only in the CLI, a normal user cannot reach it; if it exists only in the UI, the agent cannot reach it. One downloader, two front ends.
A human must be able to go from a fresh phone to a built APK without any AI, any curl, any tar, any chmod, and any manual license file. Today that is not true (P0-1…P0-4).
The AI should only orchestrate: closecode setup --gradle, closecode build, closecode install, closecode run. It must never need to repair the toolchain.
The acceptance test is the human path: reset the toolchain, use only the Settings UI, build a Compose + Media3 + Vosk project with AGP 8.12. If that fails, the app is incomplete — regardless of whether the AI can work around it.
What the app must download and verify itself
Component	Needed for	Notes
JDK 17	Gradle, Kotlin, aapt2 wrappers	Termux openjdk-17; run java -version after install
Gradle distribution	Any Gradle build	Version from gradle-wrapper.properties; fall back to a compatible one
Android platform 34/35/36	compileSdk	Full platform zip, not just android.jar
Build-tools (arm64)	aapt2, zipalign, apksigner, aidl	Must match the project's AGP (35.0.0+ for AGP 8.12)
Platform-tools (arm64)	AGP SDK validation	Missing today (P0-3)
SDK license files	AGP package checks	Missing today (P0-4)
NDK + CMake	Native C/C++ only	Optional; never required for ndk { abiFilters }
Shell tools	Node, npm, Python, pip, uv, git, curl, zip, jq	Already present; keep the post-install checks
Media/data tools	ffmpeg, sqlite3, imagemagick	Optional --media pack
Language packs	Go, Rust, Dart, PHP, Ruby, Deno, Bun	Optional, on demand
Each component needs: URL, size, SHA-256/SHA-1, resumable .part download, safe unpack, a real post-install self-test, and a Remove action. The same manifest must drive Settings and closecode setup.

Consequence: the failures in P0-1…P0-4 are app bugs, not agent limitations. They affect every user who builds an AGP 8.12 project, with or without the AI.

1.1.1 What the rule does and does not restrict
This is not a limit on what the AI can do. It is a limit on who owns installation and consent.

The AI may do anything a human can do through the app:

Allowed	Example
Build any supported project	closecode build, closecode build --gradle, closecode build --release
Run web, Node, Python, Go, Rust, Dart, PHP, Ruby projects	closecode init, closecode serve, closecode preview, closecode test
Install any component the app ships	closecode setup --gradle, --media, --go, --rust, --deno, …
Read and fix project source	edit files, run closecode check, rebuild
Drive apps and the device	closecode ui, closecode apps, closecode intent, closecode device
Run arbitrary shell when genuinely needed	closecode exec "<command>" — the documented escape hatch
Read logs, dumps, artifacts, screenshots	closecode logcat, closecode ui dump, closecode screenshot
The AI must not do these, because they are the app's job or the user's decision:

Not allowed	Why	Correct action
Download a toolchain with curl/wget	Unpinned version, no checksum, no resume, not removable	closecode setup <component>; if missing, report a gap
tar/unzip a toolchain by hand	Wrong layout, wrong exec bits, wrong prefix	Let the installer unpack
chmod +x a binary to make it run	Hides an installer bug; breaks on the next update	Fix the installer, or report it
Write android-sdk-license hashes	That is the user accepting a Google legal agreement	Show the license in-app, record acceptance, then write
Grant itself shell/secrets consent	Consent belongs to the user	closecode consent grant must ask and wait for a tap
Enable "install unknown apps" / accessibility	Only the user can	Return needsUser:true with a working settings url
The dividing line: the AI has full capability; it does not have ownership of the toolchain or authority to consent for the user.

Corollary — do not paper over bugs. If the AI has to curl + chmod to make a standard Android build work, that is a CloseCode defect, not a solution. The agent should report it (P0-style) so the installer gets fixed, because every human user hits the same wall. A workaround is acceptable only as a temporary diagnostic step, and must be disclosed.

1.2 There are currently two toolchains — they must become one
The editor's JDK Configuration / Gradle menu and the agent's closecode setup are separate systems today:

Editor dialog (screenshot)	Agent CLI
JDK	"Current JDK: Default", path /opt/java/jdk1.8.0_212; offers JDK 8/11/17/21	/data/user/0/com.closecode/file/g/lib/jvm/java-17-openjdk (17.0.21)
Gradle	gradlew assembleDebug (project wrapper)	/data/user/0/com.closecode/files/gradle/gradle-8.13
SDK	editor's own SDK config	/data/user/0/com.closecode/files/sdk
Build-tools	editor default	35.0.1
Signing	"Generate Signed Bundle / APK"	closecode signing / debug key
Why this is a problem

A human can build with JDK 8 or 21 while the agent builds with JDK 17. The same project then produces different results.
The editor runs gradlew; the agent runs its own runner. They can resolve different Gradle, aapt2, build-tools, and signing keys.
A fix applied to one path does not fix the other. The -Xmx64m and AAPT2 bugs exist in the agent path; the editor path has its own.
The dialog shows "Current JDK: Default" with a stale /opt/java/jdk1.8.0_212 path even while JDK 21 is selected. The displayed state does not match the selection.
Required

One ToolchainStore for both. The editor dialog and closecode setup must read/write the same manifest, paths, versions, and checksums.
Default to JDK 17 for AGP 8.x. Offer 21 only when the project's Gradle/AGP supports it; never silently use JDK 8 for a modern project.
Show the resolved path and version, not "Default" plus a stale path.
Add platform-tools, build-tools, and SDK licenses to the same dialog, with a Test toolchain button that runs java -version, gradle -v, aapt2 version, and a 30-second assembleDebug on a scaffolded project.
One signing key for "Generate Signed Bundle / APK" and closecode signing, so an APK built from the editor and one built by the agent can update each other.
This is the practical meaning of "the app works without AI": the human-facing dialog and the agent-facing CLI must be two views of the same installer.

2. Executive summary
ID	Severity	Issue	Status
P0-1	Blocker	Gradle launcher passes "-Xmx64m" as the main class (toybox xargs does not strip quotes)	Root cause verified; local patch applied
P0-2	Blocker	AGP 8.12 rejects libaapt2.so as the AAPT2 override (must end in aapt2)	Root cause verified in AGP bytecode
P0-3	Blocker	closecode setup --gradle does not install platform-tools	Verified; local install applied
P0-4	Blocker	closecode setup --gradle writes no SDK license files	Verified; local licenses written
P0-5	Blocker	A failed closecode build exits with code 0	Verified
P0-6	High	Build failure message is the stack tail, not the root cause	Verified
P0-7	High	Custom-engine and Full-Gradle debug APKs use different debug keys; in-place update fails	Verified
P1-1	High	python3 -m venv is broken (ensurepip/_bundled empty)	Verified
P1-2	High	--json is parsed as a positional argument in config show and app	Verified
P1-3	Medium	toolchain status reports ok:false when only optional NDK/CMake are missing	Verified
P1-4	Medium	setup --gradle output is buffered; no live progress	Verified
P1-5	Medium	Setup self-test marks a broken Gradle as ok:true	Verified
P1-6	Medium	Accessibility onboarding still requires a manual Settings tap	Expected; path exists
P2-1	Critical	closecode consent grant <capability> self-grants with no confirmation	Verified
P3-1	Medium	Several new commands are untested (selftest, test-ui, secrets, deploy, screen, notifications)	Not exercised
Bottom line: the architecture is now correct. The remaining P0s are three concrete toolchain defects (Gradle launcher, AAPT2 name, platform-tools/licenses) plus one agent-contract bug (exit code). Fix those and the phone builds real Android projects.

Verified build result
With P0-1 through P0-4 applied locally, assembleDebug succeeded in 22m 1s:

APK   app/build/outputs/apk/debug/app-debug.apk   91,827,672 bytes
      com.rapas.redit  versionCode 4  versionName 1.2.0
      minSdk 26  targetSdk 35  compileSdk 35  label "Redit"
Kotlin compiled with warnings only (deprecated Icons.* aliases, unused parameters). There are no source-level compile errors in the project. The only reason closecode build still fails is P0-2: CloseCode keeps passing libaapt2.so, and a $GRADLE_USER_HOME/init.d workaround cannot override the command-line -P value. The fix must be in the code that builds the Gradle command.

This proves the toolchain is correct once P0-1…P0-4 are fixed in CloseCode; the project does not need changes.

3. What already works (do not regress)
These were verified on build 76 and should be covered by regression tests.

Area	Evidence
Shell environment	node v26.13.0, npm 12.2.2, python3 3.14.10, uv 0.13.7, git 2.56.0, curl 8.23.0, zip, jq all run with no linker error
Global pip	pip 26.3 works
Compose false positive	gone from closecode check
Native false positive	describe → "native": {"used": false}; missing no longer includes Native tools
Toolchain paths	doctor and toolchain status --json expose jdk, gradle, gradleUserHome, sdkRoot, platforms, buildTools
Full Gradle install	setup --gradle installed JDK 17.0.21, Gradle 8.13, platform 35, build-tools 35.0.1
Auto engine selection	closecode build selects Gradle and returns needs_setup + closecode setup --gradle
Help	closecode help ui/build/serve work
Web	serve returns the page; preview --dom returns rendered DOM
Templates	init --template web-static and python-fastapi scaffold correctly
Apps	apps list (116 apps), apps info com.whatsapp.w4b
Device	device battery (100%, 39.4 °C)
Consent/audit	consent status, audit tail work
Signing	signing status shows the phone-bound debug key
Exec/files	exec and files work once consent is granted
4. P0 — Build blockers
P0-1 — Gradle launcher mis-parses JVM options under Android xargs
Symptom

closecode build
→ Gradle failed (exit 1):
  Error: Could not find or load main class "-Xmx64m"
  Caused by: java.lang.ClassNotFoundException: "-Xmx64m"
gradle -v fails the same way, but Gradle itself is fine.

Evidence

$ gradle -v
Error: Could not find or load main class "-Xmx64m"

$ java -Xmx64m -Xms64m -Dorg.gradle.appname=gradle \
    -classpath lib/gradle-launcher-8.13.jar \
    org.gradle.launcher.GradleMain -v
Welcome to Gradle 8.13!
Root cause

gradle-8.13/bin/gradle contains:

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'" \"-javaagent:$APP_HOME/lib/agents/gradle-instrumentation-agent-8.13.jar\""
...
eval "set -- $(
    printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
    xargs -n1 |
    sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
    tr '\n' ' '
)" '"$@"'
The script relies on GNU xargs to strip the embedded quotes. Android ships toybox xargs, which does not strip quotes. The literal string "-Xmx64m" (quotes included) survives, is escaped by sed, and eval passes it as the main class.

Fix (choose one; option C is best)

A. Patch the launcher after unpacking (smallest change):

// ToolchainStore.kt — after the Gradle zip is unpacked
val launcher = File(gradleHome, "bin/gradle")
val text = launcher.readText()
val patched = text.replace(
    "DEFAULT_JVM_OPTS='\"-Xmx64m\" \"-Xms64m\"'\" \\\"-javaagent:\$APP_HOME/lib/agents/gradle-instrumentation-agent-8.13.jar\\\"\"",
    "DEFAULT_JVM_OPTS=\"-Xmx64m -Xms64m -javaagent:\$APP_HOME/lib/agents/gradle-instrumentation-agent-8.13.jar\""
)
if (patched != text) launcher.writeText(patched)
B. Ship GNU findutils xargs in the shell pack and put $PREFIX/bin before /system/bin (already true). Verify with xargs --version → GNU.

C. Bypass the shell script entirely (recommended): invoke Gradle's main class directly, exactly as tested:

val command = listOf(
    File(javaHome, "bin/java").absolutePath,
    "-Xmx2048m", "-Dfile.encoding=UTF-8",
    "-Dorg.gradle.appname=gradle",
    "-classpath", File(gradleHome, "lib/gradle-launcher-8.13.jar").absolutePath,
    "org.gradle.launcher.GradleMain",
) + gradleArgs
This removes the dependency on xargs, shell quoting, and the -javaagent line, and is the most reliable on Android.

Files: GradleRunner.kt, ToolchainStore.kt (post-unpack), PlatformStore.kt.

Acceptance

gradle -v            # prints "Gradle 8.13"
closecode build      # no class-not-found error
P0-2 — AAPT2 override must end with the file name aapt2
Symptom

Failed to apply plugin 'com.android.internal.application'.
> Custom AAPT2 location does not point to an AAPT2 executable:
  /data/app/~~.../com.closecode-.../lib/arm64/libaapt2.so
Evidence

libaapt2.so runs correctly: .../libaapt2.so version → Android Asset Packaging Tool (aapt) 2.19-OS3.0.303.0.WPFEUXM.
Build-tools 35.0.1 aapt2 also runs: .../build-tools/35.0.1/aapt2 version → same banner.
AGP 8.12 bytecode (Aapt2FromMaven$Companion.create):
115: aload_3
116: getstatic SdkConstants.FN_AAPT2        // "aapt2"
119: ...
128: invokestatic StringsKt.endsWith$default
131: ifne 151
134: new IllegalStateException
139: invokedynamic makeConcatWithConstants   // "... does not point to an AAPT2 executable: "
AGP only checks that the path ends with aapt2. It rejects libaapt2.so before executing it.

Fix

Resolve AAPT2 to a path whose file name is exactly aapt2:

val sdkAapt2 = File(sdkRoot, "build-tools/$buildToolsVersion/aapt2")
val aapt2 = if (sdkAapt2.isFile) {
    sdkAapt2
} else {
    // Copy the bundled arm64 aapt2 out of the APK lib dir to a writable, correctly named file.
    File(toolchainRoot, "aapt2").also { dest ->
        bundledAapt2.copyTo(dest, overwrite = true)
        dest.setExecutable(true, false)
    }
}
// then: -Pandroid.aapt2FromMavenOverride=${aapt2.absolutePath}
Do not point the override at libaapt2.so, even though it is executable.

Workaround limitation (verified): adding an init.d script that sets android.aapt2FromMavenOverride via extraProperties does not win over CloseCode's -Pandroid.aapt2FromMavenOverride=...libaapt2.so; closecode build still fails. The fix has to be in the code that builds the Gradle command (GradleRunner / GradleToolchain), not in project or user Gradle config.

Files: GradleRunner.kt (GradleToolchain.aapt2), BuildSetup.kt / wherever GradleToolchain is constructed, PlatformStore.kt.

Acceptance

closecode build    # plugin applies; no "Custom AAPT2 location" error
P0-3 — setup --gradle does not install platform-tools
Symptom

Checking the license for package Android SDK Platform-Tools in .../sdk/licenses
Warning: License for package Android SDK Platform-Tools not accepted.
WARNING: platform-tools package is not installed. Please accept the installation licence to continue
The build stalls at 25%.

Root cause

PlatformStore installs platforms/android-35 and build-tools/35.0.1 but never platform-tools. AGP requires the package to be present even for assembleDebug.

Fix

Add platform-tools to the --gradle component set:

https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/platform-tools-34.0.4-aarch64.tar.xz
Extract to <sdkRoot>/platform-tools (the archive already contains the platform-tools/ directory, source.properties, and package.xml). Verify adb version as a post-install self-test.

Files: PlatformStore.kt (URL, checksum, version), ToolchainDownloadService.kt, BuildSetup.kt.

Acceptance

closecode doctor --json    # platform-tools: ok
closecode build            # no platform-tools warning
P0-4 — setup --gradle writes no SDK license files
Symptom

Same as P0-3: AGP refuses to proceed because <sdkRoot>/licenses does not exist.

Fix

Create <sdkRoot>/licenses/ and write the standard accepted-license hashes during setup:

licenses/android-sdk-license
  8933bad161af4178b1185d1a37fbf41ea5269c55
  d56f5187479451eabf01fb78af6dfcb131a6481e
  24333f8a63b6825ea9c5514f83c2829b004d1fee

licenses/android-sdk-preview-license
  84831b9409646a918e30573bab4c9c91346d8abd

licenses/android-sdk-arm-dbt-license
  859f317696f67ef3d7f30a50a5560e7834b43903

licenses/android-googletv-license
  601085b94cd77f0b54ff86406957099ebe79c4d6
Also expose licenses in closecode toolchain status --json.

Files: PlatformStore.kt, ToolchainDownloadService.kt.

Acceptance

ls $ANDROID_HOME/licenses/android-sdk-license
closecode build    # no license warning
P0-5 — A failed build exits with code 0
Symptom

closecode build > log 2>&1
echo $?          # 0
But the payload says:

{"event":"done","ok":true,
 "result":{"ok":false,"state":"failed","code":"build_failed", ...}}
Why it matters

Every agent and CI wrapper checks the exit code. A failed build that exits 0 is reported as success, and the agent stops instead of fixing the error. This is the single most damaging agent-contract bug in the build.

Fix

closecode build, closecode gradle, closecode install, closecode run, closecode test, closecode lint, closecode package, closecode release must exit non-zero when result.ok == false.
The top-level done event must set "ok": false.
Keep the structured result payload unchanged.
val result = runBuild(...)
emit(doneEvent(ok = result.ok, result = result))
if (!result.ok) exitProcess(1)
Acceptance

closecode build; echo $?        # non-zero on failure
closecode build; echo $?        # 0 on success
P0-6 — Build failure reports the stack tail, not the cause
Symptom

"message": "Gradle failed (exit 1): at org.gradle.internal.operations... at org.gradle.api.internal.plugins... ... 194 more BUILD FAILED in 8m 21s"
The real cause was at line 19 of a 263-line log:

* What went wrong:
An exception occurred applying plugin request [id: 'com.android.application']
> Failed to apply plugin 'com.android.internal.application'.
   > Custom AAPT2 location does not point to an AAPT2 executable: .../libaapt2.so
Fix

Parse the Gradle log and return the root cause first:

Extract the block after * What went wrong: up to * Try:.
Follow the Caused by: chain; the deepest cause is the root.
Set message to that block (single line, trimmed).
Put every Caused by: in diagnostics[] with {message, file?, line?}.
Keep logPath and exitCode.
Also parse Kotlin/Java/AAPT2/R8 diagnostics into {file, line, column, severity, message}.

Files: BuildReport.kt / BuildEngine.kt / GradleRunner.kt.

Acceptance

A deliberately broken AAPT2 override returns message containing "Custom AAPT2 location does not point to an AAPT2 executable", not the stack tail.

P0-7 — Debug signing differs between the custom engine and Full Gradle
Symptom

Installing a Full-Gradle debug APK over an app previously built by CloseCode's custom engine fails with:

INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.rapas.redit signatures do not match
Evidence

Installed Redit:  CN=Android Debug, SHA-256 8f2d65c4cea633f9edde5dac3f1711b91d6d80cf9b34134fc5b72c654029ba49
Gradle APK:       CN=Android Debug, SHA-256 a6811008ffe66d61dd679b8a1954418a642e9a065de06cccd10a9bac3f41ddcc
Both use the standard subject but different key pairs. The custom engine and Gradle resolve different ~/.android/debug.keystore files (or CloseCode injects its own key for one engine only).

Key search result: the only debug keystore on disk is /data/user/0/com.closecode/files/opencode/home/config/.android/debug.keystore with SHA-256 A6811008…, i.e. the new Gradle APK's key. CloseCode's own secure store (shared_prefs/closecode_signing_secure.xml, AndroidX-encrypted) exposes CN=Closecode Debug, which matches neither the installed app (8F2D65C4…) nor the Gradle APK. The installed app's private key is therefore not recoverable, so an in-place update of this particular installation is impossible without uninstalling it.

Why it matters

Switching between closecode build and closecode build --gradle silently breaks updates. The only recovery is uninstalling the app, which deletes its data. For a user testing both engines, this looks like "the APK won't install".

Fix

Use one debug keystore for both engines:

Point Gradle's debug signing config at CloseCode's keystore (or at a stable path such as <filesDir>/signing/debug.keystore) via android.debug.signingConfig / an init script, or
Make the custom engine use the standard ~/.android/debug.keystore, or
Generate one CloseCode debug keystore at first run and use it for both.
Add closecode signing sign <apk> [--key <id>] that post-signs a Gradle APK with the selected CloseCode key. This lets Full-Gradle output match the custom engine without touching the project, and is the only way to keep a stable key when the two engines evolve separately.
Additionally, closecode install must detect a signature mismatch and return a structured remedy:

{"ok":false,"code":"signature_mismatch","needsUser":true,
 "package":"com.rapas.redit",
 "remedy":"Uninstall the existing app (data will be lost) or rebuild with the same signing key.",
 "actions":["closecode apps uninstall com.rapas.redit","closecode signing select <key>"]}
Files: DebugKey.kt, GradleRunner.kt (signing args), InstallHelper.kt, signing command.

Acceptance

closecode build && closecode install          # succeeds over a previous install
closecode build --gradle && closecode install # succeeds over the same app
Both paths produce the same certificate SHA-256.

5. P1 — Correctness and agent ergonomics
P1-1 — python3 -m venv is broken
Symptom

python3 -m venv venvtest
Error: Command '[.../venvtest/bin/python3', '-m', 'ensurepip', '--upgrade', '--default-pip']' returned non-zero exit status 1.
python3 -m pip --version works, and uv works, but the standard venv path does not.

Root cause

.../lib/python3.14/ensurepip/_bundled is empty. ensurepip cannot install pip into a new venv.

Fix

Ship the pip wheel in ensurepip/_bundled (run get-pip.py at pack-build time and include pip-*.whl), or
Make venv fall back to uv/get-pip.py automatically, or
Ship virtualenv.
Also add a post-install self-test:

python3 -m venv /tmp/v && /tmp/v/bin/pip -V
Files: shell-tools pack manifest, closecode setup --python.

Acceptance

python3 -m venv /tmp/v
/tmp/v/bin/pip install requests
/tmp/v/bin/python -c "import requests; print(requests.__version__)"
P1-2 — --json is parsed as a positional argument
Symptom

closecode config show --json
→ {"ok":true,"key":"--json","value":null,...}

closecode app --json
→ {"ok":true,"package":"--json","installed":false,...}

closecode audit tail
→ {"action":"apps list","package":"--json",...}
Fix

Parse flags before positional arguments in every command.
Reject unknown flags with {ok:false,error:"unknown option '--json'"} when a command does not support it.
Add --json to config show and app.
Never let a flag become a package name or config key.
Files: CloseCodeControl.kt (argument parsing), config/app/apps handlers.

Acceptance

closecode config show --json     # returns settings
closecode app --json             # returns the current package
closecode apps list --json       # audit entry has package ""
P1-3 — toolchain status ok:false when only optional NDK/CMake are missing
Symptom

"ok": false,
"missing": ["gradle", "ndk", "cmake"]
after Gradle, platform 35, and build-tools were installed and Gradle was ready.

Fix

ok should mean "ready for the project's needs".
Split missing into required and optional.
For a project with native.used == false, NDK/CMake are optional and must not make ok false.
doctor should mark native checks optional: true when the project has no native code.
Files: BuildSetup.kt, DoctorCommand.kt, ToolchainStatusCommand.kt.

Acceptance

After setup --gradle on a non-native project: ok:true, required:[], optional:["ndk","cmake"].

P1-4 — setup --gradle output is buffered
Symptom

The setup log stayed at 0 bytes for minutes while a 96 MB JDK package downloaded. Progress events only appeared at the end.

Fix

Flush stdout after every JSON event (System.out.println(...); System.out.flush()), or write events directly to the job log file as they occur.
Emit at least: start, download (with received/total), verify, unpack, selftest, done.
Make closecode jobs logs <id> -f stream them.
Files: SetupCommand.kt, ToolchainDownloadService.kt, CloseCodeControl.kt (output writer).

Acceptance

closecode setup --gradle --json | head -3
# prints start + first download event within seconds, before completion
P1-5 — Setup self-test marks a broken Gradle as ok:true
Symptom

{"name":"gradle","ok":true,
 "version":"Error: Could not find or load main class \"-Xmx64m\""}
Fix

Check the process exit code.
Parse the version output; require a line matching Gradle \d+\.\d+.
If the output contains Error: or no version, return ok:false with the output.
Run the same check for java -version and aapt2 version.
Files: SetupCommand.kt / GradleRunner.kt self-test.

Acceptance

With a broken launcher, setup --gradle exits non-zero and reports gradle.ok:false.

P1-6 — Accessibility onboarding
Symptom

closecode accessibility → {"enabled": false, "needsUser": true}
closecode ui dump       → {"needsAccessibility": true}
Status: the deep-link path exists (closecode accessibility enable), but the service still needs one user tap. That is acceptable, but the agent must be able to request it cleanly.

Fix

closecode accessibility enable returns {ok:false, needsUser:true, url:"..."} and opens the exact service screen.
Show a first-run onboarding card and a persistent status chip.
Include accessibility in doctor and status --json.
Never require accessibility for apps metadata.
Acceptance

closecode accessibility enable → user taps → closecode accessibility returns enabled:true → ui dump returns a tree.

P1-7 — install returns needsPermission without an action or URL
Symptom

closecode install
→ {"ok":false,
   "error":"Allow CloseCode to install apps in Settings, then try again.",
   "needsPermission":true,
   "code":"failed"}
There is no url, no needsUser, and no command the agent can run. The agent is stuck.

Fix

Return a structured, actionable result:
{"ok":false,"code":"needs_permission","needsUser":true,
 "permission":"REQUEST_INSTALL_PACKAGES",
 "url":"android.settings.MANAGE_UNKNOWN_APP_SOURCES",
 "package":"com.closecode",
 "remedy":"Allow CloseCode to install unknown apps, then run closecode install again."}
Add closecode install --open-settings (and the same for accessibility, notifications, screen) that launches the exact system screen.
After the user grants it, closecode install must retry automatically.
Files: CloseCodeControl.kt (install handler), InstallHelper.kt.

Acceptance

closecode install on a fresh device returns needsUser:true with a working url; after the tap, the same command installs.

6. P2 — Security
P2-1 — Consent can be self-granted
Symptom

closecode consent grant shell
→ {"ok":true,"capability":"shell"}
No confirmation, no needsUser, no audit of a user decision.

Why it matters

An agent can grant itself shell, files, secrets, and ui-automation, which defeats the entire consent model. The most sensitive capabilities (secrets, ui-automation, screen) must never be self-grantable.

Fix

closecode consent grant must require an explicit user confirmation:
return {ok:false, code:"needs_consent", needsUser:true, capability, package} and show a notification/dialog;
only the UI (or a one-shot --approve that opens the dialog) may flip the grant.
secrets, ui-automation, screen, and shell are never self-grantable.
Record both granted and denied attempts in the audit log.
Add closecode consent request <capability> for the agent to ask.
Files: consent store, CloseCodeControl.kt consent handler, audit writer, UI.

Acceptance

closecode consent grant shell
→ {"ok":false,"code":"needs_consent","needsUser":true}
# after the user confirms in the UI:
closecode consent status --json → "shell": true
7. P3 — Test gaps
These commands were not exercised end-to-end. Add them to closecode selftest and to the release checklist:

Command	What to test
closecode selftest --build	Scaffold every template, detect type, build web + Python
closecode init --template android-compose	AGP 8.12 + Compose BOM 2024.09.03 + Media3 + Vosk template builds
closecode test/lint/format/package/release	Each ecosystem dispatches correctly
closecode test-ui script.json	launch → wait → tap → assert across two apps
closecode secrets set/list/delete	Value never printed; survives restart
closecode deploy --dry-run	rsync/scp over SSH using the vault
closecode screen start/stop	MediaProjection consent + file output
closecode notifications list/reply	Listener permission + reply
closecode files read/write/delete	Scoped shared folder + MediaStore
closecode signing generate/import	Release APK is signed and installs
8. Exact patch recipes
8.1 Gradle launcher (P0-1)
// After unpacking gradle-8.13-bin.zip
val launcher = File(gradleHome, "bin/gradle")
val original = launcher.readText()
val fixed = original.replace(
    "DEFAULT_JVM_OPTS='\"-Xmx64m\" \"-Xms64m\"'\" \\\"-javaagent:\$APP_HOME/lib/agents/gradle-instrumentation-agent-8.13.jar\\\"\"",
    "DEFAULT_JVM_OPTS=\"-Xmx64m -Xms64m -javaagent:\$APP_HOME/lib/agents/gradle-instrumentation-agent-8.13.jar\""
)
require(fixed != original) { "Gradle launcher layout changed; update the patch" }
launcher.writeText(fixed)
Or bypass the script:

val command = buildList {
    add(File(javaHome, "bin/java").absolutePath)
    add("-Xmx2048m"); add("-Dfile.encoding=UTF-8")
    add("-Dorg.gradle.appname=gradle")
    add("-classpath"); add(File(gradleHome, "lib/gradle-launcher-8.13.jar").absolutePath)
    add("org.gradle.launcher.GradleMain")
    addAll(gradleArgs)
}
8.2 AAPT2 (P0-2)
val aapt2 = File(sdkRoot, "build-tools/$buildToolsVersion/aapt2")
    .takeIf { it.isFile }
    ?: File(toolchainRoot, "aapt2").also { dest ->
        bundledAapt2.copyTo(dest, overwrite = true)
        dest.setExecutable(true, false)
    }
gradleArgs += "-Pandroid.aapt2FromMavenOverride=${aapt2.absolutePath}"
8.3 platform-tools + licenses (P0-3, P0-4)
val platformTools = SdkComponent(
    id = "platform-tools",
    version = "34.0.4",
    fileName = "platform-tools-34.0.4-aarch64.tar.xz",
    url = "https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/platform-tools-34.0.4-aarch64.tar.xz",
    target = File(sdkRoot, "platform-tools"),
)
Write the license files listed in P0-4 before the first Gradle invocation.

8.4 Exit codes (P0-5)
val result = runBuild(...)
emit(doneEvent(ok = result.ok, result = result))
if (!result.ok) exitProcess(1)
8.5 Diagnostics (P0-6)
fun rootCause(log: String): String {
    val block = log.substringAfter("* What went wrong:", "")
        .substringBefore("* Try:", "")
        .trim()
    if (block.isNotEmpty()) return block.lineSequence().first { it.isNotBlank() }.trim()
    return log.lineSequence().lastOrNull { it.contains("Caused by:") }?.trim()
        ?: log.takeLast(500)
}
8.6 Flag parsing (P1-2)
data class Parsed(val json: Boolean, val positional: List<String>)

fun parse(args: List<String>): Parsed {
    val flags = args.filter { it.startsWith("--") }
    val positional = args.filterNot { it.startsWith("--") }
    val unknown = flags - setOf("--json", "--yes", "--approve", ...)
    if (unknown.isNotEmpty()) fail("unknown option '${unknown.first()}'")
    return Parsed(json = "--json" in flags, positional = positional)
}
9. Definition of done
Run this on a clean install, with no manual Settings taps and no manual file edits:

closecode setup --gradle --json
closecode doctor --json
closecode build
closecode install
closecode run
closecode ui dump
Success criteria:

setup exits 0 and its self-test reports java, gradle, aapt2, and platform-tools as ok:true with real version strings.
doctor reports every required check ok:true.
closecode build applies AGP, compiles, and produces app/build/outputs/apk/debug/app-debug.apk.
A deliberately broken build exits non-zero and reports the root cause.
install installs the APK through PackageInstaller.
run launches it and waits for the foreground activity.
ui dump returns a tree (after the one-time accessibility tap).
Then repeat with closecode init --template android-compose and closecode selftest --build to prove the platform, not just Redit.

10. Local workarounds applied during this test
These were applied on the phone to keep testing. Replace them with the proper fixes above and remove them.

Workaround	Path	Remove when
Patched Gradle launcher DEFAULT_JVM_OPTS	/data/user/0/com.closecode/files/gradle/gradle-8.13/bin/gradle	P0-1 fixed
Gradle init script forcing a valid aapt2	/data/user/0/com.closecode/files/gradle/home/init.d/zz-closecode-aapt2-fix.gradle	P0-2 fixed
Installed arm64 platform-tools 34.0.4	/data/user/0/com.closecode/files/sdk/platform-tools	P0-3 fixed
Wrote SDK license files	/data/user/0/com.closecode/files/sdk/licenses	P0-4 fixed
Re-running closecode setup --gradle will overwrite the Gradle launcher and re-break the build until P0-1 is fixed in the unpack step.

11. Appendix — verified paths and values
PREFIX              /data/user/0/com.closecode/file/u
JDK                 /data/user/0/com.closecode/file/g/lib/jvm/java-17-openjdk   (17.0.21)
Gradle              /data/user/0/com.closecode/files/gradle/gradle-8.13
GRADLE_USER_HOME    /data/user/0/com.closecode/files/gradle/home
SDK                 /data/user/0/com.closecode/files/sdk
Platform            /data/user/0/com.closecode/files/sdk/platforms/android-35
Build-tools         /data/user/0/com.closecode/files/sdk/build-tools/35.0.1
Platform-tools      /data/user/0/com.closecode/files/sdk/platform-tools        (34.0.4 arm64)
Licenses            /data/user/0/com.closecode/files/sdk/licenses
Init script         /data/user/0/com.closecode/files/gradle/home/init/closecode.gradle
Versions

Component	Version
AGP	8.12.0
Gradle	8.13
JDK	17.0.21
Build-tools	35.0.1
Platform	android-35
Kotlin	1.9.24
Compose compiler	1.5.14
Official AGP compatibility

AGP	Gradle	Build Tools	JDK
8.12	8.13	35.0.0	17
9.5	9.6.0	36.0.0	17
Download sources

Gradle       https://services.gradle.org/distributions/gradle-8.13-bin.zip
             SHA-256 20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
Platform 35  https://dl.google.com/android/repository/platform-35_r02.zip
Build-tools  https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/build-tools-34.0.4-aarch64.tar.xz
Platform-tools https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/platform-tools-34.0.4-aarch64.tar.xz
JDK          Termux openjdk-17 (via gradle-tools.json)
12. Recommended order of work
P0-1 Gradle launcher (or bypass bin/gradle).
P0-2 AAPT2 name.
P0-3 + P0-4 platform-tools and licenses.
P0-5 + P0-6 exit codes and root-cause diagnostics.
P1-1 venv/pip.
P1-2 flag parsing.
P1-3/4/5 status semantics, streaming, self-test.
P2-1 consent confirmation.
P3 selftest coverage.
After 1–4, closecode build will produce an APK on the phone. After 5–8, the agent loop is trustworthy. After 9, regressions are caught automatically.