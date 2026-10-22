package com.closecode.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.closecode.MainActivity
import com.closecode.debug.DebugLog

/**
 * Keeps the OpenCode server alive when the user leaves the screen.
 *
 * The server itself runs in the app process, the same code the AI tab drives. This service only
 * holds a foreground slot so Android does not stop that process while the phone is asleep, and
 * it carries the notification the spec asks for: what is running, where it is, and a Stop button.
 */
class OpenCodeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                val project = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
                running = true
                startForegroundCompat(notification(url, project, running = false))
            }

            ACTION_RUNNING -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                val project = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
                running = true
                manager().notify(ID_RUNNING, notification(url, project, running = true))
            }

            ACTION_STOP -> {
                running = false
                OpenCode.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                ID_RUNNING,
                notification,
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }.onFailure { error ->
            DebugLog.crumb("AICO", "foreground start refused: ${error.javaClass.simpleName}")
        }
    }

    private fun notification(url: String, project: String, running: Boolean): Notification {
        val stop = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, OpenCodeStopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(
                when {
                    !running -> "Starting OpenCode"
                    project.isBlank() -> "OpenCode is running"
                    else -> "OpenCode is running on $project"
                },
            )
            .setContentText(if (url.isBlank()) "Starting the server" else url)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        runCatching {
            manager().createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL,
                        "OpenCode",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "The OpenCode server running on this phone."
                        setShowBadge(false)
                    },
                ),
            )
        }
    }

    private fun manager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL = "opencode_server"
        private const val ID_RUNNING = 71

        const val ACTION_START = "com.closecode.ai.START"
        const val ACTION_RUNNING = "com.closecode.ai.RUNNING"
        const val ACTION_STOP = "com.closecode.ai.STOP"
        const val EXTRA_URL = "url"
        const val EXTRA_PROJECT = "project"

        @Volatile
        var running: Boolean = false
            private set

        /** Takes the foreground slot. Safe to call more than once. */
        fun start(context: Context, url: String, project: String) {
            running = true
            runCatching {
                val intent = Intent(context, OpenCodeService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_PROJECT, project)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** The notification changes once the server answers. */
        fun update(context: Context, url: String, project: String) {
            if (!running) return
            runCatching {
                context.startService(
                    Intent(context, OpenCodeService::class.java)
                        .setAction(ACTION_RUNNING)
                        .putExtra(EXTRA_URL, url)
                        .putExtra(EXTRA_PROJECT, project),
                )
            }
        }

        /** The notification's Stop button. */
        fun stop(context: Context) {
            running = false
            runCatching {
                context.startService(
                    Intent(context, OpenCodeService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}

/** The Stop action on the OpenCode notification. It stops the server for real. */
class OpenCodeStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        OpenCode.stop()
    }
}
