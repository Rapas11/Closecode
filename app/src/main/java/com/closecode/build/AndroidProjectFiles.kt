package com.closecode.build

import java.io.File

/**
 * Files a Gradle Android project needs on a phone. The wrapper is a delegate, not the
 * desktop wrapper: it runs the Gradle distribution installed from Settings, so opening a
 * PC-created project does not download a second copy of the same distribution.
 */
object AndroidProjectFiles {

    const val GRADLEW = "#!/system/bin/sh\n" +
        "# CloseCode shim v2: delegate to the Gradle installed in Settings.\n" +
        "GRADLE_BIN=\"\$CLOSECODE_GRADLE\"\n" +
        "if [ -z \"\$GRADLE_BIN\" ] || [ ! -x \"\$GRADLE_BIN\" ]; then\n" +
        "  GRADLE_BIN=\$(command -v gradle 2>/dev/null)\n" +
        "fi\n" +
        "if [ -z \"\$GRADLE_BIN\" ]; then\n" +
        "  echo \"Gradle is not installed. Open Settings > Build and Toolchain > Full Gradle.\" >&2\n" +
        "  exit 127\n" +
        "fi\n" +
        "DIST_HOME=\$(dirname \"\$(dirname \"\$GRADLE_BIN\")\")\n" +
        "GRADLE_ROOT_DIR=\$(dirname \"\$DIST_HOME\")\n" +
        "FILES_DIR=\$(dirname \"\$GRADLE_ROOT_DIR\")\n" +
        "DATA_DIR=\$(dirname \"\$FILES_DIR\")\n" +
        "if [ -z \"\$GRADLE_USER_HOME\" ] && [ -d \"\$GRADLE_ROOT_DIR/home\" ]; then\n" +
        "  export GRADLE_USER_HOME=\"\$GRADLE_ROOT_DIR/home\"\n" +
        "fi\n" +
        "PROPS=\"\${GRADLE_USER_HOME:-\$GRADLE_ROOT_DIR/home}/gradle.properties\"\n" +
        "SDK_DIR=\"\${ANDROID_HOME:-\${ANDROID_SDK_ROOT:-\$FILES_DIR/sdk}}\"\n" +
        "AAPT2=\"\$SDK_DIR/build-tools/35.0.1/aapt2\"\n" +
        "if [ ! -x \"\$AAPT2\" ]; then\n" +
        "  AAPT2=\"\"\n" +
        "  for CANDIDATE in \"\$SDK_DIR\"/build-tools/*/aapt2; do\n" +
        "    if [ -x \"\$CANDIDATE\" ]; then AAPT2=\"\$CANDIDATE\"; fi\n" +
        "  done\n" +
        "fi\n" +
        "if [ -n \"\$AAPT2\" ]; then\n" +
        "  mkdir -p \"\$(dirname \"\$PROPS\")\"\n" +
        "  if ! grep -qxF \"android.aapt2FromMavenOverride=\$AAPT2\" \"\$PROPS\" 2>/dev/null; then\n" +
        "    if [ -f \"\$PROPS\" ]; then\n" +
        "      grep -v '^android.aapt2FromMavenOverride=' \"\$PROPS\" > \"\$PROPS.tmp\"\n" +
        "    else\n" +
        "      : > \"\$PROPS.tmp\"\n" +
        "    fi\n" +
        "    printf 'android.aapt2FromMavenOverride=%s\\n' \"\$AAPT2\" >> \"\$PROPS.tmp\"\n" +
        "    mv \"\$PROPS.tmp\" \"\$PROPS\"\n" +
        "  fi\n" +
        "fi\n" +
        "SELECTED_JAVA=\"\"\n" +
        "if [ -f \"\$PROPS\" ]; then\n" +
        "  while IFS='=' read -r KEY VALUE; do\n" +
        "    if [ \"\$KEY\" = \"org.gradle.java.home\" ]; then SELECTED_JAVA=\"\$VALUE\"; fi\n" +
        "  done < \"\$PROPS\"\n" +
        "fi\n" +
        "if [ -n \"\$SELECTED_JAVA\" ] && [ -x \"\$SELECTED_JAVA/bin/java\" ]; then\n" +
        "  export JAVA_HOME=\"\$SELECTED_JAVA\"\n" +
        "elif [ -z \"\$JAVA_HOME\" ]; then\n" +
        "  for CANDIDATE in \"\$DATA_DIR/file/g/lib/jvm/java-17-openjdk\" " +
        "\"\$DATA_DIR/file/u/lib/jvm/java-17-openjdk\" " +
        "\"\$DATA_DIR/file/j/lib/jvm/java-21-openjdk\"; do\n" +
        "    if [ -x \"\$CANDIDATE/bin/java\" ]; then\n" +
        "      export JAVA_HOME=\"\$CANDIDATE\"\n" +
        "      break\n" +
        "    fi\n" +
        "  done\n" +
        "fi\n" +
        "CLOSECODE_CLI=\"\$FILES_DIR/opencode/bin/closecode\"\n" +
        "if [ ! -x \"\$CLOSECODE_CLI\" ]; then\n" +
        "  CLOSECODE_CLI=\$(command -v closecode 2>/dev/null)\n" +
        "fi\n" +
        "\"\$GRADLE_BIN\" \"\$@\"\n" +
        "GRADLE_CODE=\$?\n" +
        "if [ \"\$GRADLE_CODE\" -eq 0 ] && [ -n \"\$CLOSECODE_CLI\" ]; then\n" +
        "  find \"\$PWD\" -maxdepth 8 -path \"*/build/outputs/apk/debug/*.apk\" -print 2>/dev/null \\\n" +
        "  | while IFS= read -r APK; do\n" +
        "    [ -f \"\$APK\" ] || continue\n" +
        "    \"\$CLOSECODE_CLI\" sign-apk \"\$APK\" >/dev/null 2>&1 || \\\n" +
        "      printf 'closecode: %s keeps the Gradle key; build from the app to sign it with the CloseCode key.\\n' \"\$APK\" >&2\n" +
        "  done\n" +
        "  find \"\$PWD\" -maxdepth 8 -path \"*/build/outputs/apk/release/*.apk\" -print 2>/dev/null \\\n" +
        "  | while IFS= read -r APK; do\n" +
        "    [ -f \"\$APK\" ] || continue\n" +
        "    \"\$CLOSECODE_CLI\" sign-apk --if-unsigned \"\$APK\" >/dev/null 2>&1 || \\\n" +
        "      printf 'closecode: %s could not be signed; open CloseCode and sign it from there.\\n' \"\$APK\" >&2\n" +
        "  done\n" +
        "fi\n" +
        "exit \"\$GRADLE_CODE\"\n"

