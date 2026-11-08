package com.closecode.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.DesignBackButton
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.ide.DefaultTerminalCommands
import com.closecode.ui.ide.TERMINAL_COMMANDS_KEY
import com.closecode.ui.ide.decodeCommands
import com.closecode.ui.ide.encodeCommands
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

@Composable
private fun ExtraPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.mock.page)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesignBackButton(onClick = onBack)
            Text(
                text = title,
                color = c.mock.text,
                fontSize = 18.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ExtraCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.mock.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun ExtraLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LocalPageColors.current.mock.faint,
        fontSize = 10.5.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ExtraAction(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Text(
        text = label,
        color = if (danger) c.mock.danger else c.mock.accent,
        fontSize = 12.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
internal fun SavedCommandsPage(store: KeyValueStore, onBack: () -> Unit) {
    val c = LocalPageColors.current
    val commands = remember {
        mutableStateListOf<String>().apply {
            val saved = decodeCommands(store.getString(TERMINAL_COMMANDS_KEY, null))
            addAll(if (saved.isEmpty()) DefaultTerminalCommands else saved)
        }
    }
    var editorIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    fun persist(next: List<String>) {
        store.putString(TERMINAL_COMMANDS_KEY, encodeCommands(next))
    }

    ExtraPageScaffold("Saved commands", onBack) {
        Text(
            text = "Commands you can run from the terminal.",
            color = c.mock.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
        )
        ExtraCard {
            if (commands.isEmpty()) {
                Text(
                    text = "No saved commands.",
                    color = c.mock.muted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }
            commands.forEachIndexed { index, command ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = command,
                        color = c.mock.text,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ExtraAction("Edit") {
                        draft = command
                        editorIndex = index
                    }
                    ExtraAction("Delete", danger = true) {
                        commands.removeAt(index)
                        persist(commands.toList())
                    }
                }
            }
            Text(
                text = "+  Add command",
                color = c.mock.accent,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        draft = ""
                        adding = true
                    }
                    .padding(vertical = 6.dp),
            )
        }
        Text(
            text = "The plus button adds a command; the terminal keeps its own history separately.",
            color = c.mock.faint,
            fontSize = 11.sp,
            fontFamily = Sans,
            lineHeight = 15.sp,
        )
    }

    if (adding || editorIndex != null) {
        val title = if (adding) "Add command" else "Edit command"
        AlertDialog(
            onDismissRequest = {
                adding = false
                editorIndex = null
            },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Command") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = draft.trim()
                        if (value.isNotEmpty()) {
                            val index = editorIndex
                            if (index == null) {
                                commands.add(value)
                            } else {
                                commands[index] = value
                            }
                            persist(commands.toList())
                        }
                        adding = false
                        editorIndex = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        adding = false
                        editorIndex = null
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun SavedSignInsPage(
    login: String?,
    onBack: () -> Unit,
    onSignOut: (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    ExtraPageScaffold("Saved sign-ins", onBack) {
        Text(
            text = "Accounts this phone can use without signing in again.",
            color = c.mock.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
        )
        ExtraCard {
            if (login == null) {
                Text(
                    text = "No saved sign-ins.",
                    color = c.mock.muted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c.mock.grayBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = login.take(1).uppercase(),
                            color = c.mock.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = login,
                            color = c.mock.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "GitHub",
                            color = c.mock.muted,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                        )
                    }
                    if (onSignOut != null) {
                        ExtraAction("Remove", danger = true, onClick = onSignOut)
                    }
                }
            }
        }
        Text(
            text = "Removing a sign-in deletes its stored token from this phone.",
            color = c.mock.faint,
            fontSize = 11.sp,
            fontFamily = Sans,
        )
    }
}

@Composable
internal fun ProviderKeyPage(
    keySet: Boolean,
    authCommand: String?,
    onBack: () -> Unit,
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ExtraPageScaffold("Provider key", onBack) {
        ExtraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (keySet) "Provider key is set" else "Provider key is not set",
                        color = c.mock.text,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Used only by OpenCode.",
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                CcIcon(
                    if (keySet) CcIcons.Check else CcIcons.Lock,
                    if (keySet) c.mock.green else c.mock.faint,
                    20.dp,
                )
            }
        }
        Text(
            text = "The key is read by OpenCode from its own auth store and is never shown here.",
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 17.sp,
        )
        ExtraLabel("Set the key")
        ExtraCard {
            val command = authCommand
            if (command == null) {
                Text(
                    text = "Install the OpenCode runtime first. The auth command appears here once " +
                        "the program and its Linux libraries are on the phone.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
            } else {
                Text(
                    text = command,
                    color = c.mock.text,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    lineHeight = 16.sp,
                )
                ExtraAction(if (copied) "Copied" else "Copy command") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OpenCode auth", command))
                    copied = true
                }
                Text(
                    text = "Run this in the terminal, finish the provider sign-in, then return here.",
                    color = c.mock.faint,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
internal fun ProjectLocationPage(path: String, onBack: () -> Unit) {
    val c = LocalPageColors.current
    ExtraPageScaffold("Project location", onBack) {
        ExtraCard {
            Text(
                text = "Projects are stored at",
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            Text(
                text = path,
                color = c.mock.text,
                fontSize = 12.sp,
                fontFamily = Mono,
                lineHeight = 17.sp,
            )
        }
        Text(
            text = "Changing the project folder is not available in this build.",
            color = c.mock.faint,
            fontSize = 11.sp,
            fontFamily = Sans,
        )
    }
}
