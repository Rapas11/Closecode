package com.closecode.build

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Milestone (b) install path, standard APIs, minSdk 29 OK. The caller streams the
 * signed APK into a full-install session; the system shows its own consent screen
 * (Android 12+: a one-time "allow this source" redirect via REQUEST_INSTALL_PACKAGES),
 * then reports back on [action]. Nothing here can silently install: no session, no app.
 */
object InstallHelper {
    const val ACTION_INSTALL_STATUS = "com.closecode.build.INSTALL_STATUS"

    /**
     * Streams [apk] into a PackageInstaller session and commits it. Returns the
     * session id, or -1 when the caller lacks install permission (prompt the user
     * to allow this source first).
     */
    fun install(
        context: Context,
        apk: File,
        packageName: String = "",
        action: String = ACTION_INSTALL_STATUS,
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return -1
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (packageName.isNotBlank()) params.setAppPackageName(packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("package", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(action).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(status.intentSender)
        }
        return sessionId
    }

    /** Launch intent for an installed package, or null when it is not there. */
    fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)
}
