package com.closecode.device.control

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.closecode.MainActivity

/**
 * P2-1: the user has to see an ask. When the agent asks for a capability, the phone shows a
 * notification that opens the permissions screen, where the only Allow/Deny buttons live.
 * The notification never grants anything by itself.
 */
object ConsentNotifier {

    const val CHANNEL = "closecode-consent"

    /** The MainActivity extra that opens the permissions screen. */
    const val EXTRA_OPEN = "closecode.open"
    const val OPEN_PERMISSIONS = "agent_permissions"

    fun notify(context: Context, request: ConsentStore.Request) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                "Agent permissions",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Requests from the agent that need your permission"
            }
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, OPEN_PERMISSIONS)
        val pending = PendingIntent.getActivity(
            context,
            request.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val scope = request.scope?.takeIf { it.isNotBlank() }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("CloseCode asks for ${request.capability}")
            .setContentText("Tap to allow or deny it.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The agent asked for '${request.capability}'" +
                        (scope?.let { " on $it" } ?: "") +
                        ". Open CloseCode to allow or deny it.",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(request.id.hashCode(), notification) }
    }
}
