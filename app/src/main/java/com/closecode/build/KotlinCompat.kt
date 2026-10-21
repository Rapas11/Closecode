package com.closecode.build

import android.content.Context
import java.io.File

/**
 * Resource sidecar for the on-device toolchain.
 *
 * The Kotlin compiler's `KotlinCoreEnvironment` finds its extension-point file by asking
 * its class loader for the `.class` resource of `KotlinCoreEnvironment$Companion` and then
 * reading `META-INF/extensions/compiler.xml` from that root. Dex has classes but no
 * `.class` resources, so the lookup throws. The sidecar carries the real `.class` bytes,
 * the compiler's extension XML, its version file and its original service registrations.
 *
 * It also carries what a desktop JVM provides but Android does not: the StAX API and
 * Woodstox (the compiler parses XML), a marker `javax.swing.Icon` (IDE-support classes
 * declare methods returning it, and the container scanner resolves those return types),
 * a minimal `javax.lang.model.SourceVersion` (ECJ probes it at class init), and ECJ's
 * message/parser resources (the dexed compiler jar holds classes only). The dexed
 * classes ride beside the dexed compiler in one DexClassLoader; the resources are found
 * through the same loader.
 */
object KotlinCompat {
    private const val ASSET = "kotlin-resources.jar"
    /** Bumped whenever the sidecar contents change, so an update never reuses an old copy. */
    private const val VERSION = 7

    fun resources(context: Context): File? {
        val target = File(context.filesDir, "kotlin-resources-v$VERSION.jar")
        if (target.isFile && target.length() > 0) return target
        return runCatching {
            context.assets.open(ASSET).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.takeIf { it.isFile }
        }.getOrNull()
    }
}
