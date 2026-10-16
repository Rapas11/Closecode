package com.closecode.build

import java.io.File

/** Where something sits: a project-relative file and a 1-based line. */
data class Place(val file: String, val line: Int)

data class DeclaredPlugin(val id: String, val version: String?, val place: Place)

data class DeclaredLibrary(val module: String, val version: String?, val place: Place)

/** Something the build files declare that the on-device build does not understand yet. */
data class UnsupportedBit(val what: String, val place: Place)

/**
 * What a project's build files declare. Read, never executed: no Gradle runs on this
 * phone, so the build files are data. The version catalog is resolved (libs.androidx.core
 * becomes a module and a version), and every value carries where it was found.
 */
data class BuildFacts(
    val packageName: String? = null,
    val packagePlace: Place? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val minSdk: Int? = null,
    val minSdkPlace: Place? = null,
    val targetSdk: Int? = null,
    val targetSdkPlace: Place? = null,
    val compileSdk: Int? = null,
    val compileSdkPlace: Place? = null,
    val kotlinVersion: String? = null,
    val kotlinPlace: Place? = null,
    val composeCompiler: String? = null,
    val composeCompilerPlace: Place? = null,
    val composeBom: String? = null,
    val composeBomPlace: Place? = null,
    val plugins: List<DeclaredPlugin> = emptyList(),
    val libraries: List<DeclaredLibrary> = emptyList(),
    val unsupported: List<UnsupportedBit> = emptyList(),
    /** True when app/build.gradle.kts exists: an Android project was readable at all. */
    val hasAppBuildFile: Boolean = false,
    /** True when only a Groovy app/build.gradle exists. */
    val groovyOnly: Boolean = false,
)

/**
 * Reads app/build.gradle.kts and the version catalog. This is a reader, not an
 * evaluator: it understands the shapes a build file takes in practice — literals,
 * the version catalog, platform(BOM) — and everything else is reported as
 * [UnsupportedBit] with its file and line rather than guessed at.
 */
object GradleReader {

    fun read(projectDir: File): BuildFacts {
        val appKts = File(projectDir, "app/build.gradle.kts")
        val appGroovy = File(projectDir, "app/build.gradle")
        return factsFrom(
            rootText = textOf(File(projectDir, "build.gradle.kts")),
            appText = textOf(appKts),
            settingsText = textOf(File(projectDir, "settings.gradle.kts")),
            catalogText = textOf(File(projectDir, "gradle/libs.versions.toml")),
            hasAppFile = appKts.isFile,
            hasGroovyApp = appGroovy.isFile,
        )
    }

    internal fun factsFrom(
        rootText: String?,
        appText: String?,
        settingsText: String?,
        catalogText: String?,
        hasAppFile: Boolean = appText != null,
        hasGroovyApp: Boolean = false,
    ): BuildFacts {
        val catalog = parseCatalog(catalogText)
        val root = rootText?.let { KtsScanner("build.gradle.kts", it, catalog, isRoot = true).also { s -> s.scan() } }
        val app = appText?.let { KtsScanner("app/build.gradle.kts", it, catalog, isRoot = false).also { s -> s.scan() } }
        val settings = settingsText?.let {
            SettingsScanner("settings.gradle.kts", it).also { s -> s.scan() }
        }

        val plugins = (root?.plugins.orEmpty() + app?.plugins.orEmpty())
            .distinctBy { it.id }
        val kotlinPlugin = plugins.firstOrNull { it.id == "org.jetbrains.kotlin.android" }
        val usesComposePlugin = plugins.any { it.id == "org.jetbrains.kotlin.plugin.compose" }

        val libraries = app?.libraries.orEmpty()
        val bom = libraries.firstOrNull { it.module == "androidx.compose:compose-bom" }

        val packageName = app?.packageName
        val composeCompiler = app?.composeCompiler
            ?: if (usesComposePlugin) kotlinPlugin?.version else null
        val composeCompilerPlace = app?.composeCompilerPlace
            ?: kotlinPlugin?.place?.takeIf { usesComposePlugin && composeCompiler != null }

        return BuildFacts(
            packageName = packageName,
            packagePlace = app?.packagePlace,
            versionCode = app?.versionCode,
            versionName = app?.versionName,
            minSdk = app?.minSdk,
            minSdkPlace = app?.minSdkPlace,
            targetSdk = app?.targetSdk,
            targetSdkPlace = app?.targetSdkPlace,
            compileSdk = app?.compileSdk,
            compileSdkPlace = app?.compileSdkPlace,
            kotlinVersion = kotlinPlugin?.version,
            kotlinPlace = kotlinPlugin?.place,
            composeCompiler = composeCompiler,
            composeCompilerPlace = composeCompilerPlace,
            composeBom = bom?.version,
            composeBomPlace = bom?.place,
            plugins = plugins,
            libraries = libraries,
            unsupported = root?.unsupported.orEmpty() + app?.unsupported.orEmpty() +
                settings?.unsupported.orEmpty(),
            hasAppBuildFile = hasAppFile,
            groovyOnly = !hasAppFile && hasGroovyApp,
        )
    }

