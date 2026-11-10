package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectTypeDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, content: String = ""): File =
        File(tmp.root, path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    @Test
    fun anAndroidManifestAndPluginMakeAGradleAndroidProject() {
        file("app/src/main/AndroidManifest.xml", "<manifest/>")
        file("build.gradle.kts", "plugins { id(\"com.android.application\") }")
        val detection = ProjectTypeDetector.detect(tmp.root)
        assertEquals(ProjectTypeDetector.GRADLE_ANDROID, detection.primary)
        assertTrue(detection.types.contains(ProjectTypeDetector.GRADLE_ANDROID))
        assertFalse(detection.overridden)
    }

    @Test
    fun packageJsonWithIndexHtmlIsMixed() {
        file("package.json", "{}")
        file("index.html", "<html/>")
        val detection = ProjectTypeDetector.detect(tmp.root)
        assertEquals(ProjectTypeDetector.NPM, detection.primary)
        assertTrue(detection.mixed)
        assertTrue(detection.types.contains(ProjectTypeDetector.STATIC_HTML))
    }

    @Test
    fun aBareIndexHtmlIsStatic() {
        file("index.html", "<html/>")
        assertEquals(ProjectTypeDetector.STATIC_HTML, ProjectTypeDetector.detect(tmp.root).primary)
    }

    @Test
    fun pythonAndCmakeAreRecognised() {
        file("requirements.txt", "fastapi\n")
        assertEquals(ProjectTypeDetector.PYTHON, ProjectTypeDetector.detect(tmp.root).primary)
        File(tmp.root, "requirements.txt").delete()
        file("CMakeLists.txt", "project(x)")
        assertEquals(ProjectTypeDetector.CMAKE, ProjectTypeDetector.detect(tmp.root).primary)
    }

    @Test
    fun theTypePinWinsEvenWithoutFiles() {
        file(ProjectSettings.FILE, """{"type":"python"}""")
        val detection = ProjectTypeDetector.detect(tmp.root)
        assertEquals(ProjectTypeDetector.PYTHON, detection.primary)
        assertTrue(detection.overridden)
    }

    @Test
    fun anEnginePinIsRead() {
        file(ProjectSettings.FILE, """{"engine":"closecode"}""")
        assertEquals(ProjectSettings.ENGINE_CLOSECODE, ProjectSettings.read(tmp.root).engine)
    }
}
