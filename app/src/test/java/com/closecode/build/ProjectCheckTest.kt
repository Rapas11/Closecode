package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The project check: the rule table from the spec, one test per rule, against projects
 * the app's own scaffold writes.
 */
class ProjectCheckTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun template(
        packageName: String = "com.closecode.biz",
        minSdk: String = "29",
        targetSdk: String = "35",
        compileSdk: String = "35",
    ) {
        ProjectFixtures.template(tmp.root, packageName, minSdk, targetSdk, compileSdk)
    }

    private fun report(
        packageName: String = "com.closecode.biz",
        minSdk: Int = 29,
        targetSdk: Int = 35,
        compileSdk: Int = 35,
        otherPackages: Set<String> = emptySet(),
        freeBytes: Long? = null,
        cacheModules: Set<String> = emptySet(),
    ): ProjectCheck.Report = ProjectCheck.check(
        tmp.root,
        "biz",
        ProjectFixtures.templateInput(
            packageName = packageName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            otherPackages = otherPackages,
            freeBytes = freeBytes,
            cacheModules = cacheModules,
        ),
    )

    // -- the package rule -----------------------------------------------------------

    @Test
    fun thePackageRuleFollowsTheSpec() {
        for (name in listOf(
            "com.example.app",
            "a.b",
            "com.closecode.my_app",
            "com.example.a1",
        )) {
            assertTrue(name, ProjectCheck.isValidPackage(name))
        }
        for (name in listOf(
            "",
            "app",
            "com..bad",
            ".com.a",
            "com.",
            "com.1app.b",
            "com.int.app",
            "com.a-b.c",
            "com.cl ass.x",
            "com.null",
        )) {
            assertFalse(name, ProjectCheck.isValidPackage(name))
        }
    }

    @Test
    fun invalidPackageFailsWithThePlainMessage() {
        template("com..bad")
        val report = report("com..bad")
        val finding = report.errors.first { it.message == ProjectCheck.INVALID_PACKAGE_MESSAGE }
        assertEquals(".closecode.json", finding.file)
        assertTrue(
            "and nothing about the manifest",
            report.errors.none { it.file?.endsWith("AndroidManifest.xml") == true },
        )
    }

    @Test
    fun foreignPackageOnlyWarns() {
        template("com.example.test")
        val report = report("com.example.test")
        assertTrue(report.errors.toString(), report.ok)
        assertEquals(1, report.warnings.size)
        assertEquals(
            "This app will be installed as com.example.test.",
            report.warnings.single().message,
        )
    }

    @Test
    fun anotherProjectWithTheSamePackageWarns() {
        template()
        val report = report(otherPackages = setOf("com.closecode.biz"))
        assertTrue(report.ok)
        assertTrue(
            report.warnings.any {
                it.message ==
                    "Another project uses this package. Installing this app would replace that one."
            },
        )
    }

    // -- the Android versions -------------------------------------------------------

    @Test
    fun minimumAndroidVersionIsChecked() {
        template(minSdk = "21")
        val report = report(minSdk = 21)
        assertTrue(report.errors.any { it.message == "The minimum Android version is not valid." })
    }

    @Test
    fun minimumAboveTargetIsInvalid() {
        template(minSdk = "33", targetSdk = "29")
        val report = report(minSdk = 33, targetSdk = 29)
        assertTrue(report.errors.any { it.message == "The minimum Android version is not valid." })
    }

    @Test
    fun oldTargetSdkIsChecked() {
        template(minSdk = "23", targetSdk = "23")
        val report = report(minSdk = 23, targetSdk = 23)
        assertTrue(
            report.errors.any {
                it.message == "The target Android version is too old to install on new phones."
            },
        )
    }

    @Test
    fun compileSdkMustMatchTheInstalledPlatform() {
        template(compileSdk = "34")
        val report = report(compileSdk = 34)
        assertTrue(
            report.errors.any {
                it.message == "This phone has build tools for Android ${ProjectPins.COMPILE_SDK} only."
            },
        )
    }

    // -- the toolchain versions -----------------------------------------------------

    @Test
    fun aDifferentKotlinVersionIsNamedWithItsFile() {
        template()
        val root = File(tmp.root, "build.gradle.kts")
        root.writeText(root.readText().replace("1.9.24", "2.0.21"))
        val report = report()
        val finding = report.errors.first { "Kotlin 2.0.21 is not supported yet" in it.message }
        assertEquals("build.gradle.kts", finding.file)
        assertNotNull(finding.line)
        assertTrue("Kotlin 1.9.24" in finding.message)
    }

    @Test
    fun aDifferentComposeCompilerIsNamed() {
        template()
        val gradle = File(tmp.root, "app/build.gradle.kts")
        gradle.writeText(gradle.readText().replace("1.5.14", "1.5.20"))
        val report = report()
        assertTrue(
            report.errors.any {
                it.message ==
                    "The Compose compiler 1.5.20 is not supported yet. " +
                    "This phone builds with Compose compiler 1.5.14."
            },
        )
    }

    @Test
    fun aDifferentComposeBomIsNamed() {
        template()
        val gradle = File(tmp.root, "app/build.gradle.kts")
        gradle.writeText(gradle.readText().replace("2024.04.01", "2024.09.00"))
        val report = report()
        assertTrue(
            report.errors.any {
                it.message ==
                    "Compose BOM 2024.09.00 is not supported yet. " +
                    "This phone builds with Compose BOM 2024.04.01."
            },
        )
        // The BOM is named once, not once per member it resolves.
        assertTrue(
            report.errors.none { it.message == "Library androidx.compose.ui:ui is not supported yet." },
        )
    }

    // -- plugins and libraries ------------------------------------------------------

    @Test
    fun unsupportedPluginsAreNamed() {
        template()
        val gradle = File(tmp.root, "app/build.gradle.kts")
        gradle.writeText(
            gradle.readText().replace(
                "plugins {",
                "plugins {\n    id(\"org.jetbrains.kotlin.plugin.serialization\") version \"1.9.24\"",
            ),
        )
        val report = report()
        assertTrue(
            report.errors.any {
                it.message ==
                    "Plugin org.jetbrains.kotlin.plugin.serialization is not supported yet."
            },
        )
    }

    @Test
    fun unsupportedLibrariesAreNamed() {
        template()
        val gradle = File(tmp.root, "app/build.gradle.kts")
        gradle.writeText(
            gradle.readText().replace(
                "dependencies {",
                "dependencies {\n    implementation(\"com.patrykandpatrick.vico:compose:1.14.0\")",
            ),
        )
        val report = report()
        assertTrue(
            report.errors.any {
                it.message == "Library com.patrykandpatrick.vico:compose is not supported yet."
            },
        )
    }

    @Test
    fun aPinnedLibraryAtTheWrongVersionIsNamed() {
        template()
        val gradle = File(tmp.root, "app/build.gradle.kts")
        gradle.writeText(gradle.readText().replace("core-ktx:1.13.1", "core-ktx:1.15.0"))
        val report = report()
        assertTrue(
            report.errors.any {
                it.message == "Library androidx.core:core-ktx is not supported yet."
            },
        )
    }

    @Test
    fun theLibraryCacheCoversWhatItHolds() {
        template()
        val gradle = File(tmp.root, "app/build.gradle.kts")
        gradle.writeText(
            gradle.readText().replace(
                "dependencies {",
                "dependencies {\n    implementation(\"com.foo:bar:1.0\")",
            ),
        )
        assertTrue(
            report().errors.any { it.message == "Library com.foo:bar is not supported yet." },
        )
        assertTrue(
            report(cacheModules = setOf("com.foo:bar:1.0")).errors.none {
                it.message == "Library com.foo:bar is not supported yet."
            },
        )
    }

    // -- what the build files declare that the build does not run -------------------

    @Test
    fun gradleFeaturesTheBuildDoesNotRunAreNamed() {
        template()
        val gradle = File(tmp.root, "app/build.gradle.kts")
        gradle.writeText(
            gradle.readText()
                .replace(
                    "    compileOptions {",
                    "    productFlavors {\n        free {\n        }\n    }\n\n    compileOptions {",
                )
                .replace(
                    "    defaultConfig {",
                    "    defaultConfig {\n        buildConfigField(\"String\", \"X\", \"\\\"x\\\"\")",
                ),
        )
        val report = report()
        assertTrue(
            report.errors.any { it.message == "The productFlavors block is not supported yet." },
        )
        assertTrue(report.errors.any { it.message == "buildConfigField is not supported yet." })
    }

    // -- the manifest ----------------------------------------------------------------

    @Test
    fun invalidManifestXmlIsNamed() {
        template()
        val manifest = File(tmp.root, "app/src/main/AndroidManifest.xml")
        manifest.writeText(manifest.readText().replace("</manifest>", ""))
        val report = report()
        assertTrue(
            report.errors.any {
                it.message == "The manifest is not valid XML." &&
                    it.file == "app/src/main/AndroidManifest.xml"
            },
        )
    }

    @Test
    fun aManifestWithoutALauncherScreenIsNamed() {
        template()
        val manifest = File(tmp.root, "app/src/main/AndroidManifest.xml")
        manifest.writeText(
            manifest.readText().replace(Regex("<intent-filter>[\\s\\S]*?</intent-filter>"), ""),
        )
        val report = report()
        assertTrue(report.errors.any { it.message == "The app has no screen to open." })
    }

    // -- sources and storage ---------------------------------------------------------

    @Test
    fun aProjectWithoutSourcesIsNamed() {
        template()
        File(tmp.root, "app/src/main/java").deleteRecursively()
        val report = report()
        assertTrue(report.errors.any { it.message == "No sources. Add at least one .kt file." })
    }

    @Test
    fun freeStorageIsChecked() {
        template()
        assertTrue(
            report(freeBytes = 100L * 1024 * 1024).errors.any {
                it.message == "Not enough free storage to build."
            },
        )
        assertTrue(
            report(freeBytes = 2L * 1024 * 1024 * 1024).errors.none {
                it.message == "Not enough free storage to build."
            },
        )
    }

    // -- a project shaped like Biz ---------------------------------------------------

    @Test
    fun bizShapedBuildFilesPassThePackageRuleWithAWarning() {
        ProjectFixtures.bizShaped(tmp.root)
        val report = ProjectCheck.check(tmp.root, "biz")
        // The package is legal and foreign: a warning, never "not packable", and the
        // manifest is not blamed for it.
        assertTrue(
            report.warnings.any { it.message == "This app will be installed as com.example." },
        )
        assertTrue(
            report.errors.none { it.message == ProjectCheck.INVALID_PACKAGE_MESSAGE },
        )
        assertTrue(report.errors.none { it.file?.endsWith("AndroidManifest.xml") == true })
        // And everything the phone cannot do yet is named, one by one.
        val expected = listOf(
            "Plugin org.jetbrains.kotlin.plugin.serialization is not supported yet.",
            "Plugin com.google.devtools.ksp is not supported yet.",
            "Library androidx.navigation:navigation-compose is not supported yet.",
            "Library io.github.jan-tennert.supabase:postgrest-kt is not supported yet.",
            "Library io.github.jan-tennert.supabase:auth-kt is not supported yet.",
            "Library com.patrykandpatrick.vico:compose is not supported yet.",
            "ksp is not supported yet.",
        )
        for (message in expected) {
            assertTrue(message, report.errors.any { it.message == message })
        }
        assertTrue(
            report.errors.any { "Kotlin 2.0.21 is not supported yet" in it.message },
        )
        assertTrue(
            report.errors.any { "The Compose compiler 2.0.21 is not supported yet" in it.message },
        )
    }
}