    private fun textOf(file: File): String? =
        if (file.isFile) runCatching { file.readText() }.getOrNull() else null

    // --- the version catalog -----------------------------------------------------

    internal class VersionCatalog(
        val versions: Map<String, String> = emptyMap(),
        val libraries: Map<String, Pair<String, String?>> = emptyMap(),
        val plugins: Map<String, Pair<String, String?>> = emptyMap(),
        val bundles: Set<String> = emptySet(),
    ) {
        fun library(accessor: String): Pair<String, String?>? =
            libraries[accessor.replace('.', '-')] ?: libraries[accessor]

        fun plugin(accessor: String): Pair<String, String?>? =
            plugins[accessor.replace('.', '-')] ?: plugins[accessor]

        fun bundle(accessor: String): Boolean =
            accessor.replace('.', '-') in bundles || accessor in bundles
    }

    internal fun parseCatalog(text: String?): VersionCatalog {
        if (text == null) return VersionCatalog()
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, Triple<String, String?, String?>>()
        val plugins = mutableMapOf<String, Triple<String, String?, String?>>()
        val bundles = mutableSetOf<String>()
        var table = ""
        for (raw in text.lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                table = line.trim('[', ']').trim()
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.take(eq).trim()
            val value = line.substring(eq + 1).trim()
            when (table) {
                "versions" -> literal(value)?.let { versions[key] = it }
                "libraries" -> libraryEntry(key, value)?.let { libraries[key] = it }
                "plugins" -> pluginEntry(key, value)?.let { plugins[key] = it }
                "bundles" -> bundles += key
            }
        }
        fun resolved(entry: Triple<String, String?, String?>): Pair<String, String?> =
            entry.first to (entry.second ?: entry.third?.let { versions[it] })
        return VersionCatalog(
            versions = versions,
            libraries = libraries.mapValues { resolved(it.value) },
            plugins = plugins.mapValues { resolved(it.value) },
            bundles = bundles,
        )
    }

    /** "g:a:v" or { module = "g:a", version = "1" } / group+name / version.ref. */
    private fun libraryEntry(key: String, value: String): Triple<String, String?, String?>? {
        if (value.startsWith("{")) {
            val content = value.trim().removeSurrounding("{", "}")
            val module = regexString(content, "module")
                ?: run {
                    val group = regexString(content, "group")
                    val name = regexString(content, "name")
                    if (group != null && name != null) "$group:$name" else null
                }
                ?: return null
            val ref = regexString(content, "version\\.ref")
                ?: Regex("version\\s*=\\s*\\{\\s*ref\\s*=\\s*\"([^\"]+)\"").find(content)
                    ?.groupValues?.get(1)
            val version = if (ref != null) null else regexString(content, "version")
            return Triple(module, version, ref)
        }
        val parts = value.trim('"').split(':')
        return when (parts.size) {
            3 -> Triple("${parts[0]}:${parts[1]}", parts[2], null)
            2 -> Triple("${parts[0]}:${parts[1]}", null, null)
            else -> null
        }
    }

