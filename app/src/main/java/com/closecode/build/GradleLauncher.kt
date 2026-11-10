package com.closecode.build

import java.io.File

/**
 * P0-1: Gradle's `bin/gradle` launcher assumes GNU xargs.
 *
 * The launcher builds its JVM options by piping `DEFAULT_JVM_OPTS` through `xargs -n1` and
 * relying on xargs to strip the embedded double quotes. Android ships toybox xargs, which does
 * not, so the literal `"-Xmx64m"` survives as the main class and every `gradle` invocation dies
 * with `ClassNotFoundException`.
 *
 * The build path no longer runs this script (see [GradlePlan.launcherJar]), but the shell still
 * puts `bin/gradle` on PATH and the acceptance test runs `gradle -v` directly. Rewriting the
 * assignment to one double-quoted string keeps `$APP_HOME` expanding while leaving the JVM
 * options unquoted by the time xargs sees them.
 */
object GradleLauncher {

    private const val PREFIX = "DEFAULT_JVM_OPTS="

    /**
     * The patched script, or null when the line is absent or already in the safe shape.
     * Pure so the transformation is unit-tested without touching a real distribution.
     *
     * Gradle 8.13's own line is:
     *
     *     DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"' "-javaagent:$APP_HOME/lib/agents/...jar"
     *
     * The result must keep `$APP_HOME` (double quotes expand it) and lose every other quote
     * (toybox xargs keeps them).
     */
    fun patchText(text: String): String? {
        var changed = false
        val patched = text.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            if (!trimmed.startsWith(PREFIX)) return@joinToString line
            val indent = line.substring(0, line.length - trimmed.length)
            var value = trimmed.substring(PREFIX.length).trim()
            if (!value.contains('-')) return@joinToString line
            if (!value.contains('"') && !value.contains('\'')) return@joinToString line
            // A `$` inside a single-quoted part must not start expanding; leave that line be.
            if (hasSingleQuotedVariable(value)) return@joinToString line
            // `\"` is an escaped quote: drop the escape first, then every quote. What is left
            // is a plain option list that xargs can split without keeping a quote.
            val flattened = value
                .replace("\\\"", "\"")
                .replace("\"", "")
                .replace("'", "")
            if (flattened.isBlank()) return@joinToString line
            val next = "$indent$PREFIX\"$flattened\""
            if (next != line) changed = true
            next
        }
        return if (changed) patched else null
    }

    /** True when a `$` sits inside a single-quoted section, where it does not expand. */
    private fun hasSingleQuotedVariable(value: String): Boolean {
        var inSingle = false
        for (character in value) {
            when (character) {
                '\'' -> inSingle = !inSingle
                '$' -> if (inSingle) return true
            }
        }
        return false
    }

    /** Patches the launcher in place; false when it is missing or already safe. */
    fun patch(launcher: File): Boolean {
        if (!launcher.isFile) return false
        val original = runCatching { launcher.readText() }.getOrNull() ?: return false
        val fixed = patchText(original) ?: return false
        val written = runCatching { launcher.writeText(fixed) }.isSuccess
        if (written) runCatching { launcher.setExecutable(true, false) }
        return written
    }
}
