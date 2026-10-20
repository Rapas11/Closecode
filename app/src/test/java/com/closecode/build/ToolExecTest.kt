package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Desktop proof of the entry points the phone will use — [ToolExec]'s reflection onto
 * K2JVMCompiler.exec, ECJ's no-exit Main constructor and D8.run. These are the three calls
 * that never invoke System.exit, which is exactly what makes them usable on ART where no
 * SecurityManager can trap an exit. Runs when the pack script's downloads folder is present
 * (`build-toolchain/downloads`); skips honestly on a machine that never prepared a pack.
 */
class ToolExecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun downloads(): File {
        val dir = System.getProperty("toolchain.dir")?.let { File(it) }
            ?: File("../build-toolchain/downloads")
        assumeTrue("pack downloads not found at ${dir.path}", dir.isDirectory)
        return dir
    }

    private fun jar(root: File, name: String): File {
        val file = File(root, name)
        assumeTrue("missing ${file.path}", file.isFile)
        return file
    }

    private fun androidJarOrSkip(): File {
        val jar = File("../build-toolchain/out/android-35.jar")
        assumeTrue("android-35.jar not prepared", jar.isFile)
        return jar
    }

    private fun launchJars(root: File): List<File> = listOf(
        jar(root, "kotlin-compiler-embeddable-1.9.24.jar"),
        jar(root, "kotlin-stdlib-1.9.24.jar"),
        jar(root, "kotlin-reflect-1.9.24.jar"),
        jar(root, "kotlin-script-runtime-1.9.24.jar"),
        jar(root, "kotlin-daemon-embeddable-1.9.24.jar"),
        jar(root, "trove4j-1.0.20200330.jar"),
        jar(root, "annotations-13.0.jar"),
        jar(root, "kotlinx-coroutines-core-jvm-1.9.0.jar"),
    )

    @Test
    fun kotlinCompilerExecEntryPointCompiles() {
        val root = downloads()
        val jars = launchJars(root)
        assertEquals(ToolchainManifest.KOTLINC_LAUNCH_IDS.size, jars.size)
        val src = File(tmp.root, "Hello.kt").apply { writeText("fun hello(): String = \"hi\"\n") }
        val out = File(tmp.root, "classes").apply { mkdirs() }
        val result = ToolExec.kotlinc(
            jars,
            CompileDriver.kotlincArgs(
                listOf(src),
                listOf(jar(root, "kotlin-stdlib-1.9.24.jar")),
                out,
                null,
            ),
            dexOptDir = File(tmp.root, "dexopt"),
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        assertTrue(File(out, "HelloKt.class").isFile)
    }

    @Test
    fun ecjNoExitConstructorCompiles() {
        val root = downloads()
        val src = File(tmp.root, "Hello.java").apply {
            writeText("class Hello { static String hello() { return \"hi\"; } }\n")
        }
        val out = File(tmp.root, "java-classes").apply { mkdirs() }
        val result = ToolExec.ecj(
            listOf(jar(root, "ecj-3.46.0.jar")),
            CompileDriver.ecjArgs(listOf(src), emptyList(), out, androidJarOrSkip()),
            dexOptDir = File(tmp.root, "dexopt"),
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        assertTrue(File(out, "Hello.class").isFile)
    }

    @Test
    fun d8RunEntryPointMakesADex() {
        val root = downloads()
        val androidJar = androidJarOrSkip()
        // A class to dex, made here so the test stands alone.
        val src = File(tmp.root, "ForD8.java").apply {
            writeText("class ForD8 { int x() { return 1; } }\n")
        }
        val classes = File(tmp.root, "d8-classes").apply { mkdirs() }
        val compiled = ToolExec.ecj(
            listOf(jar(root, "ecj-3.46.0.jar")),
            CompileDriver.ecjArgs(listOf(src), emptyList(), classes, androidJar),
            dexOptDir = File(tmp.root, "dexopt"),
        )
        assertEquals(compiled.output.takeLast(2000), 0, compiled.code)
        val dexOut = File(tmp.root, "dex").apply { mkdirs() }
        val result = ToolExec.d8(
            listOf(jar(root, "r8-8.13.25.jar")),
            CompileDriver.d8Args(listOf(File(classes, "ForD8.class")), listOf(androidJar), dexOut),
            dexOptDir = File(tmp.root, "dexopt"),
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        assertTrue(File(dexOut, "classes.dex").isFile)
    }
}
