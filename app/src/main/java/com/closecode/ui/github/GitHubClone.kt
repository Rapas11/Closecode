package com.closecode.ui.github

import java.io.File
import org.eclipse.jgit.api.Git

/**
 * Fetching a repository down onto the device.
 *
 * Separate from [GitHubRemote], which talks to a remote for a repository that is already here.
 * The destination has to be absent or empty: JGit refuses a non-empty directory, which is why
 * [com.closecode.ui.project.ProjectRepository.importCloned] creates the project folder and writes
 * nothing into it until the clone has finished.
 *
 * Any https remote is accepted — GitHub, GitLab, Bitbucket or something on a private network — so
 * this is deliberately not a GitHub call. [token] may be null: a public remote needs no
 * credentials, and asking for them anyway would turn a working anonymous clone into an
 * authentication prompt.
 *
 * No branch is named. `git clone` checks out the remote's own default HEAD — main, master or
 * whatever the repository has set — which is the branch the user would have picked from the list
 * anyway; choosing among the rest is work for the Source Control view once the code is here.
 */
class GitHubClone {

    fun clone(url: String, token: String?, into: File): Result<Unit> = runCatching {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://")) {
            "Only https:// remotes are supported in this build."
        }
        val existing = into.list()
        require(existing == null || existing.isEmpty()) {
            "\"${into.name}\" already exists and is not an empty directory."
        }
        into.parentFile?.mkdirs()
        val command = Git.cloneRepository()
            .setURI(trimmed)
            .setDirectory(into)
        if (token != null) command.setCredentialsProvider(tokenCredentials(token))
        command.call().use { }
    }
}

/**
 * Whether [error] says the operation failed because nobody was signed in — a 401, an expired
 * token, a private repository asked for without credentials.
 *
 * It answers one question the clone screen needs before it can offer the right thing: a network
 * failure is "try again", but an authentication failure is "sign in", and showing the second for
 * the first wastes the user's time. Two sources are checked because they are the two that can
 * actually say it — [GitHubException] carries the HTTP status it failed on, and JGit wraps its
 * transport errors with a sentence that quotes the status a few causes deep.
 *
 * False positives are cheap (a sign-in card nobody needed) and false negatives are not (a user
 * staring at a retry that cannot succeed), so the sentence markers are deliberately loose — but
 * they are matched as words, because "401" inside a port or a millisecond count is not a status.
 */
internal fun needsAuthentication(error: Throwable): Boolean {
    val marker = Regex(
        """(?i)(\b401\b|\b403\b|\bunauthorized\b|\bnot authorized\b|\bauthentication\b|""" +
            """\b(bad|invalid) credentials\b|\binvalid username or password\b|""" +
            """\baccess denied\b)""",
    )
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 6) {
        if (current is GitHubException && current.code in AUTH_STATUS) return true
        val message = current.message
        if (message != null && marker.containsMatchIn(message)) return true
        val next = current.cause
        if (next === current) break
        current = next
        depth++
    }
    return false
}

/** The statuses that mean "whoever asked is not recognised", as opposed to "go away". */
private val AUTH_STATUS = setOf(401, 403)
