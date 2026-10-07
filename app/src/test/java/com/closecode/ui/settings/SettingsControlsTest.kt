package com.closecode.ui.settings

import org.junit.Assert.assertEquals
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
        // Ten of the eighteen toggles have no key yet, so they hold their own default and answer
        // to nothing in the store. They still draw a switch, and a switch still says On or Off —
        // the label does not depend on the row having been wired up.
        val toggles = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Toggle>()

        assertEquals(18, toggles.size)
        assertEquals(10, toggles.count { it.key == null })
        assertEquals("On", toggleStateLabel(true))
        assertEquals("Off", toggleStateLabel(false))
    }
}
