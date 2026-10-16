package com.closecode.build

import java.io.File

/**
 * Applies the package and Android versions the user edits in Project settings to a
 * CloseCode-created project's files. [.closecode.json] is the record the build reads;
 * these rewrites keep the build files, the manifest and the source folder saying the
 * same thing, so the project check agrees with what the user saved.
 *
 * Imported and cloned projects never come through here: they keep their own package.
 */
object ProjectFiles {

    data class Result(val changed: List<String>, val problems: List<String>) {
        val ok: Boolean get() = problems.isEmpty()
    }

    /** Rewrites namespace, applicationId, the manifest and the source folder. */
    fun applyPackage(projectDir: File, oldPackage: String, newPackage: String): Result {
        val changed = mutableListOf<String>()
        val problems = mutableListOf<String>()
        if (oldPackage == newPackage || newPackage.isBlank()) return Result(changed, problems)

        val appGradle = File(projectDir, "app/build.gradle.kts")
        if (appGradle.isFile) {
            val text = runCatching { appGradle.readText() }.getOrNull()
            if (text == null) {
                problems += "Could not read app/build.gradle.kts."
            } else {
                var next = text
                next = next.replace(quoted("namespace", oldPackage), "namespace = \"$newPackage\"")
                next = next.replace(quoted("applicationId", oldPackage), "applicationId = \"$newPackage\"")
                if (next != text) {
                    write(appGradle, next, problems)
                    changed += "app/build.gradle.kts"
                } else {
                    problems += "app/build.gradle.kts does not declare $oldPackage."
                }
            }
        }

        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (manifest.isFile) {
            val text = runCatching { manifest.readText() }.getOrNull()
            if (text != null) {
                val next = if (Regex("package\\s*=\\s*\"").containsMatchIn(text)) {
                    text.replace(quoted("package", oldPackage), "package=\"$newPackage\"")
                } else {
                    Regex("<manifest(\\s)").replaceFirst(
                        text,
                        "<manifest package=\"$newPackage\"${'$'}1",
                    )
                }
                if (next != text) {
                    write(manifest, next, problems)
                    changed += "app/src/main/AndroidManifest.xml"
                }
            }
        } else {
            problems += "AndroidManifest.xml is missing."
        }

        val javaRoot = File(projectDir, "app/src/main/java")
        val oldDir = File(javaRoot, oldPackage.replace('.', '/'))
        val newDir = File(javaRoot, newPackage.replace('.', '/'))
        if (oldDir.isDirectory && oldDir.canonicalPath != newDir.canonicalPath) {
            newDir.parentFile?.mkdirs()
            val moved = oldDir.renameTo(newDir) || runCatching {
                oldDir.copyRecursively(newDir, overwrite = true)
                oldDir.deleteRecursively()
            }.isSuccess
            if (!moved) {
                problems += "Could not move the source folder to ${newPackage.replace('.', '/')}."
            } else {
                rewritePackageLines(newDir, oldPackage, newPackage, problems)
                pruneEmptyParents(oldDir.parentFile, javaRoot)
                changed += "app/src/main/java/${newPackage.replace('.', '/')}"
            }
        }

        return Result(changed, problems)
    }

    /** Rewrites compileSdk, minSdk and targetSdk wherever they are written as literals. */
    fun applySdks(
        projectDir: File,
        minSdk: Int,
        targetSdk: Int,
        compileSdk: Int,
    ): Result {
        val changed = mutableListOf<String>()
        val problems = mutableListOf<String>()
        val appGradle = File(projectDir, "app/build.gradle.kts")
        if (!appGradle.isFile) return Result(changed, listOf("app/build.gradle.kts is missing."))
        val text = runCatching { appGradle.readText() }.getOrNull()
            ?: return Result(changed, listOf("Could not read app/build.gradle.kts."))
        var next = text
        for ((key, value) in listOf(
            "compileSdk" to compileSdk,
            "minSdk" to minSdk,
            "targetSdk" to targetSdk,
        )) {
            val outcome = setIntValue(next, key, value)
            if (outcome == null) {
                problems += "app/build.gradle.kts does not declare $key."
            } else {
                next = outcome
            }
        }
        if (next != text) {
            write(appGradle, next, problems)
            changed += "app/build.gradle.kts"
        }
        return Result(changed, problems)
    }

    /** `key = 35`, `keyVersion = 35`, `keyVersion(35)` or `key(35)`; null when absent. */
    private fun setIntValue(text: String, key: String, value: Int): String? {
        val assign = Regex("(?m)^(\\s*$key(?:Version)?\\s*=\\s*)\\d+")
        val call = Regex("(?m)^(\\s*$key(?:Version)?\\s*\\(\\s*)\\d+(\\s*\\))")
        if (assign.containsMatchIn(text)) {
            return assign.replace(text) { it.groupValues[1] + value }
        }
        if (call.containsMatchIn(text)) {
            return call.replace(text) { it.groupValues[1] + value + it.groupValues[2] }
        }
        return null
    }

    /** The pattern that finds `key = "value"`, spacing tolerated, nothing guessed. */
    private fun quoted(key: String, value: String): Regex =
        Regex("$key\\s*=\\s*\"" + Regex.escape(value) + "\"")

    private fun write(file: File, text: String, problems: MutableList<String>) {
        runCatching { file.writeText(text) }
            .onFailure { problems += "Could not write ${file.name}." }
    }

    private fun rewritePackageLines(
        dir: File,
        oldPackage: String,
        newPackage: String,
        problems: MutableList<String>,
    ) {
        val pattern = Regex("(?m)^package\\s+" + Regex.escape(oldPackage) + "\\b")
        dir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
                val next = pattern.replaceFirst(text, "package $newPackage")
                if (next != text) write(file, next, problems)
            }
    }

    private fun pruneEmptyParents(start: File?, stopAt: File) {
        var current = start
        val stop = stopAt.canonicalPath
        while (current != null && current.canonicalPath != stop &&
            current.canonicalPath.startsWith(stop + File.separator)
        ) {
            if (current.listFiles()?.isEmpty() != true) return
            current.delete()
            current = current.parentFile
        }
    }
}
