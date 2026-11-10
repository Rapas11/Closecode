package com.closecode.debug.probe

internal enum class ProbeSize(val lines: Int, val fileName: String) {
    K1(1_000, "SORA_PROBE_1K.kt"),
    K10(10_000, "SORA_PROBE_10K.kt"),
    K50(50_000, "SORA_PROBE_50K.kt"),
}

internal object ProbeContent {

    fun text(size: ProbeSize): String {
        val out = StringBuilder(size.lines * 48)
        var line = 0
        var block = 0
        while (line < size.lines) {
            if (size.lines - line < 10) {
                out.append("// filler ").append(line).append('\n')
                line++
                continue
            }
            out.append("class Probe").append(block).append(" {\n")
            out.append("    val id: Int = ").append(block).append('\n')
            out.append("    val name: String = \"probe-").append(block).append("\"\n")
            out.append("    fun value(input: Int): Int {\n")
            out.append("        if (input > 0) {\n")
            out.append("            return input + id\n")
            out.append("        }\n")
            out.append("        return id\n")
            out.append("    }\n")
            out.append("}\n")
            line += 10
            block++
        }
        return out.toString()
    }
}
