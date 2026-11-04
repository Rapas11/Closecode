package com.closecode.ui.github

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.closecode.debug.DebugLog

/**
 * Where the GitHub access token lives between launches.
 *
 * Keystore-backed encrypted preferences, and nothing else: the token is encrypted at rest, and
 * `android:allowBackup="false"` stops the directory being copied off the device by a backup.
 * The token is never written to a log, an exception message or a repository.
 *
 * When the Keystore is broken there is no durable sign-in, and the app does not fake one with a
 * plain-text copy: a read answers null — signed out, so the user is asked to sign in again — and
 * a write answers false, so a fresh sign-in is good for this session but is not remembered. A
 * token an older version left in plain preferences is wiped, never adopted into a broken store.
 */
class TokenStore internal constructor(
    private val secure: TokenFile?,
    private val legacy: TokenFile,
) {

    constructor(context: Context) : this(
        secure = runCatching { EncryptedTokenFile(context.applicationContext) }
            .onFailure { DebugLog.crumb("GITHUB", "secure storage unavailable · ${it.javaClass.simpleName}") }
            .getOrNull(),
        legacy = LegacyTokenFile(context.applicationContext),
    )

    /**
     * The stored token, or null. A token an older version left in plain preferences is moved into
     * the encrypted store on first read and wiped from the old file; with a broken store the old
     * file is wiped instead, because the thing that keeps a session must never be a plain copy.
     */
    fun read(): String? {
        val secureStore = secure
        if (secureStore == null) {
            if (legacy.read() != null) {
                legacy.clear()
                DebugLog.crumb("GITHUB", "plain token wiped · no secure storage")
            }
            return sessionToken
        }
        runCatching { secureStore.read() }.getOrNull()?.let { return it }
        // An older version's token, from back when the store fell back to plain text.
        val leftover = legacy.read()
        if (leftover != null) {
            runCatching { secureStore.write(leftover) }
            legacy.clear()
            return leftover
        }
        return sessionToken
    }

    /**
     * Stores the token. False means this phone cannot store it securely: the sign-in is good for
     * this session, nothing is written in the clear, and the next launch will ask again.
     */
    fun write(token: String): Boolean {
        // The session copy is the source of truth for the running app. A phone whose Keystore
        // is broken still keeps the sign-in until the process dies, instead of telling every
        // other screen that GitHub is not connected while this one says it is.
        sessionToken = token
        val secureStore = secure
        if (secureStore == null) {
            legacy.clear()
            DebugLog.crumb("GITHUB", "token not stored · no secure storage")
            return false
        }
        val kept = runCatching { secureStore.write(token) }.isSuccess
        legacy.clear()
        // A fact for the debug log's GITHUB area — the token itself never gets near it.
        DebugLog.crumb("GITHUB", if (kept) "token saved (hidden)" else "token kept for this session only")
        return kept
    }

    fun clear() {
        sessionToken = null
        secure?.clear()
        legacy.clear()
        DebugLog.crumb("GITHUB", "token cleared")
    }
}

/**
 * The token for this process. Every TokenStore shares it, so signing in on one screen is visible
 * on the next even when the Keystore cannot persist it. It dies with the process and is never
 * written anywhere except the encrypted store.
 */
@Volatile
private var sessionToken: String? = null

/** Test hook: the session normally lives as long as the process. */
internal fun resetTokenSessionForTest() {
    sessionToken = null
}

/** One file a token can sit in. [TokenStore]'s secure side is null when the Keystore is broken. */
internal interface TokenFile {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

/** EncryptedSharedPreferences, keyed by the Android Keystore. Construction throws when it broke. */
internal class EncryptedTokenFile(context: Context) : TokenFile {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILENAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }
    override fun write(token: String) { prefs.edit().putString(KEY, token).apply() }
    override fun clear() { prefs.edit().remove(KEY).apply() }
}

/** The plain preferences of an older version, under the same file name: adopted once, or wiped. */
internal class LegacyTokenFile(context: Context) : TokenFile {

    private val prefs = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }
    override fun write(token: String) { prefs.edit().putString(KEY, token).apply() }
    override fun clear() { prefs.edit().remove(KEY).apply() }
}

private const val FILENAME = "closecode_github"
private const val KEY = "access_token"
