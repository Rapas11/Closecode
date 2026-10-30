package com.closecode.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.build.PlatformStore
import com.closecode.build.SdkPlatforms
import com.closecode.build.SigningKeySource
import com.closecode.build.SigningStore
import com.closecode.ai.OpenCode
import com.closecode.ai.OpenCodeAiConfig
import com.closecode.ai.OpenCodeDefaults
import com.closecode.ai.OpenCodeModelChoice
import com.closecode.ai.OpenCodeModelChoices
import com.closecode.ai.OpenCodePhase
import com.closecode.rememberProjectRepository
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.closecode.ui.github.TokenStore
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LicensesText = """
Closecode is built on open-source software.

Kotlin and the Kotlin compiler are licensed under the Apache License 2.0. The Kotlin standard library and reflection libraries carry the same license.

The Android SDK platform files are used under the Android SDK License. The AndroidIDE build-tools archive is distributed by the AndroidIDE project; its aapt2 is used under the terms of that distribution.

The AndroidX, Jetpack Compose and Material libraries are licensed under the Apache License 2.0.

JGit is licensed under the Eclipse Distribution License 1.0 and the Eclipse Public License 1.0.

The R8 shrinker and the ECJ compiler are distributed under their respective open-source licenses. The APK signing library is licensed under the Apache License 2.0.

Gson is licensed under the Apache License 2.0. The XZ library is in the public domain. The Termux shell packages retain their own upstream licenses.

Full license texts are available from each project's source distribution.
""".trimIndent()

internal val PrivacyText = """
Closecode keeps your projects, settings and signing keys on this phone. It does not create an account and does not send your source code anywhere by default.

GitHub: when you connect an account, the access token is stored in Android Keystore-backed encrypted preferences. Git operations send the token only to GitHub over HTTPS. Disconnecting removes it.

AI: the AI page runs OpenCode locally. Prompts and project files are sent only to the provider you configure inside OpenCode. Closecode does not proxy or retain them. The AI settings page edits OpenCode's own configuration file; it applies when OpenCode starts again.

Build tools and SDK packages are downloaded directly from Google and the AndroidIDE project. Download requests reveal your IP address and user agent to those servers, as any download does. No analytics or crash-reporting service is contacted.

Debug logs and crash reports stay in app-private storage. Sharing a report is an explicit action; nothing is uploaded automatically.

Removing the app removes its private storage, including projects and signing keys. Exported APKs and any files you copied elsewhere are not affected.
""".trimIndent()

@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(c.bg)
                .drawBehind {
                    drawLine(
                        color = c.ln,
                        start = Offset(0f, size.height - 0.5f),
                        end = Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f,
                    )
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = c.mu, fontSize = 26.sp, fontFamily = Sans)
            }
            Text(
                text = title,
                color = c.tx,
                fontSize = 18.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsInfoPage(title: String, body: String, onBack: () -> Unit) {
    SettingsPageScaffold(title = title, onBack = onBack) {
        body.split("\n\n").forEach { paragraph ->
            Text(
                text = paragraph,
                color = LocalPageColors.current.tx,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontFamily = Sans,
            )
        }
    }
}

@Composable
private fun PageCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content,
    )
}

