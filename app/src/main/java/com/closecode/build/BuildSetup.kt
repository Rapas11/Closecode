package com.closecode.build

import android.content.Context
import com.closecode.tools.GradleToolsStore
import com.closecode.tools.NativeToolsStore
import com.closecode.tools.NativeToolchain
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BuildSetup(
    val checkInput: ProjectCheck.Input,
    val tools: BuildTools?,
    val missing: List<String>,
    val gradle: GradleToolchain? = null,
    val gradleMissing: List<String> = emptyList(),
    val native: NativeToolchain? = null,
    val nativeMissing: List<String> = emptyList(),
    val usesNative: Boolean = false,
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
            val facts = GradleReader.read(project.directory)
            val nativeUsage = NativeProject.detect(project.directory, facts)
            val compileSdk = project.savedCompileSdk
                ?: facts.compileSdk
                ?: ProjectPins.COMPILE_SDK
            val bundledAapt2 = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
                .takeIf { it.isFile }
            val downloadedAapt2 = platformStore.buildToolsAapt2()
            val aapt2 = selectAapt2(bundledAapt2, downloadedAapt2)
            val androidJar = platformStore.androidJar(compileSdk) ?: store.jar("android-jar")
            val stdlib = store.jar("kotlin-stdlib")
            val signer = runCatching { SigningStore(context).identity() }.getOrNull()
            val aapt2Env = Aapt2Runtime.environment(context).toMutableMap().apply {
                downloadedAapt2?.let { downloaded ->
                    val dir = downloaded.parentFile
                    val paths = listOf(File(dir, "lib"), File(dir, "lib64"))
                        .filter { it.isDirectory }
                        .map { it.absolutePath }
                    if (paths.isNotEmpty()) {
                        val base = this["LD_LIBRARY_PATH"].orEmpty()
                            .split(':')
                            .filter { it.isNotEmpty() }
                        val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
                            .split(':')
                            .filter { it.isNotEmpty() }
                        this["LD_LIBRARY_PATH"] = (base + paths + existing)
                            .distinct()
                            .joinToString(":")
                    }
                }
            }
            val supportLib = File(context.filesDir, "support").absolutePath
            val gradleAapt2Env = aapt2Env.mapValues { (key, value) ->
                if (key != "LD_LIBRARY_PATH") {
                    value
                } else {
                    value.split(':')
                        .filter { it.isNotEmpty() && it != supportLib }
                        .joinToString(":")
                }
            }
            val gradleStore = GradleToolsStore(context)
            val nativeStore = NativeToolsStore(context)
            val native = nativeStore.toolchain()
            val nativeMissing = if (nativeUsage.usesNative) nativeStore.missing() else emptyList()
            val gradlePlatform = platformStore.androidJar(compileSdk)
            val gradleBuildTools = platformStore.buildToolsDir()
            val gradleMissing = buildList {
                if (!gradleStore.isReady()) add("Gradle tools")
                if (gradlePlatform == null) add("Android $compileSdk platform")
                if (gradleBuildTools == null) add("build-tools ${platformStore.buildToolsVersion()}")
                if (aapt2 == null) add("aapt2")
                if (nativeUsage.usesNative && native == null) add("Native tools")
            }
            val gradle = if (gradleMissing.isEmpty()) {
                GradleToolchain(
                    javaHome = gradleStore.javaHome,
                    gradle = gradleStore.gradle,
                    gradleHome = gradleStore.gradleHome,
                    sdkRoot = platformStore.rootDir(),
                    platformDir = platformStore.platformDir(compileSdk),
                    buildToolsDir = gradleBuildTools!!,
                    buildToolsVersion = platformStore.buildToolsVersion(),
                    aapt2 = aapt2,
                    aapt2Env = gradleAapt2Env,
                    distributionVersion = gradleStore.manifest.distribution.version,
                    native = native,
                    ndkVersionOverride = native
                        ?.takeIf { nativeUsage.requestedNdkVersion == null }
                        ?.ndkVersion,
                )
            } else {
                null
            }
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
                    aapt2Env = aapt2Env,
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
                    nativeToolsReady = native != null,
                ),
                tools = tools,
                missing = missing,
                gradle = gradle,
                gradleMissing = gradleMissing,
                native = native,
                nativeMissing = nativeMissing,
                usesNative = nativeUsage.usesNative,
            )
        }
}

internal fun selectAapt2(bundled: File?, downloaded: File?): File? = bundled ?: downloaded
