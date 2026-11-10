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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.closecode.MainActivity
import com.closecode.debug.DebugLog

/**
 * Keeps the process alive while `closecode setup` downloads JDK, Gradle, the SDK platform,
 * build-tools and optional native tools. The work itself runs on the control socket thread;
 * this foreground service and its wake lock stop Android from freezing that thread when the
 * screen goes away.
 */
class SetupKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(),
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }.onFailure {
            DebugLog.crumb("SETUP", "foreground start refused: ${it.javaClass.simpleName}")
        }
        val power = getSystemService(PowerManager::class.java)
        wakeLock = runCatching {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CloseCode:setup").apply {
                setReferenceCounted(false)
                acquire(45 * 60 * 1000L)
            }
        }.getOrNull()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Setting up CloseCode")
            .setContentText("Downloading and verifying build tools…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun createChannel() {
        runCatching {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Setup", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Toolchain setup progress."
                    setShowBadge(false)
                },
            )
        }
    }

    companion object {
        private const val CHANNEL = "setup_tools"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.closecode.setup.STOP"

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, SetupKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, SetupKeepAliveService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
