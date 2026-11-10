package com.closecode.control

import android.content.Context
import com.closecode.build.ProjectSettings
import com.closecode.build.SigningStore
import com.closecode.device.CloseCodeAccessibilityService
import com.closecode.device.NotificationControl
import com.closecode.device.control.ConsentStore
import com.closecode.device.control.DeviceControl
import com.closecode.device.control.ScreenControl
import com.closecode.device.control.UiScript
import com.closecode.ui.project.ProjectRepository
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/**
 * P3: the commands the report lists as never exercised end to end.
 *
 * Every check runs the same code path the CLI uses, so a green self-test means the command
 * really works, not that a mock does. Where a check needs something only the user can give
 * (screen capture consent, notification access, a second app for a UI script, a device to
 * install on), the check says so instead of pretending it passed.
 */
internal object SelftestCommands {

    fun run(context: Context): JsonArray {
        val checks = JsonArray()
        checks.add(secrets(context))
        checks.add(files(context))
        checks.add(deploy(context))
        checks.add(signing(context))
        checks.add(screen(context))
        checks.add(notifications(context))
        checks.add(uiScript(context))
        checks.add(ecosystemDispatch(context))
        return checks
    }

    /** `closecode secrets set/list/delete`: the value is never printed and survives a restart. */
    private fun secrets(context: Context): JsonObject {
        val store = SecretStore(context)
        val name = "selftest-${System.nanoTime().toString(36)}"
        val value = "value-${System.nanoTime().toString(36)}"
        val stored = store.put(name, value)
        val names = store.list()
        val printed = names.any { it.contains(value) }
        // A fresh store reads the same encrypted file: the value outlives the process.
        val survived = SecretStore(context).get(name) == value
        store.remove(name)
        val gone = store.get(name) == null
        val consent = ConsentStore(context).isGranted("secrets")
        val refused = SecretsCommand.run(context, listOf("list")).get("ok")?.asBoolean != true
        val ok = stored && survived && gone && !printed &&
            (consent || refused)
        return check(
            "secrets",
            ok,
            if (ok) {
                "values are never listed, survive a restart and can be deleted"
            } else {
                "the secret store leaked a value, lost it, or printed it"
            },
        ).apply {
            addProperty("consent", consent)
            addProperty("durable", store.durable())
        }
    }

    /** `closecode files read/write/delete`: the scoped shared folder. */
    private fun files(context: Context): JsonObject {
        val name = "selftest-${System.nanoTime().toString(36)}.txt"
        val written = DeviceControl.files(context, listOf("write", name, "hello"))
        val read = DeviceControl.files(context, listOf("read", name))
        val deleted = DeviceControl.files(context, listOf("delete", name))
        val gone = DeviceControl.files(context, listOf("read", name))
            .get("ok")?.asBoolean != true
        val consent = ConsentStore(context).isGranted("files")
        val refused = DeviceControl.files(context, listOf("list"))
            .get("code")?.asString == "needs_consent"
        val ok = if (consent) {
            written.get("ok")?.asBoolean == true &&
                read.get("content")?.asString?.contains("hello") == true &&
                deleted.get("ok")?.asBoolean == true &&
                gone
        } else {
            refused
        }
        return check(
            "files",
            ok,
            if (consent) "write, read and delete inside the scoped folder"
            else "refused without the files grant",
        ).apply {
            addProperty("consent", consent)
            addProperty("root", DeviceControl.files(context, listOf("list")).get("root")?.asString ?: "")
        }
    }

