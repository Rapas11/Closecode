package com.closecode.control

import com.closecode.build.BuildSetup
import com.closecode.build.GradleReader
import com.closecode.build.NativeProject
import com.closecode.build.ProjectCheck
import com.closecode.build.TemplateContract
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/**
 * The facts `closecode describe` reports. Reading a project is deliberately separate from
 * building it: an agent can ask what the project is before the toolchain is installed, and
 * the answer never depends on a build having run.
 */
internal object ProjectDescription {

    data class Facts(
        val packageName: String?,
        val versionCode: Int?,
        val versionName: String?,
        val minSdk: Int?,
        val targetSdk: Int?,
        val compileSdk: Int?,
        val kotlinVersion: String?,
        val compose: Boolean,
        val kotlinSources: Int,
        val javaSources: Int,
        val resourceFiles: Int,
        val assetFiles: Int,
        val apk: File?,
        val buildJson: File?,
        val usesNative: Boolean = false,
        val nativeReason: String? = null,
        val requestedNdkVersion: String? = null,
    )

    fun read(project: BuildSetup.Project): Facts {
        val facts = GradleReader.read(project.directory)
        val native = NativeProject.detect(project.directory, facts)
        val manifest = runCatching {
            File(project.directory, "app/src/main/AndroidManifest.xml").readText()
        }.getOrDefault("")
        val sourceRoot = File(project.directory, "app/src/main/java")
        val kotlin = sourceRoot.walkTopDown().count { it.isFile && it.extension == "kt" }
        val java = sourceRoot.walkTopDown().count { it.isFile && it.extension == "java" }
        val resources = File(project.directory, "app/src/main/res")
            .walkTopDown().count { it.isFile }
        val assets = File(project.directory, "app/src/main/assets")
            .walkTopDown().count { it.isFile }
        val apk = listOf(
            "build/outputs/apk/debug/app-debug.apk",
            "build/outputs/apk/release/app-release.apk",
        ).map { File(project.directory, it) }.firstOrNull { it.isFile }
        val buildJson = File(project.directory, "build/build.json").takeIf { it.isFile }
        return Facts(
            packageName = project.savedPackage?.takeIf { it.isNotBlank() }
                ?: facts.packageName
                ?: TemplateContract.manifestPackage(manifest),
            versionCode = facts.versionCode,
            versionName = facts.versionName,
            minSdk = project.savedMinSdk ?: facts.minSdk,
            targetSdk = project.savedTargetSdk ?: facts.targetSdk,
            compileSdk = project.savedCompileSdk ?: facts.compileSdk,
            kotlinVersion = facts.kotlinVersion,
            compose = facts.composeBom != null || facts.composeCompiler != null ||
                facts.libraries.any { it.module.startsWith("androidx.compose") },
            kotlinSources = kotlin,
            javaSources = java,
            resourceFiles = resources,
            assetFiles = assets,
            apk = apk,
            buildJson = buildJson,
            usesNative = native.usesNative,
            nativeReason = native.reasons.firstOrNull()?.what,
            requestedNdkVersion = native.requestedNdkVersion,
        )
    }

    fun json(
        project: BuildSetup.Project,
        facts: Facts,
        setup: BuildSetup,
        toolchainVersion: String,
        toolchainReady: Boolean,
        toolchainMissing: List<String>,
        toolchainBytes: Long,
        gradleReady: Boolean = false,
        gradleMissing: List<String> = emptyList(),
        last: JsonObject? = null,
    ): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("id", project.id)
        addProperty("slug", project.slug)
        addProperty("name", project.name)
        addProperty("directory", project.directory.absolutePath)
        addProperty("package", facts.packageName)
        addProperty("versionCode", facts.versionCode)
        addProperty("versionName", facts.versionName)
        addProperty("minSdk", facts.minSdk)
        addProperty("targetSdk", facts.targetSdk)
        addProperty("compileSdk", facts.compileSdk)
        addProperty("kotlin", facts.kotlinVersion)
        addProperty("compose", facts.compose)
        add("sources", JsonObject().apply {
            addProperty("kotlin", facts.kotlinSources)
            addProperty("java", facts.javaSources)
            addProperty("resources", facts.resourceFiles)
            addProperty("assets", facts.assetFiles)
        })
        add("build", JsonObject().apply {
            addProperty("apk", facts.apk?.absolutePath)
            addProperty("apkBytes", facts.apk?.length() ?: 0L)
            addProperty("report", facts.buildJson?.absolutePath)
            addProperty("running", com.closecode.build.BuildCoordinator.active() != null)
        })
        add("toolchain", JsonObject().apply {
            addProperty("version", toolchainVersion)
            addProperty("ready", toolchainReady)
            addProperty("bytes", toolchainBytes)
            add("missing", JsonArray().apply { toolchainMissing.forEach { add(it) } })
        })
        add("gradle", JsonObject().apply {
            addProperty("ready", gradleReady)
            add("missing", JsonArray().apply { gradleMissing.forEach { add(it) } })
        })
        add("native", JsonObject().apply {
            addProperty("used", facts.usesNative)
            addProperty("reason", facts.nativeReason)
            addProperty("requestedNdk", facts.requestedNdkVersion)
            addProperty("ready", setup.native != null)
            addProperty("ndk", setup.native?.ndkVersion)
            addProperty("cmake", setup.native?.cmakeVersion)
            add("missing", JsonArray().apply { setup.nativeMissing.forEach { add(it) } })
        })
        val report = ProjectCheck.check(project.directory, project.slug, setup.checkInput)
        add("check", JsonObject().apply {
            addProperty("ok", report.ok)
            add("errors", JsonArray().apply {
                report.errors.forEach { finding ->
                    add(JsonObject().apply {
                        addProperty("message", finding.message)
                        if (finding.file != null) addProperty("file", finding.file)
                        if (finding.line != null) addProperty("line", finding.line)
                    })
                }
            })
            add("warnings", JsonArray().apply {
                report.warnings.forEach { finding ->
                    add(JsonObject().apply {
                        addProperty("message", finding.message)
                        if (finding.file != null) addProperty("file", finding.file)
                        if (finding.line != null) addProperty("line", finding.line)
                    })
                }
            })
        })
        if (last != null) add("lastBuild", last)
        add("commands", JsonObject().apply {
            addProperty("build", "closecode build [--release] [--gradle]")
            addProperty("run", "closecode run — build, install, launch, then wait for the app")
            addProperty("install", "closecode install")
            addProperty("launch", "closecode launch")
            addProperty("app", "closecode app — installed/foreground state and screen size")
            addProperty("stop", "closecode stop — press Home and stop the app")
            addProperty("restart", "closecode restart — stop, launch, wait for the app")
            addProperty("ui", "closecode ui dump|tap|wait|assert|scroll — inspect and drive the screen")
            addProperty("clean", "closecode clean — delete the project build folder")
            addProperty("logcat", "closecode logcat")
            addProperty("export", "closecode export")
            addProperty("screenshot", "closecode screenshot")
            addProperty("tool", "closecode tool aapt2|d8|r8|kotlinc")
            addProperty(
                "gradle",
                "closecode gradle assembleDebug|assembleRelease|bundleRelease|test|lint|" +
                    "check|tasks — without --gradle these use the phone's own builder or " +
                    "static checks; add --gradle for real Gradle, including flavours, " +
                    "R8 and App Bundles.",
            )
            addProperty("check", "closecode check — static project checks, no build")
            addProperty(
                "github",
                "closecode github push|pull — publish to GitHub or take what came back; " +
                    "uses the app's own signed-in token and is never exposed to the shell.",
            )
        })
    }
}
