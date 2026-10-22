package com.closecode.ai

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The OpenCode program itself: where it comes from, and how it is put on the phone.
 *
 * OpenCode is not shipped inside CloseCode — it is about sixty megabytes and it changes on its
 * own schedule. The first start downloads the release that matches the phone's CPU, and every
 * later start uses the copy already on the phone.
 *
 * The musl builds are the ones used: they are statically linked, so they run on Android, whose
 * libc is not glibc.
 */
object OpenCodeBinary {

    /** Where the program lives on the phone. */
    fun installDir(context: Context): File = File(context.filesDir, "opencode")

    /** The program itself. */
    fun binaryFile(context: Context): File = File(installDir(context), "opencode")

    /** True when the program is already on the phone and ready to run. */
    fun isInstalled(context: Context): Boolean {
        val file = binaryFile(context)
        return file.isFile && file.length() > 0 && file.canExecute()
    }

    /**
     * The release file for this phone, or null when the CPU is not one OpenCode ships for.
     *
     * A phone is arm64 in practice; the x86_64 build is here because an emulator is x86_64, and
     * the app must be testable on one.
     */
    fun assetForAbis(abis: List<String>): String? = when {
        abis.any { it.equals("arm64-v8a", ignoreCase = true) } -> "opencode-linux-arm64-musl.tar.gz"
        abis.any { it.equals("x86_64", ignoreCase = true) } -> "opencode-linux-x64-musl.tar.gz"
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
        val target = binaryFile(context)
        if (isInstalled(context)) return target

        val asset = assetForAbis(abis)
            ?: throw IllegalStateException(
                "This phone's CPU is not one OpenCode runs on. It needs ARM64.",
            )
        val url = downloadUrl(asset)

        installDir(context).mkdirs()
        val archive = File(installDir(context), asset)
        // A previous start may have downloaded the archive and then failed while unpacking it.
        // The file is only ever renamed into place once every byte arrived, so a file that is
        // there is a whole archive, and downloading it a second time would buy nothing.
        if (!archive.isFile || archive.length() == 0L) {
            download(url, archive, onProgress)
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
        return target
    }

    /** Downloads a file, reporting the percent as it goes. */
    private fun download(url: String, into: File, onProgress: (Int) -> Unit) {
        val partial = File(into.parentFile, into.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("The OpenCode download failed ($code).")
            }
            val total = connection.contentLength.toLong()
            var written = 0L
            FileOutputStream(partial).use { out ->
                connection.inputStream.use { input: InputStream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            // Only now is the file real: an interrupted download leaves a .part behind.
            if (partial.renameTo(into)) {
                onProgress(100)
            } else {
                partial.copyTo(into, overwrite = true)
                partial.delete()
                onProgress(100)
            }
        } finally {
            connection.disconnect()
        }
    }
}
