package com.closecode.build

import org.junit.Assert.assertEquals
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
}
