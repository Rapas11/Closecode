package com.closecode.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** P1-1: the pip wheel the installer drops into ensurepip is pinned and checksummed. */
class PythonPackRepairTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theWheelIsPinnedToOneVersionAndOneHash() {
        assertTrue(PythonPackRepair.PIP_URL.endsWith("pip-25.2-py3-none-any.whl"))
        assertTrue(PythonPackRepair.PIP_URL.startsWith("https://files.pythonhosted.org/"))
        assertEquals(64, PythonPackRepair.PIP_SHA256.length)
        assertEquals(1_752_557L, PythonPackRepair.PIP_SIZE)
    }

    @Test
    fun anEmptyEnsurepipNeedsTheWheel() {
        val prefix = tmp.newFolder("prefix")
        val bundled = File(prefix, "lib/python3.14/ensurepip/_bundled").apply { mkdirs() }

        assertTrue(PythonPackRepair.needsRepair(prefix))
        assertFalse(PythonPackRepair.isRepaired(prefix))

        File(bundled, PythonPackRepair.PIP_FILE).writeText("wheel")
        assertTrue(PythonPackRepair.needsRepair(prefix))
        assertFalse(PythonPackRepair.isRepaired(prefix))
    }

    @Test
    fun theNewestPythonDirectoryWins() {
        val prefix = tmp.newFolder("prefix-two-pythons")
        File(prefix, "lib/python3.9/ensurepip/_bundled").mkdirs()
        val newest = File(prefix, "lib/python3.14/ensurepip/_bundled").apply { mkdirs() }

        assertEquals(newest, PythonPackRepair.bundledDir(prefix))
    }

    @Test
    fun aPrefixWithoutPythonNeedsNothing() {
        val prefix = tmp.newFolder("prefix-without-python")

        assertFalse(PythonPackRepair.needsRepair(prefix))
        assertFalse(PythonPackRepair.isRepaired(prefix))
        assertEquals(null, PythonPackRepair.bundledDir(prefix))
    }
}
