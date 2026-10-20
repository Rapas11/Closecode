package com.closecode.build

import java.io.File

/**
 * Contract between the IDE and the preview renderer. The renderer is a generated harness
 * compiled with the user's own classes and resources into `build/previews/<key>/preview.apk`,
 * then loaded in CloseCode's `:preview` process, where it draws each `@Preview` on the real
 * runtime with drawToBitmap and writes PNGs plus this index.
 */
object PreviewContract {
    const val RUNNER_SUFFIX = ".previewrunner"
    const val ACTIVITY = "com.closecode.previewrunner.PreviewRunnerActivity"
    const val EXTRA_FUNCTIONS = "functions"
    const val EXTRA_THEME_DARK = "dark"
    const val EXTRA_WIDTH_DP = "widthDp"
    const val INDEX_NAME = "previews.json"

    data class Entry(
        val function: String,
        val png: String,
        val widthPx: Int,
        val heightPx: Int,
        val error: String? = null,
        val id: String = "",
    )

    /**
     * Parses the runner's index file. Pure, so card states stay tested while the
     * runner itself is still being built.
     */
    fun readIndex(root: File): List<Entry> {
        val file = File(root, INDEX_NAME)
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return text.split("}").mapNotNull { chunk ->
            if ("\"function\"" !in chunk) return@mapNotNull null
            fun field(name: String): String? =
                Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(chunk)?.groupValues?.get(1)
            fun number(name: String): Int =
                Regex("\"$name\"\\s*:\\s*(\\d+)").find(chunk)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val function = field("function") ?: return@mapNotNull null
            Entry(
                function = function,
                png = field("png").orEmpty(),
                widthPx = number("widthPx"),
                heightPx = number("heightPx"),
                error = field("error"),
                id = field("id").orEmpty(),
            )
        }
    }

    fun runnerPackage(appPackage: String): String = appPackage + RUNNER_SUFFIX
}
