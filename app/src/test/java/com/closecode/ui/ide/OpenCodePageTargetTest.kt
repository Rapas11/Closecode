package com.closecode.ui.ide

import com.closecode.ai.projectPageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The AI tab's page target must never be OpenCode's home screen. The home screen only lists
 * what OpenCode itself has already registered, so while a session is still being created the
 * Projects sidebar is empty and looks broken. The project route opens the project instead.
 */
class OpenCodePageTargetTest {

    private val baseUrl = "http://127.0.0.1:4098"
    private val directory = "/data/user/0/com.closecode/files/projects/app"
    private val projectUrl = projectPageUrl(baseUrl, directory)

    @Test
    fun aMissingSessionOpensTheProjectRoute() {
        assertEquals(
            projectUrl,
            targetPagePath(baseUrl, projectUrl, savedPath = null, sessionId = ""),
        )
    }

    @Test
    fun aMissingSessionNeverOpensTheHomeScreen() {
        val home = baseUrl + "/"
        val target = targetPagePath(baseUrl, projectUrl, savedPath = null, sessionId = "")
        assertNotEquals(home, target)
    }

    @Test
    fun anExistingSessionOpensTheSessionRoute() {
        val target = targetPagePath(baseUrl, projectUrl, savedPath = null, sessionId = "ses_1")
        assertEquals("$baseUrl/server/aHR0cDovLzEyNy4wLjAuMTo0MDk4/session/ses_1", target)
    }

    @Test
    fun aSavedPageWinsOverTheProjectRoute() {
        val target = targetPagePath(baseUrl, projectUrl, savedPath = "/session/ses_9", sessionId = "")
        assertEquals("$baseUrl/session/ses_9", target)
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
}
