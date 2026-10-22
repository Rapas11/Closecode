package com.closecode.ai

import android.content.Context
import java.io.File

/**
 * The libraries the OpenCode program is linked against.
 *
 * OpenCode is built for Linux against the musl C library. Android's C library is not musl, and
 * it ships neither musl nor the C++ library nor libgcc, so the program cannot run on a phone as
 * it comes: it is linked against `libc.musl-<cpu>.so.1`, `libstdc++.so.6` and `libgcc_s.so.1`,
 * and the kernel looks for its loader at `/lib/ld-musl-<cpu>.so.1`, which does not exist here.
 *
 * So the first start puts those few megabytes on the phone as well — the files Alpine Linux
 * ships, at a release and a version pinned in this file and checked against pinned hashes — and
 * the program is started *through that loader*, with those directories on its search path:
 *
 *     ld-musl-aarch64.so.1 --library-path <root>/lib:<root>/usr/lib <opencode> web …
 *
 * That is the ordinary way a Linux program is run on an unrooted phone: no chroot, no second
 * system inside the app, just the program's own loader handed the program. The loader is the
 * only thing that has to be executable; it maps the libraries itself.
 */
object OpenCodeRuntime {

    /** The Alpine release every package here comes from. */
    const val ALPINE = "v3.24"

    /** One package: the file it is downloaded as, and the hash that proves it is the file we mean. */
    data class Package(val file: String, val sha256: String)

    /**
     * The CPU the packages are built for, as Alpine names it, or null when OpenCode has no build
     * for this phone.
     *
     * The phone's *preferred* ABI decides — the first of [Build.SUPPORTED_ABIS]. A phone reports
     * what it runs best, and an emulator that can also translate ARM reports x86_64 first and
     * must get the x86_64 program, or it would be handed a program it can only crash on.
     */
    fun alpineArch(abis: List<String>): String? = when (abis.firstOrNull()?.lowercase()) {
        "arm64-v8a" -> "aarch64"
        "x86_64" -> "x86_64"
        else -> null
    }

    /** The packages, per CPU. `musl` is the loader and the C library; the rest it needs beside it. */
    fun packages(arch: String): List<Package> = when (arch) {
        "aarch64" -> listOf(
            Package("musl-1.2.6-r3.apk", "2e17864102278d9049c3a56f1bceed9e8cf287065617e579325123443daed8a5"),
            Package("libgcc-15.2.0-r5.apk", "369aaa6e9d099a737bad6dd3e6c2fe7bb1547ca26d22b94ee0411228f709b403"),
            Package("libstdc++-15.2.0-r5.apk", "2302e766d4e4926038ec166ecb85837ee884576115236ddb565e3a5fca4a11d7"),
            Package("zlib-1.3.2-r1.apk", "1f77a542ac6634761b9fe38d8bdca19b154071dcfa25432e73f2bc8a0740a33f"),
        )

        "x86_64" -> listOf(
            Package("musl-1.2.6-r3.apk", "e142255d0fa397f14318996bc10378093dcc069dc3dd1b6818c8f28a789c21da"),
            Package("libgcc-15.2.0-r5.apk", "393dcd32629f06d7d85409c272d142d0c082772d10b87ef55ee82f47de3be637"),
            Package("libstdc++-15.2.0-r5.apk", "14c987b556f5385a5db18376e788c75f37d85321b8dc1920d926ea7daac1d6f6"),
            Package("zlib-1.3.2-r1.apk", "63aeea03c15a2f9018f81805cfc8aa926bdf5cd68921f22149c2fbb5d0ee9f47"),
        )

        else -> emptyList()
    }

    /** Where the packages are unpacked, in Alpine's own layout. */
    fun root(context: Context): File = File(OpenCodeBinary.installDir(context), "alpine")

    /** The loader that starts the program, and the only file here that has to be executable. */
    fun loader(context: Context, arch: String): File =
        File(root(context), "lib/ld-musl-$arch.so.1")

    /** The directories the loader searches for libraries, in the order it searches them. */
    fun libraryPath(context: Context): String =
        File(root(context), "lib").absolutePath +
            File.pathSeparator +
            File(root(context), "usr/lib").absolutePath

    /** Where a package is kept once downloaded, so a later start has it and needs no network. */
    fun packageFile(context: Context, arch: String, pkg: Package): File =
        File(File(root(context), "pkgs/$arch"), pkg.file)

    /** True when everything the program needs is already on the phone. */
    fun isInstalled(context: Context, arch: String): Boolean {
        val loader = loader(context, arch)
        if (!loader.isFile || !loader.canExecute()) return false
        val stdc = File(root(context), "usr/lib/libstdc++.so.6")
        return stdc.isFile && stdc.length() > 0
    }

    /**
     * Puts the libraries on the phone, downloading the pinned packages the first time.
     *
     * A package already on the phone is checked against its hash instead of downloaded again, so
     * a start without a network works whenever the files are there — and a file of the wrong
     * shape never gets past that check into the loader's hands.
     *
     * The loader, ready to start the program.
     */
    fun ensure(context: Context, arch: String, onProgress: (Int) -> Unit): File {
        val list = packages(arch)
        if (list.isEmpty()) {
            throw IllegalStateException("This phone's CPU has no Linux runtime for OpenCode.")
        }
        if (isInstalled(context, arch)) {
            onProgress(100)
            return loader(context, arch)
        }

        root(context).mkdirs()
        list.forEachIndexed { index, pkg ->
            val archive = packageFile(context, arch, pkg)
            if (!archive.isFile || archive.length() == 0L || Downloads.sha256(archive) != pkg.sha256) {
                Downloads.get(
                    url(arch, pkg.file),
                    archive,
                    what = "${pkg.file} download",
                ) { percent -> onProgress(((index * 100) + percent) / list.size) }
            }
            if (Downloads.sha256(archive) != pkg.sha256) {
                // Not the file we pinned, so it is not worth keeping.
                archive.delete()
                throw IllegalStateException("The ${pkg.file} download did not match its hash.")
            }
            TarReader.extractInto(archive.inputStream(), root(context))
        }

        val loader = loader(context, arch)
        if (!loader.isFile) {
            throw IllegalStateException("The Linux runtime for $arch held no loader.")
        }
        mark(root(context))
        onProgress(100)
        return loader
    }

    /** Where a package is downloaded from. `+` is not a plain character in a path, so it is quoted. */
    fun url(arch: String, file: String): String =
        "https://dl-cdn.alpinelinux.org/alpine/$ALPINE/main/$arch/${file.replace("+", "%2B")}"

    /** Makes every file here readable and executable: the loader has to run, the rest is mapped. */
    private fun mark(file: File) {
        file.setReadable(true, false)
        if (file.isDirectory) {
            file.setExecutable(true, false)
            file.listFiles()?.forEach { mark(it) }
        } else {
            file.setExecutable(true, false)
        }
    }
}
