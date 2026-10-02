package com.closecode.ui.editor

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.URLClassLoader

/**
 * Isolated experiment: runs the real Kotlin 2.4.0 compiler over a source string and
 * reports what it found. Not wired into the editor.
 *
 * The compiler is loaded through a [URLClassLoader] over a real jar on disk rather
 * than being dexed into the APK. Two reasons, both verified on device:
 *
 *  1. IntelliJ's PathManager.getResourceRoot() resolves each compiler class through
 *     Class.getResource(...). A dexed APK contains no .class files, so the lookup
 *     always returns null and the compiler dies with "Resource not found". Adding
 *     assets at the same paths does not help: getSystemResource and Class.getResource
 *     both return NULL on Android even when the file is present.
 *  2. A URLClassLoader over a jar returns a real jar:file: URL, which is what
 *     PathManager requires.
 *
 * Because the compiler is now in a separate class loader, its types are no longer the
 * app's types, so every call here is reflective. MessageCollector is implemented with
 * a dynamic [Proxy] against the isolated interface.
 *
 * Compiler classes, all resolved from the isolated loader by name:
 *   org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
 *   org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
 *   org.jetbrains.kotlin.cli.common.messages.MessageCollector
 *   org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
 *   org.jetbrains.kotlin.config.Services
 */
class CompilerProbe private constructor() {

    data class Diagnostic(
        val severity: String,
        val message: String,
        val line: Int,
        val column: Int,
    )