    private fun pluginEntry(key: String, value: String): Triple<String, String?, String?>? {
        if (!value.startsWith("{")) {
            // plugin aliases may be a bare id
            return if (value.startsWith("\"")) Triple(value.trim('"'), null, null) else null
        }
        val content = value.trim().removeSurrounding("{", "}")
        val id = regexString(content, "id") ?: return null
        val ref = regexString(content, "version\\.ref")
            ?: Regex("version\\s*=\\s*\\{\\s*ref\\s*=\\s*\"([^\"]+)\"").find(content)
                ?.groupValues?.get(1)
        val version = if (ref != null) null else regexString(content, "version")
        return Triple(id, version, ref)
    }

    private fun regexString(text: String, key: String): String? =
        Regex("$key\\s*=\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)

    private fun literal(value: String): String? =
        if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            value.substring(1, value.length - 1)
        } else {
            null
        }

    // --- one .kts file -----------------------------------------------------------

    /** Blocks a build file may open at the top level, per file role. */
    private val rootTopLevel = setOf("plugins", "allprojects", "subprojects")
    private val appTopLevel = setOf("plugins", "android", "dependencies")

    /** Blocks the reader understands inside android { }. */
    private val androidBlocks = setOf(
        "defaultConfig", "buildTypes", "compileOptions", "composeOptions",
        "composeCompiler", "buildFeatures", "kotlinOptions", "lint", "packaging", "testOptions",
    )

    /**
     * Known blocks whose own children must be understood one by one: a block the reader
     * does not know inside these is reported. buildTypes is not in the set — its children
     * are build type names, which the build treats as one debug variant.
     */
    private val nestedKnownBlocks = setOf(
        "defaultConfig", "compileOptions", "composeOptions", "buildFeatures",
        "kotlinOptions", "lint", "packaging",
    )

    /** Keys that name a tool or feature the build does not run yet, wherever they appear. */
    private val flaggedKeys = setOf(
        "productFlavors", "flavorDimensions", "signingConfigs", "dynamicFeatures",
        "externalNativeBuild", "ndkVersion", "buildConfigField", "resValue",
        "manifestPlaceholders", "publishing", "mavenPublishing", "includeBuild",
    )

