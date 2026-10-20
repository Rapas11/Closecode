package com.closecode.build

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * One downloadable file of the build-tools pack: what it is, where it comes from, how big it
 * is, the SHA-256 that must match before it is used, and its license.
 */
data class ToolComponent(
    val id: String,
    val version: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
)

/** The aapt2 story: it rides inside the APK as a lib file and is never downloaded. */
data class Aapt2Info(
    val version: String,
    val license: String,
    val source: String,
)

/**
 * The pack descriptor shipped in `assets/toolchain.json`. [scripts/prepare-toolchain.ps1]
 * writes both the files and this descriptor, so the versions here, the files on the host and
 * the checksums can never drift apart: everything comes from one run of the script.
 */
data class Toolchain(
    val version: String,
    val kotlin: String,
    val composeCompiler: String,
    val compileSdk: Int,
    val aapt2: Aapt2Info,
    val files: List<ToolComponent>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }

    fun byId(id: String): ToolComponent? = files.firstOrNull { it.id == id }
}

/**
 * Reading `assets/toolchain.json`. Parsing is pure, so what the app will believe is pinned
 * by a test against the file the script actually produced; a broken asset is an empty pack
 * — "tools are missing" — never a crash.
 */
object ToolchainManifest {

    /** The ids forming the kotlinc launch classpath, in order. */
    val KOTLINC_LAUNCH_IDS: List<String> = listOf(
        "kotlin-compiler", "kotlin-stdlib", "kotlin-reflect", "kotlin-script-runtime",
        "kotlin-daemon-embeddable", "trove4j", "annotations", "coroutines",
    )

    fun load(context: Context): Toolchain =
        runCatching {
            context.assets.open("toolchain.json").bufferedReader().use { it.readText() }
        }.mapCatching { parse(it) }.getOrElse { empty() }

    /** A pack with no files: every check that needs tools says "not installed yet". */
    fun empty(): Toolchain = Toolchain(
        version = "0",
        kotlin = "unknown",
        composeCompiler = "unknown",
        compileSdk = ProjectPins.COMPILE_SDK,
        aapt2 = Aapt2Info("unknown", "", ""),
        files = emptyList(),
    )

    fun parse(json: String): Toolchain {
        val root = JsonParser.parseString(json).asJsonObject
        val files = root.arrayObjects("files").mapNotNull { element ->
            val id = element.str("id") ?: return@mapNotNull null
            val fileName = element.str("fileName") ?: return@mapNotNull null
            val url = element.str("url") ?: return@mapNotNull null
            ToolComponent(
                id = id,
                version = element.str("version").orEmpty(),
                fileName = fileName,
                url = url,
                sizeBytes = element.long("sizeBytes") ?: 0L,
                sha256 = element.str("sha256").orEmpty(),
                license = element.str("license").orEmpty(),
            )
        }
        val aapt2 = root.obj("aapt2")
        return Toolchain(
            version = root.str("version") ?: "0",
            kotlin = root.str("kotlin") ?: "unknown",
            composeCompiler = root.str("composeCompiler") ?: "unknown",
            compileSdk = root.str("compileSdk")?.toIntOrNull() ?: ProjectPins.COMPILE_SDK,
            aapt2 = Aapt2Info(
                version = aapt2?.str("version") ?: "unknown",
                license = aapt2?.str("license").orEmpty(),
                source = aapt2?.str("source").orEmpty(),
            ),
            files = files,
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arrayObjects(key: String): List<JsonObject> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }
            ?.map { it.asJsonObject }
            .orEmpty()

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { !it.isJsonNull }?.asLong
}

/** Setup progress for the whole toolchain, shown on the first-setup screen. */
sealed interface ToolchainState {
    data object NotStarted : ToolchainState

    /** [index] of [count] components; the screen draws "Downloading 3 of 14 · 120 MB of 260 MB". */
    data class Downloading(
        val index: Int,
        val count: Int,
        val doneBytes: Long,
        val totalBytes: Long,
        val current: String,
        val note: String? = null,
    ) : ToolchainState

    data class Verifying(val fileName: String) : ToolchainState

    /** Check files: every checksum is being recomputed. */
    data object Checking : ToolchainState

    /** Wi-Fi is gone and the user has not allowed mobile data. */
    data object WaitingForWifi : ToolchainState

    data object Ready : ToolchainState
    data class Failed(val message: String) : ToolchainState
}
