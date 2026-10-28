package com.closecode.control

import org.junit.Assert.assertTrue
import org.junit.Test

class ToolWrappersTest {

    @Test
    fun toolWrappersForwardTheirName() {
        val script = ToolWrappers.forwardScript("/data/user/0/com.closecode/files/opencode", "tool aapt2")

        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains("exec '/data/user/0/com.closecode/files/opencode/bin/closecode' tool aapt2 \"$@\""))
    }

    @Test
    fun theCcAliasForwardsWithoutACommand() {
        val script = ToolWrappers.forwardScript("/data/user/0/com.closecode/files/opencode", "")

        assertTrue(script.contains("exec '/data/user/0/com.closecode/files/opencode/bin/closecode' \"$@\""))
    }

    @Test
    fun theWrapperListIsLimitedToTheSupportedTools() {
        assertTrue(ToolWrappers.tools.containsAll(listOf("aapt2", "d8", "r8", "kotlinc")))
    }
}
