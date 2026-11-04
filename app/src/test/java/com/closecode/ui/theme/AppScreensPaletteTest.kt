package com.closecode.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the app screens design spec's token table against the dark theme, value for value —
 * the same job EditorSpecPaletteTest does for the editor spec.
 */
class AppScreensPaletteTest {

    private fun hex(color: Color): String {
        val a = (color.alpha * 255).toInt()
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return "%02X%02X%02X%02X".format(a, r, g, b)
    }

    @Test
    fun darkThemeMatchesTheSpecTable() {
        val c = DarkColors
        // The November rebase (build 56) moved the dark theme to the handoff's Discord ramp.
        assertEquals("FF2B2D31", hex(c.surface))
        assertEquals("FF3F4147", hex(c.surfaceBorder))
        assertEquals("FF383A40", hex(c.chipBg))
        assertEquals("FF383A40", hex(c.fieldBg))
        assertEquals("FF3F4147", hex(c.fieldBorder))
        assertEquals("FF5865F2", hex(c.primary))
        assertEquals("2E5865F2", hex(c.primaryTint))
        assertEquals("FF5865F2", hex(c.link))
        assertEquals("FF949BA4", hex(c.textMuted))
        assertEquals("FF6D6F78", hex(c.textFaint))
        assertEquals("FFF0B232", hex(c.warning))
        assertEquals("FF1F3B2B", hex(c.statusAddedBg))
        assertEquals("FF3A3115", hex(c.statusModifiedBg))
        assertEquals("FF3A1A1F", hex(c.statusDeletedBg))
        assertEquals("FF404249", hex(c.fileRowActive))
        assertEquals("FFE5784A", hex(c.htmlIcon))
        assertEquals("FF2B2D31", hex(c.keyRowBg))
        assertEquals("FF383A40", hex(c.cmdButtonBg))
        assertEquals("FF383A40", hex(c.cmdAddBg))
    }
}
