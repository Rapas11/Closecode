package com.closecode.ai

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app gives OpenCode its own grounded configuration file. It must exist after a first
 * server start, must never be rewritten, and must not depend on anything outside the app's
 * own storage.
 */
class OpenCodeConfigTest {

    private val configHome = Files.createTempDirectory("opencode-config").toFile()

    @Test
    fun theFileLivesWhereTheAppGroundsOpenCode() {
        assertEquals(File(configHome, "opencode/opencode.json"), OpenCodeConfig.file(configHome))
    }

    @Test
    fun theDefaultFileNamesTheSchemaAndOpensEverything() {
        val json = OpenCodeConfig.defaultJson()
        assertTrue(json.contains("\"${'$'}schema\": \"https://opencode.ai/config.json\""))
        assertFalse(json.contains("OPENCODE_ENABLE"))
        for (tool in listOf("edit", "bash", "webfetch")) {
            assertTrue(json.contains("\"$tool\": \"allow\""))
        }
    }

    @Test
    fun aFirstStartWritesTheFile() {
        assertNull(OpenCodeConfig.file(configHome).takeIf { it.isFile })

        val written = OpenCodeConfig.installOnce(configHome)

        assertNotNull(written)
        assertTrue(OpenCodeConfig.file(configHome).isFile)
        assertEquals(OpenCodeConfig.defaultJson(), written?.readText())
    }

    @Test
    fun aSecondStartNeverRewritesTheFile() {
        val file = OpenCodeConfig.installOnce(configHome)!!
        file.writeText("{ \"api\": true }")

        val written = OpenCodeConfig.installOnce(configHome)

        assertEquals(file, written)
        assertEquals("{ \"api\": true }", file.readText())
    }

    @Test
    fun aBlockedDirectoryWritesNothing() {
        val blockedHome = Files.createTempDirectory("opencode-blocked").toFile()
        val config = File(blockedHome, "config").apply {
            // A file where a directory must be: mkdirs() fails, and nothing is written.
            createNewFile()
        }

        val written = OpenCodeConfig.installOnce(config)

        assertNull(written)
        assertFalse(OpenCodeConfig.file(config).isFile)
    }

    @Test
    fun theWebSearchFlagsAreNamedForTheHostedProviders() {
        assertEquals("OPENCODE_ENABLE_EXA", OpenCodeConfig.WEB_SEARCH_FLAG)
        assertEquals("OPENCODE_ENABLE_PARALLEL", OpenCodeConfig.WEB_SEARCH_FALLBACK_FLAG)
    }

    @Test
    fun aConfigFromAnOlderBuildGainsTheOpenPermissionsOnce() {
        val file = OpenCodeConfig.installOnce(configHome)
        file?.writeText("{\n  \"${'$'}schema\": \"https://opencode.ai/config.json\"\n}\n")

        assertTrue(OpenCodeConfig.ensureOpenToTheAgent(configHome))

        val json = OpenCodeAiConfig.read(OpenCodeConfig.file(configHome))!!
        for (tool in OpenCodeConfig.OPEN_TOOLS) {
            assertEquals(OpenCodeAiConfig.ALLOW, OpenCodeAiConfig.permission(json, tool))
        }
        assertTrue(OpenCodeConfig.stamp(configHome).isFile)
        // The keys a reader or model wrote stay put.
        assertEquals("https://opencode.ai/config.json", json.get("\u0024schema").asString)
    }

    @Test
    fun thePermissionUpgradeRunsOnlyOnce() {
        OpenCodeConfig.installOnce(configHome)
        assertTrue(OpenCodeConfig.ensureOpenToTheAgent(configHome))
        assertTrue(OpenCodeConfig.stamp(configHome).isFile)

        // A hand edit after the stamp is never overwritten by a later start.
        OpenCodeConfig.file(configHome).writeText("{ \"model\": \"x/y\" }")
        assertTrue(OpenCodeConfig.ensureOpenToTheAgent(configHome))
        assertEquals("{ \"model\": \"x/y\" }", OpenCodeConfig.file(configHome).readText())
    }

    @Test
    fun aConfigThatAlreadyOwnsPermissionsIsStampedNotRewritten() {
        OpenCodeConfig.installOnce(configHome)
        val file = OpenCodeConfig.file(configHome)
        file.writeText(
            "{ " +
                "\"permission\": { \"bash\": \"ask\" } " +
                "}",
        )

        assertTrue(OpenCodeConfig.ensureOpenToTheAgent(configHome))

        val json = OpenCodeAiConfig.read(file)!!
        assertEquals(OpenCodeAiConfig.ASK, OpenCodeAiConfig.permission(json, "bash"))
    }

    @Test
    fun noConfigFileMeansNothingToDo() {
        assertTrue(OpenCodeConfig.ensureOpenToTheAgent(configHome))
        assertFalse(OpenCodeConfig.stamp(configHome).isFile)
    }

    @Test
    fun aBrokenConfigFileIsLeftAlone() {
        OpenCodeConfig.installOnce(configHome)
        val stampBefore = OpenCodeConfig.stamp(configHome)
        OpenCodeConfig.file(configHome).writeText("{ no json here")

        assertFalse(OpenCodeConfig.ensureOpenToTheAgent(configHome))
        assertFalse(stampBefore.isFile)
    }
}
