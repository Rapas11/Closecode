package com.closecode.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolsTest {

    @Test
    fun theShippedManifestNamesTheAndroidNativePackages() {
        val manifest = NativeToolsManifest.parse(
            File("src/main/assets/native-tools.json").readText(),
        )
        assertEquals("27.1.12297006", manifest.ndk.version)
        assertEquals("3.22.1", manifest.cmake.version)
        assertTrue(manifest.ndk.url.endsWith("android-ndk-r27b-aarch64.zip"))
        assertTrue(manifest.cmake.url.endsWith("cmake-3.22.1-android-aarch64.zip"))
        assertTrue(manifest.cmake.sha256.isNotBlank())
    }

    @Test
    fun aBrokenManifestIsEmptyRatherThanThrowing() {
        val manifest = NativeToolsManifest.parse("{}")
        assertEquals("", manifest.ndk.version)
        assertEquals(0L, manifest.cmake.sizeBytes)
    }
}
