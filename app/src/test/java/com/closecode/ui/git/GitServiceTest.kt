package com.closecode.ui.git

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The three-way split the Changes screen depends on — staged / unstaged / untracked —
 * is only worth as much as it is correct, so it is pinned here against a real
 * repository rather than mocked.
 */
class GitServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(name: String, body: String): File {
        val file = File(tmp.root, name)
        file.parentFile?.mkdirs()
        file.writeText(body)
        return file
    }

    private fun stagedPaths(s: GitSnapshot) = s.staged.map { it.path }

    private fun unstagedPaths(s: GitSnapshot) = s.unstaged.map { it.path }

    private fun untrackedPaths(s: GitSnapshot) = s.untracked.map { it.path }

    @Test
    fun aFreshDirectoryIsNotARepository() {
        assertFalse(GitService(tmp.root).isVersioned())
    }

    @Test
    fun initCreatesARepositoryOnTheInitialBranch() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        assertTrue(git.isVersioned())
        val snap = git.snapshot().getOrThrow()
        assertEquals("main", snap.branch)
        assertFalse(snap.hasCommits)
        assertTrue(snap.isClean)
    }

    @Test
    fun initTwiceIsHarmless() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        git.init().getOrThrow()
        assertTrue(git.isVersioned())
    }

    @Test
    fun anUntrackedFileShowsUpAsUntracked() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        val snap = git.snapshot().getOrThrow()
        assertEquals(listOf("main.kt"), untrackedPaths(snap))
        assertTrue(snap.staged.isEmpty())
        assertTrue(snap.unstaged.isEmpty())
    }

    @Test
    fun stagingMovesAFileFromUntrackedToStaged() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()

        val snap = git.snapshot().getOrThrow()
        assertTrue(untrackedPaths(snap).isEmpty())
        assertEquals(listOf("main.kt"), stagedPaths(snap))
        assertEquals(GitChangeKind.Added, snap.staged.single().kind)
    }

    @Test
    fun aModificationAfterCommitShowsUpAsUnstaged() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        write("main.kt", "fun main() = println(1)\n")
        val snap = git.snapshot().getOrThrow()
        assertEquals(listOf("main.kt"), unstagedPaths(snap))
        assertEquals(GitChangeKind.Modified, snap.unstaged.single().kind)
        assertTrue(snap.staged.isEmpty())
    }

    @Test
    fun stagingAModificationMovesItToStaged() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        write("main.kt", "fun main() = println(1)\n")
        git.stage(listOf("main.kt")).getOrThrow()
        val snap = git.snapshot().getOrThrow()
        assertTrue(unstagedPaths(snap).isEmpty())
        assertEquals(listOf("main.kt"), stagedPaths(snap))
        assertEquals(GitChangeKind.Modified, snap.staged.single().kind)
    }

    @Test
    fun deletingAFileShowsUpAsAnUnstagedDeletion() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        File(tmp.root, "main.kt").delete()
        val snap = git.snapshot().getOrThrow()
        assertEquals(listOf("main.kt"), unstagedPaths(snap))
        assertEquals(GitChangeKind.Deleted, snap.unstaged.single().kind)
    }

    @Test
    fun stagingADeletionMovesItToStaged() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        File(tmp.root, "main.kt").delete()
        git.stage(listOf("main.kt")).getOrThrow()
        val snap = git.snapshot().getOrThrow()
        assertTrue(unstagedPaths(snap).isEmpty())
        assertEquals(listOf("main.kt"), stagedPaths(snap))
        assertEquals(GitChangeKind.Deleted, snap.staged.single().kind)
    }

    @Test
    fun unstagePutsAPathBackWithoutTouchingTheFile() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.unstage(listOf("main.kt")).getOrThrow()

        val snap = git.snapshot().getOrThrow()
        assertTrue(snap.staged.isEmpty())
        assertEquals(listOf("main.kt"), untrackedPaths(snap))
        assertTrue(File(tmp.root, "main.kt").exists())
        assertEquals("fun main() {}\n", File(tmp.root, "main.kt").readText())
    }

    @Test
    fun unstageAfterACommitKeepsTheEditOnDisk() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        write("main.kt", "fun main() = println(1)\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.unstage(listOf("main.kt")).getOrThrow()

        val snap = git.snapshot().getOrThrow()
        assertTrue(snap.staged.isEmpty())
        assertEquals(listOf("main.kt"), unstagedPaths(snap))
        assertEquals("fun main() = println(1)\n", File(tmp.root, "main.kt").readText())
    }

    @Test
    fun commitRecordsTheMessageAndReturnsTheId() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        val id = git.commit("  Add the entry point  ").getOrThrow()

        assertEquals(40, id.length)
        val snap = git.snapshot().getOrThrow()
        assertTrue(snap.hasCommits)
        assertTrue(snap.isClean)

        val entry = git.history().getOrThrow().single()
        assertEquals(id, entry.id)
        assertEquals("Add the entry point", entry.summary)
        assertEquals(GitService.DEFAULT_USER, entry.author)
        assertTrue(entry.shortId.length == 7)
    }

    @Test
    fun commitRejectsAnEmptyMessage() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        val failure = git.commit("   ")
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun historyIsEmptyBeforeTheFirstCommit() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        assertTrue(git.history().getOrThrow().isEmpty())
    }

    @Test
    fun historyListsNewestFirstAndHonoursTheLimit() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        repeat(4) { i ->
            write("file$i.kt", "val v$i = $i\n")
            git.stage(listOf("file$i.kt")).getOrThrow()
            git.commit("commit $i").getOrThrow()
        }
        val all = git.history().getOrThrow()
        assertEquals(4, all.size)
        assertEquals("commit 3", all.first().summary)
        assertEquals(2, git.history(2).getOrThrow().size)
    }

    @Test
    fun branchesAppearAfterTheFirstCommit() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        // Nothing points at a branch until a commit exists, exactly like git itself.
        assertTrue(git.branches().getOrThrow().isEmpty())

        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        assertEquals(listOf("main"), git.branches().getOrThrow())
        git.createBranch("feature").getOrThrow()
        assertEquals(listOf("feature", "main"), git.branches().getOrThrow())
        git.checkout("feature").getOrThrow()
        assertEquals("feature", git.snapshot().getOrThrow().branch)
        git.checkout("main").getOrThrow()
        assertEquals("main", git.snapshot().getOrThrow().branch)
    }

    @Test
    fun creatingABranchBeforeTheFirstCommitFails() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        assertTrue(git.createBranch("scratch").isFailure)
    }

    @Test
    fun operationsOnAnUninitialisedDirectoryFailWithAMessage() {
        val git = GitService(tmp.root)
        val snap = git.snapshot()
        assertTrue(snap.isFailure)
        assertNotNull(snap.exceptionOrNull())
        assertTrue(git.commit("nope").isFailure)
        assertTrue(git.history().isFailure)
    }

    @Test
    fun ensureGitignoreFillsTheGapWithoutTouchingAFileTheUserWrote() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()

        // init() only makes a repository; the .gitignore arrives from the panel before it reads
        // status, so a test that inspects the working tree still sees only what it wrote.
        assertTrue(git.ensureGitignore())
        val written = File(tmp.root, GitService.GITIGNORE)
        assertTrue(written.exists())
        assertTrue(
            "app state must be ignored",
            written.readText().contains(".closecode.json"),
        )

        // Already there: the user's own file must never be overwritten.
        written.writeText("# mine\n")
        assertFalse(git.ensureGitignore())
        assertEquals("# mine\n", written.readText())
    }

    @Test
    fun ensureGitignoreDoesNothingOutsideARepository() {
        assertFalse(GitService(tmp.root).ensureGitignore())
        assertFalse(File(tmp.root, GitService.GITIGNORE).exists())
    }

    @Test
    fun stashClearsTheWorkingTreeAndRestoringPutsItBack() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() { one() }\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        write("main.kt", "fun main() { two() }\n")
        assertFalse(git.snapshot().getOrThrow().isClean)
        assertFalse(git.hasStash())

        assertTrue(git.stash().getOrThrow().startsWith("Stashed"))
        assertTrue(git.hasStash())
        // The tree really went back to HEAD; a stash that left the edits in place would make the
        // button look inert the moment the user pressed it.
        assertEquals("fun main() { one() }\n", File(tmp.root, "main.kt").readText())
        assertTrue(git.snapshot().getOrThrow().isClean)

        git.stashPop().getOrThrow()
        assertEquals("fun main() { two() }\n", File(tmp.root, "main.kt").readText())
        assertFalse(git.hasStash())
        assertFalse(git.snapshot().getOrThrow().isClean)
    }

    @Test
    fun stashRefusesWhenThereIsNothingToPark() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        git.stage(listOf("main.kt")).getOrThrow()
        git.commit("first").getOrThrow()

        assertTrue(git.stash().isFailure)
        assertFalse(git.hasStash())
    }

    @Test
    fun restoringWithoutAStashSaysSo() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        val pop = git.stashPop()
        assertTrue(pop.isFailure)
        assertTrue(pop.exceptionOrNull()?.message.orEmpty().contains("no stash"))
    }

    @Test
    fun stashNeedsACommitToParkWorkAgainst() {
        val git = GitService(tmp.root)
        git.init().getOrThrow()
        write("main.kt", "fun main() {}\n")
        assertTrue(git.stash().isFailure)
        assertFalse(git.hasStash())
    }

    @Test
    fun hasStashIsFalseOutsideARepository() {
        assertFalse(GitService(tmp.root).hasStash())
    }
}
