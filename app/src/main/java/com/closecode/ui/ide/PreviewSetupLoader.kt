package com.closecode.ui.ide

import android.content.Context
import com.closecode.build.BuildConfigValues
import com.closecode.build.BuildTools
import com.closecode.build.DebugKey
import com.closecode.build.GradleReader
import com.closecode.build.LibraryIndex
import com.closecode.build.SigningIdentity
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainManifest
import java.io.File

/**
 * Collects the same toolchain the Build panel uses, once, when Preview opens. It is blocking
 * and belongs on an IO dispatcher. A missing piece returns null rather than a partial setup:
 * the panel then says previews need the build tools, which is true and actionable.
 */
internal fun loadPreviewSetup(
    context: Context,
    projectRoot: File,
    packageName: String,
    savedMinSdk: Int?,
    savedTargetSdk: Int?,
): PreviewSetup? {
    val store = ToolchainHolder.store(context)
    val libraries = store.librariesDir()?.let { LibraryIndex.load(it) } ?: return null
    val ids = ToolchainManifest.KOTLINC_LAUNCH_IDS + listOf("ecj", "r8")
    val toolFiles = ids.distinct()
        .mapNotNull { id -> store.jar(id)?.let { id to it } }
        .toMap()
    val aapt2 = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
        .takeIf { it.isFile } ?: return null
    val androidJar = store.jar("android-jar") ?: return null
    val stdlib = store.jar("kotlin-stdlib") ?: return null
    if (toolFiles.size != ids.distinct().size) return null
    val signer = runCatching { DebugKey.ensure(context) }.getOrNull() ?: return null
    val tools = BuildTools(
        aapt2 = aapt2,
        androidJar = androidJar,
        stdlib = stdlib,
        libraries = libraries,
        signer = SigningIdentity(signer.key, signer.chain),
        toolFiles = toolFiles,
        composePlugin = store.jar("kotlin-compiler"),
        dexOptDir = File(context.codeCacheDir, "preview-build-dex"),
    )
    val facts = GradleReader.read(projectRoot)
    val useCompose = facts.composeBom != null ||
        facts.composeCompiler != null ||
        facts.libraries.any { it.module.startsWith("androidx.compose") }
    return PreviewSetup(
        projectRoot = projectRoot,
        cacheDir = File(context.cacheDir, "previews"),
        config = BuildConfigValues(
            packageName = packageName,
            minSdk = savedMinSdk ?: facts.minSdk ?: 29,
            targetSdk = savedTargetSdk ?: facts.targetSdk ?: 35,
            versionCode = facts.versionCode ?: 1,
            versionName = facts.versionName ?: "1.0",
            useCompose = useCompose,
        ),
        tools = tools,
    )
}
