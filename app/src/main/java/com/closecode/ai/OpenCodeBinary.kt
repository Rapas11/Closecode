package com.closecode.ai

import android.content.Context
import java.io.File

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
        "https://github.com/sst/opencode/releases/download/${OpenCodeDefaults.TAG}/$asset"

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
    ): File {
        val asset = assetForAbis(abis)
            ?: throw IllegalStateException(
                "This phone's CPU is not one OpenCode runs on. It needs ARM64 or x86_64.",
            )
        val target = binaryFile(context)
        val marker = markerFile(context)
        // The file on its own is not enough: it has to be the build for *this* phone. The marker
        // says which release it came out of, so a phone that reports a different CPU — an
        // emulator, or an app restored onto new hardware — gets the right one instead of a
        // program that cannot run here.
        // The pinned release the local files must be from. The archive's name carries it, so
        // leftovers from another release cannot be quietly unpacked in place of this one.
        val expectedMarker = asset.replace(".tar.gz", "-${OpenCodeDefaults.VERSION}.tar.gz")
        if (isInstalled(context) && marker.isFile && marker.readText().trim() == expectedMarker) {
            return target
        }

        val url = downloadUrl(asset)
        installDir(context).mkdirs()
        val archive = File(installDir(context), expectedMarker)
        // An older release's program or archive is quietly in the way: the marker says so. The
        // program of the older release goes, and so do archives that do not carry this
        // release's name — an archive with the right name is this release's own download and
        // stays for the unpacking below.
        if (marker.isFile && marker.readText().trim() != expectedMarker) {
            target.delete()
            installDir(context).listFiles()
                ?.filter { it.name.endsWith(".tar.gz") && it.name != expectedMarker }
                ?.forEach { it.delete() }
            marker.delete()
        }
        // A previous start may have downloaded the archive and then failed while unpacking it.
        // The file is only ever renamed into place once every byte arrived, so a file that is
        // there is a whole archive, and downloading it a second time would buy nothing.
        if (!archive.isFile || archive.length() == 0L) {
            Downloads.get(url, archive, what = "OpenCode download", onProgress = onProgress)
        } else {
            onProgress(100)
        }

        // Unpacked straight to disk: the program is close to two hundred megabytes, so it is
        // written through a small buffer and renamed into place only once every byte arrived.
        val entry = TarReader.extractFirstFile(archive.inputStream(), target)
        if (entry == null) {
            // Nothing usable inside, so it is not worth keeping: the next start downloads again.
            archive.delete()
            throw IllegalStateException("The downloaded OpenCode archive held no program.")
        }
        archive.delete()
        // The program has to be executable to be run at all.
        target.setExecutable(true, false)
        target.setReadable(true, false)
        marker.writeText(expectedMarker)
        return target
    }
}
