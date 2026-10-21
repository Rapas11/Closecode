package com.closecode.ui.ide

import com.closecode.build.PreviewContract
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The pure parts of real previews: which functions can be called by a harness, what source is
 * generated for them, and the cache key that decides whether a picture belongs to this code,
 * device and theme.
 */
class PreviewBuildTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val packageSource = """
        package com.example.ui

        @Preview
        @Composable
        fun HomeScreen() {
            Text("Hello")
        }

        @Preview(showBackground = true)
        fun WithParameters(name: String) {
            Text(name)
        }

        @Preview
        @Composable
        private fun PrivateScreen() {
        }

        class Holder {
            @Preview
            @Composable
            fun NestedScreen() {
            }
        }

        @Preview
        @Composable
        fun <T> GenericScreen() {
        }

        @Preview
        @Composable
        fun String.ExtensionScreen() {
        }

        @Preview
        @Composable
        fun `Backtick Screen`() {
        }

        @Preview
        fun PlainFunction() {
        }
    """.trimIndent()

    @Test
    fun everyPreviewIsDescribedWithWhatTheHarnessNeeds() {
        val functions = previewFunctionsOf(packageSource)
        assertEquals(8, functions.size)
        val byName = functions.associateBy { it.name }
        val home = byName.getValue("HomeScreen")
        assertEquals("com.example.ui", home.packageName)
        assertEquals(5, home.line)
        assertTrue(home.isComposable)
        assertNull(previewUnsupportedReason(home))

        assertEquals(
            "This preview needs parameters.",
            previewUnsupportedReason(byName.getValue("WithParameters")),
        )
        assertEquals(
            "This preview is private, so it cannot be shown.",
            previewUnsupportedReason(byName.getValue("PrivateScreen")),
        )
        assertEquals(
            "This preview is inside a class or function.",
            previewUnsupportedReason(byName.getValue("NestedScreen")),
        )
        assertEquals(
            "This preview needs a type parameter.",
            previewUnsupportedReason(byName.getValue("GenericScreen")),
        )
        assertEquals(
            "This preview needs a receiver.",
            previewUnsupportedReason(byName.getValue("ExtensionScreen")),
        )
        assertEquals(
            "This preview is not @Composable.",
            previewUnsupportedReason(byName.getValue("PlainFunction")),
        )
        assertNull(previewUnsupportedReason(byName.getValue("`Backtick Screen`")))
    }

    @Test
    fun onlyCallablePreviewsBecomeHarnesses() {
        val plan = PreviewBuild.planFor(
            listOf("app/src/main/java/com/example/ui/Screens.kt" to packageSource),
        )
        assertEquals(2, plan.harnesses.size)
        assertTrue(plan.harnesses.any { it.function == "HomeScreen" })
        assertTrue(plan.harnesses.any { it.function == "`Backtick Screen`" })
        // One per unsupported preview: parameters, private, nested, generic, receiver, not
        // composable.
        assertEquals(6, plan.problems.size)
    }

    @Test
    fun generatedHarnessCallsThePackageQualifiedFunction() {
        val plan = PreviewBuild.planFor(
            listOf("app/src/main/java/com/example/ui/Screens.kt" to packageSource),
        )
        val sources = PreviewBuild.writeSources(plan, tmp.newFolder("src"))
        val harnessText = sources.first { it.name == "Harnesses.kt" }.readText()
        val hostText = sources.first { it.name == "PreviewHost.kt" }.readText()
        assertTrue(harnessText.contains("package com.closecode.preview.gen"))
        assertTrue(harnessText.contains("com.example.ui.HomeScreen()"))
        assertTrue(harnessText.contains(": PreviewHost"))
        assertTrue(hostText.contains("interface PreviewHost"))
    }

    @Test
    fun planNamesTheFullyQualifiedHarnessClass() {
        val plan = PreviewBuild.planFor(
            listOf("app/src/main/java/com/example/ui/Screens.kt" to packageSource),
        )
        val dir = tmp.newFolder("plan")
        val apk = File(dir, "preview.apk").apply { writeText("apk") }
        PreviewBuild.writePlan(dir, apk, plan)
        val text = File(dir, PreviewBuild.PLAN_NAME).readText()
        val harness = plan.harnesses.first()
        assertTrue(
            "The plan must name the harness by its package-qualified class, " +
                "because the renderer loads it with Class.forName.",
            text.contains("\"harness\":\"com.closecode.preview.gen.${harness.className}\""),
        )
    }

    @Test
    fun harnessIdIsTheSameEverywhereAndUniquePerLocation() {
        val first = PreviewBuild.harnessId("A.kt", 4, "Home")
        assertEquals(first, PreviewBuild.harnessId("A.kt", 4, "Home"))
        assertNotEquals(first, PreviewBuild.harnessId("A.kt", 5, "Home"))
        assertNotEquals(first, PreviewBuild.harnessId("B.kt", 4, "Home"))
        assertNotEquals(first, PreviewBuild.harnessId("A.kt", 4, "Login"))
    }

    @Test
    fun cacheKeyCoversCodeDeviceAndTheme() {
        val files = listOf("A.kt" to "fun A() {}")
        val base = PreviewBuild.cacheKey(files, 412, 915, dark = false)
        assertEquals(base, PreviewBuild.cacheKey(files, 412, 915, dark = false))
        assertNotEquals(base, PreviewBuild.cacheKey(listOf("A.kt" to "fun B() {}"), 412, 915, false))
        assertNotEquals(base, PreviewBuild.cacheKey(files, 360, 780, false))
        assertNotEquals(base, PreviewBuild.cacheKey(files, 412, 915, true))
        // File order is not content: two folders with the same files share a key.
        val two = listOf("B.kt" to "fun B() {}", "A.kt" to "fun A() {}")
        assertEquals(
            PreviewBuild.cacheKey(two, 412, 915, false),
            PreviewBuild.cacheKey(two.reversed(), 412, 915, false),
        )
    }

    @Test
    fun indexReadsTheHarnessIdAndError() {
        val dir = tmp.newFolder("index")
        File(dir, PreviewContract.INDEX_NAME).writeText(
            """[{"id":"abc","function":"Home","png":"abc.png","widthPx":540,"heightPx":1200},""" +
                """{"id":"def","function":"Broken","png":"","widthPx":0,"heightPx":0,""" +
                """"error":"It threw."}]""",
        )
        val entries = PreviewContract.readIndex(dir)
        assertEquals(2, entries.size)
        assertEquals("abc", entries[0].id)
        assertEquals("abc.png", entries[0].png)
        assertEquals("It threw.", entries[1].error)
    }
}
