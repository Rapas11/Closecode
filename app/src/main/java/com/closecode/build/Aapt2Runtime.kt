package com.closecode.build

import android.content.Context
import java.io.File
import java.nio.file.Files

/**
 * Runtime shims for the aapt2 binary that ships inside the APK.
 *
 * The bundled aapt2 is built from AOSP platform-tools-35.0.2 (Apache-2.0) and links
 * `libc++_shared.so` plus `libz.so.1`. `libc++_shared.so` rides in jniLibs and lands in
 * the app's native library directory. Android names zlib `libz.so`, so this makes a
 * `libz.so.1` link to the device's own zlib and hands aapt2 an LD_LIBRARY_PATH. On an
 * ARM-translation emulator the native zlib is `libz.so` under `/system/lib64/arm64`;
 * on a real ARM phone it is `/system/lib64/libz.so`. No system file is modified.
 */
object Aapt2Runtime {
    private const val AARCH64 = 183

    /** Directory holding `libz.so.1`; safe to call from a background thread. */
    fun libDir(context: Context): File {
        val dir = File(context.filesDir, "aapt2-libs").apply { mkdirs() }
        val link = File(dir, "libz.so.1")
        if (link.exists()) return dir
        if (Files.isSymbolicLink(link.toPath())) link.delete()
        val source = zlibForDevice() ?: return dir
        runCatching { Files.createSymbolicLink(link.toPath(), source.toPath()) }
            .onFailure { runCatching { source.copyTo(link, overwrite = true) } }
        return dir
    }

    /** The environment aapt2 needs; the native lib dir has `libc++_shared.so`. */
    fun environment(context: Context): Map<String, String> {
        val native = context.applicationInfo.nativeLibraryDir
        val shims = libDir(context).absolutePath
        val existing = System.getenv("LD_LIBRARY_PATH")?.takeIf { it.isNotBlank() }
        val path = listOf(native, shims, existing).filterNotNull().joinToString(":")
        return mapOf("LD_LIBRARY_PATH" to path)
    }

    private fun zlibForDevice(): File? {
        val normal = File("/system/lib64/libz.so")
        if (normal.isFile && machineOf(normal) == AARCH64) return normal
        val armUnderTranslation = File("/system/lib64/arm64/libz.so")
        if (armUnderTranslation.isFile && machineOf(armUnderTranslation) == AARCH64) {
            return armUnderTranslation
        }
        return normal.takeIf { it.isFile }
    }

    private fun machineOf(file: File): Int = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(20)
            if (input.read(header) != header.size) return@use -1
            (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
        }
    }.getOrDefault(-1)
}
