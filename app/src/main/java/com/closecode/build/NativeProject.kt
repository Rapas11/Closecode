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

    /** `ndkVersion` only matters when the project has real native input. */
    fun isOptionalConfig(what: String): Boolean = what == "ndkVersion"

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
                    requested = requested ?: Regex("ndkVersion\\s*=\\s*\"([^\"]+)\"")
                        .find(code)
                        ?.groupValues
                        ?.get(1)
                        ?: Regex("ndkVersion\\s+\"([^\"]+)\"")
                            .find(code)
                            ?.groupValues
                            ?.get(1)
                }
            }
        }
        val sourceRoot = File(projectDir, "app/src")
        if (sourceRoot.isDirectory) {
            sourceRoot.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(projectDir).path
                    when {
                        file.name == "CMakeLists.txt" ->
                            reasons += NativeReason("CMakeLists.txt", Place(relative, 1))
                        file.name == "Android.mk" || file.name == "Application.mk" ->
                            reasons += NativeReason(file.name, Place(relative, 1))
                        file.extension.lowercase() in NATIVE_SOURCE_EXTENSIONS ->
                            reasons += NativeReason("native source $relative", Place(relative, 1))
                        relative.contains("/jni/") || relative.contains("/cpp/") ->
                            reasons += NativeReason("native source $relative", Place(relative, 1))
                    }
                }
        }
        // A bare ndkVersion pin is not native input; it only explains a project that already
        // has C/C++ sources or a native build file.
        if (requested != null && reasons.isNotEmpty()) {
            reasons += NativeReason("ndkVersion", reasons.firstOrNull()?.place)
        }
        return NativeUsage(
            usesNative = reasons.isNotEmpty(),
            reasons = reasons.distinctBy { it.what to it.place },
            requestedNdkVersion = requested,
        )
    }

    private val NATIVE_SOURCE_EXTENSIONS = setOf("c", "cc", "cpp", "cxx", "h", "hh", "hpp")
}
