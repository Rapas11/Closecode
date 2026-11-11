package com.closecode.build

import java.io.File

/**
 * Values Gradle must see even when the build starts from a project's own `./gradlew`.
 *
 * `closecode build` passes these on its command line, but a wrapper invocation has no such
 * flags. Gradle always reads `$GRADLE_USER_HOME/gradle.properties`, so the ARM64 aapt2 and
 * the installed JDK are written there too. Other lines in the file belong to the user and
 * are preserved.
 */
object GradleUserProperties {

    const val AAPT2_KEY = "android.aapt2FromMavenOverride"
    const val JAVA_HOME_KEY = "org.gradle.java.home"

    fun ensure(gradleHome: File, aapt2: File?, javaHome: File?): Boolean {
        val values = buildMap {
            if (aapt2?.isFile == true) put(AAPT2_KEY, aapt2.absolutePath)
            if (javaHome != null && File(javaHome, "bin/java").isFile) {
                put(JAVA_HOME_KEY, javaHome.absolutePath)
            }
        }
        if (values.isEmpty()) return false

        val file = File(gradleHome, "gradle.properties")
        val existing = if (file.isFile) {
            runCatching { file.readLines() }.getOrNull()
        } else {
            null
        }
        val lines = existing ?: emptyList()
        val seen = mutableSetOf<String>()
        val next = mutableListOf<String>()
        lines.forEach { line ->
            val key = line.substringBefore('=').trim()
            val replacement = values[key]
            if (replacement != null) {
                if (seen.add(key)) next += "$key=$replacement"
            } else {
                next += line
            }
        }
        values.forEach { (key, value) ->
            if (seen.add(key)) next += "$key=$value"
        }
        if (next == lines) return false
        gradleHome.mkdirs()
        file.writeText(next.joinToString("\n", postfix = "\n"))
        return true
    }
}
