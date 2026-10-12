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
        val report = TemplateContract.check(tmp.root, "biz")
        assertTrue(report.violations.any { it.path.endsWith("AndroidManifest.xml") })
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
        val report = TemplateContract.check(tmp.root, "biz")
        assertTrue(report.ok)
        assertTrue(report.warnings.isEmpty())
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
        val report = TemplateContract.check(tmp.root, "biz")
        assertTrue(report.violations.any { "allowlist" in it.message })
    }

    @Test
    fun kotlincArgsPinJvm17AndCompose() {
        val args = CompileDriver.kotlincArgs(
            sources = listOf(File("A.kt")),
            classpath = listOf(File("android.jar")),
            outputDir = File("out"),
            composePluginJar = File("compose.jar"),
        )
        assertTrue(args.containsAll(listOf("-jvm-target", "17")))
        assertTrue(args.any { it.startsWith("-Xplugin=") && it.endsWith("compose.jar") })
        assertTrue(args.last().endsWith("A.kt"))
    }

    @Test
    fun d8TargetsMinSdk29() {
        val args = CompileDriver.d8Args(
            classes = listOf(File("classes")),
            libs = listOf(File("android.jar")),
            outputDir = File("dex"),
        )
        assertTrue(args.containsAll(listOf("--min-api", "29", "--lib")))
    }

    @Test
    fun diagnosticsParseToFileAndLine() {
        val error = CompileDriver.parseDiagnostic("Main.kt:12:8: error: unresolved reference: foo")
        assertEquals("Main.kt", error?.path)
        assertEquals(12, error?.line)
        assertEquals(CompileDriver.Diagnostic.Kind.Error, error?.kind)
        val warning = CompileDriver.parseDiagnostic("R.java:3: warning: [deprecation] old()")
        assertEquals(CompileDriver.Diagnostic.Kind.Warning, warning?.kind)
        assertEquals(null, CompileDriver.parseDiagnostic("warming up the compiler…"))
    }

    @Test
    fun driverDiagnosticsBecomePanelCards() {
        val parsed = CompileDriver.parseDiagnostic("Main.kt:12:8: error: unresolved reference: foo")
        val card = CompileDriver.toProjectDiagnostic(parsed!!) { it.substringAfterLast('/') }
        assertEquals("Main.kt", card.fileName)
        assertEquals(12, card.diagnostic.line)
    }

    @Test
    fun previewIndexReadsEntriesAndErrors() {
        val root = File(tmp.root, "previews").apply { mkdirs() }
        File(root, "previews.json").writeText(
            "{\"function\":\"Home\",\"png\":\"home.png\",\"widthPx\":132,\"heightPx\":268}," +
                "{\"function\":\"Broken\",\"png\":\"\",\"widthPx\":0,\"heightPx\":0,\"error\":\"boom\"}",
        )
        val entries = PreviewContract.readIndex(root)
        assertEquals(2, entries.size)
        assertEquals("home.png", entries[0].png)
        assertEquals("boom", entries[1].error)
        assertTrue(PreviewContract.readIndex(tmp.root).isEmpty())
    }

    @Test
    fun importedProjectKeepsItsOwnPackageWithAWarning() {
        File(tmp.root, "app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest package=\"com.example.biz\"></manifest>")
        }
        File(tmp.root, "app/src/main/java/com/example/biz/Main.kt").apply {
            parentFile.mkdirs()
            writeText("package com.example.biz\n")
        }
        val report = TemplateContract.check(tmp.root, "biz")
        assertTrue(report.ok)
        assertEquals(1, report.warnings.size)
        assertTrue("clash" in report.warnings.single().message)
    }

    @Test
    fun manifestWithoutAnyPackageFails() {
        File(tmp.root, "app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest></manifest>")
        }
        val report = TemplateContract.check(tmp.root, "biz")
        assertTrue(report.violations.any { "No package declared" in it.message })
    }

    @Test
    fun runnerPackageDerivesFromApp() {
        assertEquals("com.closecode.biz.previewrunner", PreviewContract.runnerPackage("com.closecode.biz"))
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
