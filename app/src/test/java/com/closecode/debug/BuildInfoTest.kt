package com.closecode.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildInfoTest {

    @Test
    fun theLabelNamesVersionBuildCommitAndBuildKind() {
        // The spec's example: CloseCode 0.9.3 (build 142) · a1b2c3d · debug
        assertEquals(
            "CloseCode 0.9.3 (build 142) · a1b2c3d · debug",
            BuildInfo.label("0.9.3", 142, "a1b2c3d", debug = true),
        )
        assertEquals(
            "CloseCode 1.0 (build 3) · 4f2a1b9 · release",
            BuildInfo.label("1.0", 3, "4f2a1b9", debug = false),
        )
    }
}
