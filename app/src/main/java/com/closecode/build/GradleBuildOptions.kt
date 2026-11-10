package com.closecode.build

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Memory and parallelism for one Gradle run, sized to the phone rather than a PC.
 * A project can override any field in `.closecode.json`:
 *
 *     { "gradle": { "heapMb": 2048, "workers": 2, "parallel": false,
 *                   "fileWatching": false,
 *                   "proxyHost": "127.0.0.1", "proxyPort": 8080 } }
 */
data class GradleBuildOptions(
    val maxHeapMb: Int = 1536,
    val workers: Int = 2,
    val parallel: Boolean = false,
    val fileWatching: Boolean = false,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyUser: String? = null,
    val proxyPassword: String? = null,
) {
    fun jvmArgs(): String = buildString {
        append("-Xmx${maxHeapMb}m")
        append(" -Dfile.encoding=UTF-8")
        proxyHost?.takeIf { it.isNotBlank() }?.let { host ->
            append(" -Dhttp.proxyHost=$host -Dhttps.proxyHost=$host")
            proxyPort?.let { append(" -Dhttp.proxyPort=$it -Dhttps.proxyPort=$it") }
            proxyUser?.takeIf { it.isNotBlank() }?.let { user ->
                append(" -Dhttp.proxyUser=$user -Dhttps.proxyUser=$user")
                proxyPassword?.let { append(" -Dhttp.proxyPassword=$it -Dhttps.proxyPassword=$it") }
            }
        }
    }

    fun gradleProperties(): List<String> = listOf(
        "-Dorg.gradle.workers.max=$workers",
        "-Dorg.gradle.parallel=$parallel",
        "-Dorg.gradle.vfs.watch=$fileWatching",
        "-Dkotlin.daemon.jvmargs=-Xmx${maxHeapMb.coerceIn(1024, 3072)}m",
    )

    companion object {
        fun forProject(projectDir: File): GradleBuildOptions {
            val base = GradleBuildOptions(maxHeapMb = heapFor(deviceMemoryBytes()))
            val record = runCatching {
                val file = File(projectDir, ".closecode.json")
                if (!file.isFile) null else JsonParser.parseString(file.readText()).asJsonObject
            }.getOrNull() ?: return base
            val gradle = record.getAsJsonObject("gradle") ?: record
            return base.copy(
                maxHeapMb = gradle.int("heapMb")?.coerceIn(512, 3072) ?: base.maxHeapMb,
                workers = gradle.int("workers")?.coerceIn(1, 4) ?: base.workers,
                parallel = gradle.bool("parallel") ?: base.parallel,
                fileWatching = gradle.bool("fileWatching") ?: base.fileWatching,
                proxyHost = gradle.string("proxyHost"),
                proxyPort = gradle.int("proxyPort"),
                proxyUser = gradle.string("proxyUser"),
                proxyPassword = gradle.string("proxyPassword"),
            )
        }

        fun heapFor(totalBytes: Long?): Int = when {
            totalBytes == null || totalBytes <= 0L -> 1536
            totalBytes <= 3L * GIB -> 1024
            totalBytes <= 4L * GIB -> 1536
            totalBytes <= 6L * GIB -> 2048
            totalBytes <= 8L * GIB -> 2560
            else -> 3072
        }

        fun deviceMemoryBytes(): Long? = runCatching {
            File("/proc/meminfo").useLines { lines ->
                val line = lines.firstOrNull { it.startsWith("MemTotal:") } ?: return@useLines null
                val kb = line.filter { it.isDigit() }.toLongOrNull() ?: return@useLines null
                kb * 1024L
            }
        }.getOrNull()

        private fun JsonObject.int(name: String): Int? = runCatching {
            get(name)?.asInt
        }.getOrNull()

        private fun JsonObject.bool(name: String): Boolean? = runCatching {
            get(name)?.asBoolean
        }.getOrNull()

        private fun JsonObject.string(name: String): String? = runCatching {
            get(name)?.takeUnless { it.isJsonNull }?.asString
        }.getOrNull()

        private const val GIB = 1024L * 1024L * 1024L
    }
}
