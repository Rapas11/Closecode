package com.closecode.build

import java.io.File

/**
 * Milestone (a) drivers, pure argument builders. The engine will execute these against
 * the verified jars; until then every flag here is unit-tested, so the first on-device
 * run assembles from known-good pieces rather than guesses.
 *
 * Entry points (all stable across the pinned versions):
 * - kotlinc: org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
 * - ECJ: org.eclipse.jdt.internal.compiler.batch.Main
 * - D8: com.android.tools.r8.D8Main
 * - apksigner: com.android.apksig.ApkSignerTool
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

    /** ECJ arguments for R.java and any Java sources: Java 17, errors to stdout. */
    fun ecjArgs(
        sources: List<File>,
        classpath: List<File>,
        outputDir: File,
        bootClasspath: File,
    ): List<String> = buildList {
        add("-17")
        add("-nowarn")
        add("-d")
        add(outputDir.absolutePath)
        add("-cp")
        add(classpath.joinToString(File.pathSeparator) { it.absolutePath })
        add("-bootclasspath")
        add(bootClasspath.absolutePath)
        sources.forEach { add(it.absolutePath) }
    }

    /** D8 arguments: program classes plus deps, min API 29, one dex output dir. */
    fun d8Args(
        classes: List<File>,
        libs: List<File>,
        outputDir: File,
        minApi: Int = TemplateContract.MIN_SDK,
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

    /** apksigner sign arguments for the debug keypair the app generates itself. */
    fun signArgs(apk: File, key: File, cert: File, out: File): List<String> = listOf(
        "sign",
        "--ks", key.absolutePath,
        "--ks-type", "PKCS12",
        "--out", out.absolutePath,
        apk.absolutePath,
    )

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
