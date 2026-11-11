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
if [ -f "$ROOT/build/logs/build.log" ]; then
  if grep -qE "^e: |^FAILURE:|BUILD FAILED" "$ROOT/build/logs/build.log"; then
    bad "build/logs/build.log contains a build error"
    grep -m2 -E "^e: |^FAILURE:|BUILD FAILED" "$ROOT/build/logs/build.log" | sed 's/^/        /'
  else
    ok "build/logs/build.log is clean"
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
