package com.closecode.device

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import com.closecode.control.CommandEnvelope
import com.google.gson.JsonObject
import java.io.File

/** Asks Android for screen-capture consent, then starts the recording service. */
class ScreenRecordActivity : Activity() {

    private lateinit var statusFile: File
    private lateinit var output: File
    private var audio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val directory = intent.getStringExtra(EXTRA_STATUS_DIR)
        val target = intent.getStringExtra(EXTRA_OUTPUT)
        if (directory.isNullOrBlank() || target.isNullOrBlank()) {
            finish()
            return
        }
        statusFile = File(directory)
        output = File(target)
        statusFile.parentFile?.mkdirs()
        output.parentFile?.mkdirs()
        audio = intent.getBooleanExtra(EXTRA_AUDIO, false)
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            writeFailure("Screen recording was not approved.")
            finish()
            return
        }
        val service = Intent(this, ScreenRecordService::class.java)
            .setAction(ScreenRecordService.ACTION_START)
            .putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
            .putExtra(ScreenRecordService.EXTRA_OUTPUT, output.absolutePath)
            .putExtra(ScreenRecordService.EXTRA_STATUS, statusFile.absolutePath)
            .putExtra(ScreenRecordService.EXTRA_AUDIO, audio)
        runCatching {
            startForegroundService(service)
            Toast.makeText(this, "Recording the screen", Toast.LENGTH_SHORT).show()
        }.onFailure { writeFailure("Could not start screen recording: ${it.message}") }
        finish()
    }

    private fun writeFailure(message: String) {
        runCatching {
            statusFile.writeText(
                JsonObject().apply {
                    addProperty("ok", false)
                    addProperty("running", false)
                    addProperty("error", message)
                }.toString(),
            )
        }
    }

    companion object {
        const val EXTRA_OUTPUT = "com.closecode.screen.OUTPUT"
        const val EXTRA_STATUS_DIR = "com.closecode.screen.STATUS"
        const val EXTRA_AUDIO = "com.closecode.screen.AUDIO"
        private const val REQUEST_CAPTURE = 4301
    }
}
