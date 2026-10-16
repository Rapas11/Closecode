package com.closecode.debug

import com.closecode.BuildConfig

/**
 * The one line that names the APK that is running: the version name, the build number and
 * the short commit, filled in at build time. It sits at the bottom of the More sheet and at
 * the top of every log and report, so a phone that has an APK installed can say which one —
 * without adb, and without asking the person holding it.
 *
 * The pure [label] overload exists so the format is unit tested without Gradle variables.
 */
object BuildInfo {

    /** `CloseCode 1.0 (build 3) · a1b2c3d · debug` */
    fun label(
        versionName: String,
        versionCode: Int,
        commit: String,
        debug: Boolean,
    ): String = "CloseCode $versionName (build $versionCode) · $commit · " +
        (if (debug) "debug" else "release")

    /** The same line for the APK this build produced. */
    val label: String
        get() = label(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            commit = BuildConfig.GIT_COMMIT,
            debug = BuildConfig.DEBUG,
        )
}
