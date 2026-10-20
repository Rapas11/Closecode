package com.closecode.build

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.closecode.MainActivity
import com.closecode.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The build-tools download, running as a foreground service so it continues when the user
 * leaves the setup screen. The notification shows the percent and carries Pause and Cancel;
 * the screen observes the same [ToolchainStore] state.
 *
 * One download at a time: a second start while running is ignored — the store is single-flight
 * and its state is the truth either way.
 */
class ToolchainDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: ToolchainStore
    private var observing: Job? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = ToolchainHolder.store(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                store.pause()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                store.cancel()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat(notification(store.state.value))
        if (started) return START_NOT_STICKY
        started = true
        observing = scope.launch {
            store.state.collectLatest { state ->
                runCatching {
                    notificationManager().notify(NOTIFICATION_ID, notification(state))
                }
            }
        }
        scope.launch {
            val ok = store.ensureAll()
            DebugLog.crumb("TOOLS", if (ok) "download service finished" else "download service stopped")
            // Give the last state a moment to reach the screen before going away.
            delay(400)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observing?.cancel()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.onFailure { error ->
            // A foreground service that may not start must not take the download with it:
            // the screen still watches the same store.
            DebugLog.crumb("TOOLS", "foreground start refused: ${error.javaClass.simpleName}")
        }
    }

    private fun notification(state: ToolchainState): Notification {
        val needsPermissionNote = if (!notificationsAllowed(this)) {
            "Allow notifications to see this progress."
        } else {
            null
        }
        val text = when (state) {
            is ToolchainState.Downloading -> {
                val percent = if (state.totalBytes > 0) {
                    (state.doneBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                "Downloading build tools… $percent%"
            }
            is ToolchainState.Verifying -> "Checking files…"
            ToolchainState.Checking -> "Checking files…"
            ToolchainState.WaitingForWifi -> "Waiting for Wi-Fi — open CloseCode to allow mobile data"
            ToolchainState.Ready -> "Build tools are ready"
            is ToolchainState.Failed -> state.message
            ToolchainState.NotStarted -> "Build tools"
        }
        val progress = when (state) {
            is ToolchainState.Downloading -> {
                val percent = if (state.totalBytes > 0) {
                    (state.doneBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(text)
                    .setContentText(state.current + (needsPermissionNote?.let { " · $it" } ?: ""))
                    .setProgress(100, percent, false)
            }
            else -> {
                NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(text)
                    .setContentText(needsPermissionNote.orEmpty())
            }
        }
            .setOngoing(state !is ToolchainState.Ready && state !is ToolchainState.Failed)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, "Pause", actionIntent(ACTION_PAUSE))
            .addAction(0, "Cancel", actionIntent(ACTION_CANCEL))
            .build()
        return progress
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, ToolchainDownloadService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        runCatching {
            val channel = NotificationChannel(
                CHANNEL,
                "Build tools",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "The one-time build tools download."
                setShowBadge(false)
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL = "build_tools"
        private const val NOTIFICATION_ID = 41
        const val ACTION_PAUSE = "com.closecode.tools.PAUSE"
        const val ACTION_CANCEL = "com.closecode.tools.CANCEL"

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, ToolchainDownloadService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun send(context: Context, action: String) {
            runCatching {
                context.startService(
                    Intent(context, ToolchainDownloadService::class.java).setAction(action),
                )
            }
        }

        /** True when the system will actually show the tool notification. */
        fun notificationsAllowed(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= 33) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
    }
}
