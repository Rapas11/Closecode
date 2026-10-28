package com.closecode.device

import android.content.Context
import android.content.Intent
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * One screenshot request at a time. The activity owns MediaProjection because consent
 * belongs to an activity result; the bridge waits here for that activity's answer.
 */
object ScreenshotController {

    data class Result(val ok: Boolean, val message: String)

    private val lock = Any()
    private var pending: CompletableDeferred<Result>? = null
    private var pendingId: String? = null

    suspend fun capture(context: Context, output: File): Result {
        val answer = CompletableDeferred<Result>()
        val requestId = UUID.randomUUID().toString()
        synchronized(lock) {
            if (pending != null) {
                return Result(false, "A screenshot is already waiting for permission.")
            }
            pending = answer
            pendingId = requestId
        }
        val intent = Intent(context, ScreenshotActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ScreenshotActivity.EXTRA_OUTPUT, output.absolutePath)
            .putExtra(ScreenshotActivity.EXTRA_REQUEST_ID, requestId)
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started) {
            complete(requestId, Result(false, "Android did not open the screen-capture permission dialog."))
        }
        return try {
            withTimeout(120_000) { answer.await() }
        } catch (_: TimeoutCancellationException) {
            complete(requestId, Result(false, "The screen-capture request timed out."))
            answer.await()
        }
    }

    fun complete(requestId: String, result: Result) {
        synchronized(lock) {
            if (pendingId != requestId) return
            pending?.complete(result)
            pending = null
            pendingId = null
        }
    }
}