    data class Result(val changed: List<String>)

    fun prepare(projectDir: File, sdkDir: File): Result {
        if (!isGradleAndroid(projectDir)) return Result(emptyList())
        val changed = mutableListOf<String>()
        if (ensureLocalProperties(projectDir, sdkDir)) changed += "local.properties"
        if (ensureWrapper(projectDir)) changed += "gradlew"
        return Result(changed)
    }

    fun isGradleAndroid(projectDir: File): Boolean =
        File(projectDir, "app/build.gradle.kts").isFile ||
            File(projectDir, "app/build.gradle").isFile ||
            File(projectDir, "app/src/main/AndroidManifest.xml").isFile

    private fun ensureLocalProperties(projectDir: File, sdkDir: File): Boolean {
        val file = File(projectDir, "local.properties")
        val line = "sdk.dir=" + sdkDir.absolutePath.replace("\\", "\\\\")
        val existing = if (file.isFile) runCatching { file.readLines() }.getOrNull() else null
        if (existing == null) {
            file.writeText(line + "\n")
            return true
        }
        var replaced = false
        val next = existing.map { current ->
            if (current.trimStart().startsWith("sdk.dir=")) {
                replaced = true
                line
            } else {
                current
            }
        }.toMutableList()
        if (!replaced) next += line
        if (next == existing) return false
        file.writeText(next.joinToString("\n", postfix = "\n"))
        return true
    }

    private fun ensureWrapper(projectDir: File): Boolean {
        val file = File(projectDir, "gradlew")
        val current = if (file.isFile) {
            runCatching { file.readText() }.getOrNull()
        } else {
            null
        }
        val generatedDesktopWrapper = current != null &&
            (current.contains("org.gradle.wrapper.GradleWrapperMain") ||
                current.lineSequence().firstOrNull()?.contains("/usr/bin/env") == true)
        val generatedCloseCodeWrapper = current != null &&
            current.contains(
                "Gradle is not installed. Open Settings > Build and Toolchain > Full Gradle.",
            )
        if (current == null || generatedDesktopWrapper || generatedCloseCodeWrapper) {
            if (current == GRADLEW && file.canExecute()) return false
            file.writeText(GRADLEW)
            file.setExecutable(true, false)
            return true
        }
        if (!file.canExecute()) {
            file.setExecutable(true, false)
            return true
        }
        return false
    }
}
