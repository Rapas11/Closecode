package com.closecode.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxIndexTest {

    private val index = TermuxIndex.parse(
        """
        Package: root
        Version: 1.0
        Architecture: aarch64
        Depends: dep (>= 2), missing | virtual
        Filename: pool/root.deb
        Size: 10
        SHA256: aa

        Package: dep
        Version: 2.0
        Architecture: aarch64
        Filename: pool/dep.deb
        Size: 20
        SHA256: bb

        Package: provider
        Version: 1.0
        Architecture: aarch64
        Provides: virtual
        Filename: pool/provider.deb
        Size: 30
        SHA256: cc
        """.trimIndent(),
    )

    @Test
    fun resolvesDependenciesAndAlternatives() {
        val resolved = index.resolve(listOf("root"))

        assertEquals(listOf("dep", "provider", "root"), resolved.map { it.name })
    }

    @Test
    fun keepsRawDependencyAlternatives() {
        val root = index.packageNamed("root")

        assertEquals(listOf("dep (>= 2)", "missing | virtual"), root?.depends)
    }

    @Test
    fun unknownRootFails() {
        val error = runCatching { index.resolve(listOf("nope")) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun architectureMappingFollowsTheAbi() {
        assertEquals("aarch64", ShellToolsRepo.architecture(listOf("arm64-v8a")))
        assertEquals("x86_64", ShellToolsRepo.architecture(listOf("x86_64")))
        assertEquals(null, ShellToolsRepo.architecture(listOf("mips")))
    }
}
