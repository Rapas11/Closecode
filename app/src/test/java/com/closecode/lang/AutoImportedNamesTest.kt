package com.closecode.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Names Kotlin imports by default.
 *
 * The API index is built from `android.jar`, which carries the `java.lang` and `java.io`
 * classes — so the index knows `System` and `File` even though a Kotlin file names them without
 * an import. Reporting them as missing an import put an error on code that compiles, and the
 * build refuses to run while an error is on the screen: a project that used `System` could not
 * be built at all.
 */
private val index = AndroidApiIndex.parse(
    """
    C|java.lang.System|System|java.lang.Object
    C|java.lang.Object|Object|
    C|java.lang.Math|Math|java.lang.Object
    C|java.lang.Integer|Integer|java.lang.Number
    C|java.io.File|File|java.lang.Object
    C|java.util.Comparator|Comparator|java.lang.Object
    C|android.widget.TextView|TextView|android.view.View
    M|java.lang.System|currentTimeMillis|0|Long|
    """.trimIndent(),
)

private fun unresolved(source: String): List<String> =
    KotlinAnalyzer.analyze(source, index).errors
        .filter { it.code == DiagnosticCodes.E0101_UNRESOLVED }
        .map { it.message }

class AutoImportedNamesTest {

    @Test
    fun javaLangClassesAreNeverMissingAnImport() {
        val src = """
            fun now(): Long = System.currentTimeMillis()
            fun half(value: Int): Double = Math.abs(value.toDouble())
            fun parse(text: String): Int = Integer.parseInt(text)
        """.trimIndent()

        assertEquals(listOf<String>(), unresolved(src))
    }

    @Test
    fun kotlinIoTypesAreNeverMissingAnImport() {
        val src = """
            fun read(path: String): String = File(path).readText()
        """.trimIndent()

        assertEquals(listOf<String>(), unresolved(src))
    }

    @Test
    fun anAndroidClassWithoutItsImportIsStillReported() {
        val src = "val tv = TextView(null)"

        assertEquals(listOf("Unresolved reference 'TextView'"), unresolved(src))
    }

    @Test
    fun theIndexKnowsWhichNamesKotlinImportsItself() {
        assertTrue(index.isAutoImported("System"))
        assertTrue(index.isAutoImported("Math"))
        // `java.io.File` is not auto-imported, but Kotlin's own `kotlin.io.File` is, and the
        // name list is what settles that.
        assertTrue(!index.isAutoImported("File"))
        assertTrue(!index.isAutoImported("TextView"))
    }
}
