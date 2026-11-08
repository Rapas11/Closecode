package com.closecode.ui.ide

import com.closecode.ai.openCodeServerKey
import com.closecode.ai.projectPageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI tab's page target must prefer a real session and fall back to the project-aware home.
 * V2's router has no V1 base64 directory route; the server's working directory is the project.
 */
class OpenCodePageTargetTest {

    private val baseUrl = "http://127.0.0.1:4098"
    private val directory = "/data/user/0/com.closecode/files/projects/app"
    private val projectUrl = projectPageUrl(baseUrl, directory)

    @Test
    fun aMissingSessionOpensTheProjectHome() {
        assertEquals(
            "$baseUrl/",
            targetPagePath(baseUrl, projectUrl, savedPath = null, sessionId = ""),
        )
    }

    @Test
    fun aMissingSessionUsesTheProjectAwareHome() {
        assertEquals(
            projectUrl,
            targetPagePath(baseUrl, projectUrl, savedPath = null, sessionId = ""),
        )
    }

    @Test
    fun anExistingSessionOpensTheV2SessionRoute() {
        val target = targetPagePath(baseUrl, projectUrl, savedPath = null, sessionId = "ses_1")
        assertEquals(
            "$baseUrl/server/${openCodeServerKey(baseUrl)}/session/ses_1",
            target,
        )
    }

    @Test
    fun aSavedV2PageWinsOverTheProjectHome() {
        val saved = "/server/${openCodeServerKey(baseUrl)}/session/ses_9"
        val target = targetPagePath(baseUrl, projectUrl, savedPath = saved, sessionId = "")
        assertEquals("$baseUrl$saved", target)
    }

    @Test
    fun aBlankProjectUrlFallsBackToTheServer() {
        assertEquals(
            baseUrl,
            targetPagePath(baseUrl, projectUrl = "", savedPath = null, sessionId = ""),
        )
    }

    @Test
    fun aBlankServerStaysBlankInsteadOfHome() {
        assertEquals("", targetPagePath("", projectUrl = "", savedPath = null, sessionId = ""))
    }

    @Test
    fun aBlankSavedPathIsIgnored() {
        assertEquals(
            projectUrl,
            targetPagePath(baseUrl, projectUrl, savedPath = "", sessionId = ""),
        )
    }

    @Test
    fun theAuthTokenIsAddedOnceAndNeverShown() {
        val withToken = withAuthToken("$baseUrl/", "abc_123")

        assertEquals("$baseUrl/?auth_token=abc_123", withToken)
        assertEquals("$baseUrl/", displayPageUrl(withToken))
        assertTrue(displayPageUrl(withToken).indexOf("auth_token") < 0)
    }
}
