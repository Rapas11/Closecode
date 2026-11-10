package com.closecode.build

import java.util.concurrent.TimeUnit

/**
 * Gradle starts child processes (the Kotlin daemon, aapt2, workers). On Android there is no
 * java.lang.ProcessHandle, so this reads `/system/bin/ps` and walks parent ids. Killing only
 * the launcher leaves those children running, holding the build lock and the CPU.
 */
internal object ProcessTree {

    fun terminate(process: Process) {
        val root = pidOf(process)
        if (root == null) {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
            return
        }
        val known = descendants(root)
        signal("TERM", known.reversed() + root)
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            val remaining = (descendants(root) + known).distinct()
            signal("KILL", remaining.reversed() + root)
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
        }
    }

    fun pid(process: Process): Long = pidOf(process)?.toLong() ?: -1L

    private fun pidOf(process: Process): Int? = runCatching {
        val field = process.javaClass.getDeclaredField("pid").apply { isAccessible = true }
        (field.get(process) as? Number)?.toInt()?.takeIf { it > 0 }
    }.getOrNull()

    private fun descendants(root: Int): List<Int> {
        val parents = parentMap() ?: return emptyList()
        val children = parents.entries.groupBy({ it.value }, { it.key })
        val found = ArrayList<Int>()
        val queue = ArrayDeque<Int>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            children[parent].orEmpty().forEach { child ->
                if (child != root && found.add(child)) queue.add(child)
            }
        }
        return found
    }

    private fun parentMap(): Map<Int, Int>? {
        val rows = readPs() ?: return null
        val map = LinkedHashMap<Int, Int>()
        for (row in rows) {
            val (pid, parent) = row
            if (pid > 0 && parent >= 0) map[pid] = parent
        }
        return map
    }

    private fun readPs(): List<Pair<Int, Int>>? {
        val detailed = runCatching { ps(listOf("/system/bin/ps", "-A", "-o", "PID,PPID")) }
            .getOrNull()
        detailed?.let { text ->
            val rows = text.lineSequence().mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val pid = parts[0].toIntOrNull()
                    val parent = parts[1].toIntOrNull()
                    if (pid != null && parent != null) pid to parent else null
                } else {
                    null
                }
            }.toList()
            if (rows.isNotEmpty()) return rows
        }
        val plain = runCatching { ps(listOf("/system/bin/ps", "-A")) }.getOrNull() ?: return null
        return plain.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 3) {
                val pid = parts[1].toIntOrNull()
                val parent = parts[2].toIntOrNull()
                if (pid != null && parent != null) pid to parent else null
            } else {
                null
            }
        }.toList()
    }

    private fun ps(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(5, TimeUnit.SECONDS)
        return text
    }

    private fun signal(signal: String, pids: List<Int>) {
        if (pids.isEmpty()) return
        runCatching {
            val process = ProcessBuilder(
                listOf("/system/bin/kill", "-$signal") + pids.distinct().map { it.toString() },
            ).redirectErrorStream(true).start()
            process.waitFor(3, TimeUnit.SECONDS)
        }
    }
}
