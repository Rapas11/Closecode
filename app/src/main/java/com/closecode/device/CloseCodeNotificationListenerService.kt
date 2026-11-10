package com.closecode.device

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.RemoteInput
import com.closecode.device.control.AuditLog
import com.closecode.device.control.ConsentStore
import com.closecode.control.CommandEnvelope
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Reads, dismisses and replies to notifications through Android's own listener API. The user
 * enables it in system settings; CloseCode can never turn it on itself.
 */
class CloseCodeNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    companion object {
        @Volatile
        private var instance: CloseCodeNotificationListenerService? = null

        fun current(): CloseCodeNotificationListenerService? = instance

        fun component(context: Context): ComponentName =
            ComponentName(context, CloseCodeNotificationListenerService::class.java)

        fun enabled(context: Context): Boolean = runCatching {
            NotificationListenerService.requestRebind(component(context))
            instance != null
        }.getOrDefault(false)
    }
}

internal object NotificationControl {

    fun run(context: Context, arguments: List<String>): JsonObject {
        val consent = ConsentStore(context)
        if (!consent.isGranted("notifications")) {
            return CommandEnvelope.failed(
                "The notifications capability is not granted.",
                code = "needs_consent",
                remedy = "Run closecode consent grant notifications",
            )
        }
        val action = arguments.firstOrNull()?.lowercase() ?: "list"
        val listener = CloseCodeNotificationListenerService.current()
        if (listener == null) {
            val opened = action == "enable" || action == "open"
            if (opened) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            return CommandEnvelope.failed(
                "Notification access is not enabled.",
                code = "needs_permission",
                remedy = if (opened) {
                    "Enable CloseCode on the settings screen, then retry."
                } else {
                    "Run closecode notifications enable, then enable CloseCode."
                },
            ).apply {
                addProperty("needsUser", true)
                addProperty("settingsAction", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                addProperty("component", CloseCodeNotificationListenerService.component(context).flattenToString())
            }
        }
        val result = when (action) {
            "list", "ls" -> list(context, listener, arguments)
            "dismiss", "cancel" -> dismiss(context, listener, arguments)
            "reply" -> reply(context, listener, arguments)
            "enable", "open" -> CommandEnvelope.of(ok = true).apply {
                addProperty("enabled", true)
            }
            else -> CommandEnvelope.failed(
                "usage: closecode notifications list|dismiss <key>|reply <key> <text>",
                code = "usage",
            )
        }
        AuditLog(context).record("notifications $action", null, arguments, result.get("ok")?.asBoolean == true, null)
        return result
    }

    private fun list(
        context: Context,
        listener: CloseCodeNotificationListenerService,
        arguments: List<String>,
    ): JsonObject {
        val wanted = valueAfter(arguments, "--package")
        val notifications = runCatching { listener.activeNotifications.toList() }
            .getOrDefault(emptyList())
            .filter { wanted == null || it.packageName == wanted }
        return CommandEnvelope.of(ok = true).apply {
            add("notifications", JsonArray().apply {
                notifications.forEach { notification ->
                    add(json(notification))
                }
            })
        }
    }

    private fun dismiss(
        context: Context,
        listener: CloseCodeNotificationListenerService,
        arguments: List<String>,
    ): JsonObject {
        val key = arguments.getOrNull(1)
            ?: return CommandEnvelope.failed("usage: closecode notifications dismiss <key>", code = "usage")
        listener.cancelNotification(key)
        return CommandEnvelope.of(ok = true).apply { addProperty("key", key) }
    }

    private fun reply(
        context: Context,
        listener: CloseCodeNotificationListenerService,
        arguments: List<String>,
    ): JsonObject {
        val key = arguments.getOrNull(1)
            ?: return CommandEnvelope.failed("usage: closecode notifications reply <key> <text>", code = "usage")
        val text = arguments.drop(2).joinToString(" ").ifBlank {
            return CommandEnvelope.failed("Reply text is empty.", code = "usage")
        }
        val notification = runCatching {
            listener.activeNotifications.firstOrNull { it.key == key }
        }.getOrNull() ?: return CommandEnvelope.failed("No notification with key '$key'.", code = "not_found")
        val action = notification.notification.actions.orEmpty().firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        } ?: return CommandEnvelope.failed("This notification has no reply action.", code = "no_reply")
        @Suppress("UNCHECKED_CAST")
        val inputs: Array<RemoteInput> = action.remoteInputs as? Array<RemoteInput> ?: emptyArray()
        val results = Bundle().apply {
            inputs.forEach { putCharSequence(it.resultKey, text) }
        }
        val intent = Intent()
        RemoteInput.addResultsToIntent(inputs, intent, results)
        return runCatching {
            action.actionIntent.send(context, 0, intent)
            CommandEnvelope.of(ok = true).apply {
                addProperty("key", key)
                addProperty("package", notification.packageName)
                addProperty("text", text)
            }
        }.getOrElse {
            CommandEnvelope.failed("Could not send the reply: ${it.message}", code = "reply_failed")
        }
    }

    private fun json(notification: StatusBarNotification): JsonObject {
        val extras = notification.notification.extras
        return JsonObject().apply {
            addProperty("key", notification.key)
            addProperty("package", notification.packageName)
            addProperty("id", notification.id)
            addProperty("tag", notification.tag.orEmpty())
            addProperty("postedAt", notification.postTime)
            addProperty("ongoing", notification.isOngoing)
            addProperty("clearable", notification.isClearable)
            addProperty("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            addProperty("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            addProperty("replyable", notification.notification.actions.orEmpty().any {
                it.remoteInputs?.isNotEmpty() == true
            })
            add("actions", JsonArray().apply {
                notification.notification.actions.orEmpty().forEach { add(it.title?.toString().orEmpty()) }
            })
        }
    }

    private fun valueAfter(arguments: List<String>, name: String): String? {
        val index = arguments.indexOf(name)
        return if (index >= 0) arguments.getOrNull(index + 1) else null
    }
}
