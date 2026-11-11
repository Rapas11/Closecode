package com.closecode.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * P1-1: the pip wheel the installer drops into ensurepip must be the one the runtime asks
 * for. Termux's Python 3.14 wants pip 26.2.2; a wheel left behind by an older Python is
 * ignored by ensurepip, so `python3 -m venv` produces an environment with no pip.
 */
class PythonPackRepairTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A Python prefix whose ensurepip asks for [version]. */
    private fun prefix(version: String, name: String = "prefix"): File {
        val root = tmp.newFolder(name)
        val python = File(root, "lib/python3.14/ensurepip")
        python.mkdirs()
        File(python, "__init__.py").writeText(
            "\"\"\"Bootstrap pip.\"\"\"\n" +
                "import os\n" +
                "def _bootstrap(\n" +
                "    import subprocess, sys\n" +
                "    # The bundled wheel.\n" +
                "    _PIP_VERSION = \"$version\"\n" +
                "    return _PIP_VERSION\n",
        )
        File(python, "_bundled").mkdirs()
        return root
    }

    @Test
    fun theWheelIsPinnedToOneVersionAndOneHash() {
        assertTrue(PythonPackRepair.PIP_URL.endsWith("pip-25.2-py3-none-any.whl"))
        assertTrue(PythonPackRepair.PIP_URL.startsWith("https://files.pythonhosted.org/"))
        assertEquals(64, PythonPackRepair.PIP_SHA256.length)
        assertEquals(1_752_557L, PythonPackRepair.PIP_SIZE)
    }

    @Test
    fun theRuntimeDecidesWhichWheelItNeeds() {
        val root = prefix("26.2.2")

        assertEquals("26.2.2", PythonPackRepair.requiredVersion(root))
        assertEquals("pip-26.2.2-py3-none-any.whl", PythonPackRepair.requiredFile(root))
    }

    @Test
    fun aMissingWheelNeedsTheRepair() {
        val root = prefix("26.2.2")

        assertTrue(PythonPackRepair.needsRepair(root))
        assertFalse(PythonPackRepair.isRepaired(root))
    }

    @Test
    fun aWheelFromAnOlderPythonIsNotGoodEnough() {
        val root = prefix("26.2.2")
        // The phone's real failure: the bundle holds pip 25.2, the runtime wants 26.2.2.
        File(root, "lib/python3.14/ensurepip/_bundled/pip-25.2-py3-none-any.whl")
            .writeText("stale wheel")

        assertTrue(PythonPackRepair.needsRepair(root))
        assertFalse(PythonPackRepair.isRepaired(root))
    }

    @Test
    fun aTruncatedWheelNeedsTheRepair() {
        val root = prefix("25.2")
        File(root, "lib/python3.14/ensurepip/_bundled/pip-25.2-py3-none-any.whl")
            .writeText("half a wheel")

        assertTrue(PythonPackRepair.needsRepair(root))
        assertFalse(PythonPackRepair.isRepaired(root))
    }

    @Test
    fun theNewestPythonDirectoryWins() {
        val root = tmp.newFolder("prefix-two-pythons")
        File(root, "lib/python3.9/ensurepip/_bundled").mkdirs()
        val newest = File(root, "lib/python3.14/ensurepip/_bundled").apply { mkdirs() }

        assertEquals(newest, PythonPackRepair.bundledDir(root))
    }

    @Test
    fun aPrefixWithoutPythonNeedsNothing() {
        val root = tmp.newFolder("prefix-without-python")

        assertFalse(PythonPackRepair.needsRepair(root))
        assertFalse(PythonPackRepair.isRepaired(root))
        assertNull(PythonPackRepair.bundledDir(root))
        assertNull(PythonPackRepair.requiredVersion(root))
    }
}