    private class KtsScanner(
        val file: String,
        text: String,
        private val catalog: VersionCatalog,
        private val isRoot: Boolean,
    ) {
        private val lines = text.lines()
        private val frames = ArrayDeque<Pair<String, Int>>()
        private var depth = 0

        val unsupported = mutableListOf<UnsupportedBit>()
        val plugins = mutableListOf<DeclaredPlugin>()
        val libraries = mutableListOf<DeclaredLibrary>()
        var packageName: String? = null
        var packagePlace: Place? = null
        var versionCode: Int? = null
        var versionName: String? = null
        var minSdk: Int? = null
        var minSdkPlace: Place? = null
        var targetSdk: Int? = null
        var targetSdkPlace: Place? = null
        var compileSdk: Int? = null
        var compileSdkPlace: Place? = null
        var composeCompiler: String? = null
        var composeCompilerPlace: Place? = null

        fun scan() {
            for ((index, raw) in lines.withIndex()) {
                val line = index + 1
                val code = clean(raw)
                val depthStart = depth
                while (frames.isNotEmpty() && depthStart <= frames.last().second) {
                    frames.removeLast()
                }
                val parents = frames.map { it.first }
                val inPlugins = "plugins" in parents
                val inDependencies = "dependencies" in parents
                val inAndroid = "android" in parents

                val opener = Regex("^\\s*([A-Za-z_][\\w.]*)\\s*(\\([^)]*\\))?\\s*\\{")
                    .find(code)
                val isTopLevel = depthStart == 0

                if (isTopLevel && opener != null) {
                    val known = if (isRoot) rootTopLevel else appTopLevel
                    if (opener.groupValues[1] !in known) {
                        unsupported += UnsupportedBit("The ${opener.groupValues[1]} block", Place(file, line))
                    }
                } else if (opener != null) {
                    val parent = parents.lastOrNull()
                    val unknown = parent == "android" && opener.groupValues[1] !in androidBlocks ||
                        parent in nestedKnownBlocks
                    if (unknown) {
                        unsupported += UnsupportedBit(
                            "The ${opener.groupValues[1]} block",
                            Place(file, line),
                        )
                    }
                }

                if (inPlugins || opener?.groupValues?.get(1) == "plugins") scanPlugin(code, line)
                if (inDependencies) scanDependency(code, line)

                val flagged = flaggedKeys.firstOrNull { key ->
                    Regex("(^|\\W)$key(\\s*[(=])").containsMatchIn(code)
                }
                if (flagged != null && opener == null) {
                    unsupported += UnsupportedBit(flagged, Place(file, line))
                }

                scanValue(code, line)

                val net = code.count { it == '{' } - code.count { it == '}' }
                depth += net
                if (net > 0 && opener != null) {
                    frames.addLast(opener.groupValues[1] to depthStart)
                }
            }
        }

        private fun scanPlugin(code: String, line: Int) {
            val place = Place(file, line)
            val idMatch = Regex("id\\s*\\(\\s*\"([^\"]+)\"\\s*\\)").find(code)
            val aliasMatch = Regex("alias\\s*\\(\\s*libs\\.plugins\\.([\\w.]+)\\s*\\)").find(code)
            val kotlinMatch = Regex("kotlin\\s*\\(\\s*\"([^\"]+)\"\\s*\\)").find(code)
            val version = Regex("version\\s+\"([^\"]+)\"").find(code)?.groupValues?.get(1)
                ?: Regex("version\\s*\\(\\s*\"([^\"]+)\"\\s*\\)").find(code)?.groupValues?.get(1)
            when {
                idMatch != null -> plugins += DeclaredPlugin(idMatch.groupValues[1], version, place)
                kotlinMatch != null -> {
                    val short = kotlinMatch.groupValues[1]
                    plugins += DeclaredPlugin("org.jetbrains.kotlin.$short", version, place)
                }
                aliasMatch != null -> {
                    val accessor = aliasMatch.groupValues[1]
                    val resolved = catalog.plugin(accessor)
                    if (resolved != null) {
                        plugins += DeclaredPlugin(resolved.first, resolved.second, place)
                    } else {
                        unsupported += UnsupportedBit(
                            "The plugin alias libs.plugins.$accessor",
                            place,
                        )
                    }
                }
            }
        }

        private fun scanDependency(code: String, line: Int) {
            val place = Place(file, line)
            val match = Regex("^\\s*([A-Za-z_][\\w]*)\\s*\\((.*)\\)\\s*$").find(code) ?: return
            val config = match.groupValues[1]
            var inner = match.groupValues[2].trim()
            if (config == "kapt" || config == "ksp" || config == "annotationProcessor") {
                unsupported += UnsupportedBit(config, place)
                return
            }
            if (config == "coreLibraryDesugaring") {
                unsupported += UnsupportedBit("coreLibraryDesugaring", place)
                return
            }
            if (inner.startsWith("project(")) {
                unsupported += UnsupportedBit("The project dependency $inner", place)
                return
            }
            val enforced = inner.startsWith("enforcedPlatform(")
            if (enforced) {
                unsupported += UnsupportedBit("enforcedPlatform", place)
                return
            }
            if (inner.startsWith("platform(")) inner = inner.removePrefix("platform(").dropLast(1).trim()
            when {
                inner.startsWith("\"") -> {
                    val coordinate = inner.trim('"')
                    // "g:a:v" has three parts; "g:a" is a module whose version comes from
                    // the BOM or the catalog, never a module of "g:a:v" misread as "g:a".
                    val parts = coordinate.split(':')
                    val module = if (parts.size >= 2) parts[0] + ":" + parts[1] else coordinate
                    val version = if (parts.size >= 3) parts[2] else null
                    libraries += DeclaredLibrary(module, version, place)
                }
                inner.startsWith("libs.") -> {
                    val accessor = inner.removePrefix("libs.")
                    if (accessor.startsWith("bundles.")) {
                        unsupported += UnsupportedBit("The bundle libs.$accessor", place)
                        return
                    }
                    val resolved = catalog.library(accessor)
                    if (resolved != null) {
                        libraries += DeclaredLibrary(resolved.first, resolved.second, place)
                    } else {
                        unsupported += UnsupportedBit("The library libs.$accessor (not in the version catalog)", place)
                    }
                }
                else -> unsupported += UnsupportedBit("The dependency $inner", place)
            }
        }

        private fun scanValue(code: String, line: Int) {
            val place = Place(file, line)
            Regex("^\\s*namespace\\s*=\\s*\"([^\"]+)\"").find(code)?.let {
                packageName = it.groupValues[1]
                packagePlace = place
            }
            Regex("^\\s*applicationId\\s*=\\s*\"([^\"]+)\"").find(code)?.let {
                if (packageName == null) {
                    packageName = it.groupValues[1]
                    packagePlace = place
                }
            }
            intValue(code, "versionCode")?.let { versionCode = it }
            Regex("^\\s*versionName\\s*=\\s*\"([^\"]*)\"").find(code)?.let {
                versionName = it.groupValues[1]
            }
            intValue(code, "minSdk")?.let { minSdk = it; minSdkPlace = place }
            intValue(code, "targetSdk")?.let { targetSdk = it; targetSdkPlace = place }
            intValue(code, "compileSdk")?.let { compileSdk = it; compileSdkPlace = place }
            Regex("^\\s*kotlinCompilerExtensionVersion\\s*=\\s*\"([^\"]+)\"").find(code)?.let {
                composeCompiler = it.groupValues[1]
                composeCompilerPlace = place
            }
        }

        /** key = 35, keyVersion = 35, keyVersion(35), key(35). */
        private fun intValue(code: String, key: String): Int? {
            Regex("^\\s*${key}(Version)?\\s*=\\s*(\\d+)").find(code)?.let {
                return it.groupValues[2].toIntOrNull()
            }
            Regex("^\\s*${key}(Version)?\\s*\\(\\s*(\\d+)\\s*\\)").find(code)?.let {
                return it.groupValues[2].toIntOrNull()
            }
            return null
        }

        /**
         * Line with comments and string literals blanked, so brace counting and block
         * names never trip over a `${...}` inside a string.
         */
        private fun clean(raw: String): String {
            val noComment = raw.substringBefore("//")
            var inString = false
            val out = StringBuilder(noComment.length)
            var i = 0
            while (i < noComment.length) {
                val ch = noComment[i]
                when {
                    ch == '\\' && inString -> {
                        out.append(' ')
                        i++
                        if (i < noComment.length) {
                            out.append(' ')
                            i++
                        }
                        continue
                    }
                    ch == '"' -> {
                        // The quote stays: the scanners below read quoted values, and only the
                        // braces inside a string need blanking for the block counting.
                        inString = !inString
                        out.append(ch)
                    }
                    inString -> out.append(if (ch == '{' || ch == '}') ' ' else ch)
                    else -> out.append(ch)
                }
                i++
            }
            return out.toString()
        }
    }

