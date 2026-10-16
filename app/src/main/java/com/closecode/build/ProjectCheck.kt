package com.closecode.build

import java.io.File

/**
 * The project check (the contract): what a project must be for the on-device build
 * to build it. Runs first on every build and when a project is opened. Every result
 * is one plain sentence, an error or a warning, with the file and line when there is one.
 *
 * An error stops the build; a warning never does — a foreign-looking package risks an
 * install clash, which the reader is told about, not stopped by.
 */
object ProjectCheck {

    enum class Severity { Error, Warning, Note }

    data class Finding(
        val severity: Severity,
        val message: String,
        val file: String? = null,
        val line: Int? = null,
    ) {
        /** "app/build.gradle.kts:12", or "" when the finding has no place. */
        val where: String
            get() = when {
                file == null -> ""
                line == null -> file
                else -> "$file:$line"
            }
    }

    data class Report(val findings: List<Finding>) {
        val errors: List<Finding> get() = findings.filter { it.severity == Severity.Error }
        val warnings: List<Finding> get() = findings.filter { it.severity == Severity.Warning }
        val notes: List<Finding> get() = findings.filter { it.severity == Severity.Note }
        val ok: Boolean get() = errors.isEmpty()
    }

    /**
     * What the check knows besides the project's own files: the package and SDK levels
     * saved in .closecode.json for a CloseCode-created project, the packages other
     * projects use, the free space, and what the library cache already holds.
     */
    data class Input(
        val savedPackage: String? = null,
        val savedMinSdk: Int? = null,
        val savedTargetSdk: Int? = null,
        val savedCompileSdk: Int? = null,
        val otherPackages: Set<String> = emptySet(),
        val freeBytes: Long? = null,
        /** "module:version" entries the library cache holds (verse 5 fills this in). */
        val cacheModules: Set<String> = emptySet(),
    )

    const val INVALID_PACKAGE_MESSAGE =
        "The package name is not valid. Use letters, digits and underscores, with at least " +
            "two parts, for example com.example.app."

    private const val PACKAGE_PREFIX = "com.closecode."

