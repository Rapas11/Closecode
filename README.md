# CloseCode

CloseCode is an Android IDE for a phone: editor, Git, an AI tab, and a build
engine that runs on the device. It can build ordinary Kotlin/Java projects with
its own engine, or use an optional downloaded JDK and Gradle when a project
needs Gradle.

## Build and test

Use JDK 17 and an Android SDK with platform 35 and build-tools 34.0.4:

```sh
JAVA_HOME=/opt/java/jdk-17.0.20.1+1 \
ANDROID_HOME=/opt/android_sdk \
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## On-device workflows

Build, install and drive the app from the `closecode` command:

```sh
closecode build --release
closecode run
closecode app
closecode ui dump
closecode ui tap --text "Continue"
closecode logcat
```

## Optional toolchains

Settings → Build & Toolchain contains:

- **SDK Manager** for platforms and build-tools.
- **Full Gradle** for OpenJDK 17 and Gradle 8.13, used by `--gradle`.
- **Native tools** for the NDK and CMake, used only by C/C++ projects.

Native projects and projects with custom dependencies, product flavours, R8,
unit tests, lint or App Bundles should use Full Gradle:

```sh
closecode build --gradle
closecode gradle bundleRelease --gradle
closecode gradle test --gradle
closecode gradle lint --gradle
closecode gradle check --gradle
closecode gradle tasks --gradle
```

Real Gradle tasks stream their output. Builds do not run in the background.

## Project state

`ROADMAP.md` has the current status and device-test checklist. `HANDOFF.md` has
the implementation history and resume notes.
