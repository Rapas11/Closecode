CloseCode — build 90 brief: three bugs in the app itself
You are the developer of my phone app CloseCode. You have everything you need to build it. This file is the complete instruction for the next build — there is nothing else you need from me, and I am asking for one build, not a series.

Last build tested: 89 (CloseCode 0.89.1663, commit 5d91d26) Device: one Android phone, arm64, Android 16, 8 cores, 11.9 GB RAM Test project: com.rapas.redit — AGP 8.12.0, Kotlin 1.9.24, Compose BOM 2024.09.03, Media3 + MediaPipe + Vosk, minSdk 26, targetSdk 35

The short version (read this first)
I am the person who uses CloseCode on my phone. I want to build my Android app on my phone, with no computer and no AI helping me.

The build engine is finished — the acceptance script at the bottom of this file prints PASS 29, FAIL 0 on build 89. But the app itself has three bugs that stop me using it. All three are in the app, not in my project.

#	What I want	In one sentence
1	A new project I create in the app must compile	The "New project" screen still writes an old template, so every project I create there fails with "Unresolved reference 'Text'".
2	The "package name already used" message must be right	It refuses a name that is not used anywhere on the phone.
3	The app's own self-test must pass	It fails at step 10 "Full test build" because the bundled aapt2 is too old for Android 15.
How you will know it worked: run the script at the end of this file on the phone. When it prints FAIL 0, we are done.

Please do not send me a build before that script says FAIL 0.

Where we are
Build 89 is good. The build engine works, and I have proved it by building on the phone:

Result
My app — Media3, MediaPipe, Vosk, Compose	✅ builds three ways, one signing key
A brand-new Android app from the CLI template	✅ 246 s, signed, no manual edits
An app with native C++ (CMake + NDK + JNI)	✅ lib/arm64-v8a/libnativetest.so in the APK
A release build	✅ signed, installable
Unit tests and lint	✅ pass
All six CLI templates	✅ generate correct files
Python venvs	✅ pip install works inside them
A fresh NDK install	✅ the compiler runs — you fixed the bash wrappers and the symlinks
A broken download	✅ resumes, and cleans up afterwards
Removing a tool	✅ the 1.6 GB cache survives
closecode speed	✅ makes my builds faster
What is left is the app's own behaviour, which the script cannot see.

Bug 1 — a project I create in the app must compile
What I see
I create a project from the New project screen in the app, enter a package name, and the project it creates will not compile:

MainActivity.kt
 12                 Text("Hello, world")
     ^^^^^^^^^^^^^^ Unresolved reference 'Text'   E0101
The cause
There are two copies of the template, and only one of them was fixed.

Build 84 fixed the template. I checked all three copies on the phone:

Where the file came from	Has import androidx.compose.material3.Text?
closecode init --template android-compose	✅ yes
The app's own self-test scaffold (cache/self-test/build-app)	✅ yes
The New project screen in the app (projects/buildprobe)	❌ no
The file the editor writes still looks like this:

package com.closecode.buildprobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClosecodeTheme {
                Text("Hello, world")      // ← Text is never imported
            }
        }
    }
}
So every project created from the app's own screen fails its first build, with an error the user has to read in a source file.

What to do
One template, two front ends. The New project screen and closecode init must write the same files from the same source. If the templates are in the APK's assets, update the copy the editor uses. If they are in two places in the code, make them one place — otherwise they will drift apart again, which is exactly what happened.
Build what you generate before the user gets it. closecode selftest already scaffolds every template — please make it compile them too, not only create them. A template that does not compile is a broken product.
Done when
a project created from the New project screen builds with no manual edit
closecode init and the New project screen produce identical files
the acceptance script's step 16 passes
Bug 2 — the "package name already used" message must be right
What I see
I enter a package name I have never used, and the app says it is taken:

Package name
com.rrbndbbdhdh.dibsh
That package name is already used by another project. Pick a unique one.
The same message appears twice — once under the field and once at the bottom of the form.