    /**
     * Java keywords may not be a part of a package name. Kept as the language reserves
     * them — `com.int.app` is legal to the eye and not to the Java compiler.
     */
    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
        "null",
    )

    fun isValidPackage(name: String): Boolean {
        val parts = name.split('.')
        if (parts.size < 2) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.first().isLetter() &&
                part.all { it.isLetterOrDigit() || it == '_' } &&
                part.lowercase() !in javaKeywords
        }
    }

    /** The package name an app gets when the user creates it with [name]. */
    fun packageForSlug(slug: String): String =
        PACKAGE_PREFIX + slug.lowercase().map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("").trim('_').ifEmpty { "app" }

    /** What is wrong with [name], or null when nothing is. */
    fun packageProblem(name: String, file: String? = null, line: Int? = null): Finding? =
        if (isValidPackage(name)) null else Finding(Severity.Error, INVALID_PACKAGE_MESSAGE, file, line)

    fun check(projectDir: File, slug: String, input: Input = Input()): Report {
        val findings = mutableListOf<Finding>()
        val facts = GradleReader.read(projectDir)

        // 1. The manifest: valid XML, and one screen Android can open.
        val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
        val manifestText = if (manifestFile.isFile) {
            runCatching { manifestFile.readText() }.getOrNull().orEmpty()
        } else {
            findings += Finding(
                Severity.Error,
                "Missing. Recreate the project from a template.",
                "app/src/main/AndroidManifest.xml",
            )
            ""
        }
        if (manifestFile.isFile) {
            val scan = ManifestScan.scan(manifestText)
            if (!scan.wellFormed) {
                findings += Finding(
                    Severity.Error,
                    "The manifest is not valid XML.",
                    "app/src/main/AndroidManifest.xml",
                )
            } else if (scan.launchableActivities == 0) {
                findings += Finding(
                    Severity.Error,
                    "The app has no screen to open.",
                    "app/src/main/AndroidManifest.xml",
                )
            }
        }

        // 2. The build files, read as data — never executed.
        if (!facts.hasAppBuildFile) {
            findings += if (facts.groovyOnly) {
                Finding(Severity.Error, "Groovy build files are not supported yet.", "app/build.gradle")
            } else {
                Finding(
                    Severity.Error,
                    "Missing. Recreate the project from a template.",
                    "app/build.gradle.kts",
                )
            }
        }

        // 3. The package: saved at creation for a template project, read from the build
        //    files for an imported one. A legal package that looks foreign only warns.
        //    The manifest's legacy package attribute is only a fallback when there is no
        //    build file to ask — a build file that names no package is the error.
        val saved = input.savedPackage?.takeIf { it.isNotBlank() }
        val packageName = saved ?: facts.packageName
            ?: if (!facts.hasAppBuildFile) TemplateContract.manifestPackage(manifestText) else null
        val packageFile = if (saved != null) ".closecode.json" else facts.packagePlace?.file
        val packageLine = if (saved != null) null else facts.packagePlace?.line
        when {
            packageName == null -> if (facts.hasAppBuildFile) {
                findings += Finding(
                    Severity.Error,
                    "No package is set. Add namespace = \"com.example.app\" to app/build.gradle.kts.",
                    "app/build.gradle.kts",
                )
            }
            !isValidPackage(packageName) -> findings += Finding(
                Severity.Error,
                INVALID_PACKAGE_MESSAGE,
                packageFile,
                packageLine,
            )
            !packageName.startsWith(PACKAGE_PREFIX) -> findings += Finding(
                Severity.Warning,
                "This app will be installed as $packageName.",
                packageFile,
                packageLine,
            )
        }
        if (packageName != null && isValidPackage(packageName) &&
            packageName in input.otherPackages
        ) {
            findings += Finding(
                Severity.Warning,
                "Another project uses this package. Installing this app would replace that one.",
                packageFile,
                packageLine,
            )
        }

        // 4. The Android versions.
        val savedSdks = input.savedMinSdk != null || input.savedTargetSdk != null ||
            input.savedCompileSdk != null
        fun sdkPlace(fallback: Place?): Pair<String?, Int?> =
            if (savedSdks) ".closecode.json" to null else fallback?.file to fallback?.line
        val minSdk = input.savedMinSdk ?: facts.minSdk
        val targetSdk = input.savedTargetSdk ?: facts.targetSdk
        val compileSdk = input.savedCompileSdk ?: facts.compileSdk
        if (minSdk != null && (minSdk < ProjectPins.MIN_SDK_FLOOR || (targetSdk != null && minSdk > targetSdk))) {
            val (file, line) = sdkPlace(facts.minSdkPlace)
            findings += Finding(
                Severity.Error,
                "The minimum Android version is not valid.",
                file,
                line,
            )
        }
        if (targetSdk != null && targetSdk < ProjectPins.MIN_SDK_FLOOR) {
            val (file, line) = sdkPlace(facts.targetSdkPlace)
            findings += Finding(
                Severity.Error,
                "The target Android version is too old to install on new phones.",
                file,
                line,
            )
        }
        if (compileSdk != null && compileSdk != ProjectPins.COMPILE_SDK) {
            val (file, line) = sdkPlace(facts.compileSdkPlace)
            findings += Finding(
                Severity.Error,
                "This phone has build tools for Android ${ProjectPins.COMPILE_SDK} only.",
                file,
                line,
            )
        }

        // 5. The toolchain versions the build supports.
        val bomDeclared = facts.composeBom != null
        val usesCompose = facts.composeBom != null || facts.composeCompiler != null ||
            facts.libraries.any { it.module.startsWith("androidx.compose") }
        if (facts.hasAppBuildFile) {
            if (facts.kotlinVersion == null) {
                findings += Finding(
                    Severity.Error,
                    "The Kotlin version could not be found in the build files.",
                    facts.kotlinPlace?.file ?: "build.gradle.kts",
                    facts.kotlinPlace?.line,
                )
            } else if (facts.kotlinVersion != ProjectPins.KOTLIN) {
                findings += Finding(
                    Severity.Error,
                    "Kotlin ${facts.kotlinVersion} is not supported yet. This phone builds " +
                        "with Kotlin ${ProjectPins.KOTLIN}.",
                    facts.kotlinPlace?.file,
                    facts.kotlinPlace?.line,
                )
            }
            if (usesCompose && facts.composeCompiler == null) {
                findings += Finding(
                    Severity.Error,
                    "The Compose compiler version is not set. Add composeOptions { " +
                        "kotlinCompilerExtensionVersion = \"${ProjectPins.COMPOSE_COMPILER}\" } " +
                        "to app/build.gradle.kts.",
                    "app/build.gradle.kts",
                )
            } else if (facts.composeCompiler != null && facts.composeCompiler != ProjectPins.COMPOSE_COMPILER) {
                findings += Finding(
                    Severity.Error,
                    "The Compose compiler ${facts.composeCompiler} is not supported yet. " +
                        "This phone builds with Compose compiler ${ProjectPins.COMPOSE_COMPILER}.",
                    facts.composeCompilerPlace?.file,
                    facts.composeCompilerPlace?.line,
                )
            }
            if (facts.composeBom != null && facts.composeBom != ProjectPins.COMPOSE_BOM) {
                findings += Finding(
                    Severity.Error,
                    "Compose BOM ${facts.composeBom} is not supported yet. This phone builds " +
                        "with Compose BOM ${ProjectPins.COMPOSE_BOM}.",
                    facts.composeBomPlace?.file,
                    facts.composeBomPlace?.line,
                )
            }
        }

        // 6. Plugins.
        for (plugin in facts.plugins) {
            if (plugin.id !in ProjectPins.supportedPlugins) {
                findings += Finding(
                    Severity.Error,
                    "Plugin ${plugin.id} is not supported yet.",
                    plugin.place.file,
                    plugin.place.line,
                )
            }
        }

        // 7. Libraries: pinned, or already in the library cache. The Compose BOM itself is
        //    covered by the version rule above, so a wrong BOM is reported once.
        for (library in facts.libraries) {
            if (library.module == "androidx.compose:compose-bom") continue
            if (!ProjectPins.librarySupported(
                    library.module,
                    library.version,
                    bomDeclared,
                    input.cacheModules,
                )
            ) {
                findings += Finding(
                    Severity.Error,
                    "Library ${library.module} is not supported yet.",
                    library.place.file,
                    library.place.line,
                )
            }
        }

        // 8. Whatever else the build files declare, named with its file and line.
        for (bit in facts.unsupported) {
            findings += Finding(
                Severity.Error,
                "${bit.what} is not supported yet.",
                bit.place.file,
                bit.place.line,
            )
        }

        // 9. Sources.
        val sourceRoot = File(projectDir, "app/src/main")
        val hasSources = sourceRoot.walkTopDown()
            .any { it.isFile && (it.extension == "kt" || it.extension == "java") }
        if (!hasSources) {
            findings += Finding(Severity.Error, "No sources. Add at least one .kt file.", "app/src/main/java")
        }

        // 10. Free storage: a build needs room for classes, dex and the APK.
        val free = input.freeBytes
        if (free != null && free < ProjectPins.FREE_SPACE_FLOOR_BYTES) {
            findings += Finding(Severity.Error, "Not enough free storage to build.")
        }

        return Report(findings)
    }
}

