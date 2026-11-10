package com.closecode.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformStoreVersionTest {
    @Test
    fun agpVersionsMapToTheDocumentedBuildToolsFloor() {
        assertEquals("33.0.2", PlatformStore.requiredBuildToolsForAgp("8.1.4"))
        assertEquals("34.0.0", PlatformStore.requiredBuildToolsForAgp("8.9.2"))
        assertEquals("35.0.0", PlatformStore.requiredBuildToolsForAgp("8.12.0"))
        assertEquals("36.0.0", PlatformStore.requiredBuildToolsForAgp("9.0.0"))
        assertEquals("35.0.1", PlatformStore.requiredBuildToolsForAgp(null))
    }

    /** P0-3: a pinned, checksummed platform-tools package, not whatever the network returns. */
    @Test
    fun platformToolsArePinnedAndChecksummed() {
        assertEquals("34.0.4", PlatformStore.PLATFORM_TOOLS_VERSION)
        assertEquals(4_354_052L, PlatformStore.PLATFORM_TOOLS_SIZE)
        assertEquals(64, PlatformStore.PLATFORM_TOOLS_SHA256.length)
        assertTrue(
            PlatformStore.PLATFORM_TOOLS_URL.startsWith(
                "https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/",
            ),
        )
        assertTrue(PlatformStore.PLATFORM_TOOLS_URL.endsWith("platform-tools-34.0.4-aarch64.tar.xz"))
        assertTrue(PlatformStore.PLATFORM_TOOLS_FILE.endsWith(".tar.xz"))
    }

    /** P0-4: every AGP package check needs the exact hash, keyed by the file AGP reads. */
    @Test
    fun theLicenseFilesCarryTheHashesAgpLooksFor() {
        val sdk = PlatformStore.LicenseFiles["android-sdk-license"]
        assertNotNull(sdk)
        assertTrue(sdk!!.contains("8933bad161af4178b1185d1a37fbf41ea5269c55"))
        assertTrue(sdk.contains("24333f8a63b6825ea9c5514f83c2829b004d1fee"))
        assertTrue(PlatformStore.LicenseFiles.containsKey("android-sdk-preview-license"))
        assertTrue(PlatformStore.LicenseFiles.containsKey("android-sdk-arm-dbt-license"))
        assertTrue(
            PlatformStore.LicenseFiles.values.all { hashes ->
                hashes.isNotEmpty() && hashes.all { it.length == 40 }
            },
        )
    }
}
