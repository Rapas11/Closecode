package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleUserPropertiesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writesTheArmAapt2AndJdkForWrapperBuilds() {
        val home = tmp.newFolder("gradle-home")
        val aapt2 = File(tmp.newFolder("build-tools"), "aapt2").apply { writeText("arm64") }
        val javaHome = tmp.newFolder("jdk17")
        File(javaHome, "bin").mkdirs()
        File(javaHome, "bin/java").writeText("java")

        assertTrue(GradleUserProperties.ensure(home, aapt2, javaHome))

        val text = File(home, "gradle.properties").readText()
        assertTrue(text.contains("${GradleUserProperties.AAPT2_KEY}=${aapt2.absolutePath}"))
        assertTrue(text.contains("${GradleUserProperties.JAVA_HOME_KEY}=${javaHome.absolutePath}"))
    }

    @Test
    fun replacesOldValuesAndKeepsEverythingElse() {
        val home = tmp.newFolder("gradle-home")
        val aapt2 = File(tmp.newFolder("build-tools"), "aapt2").apply { writeText("arm64") }
        File(home, "gradle.properties").writeText(
            "# user settings\n" +
                "org.gradle.parallel=true\n" +
                "${GradleUserProperties.AAPT2_KEY}=/old/aapt2\n" +
                "${GradleUserProperties.AAPT2_KEY}=/duplicate/aapt2\n",
        )

        assertTrue(GradleUserProperties.ensure(home, aapt2, null))

        val text = File(home, "gradle.properties").readText()
        assertTrue("# user settings" in text)
        assertTrue("org.gradle.parallel=true" in text)
        assertTrue("${GradleUserProperties.AAPT2_KEY}=${aapt2.absolutePath}" in text)
        assertFalse("/old/aapt2" in text)
        assertFalse("/duplicate/aapt2" in text)
        assertEquals(1, text.lines().count { it.startsWith("${GradleUserProperties.AAPT2_KEY}=") })
    }

    @Test
    fun doesNothingWithoutARealAapt2OrJdk() {
        val home = tmp.newFolder("gradle-home")

        assertFalse(GradleUserProperties.ensure(home, null, null))
        assertFalse(File(home, "gradle.properties").exists())
    }
}
