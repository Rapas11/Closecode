package com.closecode.build

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
internal object BuildLock {
    val mutex = Mutex()
}

class BuildEngine(
    private val toolchain: ToolchainStore,
    private val cache: BuildCache? = null,
    private val network: NetworkGate? = null,
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
        projectId: String,
        checkInput: ProjectCheck.Input = ProjectCheck.Input(),
        tools: BuildTools? = null,
        onLog: (String) -> Unit = {},
        cacheEnabled: Boolean = true,
        buildType: String = "debug",
        useGradle: Boolean = false,
        gradle: GradleToolchain? = null,
        setupCommand: String? = null,
    ): BuildOutcome {
        cancelled = false
        return BuildLock.mutex.withLock {
            buildLocked(
                projectDir,
                projectId,
                checkInput,
                tools,
                onLog,
                cacheEnabled,
                buildType,
                useGradle,
                gradle,
                setupCommand,
            )
        }
    }

    private suspend fun buildLocked(
        projectDir: File,
        projectId: String,
        checkInput: ProjectCheck.Input,
        tools: BuildTools?,
        onLog: (String) -> Unit,
        cacheEnabled: Boolean,
        buildType: String,
        useGradle: Boolean,
        gradle: GradleToolchain?,
        setupCommand: String?,
    ): BuildOutcome = withContext(Dispatchers.IO) {
        val type = if (buildType == "release") "release" else "debug"
        if (useGradle) {
            // Toolchain first: a missing Gradle is the one blocker worth reporting before
            // dozens of custom-engine findings that do not apply to a real Gradle build.
            if (gradle == null) {
                _state.value = BuildState.RequiresSetup(Step.CheckProject, setupCommand)
                return@withContext BuildOutcome.NeedsSetup(command = setupCommand)
            }
            _state.value = BuildState.Running(Step.CheckProject, 0f)
            val report = ProjectCheck.check(projectDir, projectId, checkInput, gradle = true)
            if (!report.ok) {
                _state.value = BuildState.Blocked(report.findings)
                return@withContext BuildOutcome.Blocked(report.findings)
            }
            return@withContext buildWithGradle(projectDir, type, onLog, gradle)
        }
        // The custom engine cannot start at all without its packs, so that answer comes
        // before the project check: install first, then talk about the project.
        if (tools == null || !toolchain.isReady()) {
            _state.value = BuildState.RequiresSetup(Step.CheckProject, setupCommand)
            return@withContext BuildOutcome.NeedsSetup(command = setupCommand)
        }
        _state.value = BuildState.Running(Step.CheckProject, 0f)
        val report = ProjectCheck.check(projectDir, projectId, checkInput)
        if (!report.ok) {
            _state.value = BuildState.Blocked(report.findings)
            return@withContext BuildOutcome.Blocked(report.findings)
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
            buildType = type,
        )
        val cacheKey = if (cacheEnabled && cache != null) {
            runCatching { cache.key(projectDir, config, tools!!) }.getOrNull()
        } else {
            null
        }
        if (cacheKey != null) {
            val hit = cache?.lookup(projectId, cacheKey)
            if (hit != null) {
                val output = File(projectDir, "build/outputs/apk/$type/app-$type.apk")
                runCatching {
                    output.parentFile?.mkdirs()
                    hit.copyTo(output, overwrite = true)
                }.onSuccess {
                    val seconds = 0L
                    _state.value = BuildState.Succeeded(
                        output,
                        seconds,
                        output.length(),
                        cached = true,
                    )
                    onLog("BUILD CACHE HIT · ${hit.name}")
                    return@withContext BuildOutcome.Built(
                        output,
                        output.length(),
                        seconds,
                        cached = true,
                    )
                }
            }
        }
        val started = System.currentTimeMillis()
        _state.value = BuildState.Running(Step.CheckProject, progress(Step.CheckProject))
        val result = BuildChain.build(
            projectDir = projectDir,
            config = config,
            tools = tools,
            onStep = { step -> _state.value = BuildState.Running(step, progress(step)) },
            cancelled = { cancelled },
            onLog = onLog,
        )
        val seconds = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
        val failure = result.failure
        when {
            failure == null && result.apk != null -> {
                val apk = result.apk!!
                if (cacheKey != null) cache?.store(projectId, cacheKey, apk)
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

    private fun buildWithGradle(
        projectDir: File,
        buildType: String,
        onLog: (String) -> Unit,
        gradle: GradleToolchain?,
    ): BuildOutcome {
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (!manifest.isFile) {
            val message = "Missing app/src/main/AndroidManifest.xml."
            _state.value = BuildState.Failed(Step.CheckProject, message)
            return BuildOutcome.Failed(message)
        }
        val buildFile = File(projectDir, "app/build.gradle.kts").takeIf { it.isFile }
            ?: File(projectDir, "app/build.gradle").takeIf { it.isFile }
        if (buildFile == null) {
            val message = "Missing app/build.gradle.kts."
            _state.value = BuildState.Failed(Step.CheckProject, message)
            return BuildOutcome.Failed(message)
        }
        if (gradle == null) {
            _state.value = BuildState.RequiresSetup(Step.CheckProject)
            return BuildOutcome.NeedsSetup(command = "closecode setup --gradle")
        }
        _state.value = BuildState.Running(Step.CheckProject, 0f)
        onLog("Full Gradle ${gradle.distributionVersion} · ${GradlePlan.taskFor(buildType)}")
        _state.value = BuildState.Running(Step.CompileCode, 0.5f)
        val started = System.currentTimeMillis()
        var attempt = 1
        var result = GradleRunner.run(
            projectDir = projectDir,
            buildType = buildType,
            toolchain = gradle,
            onLog = onLog,
            cancelled = { cancelled },
        )
        while (attempt < 3 && !cancelled && !result.cancelled && !result.timedOut &&
            result.code != 0 && NETWORK_FAILURE.containsMatchIn(result.output)
        ) {
            onLog("Network hiccup during dependency resolution; retrying in ${attempt * 2}s…")
            Thread.sleep(attempt * 2_000L)
            attempt++
            result = GradleRunner.run(
                projectDir = projectDir,
                buildType = buildType,
                toolchain = gradle,
                onLog = onLog,
                cancelled = { cancelled },
            )
        }
        val seconds = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
        if (result.cancelled) {
            _state.value = BuildState.Cancelled(emptyList())
            return BuildOutcome.Cancelled
        }
        var apk = result.apk
        if (apk != null && buildType == "release") {
            val signer = gradle.signer
            if (signer == null) {
                val message = "The release APK was built but no signing key is available."
                _state.value = BuildState.Failed(Step.Sign, message)
                return BuildOutcome.Failed(message)
            }
            _state.value = BuildState.Running(Step.Sign, 0.9f)
            onLog("Signing ${apk.name} on the phone…")
            apk = try {
                ApkPostSigner.sign(
                    apk,
                    signer,
                    GradleReader.read(projectDir).minSdk ?: 29,
                )
            } catch (error: Throwable) {
                val message = "Signing the release APK failed: " +
                    (error.message ?: error.javaClass.simpleName)
                _state.value = BuildState.Failed(Step.Sign, message)
                return BuildOutcome.Failed(message)
            }
        }
        if (apk != null) {
            _state.value = BuildState.Succeeded(apk, seconds, apk.length())
            return BuildOutcome.Built(apk, apk.length(), seconds)
        }
        var message = if (result.code == 0 && !result.timedOut) {
            "Gradle finished but produced no APK."
        } else {
            GradleRunner.failureMessage(result)
        }
        val offline = network != null && !network.isWifi() && !network.mobileAllowed()
        if (offline && NETWORK_FAILURE.containsMatchIn(result.output)) {
            message += " Gradle needed the network and mobile data is disabled for downloads. " +
                "Connect Wi-Fi, or allow mobile data under Settings → Build & Toolchain."
        }
        val diagnostics = GradleDiagnostics.parse(result.output)
        _state.value = BuildState.Failed(
            Step.CompileCode,
            message,
            diagnostics,
            emptyList(),
            result.code,
            result.durationMs,
            result.logPath,
        )
        return if (diagnostics.any { it.kind == CompileDriver.Diagnostic.Kind.Error }) {
            BuildOutcome.CompileFailed(
                diagnostics,
                result.code,
                result.durationMs,
                result.logPath,
            )
        } else {
            BuildOutcome.Failed(message, diagnostics, result.code, result.durationMs, result.logPath)
        }
    }

    private fun progress(step: Step): Float =
        (step.ordinal + 0.5f) / Step.entries.size

    private companion object {
        val NETWORK_FAILURE = Regex(
            "(?i)could not (resolve|get)|unknown host|connect timed out|" +
                "network is unreachable|offline mode",
        )
    }
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
    data class RequiresSetup(val step: Step, val command: String? = null) : BuildState
    data class Blocked(val findings: List<ProjectCheck.Finding>) : BuildState
    data class Failed(
        val step: Step,
        val message: String,
        val diagnostics: List<CompileDriver.Diagnostic> = emptyList(),
        val times: List<BuildChain.StepTime> = emptyList(),
        val exitCode: Int? = null,
        val durationMs: Long? = null,
        val logPath: String? = null,
    ) : BuildState
    data class Succeeded(
        val apk: File,
        val seconds: Long,
        val sizeBytes: Long,
        val times: List<BuildChain.StepTime> = emptyList(),
        val cached: Boolean = false,
    ) : BuildState
    data class Cancelled(val times: List<BuildChain.StepTime>) : BuildState
}

sealed interface BuildOutcome {
    /** The project check stopped the build: every finding, errors first, ready to list. */
    data class Blocked(val findings: List<ProjectCheck.Finding>) : BuildOutcome
    data class NeedsSetup(
        val warnings: List<ProjectCheck.Finding> = emptyList(),
        val command: String? = null,
    ) : BuildOutcome
    data class CompileFailed(
        val diagnostics: List<CompileDriver.Diagnostic>,
        val exitCode: Int? = null,
        val durationMs: Long? = null,
        val logPath: String? = null,
    ) : BuildOutcome
    data class Failed(
        val message: String,
        val diagnostics: List<CompileDriver.Diagnostic> = emptyList(),
        val exitCode: Int? = null,
        val durationMs: Long? = null,
        val logPath: String? = null,
    ) : BuildOutcome
    data class Built(
        val apk: File,
        val sizeBytes: Long,
        val seconds: Long,
        val cached: Boolean = false,
    ) : BuildOutcome
    data object Cancelled : BuildOutcome
}
