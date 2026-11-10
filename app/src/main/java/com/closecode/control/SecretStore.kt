package com.closecode.control

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The deployment secret vault. Values are encrypted by the Android Keystore and never appear
 * in command output, audit entries, or logs. If the Keystore is unavailable the secret lives
 * only in this process instead of being written in the clear.
 */
class SecretStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            appContext,
            FILENAME,
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    fun put(name: String, value: String): Boolean {
        val key = clean(name) ?: return false
        session[key] = value
        val store = prefs ?: return false
        return runCatching { store.edit().putString(key, value).commit() }.getOrDefault(false)
    }

    fun get(name: String): String? {
        val key = clean(name) ?: return null
        val stored = prefs?.let { runCatching { it.getString(key, null) }.getOrNull() }
        return stored ?: session[key]
    }

    fun remove(name: String): Boolean {
        val key = clean(name) ?: return false
        session.remove(key)
        prefs?.let { runCatching { it.edit().remove(key).commit() } }
        return true
    }

    fun list(): List<String> {
        val stored = prefs?.let { runCatching { it.all.keys.toList() }.getOrElse { emptyList() } }.orEmpty()
        return (stored + session.keys).distinct().sorted()
    }

    fun durable(): Boolean = prefs != null

    private fun clean(name: String): String? {
        val key = name.trim()
        return key.takeIf { it.isNotEmpty() && it.length <= 128 && key.all { ch -> ch.isLetterOrDigit() || ch in "._-" } }
    }

    private companion object {
        const val FILENAME = "closecode_secrets"
        val session = mutableMapOf<String, String>()
    }
}
