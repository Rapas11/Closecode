package com.closecode.tools

/**
 * One stanza from a Termux `Packages` index. Only the fields the installer uses are
 * kept; a package's Description and other metadata are intentionally ignored.
 */
data class TermuxPackage(
    val name: String,
    val version: String,
    val architecture: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val depends: List<String>,
    val provides: List<String>,
)

/**
 * Dependency resolution is deliberately small: Termux dependencies are package names
 * with optional version constraints and alternatives. The installer uses the current
 * repository version, because the index it just downloaded is the same snapshot the
 * packages come from.
 */
class TermuxIndex(
    val packages: Map<String, TermuxPackage>,
    private val providers: Map<String, String>,
) {
    fun packageNamed(name: String): TermuxPackage? = packages[name]

    fun resolve(roots: List<String>): List<TermuxPackage> {
        val seen = linkedSetOf<String>()
        val ordered = mutableListOf<TermuxPackage>()

        fun add(name: String) {
            val packageName = providers[name] ?: name
            if (packageName in seen) return
            val packageFile = packages[packageName]
                ?: throw IllegalArgumentException("The shell-tools package '$name' is not in the repository.")
            seen += packageName
            packageFile.depends.forEach { dependency ->
                val available = firstAvailable(dependency, this)
                    ?: throw IllegalArgumentException(
                        "The shell-tools dependency '$dependency' is not in the repository.",
                    )
                add(available)
            }
            ordered += packageFile
        }

        roots.forEach { add(it) }
        return ordered
    }

    companion object {
        fun parse(text: String): TermuxIndex {
            val packages = linkedMapOf<String, TermuxPackage>()
            val providers = linkedMapOf<String, String>()
            text.split(Regex("\\n\\s*\\n")).forEach { stanza ->
                val fields = fields(stanza)
                val name = fields["Package"] ?: return@forEach
                val packageFile = TermuxPackage(
                    name = name,
                    version = fields["Version"].orEmpty(),
                    architecture = fields["Architecture"].orEmpty(),
                    fileName = fields["Filename"].orEmpty(),
                    sizeBytes = fields["Size"]?.toLongOrNull() ?: 0L,
                    sha256 = fields["SHA256"].orEmpty(),
                    depends = splitRaw(fields["Depends"].orEmpty()) +
                        splitRaw(fields["Pre-Depends"].orEmpty()),
                    provides = splitList(fields["Provides"].orEmpty()),
                )
                packages[name] = packageFile
                providers.putIfAbsent(name, name)
                packageFile.provides.forEach { provided ->
                    providers.putIfAbsent(provided, name)
                }
            }
            packages.keys.forEach { providers[it] = it }
            return TermuxIndex(packages, providers)
        }

        private fun fields(stanza: String): Map<String, String> {
            val result = linkedMapOf<String, String>()
            var current: String? = null
            stanza.lineSequence().forEach { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    current?.let { result[it] = result[it].orEmpty() + "\n" + line.trim() }
                } else {
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon)
                        current = key
                        result[key] = line.substring(colon + 1).trim()
                    }
                }
            }
            return result
        }

        private fun splitRaw(value: String): List<String> =
            value.split(',').map { it.trim() }.filter { it.isNotBlank() }

        private fun splitList(value: String): List<String> =
            value.split(',').mapNotNull { dependencyName(it) }.filter { it.isNotBlank() }

        /** `foo (>= 1.2) | bar:any` becomes `foo`; the first available alternative wins. */
        private fun dependencyName(value: String): String? =
            value.split('|')
                .map { alternative ->
                    alternative.substringBefore('(')
                        .substringBefore(':')
                        .trim()
                }
                .firstOrNull { it.isNotBlank() }

        fun firstAvailable(value: String, index: TermuxIndex): String? =
            value.split('|')
                .map { alternative ->
                    alternative.substringBefore('(')
                        .substringBefore(':')
                        .trim()
                }
                .firstOrNull { candidate ->
                    candidate.isNotBlank() &&
                        (index.packages.containsKey(candidate) ||
                            index.providers.containsKey(candidate))
                }
    }
}
