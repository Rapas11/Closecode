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
 * server start, must keep a V2 file as it is, and must not depend on anything outside the
 * app's own storage.
 */
class OpenCodeConfigTest {

    private val configHome = Files.createTempDirectory("opencode-config").toFile()

    @Test
    fun theFileLivesWhereTheAppGroundsOpenCode() {
        assertEquals(File(configHome, "opencode/opencode.json"), OpenCodeConfig.file(configHome))
    }

    @Test
    fun theDefaultFileNamesTheSchemaAndOpensTheProject() {
        val json = OpenCodeAiConfig.read(OpenCodeConfig.file(configHome).apply {
            parentFile?.mkdirs()
            writeText(OpenCodeConfig.defaultJson())
        })!!

        assertEquals("https://opencode.ai/config.json", json.get("\u0024schema").asString)
        assertEquals("disable", json.get("update").asString)
        assertTrue(OpenCodeAiConfig.hasWildcardAllow(json))
        assertEquals(
            OpenCodeAiConfig.ASK,
            OpenCodeAiConfig.permission(json, "external_directory"),
        )
        assertFalse(OpenCodeConfig.defaultJson().contains("OPENCODE_ENABLE"))
        assertTrue(
            OpenCodeConfig.defaultJson().contains(
                "\"action\":\"shell\",\"resource\":\"rm *\",\"effect\":\"ask\"",
            ),
        )
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
    fun aSecondStartNeverRewritesAV2File() {
        val file = OpenCodeConfig.installOnce(configHome)!!
        val edited = OpenCodeConfig.defaultJson().replace("\"update\":\"disable\"", "\"update\":\"notify\"")
        file.writeText(edited)

        val written = OpenCodeConfig.installOnce(configHome)

        assertEquals(file, written)
        assertEquals(edited, file.readText())
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
    fun aConfigFromV1IsBackedUpAndGainsTheV2Rules() {
        val file = OpenCodeConfig.installOnce(configHome)!!
        file.writeText(
            "{\n" +
                "  \"${'$'}schema\": \"https://opencode.ai/config.json\",\n" +
                "  \"model\": \"x/y\",\n" +
                "  \"permission\": { \"bash\": \"ask\" }\n" +
                "}\n",
        )

        assertTrue(OpenCodeConfig.installOnce(configHome)!!.isFile)

        val json = OpenCodeAiConfig.read(file)!!
        assertTrue(OpenCodeAiConfig.hasWildcardAllow(json))
        assertEquals("x/y", json.get("model").asString)
        assertFalse(json.has("permission"))
        assertEquals("ask", OpenCodeAiConfig.permission(json, "shell"))
        val backup = File(OpenCodeConfig.directory(configHome), "opencode.v1-backup.json")
        assertTrue(backup.isFile)
        assertTrue(backup.readText().contains("\"permission\""))
    }

    @Test
    fun aV1DenyIsCarriedIntoV2InsteadOfBeingOpened() {
        val file = OpenCodeConfig.installOnce(configHome)!!
        file.writeText(
            "{\n" +
                "  \"\${'$'}schema\": \"https://opencode.ai/config.json\",\n" +
                "  \"permission\": { \"edit\": \"deny\", \"bash\": \"ask\" }\n" +
                "}\n",
        )

        OpenCodeConfig.installOnce(configHome)

        val json = OpenCodeAiConfig.read(file)!!
        assertEquals(OpenCodeAiConfig.DENY, OpenCodeAiConfig.permission(json, "edit"))
        assertEquals(OpenCodeAiConfig.ASK, OpenCodeAiConfig.permission(json, "shell"))
        assertTrue(OpenCodeAiConfig.hasWildcardAllow(json))
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
    fun aV2FileWithoutAStampIsNotRewritten() {
        OpenCodeConfig.installOnce(configHome)
        val file = OpenCodeConfig.file(configHome)
        val original = file.readText()

        assertTrue(OpenCodeConfig.ensureOpenToTheAgent(configHome))

        assertEquals(original, file.readText())
        assertTrue(OpenCodeConfig.stamp(configHome).isFile)
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
