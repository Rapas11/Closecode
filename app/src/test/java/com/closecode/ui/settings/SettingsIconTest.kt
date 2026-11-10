package com.closecode.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsIconTest {

    @Test
    fun editorAndIntelligenceRowsUseTheirOwnIcons() {
        val expected = mapOf(
            "Auto indent" to "menu",
            "Bracket guides" to "braces",
            "Rainbow brackets" to "braces",
            "Sora editor" to "bolt",
            "Auto import" to "down",
            "Parameter hints" to "info",
            "Quick fixes" to "wrench",
        )
        expected.forEach { (row, icon) -> assertEquals("$row icon", icon, rowIcon(row)) }
    }
}
