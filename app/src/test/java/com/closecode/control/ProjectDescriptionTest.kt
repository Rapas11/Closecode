package com.closecode.control

import com.closecode.build.BuildSetup
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDescriptionTest {

    @Test
    fun readsSourcesSdksAndSavedValues() {
        val root = Files.createTempDirectory("closecode-description").toFile()
        try {
            File(root, "app/src/main/java/com/example").mkdirs()
            File(root, "app/src/main/java/com/example/Main.kt").writeText("fun main() = Unit\n")
            File(root, "app/src/main/java/com/example/Other.java").writeText("class Other {}\n")
            File(root, "app/src/main/res/values").mkdirs()
            File(root, "app/src/main/res/values/strings.xml").writeText("<resources/>\n")
            File(root, "app/src/main/assets").mkdirs()
            File(root, "app/src/main/assets/data.txt").writeText("x")
            File(root, "app/src/main/AndroidManifest.xml").writeText(
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                   <application><activity android:name=".MainActivity"/></application>
                   </manifest>""",
            )
            File(root, "app/build.gradle.kts").writeText(
                """
                android {
                    namespace = "com.example.app"
                    compileSdk = 35
                    defaultConfig {
                        minSdk = 28
                        targetSdk = 35
                        versionCode = 7
                        versionName = "1.2"
                    }
                }
                """.trimIndent(),
            )
            val project = BuildSetup.Project(
                slug = "demo",
                name = "Demo",
                directory = root,
                savedPackage = "com.closecode.demo",
                savedMinSdk = 29,
            )

            val facts = ProjectDescription.read(project)

            assertEquals("com.closecode.demo", facts.packageName)
            assertEquals(29, facts.minSdk)
            assertEquals(35, facts.targetSdk)
            assertEquals(7, facts.versionCode)
            assertEquals("1.2", facts.versionName)
            assertEquals(1, facts.kotlinSources)
            assertEquals(1, facts.javaSources)
            assertEquals(1, facts.resourceFiles)
            assertEquals(1, facts.assetFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun emptyProjectIsStillReadable() {
        val root = Files.createTempDirectory("closecode-empty").toFile()
        try {
            val facts = ProjectDescription.read(
                BuildSetup.Project(slug = "empty", name = "Empty", directory = root),
            )
            assertEquals(0, facts.kotlinSources)
            assertEquals(0, facts.javaSources)
            assertTrue(facts.apk == null)
        } finally {
            root.deleteRecursively()
        }
    }
}
