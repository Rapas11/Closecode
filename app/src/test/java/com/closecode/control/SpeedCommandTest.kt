package com.closecode.control

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Item 3 of the build-85 brief: the speed settings a new project gets, offered to the
 * projects that already exist — without the user editing a file by hand.
 */
class SpeedCommandTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theSpeedSettingsAreAddedToAnExistingProject() {
        val project = tmp.newFolder("project")
        File(project, "gradle.properties").writeText(
            "android.useAndroidX=true\n" +
                "android.enableJetifier=true\n" +
                "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n",
        )

        val result = SpeedCommand.apply(project)

        val text = File(project, "gradle.properties").readText()
        assertTrue(text.contains("org.gradle.parallel=true"))
        assertTrue(text.contains("org.gradle.caching=true"))
        assertTrue(text.contains("kotlin.compiler.execution.strategy=in-process"))
        // Everything else is left exactly as it was.
        assertTrue(text.contains("android.useAndroidX=true"))
        assertTrue(text.contains("org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8"))
        // The deprecated line is reported, not removed behind the user's back.
        assertTrue(text.contains("android.enableJetifier=true"))
        assertTrue(result.jetifier)
        assertEquals(3, result.added.size)
    }

    @Test
    fun anOldValueIsReplacedRatherThanDuplicated() {
        val project = tmp.newFolder("project-old")
        File(project, "gradle.properties").writeText(
            "org.gradle.parallel=false\n" +
                "org.gradle.caching=false\n" +
                "kotlin.compiler.execution.strategy=daemon\n",
        )

        SpeedCommand.apply(project)

        val lines = File(project, "gradle.properties").readLines()
        assertEquals(1, lines.count { it.startsWith("org.gradle.parallel=") })
        assertEquals(1, lines.count { it.startsWith("org.gradle.caching=") })
        assertEquals(1, lines.count { it.startsWith("kotlin.compiler.execution.strategy=") })
        assertTrue(lines.contains("org.gradle.parallel=true"))
        assertTrue(lines.contains("kotlin.compiler.execution.strategy=in-process"))
    }

    @Test
    fun aProjectThatAlreadyHasThemIsNotChanged() {
        val project = tmp.newFolder("project-fast")
        File(project, "gradle.properties").writeText(
            SpeedCommand.SPEED_SETTINGS.joinToString("\n") { "${it.first}=${it.second}" } + "\n",
        )
        val before = File(project, "gradle.properties").readText()

        val result = SpeedCommand.apply(project)

        assertFalse(result.changed)
        assertEquals(before, File(project, "gradle.properties").readText())
    }

    @Test
    fun theDeprecatedJetifierIsOnlyDroppedWhenAsked() {
        val project = tmp.newFolder("project-jetifier")
        File(project, "gradle.properties").writeText(
            "android.enableJetifier=true\nandroid.useAndroidX=true\n",
        )

        SpeedCommand.apply(project, dropJetifier = true)

        val text = File(project, "gradle.properties").readText()
        assertFalse(text.contains("android.enableJetifier"))
        assertTrue(text.contains("android.useAndroidX=true"))
    }

    @Test
    fun aProjectWithoutAPropertiesFileGetsOne() {
        val project = tmp.newFolder("project-empty")

        val result = SpeedCommand.apply(project)

        assertTrue(result.changed)
        val text = File(project, "gradle.properties").readText()
        assertTrue(text.contains("org.gradle.parallel=true"))
        assertTrue(text.contains("org.gradle.caching=true"))
        assertTrue(text.contains("kotlin.compiler.execution.strategy=in-process"))
    }
}
