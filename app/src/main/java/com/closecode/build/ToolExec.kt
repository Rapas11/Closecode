package com.closecode.build

import java.io.File
import java.io.PrintWriter

/**
 * Runs the three compilers through entry points that return instead of calling
 * `System.exit`, so no exit trap is needed — which matters because ART refuses a
 * SecurityManager. The build path uses these; [ToolRunner.runMain] stays for the JVM
 * and for tests.
 *
 * - kotlinc: `K2JVMCompiler().exec(errStream, args)` returns an ExitCode ("OK" on success)
 * - ECJ: `Main(out, err, systemExitWhenFinished = false, ...)` promises not to exit;
 *   `compile(args)` returns true when everything compiled (older builds: `execute`)
 * - D8: `D8Command.parse(args, Origin.unknown())` → builder → `D8.run(command)`; its
 *   `main()` exits, this path returns
 *
 * Reflection, not direct calls: the toolchain jars are not on this app's compile classpath.
 */
object ToolExec {

    fun kotlinc(jars: List<File>, args: List<String>, dexOptDir: File? = null): ToolRunner.Result =
        ToolRunner.runCall(jars, dexOptDir = dexOptDir, allowWithoutTrap = true) { loader ->
            val compiler = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                .getDeclaredConstructor()
                .newInstance()
            // exec(PrintStream, Array<String>): ExitCode — the vararg compiles to an array.
            val exec = compiler.javaClass.methods.firstOrNull { method ->
                method.name == "exec" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == java.io.PrintStream::class.java &&
                    method.parameterTypes[1] == Array<String>::class.java
            } ?: return@runCall report("This Kotlin compiler has no exec() entry point.")
            val exit = exec.invoke(compiler, System.err, args.toTypedArray())
            val name = exit?.toString() ?: "INTERNAL_ERROR"
            if (name == "OK") 0 else 1
        }

    fun ecj(jars: List<File>, args: List<String>, dexOptDir: File? = null): ToolRunner.Result =
        ToolRunner.runCall(jars, dexOptDir = dexOptDir, allowWithoutTrap = true) { loader ->
            val mainClass = loader.loadClass("org.eclipse.jdt.internal.compiler.batch.Main")
            val ctor = mainClass.constructors.firstOrNull { candidate ->
                candidate.parameterTypes.contentEquals(
                    arrayOf(
                        PrintWriter::class.java,
                        PrintWriter::class.java,
                        Boolean::class.javaPrimitiveType,
                        Map::class.java,
                    ),
                )
            } ?: return@runCall report("This ECJ build has no no-exit constructor.")
            // Auto-flushing writer: ECJ writes diagnostics with println.
            val writer = PrintWriter(System.err, true)
            val instance = ctor.newInstance(writer, writer, false, null)
            // ECJ 3.46 calls it compile(); older builds called it execute(). Both return
            // true when everything compiled and never exit with the flag above.
            val run = mainClass.methods.firstOrNull {
                (it.name == "compile" || it.name == "execute") &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Array<String>::class.java
            } ?: return@runCall report("This ECJ build has no compile() entry point.")
            val ok = run.invoke(instance, args.toTypedArray()) as? Boolean ?: false
            writer.flush()
            if (ok) 0 else 1
        }

    fun d8(jars: List<File>, args: List<String>, dexOptDir: File? = null): ToolRunner.Result =
        ToolRunner.runCall(jars, dexOptDir = dexOptDir, allowWithoutTrap = true) { loader ->
            // R8's D8 has no run(String[]) and its main() exits the process. The command
            // object API is the no-exit path: parse(args) -> builder -> build -> run.
            val commandClass = loader.loadClass("com.android.tools.r8.D8Command")
            val originClass = loader.loadClass("com.android.tools.r8.origin.Origin")
            val origin = originClass.getMethod("unknown").invoke(null)
            val builder = commandClass
                .getMethod("parse", Array<String>::class.java, originClass)
                .invoke(null, args.toTypedArray(), origin)
            val command = builder.javaClass.getMethod("build").invoke(builder)
            loader.loadClass("com.android.tools.r8.D8")
                .getMethod("run", commandClass)
                .invoke(null, command)
            0
        }

    /** A sentence for the rare case a jar is present but its entry point is not. */
    private fun report(message: String): Int {
        println(message)
        return 1
    }
}
