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

    /**
     * Full build. [aapt2Bin] and [androidJar] come from the SDK download (a separate
     * setup from the compiler pack); null means packaging stays honestly pending.
     * [stdlibJar] is the Kotlin stdlib for user code to link against.
     */
    suspend fun build(
        projectDir: File,
        slug: String,
        aapt2Bin: File? = null,
        androidJar: File? = null,
        stdlibJar: File? = null,
    ): BuildOutcome = withContext(Dispatchers.Default) {
        _state.value = BuildState.Running(Step.ValidateContract, 0f)
        val report = TemplateContract.check(projectDir, slug)
        if (!report.ok) {
            _state.value = BuildState.Failed(Step.ValidateContract, report.violations.first().message)
            return@withContext BuildOutcome.ContractFailed(report.violations)
        }
        _state.value = BuildState.Running(Step.ResolveDeps, 0.15f)
        val ktSources = projectDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val javaSources = projectDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .toList()
        if (ktSources.isEmpty() && javaSources.isEmpty()) {
            _state.value = BuildState.Failed(Step.ResolveDeps, "No sources to compile.")
            return@withContext BuildOutcome.NoSources
        }
        if (!toolchain.isReady()) {
            _state.value = BuildState.RequiresSetup(Step.CompileKotlin)
            return@withContext BuildOutcome.NeedsSetup(report.warnings)
        }
        val work = File(projectDir, "build/closecode").apply { mkdirs() }
        val classesDir = File(work, "classes").apply { mkdirs() }

        // Kotlin first: real diagnostics become file+line cards on failure.
        if (ktSources.isNotEmpty()) {
            _state.value = BuildState.Running(Step.CompileKotlin, 0.35f)
            val launchJars = ToolchainManifest.kotlincLaunchIds.mapNotNull { toolchain.jar(it) }
            val stdlib = stdlibJar ?: toolchain.jar("kotlin-stdlib")
            if (launchJars.size < ToolchainManifest.kotlincLaunchIds.size || stdlib == null) {
                _state.value = BuildState.RequiresSetup(Step.CompileKotlin)
                return@withContext BuildOutcome.NeedsSetup(report.warnings)
            }
            val result = ToolRunner.runMain(
                launchJars,
                "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                CompileDriver.kotlincArgs(ktSources, listOfNotNull(stdlib), classesDir, null),
            )
            val diagnostics = result.output.lines()
                .mapNotNull { CompileDriver.parseDiagnostic(it) }
            val errors = diagnostics.filter { it.kind == CompileDriver.Diagnostic.Kind.Error }
            if (result.code != 0 || errors.isNotEmpty()) {
                val first = errors.firstOrNull()
                _state.value = BuildState.Failed(
                    Step.CompileKotlin,
                    first?.let { "${it.path}:${it.line}: ${it.message}" } ?: "Kotlin failed.",
                )
                return@withContext BuildOutcome.CompileFailed(diagnostics)
            }
        }

        // Packaging (resources, dex, sign, install) needs the SDK pair, which ships
        // separately from the compiler pack. Until then the honest answer is pending.
        if (aapt2Bin?.isFile != true || androidJar?.isFile != true) {
            _state.value = BuildState.Failed(
                Step.LinkResources,
                "SDK tools (aapt2, android.jar) are not downloaded yet.",
            )
            return@withContext BuildOutcome.DriverPending
        }
        _state.value = BuildState.Failed(Step.Dex, "Dex + sign drivers land next.")
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
    data class NeedsSetup(val warnings: List<TemplateContract.Violation> = emptyList()) : BuildOutcome
    data class CompileFailed(val diagnostics: List<CompileDriver.Diagnostic>) : BuildOutcome
    data object DriverPending : BuildOutcome
    data class Installed(val apk: File) : BuildOutcome
}
