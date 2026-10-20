package com.closecode.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.closecode.MainActivity

/**
 * The floating Back to editor button that sits over the user's built app. It is a
 * window added with the draw-over-other-apps permission the user granted once; after
 * five seconds the pill shrinks to a 24 dp handle at the edge. One tap brings CloseCode
 * back and removes the window. If the permission is not granted, no window is added —
 * [showFallback] posts a notification with the same action instead.
 */
class BackToEditorService : Service() {

    private lateinit var windowManager: WindowManager
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val shrink = Runnable { shrinkToHandle() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(ID_NOTIFICATION, notification())
        windowManager = getSystemService(WindowManager::class.java)
        addPill()
    }

    override fun onDestroy() {
        handler.removeCallbacks(shrink)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun addPill() {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.parseColor("#1A1F2B"), 18f)
            elevation = dp(6).toFloat()
            setPadding(dp(14), 0, dp(16), 0)
            setOnClickListener { backToEditor() }
            addView(arrow())
            addView(
                TextView(this@BackToEditorService).apply {
                    text = "Back to editor"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setPadding(dp(8), 0, 0, 0)
                },
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(36),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        runCatching { windowManager.addView(pill, params) }
            .onFailure { stopSelf() }
        view = pill
        handler.postDelayed(shrink, 5_000L)
    }

    private fun shrinkToHandle() {
        val old = view ?: return
        runCatching { windowManager.removeView(old) }
        val handle = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            background = rounded(Color.parseColor("#1A1F2B"), 8f)
            setOnClickListener { backToEditor() }
        }
        val params = WindowManager.LayoutParams(
            dp(24),
            dp(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = dp(160)
        }
        runCatching { windowManager.addView(handle, params) }
        view = handle
    }

    private fun arrow(): TextView = TextView(this).apply {
        text = "←"
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
    }

    private fun backToEditor() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
        stopSelf()
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun createChannels() {
        runCatching {
            getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL,
                        "Back to editor",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "The floating button over a built app."
                        setShowBadge(false)
                    },
                    NotificationChannel(
                        CHANNEL_FALLBACK,
                        "Back to editor (notification)",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Used when draw-over-other-apps is not allowed."
                    },
                ),
            )
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_revert)
        .setContentTitle("Back to editor")
        .setContentText("CloseCode is showing a button over your app.")
        .setOngoing(true)
        .setContentIntent(openCloseCode())
        .build()

    private fun openCloseCode(): PendingIntent = PendingIntent.getActivity(
        this,
        3,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL = "back_to_editor"
        const val CHANNEL_FALLBACK = "back_to_editor_fallback"
        private const val ID_NOTIFICATION = 61
        private const val ID_FALLBACK = 62
        const val ACTION_STOP = "com.closecode.overlay.STOP"

        /** True when Android will let this app draw over another app. */
        fun allowed(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            if (!allowed(context)) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BackToEditorService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BackToEditorService::class.java))
            }
        }

        /**
         * The refused-permission path: a notification with the same action. It is posted
         * only when notifications are allowed; otherwise the caller says so in the panel.
         */
        fun showFallback(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            runCatching {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_FALLBACK,
                        "Back to editor (notification)",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
            val back = PendingIntent.getActivity(
                context,
                4,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val note = NotificationCompat.Builder(context, CHANNEL_FALLBACK)
                .setSmallIcon(android.R.drawable.ic_menu_revert)
                .setContentTitle("Back to editor")
                .setContentText("Tap to return to CloseCode.")
                .setAutoCancel(true)
                .setContentIntent(back)
                .addAction(0, "Back to editor", back)
                .build()
            runCatching { manager.notify(ID_FALLBACK, note) }
        }
    }
}
