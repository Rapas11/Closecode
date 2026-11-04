package com.closecode.ui

import com.closecode.ui.github.GitHubUser

/**
 * The account line shown for the signed-in GitHub account.
 *
 * The two GitHub pills can only say that *a* token exists, so this string is the one
 * place that says who it belongs to — and it must never borrow a name it has not been told.
 */
internal fun accountLabel(account: GitHubUser?): String = when {
    account == null -> "Connected"
    account.name.isNullOrBlank() -> "@${account.login}"
    else -> "${account.name} · @${account.login}"
}