    // --- settings.gradle.kts -----------------------------------------------------

    private class SettingsScanner(val file: String, text: String) {
        private val lines = text.lines()
        val unsupported = mutableListOf<UnsupportedBit>()
        private val knownBlocks = setOf("pluginManagement", "dependencyResolutionManagement")
        private val knownCalls = setOf("rootProject", "include")

        fun scan() {
            var depth = 0
            for ((index, raw) in lines.withIndex()) {
                val line = index + 1
                val code = raw.substringBefore("//")
                if (depth == 0) {
                    val opener = Regex("^\\s*([A-Za-z_][\\w.]*)\\s*\\{").find(code)
                    val call = Regex("^\\s*([A-Za-z_][\\w.]*)\\s*[\\.(]").find(code)
                    when {
                        opener != null && opener.groupValues[1] !in knownBlocks -> {
                            unsupported += UnsupportedBit(
                                "The ${opener.groupValues[1]} block",
                                Place(file, line),
                            )
                        }
                        opener == null && call != null &&
                            call.groupValues[1] !in knownCalls -> {
                            unsupported += UnsupportedBit(
                                "The ${call.groupValues[1]} call",
                                Place(file, line),
                            )
                        }
                    }
                }
                depth += code.count { it == '{' } - code.count { it == '}' }
            }
        }
    }
}
