package com.closecode.control

import com.google.gson.JsonObject

/**
 * The pure rules behind `closecode doctor` and `closecode toolchain status`.
 *
 * Both answers say the same thing: only the checks the build actually needs decide `ok`.
 * The optional ones — shell pack, NDK, CMake, accessibility, notifications, battery, disk,
 * network — are reported, but they never turn a working install into a red one.
 */
internal object DiagnosticRules {

    /** True when a check is not marked optional. */
    fun isRequired(check: JsonObject): Boolean =
        check.get("optional") == null || check.get("optional")?.asBoolean != true

    /** The verdict over a list of checks: every required check must pass. */
    fun overallOk(checks: List<JsonObject>): Boolean =
        checks.filter { isRequired(it) }.all { it.get("ok")?.asBoolean == true }

    /** The names of the required checks that did not pass. */
    fun failedRequired(checks: List<JsonObject>): List<String> =
        checks.filter { isRequired(it) }
            .filter { it.get("ok")?.asBoolean != true }
            .mapNotNull { it.get("name")?.asString }
}
