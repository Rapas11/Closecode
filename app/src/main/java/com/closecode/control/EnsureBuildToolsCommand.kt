package com.closecode.control

import android.content.Context
import com.closecode.build.BuildSetup
import com.closecode.build.GradleReader
import com.closecode.build.PlatformStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.google.gson.JsonObject
import java.io.File

/**
 * `closecode ensure-build-tools`: the build-tools a project asks for, ready to build with.
 *
 * A project that pins a build-tools revision the app did not install makes AGP download
 * that revision itself — and Google's package holds x86-64 binaries, which cannot run on an
 * ARM phone. This command gives the project the version it asked for, as a copy of the
 * working ARM64 build-tools, before Gradle starts.
 *
 * It also clears the leftovers of an interrupted install. A `.installer` folder means
 * "not installed": a build that finds one decides the package is broken and fetches it
 * itself, which is how a build ends up downloading a package that cannot run here.
 */
object EnsureBuildToolsCommand {

    fun run(context: Context, projectDir: File?): JsonObject {
        val platform = PlatformStore(context, SharedPreferencesKeyValueStore(context))
        val removed = platform.removeIncompleteInstalls()
        val removedForeign = platform.removeUnusableBuildTools()

        if (projectDir == null) {
            return JsonObject().apply {
                addProperty("ok", true)
                addProperty("message", "The SDK was checked; no project was given.")
                addProperty("removedIncomplete", removed)
                addProperty("removedForeign", removedForeign)
            }
        }

        val facts = GradleReader.read(projectDir)
        val pinned = facts.buildToolsVersion
        val ensured = pinned?.let { platform.ensureBuildToolsVersion(it) }
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("pinned", pinned ?: "")
            if (pinned != null) {
                addProperty(
                    "ready",
                    ensured != null && platform.buildToolsUsable(ensured),
                )
                addProperty("path", ensured?.absolutePath ?: "")
                addProperty(
                    "message",
                    if (ensured != null) {
                        "build-tools $pinned is ready on this phone."
                    } else {
                        "build-tools $pinned could not be prepared; the build may fail."
                    },
                )
            } else {
                addProperty("ready", true)
                addProperty("message", "This project does not pin a build-tools version.")
            }
            addProperty("removedIncomplete", removed)
            addProperty("removedForeign", removedForeign)
        }
    }
}
