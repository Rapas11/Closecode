package com.closecode.build

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Milestone (a) proof: the pinned toolchain end to end — kotlinc, ECJ, D8, aapt2
 * link, dex packing and a diagnostics failure case. Runs only when the jars and an
 * SDK are pointed at it (`-Dtoolchain.dir=… -Daapt2.bin=… -Dandroid.jar=…` plus a
 * `stdlib.jar` beside the compiler); anywhere else it skips silently instead of
 * failing a machine that was never asked to prove anything.
 */
class ToolchainProofTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private data class Kit(
        val dir: File,
        val kotlinc: File,
        val launchCp: List<File>,
        val ecj: File,
        val r8: File,
        val stdlib: File,
        val javaBin: String,
        val aapt2: File,
        val androidJar: File,
    )

    private fun kitOrSkip(): Kit {
        val dir = (System.getProperty("toolchain.dir") ?: System.getenv("TOOLCHAIN_DIR"))
            ?.let { File(it) }
        assumeTrue("toolchain.dir not set", dir != null && dir!!.isDirectory)
        val root = dir!!
        val kotlinc = File(root, "kotlin-compiler-embeddable-1.9.24.jar")
        val stdlib = File(
            System.getProperty("stdlib.jar")
                ?: File(root, "kotlin-stdlib-1.9.24.jar").path,
        )
        val launchCp = listOf(
            kotlinc,
            stdlib,
            File(root, "kotlin-reflect-1.9.24.jar"),
            File(root, "kotlin-script-runtime-1.9.24.jar"),
            File(root, "kotlin-daemon-embeddable-1.9.24.jar"),
            File(root, "trove4j-1.0.20200330.jar"),
            File(root, "annotations-13.0.jar"),
            File(root, "kotlinx-coroutines-core-jvm-1.9.0.jar"),
        )
        val ecj = File(root, "ecj-3.46.0.jar")
        val r8 = File(root, "r8-8.13.25.jar")
        val aapt2 = File(System.getProperty("aapt2.bin") ?: "/opt/android_sdk/build-tools/34.0.0/aapt2")
        val androidJar = File(
            System.getProperty("android.jar")
                ?: "/opt/android_sdk/platforms/android-34/android.jar",
        )
        for (file in launchCp + listOf(ecj, r8, aapt2, androidJar)) {
            assumeTrue("missing ${file.path}", file.isFile)
        }
        val javaBin = System.getProperty("java.home") + File.separator + "bin" +
            File.separator + "java"
        return Kit(dir, kotlinc, launchCp, ecj, r8, stdlib, javaBin, aapt2, androidJar)
    }

    /**
     * The original (non-dexed) jars the pack script downloads, wired up as a ready store:
     * this is how the JVM side of the engine gets real compilers without a download.
     */
    private fun downloadsStore(): ToolchainStore {
        val root = (System.getProperty("toolchain.dir") ?: System.getenv("TOOLCHAIN_DIR"))
            ?.let { File(it) }
        assumeTrue("toolchain.dir not set", root != null && root!!.isDirectory)
        val names = mapOf(
            "kotlin-compiler" to "kotlin-compiler-embeddable-1.9.24.jar",
            "kotlin-stdlib" to "kotlin-stdlib-1.9.24.jar",
            "kotlin-reflect" to "kotlin-reflect-1.9.24.jar",
            "kotlin-script-runtime" to "kotlin-script-runtime-1.9.24.jar",
            "kotlin-daemon-embeddable" to "kotlin-daemon-embeddable-1.9.24.jar",
            "trove4j" to "trove4j-1.0.20200330.jar",
            "annotations" to "annotations-13.0.jar",
            "coroutines" to "kotlinx-coroutines-core-jvm-1.9.0.jar",
            "ecj" to "ecj-3.46.0.jar",
            "r8" to "r8-8.13.25.jar",
        )
        val files = names.mapNotNull { (id, name) ->
            File(root!!, name).takeIf { it.isFile }?.let { file ->
                ToolComponent(
                    id = id,
                    version = "local",
                    fileName = name,
                    url = "https://example.invalid/$name",
                    sizeBytes = file.length(),
                    sha256 = "",
                    license = "",
                )
            }
        }
        assumeTrue("missing jars under ${root!!.path}", files.size == names.size)
        val toolchain = Toolchain("local", "1.9.24", "1.5.14", 35, Aapt2Info("", "", ""), files)
        val kv = com.closecode.ui.settings.InMemoryKeyValueStore()
        kv.putString("toolchain_pack_version", toolchain.version)
        return ToolchainStore(toolchain, root, kv)
    }

    @Test
    fun kotlincCompilesHelloWorld() {
        val kit = kitOrSkip()
        val src = File(tmp.root, "Hello.kt").apply { writeText("fun main() { println(\"proof\") }\n") }
        val out = File(tmp.root, "out").apply { mkdirs() }
        val result = DriverExec.runJava(
            kit.javaBin,
            kit.launchCp,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            CompileDriver.kotlincArgs(
                sources = listOf(src),
                classpath = listOf(kit.stdlib),
                outputDir = out,
                composePluginJar = null,
            ),
            tmp.root,
            timeoutMs = 300_000,
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        assertTrue(File(out, "HelloKt.class").isFile)
    }

    @Test
    fun kotlincFailuresParseToFileAndLine() {
        val kit = kitOrSkip()
        val src = File(tmp.root, "Bad.kt").apply { writeText("fun main() { nosuchthing() }\n") }
        val out = File(tmp.root, "out-bad").apply { mkdirs() }
        val result = DriverExec.runJava(
            kit.javaBin,
            kit.launchCp,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            CompileDriver.kotlincArgs(
                sources = listOf(src),
                classpath = listOf(kit.stdlib),
                outputDir = out,
                composePluginJar = null,
            ),
            tmp.root,
            timeoutMs = 300_000,
        )
        assertTrue("exit=${result.code}", result.code != 0)
        val diagnostic = result.output.lines().mapNotNull { CompileDriver.parseDiagnostic(it) }
            .firstOrNull { it.kind == CompileDriver.Diagnostic.Kind.Error }
        assertTrue("no parsed error in:\n${result.output.takeLast(1000)}", diagnostic != null)
        assertEquals(1, diagnostic?.line)
    }

    @Test
    fun engineCompilesThroughRealKotlinc() {
        // Same code path as on-device: ToolRunner + ToolExec + manifest jars, no DriverExec.
        val store = downloadsStore()
        org.junit.Assert.assertTrue(store.isReady())
        val project = java.io.File(tmp.root, "engine-proj").apply { mkdirs() }
        java.io.File(project, "app/src/main/AndroidManifest.xml").apply {
            parentFile?.mkdirs()
            writeText("<manifest package=\"com.example.biz\"></manifest>")
        }
        java.io.File(project, "app/src/main/java/com/example/biz/Main.kt").apply {
            parentFile?.mkdirs()
            writeText("package com.example.biz\nfun main() { println(\"hi\") }\n")
        }
        val outcome = kotlinx.coroutines.runBlocking {
            BuildEngine(store).build(project, "biz")
        }
        // Imported package passes the contract; compilation succeeds; packaging waits
        // for the SDK pair, which this test deliberately withholds.
        org.junit.Assert.assertTrue("got $outcome", outcome is BuildOutcome.DriverPending)
        org.junit.Assert.assertTrue(
            java.io.File(project, "build/closecode/classes/com/example/biz/MainKt.class").isFile,
        )
    }

    @Test
    fun engineSurfacesRealCompilerErrors() {
        val store = downloadsStore()
        org.junit.Assume.assumeTrue(store.isReady())
        val project = java.io.File(tmp.root, "engine-bad").apply { mkdirs() }
        java.io.File(project, "app/src/main/AndroidManifest.xml").apply {
            parentFile?.mkdirs()
            writeText("<manifest package=\"com.example.biz\"></manifest>")
        }
        java.io.File(project, "app/src/main/java/com/example/biz/Main.kt").apply {
            parentFile?.mkdirs()
            writeText("package com.example.biz\nfun main() { nosuchthing() }\n")
        }
        val outcome = kotlinx.coroutines.runBlocking {
            BuildEngine(store).build(project, "biz")
        }
        org.junit.Assert.assertTrue("got $outcome", outcome is BuildOutcome.CompileFailed)
        val errors = (outcome as BuildOutcome.CompileFailed).diagnostics
        org.junit.Assert.assertTrue(errors.isNotEmpty())
    }

    @Test
    fun fullChainToLinkedApk() {
        val kit = kitOrSkip()
        val work = File(tmp.root, "chain").apply { mkdirs() }
        // 1. Kotlin source.
        val src = File(work, "Hello.kt").apply { writeText("fun main() { println(\"proof\") }\n") }
        val classes = File(work, "classes").apply { mkdirs() }
        var result = DriverExec.runJava(
            kit.javaBin, kit.launchCp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            CompileDriver.kotlincArgs(listOf(src), listOf(kit.stdlib), classes, null),
            work, timeoutMs = 300_000,
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        // 2. Resources: manifest + strings, compiled and linked.
        File(work, "res/values").apply { mkdirs() }
        File(work, "res/values/strings.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n    <string name=\"app_name\">Proof</string>\n</resources>\n",
        )
        File(work, "AndroidManifest.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                "package=\"com.closecode.proof\">\n" +
                "    <application android:label=\"@string/app_name\" />\n</manifest>\n",
        )
        val compiled = File(work, "compiled").apply { mkdirs() }
        result = DriverExec.runNative(
            kit.aapt2,
            listOf("compile", "--dir", File(work, "res").absolutePath, "-o", compiled.absolutePath),
            work,
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        val gen = File(work, "gen").apply { mkdirs() }
        val base = File(work, "base.apk")
        val flats = compiled.walkTopDown().filter { it.isFile && it.extension == "flat" }.toList()
        assertTrue("aapt2 compiled nothing", flats.isNotEmpty())
        result = DriverExec.runNative(
            kit.aapt2,
            listOf(
                "link", "-o", base.absolutePath, "-I", kit.androidJar.absolutePath,
                "--manifest", File(work, "AndroidManifest.xml").absolutePath,
                "--java", gen.absolutePath,
                "--min-sdk-version", "29", "--target-sdk-version", "34",
            ) + flats.map { it.absolutePath },
            work,
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        assertTrue(base.isFile)
        // 3. ECJ on the generated R.java.
        val rJava = gen.walkTopDown().first { it.isFile && it.name == "R.java" }
        val rClasses = File(work, "rclasses").apply { mkdirs() }
        result = DriverExec.runJava(
            kit.javaBin, listOf(kit.ecj), "org.eclipse.jdt.internal.compiler.batch.Main",
            CompileDriver.ecjArgs(listOf(rJava), emptyList(), rClasses, kit.androidJar),
            work, timeoutMs = 180_000,
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        // 4. D8 everything to dex.
        val program = classes.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() +
            rClasses.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        val dexOut = File(work, "dex").apply { mkdirs() }
        result = DriverExec.runJava(
            kit.javaBin, listOf(kit.r8), "com.android.tools.r8.D8",
            CompileDriver.d8Args(program, listOf(kit.androidJar, kit.stdlib), dexOut),
            work, timeoutMs = 300_000,
        )
        assertEquals(result.output.takeLast(2000), 0, result.code)
        val dex = File(dexOut, "classes.dex")
        assertTrue(dex.isFile)
        // 5. Pack the dex into the APK and check the entries.
        FileSystems.newFileSystem(
            java.net.URI.create("jar:" + base.toURI()),
            mapOf("create" to "false"),
        ).use { zip ->
            Files.copy(
                dex.toPath(), zip.getPath("classes.dex"),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        val entries = FileSystems.newFileSystem(
            java.net.URI.create("jar:" + base.toURI()), emptyMap<String, Any>(),
        ).use { zip ->
            Files.walk(zip.getPath("/")).map { it.toString() }.toList()
        }
        assertTrue(entries.any { it.endsWith("classes.dex") })
        assertTrue(entries.any { it.endsWith("AndroidManifest.xml") })
    }
}
