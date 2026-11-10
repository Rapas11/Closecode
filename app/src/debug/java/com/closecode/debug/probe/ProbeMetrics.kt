package com.closecode.debug.probe

import android.os.Build
import android.os.Debug
import com.closecode.debug.BuildInfo
import java.util.Locale

internal data class ProbeMemory(
    val pssKb: Int,
    val javaHeapKb: Long,
    val nativePssKb: Int,
)

internal data class ProbeFrames(
    val averageMs: Double = 0.0,
    val p95Ms: Double = 0.0,
    val maxMs: Double = 0.0,
    val over32: Int = 0,
    val samples: Int = 0,
)

internal data class ProbeMetrics(
    val size: ProbeSize = ProbeSize.K1,
    val chars: Int = 0,
    val lines: Int = 0,
    val generateMs: Long = 0,
    val loadMs: Long = 0,
    val frames: ProbeFrames = ProbeFrames(),
    val memory: ProbeMemory? = null,
)

internal object ProbeMeter {

    fun memory(): ProbeMemory {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        return ProbeMemory(
            pssKb = info.totalPss,
            javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
            nativePssKb = info.nativePss,
        )
    }

    fun report(
        metrics: ProbeMetrics,
        scratchProject: String?,
        sampleCount: Int,
        sampleLastKb: Int,
        samplePeakKb: Int,
        currentEditorNote: String,
    ): String = buildString {
        appendLine("Sora probe · editor-only 0.24.4")
        appendLine(BuildInfo.label)
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Scratch project: ${scratchProject ?: "(none)"}")
        appendLine()
        appendLine("File: ${metrics.size.fileName} (${metrics.size.lines} requested lines)")
        appendLine("Characters: ${metrics.chars}")
        appendLine("Lines in editor: ${metrics.lines}")
        appendLine("Generate: ${metrics.generateMs} ms")
        appendLine("Load/setText to first frame: ${metrics.loadMs} ms")
        appendLine(
            "Frames since reset: ${metrics.frames.samples} · " +
                "${one(metrics.frames.averageMs)} avg · " +
                "${one(metrics.frames.p95Ms)} p95 · " +
                "${one(metrics.frames.maxMs)} max · " +
                "${metrics.frames.over32} over 32 ms",
        )
        metrics.memory?.let { memory ->
            appendLine(
                "Memory after load: ${mb(memory.pssKb)} PSS · " +
                    "${mb(memory.javaHeapKb.toInt())} Java heap · " +
                    "${mb(memory.nativePssKb)} native PSS",
            )
        }
        appendLine(
            "Current-editor sampler: $sampleCount samples · " +
                "${mb(sampleLastKb)} last · ${mb(samplePeakKb)} peak",
        )
        appendLine()
        appendLine(currentEditorNote)
    }

    private fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun mb(kb: Int): String = String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