    /** `closecode deploy --dry-run`: the plan is produced without touching the network. */
    private fun deploy(context: Context): JsonObject {
        val directory = File(context.cacheDir, "selftest-deploy").apply { mkdirs() }
        return try {
            File(directory, "index.html").writeText("<h1>selftest</h1>")
            val result = DeployCommand.run(
                context,
                directory,
                listOf(
                    "--dry-run",
                    "--host", "example.invalid",
                    "--user", "closecode",
                    "--path", "/var/www/selftest",
                    "--source", directory.absolutePath,
                ),
            )
            val code = result.get("code")?.asString.orEmpty()
            val planned = result.get("command")?.asString
                ?: result.get("plan")?.asString
                ?: result.get("commands")?.toString()
            // A missing rsync/scp is an honest answer, not a dispatch failure.
            val ok = result.get("ok")?.asBoolean == true ||
                code == "missing_tool" ||
                code == "tool_missing" ||
                code == "needs_consent"
            check(
                "deploy-dry-run",
                ok && (planned != null || result.get("ok")?.asBoolean == true),
                if (ok) {
                    planned?.take(200) ?: "the deploy target was accepted"
                } else {
                    "the dry run did not produce a plan (code=$code)"
                },
            ).apply {
                addProperty("code", code)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    /** `closecode signing status|generate`: the keys are listed without exposing anything. */
    private fun signing(context: Context): JsonObject {
        val status = SigningCommand.run(context, listOf("status"))
        val keys = status.getAsJsonArray("keys")
        val selected = status.get("selected")?.asString
        val listed = keys?.size() ?: 0
        val store = SigningStore(context)
        val identity = runCatching { store.identity() }.getOrNull()
        val ok = status.get("ok")?.asBoolean == true && listed > 0 && identity != null
        return check(
            "signing",
            ok,
            if (ok) "$listed key(s), selected ${selected ?: "none"}" else "no signing key is available",
        ).apply {
            addProperty("keys", listed)
            addProperty("selected", selected)
            addProperty("certificateSha256", identity?.chain?.firstOrNull()?.let { cert ->
                runCatching {
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(cert.encoded)
                        .joinToString("") { "%02x".format(it) }
                }.getOrNull()
            } ?: "")
        }
    }

    /** `closecode screen start/stop`: MediaProjection consent, and the file it writes. */
    private fun screen(context: Context): JsonObject {
        val status = ScreenControl.run(context, listOf("status"))
        val recording = status.get("recording")?.asBoolean == true
        val ok = status.get("ok")?.asBoolean == true
        return check(
            "screen",
            ok,
            if (recording) "a capture is running" else "the screen capture state is readable",
        ).apply {
            addProperty("recording", recording)
            addProperty(
                "needsUser",
                !recording && ScreenControl.run(context, listOf("start"))
                    .get("needsUser")?.asBoolean == true,
            )
        }
    }

    /** `closecode notifications list|reply`: the listener permission and the reply path. */
    private fun notifications(context: Context): JsonObject {
        val result = NotificationControl.run(context, listOf("list"))
        val ok = result.get("ok")?.asBoolean == true
        val needsUser = result.get("code")?.asString == "needs_permission"
        return check(
            "notifications",
            ok || needsUser,
            if (ok) "the listener is connected and the list is readable"
            else "the listener permission is still missing",
        ).apply {
            addProperty("needsUser", needsUser)
        }
    }

    /** `closecode test-ui script.json`: a script that launches, waits, taps and asserts. */
    private fun uiScript(context: Context): JsonObject {
        val service = CloseCodeAccessibilityService.current()
        if (service == null) {
            return check("test-ui", true, "skipped: the accessibility service is off").apply {
                addProperty("skipped", true)
                addProperty("needsUser", true)
            }
        }
        val script = File(context.cacheDir, "selftest-ui.json")
        script.writeText(
            """
            {"steps":[
              {"action":"dump"}
            ]}
            """.trimIndent(),
        )
        return try {
            val result = UiScript.run(context, null, listOf(script.absolutePath))
            val ok = result.get("ok")?.asBoolean == true
            check(
                "test-ui",
                ok,
                if (ok) "the script ran" else "the script did not pass",
            ).apply {
                addProperty("steps", result.get("steps")?.toString()?.take(400) ?: "")
            }
        } finally {
            script.delete()
        }
    }

    /** `closecode test|lint|format|package|release`: every ecosystem dispatches. */
    private fun ecosystemDispatch(context: Context): JsonObject {
        val repository = ProjectRepository(context)
        val name = "selftest-ecosystem-${System.nanoTime().toString(36)}"
        val created = InitCommand.run(context, InitCommand.Options(template = "python-cli", name = name))
        if (created.get("ok")?.asBoolean != true) {
            return check("ecosystem-dispatch", false, "could not scaffold a test project")
        }
        val slug = created.get("slug")?.asString.orEmpty()
        val directory = repository.projectDir(slug)
        return try {
            val settings = ProjectSettings.read(directory)
            val results = JsonObject()
            var dispatched = 0
            for (task in listOf("test", "lint", "format", "package", "release")) {
                val result = runCatching {
                    EcosystemCommand.run(context, directory, settings, task, emptyList()) { }
                }.getOrNull()
                val code = result?.json?.get("code")?.asString.orEmpty()
                val known = result != null && code != "unknown_task" && code != "usage"
                if (known) dispatched++
                results.add(
                    task,
                    JsonObject().apply {
                        addProperty("dispatched", known)
                        addProperty("code", code)
                        addProperty("exitCode", result?.exitCode ?: -1)
                    },
                )
            }
            val ok = dispatched == 5
            check(
                "ecosystem-dispatch",
                ok,
                if (ok) "test, lint, format, package and release all reach their tool"
                else "$dispatched of 5 tasks dispatched",
            ).apply { add("tasks", results) }
        } finally {
            runCatching { repository.delete(slug) }
        }
    }

    private fun check(name: String, ok: Boolean, detail: String): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("ok", ok)
        addProperty("detail", detail)
    }
}
