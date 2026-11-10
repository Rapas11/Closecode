# CloseCode release checklist

Run this on a clean install of the release build, on the phone, with no manual file edits. Every
step must pass before a build is published.

## 1. The toolchain installs itself

```
closecode setup --gradle --json
java -version
gradle -v
aapt2 version
```

- [ ] `setup --gradle` exits 0 and ends with `ready:true`.
- [ ] The self-test reports `java`, `gradle`, `aapt2` and `platform-tools` as `ok:true`, each
      with a real version string.
- [ ] The installed set is JDK 17, Gradle, platform 35, build-tools 35.0.1, platform-tools and
      the four SDK licence files. Nothing was downloaded by hand.
- [ ] `closecode setup --remove platform-tools` and `--remove licenses` remove them again, and
      `closecode setup --gradle` puts them back.

## 2. The build works, and fails honestly

```
closecode build; echo $?
closecode doctor --json
```

- [ ] `closecode build` prints `BUILD SUCCESSFUL`, exits 0 and produces
      `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Break one Kotlin line: `closecode build` exits non-zero, prints one root-cause line with
      the file and the line number, the structured diagnostics and the log path.
- [ ] `closecode doctor --json` reports `ok:true` and every required check `ok:true`. Optional
      checks (shell pack, NDK, CMake, accessibility, notifications) are listed apart.

## 3. Both engines produce the same app

```
closecode build && closecode install
closecode build --gradle && closecode install
```

- [ ] Installing one engine's APK over the other's works — the certificate SHA-256 is the same.
- [ ] `closecode install` on an app signed with another key answers
      `{"ok":false,"code":"signature_mismatch"}` with the remedy and the commands to fix it.

## 4. The app runs

```
closecode install
closecode run
closecode ui dump
```

- [ ] `closecode install` installs through PackageInstaller (the system asks once).
- [ ] `closecode run` launches it and waits for the foreground activity.
- [ ] `closecode ui dump` returns a tree after the one-time accessibility tap.
- [ ] `closecode install --open-settings` opens the unknown-apps screen and retries by itself
      once the permission is granted.

## 5. The agent never grants itself anything (P2-1)

```
closecode consent grant shell
closecode consent status --json
closecode audit tail
```

- [ ] `consent grant shell` answers `{"ok":false,"code":"needs_consent","needsUser":true}` and
      grants nothing.
- [ ] A notification asks for the permission; Settings → Agent permissions shows the request
      with Allow and Deny.
- [ ] Only the user's tap in the app flips the grant.
- [ ] The audit log holds the request and both the approval and the denial.

## 6. The self-test (P3)

```
closecode selftest
closecode selftest --build
closecode init --template android-compose
```

- [ ] Every template scaffolds, is detected and (with `--build`) builds.
- [ ] `secrets` — the value is never listed, survives a restart and can be deleted.
- [ ] `files` — write, read and delete inside the scoped folder.
- [ ] `deploy --dry-run` — the plan is produced without touching the network.
- [ ] `signing` — the keys are listed and the selected certificate has a SHA-256.
- [ ] `screen`, `notifications`, `test-ui` — the state is readable; the checks that need a
      system permission say so instead of pretending they passed.
- [ ] `test`, `lint`, `format`, `package`, `release` — every ecosystem dispatches to its tool.

## 7. Nothing is broken

- [ ] `:app:testDebugUnitTest` is green.
- [ ] `:app:assembleDebug` succeeds.
- [ ] The Redit project is untouched.
- [ ] The APK's signature SHA-256 is recorded in the release notes.
