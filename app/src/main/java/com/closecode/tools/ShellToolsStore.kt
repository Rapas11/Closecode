package com.closecode.tools

import android.content.Context
import android.os.Build
import com.closecode.debug.DebugLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Installs the optional shell tools on demand. The packages are the current Termux
 * aarch64/x86_64 builds, verified against the repository's SHA-256 and unpacked under
 * `file/u`; the Termux prefix compiled into each binary is rewritten to that path.
 * Nothing is shipped in the APK and nothing runs as root.
 */
class ShellToolsStore(
    private val context: Context,
    private val manifest: ShellToolsManifest,
    private val prefixName: String = "u",
    private val homeName: String = "hh",
    private val label: String = "Shell tools",
    private val cachePrefix: String = "shell-tools",
) {
    data class Progress(
        val stage: String,
        val doneBytes: Long,
        val totalBytes: Long,
        val packageName: String?,
        val message: String,
    )

    data class Result(val ok: Boolean, val error: String? = null)

    val directory: File get() = ShellToolsRepo.workingPrefix(context.dataDir, prefixName)
    val home: File get() = ShellToolsRepo.workingHome(context.dataDir, homeName)

    fun isReady(): Boolean =
        manifest.roots.isNotEmpty() &&
            File(directory, ".installed").isFile &&
            File(directory, "bin/sh").exists() &&
            manifest.required.all { File(directory, it).exists() }

    fun installedBytes(): Long = directory.walkTopDown()
        .onEnter { !Files.isSymbolicLink(it.toPath()) }
        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
        .sumOf { it.length() }

    fun installedPackages(): List<String> {
        val marker = File(directory, ".installed")
        if (!marker.isFile) return emptyList()
        return marker.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    fun install(progress: (Progress) -> Unit): Result = synchronized(lock) {
        if (manifest.repo.isBlank() || manifest.roots.isEmpty()) {
            return Result(false, "The $label manifest is missing.")
        }
        val architecture = ShellToolsRepo.architecture(Build.SUPPORTED_ABIS.toList())
            ?: return Result(false, "This phone's ABI has no Termux packages.")
        val temp = File(context.dataDir, "file/$prefixName.tmp")
        deleteTree(temp)
        val indexFile = File(context.cacheDir, "$cachePrefix-Packages")
        try {
            progress(Progress("index", 0, 0, null, "Reading the package index..."))
            download(
                ShellToolsRepo.indexUrl(manifest.repo, architecture),
                indexFile,
            )
            val index = TermuxIndex.parse(indexFile.readText())
            val packages = index.resolve(manifest.roots)
            val total = packages.sumOf { it.sizeBytes }
            progress(Progress("resolve", 0, total, null, "${packages.size} packages"))

            var done = 0L
            packages.forEach { packageFile ->
                progress(
                    Progress("download", done, total, packageFile.name, packageFile.name),
                )
                val deb = File(context.cacheDir, "$cachePrefix-${packageFile.name}.deb")
                try {
                    download(
                        ShellToolsRepo.packageUrl(manifest.repo, packageFile.fileName),
                        deb,
                    )
                    if (packageFile.sizeBytes > 0 && deb.length() != packageFile.sizeBytes) {
                        throw IOException("${packageFile.name} has the wrong size.")
                    }
                    if (!matchesSha256(deb, packageFile.sha256)) {
                        throw IOException("${packageFile.name} failed its checksum.")
                    }
                    DebArchive.dataTar(deb.readBytes()).use { tar ->
                        TarExtractor(SOURCE_PREFIX, temp, directory).extract(tar)
                    }
                } finally {
                    deb.delete()
                }
                done += packageFile.sizeBytes
                progress(Progress("unpack", done, total, packageFile.name, packageFile.name))
            }

            val prefix = TermuxPrefix(context.packageName, prefixName, homeName)
            progress(Progress("relocate", done, total, null, "Relocating the Termux prefix..."))
            prefix.patch(temp)
            ensureLink(File(temp, "bin/sh"), "/system/bin/sh")
            ensureLink(File(temp, "bin/env"), "/system/bin/env")
            ensureShellAliases(temp)
            if (!File(temp, "bin/sh").exists()) {
                throw IOException("Could not create the shell entry point.")
            }
            val missing = manifest.required.filter { !File(temp, it).exists() }
            if (missing.isNotEmpty()) {
                throw IOException("The $label are missing ${missing.joinToString()}.")
            }
            File(temp, "tmp").mkdirs()
            home.mkdirs()
            File(temp, ".installed").writeText(
                buildString {
                    appendLine("# CloseCode $label ${manifest.version} · $architecture")
                    packages.forEach { appendLine(it.name) }
                },
            )

            deleteTree(directory)
            if (!temp.renameTo(directory)) {
                temp.copyRecursively(directory, overwrite = true)
                deleteTree(temp)
            }
            DebugLog.crumb("TOOLS", "$label installed · ${packages.size} packages")
            progress(Progress("done", total, total, null, "$label installed."))
            Result(true)
        } catch (error: Throwable) {
            deleteTree(temp)
            DebugLog.crumb("TOOLS", "$label failed: ${error.message ?: error.javaClass.simpleName}")
            Result(false, error.message ?: error.javaClass.simpleName)
        } finally {
            indexFile.delete()
        }
    }

    fun remove(): Boolean = synchronized(lock) {
        deleteTree(directory)
        deleteTree(home)
        DebugLog.crumb("TOOLS", "$label removed")
        !directory.exists() && !home.exists()
    }

    private fun deleteTree(file: File) {
        if (!file.exists() && !Files.isSymbolicLink(file.toPath())) return
        if (Files.isSymbolicLink(file.toPath()) || !file.isDirectory) {
            file.delete()
            return
        }
        file.listFiles()?.forEach { child -> deleteTree(child) }
        file.delete()
    }

    private fun ensureLink(file: File, target: String) {
        if (file.exists()) return
        if (Files.isSymbolicLink(file.toPath())) {
            if (runCatching { file.canonicalFile.exists() }.getOrDefault(false)) return
            file.delete()
        }
        file.parentFile?.mkdirs()
        runCatching {
            Files.createSymbolicLink(file.toPath(), File(target).toPath())
        }.onFailure {
            // A device that refuses symlinks still gets a working executable copy.
            val source = File(target)
            if (source.isFile) source.copyTo(file, overwrite = true)
        }
    }

    private fun ensureShellAliases(root: File) {
        ensureLink(File(root, "bin/python"), File(root, "bin/python3").absolutePath)
        ensureLink(File(root, "bin/pip3"), File(root, "bin/pip").absolutePath)
        val npm = File(root, "bin/npm")
        val npx = File(root, "bin/npx")
        if (!npx.exists() && Files.isSymbolicLink(npx.toPath())) npx.delete()
        if (!npx.exists() && npm.exists()) {
            npx.writeText(
                "#!/system/bin/sh\n" +
                    "exec '${npm.absolutePath}' exec -- \"\$@\"\n",
            )
            npx.setExecutable(true, false)
        }
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "CloseCode/${Build.VERSION.SDK_INT}")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("The package server returned HTTP $code.")
            target.outputStream().use { output ->
                connection.inputStream.use { input -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun matchesSha256(file: File, expected: String): Boolean {
        if (expected.isBlank()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
    }

    private companion object {
        val lock = Any()
        const val SOURCE_PREFIX = "data/data/com.termux/files/usr"
    }
}
