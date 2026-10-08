package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Spec 7 logcat levels. Verbose is muted, Debug accent, Info green, Warn amber, Error red. */
enum class LogLevel(val label: String, val letter: Char) {
    VERBOSE("Verbose", 'V'),
    DEBUG("Debug", 'D'),
    INFO("Info", 'I'),
    WARN("Warn", 'W'),
    ERROR("Error", 'E'),
}

/** One logcat row. [formatted] matches the spec's `HH:mm:ss.SSS L/Tag: message`. */
data class LogLine(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val time: String = NOW.format(LocalTime.now()),
) {
    val formatted: String get() = "$time ${level.letter}/$tag: $message"
    val sortableLevel: Int get() = level.ordinal

    companion object {
        private val NOW: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}

/** Spec 7: the log is capped at 1,000 lines. */
const val LOGCAT_MAX_LINES = 1_000

/**
 * Appends while enforcing the cap. Dropping the oldest lines is one array copy of at most a
 * thousand elements, which is cheap enough per line and keeps the bound strict: the list never
 * holds more than [LOGCAT_MAX_LINES], rather than drifting up to a batch threshold.
 */
fun MutableList<LogLine>.appendCapped(line: LogLine) {
    add(line)
    if (size > LOGCAT_MAX_LINES) {
        // Excess is usually 1, so this clears a short prefix and shifts the rest once.
        subList(0, size - LOGCAT_MAX_LINES).clear()
    }
}

/**
 * How far through a build a step is.
 *
 * There is no FAILED state: a build that reports an error still completed its work, and showing a
 * red cross beside a step that actually finished would be a second lie on top of the first one.
 * Whether the project compiled is the result line's business, not a step's.
 */
internal enum class StepState { PENDING, RUNNING, DONE }

/** Which part of the sweep a build is in, in the order it happens. */
enum class BuildPhase { IDLE, SYMBOLS, FILES, DONE }

/** The steps a build goes through, named after what they actually do. */
internal fun buildStepLabels(fileCount: Int): List<String> = listOf(
    "Read project symbols",
    when (fileCount) {
        0 -> "No Kotlin files"
        1 -> "Analyse 1 file"
        else -> "Analyse $fileCount files"
    }
)

/**
 * Which step each phase of a build is in.
 *
 * One number would have been tidier — divide the bar into equal shares — but it would have been
 * wrong. Reading the project's symbols is a single quick pass over every file, while analysing them
 * is the long haul, so on a real project the first step is finished long before a half-full bar.
 * Tracking the phase instead of guessing at a fraction is what lets the list say something true.
 */
internal fun buildStepStates(phase: BuildPhase): List<StepState> = when (phase) {
    BuildPhase.IDLE -> listOf(StepState.PENDING, StepState.PENDING)
    BuildPhase.SYMBOLS -> listOf(StepState.RUNNING, StepState.PENDING)
    BuildPhase.FILES -> listOf(StepState.DONE, StepState.RUNNING)
    BuildPhase.DONE -> listOf(StepState.DONE, StepState.DONE)
}

/** The length a build is reported in, so a sub-second check does not read as "0 ms". */
internal fun formatBuildTime(elapsedMs: Long): String =
    if (elapsedMs < 1000) {
        "$elapsedMs ms"
    } else {
        String.format(java.util.Locale.US, "%.1f s", elapsedMs / 1000.0)
    }

private fun countOf(n: Int, one: String, many: String): String =
    "$n ${if (n == 1) one else many}"

/**
 * The one line that says whether the project compiled.
 *
 * Counts and time come from the sweep that just ran, so this is a report rather than a mood. The
 * failing case says the errors are in the marked lines, because that is where they can be acted on
 * — the panel opens the Problems sheet from a tap, and the editor has already underlined them.
 */
internal fun buildResultLine(errorCount: Int, warningCount: Int, elapsedMs: Long): String {
    val time = formatBuildTime(elapsedMs)
    return when {
        errorCount > 0 -> "BUILD FAILED · ${countOf(errorCount, "error", "errors")}, " +
            "${countOf(warningCount, "warning", "warnings")} in $time. Open the marked lines to fix."
        warningCount > 0 -> "BUILD SUCCESSFUL · ${countOf(warningCount, "warning", "warnings")} in $time"
        else -> "BUILD SUCCESSFUL · 0 errors in $time"
    }
}

/**
 * The way out of Logcat and Build. Neither panel has a path bar, so without this the bottom
 * navigation was the only escape from a screen the top bar's build button had just opened.
 */
@Composable
internal fun PanelBackBar(title: String, onBack: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(c.panel2)
                .border(1.dp, c.ln, RoundedCornerShape(9.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "‹ Code",
                color = c.ac,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = title,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Spec 7 Logcat panel: filter chips, one mono row per line with a divider, long lines wrapping.
 *
 * The log is generated by the app's own analysis and build events. There is no real device
 * logcat on Android, so the header says so rather than implying otherwise.
 */
@Composable
fun IdeLogcatPanel(
    lines: List<LogLine>,
    modifier: Modifier = Modifier,
    filter: LogLevel? = null,
    onFilterChange: (LogLevel?) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val c = LocalPageColors.current
    val visible = remember(lines, filter) {
        if (filter == null) lines else lines.filter { it.level == filter }
    }

    Column(modifier.fillMaxSize().background(c.bg)) {
        PanelBackBar("Logcat", onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip("All", filter == null) { onFilterChange(null) }
            for (level in LogLevel.entries) {
                FilterChip(level.label, filter == level) { onFilterChange(level) }
            }
        }

        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (visible.isEmpty()) {
                Text(
                    text = "Run the app to see logs.",
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                    modifier = Modifier.padding(40.dp),
                )
                return@Column
            }
            Column(Modifier.fillMaxWidth()) {
                visible.forEach { line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = line.formatted,
                            color = when (line.level) {
                                LogLevel.INFO -> c.success
                                LogLevel.WARN -> c.amber
                                LogLevel.ERROR -> c.red
                                LogLevel.DEBUG -> c.ac
                                LogLevel.VERBOSE -> c.mu
                            },
                            fontSize = 13.sp,
                            fontFamily = Mono,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

/**
 * Spec 7 Build panel: a 6 dp progress bar, one row per phase of the compile check with a status
 * glyph, and a bold result line.
 *
 * This is a real check, not a Gradle build: there is no Gradle daemon inside an Android app and
 * there never will be, so the panel runs the app's own Kotlin analyser across the project's files
 * and reports what it genuinely found. The old version of this panel named Gradle tasks it never
 * ran and walked them on a timer, which is worse than useless — it looked like a build log without
 * being one. Everything drawn here now comes from the sweep: the bar from the files it has done, the
 * steps from the phase it is in, and the result line from the counts and time it measured.
 */
@Composable
fun IdeBuildPanel(
    building: Boolean,
    errorCount: Int,
    warningCount: Int,
    elapsedMs: Long,
    phase: BuildPhase,
    fileCount: Int,
    progress: Float,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val c = LocalPageColors.current
    val failed = errorCount > 0
    // While nothing is running the bar shows the whole sweep: full on a clean project, and stopped
    // where it got to on one that failed, so the last state is still visible rather than snapping
    // back to empty the moment the button is released.
    val fraction = when {
        building -> progress.coerceIn(0f, 1f)
        failed -> 1f
        else -> 1f
    }

    Column(modifier.fillMaxSize().background(c.bg)) {
        PanelBackBar("Build", onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.panel2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (failed) c.red else c.ac),
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (failed) c.red else c.ac)
                    .clickable(onClick = onRun)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (building) "Building" else "Run Build",
                    color = c.af,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val labels = buildStepLabels(fileCount)
            buildStepStates(phase).forEachIndexed { index, state ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (state) {
                            StepState.PENDING -> "."
                            StepState.RUNNING -> "..."
                            StepState.DONE -> "✓"
                        },
                        color = if (state == StepState.DONE) c.success else c.mu,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.size(width = 18.dp, height = 18.dp),
                    )
                    Text(
                        text = labels[index],
                        color = if (state == StepState.DONE) c.tx else c.mu,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln.copy(alpha = 0.45f)))
            }
            Text(
                text = buildResultLine(errorCount, warningCount, elapsedMs),
                color = if (failed) c.red else c.success,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) c.ac else Color.Transparent)
            .border(1.dp, if (active) c.ac else c.ln, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = if (active) Color.White else c.tx,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}