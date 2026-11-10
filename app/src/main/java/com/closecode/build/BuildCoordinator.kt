package com.closecode.build

import android.content.Context
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

object BuildCoordinator {

    data class Active(
        val id: String,
        val slug: String,
        val name: String,
        val engine: BuildEngine,
    )

    data class Last(
        val id: String,
        val slug: String,
        val name: String,
        val outcome: BuildOutcome,
        val summary: String,
    )

    private val engines = ConcurrentHashMap<String, BuildEngine>()

    @Volatile
    private var active: Active? = null

    @Volatile
    private var last: Last? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun engine(context: Context, projectId: String): BuildEngine =
        engines.computeIfAbsent(projectId) {
            val app = context.applicationContext
            BuildEngine(
                ToolchainHolder.store(app),
                BuildCache(File(app.cacheDir, "build-cache")),
                AndroidNetworkGate(app),
            )
        }

    fun active(): Active? = active

    fun last(): Last? = last

    fun cancel(): Boolean {
        val running = active ?: return false
        running.engine.cancel()
        return true
    }

    suspend fun build(
        context: Context,
        project: BuildSetup.Project,
        setup: BuildSetup,
        onEvent: (JsonObject) -> Unit,
        onLog: (String) -> Unit = {},
        cacheEnabled: Boolean = true,
        buildType: String = "debug",
        useGradle: Boolean = false,
    ): BuildOutcome {
        val engine = engine(context, project.id)
        synchronized(this) {
            val running = active
            if (running != null) {
                throw IllegalStateException("A build is already running for ${running.name}.")
            }
            active = Active(project.id, project.slug, project.name, engine)
        }
        BuildService.start(context, project.name)
        BuildCancel.register { engine.cancel() }
        val watcher = scope.launch {
            engine.state.collect { state ->
                if (state is BuildState.Running) {
                    val progress = ((state.step.ordinal + 1) * 100 / Step.entries.size)
                        .coerceIn(0, 100)
                    BuildService.update(context, project.name, state.step.label, progress)
                }
                runCatching {
                    onEvent(JsonObject().apply {
                        addProperty("event", "state")
                        add("state", BuildReport.state(state))
                    })
                }
            }
        }
        try {
            val outcome = engine.build(
                projectDir = project.directory,
                projectId = project.id,
                checkInput = setup.checkInput,
                tools = setup.tools,
                onLog = onLog,
                cacheEnabled = cacheEnabled,
                buildType = buildType,
                useGradle = useGradle,
                gradle = setup.gradle,
                setupCommand = if (useGradle) {
                    "closecode setup --gradle"
                } else {
                    "closecode setup --all"
                },
            )
            val summary = BuildReport.summary(outcome)
            last = Last(project.id, project.slug, project.name, outcome, summary)
            BuildService.finish(context, project.name, summary, outcome is BuildOutcome.Built)
            return outcome
        } catch (error: Throwable) {
            val outcome = BuildOutcome.Failed(error.message ?: error.javaClass.simpleName)
            val summary = BuildReport.summary(outcome)
            last = Last(project.id, project.slug, project.name, outcome, summary)
            BuildService.finish(context, project.name, summary, false)
            throw error
        } finally {
            watcher.cancelAndJoin()
            synchronized(this) {
                if (active?.id == project.id) active = null
            }
        }
    }
}
