package com.closecode.control

import android.content.Context
import com.closecode.build.GradleWrapper
import com.closecode.build.PlatformStore
import com.closecode.build.SetupKeepAliveService
import com.closecode.debug.DebugLog
import com.closecode.tools.GradleToolsStore
import com.closecode.tools.JdkStore
import com.closecode.tools.NativeToolsStore
import com.closecode.tools.OptionalPack
import com.closecode.tools.OptionalPacks
import com.closecode.tools.ShellEnvironment
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `closecode setup`: the CLI path the agent uses to install Full Gradle, the SDK platform,
 * build-tools and the optional web/python/native packs. It drives the same downloaders as
 * Settings, verifies every checksum before use, is idempotent, and emits line-delimited JSON
 * so the caller can follow progress.
 */
object SetupCommand {

    private const val MAX_SELF_TEST_CHARS = 8_000

    private val languagePacks: List<OptionalPack> get() = OptionalPacks.languages

    data class Options(
        val gradle: Boolean = false,
        val web: Boolean = false,
        val python: Boolean = false,
        val native: Boolean = false,
        val media: Boolean = false,
        val languages: Set<String> = emptySet(),
        val java21: Boolean = false,
        val all: Boolean = false,
        val sdk: Int? = null,
        val buildTools: String? = null,
        val ndk: Boolean = false,
        val cmake: Boolean = false,
        val yes: Boolean = false,
        val wifiOnly: Boolean = false,
        val remove: String? = null,
        val gradleVersion: String? = null,
        val help: Boolean = false,
        val invalid: List<String> = emptyList(),
    ) {
        val wantsGradle: Boolean get() = gradle || all
        val wantsWeb: Boolean get() = web || python || all
        val wantsNative: Boolean get() = native || ndk || cmake || all
        val wantsMedia: Boolean get() = media
        /** True when the request would install or remove something. */
        fun installsSomething(): Boolean =
            wantsGradle || wantsWeb || wantsNative || wantsMedia || java21 ||
                languages.isNotEmpty() || sdk != null || buildTools != null ||
                !remove.isNullOrBlank()
        val valid: Boolean
            get() = invalid.isEmpty() && (wantsGradle || wantsWeb || wantsNative || wantsMedia ||
                java21 || languages.isNotEmpty() || sdk != null || buildTools != null ||
                !remove.isNullOrBlank())
    }

