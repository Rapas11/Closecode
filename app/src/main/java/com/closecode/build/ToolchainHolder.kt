package com.closecode.build

import android.content.Context
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import java.io.File

/**
 * One [ToolchainStore] for the whole app. The setup screen draws it, the download service
 * runs it, and the build panel asks it whether tools are ready — all looking at the same
 * state, so leaving the screen never lies about what is happening.
 *
 * Packs are stored under `files/toolchain/<pack version>/`, so an app update that changes
 * the pack can download the new version beside the old one and switch only when it verified.
 */
object ToolchainHolder {
    @Volatile
    private var store: ToolchainStore? = null

    fun store(context: Context): ToolchainStore {
        store?.let { return it }
        synchronized(this) {
            store?.let { return it }
            val app = context.applicationContext
            val toolchain = ToolchainManifest.load(app)
            return ToolchainStore(
                toolchain,
                File(app.filesDir, "toolchain/${toolchain.version}"),
                SharedPreferencesKeyValueStore(app),
                AndroidNetworkGate(app),
            ).also { store = it }
        }
    }

    /** Delete build tools: this pack and every older pack version beside it. */
    fun deleteEverything(context: Context): Boolean {
        val app = context.applicationContext
        val removed = store(app).deleteAll()
        runCatching {
            File(app.filesDir, "toolchain").listFiles()?.forEach { it.deleteRecursively() }
        }
        return removed
    }
}
