package com.closecode.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the code editor design spec's Colors and fonts table against the dark theme, value for
 * value. The table is the single source of truth: if a drawing ever disagrees with it, this is
 * the test that says so first.
 */
class EditorSpecPaletteTest {

    private fun hex(color: Color): String {
        val a = (color.alpha * 255).toInt()
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return "%02X%02X%02X%02X".format(a, r, g, b)
    }

    @Test
    fun darkThemeMatchesTheSpecTable() {
        // The November rebase (build 56) moved the dark theme to the handoff's Discord ramp;
        // these pin the rebased values, in the spec table's order.
        val c = DarkColors
        assertEquals("FF313338", hex(c.editorBg))
        assertEquals("FF383A40", hex(c.activeLineBg))
        assertEquals("FF26272B", hex(c.divider))
        assertEquals("FFDBDEE1", hex(c.text))
        assertEquals("FF6D6F78", hex(c.lineNumber))
        assertEquals("FFB5BAC1", hex(c.lineNumberActive))
        assertEquals("FF393C42", hex(c.indentGuide))
        assertEquals("FF5865F2", hex(c.accent))
        assertEquals("FFF23F42", hex(c.error))
        assertEquals("FFC792EA", hex(c.codeKeyword))
        assertEquals("FF4EC9B0", hex(c.codeType))
        assertEquals("FFDCDCAA", hex(c.codeFunction))
        assertEquals("FFCE9178", hex(c.codeString))
        assertEquals("FF2B2D31", hex(c.popupBg))
        assertEquals("FF3F4147", hex(c.popupBorder))
        assertEquals("FF404249", hex(c.popupSelected))
        assertEquals("FFF2F3F5", hex(c.popupText))
        assertEquals("FF949BA4", hex(c.popupMuted))
        assertEquals("33383A40", hex(c.textSelection))
        assertEquals("FF2B2D31", hex(c.toolbarBg))
        assertEquals("FF3F4147", hex(c.toolbarBorder))
        assertEquals("FF5865F2", hex(c.toolbarAccent))
        assertEquals("FFE5C07B", hex(c.bulbGlyph))
        assertEquals("FF2A2615", hex(c.bulbTileBg))
        assertEquals("FF3A1A1F", hex(c.pillBg))
        assertEquals("FF5C2A31", hex(c.pillBorder))
        assertEquals("FFF2A0A0", hex(c.pillText))
        assertEquals("FF23A559", hex(c.gitAdded))
        assertEquals("FFF0B232", hex(c.gitModified))
        assertEquals("FFF23F42", hex(c.gitDeleted))
        assertEquals("FF9BA1A8", hex(c.foldArrow))
        // Hexes the spec gives inline outside the table, rebased with the theme.
        assertEquals("FFF2F3F5", hex(c.brightText))
        assertEquals("FF6D6F78", hex(c.hintText))
        assertEquals("FF949BA4", hex(c.iconTint))
        assertEquals("FF23A559", hex(c.runFill))
        assertEquals("FF2B2D31", hex(c.tabActiveBg))
        assertEquals("FF26272B", hex(c.foldChipBg))
        assertEquals("FF3E4046", hex(c.scrollThumb))
        assertEquals("FFA78BFA", hex(c.badgeP))
        assertEquals("FF23A559", hex(c.badgeF))
        assertEquals("FFF0B232", hex(c.badgeM))
        assertEquals("FFF0B232", hex(c.warnText))
    }

    @Test
    fun everySpecTokenHasALightValueToo() {
        // Not verbatim — the spec only defines dark — but present, so no screen can read an
        // uninitialised token on the light theme. indentGuide is exempt from the opacity check:
        // it is translucent by design on both themes, like the guide rule it replaces.
        val c = LightColors
        listOf(
            c.editorBg, c.activeLineBg, c.divider, c.text, c.lineNumber, c.lineNumberActive,
            c.accent, c.error, c.codeKeyword, c.codeType, c.codeFunction,
            c.codeString, c.popupBg, c.popupBorder, c.popupSelected, c.popupText, c.popupMuted,
            c.textSelection, c.toolbarBg, c.toolbarBorder, c.toolbarAccent, c.bulbTileBg,
            c.bulbGlyph, c.pillBg, c.pillBorder, c.pillText, c.gitAdded, c.gitModified,
            c.gitDeleted, c.foldArrow, c.brightText, c.hintText, c.iconTint, c.runFill,
            c.tabActiveBg, c.foldChipBg, c.scrollThumb, c.badgeP, c.badgeF, c.badgeM, c.warnText,
        ).forEach { assertEquals(1f, it.alpha, 0.001f) }
        assertEquals(0x26 / 255f, c.indentGuide.alpha, 0.01f)
    }
}
