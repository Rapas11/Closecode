package com.closecode.build

import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * A cache of complete, signed APKs.
 *
 * Only the final APK is cached. A hit is valid only when every input that can change the bytes
 * is unchanged: the project files, the build configuration, the tool files, the platform jar,
 * aapt2 and the signing certificate. The key is a SHA-256 of those facts, so a stale APK cannot
 * be served by a timestamp accident. Any problem while computing or copying falls back to the
 * normal build — a cache is an optimisation, never a reason a build cannot run.
 */
class BuildCache(private val root: File) {

    fun key(projectDir: File, config: BuildConfigValues, tools: BuildTools): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun text(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        text(config.toString())
        text(tools.androidJar.absolutePath)
        text(tools.androidJar.length().toString())
        text(tools.aapt2.absolutePath)
        text(tools.aapt2.length().toString())
        tools.toolFiles.toSortedMap().forEach { (id, file) ->
            text(id)
            text(file.absolutePath)
            text(file.length().toString())
            text(file.lastModified().toString())
        }
        tools.signer.chain.firstOrNull()?.let { certificate ->
            text(certificateFingerprint(certificate))
        }
        listOfNotNull(tools.stdlib, tools.composePlugin, tools.kotlinResources).forEach { file ->
            text(file.absolutePath)
            text(file.length().toString())
            text(file.lastModified().toString())
        }
        tools.libraries.libraries.sortedBy { it.coordinate }.forEach { library ->
            text(library.coordinate)
            listOfNotNull(library.jar, library.res, library.manifest).forEach { file ->
                text(file.absolutePath)
                text(file.length().toString())
                text(file.lastModified().toString())
            }
        }
        tools.libraries.dexFiles.sortedBy { it.name }.forEach { file ->
            text(file.absolutePath)
            text(file.length().toString())
            text(file.lastModified().toString())
        }
        projectDir.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.isFile }
            .sortedBy { it.relativeTo(projectDir).path }
            .forEach { file ->
                text(file.relativeTo(projectDir).path)
                digest.update(file.readBytes())
                digest.update(0)
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun lookup(slug: String, key: String): File? =
        File(File(root, safe(slug)), "$key.apk").takeIf { it.isFile }

    fun store(slug: String, key: String, apk: File) {
        val dir = File(root, safe(slug))
        dir.mkdirs()
        val target = File(dir, "$key.apk")
        val part = File(dir, "$key.apk.part")
        runCatching {
            apk.copyTo(part, overwrite = true)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
        }.onFailure { part.delete() }
    }

    fun clear(slug: String? = null) {
        if (slug == null) {
            root.deleteRecursively()
        } else {
            File(root, safe(slug)).deleteRecursively()
        }
    }

    private fun safe(slug: String): String =
        slug.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")

    private fun certificateFingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
}
