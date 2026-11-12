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
    val version: String get() = manifest.version

    fun isReady(): Boolean =
        manifest.roots.isNotEmpty() &&
            File(directory, ".installed").isFile &&
            File(directory, "bin/sh").exists() &&
            manifest.required.all { File(directory, it).exists() }

    fun installedBytes(): Long = DiskUsage.bytes(directory)

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
        if (isReady()) return repairPython(progress)
        val architecture = ShellToolsRepo.architecture(Build.SUPPORTED_ABIS.toList())
            ?: return Result(false, "This phone's ABI has no Termux packages.")
        val temp = File(context.dataDir, "file/$prefixName.tmp")
        deleteTree(temp)
        val indexFile = File(context.cacheDir, "$cachePrefix-Packages")
        indexFile.delete()
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
                // The transfer goes to a .part file, so an interrupted download is never
                // mistaken for a finished one, and it is found again by name to resume.
                val part = File(context.cacheDir, "$cachePrefix-${packageFile.name}.deb.part")
                download(
                    ShellToolsRepo.packageUrl(manifest.repo, packageFile.fileName),
                    part,
                    packageFile.sizeBytes,
                ) { saved, size ->
                    val message = if (saved > 0L && size > 0L && saved < size) {
                        "Continuing ${packageFile.name} from $saved of $size bytes…"
                    } else {
                        "Downloading ${packageFile.name}…"
                    }
                    progress(
                        Progress("download", saved, size, packageFile.name, message),
                    )
                }
                if (packageFile.sizeBytes > 0 && part.length() != packageFile.sizeBytes) {
                    throw IOException("${packageFile.name} has the wrong size.")
                }
                if (!matchesSha256(part, packageFile.sha256)) {
                    part.delete()
                    throw IOException("${packageFile.name} failed its checksum.")
                }
                // Verified: the partial becomes the finished package.
                if (!part.renameTo(deb)) {
                    part.copyTo(deb, overwrite = true)
                    part.delete()
                }
                DebArchive.dataTar(deb.readBytes()).use { tar ->
                    TarExtractor(SOURCE_PREFIX, temp, directory).extract(tar)
                }
                deb.delete()
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

            // P1-1: the pip wheel this runtime's own ensurepip asks for is put in place
            // while the new prefix is still in the staging area, so the swap below installs
            // a prefix that already works. The interpreter is pointed at a wheels directory
            // too, which is what makes it self-healing: ensurepip takes the newest pip wheel
            // it finds there, whatever version the runtime thinks it wants. A wheel left
            // behind by an older Python is removed at the same time.
            if (PythonPackRepair.needsRepair(temp)) {
                progress(
                    Progress(
                        "download",
                        0,
                        PythonPackRepair.expectedSize(temp),
                        "pip",
                        "Installing the pip wheel this Python asks for…",
                    ),
                )
                val repaired = PythonPackRepair.repair(context, temp, directory) { done, total ->
                    progress(Progress("download", done, total, "pip", "Installing the pip wheel…"))
                }
                repaired.exceptionOrNull()?.let { error ->
                    // The pack itself is good; only the wheel could not be fetched. The
                    // install goes ahead and the next app start finishes the job, so the
                    // user keeps a working shell instead of losing it to a slow download.
                    DebugLog.crumb(
                        "TOOLS",
                        "pip wheel not installed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }

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
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("$cachePrefix-") || it.name.endsWith(".whl.part") }
            ?.forEach { file ->
                if (file.isDirectory) deleteTree(file) else file.delete()
            }
        DebugLog.crumb("TOOLS", "$label removed")
        !directory.exists() && !home.exists()
    }

    fun repairPython(progress: (Progress) -> Unit = {}): Result = synchronized(lock) {
        if (!PythonPackRepair.needsRepair(directory)) return Result(true)
        return try {
            progress(Progress("download", 0, PythonPackRepair.expectedSize(directory), "pip", "pip wheel"))
            PythonPackRepair.repair(context, directory) { done, total ->
                progress(Progress("download", done, total, "pip", "pip wheel"))
            }.getOrThrow()
            Result(true)
        } catch (error: Throwable) {
            Result(false, error.message ?: error.javaClass.simpleName)
        }
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

    private fun download(
        url: String,
        target: File,
        expectedSize: Long = 0L,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) = ResumableDownload.download(url, target, expectedSize, onProgress)

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

    /**
     * Repairs the ensurepip bundle once per app run.
     *
     * The wheel is checked, not downloaded, on every call: the check is a file test and a
     * checksum of one small file. When the runtime was upgraded under the app and the wheel
     * it asks for is missing, this puts it back, so `python3 -m venv` works without anyone
     * touching the phone by hand.
     */
    fun repairPythonOnce(progress: (Progress) -> Unit = {}): Result = synchronized(lock) {
        if (pythonChecked) return Result(true)
        val result = repairPython(progress)
        if (result.ok) pythonChecked = true
        result
    }

    private companion object {
        val lock = Any()
        const val SOURCE_PREFIX = "data/data/com.termux/files/usr"

        /** The pip wheel has been checked in this app run already. */
        @Volatile
        var pythonChecked = false
    }
}
