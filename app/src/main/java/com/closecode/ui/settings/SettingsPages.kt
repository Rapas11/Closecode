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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.build.PlatformStore
import com.closecode.build.ProjectPins
import com.closecode.build.SdkPlatforms
import com.closecode.build.SigningKeySource
import com.closecode.build.SigningStore
import com.closecode.ai.OpenCode
import com.closecode.ai.OpenCodeDefaults
import com.closecode.device.control.AuditLog
import com.closecode.device.control.ConsentStore
import com.closecode.tools.GradleToolsManifest
import com.closecode.tools.GradleToolsStore
import com.closecode.tools.InstalledJdk
import com.closecode.tools.JdkStore
import com.closecode.tools.NativeToolsStore
import com.closecode.tools.OptionalPack
import com.closecode.tools.OptionalPacks
import com.closecode.tools.PythonPackRepair
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

The optional native tools use Android-native NDK and CMake archives distributed by the AndroidIDE project. The NDK and CMake retain their own upstream licenses.

The AndroidX, Jetpack Compose and Material libraries are licensed under the Apache License 2.0.

JGit is licensed under the Eclipse Distribution License 1.0 and the Eclipse Public License 1.0.

The R8 shrinker and the ECJ compiler are distributed under their respective open-source licenses. The APK signing library is licensed under the Apache License 2.0.

Gson is licensed under the Apache License 2.0. The XZ library is in the public domain. The Termux shell packages retain their own upstream licenses.

The downloadable icon packs keep their upstream licenses. Material Symbols is Copyright Google LLC and licensed under the Apache License 2.0. Lucide is licensed under the ISC License. Both packs are downloaded from the Closecode icon-pack host only when you tap Get.

Full license texts are available from each project's source distribution.
""".trimIndent()

internal val PrivacyText = """
Closecode keeps your projects, settings and signing keys on this phone. It does not create an account and does not send your source code anywhere by default.

GitHub: when you connect an account, the access token is stored in Android Keystore-backed encrypted preferences. Git operations send the token only to GitHub over HTTPS. Disconnecting removes it.

AI: the AI page runs OpenCode locally. Prompts and project files are sent only to the provider you configure inside OpenCode. Closecode does not proxy or retain them. The agent is set to work without asking: it may edit files and run commands on this phone by itself.

Build tools and SDK packages are downloaded directly from Google and the AndroidIDE project. Download requests reveal your IP address and user agent to those servers, as any download does. No analytics or crash-reporting service is contacted.

Debug logs and crash reports stay in app-private storage. Sharing a report is an explicit action; nothing is uploaded automatically.