/**
 * A small XML scanner for AndroidManifest.xml: enough to tell a manifest that parses
 * from one that cannot, and to count the activities Android can launch. It is not a
 * parser for everything XML allows — attributes and elements are what manifests use.
 */
internal object ManifestScan {

    data class Result(val wellFormed: Boolean, val launchableActivities: Int)

    private val tag = Regex("<\\s*(/?)\\s*([A-Za-z_][\\w:.-]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*?)(/?)>")
    private val attribute = Regex("([A-Za-z_][\\w:.-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')")

    fun scan(text: String): Result {
        val body = text
            .replace(Regex("<!--[\\s\\S]*?-->"), " ")
            .replace(Regex("<\\?[\\s\\S]*?\\?>"), " ")
            .replace(Regex("<!\\[CDATA\\[[\\s\\S]*?]]>"), " ")

        var wellFormed = true
        val stack = ArrayDeque<String>()
        // intent-filter elements: whether an activity encloses them, and their content.
        val filters = mutableListOf<Pair<Boolean, String>>()
        var openFilter: Pair<Int, Boolean>? = null // content start, inside an activity

        var index = 0
        for (match in tag.findAll(body)) {
            if (body.substring(index, match.range.first).trim().isNotEmpty()) {
                wellFormed = false
                break
            }
            index = match.range.last + 1
            val closing = match.groupValues[1].isNotEmpty()
            val name = match.groupValues[2]
            val attrs = match.groupValues[3]
            val selfClosing = match.groupValues[4].isNotEmpty()

            if (!attributesOk(attrs)) {
                wellFormed = false
                break
            }
            if (closing) {
                if (stack.isEmpty() || stack.removeLast() != name) {
                    wellFormed = false
                    break
                }
                if (name == "intent-filter" && openFilter != null) {
                    val content = body.substring(openFilter.first, match.range.first)
                    filters += openFilter.second to content
                    openFilter = null
                }
            } else if (!selfClosing) {
                val insideActivity = stack.contains("activity") || stack.contains("activity-alias")
                if (name == "intent-filter") {
                    openFilter = (match.range.last + 1) to insideActivity
                }
                stack.addLast(name)
            }
        }
        if (index < body.length && body.substring(index).trim().isNotEmpty()) wellFormed = false
        if (stack.isNotEmpty()) wellFormed = false

        val launchable = filters.count { (insideActivity, content) ->
            insideActivity &&
                "android.intent.action.MAIN" in content &&
                "android.intent.category.LAUNCHER" in content
        }
        return Result(wellFormed, launchable)
    }

    private fun attributesOk(attrs: String): Boolean {
        if (attrs.isBlank()) return true
        var rest = attrs
        for (match in attribute.findAll(attrs)) {
            rest = rest.replaceFirst(match.value, " ")
        }
        return rest.isBlank()
    }
}
