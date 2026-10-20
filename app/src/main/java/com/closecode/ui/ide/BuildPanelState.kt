package com.closecode.ui.ide

import com.closecode.build.BuildState
import com.closecode.build.Step

/**
 * The Build panel's mapping from the engine's state. Pure so the seven rows, their
 * check marks and the one-line result can be tested without Compose or a phone.
 */
internal data class BuildPanelModel(
    val rows: List<BuildStepUi>,
    val result: String?,
    val resultOk: Boolean,
    val failed: Boolean,
    val progress: Float,
    val primaryLabel: String?,
)

internal fun buildPanelModel(state: BuildState): BuildPanelModel {
    // Install and open is not part of building; it is the button the APK unlocks.
    val steps = Step.entries.filter { it != Step.InstallOpen }
    val times = when (state) {
        is BuildState.Succeeded -> state.times.associate { it.step to it.millis }
        is BuildState.Failed -> state.times.associate { it.step to it.millis }
        is BuildState.Cancelled -> state.times.associate { it.step to it.millis }
        else -> emptyMap()
    }
    val running = (state as? BuildState.Running)?.step
    val failedAt = (state as? BuildState.Failed)?.step
    val cancelled = state is BuildState.Cancelled
    val doneThrough = when {
        state is BuildState.Succeeded -> steps.last().ordinal
        failedAt != null -> failedAt.ordinal - 1
        cancelled -> times.keys.maxOfOrNull { it.ordinal } ?: -1
        running != null -> running.ordinal - 1
        else -> -1
    }
    val rows = steps.map { step ->
        val stepState = when {
            step == failedAt -> StepState.FAILED
            step == running -> StepState.RUNNING
            step.ordinal <= doneThrough -> StepState.DONE
            else -> StepState.PENDING
        }
        BuildStepUi(step.label, stepState, times[step])
    }
    return when (state) {
        BuildState.Idle -> BuildPanelModel(rows, null, false, false, 1f, "Build APK")
        is BuildState.Running -> BuildPanelModel(
            rows,
            null,
            false,
            false,
            ((state.step.ordinal + 1).toFloat() / steps.size).coerceIn(0f, 1f),
            null,
        )
        is BuildState.RequiresSetup -> BuildPanelModel(
            rows,
            null,
            false,
            false,
            1f,
            "Build",
        )
        is BuildState.Blocked -> BuildPanelModel(rows, null, false, false, 1f, "Build")
        is BuildState.Succeeded -> BuildPanelModel(
            rows,
            "Build successful · ${state.seconds}s · ${megaBytes(state.sizeBytes)}",
            true,
            false,
            1f,
            "Build again",
        )
        is BuildState.Failed -> BuildPanelModel(
            rows,
            "Build failed at ${state.step.label}: ${state.message.take(180)}",
            false,
            true,
            1f,
            "Build again",
        )
        is BuildState.Cancelled -> BuildPanelModel(
            rows,
            "Build cancelled.",
            false,
            false,
            1f,
            "Build again",
        )
    }
}

private fun megaBytes(bytes: Long): String =
    "%.1f MB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