@Composable
private fun PageAction(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Text(
        text = label,
        color = if (danger) c.red else c.ac,
        fontSize = 13.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
internal fun CredentialsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val tokenStore = remember(context) { TokenStore(context) }
    val signingStore = remember(context) { SigningStore(context) }
    var token by remember { mutableStateOf(tokenStore.read()) }
    var signing by remember { mutableStateOf(signingStore.selectedId()) }
    SettingsPageScaffold(title = "Credentials", onBack = onBack) {
        PageCard {
            Text("GitHub", color = c.tx, fontSize = 14.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold)
            Text(
                if (token == null) "Not connected" else "Access token stored securely",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
            )
            if (token != null) {
                PageAction("Disconnect GitHub", danger = true) {
                    tokenStore.clear()
                    token = null
                }
            }
        }
        PageCard {
            Text("Signing", color = c.tx, fontSize = 14.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold)
            Text(
                text = signingStore.allKeys().firstOrNull { it.id == signing }?.label
                    ?: "Debug key",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
            )
            Text(
                "Passwords and private keys stay in app-private storage.",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
        }
    }
}

@Composable
internal fun SdkManagerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val store = remember(context) {
        PlatformStore(context, SharedPreferencesKeyValueStore(context))
    }
    val scope = rememberCoroutineScope()
    var license by remember { mutableStateOf(store.licenseAccepted()) }
    var installed by remember { mutableStateOf(store.installedPlatforms().toSet()) }
    var buildTools by remember { mutableStateOf(store.buildToolsAapt2() != null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<Int?>(null) }

    fun install(api: Int) {
        busy = "Android $api"
        progress = null
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.installPlatform(api) { done, total ->
                    scope.launch { progress = done to total }
                }
            }
            installed = store.installedPlatforms().toSet()
            busy = null
            progress = null
            message = result.exceptionOrNull()?.message
                ?: "Android $api installed."
        }
    }

    fun installTools() {
        busy = "Build tools"
        progress = null
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.installBuildTools { done, total ->
                    scope.launch { progress = done to total }
                }
            }
            buildTools = store.buildToolsAapt2() != null
            busy = null
            progress = null
            message = result.exceptionOrNull()?.message ?: "Build tools installed."
        }
    }

    SettingsPageScaffold(title = "SDK Manager", onBack = onBack) {
        Text(
            "Platforms are downloaded from Google. Build tools come from AndroidIDE; the " +
                "bundled aapt2 stays available as a fallback.",
            color = c.mu,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )
        if (!license) {
            PageCard {
                Text(
                    "The Android SDK License must be accepted before a platform can be installed.",
                    color = c.tx,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
                PageAction("Accept Android SDK License") {
                    store.acceptLicense()
                    license = true
                    pendingInstall?.let { api ->
                        pendingInstall = null
                        install(api)
                    }
                }
            }
        }
        SdkPlatforms.forEach { platform ->
            PageCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Android ${platform.api}",
                            color = c.tx,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (platform.api in installed) "Installed · ${platform.fileName}"
                            else humanBytes(platform.sizeBytes),
                            color = c.mu,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                        )
                    }
                    if (platform.api in installed) {
                        PageAction("Remove", danger = true) {
                            store.removePlatform(platform.api)
                            installed = store.installedPlatforms().toSet()
                        }
                    } else {
                        PageAction(if (busy == "Android ${platform.api}") "Installing…" else "Install") {
                            if (busy != null) return@PageAction
                            if (!license) pendingInstall = platform.api else install(platform.api)
                        }
                    }
                }
            }
        }
        PageCard {
            Text(
                "AndroidIDE build-tools 34.0.4",
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (buildTools) "Installed · aapt2 available"
                else "Not installed · bundled aapt2 is used",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            if (buildTools) {
                PageAction("Remove", danger = true) {
                    store.removeBuildTools()
                    buildTools = false
                }
            } else {
                PageAction(if (busy == "Build tools") "Installing…" else "Install build tools") {
                    if (busy == null) installTools()
                }
            }
        }
        val current = busy
        if (current != null) {
            val p = progress
            Text(
                text = if (p == null || p.second <= 0) "$current…"
                else "$current · ${humanBytes(p.first)} of ${humanBytes(p.second)}",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Mono,
            )
        }
        message?.let {
            Text(it, color = c.mu, fontSize = 12.sp, fontFamily = Sans)
        }
    }
}

