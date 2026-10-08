package com.closecode.ui.ide

import com.closecode.ui.model.ProjectFile

/**
 * Path completion for the terminal.
 *
 * The shell cannot help here: it is fed through a pipe, so it has no readline, and Tab would be
 * passed straight through to the program the reader ran rather than completing anything. So the
 * completion is done by the app, against the project's own file tree — which means it knows the
 * project rather than having to guess by listing the filesystem, and it can offer a file the shell
 * would not have listed because it is hidden or ignored.
 *
 * The shell's working directory is invisible from out here, so the app follows `cd` itself. That is
 * a deliberate limitation: a program that changes directory without telling us desyncs completion,
 * and there is no way to ask the shell where it is without running a command in it.
 */

/** The word under the caret, and the offset it starts at. */
internal fun wordAt(text: String, caret: Int): Pair<Int, String> {
    val at = caret.coerceIn(0, text.length)
    var start = at
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    return start to text.substring(start, at)
}

/**
 * A path split into the part before the last separator and the name being typed.
 *
 * `app/sr/Ma` becomes `app/sr/` and `Ma`. The separator goes with the directory so the two halves
 * can be resolved and matched independently and then rejoined without losing it.
 */
internal fun splitPath(token: String): Pair<String, String> {
    val cut = token.lastIndexOf('/')
    return when {
        cut < 0 -> "" to token
        cut == token.length - 1 -> token to ""
        else -> token.substring(0, cut + 1) to token.substring(cut + 1)
    }
}

/**
 * [path] made absolute against [cwd], without touching the disk.
 *
 * Pure string arithmetic on purpose: the answer is needed while the reader is typing, on every Tab,
 * and a round trip to the filesystem for it would put a stat on the typing path for no gain — the
 * tree in memory already knows which of these paths exist.
 */
internal fun resolvePath(cwd: String, path: String): String {
    if (path.startsWith("/")) return normalisePath(path)
    val base = if (path.startsWith("~/")) "" else cwd
    return normalisePath("$base/$path")
}

/** The project-relative path [absolute] occupies, or null when it is outside the project. */
internal fun projectRelative(projectRootAbs: String, absolute: String): String? {
    val root = projectRootAbs.trimEnd('/')
    if (root.isEmpty()) return null
    if (!absolute.startsWith("$root/")) return null
    return absolute.removePrefix("$root/")
}

/** Collapses `.` and `..` segments, so `cd ..` and `./x` mean what they look like. */
internal fun normalisePath(path: String): String {
    val out = ArrayList<String>()
    for (part in path.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
            else -> out.add(part)
        }
    }
    return out.joinToString("/")
}

/**
 * Where a `cd` command would take the shell, given where it is now.
 *
 * Returns null for any command that is not a plain `cd`, and for one that cannot be resolved to a
 * project directory — which leaves completion where it was rather than sending it somewhere the
 * reader did not ask to go. `cd` on its own goes home, `cd -` is not tracked because the shell
 * keeps the previous directory in a variable the app cannot see.
 */
internal fun cwdAfterCommand(cwd: String, projectRootRel: String, command: String): String? {
    val parts = command.trim().split(Regex("\\s+"))
    val head = parts.firstOrNull()?.substringAfterLast('/') ?: return null
    if (head != "cd") return null
    val arg = parts.getOrNull(1) ?: return projectRootRel
    return when {
        arg == "-" || arg.startsWith("-") -> null
        arg == "~" || arg == "~/" -> projectRootRel
        arg.startsWith("/") -> projectRelative(projectRootRel, arg) ?: return null
        else -> resolvePath(cwd, arg)
    }
}

/**
 * One thing Tab can complete to: the name to put on the line, and whether it is a directory.
 *
 * The trailing slash is part of the name rather than added by the caller, because it is what tells
 * the reader there is more to come — and it is what makes a second Tab keep going into the folder.
 */
internal data class Completion(val name: String, val isFolder: Boolean)

/**
 * The entries directly inside [dir] whose names begin with [namePart].
 *
 * Sorted by name so the list a reader sees never reorders itself between two Taps, and folders are
 * not distinguished from files here — the caller needs the whole set to work out a common prefix,
 * and filtering afterwards would give a prefix that stops short of what is actually there.
 *
 * `.git` is never offered. It is a real directory, it is never what anyone is reaching for, and
 * walking into it produces paths the shell cannot usefully do anything with.
 */
internal fun completionCandidates(
    dir: String,
    namePart: String,
    tree: List<ProjectFile>,
): List<Completion> {
    // The project root is an empty string, not a separator: turning it into "/" would ask for entries
    // whose paths begin with a slash, and the project root has none — so completing there found
    // nothing at all, which is the most common place anyone completes from.
    val prefix = if (dir.isEmpty() || dir.endsWith("/")) dir else "$dir/"
    return tree.asSequence()
        .filter { it.path.startsWith(prefix) }
        .filter { it.path.length > prefix.length }
        // Only the leaf: a deeper descendant is not something that can be named here.
        .filter { !it.path.substring(prefix.length).contains('/') }
        .filter { it.name.startsWith(namePart) }
        .filter { it.name != ".git" }
        .distinctBy { it.name }
        .sortedBy { it.name }
        .map { Completion(it.name, it.kind == com.closecode.ui.model.FileKind.Folder) }
        .toList()
}

/**
 * What every candidate has at the front, beyond what has already been typed.
 *
 * Empty when they share nothing more, which is the signal that there is no useful prefix to add and
 * the reader needs to see the list instead. A single candidate is its own prefix, so one Tab is
 * enough to finish it.
 */
internal fun commonPrefixOf(candidates: List<Completion>): String {
    if (candidates.isEmpty()) return ""
    if (candidates.size == 1) return candidates.first().name
    var length = candidates.first().name.length
    while (length > 0 && candidates.any { !it.name.startsWith(candidates.first().name.take(length)) }) {
        length--
    }
    return candidates.first().name.take(length)
}

/**
 * The candidates with directories marked, ready to be shown.
 *
 * The slash is only added here, at the point of display: it says "there is more inside", and the
 * name that goes back onto the command line stays the bare one so a second Tab can carry on.
 */
internal fun completionLabels(candidates: List<Completion>): List<String> =
    candidates.map { if (it.isFolder) "${it.name}/" else it.name }

/**
 * What the next Tab should insert.
 *
 * There are three answers, and which one applies depends on whether there is anything left to add:
 *
 * - One candidate: it *is* the answer. A folder gets a trailing slash, because there is more inside
 *   it and the reader should not have to type one.
 * - Several candidates, and they share more than has been typed: the shared part is inserted. This
 *   is the first Tab's job, and choosing one of them here would skip past the others the reader can
 *   still see in the popup.
 * - Several candidates, and nothing more to add: with no choice made yet the answer is null, which
 *   is the signal to put the list up rather than to pretend something was completed. A later Tab
 *   cycles, and wraps once it runs off the end.
 */
internal fun nextCompletion(
    current: String,
    candidates: List<Completion>,
    cycle: Int,
): String? {
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) {
        val only = candidates.first()
        return if (only.isFolder) "${only.name}/" else only.name
    }
    val extension = commonPrefixOf(candidates)
    if (extension.length > current.length) return extension
    if (cycle <= 0) return null
    val picked = candidates[(cycle - 1) % candidates.size]
    return if (picked.isFolder) "${picked.name}/" else picked.name
}