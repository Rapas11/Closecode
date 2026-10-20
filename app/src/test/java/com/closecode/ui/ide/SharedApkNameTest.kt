package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedApkNameTest {

    @Test
    fun nameIsAppDebugVersion() {
        assertEquals("Build-test-debug-1.0.apk", sharedApkName("Build test", "1.0"))
    }

    @Test
    fun unsafeCharactersBecomeDashesAndBlankFallsBack() {
        assertEquals("My-App-debug-2.3.apk", sharedApkName("My/App", "2.3"))
        assertEquals("app-debug-1.0.apk", sharedApkName("  ", ""))
    }
}