    fun parse(arguments: List<String>): Options {
        var options = Options()
        fun invalid(message: String) {
            options = options.copy(invalid = options.invalid + message)
        }
        var index = 0
        while (index < arguments.size) {
            when (val arg = arguments[index]) {
                "--help", "-h", "help" -> options = options.copy(help = true)
                "--json" -> Unit
                "--gradle", "gradle", "--full-gradle" -> options = options.copy(gradle = true)
                "--web", "web" -> options = options.copy(web = true)
                "--python", "python" -> options = options.copy(python = true)
                "--native", "native" -> options = options.copy(native = true)
                "--media", "media", "--ffmpeg" -> options = options.copy(media = true)
                "--java21", "--jdk21", "--java-21", "--jdk-21" ->
                    options = options.copy(java21 = true)
                "--go", "go", "--golang" -> options = options.copy(languages = options.languages + "go")
                "--rust", "rust", "--cargo" -> options = options.copy(languages = options.languages + "rust")
                "--dart", "dart" -> options = options.copy(languages = options.languages + "dart")
                "--php", "php" -> options = options.copy(languages = options.languages + "php")
                "--ruby", "ruby" -> options = options.copy(languages = options.languages + "ruby")
                "--deno", "deno" -> options = options.copy(languages = options.languages + "deno")
                "--bun", "bun" -> options = options.copy(languages = options.languages + "bun")
                "--all", "all" -> options = options.copy(all = true)
                "--yes", "-y" -> options = options.copy(yes = true)
                "--wifi-only", "wifi", "--wifi" -> options = options.copy(wifiOnly = true)
                "--ndk" -> options = options.copy(ndk = true)
                "--cmake" -> options = options.copy(cmake = true)
                "--jdk", "--java" -> {
                    val value = arguments.getOrNull(index + 1)?.takeUnless { it.startsWith("-") }
                    if (value != null) {
                        index++
                        if (value == "21") {
                            options = options.copy(java21 = true)
                        } else {
                            invalid("--jdk only offers 21; JDK 17 is part of Full Gradle")
                        }
                    } else {
                        invalid("--jdk only offers 21; JDK 17 is part of Full Gradle")
                    }
                }
                "--sdk" -> {
                    val value = arguments.getOrNull(index + 1)?.toIntOrNull()?.takeIf { it > 0 }
                    if (value != null) {
                        options = options.copy(sdk = value)
                        index++
                    } else {
                        invalid("--sdk needs an Android API number")
                    }
                }
                "--build-tools" -> {
                    val value = arguments.getOrNull(index + 1)?.takeUnless { it.startsWith("-") }
                    if (value != null) {
                        options = options.copy(buildTools = value)
                        index++
                    } else {
                        invalid("--build-tools needs a version")
                    }
                }
                "--gradle-version" -> {
                    val value = arguments.getOrNull(index + 1)?.takeUnless { it.startsWith("-") }
                    if (value != null) {
                        options = options.copy(gradleVersion = value.trim())
                        index++
                    } else {
                        invalid("--gradle-version needs a version")
                    }
                }
                "--remove" -> {
                    val value = arguments.getOrNull(index + 1)?.takeUnless { it.startsWith("-") }
                    if (value != null) {
                        options = options.copy(remove = value.lowercase())
                        index++
                    } else {
                        invalid("--remove needs a component name")
                    }
                }
                else -> when {
                    arg.startsWith("--build-tools=") -> {
                        val value = arg.substringAfter('=')
                        if (value.isBlank()) invalid("--build-tools needs a version")
                        else options = options.copy(buildTools = value)
                    }
                    arg.startsWith("--sdk=") -> {
                        val value = arg.substringAfter('=').toIntOrNull()?.takeIf { it > 0 }
                        if (value == null) invalid("--sdk needs an Android API number")
                        else options = options.copy(sdk = value)
                    }
                    arg.startsWith("--gradle-version=") -> {
                        val value = arg.substringAfter('=').trim()
                        if (value.isBlank()) invalid("--gradle-version needs a version")
                        else options = options.copy(gradleVersion = value)
                    }
                    arg.startsWith("--remove=") -> {
                        val value = arg.substringAfter('=').trim()
                        if (value.isBlank()) invalid("--remove needs a component name")
                        else options = options.copy(remove = value.lowercase())
                    }
                    arg.startsWith("--jdk=") || arg.startsWith("--java=") -> {
                        val value = arg.substringAfter('=').trim()
                        if (value == "21") options = options.copy(java21 = true)
                        else invalid("--jdk only offers 21; JDK 17 is part of Full Gradle")
                    }
                    else -> invalid("unknown option '$arg'")
                }
            }
            index++
        }
        // "setup --native --help" asks for two different things. The install is refused with
        // the usage, so nothing is downloaded; a plain "setup --help" is still the help.
        if (options.help && options.installsSomething()) {
            options = options.copy(
                help = false,
                invalid = options.invalid +
                    "--help cannot be combined with an install option; run them one at a time",
            )
        }
        return options
    }

    fun usage(): String = """
        usage: closecode setup --gradle [--sdk 35] [--build-tools 35.0.0] [--ndk] [--cmake]
               closecode setup --gradle --gradle-version 8.12
               closecode setup --web
               closecode setup --python
               closecode setup --native
               closecode setup --media
               closecode setup --java21
               closecode setup --go
               closecode setup --rust
               closecode setup --dart|--php|--ruby|--deno|--bun
               closecode setup --all
               closecode setup --remove gradle|sdk|platform-tools|licenses|native|web|python|java21
        Options: --json --yes --wifi-only
    """.trimIndent()

