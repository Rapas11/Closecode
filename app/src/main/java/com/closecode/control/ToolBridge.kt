package com.closecode.control

import android.content.Context
import com.closecode.build.Aapt2Runtime
import com.closecode.build.DriverExec
import com.closecode.build.KotlinCompat
import com.closecode.build.KotlinHome
import com.closecode.build.ToolExec
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainManifest
import java.io.File

/**
 * The four toolchain entry points an agent may call directly. They are deliberately
 * limited: `aapt2` runs the bundled native binary, `kotlinc`/`d8`/`r8` use the same
 * no-exit entry points as the build, and nothing here changes a project unless the
 * caller's arguments say so.
 */
internal object ToolBridge {

    val names = listOf("aapt2", "d8", "r8", "kotlinc")

    data class Result(val code: Int, val output: String)

    fun run(context: Context, name: String, args: List<String>, workDir: File): Result {
        val store = ToolchainHolder.store(context)
        if (!store.isReady()) {
            return Result(1, "Build tools are not installed. Open CloseCode's build tools setup first.")
        }
        return when (name) {
            "aapt2" -> {
                // The same aapt2 the phone engine builds with: the SDK's ARM64 one when it
                // is installed, because the bundled build cannot read the platform resources.
                val binary = com.closecode.build.Aapt2Picker.pick(context)
                if (binary == null) {
                    Result(1, "aapt2 is missing from this installation.")
                } else {
                    val result = DriverExec.runNative(
                        bin = binary,
                        args = args,
                        workDir = workDir,
                        timeoutMs = 600_000,
                        env = Aapt2Runtime.environment(context),
                    )
                    Result(
                        result.code,
                        result.output + if (result.timedOut) "\naapt2 timed out." else "",
                    )
                }
            }
            "d8" -> {
                val jar = store.jar("r8")
                if (jar == null) {
                    Result(1, "D8 is missing from the build tools.")
                } else {
                    val result = inDir(workDir) {
                        ToolExec.d8(listOf(jar), args, dexOptDir(context))
                    }
                    Result(result.code, result.output)
                }
            }
            "r8" -> {
                val jar = store.jar("r8")
                if (jar == null) {
                    Result(1, "R8 is missing from the build tools.")
                } else {
                    val result = inDir(workDir) {
                        ToolExec.r8(listOf(jar), args, dexOptDir(context))
                    }
                    Result(result.code, result.output)
                }
            }
            "kotlinc" -> kotlinc(context, args, workDir)
            else -> Result(2, "usage: closecode tool aapt2|d8|r8|kotlinc [args...]")
        }
    }

    /**
     * `-kotlin-home` and `-no-jdk` are what make the same compiler that builds CloseCode
     * projects usable as a standalone command on Android. A caller that supplied either
     * flag keeps control; otherwise the wrapper adds them before the caller's arguments.
     */
    fun kotlincArgs(args: List<String>, kotlinHome: File?): List<String> = buildList {
        if (args.none { it == "-no-jdk" }) add("-no-jdk")
        if (kotlinHome != null &&
            args.none { it == "-kotlin-home" || it.startsWith("-kotlin-home=") }
        ) {
            add("-kotlin-home")
            add(kotlinHome.absolutePath)
        }
        addAll(args)
    }

    private fun kotlinc(context: Context, args: List<String>, workDir: File): Result {
        val store = ToolchainHolder.store(context)
        val jars = ToolchainManifest.KOTLINC_LAUNCH_IDS.mapNotNull { store.jar(it) }
        if (jars.size != ToolchainManifest.KOTLINC_LAUNCH_IDS.size) {
            return Result(1, "The Kotlin compiler is missing from the build tools.")
        }
        val resources = KotlinCompat.resources(context)
        val home = store.jar("kotlin-stdlib")?.let { KotlinHome.ensure(it) }
        val result = inDir(workDir) {
            ToolExec.kotlinc(
                jars = jars + listOfNotNull(resources),
                args = kotlincArgs(args, home),
                dexOptDir = dexOptDir(context),
            )
        }
        return Result(result.code, result.output)
    }

    private fun dexOptDir(context: Context): File =
        File(context.codeCacheDir, "build-dex")

    private fun <T> inDir(directory: File, block: () -> T): T {
        val previous = System.getProperty("user.dir")
        System.setProperty("user.dir", directory.absolutePath)
        return try {
            block()
        } finally {
            if (previous == null) System.clearProperty("user.dir")
            else System.setProperty("user.dir", previous)
        }
    }
}
