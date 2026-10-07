package com.closecode.ui.github

import android.content.Context
import android.content.SharedPreferences

/**
 * Where the GitHub access token lives between launches.
 *
 * It goes into this app's private preferences, which Android sandboxes to the app's own data
 * directory, and `android:allowBackup="false"` stops that directory being copied off the device
 * by a backup. The token is never written to a log, an exception message or a repository.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    fun read(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }

    fun write(token: String) {
        prefs.edit().putString(KEY, token).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val FILENAME = "closecode_github"
        const val KEY = "access_token"
    }
}
