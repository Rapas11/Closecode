package com.closecode.build

/**
 * The supported set: what the on-device build can build, in one place. The project
 * check reads every project against these numbers and names the file and line when
 * something else shows up — an unsupported version is reported, never guessed at.
 *
 * The Kotlin, Compose compiler and Compose BOM versions are the ones the templates
 * write (Checked against Scaffold on 2026-10-15: they match the spec's example
 * .closecode.json, so the templates' versions are the supported set). The Compose
 * compiler version must match the Kotlin version — 1.5.14 is the compiler for 1.9.24.
 *
 * COMPILE_SDK is the platform the phone ships an android.jar for. The spec names 35;
 * the download in verse 5 must provide API 35 to match.
 */
object ProjectPins {
    const val COMPILE_SDK = 35

    /** The oldest and newest Android versions an app may ask for. */
    const val MIN_SDK_FLOOR = 24

    const val KOTLIN = "1.9.24"
    const val COMPOSE_COMPILER = "1.5.14"
    const val COMPOSE_BOM = "2024.04.01"

    /** Below this, packaging an APK is not worth starting. */
    const val FREE_SPACE_FLOOR_BYTES = 300L * 1024 * 1024

    /** The plugins the build understands. Anything else is named, not run. */
    val supportedPlugins: Set<String> = setOf(
        "com.android.application",
        "org.jetbrains.kotlin.android",
        "org.jetbrains.kotlin.plugin.compose",
    )

    /**
     * Maven modules the library cache holds, with their pinned versions. A null version
     * means the Compose BOM decides it; the BOM itself is pinned above.
     */
    val supportedLibraries: Map<String, String?> = linkedMapOf(
        "androidx.core:core-ktx" to "1.13.1",
        "androidx.activity:activity-compose" to "1.9.3",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android" to "1.9.0",
        "androidx.compose:compose-bom" to COMPOSE_BOM,
        "androidx.compose.ui:ui" to null,
        "androidx.compose.ui:ui-graphics" to null,
        "androidx.compose.ui:ui-tooling-preview" to null,
        "androidx.compose.material3:material3" to null,
        "androidx.compose.runtime:runtime" to null,
        "androidx.compose.foundation:foundation" to null,
    )

    /**
     * A library is fine when the library cache already holds that module and version, or
     * when it is one of the pinned modules at its pinned version. A module whose version
     * comes from the Compose BOM counts when a BOM is declared and the line does not pin
     * a version of its own; whether that BOM is the supported one is [ProjectCheck]'s
     * version rule, so a wrong BOM is named once, not once per member.
     */
    fun librarySupported(
        module: String,
        version: String?,
        bomDeclared: Boolean,
        cache: Set<String>,
    ): Boolean {
        if (version != null && "$module:$version" in cache) return true
        if (!supportedLibraries.containsKey(module)) return false
        val pin = supportedLibraries[module]
        if (pin != null) return version == null || version == pin
        return bomDeclared && version == null
    }
}
