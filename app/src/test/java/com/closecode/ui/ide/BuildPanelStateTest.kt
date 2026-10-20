package com.closecode.ui.ide

import com.closecode.build.BuildChain
import com.closecode.build.BuildState
import com.closecode.build.Step
import com.closecode.build.CompileDriver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPanelStateTest {

    private fun times(vararg pairs: Pair<Step, Long>) =
        pairs.map { BuildChain.StepTime(it.first, it.second) }

    @Test
    fun idleDrawsEveryStepPendingExceptNone() {
        val model = buildPanelModel(BuildState.Idle)
        assertEquals(7, model.rows.size)
        assertTrue(model.rows.all { it.state == StepState.PENDING })
        assertNull(model.result)
        assertEquals("Build APK", model.primaryLabel)
        assertFalse(model.failed)
        assertEquals(1f, model.progress, 0f)
    }

    @Test
    fun runningShowsTheCurrentStepAndChecksWhatFinished() {
        val model = buildPanelModel(BuildState.Running(Step.MakeDex, 0.5f))
        assertEquals(StepState.DONE, model.rows[0].state)
        assertEquals(StepState.DONE, model.rows[3].state)
        assertEquals(StepState.RUNNING, model.rows[4].state)
        assertEquals(StepState.PENDING, model.rows[5].state)
        assertNull(model.result)
        assertTrue(model.progress in 0.5f..1f)
    }

    @Test
    fun failureMarksTheStepThatStoppedAndKeepsItsEarlierTimes() {
        val model = buildPanelModel(
            BuildState.Failed(
                step = Step.CompileCode,
                message = "2 errors",
                diagnostics = listOf(
                    CompileDriver.Diagnostic(
                        path = "app/src/main/java/Foo.kt",
                        line = 12,
                        kind = CompileDriver.Diagnostic.Kind.Error,
                        message = "unresolved reference",
                    ),
                ),
                times = times(Step.CheckProject to 20, Step.FindLibraries to 30, Step.CompileResources to 80),
            ),
        )
        assertEquals(StepState.DONE, model.rows[2].state)
        assertEquals(80L, model.rows[2].millis)
        assertEquals(StepState.FAILED, model.rows[3].state)
        assertEquals(StepState.PENDING, model.rows[4].state)
        assertTrue(model.failed)
        assertEquals(false, model.resultOk)
        assertTrue(model.result!!.contains("Compile code"))
    }

    @Test
    fun successChecksEveryRowAndWritesOneResultLine() {
        val apk = File("build/app-debug.apk")
        val all = times(
            Step.CheckProject to 10,
            Step.FindLibraries to 10,
            Step.CompileResources to 100,
            Step.CompileCode to 200,
            Step.MakeDex to 50,
            Step.PackApp to 20,
            Step.Sign to 10,
        )
        val model = buildPanelModel(
            BuildState.Succeeded(apk, seconds = 4, sizeBytes = 3L * 1024 * 1024, times = all),
        )
        assertEquals(7, model.rows.size)
        assertTrue(model.rows.all { it.state == StepState.DONE })
        assertEquals(100L, model.rows[2].millis)
        assertEquals("Build successful · 4s · 3.0 MB", model.result)
        assertTrue(model.resultOk)
        assertFalse(model.failed)
        assertEquals("Build again", model.primaryLabel)
    }

    @Test
    fun cancellationIsNotAFailure() {
        val model = buildPanelModel(
            BuildState.Cancelled(times(Step.CheckProject to 5)),
        )
        assertEquals(StepState.DONE, model.rows[0].state)
        assertEquals(StepState.PENDING, model.rows[1].state)
        assertFalse(model.failed)
        assertEquals("Build cancelled.", model.result)
        assertEquals("Build again", model.primaryLabel)
    }

    @Test
    fun setupAndBlockedKeepTheRowsButOfferARetry() {
        val setup = buildPanelModel(BuildState.RequiresSetup(Step.CheckProject))
        assertEquals("Build", setup.primaryLabel)
        assertNull(setup.result)
        assertFalse(setup.failed)

        val blocked = buildPanelModel(BuildState.Blocked(emptyList()))
        assertNull(blocked.result)
        assertFalse(blocked.failed)
        assertEquals(7, blocked.rows.size)
    }
}