@Composable
internal fun SigningKeysPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val store = remember(context) { SigningStore(context) }
    var keys by remember { mutableStateOf(store.allKeys()) }
    var selected by remember { mutableStateOf(store.selectedId()) }
    var message by remember { mutableStateOf<String?>(null) }
    var importBytes by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var alias by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmGenerate by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "keystore"
                importBytes = bytes to name
                alias = name.substringBeforeLast('.')
            } else {
                message = "That file could not be read."
            }
        }
    }

    fun refresh() {
        keys = store.allKeys()
        selected = store.selectedId()
    }

    SettingsPageScaffold(title = "Signing keys", onBack = onBack) {
        keys.forEach { key ->
            PageCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            key.label,
                            color = c.tx,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when (key.source) {
                                SigningKeySource.Debug -> "Debug · generated on this phone"
                                SigningKeySource.Generated -> "Release · device-bound"
                                SigningKeySource.Imported -> "Imported · exportable"
                            },
                            color = c.mu,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Text(
                            key.subject,
                            color = c.mu,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                            maxLines = 2,
                        )
                    }
                    Text(
                        if (key.id == selected) "Selected" else "Use",
                        color = if (key.id == selected) c.ac else c.mu,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                store.select(key.id)
                                refresh()
                                message = "${key.label} selected."
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                when (key.source) {
                    SigningKeySource.Debug -> PageAction("Reset debug key") {
                        store.resetDebug().onFailure { message = it.message }
                        refresh()
                    }
                    SigningKeySource.Generated -> PageAction("Delete generated key", danger = true) {
                        store.deleteGenerated()
                        refresh()
                    }
                    SigningKeySource.Imported -> PageAction("Delete", danger = true) {
                        store.deleteImported(key.id)
                        refresh()
                    }
                }
            }
        }
        PageCard {
            Text(
                "Import a release key",
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "PKCS#12 or JKS. The password is encrypted with the Android Keystore.",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            PageAction("Choose keystore file") { picker.launch(arrayOf("*/*")) }
        }
        PageCard {
            Text(
                "Generate a release key",
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Generated keys stay on this phone and cannot be exported for Play Store upload.",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            PageAction("Generate release key") { confirmGenerate = true }
        }
        message?.let { Text(it, color = c.mu, fontSize = 12.sp, fontFamily = Sans) }
    }

    val pending = importBytes
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { importBytes = null; password = "" },
            title = { Text("Import signing key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(pending.second, color = c.mu, fontSize = 12.sp, fontFamily = Mono)
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Keystore password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.importKey(
                            bytes = pending.first,
                            originalName = pending.second,
                            alias = alias,
                            password = password.toCharArray(),
                        ).onSuccess {
                            message = "Imported ${it.label}."
                            refresh()
                        }.onFailure {
                            message = it.message ?: "The key could not be imported."
                        }
                        importBytes = null
                        password = ""
                    },
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { importBytes = null; password = "" }) { Text("Cancel") }
            },
        )
    }

    if (confirmGenerate) {
        AlertDialog(
            onDismissRequest = { confirmGenerate = false },
            title = { Text("Generate release key?") },
            text = {
                Text(
                    "This key is generated inside the Android Keystore and cannot be exported. " +
                        "Use it to sign APKs installed on this phone, not for a Play Store upload.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmGenerate = false
                        store.generateRelease()
                            .onSuccess { message = "Generated ${it.label}." }
                            .onFailure { message = it.message ?: "The key was not generated." }
                        refresh()
                    },
                ) { Text("Generate") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGenerate = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun ShellToolsPage(
    onBack: () -> Unit,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val store = remember(context) {
        ShellToolsStore(context, ShellToolsManifest.load(context))
    }
    val manifest = remember(context) { ShellToolsManifest.load(context) }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf(0L) }
    var packages by remember { mutableStateOf(emptyList<String>()) }
    var installing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ShellToolsStore.Progress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) {
            Triple(store.isReady(), store.installedBytes(), store.installedPackages())
        }
        ready = snapshot.first
        bytes = snapshot.second
        packages = snapshot.third
        loaded = true
    }

    fun install() {
        if (installing) return
        installing = true
        error = null
        progress = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.install { update ->
                    scope.launch { progress = update }
                }
            }
            installing = false
            progress = null
            refresh()
            if (!result.ok) {
                error = result.error ?: "The shell tools did not install."
            } else {
                onChanged()
            }
        }
    }

    fun remove() {
        if (removing) return
        removing = true
        error = null
        scope.launch {
            val removed = withContext(Dispatchers.IO) { store.remove() }
            removing = false
            refresh()
            if (!removed) error = "The shell tools could not be removed."
            onChanged()
        }
    }

    LaunchedEffect(store) { refresh() }

    SettingsPageScaffold(title = "Shell tools", onBack = onBack) {
        Text(
            text = "Android's shell is real, but it only includes Android's own commands. " +
                "This optional pack adds the Termux builds of git, curl, Python, Node, zip " +
                "and jq under CloseCode's private storage. It does not need root.",
            color = c.mu,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )

        when {
            !loaded -> PageCard {
                Text("Checking shell tools…", color = c.mu, fontSize = 13.sp, fontFamily = Sans)
            }

            installing -> PageCard {
                val current = progress
                Text(
                    text = current?.message ?: "Preparing the download…",
                    color = c.tx,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
                val fraction = if (current != null && current.totalBytes > 0) {
                    (current.doneBytes.toFloat() / current.totalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.chipBg),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.ac),
                    )
                }
                if (current != null && current.totalBytes > 0) {
                    Text(
                        text = "${humanSize(current.doneBytes)} of " +
                            humanSize(current.totalBytes),
                        color = c.mu,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                Text(
                    text = "Keep CloseCode open while the packages download.",
                    color = c.mu,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }

            removing -> PageCard {
                Text("Removing shell tools…", color = c.mu, fontSize = 13.sp, fontFamily = Sans)
            }

            ready -> {
                PageCard {
                    Text(
                        text = "Installed",
                        color = c.success,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${humanSize(bytes)} on this phone",
                        color = c.mu,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                    if (packages.isNotEmpty()) {
                        Text(
                            text = packages.joinToString(" · "),
                            color = c.mu,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = Sans,
                        )
                    }
                    Text(
                        text = "New terminal tabs and OpenCode shells use these commands. " +
                            "Close and reopen an existing shell to pick them up.",
                        color = c.mu,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                }
                PageCard {
                    PageAction("Remove shell tools", danger = true) {
                        confirmRemove = true
                    }
                }
            }

            else -> PageCard {
                Text(
                    text = "Not installed",
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The download includes: " +
                        manifest.roots.joinToString(", ").ifBlank { "the Termux command pack" } +
                        ".",
                    color = c.mu,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = "Download size depends on the current Termux packages. " +
                        "A Wi-Fi connection is recommended.",
                    color = c.mu,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                PageAction("Download shell tools") { install() }
            }
        }

        error?.let { message ->
            PageCard {
                Text(
                    text = message,
                    color = c.red,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = Sans,
                )
                if (!installing) PageAction("Try again") { install() }
            }
        }

        if (confirmRemove) {
            PageCard {
                Text(
                    text = "Remove the downloaded shell tools? Projects and project files " +
                        "are not touched.",
                    color = c.tx,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = Sans,
                )
                if (removing) {
                    Text("Removing…", color = c.mu, fontSize = 12.sp, fontFamily = Sans)
                } else {
                    PageAction("Remove everything", danger = true) {
                        confirmRemove = false
                        remove()
                    }
                    PageAction("Cancel") { confirmRemove = false }
                }
            }
        }

        PageCard {
            Text(
                text = "This is a Termux package pack, not an Android system install. " +
                    "There is no apt and no root. pip and npm can install packages that " +
                    "do not need extra native libraries; Android-only binaries may not run.",
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
        }
    }
}

private fun humanBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else "%.1f %s".format(value, units[unit])
}

/**
 * The AI page, replacing the "coming soon" placeholder.
 *
 * Everything here edits OpenCode's own configuration file, which OpenCode reads at its next
 * start. Nothing talks to the network: the model list is OpenCode's own durable cache, and
 * the page says when the AI has to be started once for the list to fill.
 */
@Composable
internal fun AiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val repository = rememberProjectRepository()

    var status by remember { mutableStateOf(OpenCode.state.value) }
    var config by remember { mutableStateOf(JsonObject()) }
    var choices by remember { mutableStateOf<List<OpenCodeModelChoice>>(emptyList()) }
    var source by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf<List<ProjectRecord>>(emptyList()) }
    var skills by remember { mutableStateOf<List<AiSettings.SkillFile>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EditingSkill?>(null) }
    var wiping by remember { mutableStateOf(false) }
    var choiceOpen by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val configHome = com.closecode.ai.OpenCodeConfig.directory(
                com.closecode.ai.OpenCode.home(context).let { File(it, "config") },
            )
            val file = com.closecode.ai.OpenCodeConfig.file(configHome)
            val json = com.closecode.ai.OpenCodeAiConfig.read(file) ?: JsonObject()
            val loaded = OpenCodeModelChoices.load(context, OpenCode.state.value.url)
            val list = repository.listProjects()
            val dirs = list.mapNotNull { record ->
                runCatching { repository.projectDir(record.slug) }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                config = json
                choices = loaded.choices
                source = loaded.source
                status = OpenCode.state.value
                projects = list
                skills = AiSettings.skillFiles(dirs, configHome)
                message = null
            }
        }
    }

    fun save(next: JsonObject) {
        scope.launch(Dispatchers.IO) {
            val configHome = com.closecode.ai.OpenCodeConfig.directory(
                File(com.closecode.ai.OpenCode.home(context), "config"),
            )
            val file = com.closecode.ai.OpenCodeConfig.file(configHome)
            val written = com.closecode.ai.OpenCodeAiConfig.save(file, next)
            withContext(Dispatchers.Main) {
                if (written) {
                    config = next
                    message = "Saved. OpenCode applies it at its next start."
                } else {
                    message = "The file could not be written."
                }
            }
        }
    }

    fun writeSkill(text: String) {
        val project = projects.firstOrNull()?.let { record ->
            runCatching { repository.projectDir(record.slug) }.getOrNull()
        }
        if (project == null) {
            message = "Create a project first; the skill has nowhere to live."
            return
        }
        val problem = AiSettings.skillProblem(text)
        val id = AiSettings.skillName(text)
        val targetFolder = if (editing?.id?.isNotBlank() == true) editing!!.id else id
        val target = AiSettings.skillDirectory(project, targetFolder)
        if (problem == null && !target.isDirectory) target.mkdirs()
        if (problem == null) {
            scope.launch(Dispatchers.IO) {
                val written = runCatching {
                    File(target, "SKILL.md").writeText(text)
                    true
                }.getOrDefault(false)
                withContext(Dispatchers.Main) {
                    if (written) {
                        editing = null
                        reload()
                    } else message = "The skill file could not be written."
                }
            }
        } else message = problem
    }

    LaunchedEffect(Unit) { reload() }

    if (editing != null) {
        SkillEditor(
            skill = editing!!,
            projects = projects,
            onBack = { editing = null },
            onSave = ::writeSkill,
        )
        return
    }

    SettingsPageScaffold(title = "AI", onBack = onBack) {
        PageCard {
            Text(
                text = "OpenCode ${OpenCodeDefaults.VERSION}",
                color = c.tx,
                fontSize = 16.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Server: ${phaseLabel(status.phase)} — " +
                    when {
                        source == "server" -> "model list live from the server"
                        source == "cache" -> "model list from OpenCode's cache"
                        else -> "start the AI once; the model list fills itself"
                    },
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            Text(
                text = "Everything here is OpenCode's own configuration file. It applies when " +
                    "the AI starts again, not mid-session.",
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
        }

        PageCard {
            Text(
                text = "Default model",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            val current = OpenCodeAiConfig.model(config)
            Text(
                text = if (current.isBlank()) "Not chosen (OpenCode picks one)" else current,
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Mono,
            )
            if (choices.isEmpty()) {
                Text(
                    text = "No models known yet. Start the AI once from the editor's AI tab.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            } else {
                PageAction(if (choiceOpen) "Hide the list" else "Choose a model") {
                    choiceOpen = !choiceOpen
                }
                if (choiceOpen) {
                    choices.forEach { choice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    choiceOpen = false
                                    save(OpenCodeAiConfig.withModel(config, choice.qualified))
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = choice.qualified,
                                color = if (choice.qualified == current) c.ac else c.tx,
                                fontSize = 13.sp,
                                fontFamily = Mono,
                            )
                        }
                    }
                    if (current.isNotBlank()) {
                        PageAction("Clear the choice") {
                            save(OpenCodeAiConfig.withModel(config, ""))
                        }
                    }
                }
            }
        }

        PageCard {
            Text(
                text = "Agent permissions",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tap a row to walk Default → Ask → Deny → Default. Untouched tools keep " +
                    "OpenCode's own default, which allows most and asks for what is not safe.",
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            OpenCodeAiConfig.TOOLS.forEach { tool ->
                val action = OpenCodeAiConfig.permission(config, tool.key)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            save(
                                OpenCodeAiConfig.withPermissionCycled(config, tool.key),
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tool.label,
                        color = c.tx,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = AiSettings.actionLabel(action),
                        color = when (AiSettings.actionLabel(action)) {
                            "Deny" -> c.red
                            "Ask" -> c.ac
                            else -> c.mu
                        },
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        PageCard {
            Text(
                text = "Skills",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Reusable instructions OpenCode can load by name. They live in the " +
                    "projects' `.opencode/skills` and in the app's own folder. Tap to read " +
                    "and edit.",
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            if (skills.isEmpty()) {
                Text("No skills yet.", color = c.mu, fontSize = 12.sp, fontFamily = Sans)
            } else {
                skills.forEach { skill ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    val text = runCatching { skill.file.readText() }
                                        .getOrDefault("")
                                    withContext(Dispatchers.Main) {
                                        editing = EditingSkill(skill.id, skill.file, text)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = skill.id,
                            color = c.tx,
                            fontSize = 13.sp,
                            fontFamily = Mono,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                skill.file.path.contains(File.separator + ".opencode" +
                                    File.separator + "skills") -> "project"
                                else -> "app"
                            },
                            color = c.mu,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                }
            }
            PageAction("New skill") {
                editing = EditingSkill("", null, AiSettings.skillSkeleton())
            }
        }

        PageCard {
            Text(
                text = "Danger zone",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Erase the AI's own data: every stored session, the OpenCode home " +
                    "directory and its cache. The provider sign-ins go too. Projects and " +
                    "their files are not touched.",
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            PageAction("Erase AI data", danger = true) { wiping = true }
        }

        message?.let { note ->
            Text(
                text = note,
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
        }
    }

    if (wiping) {
        AlertDialog(
            onDismissRequest = { wiping = false },
            title = { Text("Erase AI data?") },
            text = {
                Text(
                    "OpenCode's own home is removed: sessions, cache and the provider sign-ins. " +
                        "Projects and their files are untouched.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        wiping = false
                        OpenCode.wipeHome(context)
                        reload()
                    },
                ) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { wiping = false }) { Text("Cancel") }
            },
        )
    }
}

/** One skill the page has open: id, file on disk (empty until it has been saved) and body. */
private data class EditingSkill(val id: String, val file: File?, val text: String)

@Composable
private fun SkillEditor(
    skill: EditingSkill,
    projects: List<ProjectRecord>,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    val c = LocalPageColors.current
    var text by remember(skill.id) { mutableStateOf(skill.text) }
    var preview by remember(skill.id) { mutableStateOf(false) }
    val editorTarget = when {
        skill.file != null -> skill.file.path
        projects.isEmpty() -> "your first project"
        else -> projects.first().name
    }
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(c.bg)
                .drawBehind {
                    drawLine(
                        color = c.ln,
                        start = Offset(0f, size.height - 0.5f),
                        end = Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f,
                    )
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = c.mu, fontSize = 26.sp, fontFamily = Sans)
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    text = "Skill",
                    color = c.tx,
                    fontSize = 18.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = editorTarget,
                    color = c.mu,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }
        }
        if (preview) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Name: ${AiSettings.skillName(text).ifBlank { "—" }}",
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Description: ${AiSettings.skillDescription(text).ifBlank { "—" }}",
                    color = c.tx,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = text.substringAfter("-->", text),
                    color = c.mu,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = Sans,
                )
            }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = c.tx,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Save",
                color = c.ac,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSave(text) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                text = if (preview) "Write" else "Preview",
                color = c.tx,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { preview = !preview }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Text(
            text = AiSettings.skillProblem(text)
                ?: "Valid. Folder: ${AiSettings.skillName(text).ifBlank { "still empty" }}",
            color = if (AiSettings.skillProblem(text) == null) c.mu else c.red,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

private fun phaseLabel(phase: OpenCodePhase): String = when (phase) {
    OpenCodePhase.Idle -> "off"
    OpenCodePhase.Starting -> "starting"
    OpenCodePhase.Running -> "running"
    OpenCodePhase.ConnectionLost -> "restarting"
    OpenCodePhase.Failed -> "failed"
}
