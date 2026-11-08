package com.closecode.build

import java.io.File

data class NativeReason(val what: String, val place: Place? = null)

/**
 * What makes a project a native project. The custom engine cannot compile C/C++, so the
 * check names the reason and points at Full Gradle; the native tools are installed separately.
 *
 * Prebuilt .so files under jniLibs do not count. They are packaged by the custom engine and do
 * not need a compiler. Only sources, CMake/native build files, or an explicit NDK declaration do.
 */
data class NativeUsage(
    val usesNative: Boolean,
    val reasons: List<NativeReason> = emptyList(),
    val requestedNdkVersion: String? = null,
)

object NativeProject {

    private val nativeKeys = setOf("externalNativeBuild", "ndkVersion")

    fun isNativeBit(what: String): Boolean = what in nativeKeys

    fun detect(projectDir: File, facts: BuildFacts = GradleReader.read(projectDir)): NativeUsage {
        val reasons = mutableListOf<NativeReason>()
        var requested: String? = null
        for (file in listOf("app/build.gradle.kts", "app/build.gradle")) {
            val text = runCatching { File(projectDir, file).readText() }.getOrNull() ?: continue
            text.lineSequence().forEachIndexed { index, line ->
                val code = line.substringBefore("//")
                if (Regex("\\bexternalNativeBuild\\b").containsMatchIn(code)) {
                    reasons += NativeReason("externalNativeBuild", Place(file, index + 1))
                }
                if (Regex("\\bndkVersion\\b").containsMatchIn(code)) {
                    reasons += NativeReason("ndkVersion", Place(file, index + 1))
                    requested = requested ?: Regex("ndkVersion\\s*=\\s*\"([^\"]+)\"")
                        .find(code)
                        ?.groupValues
                        ?.get(1)
                        ?: Regex("ndkVersion\\s+\"([^\"]+)\"")
                            .find(code)
                            ?.groupValues
                            ?.get(1)
                }
                if (Regex("\\bndk\\s*\\{").containsMatchIn(code)) {
                    reasons += NativeReason("the ndk block", Place(file, index + 1))
                }
            }
        }
        val sourceRoot = File(projectDir, "app/src/main")
        if (sourceRoot.isDirectory) {
            sourceRoot.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    when (file.name) {
                        "CMakeLists.txt" -> reasons += NativeReason(
                            "CMakeLists.txt",
                            Place(file.relativeTo(projectDir).path, 1),
                        )
                        "Android.mk", "Application.mk" -> reasons += NativeReason(
                            file.name,
                            Place(file.relativeTo(projectDir).path, 1),
                        )
                    }
                }
        }
        facts.unsupported
            .filter { isNativeBit(it.what) }
            .forEach { reasons += NativeReason(it.what, it.place) }
        return NativeUsage(
            usesNative = reasons.isNotEmpty(),
            reasons = reasons.distinctBy { it.what to it.place },
            requestedNdkVersion = requested,
        )
    }
}
