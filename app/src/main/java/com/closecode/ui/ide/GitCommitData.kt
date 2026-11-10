package com.closecode.ui.ide

import com.closecode.ui.git.GitChangeKind
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.util.io.DisabledOutputStream

/** One changed file in a commit, with the line counts the detail page shows. */
internal data class GitCommitFileInfo(
    val path: String,
    val kind: GitChangeKind,
    val added: Int,
    val removed: Int,
)

/** One commit ready for screen 20: message, author, time and every changed file. */
internal data class GitCommitInfo(
    val id: String,
    val message: String,
    val author: String,
    val whenMillis: Long,
    val files: List<GitCommitFileInfo>,
) {
    val shortId: String get() = id.take(7)
    val added: Int get() = files.sumOf { it.added }
    val removed: Int get() = files.sumOf { it.removed }
}

/**
 * Reads a commit straight from the repository rather than searching the 100-row history
 * window, so opening an older commit from a loaded history page still works.
 */
internal fun loadGitCommitInfo(root: File, id: String): Result<GitCommitInfo> = runCatching {
    Git.open(root).use { git ->
        val repo = git.repository
        RevWalk(repo).use { walk ->
            val commit = walk.parseCommit(repo.resolve(id))
            val parent = commit.parents.firstOrNull()?.let { walk.parseCommit(it) }
            val files = repo.newObjectReader().use { reader ->
                fun parser(tree: ObjectId?): AbstractTreeIterator =
                    if (tree == null) {
                        EmptyTreeIterator()
                    } else {
                        CanonicalTreeParser().apply { reset(reader, tree) }
                    }
                DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
                    formatter.setRepository(repo)
                    formatter.scan(parser(parent?.tree), parser(commit.tree)).map { entry ->
                        var added = 0
                        var removed = 0
                        formatter.toFileHeader(entry).toEditList().forEach { edit ->
                            when (edit.type) {
                                Edit.Type.INSERT -> added += edit.endB - edit.beginB
                                Edit.Type.DELETE -> removed += edit.endA - edit.beginA
                                Edit.Type.REPLACE -> {
                                    removed += edit.endA - edit.beginA
                                    added += edit.endB - edit.beginB
                                }
                                else -> Unit
                            }
                        }
                        val path = if (entry.changeType == DiffEntry.ChangeType.DELETE) {
                            entry.oldPath
                        } else {
                            entry.newPath
                        }
                        val kind = when (entry.changeType) {
                            DiffEntry.ChangeType.ADD,
                            DiffEntry.ChangeType.COPY,
                            -> GitChangeKind.Added

                            DiffEntry.ChangeType.DELETE -> GitChangeKind.Deleted
                            else -> GitChangeKind.Modified
                        }
                        GitCommitFileInfo(path, kind, added, removed)
                    }.sortedBy { it.path }
                }
            }
            GitCommitInfo(
                id = commit.name,
                message = commit.fullMessage.trim(),
                author = commit.authorIdent.name,
                whenMillis = commit.commitTime * 1_000L,
                files = files,
            )
        }
    }
}

/** Local and remote branch names for the switch-branch sheet on screen 18. */
internal data class GitBranchChoices(
    val local: List<String>,
    val remote: List<String>,
)

internal fun loadGitBranches(root: File): GitBranchChoices = runCatching {
    Git.open(root).use { git ->
        val repo = git.repository
        val local = git.branchList().call()
            .map { Repository.shortenRefName(it.name) }
            .sorted()
        val remote = repo.refDatabase.getRefsByPrefix(Constants.R_REMOTES)
            .map { Repository.shortenRefName(it.name) }
            .filterNot { it.endsWith("/HEAD") }
            .sorted()
        GitBranchChoices(local, remote)
    }
}.getOrElse { GitBranchChoices(emptyList(), emptyList()) }

/** Creates a local branch that tracks the tapped remote branch, then switches to it. */
internal fun checkoutGitRemoteBranch(root: File, remoteBranch: String): Result<Unit> = runCatching {
    val localName = remoteBranch.substringAfter('/')
    require(localName.isNotBlank()) { "That remote branch has no name to check out." }
    Git.open(root).use { git ->
        git.checkout()
            .setCreateBranch(true)
            .setName(localName)
            .setStartPoint(remoteBranch)
            .call()
    }
}
