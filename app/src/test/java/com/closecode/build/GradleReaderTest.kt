package com.closecode.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reading build files as data: the version catalog, the pinned set, the unknowns. */
class GradleReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theScaffoldFilesParseToThePinnedSet() {
        ProjectFixtures.template(tmp.root)
        val facts = GradleReader.read(tmp.root)
        assertTrue(facts.hasAppBuildFile)
        assertEquals("com.closecode.biz", facts.packageName)
        assertEquals("1.9.24", facts.kotlinVersion)
        assertEquals("1.5.14", facts.composeCompiler)
        assertEquals("2024.04.01", facts.composeBom)
        assertEquals(29, facts.minSdk)
        assertEquals(35, facts.targetSdk)
        assertEquals(35, facts.compileSdk)
        assertEquals(
            setOf("com.android.application", "org.jetbrains.kotlin.android"),
            facts.plugins.map { it.id }.toSet(),
        )
        assertTrue(facts.unsupported.toString(), facts.unsupported.isEmpty())
        assertEquals(
            setOf(
                "androidx.core:core-ktx",
                "androidx.activity:activity-compose",
                "androidx.compose:compose-bom",
                "androidx.compose.ui:ui",
                "androidx.compose.material3:material3",
            ),
            facts.libraries.map { it.module }.toSet(),
        )
    }

    @Test
    fun theCatalogResolvesAliasesAndRefs() {
        ProjectFixtures.bizShaped(tmp.root)
        val facts = GradleReader.read(tmp.root)
        assertEquals("com.example", facts.packageName)
        assertEquals("2.0.21", facts.kotlinVersion)
        assertEquals("2.0.21", facts.composeCompiler)
        assertEquals("2024.09.00", facts.composeBom)
        assertEquals(26, facts.minSdk)
        assertEquals(35, facts.targetSdk)
        assertEquals(1, facts.versionCode)
        assertEquals("1.0", facts.versionName)
        assertTrue(
            facts.plugins.any {
                it.id == "com.google.devtools.ksp" && it.version == "2.0.21-1.0.25"
            },
        )
        assertTrue(
            facts.libraries.any {
                it.module == "io.github.jan-tennert.supabase:postgrest-kt" && it.version == "2.6.0"
            },
        )
        assertTrue(
            facts.libraries.any { it.module == "androidx.compose.ui:ui" && it.version == null },
        )
        // Every value carries where it was found.
        assertTrue(facts.kotlinPlace?.file == "build.gradle.kts")
        assertTrue(facts.packagePlace?.file == "app/build.gradle.kts")
    }

    @Test
    fun unknownBlocksAndKeysAreReportedWithFileAndLine() {
        val appText = """
            plugins { id("com.android.application") }
            android {
                namespace = "com.a.b"
                compileSdk = 35
                productFlavors {
                    free { }
                }
                defaultConfig {
                    applicationId = "com.a.b"
                    buildConfigField("String", "X", "\"x\"")
                }
            }
        """.trimIndent()
        val facts = GradleReader.factsFrom(
            rootText = null,
            appText = appText,
            settingsText = null,
            catalogText = null,
        )
        val flavors = facts.unsupported.first { it.what == "The productFlavors block" }
        assertEquals("app/build.gradle.kts", flavors.place.file)
        assertEquals(5, flavors.place.line)
        val field = facts.unsupported.first { it.what == "buildConfigField" }
        assertEquals(10, field.place.line)
    }

    @Test
    fun aGroovyOnlyProjectIsNamedAsSuch() {
        val facts = GradleReader.factsFrom(
            rootText = null,
            appText = null,
            settingsText = null,
            catalogText = null,
            hasAppFile = false,
            hasGroovyApp = true,
        )
        assertTrue(facts.groovyOnly)
    }
}