    fun run(
        context: Context,
        options: Options,
        cwd: String? = null,
        emit: (JsonObject) -> Unit,
    ): JsonObject {
        if (options.help) {
            return JsonObject().apply {
                addProperty("ok", true)
                addProperty("event", "help")
                addProperty("usage", usage())
            }
        }
        if (options.invalid.isNotEmpty()) {
            return JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", options.invalid.joinToString("; "))
                addProperty("code", "usage")
                addProperty("usage", usage())
            }
        }
        if (!options.valid) {
            return error("Nothing to install. ${usage()}", "setup")
        }
        SetupKeepAliveService.start(context)
        return try {
            if (!options.remove.isNullOrBlank()) return remove(context, options.remove, emit)
            install(context, options, cwd, emit)
        } catch (error: Throwable) {
            DebugLog.crumb("SETUP", "failed: ${error.message ?: error.javaClass.simpleName}")
            emit(event("error").apply {
                addProperty("error", error.message ?: error.javaClass.simpleName)
                addProperty("remedy", "Re-run closecode setup; verified downloads resume.")
            })
            error(error.message ?: error.javaClass.simpleName, "setup")
        } finally {
            SetupKeepAliveService.stop(context)
        }
    }

    private fun install(
        context: Context,
        options: Options,
        cwd: String?,
        emit: (JsonObject) -> Unit,
    ): JsonObject {
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        platform.acceptLicense()
        val gradle = GradleToolsStore(context)
        val shellTools = ShellToolsStore(context, ShellToolsManifest.load(context))
        val mediaTools = OptionalPacks.store(context, OptionalPacks.media)
        val languageStores = languagePacks
            .filter { it.id in options.languages }
            .associate { pack -> pack.id to OptionalPacks.store(context, pack) }
        val native = NativeToolsStore(context)
        val sdk = options.sdk ?: 35
        val requestedBuildTools = options.buildTools ?: platform.buildToolsVersion()
        val components = JsonArray()
        if (options.wantsGradle) {
            components.add("jdk")
            components.add("gradle")
            components.add("platform-$sdk")
            components.add("build-tools-$requestedBuildTools")
            // P0-3: AGP validates the platform-tools package before it runs any task.
            components.add("platform-tools")
            components.add("licenses")
        } else {
            if (options.sdk != null) components.add("platform-${options.sdk}")
            if (options.buildTools != null) components.add("build-tools-$requestedBuildTools")
        }
        if (options.wantsWeb) components.add("web-python")
        if (options.wantsMedia) components.add("media-tools")
        if (options.java21) components.add("java21")
        options.languages.sorted().forEach { components.add("$it-tools") }
        if (options.wantsNative) {
            if (options.cmake || options.native || options.all) components.add("cmake")
            if (options.ndk || options.native || options.all) components.add("ndk")
        }
        emit(event("start").apply { add("components", components) })

        if (options.java21) {
            val jdk = JdkStore(context)
            var lastBytes = 0L
            var lastAt = System.currentTimeMillis()
            val result = jdk.install21 { progress ->
                val now = System.currentTimeMillis()
                val speed = if (now > lastAt) {
                    ((progress.doneBytes - lastBytes) * 1000L / (now - lastAt)).coerceAtLeast(0)
                } else {
                    0L
                }
                if (now - lastAt >= 500 || progress.stage == "done") {
                    lastBytes = progress.doneBytes
                    lastAt = now
                }
                emit(event("download").apply {
                    addProperty("component", "java21")
                    addProperty("stage", progress.stage)
                    addProperty("received", progress.doneBytes)
                    addProperty("total", progress.totalBytes)
                    addProperty("bytesPerSec", speed)
                    progress.packageName?.let { addProperty("package", it) }
                    addProperty("message", progress.message)
                })
            }
            if (!result.ok) {
                throw IllegalStateException(result.error ?: "JDK 21 did not install.")
            }
            emit(event("verify").apply {
                addProperty("component", "java21")
                addProperty("ok", jdk.jdk21Ready())
            })
        }

        if (options.wantsGradle || options.sdk != null) {
            val target = if (options.wantsGradle) sdk else options.sdk!!
            emit(event("download").apply {
                addProperty("component", "platform-$target")
                addProperty("received", 0L)
                addProperty("total", 0L)
            })
            val result = platform.installPlatform(target) { done, total ->
                emit(event("download").apply {
                    addProperty("component", "platform-$target")
                    addProperty("received", done)
                    addProperty("total", total)
                })
            }
            result.exceptionOrNull()?.let { throw it }
            emit(event("verify").apply {
                addProperty("component", "platform-$target")
                addProperty("ok", true)
            })
        }

        if (options.wantsGradle || options.buildTools != null) {
            emit(event("download").apply {
                addProperty("component", "build-tools-$requestedBuildTools")
                addProperty("received", 0L)
                addProperty("total", 0L)
            })
            val result = platform.installBuildTools { done, total ->
                emit(event("download").apply {
                    addProperty("component", "build-tools-$requestedBuildTools")
                    addProperty("received", done)
                    addProperty("total", total)
                })
            }
            result.exceptionOrNull()?.let { throw it }
            ensureBuildToolsAlias(platform, requestedBuildTools)
            emit(event("verify").apply {
                addProperty("component", "build-tools-$requestedBuildTools")
                addProperty("ok", platform.buildToolsDir(requestedBuildTools) != null ||
                    platform.bestBuildTools(requestedBuildTools) != null)
            })
        }

        if (options.wantsGradle) {
            // P0-3: a separately-versioned package; the archive already contains platform-tools/.
            emit(event("download").apply {
                addProperty("component", "platform-tools")
                addProperty("received", 0L)
                addProperty("total", PlatformStore.PLATFORM_TOOLS_SIZE)
            })
            val result = platform.installPlatformTools { done, total ->
                emit(event("download").apply {
                    addProperty("component", "platform-tools")
                    addProperty("received", done)
                    addProperty("total", total)
                })
            }
            result.exceptionOrNull()?.let { throw it }
            // P0-4: acceptLicense() wrote these, but re-assert after every install so a
            // removed licenses/ folder is repaired by re-running setup.
            platform.installLicenses()
            emit(event("verify").apply {
                addProperty("component", "platform-tools")
                addProperty("ok", platform.platformToolsReady())
            })
            emit(event("verify").apply {
                addProperty("component", "licenses")
                addProperty("ok", platform.licensesReady())
            })
        }

        if (options.wantsGradle) {
            val wrapper = options.gradleVersion
                ?: cwd?.takeIf { it.isNotBlank() }?.let {
                    GradleWrapper.version(File(it))
                }
            val gradleVersion = wrapper
                ?.takeIf { gradle.manifest.forVersion(it) != null }
                ?: gradle.manifest.distribution.version
            var lastBytes = 0L
            var lastAt = System.currentTimeMillis()
            val result = gradle.install(progress = { progress ->
                val now = System.currentTimeMillis()
                val speed = if (now > lastAt) {
                    ((progress.doneBytes - lastBytes) * 1000L / (now - lastAt)).coerceAtLeast(0)
                } else {
                    0L
                }
                if (now - lastAt >= 500 || progress.stage == "done") {
                    lastBytes = progress.doneBytes
                    lastAt = now
                }
                emit(event("download").apply {
                    addProperty("component", "jdk-gradle")
                    addProperty("gradleVersion", gradleVersion)
                    addProperty("stage", progress.stage)
                    addProperty("received", progress.doneBytes)
                    addProperty("total", progress.totalBytes)
                    addProperty("bytesPerSec", speed)
                    progress.packageName?.let { addProperty("package", it) }
                    addProperty("message", progress.message)
                })
            }, version = gradleVersion)
            if (!result.ok) throw IllegalStateException(result.error ?: "The Gradle tools did not install.")
            emit(event("verify").apply {
                addProperty("component", "jdk-gradle")
                addProperty("gradleVersion", gradleVersion)
                addProperty("ok", gradle.isReady(gradleVersion))
            })
        }

        if (options.wantsWeb) {
            var lastBytes = 0L
            var lastAt = System.currentTimeMillis()
            val result = shellTools.install { progress ->
                val now = System.currentTimeMillis()
                val speed = if (now > lastAt) {
                    ((progress.doneBytes - lastBytes) * 1000L / (now - lastAt)).coerceAtLeast(0)
                } else {
                    0L
                }
                if (now - lastAt >= 500 || progress.stage == "done") {
                    lastBytes = progress.doneBytes
                    lastAt = now
                }
                emit(event("download").apply {
                    addProperty("component", "web-python")
                    addProperty("stage", progress.stage)
                    addProperty("received", progress.doneBytes)
                    addProperty("total", progress.totalBytes)
                    addProperty("bytesPerSec", speed)
                    progress.packageName?.let { addProperty("package", it) }
                    addProperty("message", progress.message)
                })
            }
            if (!result.ok) throw IllegalStateException(result.error ?: "The shell tools did not install.")
            emit(event("verify").apply {
                addProperty("component", "web-python")
                addProperty("ok", shellTools.isReady())
            })
        }

        if (options.wantsMedia) {
            var lastBytes = 0L
            var lastAt = System.currentTimeMillis()
            val result = mediaTools.install { progress ->
                val now = System.currentTimeMillis()
                val speed = if (now > lastAt) {
                    ((progress.doneBytes - lastBytes) * 1000L / (now - lastAt)).coerceAtLeast(0)
                } else {
                    0L
                }
                if (now - lastAt >= 500 || progress.stage == "done") {
                    lastBytes = progress.doneBytes
                    lastAt = now
                }
                emit(event("download").apply {
                    addProperty("component", "media-tools")
                    addProperty("stage", progress.stage)
                    addProperty("received", progress.doneBytes)
                    addProperty("total", progress.totalBytes)
                    addProperty("bytesPerSec", speed)
                    progress.packageName?.let { addProperty("package", it) }
                    addProperty("message", progress.message)
                })
            }
            if (!result.ok) throw IllegalStateException(result.error ?: "The media tools did not install.")
            emit(event("verify").apply {
                addProperty("component", "media-tools")
                addProperty("ok", mediaTools.isReady())
            })
        }

        languageStores.forEach { (id, store) ->
            val result = installPack(context, store, "$id-tools", emit)
            if (!result.ok) throw IllegalStateException(result.error ?: "The $id tools did not install.")
            emit(event("verify").apply {
                addProperty("component", "$id-tools")
                addProperty("ok", store.isReady())
            })
        }

        if (options.wantsNative) {
            var lastBytes = 0L
            var lastAt = System.currentTimeMillis()
            val result = native.install { progress ->
                val now = System.currentTimeMillis()
                val speed = if (now > lastAt) {
                    ((progress.doneBytes - lastBytes) * 1000L / (now - lastAt)).coerceAtLeast(0)
                } else {
                    0L
                }
                if (now - lastAt >= 500 || progress.stage == "done") {
                    lastBytes = progress.doneBytes
                    lastAt = now
                }
                emit(event("download").apply {
                    addProperty("component", "native")
                    addProperty("stage", progress.stage)
                    addProperty("received", progress.doneBytes)
                    addProperty("total", progress.totalBytes)
                    addProperty("bytesPerSec", speed)
                    addProperty("message", progress.message)
                })
            }
            if (!result.ok) throw IllegalStateException(result.error ?: "The native tools did not install.")
            emit(event("verify").apply {
                addProperty("component", "native")
                addProperty("ok", native.isReady())
            })
        }

        val tests = JsonArray()
        if (options.wantsGradle) {
            tests.add(selfTest("java", gradle.java, listOf("-version"), context))
            tests.add(selfTest("gradle", gradle.gradle, listOf("-v"), context))
            platform.buildToolsAapt2()?.let { aapt2 ->
                tests.add(selfTest("aapt2", aapt2, listOf("version"), context))
            }
            // P0-3: prove the package is usable, not merely present.
            tests.add(
                selfTest(
                    "platform-tools",
                    File(platform.platformToolsDir(), "adb"),
                    listOf("version"),
                    context,
                ),
            )
        }
        languageStores.forEach { (id, store) ->
            languagePacks.first { it.id == id }.checks.forEach { (name, arguments) ->
                val command = File(store.directory, "bin/$name")
                if (command.isFile) tests.add(selfTest(name, command, arguments, context))
            }
        }
        if (options.wantsMedia) {
            val prefix = mediaTools.directory
            for ((name, command) in listOf(
                "ffmpeg" to File(prefix, "bin/ffmpeg"),
                "sqlite3" to File(prefix, "bin/sqlite3"),
                "magick" to File(prefix, "bin/magick"),
            )) {
                if (command.isFile) tests.add(selfTest(name, command, listOf("-version"), context))
            }
        }
        if (options.wantsWeb) {
            val prefix = shellTools.directory
            for ((name, command) in listOf(
                "node" to File(prefix, "bin/node"),
                "npm" to File(prefix, "bin/npm"),
                "python" to File(prefix, "bin/python3"),
                "pip" to File(prefix, "bin/pip"),
                "uv" to File(prefix, "bin/uv"),
            )) {
                if (command.isFile) tests.add(selfTest(name, command, listOf("--version"), context))
            }
            // P1-1: the real proof is a virtual environment that ends up with a working pip.
            File(prefix, "bin/python3").takeIf { it.isFile }?.let { python ->
                tests.add(venvSelfTest(python, context))
            }
        }
        emit(event("selftest").apply { add("checks", tests) })
        val ready = tests.all { it.asJsonObject.get("ok")?.asBoolean == true }
        emit(event("done").apply {
            addProperty("ready", ready)
            addProperty("installCommand", "closecode setup --gradle")
        })
        val response = JsonObject().apply {
            addProperty("ok", ready)
            addProperty("event", "done")
            addProperty("ready", ready)
            add("checks", tests)
        }
        if (!ready) {
            response.addProperty("error", "A self-test failed. Run closecode doctor --json.")
            response.addProperty("remedy", "Re-run closecode setup; verified downloads resume.")
        }
        return response
    }

    private fun installPack(
        context: Context,
        store: ShellToolsStore,
        component: String,
        emit: (JsonObject) -> Unit,
    ): ShellToolsStore.Result {
        var lastBytes = 0L
        var lastAt = System.currentTimeMillis()
        return store.install { progress ->
            val now = System.currentTimeMillis()
            val speed = if (now > lastAt) {
                ((progress.doneBytes - lastBytes) * 1000L / (now - lastAt)).coerceAtLeast(0)
            } else {
                0L
            }
            if (now - lastAt >= 500 || progress.stage == "done") {
                lastBytes = progress.doneBytes
                lastAt = now
            }
            emit(event("download").apply {
                addProperty("component", component)
                addProperty("stage", progress.stage)
                addProperty("received", progress.doneBytes)
                addProperty("total", progress.totalBytes)
                addProperty("bytesPerSec", speed)
                progress.packageName?.let { addProperty("package", it) }
                addProperty("message", progress.message)
            })
        }
    }

    private fun remove(context: Context, what: String, emit: (JsonObject) -> Unit): JsonObject {
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        // The Gradle dependency cache is not part of the Gradle install. Removing the tool
        // keeps it, so the next build is as fast as it was; clearing it is a separate action.
        val gradleHome = GradleToolsStore(context).gradleHome
        val removed = when (what) {
            "gradle", "jdk" -> GradleToolsStore(context).remove()
            "sdk" -> platform.removeAllPlatforms() && platform.removeAllBuildTools()
            "platform-tools", "adb" -> platform.removePlatformTools()
            "licenses" -> platform.removeAllLicenses()
            "build-tools", "buildtools" -> platform.removeAllBuildTools()
            "native", "ndk", "cmake" -> NativeToolsStore(context).remove()
            "java21", "jdk21", "jdk-21" -> JdkStore(context).remove21()
            "web", "python", "shell", "shell-tools" ->
                ShellToolsStore(context, ShellToolsManifest.load(context)).remove()
            "media", "ffmpeg", "media-tools" ->
                OptionalPacks.store(context, OptionalPacks.media).remove()
            "go", "golang", "go-tools", "rust", "cargo", "rust-tools",
            "dart", "dart-tools", "php", "php-tools", "ruby", "ruby-tools",
            "deno", "deno-tools", "bun", "bun-tools" -> {
                val id = what.substringBefore('-').let {
                    when (it) {
                        "golang" -> "go"
                        "cargo" -> "rust"
                        else -> it
                    }
                }
                val pack = languagePacks.firstOrNull { it.id == id }
                    ?: return error("Unknown component '$what'. ${usage()}", "setup")
                OptionalPacks.store(context, pack).remove()
            }
            else -> return error("Unknown component '$what'. ${usage()}", "setup")
        }
        emit(event("done").apply {
            addProperty("removed", removed)
            addProperty("component", what)
            if (what == "gradle" || what == "jdk") {
                addProperty("kept", gradleHome.absolutePath)
                addProperty(
                    "message",
                    "The dependency cache in ${gradleHome.absolutePath} was kept. " +
                        "Run closecode clean --gradle-caches to clear it on purpose.",
                )
            }
        })
        return JsonObject().apply {
            addProperty("ok", removed)
            addProperty("event", "done")
            addProperty("removed", removed)
            addProperty("component", what)
            if (what == "gradle" || what == "jdk") {
                addProperty("kept", gradleHome.absolutePath)
                addProperty(
                    "message",
                    "The dependency cache in ${gradleHome.absolutePath} was kept. " +
                        "Run closecode clean --gradle-caches to clear it on purpose.",
                )
            }
            if (!removed) addProperty("error", "Could not remove $what.")
        }
    }

    /**
     * Makes sure the build-tools version a project asks for exists and can run here.
     *
     * The app ships one version, built for ARM64. A project that pins another version gets a
     * copy of that one under the name it asked for — and if a folder with that name already
     * exists but holds binaries for another processor, it is replaced, because it could never
     * run on this phone.
     */
    private fun ensureBuildToolsAlias(platform: PlatformStore, requested: String) {
        if (requested.isBlank()) return
        val source = platform.buildToolsDir() ?: return
        if (requested == source.name) return
        val target = File(platform.rootDir(), "build-tools/$requested")
        val existing = platform.buildToolsDir(requested)
        if (existing != null && platform.buildToolsUsable(existing)) return
        if (existing != null) {
            // The folder is there but its tools are for another processor.
            existing.deleteRecursively()
        }
        runCatching {
            source.copyRecursively(target, overwrite = true)
            File(target, ".closecode-shim").writeText(
                "Compatibility copy of build-tools ${source.name} for $requested\n",
            )
            for (name in listOf("source.properties", "package.xml")) {
                val file = File(target, name)
                if (!file.isFile) continue
                val text = file.readText()
                    .replace(Regex("Pkg\\.Revision=.*"), "Pkg.Revision=$requested")
                    .replace(
                        Regex("<revision>\\s*<major>(\\d+)</major>\\s*<minor>(\\d+)</minor>\\s*<micro>(\\d+)</micro>\\s*</revision>"),
                        "<revision><major>${requested.substringBefore('.')}</major>" +
                            "<minor>${requested.substringAfter('.').substringBefore('.')}</minor>" +
                            "<micro>${requested.substringAfterLast('.')}</micro></revision>",
                    )
                file.writeText(text)
            }
        }.onFailure {
            DebugLog.crumb("SETUP", "build-tools alias failed: ${it.javaClass.simpleName}")
        }
    }

    /**
     * A real self-test (P1-5): exit code 0 *and* output that names the tool's version. The old
     * check only looked for a non-empty line, so a launcher that printed
     * `Could not find or load main class "-Xmx64m"` was reported as ok:true.
     */
    private fun selfTest(
        name: String,
        executable: File,
        arguments: List<String>,
        context: Context,
    ): JsonObject {
        val environment = ShellEnvironment.env(context, File(context.filesDir, "tmp"))
        val text = StringBuilder()
        var finished = false
        var code = -1
        val failure = runCatching {
            val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(text) {
                            if (text.length < MAX_SELF_TEST_CHARS) text.appendLine(line)
                        }
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
            finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            reader.join(2_000)
            code = runCatching { process.exitValue() }.getOrDefault(1)
        }.exceptionOrNull()
        val output = synchronized(text) { text.toString().trim() }
        val firstLine = output.lineSequence().firstOrNull().orEmpty().take(300)
        val broken = SelfTestRules.looksBroken(output)
        val ok = executable.isFile && failure == null && finished && code == 0 &&
            output.isNotBlank() && !broken && SelfTestRules.versionLooksRight(name, output)
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("ok", ok)
            addProperty("path", executable.absolutePath)
            addProperty("exitCode", code)
            addProperty("version", firstLine)
            if (!ok) {
                addProperty(
                    "error",
                    failure?.message ?: when {
                        !executable.isFile -> "The file is missing."
                        !finished -> "The check did not finish in 20 seconds."
                        code != 0 -> "Exit code $code."
                        broken -> firstLine.ifBlank { "The tool printed an error." }
                        else -> "The version output was not recognised."
                    },
                )
            }
        }
    }

    /**
     * P1-1: the self-test that matters for Python is a virtual environment that ends up with
     * a working pip — `python3 -m venv DIR && DIR/bin/pip -V`. A python that only answers
     * `--version` is not enough.
     */
    private fun venvSelfTest(python: File, context: Context): JsonObject {
        val directory = File(context.filesDir, "tmp/closecode-venv-check")
        directory.deleteRecursively()
        val environment = ShellEnvironment.env(context, File(context.filesDir, "tmp"))
        val created = runCatching {
            val process = ProcessBuilder(
                listOf(python.absolutePath, "-m", "venv", directory.absolutePath),
            )
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(120, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            (finished to text)
        }.getOrElse { error ->
            return JsonObject().apply {
                addProperty("name", "venv")
                addProperty("ok", false)
                addProperty("error", error.message ?: "The virtual environment could not be created.")
            }
        }
        val pip = File(directory, "bin/pip")
        val pipVersion = if (pip.isFile) {
            runCatching {
                val process = ProcessBuilder(listOf(pip.absolutePath, "-V"))
                    .redirectErrorStream(true)
                    .apply { environment().putAll(environment) }
                    .start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor(30, TimeUnit.SECONDS)
                text.trim()
            }.getOrDefault("")
        } else {
            ""
        }
        val ok = created.first && pip.isFile && Regex("pip \\d+").containsMatchIn(pipVersion)
        return JsonObject().apply {
            addProperty("name", "venv")
            addProperty("ok", ok)
            addProperty("path", directory.absolutePath)
            addProperty("version", pipVersion.take(300))
            if (!ok) {
                addProperty(
                    "error",
                    if (!created.first) {
                        created.second.trim().take(300).ifBlank { "python3 -m venv failed." }
                    } else {
                        "The virtual environment was created but has no working pip."
                    },
                )
                addProperty(
                    "remedy",
                    "Re-run closecode setup --python; the installer puts a verified pip wheel " +
                        "into ensurepip.",
                )
            }
        }
    }

    private fun event(name: String): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("event", name)
    }

    private fun error(message: String, code: String): JsonObject = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", message)
        addProperty("code", code)
        addProperty("remedy", "Run closecode setup --gradle, or closecode doctor --json.")
        addProperty("installCommand", "closecode setup --gradle")
    }
}
