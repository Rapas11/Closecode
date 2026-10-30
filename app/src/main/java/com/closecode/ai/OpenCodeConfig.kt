package com.closecode.ai

import java.io.File

/**
 * The default OpenCode configuration the app writes for the phone.
 *
 * OpenCode reads its configuration from `$XDG_CONFIG_HOME/opencode/opencode.json`, and the app
 * grounds that directory itself, so the file lives where the app can write and nothing on the
 * phone's storage is touched. The file is written once, only when it is missing: anything the
 * user or the AI settings page wrote before stays untouched.
 *
 * Web search is not in the file. The server is started with the hosted providers' environment
 * flag instead, which turns the `websearch`/`webfetch` tools on without an API key.
 */
object OpenCodeConfig {

    /** Turns OpenCode's hosted `websearch`/`webfetch` tools on for the server. */
    const val WEB_SEARCH_FLAG = "OPENCODE_ENABLE_EXA"

    /** The second hosted provider, kept as the app's own fallback for web search. */
    const val WEB_SEARCH_FALLBACK_FLAG = "OPENCODE_ENABLE_PARALLEL"

    fun directory(configHome: File): File = File(configHome, "opencode")

    fun file(configHome: File): File = File(directory(configHome), "opencode.json")

    /** The tools the app turns wide open: on this phone the user only talks to the agent. */
    val OPEN_TOOLS = listOf("edit", "bash", "webfetch")

    /** The default file as OpenCode accepts it: the schema, and the always-on permissions. */
    fun defaultJson(): String = """
        {
          "${'$'}schema": "https://opencode.ai/config.json",
          "permission": {
            "edit": "allow",
            "bash": "allow",
            "webfetch": "allow"
          }
        }
    """.trimIndent() + "\n"

    /** Writes the default file when it is missing. Returns the file when it exists afterwards. */
    fun installOnce(configHome: File): File? {
        val file = file(configHome)
        if (file.isFile) return file
        if (!ensureDirectory(directory(configHome))) return null
        return runCatching {
            file.writeText(defaultJson())
            file
        }.getOrNull()
    }

    /**
     * Config files written before the hands-free build have no permission block at all, and
     * OpenCode would then ask the user to confirm everything — on a phone where the user only
     * talks, those asks are dead ends. This fills `edit`, `bash` and `webfetch` in once and
     * stamps it done, keeping every other key; after the stamp, the AI settings page owns the
     * permission block and this never touches it again.
     */
    fun ensureOpenToTheAgent(configHome: File): Boolean {
        val file = file(configHome)
        if (!file.isFile) return true
        if (stamp(configHome).isFile) return true
        val json = OpenCodeAiConfig.read(file) ?: return false
        if (json.has("permission")) {
            return runCatching { stamp(configHome).createNewFile() }.isSuccess
        }
        val opened = OPEN_TOOLS.fold(json) { acc, tool -> OpenCodeAiConfig.withPermission(acc, tool, OpenCodeAiConfig.ALLOW) }
        val saved = OpenCodeAiConfig.save(file, opened)
        return if (saved) {
            runCatching { stamp(configHome).createNewFile() }.isSuccess
        } else {
            false
        }
    }

    /** The one-time marker saying the permission upgrade ran, and owned by the user since. */
    fun stamp(configHome: File): File = File(directory(configHome), ".closecode-permissions")

    private fun ensureDirectory(directory: File): Boolean =
        directory.isDirectory || directory.mkdirs()
}
