package com.closecode.build

import java.io.File

/**
 * Milestone (a) drivers, pure argument builders. Proven end to end on JVM 2026-10-12:
 * kotlinc → ECJ → D8 → aapt2 link → zip-pack → apksig sign → verify, all green.
 *
 * Entry points (verified against the pinned jars):
 * - kotlinc: org.jetbrains.kotlin.cli.jvm.K2JVMCompiler, launched with stdlib,
 *   reflect, trove4j, annotations and coroutines beside the embeddable jar
 * - ECJ: org.eclipse.jdt.internal.compiler.batch.Main (platform jar on -cp;
 *   -bootclasspath is rejected at compliance 9+)
 * - D8: com.android.tools.r8.D8 (there is no D8Main in r8 8.13.x)
 * - apksig: library only (com.android.apksig.ApkSigner.Builder) — there is no CLI
 *   in the artifact, so signing calls the API with the app-generated debug key
 *
 * On-device correction: ART runs dex, not JVM bytecode, so the setup ships these
 * jars pre-dexed (converted here at release time); the app loads them through a
 * DexClassLoader. The hashes in [ToolchainManifest] cover the jars; dexed artifacts
 * get their own manifest entries before milestone (a) closes.
 */
object CompileDriver {

    /**
     * kotlinc arguments for template sources: Compose plugin on, JVM 17 bytecode,
     * no daemon, no reflection fallbacks. Returns the arg list for the main class.
     */
    fun kotlincArgs(
        sources: List<File>,
        classpath: List<File>,
        outputDir: File,
        composePluginJar: File?,
    ): List<String> = buildList {
        add("-no-stdlib")
        add("-jvm-target")
        add("17")
        add("-d")
        add(outputDir.absolutePath)
        add("-cp")
        add(classpath.joinToString(File.pathSeparator) { it.absolutePath })
        if (composePluginJar != null) {
            add("-Xplugin=${composePluginJar.absolutePath}")
        }
        add("-Xskip-metadata-version-check")
        sources.forEach { add(it.absolutePath) }
    }

    /**
     * ECJ arguments for R.java and any Java sources. The platform jar rides on -cp:
     * -bootclasspath is rejected at compliance 9+, and the platform is a library
     * here, not a boot runtime.
     */
    fun ecjArgs(
        sources: List<File>,
        classpath: List<File>,
        outputDir: File,
        platformJar: File,
    ): List<String> = buildList {
        add("-17")
        add("-nowarn")
        add("-d")
        add(outputDir.absolutePath)
        add("-cp")
        add((classpath + platformJar).joinToString(File.pathSeparator) { it.absolutePath })
        sources.forEach { add(it.absolutePath) }
    }

    /** D8 arguments: program classes plus deps, the project's min API, one dex output dir. */
    fun d8Args(
        classes: List<File>,
        libs: List<File>,
        outputDir: File,
        minApi: Int = ProjectPins.MIN_SDK_FLOOR,
    ): List<String> = buildList {
        add("--min-api")
        add("$minApi")
        add("--output")
        add(outputDir.absolutePath)
        libs.forEach {
            add("--lib")
            add(it.absolutePath)
        }
        classes.forEach { add(it.absolutePath) }
    }

    /**
     * Signing runs through ApkSigner.Builder with the [com.closecode.build.DebugKey]
     * pair (v1+v2+v3). No CLI exists in the artifact, so there are no arg lists here —
     * the proof run signed and verified with exactly that API.
     */

    /** A compiler diagnostic with a file and line the UI can jump to. */
    data class Diagnostic(
        val path: String,
        val line: Int,
        val kind: Kind,
        val message: String,
    ) {
        enum class Kind { Error, Warning }
    }

    private val KotlincLine = Regex("^(.+\\.kt):(\\d+):\\d+: (warning|error): (.*)$")
    private val EcjJavacLine = Regex("^(.+\\.java):(\\d+): (warning|error): (.*)$")

    /**
     * A parsed diagnostic as the Build panel's error cards read it: same file+line
     * tap-to-jump as the analyzer cards, fed by real compiler output.
     */
    internal fun toProjectDiagnostic(
        parsed: Diagnostic,
        fileName: (String) -> String,
    ): com.closecode.ui.ide.ProjectDiagnostic {
        val severity = if (parsed.kind == Diagnostic.Kind.Error) {
            com.closecode.lang.Severity.ERROR
        } else {
            com.closecode.lang.Severity.WARNING
        }
        return com.closecode.ui.ide.ProjectDiagnostic(
            fileName = fileName(parsed.path),
            diagnostic = com.closecode.lang.Diagnostic(
                severity = severity,
                message = parsed.message,
                offset = 0,
                length = 0,
                line = parsed.line,
                column = 1,
                code = if (severity == com.closecode.lang.Severity.ERROR) "E0001" else "W0001",
            ),
        )
    }

    /**
     * Parses one kotlinc/ECJ output line (`path.kt:12:8: error: message`) into a
     * jumpable diagnostic, or null for progress chatter. Pure, so the panel's
     * tap-to-jump is tested without ever launching a compiler.
     */
    fun parseDiagnostic(line: String): Diagnostic? {
        val match = KotlincLine.matchEntire(line.trim()) ?: EcjJavacLine.matchEntire(line.trim())
            ?: return null
        return Diagnostic(
            path = match.groupValues[1],
            line = match.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1,
            kind = if (match.groupValues[3] == "warning") Diagnostic.Kind.Warning else Diagnostic.Kind.Error,
            message = match.groupValues[4].trim(),
        )
    }
}
