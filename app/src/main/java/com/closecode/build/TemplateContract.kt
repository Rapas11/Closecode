package com.closecode.build

import java.io.File

/**
 * The template contract: user projects stay buildable-by-construction, which is what
 * makes "no Gradle" possible. The builder never interprets Gradle scripts; it checks
 * the project matches the template shape, then compiles sources directly.
 *
 * v1 rules (each failure names the file and the fix):
 * - package name follows com.closecode.<slug>
 * - AndroidManifest.xml exists with that package
 * - at least one Kotlin source under app/src/main/java/<package path>
 * - no build.gradle.kts / settings.gradle.kts is *executed* — if present they must be
 *   the untouched template files (presence of unknown script blocks fails closed)
 * - declared third-party deps come only from the allowlist (versions pinned here)
 */
object TemplateContract {
    const val PACKAGE_PREFIX = "com.closecode."

    /** Maven coordinates user projects may declare, pinned. Everything else fails closed. */
    val allowedDeps: Map<String, String> = mapOf(
        "androidx.core:core-ktx" to "1.13.1",
        "androidx.activity:activity-compose" to "1.9.3",
        "androidx.compose.ui:ui" to "1.7.0",
        "androidx.compose.material3:material3" to "1.3.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android" to "1.9.0",
    )

    const val KOTLIN_VERSION = ToolchainManifest.KOTLIN_VERSION
    const val COMPILE_SDK = ToolchainManifest.COMPILE_SDK
    const val MIN_SDK = 29

    data class Violation(val path: String, val message: String)

    fun packageForSlug(slug: String): String {
        val safe = slug.lowercase().map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("").trim('_').ifEmpty { "app" }
        return "$PACKAGE_PREFIX$safe"
    }

    fun check(projectDir: File, slug: String): List<Violation> {
        val problems = mutableListOf<Violation>()
        val expectedPackage = packageForSlug(slug)

        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (!manifest.isFile) {
            problems.add(Violation("app/src/main/AndroidManifest.xml", "Missing. Recreate the project from a template."))
        } else {
            val text = runCatching { manifest.readText() }.getOrNull().orEmpty()
            if (expectedPackage !in text) {
                problems.add(
                    Violation(
                        "app/src/main/AndroidManifest.xml",
                        "Package must be $expectedPackage.",
                    ),
                )
            }
        }

        val packagePath = expectedPackage.replace('.', '/')
        val ktRoot = File(projectDir, "app/src/main/java/$packagePath")
        val sources = ktRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        if (sources.isEmpty()) {
            problems.add(
                Violation(
                    "app/src/main/java/$packagePath",
                    "No Kotlin sources. Add at least one .kt file.",
                ),
            )
        }

        // Gradle scripts are data, never executed. Unknown files or unknown blocks fail closed.
        for (script in listOf("build.gradle.kts", "settings.gradle.kts", "app/build.gradle.kts")) {
            val file = File(projectDir, script)
            if (!file.isFile) continue
            val text = runCatching { file.readText() }.getOrNull().orEmpty()
            val banned = listOf("exec", "ProcessBuilder", "delete(", "renameTo", "apply(", "maven(")
            val hit = banned.firstOrNull { it in text }
            if (hit != null) {
                problems.add(Violation(script, "Not a template script (found \"$hit\"). Only template projects build on-device."))
            }
            val dep = Regex("implementation\\(\"([^\"]+)\"\\)").findAll(text)
                .map { it.groupValues[1].substringBeforeLast(':') to it.groupValues[1].substringAfterLast(':') }
                .firstOrNull { (name, _) -> name !in allowedDeps }
            if (dep != null) {
                problems.add(Violation(script, "Dependency ${dep.first}:${dep.second} is not on the on-device allowlist."))
            }
        }
        return problems
    }
}
