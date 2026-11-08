package com.closecode.build

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Applying what Project settings saves: the build files, the manifest, the sources. */
class ProjectFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aPackageChangeMovesTheSourcesAndRewritesTheFiles() {
        ProjectFixtures.template(tmp.root)
        val result = ProjectFiles.applyPackage(tmp.root, "com.closecode.biz", "com.example.test")
        assertTrue(result.problems.toString(), result.ok)
        val gradle = File(tmp.root, "app/build.gradle.kts").readText()
        assertTrue("namespace = \"com.example.test\"" in gradle)
        assertTrue("applicationId = \"com.example.test\"" in gradle)
        val manifest = File(tmp.root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue("package=\"com.example.test\"" in manifest)
        val moved = File(tmp.root, "app/src/main/java/com/example/test")
        assertTrue(moved.isDirectory)
        assertFalse(File(tmp.root, "app/src/main/java/com/closecode/biz").exists())
        assertTrue(
            File(moved, "MainActivity.kt").readText().startsWith("package com.example.test"),
        )
        assertTrue(
            File(moved, "Theme.kt").readText().startsWith("package com.example.test"),
        )
        assertTrue("app/build.gradle.kts" in result.changed)
        assertTrue("app/src/main/java/com/example/test" in result.changed)
    }

    @Test
    fun theRenamedProjectPassesTheCheckUnderItsNewPackage() {
        ProjectFixtures.template(tmp.root)
        ProjectFiles.applyPackage(tmp.root, "com.closecode.biz", "com.example.test")
        val report = ProjectCheck.check(
            tmp.root,
            "biz",
            ProjectFixtures.templateInput(packageName = "com.example.test"),
        )
        assertTrue(report.errors.toString(), report.ok)
        assertTrue(
            report.warnings.any { it.message == "This app will be installed as com.example.test." },
        )
    }

    @Test
    fun anSdkChangeRewritesTheBuildFile() {
        ProjectFixtures.template(tmp.root)
        val result = ProjectFiles.applySdks(tmp.root, 26, 34, 35)
        assertTrue(result.problems.toString(), result.ok)
        val gradle = File(tmp.root, "app/build.gradle.kts").readText()
        assertTrue("minSdk = 26" in gradle)
        assertTrue("targetSdk = 34" in gradle)
        assertTrue("compileSdk = 35" in gradle)
    }

    @Test
    fun aVersionChangeRewritesTheBuildFileAndManifestLabel() {
        ProjectFixtures.template(tmp.root)
        val result = ProjectFiles.applyVersion(tmp.root, "CloseCode Demo", "2.4.1", 27)
        assertTrue(result.problems.toString(), result.ok)
        val gradle = File(tmp.root, "app/build.gradle.kts").readText()
        assertTrue("versionName = \"2.4.1\"" in gradle)
        assertTrue("versionCode = 27" in gradle)
        val strings = File(tmp.root, "app/src/main/res/values/strings.xml").readText()
        assertTrue("<string name=\"app_name\">CloseCode Demo</string>" in strings)
    }

    @Test
    fun anAppNameUpdatesAStringResourceWhenTheManifestPointsAtIt() {
        ProjectFixtures.template(tmp.root)
        val manifest = File(tmp.root, "app/src/main/AndroidManifest.xml")
        manifest.writeText(manifest.readText().replace("android:label=\"Biz\"", "android:label=\"@string/app_name\""))
        val strings = File(tmp.root, "app/src/main/res/values/strings.xml")
        strings.parentFile?.mkdirs()
        strings.writeText("<resources><string name=\"app_name\">Biz</string></resources>")
        val result = ProjectFiles.applyVersion(tmp.root, "Demo & Co", "1.2", 3)
        assertTrue(result.problems.toString(), result.ok)
        assertTrue("<string name=\"app_name\">Demo &amp; Co</string>" in strings.readText())
    }

    @Test
    fun aPackageChangeOnAProjectWithoutSourcesIsStillFine() {
        ProjectFixtures.template(tmp.root)
        File(tmp.root, "app/src/main/java").deleteRecursively()
        val result = ProjectFiles.applyPackage(tmp.root, "com.closecode.biz", "com.example.test")
        assertTrue(result.problems.toString(), result.ok)
        assertTrue(
            "namespace = \"com.example.test\"" in
                File(tmp.root, "app/build.gradle.kts").readText(),
        )
    }

    @Test
    fun missingDeclarationsAreReportedNotGuessed() {
        ProjectFixtures.template(tmp.root)
        File(tmp.root, "app/build.gradle.kts").writeText("android {\n}\n")
        val result = ProjectFiles.applyPackage(tmp.root, "com.closecode.biz", "com.example.test")
        assertFalse(result.ok)
        assertTrue(result.problems.any { "does not declare" in it })
    }
}
