package com.closecode.control

import android.content.Context
import com.closecode.build.GradleWrapper
import com.closecode.build.PlatformStore
import com.closecode.build.SetupKeepAliveService
import com.closecode.debug.DebugLog
import com.closecode.tools.GradleToolsStore
import com.closecode.tools.NativeToolsStore
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

    private data class LanguagePack(
        val id: String,
        val asset: String,
        val prefix: String,
        val home: String,
        val label: String,
        val checks: List<Pair<String, List<String>>>,
    )

    private val languagePacks = listOf(
        LanguagePack("go", "shell-go.json", "go", "hgo", "Go", listOf("go" to listOf("version"))),
        LanguagePack("rust", "shell-rust.json", "rs", "hrs", "Rust", listOf(
            "rustc" to listOf("--version"),
            "cargo" to listOf("--version"),
        )),
        LanguagePack("dart", "shell-dart.json", "dart", "hdart", "Dart", listOf("dart" to listOf("--version"))),
        LanguagePack("php", "shell-php.json", "php", "hphp", "PHP", listOf("php" to listOf("--version"))),
        LanguagePack("ruby", "shell-ruby.json", "rb", "hrb", "Ruby", listOf("ruby" to listOf("--version"))),
        LanguagePack("deno", "shell-deno.json", "deno", "hdeno", "Deno", listOf("deno" to listOf("--version"))),
        LanguagePack("bun", "shell-bun.json", "bun", "hbun", "Bun", listOf("bun" to listOf("--version"))),
    )

    data class Options(
        val gradle: Boolean = false,
        val web: Boolean = false,
        val python: Boolean = false,
        val native: Boolean = false,
        val media: Boolean = false,
        val languages: Set<String> = emptySet(),
        val all: Boolean = false,
        val sdk: Int? = null,
        val buildTools: String? = null,
        val ndk: Boolean = false,
        val cmake: Boolean = false,
        val yes: Boolean = false,
        val wifiOnly: Boolean = false,
        val remove: String? = null,
        val gradleVersion: String? = null,
    ) {
        val wantsGradle: Boolean get() = gradle || all
        val wantsWeb: Boolean get() = web || python || all
        val wantsNative: Boolean get() = native || ndk || cmake || all
        val wantsMedia: Boolean get() = media
        val valid: Boolean
            get() = wantsGradle || wantsWeb || wantsNative || wantsMedia || languages.isNotEmpty() ||
                sdk != null || buildTools != null || !remove.isNullOrBlank()
    }

    fun parse(arguments: List<String>): Options {
        var options = Options()
        var index = 0
        while (index < arguments.size) {
            when (val arg = arguments[index]) {
                "--gradle", "gradle", "--full-gradle" -> options = options.copy(gradle = true)
                "--web", "web" -> options = options.copy(web = true)
                "--python", "python" -> options = options.copy(python = true)
                "--native", "native" -> options = options.copy(native = true)
                "--media", "media", "--ffmpeg" -> options = options.copy(media = true)
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
                "--sdk" -> {
                    val value = arguments.getOrNull(index + 1)?.toIntOrNull()
                    if (value != null) {
                        options = options.copy(sdk = value)
                        index++
                    }
                }
                "--build-tools" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value != null) {
                        options = options.copy(buildTools = value)
                        index++
                    }
                }
                "--gradle-version" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value != null) {
                        options = options.copy(gradleVersion = value.trim())
                        index++
                    }
                }
                "--remove" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value != null) {
                        options = options.copy(remove = value.lowercase())
                        index++
                    }
                }
                else -> if (arg.startsWith("--build-tools=")) {
                    options = options.copy(buildTools = arg.substringAfter('='))
                } else if (arg.startsWith("--sdk=")) {
                    arg.substringAfter('=').toIntOrNull()?.let { options = options.copy(sdk = it) }
                } else if (arg.startsWith("--gradle-version=")) {
                    options = options.copy(gradleVersion = arg.substringAfter('=').trim())
                } else if (arg.startsWith("--remove=")) {
                    options = options.copy(remove = arg.substringAfter('=').lowercase())
                }
            }
            index++
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
               closecode setup --go
               closecode setup --rust
               closecode setup --dart|--php|--ruby|--deno|--bun
               closecode setup --all
               closecode setup --remove gradle|sdk|native|web|python
        Options: --json --yes --wifi-only
    """.trimIndent()

    fun run(
        context: Context,
        options: Options,
        cwd: String? = null,
        emit: (JsonObject) -> Unit,
    ): JsonObject {
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
        val mediaTools = ShellToolsStore(
            context,
            ShellToolsManifest.load(context, "shell-media.json"),
            prefixName = "m",
            homeName = "hm",
            label = "Media tools",
            cachePrefix = "media-tools",
        )
        val languageStores = languagePacks
            .filter { it.id in options.languages }
            .associate { pack ->
                pack.id to ShellToolsStore(
                    context,
                    ShellToolsManifest.load(context, pack.asset),
                    prefixName = pack.prefix,
                    homeName = pack.home,
                    label = "${pack.label} tools",
                    cachePrefix = "${pack.id}-tools",
                )
            }
        val native = NativeToolsStore(context)
        val sdk = options.sdk ?: 35
        val requestedBuildTools = options.buildTools ?: platform.buildToolsVersion()
        val components = JsonArray()
        if (options.wantsGradle) {
            components.add("jdk")
            components.add("gradle")
            components.add("platform-$sdk")
            components.add("build-tools-$requestedBuildTools")
        } else {
            if (options.sdk != null) components.add("platform-${options.sdk}")
            if (options.buildTools != null) components.add("build-tools-$requestedBuildTools")
        }
        if (options.wantsWeb) components.add("web-python")
        if (options.wantsMedia) components.add("media-tools")
        options.languages.sorted().forEach { components.add("$it-tools") }
        if (options.wantsNative) {
            if (options.cmake || options.native || options.all) components.add("cmake")
            if (options.ndk || options.native || options.all) components.add("ndk")
        }
        emit(event("start").apply { add("components", components) })

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
        val removed = when (what) {
            "gradle", "jdk" -> GradleToolsStore(context).remove()
            "sdk" -> platform.removeAllPlatforms() && platform.removeAllBuildTools()
            "build-tools", "buildtools" -> platform.removeAllBuildTools()
            "native", "ndk", "cmake" -> NativeToolsStore(context).remove()
            "web", "python", "shell", "shell-tools" ->
                ShellToolsStore(context, ShellToolsManifest.load(context)).remove()
            "media", "ffmpeg", "media-tools" -> ShellToolsStore(
                context,
                ShellToolsManifest.load(context, "shell-media.json"),
                prefixName = "m",
                homeName = "hm",
                label = "Media tools",
                cachePrefix = "media-tools",
            ).remove()
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
                ShellToolsStore(
                    context,
                    ShellToolsManifest.load(context, pack.asset),
                    prefixName = pack.prefix,
                    homeName = pack.home,
                    label = "${pack.label} tools",
                    cachePrefix = "${pack.id}-tools",
                ).remove()
            }
            else -> return error("Unknown component '$what'. ${usage()}", "setup")
        }
        emit(event("done").apply {
            addProperty("removed", removed)
            addProperty("component", what)
        })
        return JsonObject().apply {
            addProperty("ok", removed)
            addProperty("event", "done")
            addProperty("removed", removed)
            addProperty("component", what)
            if (!removed) addProperty("error", "Could not remove $what.")
        }
    }

    private fun ensureBuildToolsAlias(platform: PlatformStore, requested: String) {
        if (requested.isBlank() || platform.buildToolsDir(requested) != null) return
        val source = platform.buildToolsDir() ?: return
        if (requested == source.name) return
        val target = File(platform.rootDir(), "build-tools/$requested")
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

    private fun selfTest(
        name: String,
        executable: File,
        arguments: List<String>,
        context: Context,
    ): JsonObject {
        val environment = ShellEnvironment.env(context, File(context.filesDir, "tmp"))
        val output = runCatching {
            val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(20, TimeUnit.SECONDS)) process.destroyForcibly()
            text.trim().lines().firstOrNull().orEmpty()
        }.getOrElse { it.message ?: it.javaClass.simpleName }
        val ok = executable.isFile && output.isNotBlank() &&
            !output.contains("not found", ignoreCase = true) &&
            !output.contains("CANNOT LINK", ignoreCase = true)
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("ok", ok)
            addProperty("path", executable.absolutePath)
            addProperty("version", output.take(300))
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
