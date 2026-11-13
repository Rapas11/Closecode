package com.closecode.build

import android.content.Context
import java.io.File

/**
 * "An install is running right now."
 *
 * A build and an install in the same SDK fight each other: the build sees a package folder
 * that is not finished yet, decides it is broken, and fetches it itself — which fails with
 * a message about the network that has nothing to do with the network.
 *
 * The lock is a file in the app's own storage. It is taken while an install runs and given
 * back when it finishes, so a build can say plainly that it cannot start yet.
 */
object InstallLock {

    private const val FILE = "install.lock"

    private fun file(context: Context): File = File(context.filesDir, FILE)

    /** True while an install is running. */
    fun isBusy(context: Context): Boolean = file(context).isFile

    /** Runs [block] with the lock held, and always gives it back. */
    fun <T> hold(context: Context, block: () -> T): T {
        val marker = file(context)
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText("${System.currentTimeMillis()}")
        }
        try {
            return block()
        } finally {
            runCatching { marker.delete() }
        }
    }

    /** The message a build gives when it cannot start. */
    fun busyMessage(): String =
        "An install is running right now, so the toolchain is not ready. " +
            "Wait for the install to finish and run the build again."
}
