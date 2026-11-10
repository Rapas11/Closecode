package com.closecode.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** P1-2: `--json` is a flag, not a positional argument. */
class ConfigCommandTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theJsonFlagIsNotReadAsASettingName() {
        val dir = tmp.newFolder("project")
        val json = ConfigCommand.run(dir, listOf("show", "--json"))

        assertTrue(json.get("ok").asBoolean)
        assertTrue(json.has("settings"))
        assertFalse(json.has("key"))
    }

    @Test
    fun anUnknownFlagIsRejectedWithAnActionableCode() {
        val dir = tmp.newFolder("project")
        val json = ConfigCommand.run(dir, listOf("show", "--nope"))

        assertFalse(json.get("ok").asBoolean)
        assertEquals("unknown_option", json.get("code").asString)
    }

    @Test
    fun aSettingCanStillBeReadByName() {
        val dir = tmp.newFolder("project")
        val json = ConfigCommand.run(dir, listOf("show", "gradleVersion"))

        assertTrue(json.get("ok").asBoolean)
        assertEquals("gradleVersion", json.get("key").asString)
    }
}
