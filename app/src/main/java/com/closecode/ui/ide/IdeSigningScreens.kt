package com.closecode.ui.ide

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.build.KeyFileEntry
import com.closecode.build.SigningKeyInfo
import com.closecode.build.SigningKeySource
import com.closecode.build.SigningStore
import com.closecode.ui.CcIcon
import com.closecode.ui.components.CcConfirmDialog
import com.closecode.ui.components.CcTextField
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun store(context: android.content.Context): SigningStore? =
    runCatching { SigningStore(context) }.getOrNull()

private fun releaseKey(signing: SigningStore?): SigningKeyInfo? =
    signing?.generatedInfo() ?: signing?.importedKeys()?.firstOrNull()

private fun ownerOf(subject: String): String =
    subject.split(',').firstOrNull { it.trim().startsWith("CN=") }
        ?.substringAfter("CN=")?.trim().orEmpty().ifBlank { "Unknown owner" }

private fun yearOf(millis: Long?): String =
    millis?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it)) } ?: "No expiry"

/** Screen 47: Signing keys, with no release key yet. */
@Composable
fun IdeSigningKeysScreen(
    onBack: () -> Unit,
    onCreateKey: () -> Unit,
    onImportKey: () -> Unit,
) {
    val c = LocalCcColors.current
    val context = LocalContext.current
    val signing = remember(context) { store(context) }
    val debug = remember(signing) { signing?.debugInfo() }
    val debugReady = !debug?.sha256.isNullOrBlank()
    BackHandler(onBack = onBack)
    IconScreenFrame("Project settings", "Signing", onBack) {
        Card20 {
            SigningCardHead(
                tint = c.teal,
                title = "Debug key",
                sub = if (debugReady) "Made by the app, automatically" else "Made when you first build",
                ok = debugReady,
            )
            IconNote(
                "Signs the builds you install on your own phone while testing. You do not create it. " +
                    "It cannot be used to publish to Google Play.",
            )
            if (debugReady) {
                Box(
                    Modifier.padding(top = 12.dp, bottom = 4.dp)
                        .fillMaxWidth().height(1.dp).background(c.toggleOff),
                )
                KvRow("SHA-256", debug?.sha256.orEmpty(), mono = true)
            }
        }
        Card20 {
            SigningCardHead(
                tint = c.amber,
                title = "Release key",
                sub = "Needed to publish",
                ok = false,
            )
            IconNote(
                "This is the real key of your app. Google Play only accepts updates signed with the same key, " +
                    "so make it once and keep a backup.",
            )
            Column(
                Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigButton(
                    text = "Create a new key",
                    onClick = onCreateKey,
                    modifier = Modifier.fillMaxWidth(),
                    icon = "plus",
                )
                BigButton(
                    text = "Import a key file",
                    onClick = onImportKey,
                    modifier = Modifier.fillMaxWidth(),
                    icon = "folder",
                    bg = c.panel,
                    fg = c.text2,
                )
            }
        }
        Text(
            text = "Keys stay on this phone, in the app's private storage. They are never uploaded. " +
                "You can import a key you made before (.jks, .keystore or .p12).",
            color = c.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SigningCardHead(
    tint: Color,
    title: String,
    sub: String,
    ok: Boolean,
) {
    val c = LocalCcColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon("lock", tint, 21.dp, 1.8f)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.text,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = sub,
                color = c.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        StatusPill(
            text = if (ok) "Ready" else "Not set",
            fg = if (ok) c.green else c.amber,
            bgAlpha = if (ok) 0.13f else 0.20f,
        )
    }
}

/** Screen 48: the release key that is configured. */
@Composable
fun IdeReleaseKeyScreen(
    onBack: () -> Unit,
    onReplace: () -> Unit,
    onRemoved: () -> Unit,
) {
    val c = LocalCcColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signing = remember(context) { store(context) }
    var key by remember(signing) { mutableStateOf(releaseKey(signing)) }
    var confirmRemove by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    if (confirmRemove) {
        CcConfirmDialog(
            title = "Remove release key?",
            message = "This project will fall back to the debug key. You cannot undo this.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemove = false
                val target = key ?: return@CcConfirmDialog
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (target.source == SigningKeySource.Generated) {
                            signing?.deleteGenerated()
                        } else {
                            signing?.deleteImported(target.id)
                        }
                    }
                    key = null
                    onRemoved()
                }
            },
            onDismiss = { confirmRemove = false },
            destructive = true,
        )
    }
    val current = key
    IconScreenFrame("Project settings", "Release key", onBack) {
        if (current == null) {
            Text(
                "No release key is configured.",
                color = c.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Card20 {
                SigningCardHead(
                    tint = c.green,
                    title = "Release key",
                    sub = "Created on this phone",
                    ok = true,
                )
                Box(
                    Modifier.padding(top = 12.dp, bottom = 4.dp)
                        .fillMaxWidth().height(1.dp).background(c.toggleOff),
                )
                KvRow("Alias", current.alias.ifBlank { current.label })
                KvRow("Valid until", yearOf(current.expiresAt))
                KvRow("Owner", ownerOf(current.subject))
                KvRow("SHA-256", current.sha256, mono = true)
                KvRow(
                    "Key file",
                    if (current.exportable) "${current.label}.jks" else "Android Keystore",
                    mono = true,
                )
            }
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigButton(
                    text = "Back up",
                    onClick = {
                        notice = if (current.exportable) {
                            "Use Files to copy ${current.label}.jks from the app's signing folder with your store password."
                        } else {
                            "This key is locked to this phone, so it has no exportable file."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = "up",
                    bg = c.field,
                    fg = c.text2,
                )
                BigButton(
                    text = "Replace",
                    onClick = onReplace,
                    modifier = Modifier.weight(1f),
                    icon = "refresh",
                    bg = c.field,
                    fg = c.text2,
                )
            }
            BigButton(
                text = "Remove from this phone",
                onClick = { confirmRemove = true },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth(),
                icon = "trash",
                bg = c.field,
                fg = c.red,
            )
            Text(
                "Back up this key now. If you lose it you cannot update your app on Google Play.",
                color = c.amber,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.amber.copy(alpha = 0.20f))
                    .padding(14.dp),
            )
        }
        if (notice != null) {
            IconNote(notice.orEmpty(), Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Screen 49: create a new release key. */
@Composable
fun IdeCreateKeyScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val c = LocalCcColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signing = remember(context) { store(context) }
    var alias by remember { mutableStateOf("release") }
    var name by remember { mutableStateOf("CloseCode") }
    var country by remember { mutableStateOf("US") }
    var years by remember { mutableStateOf(25) }
    var password by remember {
        mutableStateOf(
            buildString {
                val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%"
                repeat(18) { append(alphabet.random()) }
            },
        )
    }
    var rememberPassword by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val valid = alias.isNotBlank() && name.isNotBlank() && country.length == 2 && password.length >= 6
    BackHandler(onBack = onBack)
    IconScreenFrame("Signing", "Create a key", onBack, footer = {
        Column(Modifier.fillMaxWidth()) {
            error?.let {
                Text(
                    it,
                    color = c.red,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            BigButton(
                text = if (busy) "Creating…" else "Create key",
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            signing?.generateRelease(
                                displayName = name,
                                country = country,
                                years = years,
                                alias = alias,
                            ) ?: Result.failure(IllegalStateException("Signing is unavailable"))
                        }
                        busy = false
                        result.onSuccess { onCreated() }
                            .onFailure { error = it.message ?: "The key could not be created." }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = "check",
                enabled = valid && !busy,
            )
        }
    }) {
        Spacer(Modifier.height(4.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CcTextField(alias, { alias = it }, label = "Key alias", hint = "release", mono = true)
            CcTextField(name, { name = it }, label = "Your name", hint = "CloseCode")
            CcTextField(
                country,
                { country = it.take(2).uppercase() },
                label = "Country code",
                hint = "US",
                mono = true,
            )
        }
        IconSectionLabel("Valid for")
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(25, 30, 50).forEach { option ->
                com.closecode.ui.components.CcChip(
                    "$option years",
                    years == option,
                    onClick = { years = option },
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            CcTextField(
                password,
                { password = it },
                label = "Password",
                hint = "At least 6 characters",
                mono = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            IconNote(
                "Made for you. Change it if you want. You need it to use the key.",
                Modifier.padding(top = 5.dp),
            )
        }
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Remember the password on this phone",
                    color = c.text,
                    fontSize = 13.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Kept in the app's protected storage",
                    color = c.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            com.closecode.ui.components.CcToggle(
                checked = rememberPassword,
                onCheckedChange = { rememberPassword = it },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Screen 50: import a .jks / .keystore from the device. */
@Composable
fun IdeImportKeyScreen(
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val c = LocalCcColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signing = remember(context) { store(context) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var storePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<KeyFileEntry>>(emptyList()) }
    var selectedAlias by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val data = context.contentResolver.openInputStream(uri).use { it?.readBytes() }
                        ?: error("The file could not be read.")
                    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                    } ?: uri.lastPathSegment ?: "keystore.jks"
                    data to name
                }
            }
            loaded.onSuccess {
                bytes = it.first
                fileName = it.second
                entries = emptyList()
                selectedAlias = null
                storePassword = ""
                error = null
            }.onFailure { error = it.message }
        }
    }
    LaunchedEffect(bytes, storePassword) {
        val data = bytes ?: return@LaunchedEffect
        if (storePassword.isEmpty()) return@LaunchedEffect
        delay(450)
        checking = true
        val result = withContext(Dispatchers.IO) {
            signing?.inspect(data, storePassword.toCharArray())
                ?: Result.failure(IllegalStateException("Signing is unavailable"))
        }
        checking = false
        result.onSuccess {
            entries = it
            selectedAlias = it.firstOrNull()?.alias
            error = null
        }.onFailure {
            entries = emptyList()
            selectedAlias = null
            error = it.message ?: "Wrong password or not a key file"
        }
    }
    BackHandler(onBack = onBack)
    IconScreenFrame("Signing keys", "Import key", onBack, footer = {
        BigButton(
            text = "Import key",
            onClick = {
                val data = bytes ?: return@BigButton
                val alias = selectedAlias ?: return@BigButton
                busy = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        signing?.importKey(
                            bytes = data,
                            originalName = fileName,
                            alias = alias,
                            password = storePassword.toCharArray(),
                            keyAlias = alias,
                            keyPassword = keyPassword.ifBlank { storePassword }.toCharArray(),
                        ) ?: Result.failure(IllegalStateException("Signing is unavailable"))
                    }
                    busy = false
                    result.onSuccess { onImported() }
                        .onFailure { error = it.message ?: "The key could not be imported." }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = "down",
            enabled = !busy && bytes != null && selectedAlias != null,
        )
    }) {
        Text(
            "Choose a .jks or .keystore file from Files, then enter its store password.",
            color = c.muted,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )
        IconSectionLabel("Key file")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.field)
                    .clickable { picker.launch(arrayOf("application/octet-stream", "application/x-java-keystore", "*/*")) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                        .background(c.accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon("lock", c.accent, 18.dp, 2f)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = fileName.ifBlank { "Choose key file" },
                        color = c.text,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (bytes != null) "${bytes!!.size / 1024} KB" else "JKS or PKCS#12",
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontFamily = Sans,
                    )
                }
                CcIcon("folder", c.muted, 18.dp, 1.8f)
            }
            Spacer(Modifier.height(12.dp))
            CcTextField(
                storePassword,
                { storePassword = it },
                label = "Store password",
                hint = "Unlock the keystore",
                mono = true,
                error = error,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        if (bytes != null) {
            IconSectionLabel("Key inside")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (checking) {
                    Text("Unlocking...", color = c.muted, fontSize = 12.5.sp, fontFamily = Sans)
                } else if (entries.isEmpty()) {
                    Text(
                        "Enter the store password to list its keys.",
                        color = c.muted,
                        fontSize = 12.5.sp,
                        fontFamily = Sans,
                    )
                } else {
                    entries.forEach { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (entry.alias == selectedAlias) c.accent.copy(alpha = 0.10f) else c.field)
                                .border(
                                    if (entry.alias == selectedAlias) 1.dp else 0.dp,
                                    if (entry.alias == selectedAlias) c.accent else Color.Transparent,
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { selectedAlias = entry.alias }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(17.dp).clip(CircleShape)
                                    .border(2.dp, if (entry.alias == selectedAlias) c.accent else c.toggleOff, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (entry.alias == selectedAlias) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.alias, color = c.text, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                                Text(
                                    entry.sha256.take(23) + "…",
                                    color = c.muted,
                                    fontSize = 10.5.sp,
                                    fontFamily = Mono,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            IconSectionLabel("Key password")
            Column(Modifier.padding(horizontal = 16.dp)) {
                CcTextField(
                    keyPassword,
                    { keyPassword = it },
                    label = "Optional",
                    hint = "Only if it differs from the store password",
                    mono = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }
        if (error != null && entries.isEmpty()) {
            Text(
                error.orEmpty(),
                color = c.red,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
