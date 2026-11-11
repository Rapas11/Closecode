package com.closecode.tools

import android.content.Context
import com.closecode.build.matchesSha256
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * P1-1: `python3 -m venv` must produce an environment with a working pip.
 *
 * Termux's Python ships an `ensurepip` bundle that does not match the interpreter: the
 * runtime's own `ensurepip/__init__.py` asks for `pip-<its version>-py3-none-any.whl`, and
 * the `_bundled` folder either has no wheel at all or has one left behind by an older
 * Python. `python -m venv` then fails inside ensurepip and the new environment has no pip.
 *
 * The installer owns the fix — never the user, never the agent: it reads the version the
 * runtime asks for, downloads exactly that wheel, verifies its size and SHA-256, and puts
 * it where ensurepip looks. Wheels left over from an older runtime are removed, so a
 * runtime upgrade repairs itself instead of failing.
 */
object PythonPackRepair {

    /** The wheel this build pins when the runtime asks for pip 25.2. */
    const val PIP_VERSION = "25.2"
    const val PIP_FILE = "pip-$PIP_VERSION-py3-none-any.whl"
    const val PIP_URL = "https://files.pythonhosted.org/packages/b7/3f/" +
        "945ef7ab14dc4f9d7f40288d2df998d1837ee0888ec3659c813487572faa/$PIP_FILE"
    const val PIP_SIZE = 1_752_557L
    const val PIP_SHA256 = "6d67a2b4e7f14d8b31b8b52648866fa717f45a1eb70e83002f4331d07e953717"

    /** Every pip wheel this build knows, so a runtime upgrade still installs offline. */
    private val KNOWN: Map<String, Triple<String, Long, String>> = mapOf(
        "25.2" to Triple(
            PIP_URL,
            PIP_SIZE,
            PIP_SHA256,
        ),
        "26.2.2" to Triple(
            "https://files.pythonhosted.org/packages/3f/06/" +
                "3e4420302363fbb71be3c7f35ce042f54bfca330e3e66b3a2e3c4cca579f/" +
                "pip-26.2.2-py3-none-any.whl",
            1_819_850L,
            "fd7f4dfa8190e014013b9f08f3e973f4e4c26ffd179a204895533a7254813a70",
        ),
    )

