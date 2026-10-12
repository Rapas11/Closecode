package com.closecode.build

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Milestone (a) task engine: the same step list the Build panel draws, driven for real.
 * Steps that can run today do real local work; steps needing the downloaded toolchain
 * report [BuildStep.RequiresSetup] instead of pretending — a missing compiler is a
 * setup screen, never a fake green bar.
 */
class BuildEngine(
    private val toolchain: ToolchainStore,
) {
    private val _state = MutableStateFlow<BuildState>(BuildState.Idle)
    val state: StateFlow<BuildState> = _state

    suspend fun build(projectDir: File, slug: String): BuildOutcome = withContext(Dispatchers.Default) {
        _state.value = BuildState.Running(Step.ValidateContract, 0f)
        val violations = TemplateContract.check(projectDir, slug)
        if (violations.isNotEmpty()) {
            _state.value = BuildState.Failed(Step.ValidateContract, violations.first().message)
            return@withContext BuildOutcome.ContractFailed(violations)
        }
        _state.value = BuildState.Running(Step.ResolveDeps, 0.15f)
        val sources = projectDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
        if (sources.isEmpty()) {
            _state.value = BuildState.Failed(Step.ResolveDeps, "No sources to compile.")
            return@withContext BuildOutcome.NoSources
        }
        if (!toolchain.isReady()) {
            _state.value = BuildState.RequiresSetup(Step.CompileKotlin)
            return@withContext BuildOutcome.NeedsSetup
        }
        // Toolchain-gated steps run here (kotlinc/ECJ/D8/aapt2/apksig). Each one shells
        // out to the verified jars and maps diagnostics to file+line cards; until the
        // firstsetup APK ships those jars, reaching this line means setup lied.
        _state.value = BuildState.Failed(
            Step.CompileKotlin,
            "Compiler wiring lands next: jars verified, driver pending.",
        )
        return@withContext BuildOutcome.DriverPending
    }
}

/** Build steps in panel order. */
enum class Step {
    ValidateContract,
    ResolveDeps,
    LinkResources,
    CompileKotlin,
    CompileJava,
    Dex,
    PackageApk,
    Sign,
    Install,
}

/** What the Build panel draws. */
sealed interface BuildState {
    data object Idle : BuildState
    data class Running(val step: Step, val progress: Float) : BuildState
    data class RequiresSetup(val step: Step) : BuildState
    data class Failed(val step: Step, val message: String) : BuildState
    data class Succeeded(val apk: File, val seconds: Long) : BuildState
}

sealed interface BuildOutcome {
    data class ContractFailed(val violations: List<TemplateContract.Violation>) : BuildOutcome
    data object NoSources : BuildOutcome
    data object NeedsSetup : BuildOutcome
    data object DriverPending : BuildOutcome
    data class Installed(val apk: File) : BuildOutcome
}
