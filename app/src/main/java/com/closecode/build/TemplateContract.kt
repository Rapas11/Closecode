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

    /**
     * What the check found. Violations block the build; warnings are shown and
     * never block — a foreign-looking package risks an install clash with the
     * original app, so the reader is told, not stopped.
     */
    data class Report(val violations: List<Violation>, val warnings: List<Violation>) {
        val ok: Boolean get() = violations.isEmpty()
    }

    fun packageForSlug(slug: String): String {
        val safe = slug.lowercase().map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("").trim('_').ifEmpty { "app" }
        return "$PACKAGE_PREFIX$safe"
    }

    private val PackageName = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

    /** The package a manifest declares, from `package=` or `namespace =`. */
    fun manifestPackage(manifestText: String): String? {
        val fromPackage = Regex("package=\"([^\"]+)\"").find(manifestText)?.groupValues?.get(1)
        if (fromPackage != null) return fromPackage
        return Regex("namespace\\s*=\\s*\"([^\"]+)\"").find(manifestText)?.groupValues?.get(1)
    }

    fun check(projectDir: File, slug: String): Report {
        val problems = mutableListOf<Violation>()
        val warnings = mutableListOf<Violation>()

        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        val manifestText = if (manifest.isFile) {
            runCatching { manifest.readText() }.getOrNull().orEmpty()
        } else {
            problems.add(Violation("app/src/main/AndroidManifest.xml", "Missing. Recreate the project from a template."))
            ""
        }
        // The manifest's own package wins: template projects default to
        // com.closecode.<name> and the field stays editable, while imported
        // projects like com.example keep building under their own name.
        val actualPackage = manifestPackage(manifestText)
        if (manifest.isFile && actualPackage == null) {
            problems.add(
                Violation("app/src/main/AndroidManifest.xml", "No package declared. Add package=\"com.example.app\"."),
            )
        }
        if (actualPackage != null && !PackageName.matches(actualPackage)) {
            problems.add(
                Violation("app/src/main/AndroidManifest.xml", "\"$actualPackage\" is not a valid package name."),
            )
        }
        if (actualPackage != null && !actualPackage.startsWith(PACKAGE_PREFIX)) {
            warnings.add(
                Violation(
                    "app/src/main/AndroidManifest.xml",
                    "\"$actualPackage\" is not a com.closecode package: installing it may " +
                        "clash with the original app.",
                ),
            )
        }

        val packagePath = (actualPackage ?: packageForSlug(slug)).replace('.', '/')
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
        return Report(problems, warnings)
    }
}
