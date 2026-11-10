package com.closecode.control

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-3: only the checks the build needs decide `ok`. The optional ones are reported, but a
 * missing NDK or an ungranted notification permission must not turn a working install red.
 */
class DiagnosticRulesTest {

    private fun check(name: String, ok: Boolean, optional: Boolean? = null) = JsonObject().apply {
        addProperty("name", name)
        addProperty("ok", ok)
        if (optional != null) addProperty("optional", optional)
    }

    @Test
    fun aFailingOptionalCheckDoesNotFailTheWholeAnswer() {
        val checks = listOf(
            check("jdk", true),
            check("gradle", true),
            check("native.ndk", false, optional = true),
            check("accessibility", false, optional = true),
        )
        assertTrue(DiagnosticRules.overallOk(checks))
        assertTrue(DiagnosticRules.failedRequired(checks).isEmpty())
    }

    @Test
    fun aFailingRequiredCheckFailsTheWholeAnswer() {
        val checks = listOf(
            check("jdk", true),
            check("sdk.platform_tools", false),
            check("native.ndk", false, optional = true),
        )
        assertFalse(DiagnosticRules.overallOk(checks))
        assertEquals(listOf("sdk.platform_tools"), DiagnosticRules.failedRequired(checks))
    }

    @Test
    fun aCheckWithoutTheOptionalMarkerIsRequired() {
        assertTrue(DiagnosticRules.isRequired(check("jdk", true)))
        assertFalse(DiagnosticRules.isRequired(check("native.ndk", true, optional = true)))
    }

    @Test
    fun anEmptyListIsReady() {
        assertTrue(DiagnosticRules.overallOk(emptyList()))
    }
}
