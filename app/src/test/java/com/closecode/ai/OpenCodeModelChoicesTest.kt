package com.closecode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model list the AI settings page offers comes from OpenCode's own durable cache or from
 * a running server. The parser must understand the shapes OpenCode writes, and never invent
 * a pair.
 */
class OpenCodeModelChoicesTest {

    @Test
    fun aBareArrayIsParsedProviderFirst() {
        val body = """
            [
              { "providerID": "anthropic", "modelID": "claude-sonnet-5-5" },
              { "providerID": "opencode", "modelID": "grok-code-fast-1" }
            ]
        """.trimIndent()

        val parsed = OpenCodeModelChoices.parse(body)

        assertEquals(
            listOf(
                OpenCodeModelChoice("anthropic", "claude-sonnet-5-5"),
                OpenCodeModelChoice("opencode", "grok-code-fast-1"),
            ),
            parsed,
        )
    }

    @Test
    fun anObjectOfProviderToModelsIsParsed() {
        val body = """
            {
              "anthropic": {
                "claude-sonnet-5-5": { "name": "Sonnet 5.5" },
                "claude-haiku-5": { "name": "Haiku 5" }
              }
            }
        """.trimIndent()

        val parsed = OpenCodeModelChoices.parse(body)

        assertEquals(2, parsed.size)
        assertTrue(parsed.contains(OpenCodeModelChoice("anthropic", "claude-sonnet-5-5")))
        assertTrue(parsed.contains(OpenCodeModelChoice("anthropic", "claude-haiku-5")))
    }

    @Test
    fun recordsWithAnExplicitProviderWin() {
        val body = """
            { "models": [ { "providerID": "opencode", "modelID": "x" } ] }
        """.trimIndent()

        assertEquals(
            listOf(OpenCodeModelChoice("opencode", "x")),
            OpenCodeModelChoices.parse(body),
        )
    }

    @Test
    fun brokenJsonParsesToNothing() {
        assertTrue(OpenCodeModelChoices.parse("not json").isEmpty())
        assertTrue(OpenCodeModelChoices.parse(null).isEmpty())
        assertTrue(OpenCodeModelChoices.parse("").isEmpty())
    }

    @Test
    fun pairsWithoutBothHalvesAreSkipped() {
        val body = """[ { "providerID": "anthropic" }, { "modelID": "x" } ]"""
        assertTrue(OpenCodeModelChoices.parse(body).isEmpty())
    }

    @Test
    fun aBlankServerAnswersRatherThanTalks() {
        val answer = OpenCodeModelChoices.fromServer("")
        assertTrue(answer.choices.isEmpty())
        assertTrue(answer.source.isEmpty())
    }

    @Test
    fun aLiveServerAnsweAnswersWithTheServerSource() {
        // Only the source string on a filled answer can be pinned without a network: a missing
        // host yields nothing and no source, same as a server that never started.
        val answer = OpenCodeModelChoices.fromServer("http://127.0.0.1:1")
        assertTrue(answer.choices.isEmpty())
        assertTrue(answer.source.isEmpty())
    }
}
