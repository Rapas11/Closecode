package com.closecode.build

import com.closecode.tools.NativeToolchain
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradlePlanTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun toolchain(): GradleToolchain {
        val root = tmp.newFolder("toolchain")
        val javaHome = File(root, "java").apply { mkdirs() }
        val gradle = File(root, "gradle/bin/gradle").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\n")
        }
        File(root, "gradle/lib").mkdirs()
        File(root, "gradle/lib/gradle-launcher-8.13.jar").apply {
            writeText("launcher")
        }
        val aapt2 = File(root, "aapt2").apply { writeText("aapt2") }
        return GradleToolchain(
            javaHome = javaHome,
            gradle = gradle,
            gradleHome = File(root, "home"),
            sdkRoot = File(root, "sdk"),
            platformDir = File(root, "sdk/platforms/android-35"),
            buildToolsDir = File(root, "sdk/build-tools/34.0.4"),
            buildToolsVersion = "34.0.4",
            aapt2 = aapt2,
            aapt2Env = mapOf("LD_LIBRARY_PATH" to "/native"),
            distributionVersion = "8.13",
        )
    }

    @Test
    fun theBuildCommandRunsTheDownloadedGradleWithoutADaemon() {
        val project = tmp.newFolder("project")
        val init = File(tmp.root, "closecode.gradle")
        val toolchain = toolchain()
        val invocation = GradlePlan.build(
            toolchain,
            project,
            "debug",
            "assembleDebug",
            init,
        )
        assertTrue(invocation.command.contains("--no-daemon"))
        assertTrue(invocation.command.contains("--project-dir"))
        assertTrue(invocation.command.contains(project.absolutePath))
        assertTrue(invocation.command.contains("assembleDebug"))
        // P0-1: the launcher jar is the main class; the bin/gradle script is not involved.
        assertEquals(
            File(toolchain.javaHome, "bin/java").absolutePath,
            invocation.command.first(),
        )
        assertTrue(invocation.command.contains("org.gradle.launcher.GradleMain"))
        assertTrue(invocation.command.contains("-Xmx512m"))
        assertFalse(invocation.command.any { it == toolchain.gradle.absolutePath })
        assertTrue(invocation.command.contains("-Psdk.dir=${toolchain.sdkRoot.absolutePath}"))
        assertTrue(invocation.command.any { it.startsWith("-Pandroid.aapt2FromMavenOverride=") })
        assertEquals(project, invocation.workingDir)
        assertEquals(toolchain.sdkRoot.absolutePath, invocation.environment["ANDROID_HOME"])
        assertEquals(toolchain.gradleHome.absolutePath, invocation.environment["GRADLE_USER_HOME"])
        assertTrue(
            invocation.environment.getValue("PATH")
                .contains(File(toolchain.javaHome, "bin").absolutePath),
        )
        assertEquals(
            File(toolchain.javaHome, "lib").absolutePath + ":/native",
            invocation.environment["LD_LIBRARY_PATH"],
        )
    }

    @Test
    fun aNativeProjectGetsTheNdkAndCmakeEnvironment() {
        val root = tmp.newFolder("native-toolchain")
        val ndk = File(root, "sdk/ndk/27.1.12297006").apply { mkdirs() }
        val cmake = File(root, "sdk/cmake/3.22.1").apply {
            File(this, "bin").mkdirs()
            File(this, "bin/cmake").writeText("cmake")
            File(this, "bin/ninja").writeText("ninja")
        }
        val native = NativeToolchain(ndk, cmake, "27.1.12297006", "3.22.1")
        val toolchain = toolchain().copy(
            native = native,
            ndkVersionOverride = native.ndkVersion,
        )
        val invocation = GradlePlan.build(
            toolchain,
            tmp.newFolder("native-project"),
            "debug",
            "assembleDebug",
            File(tmp.root, "native.gradle"),
        )
        assertEquals(ndk.absolutePath, invocation.environment["ANDROID_NDK_HOME"])
        assertEquals(ndk.absolutePath, invocation.environment["ANDROID_NDK_ROOT"])
        assertEquals(
            File(cmake, "bin/cmake").absolutePath,
            invocation.environment["CMAKE"],
        )
        assertTrue(
            invocation.environment.getValue("PATH").startsWith(File(cmake, "bin").absolutePath),
        )
        val script = GradlePlan.initScript("34.0.4", native.ndkVersion)
        assertTrue(script.contains("android.ndkVersion = \"27.1.12297006\""))
    }

    @Test
    fun releaseUsesAssembleRelease() {
        assertEquals("assembleRelease", GradlePlan.taskFor("release"))
        assertEquals("assembleDebug", GradlePlan.taskFor("debug"))
    }

    @Test
    fun theInitScriptForcesTheInstalledBuildTools() {
        val script = GradlePlan.initScript("34.0.4")
        assertTrue(script.contains("com.android.application"))
        assertTrue(script.contains("com.android.library"))
        assertTrue(script.contains("buildToolsVersion = \"34.0.4\""))
    }

    @Test
    fun theNewestApkInTheRequestedTypeWins() {
        val project = tmp.newFolder("apk-project")
        val debug = File(project, "app/build/outputs/apk/debug").apply { mkdirs() }
        val old = File(debug, "old.apk").apply {
            writeText("old")
            setLastModified(1_000)
        }
        val new = File(debug, "new.apk").apply {
            writeText("new")
            setLastModified(2_000)
        }
        File(project, "app/build/outputs/apk/release").mkdirs()
        File(project, "app/build/outputs/apk/release/app-release.apk").writeText("release")
        assertEquals(new, GradlePlan.findApk(project, "debug"))
        assertFalse(old == GradlePlan.findApk(project, "debug"))
    }

    @Test
    fun theNewestBundleInTheOutputFolderWins() {
        val project = tmp.newFolder("bundle-project")
        val release = File(project, "app/build/outputs/bundle/release").apply { mkdirs() }
        val old = File(release, "old.aab").apply {
            writeText("old")
            setLastModified(1_000)
        }
        val new = File(release, "app-release.aab").apply {
            writeText("new")
            setLastModified(2_000)
        }
        assertEquals(new, GradlePlan.findBundle(project))
        assertFalse(old == GradlePlan.findBundle(project))
    }

    @Test
    fun aFailedRunReportsTheRootCauseNotTheTail() {
        val message = GradleRunner.failureMessage(
            GradleRunResult(
                apk = null,
                output = """
                    FAILURE: Build failed with an exception.

                    * What went wrong:
                    Execution failed for task ':app:compileDebugKotlin'.
                    > Compilation error. See log for more details
                    > e: file:///work/Main.kt:9:5: Unresolved reference: nope

                    * Try:
                    > Run with --stacktrace option to get the stack trace.
                    BUILD FAILED in 3s
                """.trimIndent(),
                code = 1,
            ),
        )
        assertTrue(message.contains("exit 1"))
        assertTrue(message, message.contains("Unresolved reference: nope"))
        assertFalse(message, message.contains("--stacktrace"))
    }

    @Test
    fun aGradleLogWithoutACauseBlockStillReportsTheExitCode() {
        val message = GradleRunner.failureMessage(
            GradleRunResult(apk = null, output = "one\ntwo\n", code = 1),
        )
        assertEquals("Gradle failed (exit 1).", message)
    }

    @Test
    fun theRootCauseNamesTheCompilerError() {
        val log = """
            e: file:///work/Main.kt:9:5: Unresolved reference: nope
            e: file:///work/Main.kt:12:1: Expecting a top level declaration
            FAILURE: Build failed with an exception.
            * What went wrong:
            Execution failed for task ':app:compileDebugKotlin'.
            > Compilation error. See log for more details
            BUILD FAILED in 3s
        """.trimIndent()
        val cause = GradleRunner.rootCause(log)
        assertEquals("file:///work/Main.kt:9:5: Unresolved reference: nope", cause)
    }

    @Test
    fun aJavacErrorIsAlsoTheRootCause() {
        val log = """
            /work/Main.java:4: error: cannot find symbol
            FAILURE: Build failed with an exception.
            * What went wrong:
            Execution failed for task ':app:compileDebugJavaWithJavac'.
        """.trimIndent()
        val cause = GradleRunner.rootCause(log)
        assertTrue(cause, cause!!.contains("cannot find symbol"))
    }

    @Test
    fun withoutACompilerErrorTheFailingTaskIsTheCause() {
        val log = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            Execution failed for task ':app:processDebugResources'.
            > AAPT: error: resource style/Theme.App not found
            * Try:
            > Run with --stacktrace option to get the stack trace.
        """.trimIndent()
        val cause = GradleRunner.rootCause(log)
        assertEquals("AAPT: error: resource style/Theme.App not found", cause)
    }

    @Test
    fun theLauncherJarIsPreferredOverTheScript() {
        val toolchain = toolchain()
        assertEquals(
            File(tmp.root, "toolchain/gradle/lib/gradle-launcher-8.13.jar"),
            GradlePlan.launcherJar(toolchain),
        )
        val missing = toolchain.copy(
            gradle = File(tmp.root, "other/bin/gradle").apply {
                parentFile.mkdirs()
                writeText("#!/bin/sh\n")
            },
        )
        assertNull(GradlePlan.launcherJar(missing))
    }

    @Test
    fun aCancelledRunSaysSo() {
        assertEquals(
            "Build cancelled.",
            GradleRunner.failureMessage(GradleRunResult(null, "", -1, cancelled = true)),
        )
    }
}
