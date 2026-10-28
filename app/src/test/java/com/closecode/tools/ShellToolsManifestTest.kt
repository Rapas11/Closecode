package com.closecode.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellToolsManifestTest {

    @Test
    fun parsesTheManifest() {
        val manifest = ShellToolsManifest.parse(
            """{"version":"1","repo":"https://example.test/repo/","roots":["git","curl"],"required":["bin/git"]}""",
        )

        assertEquals("1", manifest.version)
        assertEquals("https://example.test/repo", manifest.repo)
        assertEquals(listOf("git", "curl"), manifest.roots)
        assertEquals(listOf("bin/git"), manifest.required)
    }

    @Test
    fun buildsIndexAndPackageUrls() {
        assertEquals(
            "https://example.test/repo/dists/stable/main/binary-aarch64/Packages",
            ShellToolsRepo.indexUrl("https://example.test/repo", "aarch64"),
        )
        assertEquals(
            "https://example.test/repo/pool/g/git/git.deb",
            ShellToolsRepo.packageUrl("https://example.test/repo", "pool/g/git/git.deb"),
        )
        assertTrue(ShellToolsRepo.prefixFor("com.closecode").endsWith("/file/u"))
    }
}
