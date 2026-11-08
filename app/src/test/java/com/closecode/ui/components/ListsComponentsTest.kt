package com.closecode.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRangesTest {

    @Test
    fun findsEveryLiteralMatchIgnoringCase() {
        assertEquals(
            listOf(IntRange(0, 8), IntRange(14, 22)),
            searchRanges("CloseCode and closeCode", "closecode"),
        )
    }

    @Test
    fun searchingForRegexCharactersIsLiteral() {
        assertEquals(
            listOf(IntRange(4, 6)),
            searchRanges("app [x] (1)", "[x]"),
        )
    }

    @Test
    fun noMatchesReturnsNothing() {
        assertEquals(emptyList<IntRange>(), searchRanges("calculator", "theme"))
    }
}
