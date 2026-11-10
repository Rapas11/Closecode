package com.closecode.build

/**
 * Turns the compilers' many output dialects into the same jumpable [CompileDriver.Diagnostic]
 * the Build panel already draws. Gradle aggregates Kotlin, javac, aapt2, manifest merger and
 * R8 in one stream; the parser is deliberately pure so every dialect has a unit test.
 */
internal object GradleDiagnostics {

    private val kotlinColon = Regex(
        "^([ew]): (?:file://)?(.+?\\.(?:kt|kts|java)):(\\d+):(\\d+):?\\s+(.*)$",
    )
    private val kotlinPair = Regex(
        "^([ew]): (?:file://)?(.+?\\.(?:kt|kts|java)): \\((\\d+),\\s*(\\d+)\\):?\\s+(.*)$",
    )
    private val javac = Regex(
        "^(.+?\\.(?:java|kt)):(\\d+):\\s*(error|warning):\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )
    private val tool = Regex(
        "^(.+?\\.(?:xml|java|kt|json|pro|txt)):(\\d+)(?::(\\d+))?:?\\s+(error|warning):\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )
    private val manifestMerger = Regex(
        "^(.+?\\.xml):(\\d+):(\\d+)(?:-\\d+)?\\s+(Error|Warning):\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )
    private val plain = Regex("^(ERROR|WARNING):\\s*(.*)$")

    fun parse(output: String): List<CompileDriver.Diagnostic> {
        val seen = HashSet<String>()
        val out = ArrayList<CompileDriver.Diagnostic>()
        for (raw in output.lineSequence()) {
            val diagnostic = parseLine(raw) ?: continue
            val key = "${diagnostic.path}:${diagnostic.line}:${diagnostic.column}:" +
                "${diagnostic.kind}:${diagnostic.message}"
            if (seen.add(key)) out += diagnostic
            if (out.size >= MAX) break
        }
        return out
    }

    fun parseLine(raw: String): CompileDriver.Diagnostic? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        kotlinColon.matchEntire(line)?.let { match ->
            return diagnostic(
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4],
                match.groupValues[1],
                match.groupValues[5],
                "KOTLIN",
            )
        }
        kotlinPair.matchEntire(line)?.let { match ->
            return diagnostic(
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4],
                match.groupValues[1],
                match.groupValues[5],
                "KOTLIN",
            )
        }
        manifestMerger.matchEntire(line)?.let { match ->
            return diagnostic(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4],
                match.groupValues[5],
                "MANIFEST",
            )
        }
        javac.matchEntire(line)?.let { match ->
            return diagnostic(
                match.groupValues[1],
                match.groupValues[2],
                "1",
                match.groupValues[3],
                match.groupValues[4],
                "JAVAC",
            )
        }
        tool.matchEntire(line)?.let { match ->
            return diagnostic(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3].ifBlank { "1" },
                match.groupValues[4],
                match.groupValues[5],
                "TOOL",
            )
        }
        plain.matchEntire(line)?.let { match ->
            return CompileDriver.Diagnostic(
                path = "<gradle>",
                line = 1,
                kind = kind(match.groupValues[1]),
                message = match.groupValues[2].trim(),
                code = "R8",
            )
        }
        return null
    }

    private fun diagnostic(
        path: String,
        line: String,
        column: String,
        level: String,
        message: String,
        code: String,
    ): CompileDriver.Diagnostic = CompileDriver.Diagnostic(
        path = path.removePrefix("file://"),
        line = line.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        kind = kind(level),
        message = message.trim(),
        column = column.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        code = code,
    )

    private fun kind(level: String): CompileDriver.Diagnostic.Kind =
        if (level.equals("w", true) || level.equals("warning", true)) {
            CompileDriver.Diagnostic.Kind.Warning
        } else {
            CompileDriver.Diagnostic.Kind.Error
        }

    private const val MAX = 200
}