    data class Result(
        val diagnostics: List<Diagnostic>,
        val elapsedMillis: Long,
        val ok: Boolean,
        val failure: String? = null,
    ) {
        val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == "ERROR" }
    }

    companion object {

        private var loader: URLClassLoader? = null

        /**
         * TEMPORARY diagnostic: reports whether each classloader can see compiler
         * resources. The app's own loader returning NULL is expected and is what
         * proved the asset-stub approach cannot work. The isolated jar loader
         * returning a jar:file: URL is what this rewrite depends on.
         */
        fun resourceProbe(compilerJar: File? = null): String = buildString {
            val path = "/org/jetbrains/kotlin/cli/common/CompilerSystemProperties.class"
            val viaSystem = runCatching { ClassLoader.getSystemResource(path.removePrefix("/")) }
            val viaClass = runCatching { CompilerProbe::class.java.getResource(path) }
            appendLine("app classloader getSystemResource -> ${viaSystem.getOrNull() ?: "NULL"} (expected)")
            appendLine("app classloader Class.getResource -> ${viaClass.getOrNull() ?: "NULL"} (expected)")
            val jarLoader = loaderFor(compilerJar)
            if (jarLoader != null) {
                val target = runCatching {
                    jarLoader.loadClass("org.jetbrains.kotlin.cli.common.CompilerSystemProperties")
                }.getOrNull()
                val viaIsolated = runCatching { target?.getResource(path) }
                appendLine("jar classloader Class.getResource -> ${viaIsolated.getOrNull() ?: "NULL"}")
            } else {
                appendLine("jar classloader -> not available")
            }
        }

        fun analyse(
            source: String,
            workDir: File,
            kotlinHome: File? = null,
            androidJar: File? = null,
            compilerJar: File? = null,
        ): Result {
            val started = System.currentTimeMillis()
            return try {
                val src = File(workDir, "Probe.kt")
                src.parentFile?.mkdirs()
                src.writeText(source)

                val loader = loaderFor(compilerJar)
                    ?: return failure(started, "kotlin-compiler.jar not unpacked")

                val argsClass = loader.loadClass(
                    "org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments"
                )
                val collectorClass = loader.loadClass(
                    "org.jetbrains.kotlin.cli.common.messages.MessageCollector"
                )
                val servicesClass = loader.loadClass("org.jetbrains.kotlin.config.Services")
                val compilerClass = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

                val args = argsClass.getDeclaredConstructor().newInstance()
                setArg(args, "freeArgs", listOf(src.absolutePath))
                setArg(args, "destination", File(workDir, "out").absolutePath)
                setArg(args, "noReflect", true)

                if (androidJar != null && androidJar.isFile) {
                    // Android is not a JDK. On a phone java.home resolves to
                    // /apex/com.android.art, which has no class roots, and the compiler
                    // errors with "No class roots are found in the JDK path".
                    setArg(args, "noJdk", true)
                    setArg(args, "jdkHome", null)

                    // android.jar supplies java.lang.* and the Android API surface, the
                    // same way a real JDK jar would on desktop.
                    setArg(args, "classpath", androidJar.absolutePath)
                }

                // Critical on Android: without an explicit kotlinHome the compiler calls
                // PathUtil.getKotlinPathsForCompiler(), which asks the classloader for its
                // own .class resource. With the jar-based loader this now resolves, but
                // pointing at a real directory is still the correct configuration.
                if (kotlinHome != null && kotlinHome.isDirectory) {
                    setArg(args, "kotlinHome", kotlinHome.absolutePath)
                }

                val collected = mutableListOf<Diagnostic>()
                val collector = Proxy.newProxyInstance(
                    loader,
                    arrayOf(collectorClass),
                ) { proxy, method, args2 ->
                    when (method.name) {
                        "clear" -> collected.clear()
                        // Deliberately always false: reporting errors would let the
                        // pipeline abort early and drop diagnostics we still want.
                        // The probe only reads diagnostics, so nothing depends on this.
                        "hasErrors" -> false
                        "report" -> {
                            collect(collected, args2)
                            null
                        }
                        "toString" -> "ProbeMessageCollector(${collected.size})"
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args2?.getOrNull(0)
                        else -> null
                    }
                } as Any

                val services = servicesInstance(servicesClass)
                val compiler = compilerClass.getDeclaredConstructor().newInstance()
                val exec = execMethod(compilerClass, collectorClass, servicesClass, argsClass)
                val exit = exec.invoke(compiler, collector, services, args)

                val diagnostics = collected.toList()
                Result(
                    diagnostics = diagnostics,
                    elapsedMillis = System.currentTimeMillis() - started,
                    ok = exit.toString() == "OK" && diagnostics.none { it.severity == "ERROR" },
                )
            } catch (error: Throwable) {
                val cause = if (error is InvocationTargetException) error.targetException else error
                Result(
                    diagnostics = emptyList(),
                    elapsedMillis = System.currentTimeMillis() - started,
                    ok = false,
                    failure = describe(cause),
                )
            }
        }

        private fun collect(into: MutableList<Diagnostic>, args: Array<out Any?>?) {
            if (args == null || args.size < 2) return
            val severity = args[0]?.toString() ?: "UNKNOWN"
            val message = args[1]?.toString() ?: ""
            val location = args.getOrNull(2)
            into += Diagnostic(
                severity = severity,
                message = message,
                line = location?.let { callInt(it, "getLine") } ?: -1,
                column = location?.let { callInt(it, "getColumn") } ?: -1,
            )
        }

        private fun callInt(target: Any, getter: String): Int = runCatching {
            target.javaClass.getMethod(getter).invoke(target) as Int
        }.getOrDefault(-1)

        private fun loaderFor(jar: File?): URLClassLoader? {
            if (jar == null || !jar.isFile) return null
            loader?.let { return it }
            return URLClassLoader(
                arrayOf(jar.toURI().toURL()),
                CompilerProbe::class.java.classLoader,
            ).also { loader = it }
        }

        /**
         * [exec] takes three parameters, but it is generic so its third parameter
         * erases to Object. Match on the two types we control rather than on arity
         * alone, which could bind to an unrelated overload.
         */
        private fun execMethod(
            compilerClass: Class<*>,
            collectorClass: Class<*>,
            servicesClass: Class<*>,
            argsClass: Class<*>,
        ) = compilerClass.methods.firstOrNull {
            it.name == "exec" &&
                it.parameterCount == 3 &&
                it.parameterTypes[0] == collectorClass &&
                it.parameterTypes[1] == servicesClass &&
                (it.parameterTypes[2] == argsClass || it.parameterTypes[2] == Any::class.java)
        } ?: compilerClass.methods.first { it.name == "exec" && it.parameterCount == 3 }

        /**
         * Falls back through the superclass chain: Kotlin puts shared properties such
         * as `freeArgs` on CommonCompilerArguments, and getDeclaredField never sees
         * fields declared on a parent.
         */
        private fun setArg(target: Any, name: String, value: Any?) {
            val setter = "set" + name.replaceFirstChar { it.uppercase() }
            val method = target.javaClass.methods.firstOrNull {
                it.name == setter && it.parameterCount == 1
            }
            if (method != null) {
                method.invoke(target, value)
                return
            }
            var type: Class<*>? = target.javaClass
            while (type != null) {
                val field = runCatching { type.getDeclaredField(name) }.getOrNull()
                if (field != null) {
                    field.isAccessible = true
                    field.set(target, value)
                    return
                }
                type = type.superclass
            }
            throw NoSuchFieldException(
                "$setter and field ${target.javaClass.name}.$name not found"
            )
        }

        /**
         * Services is a Kotlin object, so EMPTY is usually an instance getter behind
         * INSTANCE rather than a public static field. Probe both shapes, and never let
         * the first miss escape as the result of the whole run.
         */
        private fun servicesInstance(servicesClass: Class<*>): Any =
            runCatching { servicesClass.getField("EMPTY").get(null) }.getOrNull()
                ?: runCatching {
                    val instance = servicesClass.getField("INSTANCE").get(null)
                    servicesClass.methods.firstOrNull { it.name == "getEMPTY" }
                        ?.invoke(instance) ?: instance
                }.getOrElse { error ->
                    throw IllegalStateException(
                        "cannot obtain Services.EMPTY from ${servicesClass.name}: $error"
                    )
                }

        private fun failure(started: Long, message: String): Result = Result(
            diagnostics = emptyList(),
            elapsedMillis = System.currentTimeMillis() - started,
            ok = false,
            failure = message,
        )

        private fun describe(error: Throwable): String = buildString {
            appendLine("${error.javaClass.name}: ${error.message}")
            var cause: Throwable? = error.cause
            var depth = 0
            while (cause != null && depth < 6) {
                appendLine("caused by ${cause.javaClass.name}: ${cause.message}")
                cause = cause.cause
                depth++
            }
            error.stackTrace.take(18).forEach { appendLine("\tat $it") }
        }
    }
}
