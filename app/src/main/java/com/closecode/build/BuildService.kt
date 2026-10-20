package com.closecode.build

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
 * Keeps a running build alive when the user leaves the screen, and says so in the
 * notification shade. The chain itself runs in the app process (the same code as the
 * Build panel); this service only holds a foreground slot so Android does not stop that
 * process, and it carries Cancel. A finished build leaves an auto-cancelling notification
 * with the result, never a fake one.
 */
class BuildService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val project = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
                running = true
                startForegroundCompat(progressNotification(project, "Starting…", indeterminate = true))
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                val progress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                val project = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
                if (running) {
                    manager().notify(
                        ID_PROGRESS,
                        progressNotification(project, text, indeterminate = progress < 0, progress),
                    )
                }
            }
            ACTION_DONE -> {
                val ok = intent.getBooleanExtra(EXTRA_OK, false)
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                val project = intent.getStringExtra(EXTRA_PROJECT).orEmpty()
                running = false
                manager().notify(ID_DONE, doneNotification(project, text, ok))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP -> {
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                ID_PROGRESS,
                notification,
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }.onFailure { error ->
            DebugLog.crumb("BUILD", "foreground start refused: ${error.javaClass.simpleName}")
        }
    }

    private fun progressNotification(
        project: String,
        text: String,
        indeterminate: Boolean,
        progress: Int = 0,
    ): Notification {
        val cancel = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, BuildCancelReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(if (project.isBlank()) "Building" else "Building $project")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(if (indeterminate) 0 else 100, progress.coerceIn(0, 100), indeterminate)
            .setContentIntent(openApp())
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun doneNotification(project: String, text: String, ok: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(
                if (ok) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(
                when {
                    ok -> if (project.isBlank()) "Build finished" else "$project built"
                    else -> if (project.isBlank()) "Build failed" else "$project failed to build"
                },
            )
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

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
                        CHANNEL_PROGRESS,
                        "Build progress",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "A build that is still running."
                        setShowBadge(false)
                    },
                    NotificationChannel(
                        CHANNEL_DONE,
                        "Build results",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "A finished build's result."
                    },
                ),
            )
        }
    }

    private fun manager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL_PROGRESS = "build_progress"
        const val CHANNEL_DONE = "build_done"
        private const val ID_PROGRESS = 51
        private const val ID_DONE = 52

        const val ACTION_START = "com.closecode.build.START"
        const val ACTION_UPDATE = "com.closecode.build.UPDATE"
        const val ACTION_DONE = "com.closecode.build.DONE"
        const val ACTION_STOP = "com.closecode.build.STOP"
        const val EXTRA_PROJECT = "project"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_OK = "ok"

        @Volatile
        var running: Boolean = false
            private set

        /** Starts the foreground slot. Safe to call repeatedly for the same build. */
        fun start(context: Context, project: String) {
            running = true
            runCatching {
                val intent = Intent(context, BuildService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_PROJECT, project)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun update(context: Context, project: String, text: String, progress: Int) {
            if (!running) return
            runCatching {
                context.startService(
                    Intent(context, BuildService::class.java)
                        .setAction(ACTION_UPDATE)
                        .putExtra(EXTRA_PROJECT, project)
                        .putExtra(EXTRA_TEXT, text)
                        .putExtra(EXTRA_PROGRESS, progress),
                )
            }
        }

        fun finish(context: Context, project: String, text: String, ok: Boolean) {
            if (!running) return
            runCatching {
                context.startService(
                    Intent(context, BuildService::class.java)
                        .setAction(ACTION_DONE)
                        .putExtra(EXTRA_PROJECT, project)
                        .putExtra(EXTRA_TEXT, text)
                        .putExtra(EXTRA_OK, ok),
                )
            }
        }

        fun stop(context: Context) {
            running = false
            runCatching {
                context.startService(
                    Intent(context, BuildService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}

/**
 * The Cancel action on the build notification. It calls the running engine, exactly like
 * the panel's Cancel button; it never claims the build stopped before the engine says so.
 */
class BuildCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        BuildCancel.request()
    }
}

/** The notification's only bridge into the running engine. */
object BuildCancel {
    @Volatile
    private var action: (() -> Unit)? = null

    fun register(block: () -> Unit) {
        action = block
    }

    fun clear() {
        action = null
    }

    fun request() {
        action?.invoke()
    }
}
