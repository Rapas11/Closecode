package com.closecode.device.control

import android.content.Context
import com.closecode.ui.settings.SharedPreferencesKeyValueStore

/**
 * Per-capability grants. The sensitive ones start denied, and every denial names the exact
 * command that grants them. Builds and installs are not gated here: the user already starts
 * them and Android shows its own install confirmation.
 */
class ConsentStore(context: Context) {

    private val kv = SharedPreferencesKeyValueStore(context)

    fun isGranted(capability: String): Boolean {
        val clean = normalize(capability) ?: return false
        return kv.getBoolean("consent.$clean", clean in DEFAULT_GRANTED)
    }

    fun grant(capability: String): Boolean {
        val clean = normalize(capability) ?: return false
        kv.putBoolean("consent.$clean", true)
        return true
    }

    fun revoke(capability: String): Boolean {
        val clean = normalize(capability) ?: return false
        kv.putBoolean("consent.$clean", false)
        return true
    }

    fun grants(): Map<String, Boolean> = CAPABILITIES.associateWith { isGranted(it) }

    companion object {
        val CAPABILITIES = listOf(
            "build",
            "install",
            "ui-automation",
            "screen",
            "files",
            "network",
            "shell",
            "secrets",
            "notifications",
        )
        private val DEFAULT_GRANTED = setOf("build", "install", "network")

        fun normalize(raw: String): String? = when (raw.trim().lowercase()) {
            "ui", "ui-automation", "automation" -> "ui-automation"
            "screen", "screenshot" -> "screen"
            "file", "files", "storage" -> "files"
            "shell", "exec", "pty" -> "shell"
            "notification", "notifications" -> "notifications"
            "secret", "secrets", "vault" -> "secrets"
            "build", "install", "network" -> raw.trim().lowercase()
            else -> null
        }
    }
}