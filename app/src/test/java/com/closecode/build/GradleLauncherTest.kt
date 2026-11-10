package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * P0-1: toybox xargs keeps the quotes in Gradle's DEFAULT_JVM_OPTS, so the script hands
 * `"-Xmx64m"` to java as the main class. The patched line must still expand $APP_HOME but leave
 * no quote for xargs to keep.
 */
class GradleLauncherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** The line Gradle 8.13 actually ships. */
    private val realLine =
        "DEFAULT_JVM_OPTS='\"-Xmx64m\" \"-Xms64m\"' " +
            "\\\"-javaagent:\$APP_HOME/lib/agents/gradle-instrumentation-agent-8.13.jar\\\""

    @Test
    fun theRealGradleLineLosesItsQuotesAndKeepsTheVariable() {
        val patched = GradleLauncher.patchText(realLine + "\n")
        assertNotNull(patched)
        assertEquals(
            "DEFAULT_JVM_OPTS=\"-Xmx64m -Xms64m -javaagent:\$APP_HOME/lib/agents/" +
                "gradle-instrumentation-agent-8.13.jar\"\n",
            patched,
        )
    }

    @Test
    fun theQuotedJvmOptionsBecomeOneSafeAssignment() {
        val script = """
            #!/bin/sh
            APP_HOME=${'$'}(cd "${'$'}{0%/*}/.." ; pwd -P)
            DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
            exec "${'$'}JAVACMD" "${'$'}DEFAULT_JVM_OPTS" "${'$'}APP_HOME/lib/gradle-launcher-8.13.jar"
        """.trimIndent()
        val patched = GradleLauncher.patchText(script)
        assertNotNull(patched)
        assertTrue(patched!!.contains("DEFAULT_JVM_OPTS=\"-Xmx64m -Xms64m\""))
        assertTrue(patched.contains("\$APP_HOME/lib"))
    }

    @Test
    fun aLineWithoutQuotesIsLeftAlone() {
        assertNull(GradleLauncher.patchText("DEFAULT_JVM_OPTS=\"-Xmx64m -Xms64m\"\n"))
        assertNull(GradleLauncher.patchText("#!/bin/sh\necho hello\n"))
    }

    @Test
    fun aValueThatKeepsItsOwnSingleQuotesIsFlattenedToo() {
        val patched = GradleLauncher.patchText("DEFAULT_JVM_OPTS='-Xmx64m \"-Xms64m\"'\n")
        assertEquals("DEFAULT_JVM_OPTS=\"-Xmx64m -Xms64m\"\n", patched)
    }

    @Test
    fun aVariableInsideSingleQuotesIsLeftAlone() {
        assertNull(GradleLauncher.patchText("DEFAULT_JVM_OPTS='-Xmx64m \"\$KEEP\"'\n"))
    }

    @Test
    fun patchingWritesTheFileAndKeepsItExecutable() {
        val launcher = File(tmp.root, "bin/gradle").apply {
            parentFile.mkdirs()
            writeText("DEFAULT_JVM_OPTS='\"-Xmx64m\"'\n")
        }
        assertTrue(GradleLauncher.patch(launcher))
        assertTrue(launcher.readText().contains("DEFAULT_JVM_OPTS=\"-Xmx64m\""))
        assertTrue(launcher.canExecute())
        // A second pass finds nothing to change.
        assertEquals(false, GradleLauncher.patch(launcher))
    }
}
