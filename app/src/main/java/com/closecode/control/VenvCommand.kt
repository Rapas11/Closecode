package com.closecode.control

import android.content.Context
import com.closecode.tools.ShellEnvironment
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `closecode venv <directory>`: a virtual environment with a working pip.
 *
 * `python3 -m venv` is the normal way and is tried first. When the runtime's ensurepip
 * cannot install pip — the failure that a mismatched ensurepip bundle causes — the command
 * falls back to `uv venv`, which the shell pack installs and which works on its own. The
 * user is told which one made the environment.
 */
object VenvCommand {

    private const val TIMEOUT_SECONDS = 180L

    /**
     * `closecode venv <directory>`: a virtual environment with a working pip.
     *
     * [cwd] is the folder the command was run from. A relative path is resolved against it,
     * the way every other command resolves one — `closecode venv venvcmd` creates
     * `./venvcmd` in the folder the user is standing in, not `/venvcmd`.
     *
     * `python3 -m venv` is the normal way and is tried first. When the runtime's ensurepip
     * cannot install pip — the failure that a mismatched ensurepip bundle causes — the command
     * falls back to `uv venv`, which the shell pack installs and which works on its own. The
     * user is told which one made the environment, and where.
     */
    fun run(context: Context, arguments: List<String>, cwd: String? = null): JsonObject {
        val directory = arguments.firstOrNull()?.trim()
        if (directory.isNullOrEmpty()) {
            return error("closecode venv needs a directory.")
        }
        val shell = ShellToolsStore(context, ShellToolsManifest.load(context))
        if (!shell.isReady()) {
            return error("The shell tools are not installed. Run closecode setup --python.")
        }
        val python = File(shell.directory, "bin/python3")
        val uv = File(shell.directory, "bin/uv")
        if (!python.isFile) return error("python3 is missing from the shell tools.")

        val target = resolve(directory, cwd)
        val environment = ShellEnvironment.env(context, File(context.filesDir, "tmp"))

        val first = run(
            listOf(python.absolutePath, "-m", "venv", target.absolutePath),
            environment,
        )
        if (first.first == 0 && pipOf(target) != null) {
            return ok(target, "python3 -m venv", pipOf(target).orEmpty())
        }

        // The safety net: uv makes an environment without needing ensurepip.
        if (uv.isFile) {
            val second = run(
                listOf(uv.absolutePath, "venv", target.absolutePath),
                environment,
            )
            val pip = pipOf(target)
            if (second.first == 0 && pip != null) {
                return ok(target, "uv venv", pip)
            }
        }
        return error(
            "The virtual environment could not be created in ${target.absolutePath}." +
                " python3 -m venv said: " + first.second.trim().take(200) +
                " Run closecode setup --python to repair the pip wheel.",
        )
    }

    /** A relative path belongs to the folder the command was run from. */
    internal fun resolve(directory: String, cwd: String?): File =
        CommandPaths.resolve(directory, cwd)

    private fun ok(target: File, madeBy: String, pip: String): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("path", target.absolutePath)
        addProperty("createdBy", madeBy)
        addProperty("pip", pip)
        addProperty("message", "The environment was created with $madeBy.")
    }

    private fun error(message: String): JsonObject = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", message)
        addProperty("code", "venv_failed")
    }

    /** The pip inside an environment, if it has one. */
    private fun pipOf(target: File): String? {
        val pip = File(target, "bin/pip")
        if (!pip.isFile) return null
        return pip.absolutePath
    }

    private fun run(
        command: List<String>,
        environment: Map<String, String>,
    ): Pair<Int, String> = runCatching {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        (if (finished) process.exitValue() else 124) to text
    }.getOrElse { 1 to (it.message ?: it.javaClass.simpleName) }
}
