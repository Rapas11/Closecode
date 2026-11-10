package com.closecode.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCommandTest {
    @Test
    fun parsesTheDocumentedGradleForm() {
        val options = SetupCommand.parse(
            listOf("--gradle", "--sdk", "35", "--build-tools", "35.0.0", "--yes", "--wifi-only"),
        )
        assertTrue(options.gradle)
        assertEquals(35, options.sdk)
        assertEquals("35.0.0", options.buildTools)
        assertTrue(options.yes)
        assertTrue(options.wifiOnly)
        assertTrue(options.valid)
    }

    @Test
    fun parsesEqualsFormsAndRemoval() {
        assertEquals(
            "35.0.0",
            SetupCommand.parse(listOf("--build-tools=35.0.0")).buildTools,
        )
        assertEquals(36, SetupCommand.parse(listOf("--sdk=36")).sdk)
        assertEquals("sdk", SetupCommand.parse(listOf("--remove=SDK")).remove)
    }

    @Test
    fun emptyOrUnknownArgumentsAreNotAnInstallRequest() {
        assertFalse(SetupCommand.parse(emptyList()).valid)
        assertFalse(SetupCommand.parse(listOf("--what")).valid)
    }

    @Test
    fun pythonAndWebBothSelectTheShellPack() {
        assertTrue(SetupCommand.parse(listOf("--web")).wantsWeb)
        assertTrue(SetupCommand.parse(listOf("--python")).wantsWeb)
    }
}
