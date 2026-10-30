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
    fun theDefaultFileNamesTheSchema() {
        val json = OpenCodeConfig.defaultJson()
        assertTrue(json.contains("\"${'$'}schema\": \"https://opencode.ai/config.json\""))
        assertFalse(json.contains("OPENCODE_ENABLE"))
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
}
