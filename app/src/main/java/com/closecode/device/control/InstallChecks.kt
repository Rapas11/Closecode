package com.closecode.device.control

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * The pure rules behind `closecode install` (P0-7, P1-7).
 *
 * Two answers the command can give before Android is involved: the installed app and the new
 * APK carry different certificates, so an in-place update is impossible; or the app is not
 * allowed to install anything yet, so the user has to grant it first.
 */
internal object InstallChecks {

    /** True when the two certificates lists share at least one certificate. */
    fun certificatesMatch(installed: List<String>, incoming: List<String>): Boolean {
        if (installed.isEmpty() || incoming.isEmpty()) return true
        return installed.any { it in incoming }
    }

    /** P0-7: a different key can never be updated in place; say so before Android does. */
    fun signatureMismatch(
        packageName: String,
        installed: List<String>,
        incoming: List<String>,
    ): JsonObject = JsonObject().apply {
        addProperty("ok", false)
        addProperty("code", "signature_mismatch")
        addProperty("needsUser", true)
        addProperty("package", packageName)
        addProperty(
            "error",
            "$packageName is installed with a different signing key, so Android will not " +
                "update it in place.",
        )
        addProperty(
            "remedy",
            "Uninstall the existing app (its data will be lost) or rebuild with the same " +
                "signing key.",
        )
        add("actions", JsonArray().apply {
            add("closecode apps uninstall $packageName")
            add("closecode signing select <key>")
        })
        addProperty("installedSha256", installed.firstOrNull().orEmpty())
        addProperty("apkSha256", incoming.firstOrNull().orEmpty())
    }

    /** P1-7: the permission is actionable — name it, link the screen, give the command. */
    fun permissionNeeded(packageName: String, openedSettings: Boolean): JsonObject =
        JsonObject().apply {
            addProperty("ok", false)
            addProperty("code", "needs_permission")
            addProperty("needsUser", true)
            addProperty("needsPermission", true)
            addProperty("permission", "REQUEST_INSTALL_PACKAGES")
            addProperty("url", "android.settings.MANAGE_UNKNOWN_APP_SOURCES")
            addProperty("package", packageName)
            addProperty(
                "error",
                "Allow CloseCode to install apps in Settings, then try again.",
            )
            addProperty(
                "remedy",
                "Allow CloseCode to install unknown apps, then run closecode install again.",
            )
            add("actions", JsonArray().apply {
                add("closecode install --open-settings")
                add("closecode install")
            })
            if (openedSettings) addProperty("openedSettings", true)
        }
}
