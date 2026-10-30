package com.closecode.ai

import com.google.gson.JsonObject
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI settings page edits OpenCode's own configuration file. Every write must survive
 * OpenCode's other known keys, keep the file readable by OpenCode, and never invent keys.
 */
class OpenCodeAiConfigTest {

    @Test
    fun unknownKeysSurviveAPermissionWrite() {
        val json = JsonObject().apply {
            addProperty("${'$'}schema", "https://opencode.ai/config.json")
            addProperty("theme", "dark")
        }

        val next = OpenCodeAiConfig.withPermission(json, "bash", "ask")

        assertEquals("https://opencode.ai/config.json", next.getAsJsonPrimitive("${'$'}schema").asString)
        assertEquals("dark", next.getAsJsonPrimitive("theme").asString)
        assertEquals(
            "ask",
            next.getAsJsonObject("permission")?.get("bash")?.asString,
        )
    }

    @Test
    fun aToolWithNoKeyIsUntouched() {
        val json = JsonObject().apply {
            add("permission", JsonObject().apply { addProperty("bash", "deny") })
        }

        assertTrue(OpenCodeAiConfig.untouched(json, "read"))
        assertFalse(OpenCodeAiConfig.untouched(json, "bash"))
        assertEquals("deny", OpenCodeAiConfig.permission(json, "bash"))
        assertEquals("", OpenCodeAiConfig.permission(json, "read"))
    }

    @Test
    fun thePickerCyclesDefaultAskDenyDefault() {
        assertEquals("ask", OpenCodeAiConfig.permission(OpenCodeAiConfig.withPermissionCycled(JsonObject(), "bash"), "bash"))
        val asked = OpenCodeAiConfig.withPermissionCycled(
            OpenCodeAiConfig.withPermission(JsonObject(), "bash", "ask"), "bash",
        )
        assertEquals("deny", OpenCodeAiConfig.permission(asked, "bash"))
        val cycled = OpenCodeAiConfig.withPermissionCycled(asked, "bash")
        assertFalse(cycled.getAsJsonObject("permission")?.has("bash") == true)
    }

    @Test
    fun aRoundTripPreservesWhatItWrote() {
        val file = Files.createTempDirectory("opencode-ai-config").toFile()
            .let { File(it, "opencode/opencode.json").also { f -> f.parentFile.mkdirs() } }
        val json = OpenCodeAiConfig.withModel(
            OpenCodeAiConfig.withPermission(JsonObject(), "edit", "ask"),
            "anthropic/claude-sonnet-5-5",
        )
        assertTrue(OpenCodeAiConfig.save(file, json))

        val read = OpenCodeAiConfig.read(file)

        assertEquals("anthropic/claude-sonnet-5-5", OpenCodeAiConfig.model(read!!))
        assertEquals("ask", OpenCodeAiConfig.permission(read, "edit"))
    }

    @Test
    fun aBlankModelRemovesTheKey() {
        val json = OpenCodeAiConfig.withModel(
            JsonObject().apply { addProperty("model", "anthropic/claude") }, "",
        )
        assertFalse(json.has("model"))
    }

    @Test
    fun anUnreadableFileIsNotAConfig() {
        val file = Files.createTempDirectory("opencode-ai-broken").toFile()
            .let { File(it, "opencode/opencode.json").also { f -> f.parentFile.mkdirs() } }
        file.writeText("not json at all")
        assertTrue(OpenCodeAiConfig.read(file) == null)
    }
}
