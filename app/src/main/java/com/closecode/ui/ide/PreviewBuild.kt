package com.closecode.ui.ide

import com.closecode.build.BuildChain
import com.closecode.build.BuildConfigValues
import com.closecode.build.BuildTools
import com.closecode.build.PreviewContract
import java.io.File
import java.security.MessageDigest

/**
 * Builds the little APK that makes real preview pictures possible.
 *
 * For every no-argument @Preview function, a harness object is generated next to the project's
 * own sources:

 *     object P12ab : PreviewHost { @Composable override fun Content() { com.example.Home() } }
 *
 * The whole project is compiled with those harnesses through the same [BuildChain] the Build
 * panel uses, into its own directory, so a preview build never touches the APK the user
 * installs. CloseCode's `:preview` process then loads this APK with a class loader and draws
 * each harness, which is why the project's own Compose code and resources are the ones on
 * screen — not an imitation of them.
 *
 * Previews that cannot be called this way (parameters, private, nested, not composable) are
 * kept out of the APK and carry a plain reason for their card.
 */
internal object PreviewBuild {

    /** Bumped when the harness format or renderer changes in a way old PNGs must not survive. */
    private const val CACHE_VERSION = 1

    const val PLAN_NAME = "plan.json"
    const val INDEX_NAME = "previews.json"
    const val ERROR_NAME = "render-error.txt"

    data class Harness(
        val id: String,
        val function: String,
        val path: String,
        val line: Int,
        val packageName: String,
        val className: String,
    )

    data class Plan(
        val harnesses: List<Harness>,
        /** `path#line` → a plain sentence for the card. */
        val problems: Map<String, String>,
    )

    data class Prepared(
        val outputDir: File,
        val plan: Plan,
        val apk: File?,
        val entries: List<PreviewContract.Entry>,
        val needsRender: Boolean,
        val error: String?,
    )

    fun problemKey(path: String, line: Int): String = "$path#$line"

    /**
     * The identity the generated harness and the PNG share. Path, line and name together are
     * unique even when two files declare a preview with the same function name.
     */
    fun harnessId(path: String, line: Int, function: String): String =
        shortHash("$path:$line:$function")

