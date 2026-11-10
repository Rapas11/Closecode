package com.closecode.ui.project

import java.io.File

object GeneratedPaths {

    val HIDDEN_DIRS = setOf(
        ".git",
        ".opencode",
        "node_modules",
        "build",
        ".gradle",
        ".idea",
        "captures",
        ".cxx",
        ".externalNativeBuild",
    )

    val HIDDEN_FILES = setOf(
        ".closecode.json",
        "local.properties",
        ".env",
        "google-services.json",
    )

    val HIDDEN_FILE_PATTERNS = listOf(
        "*.iml",
        "*.apk",
        "*.aab",
        "*.jks",
        "*.keystore",
        "*.pem",
    )

    fun isAlwaysHiddenName(name: String): Boolean = name == ".git" || name == ".closecode.json"

    fun isHiddenName(name: String, isDirectory: Boolean = false): Boolean {
        val leaf = File(name).name
        if (isAlwaysHiddenName(leaf)) return true
        if (isDirectory) return leaf in HIDDEN_DIRS
        if (leaf in HIDDEN_FILES) return true
        if (leaf in HIDDEN_DIRS) return true
        return HIDDEN_FILE_PATTERNS.any { glob(it, leaf) } || ProjectSafety.isSecretName(leaf)
    }

    fun isProtectedRelative(relative: String): Boolean {
        val clean = relative.replace('\\', '/').trim('/')
        if (clean.isBlank()) return false
        val parts = clean.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return false
        if (parts.any { it in HIDDEN_DIRS }) return true
        return isHiddenName(parts.last(), isDirectory = false)
    }

    fun isExplorerHidden(relative: String, isDirectory: Boolean, showGenerated: Boolean): Boolean {
        val leaf = relative.substringAfterLast('/')
        if (isAlwaysHiddenName(leaf)) return true
        if (relative.replace('\\', '/').split('/').any { it == ".git" }) return true
        if (showGenerated) return false
        return isHiddenName(leaf, isDirectory) || isProtectedRelative(relative)
    }

    private fun glob(pattern: String, name: String): Boolean {
        val regex = buildString {
            append('^')
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '.' -> append("\\.")
                    else -> append(ch)
                }
            }
            append('$')
        }
        return Regex(regex).matches(name)
    }
}
