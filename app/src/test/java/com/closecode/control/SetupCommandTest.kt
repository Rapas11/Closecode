package com.closecode.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCommandTest {
    @Test
    fun parsesTheDocumentedGradleForm() {
        val options = SetupCommand.parse(
            listOf("--gradle", "--sdk", "35", "--build-tools", "35.0.0", "--yes", "--wifi-only"),
        )
        assertTrue(options.gradle)
        assertEquals(35, options.sdk)
        assertEquals("35.0.0", options.buildTools)
        assertTrue(options.yes)
        assertTrue(options.wifiOnly)
        assertTrue(options.valid)
    }

    @Test
    fun parsesEqualsFormsAndRemoval() {
        assertEquals(
            "35.0.0",
            SetupCommand.parse(listOf("--build-tools=35.0.0")).buildTools,
        )
        assertEquals(36, SetupCommand.parse(listOf("--sdk=36")).sdk)
        assertEquals("sdk", SetupCommand.parse(listOf("--remove=SDK")).remove)
    }

    @Test
    fun emptyOrUnknownArgumentsAreNotAnInstallRequest() {
        assertFalse(SetupCommand.parse(emptyList()).valid)
        val unknown = SetupCommand.parse(listOf("--what"))
        assertFalse(unknown.valid)
        assertEquals(listOf("unknown option '--what'"), unknown.invalid)
    }

    @Test
    fun helpNeverStartsAnInstall() {
        val options = SetupCommand.parse(listOf("--help"))
        assertTrue(options.help)
        assertFalse(options.valid)
        assertTrue(options.invalid.isEmpty())
    }

    @Test
    fun missingOptionValuesAreRejected() {
        assertEquals(
            listOf("--sdk needs an Android API number"),
            SetupCommand.parse(listOf("--sdk")).invalid,
        )
        assertEquals(
            listOf("--remove needs a component name"),
            SetupCommand.parse(listOf("--remove")).invalid,
        )
        assertEquals(
            listOf("--sdk needs an Android API number"),
            SetupCommand.parse(listOf("--sdk=abc")).invalid,
        )
    }

    @Test
    fun acceptedGlobalFlagsAreNotReportedAsUnknown() {
        val options = SetupCommand.parse(listOf("--gradle", "--json"))
        assertTrue(options.gradle)
        assertTrue(options.invalid.isEmpty())
    }

    @Test
    fun helpCombinedWithAnInstallOptionRefusesTheInstall() {
        val options = SetupCommand.parse(listOf("--native", "--help"))

        assertFalse(options.help)
        assertFalse(options.valid)
        assertTrue(options.invalid.any { it.contains("--help cannot be combined") })
        // A plain --help is still the help, and asks for nothing.
        val plain = SetupCommand.parse(listOf("--help"))
        assertTrue(plain.help)
        assertFalse(plain.installsSomething())
    }

    @Test
    fun jdk21CanBeInstalledRemovedAndOnly21IsAccepted() {
        assertTrue(SetupCommand.parse(listOf("--java21")).java21)
        assertTrue(SetupCommand.parse(listOf("--jdk", "21")).java21)
        assertTrue(SetupCommand.parse(listOf("--jdk=21")).java21)
        assertEquals("java21", SetupCommand.parse(listOf("--remove=java21")).remove)
        val rejected = SetupCommand.parse(listOf("--jdk", "8"))
        assertFalse(rejected.valid)
        assertTrue(rejected.invalid.single().contains("only offers 21"))
    }

    @Test
    fun pythonAndWebBothSelectTheShellPack() {
        assertTrue(SetupCommand.parse(listOf("--web")).wantsWeb)
        assertTrue(SetupCommand.parse(listOf("--python")).wantsWeb)
    }

    @Test
    fun removalNamesThePackagesTheInstallerNowShips() {
        assertEquals("platform-tools", SetupCommand.parse(listOf("--remove", "platform-tools")).remove)
        assertEquals("licenses", SetupCommand.parse(listOf("--remove=licenses")).remove)
        val usage = SetupCommand.usage()
        assertTrue(usage.contains("platform-tools"))
        assertTrue(usage.contains("licenses"))
    }
}