    /**
     * A content hash for the pictures. Code, device and theme are all in it: changing a colour
     * or switching to Dark makes a different folder, so the old picture is never shown as the
     * new one. Files are sorted so folder order cannot change the key.
     */
    fun cacheKey(
        files: List<Pair<String, String>>,
        widthDp: Int,
        heightDp: Int,
        dark: Boolean,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("closecode-preview-$CACHE_VERSION\n".toByteArray())
        digest.update("$widthDp x $heightDp dark=$dark\n".toByteArray())
        files.sortedBy { it.first }.forEach { (path, source) ->
            digest.update(path.toByteArray())
            digest.update(0)
            digest.update(source.toByteArray())
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(24)
    }

    fun planFor(files: List<Pair<String, String>>): Plan {
        val harnesses = ArrayList<Harness>()
        val problems = LinkedHashMap<String, String>()
        for ((path, source) in files.sortedBy { it.first }) {
            for (function in previewFunctionsOf(source)) {
                val key = problemKey(path, function.line)
                val reason = previewUnsupportedReason(function)
                if (reason != null) {
                    problems[key] = reason
                    continue
                }
                val id = harnessId(path, function.line, function.name)
                harnesses += Harness(
                    id = id,
                    function = function.name,
                    path = path,
                    line = function.line,
                    packageName = function.packageName,
                    className = "P$id",
                )
            }
        }
        return Plan(harnesses, problems)
    }

    /**
     * Returns a cached result when the folder for this key already has its pictures, otherwise
     * generates the harnesses and builds the preview APK. Blocking: call it off the main thread.
     */
    @Synchronized
    fun prepare(
        projectDir: File,
        config: BuildConfigValues,
        tools: BuildTools,
        files: List<Pair<String, String>>,
        widthDp: Int,
        heightDp: Int,
        dark: Boolean,
        cacheDir: File,
        cancelled: () -> Boolean = { false },
        onStatus: (String) -> Unit = {},
    ): Prepared {        val key = cacheKey(files, widthDp, heightDp, dark)
        val outputDir = File(cacheDir, "previews/$key").apply { mkdirs() }
        val plan = planFor(files)
        if (plan.harnesses.isEmpty()) {
            return Prepared(outputDir, plan, null, emptyList(), false, null)
        }
        val cached = PreviewContract.readIndex(outputDir)
        if (cached.isNotEmpty() &&
            cached.all { it.png.isNotBlank() && File(outputDir, it.png).isFile } &&
            !File(outputDir, ERROR_NAME).isFile
        ) {
            return Prepared(outputDir, plan, null, cached, false, null)
        }

        File(outputDir, INDEX_NAME).delete()
        File(outputDir, ERROR_NAME).delete()
        val sourceDir = File(outputDir, "src").apply {
            deleteRecursively()
            mkdirs()
        }
        val sources = writeSources(plan, sourceDir)
        val apk = File(outputDir, "preview.apk")
        apk.delete()
        onStatus("Building ${plan.harnesses.size} preview${if (plan.harnesses.size == 1) "" else "s"}…")
        val result = BuildChain.build(
            projectDir = projectDir,
            config = config,
            tools = tools,
            extraSources = sources,
            buildDirectory = File(projectDir, "build/preview-build"),
            outputApk = apk,
            onStep = { step -> onStatus("Previews · ${step.label}") },
            cancelled = cancelled,
        )
        if (result.failure?.cancelled == true) {
            return Prepared(outputDir, plan, null, emptyList(), false, null)
        }
        if (!result.ok || !apk.isFile) {
            val reason = result.failure?.message
                ?: "The preview build did not produce an APK."
            return Prepared(outputDir, plan, null, emptyList(), false, reason)
        }
        writePlan(outputDir, apk, plan)
        return Prepared(outputDir, plan, apk, emptyList(), true, null)
    }

    /** Writes the two generated sources and returns them for the compiler. */
    fun writeSources(plan: Plan, directory: File): List<File> {
        directory.mkdirs()
        val host = File(directory, "PreviewHost.kt")
        host.writeText(
            """
            package com.closecode.preview

            import androidx.compose.runtime.Composable

            interface PreviewHost {
                @Composable
                fun Content()
            }
            """.trimIndent() + "\n",
        )
        val bodies = plan.harnesses.joinToString("\n\n") { harness ->
            val target = if (harness.packageName.isBlank()) {
                harness.function
            } else {
                "${harness.packageName}.${harness.function}"
            }
            """
            object ${harness.className} : PreviewHost {
                @Composable
                override fun Content() {
                    $target()
                }
            }
            """.trimIndent()
        }
        val harnesses = File(directory, "Harnesses.kt")
        harnesses.writeText(
            """
            package com.closecode.preview.gen

            import androidx.compose.runtime.Composable
            import com.closecode.preview.PreviewHost

            $bodies
            """.trimIndent() + "\n",
        )
        return listOf(host, harnesses)
    }

    private fun writePlan(outputDir: File, apk: File, plan: Plan) {
        val entries = plan.harnesses.joinToString(",") { harness ->
            """{"id":"${harness.id}","function":"${harness.function}","harness":"${harness.className}","png":"${harness.id}.png"}"""
        }
        File(outputDir, PLAN_NAME).writeText(
            """{"apk":"${jsonEscape(apk.absolutePath)}","entries":[$entries]}""" + "\n",
        )
    }

    private fun jsonEscape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun shortHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
}

/**
 * Everything a preview render needs that is expensive to gather: the built toolchain and the
 * project's package/version settings. Loaded once when the Preview panel opens, off the main
 * thread, and reused for every save.
 */
internal data class PreviewSetup(
    val projectRoot: File,
    val cacheDir: File,
    val config: BuildConfigValues,
    val tools: BuildTools,
)
