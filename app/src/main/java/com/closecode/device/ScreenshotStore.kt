package com.closecode.device

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshots are private, short-lived bridge output.
 *
 * They used to be written into `build/screenshots` inside the project, where the explorer and the
 * next commit could see them. They now live in the app's cache, are never listed in the project
 * tree, and are removed when OpenCode stops, when the app starts, or after ten minutes.
 */
object ScreenshotStore {

    const val DIRECTORY = "screenshots"
    const val MAX_AGE_MS = 10L * 60L * 1_000L

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun directory(context: Context): File =
        File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    fun newFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var file = File(directory(context), "screenshot-$stamp.png")
        var suffix = 1
        while (file.exists()) {
            file = File(directory(context), "screenshot-$stamp-${suffix++}.png")
        }
        scheduleCleanup(context)
        return file
    }

    /** Deletes every capture, for app start and for a server that has stopped. */
    fun clear(context: Context) {
        runCatching {
            directory(context).listFiles()?.forEach { it.delete() }
        }
    }

    /** Deletes captures older than [MAX_AGE_MS]; call on start and after a server stops. */
    fun cleanup(context: Context, now: Long = System.currentTimeMillis()) {
        runCatching {
            directory(context).listFiles()?.forEach { file ->
                if (now - file.lastModified() >= MAX_AGE_MS) file.delete()
            }
        }
    }

    private fun scheduleCleanup(context: Context) {
        val app = context.applicationContext
        handler.postDelayed({ cleanup(app) }, MAX_AGE_MS)
    }
}
