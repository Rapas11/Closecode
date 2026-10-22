package com.closecode.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.closecode.debug.DebugLog
import java.security.SecureRandom

/**
 * The password the OpenCode server is protected by.
 *
 * The server listens on the phone's own loopback, but "on the phone's loopback" is not the same
 * as "only this app": another app on the phone can reach 127.0.0.1 too. The password is what
 * stops it. It is a random string, never written to a log or to Build Info, and it lives in
 * Keystore-backed encrypted storage.
 *
 * When the Keystore is broken there is no durable secret: the password is made fresh for this
 * session and the app says so, rather than writing a copy in the clear.
 */
class OpenCodeSecret internal constructor(
    private val secure: SecureFile?,
    private val legacy: LegacySecretFile,
) {

    constructor(context: Context) : this(
        secure = runCatching { EncryptedSecretFile(context.applicationContext) }.getOrNull(),
        legacy = LegacySecretFile(context.applicationContext),
    )

    /** The password for this phone, made once and kept. */
    fun read(): String {
        val store = secure
        if (store == null) return fresh()
        store.read()?.let { return it }
        val fresh = fresh()
        store.write(fresh)
        return fresh
    }

    /** True when the password can be kept between launches. */
    fun isDurable(): Boolean = secure != null

    private fun fresh(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val text = bytes.joinToString("") { "%02x".format(it) }
        DebugLog.crumb("AICO", "server password made")
        return text
    }

    interface SecureFile {
        fun read(): String?
        fun write(value: String)
    }

    internal class EncryptedSecretFile(context: Context) : SecureFile {
        private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
            context,
            FILENAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        override fun read(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }
        override fun write(value: String) { prefs.edit().putString(KEY, value).apply() }
    }

    internal class LegacySecretFile(context: Context) : SecureFile {
        private val prefs = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

        override fun read(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }
        override fun write(value: String) { prefs.edit().putString(KEY, value).apply() }
    }

    companion object {
        const val FILENAME = "closecode_opencode"
        const val KEY = "server_password"
    }
}
