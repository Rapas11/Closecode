package com.closecode

import com.closecode.ui.editor.CompilerProbe
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompilerProbeTest {

    @get:Rule
    val temp = TemporaryFolder()

    /**
     * Gradle passes the path directly. If that is ever missing, fall back to asking
     * the classloader where the resource lives and reading the jar out of the URL.
     */
    private fun jarFor(property: String, resource: String? = null): File? {
        val raw = System.getProperty(property).orEmpty()
        if (raw.isNotBlank()) {
            val file = File(raw)
            if (file.isFile) return file
        }
        val url = resource?.let { ClassLoader.getSystemResource(it) }?.toString() ?: return null
        if (!url.startsWith("jar:")) return null
        val end = url.indexOf("!/")
        if (end < 0) return null
        return runCatching { File(java.net.URI(url.removePrefix("jar:").substring(0, end))) }
            .getOrNull()?.takeIf { it.isFile }
    }

    private val compilerJar: File by lazy {
        jarFor("probe.compilerJar", "org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class")
            ?: error(
                "kotlin-compiler-embeddable not available. " +
                    "Gradle did not set probe.compilerJar and no resource resolved.",
            )
    }

    private val kotlinHome: File by lazy {
        val home = temp.newFolder("kotlin-home")
        val lib = File(home, "lib").apply { mkdirs() }
        val wanted = listOf(
            Triple("probe.stdlibJar", "kotlin/Unit.class", "kotlin-stdlib.jar"),
            Triple(
                "probe.scriptRuntimeJar",
                "kotlin/script/templates/annotations/ScriptTemplate.class",
                "kotlin-script-runtime.jar",
            ),
            Triple(
                "probe.annotationsJar",
                "org/jetbrains/annotations/Nullable.class",
                "annotations.jar",
            ),
        )
        for ((property, resource, target) in wanted) {
            jarFor(property, resource)?.copyTo(File(lib, target), overwrite = true)
        }
        assertTrue(
            "kotlin-stdlib jar was not found",
            File(lib, "kotlin-stdlib.jar").isFile,
        )
        home
    }

    /**
     * Runs the compiler the same way the probe does on device: through a
     * URLClassLoader over a real jar. No android.jar here, so the JVM's own JDK
     * stays available to the compiler.
     */
    private fun analyse(source: String): CompilerProbe.Result =
        CompilerProbe.analyse(
            source = source,
            workDir = temp.newFolder(),
            kotlinHome = kotlinHome,
            androidJar = null,
            compilerJar = compilerJar,
        )

    @Test
    fun reportsTypeMismatchFromTheRealCompiler() {
        val result = analyse("val name: String = 123\n")

        val mismatch = result.diagnostics.filter { it.message.contains("type mismatch", ignoreCase = true) }
        println("PROBE diagnostics: ${result.diagnostics}")
        println("PROBE elapsed=${result.elapsedMillis}ms ok=${result.ok}")
        if (result.failure != null) println("PROBE failure: ${result.failure}")

        assertTrue(
            "expected a Type mismatch diagnostic, got ${result.diagnostics}",
            mismatch.isNotEmpty(),
        )
        assertTrue("diagnostics should point at line 1", mismatch.all { it.line == 1 })
    }

    @Test
    fun reportsSyntaxError() {
        val result = analyse("fun broken( {\n")
        println("PROBE syntax diagnostics: ${result.diagnostics}")
        if (result.failure != null) println("PROBE failure: ${result.failure}")
        assertTrue(
            "expected an ERROR for unbalanced braces, got ${result.diagnostics}",
            result.errors.isNotEmpty(),
        )
    }

    @Test
    fun cleanSourceProducesNoErrors() {
        val result = analyse(
            """
            fun add(a: Int, b: Int): Int {
                return a + b
            }
            """.trimIndent()
        )
        println("PROBE clean diagnostics: ${result.diagnostics}")
        if (result.failure != null) println("PROBE failure: ${result.failure}")
        assertTrue(
            "clean source should not error, got ${result.errors}",
            result.errors.isEmpty(),
        )
    }

    @Test
    fun detectsUnresolvedReference() {
        val result = analyse("fun main() { notDefinedAnywhere() }\n")
        println("PROBE unresolved diagnostics: ${result.diagnostics}")
        if (result.failure != null) println("PROBE failure: ${result.failure}")
        assertTrue(
            "expected an unresolved reference error, got ${result.diagnostics}",
            result.errors.any { it.message.contains("Unresolved reference") },
        )
    }

    /**
     * The whole point of this rewrite. A dexed APK returns null for this lookup;
     * a URLClassLoader over a real jar must return a jar:file: URL, which is what
     * IntelliJ's PathManager requires to locate itself.
     */
    @Test
    fun isolatedLoaderResolvesClassResources() {
        val loader = java.net.URLClassLoader(
            arrayOf(compilerJar.toURI().toURL()),
            javaClass.classLoader,
        )
        val cls = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        val url = cls.getResource("/org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class")

        assertTrue("Class.getResource returned null for the jar-based loader", url != null)
        assertTrue(
            "expected a jar: URL, got $url",
            url?.toString()?.startsWith("jar:") == true,
        )
    }
}
