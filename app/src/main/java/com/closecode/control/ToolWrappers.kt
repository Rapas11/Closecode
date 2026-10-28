package com.closecode.control

import android.content.Context
import com.closecode.debug.DebugLog
import java.io.File

/**
 * Executable aliases in the terminal PATH. They only forward to the loopback bridge:
 * the bridge owns the toolchain, the token and the process, so a wrapper cannot drift
 * from the app or be used without the app running.
 */
internal object ToolWrappers {

    val tools = ToolBridge.names

    fun install(context: Context) {
        val bin = File(context.filesDir, "opencode/bin").apply { mkdirs() }
        val base = File(context.filesDir, "opencode").absolutePath
        tools.forEach { name ->
            write(bin, name, forwardScript(base, "tool $name"))
        }
        write(bin, "cc", forwardScript(base, ""))
    }

    fun forwardScript(base: String, subcommand: String): String {
        val escaped = base.replace("'", "'\\''")
        val suffix = if (subcommand.isBlank()) "" else " $subcommand"
        return """
            #!/system/bin/sh
            exec '$escaped/bin/closecode'$suffix "${'$'}@"
        """.trimIndent() + "\n"
    }

    private fun write(bin: File, name: String, content: String) {
        val file = File(bin, name)
        if (!file.isFile || file.readText() != content) file.writeText(content)
        if (!file.setExecutable(true, false)) {
            DebugLog.crumb("CONTROL", "could not make $name executable")
        }
    }
}