Removing the app removes its private storage, including projects and signing keys. Exported APKs and any files you copied elsewhere are not affected.
""".trimIndent()

@Composable
internal fun SettingsPageScaffold(
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
            com.closecode.ui.components.DesignBackButton(onClick = onBack)
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
internal fun PageCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.mock.card)
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
        color = if (danger) c.mock.danger else c.mock.accent,
        fontSize = 12.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/**
 * P2-1: the only place a capability becomes allowed.
 *
 * The agent may ask (`closecode consent request shell`), never grant. The ask appears here
 * and in a notification; the user taps Allow or Deny, and both decisions are audited.
 */
@Composable
internal fun AgentPermissionsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val store = remember(context) { ConsentStore(context) }
    var pending by remember { mutableStateOf(store.pending()) }
    var grants by remember { mutableStateOf(store.grants()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        pending = store.pending()
        grants = store.grants()
    }

    SettingsPageScaffold(title = "Agent permissions", onBack = onBack) {
        Text(
            "The agent asks for these; you decide. Nothing is allowed without your tap here.",
            color = c.mock.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )
        val request = pending
        if (request != null) {
            PageCard {
                Text(
                    "The agent asked for ${request.capability}",
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    request.scope?.let { "Scoped to $it." }
                        ?: "Applies to every app.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PageAction("Allow") {
                        store.resolve(request, approved = true)
                        AuditLog(context).record(
                            "consent approve",
                            request.scope,
                            listOf(request.capability),
                            true,
                            "granted: ${request.capability}",
                        )
                        message = "${request.capability} is now allowed."
                        refresh()
                    }
                    PageAction("Deny", danger = true) {
                        store.resolve(request, approved = false)
                        AuditLog(context).record(
                            "consent deny",
                            request.scope,
                            listOf(request.capability),
                            false,
                            "denied: ${request.capability}",
                        )
                        message = "${request.capability} stays off."
                        refresh()
                    }
                }
            }
        }
        ConsentStore.CAPABILITIES.forEach { capability ->
            val allowed = grants[capability] == true
            PageCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            capability,
                            color = c.mock.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when {
                                allowed -> "Allowed"
                                ConsentStore.isSensitive(capability) ->
                                    "Not allowed · only you can turn this on"
                                else -> "Not allowed"
                            },
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                    PageAction(if (allowed) "Revoke" else "Allow", danger = allowed) {
                        store.set(capability, allowed.not())
                        AuditLog(context).record(
                            if (allowed) "consent revoke" else "consent approve",
                            null,
                            listOf(capability),
                            true,
                            "${if (allowed) "revoked" else "granted"}: $capability",
                        )
                        message = "$capability is now ${if (allowed) "off" else "allowed"}."
                        refresh()
                    }
                }
            }
        }
        message?.let {
            Text(it, color = c.mock.muted, fontSize = 12.sp, fontFamily = Sans)
        }
    }
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
            Text("GitHub", color = c.mock.text, fontSize = 14.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold)
            Text(
                if (token == null) "Not connected" else "Access token stored securely",
                color = c.mock.muted,
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
            Text("Signing", color = c.mock.text, fontSize = 14.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold)
            Text(
                text = signingStore.allKeys().firstOrNull { it.id == signing }?.label
                    ?: "Debug key",
                color = c.mock.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
            )
            Text(
                "Passwords and private keys stay in app-private storage.",
                color = c.mock.muted,
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
    var licenseFiles by remember { mutableStateOf(store.licensesReady()) }
    var installed by remember { mutableStateOf(store.installedPlatforms().toSet()) }
    var buildTools by remember { mutableStateOf(store.buildToolsAapt2() != null) }
    var platformTools by remember { mutableStateOf(store.platformToolsReady()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<Int?>(null) }
    var pendingTools by remember { mutableStateOf(false) }

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

    /** P0-3: the same downloader the CLI uses, with the same pinned archive and checksum. */
    fun installPlatformTools() {
        busy = "Platform tools"
        progress = null
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.installPlatformTools { done, total ->
                    scope.launch { progress = done to total }
                }
            }
            platformTools = store.platformToolsReady()
            licenseFiles = store.licensesReady()
            busy = null
            progress = null
            message = result.exceptionOrNull()?.message ?: "Platform tools installed."
        }
    }

    SettingsPageScaffold(title = "SDK Manager", onBack = onBack) {
        Text(
            "Platforms are downloaded from Google. Build tools come from AndroidIDE; the " +
                "bundled aapt2 stays available as a fallback.",
            color = c.mock.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )
        if (!license) {
            PageCard {
                Text(
                    "The Android SDK License must be accepted before a platform can be installed.",
                    color = c.mock.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
                PageAction("Accept Android SDK License") {
                    store.acceptLicense()
                    license = true
                    licenseFiles = store.licensesReady()
                    if (pendingTools) {
                        pendingTools = false
                        installPlatformTools()
                    }
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
                            color = c.mock.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (platform.api in installed) "Installed · ${platform.fileName}"
                            else humanBytes(platform.sizeBytes),
                            color = c.mock.muted,
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
                "AndroidIDE build-tools 35.0.1",
                color = c.mock.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (buildTools) "Installed · aapt2 available"
                else "Not installed · bundled aapt2 is used",
                color = c.mock.muted,
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
        PageCard {
            Text(
                "Platform tools 34.0.4",
                color = c.mock.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (platformTools) "Installed · adb available"
                else "Not installed · Gradle needs this package",
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            if (platformTools) {
                PageAction("Remove", danger = true) {
                    if (store.removePlatformTools()) platformTools = false
                }
            } else {
                PageAction(
                    if (busy == "Platform tools") "Installing…" else "Install platform tools",
                ) {
                    if (busy != null) return@PageAction
                    if (!license) pendingTools = true else installPlatformTools()
                }
            }
        }
        PageCard {
            Text(
                "SDK licenses",
                color = c.mock.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (licenseFiles) "Written · android-sdk-license and previews"
                else "Not written · a Gradle build will fail",
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            if (licenseFiles) {
                PageAction("Remove", danger = true) {
                    store.removeAllLicenses()
                    licenseFiles = false
                }
            } else {
                PageAction("Write license files") {
                    store.acceptLicense()
                    license = true
                    licenseFiles = store.licensesReady()
                    if (pendingTools) {
                        pendingTools = false
                        installPlatformTools()
                    }
                }
            }
        }
        val current = busy
        if (current != null) {
            val p = progress
            Text(
                text = if (p == null || p.second <= 0) "$current…"
                else "$current · ${humanBytes(p.first)} of ${humanBytes(p.second)}",
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Mono,
            )
        }
        message?.let {
            Text(it, color = c.mock.muted, fontSize = 12.sp, fontFamily = Sans)
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
                            color = c.mock.text,
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
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Text(
                            key.subject,
                            color = c.mock.muted,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                            maxLines = 2,
                        )
                    }
                    Text(
                        if (key.id == selected) "Selected" else "Use",
                        color = if (key.id == selected) c.mock.accent else c.mock.muted,
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
                color = c.mock.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "PKCS#12 or JKS. The password is encrypted with the Android Keystore.",
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            PageAction("Choose keystore file") { picker.launch(arrayOf("*/*")) }
        }
        PageCard {
            Text(
                "Generate a release key",
                color = c.mock.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Generated keys stay on this phone and cannot be exported for Play Store upload.",
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            PageAction("Generate release key") { confirmGenerate = true }
        }
        message?.let { Text(it, color = c.mock.muted, fontSize = 12.sp, fontFamily = Sans) }
    }

    val pending = importBytes
    if (pending != null) {
        CcPopup(
            title = "Import signing key",
            onDismiss = {
                importBytes = null
                password = ""
            },
            actions = {
                CcPopupButton("Cancel", onClick = {
                    importBytes = null
                    password = ""
                })
                CcPopupButton(
                    "Import",
                    PopupButtonStyle.Filled,
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
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pending.second, color = c.mock.muted, fontSize = 12.sp, fontFamily = Mono)
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
        }
    }

    if (confirmGenerate) {
        CcConfirmPopup(
            title = "Generate release key?",
            message = "This key is generated inside the Android Keystore and cannot be exported. " +
                "Use it to sign APKs installed on this phone, not for a Play Store upload.",
            confirmText = "Generate",
            destructive = false,
            onConfirm = {
                confirmGenerate = false
                store.generateRelease()
                    .onSuccess { message = "Generated ${it.label}." }
                    .onFailure { message = it.message ?: "The key was not generated." }
                refresh()
            },
            onDismiss = { confirmGenerate = false },
        )
    }
}

@Composable
private fun JdkSelectionCard(onChanged: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val store = remember(context) { JdkStore(context) }
    val scope = rememberCoroutineScope()
    var jdks by remember { mutableStateOf<List<InstalledJdk>>(emptyList()) }
    var selectedPath by remember { mutableStateOf("") }
    var jdk21Ready by remember { mutableStateOf(false) }
    var jdk21Bytes by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ShellToolsStore.Progress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) {
            JdkSnapshot(
                jdks = store.installed(),
                selected = store.selected()?.home?.absolutePath.orEmpty(),
                jdk21Ready = store.jdk21Ready(),
                jdk21Bytes = store.jdk21Bytes(),
            )
        }
        jdks = snapshot.jdks
        selectedPath = snapshot.selected
        jdk21Ready = snapshot.jdk21Ready
        jdk21Bytes = snapshot.jdk21Bytes
    }

    fun choose(jdk: InstalledJdk) {
        if (busy) return
        scope.launch {
            withContext(Dispatchers.IO) { store.select(jdk) }
            refresh()
            onChanged()
        }
    }

    fun install21() {
        if (busy) return
        busy = true
        error = null
        progress = null
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                val result = store.install21 { update -> scope.launch { progress = update } }
                if (result.ok) {
                    val chosen = store.installed().firstOrNull {
                        it.home == store.jdk21Home ||
                            it.home.absolutePath == store.jdk21Home.absolutePath
                    }
                    if (chosen != null && store.selected()?.home == chosen.home) {
                        store.select(chosen)
                    }
                    null
                } else {
                    result.error ?: "JDK 21 did not install."
                }
            }
            busy = false
            progress = null
            refresh()
            if (failure != null) error = failure else onChanged()
        }
    }

    fun remove21() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val removed = withContext(Dispatchers.IO) { store.remove21() }
            busy = false
            refresh()
            if (!removed) error = "JDK 21 could not be removed." else onChanged()
        }
    }

    LaunchedEffect(store) { refresh() }

    PageCard {
        Text(
            text = "Java for Gradle",
            color = c.mock.text,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "JDK 17 is the default. JDK 8 and 11 are not offered because AGP 8.x " +
                "requires Java 17 or newer. The selected JDK is written to Gradle and used " +
                "by the editor, closecode build and ./gradlew.",
            color = c.mock.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )
        jdks.forEach { jdk ->
            Text(
                text = "JDK ${jdk.version} · ${jdk.home.absolutePath}",
                color = c.mock.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            Text(
                text = when {
                    !jdk.supported -> "${jdk.source} · unsupported by AGP 8.x"
                    jdk.home.absolutePath == selectedPath -> "${jdk.source} · selected"
                    else -> jdk.source
                },
                color = if (jdk.home.absolutePath == selectedPath) c.mock.green else c.mock.muted,
                fontSize = 11.sp,
                fontFamily = Sans,
            )
            if (jdk.supported && jdk.home.absolutePath != selectedPath) {
                PageAction("Use this JDK") { choose(jdk) }
            }
        }
        if (jdk21Ready) {
            Text(
                text = "JDK 21 · ${store.jdk21Home.absolutePath}",
                color = c.mock.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            Text(
                text = "${humanSize(jdk21Bytes)} on this phone",
                color = c.mock.muted,
                fontSize = 11.sp,
                fontFamily = Sans,
            )
            PageAction("Remove JDK 21", danger = true) { remove21() }
        } else {
            Text(
                text = "JDK 21 is optional. It downloads the current verified Termux OpenJDK 21 " +
                    "package; JDK 17 stays installed.",
                color = c.mock.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            PageAction("Install JDK 21") { install21() }
        }
        if (busy) {
            val current = progress
            Text(
                text = current?.message ?: "Preparing JDK 21…",
                color = c.mock.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            if (current != null && current.totalBytes > 0) {
                Text(
                    text = "${humanSize(current.doneBytes)} of ${humanSize(current.totalBytes)}",
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }
        }
        error?.let { message ->
            Text(
                text = message,
                color = c.mock.danger,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
        }
    }
}

private data class JdkSnapshot(
    val jdks: List<InstalledJdk>,
    val selected: String,
    val jdk21Ready: Boolean,
    val jdk21Bytes: Long,
)

@Composable
internal fun GradleToolsPage(
    onBack: () -> Unit,
    onOpenSdk: () -> Unit,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val store = remember(context) {
        GradleToolsStore(context, GradleToolsManifest.load(context))
    }
    val platform = remember(context) {
        PlatformStore(context, SharedPreferencesKeyValueStore(context))
    }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var sdkReady by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf(0L) }
    var packages by remember { mutableStateOf(emptyList<String>()) }
    var installing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ShellToolsStore.Progress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) {
            GradleSnapshot(
                ready = store.isReady(),
                sdkReady = platform.androidJar(ProjectPins.COMPILE_SDK)?.isFile == true &&
                    platform.buildToolsDir() != null,
                bytes = store.installedBytes(),
                packages = store.installedPackages(),
            )
        }
        ready = snapshot.ready
        sdkReady = snapshot.sdkReady
        bytes = snapshot.bytes
        packages = snapshot.packages
        loaded = true
    }

    fun install() {
        if (installing) return
        installing = true
        error = null
        progress = null
        fun report(stage: String, component: String, done: Long, total: Long, message: String) {
            scope.launch {
                progress = ShellToolsStore.Progress(stage, done, total, component, message)
            }
        }
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                try {
                    platform.acceptLicense()
                    report("download", "Android ${ProjectPins.COMPILE_SDK}", 0L, 0L,
                        "Downloading Android ${ProjectPins.COMPILE_SDK}…")
                    platform.installPlatform(ProjectPins.COMPILE_SDK) { done, total ->
                        report("download", "Android ${ProjectPins.COMPILE_SDK}", done, total,
                            "Downloading Android ${ProjectPins.COMPILE_SDK}…")
                    }.getOrThrow()
                    report("download", "Build tools ${platform.buildToolsVersion()}", 0L, 0L,
                        "Downloading build tools…")
                    platform.installBuildTools { done, total ->
                        report("download", "Build tools ${platform.buildToolsVersion()}", done, total,
                            "Downloading build tools…")
                    }.getOrThrow()
                    report("download", "Platform tools", 0L, 0L, "Downloading platform tools…")
                    platform.installPlatformTools { done, total ->
                        report("download", "Platform tools", done, total,
                            "Downloading platform tools…")
                    }.getOrThrow()
                    platform.installLicenses()
                    val result = store.install(progress = { update ->
                        report(update.stage, update.packageName ?: "JDK and Gradle",
                            update.doneBytes, update.totalBytes, update.message)
                    })
                    if (result.ok) null else result.error ?: "The Gradle tools did not install."
                } catch (problem: Throwable) {
                    problem.message ?: problem.javaClass.simpleName
                }
            }
            installing = false
            progress = null
            refresh()
            if (failure != null) {
                error = failure
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
            if (!removed) error = "The Gradle tools could not be removed."
            onChanged()
        }
    }

    LaunchedEffect(store) { refresh() }

    SettingsPageScaffold(title = "Full Gradle", onBack = onBack) {
        Text(
            text = "Full Gradle is the default engine whenever it is installed, for editor builds " +
                "and closecode build alike. The on-phone engine remains the fallback when it is " +
                "not. This pack adds OpenJDK 17 and Gradle ${store.manifest.distribution.version}.",
            color = c.mock.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )

        when {
            !loaded -> PageCard {
                Text("Checking Gradle tools…", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }

            installing -> PageCard {
                val current = progress
                Text(
                    text = current?.message ?: "Preparing the download…",
                    color = c.mock.text,
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
                            .background(c.mock.accent),
                    )
                }
                if (current != null && current.totalBytes > 0) {
                    Text(
                        text = "${humanSize(current.doneBytes)} of " +
                            humanSize(current.totalBytes),
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                Text(
                    text = "Keep CloseCode open while the toolchain downloads.",
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }

            removing -> PageCard {
                Text("Removing Gradle tools…", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }

            ready -> {
                PageCard {
                    Text(
                        text = "JDK and Gradle installed",
                        color = c.mock.green,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Selected Java · ${store.javaHome.absolutePath}",
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                    Text(
                        text = "Gradle ${store.manifest.distribution.version} · " +
                            store.gradle.absolutePath,
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                    Text(
                        text = "The editor, closecode build and ./gradlew all use the selected JDK.",
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                    Text(
                        text = "${humanSize(bytes)} on this phone",
                        color = c.mock.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                    if (packages.isNotEmpty()) {
                        Text(
                            text = packages.joinToString(" · "),
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = Sans,
                        )
                    }
                    Text(
                        text = if (sdkReady) {
                            "Android SDK packages are ready. Editor and CLI builds use " +
                                "Full Gradle by default."
                        } else {
                            "Android ${ProjectPins.COMPILE_SDK} and build-tools " +
                                "${platform.buildToolsVersion()} are still missing. " +
                                "Run the full toolchain download to repair them."
                        },
                        color = if (sdkReady) c.mock.muted else c.mock.danger,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                }
                if (!sdkReady) {
                    PageCard {
                        PageAction("Install missing Android SDK") { install() }
                        PageAction("Open SDK Manager") { onOpenSdk() }
                    }
                }
                PageCard {
                    PageAction("Remove Gradle tools", danger = true) { confirmRemove = true }
                }
            }

            else -> PageCard {
                Text(
                    text = "Not installed",
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The download includes OpenJDK 17, Gradle " +
                        "${store.manifest.distribution.version}, Android " +
                        "${ProjectPins.COMPILE_SDK}, build-tools " +
                        "${platform.buildToolsVersion()}, platform-tools and the SDK licenses.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = "This is a large download and needs several hundred MB free. " +
                        "A Wi-Fi connection is recommended.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                PageAction("Download full toolchain") { install() }
            }
        }

        if (loaded && !installing && !removing) {
            JdkSelectionCard(onChanged = onChanged)
        }

        error?.let { message ->
            PageCard {
                Text(
                    text = message,
                    color = c.mock.danger,
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
                    text = "Remove the downloaded JDK and Gradle? Projects and project files " +
                        "are not touched.",
                    color = c.mock.text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = Sans,
                )
                if (removing) {
                    Text("Removing…", color = c.mock.muted, fontSize = 12.sp, fontFamily = Sans)
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
                text = "Full Gradle is slower than CloseCode's engine and downloads project " +
                    "dependencies on its first build. The Android SDK license and network access " +
                    "still apply.",
                color = c.mock.muted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                fontFamily = Sans,
            )
        }
    }
}

private data class GradleSnapshot(
    val ready: Boolean,
    val sdkReady: Boolean,
    val bytes: Long,
    val packages: List<String>,
)

private data class NativeSnapshot(
    val ready: Boolean,
    val ndkReady: Boolean,
    val cmakeReady: Boolean,
    val bytes: Long,
    val gradleReady: Boolean,
)

@Composable
internal fun NativeToolsPage(
    onBack: () -> Unit,
    onOpenGradle: () -> Unit,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val store = remember(context) { NativeToolsStore(context) }
    val gradle = remember(context) { GradleToolsStore(context, GradleToolsManifest.load(context)) }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf(NativeSnapshot(false, false, false, 0L, false)) }
    var installing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ShellToolsStore.Progress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    suspend fun refresh() {
        snapshot = withContext(Dispatchers.IO) {
            NativeSnapshot(
                ready = store.isReady(),
                ndkReady = store.ndkReady(),
                cmakeReady = store.cmakeReady(),
                bytes = store.installedBytes(),
                gradleReady = gradle.isReady(),
            )
        }
        loaded = true
    }

    fun install() {
        if (installing) return
        installing = true
        error = null
        progress = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.install { update -> scope.launch { progress = update } }
            }
            installing = false
            progress = null
            refresh()
            if (!result.ok) {
                error = result.error ?: "The native tools did not install."
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
            if (!removed) error = "The native tools could not be removed."
            onChanged()
        }
    }

    LaunchedEffect(store) { refresh() }

    SettingsPageScaffold(title = "Native tools", onBack = onBack) {
        Text(
            text = "Projects with C/C++ sources need the Android NDK and CMake. These are " +
                "optional; projects without native code do not need them. Native projects " +
                "build with Full Gradle, which is selected automatically once installed.",
            color = c.mock.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )

        when {
            !loaded -> PageCard {
                Text("Checking native tools…", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }

            installing -> PageCard {
                val current = progress
                Text(
                    text = current?.message ?: "Preparing the download…",
                    color = c.mock.text,
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
                            .background(c.mock.accent),
                    )
                }
                if (current != null && current.totalBytes > 0) {
                    Text(
                        text = "${humanSize(current.doneBytes)} of " +
                            humanSize(current.totalBytes),
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                Text(
                    text = "Keep CloseCode open. The NDK alone is about 540 MB to download " +
                        "and more once unpacked.",
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }

            removing -> PageCard {
                Text("Removing native tools…", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }

            snapshot.ready -> {
                PageCard {
                    Text(
                        text = "NDK and CMake installed",
                        color = c.mock.green,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${humanSize(snapshot.bytes)} on this phone",
                        color = c.mock.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                    Text(
                        text = "NDK ${store.manifest.ndk.version} · " +
                            "CMake ${store.manifest.cmake.version}",
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                    Text(
                        text = if (snapshot.gradleReady) {
                            "Full Gradle is installed too. closecode build and the editor " +
                                "use it for this project by default."
                        } else {
                            "Full Gradle is still missing. Install it to build native projects."
                        },
                        color = if (snapshot.gradleReady) c.mock.muted else c.mock.danger,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                }
                if (!snapshot.gradleReady) {
                    PageCard {
                        PageAction("Open Full Gradle") { onOpenGradle() }
                    }
                }
                PageCard {
                    PageAction("Remove native tools", danger = true) { confirmRemove = true }
                }
            }

            else -> PageCard {
                Text(
                    text = "Not installed",
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "NDK ${store.manifest.ndk.version} and CMake " +
                        "${store.manifest.cmake.version} will be installed inside the " +
                        "CloseCode SDK folder, where Full Gradle looks for them.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = "The NDK download is about 540 MB and the installed tools take " +
                        "well over 1 GB. A Wi-Fi connection and free storage are recommended.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                if (!snapshot.ndkReady && !snapshot.cmakeReady) {
                    PageAction("Download NDK and CMake") { install() }
                } else {
                    PageAction("Finish native tools install") { install() }
                }
            }
        }

        error?.let { message ->
            PageCard {
                Text(
                    text = message,
                    color = c.mock.danger,
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
                    text = "Remove the downloaded NDK and CMake? Projects and project files " +
                        "are not touched. Native projects will stop building until reinstalled.",
                    color = c.mock.text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = Sans,
                )
                if (removing) {
                    Text("Removing…", color = c.mock.muted, fontSize = 12.sp, fontFamily = Sans)
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
                text = "The Android SDK Manager cannot install these packages: Google ships " +
                    "desktop NDK binaries. CloseCode uses the Android-native NDK and CMake " +
                    "builds from AndroidIDE-NDK.",
                color = c.mock.muted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                fontFamily = Sans,
            )
        }
    }
}

@Composable
internal fun OptionalPacksPage(
    onBack: () -> Unit,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val stores = remember(context) {
        OptionalPacks.all.associate { pack -> pack.id to OptionalPacks.store(context, pack) }
    }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var bytes by remember { mutableStateOf(emptyMap<String, Long>()) }
    var active by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ShellToolsStore.Progress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) {
            stores.mapValues { (_, store) -> store.isReady() to store.installedBytes() }
        }
        ready = snapshot.mapValues { it.value.first }
        bytes = snapshot.mapValues { it.value.second }
        loaded = true
    }

    fun install(pack: OptionalPack) {
        if (active != null) return
        val store = stores[pack.id] ?: return
        active = pack.id
        installing = true
        error = null
        progress = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.install { update -> scope.launch { progress = update } }
            }
            installing = false
            progress = null
            active = null
            refresh()
            if (!result.ok) error = result.error ?: "The ${pack.label} did not install."
            else onChanged()
        }
    }

    fun remove(pack: OptionalPack) {
        if (active != null) return
        val store = stores[pack.id] ?: return
        active = pack.id
        installing = false
        error = null
        scope.launch {
            val removed = withContext(Dispatchers.IO) { store.remove() }
            active = null
            refresh()
            if (!removed) error = "The ${pack.label} could not be removed."
            else onChanged()
        }
    }

    LaunchedEffect(stores) { refresh() }

    SettingsPageScaffold(title = "Optional packs", onBack = onBack) {
        Text(
            text = "Optional Termux packs install into CloseCode's private storage and use " +
                "the same downloader, checksums and Remove action as the CLI: " +
                "closecode setup --media, --go, --rust and the rest.",
            color = c.mock.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )
        if (!loaded) {
            PageCard {
                Text("Checking packs…", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }
        }
        OptionalPacks.all.forEach { pack ->
            val store = stores[pack.id]
            val isReady = ready[pack.id] == true
            val isActive = active == pack.id
            PageCard {
                Text(
                    text = pack.label,
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pack.description,
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = if (isReady) {
                        "Installed · ${humanSize(bytes[pack.id] ?: 0L)} · Termux ${store?.version.orEmpty()}"
                    } else {
                        "Not installed · Termux ${store?.version.orEmpty()}"
                    },
                    color = if (isReady) c.mock.green else c.mock.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                if (isActive && installing) {
                    val current = progress
                    Text(
                        text = current?.message ?: "Preparing the download…",
                        color = c.mock.text,
                        fontSize = 12.sp,
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
                                .background(c.mock.accent),
                        )
                    }
                } else if (active == null) {
                    if (confirmRemove == pack.id) {
                        Text(
                            text = "Remove ${pack.label}? Projects and project files are not touched.",
                            color = c.mock.text,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = Sans,
                        )
                        PageAction("Remove ${pack.label}", danger = true) {
                            confirmRemove = null
                            remove(pack)
                        }
                        PageAction("Cancel") { confirmRemove = null }
                    } else if (isReady) {
                        PageAction("Remove") { confirmRemove = pack.id }
                    } else {
                        PageAction("Install") { install(pack) }
                    }
                }
            }
        }
        error?.let { message ->
            PageCard {
                Text(
                    text = message,
                    color = c.mock.danger,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = Sans,
                )
            }
        }
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
    var pythonNeedsRepair by remember { mutableStateOf(false) }
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
        pythonNeedsRepair = withContext(Dispatchers.IO) {
            PythonPackRepair.needsRepair(store.directory)
        }
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

    fun repairPython() {
        if (installing || removing) return
        installing = true
        error = null
        progress = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.repairPython { update -> scope.launch { progress = update } }
            }
            installing = false
            progress = null
            refresh()
            if (!result.ok) {
                error = result.error ?: "Python could not be repaired."
            } else {
                onChanged()
            }
        }
    }

    LaunchedEffect(store) { refresh() }

    SettingsPageScaffold(title = "Shell tools", onBack = onBack) {
        Text(
            text = "Android's shell is real, but it only includes Android's own commands. " +
                "This optional pack adds the Termux builds of git, curl, Python, Node, zip " +
                "and jq under CloseCode's private storage. It does not need root.",
            color = c.mock.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )

        when {
            !loaded -> PageCard {
                Text("Checking shell tools…", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }

            installing -> PageCard {
                val current = progress
                Text(
                    text = current?.message ?: "Preparing the download…",
                    color = c.mock.text,
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
                            .background(c.mock.accent),
                    )
                }
                if (current != null && current.totalBytes > 0) {
                    Text(
                        text = "${humanSize(current.doneBytes)} of " +
                            humanSize(current.totalBytes),
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                Text(
                    text = "Keep CloseCode open while the packages download.",
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }

            removing -> PageCard {
                Text("Removing shell tools…", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }

            ready -> {
                PageCard {
                    Text(
                        text = "Installed",
                        color = c.mock.green,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${humanSize(bytes)} on this phone",
                        color = c.mock.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                    if (packages.isNotEmpty()) {
                        Text(
                            text = packages.joinToString(" · "),
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = Sans,
                        )
                    }
                    Text(
                        text = "New terminal tabs and OpenCode shells use these commands. " +
                            "Close and reopen an existing shell to pick them up.",
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                }
                if (pythonNeedsRepair) {
                    PageCard {
                        Text(
                            text = "Python's ensurepip bundle is missing the pip wheel, so " +
                                "python3 -m venv cannot create a working environment. Repair " +
                                "downloads the pinned, verified wheel.",
                            color = c.mock.text,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = Sans,
                        )
                        PageAction("Repair Python") { repairPython() }
                    }
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
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The download includes: " +
                        manifest.roots.joinToString(", ").ifBlank { "the Termux command pack" } +
                        ".",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = "Download size depends on the current Termux packages. " +
                        "A Wi-Fi connection is recommended.",
                    color = c.mock.muted,
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
                    color = c.mock.danger,
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
                    color = c.mock.text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = Sans,
                )
                if (removing) {
                    Text("Removing…", color = c.mock.muted, fontSize = 12.sp, fontFamily = Sans)
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
                color = c.mock.muted,
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
