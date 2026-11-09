package com.closecode.build

import java.io.File
import java.util.Properties

/** Reads `gradle/wrapper/gradle-wrapper.properties`; the wrapper is the project's own pin. */
object GradleWrapper {

    private val distribution = Regex("""gradle-(\d+(?:\.\d+)+(?:-[A-Za-z0-9.]+)?)-(?:bin|all)\.zip""")

    fun version(projectDir: File): String? {
        val file = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        if (!file.isFile) return null
        val url = runCatching {
            Properties().apply { file.inputStream().use { load(it) } }
                .getProperty("distributionUrl")
        }.getOrNull() ?: return null
        return distribution.find(url)?.groupValues?.get(1)
    }
}
