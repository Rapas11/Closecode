package com.closecode.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/**
 * The default OpenCode V2 configuration the app writes for the phone.
 *
 * OpenCode reads its configuration from `$XDG_CONFIG_HOME/opencode/opencode.json`, and the app
 * grounds that directory itself, so the file lives where the app can write and nothing on the
 * phone's storage is touched. The file is written once, only when it is missing: anything the
 * user or the AI settings page wrote before stays untouched.
 *
 * V2 uses a `permissions` ruleset instead of V1's `permission` map. The wildcard lets the agent
 * work without a permission dialog on a phone; folders outside the app and environment files are
 * still asked about. The V1 file is backed up beside it before it is rewritten.
 *
 * Web search is not in the file. The server is started with the hosted providers' environment
 * flag instead, which turns the `websearch`/`webfetch` tools on without an API key.
 */
object OpenCodeConfig {

    /** Turns OpenCode's hosted `websearch`/`webfetch` tools on for the server. */
    const val WEB_SEARCH_FLAG = "OPENCODE_ENABLE_EXA"

    /** The second hosted provider, kept as the app's own fallback for web search. */
    const val WEB_SEARCH_FALLBACK_FLAG = "OPENCODE_ENABLE_PARALLEL"

    fun directory(configHome: File): File = File(configHome, "opencode")

    fun file(configHome: File): File = File(directory(configHome), "opencode.json")

    /** Files that usually hold credentials, so reading or editing them asks first. */
    private val SECRET_PATTERNS = listOf(
        "**/*.pem",
        "**/*.key",
        "**/*.jks",
        "**/*.keystore",
        "**/*.p12",
        "**/id_rsa",
        "**/id_ed25519",
        "**/.ssh/*",
        "**/.gnupg/*",
        "**/credentials*",
        "**/secrets*",
    )

    /** The V2 default permissions, in the order the host POC accepted. */
    fun defaultPermissions(): JsonArray = JsonArray().apply {
        add(rule("*", "*", "allow"))
        add(rule("external_directory", "*", "ask"))
        add(rule("read", "*.env", "ask"))
        add(rule("read", "*.env.*", "ask"))
        add(rule("read", "**/.env", "ask"))
        add(rule("read", "**/.env.*", "ask"))
        add(rule("read", "**/.env.example", "allow"))
        for (pattern in SECRET_PATTERNS) {
            add(rule("read", pattern, "ask"))
            add(rule("edit", pattern, "ask"))
        }
        // Destructive or privileged shell work asks first. Routine project commands keep
        // running without a tap, which is what makes the phone usable.
        add(rule("shell", "rm *", "ask"))
        add(rule("shell", "rmdir *", "ask"))
        add(rule("shell", "shred *", "ask"))
        add(rule("shell", "truncate *", "ask"))
        add(rule("shell", "git clean *", "ask"))
        add(rule("shell", "git reset *", "ask"))
        add(rule("shell", "git push *", "ask"))
        add(rule("shell", "find * -delete*", "ask"))
        add(rule("shell", "chmod *", "ask"))
        add(rule("shell", "chown *", "ask"))
        add(rule("shell", "dd *", "ask"))
        add(rule("shell", "mkfs*", "ask"))
        add(rule("shell", "kill *", "ask"))
        add(rule("shell", "pkill *", "ask"))
        add(rule("shell", "killall *", "ask"))
        add(rule("shell", "sudo *", "deny"))
        add(rule("shell", "su *", "deny"))
        add(rule("shell", "reboot *", "deny"))
        add(rule("shell", "shutdown *", "deny"))
    }

    /** The default file as OpenCode V2 accepts it. */
    fun defaultJson(): String = JsonObject().apply {
        addProperty("\$schema", "https://opencode.ai/config.json")
        addProperty("update", "disable")
        add("permissions", defaultPermissions())
    }.toString() + "\n"

    /**
     * Writes the default file when it is missing, and upgrades a V1 file in place.
     *
     * A V1 file is copied to `opencode.v1-backup.json` first. Its model and every key the app
     * does not own are kept; only the V1 `permission` map is replaced with V2 `permissions`.
     */
    fun installOnce(configHome: File): File? {
        val file = file(configHome)
        if (file.isFile) {
            migrateV1(configHome, file)
            return file
        }
        if (!ensureDirectory(directory(configHome))) return null
        return runCatching {
            file.writeText(defaultJson())
            file
        }.getOrNull()
    }

    /**
     * Fills the V2 permission rules into a config that predates them.
     *
     * Config files written before the hands-free build have no permissions at all, and OpenCode
     * would then ask the user to confirm everything — on a phone where the user only talks,
     * those asks are dead ends. This adds the default rules once and stamps it done, keeping
     * every other key; after the stamp, the AI settings page owns the rules and this never
     * touches them again.
     */
    fun ensureOpenToTheAgent(configHome: File): Boolean {
        val file = file(configHome)
        if (!file.isFile) return true
        migrateV1(configHome, file)
        if (stamp(configHome).isFile) return true
        val upgraded = ensurePermissions(file)
        return if (upgraded) {
            runCatching { stamp(configHome).createNewFile() }.isSuccess
        } else {
            false
        }
    }

    /** The one-time marker saying the permission upgrade ran, and owned by the user since. */
    fun stamp(configHome: File): File = File(directory(configHome), ".closecode-permissions")

    private fun migrateV1(configHome: File, file: File): Boolean {
        val json = OpenCodeAiConfig.read(file) ?: return false
        if (!json.has("permission")) return true
        val backup = File(directory(configHome), "opencode.v1-backup.json")
        if (!backup.isFile) runCatching { file.copyTo(backup, overwrite = false) }
        val v1 = json.get("permission") as? JsonObject
        json.remove("permission")
        val rules = json.getAsJsonArray("permissions") ?: defaultPermissions().also {
            json.add("permissions", it)
        }
        v1?.entrySet()?.forEach { (key, value) ->
            val action = v1Action(key) ?: return@forEach
            val effect = runCatching { value.asString }.getOrNull()
                ?.takeIf { it in OpenCodeAiConfig.ACTIONS }
                ?: return@forEach
            rules.add(rule(action, "*", effect))
        }
        if (!json.has("update")) json.addProperty("update", "disable")
        if (!json.has("\$schema")) json.addProperty("\$schema", "https://opencode.ai/config.json")
        return OpenCodeAiConfig.save(file, json)
    }

    /** V1's tool names mapped onto V2's actions; `doom_loop` has no V2 action. */
    private fun v1Action(key: String): String? = when (key) {
        "bash" -> "shell"
        "task" -> "subagent"
        "patch" -> "edit"
        "read", "edit", "glob", "grep", "shell", "subagent", "skill", "webfetch",
        "websearch", "external_directory" -> key
        else -> null
    }

    private fun ensurePermissions(file: File): Boolean {
        val json = OpenCodeAiConfig.read(file) ?: return false
        if (json.has("permissions")) return true
        json.add("permissions", defaultPermissions())
        if (!json.has("update")) json.addProperty("update", "disable")
        if (!json.has("\$schema")) json.addProperty("\$schema", "https://opencode.ai/config.json")
        return OpenCodeAiConfig.save(file, json)
    }

    private fun rule(action: String, resource: String, effect: String): JsonObject =
        JsonObject().apply {
            addProperty("action", action)
            addProperty("resource", resource)
            addProperty("effect", effect)
        }

    private fun ensureDirectory(directory: File): Boolean =
        directory.isDirectory || directory.mkdirs()
}
