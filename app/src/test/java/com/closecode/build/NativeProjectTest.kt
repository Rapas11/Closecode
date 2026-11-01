package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeProjectTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun appBuild(): File {
        val app = File(tmp.root, "app").apply { mkdirs() }
        return File(app, "build.gradle.kts").apply {
            writeText(
                """
                android {
                    defaultConfig { }
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun cmakeAndAnNdkVersionMakeANativeProject() {
        val gradle = appBuild()
        gradle.writeText(
            gradle.readText() + "\nandroid { ndkVersion = \"27.1.12297006\" }\n",
        )
        File(tmp.root, "app/src/main").mkdirs()
        File(tmp.root, "app/src/main/CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.22.1)")
        val usage = NativeProject.detect(tmp.root)
        assertTrue(usage.usesNative)
        assertEquals("27.1.12297006", usage.requestedNdkVersion)
        assertTrue(usage.reasons.any { it.what == "CMakeLists.txt" })
        assertTrue(usage.reasons.any { it.what == "ndkVersion" })
    }

    @Test
    fun externalNativeBuildIsNative() {
        val gradle = appBuild()
        gradle.writeText(
            gradle.readText() + "\nandroid { externalNativeBuild { cmake { path = file(\"src/main/CMakeLists.txt\") } } }\n",
        )
        val usage = NativeProject.detect(tmp.root)
        assertTrue(usage.usesNative)
        assertTrue(usage.reasons.any { it.what == "externalNativeBuild" })
    }

    @Test
    fun prebuiltJniLibrariesAreNotNativeSources() {
        appBuild()
        File(tmp.root, "app/src/main/jniLibs/arm64-v8a").mkdirs()
        File(tmp.root, "app/src/main/jniLibs/arm64-v8a/libfoo.so").writeText("elf")
        val usage = NativeProject.detect(tmp.root)
        assertFalse(usage.usesNative)
    }
}
