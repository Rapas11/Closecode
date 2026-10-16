package com.closecode.build

/**
 * Package-name helpers shared by the scaffold, the project check and the IDE. The
 * contract itself — every rule a project must pass — lives in [ProjectCheck]; this
 * carries only the naming scheme CloseCode-created projects follow.
 */
object TemplateContract {
    const val PACKAGE_PREFIX = "com.closecode."

    fun packageForSlug(slug: String): String {
        val safe = slug.lowercase().map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("").trim('_').ifEmpty { "app" }
        return "$PACKAGE_PREFIX$safe"
    }

    /** The package a manifest declares, from `package=` or `namespace =`. */
    fun manifestPackage(manifestText: String): String? {
        val fromPackage = Regex("package=\"([^\"]+)\"").find(manifestText)?.groupValues?.get(1)
        if (fromPackage != null) return fromPackage
        return Regex("namespace\\s*=\\s*\"([^\"]+)\"").find(manifestText)?.groupValues?.get(1)
    }
}
