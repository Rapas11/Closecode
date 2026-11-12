package com.closecode.tools

import java.io.File
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * P1-1: the pip wheel the installer drops into ensurepip must be the one the runtime asks
 * for, and the runtime must be pointed at a wheels directory so it does not depend on that
 * version constant at all.
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
        // The interpreter's build-time configuration, the way a distribution ships it.
        File(root, "lib/python3.14/_sysconfigdata__android.py").writeText(
            "build_time_vars = {\n    'prefix': '/usr',\n}\n",
        )
        return root
    }

    /** A wheel file that really is a wheel. */
    private fun wheel(dir: File, name: String) {
        dir.mkdirs()
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("pip/__init__.py"))
            zip.write("# pip\n".toByteArray())
            zip.closeEntry()
        }
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
    fun aCompleteSetupNeedsNothing() {
        // A version this build does not pin, so the wheel is judged by being a real wheel.
        val root = prefix("26.9.9")
        wheel(File(root, "lib/python3.14/ensurepip/_bundled"), "pip-26.9.9-py3-none-any.whl")
        wheel(PythonPackRepair.wheelsDir(root), "pip-26.9.9-py3-none-any.whl")
        PythonPackRepair.pointInterpreterAtWheels(root, PythonPackRepair.wheelsDir(root))

        assertFalse(PythonPackRepair.needsRepair(root))
        assertTrue(PythonPackRepair.isRepaired(root))
    }

    @Test
    fun theInterpreterIsPointedAtTheWheelsDirectoryOnce() {
        val root = prefix("26.2.2")
        val wheels = PythonPackRepair.wheelsDir(root)

        PythonPackRepair.pointInterpreterAtWheels(root, wheels)
        PythonPackRepair.pointInterpreterAtWheels(root, wheels)

        val text = File(root, "lib/python3.14/_sysconfigdata__android.py").readText()
        assertTrue(text.contains("WHEEL_PKG_DIR"))
        assertTrue(text.contains(wheels.absolutePath))
        assertEquals(1, text.split("WHEEL_PKG_DIR").size - 1)
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
