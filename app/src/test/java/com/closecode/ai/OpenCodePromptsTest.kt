package com.closecode.ai

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ask AI: the text that leaves an error row must name the file, the line and the rule that
 * makes the agent finish the job by itself, and the body OpenCode receives must be exactly
 * the shape `POST /session/:id/message` reads, with everything escaped.
 */
class OpenCodePromptsTest {

    @Test
    fun thePromptNamesThePlaceAndTheRule() {
        val prompt = OpenCodePrompts.fixPrompt("MainActivity.kt", 12, "cannot resolve symbol Hello")

        assertTrue(prompt.contains("closecode gradle assembleDebug"))
        assertTrue(prompt.contains("MainActivity.kt:12"))
        assertTrue(prompt.contains("cannot resolve symbol Hello"))
    }

    @Test
    fun aLineOfZeroIsTheFileAlone() {
        val prompt = OpenCodePrompts.diagnosticPrompt("Main.cs", 0, "parse error")

        assertFalse(prompt.contains("Main.cs:0"))
        assertTrue(prompt.contains("Main.cs"))
        assertTrue(prompt.contains("parse error"))
    }

    @Test
    fun theBodyIsOneTextPart() {
        val parts = JsonParser.parseString(OpenCodePrompts.bodyJson("hello world"))
        val body = parts.asJsonObject
            .getAsJsonArray("parts")
            .get(0).asJsonObject

        assertEquals("text", body.get("type").asString)
        assertEquals("hello world", body.get("text").asString)
    }

    @Test
    fun theBodyEscapesQuotesAndNewLines() {
        val body = OpenCodePrompts.bodyJson("he said \"no\"\nsecond line")

        val text = JsonParser.parseString(body).asJsonObject
            .getAsJsonArray("parts").get(0).asJsonObject
            .get("text").asString

        assertEquals("he said \"no\"\nsecond line", text)
    }

    @Test
    fun theEndpointReachesTheRightSession() {
        assertEquals(
            "http://127.0.0.1:4098/session/abc/message",
            OpenCodePrompts.endpoint("http://127.0.0.1:4098/", "abc"),
        )
        assertEquals(
            "http://127.0.0.1:4098/session/abc/message",
            OpenCodePrompts.endpoint("http://127.0.0.1:4098", "abc"),
        )
    }

    @Test
    fun nothingSendsWithoutAPlaceToGo() {
        assertFalse(OpenCodePrompts.send("", "abc", "prompt"))
        assertFalse(OpenCodePrompts.send("http://127.0.0.1:4098", "", "prompt"))
    }
}
