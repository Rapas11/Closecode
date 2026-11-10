package com.closecode.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.gson.JsonObject
import java.io.File

/** Records the screen through MediaProjection into an MP4 while a foreground notification shows. */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var statusFile: File? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> startRecording(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (running) return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        output = intent.getStringExtra(EXTRA_OUTPUT)?.let(::File)
        statusFile = intent.getStringExtra(EXTRA_STATUS)?.let(::File)
        if (data == null || output == null) {
            finish(false, "Screen recording consent data is missing.")
            return
        }
        output?.parentFile?.mkdirs()
        startForeground(NOTIFICATION_ID, notification())
        var audio = intent.getBooleanExtra(EXTRA_AUDIO, false)
        if (audio && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audio = false
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = (metrics.widthPixels / 2 * 2).coerceAtLeast(2)
        val height = (metrics.heightPixels / 2 * 2).coerceAtLeast(2)
        val density = metrics.densityDpi
        val file = output ?: return
        try {
            val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (audio) media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audio) media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setVideoSize(width, height)
            media.setVideoFrameRate(30)
            media.setVideoEncodingBitRate(6_000_000)
            media.setOrientationHint(0)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            val manager = getSystemService(MediaProjectionManager::class.java)
            val mediaProjection = manager.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("Android did not provide a screen projection.")
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRecording()
                }
            }, null)
            display = mediaProjection.createVirtualDisplay(
                "closecode-screen",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                media.surface,
                null,
                null,
            )
            media.start()
            recorder = media
            projection = mediaProjection
            running = true
            writeStatus(
                JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("running", true)
                    addProperty("path", file.absolutePath)
                    addProperty("startedAt", System.currentTimeMillis())
                    addProperty("width", width)
                    addProperty("height", height)
                    addProperty("audio", audio)
                },
            )
        } catch (error: Throwable) {
            runCatching { recorder?.release() }
            recorder = null
            runCatching { display?.release() }
            display = null
            runCatching { projection?.stop() }
            projection = null
            finish(false, "Could not start recording: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun stopRecording() {
        if (!running) {
            finish(true, null)
            return
        }
        val file = output
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { display?.release() }
        display = null
        runCatching { projection?.stop() }
        projection = null
        running = false
        writeStatus(
            JsonObject().apply {
                addProperty("ok", true)
                addProperty("running", false)
                addProperty("path", file?.absolutePath.orEmpty())
                addProperty("stoppedAt", System.currentTimeMillis())
                addProperty("bytes", file?.length() ?: 0L)
            },
        )
        stopForeground(true)
        stopSelf()
    }

    private fun finish(ok: Boolean, error: String?) {
        writeStatus(
            JsonObject().apply {
                addProperty("ok", ok)
                addProperty("running", false)
                error?.let { addProperty("error", it) }
            },
        )
        stopForeground(true)
        stopSelf()
    }

    private fun writeStatus(json: JsonObject) {
        runCatching {
            statusFile?.let { file ->
                file.parentFile?.mkdirs()
                file.writeText(json.toString())
            }
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Screen recording", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("CloseCode is recording the screen")
            .setContentText("Tap Stop to finish the recording.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    override fun onDestroy() {
        if (running) stopRecording()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.closecode.screen.START"
        const val ACTION_STOP = "com.closecode.screen.STOP"
        const val EXTRA_RESULT_CODE = "com.closecode.screen.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.closecode.screen.RESULT_DATA"
        const val EXTRA_OUTPUT = "com.closecode.screen.OUTPUT"
        const val EXTRA_STATUS = "com.closecode.screen.STATUS"
        const val EXTRA_AUDIO = "com.closecode.screen.AUDIO"
        private const val CHANNEL = "closecode_screen"
        private const val NOTIFICATION_ID = 7301

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
