package com.closecode.tools

import android.content.Context
import java.io.File

/**
 * The one shell environment every CloseCode subprocess starts from. The Termux packs are
 * installed outside `files/`, so a shell that only inherits Android's environment cannot
 * find `node`, `python3`, the JDK or Gradle. Everything here is conditional: an uninstalled
 * tool is left out instead of pointing at a path that does not exist.
 */
object ShellEnvironment {

    data class Paths(
        val dataDir: File,
        val filesDir: File,
        val shellTools: File,
        val mediaTools: File,
        val goTools: File,
        val rustTools: File,
        val dartTools: File,
        val phpTools: File,
        val rubyTools: File,
        val denoTools: File,
        val bunTools: File,
        val gradlePrefix: File,
        val sdk: File,
        val gradleRoot: File,
        val jdkHome: File? = null,
    )

    fun paths(context: Context): Paths {
        val data = context.dataDir
        val files = context.filesDir
        return Paths(
            dataDir = data,
            filesDir = files,
            shellTools = ShellToolsRepo.workingPrefix(data),
            mediaTools = ShellToolsRepo.workingPrefix(data, "m"),
            goTools = ShellToolsRepo.workingPrefix(data, "go"),
            rustTools = ShellToolsRepo.workingPrefix(data, "rs"),
            dartTools = ShellToolsRepo.workingPrefix(data, "dart"),
            phpTools = ShellToolsRepo.workingPrefix(data, "php"),
            rubyTools = ShellToolsRepo.workingPrefix(data, "rb"),
            denoTools = ShellToolsRepo.workingPrefix(data, "deno"),
            bunTools = ShellToolsRepo.workingPrefix(data, "bun"),
            gradlePrefix = File(data, "file/g"),
            sdk = File(files, "sdk"),
            gradleRoot = File(files, "gradle"),
            jdkHome = JdkStore.selected(context)?.home,
        )
    }

    fun javaHome(paths: Paths): File? = listOfNotNull(
        paths.jdkHome,
        File(paths.gradlePrefix, "lib/jvm/java-17-openjdk"),
        File(paths.shellTools, "lib/jvm/java-17-openjdk"),
        File(File(paths.dataDir, "file/j"), "lib/jvm/java-21-openjdk"),
    ).firstOrNull { File(it, "bin/java").isFile }

    fun gradleHome(paths: Paths): File? =
        File(paths.gradleRoot, "home").takeIf { it.isDirectory }

    fun gradleBin(paths: Paths): File? =
        paths.gradleRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("gradle-") }
            ?.sortedByDescending { it.name }
            ?.firstOrNull { File(it, "bin/gradle").isFile }
            ?.let { File(it, "bin") }

    /** The certificate bundle the packaged curl/git/python were built to trust. */
    fun certificate(paths: Paths): File? = listOf(
        File(paths.shellTools, "etc/tls/cert.pem"),
        File(paths.filesDir, "opencode/ca.pem"),
    ).firstOrNull { it.isFile }

    /**
     * The variables every shell/exec/PTY receives. `HOME` is the caller's working home;
     * `PREFIX` is only present once the shell pack is installed.
     */
    fun env(context: Context, home: File): LinkedHashMap<String, String> {
        val paths = paths(context)
        val map = linkedMapOf<String, String>()
        apply(map, paths, home)
        return map
    }

    fun apply(
        environment: MutableMap<String, String>,
        paths: Paths,
        home: File,
    ) {
        val shell = paths.shellTools.takeIf { File(it, "bin/sh").exists() }
        val media = paths.mediaTools.takeIf { File(it, "bin").isDirectory }
        val go = paths.goTools.takeIf { File(it, "bin/go").isFile }
        val rust = paths.rustTools.takeIf { File(it, "bin/cargo").isFile }
        val dart = paths.dartTools.takeIf { File(it, "bin/dart").isFile }
        val php = paths.phpTools.takeIf { File(it, "bin/php").isFile }
        val ruby = paths.rubyTools.takeIf { File(it, "bin/ruby").isFile }
        val deno = paths.denoTools.takeIf { File(it, "bin/deno").isFile }
        val bun = paths.bunTools.takeIf { File(it, "bin/bun").isFile }
        val java = javaHome(paths)
        val gradleBin = gradleBin(paths)
        val certificate = certificate(paths)
        val path = buildList {
            shell?.let { add(File(it, "bin").absolutePath) }
            media?.let { add(File(it, "bin").absolutePath) }
            go?.let { add(File(it, "bin").absolutePath) }
            rust?.let { add(File(it, "bin").absolutePath) }
            dart?.let { add(File(it, "bin").absolutePath) }
            php?.let { add(File(it, "bin").absolutePath) }
            ruby?.let { add(File(it, "bin").absolutePath) }
            deno?.let { add(File(it, "bin").absolutePath) }
            bun?.let { add(File(it, "bin").absolutePath) }
            add(File(home, ".local/bin").absolutePath)
            java?.let { add(File(it, "bin").absolutePath) }
            gradleBin?.let { add(it.absolutePath) }
            add("/system/bin")
            add("/system/xbin")
            add(environment["PATH"].orEmpty().ifBlank { "/usr/bin:/bin" })
        }.filter { it.isNotBlank() }.joinToString(":")
        environment["PATH"] = path
        environment["HOME"] = home.absolutePath
        environment["TMPDIR"] = (shell?.let { File(it, "tmp") }
            ?: File(paths.filesDir, "tmp")).apply { mkdirs() }.absolutePath
        if (shell != null) {
            environment["PREFIX"] = shell.absolutePath
            environment["TERMUX_PREFIX"] = shell.absolutePath
            environment["SHELL"] = File(shell, "bin/sh").absolutePath
            val base = environment["LD_LIBRARY_PATH"].orEmpty()
                .split(':')
                .filter { it.isNotEmpty() && it != File(shell, "lib").absolutePath }
            environment["LD_LIBRARY_PATH"] =
                (listOf(File(shell, "lib").absolutePath) + base).joinToString(":")
            media?.let { mediaPrefix ->
                val existing = environment["LD_LIBRARY_PATH"].orEmpty()
                val mediaLib = File(mediaPrefix, "lib").absolutePath
                if (!existing.split(':').contains(mediaLib)) {
                    environment["LD_LIBRARY_PATH"] = (listOf(mediaLib) + existing.split(':').filter { it.isNotEmpty() }).joinToString(":")
                }
            }
            environment["npm_config_prefix"] = shell.absolutePath
            environment["NODE_PATH"] = File(shell, "lib/node_modules").absolutePath
            environment["PIP_CACHE_DIR"] = File(shell, "cache/pip").absolutePath
            environment["PYTHONUSERBASE"] = File(home, ".local").absolutePath
        }
        java?.let { environment["JAVA_HOME"] = it.absolutePath }
        paths.sdk.takeIf { it.isDirectory }?.let {
            environment["ANDROID_HOME"] = it.absolutePath
            environment["ANDROID_SDK_ROOT"] = it.absolutePath
        }
        gradleHome(paths)?.let { environment["GRADLE_USER_HOME"] = it.absolutePath }
        gradleBin?.let { environment["CLOSECODE_GRADLE"] = File(it, "gradle").absolutePath }
        certificate?.let {
            environment["SSL_CERT_FILE"] = it.absolutePath
            environment["CURL_CA_BUNDLE"] = it.absolutePath
            environment["GIT_SSL_CAINFO"] = it.absolutePath
            environment["NODE_EXTRA_CA_CERTS"] = it.absolutePath
        }
    }
}
