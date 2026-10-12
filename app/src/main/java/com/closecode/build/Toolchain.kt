package com.closecode.build

/**
 * Milestone (a) setup: every build tool the phone needs, pinned by version, URL, size
 * and SHA-256. The app downloads these once over Wi-Fi (~200-300 MB total), verifies
 * each file, and then builds fully offline.
 *
 * Hashes were taken from Maven Central / dl.google.com on 2026-10-12. A file whose
 * bytes do not match its hash is deleted and reported, never used.
 */
data class ToolComponent(
    val id: String,
    val version: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

object ToolchainManifest {
    const val KOTLIN_VERSION = "2.4.0"
    const val COMPILE_SDK = 34

    val components: List<ToolComponent> = listOf(
        ToolComponent(
            id = "kotlin-compiler",
            version = "2.4.0",
            fileName = "kotlin-compiler-embeddable-2.4.0.jar",
            url = "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/" +
                "kotlin-compiler-embeddable/2.4.0/kotlin-compiler-embeddable-2.4.0.jar",
            sizeBytes = 60_351_320L,
            sha256 = "c2b25e8c1c93ec416ba4327f5e31eaec0f0c8847241b9353e294b8db9dce564f",
        ),
        ToolComponent(
            id = "ecj",
            version = "3.46.0",
            fileName = "ecj-3.46.0.jar",
            url = "https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.46.0/ecj-3.46.0.jar",
            sizeBytes = 3_370_586L,
            sha256 = "d0d43f8e2d7003e5efed612e2cbb5f01870043397d8f1bbe536fd9128f4fcbf7",
        ),
        ToolComponent(
            id = "r8",
            version = "8.13.25",
            fileName = "r8-8.13.25.jar",
            url = "https://dl.google.com/dl/android/maven2/com/android/tools/r8/" +
                "8.13.25/r8-8.13.25.jar",
            sizeBytes = 18_273_598L,
            sha256 = "0d4f2f68c4fcbc9434eff122f41feb6fb1d98efd2faa530cec1abacab1cea03d",
        ),
        ToolComponent(
            id = "apksig",
            version = "8.13.0",
            fileName = "apksig-8.13.0.jar",
            url = "https://dl.google.com/dl/android/maven2/com/android/tools/build/" +
                "apksig/8.13.0/apksig-8.13.0.jar",
            sizeBytes = 506_969L,
            sha256 = "c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89",
        ),
        // Compiler launch classpath: kotlin-compiler-embeddable refuses to start without
        // these beside it (verified: each missing jar named itself in turn).
        ToolComponent(
            id = "kotlin-stdlib",
            version = "2.4.0",
            fileName = "kotlin-stdlib-2.4.0.jar",
            url = "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/" +
                "kotlin-stdlib/2.4.0/kotlin-stdlib-2.4.0.jar",
            sizeBytes = 1_841_929L,
            sha256 = "ccb14ff83fabcb11458b798dbc9824748ccdffeec79c9aba789e6ed1cda86b1c",
        ),
        ToolComponent(
            id = "kotlin-reflect",
            version = "2.4.0",
            fileName = "kotlin-reflect-2.4.0.jar",
            url = "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/" +
                "kotlin-reflect/2.4.0/kotlin-reflect-2.4.0.jar",
            sizeBytes = 3_750_099L,
            sha256 = "f0d8e8908624eb4478bb131e9f887366680d2e239741195e548fa5c906963b90",
        ),
        ToolComponent(
            id = "trove4j",
            version = "1.0.20200330",
            fileName = "trove4j-1.0.20200330.jar",
            url = "https://repo.maven.apache.org/maven2/org/jetbrains/intellij/deps/" +
                "trove4j/1.0.20200330/trove4j-1.0.20200330.jar",
            sizeBytes = 572_985L,
            sha256 = "c5fd725bffab51846bf3c77db1383c60aaaebfe1b7fe2f00d23fe1b7df0a439d",
        ),
        ToolComponent(
            id = "annotations",
            version = "13.0",
            fileName = "annotations-13.0.jar",
            url = "https://repo.maven.apache.org/maven2/org/jetbrains/annotations/" +
                "13.0/annotations-13.0.jar",
            sizeBytes = 17_536L,
            sha256 = "ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478",
        ),
        ToolComponent(
            id = "coroutines",
            version = "1.9.0",
            fileName = "kotlinx-coroutines-core-jvm-1.9.0.jar",
            url = "https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/" +
                "kotlinx-coroutines-core-jvm/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar",
            sizeBytes = 1_463_587L,
            sha256 = "ad89c2892235e670f222d819cb3d81188143cb19a05b59df9889ae4269f5c70a",
        ),
    )

    /** Jar ids forming the kotlinc launch classpath, in order. */
    val kotlincLaunchIds: List<String> = listOf(
        "kotlin-compiler", "kotlin-stdlib", "kotlin-reflect", "trove4j", "annotations", "coroutines",
    )

    /**
     * arm64 aapt2. Sourced as a user download (SDK license, like AndroidIDE): first
     * candidate is the AndroidIDE androidide-tools build-tools-*-aarch64 release, whose
     * license file must be shown on the setup screen; fallback is an AOSP build made
     * here with a downloaded NDK. Native binaries ship inside our APK as .so files and
     * are never executed from writable storage.
     */
    const val AAPT2_SOURCE = "https://github.com/AndroidIDEOfficial/androidide-tools/releases"
    const val AAPT2_VERSION = "34.0.4"
    const val ANDROID_JAR_API = 34

    val totalBytes: Long get() = components.sumOf { it.sizeBytes }
}

/** Setup progress for the whole toolchain, shown on the first-setup screen. */
sealed interface ToolchainState {
    data object NotStarted : ToolchainState
    data class Downloading(val doneBytes: Long, val totalBytes: Long, val current: String) : ToolchainState
    data class Verifying(val fileName: String) : ToolchainState
    data object Ready : ToolchainState
    data class Failed(val message: String) : ToolchainState
}
