package com.closecode.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Item 7: the build-tools on the phone include wrappers that start with `#!/bin/bash`.
 * Android has no bash, so those scripts fail with "No such file or directory" — the exact
 * failure the phone reported when `apksigner` was run from build-tools 35.0.0.
 */
class ShebangRepairTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aBashWrapperBecomesOneAndroidCanRun() {
        val dir = tmp.newFolder("build-tools")
        val script = File(dir, "apksigner").apply {
            writeText("#!/bin/bash\n\nexec java -jar \"$0\" \"$@\"\n")
            setExecutable(true, false)
        }

        assertEquals(1, ShebangRepair.repair(dir))

        val text = script.readText()
        assertTrue(text.startsWith("#!/system/bin/sh\n"))
        assertFalse(text.contains("bash"))
        // The body of the script must survive the rewrite.
        assertTrue(text.contains("exec java -jar"))
    }

    @Test
    fun aScriptThatAlreadyWorksIsLeftAlone() {
        val dir = tmp.newFolder("build-tools-ok")
        val script = File(dir, "zipalign").apply {
            writeText("#!/bin/sh\necho zipalign\n")
            setExecutable(true, false)
        }

        assertEquals(0, ShebangRepair.repair(dir))
        assertEquals("#!/bin/sh\necho zipalign\n", script.readText())
    }

    @Test
    fun everyInstalledVersionIsRepaired() {
        val root = tmp.newFolder("build-tools-root")
        listOf("35.0.0", "35.0.1").forEach { version ->
            val dir = File(root, version).apply { mkdirs() }
            File(dir, "apksigner").writeText("#!/bin/bash\necho $version\n")
            File(dir, "aapt2").writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
        }

        assertEquals(2, ShebangRepair.repair(root))
        listOf("35.0.0", "35.0.1").forEach { version ->
            assertTrue(
                File(root, "$version/apksigner").readText().startsWith("#!/system/bin/sh"),
            )
        }
    }

    @Test
    fun aBinaryIsNeverTouched() {
        val dir = tmp.newFolder("build-tools-binary")
        val binary = File(dir, "aapt2").apply {
            writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x02, 0x01))
        }

        ShebangRepair.repair(dir)

        assertEquals(6, binary.readBytes().size)
    }
}
