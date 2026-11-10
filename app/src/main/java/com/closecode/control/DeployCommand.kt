package com.closecode.control

import android.content.Context
import com.closecode.build.ProjectTypeDetector
import com.closecode.device.control.AuditLog
import com.closecode.device.control.ConsentStore
import com.closecode.tools.ShellEnvironment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Deploys a built project over SSH. Connection details come from arguments or the encrypted
 * secret vault; passwords are not supported, so a key secret is written to a private temp file
 * only for the length of the rsync/scp call.
 */
internal object DeployCommand {

    fun run(context: Context, projectDir: File, arguments: List<String>): JsonObject {
        if (!ConsentStore(context).isGranted("network")) {
            return CommandEnvelope.failed(
                "The network capability is not granted.",
                code = "needs_consent",
                remedy = "Run closecode consent grant network",
            )
        }
        val action = arguments.firstOrNull()?.lowercase()
        if (action == "status" || action == "presets") {
            return status(context, arguments)
        }
        val secrets = SecretStore(context)
        val preset = option(arguments, "--preset") ?: "auto"
        val host = option(arguments, "--host") ?: secrets.get("deploy.host")
        val user = option(arguments, "--user") ?: secrets.get("deploy.user") ?: "root"
        val path = option(arguments, "--path") ?: secrets.get("deploy.path")
        val port = option(arguments, "--port")?.toIntOrNull() ?: secrets.get("deploy.port")?.toIntOrNull() ?: 22
        val dryRun = arguments.contains("--dry-run")
        val approved = arguments.contains("--yes") || arguments.contains("-y")
        if (host.isNullOrBlank() || path.isNullOrBlank()) {
            return CommandEnvelope.failed(
                "A deploy host and remote path are required.",
                code = "missing_target",
                remedy = "Run closecode secrets set deploy.host HOST, deploy.path /var/www/app, deploy.user USER, then retry.",
            )
        }
        if (!safeHost(host)) return CommandEnvelope.failed("Invalid deploy host.", code = "usage")
        if (!safeUser(user)) return CommandEnvelope.failed("Invalid deploy user.", code = "usage")
        if (!safePath(path)) return CommandEnvelope.failed("Invalid deploy path.", code = "usage")
        if (!dryRun && !approved) {
            return CommandEnvelope.failed(
                "Deploying to $user@$host:$path needs confirmation.",
                code = "needs_confirmation",
                remedy = "Re-run with --yes, or add --dry-run to see what would change.",
            ).apply {
                addProperty("host", host)
                addProperty("user", user)
                addProperty("path", path)
            }
        }
        val source = resolveSource(context, projectDir, preset, option(arguments, "--source"))
            ?: return CommandEnvelope.failed(
                "No deployable output found for preset '$preset'.",
                code = "not_found",
                remedy = "Build the project first, or pass --source DIR.",
            )
        val keyName = option(arguments, "--key") ?: "deploy.key"
        val keyValue = secrets.get(keyName)
            ?: option(arguments, "--key-file")?.let { runCatching { File(it).readText() }.getOrNull() }
        val keyFile = keyValue?.let { writeKey(context, it) }
        try {
            val tools = tools(context)
            if (source.isFile) {
                if (!tools.scp) {
                    return toolRemedy("scp")
                }
                return transferFile(context, projectDir, source, host, user, path, port, keyFile, dryRun, tools)
            }
            if (!tools.rsync) {
                return toolRemedy("rsync")
            }
            return transferTree(context, projectDir, source, host, user, path, port, keyFile, dryRun, tools)
        } finally {
            keyFile?.delete()
        }
    }

    private fun status(context: Context, arguments: List<String>): JsonObject {
        val secrets = SecretStore(context)
        val tools = tools(context)
        return CommandEnvelope.of(ok = true).apply {
            add("presets", JsonArray().apply {
                listOf("auto", "static", "node", "python", "android", "directory").forEach { add(it) }
            })
            add("config", JsonObject().apply {
                addProperty("host", secrets.get("deploy.host") ?: "")
                addProperty("user", secrets.get("deploy.user") ?: "")
                addProperty("path", secrets.get("deploy.path") ?: "")
                addProperty("port", secrets.get("deploy.port")?.toIntOrNull() ?: 22)
                addProperty("key", secrets.get("deploy.key") != null)
            })
            add("tools", JsonObject().apply {
                addProperty("ssh", tools.ssh)
                addProperty("scp", tools.scp)
                addProperty("rsync", tools.rsync)
                addProperty("tar", tools.tar)
            })
            addProperty("vaultDurable", secrets.durable())
            if (!tools.rsync) {
                add("warnings", JsonArray().apply {
                    add("The shell pack is missing rsync; run closecode setup --shell to update it.")
                })
            }
        }
    }

    private data class Tools(val ssh: Boolean, val scp: Boolean, val rsync: Boolean, val tar: Boolean)

    private fun tools(context: Context): Tools {
        val paths = ShellEnvironment.paths(context)
        fun has(name: String): Boolean = File(paths.shellTools, "bin/$name").exists()
        return Tools(has("ssh"), has("scp"), has("rsync"), has("tar"))
    }

    private fun toolRemedy(name: String): JsonObject = CommandEnvelope.failed(
        "The shell pack is missing $name.",
        code = "tool_missing",
        remedy = "Run closecode setup --shell to install the updated shell tools.",
    )

