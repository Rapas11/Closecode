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
import com.closecode.build.ProjectPins
import com.closecode.build.SdkPlatforms
import com.closecode.build.SigningKeySource
import com.closecode.build.SigningStore
import com.closecode.ai.OpenCode
import com.closecode.ai.OpenCodeDefaults
import com.closecode.tools.GradleToolsManifest
import com.closecode.tools.GradleToolsStore
import com.closecode.tools.NativeToolsStore
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
internal fun PageCard(content: @Composable ColumnScope.() -> Unit) {
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
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                store.install { update -> scope.launch { progress = update } }
            }
            installing = false
            progress = null
            refresh()
            if (!result.ok) {
                error = result.error ?: "The Gradle tools did not install."
            } else if (!sdkReady) {
                error = "The JDK and Gradle are installed. Install Android " +
                    "${ProjectPins.COMPILE_SDK} and build-tools " +
                    "${platform.buildToolsVersion()} in SDK Manager before building."
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
            text = "CloseCode builds with its own engine by default. This optional pack adds " +
                "a Termux OpenJDK 17 and the official Gradle ${store.manifest.distribution.version} " +
                "distribution, so projects that need Gradle can build with --gradle.",
            color = c.mu,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )

        when {
            !loaded -> PageCard {
                Text("Checking Gradle tools…", color = c.mu, fontSize = 13.sp, fontFamily = Sans)
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
                    text = "Keep CloseCode open while the JDK and Gradle download.",
                    color = c.mu,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }

            removing -> PageCard {
                Text("Removing Gradle tools…", color = c.mu, fontSize = 13.sp, fontFamily = Sans)
            }

            ready -> {
                PageCard {
                    Text(
                        text = "JDK and Gradle installed",
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
                        text = if (sdkReady) {
                            "Android SDK packages are ready. Add --gradle to a build to use " +
                                "the full Gradle engine."
                        } else {
                            "Android ${ProjectPins.COMPILE_SDK} and build-tools " +
                                "${platform.buildToolsVersion()} are still missing. Open SDK " +
                                "Manager to install them."
                        },
                        color = if (sdkReady) c.mu else c.red,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                }
                if (!sdkReady) {
                    PageCard {
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
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The download includes OpenJDK 17 and Gradle " +
                        "${store.manifest.distribution.version}. Android " +
                        "${ProjectPins.COMPILE_SDK} and build-tools " +
                        "${platform.buildToolsVersion()} are installed from SDK Manager.",
                    color = c.mu,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = "This is a large download and needs several hundred MB free. " +
                        "A Wi-Fi connection is recommended.",
                    color = c.mu,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                PageAction("Download JDK and Gradle") { install() }
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
                    text = "Remove the downloaded JDK and Gradle? Projects and project files " +
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
                text = "Full Gradle is slower than CloseCode's engine and downloads project " +
                    "dependencies on its first build. The Android SDK license and network access " +
                    "still apply.",
                color = c.mu,
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
                "build with Full Gradle (--gradle).",
            color = c.mu,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )

        when {
            !loaded -> PageCard {
                Text("Checking native tools…", color = c.mu, fontSize = 13.sp, fontFamily = Sans)
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
                    text = "Keep CloseCode open. The NDK alone is about 540 MB to download " +
                        "and more once unpacked.",
                    color = c.mu,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }

            removing -> PageCard {
                Text("Removing native tools…", color = c.mu, fontSize = 13.sp, fontFamily = Sans)
            }

            snapshot.ready -> {
                PageCard {
                    Text(
                        text = "NDK and CMake installed",
                        color = c.success,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${humanSize(snapshot.bytes)} on this phone",
                        color = c.mu,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                    Text(
                        text = "NDK ${store.manifest.ndk.version} · " +
                            "CMake ${store.manifest.cmake.version}",
                        color = c.mu,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = Sans,
                    )
                    Text(
                        text = if (snapshot.gradleReady) {
                            "Full Gradle is installed too. Use closecode build --gradle " +
                                "for this project."
                        } else {
                            "Full Gradle is still missing. Install it to build native projects."
                        },
                        color = if (snapshot.gradleReady) c.mu else c.red,
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
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "NDK ${store.manifest.ndk.version} and CMake " +
                        "${store.manifest.cmake.version} will be installed inside the " +
                        "CloseCode SDK folder, where Full Gradle looks for them.",
                    color = c.mu,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Sans,
                )
                Text(
                    text = "The NDK download is about 540 MB and the installed tools take " +
                        "well over 1 GB. A Wi-Fi connection and free storage are recommended.",
                    color = c.mu,
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
                    text = "Remove the downloaded NDK and CMake? Projects and project files " +
                        "are not touched. Native projects will stop building until reinstalled.",
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
                text = "The Android SDK Manager cannot install these packages: Google ships " +
                    "desktop NDK binaries. CloseCode uses the Android-native NDK and CMake " +
                    "builds from AndroidIDE-NDK.",
                color = c.mu,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                fontFamily = Sans,
            )
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
