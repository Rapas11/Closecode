package com.closecode.debug.probe

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.closecode.MainActivity
import com.closecode.debug.sharePlainText
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.theme.CcColors
import com.closecode.ui.theme.ClosecodeTheme
import com.closecode.ui.theme.LocalCcColors
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoraProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClosecodeTheme {
                SoraProbeScreen()
            }
        }
    }
}

@Composable
private fun SoraProbeScreen() {
    val context = LocalContext.current
    val colors = LocalCcColors.current
    val repository = remember(context) { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    val editor = remember(context) { CodeEditor(context) }

    var selected by remember { mutableStateOf(ProbeSize.K1) }
    var metrics by remember { mutableStateOf(ProbeMetrics()) }
    var project by remember { mutableStateOf<ProjectRecord?>(null) }
    var status by remember {
        mutableStateOf("Generate the scratch project, then open it in CloseCode.")
    }
    var busy by remember { mutableStateOf(false) }
    var sampling by remember { mutableStateOf(false) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var sampleLast by remember { mutableIntStateOf(0) }
    var samplePeak by remember { mutableIntStateOf(0) }
    var frameEpoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(editor, colors) {
        configureEditor(editor, colors)
    }

    LaunchedEffect(frameEpoch) {
        val ring = DoubleArray(240)
        var filled = 0
        var cursor = 0
        var over32 = 0
        var sinceUpdate = 0
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000.0
            last = now
            if (dt > 1_000.0) continue
            ring[cursor] = dt
            cursor = (cursor + 1) % ring.size
            if (filled < ring.size) filled++
            if (dt > 32.0) over32++
            sinceUpdate++
            if (sinceUpdate >= 30) {
                val sorted = ring.take(filled).sorted()
                val average = sorted.average()
                val p95 = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.lastIndex)]
                metrics = metrics.copy(
                    frames = ProbeFrames(
                        averageMs = average,
                        p95Ms = p95,
                        maxMs = sorted.last(),
                        over32 = over32,
                        samples = filled,
                    ),
                )
                sinceUpdate = 0
            }
        }
    }

    LaunchedEffect(sampling) {
        if (!sampling) return@LaunchedEffect
        while (true) {
            val memory = withContext(Dispatchers.IO) { ProbeMeter.memory() }
            sampleCount++
            sampleLast = memory.pssKb
            if (memory.pssKb > samplePeak) samplePeak = memory.pssKb
            delay(1_000)
        }
    }

    fun load(size: ProbeSize) {
        if (busy) return
        busy = true
        status = "Generating ${size.fileName}…"
        scope.launch {
            try {
                val generating = System.nanoTime()
                val text = withContext(Dispatchers.Default) { ProbeContent.text(size) }
                val generateMs = (System.nanoTime() - generating) / 1_000_000
                status = "Loading ${size.fileName}…"
                val loading = System.nanoTime()
                editor.setText(text)
                withFrameNanos { }
                val loadMs = (System.nanoTime() - loading) / 1_000_000
                val memory = withContext(Dispatchers.IO) { ProbeMeter.memory() }
                selected = size
                metrics = ProbeMetrics(
                    size = size,
                    chars = text.length,
                    lines = editor.lineCount,
                    generateMs = generateMs,
                    loadMs = loadMs,
                    frames = ProbeFrames(),
                    memory = memory,
                )
                frameEpoch++
                status = "${size.fileName} loaded in Sora"
            } catch (error: Throwable) {
                status = "Load failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun generateScratch() {
        if (busy) return
        busy = true
        status = "Creating ${BenchProject.NAME}…"
        scope.launch {
            try {
                val record = withContext(Dispatchers.IO) {
                    BenchProject.ensure(context, repository)
                }
                project = record
                status = "${record.name} ready. Open it in CloseCode and pick a SORA_PROBE file."
            } catch (error: Throwable) {
                status = "Scratch project failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun deleteScratch() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    BenchProject.delete(repository)
                }
                project = null
                status = if (deleted > 0) {
                    "Deleted $deleted scratch project(s) named ${BenchProject.NAME}."
                } else {
                    "No scratch project named ${BenchProject.NAME}."
                }
            } catch (error: Throwable) {
                status = "Delete failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.content)) {
        Row(
            Modifier.fillMaxWidth().background(colors.panel).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Sora Probe",
                    color = colors.text,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.SansSerif,
                )
                Text(
                    text = "editor 0.24.4 · plain text · no TextMate",
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = project?.name ?: BenchProject.NAME,
                color = if (project == null) colors.muted else colors.green,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProbeButton("Generate", !busy) { generateScratch() }
            ProbeSize.values().forEach { size ->
                val selectedStyle = size == selected
                if (selectedStyle) {
                    Button(
                        onClick = { load(size) },
                        enabled = !busy,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 11.dp),
                        shape = RoundedCornerShape(9.dp),
                    ) {
                        Text(size.fileName.removePrefix("SORA_PROBE_").removeSuffix(".kt"), fontSize = 11.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { load(size) },
                        enabled = !busy,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 11.dp),
                        shape = RoundedCornerShape(9.dp),
                    ) {
                        Text(size.fileName.removePrefix("SORA_PROBE_").removeSuffix(".kt"), fontSize = 11.sp)
                    }
                }
            }
            ProbeButton("Undo", !busy) {
                editor.undo()
                status = "Undo"
            }
            ProbeButton("Redo", !busy) {
                editor.redo()
                status = "Redo"
            }
            ProbeButton("Reset frames", true) {
                metrics = metrics.copy(frames = ProbeFrames())
                frameEpoch++
                status = "Frame window reset"
            }
            ProbeButton("Sample memory", true) {
                scope.launch {
                    val memory = withContext(Dispatchers.IO) { ProbeMeter.memory() }
                    metrics = metrics.copy(memory = memory)
                    status = "Memory sampled"
                }
            }
            ProbeButton("Open current editor", !busy) {
                sampling = true
                sampleCount = 0
                sampleLast = 0
                samplePeak = 0
                status = "Sampler on. Open ${BenchProject.NAME} → ${selected.fileName} in CloseCode."
                context.startActivity(Intent(context, MainActivity::class.java))
            }
            if (sampling) {
                ProbeButton("Stop sampler", true) {
                    sampling = false
                    status = "Sampler stopped"
                }
            }
            ProbeButton("Reset project", !busy) { deleteScratch() }
            ProbeButton("Copy report", true) {
                val report = ProbeMeter.report(
                    metrics = metrics,
                    scratchProject = project?.name,
                    sampleCount = sampleCount,
                    sampleLastKb = sampleLast,
                    samplePeakKb = samplePeak,
                    currentEditorNote = "Current editor: manual stopwatch for load; " +
                        "enable Settings > Frame stats for avg/p95/max.",
                )
                if (!sharePlainText(context, "Sora probe report", report)) {
                    status = "No app accepted the report"
                }
            }
        }

        Text(
            text = status,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            color = colors.textSoft,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
        )

        MetricsStrip(colors, metrics, sampleCount, sampleLast, samplePeak, sampling)

        AndroidView(
            factory = { editor },
            modifier = Modifier.fillMaxWidth().weight(1f),
            onRelease = { it.release() },
        )

        Text(
            text = "Type, scroll, undo/redo and rotate here. For the current editor, " +
                "open the same SORA_PROBE file from Files and time it manually.",
            modifier = Modifier.fillMaxWidth().background(colors.panel).padding(10.dp),
            color = colors.muted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )
    }
}

@Composable
private fun ProbeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 11.dp),
        shape = RoundedCornerShape(9.dp),
    ) {
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun MetricsStrip(
    colors: CcColors,
    metrics: ProbeMetrics,
    sampleCount: Int,
    sampleLastKb: Int,
    samplePeakKb: Int,
    sampling: Boolean,
) {
    val frames = metrics.frames
    val memory = metrics.memory
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.field)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = "load ${metrics.loadMs} ms · ${metrics.lines} lines · " +
                "${metrics.chars} chars · gen ${metrics.generateMs} ms",
            color = colors.textSoft,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "frames ${one(frames.averageMs)} avg · ${one(frames.p95Ms)} p95 · " +
                "${one(frames.maxMs)} max · ${frames.over32} > 32 ms · ${frames.samples} samples",
            color = colors.textSoft,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "PSS ${mb(memory?.pssKb)} after load · sampler " +
                (if (sampling) "on" else "off") +
                " · ${mb(sampleLastKb)} last · ${mb(samplePeakKb)} peak · $sampleCount",
            color = colors.textSoft,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun one(value: Double): String = "%.1f".format(value)

private fun mb(kb: Int?): String = if (kb == null) "—" else "%.1f MB".format(kb / 1024.0)

private fun configureEditor(editor: CodeEditor, colors: CcColors) {
    editor.typefaceText = Typeface.MONOSPACE
    editor.typefaceLineNumber = Typeface.MONOSPACE
    editor.setTextSize(13f)
    editor.setLineSpacing(0f, 1.05f)
    editor.setLineNumberEnabled(true)
    editor.setWordwrap(false)
    editor.setTabWidth(4)
    editor.setUndoEnabled(true)
    editor.setHighlightCurrentLine(true)
    editor.setBlockLineEnabled(true)
    editor.setHighlightBracketPair(true)
    editor.props.drawSideBlockLine = true
    editor.props.highlightMatchingDelimiters = true
    editor.props.stickyScroll = false

    val scheme = EditorColorScheme()
    scheme.applyDefault()
    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, colors.content.toArgb())
    scheme.setColor(EditorColorScheme.TEXT_NORMAL, colors.text2.toArgb())
    scheme.setColor(EditorColorScheme.CURRENT_LINE, colors.field.copy(alpha = 0.55f).toArgb())
    scheme.setColor(EditorColorScheme.LINE_NUMBER, colors.faint.toArgb())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, colors.textSoft.toArgb())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, colors.content.toArgb())
    scheme.setColor(EditorColorScheme.LINE_DIVIDER, colors.divider.toArgb())
    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, colors.accent.copy(alpha = 0.24f).toArgb())
    scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, colors.content.toArgb())
    scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, colors.faint.toArgb())
    scheme.setColor(EditorColorScheme.BLOCK_LINE, colors.divider.toArgb())
    scheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, colors.faint.toArgb())
    scheme.setColor(EditorColorScheme.SIDE_BLOCK_LINE, colors.divider.toArgb())
    scheme.setColor(
        EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND,
        colors.accent.copy(alpha = 0.20f).toArgb(),
    )
    scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, colors.accent.toArgb())
    scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, colors.text.toArgb())
    scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER, colors.accent.toArgb())
    scheme.setColor(EditorColorScheme.STICKY_SCROLL_DIVIDER, colors.divider.toArgb())
    editor.setColorScheme(scheme)
}
