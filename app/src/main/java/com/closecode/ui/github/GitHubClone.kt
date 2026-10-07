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
 */
class GitHubClone {

    fun clone(url: String, token: String, into: File): Result<Unit> = runCatching {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://")) {
            "Only https:// remotes are supported in this build."
        }
        val existing = into.list()
        require(existing == null || existing.isEmpty()) {
            "\"${into.name}\" already exists and is not an empty directory."
        }
        into.parentFile?.mkdirs()
        Git.cloneRepository()
            .setURI(trimmed)
            .setDirectory(into)
            .setCredentialsProvider(tokenCredentials(token))
            .call()
            .use { }
    }
}
