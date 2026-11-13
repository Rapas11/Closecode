package com.closecode.build

import android.content.Context
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import java.io.File

/**
 * The `aapt2` this phone should use.
 *
 * The APK ships an `aapt2` as a native library, and that build is older than the platform
 * resources the app installs: it cannot read an Android 15 resource table
 * ("illegal map type 'string'"). The SDK's own ARM64 `aapt2` — the one every Gradle build
 * already uses through `android.aapt2FromMavenOverride` — reads them without trouble.
 *
 * So there is one answer for the whole app: the SDK's `aapt2` when it is installed, and the
 * bundled one only as a fallback.
 */
object Aapt2Picker {

    /** The `aapt2` to use, or null when neither is available. */
    fun pick(
        context: Context,
        staging: File = File(context.filesDir, "toolchain/aapt2"),
    ): File? {
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        val downloaded = platform.buildToolsAapt2()
        val bundled = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
        return selectAapt2(bundled, downloaded, staging)
    }

    /** True when the given `aapt2` can read the platform resources the app installs. */
    fun readsPlatformResources(aapt2: File?, platformJar: File?): Boolean {
        if (aapt2 == null || platformJar == null) return false
        if (!aapt2.isFile || !platformJar.isFile) return false
        return runCatching {
            val process = ProcessBuilder(aapt2.absolutePath, "dump", "badging", platformJar.absolutePath)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            text.contains("package: name='android'")
        }.getOrDefault(false)
    }
}
