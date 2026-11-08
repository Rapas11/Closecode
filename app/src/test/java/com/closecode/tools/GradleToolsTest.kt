package com.closecode.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleToolsTest {

    @Test
    fun theManifestReadsTheTermuxAndDistributionParts() {
        val manifest = GradleToolsManifest.parse(
            """
            {
              "version": "1",
              "repo": "https://example.test/termux",
              "roots": ["openjdk-17"],
              "required": ["bin/java"],
              "distribution": {
                "version": "8.13",
                "fileName": "gradle-8.13-bin.zip",
                "url": "https://example.test/gradle-8.13-bin.zip",
                "sizeBytes": 123,
                "sha256": "abc"
              }
            }
            """.trimIndent(),
        )
        assertEquals("1", manifest.tools.version)
        assertEquals(listOf("openjdk-17"), manifest.tools.roots)
        assertEquals(listOf("bin/java"), manifest.tools.required)
        assertEquals("8.13", manifest.distribution.version)
        assertEquals("gradle-8.13-bin.zip", manifest.distribution.fileName)
        assertEquals(123L, manifest.distribution.sizeBytes)
        assertEquals("abc", manifest.distribution.sha256)
    }

    @Test
    fun theShippedManifestPointsAtTheJdkInsideTheTermuxPrefix() {
        val manifest = GradleToolsManifest.parse(
            java.io.File("src/main/assets/gradle-tools.json").readText(),
        )
        assertEquals("8.13", manifest.distribution.version)
        assertTrue(
            manifest.tools.required.contains("lib/jvm/java-17-openjdk/bin/java"),
        )
    }

    @Test
    fun aBrokenManifestIsEmptyRatherThanThrowing() {
        val manifest = GradleToolsManifest.parse("{}")
        assertEquals("", manifest.distribution.version)
        assertTrue(manifest.tools.roots.isEmpty())
    }

    @Test
    fun theGradlePrefixKeepsTheTermuxLength() {
        val prefix = TermuxPrefix("com.closecode", "g", "gh")
        assertTrue(prefix.newPrefix.endsWith("/file/g"))
        assertTrue(prefix.newHome.endsWith("/file/gh"))
        assertEquals(prefix.oldPrefix.length, prefix.newPrefix.length)
        assertEquals(prefix.oldHome.length, prefix.newHome.length)
    }
}
