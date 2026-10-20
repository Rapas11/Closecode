package com.closecode.build

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Drives the real build chain for the Build panel. The project check runs first (a
 * block is not a build), the toolchain must be genuinely ready, and then every step
 * reports through [state]. A failed step is a [BuildOutcome.Failed] with the compiler's
 * own diagnostics; a missing pack is [BuildOutcome.NeedsSetup], never a fake success.
 *
 * One engine has one build at a time. [cancel] is checked between steps and inside the
 * chain at the start of each step; the tools themselves cannot be interrupted mid-call,
 * so a cancel stops the build at the next step boundary.
 */
class BuildEngine(
    private val toolchain: ToolchainStore,
) {
    private val _state = MutableStateFlow<BuildState>(BuildState.Idle)
    val state: StateFlow<BuildState> = _state

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    suspend fun build(
        projectDir: File,
        slug: String,
        checkInput: ProjectCheck.Input = ProjectCheck.Input(),
        tools: BuildTools? = null,
    ): BuildOutcome = withContext(Dispatchers.IO) {
        cancelled = false
        _state.value = BuildState.Running(Step.CheckProject, 0f)
        val report = ProjectCheck.check(projectDir, slug, checkInput)
        if (!report.ok) {
            _state.value = BuildState.Blocked(report.findings)
            return@withContext BuildOutcome.Blocked(report.findings)
        }
        if (tools == null || !toolchain.isReady()) {
            _state.value = BuildState.RequiresSetup(Step.CheckProject)
            return@withContext BuildOutcome.NeedsSetup(report.warnings)
        }
        val facts = GradleReader.read(projectDir)
        val manifestText = runCatching {
            File(projectDir, "app/src/main/AndroidManifest.xml").readText()
        }.getOrDefault("")
        val packageName = checkInput.savedPackage
            ?: facts.packageName
            ?: TemplateContract.manifestPackage(manifestText)
            ?: ""
        if (packageName.isBlank()) {
            _state.value = BuildState.Failed(Step.CheckProject, "No package is set for this app.")
            return@withContext BuildOutcome.Failed("No package is set for this app.", emptyList())
        }
        val config = BuildConfigValues(
            packageName = packageName,
            minSdk = checkInput.savedMinSdk ?: facts.minSdk ?: 29,
            targetSdk = checkInput.savedTargetSdk ?: facts.targetSdk ?: ProjectPins.COMPILE_SDK,
            versionCode = facts.versionCode ?: 1,
            versionName = facts.versionName ?: "1.0",
            useCompose = facts.composeBom != null ||
                facts.composeCompiler != null ||
                facts.libraries.any { it.module.startsWith("androidx.compose") },
        )
        val started = System.currentTimeMillis()
        _state.value = BuildState.Running(Step.CheckProject, progress(Step.CheckProject))
        val result = BuildChain.build(
            projectDir = projectDir,
            config = config,
            tools = tools,
            onStep = { step -> _state.value = BuildState.Running(step, progress(step)) },
            cancelled = { cancelled },
        )
        val seconds = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
        val failure = result.failure
        when {
            failure == null && result.apk != null -> {
                val apk = result.apk!!
                _state.value = BuildState.Succeeded(apk, seconds, apk.length(), result.times)
                BuildOutcome.Built(apk, apk.length(), seconds)
            }
            failure?.cancelled == true -> {
                _state.value = BuildState.Cancelled(result.times)
                BuildOutcome.Cancelled
            }
            failure != null -> {
                _state.value = BuildState.Failed(
                    failure.step,
                    failure.message,
                    result.diagnostics,
                    result.times,
                )
                if (failure.step == Step.CompileCode && result.diagnostics.isNotEmpty()) {
                    BuildOutcome.CompileFailed(result.diagnostics)
                } else {
                    BuildOutcome.Failed(failure.message, result.diagnostics)
                }
            }
            else -> {
                _state.value = BuildState.Failed(Step.Sign, "The build stopped without an APK.")
                BuildOutcome.Failed("The build stopped without an APK.", emptyList())
            }
        }
    }

    private fun progress(step: Step): Float =
        (step.ordinal + 0.5f) / Step.entries.size
}

/** Build steps in panel order. Install and open is drawn only for Run. */
enum class Step(val label: String) {
    CheckProject("Check the project"),
    FindLibraries("Find libraries"),
    CompileResources("Compile resources"),
    CompileCode("Compile code"),
    MakeDex("Make dex"),
    PackApp("Pack the app"),
    Sign("Sign"),
    InstallOpen("Install and open"),
}

/** What the Build panel draws. */
sealed interface BuildState {
    data object Idle : BuildState
    data class Running(val step: Step, val progress: Float) : BuildState
    data class RequiresSetup(val step: Step) : BuildState
    data class Blocked(val findings: List<ProjectCheck.Finding>) : BuildState
    data class Failed(
        val step: Step,
        val message: String,
        val diagnostics: List<CompileDriver.Diagnostic> = emptyList(),
        val times: List<BuildChain.StepTime> = emptyList(),
    ) : BuildState
    data class Succeeded(
        val apk: File,
        val seconds: Long,
        val sizeBytes: Long,
        val times: List<BuildChain.StepTime> = emptyList(),
    ) : BuildState
    data class Cancelled(val times: List<BuildChain.StepTime>) : BuildState
}

sealed interface BuildOutcome {
    /** The project check stopped the build: every finding, errors first, ready to list. */
    data class Blocked(val findings: List<ProjectCheck.Finding>) : BuildOutcome
    data class NeedsSetup(val warnings: List<ProjectCheck.Finding> = emptyList()) : BuildOutcome
    data class CompileFailed(val diagnostics: List<CompileDriver.Diagnostic>) : BuildOutcome
    data class Failed(
        val message: String,
        val diagnostics: List<CompileDriver.Diagnostic> = emptyList(),
    ) : BuildOutcome
    data class Built(val apk: File, val sizeBytes: Long, val seconds: Long) : BuildOutcome
    data object Cancelled : BuildOutcome
}
