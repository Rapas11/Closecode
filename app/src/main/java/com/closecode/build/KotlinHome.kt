package com.closecode.build

import java.io.File

/**
 * Kotlin 1.9.24's CLI finds its stdlib by asking the class loader for
 * `/org/jetbrains/kotlin/utils/PathUtil.class`. On Android the compiler runs from dex,
 * where `.class` resources do not exist, so that lookup throws "Resource not found".
 * Passing `-kotlin-home` skips the lookup entirely: the compiler derives the stdlib path
 * from `<home>/lib/kotlin-stdlib.jar`. The build already passes `-no-stdlib`, so this
 * copy is never added to the classpath twice; it only satisfies the path probe.
 */
object KotlinHome {
    fun ensure(stdlib: File): File? {
        if (!stdlib.isFile) return null
        val root = File(System.getProperty("java.io.tmpdir") ?: return null, "closecode-kotlin-home")
        val lib = File(root, "lib").apply { mkdirs() }
        val copy = File(lib, "kotlin-stdlib.jar")
        if (!copy.isFile || copy.length() != stdlib.length()) {
            runCatching { stdlib.copyTo(copy, overwrite = true) }.onFailure { return null }
        }
        return root
    }
}
