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
     * The release file for this phone, or null when the CPU is not one OpenCode ships for.
     *
     * The phone's *preferred* ABI decides — the first of the supported ones. A phone reports what
     * it runs best, and an emulator that can also translate ARM lists x86_64 first: taking any
     * arm64 it mentions instead would hand it a program it can only crash on, which is exactly
     * what happened before this looked at the first ABI and nothing else.
     */
    fun assetForAbis(abis: List<String>): String? = when (abis.firstOrNull()?.lowercase()) {
        "arm64-v8a" -> "opencode-linux-arm64-musl.tar.gz"
        "x86_64" -> "opencode-linux-x64-musl.tar.gz"
        else -> null
    }

    /** Where the release file is downloaded from. */
    fun downloadUrl(asset: String): String =
        "https://github.com/anomalyco/opencode/releases/download/${OpenCodeDefaults.TAG}/$asset"

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
                "This phone's CPU is not one OpenCode runs on. It needs ARM64 or x86_64.",
            )
        val target = binaryFile(context)
        val marker = markerFile(context)
        val expectedMarker = asset.replace(".tar.gz", "-${OpenCodeDefaults.VERSION}.tar.gz")
        if (isInstalled(context) && marker.isFile && marker.readText().trim() == expectedMarker) {
            return Install(target, changed = false)
        }

        val url = downloadUrl(asset)
        installDir(context).mkdirs()
        val archive = File(installDir(context), expectedMarker)
        if (marker.isFile && marker.readText().trim() != expectedMarker) {
            installDir(context).listFiles()
                ?.filter { it.name.endsWith(".tar.gz") && it.name != expectedMarker }
                ?.forEach { it.delete() }
        }
        if (!archive.isFile || archive.length() == 0L) {
            Downloads.get(url, archive, what = "OpenCode download", onProgress = onProgress)
        } else {
            onProgress(100)
        }

        val staged = File(installDir(context), "opencode.new")
        val part = File(installDir(context), "opencode.new.part")
        staged.delete()
        part.delete()
        try {
            val entry = TarReader.extractFirstFile(archive.inputStream(), staged)
            if (entry == null) {
                archive.delete()
                throw IllegalStateException("The downloaded OpenCode archive held no program.")
            }
            staged.setExecutable(true, false)
            staged.setReadable(true, false)
            replaceFileAtomically(staged, target)
            archive.delete()
            marker.writeText(expectedMarker)
            return Install(target, changed = true)
        } catch (error: Throwable) {
            staged.delete()
            part.delete()
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
