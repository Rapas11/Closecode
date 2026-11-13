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
            runCatching { AndroidProjectFiles.prepare(project.directory, platformStore.rootDir()) }
            val facts = GradleReader.read(project.directory)
            val settings = ProjectSettings.read(project.directory)
            val nativeUsage = NativeProject.detect(project.directory, facts)
            val compileSdk = settings.sdk
                ?: project.savedCompileSdk
                ?: facts.compileSdk
                ?: ProjectPins.COMPILE_SDK
            val bundledAapt2 = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
                .takeIf { it.isFile }
            val downloadedAapt2 = platformStore.buildToolsAapt2()
            val aapt2 = selectAapt2(
                bundledAapt2,
                downloadedAapt2,
                File(context.filesDir, "toolchain"),
            )
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
            val wrapperVersion = GradleWrapper.version(project.directory)
            val knownWrapper = wrapperVersion
                ?.takeIf { gradleStore.manifest.forVersion(it) != null }
            val gradleVersion = settings.gradleVersion
                ?: knownWrapper
                ?: gradleStore.manifest.distribution.version
            val nativeStore = NativeToolsStore(context)
            val native = nativeStore.toolchain()
            val nativeMissing = if (nativeUsage.usesNative) nativeStore.missing() else emptyList()
            val gradlePlatform = platformStore.androidJar(compileSdk)
            val agpVersion = facts.plugins
                .firstOrNull { it.id == "com.android.application" || it.id == "com.android.library" }
                ?.version
            val requiredBuildTools = settings.buildTools
                ?: platformStore.requiredBuildToolsVersion(agpVersion)
            val pinnedBuildTools = (facts.buildToolsVersion ?: settings.buildTools)?.let { pin ->
                // A project may pin a version the app did not install; the working ARM64
                // build-tools are copied under that name so the build works here. Without
                // this, AGP downloads Google's package for that revision — and Google's
                // build-tools hold x86-64 binaries that cannot run on this phone.
                platformStore.ensureBuildToolsVersion(pin)?.let { pin to it }
            }
            val gradleBuildTools = pinnedBuildTools ?: platformStore.bestBuildTools(requiredBuildTools)
            val gradleMissing = buildList {
                if (!gradleStore.isReady(gradleVersion)) add("Gradle tools")
                if (gradlePlatform == null) add("Android $compileSdk platform")
                if (gradleBuildTools == null) {
                    add(
                        if (settings.buildTools != null) "build-tools ${settings.buildTools}"
                        else "build-tools $requiredBuildTools (or newer)",
                    )
                }
                // AGP checks both even for a Kotlin-only assembleDebug (P0-3, P0-4).
                if (!platformStore.platformToolsReady()) add("platform-tools")
                if (!platformStore.licensesReady()) add("Android SDK licenses")
                if (aapt2 == null) add("aapt2")
                if (nativeUsage.usesNative && native == null) add("Native tools")
            }
            val gradle = if (gradleMissing.isEmpty()) {
                GradleToolchain(
                    javaHome = gradleStore.javaHome,
                    gradle = gradleStore.gradleFor(gradleVersion),
                    gradleHome = gradleStore.gradleHome,
                    sdkRoot = platformStore.rootDir(),
                    platformDir = platformStore.platformDir(compileSdk),
                    buildToolsDir = gradleBuildTools!!.second,
                    buildToolsVersion = gradleBuildTools.first,
                    aapt2 = aapt2,
                    aapt2Env = gradleAapt2Env,
                    distributionVersion = gradleVersion,
                    wrapperVersion = wrapperVersion,
                    native = native,
                    signer = signer?.let { SigningIdentity(it.key, it.chain) },
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

/**
 * P0-2: AGP's `Aapt2FromMaven.create` rejects an override whose file name is not exactly
 * `aapt2`. The APK ships `libaapt2.so`, so handing that path to Gradle fails before a single
 * task runs even though the binary is a working aapt2. Prefer the downloaded build-tools
 * `aapt2`; otherwise copy the bundled one to a writable, correctly named file once and reuse it.
 */
internal fun selectAapt2(
    bundled: File?,
    downloaded: File?,
    stagingDir: File? = null,
): File? {
    if (downloaded != null && downloaded.isFile && downloaded.name == "aapt2") return downloaded
    if (bundled == null || !bundled.isFile) return downloaded?.takeIf { it.isFile }
    // Already named correctly (a test or a future pack); nothing to copy.
    if (bundled.name == "aapt2") return bundled
    val directory = stagingDir ?: bundled.parentFile ?: return null
    if (!directory.isDirectory && !directory.mkdirs()) return null
    val destination = File(directory, "aapt2")
    if (destination.isFile && destination.length() == bundled.length()) return destination
    return runCatching {
        bundled.copyTo(destination, overwrite = true)
        destination.setExecutable(true, false)
        destination
    }.getOrNull()
}
