package com.closecode.ui.github

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish

/**
 * Talking to a GitHub remote from the repository on disk: link, push, pull.
 *
 * Only HTTPS remotes are handled, authenticated with the Device Flow token as an
 * x-access-token basic-auth pair. Nothing here stores credentials in the repository —
 * the token stays in [TokenStore] — so a `.git/config` left behind on shared storage
 * cannot leak access.
 */
class GitHubRemote(private val root: File) {

    /** The remote's URL, or null when the project has never been linked. */
    fun originUrl(): String? = runCatching {
        Git.open(root).use { git ->
            git.repository.config.getString("remote", ORIGIN, "url")
        }
    }.getOrNull()

    /**
     * Points `origin` at [url] and gives the current branch an upstream, so the first push
     * leaves the repository in a state where a later pull knows what to pull.
     */
    fun link(url: String): Result<Unit> = runCatching {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://")) {
            "Only https:// remotes are supported in this build."
        }
        Git.open(root).use { git ->
            val config = git.repository.config
            config.setString("remote", ORIGIN, "url", trimmed)
            config.setString("remote", ORIGIN, "fetch", "+refs/heads/*:refs/remotes/origin/*")
            config.setString("remote", ORIGIN, "mirror", "false")
            git.repository.branch?.let { branch ->
                config.setString("branch", branch, "remote", ORIGIN)
                config.setString("branch", branch, "merge", "refs/heads/$branch")
            }
            config.save()
        }
    }

    /** Sends the current branch to the remote. Returns a sentence describing what happened. */
    fun push(token: String): Result<String> = runCatching {
        Git.open(root).use { git ->
            val branch = git.repository.branch
                ?: throw GitHubException("There is no branch to push yet. Make a commit first.")
            val result = git.push()
                .setRemote(ORIGIN)
                .setCredentialsProvider(tokenCredentials(token))
                .call()
            val summary = summarise(result.flatMap { it.remoteUpdates })

            // Record the upstream now that the branch exists on both sides, so a later pull
            // knows which remote branch to take.
            val config = git.repository.config
            config.setString("branch", branch, "remote", ORIGIN)
            config.setString("branch", branch, "merge", "refs/heads/$branch")
            config.save()
            summary
        }
    }

    /** Brings the current branch up to date from the remote. */
    fun pull(token: String): Result<String> = runCatching {
        Git.open(root).use { git ->
            val result = git.pull()
                .setCredentialsProvider(tokenCredentials(token))
                .call()
            val failures = result.mergeResult?.let { merge ->
                if (merge.mergeStatus.isSuccessful) {
                    if (merge.newHead == null) "Already up to date."
                    else "Merged ${merge.newHead.name.take(7)}."
                } else {
                    throw GitHubException(
                        "The pull needs a merge this app cannot finish: ${merge.mergeStatus.name}. " +
                            "Resolve it in the editor first.",
                    )
                }
            } ?: "Already up to date."
            failures
        }
    }

    private fun summarise(updates: List<RemoteRefUpdate>): String {
        if (updates.isEmpty()) return "Nothing to push."
        val rejected = updates.filter { it.status != RemoteRefUpdate.Status.OK }
        if (rejected.isNotEmpty()) {
            val detail = rejected.joinToString("; ") { update ->
                "${update.remoteName}: ${update.message ?: update.status.name}"
            }
            throw GitHubException("Push rejected — $detail")
        }
        return if (updates.size == 1) "Pushed ${updates.first().remoteName}." else "Pushed ${updates.size} refs."
    }

    private fun tokenCredentials(token: String): CredentialsProvider =
        object : CredentialsProvider() {
            override fun isInteractive(): Boolean = false

            override fun supports(vararg items: CredentialItem): Boolean = items.all {
                it is CredentialItem.Username || it is CredentialItem.Password
            }

            override fun get(uri: URIish?, vararg items: CredentialItem): Boolean {
                for (item in items) {
                    when (item) {
                        is CredentialItem.Username -> item.value = TOKEN_USER
                        // Password stores itself as a char[] (it extends CharArrayType), so the
                        // token is handed over in the shape the item actually holds.
                        is CredentialItem.Password -> item.setValue(token.toCharArray())
                        else -> return false
                    }
                }
                return true
            }
        }

    private companion object {
        const val ORIGIN = "origin"
        const val TOKEN_USER = "x-access-token"
    }
}
