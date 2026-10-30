package com.closecode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeSessionTest {

    private val directory = "/data/user/0/com.closecode/files/projects/notes"

    @Test
    fun theListIsRootedAtTheProjectAndLimited() {
        val path = OpenCodeSessions.sessionsPath(directory)

        assertTrue(path.startsWith("/session?directory="))
        assertTrue(path.contains("roots=true"))
        assertTrue(path.contains("limit=100"))
    }

    @Test
    fun theNewestRootSessionForThisDirectoryWins() {
        val json = """
            [
              {"id":"old","directory":"$directory","time":{"created":1,"updated":2}},
              {"id":"other","directory":"/data/user/0/com.closecode/files/projects/other","time":{"updated":99}},
              {"id":"child","directory":"$directory","parentID":"old","time":{"updated":100}},
              {"id":"new","directory":"$directory","time":{"created":3,"updated":4}}
            ]
        """.trimIndent()

        assertEquals("new", OpenCodeSessions.chooseSessionId(json, directory))
    }

    @Test
    fun aNullParentIdIsStillARootSession() {
        val json = """
            [
              {"id":"root","directory":"$directory","parentID":null,"time":{"updated":1}}
            ]
        """.trimIndent()

        assertEquals("root", OpenCodeSessions.chooseSessionId(json, directory))
    }

    @Test
    fun aRememberedSessionIsPreferredWhenItStillMatches() {
        val json = """
            [
              {"id":"old","directory":"$directory","time":{"updated":2}},
              {"id":"new","directory":"$directory","time":{"updated":99}}
            ]
        """.trimIndent()

        assertEquals("old", OpenCodeSessions.chooseSessionId(json, directory, "old"))
        assertEquals("new", OpenCodeSessions.chooseSessionId(json, directory, "gone"))
    }

    @Test
    fun aSessionFromAnotherProjectIsNeverUsed() {
        val json = """
            [
              {"id":"other","directory":"/data/user/0/com.closecode/files/projects/other","time":{"updated":99}}
            ]
        """.trimIndent()

        assertNull(OpenCodeSessions.chooseSessionId(json, directory))
        assertNull(OpenCodeSessions.chooseSessionId("not json", directory))
    }

    @Test
    fun aCreatedSessionMustNameTheSameDirectory() {
        assertEquals(
            "ses_1",
            OpenCodeSessions.createdSessionId(
                """{"id":"ses_1","directory":"$directory"}""",
                directory,
            ),
        )
        assertEquals(
            "ses_2",
            OpenCodeSessions.createdSessionId(
                """{"session":{"id":"ses_2","directory":"$directory"}}""",
                directory,
            ),
        )
        assertNull(
            OpenCodeSessions.createdSessionId(
                """{"id":"ses_1","directory":"/somewhere/else"}""",
                directory,
            ),
        )
    }

    @Test
    fun aSessionOpensOnTheCurrentServer() {
        val base = "http://127.0.0.1:4098"
        val key = openCodeServerKey(base)

        assertEquals(
            "$base/server/$key/session/ses_1",
            OpenCodeSessions.sessionPageUrl(base, "ses_1"),
        )
    }
}
