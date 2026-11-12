package com.closecode.build

import java.io.File

/**
 * The processor of an ELF binary.
 *
 * A build-tools folder that came from a desktop package holds x86-64 binaries. On an ARM
 * phone they cannot execute at all, and the error — "not executable: 64-bit ELF file" — says
 * nothing about the cause. The header is read instead, so the app can tell a working tool
 * from one that can never run here, and can say so before a build fails.
 */
object ElfArchitecture {

    const val ARM64 = "arm64"
    const val X86_64 = "x86_64"
    const val ARM = "arm"

    /** The architecture of an ELF file, or null when the file is not an ELF binary. */
    fun of(file: File): String? {
        if (!file.isFile || file.length() < 20L) return null
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(20)
                if (input.read(header) != header.size) return null
                if (header[0] != 0x7f.toByte() || header[1] != 'E'.code.toByte() ||
                    header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
                ) {
                    return null
                }
                // e_machine is a 16-bit value at offset 18, little endian.
                val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
                when (machine) {
                    183 -> ARM64
                    62 -> X86_64
                    40 -> ARM
                    else -> "machine-$machine"
                }
            }
        }.getOrNull()
    }

    /** True when the binary is an ARM64 ELF, which is what this phone can run. */
    fun isArm64(file: File): Boolean = of(file) == ARM64

    /** True when the file is a binary this phone cannot execute. */
    fun isForeign(file: File): Boolean {
        val arch = of(file) ?: return false
        return arch != ARM64
    }
}
