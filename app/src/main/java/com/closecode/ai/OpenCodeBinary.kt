package com.closecode.ai

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The OpenCode program itself: where it comes from, and how it is put on the phone.
 *
 * OpenCode is not shipped inside CloseCode — it is close to two hundred megabytes and it changes
 * on its own schedule. The first start downloads the release that matches the phone's CPU, and
 * every later start uses the copy already on the phone.
 *
 * The musl releases are the ones used, because Android's C library is not glibc and cannot run a
 * glibc program. They are not static, though: they need musl itself, the C++ library and libgcc,
 * and [OpenCodeRuntime] puts those on the phone beside the program and starts it through them.
 */
object OpenCodeBinary {

    data class Install(val binary: File, val changed: Boolean)

    /** Where the program lives on the phone. */
    fun installDir(context: Context): File = File(context.filesDir, "opencode")

    /** The program itself. */
    fun binaryFile(context: Context): File = File(installDir(context), "opencode")

    /** The name of the release the program on the phone came out of. */
    private fun markerFile(context: Context): File = File(installDir(context), "program.txt")

    /** True when the program is already on the phone and ready to run. */
    fun isInstalled(context: Context): Boolean {
        val file = binaryFile(context)
        return file.isFile && file.length() > 0 && file.canExecute()
    }

    /**
     * The npm platform package for this phone, or null when the CPU is not supported.
     *
     * CloseCode's V2 migration targets 64-bit ARM only. The first ABI decides, because an
     * emulator can list an ARM ABI it only translates and must not be handed a build it cannot
     * execute.
     */
    fun assetForAbis(abis: List<String>): String? =
        if (abis.firstOrNull()?.lowercase() == "arm64-v8a") OpenCodeDefaults.NPM_PACKAGE else null

    /** Where the pinned npm tarball is downloaded from. */
    fun downloadUrl(asset: String): String = OpenCodeDefaults.NPM_TARBALL

    /**
     * The program on the phone, downloading it first if this is the first start.
     *
     * [onProgress] is the percent, so the download shows in the same Build Info box as
     * everything else. Nothing is written until the whole archive has arrived, so an interrupted
     * download leaves no half a program behind.
     */
    fun ensure(
        context: Context,
        abis: List<String>,
        onProgress: (Int) -> Unit,
    ): Install {
        val asset = assetForAbis(abis)
            ?: throw IllegalStateException(
                "This phone's CPU is not one OpenCode runs on. CloseCode V2 needs ARM64.",
            )
        val target = binaryFile(context)
        val marker = markerFile(context)
        val expectedMarker = "opencode-${OpenCodeDefaults.VERSION}-arm64-musl"
        if (isInstalled(context) && marker.isFile && marker.readText().trim() == expectedMarker) {
            return Install(target, changed = false)
        }

        val url = downloadUrl(asset)
        installDir(context).mkdirs()
        val archive = File(installDir(context), "cli-linux-arm64-musl-${OpenCodeDefaults.VERSION}.tgz")
        if (marker.isFile && marker.readText().trim() != expectedMarker) {
            installDir(context).listFiles()
                ?.filter { it.isFile && it.name != archive.name && it.name != target.name }
                ?.filter { it.name.endsWith(".tgz") || it.name.endsWith(".tar.gz") }
                ?.forEach { it.delete() }
        }
        if (archive.isFile && archive.length() > 0L &&
            Downloads.sha256(archive) != OpenCodeDefaults.NPM_TARBALL_SHA256
        ) {
            archive.delete()
        }
        if (!archive.isFile || archive.length() == 0L) {
            Downloads.get(url, archive, what = "OpenCode V2 download", onProgress = onProgress)
        } else {
            onProgress(100)
        }
        if (Downloads.sha256(archive) != OpenCodeDefaults.NPM_TARBALL_SHA256) {
            archive.delete()
            throw IllegalStateException("The OpenCode V2 download did not match its hash.")
        }

        val staged = File(installDir(context), "opencode.new")
        staged.delete()
        try {
            val entry = TarReader.extractFile(
                archive.inputStream(),
                OpenCodeDefaults.NPM_BINARY_PATH,
                staged,
            )
            if (entry == null) {
                archive.delete()
                throw IllegalStateException("The OpenCode V2 archive held no program.")
            }
            staged.setExecutable(true, false)
            staged.setReadable(true, false)
            replaceFileAtomically(staged, target)
            archive.delete()
            marker.writeText(expectedMarker)
            return Install(target, changed = true)
        } catch (error: Throwable) {
            staged.delete()
            archive.delete()
            throw error
        }
    }
}

internal fun replaceFileAtomically(staged: File, target: File) {
    if (!staged.isFile) throw IllegalStateException("The staged OpenCode program is missing.")
    val direct = runCatching {
        Files.move(
            staged.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }.isSuccess
    if (direct) return

    val backup = File(target.parentFile, "${target.name}.old")
    backup.delete()
    if (target.isFile) {
        val backedUp = runCatching {
            Files.move(
                target.toPath(),
                backup.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.isSuccess
        if (!backedUp) {
            throw IllegalStateException("Could not move the old OpenCode program aside.")
        }
    }
    val installed = runCatching {
        Files.move(
            staged.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }.isSuccess
    if (!installed) {
        if (backup.isFile) {
            runCatching {
                Files.move(
                    backup.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
        throw IllegalStateException("Could not install the new OpenCode program.")
    }
    backup.delete()
}
