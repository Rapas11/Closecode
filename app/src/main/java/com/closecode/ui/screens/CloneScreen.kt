package com.closecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.CcTextField
import com.closecode.ui.components.EmptyState
import com.closecode.ui.components.ErrorCard
import com.closecode.ui.components.LoadingList
import com.closecode.ui.components.OneLine
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.components.SecLabel
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType
import com.closecode.ui.theme.tint

/** One repository row in screen 04. Real name, real clone URL, real updated time. */
data class RepoUi(
    val fullName: String,
    val cloneUrl: String,
    val updatedText: String,
)

/** The four states the repository list can be in. */
sealed interface RepoListState {
    data object Loading : RepoListState
    data object NotConnected : RepoListState
    data class Error(val message: String) : RepoListState
    data class Loaded(val repos: List<RepoUi>) : RepoListState
}

/**
 * Screen 04 — Clone a repository.
 *
 * The link field works without a connection; the list below needs the signed-in account.
 * Picking a row only fills the two fields — the Clone button starts the real download
 * (screen 05).
 */
@Composable
fun CloneScreen(
    origin: String?,
    url: String,
    onUrl: (String) -> Unit,
    urlError: String?,
    folder: String,
    onFolder: (String) -> Unit,
    folderError: String?,
    repos: RepoListState,
    query: String = "",
    onQuery: (String) -> Unit = {},
    onPickRepo: (RepoUi) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onClone: () -> Unit,
    onBack: () -> Unit,
) {
    val c = CcTheme.colors
    var searching by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.content)) {
        ScreenHeader(origin, "Clone a repository", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            CcTextField(
                value = url,
                onValueChange = onUrl,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 10.dp),
                label = "Repository link",
                hint = "https://github.com/owner/repo",
                mono = true,
                error = urlError,
            )

            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                CcTextField(
                    value = folder,
                    onValueChange = onFolder,
                    label = "Save in folder",
                    mono = true,
                    error = folderError,
                )
                if (folderError == null) {
                    Text(
                        "Filled in from the link. You can change it.",
                        modifier = Modifier.padding(top = 5.dp),
                        style = CcType.caption.copy(fontSize = 11.5.sp),
                        color = c.muted,
                    )
                }
            }

            PrimaryButton(
                text = "Clone",
                onClick = onClone,
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                icon = "down",
                enabled = url.isNotBlank() && folder.isNotBlank() && folderError == null,
            )

            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SecLabel("Your GitHub repositories", start = 18.dp, top = 16.dp, bottom = 6.dp)
                }
                // The app already let you filter a long repository list; the filter stays,
                // as a small icon on the section line.
                Box(
                    Modifier
                        .clickable {
                            searching = !searching
                            if (!searching) onQuery("")
                        }
                        .padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                ) {
                    CcIcon(if (searching) "x" else "search", c.faint, 16.dp, 1.8f)
                }
            }

            if (searching) {
                CcTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    hint = "Find a repository",
                    leadingIcon = "search",
                )
            }

            when (repos) {
                RepoListState.Loading -> LoadingList(3)

                RepoListState.NotConnected -> EmptyState(
                    icon = "gh",
                    title = "GitHub is not connected",
                    message = "Sign in to see your repositories, or paste a link above.",
                    actionText = "Sign in",
                    onAction = onSignIn,
                )

                is RepoListState.Error -> ErrorCard(repos.message, onRetry)

                is RepoListState.Loaded -> {
                    val shown = repos.repos.filter {
                        query.isBlank() || it.fullName.contains(query, ignoreCase = true)
                    }
                    if (repos.repos.isEmpty()) {
                        EmptyState(
                            icon = "folder",
                            title = "No repositories",
                            message = "Paste a link above.",
                        )
                    } else if (shown.isEmpty()) {
                        EmptyState(
                            icon = "search",
                            title = "No repositories match",
                            message = "Try another name.",
                        )
                    } else {
                        shown.forEachIndexed { i, repo ->
                            val tint = listOf(c.blue, c.green, c.yellow)[i % 3]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickRepo(repo) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tint.tint()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CcIcon("gh", tint, 18.dp, 1.8f)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    OneLine(
                                        repo.fullName,
                                        CcType.body.copy(fontWeight = FontWeight.SemiBold),
                                        c.text,
                                    )
                                    OneLine(
                                        repo.updatedText,
                                        CcType.caption.copy(fontSize = 11.5.sp),
                                        c.muted,
                                    )
                                }
                                CcIcon("cr", c.faint, 16.dp, 2.2f)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
