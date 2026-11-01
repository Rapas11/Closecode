package com.closecode.ui.project

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedPathsTest {

    @Test
    fun generatedFoldersAreHiddenAtAnyDepth() {
        listOf(
            ".opencode/skill/SKILL.md",
            "node_modules/pkg/index.js",
            "app/build/outputs/apk/debug/app-debug.apk",
            ".gradle/8.9/fileHashes.bin",
            ".idea/workspace.xml",
            "captures/screen.png",
            ".cxx/Debug/obj.o",
            ".externalNativeBuild/cmake/debug/build.ninja",
        ).forEach { path ->
            assertTrue("expected $path to be protected", GeneratedPaths.isProtectedRelative(path))
        }
    }

    @Test
    fun generatedAndSecretFilesAreHidden() {
        listOf(
            ".closecode.json",
            "local.properties",
            ".env",
            "google-services.json",
            "release.jks",
            "release.keystore",
            "server.pem",
            "app.iml",
            "app-debug.apk",
            "bundle.aab",
        ).forEach { path ->
            assertTrue("expected $path to be hidden", GeneratedPaths.isHiddenName(path.substringAfterLast('/')))
        }
    }

    @Test
    fun sourcesAndProjectFilesStayVisible() {
        listOf(
            "app/src/main/java/MainActivity.kt",
            "app/build.gradle.kts",
            "settings.gradle.kts",
            "gradle/wrapper/gradle-wrapper.properties",
            "README.md",
            "AGENTS.md",
            ".gitignore",
            "app/src/main/res/values/strings.xml",
        ).forEach { path ->
            assertFalse("expected $path to stay visible", GeneratedPaths.isProtectedRelative(path))
        }
    }

    @Test
    fun explorerCanRevealGeneratedFilesButNeverGitOrMetadata() {
        assertTrue(GeneratedPaths.isExplorerHidden("build/outputs/app.apk", false, showGenerated = false))
        assertFalse(GeneratedPaths.isExplorerHidden("build/outputs/app.apk", false, showGenerated = true))
        assertTrue(GeneratedPaths.isExplorerHidden(".git/config", false, showGenerated = true))
        assertTrue(GeneratedPaths.isExplorerHidden(".closecode.json", false, showGenerated = true))
    }
}
