package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Source Control header names the repository a project is connected to. A project with
 * no remote must say so, and must never wear the account's name — that reads like it
 * belongs to somebody else's repository.
 */
class GitProjectRepoLabelTest {

    @Test
    fun aGitHubRemoteIsNamedByOwnerAndRepository() {
        assertEquals(
            "owner/repo",
            gitProjectRepoLabel("https://github.com/owner/repo.git", "Project"),
        )
        assertEquals(
            "owner/repo",
            gitProjectRepoLabel("git@github.com:owner/repo.git", "Project"),
        )
    }

    @Test
    fun aProjectWithNoRemoteSaysSo() {
        assertEquals(
            "Not connected to GitHub",
            gitProjectRepoLabel(null, "Project"),
        )
        assertEquals(
            "Not connected to GitHub",
            gitProjectRepoLabel("", "Project"),
        )
    }

    @Test
    fun aRemoteThatIsNotGitHubFallsBackToTheProjectName() {
        assertEquals(
            "Project",
            gitProjectRepoLabel("https://gitlab.com/owner/repo.git", "Project"),
        )
    }
}
