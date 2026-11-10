package com.closecode.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-5: the setup self-test must not report a broken tool as ok.
 *
 * The bug this closes: Gradle's launcher printed `Could not find or load main class "-Xmx64m"`
 * and the old check — "did it print something?" — called that a pass.
 */
class SelfTestRulesTest {

    @Test
    fun aRealVersionLinePasses() {
        assertTrue(SelfTestRules.versionLooksRight("gradle", "Gradle 8.13"))
        assertTrue(SelfTestRules.versionLooksRight("java", "openjdk version \"17.0.21\""))
        assertTrue(SelfTestRules.versionLooksRight("aapt2", "Android Asset Packaging Tool (aapt) 2.19"))
        assertTrue(SelfTestRules.versionLooksRight("platform-tools", "Android Debug Bridge version 1.0.41"))
        assertTrue(SelfTestRules.versionLooksRight("python", "Python 3.12.1"))
        assertTrue(SelfTestRules.versionLooksRight("pip", "pip 24.0 from /x/pip"))
        assertTrue(SelfTestRules.versionLooksRight("node", "v22.11.0"))
    }

    @Test
    fun theWrongToolDoesNotPass() {
        assertFalse(SelfTestRules.versionLooksRight("gradle", "Gradle daemon is busy"))
        assertFalse(SelfTestRules.versionLooksRight("aapt2", "Android Debug Bridge version 1.0.41"))
        assertFalse(SelfTestRules.versionLooksRight("platform-tools", "Gradle 8.13"))
        assertFalse(SelfTestRules.versionLooksRight("java", ""))
    }

    @Test
    fun theOldGradleBugIsNotAPass() {
        val output = "Error: Could not find or load main class \"-Xmx64m\""
        assertTrue(SelfTestRules.looksBroken(output))
        assertFalse(SelfTestRules.versionLooksRight("gradle", output))
    }

    @Test
    fun errorTextMeansTheToolDidNotRun() {
        assertTrue(SelfTestRules.looksBroken("Error: something went wrong"))
        assertTrue(SelfTestRules.looksBroken("CANNOT LINK EXECUTABLE"))
        assertTrue(SelfTestRules.looksBroken("bash: gradle: not found"))
        assertFalse(SelfTestRules.looksBroken("Gradle 8.13"))
    }
}
