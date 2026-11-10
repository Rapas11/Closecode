package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(content: String) {
        File(tmp.root, ProjectSettings.FILE).writeText(content)
    }

    @Test
    fun pinsOnDiskAreRead() {
        write(
            """
            {
              "engine": "gradle",
              "sdk": 35,
              "buildTools": "35.0.1",
              "gradleVersion": "8.13",
              "nodeVersion": "20",
              "pythonVersion": "3.12",
              "env": {"API_URL": "https://example.test"},
              "ports": {"serve": 4321}
            }
            """.trimIndent(),
        )
        val settings = ProjectSettings.read(tmp.root)
        assertEquals("gradle", settings.engine)
        assertEquals(35, settings.sdk)
        assertEquals("35.0.1", settings.buildTools)
        assertEquals("8.13", settings.gradleVersion)
        assertEquals("https://example.test", settings.env["API_URL"])
        assertEquals(4321, settings.ports["serve"])
    }

    @Test
    fun settingAKeyKeepsKeysCloseCodeDoesNotKnow() {
        write("""{"custom":{"keep":true},"sdk":33}""")
        val result = ProjectSettings.set(tmp.root, "sdk", "34")
        assertTrue(result.isSuccess)
        assertEquals(34, result.getOrThrow().sdk)
        val raw = File(tmp.root, ProjectSettings.FILE).readText()
        assertTrue(raw.contains("\"custom\""))
        assertTrue(raw.contains("\"keep\""))
    }

    @Test
    fun envAndPortsAreNested() {
        assertTrue(ProjectSettings.set(tmp.root, "env.API_URL", "http://x").isSuccess)
        assertTrue(ProjectSettings.set(tmp.root, "ports.serve", "3001").isSuccess)
        val settings = ProjectSettings.read(tmp.root)
        assertEquals("http://x", settings.env["API_URL"])
        assertEquals(3001, settings.ports["serve"])
    }

    @Test
    fun anUnknownTypeIsRefused() {
        assertTrue(ProjectSettings.set(tmp.root, "type", "rust").isFailure)
    }

    @Test
    fun unsetRemovesOnlyThatKey() {
        write("""{"sdk":35,"gradleVersion":"8.13"}""")
        assertTrue(ProjectSettings.unset(tmp.root, "sdk").isSuccess)
        val settings = ProjectSettings.read(tmp.root)
        assertEquals(null, settings.sdk)
        assertEquals("8.13", settings.gradleVersion)
    }

    @Test
    fun aMissingFileIsJustDefaults() {
        val settings = ProjectSettings.read(tmp.root)
        assertEquals(null, settings.engine)
        assertTrue(settings.env.isEmpty())
        assertTrue(settings.ports.isEmpty())
    }
}
