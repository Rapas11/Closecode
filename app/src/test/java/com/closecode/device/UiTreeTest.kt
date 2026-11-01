package com.closecode.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTreeTest {

    private fun node(
        text: String? = null,
        description: String? = null,
        id: String? = null,
        visible: Boolean = true,
        index: Int = 0,
    ) = UiNode(
        text = text,
        description = description,
        id = id,
        className = "android.widget.TextView",
        bounds = listOf(index, index, index + 100, index + 100),
        clickable = true,
        enabled = true,
        focused = false,
        scrollable = false,
        visible = visible,
        password = false,
        depth = index,
    )

    private val nodes = listOf(
        node(text = "Log in", id = "com.example:id/login", index = 0),
        node(text = "Log in", id = "com.example:id/login_alt", index = 1),
        node(text = "Settings", description = "Open settings", index = 2),
        node(text = "Hidden", visible = false, index = 3),
    )

    private val snapshot = UiSnapshot(
        packageName = "com.example",
        activity = "com.example.MainActivity",
        width = 1080,
        height = 2400,
        nodes = nodes,
        truncated = false,
    )

    @Test
    fun exactTextFindsTheFirstMatchAndIndexPicksTheSecond() {
        val selector = UiSelector(text = "Log in")
        assertEquals(nodes[0], snapshot.find(selector))
        assertEquals(nodes[1], snapshot.find(selector.copy(index = 1)))
        assertNull(snapshot.find(selector.copy(index = 2)))
    }

    @Test
    fun textContainsIsCaseInsensitive() {
        assertEquals(nodes[2], snapshot.find(UiSelector(textContains = "SETT")))
    }

    @Test
    fun idsMatchWholeNamesAndSuffixes() {
        assertEquals(nodes[0], snapshot.find(UiSelector(id = "com.example:id/login")))
        assertEquals(nodes[1], snapshot.find(UiSelector(id = "login_alt")))
    }

    @Test
    fun descriptionsMatchExactly() {
        assertEquals(nodes[2], snapshot.find(UiSelector(description = "Open settings")))
        assertNull(snapshot.find(UiSelector(description = "settings")))
    }

    @Test
    fun invisibleNodesNeverMatch() {
        assertNull(snapshot.find(UiSelector(text = "Hidden")))
    }

    @Test
    fun parseUnderstandsFlagsAndPositionalText() {
        assertEquals(
            UiSelector(text = "Log in", index = 1),
            UiSelector.parse(listOf("--text", "Log in", "--index", "1")),
        )
        assertEquals(UiSelector(text = "Log in"), UiSelector.parse(listOf("Log in")))
        assertEquals(
            UiSelector(id = "login", textContains = "Log"),
            UiSelector.parse(listOf("--id", "login", "--contains", "Log")),
        )
    }

    @Test
    fun parseRejectsAnEmptyOrMalformedSelector() {
        assertNull(UiSelector.parse(emptyList()))
        assertNull(UiSelector.parse(listOf("--text")))
        assertNull(UiSelector.parse(listOf("one", "two")))
        assertNull(UiSelector.parse(listOf("--index", "x")))
    }

    @Test
    fun aSelectorNeedsAtLeastOneCriterion() {
        assertFalse(UiSelector().matches(nodes[0]))
    }

    @Test
    fun boundsGiveTheTapCenter() {
        assertEquals(50, nodes[0].centerX)
        assertEquals(50, nodes[0].centerY)
        assertNotNull(nodes[0].bounds)
        assertTrue(nodes[0].clickable)
    }
}
