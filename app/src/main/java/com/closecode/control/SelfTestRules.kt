package com.closecode.control

/**
 * The rules the setup self-test applies to a tool's output (P1-5).
 *
 * The old check only asked "did it print something", so a launcher that printed
 * `Could not find or load main class "-Xmx64m"` was reported as ok:true. A tool passes only
 * when it exits 0 and its output names its own version.
 */
internal object SelfTestRules {

    /** True when the output proves this tool is the one we asked for. */
    fun versionLooksRight(name: String, output: String): Boolean = when (name) {
        "java" -> Regex("(openjdk|java) version \\\"?\\d+").containsMatchIn(output)
        "gradle" -> Regex("Gradle \\d+\\.\\d+").containsMatchIn(output)
        "aapt2" -> output.contains("Android Asset Packaging", ignoreCase = true)
        "platform-tools" -> output.contains("Android Debug Bridge", ignoreCase = true)
        "python" -> Regex("Python \\d+\\.\\d+").containsMatchIn(output)
        "pip" -> Regex("pip \\d+").containsMatchIn(output)
        "node" -> Regex("v\\d+\\.\\d+").containsMatchIn(output)
        "npm" -> Regex("\\d+\\.\\d+").containsMatchIn(output)
        "uv" -> Regex("uv \\d+").containsMatchIn(output)
        "ffmpeg" -> Regex("ffmpeg version", RegexOption.IGNORE_CASE).containsMatchIn(output)
        "sqlite3" -> Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(output)
        "magick" -> Regex("ImageMagick", RegexOption.IGNORE_CASE).containsMatchIn(output)
        else -> output.isNotBlank()
    }

    /** The text of an error line means the tool did not run, whatever it printed. */
    fun looksBroken(output: String): Boolean =
        output.contains("Error:") ||
            output.contains("CANNOT LINK", ignoreCase = true) ||
            output.contains("not found", ignoreCase = true) ||
            output.contains("Could not find or load main class", ignoreCase = true)
}
