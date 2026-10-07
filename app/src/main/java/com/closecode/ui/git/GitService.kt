package com.closecode.ui.git

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent

/** A single path the index and the working tree disagree about. */
data class GitChange(
    val path: String,
    /** True once the change is already in the index waiting to be committed. */
    val staged: Boolean,
    val kind: GitChangeKind,
)

enum class GitChangeKind { Added, Modified, Deleted }

/**
 * Everything the Changes screen needs from one `git status`.
 *
 * The three lists are mutually exclusive: [staged] is index-vs-HEAD, [unstaged] is
 * working-tree-vs-index, [untracked] is a file git has never seen. `conflicts` are
 * paths that are merged badly and must be resolved before anything else can move.
 */
data class GitSnapshot(
    val branch: String,
    val detached: Boolean,
    val hasCommits: Boolean,
    val ahead: Int,
    val behind: Int,
    val staged: List<GitChange>,
    val unstaged: List<GitChange>,
    val untracked: List<GitChange>,
    val conflicts: List<String>,
) {
    val changeCount: Int
        get() = staged.size + unstaged.size + untracked.size + conflicts.size

    val isClean: Boolean
        get() = changeCount == 0

    companion object {
        val Empty = GitSnapshot(
            branch = "main",
            detached = false,
            hasCommits = false,
            ahead = 0,
            behind = 0,
            staged = emptyList(),
            unstaged = emptyList(),
            untracked = emptyList(),
            conflicts = emptyList(),
        )
    }
}

/** One row of `git log`. */
data class GitCommit(
    val id: String,
    val summary: String,
    val author: String,
    val whenMillis: Long,
) {
    val shortId: String get() = id.take(7)
}

/**
 * The local half of version control: init, status, stage, commit, log, branches.
 *
 * Wraps JGit so nothing in the UI ever sees an exception type from the library — every
 * entry point returns [Result] and failures carry a message a person can read. GitHub,
 * remotes and pushing live in a separate client; this class only ever touches the
 * repository inside the project directory.
 */
class GitService(private val root: File) {

    /** True once `git init` has run. `.git` is filtered out of the file tree elsewhere. */
    fun isVersioned(): Boolean = File(root, ".git").exists()

    fun init(initialBranch: String = "main"): Result<Unit> = runCatching {
        if (isVersioned()) return@runCatching
        Git.init().setDirectory(root).setInitialBranch(initialBranch).call()
    }

    fun snapshot(): Result<GitSnapshot> = runCatching {
        open().use { git ->
            val repo = git.repository
            val status = git.status().call()
            val hasCommits = repo.resolve(Constants.HEAD) != null
            val rawBranch = repo.branch
            var ahead = 0
            var behind = 0
            if (hasCommits && rawBranch != null) {
                val tracking = BranchTrackingStatus.of(repo, rawBranch)
                ahead = tracking?.aheadCount ?: 0
                behind = tracking?.behindCount ?: 0
            }
            GitSnapshot(
                branch = rawBranch ?: DETACHED,
                detached = rawBranch == null,
                hasCommits = hasCommits,
                ahead = ahead,
                behind = behind,
                staged = stagedOf(status),
                unstaged = unstagedOf(status),
                untracked = status.untracked.sorted().map { GitChange(it, false, GitChangeKind.Added) },
                conflicts = status.conflicting.sorted(),
            )
        }
    }

    /** Adds work-tree changes to the index so the next commit picks them up. */
    fun stage(paths: List<String>): Result<Unit> = runCatching {
        open().use { git ->
            paths.forEach { path ->
                if (File(root, path).exists()) {
                    git.add().addFilepattern(path).call()
                } else {
                    // The file was deleted in the editor: stage the deletion, not a re-add.
                    git.rm().addFilepattern(path).setCached(true).call()
                }
            }
        }
    }

    /** Takes paths back out of the index, leaving the files on disk exactly as they are. */
    fun unstage(paths: List<String>): Result<Unit> = runCatching {
        open().use { git ->
            val hasCommits = git.repository.resolve(Constants.HEAD) != null
            paths.forEach { path ->
                if (hasCommits) {
                    git.reset().setRef(Constants.HEAD).addPath(path).call()
                } else {
                    // No HEAD to reset against yet, so drop the index entry outright. The
                    // working copy stays put and the path simply becomes untracked again.
                    git.rm().addFilepattern(path).setCached(true).call()
                }
            }
        }
    }

    /** Commits whatever is staged. Returns the id of the new commit. */
    fun commit(message: String): Result<String> = runCatching {
        val trimmed = message.trim()
        require(trimmed.isNotEmpty()) { "A commit needs a message." }
        open().use { git ->
            val ident = identity(git)
            git.commit()
                .setAuthor(ident)
                .setCommitter(ident)
                .setMessage(trimmed)
                .call()
                .name
        }
    }

    fun history(limit: Int = 50): Result<List<GitCommit>> = runCatching {
        open().use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) return@runCatching emptyList()
            val commits = git.log().setMaxCount(limit.coerceAtLeast(1)).call()
            val rows = commits.map { rev ->
                GitCommit(
                    id = rev.name,
                    summary = rev.shortMessage,
                    author = rev.authorIdent.name,
                    whenMillis = rev.commitTime * 1000L,
                )
            }
            (commits as? AutoCloseable)?.close()
            rows
        }
    }

    fun branches(): Result<List<String>> = runCatching {
        open().use { git ->
            git.branchList().call()
                .map { it.name.removePrefix(Constants.R_HEADS) }
                .sorted()
        }
    }

    fun createBranch(name: String): Result<Unit> = runCatching {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A branch needs a name." }
        open().use { git -> git.branchCreate().setName(trimmed).call() }
    }

    fun checkout(name: String): Result<Unit> = runCatching {
        open().use { git -> git.checkout().setName(name).call() }
    }

    private fun open(): Git = Git.open(root)

    private fun identity(git: Git): PersonIdent {
        val cfg = git.repository.config
        val name = cfg.getString("user", null, "name")?.takeIf { it.isNotBlank() } ?: DEFAULT_USER
        val email = cfg.getString("user", null, "email")?.takeIf { it.isNotBlank() } ?: DEFAULT_EMAIL
        return PersonIdent(name, email)
    }

    companion object {
        const val DETACHED = "detached"
        const val DEFAULT_USER = "Closecode"
        const val DEFAULT_EMAIL = "closecode@localhost"

        /**
         * JGit's `Status` splits the index and the working tree into two disjoint halves:
         * added/changed/removed are index-vs-HEAD (staged), modified/missing are
         * working-tree-vs-index (not staged).
         */
        internal fun stagedOf(status: Status): List<GitChange> = buildList {
            status.added.sorted().forEach { add(GitChange(it, true, GitChangeKind.Added)) }
            status.changed.sorted().forEach { add(GitChange(it, true, GitChangeKind.Modified)) }
            status.removed.sorted().forEach { add(GitChange(it, true, GitChangeKind.Deleted)) }
        }

        internal fun unstagedOf(status: Status): List<GitChange> = buildList {
            status.modified.sorted().forEach { add(GitChange(it, false, GitChangeKind.Modified)) }
            status.missing.sorted().forEach { add(GitChange(it, false, GitChangeKind.Deleted)) }
        }
    }
}