    /** The ensurepip bundle directory inside an unpacked prefix, or null without python. */
    fun bundledDir(prefix: File): File? {
        val lib = File(prefix, "lib")
        val python = lib.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("python3.") }
            ?.maxByOrNull { pythonRank(it.name) }
            ?: return null
        return File(python, "ensurepip/_bundled")
    }

    private fun pythonRank(name: String): Int {
        val parts = name.removePrefix("python").split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major * 1000 + minor
    }

    /**
     * The pip version this runtime's own ensurepip asks for.
     *
     * `ensurepip/__init__.py` carries `_PIP_VERSION`; that value decides the wheel file
     * name, so it is the only authority on which wheel belongs in the bundle.
     */
    fun requiredVersion(prefix: File): String? {
        val python = bundledDir(prefix)?.parentFile?.parentFile ?: return null
        val source = File(python, "ensurepip/__init__.py")
        if (!source.isFile) return null
        val text = runCatching { source.readText() }.getOrNull() ?: return null
        return Regex("_PIP_VERSION\\s*=\\s*[\"']([^\"']+)[\"']").find(text)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** The wheel file name the runtime asks for, or null when the runtime does not say. */
    fun requiredFile(prefix: File): String? =
        requiredVersion(prefix)?.let { "pip-$it-py3-none-any.whl" }

    /** True when the pack has python but ensurepip has no usable wheel to install. */
    fun needsRepair(prefix: File): Boolean {
        val bundled = bundledDir(prefix) ?: return false
        val required = requiredFile(prefix) ?: return false
        val target = File(bundled, required)
        if (!target.isFile) return true
        val expected = KNOWN[requiredVersion(prefix)] ?: return !looksLikeWheel(target)
        return target.length() != expected.second || !matchesSha256(target, expected.third)
    }

    /** True when the prefix has the wheel ensurepip will actually install. */
    fun isRepaired(prefix: File): Boolean {
        val bundled = bundledDir(prefix) ?: return false
        val required = requiredFile(prefix) ?: return false
        val target = File(bundled, required)
        if (!target.isFile) return false
        val expected = KNOWN[requiredVersion(prefix)] ?: return looksLikeWheel(target)
        return target.length() == expected.second && matchesSha256(target, expected.third)
    }

    /** A readable wheel, used when the exact version is not pinned in this build. */
    private fun looksLikeWheel(file: File): Boolean = runCatching {
        java.util.zip.ZipFile(file).use { zip ->
            zip.getEntry("pip/__init__.py") != null
        }
    }.getOrDefault(false)

    /**
     * Puts the wheel the runtime asks for in place. Returns the wheel, or null when the
     * prefix has no python at all. Fails loudly rather than leaving a half-written wheel.
     */
    fun repair(
        context: Context,
        prefix: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File?> = runCatching {
        val bundled = bundledDir(prefix) ?: return@runCatching null
        val version = requiredVersion(prefix)
            ?: throw IOException("This Python does not say which pip it needs.")
        val wheel = "pip-$version-py3-none-any.whl"
        val known = KNOWN[version]
        val fetched = if (known == null) metadata(version) else null
        val url = known?.first ?: fetched?.first
            ?: throw IOException("PyPI has no wheel for pip $version.")
        val expectedSize = known?.second ?: fetched?.second ?: 0L
        val expectedSha = known?.third ?: fetched?.third
        val target = File(bundled, wheel)
        if (target.isFile && expectedSize > 0L && target.length() == expectedSize &&
            expectedSha != null && matchesSha256(target, expectedSha)
        ) {
            removeStaleWheels(bundled, wheel)
            return@runCatching target
        }
        bundled.mkdirs()
        val part = File(context.cacheDir, "$wheel.part")
        download(url, part, expectedSize, onProgress)
        if (expectedSize > 0L && part.length() != expectedSize) {
            part.delete()
            throw IOException("The pip wheel for $version has the wrong size.")
        }
        if (expectedSha != null && !matchesSha256(part, expectedSha)) {
            part.delete()
            throw IOException("The pip wheel for $version failed its checksum.")
        }
        part.copyTo(target, overwrite = true)
        part.delete()
        removeStaleWheels(bundled, wheel)
        target
    }

    /** A wheel from an older runtime must not survive an upgrade. */
    private fun removeStaleWheels(bundled: File, keep: String) {
        bundled.listFiles()
            ?.filter { it.isFile && it.name.startsWith("pip-") && it.name.endsWith(".whl") }
            ?.filter { it.name != keep }
            ?.forEach { it.delete() }
    }

    /** The PyPI metadata for a pip version, used when the wheel is not pinned in this build. */
    private fun metadata(version: String): Triple<String, Long, String> {
        val connection = (URL("https://pypi.org/pypi/pip/$version/json").openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "CloseCode")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("PyPI answered HTTP ${connection.responseCode} for pip $version.")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val root = com.google.gson.JsonParser.parseString(text).asJsonObject
            val wheel = root.getAsJsonArray("urls")?.mapNotNull { element ->
                runCatching { element.asJsonObject }.getOrNull()
            }?.firstOrNull { item ->
                item.get("filename")?.asString == "pip-$version-py3-none-any.whl"
            } ?: throw IOException("PyPI has no wheel for pip $version.")
            val url = wheel.get("url")?.asString
                ?: throw IOException("PyPI gave no download URL for pip $version.")
            val size = wheel.get("size")?.asLong ?: 0L
            val sha = wheel.getAsJsonObject("digests")?.get("sha256")?.asString
                ?: throw IOException("PyPI gave no checksum for pip $version.")
            return Triple(url, size, sha)
        } finally {
            connection.disconnect()
        }
    }

    /** Resumable download: a `.part` file, a Range request, and a size check before use. */
    private fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        var done = if (target.isFile) target.length() else 0L
        if (expectedSize > 0 && done > expectedSize) {
            target.delete()
            done = 0L
        }
        if (expectedSize > 0 && done == expectedSize && done > 0L) return
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            if (done > 0L) setRequestProperty("Range", "bytes=$done-")
            setRequestProperty("User-Agent", "CloseCode")
        }
        try {
            val code = connection.responseCode
            if (done > 0L && code != HttpURLConnection.HTTP_PARTIAL) {
                target.delete()
                done = 0L
                if (code !in 200..299) throw IOException("The pip server returned HTTP $code.")
            } else if (code !in 200..299) {
                throw IOException("The pip server returned HTTP $code.")
            }
            val remaining = connection.contentLengthLong.takeIf { it > 0 }
            val total = if (remaining != null) done + remaining else expectedSize
            RandomAccessFile(target, "rw").use { output ->
                output.seek(done)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        onProgress(done, total)
                    }
                }
            }
            if (expectedSize > 0 && done != expectedSize) {
                throw IOException("Short download: $done of $expectedSize bytes.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
