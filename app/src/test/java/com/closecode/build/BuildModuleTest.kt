package com.closecode.build

import com.closecode.ui.settings.InMemoryKeyValueStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Part B foundation: pinned toolchain, template contract and the honest engine —
 * missing compilers report setup, never fake success.
 */
class BuildModuleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun manifestPinsVersionsSizesAndHashes() {
        val byId = ToolchainManifest.components.associateBy { it.id }
        assertEquals("2.4.0", byId.getValue("kotlin-compiler").version)
        assertEquals("3.46.0", byId.getValue("ecj").version)
        assertEquals("8.13.25", byId.getValue("r8").version)
        assertEquals("8.13.0", byId.getValue("apksig").version)
        for (component in ToolchainManifest.components) {
            assertTrue(component.sizeBytes > 0)
            assertEquals(64, component.sha256.length)
            assertTrue(component.url.startsWith("https://"))
        }
        assertEquals(
            ToolchainManifest.components.sumOf { it.sizeBytes },
            ToolchainManifest.totalBytes,
        )
    }

    @Test
    fun sha256MatchesRealBytes() {
        val file = File(tmp.root, "a.bin")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val expected = MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1, 2, 3))
            .joinToString("") { "%02x".format(it) }
        assertTrue(matchesSha256(file, expected))
        assertFalse(matchesSha256(file, "0".repeat(64)))
    }

    @Test
    fun packageSchemeFollowsTheSlug() {
        assertEquals("com.closecode.biz", TemplateContract.packageForSlug("Biz"))
        assertEquals("com.closecode.my_app", TemplateContract.packageForSlug("My App!"))
    }

    @Test
    fun emptyDirectoryFailsTheContract() {
        val violations = TemplateContract.check(tmp.root, "biz")
        assertTrue(violations.any { it.path.endsWith("AndroidManifest.xml") })
    }

    @Test
    fun templateProjectPassesTheContract() {
        val pkg = "com.closecode.biz"
        File(tmp.root, "app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest package=\"$pkg\"></manifest>")
        }
        File(tmp.root, "app/src/main/java/com/closecode/biz/Main.kt").apply {
            parentFile.mkdirs()
            writeText("package $pkg\n")
        }
        assertTrue(TemplateContract.check(tmp.root, "biz").isEmpty())
    }

    @Test
    fun rogueScriptsAndDepsFailClosed() {
        val pkg = "com.closecode.biz"
        File(tmp.root, "app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest package=\"$pkg\"></manifest>")
        }
        File(tmp.root, "app/src/main/java/com/closecode/biz/Main.kt").apply {
            parentFile.mkdirs()
            writeText("package $pkg\n")
        }
        File(tmp.root, "app/build.gradle.kts").writeText(
            "implementation(\"com.evil:lib:1.0\")\n",
        )
        val violations = TemplateContract.check(tmp.root, "biz")
        assertTrue(violations.any { "allowlist" in it.message })
    }

    @Test
    fun engineReportsContractFailureHonestly() = runBlocking {
        val toolchain = ToolchainStore(File(tmp.root, "toolchain"), InMemoryKeyValueStore())
        val outcome = BuildEngine(toolchain).build(tmp.root, "biz")
        assertTrue(outcome is BuildOutcome.ContractFailed)
    }

    @Test
    fun engineReportsMissingToolchainAsSetup() = runBlocking {
        val pkg = "com.closecode.biz"
        File(tmp.root, "app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest package=\"$pkg\"></manifest>")
        }
        File(tmp.root, "app/src/main/java/com/closecode/biz/Main.kt").apply {
            parentFile.mkdirs()
            writeText("package $pkg\n")
        }
        val toolchain = ToolchainStore(File(tmp.root, "toolchain"), InMemoryKeyValueStore())
        val engine = BuildEngine(toolchain)
        assertTrue(engine.build(tmp.root, "biz") is BuildOutcome.NeedsSetup)
        // And the state says so too — never a fake green bar.
        assertTrue(engine.state.value is BuildState.RequiresSetup)
    }
}
