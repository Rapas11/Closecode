package com.closecode.build

import java.io.File

/**
 * What kind of project is in a folder, from the files alone. Detection never runs a build and
 * never downloads anything; an explicit `.closecode.json` `type` always wins.
 */
data class ProjectDetection(
    val types: List<String>,
    val primary: String,
    val mixed: Boolean,
    val signals: List<String>,
    val overridden: Boolean,
) {
    fun json(): com.google.gson.JsonObject = com.google.gson.JsonObject().apply {
        addProperty("primary", primary)
        addProperty("mixed", mixed)
        addProperty("overridden", overridden)
        add("types", com.google.gson.JsonArray().apply { types.forEach { add(it) } })
        add("signals", com.google.gson.JsonArray().apply { signals.forEach { add(it) } })
    }

    companion object {
        const val GRADLE_ANDROID = "gradle-android"
        const val NPM = "npm"
        const val STATIC_HTML = "static-html"
        const val PYTHON = "python"
        const val CMAKE = "cmake"
        const val MIXED = "mixed"
        const val UNKNOWN = "unknown"

        fun detect(directory: File, settings: ProjectSettings = ProjectSettings.read(directory)): ProjectDetection {
            val types = linkedSetOf<String>()
            val signals = mutableListOf<String>()
            fun signal(type: String, text: String) {
                types += type
                signals += text
            }

            val manifest = File(directory, "app/src/main/AndroidManifest.xml")
            val androidManifest = File(directory, "src/main/AndroidManifest.xml")
            val gradleFiles = listOf(
                "build.gradle.kts", "build.gradle", "app/build.gradle.kts", "app/build.gradle",
            ).map { File(directory, it) }.filter { it.isFile }
            val androidGradle = gradleFiles.any { file ->
                runCatching { file.readText() }.getOrDefault("").let {
                    it.contains("com.android.application") ||
                        it.contains("com.android.library") ||
                        it.contains("android {")
                }
            }
            if (manifest.isFile || androidManifest.isFile || androidGradle) {
                signal(
                    GRADLE_ANDROID,
                    when {
                        manifest.isFile -> "app/src/main/AndroidManifest.xml"
                        androidManifest.isFile -> "src/main/AndroidManifest.xml"
                        else -> "Gradle Android plugin"
                    },
                )
            }

            if (File(directory, "package.json").isFile) {
                signal(NPM, "package.json")
            }
            if (hasIndexHtml(directory)) {
                signal(STATIC_HTML, "index.html")
            }
            if (listOf("pyproject.toml", "requirements.txt", "setup.py", "Pipfile")
                    .any { File(directory, it).isFile } ||
                directory.listFiles()?.any { it.isFile && it.extension == "py" } == true
            ) {
                signal(PYTHON, "Python project files")
            }
            if (File(directory, "CMakeLists.txt").isFile) {
                signal(CMAKE, "CMakeLists.txt")
            }

            val override = settings.type
            if (override != null && override != MIXED) {
                if (override !in types) types += override
                return ProjectDetection(
                    types = types.toList(),
                    primary = override,
                    mixed = false,
                    signals = signals,
                    overridden = true,
                )
            }
            if (override == MIXED) {
                return ProjectDetection(
                    types = types.toList(),
                    primary = MIXED,
                    mixed = types.size > 1,
                    signals = signals,
                    overridden = true,
                )
            }
            val ordered = listOf(GRADLE_ANDROID, NPM, PYTHON, CMAKE, STATIC_HTML)
                .filter { it in types }
            val primary = ordered.firstOrNull() ?: UNKNOWN
            return ProjectDetection(
                types = ordered,
                primary = primary,
                mixed = ordered.size > 1,
                signals = signals,
                overridden = false,
            )
        }

        private fun hasIndexHtml(directory: File): Boolean = listOf(
            "index.html",
            "public/index.html",
            "dist/index.html",
            "www/index.html",
        ).any { File(directory, it).isFile }
    }
}

/** Public entry point used by the CLI adapters. */
object ProjectTypeDetector {
    const val GRADLE_ANDROID = ProjectDetection.GRADLE_ANDROID
    const val NPM = ProjectDetection.NPM
    const val STATIC_HTML = ProjectDetection.STATIC_HTML
    const val PYTHON = ProjectDetection.PYTHON
    const val CMAKE = ProjectDetection.CMAKE
    const val MIXED = ProjectDetection.MIXED
    const val UNKNOWN = ProjectDetection.UNKNOWN

    fun detect(directory: File, settings: ProjectSettings = ProjectSettings.read(directory)): ProjectDetection =
        ProjectDetection.detect(directory, settings)
}
