package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleBuildOptionsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun heapFollowsDeviceRamWithinTheSpecRange() {
        assertEquals(1024, GradleBuildOptions.heapFor(2L * GIB))
        assertEquals(1536, GradleBuildOptions.heapFor(4L * GIB))
        assertEquals(2048, GradleBuildOptions.heapFor(6L * GIB))
        assertEquals(2560, GradleBuildOptions.heapFor(8L * GIB))
        assertEquals(3072, GradleBuildOptions.heapFor(12L * GIB))
    }

    @Test
    fun defaultsKeepTwoWorkersAndNoParallelOrWatching() {
        val options = GradleBuildOptions(maxHeapMb = 2048)
        assertTrue(options.jvmArgs().contains("-Xmx2048m"))
        assertTrue(options.gradleProperties().contains("-Dorg.gradle.workers.max=2"))
        assertTrue(options.gradleProperties().contains("-Dorg.gradle.parallel=false"))
        assertTrue(options.gradleProperties().contains("-Dorg.gradle.vfs.watch=false"))
        assertTrue(options.gradleProperties().contains("-Dkotlin.daemon.jvmargs=-Xmx2048m"))
    }

    @Test
    fun theProjectRecordOverridesTheDefaultsAndProxy() {
        File(tmp.root, ".closecode.json").writeText(
            """
            {
              "gradle": {
                "heapMb": 3072,
                "workers": 1,
                "parallel": true,
                "fileWatching": true,
                "proxyHost": "127.0.0.1",
                "proxyPort": 8080,
                "proxyUser": "u",
                "proxyPassword": "p"
              }
            }
            """.trimIndent(),
        )
        val options = GradleBuildOptions.forProject(tmp.root)
        assertEquals(3072, options.maxHeapMb)
        assertEquals(1, options.workers)
        assertTrue(options.parallel)
        assertTrue(options.fileWatching)
        assertTrue(options.jvmArgs().contains("-Dhttp.proxyHost=127.0.0.1"))
        assertTrue(options.jvmArgs().contains("-Dhttps.proxyPort=8080"))
    }

    @Test
    fun aMissingOrBrokenRecordFallsBackToTheDeviceDefault() {
        val options = GradleBuildOptions.forProject(tmp.root)
        assertFalse(options.parallel)
        assertTrue(options.maxHeapMb >= 1024)
        File(tmp.root, ".closecode.json").writeText("{ not json")
        assertFalse(GradleBuildOptions.forProject(tmp.root).parallel)
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