The proof it is wrong
That name exists nowhere on the phone:

$ grep '"packageName"' files/project-registry.json | sort -u
"packageName":"com.closecode.apitest"     "packageName":"com.closecode.buildprobe"
"packageName":"com.closecode.gjb"         "packageName":"com.closecode.hh"
…                                         "packageName":"com.rapas.redit"
(no com.rrbndbbdhdh.dibsh)

$ closecode apps list | grep rrbndbbdhdh
(nothing)
So it is not registered as a project and not installed on the phone — yet the form refuses it.

Two things I noticed that point at the cause
1. The registry is allowed to hold duplicates. While testing I created a project twice and the app happily wrote two entries with the same package name:

"slug":"nativetest",  "packageName":"com.closecode.nativetest"
"slug":"nativetest-2","packageName":"com.closecode.nativetest"
So the uniqueness rule is not applied everywhere — the CLI does not check it at all. That suggests the editor's check is comparing against something that includes the entry for the project being created, so it finds itself and reports a clash.

2. If it matches only a part of the name, every name fails. Every package name registered on this phone starts with com. If the check compares the first segment of what I typed, or looks for the new name as a substring of the existing ones, then com matches everything and no name is ever accepted — which is what I am seeing.

I cannot tell which of the two it is without your code, but both produce exactly this symptom.

What to do
Compare the whole package name, against the registry's package names and the installed apps. Never a fragment, a segment or a substring.
Exclude the project being created from the comparison. If the editor writes its registry entry before it validates, it will always find a match.
Apply the same rule in both places. The CLI must refuse a duplicate too, so the two front ends cannot disagree.
Show the name that is in the way. If it really is taken, say which project holds it. Right now the message gives the user nothing to act on.
Done when
a package name that is not used anywhere is accepted
a package name that is used is refused, and the message names the project using it
the check behaves the same in the app and in closecode init
Bug 3 — the app's own self-test must pass
What I see
In the app, Developer → Self-test, step 10 fails:

10. Full test build          Fail
    Compile resources: The resources did not compile. See the build log.
Every other step passes (Java compiler, D8, signing key, install permission, network, shell).

The cause
The build log says:

== Compile resources
version 1 (1.0) from the project
error: failed to load include path /data/user/0/com.closecode/files/toolchain/1/android-35.jar.
The app's own aapt2 cannot read the API 35 platform resources:

$ closecode tool aapt2 dump badging sdk/platforms/android-35/android.jar
error: illegal map type 'string' (22).
error: failed to parse value for resource android:string/ (0x010407c1) with configuration 'am'.
The SDK's aapt2 reads the very same file without trouble:

$ sdk/build-tools/35.0.1/aapt2 dump badging sdk/platforms/android-35/android.jar
package: name='android' versionCode='35' versionName='15'
minSdkVersion:'35'
Two copies of android.jar are involved and they are byte-for-byte identical (cmp says so), so the file is not the problem — the binary is. The aapt2 the phone engine uses is an older build whose resource-table parser does not understand Android 15's format. Its banner gives it away:

app's aapt2 : aapt (aapt) 2.19-OS3.0.303.0.WPFEUXM
SDK's aapt2 : Android Asset Packaging Tool (aapt) 2.19-SOONG BUILD NUMBER PLACEHOLDER
What to do
Use the SDK's aapt2 for the phone engine. It is already installed, it is arm64, and it parses the API 35 resources correctly. The Gradle path already does this through android.aapt2FromMavenOverride; the phone engine should point at the same one.
Or update the bundled aapt2 to a build that understands the platform resource table you ship.
Make the self-test a gate. Step 10 exists to catch exactly this. It should fail the release, not sit there red.
Done when
the self-test in the app shows every step green
closecode selftest passes on a fresh phone
the acceptance script's step 17 passes
Rules
One installer, two front ends. Anything in Settings must also be reachable from closecode setup, and the reverse. One template, two front ends, too.
The app installs, the user consents. The AI must never need curl, tar, chmod, or a hand-written license file.
Never destroy a working install or a cache to install or remove something.
One signing key for every engine and every build path.
A failed build exits non-zero with one root cause, diagnostics and a log path.
Reject unknown flags. Unknown arguments print usage and exit with no side effects.
Never claim a capability the code does not have. If a screen says a tool works, it must work. A command that produces an unsigned APK must not report ok:true. A command that reports a certificate must report the one the file really has.
A template that does not compile is a broken product. Anything you generate must be built and run before it reaches a user.
A path means what the user typed. Resolve it against the folder they are in.
Never install something that cannot run on this phone, and never leave a half-installed package where a build can find it.
A validation message must be true. If the app says a name is taken, it is taken. If it is not, the message is worse than no message.
One tool, one answer. The phone engine and the Gradle path must not disagree about what a file is or whether it works.
Before you ship — run this yourself
Do not send me a build until these are true on your side. Each one takes a minute.