    private fun resolveSource(
        context: Context,
        projectDir: File,
        preset: String,
        override: String?,
    ): File? {
        if (override != null) {
            return (if (File(override).isAbsolute) File(override) else File(projectDir, override))
                .takeIf { it.exists() }
        }
        val chosen = if (preset == "auto") detectPreset(context, projectDir) else preset
        val candidates = when (chosen) {
            "static" -> listOf("dist", "build/web", "public", ".")
            "node" -> listOf("dist", "build", ".next", "out", ".")
            "python" -> listOf("dist", "build", ".")
            "android" -> listOf(
                "app/build/outputs/apk/release/app-release.apk",
                "app/build/outputs/apk/debug/app-debug.apk",
            )
            "directory", "dir" -> listOf(".")
            else -> listOf("dist", "build", ".")
        }
        return candidates.asSequence()
            .map { File(projectDir, it) }
            .firstOrNull { it.exists() }
    }

    private fun detectPreset(context: Context, projectDir: File): String =
        when (ProjectTypeDetector.detect(projectDir).primary) {
            ProjectTypeDetector.GRADLE_ANDROID -> "android"
            ProjectTypeDetector.NPM -> "node"
            ProjectTypeDetector.PYTHON -> "python"
            ProjectTypeDetector.STATIC_HTML -> "static"
            else -> "directory"
        }

    private fun writeKey(context: Context, value: String): File {
        val file = File(context.cacheDir, "deploy-key-${System.nanoTime()}")
        file.writeText(value.trimEnd() + "\n")
        runCatching { file.setReadable(false, false); file.setReadable(true, true) }
        return file
    }

    private fun transferTree(
        context: Context,
        projectDir: File,
        source: File,
        host: String,
        user: String,
        path: String,
        port: Int,
        key: File?,
        dryRun: Boolean,
        tools: Tools,
    ): JsonObject {
        val command = mutableListOf("rsync", "-az", "--delete")
        listOf(".git", "build", ".gradle", "node_modules", ".closecode.json", ".env").forEach {
            command += "--exclude=$it"
        }
        if (dryRun) command += "--dry-run"
        command += "-e"
        command += sshCommand(port, key, tools.ssh)
        val target = if (path.endsWith("/")) path else "$path/"
        command += "${source.absolutePath}/"
        command += "$user@$host:$target"
        val result = execute(context, projectDir, command, timeout = 600_000)
        AuditLog(context).record(
            "egress deploy",
            host,
            listOf(user, path, source.absolutePath, if (dryRun) "dry-run" else "live"),
            result.code == 0,
            null,
        )
        return envelope(result, dryRun, source, host, path)
    }

    private fun transferFile(
        context: Context,
        projectDir: File,
        file: File,
        host: String,
        user: String,
        path: String,
        port: Int,
        key: File?,
        dryRun: Boolean,
        tools: Tools,
    ): JsonObject {
        if (dryRun) {
            return CommandEnvelope.of(ok = true).apply {
                addProperty("dryRun", true)
                addProperty("source", file.absolutePath)
                addProperty("target", "$user@$host:$path")
            }
        }
        val command = mutableListOf("scp", "-P", port.toString())
        key?.let { command += listOf("-i", it.absolutePath) }
        command += listOf("-o", "StrictHostKeyChecking=accept-new")
        command += file.absolutePath
        val destination = if (path.endsWith("/") || File(path).extension.isEmpty()) "$path/${file.name}" else path
        command += "$user@$host:$destination"
        val result = execute(context, projectDir, command, timeout = 300_000)
        AuditLog(context).record(
            "egress deploy",
            host,
            listOf(user, path, file.absolutePath),
            result.code == 0,
            null,
        )
        return envelope(result, false, file, host, path)
    }

    private fun sshCommand(port: Int, key: File?, ssh: Boolean): String {
        val binary = if (ssh) "ssh" else "ssh"
        return buildString {
            append(binary)
            append(" -p ").append(port)
            key?.let { append(" -i ").append(it.absolutePath) }
            append(" -o StrictHostKeyChecking=accept-new")
        }
    }

    private data class Execution(val code: Int, val output: String)

    private fun execute(context: Context, projectDir: File, command: List<String>, timeout: Long): Execution {
        val builder = ProcessBuilder(command)
            .directory(projectDir)
            .redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(ShellEnvironment.env(context, projectDir))
        val process = runCatching { builder.start() }.getOrElse {
            return Execution(-1, it.message ?: "Could not start ${command.first()}.")
        }
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().use { it.readText() } }
        }.apply { isDaemon = true; start() }
        val finished = runCatching { process.waitFor(timeout, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!finished) process.destroyForcibly()
        reader.join(3_000)
        val code = runCatching { process.exitValue() }.getOrDefault(124)
        return Execution(code, "")
    }

    private fun envelope(result: Execution, dryRun: Boolean, source: File, host: String, path: String): JsonObject {
        if (result.code != 0) {
            return CommandEnvelope.failed(
                "Deploy failed (exit ${result.code}).",
                code = "deploy_failed",
                remedy = "Check the host, key, remote path, and that the remote directory exists.",
            ).apply {
                addProperty("exitCode", result.code)
                addProperty("source", source.absolutePath)
                addProperty("host", host)
                addProperty("path", path)
            }
        }
        return CommandEnvelope.of(ok = true).apply {
            addProperty("dryRun", dryRun)
            addProperty("source", source.absolutePath)
            addProperty("host", host)
            addProperty("path", path)
            addProperty("exitCode", 0)
        }
    }

    private fun safeHost(value: String): Boolean =
        value.length <= 253 && value.matches(Regex("[A-Za-z0-9.:\\[\\]-]+"))

    private fun safeUser(value: String): Boolean =
        value.matches(Regex("[A-Za-z0-9._-]{1,64}"))

    private fun safePath(value: String): Boolean =
        value.startsWith("/") && value.length <= 1024 &&
            value.matches(Regex("/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*")) &&
            !value.contains("..")

    private fun option(arguments: List<String>, name: String): String? {
        val index = arguments.indexOf(name)
        if (index >= 0) return arguments.getOrNull(index + 1)
        return arguments.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
    }
}
