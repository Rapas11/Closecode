package com.closecode.device.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-7 and P1-7: the two answers `closecode install` can give before Android is involved.
 */
class InstallChecksTest {

    private val installed = listOf("8f2d65c4cea633f9edde5dac3f1711b91d6d80cf9b34134fc5b72c654029ba49")
    private val other = listOf("a6811008ffe66d61dd679b8a1954418a642e9a065de06cccd10a9bac3f41ddcc")

    @Test
    fun theSameCertificateMatches() {
        assertTrue(InstallChecks.certificatesMatch(installed, installed))
    }

    @Test
    fun aDifferentCertificateDoesNotMatch() {
        assertFalse(InstallChecks.certificatesMatch(installed, other))
    }

    @Test
    fun anUnknownCertificateIsNotAMismatch() {
        // Nothing to compare means nothing to complain about.
        assertTrue(InstallChecks.certificatesMatch(emptyList(), other))
        assertTrue(InstallChecks.certificatesMatch(installed, emptyList()))
    }

    @Test
    fun theMismatchAnswerNamesBothCertificatesAndTheWayOut() {
        val json = InstallChecks.signatureMismatch("com.rapas.redit", installed, other)

        assertFalse(json.get("ok").asBoolean)
        assertEquals("signature_mismatch", json.get("code").asString)
        assertTrue(json.get("needsUser").asBoolean)
        assertEquals("com.rapas.redit", json.get("package").asString)
        assertEquals(installed.first(), json.get("installedSha256").asString)
        assertEquals(other.first(), json.get("apkSha256").asString)
        val actions = json.getAsJsonArray("actions").map { it.asString }
        assertTrue(actions.contains("closecode apps uninstall com.rapas.redit"))
        assertTrue(json.get("remedy").asString.contains("Uninstall"))
    }

    @Test
    fun thePermissionAnswerIsActionable() {
        val json = InstallChecks.permissionNeeded("com.closecode", openedSettings = false)

        assertFalse(json.get("ok").asBoolean)
        assertEquals("needs_permission", json.get("code").asString)
        assertTrue(json.get("needsUser").asBoolean)
        assertTrue(json.get("needsPermission").asBoolean)
        assertEquals("REQUEST_INSTALL_PACKAGES", json.get("permission").asString)
        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", json.get("url").asString)
        assertEquals("com.closecode", json.get("package").asString)
        assertTrue(json.get("remedy").asString.isNotBlank())
        assertTrue(json.getAsJsonArray("actions").map { it.asString }
            .contains("closecode install --open-settings"))
        assertFalse(json.has("openedSettings"))
    }

    @Test
    fun thePermissionAnswerSaysWhenItOpenedTheScreen() {
        val json = InstallChecks.permissionNeeded("com.closecode", openedSettings = true)
        assertTrue(json.get("openedSettings").asBoolean)
    }
}