# bug 1 — the editor's template
# create a project from the New project screen, then:
grep -n "material3.Text" <project>/app/src/main/java/*/*/MainActivity.kt

# bug 2 — the uniqueness check
# enter a package name that exists nowhere, such as com.zztest.aa11
# it must be accepted

# bug 3 — the phone engine's aapt2
closecode tool aapt2 dump badging sdk/platforms/android-35/android.jar
# must print: package: name='android' versionCode='35'

closecode selftest                       # every step green
sh closecode-acceptance.sh               # the whole board, steps 16 and 17 are new
Then, and only then, send me the board.

The test script
Save this as closecode-acceptance.sh next to the project.

sh closecode-acceptance.sh          # safe checks, about 10 minutes
sh closecode-acceptance.sh --all    # also removes and reinstalls Gradle
It is written for Android's toybox, so it uses grep -E for alternation. The only thing it changes is a temporary syntax error in app/build.gradle.kts, which it puts back immediately.

Steps 16 and 17 are new:

16 — every project in the registry imports what its source uses
17 — the phone engine can read the platform resources
#!/system/bin/sh
# =============================================================================
# CloseCode acceptance test — run it ON THE PHONE, no computer needed.
#
#   sh closecode-acceptance.sh          safe checks only
#   sh closecode-acceptance.sh --all    also runs the destructive checks
#                                           (destroys the Gradle cache)
#
# Exit code 0 = everything passed. Non-zero = read the FAIL lines.
# Every check prints PASS / FAIL / SKIP with the number it measured.
#
# NOTE: this runs on Android's toybox, so it uses `grep -E` for alternation
# (toybox grep does not understand `\|`).
# =============================================================================

PASS=0
FAIL=0
SKIP=0
FAILED_LINES=""
ROOT=$(cd "$(dirname "$0")" && pwd)
TMP="$ROOT/.acceptance"
RUN_ALL="$1"

