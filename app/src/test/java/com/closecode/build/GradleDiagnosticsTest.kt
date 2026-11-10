package com.closecode.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleDiagnosticsTest {

    @Test
    fun kotlinWithAColumnIsJumpable() {
        val diagnostic = GradleDiagnostics.parseLine(
            "e: file:///work/app/src/main/Main.kt:12:8: Unresolved reference: foo",
        )
        assertNotNull(diagnostic)
        assertEquals("/work/app/src/main/Main.kt", diagnostic!!.path)
        assertEquals(12, diagnostic.line)
        assertEquals(8, diagnostic.column)
        assertEquals(CompileDriver.Diagnostic.Kind.Error, diagnostic.kind)
        assertEquals("Unresolved reference: foo", diagnostic.message)
    }

    @Test
    fun kotlinK2PairSyntaxIsRead() {
        val diagnostic = GradleDiagnostics.parseLine(
            "w: file:///work/app/src/main/Main.kt: (7, 3): Variable never used",
        )
        assertNotNull(diagnostic)
        assertEquals(7, diagnostic!!.line)
        assertEquals(3, diagnostic.column)
        assertEquals(CompileDriver.Diagnostic.Kind.Warning, diagnostic.kind)
    }

    @Test
    fun javacAndAapt2AndManifestMergerAreRead() {
        val javac = GradleDiagnostics.parseLine(
            "/work/app/src/main/Java.java:4: error: cannot find symbol",
        )
        assertEquals(4, javac!!.line)
        assertEquals(CompileDriver.Diagnostic.Kind.Error, javac.kind)

        val aapt2 = GradleDiagnostics.parseLine(
            "/work/app/src/main/res/values/strings.xml:9: error: resource string/x not found.",
        )
        assertEquals(9, aapt2!!.line)
        assertTrue(aapt2.message.contains("resource string/x"))

        val merger = GradleDiagnostics.parseLine(
            "/work/app/src/main/AndroidManifest.xml:18:5-42 Error: uses-sdk:minSdkVersion 21",
        )
        assertEquals(18, merger!!.line)
        assertEquals(5, merger.column)
        assertEquals(CompileDriver.Diagnostic.Kind.Error, merger.kind)
    }

    @Test
    fun r8AndGradleErrorsBecomeAGradleDiagnostic() {
        val r8 = GradleDiagnostics.parseLine("ERROR: Missing class com.example.Foo")
        assertNotNull(r8)
        assertEquals("<gradle>", r8!!.path)
        assertEquals(CompileDriver.Diagnostic.Kind.Error, r8.kind)
        assertTrue(r8.message.contains("Missing class"))
    }

    @Test
    fun duplicatesAreCollapsedAndNoiseIsIgnored() {
        val output = """
            e: file:///work/Main.kt:1:2: bad
            e: file:///work/Main.kt:1:2: bad
            > Task :app:compileDebugKotlin FAILED
            BUILD FAILED in 3s
        """.trimIndent()
        assertEquals(1, GradleDiagnostics.parse(output).size)
    }
}
