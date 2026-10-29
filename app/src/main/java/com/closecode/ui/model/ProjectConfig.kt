package com.closecode.ui.model

import androidx.compose.runtime.Immutable
import com.closecode.build.ProjectPins

private const val FieldSep = "\u0001"

@Immutable
data class ProjectConfig(
    val packageName: String = "com.closecode.myproject",
    val language: String = "Kotlin",
    val gradleVersion: String = "8.13",
    val minSdk: String = "29",
    val compileSdk: String = "35",
    val targetSdk: String = "35",
    val buildType: String = "Debug",
) {
    fun encode(): String = listOf(
        packageName,
        language,
        gradleVersion,
        minSdk,
        compileSdk,
        targetSdk,
        buildType,
    ).joinToString(FieldSep)

    companion object {
        fun decode(raw: String): ProjectConfig = runCatching {
            val parts = raw.split(FieldSep)
            ProjectConfig(
                packageName = parts[0],
                language = parts[1],
                gradleVersion = parts[2],
                minSdk = parts[3],
                compileSdk = parts[4],
                targetSdk = parts[5],
                buildType = parts[6],
            )
        }.getOrDefault(ProjectConfig())
    }
}

val LanguageOptions = listOf("Kotlin", "Java")

val GradleOptions = listOf("8.13", "8.12", "8.9", "8.5")

val MinSdkOptions = listOf("21", "23", "24", "26", "29", "31", "33", "34", "35")

val SdkOptions = listOf("33", "34", "35", "36")

val BuildTypeOptions = listOf("Debug", "Release")

/**
 * The Gradle version is export metadata, not the on-device compiler, but a desktop build
 * still needs the selected AGP to understand the selected compileSdk. Null when compatible.
 */
fun gradleCompatibilityProblem(config: ProjectConfig): String? {
    val maxApi = ProjectPins.maxApiForGradle(config.gradleVersion)
    val compileApi = config.compileSdk.toIntOrNull() ?: return null
    if (compileApi <= maxApi) return null
    return "Gradle ${config.gradleVersion} maps to Android Gradle Plugin " +
        "${ProjectPins.agpForGradle(config.gradleVersion)}, which supports compileSdk $maxApi. " +
        "Choose a newer Gradle version for SDK $compileApi."
}

fun defaultPackageName(projectName: String): String {
    val slug = projectName
        .lowercase()
        .filter { it.isLetterOrDigit() }
        .ifEmpty { "myproject" }
    return "com.closecode.$slug"
}