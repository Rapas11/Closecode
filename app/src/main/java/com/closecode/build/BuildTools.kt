package com.closecode.build

import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Everything the build chain needs besides the project: the tool files, the library
 * cache and the signing identity. Assembled by the caller so the chain itself is pure
 * JVM code and the same code runs in the on-phone build and in the proof test.
 */
data class BuildTools(
    val aapt2: File,
    val androidJar: File,
    val stdlib: File,
    val libraries: LibraryIndex,
    val signer: SigningIdentity,
    /** The tool files by manifest id: kotlin-compiler, ecj, r8, ... */
    val toolFiles: Map<String, File>,
    /**
     * The jar handed to kotlinc's `-Xplugin`. On the phone this is the compiler's own
     * dexed jar (its service files and classes make the Compose plugin load); in a
     * desktop proof it is the original Compose compiler jar.
     */
    val composePlugin: File? = null,
    /** Where the system may cache dex it converts for the tools; null uses the JVM default. */
    val dexOptDir: File? = null,
    /** The project's minSdk: D8 desugars to this API and APK signing v1 follows it. */
    val minApi: Int = 29,
)

/** A private key and its certificate chain, from the Android Keystore or a test key. */
data class SigningIdentity(
    val key: PrivateKey,
    val chain: List<X509Certificate>,
)

/** The settings a build reads from the project and .closecode.json. */
data class BuildConfigValues(
    val packageName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val versionCode: Int,
    val versionName: String,
    val useCompose: Boolean,
)
