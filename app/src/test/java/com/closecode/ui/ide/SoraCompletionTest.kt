package com.closecode.ui.ide

import com.closecode.lang.AndroidApiIndex
import com.closecode.ui.model.FileKind
import org.junit.Assert.assertTrue
import org.junit.Test

class SoraCompletionTest {

    @Test
    fun suggestionsAreOffWhenTheSettingIsOff() {
        val source = "val greeting = \"hi\"\ngre"
        assertTrue(
            soraCompletionModels(
                source = source,
                offset = source.length,
                language = "Kotlin",
                activeKind = FileKind.Kotlin,
                enabled = false,
                api = AndroidApiIndex.EMPTY,
                projectSymbols = emptySet(),
            ).isEmpty(),
        )
    }

    @Test
    fun kotlinCompletesTheWordBeingTyped() {
        val source = "val greeting = \"hi\"\ngre"
        val items = soraCompletionModels(
            source = source,
            offset = source.length,
            language = "Kotlin",
            activeKind = FileKind.Kotlin,
            enabled = true,
            api = AndroidApiIndex.EMPTY,
            projectSymbols = emptySet(),
        )
        assertTrue(items.any { it.label == "greeting" })
    }

    @Test
    fun markupCompletesATagName() {
        val source = "<di"
        val items = soraCompletionModels(
            source = source,
            offset = source.length,
            language = "HTML",
            activeKind = FileKind.Html,
            enabled = true,
            api = AndroidApiIndex.EMPTY,
            projectSymbols = emptySet(),
        )
        assertTrue(items.any { it.label == "div" })
    }

    @Test
    fun markupOffersAttributesAfterTheTagName() {
        val source = "<div "
        val items = soraCompletionModels(
            source = source,
            offset = source.length,
            language = "HTML",
            activeKind = FileKind.Html,
            enabled = true,
            api = AndroidApiIndex.EMPTY,
            projectSymbols = emptySet(),
        )
        assertTrue(items.any { it.label == "class" })
    }
}
