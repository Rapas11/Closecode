package com.closecode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodePageTest {

    private val base = "http://127.0.0.1:4098"

    @Test
    fun aSessionPageKeepsItsPathAndQuery() {
        val key = openCodeServerKey(base)

        assertEquals(
            "/server/$key/session/ses_1?tab=chat",
            openCodePagePath(
                base,
                "$base/server/$key/session/ses_1?tab=chat",
            ),
        )
    }

    @Test
    fun aDraftPageKeepsItsDraftID() {
        assertEquals(
            "/new-session?draftId=draft-1",
            openCodePagePath(base, "$base/new-session?draftId=draft-1"),
        )
    }

    @Test
    fun aPageFromAnotherOriginIsNotSaved() {
        assertNull(
            openCodePagePath(
                base,
                "http://127.0.0.1:4099/server/a2V5/session/ses_1",
            ),
        )
    }

    @Test
    fun homeAndTheBareProjectRouteAreNotRestorable() {
        assertFalse(isRestorableOpenCodePage("/"))
        assertFalse(isRestorableOpenCodePage("/L3Byb2plY3Q/session"))
        assertFalse(isRestorableOpenCodePage("/new-session"))
        assertFalse(isRestorableOpenCodePage("/new-session?draftId="))
    }

    @Test
    fun sessionRoutesAreRestorable() {
        val key = openCodeServerKey(base)

        assertTrue(isRestorableOpenCodePage("/server/$key/session/ses_1"))
        assertTrue(isRestorableOpenCodePage("/L3Byb2plY3Q/session/ses_1"))
        assertTrue(isRestorableOpenCodePage("/new-session?draftId=draft-1"))
    }

    @Test
    fun aSavedSessionIsMovedToTheCurrentServer() {
        val old = openCodeServerKey("http://127.0.0.1:4098")
        val new = openCodeServerKey("http://127.0.0.1:4099")

        assertEquals(
            "/server/$new/session/ses_1",
            rebaseOpenCodeServerPath(
                "/server/$old/session/ses_1",
                "http://127.0.0.1:4099",
            ),
        )
    }

    @Test
    fun aNonLoopbackServerIsNotRebased() {
        val external = openCodeServerKey("https://example.com")
        val path = "/server/$external/session/ses_1"

        assertEquals(path, rebaseOpenCodeServerPath(path, "http://127.0.0.1:4099"))
    }

    @Test
    fun aDraftRouteIsNotRebased() {
        val path = "/new-session?draftId=draft-1"

        assertEquals(path, rebaseOpenCodeServerPath(path, "http://127.0.0.1:4099"))
    }

    @Test
    fun aPagePathBecomesAUrlOnTheCurrentOrigin() {
        assertEquals(
            "$base/server/a2V5/session/ses_1",
            openCodePageUrl(base, "/server/a2V5/session/ses_1"),
        )
    }
}
