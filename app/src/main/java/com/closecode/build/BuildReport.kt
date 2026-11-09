package com.closecode.build

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

object BuildReport {

    fun state(state: BuildState): JsonObject = JsonObject().apply {
        when (state) {
            BuildState.Idle -> addProperty("state", "idle")
            is BuildState.Running -> {
                addProperty("state", "running")
                addProperty("step", state.step.label)
                addProperty("stepIndex", state.step.ordinal)
                addProperty("progress", state.progress)
            }
            is BuildState.RequiresSetup -> {
                addProperty("state", "setup")
                addProperty("step", state.step.label)
                state.command?.let { addProperty("command", it) }
            }
            is BuildState.Blocked -> {
                addProperty("state", "blocked")
                add("findings", findings(state.findings))
            }
            is BuildState.Failed -> {
                addProperty("state", "failed")
                addProperty("step", state.step.label)
                addProperty("message", state.message)
                add("diagnostics", diagnostics(state.diagnostics))
                add("times", times(state.times))
                state.exitCode?.let { addProperty("exitCode", it) }
                state.durationMs?.let { addProperty("durationMs", it) }
                state.logPath?.let { addProperty("logPath", it) }
            }
            is BuildState.Succeeded -> {
                addProperty("state", "succeeded")
                addProperty("apk", state.apk.toString())
                addProperty("seconds", state.seconds)
                addProperty("sizeBytes", state.sizeBytes)
                add("times", times(state.times))
            }
            is BuildState.Cancelled -> {
                addProperty("state", "cancelled")
                add("times", times(state.times))
            }
        }
    }

    fun outcome(outcome: BuildOutcome): JsonObject = JsonObject().apply {
        when (outcome) {
            is BuildOutcome.Blocked -> {
                addProperty("state", "blocked")
                add("findings", findings(outcome.findings))
            }
            is BuildOutcome.NeedsSetup -> {
                addProperty("state", "setup")
                add("warnings", findings(outcome.warnings))
                outcome.command?.let { addProperty("command", it) }
            }
            is BuildOutcome.CompileFailed -> {
                addProperty("state", "failed")
                add("diagnostics", diagnostics(outcome.diagnostics))
                outcome.exitCode?.let { addProperty("exitCode", it) }
                outcome.durationMs?.let { addProperty("durationMs", it) }
                outcome.logPath?.let { addProperty("logPath", it) }
            }
            is BuildOutcome.Failed -> {
                addProperty("state", "failed")
                addProperty("message", outcome.message)
                add("diagnostics", diagnostics(outcome.diagnostics))
                outcome.exitCode?.let { addProperty("exitCode", it) }
                outcome.durationMs?.let { addProperty("durationMs", it) }
                outcome.logPath?.let { addProperty("logPath", it) }
            }
            is BuildOutcome.Built -> {
                addProperty("state", "succeeded")
                addProperty("apk", outcome.apk.toString())
                addProperty("sizeBytes", outcome.sizeBytes)
                addProperty("seconds", outcome.seconds)
            }
            BuildOutcome.Cancelled -> addProperty("state", "cancelled")
        }
    }

    fun summary(outcome: BuildOutcome): String = when (outcome) {
        is BuildOutcome.Built ->
            "Built ${outcome.apk.name} in ${outcome.seconds} s (${outcome.sizeBytes} bytes)."
        is BuildOutcome.CompileFailed ->
            "Compilation failed with ${outcome.diagnostics.size} diagnostic(s)."
        is BuildOutcome.Failed -> "Build failed: ${outcome.message}"
        is BuildOutcome.Blocked ->
            "The project cannot be built yet: ${outcome.findings.count { it.severity == ProjectCheck.Severity.Error }} error(s)."
        is BuildOutcome.NeedsSetup -> "The build tools are not ready."
        BuildOutcome.Cancelled -> "Build cancelled."
    }

    fun latestLog(projectDir: File): File? {
        val dir = File(projectDir, "build/logs")
        val stable = File(dir, "build.log")
        if (stable.isFile) return stable
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("build-") }
            ?.maxByOrNull { it.lastModified() }
    }

    fun tail(projectDir: File, maxLines: Int = 20): List<String> =
        runCatching {
            latestLog(projectDir)?.readLines()?.takeLast(maxLines).orEmpty()
        }.getOrDefault(emptyList())

    private fun findings(findings: List<ProjectCheck.Finding>): JsonArray = JsonArray().apply {
        findings.forEach { finding ->
            add(JsonObject().apply {
                addProperty("severity", finding.severity.name.lowercase())
                addProperty("message", finding.message)
                finding.file?.let { addProperty("file", it) }
                finding.line?.let { addProperty("line", it) }
            })
        }
    }

    private fun diagnostics(diagnostics: List<CompileDriver.Diagnostic>): JsonArray =
        JsonArray().apply {
            diagnostics.forEach { diagnostic ->
                add(JsonObject().apply {
                    addProperty("path", diagnostic.path)
                    addProperty("line", diagnostic.line)
                    addProperty("column", diagnostic.column)
                    addProperty("kind", diagnostic.kind.name.lowercase())
                    addProperty("message", diagnostic.message)
                    diagnostic.code?.let { addProperty("code", it) }
                })
            }
        }

    private fun times(times: List<BuildChain.StepTime>): JsonArray = JsonArray().apply {
        times.forEach { time ->
            add(JsonObject().apply {
                addProperty("step", time.step.label)
                addProperty("millis", time.millis)
            })
        }
    }
}
