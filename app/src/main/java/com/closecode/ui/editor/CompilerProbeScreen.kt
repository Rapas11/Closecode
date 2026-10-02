package com.closecode.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import java.io.File

// TEMPORARY diagnostic screen. Not part of the product. Delete this file and the
// "Compiler probe" entry in IdeScreen.kt's overflow menu to remove the experiment.

private val Samples = listOf(
    "Type mismatch" to "val name: String = 123\n",
    "Unresolved reference" to "fun main() {\n    notDefinedAnywhere()\n}\n",
    "Syntax error" to "fun broken( {\n",
    "Clean source" to "fun add(a: Int, b: Int): Int {\n    return a + b\n}\n",
)

@Composable
fun CompilerProbeScreen(onBack: () -> Unit) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sample by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("Not run yet.") }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.cd)
                    .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "‹", color = c.mu, fontSize = 26.sp)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Compiler probe",
                    color = c.tx,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Temporary on-device test",
                    color = c.ac,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("SAMPLE", color = c.mu, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Samples.forEachIndexed { index, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (index == sample) c.ac.copy(alpha = 0.14f) else c.cd)
                            .clickable { sample = index }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.first,
                            color = if (index == sample) c.ac else c.tx,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Text(
                text = Samples[sample].second.trimEnd(),
                color = c.mu,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                fontFamily = Mono,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.panel)
                    .padding(12.dp),
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (running) c.mu.copy(alpha = 0.3f) else c.ab)
                    .clickable(enabled = !running) {
                        running = true
                        scope.launch {
                            output = withContext(Dispatchers.IO) {
                                val work = File(context.cacheDir, "probe").apply { mkdirs() }
                                val setup = kotlinHome(context)
                                val probe = CompilerProbe.resourceProbe(setup.compilerJar)
                                val measured = measureMemory {
                                    CompilerProbe.analyse(
                                        source = Samples[sample].second,
                                        workDir = work,
                                        kotlinHome = setup.kotlinHome,
                                        androidJar = setup.androidJar,
                                        compilerJar = setup.compilerJar,
                                    )
                                }
                                probe + "\n" +
                                    "setup ${setup.note}\n\n" +
                                    format(measured.first, measured.second)
                            }
                            running = false
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (running) "Running…" else "Run compiler",
                    color = c.af,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text("RESULT", color = c.mu, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            Text(
                text = output,
                color = c.tx,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Mono,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.panel)
                    .padding(12.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.ab)
                        .clickable {
                            clipboard.setText(AnnotatedString(output))
                            copied = true
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (copied) "Copied" else "Copy result",
                        color = c.af,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}


private fun <T> measureMemory(block: () -> T): Pair<T, String> {
    val runtime = Runtime.getRuntime()
    val before = runtime.totalMemory() - runtime.freeMemory()
    val value = block()
    val after = runtime.totalMemory() - runtime.freeMemory()
    val peak = (runtime.maxMemory() - runtime.freeMemory()) / 1_048_576L
    return value to "heap before ${before / 1_048_576L} MB, after ${after / 1_048_576L} MB, peak ${peak} MB"
}

private fun format(result: CompilerProbe.Result, memory: String): String = buildString {
    appendLine("elapsed ${result.elapsedMillis} ms")
    appendLine(memory)
    appendLine("exit ok = ${result.ok}")

    val failure = result.failure
    if (failure != null) {
        appendLine()
        appendLine("=== COMPILER THREW ===")
        append(failure)
        return@buildString
    }

    appendLine("diagnostics = ${result.diagnostics.size} (errors ${result.errors.size})")
    appendLine()
    val interesting = result.diagnostics.filter { it.severity != "LOGGING" }
    if (interesting.isEmpty()) {
        appendLine("(no errors or warnings)")
    } else {
        interesting.forEach { d ->
            appendLine("[${d.severity}] ${d.message}")
            if (d.line > 0) appendLine("    at ${d.line}:${d.column}")
        }
    }
}


/**
 * TEMPORARY: unpacks the bundled kotlin-stdlib into a real on-disk directory so the
 * compiler can be pointed at it. The compiler locates its own jars through the
 * classloader, which does not work on Android, so we hand it a directory instead.
 */
private data class ProbeAssets(
    val kotlinHome: File?,
    val androidJar: File?,
    val compilerJar: File?,
    val note: String,
)

/**
 * TEMPORARY: unpacks the bundled compiler and its Kotlin home into filesDir. The
 * compiler must be a real jar on disk, not dexed, so that Class.getResource can
 * resolve the .class entries IntelliJ's PathManager looks for.
 */
private fun kotlinHome(context: Context): ProbeAssets {
    val lib = listOf(
        "kotlin-home/lib/kotlin-stdlib.jar" to "kotlin-stdlib.jar",
        "kotlin-home/lib/kotlin-script-runtime.jar" to "kotlin-script-runtime.jar",
        "kotlin-home/lib/annotations.jar" to "annotations.jar",
        "kotlin-home/lib/android.jar" to "android.jar",
        "kotlin-home/lib/kotlin-compiler.jar" to "kotlin-compiler.jar",
    )
    return runCatching {
        val home = File(context.filesDir, "kotlin-home")
        val libDir = File(home, "lib").apply { mkdirs() }
        for ((asset, name) in lib) {
            val target = File(libDir, name)
            if (target.length() == 0L) {
                context.assets.open(asset).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        val stdlib = File(home, "lib/kotlin-stdlib.jar")
        val android = File(home, "lib/android.jar")
        val compiler = File(home, "lib/kotlin-compiler.jar")
        check(stdlib.length() > 0L) { "kotlin-stdlib.jar unpacked empty" }
        check(android.length() > 0L) { "android.jar unpacked empty" }
        check(compiler.length() > 0L) { "kotlin-compiler.jar unpacked empty" }
        ProbeAssets(
            kotlinHome = home,
            androidJar = android,
            compilerJar = compiler,
            note = "ok kotlin-stdlib ${stdlib.length()}, android.jar ${android.length()}, " +
                "kotlin-compiler.jar ${compiler.length()} bytes",
        )
    }.getOrElse { error ->
        val listing = runCatching {
            (context.assets.list("")?.toList().orEmpty() +
                context.assets.list("kotlin-home")?.toList().orEmpty() +
                context.assets.list("kotlin-home/lib")?.toList().orEmpty()).joinToString(",")
        }.getOrDefault("<assets unreadable>")
        ProbeAssets(null, null, null, "SETUP FAILED: ${error.message} | assets=[$listing]")
    }
}
