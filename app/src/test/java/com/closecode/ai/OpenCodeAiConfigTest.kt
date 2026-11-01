package com.closecode.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeAiConfigTest {

    private fun rules(vararg triples: Triple<String, String, String>): JsonObject =
        JsonObject().apply {
            add("permissions", JsonArray().apply {
                triples.forEach { (action, resource, effect) ->
                    add(JsonObject().apply {
                        addProperty("action", action)
                        addProperty("resource", resource)
                        addProperty("effect", effect)
                    })
                }
            })
        }

    @Test
    fun aWildcardRuleIsReadAsTheToolSetting() {
        val json = rules(Triple("shell", "*", "deny"))

        assertEquals("deny", OpenCodeAiConfig.permission(json, "shell"))
        assertEquals("", OpenCodeAiConfig.permission(json, "read"))
    }

    @Test
    fun aSpecificFileRuleIsNotMistakenForTheToolSetting() {
        val json = rules(
            Triple("*", "*", "allow"),
            Triple("read", "*.env", "ask"),
        )

        assertEquals("", OpenCodeAiConfig.permission(json, "read"))
        assertTrue(OpenCodeAiConfig.hasWildcardAllow(json))
    }

    @Test
    fun steppingWalksAskDenyAndBackToDefault() {
        val asked = OpenCodeAiConfig.withPermissionCycled(JsonObject(), "shell")
        assertEquals("ask", OpenCodeAiConfig.permission(asked, "shell"))

        val denied = OpenCodeAiConfig.withPermissionCycled(asked, "shell")
        assertEquals("deny", OpenCodeAiConfig.permission(denied, "shell"))

        val cleared = OpenCodeAiConfig.withPermissionCycled(denied, "shell")
        assertEquals("", OpenCodeAiConfig.permission(cleared, "shell"))
    }

    @Test
    fun writingAToolKeepsEveryOtherRule() {
        val json = rules(
            Triple("*", "*", "allow"),
            Triple("read", "*.env", "ask"),
        )

        val next = OpenCodeAiConfig.withPermission(json, "shell", "deny")
        val array = next.getAsJsonArray("permissions")

        assertEquals(3, array.size())
        assertEquals("deny", OpenCodeAiConfig.permission(next, "shell"))
        assertEquals("allow", OpenCodeAiConfig.permission(next, "*"))
        assertTrue(
            (0 until array.size()).any { index ->
                val rule = array.get(index).asJsonObject
                rule.get("action").asString == "read" &&
                    rule.get("resource").asString == "*.env" &&
                    rule.get("effect").asString == "ask"
            },
        )
    }

    @Test
    fun removingAToolLeavesTheWildcardAlone() {
        val json = rules(
            Triple("*", "*", "allow"),
            Triple("shell", "*", "deny"),
        )

        val next = OpenCodeAiConfig.withPermission(json, "shell", "")

        assertEquals("", OpenCodeAiConfig.permission(next, "shell"))
        assertEquals("allow", OpenCodeAiConfig.permission(next, "*"))
        assertEquals(1, next.getAsJsonArray("permissions").size())
    }

    @Test
    fun theModelSurvivesAPermissionEdit() {
        val file = Files.createTempFile("opencode-ai", ".json").toFile()
        try {
            val json = JsonParser.parseString(
                """{"model":"provider/model","permissions":[]}""",
            ).asJsonObject
            val next = OpenCodeAiConfig.withModel(
                OpenCodeAiConfig.withPermission(json, "read", "ask"),
                "other/model",
            )

            assertTrue(OpenCodeAiConfig.save(file, next))
            val saved = OpenCodeAiConfig.read(file)!!
            assertEquals("other/model", OpenCodeAiConfig.model(saved))
            assertEquals("ask", OpenCodeAiConfig.permission(saved, "read"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun anUnreadableFileIsNotData() {
        val file = File(System.getProperty("java.io.tmpdir"), "opencode-ai-bad-${System.nanoTime()}")
        file.writeText("{ nope")
        try {
            assertNull(OpenCodeAiConfig.read(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun untouchedIsTrueOnlyWithoutAWildcardRule() {
        val json = rules(
            Triple("read", "*.env", "ask"),
            Triple("shell", "*", "deny"),
        )

        assertTrue(OpenCodeAiConfig.untouched(json, "read"))
        assertFalse(OpenCodeAiConfig.untouched(json, "shell"))
        assertFalse(OpenCodeAiConfig.hasWildcardAllow(json))
    }
}
