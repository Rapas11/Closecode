package com.closecode.build

import android.content.Context
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BuildSetup(
    val checkInput: ProjectCheck.Input,
    val tools: BuildTools?,
    val missing: List<String>,
) {
    data class Project(
        val slug: String,
        val name: String,
        val directory: File,
        val id: String = slug,
        val savedPackage: String? = null,
        val savedMinSdk: Int? = null,
        val savedTargetSdk: Int? = null,
        val savedCompileSdk: Int? = null,
        val otherPackages: Set<String> = emptySet(),
    )
}

object BuildSetupFactory {

    suspend fun create(context: Context, project: BuildSetup.Project): BuildSetup =
        withContext(Dispatchers.IO) {
            val store = ToolchainHolder.store(context)
            val libraries = store.librariesDir()?.let { LibraryIndex.load(it) }
            val ids = ToolchainManifest.KOTLINC_LAUNCH_IDS + listOf("ecj", "r8")
            val toolFiles = ids.distinct()
                .mapNotNull { id -> store.jar(id)?.let { id to it } }
                .toMap()
            val platformStore = PlatformStore(
                context,
                SharedPreferencesKeyValueStore(context),
            )
            val compileSdk = project.savedCompileSdk
                ?: GradleReader.read(project.directory).compileSdk
                ?: ProjectPins.COMPILE_SDK
            val bundledAapt2 = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
                .takeIf { it.isFile }
            val downloadedAapt2 = platformStore.buildToolsAapt2()
            val aapt2 = downloadedAapt2 ?: bundledAapt2
            val androidJar = platformStore.androidJar(compileSdk) ?: store.jar("android-jar")
            val stdlib = store.jar("kotlin-stdlib")
            val signer = runCatching { SigningStore(context).identity() }.getOrNull()
            val missing = buildList {
                if (aapt2 == null) add("aapt2")
                if (androidJar == null) add("android.jar")
                if (stdlib == null) add("kotlin-stdlib")
                if (libraries == null) add("library index")
                if (signer == null) add("debug signing key")
                val absent = ids.distinct().filter { it !in toolFiles }
                if (absent.isNotEmpty()) add("tools: ${absent.joinToString()}")
            }
            val tools = if (missing.isEmpty()) {
                BuildTools(
                    aapt2 = aapt2!!,
                    androidJar = androidJar!!,
                    stdlib = stdlib!!,
                    libraries = libraries!!,
                    signer = SigningIdentity(signer!!.key, signer.chain),
                    toolFiles = toolFiles,
                    composePlugin = store.jar("kotlin-compiler"),
                    dexOptDir = File(context.codeCacheDir, "build-dex"),
                    aapt2Env = Aapt2Runtime.environment(context) +
                        downloadedAapt2?.let { downloaded ->
                            val dir = downloaded.parentFile
                            val paths = listOf(File(dir, "lib"), File(dir, "lib64"))
                                .filter { it.isDirectory }
                                .map { it.absolutePath }
                            if (paths.isEmpty()) {
                                emptyMap()
                            } else {
                                val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
                                mapOf(
                                    "LD_LIBRARY_PATH" to
                                        (paths + existing.split(':').filter { it.isNotEmpty() })
                                            .joinToString(":"),
                                )
                            }
                        }.orEmpty(),
                    kotlinResources = KotlinCompat.resources(context),
                )
            } else {
                null
            }
            BuildSetup(
                checkInput = ProjectCheck.Input(
                    savedPackage = project.savedPackage?.takeIf { it.isNotBlank() },
                    savedMinSdk = project.savedMinSdk,
                    savedTargetSdk = project.savedTargetSdk,
                    savedCompileSdk = project.savedCompileSdk,
                    otherPackages = project.otherPackages,
                    freeBytes = context.filesDir.usableSpace,
                    cacheModules = libraries?.cacheModules.orEmpty(),
                    installedCompileSdks = platformStore.installedPlatforms().toMutableSet()
                        .apply { if (store.jar("android-jar") != null) add(ProjectPins.COMPILE_SDK) },
                ),
                tools = tools,
                missing = missing,
            )
        }
}