say()  { printf '%s\n' "$*"; }
ok()   { PASS=$((PASS+1)); printf '  PASS  %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); FAILED_LINES="$FAILED_LINES
    - $1"; printf '  FAIL  %s\n' "$1"; }
skip() { SKIP=$((SKIP+1)); printf '  SKIP  %s\n' "$1"; }
head_() { printf '\n%s\n' "$1"; }

# ---- small helpers ----------------------------------------------------------
jget() {  # jget <file> <key>  -> first string value of key
  sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" "$1" 2>/dev/null | head -1
}
jnum() {  # jnum <file> <key>  -> first numeric value of key
  sed -n "s/.*\"$2\":\([0-9][0-9]*\).*/\1/p" "$1" 2>/dev/null | head -1
}
du_k() { du -sk "$1" 2>/dev/null | cut -f1; }

rm -rf "$TMP"; mkdir -p "$TMP"
STATUS="$TMP/status.json"

say "==========================================================="
say " CloseCode acceptance test"
say " project : $ROOT"
say " date    : $(date '+%Y-%m-%d %H:%M')"
say "==========================================================="

# =============================================================================
head_ "1. Toolchain is installed and complete"
closecode toolchain status --json > "$STATUS" 2>/dev/null
if [ ! -s "$STATUS" ]; then
  bad "closecode toolchain status produced no output"
else
  GRADLE_HOME=$(jget "$STATUS" gradleUserHome)
  SDK_ROOT=$(jget "$STATUS" sdkRoot)
  JDK_PATH=$(sed -n 's/.*"jdk":{[^}]*"path":"\([^"]*\)".*/\1/p' "$STATUS" | head -1)

  [ -n "$JDK_PATH" ] && [ -x "$JDK_PATH/bin/java" ] \
    && ok "JDK installed: $JDK_PATH" || bad "no usable JDK reported by toolchain status"

  [ -n "$GRADLE_HOME" ] && [ -d "$GRADLE_HOME" ] \
    && ok "GRADLE_USER_HOME exists: $GRADLE_HOME" || bad "GRADLE_USER_HOME missing"

  [ -n "$SDK_ROOT" ] && [ -d "$SDK_ROOT" ] \
    && ok "SDK root exists: $SDK_ROOT" || bad "SDK root missing"

  grep -q '"api":35,"path":"[^"]*","ok":true' "$STATUS" \
    && ok "Android platform 35 installed" || bad "Android platform 35 not installed"

  grep -q '"buildTools":\[{"version"' "$STATUS" \
    && ok "build-tools installed" || bad "build-tools not installed"

  [ -d "$SDK_ROOT/platform-tools" ] \
    && ok "platform-tools installed" || bad "platform-tools missing"

  if [ -f "$SDK_ROOT/licenses/android-sdk-license" ]; then
    ok "SDK licenses accepted ($(ls "$SDK_ROOT/licenses" | wc -l | tr -d ' ') files)"
  else
    bad "SDK licenses missing (android-sdk-license not found)"
  fi

  [ -d "$SDK_ROOT/ndk" ] && [ -d "$SDK_ROOT/cmake" ] \
    && ok "NDK and CMake installed" || bad "NDK or CMake missing"
fi

# =============================================================================
head_ "2. The size the app shows matches the disk"
if [ -n "$SDK_ROOT" ] && [ -d "$SDK_ROOT" ]; then
  REPORTED=$(jnum "$STATUS" sdkBytes)
  # du of everything in the SDK except the native toolchain, which the app
  # reports separately under "Native tools".
  ONDISK=0
  for D in build-tools platforms platform-tools licenses; do
    K=$(du_k "$SDK_ROOT/$D" 2>/dev/null)
    [ -n "$K" ] && ONDISK=$((ONDISK + K))
  done
  REPORTED_K=0
  [ -n "$REPORTED" ] && REPORTED_K=$((REPORTED / 1024))
  if [ "$ONDISK" -gt 0 ] && [ "$REPORTED_K" -gt 0 ]; then
    say "        app reports ${REPORTED_K} KB, disk has ${ONDISK} KB"
    # A previous build showed 3.6 GB for 1.8 GB of NDK + CMake, exactly double.
    # Catch that class of bug without demanding byte-exact accounting.
    if [ "$REPORTED_K" -gt $((ONDISK * 3 / 2)) ] || [ "$REPORTED_K" -lt $((ONDISK / 2)) ]; then
      bad "reported SDK size is more than 1.5x away from the disk"
    else
      ok "reported SDK size is in the right range"
    fi
  else
    skip "no size reported by toolchain status"
  fi
else
  skip "SDK root missing"
fi

# =============================================================================
head_ "3. closecode build produces a signed APK"
# Remove the old logs so check 9 only sees what this build writes.
rm -f "$ROOT/build/logs/build.log" "$ROOT/build/logs/exec.log" 2>/dev/null
closecode build > "$TMP/build.json" 2>&1
BUILD_EXIT=$?
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ "$BUILD_EXIT" -eq 0 ] && [ -f "$APK" ]; then
  ok "closecode build exited 0 ($(wc -c < "$APK" | tr -d ' ') bytes)"
  cp "$APK" "$TMP/apk-pipeline.apk"
