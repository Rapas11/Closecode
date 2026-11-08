package com.closecode.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words the two settings controls draw.
 *
 * Neither is a Compose test: what a row says is decided by a plain function, so it is pinned here
 * where it can be read. The picture these tests guard is a settings screen where a switch tells
 * you its state instead of leaving you to decode which way the knob points, and where the option
 * in force is marked by something other than the shade of its text.
 */
class SettingsControlsTest {

    @Test
    fun aSwitchSaysWhetherItIsOn() {
        assertEquals("On", toggleStateLabel(true))
        assertEquals("Off", toggleStateLabel(false))
    }

    @Test
    fun theChoiceInForceCarriesATick() {
        assertEquals("✓ 80%", optionLabel("80", selected = true, unit = "%"))
        assertEquals("✓ Off", optionLabel("Off", selected = true, unit = ""))
    }

    @Test
    fun aChoiceNotInForceIsLeftAlone() {
        assertEquals("80%", optionLabel("80", selected = false, unit = "%"))
        assertEquals("120", optionLabel("120", selected = false, unit = ""))
    }

    @Test
    fun everyToggleStillHasSomewhereToSayItsState() {
        val toggles = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Toggle>()

        assertEquals(24, toggles.size)
        assertEquals(0, toggles.count { it.key == null })
        assertTrue(toggles.any { it.name == "Word wrap" && it.key == BoolSetting.WORD_WRAP })
        assertTrue(toggles.any { it.name == "Performance overlay" && it.key == BoolSetting.FRAME_STATS })
        assertTrue(toggles.any { it.name == "Use pipe terminal" && it.key == BoolSetting.USE_PIPE_TERMINAL })
        assertTrue(toggles.any { it.name == "App lock" && it.key == BoolSetting.APP_LOCK })
        assertTrue(toggles.any { it.name == "Send usage data" && it.key == BoolSetting.SEND_USAGE_DATA })
        assertEquals("On", toggleStateLabel(true))
        assertEquals("Off", toggleStateLabel(false))
    }
}
