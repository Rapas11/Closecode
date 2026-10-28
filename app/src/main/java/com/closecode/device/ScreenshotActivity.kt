package com.closecode.device

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * The consent screen for one screenshot. It has no visible UI of its own; Android's
 * MediaProjection dialog is the UI, and the captured frame is written to the path the
 * bridge chose before this activity was opened.
 */
class ScreenshotActivity : ComponentActivity() {

    private var completed = false
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtual: VirtualDisplay? = null
    private var output: File? = null
    private var requestId = ""

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            finishWithError("Screen capture was cancelled.")
        } else {
            capture(result.resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_OUTPUT)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (path.isNullOrBlank() || requestId.isBlank()) {
            finishWithError("No screenshot request was supplied.")
            return
        }
        output = File(path)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager == null) {
            finishWithError("This Android version has no screen-capture service.")
            return
        }
        runCatching { consent.launch(manager.createScreenCaptureIntent()) }
            .onFailure {
                finishWithError(
                    "Android did not open the screen-capture permission dialog: " +
                        (it.message ?: it.javaClass.simpleName),
                )
            }
    }

    private fun capture(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val mediaProjection = runCatching {
            manager?.getMediaProjection(resultCode, data)
        }.getOrNull()
        if (mediaProjection == null) {
            finishWithError("Android refused screen capture.")
            return
        }
        val metrics = resources.displayMetrics
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            mediaProjection.stop()
            finishWithError("The screen size is not available.")
            return
        }
        val imageReader = runCatching {
            ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2,
            )
        }.getOrNull()
        if (imageReader == null) {
            mediaProjection.stop()
            finishWithError("Could not create the screen-capture reader.")
            return
        }
        val display = runCatching {
            mediaProjection.createVirtualDisplay(
                "closecode-screenshot",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null,
            )
        }.getOrNull()
        if (display == null) {
            runCatching { imageReader.close() }
            runCatching { mediaProjection.stop() }
            finishWithError("Could not start the screen-capture display.")
            return
        }
        projection = mediaProjection
        reader = imageReader
        virtual = display
        var saved = false
        imageReader.setOnImageAvailableListener({ source ->
            if (saved) return@setOnImageAvailableListener
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            saved = true
            try {
                save(image)
                finishWithSuccess()
            } catch (error: Throwable) {
                finishWithError("Could not save the screenshot: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                image.close()
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun save(image: Image) {
        val target = output ?: error("No screenshot path.")
        val plane = image.planes.firstOrNull() ?: error("The captured frame has no pixels.")
        val width = image.width
        val height = image.height
        val pixelStride = plane.pixelStride
        if (pixelStride <= 0) error("The captured frame has an invalid pixel stride.")
        val rowPadding = plane.rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val bitmap = if (paddedWidth == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
        try {
            target.parentFile?.mkdirs()
            target.outputStream().buffered().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "PNG encoding failed."
                }
            }
        } finally {
            if (bitmap !== padded) bitmap.recycle()
            padded.recycle()
        }
    }

    private fun finishWithSuccess() {
        if (completed) return
        completed = true
        val path = output?.absolutePath.orEmpty()
        release()
        ScreenshotController.complete(requestId, ScreenshotController.Result(true, path))
        finish()
    }

    private fun finishWithError(message: String) {
        if (completed) return
        completed = true
        release()
        ScreenshotController.complete(requestId, ScreenshotController.Result(false, message))
        finish()
    }

    private fun release() {
        runCatching { virtual?.release() }
        virtual = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.stop() }
        projection = null
    }

    override fun onDestroy() {
        if (!completed && !isChangingConfigurations) {
            completed = true
            release()
            ScreenshotController.complete(
                requestId,
                ScreenshotController.Result(false, "The screenshot activity closed before capture."),
            )
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OUTPUT = "com.closecode.device.SCREENSHOT_OUTPUT"
        const val EXTRA_REQUEST_ID = "com.closecode.device.SCREENSHOT_REQUEST_ID"
    }
}