else
  bad "closecode build exited $BUILD_EXIT"
  tail -c 400 "$TMP/build.json" | sed 's/^/        /'
fi

# =============================================================================
head_ "4. ./gradlew assembleDebug succeeds"
if [ -x "$ROOT/gradlew" ]; then
  ( cd "$ROOT" && ./gradlew assembleDebug ) > "$TMP/gradlew.log" 2>&1
  GW_EXIT=$?
  if [ "$GW_EXIT" -eq 0 ]; then
    ok "./gradlew assembleDebug exited 0"
    cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$TMP/apk-gradle.apk"
  else
    bad "./gradlew assembleDebug exited $GW_EXIT"
    grep -iE "error|what went wrong" "$TMP/gradlew.log" | head -3 | sed 's/^/        /'
  fi
else
  skip "no gradlew shim in the project"
fi

# =============================================================================
head_ "5. Both build paths sign with the same key"
[ -n "$JAVA_HOME" ] || export JAVA_HOME="$JDK_PATH"
# Some installed build-tools have '#!/bin/bash' shebangs, which Android does not
# have, so try every apksigner until one actually answers.
cert_of() {  # cert_of <apk> -> signer certificate SHA-256, or empty
  for C in "$SDK_ROOT"/build-tools/*/apksigner; do
    [ -f "$C" ] || continue
    OUT=$("$C" verify --print-certs "$1" 2>/dev/null | grep "SHA-256 digest" | head -1 | sed 's/.*digest: //')
    if [ -n "$OUT" ]; then echo "$OUT"; return; fi
  done
  echo ""
}
if [ ! -f "$TMP/apk-pipeline.apk" ] || [ ! -f "$TMP/apk-gradle.apk" ]; then
  skip "no APK from one of the two paths"
else
  CERT_A=$(cert_of "$TMP/apk-pipeline.apk")
  CERT_B=$(cert_of "$TMP/apk-gradle.apk")
  say "        closecode build : ${CERT_A:-none}"
  say "        ./gradlew       : ${CERT_B:-none}"
  if [ -z "$CERT_A" ] || [ -z "$CERT_B" ]; then
    skip "no working apksigner found (build-tools scripts may need /bin/sh)"
  elif [ "$CERT_A" = "$CERT_B" ]; then
    ok "both build paths sign with the same key"
  else
    bad "the two build paths use different signing keys"
  fi
  if [ "$CERT_A" = "30fae35d64645323304340c4f570a58377de8153477a5e334dc62c036a87f979" ]; then
    ok "the key is CN=Closecode Debug"
  else
    bad "the key is not CN=Closecode Debug"
  fi
fi

# =============================================================================
head_ "6. A failed build exits non-zero with one clear cause"
GRADLE_FILE="$ROOT/app/build.gradle.kts"
if [ -f "$GRADLE_FILE" ]; then
  cp "$GRADLE_FILE" "$TMP/build.gradle.kts.bak"
  printf '\nthis is not valid kotlin !!!\n' >> "$GRADLE_FILE"
  ( cd "$ROOT" && closecode build ) > "$TMP/badbuild.log" 2>&1
  BAD_EXIT=$?
  cp "$TMP/build.gradle.kts.bak" "$GRADLE_FILE"        # always put it back
  if [ "$BAD_EXIT" -ne 0 ]; then
    ok "a broken project exits $BAD_EXIT (non-zero)"
    if grep -qE "Gradle failed \(exit 1\)" "$TMP/badbuild.log"; then
      ok "the failure names one root cause"
      grep -o '"summary":"[^"]*"' "$TMP/badbuild.log" | head -1 | sed 's/^/        /'
    else
      bad "the failure does not name one root cause"
    fi
    if grep -qE "[a-zA-Z]+\.log" "$TMP/badbuild.log"; then
      ok "the failure output names a log file"
    else
      bad "the failure output does not name a log file"
    fi
  else
    bad "a broken project built successfully"
  fi
else
  skip "no app/build.gradle.kts to break"
fi

# =============================================================================
head_ "7. A JDK can be chosen, and the choice is real"
JDK21="/data/user/0/com.closecode/file/j/lib/jvm/java-21-openjdk"
if [ ! -x "$JDK21/bin/java" ]; then
  skip "JDK 21 is not installed (closecode setup --java21)"
else
  # a) some command must accept a JDK selection
  ACCEPTED=""
  for KEY in jdk javaHome java.home jdkPath java; do
    OUT=$(closecode config set "$KEY" "$JDK21" 2>&1)
    if ! echo "$OUT" | grep -qi "unknown key"; then
      ACCEPTED="$KEY"
      break
    fi
  done
  if [ -n "$ACCEPTED" ]; then
    ok "closecode config set $ACCEPTED <jdk> is accepted"
  else
    bad "no command can select a JDK (config set rejects every key)"
  fi

  # b) the toolchain status must show the selection
  closecode toolchain status --json > "$STATUS" 2>/dev/null
  if grep -q "java-21-openjdk" "$STATUS"; then
    ok "toolchain status reports the selected JDK 21"
  else
    bad "toolchain status does not report the selected JDK 21"
  fi

  # c) the wrapper must use it for launcher AND daemon
  ( cd "$ROOT" && env -u JAVA_HOME ./gradlew --version ) > "$TMP/version.log" 2>&1
  LAUNCH=$(grep "Launcher JVM" "$TMP/version.log" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
  DAEMON=$(grep "Daemon JVM" "$TMP/version.log" | grep -oE 'java-[0-9]+' | head -1)
  if [ "$DAEMON" = "java-21" ]; then
    ok "./gradlew daemon runs on the selected JDK ($DAEMON)"
  else
    bad "./gradlew daemon runs on ${DAEMON:-unknown}, expected java-21"
  fi
  case "$LAUNCH" in
    21.*) ok "./gradlew launcher runs on the selected JDK ($LAUNCH)" ;;
    *)    bad "./gradlew launcher runs on ${LAUNCH:-unknown}, expected 21.x" ;;
  esac

  # d) closecode build must use the same JDK
  closecode build > "$TMP/build21.json" 2>&1
  if grep -qE "21\.0\." "$ROOT/build/logs/exec.log" 2>/dev/null \
     || grep -qE "21\.0\." "$ROOT/build/logs/build.log" 2>/dev/null; then
    ok "closecode build ran on JDK 21"
  else
    bad "closecode build did not run on JDK 21"
    head -2 "$ROOT/build/logs/exec.log" 2>/dev/null | sed 's/^/        /'
  fi

  # put it back
  if [ -n "$ACCEPTED" ]; then closecode config unset "$ACCEPTED" >/dev/null 2>&1; fi
fi

# =============================================================================
head_ "8. python3 -m venv works"
( cd "$TMP" && python3 -m venv venvtest ) > "$TMP/venv.log" 2>&1
if [ -x "$TMP/venvtest/bin/pip" ] && "$TMP/venvtest/bin/pip" --version >/dev/null 2>&1; then
  ok "python3 -m venv creates a working pip"
else
  bad "python3 -m venv does not create a working pip"
  tail -2 "$TMP/venv.log" | sed 's/^/        /'
fi

# =============================================================================
head_ "9. The build log contains no error"
# Step 6 breaks the build on purpose, so rebuild cleanly first: this check must
# look at the log of a successful build, not of the deliberate failure.
rm -f "$ROOT/build/logs/build.log" "$ROOT/build/logs/exec.log" 2>/dev/null
closecode build > "$TMP/build9.json" 2>&1
if [ -f "$ROOT/build/logs/build.log" ]; then
  if grep -qE "BUILD FAILED|^FAILURE:" "$ROOT/build/logs/build.log"; then
    bad "build/logs/build.log contains a failed build"
    grep -m2 -E "BUILD FAILED|^FAILURE:" "$ROOT/build/logs/build.log" | sed 's/^/        /'
  else
    ok "build/logs/build.log has no failed build"
  fi
  if grep -q "Could not connect to Kotlin compile daemon" "$ROOT/build/logs/build.log" 2>/dev/null; then
    printf '  WARN  the Kotlin compile daemon did not connect; the build fell back to in-process compilation\n'
  fi
else
  skip "no build/logs/build.log"
fi
if [ -f "$ROOT/build/logs/exec.log" ]; then
  if grep -qE "Error|Exception" "$ROOT/build/logs/exec.log"; then
    bad "build/logs/exec.log contains an error"
    grep -m2 -E "Error|Exception" "$ROOT/build/logs/exec.log" | sed 's/^/        /'
  else
    ok "build/logs/exec.log is clean"
  fi
else
  skip "the build does not write build/logs/exec.log"
fi

# =============================================================================
head_ "10. Removing a component keeps the dependency cache"
if [ "$RUN_ALL" = "--all" ]; then
  say "        removing Gradle and reinstalling — this takes about 25 minutes"
  GRADLE_HOME=$(jget "$STATUS" gradleUserHome)
  before=$(du_k "$GRADLE_HOME" 2>/dev/null)
  closecode setup --remove gradle > "$TMP/remove.log" 2>&1
  if [ -d "$GRADLE_HOME" ]; then
    after=$(du_k "$GRADLE_HOME" 2>/dev/null)
    say "        cache before ${before} KB, after ${after} KB"
    if [ "$after" -ge "$before" ]; then
      ok "removing Gradle kept the dependency cache"
    else
      bad "removing Gradle deleted the dependency cache"
    fi
    closecode setup --gradle > "$TMP/reinstall.log" 2>&1
    ok "Gradle reinstalled"
  else
    bad "removing Gradle deleted $GRADLE_HOME (the whole cache)"
    closecode setup --gradle > "$TMP/reinstall.log" 2>&1
    ok "Gradle reinstalled"
  fi
else
  skip "destructive — run with --all to test it"
fi

# =============================================================================
head_ "11. A failed download resumes"
skip "needs a network that fails on purpose — test manually"

# =============================================================================
head_ "12. Commands that take a path accept a relative one"
cd "$TMP" 2>/dev/null || cd "$ROOT"
if [ -f "$TMP/apk-pipeline.apk" ]; then
  cp "$TMP/apk-pipeline.apk" "$TMP/rel.apk"
  OUT=$(closecode sign-apk rel.apk 2>&1)
  if echo "$OUT" | grep -qi '"ok":true'; then
    ok "closecode sign-apk rel.apk works with a relative path"
  else
    bad "closecode sign-apk does not accept a relative path"
    echo "$OUT" | head -c 200 | sed 's/^/        /'
  fi
else
  skip "no APK to test with"
fi
cd "$TMP" 2>/dev/null || cd "$ROOT"
rm -rf "$TMP/relvenv" 2>/dev/null
OUT=$(closecode venv relvenv 2>&1)
if echo "$OUT" | grep -qi '"ok":true'; then
  ok "closecode venv relvenv works with a relative path"
else
  bad "closecode venv does not accept a relative path"
  echo "$OUT" | head -c 200 | sed 's/^/        /'
fi

# =============================================================================
head_ "13. Every installed build-tools can run on this phone"
BAD=""
for D in "$SDK_ROOT"/build-tools/*/; do
  [ -d "$D" ] || continue
  A="$D/aapt2"
  [ -f "$A" ] || continue
  if ! "$A" version >/dev/null 2>&1; then
    BAD="$BAD $(basename "$D")"
  fi
done
if [ -n "$BAD" ]; then
  bad "these installed build-tools cannot run on this phone:$BAD"
  for D in "$SDK_ROOT"/build-tools/*/; do
    [ -f "$D/aapt2" ] || continue
    "$D/aapt2" version 2>&1 | head -1 | sed 's/^/        /'
  done
else
  ok "every installed build-tools runs on this phone"
fi

# =============================================================================
head_ "14. No half-finished install is left in the SDK"
LEFTOVER=$(find "$SDK_ROOT" -maxdepth 3 -name ".installer" 2>/dev/null | head -3)
if [ -n "$LEFTOVER" ]; then
  bad "an unfinished install is still in the SDK"
  printf '%s\n' "$LEFTOVER" | sed 's/^/        /'
else
  ok "no unfinished install is left in the SDK"
fi

# =============================================================================
head_ "15. The NDK compiler runs on this phone"
NDK_DIR=$(ls -d "$SDK_ROOT"/ndk/*/ 2>/dev/null | head -1)
if [ -z "$NDK_DIR" ]; then
  skip "no NDK installed"
