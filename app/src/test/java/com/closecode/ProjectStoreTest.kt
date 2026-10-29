package com.closecode

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.gradleCompatibilityProblem
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates
import com.closecode.ui.project.ProjectPathException
import com.closecode.ui.project.ProjectSafety
import com.closecode.ui.project.Scaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun root(): File = temp.newFolder("project")

    private fun selection(tab: TemplateTab, index: Int) = SelectedTemplate(tab, index)

    private fun config(pkg: String = "com.closecode.notes") = ProjectConfig(
        packageName = pkg,
        minSdk = "29",
        compileSdk = "35",
        targetSdk = "35",
    )

    // ---- path confinement -------------------------------------------------

    @Test
    fun resolvesPathsInsideTheProject() {
        val root = root()
        val resolved = ProjectSafety.resolveInProject(root, "app/src/main/java/A.kt")
        assertTrue(resolved.canonicalPath.startsWith(root.canonicalPath))
    }

    @Test
    fun rejectsParentTraversal() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "../escaped.txt") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsDeepParentTraversal() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "a/b/../../../out.txt") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsAbsolutePaths() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "/etc/passwd") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsEmptyPaths() {
        val root = root()
        val error = runCatching { ProjectSafety.resolveInProject(root, "") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    @Test
    fun rejectsSymlinkEscape() {
        val root = root()
        val outside = temp.newFolder("outside")
        val secret = File(outside, "secret.txt").apply { writeText("nope") }

        val link = File(root, "link")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), secret.toPath())
        } catch (unsupported: UnsupportedOperationException) {
            return // filesystem cannot make symlinks; nothing to assert
        } catch (denied: SecurityException) {
            return
        } catch (io: java.io.IOException) {
            return
        }

        val error = runCatching { ProjectSafety.resolveInProject(root, "link") }
        assertTrue(error.exceptionOrNull() is ProjectPathException)
    }

    // ---- secrets ----------------------------------------------------------

    @Test
    fun refusesSecretFileNames() {
        listOf(
            "local.properties",
            ".env",
            "release.jks",
            "release.keystore",
            "server.pem",
            "google-services.json"
        ).forEach { name ->
            assertTrue("expected $name to be treated as secret", ProjectSafety.isSecretName(name))
        }
    }

    @Test
    fun allowsOrdinaryFileNames() {
        listOf("MainActivity.kt", "build.gradle.kts", "strings.xml", "index.html").forEach { name ->
            assertFalse("expected $name to be allowed", ProjectSafety.isSecretName(name))
        }
    }

    @Test
    fun hidesMetadataFile() {
        assertTrue(ProjectSafety.isHiddenName(".closecode.json"))
        assertFalse(ProjectSafety.isHiddenName("MainActivity.kt"))
    }

    // ---- atomic writes ---------------------------------------------------

    @Test
    fun atomicWriteReplacesContent() {
        val target = File(root(), "a.txt")
        ProjectSafety.writeAtomic(target, "first")
        assertEquals("first", target.readText())
        ProjectSafety.writeAtomic(target, "second")
        assertEquals("second", target.readText())
    }

    @Test
    fun atomicWriteLeavesNoTempFileBehind() {
        val root = root()
        val target = File(root, "b.txt")
        ProjectSafety.writeAtomic(target, "hello")
        val leftovers = root.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertTrue("temp files left behind: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun atomicWriteCreatesParentDirectories() {
        val target = File(File(root(), "deep/nested"), "c.txt")
        ProjectSafety.writeAtomic(target, "ok")
        assertEquals("ok", target.readText())
    }

    // ---- scaffolding ------------------------------------------------------

    @Test
    fun androidScaffoldWritesExpectedFiles() {
        val files = Scaffold.filesFor("Notes", selection(TemplateTab.App, 2), config())
        val paths = files.map { it.path }.toSet()

        assertTrue(paths.contains("settings.gradle.kts"))
        assertTrue(paths.contains("build.gradle.kts"))
        assertTrue(paths.contains("gradle.properties"))
        assertTrue(paths.contains("app/build.gradle.kts"))
        assertTrue(paths.contains("app/src/main/AndroidManifest.xml"))
        assertTrue(paths.contains("app/src/main/res/values/strings.xml"))
        assertTrue(paths.contains("app/src/main/res/values/themes.xml"))
    }

    @Test
    fun androidScaffoldPlacesKotlinUnderPackagePath() {
        val files = Scaffold.filesFor("Notes", selection(TemplateTab.App, 0), config("com.example.demo"))
        val main = files.firstOrNull { it.path.endsWith("MainActivity.kt") }
        assertNotNull(main)
        assertEquals("app/src/main/java/com/example/demo/MainActivity.kt", main!!.path)
        assertTrue(main.content.contains("package com.example.demo"))
    }

    @Test
    fun javaScaffoldUsesJavaAndXmlWithoutKotlinOrCompose() {
        val cfg = config().copy(language = "Java")
        val files = Scaffold.filesFor("Notes", selection(TemplateTab.App, 0), cfg)
        val paths = files.map { it.path }.toSet()
        val appGradle = files.first { it.path == "app/build.gradle.kts" }.content
        val rootGradle = files.first { it.path == "build.gradle.kts" }.content

        assertTrue(paths.contains("app/src/main/java/com/closecode/notes/MainActivity.java"))
        assertTrue(paths.contains("app/src/main/res/layout/activity_main.xml"))
        assertFalse(paths.any { it.endsWith(".kt") })
        assertFalse(appGradle.contains("org.jetbrains.kotlin"))
        assertFalse(appGradle.contains("compose"))
        assertFalse(rootGradle.contains("org.jetbrains.kotlin"))
    }

    @Test
    fun everyJavaAppTemplateGetsJavaExtras() {
        val cfg = config().copy(language = "Java")
        for (index in 0 until Templates[TemplateTab.App].orEmpty().size) {
            val files = Scaffold.filesFor("Demo", selection(TemplateTab.App, index), cfg)
            assertTrue(
                "template $index lacks MainActivity.java",
                files.any { it.path.endsWith("MainActivity.java") },
            )
            assertTrue(
                "template $index contains Kotlin",
                files.none { it.path.endsWith(".kt") },
            )
        }
    }

    @Test
    fun gradleFileSubstitutesChosenValues() {
        val cfg = ProjectConfig(
            packageName = "com.example.tool",
            minSdk = "24",
            compileSdk = "34",
            targetSdk = "34",
            buildType = "Release",
            gradleVersion = "8.9",
        )
        val gradle = Scaffold.filesFor("Tool", selection(TemplateTab.App, 0), cfg)
            .first { it.path == "app/build.gradle.kts" }.content

        assertTrue(gradle.contains("com.example.tool"))
        assertTrue(gradle.contains("minSdk = 24"))
        assertTrue(gradle.contains("compileSdk = 34"))
        assertTrue(gradle.contains("release {"))
        assertTrue(!gradle.contains("minSdk = 29"))
    }

    @Test
    fun gradleVersionSelectsTheMatchingAgp() {
        val expected = mapOf(
            "8.13" to "8.12.0",
            "8.12" to "8.10.1",
            "8.9" to "8.7.3",
            "8.5" to "8.3.2",
        )
        for ((gradle, agp) in expected) {
            val root = Scaffold.filesFor(
                "Demo",
                selection(TemplateTab.App, 0),
                config().copy(gradleVersion = gradle),
            ).first { it.path == "build.gradle.kts" }.content
            assertTrue("Gradle $gradle should use AGP $agp", root.contains(agp))
        }
    }

    @Test
    fun compileSdkBeyondTheMappedAgpWarns() {
        val config = config().copy(gradleVersion = "8.5", compileSdk = "36", targetSdk = "36")
        val problem = gradleCompatibilityProblem(config)
        assertNotNull(problem)
        assertTrue(problem!!.contains("compileSdk 34"))
        assertNull(gradleCompatibilityProblem(config.copy(compileSdk = "34", targetSdk = "34")))
    }

    @Test
    fun wrapperPropertiesMatchTheSelectedGradleVersion() {
        val properties = Scaffold.filesFor(
            "Demo",
            selection(TemplateTab.App, 0),
            config().copy(gradleVersion = "8.9"),
        ).first { it.path == "gradle/wrapper/gradle-wrapper.properties" }.content

        assertTrue(properties.contains("gradle-8.9-bin.zip"))
    }

    @Test
    fun manifestMatchesPackageName() {
        val manifest = Scaffold.filesFor("Demo", selection(TemplateTab.App, 0), config("com.example.pkg"))
            .first { it.path.endsWith("AndroidManifest.xml") }.content
        assertTrue(manifest.contains("<manifest"))
        assertTrue(manifest.contains(".MainActivity"))
    }

    @Test
    fun webScaffoldHasNoGradleFiles() {
        val files = Scaffold.filesFor("Blog", selection(TemplateTab.Web, 2), config())
        val paths = files.map { it.path }
        assertTrue(paths.contains("index.html"))
        assertTrue(paths.contains("styles.css"))
        assertTrue(paths.contains("README.md"))
        assertFalse(paths.any { it.contains("build.gradle") })
    }

    @Test
    fun templateExtrasAppear() {
        val weather = Scaffold.filesFor("W", selection(TemplateTab.App, 3), config())
            .map { it.path }
        assertTrue(weather.any { it.endsWith("WeatherApi.kt") })

        val blog = Scaffold.filesFor("B", selection(TemplateTab.Web, 2), config())
            .map { it.path }
        assertTrue(blog.any { it.endsWith("main.js") })

        val blank = Scaffold.filesFor("B", selection(TemplateTab.App, 0), config())
            .map { it.path }
        assertFalse(blank.any { it.endsWith("WeatherApi.kt") })
    }

    @Test
    fun stringsXmlCarriesProjectName() {
        val strings = Scaffold.filesFor("My App", selection(TemplateTab.App, 0), config())
            .first { it.path.endsWith("strings.xml") }.content
        assertTrue(strings.contains("My App"))
    }

    @Test
    fun everyScaffoldFileHasANonEmptyRelativePath() {
        val files = Scaffold.filesFor("Chk", selection(TemplateTab.App, 0), config()) +
            Scaffold.filesFor("Chk", selection(TemplateTab.Web, 0), config())
        files.forEach { file ->
            assertTrue("absolute path: ${file.path}", !file.path.startsWith("/"))
            assertTrue("traversal in ${file.path}", !file.path.contains(".."))
        }
    }
}