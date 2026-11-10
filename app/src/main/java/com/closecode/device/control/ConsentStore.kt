package com.closecode.device.control

import android.content.Context
import com.closecode.ui.settings.KeyValueStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Per-capability grants. The sensitive ones start denied, and every denial names the exact
 * command that grants them. Builds and installs are not gated here: the user already starts
 * them and Android shows its own install confirmation.
 *
 * P2-1: the agent may **ask**, never grant. `request` records the ask; only the user, through
 * the app's own permissions screen, turns it into a grant. The sensitive capabilities
 * (secrets, ui-automation, screen, shell) are never self-grantable by any route.
 */
class ConsentStore(kv: KeyValueStore) {

    /** The production store: the app's private preferences. */
    constructor(context: Context) : this(SharedPreferencesKeyValueStore(context))

    private val kv = kv

    /** One pending ask, waiting for the user. */
    data class Request(
        val id: String,
        val capability: String,
        val scope: String?,
        val createdAt: Long,
    ) {
        fun json(): JsonObject = JsonObject().apply {
            addProperty("id", id)
            addProperty("capability", capability)
            addProperty("package", scope.orEmpty())
            addProperty("createdAt", createdAt)
        }
    }

    fun isGranted(capability: String): Boolean {
        val clean = normalize(capability) ?: return false
        return kv.getBoolean("consent.$clean", clean in DEFAULT_GRANTED)
    }

    /** A grant for one package, falling back to the global capability. */
    fun isGrantedFor(capability: String, scope: String?): Boolean {
        if (isGranted(capability)) return true
        val clean = normalize(capability) ?: return false
        val key = scope?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return kv.getBoolean("consent.$clean.scope.$key", false)
    }

    /** True only for an explicit grant on this exact package. */
    fun isScopedGranted(capability: String, scope: String?): Boolean {
        val clean = normalize(capability) ?: return false
        val key = scope?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return kv.getBoolean("consent.$clean.scope.$key", false)
    }

    fun grantFor(capability: String, scope: String): Boolean {
        val clean = normalize(capability) ?: return false
        val key = scope.trim().takeIf { it.isNotEmpty() } ?: return false
        kv.putBoolean("consent.$clean.scope.$key", true)
        return true
    }

    fun revokeFor(capability: String, scope: String): Boolean {
        val clean = normalize(capability) ?: return false
        val key = scope.trim().takeIf { it.isNotEmpty() } ?: return false
        kv.putBoolean("consent.$clean.scope.$key", false)
        return true
    }

    fun scopedGrants(): Map<String, Set<String>> {
        val prefixes = CAPABILITIES.associateWith { "consent.$it.scope." }
        return prefixes.mapValues { (_, prefix) ->
            kv.keys().filter { it.startsWith(prefix) && kv.getBoolean(it, false) }
                .map { it.removePrefix(prefix) }
                .toSet()
        }
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

    /** Records the agent's ask. It grants nothing; only [resolve] can. */
    fun request(capability: String, scope: String?): Request? {
        val clean = normalize(capability) ?: return null
        val request = Request(
            id = "req-" + System.currentTimeMillis().toString(36),
            capability = clean,
            scope = scope?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis(),
        )
        kv.putString(KEY_REQUEST, request.json().toString())
        return request
    }

    /** The ask waiting for the user, if there is one. */
    fun pending(): Request? {
        val raw = kv.getString(KEY_REQUEST, null) ?: return null
        val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return null
        val id = json.get("id")?.asString ?: return null
        val capability = json.get("capability")?.asString ?: return null
        return Request(
            id = id,
            capability = capability,
            scope = json.get("package")?.asString?.takeIf { it.isNotBlank() },
            createdAt = json.get("createdAt")?.asLong ?: 0L,
        )
    }

    /**
     * The user decided. This is the only path that turns a request into a grant, and it is
     * called by the app's own permissions screen — never by the agent.
     */
    fun resolve(request: Request, approved: Boolean): Boolean {
        val clean = normalize(request.capability) ?: return false
        if (approved) {
            if (request.scope.isNullOrBlank()) grant(clean) else grantFor(clean, request.scope)
        }
        kv.remove(KEY_REQUEST)
        return true
    }

    /** The user changed a capability directly from the permissions screen. */
    fun set(capability: String, allowed: Boolean, scope: String? = null): Boolean {
        val clean = normalize(capability) ?: return false
        return if (scope.isNullOrBlank()) {
            if (allowed) grant(clean) else revoke(clean)
        } else if (allowed) {
            grantFor(clean, scope)
        } else {
            revokeFor(clean, scope)
        }
    }

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

        /** Never granted by the agent, in any form (P2-1). */
        val NEVER_SELF_GRANTED = setOf("secrets", "ui-automation", "screen", "shell")

        fun isSensitive(capability: String): Boolean {
            val clean = normalize(capability) ?: return false
            return clean in NEVER_SELF_GRANTED
        }

        fun normalize(raw: String): String? = when (raw.trim().lowercase()) {
            "ui", "ui-automation", "automation" -> "ui-automation"
            "screen", "screenshot" -> "screen"
            "file", "files", "storage" -> "files"
            "shell", "exec", "pty" -> "shell"
            "notification", "notifications", "notify" -> "notifications"
            "secret", "secrets", "vault" -> "secrets"
            "build", "install", "network" -> raw.trim().lowercase()
            else -> null
        }

        private const val KEY_REQUEST = "consent.request"
    }
}