else
  CC=""
  for CAND in "$NDK_DIR"toolchains/llvm/prebuilt/linux-aarch64/bin/aarch64-linux-android21-clang \
              "$NDK_DIR"toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang; do
    [ -f "$CAND" ] && CC="$CAND" && break
  done
  if [ -z "$CC" ]; then
    bad "the NDK has no android clang"
  elif "$CC" --version >/dev/null 2>&1; then
    ok "the NDK compiler runs on this phone"
  else
    bad "the NDK compiler cannot run on this phone"
    "$CC" --version 2>&1 | head -1 | sed 's/^/        /'
    head -1 "$CC" | sed 's/^/        shebang: /'
  fi
  # broken symlinks that were extracted as plain text files
  BIN="$NDK_DIR"toolchains/llvm/prebuilt/linux-aarch64/bin
  BROKEN=0
  for f in "$BIN"/*; do
    [ -f "$f" ] || continue
    SIZE=$(wc -c < "$f" 2>/dev/null || echo 0)
    if [ "$SIZE" -gt 0 ] && [ "$SIZE" -lt 40 ]; then
      TARGET=$(cat "$f" 2>/dev/null)
      if [ -n "$TARGET" ] && [ -f "$BIN/$TARGET" ]; then
        BROKEN=$((BROKEN+1))
      fi
    fi
  done
  if [ "$BROKEN" -gt 0 ]; then
    bad "$BROKEN NDK symlinks were extracted as plain text files"
  else
    ok "the NDK symlinks are intact"
  fi
fi

# =============================================================================
head_ "16. Every generated project uses the imports it needs"
BADPROJ=""
for PROJ in /data/user/0/com.closecode/files/projects/*/; do
  [ -d "$PROJ" ] || continue
  for KT in "$PROJ"app/src/main/java/*/*/MainActivity.kt; do
    [ -f "$KT" ] || continue
    if grep -q "Text(" "$KT" 2>/dev/null && ! grep -q "material3.Text" "$KT" 2>/dev/null; then
      BADPROJ="$BADPROJ $(basename "$PROJ")"
    fi
  done
done
if [ -n "$BADPROJ" ]; then
  bad "these projects were generated without the Text import:$BADPROJ"
else
  ok "every generated project imports what it uses"
fi

# =============================================================================
head_ "17. The phone engine can read the platform resources"
PJ=$(ls "$SDK_ROOT"/platforms/android-*/android.jar 2>/dev/null | head -1)
if [ -z "$PJ" ]; then
  skip "no platform android.jar found"
else
  OUT=$(closecode tool aapt2 dump badging "$PJ" 2>&1 | head -2)
  if echo "$OUT" | grep -q "package: name='android'"; then
    ok "the phone engine reads the platform resources"
  else
    bad "the phone engine cannot read the platform resources"
    printf '%s\n' "$OUT" | sed 's/^/        /'
  fi
fi

# =============================================================================
say ""
say "==========================================================="
say " PASS $PASS   FAIL $FAIL   SKIP $SKIP"
if [ "$FAIL" -gt 0 ]; then
  say ""
  say " Still failing:"
  printf '%s\n' "$FAILED_LINES"
fi
say "==========================================================="
rm -rf "$TMP"
[ "$FAIL" -eq 0 ] && exit 0
exit 1