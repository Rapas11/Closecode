package com.closecode.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ai.OpenCode
import com.closecode.ai.OpenCodeBinary
import com.closecode.ai.OpenCodeDefaults
import com.closecode.ai.OpenCodeRuntime
import com.closecode.build.ToolchainHolder
import com.closecode.debug.BuildInfo
import com.closecode.rememberProjectRepository
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.TokenStore
import com.closecode.ui.ide.DefaultTerminalCommands
import com.closecode.ui.ide.GitHubSignIn
import com.closecode.ui.ide.TERMINAL_COMMANDS_KEY
import com.closecode.ui.ide.decodeCommands
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Sans
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads the facts the Storage and About rows report, and clears the cache when asked.
 *
 * Every line of it is I/O — a recursive directory walk is the expensive one — so it all sits on
 * [Dispatchers.IO] and the screen renders `…` until an answer comes back.
 */
private class SettingsFacts(
    private val context: Context,
    private val repository: ProjectRepository,
) {
    suspend fun measure(): SettingsLive = withContext(Dispatchers.IO) {
        val buildToolsBytes = dirSize(File(context.filesDir, "toolchain"))
        val shellStore = ShellToolsStore(context, ShellToolsManifest.load(context))
        val shellBytes = if (shellStore.isReady()) shellStore.installedBytes() else 0L
        val arch = OpenCodeRuntime.alpineArch(android.os.Build.SUPPORTED_ABIS.toList())
        val runtimeInstalled = arch != null &&
            OpenCodeRuntime.isInstalled(context, arch) &&
            OpenCodeBinary.isInstalled(context)
        val providerKey = File(OpenCode.home(context), "data/opencode/auth.json")
        SettingsLive(
            projectRoot = runCatching { repository.projectsDir().absolutePath }.getOrDefault("—"),
            projectsSize = humanSize(dirSize(repository.projectsDir())),
            cachesSize = humanSize(dirSize(context.cacheDir) + dirSize(context.codeCacheDir)),
            buildToolsSize = if (buildToolsBytes == 0L) "Not installed" else humanSize(buildToolsBytes),
            shellToolsSize = if (shellBytes == 0L) "Not installed" else humanSize(shellBytes),
            debugFilesSize = humanSize(
                dirSize(File(context.filesDir, "debug")) +
                    dirSize(File(context.filesDir, "crash")),
            ),
            freeSpace = humanSize(android.os.StatFs(context.filesDir.path).availableBytes),
            version = BuildInfo.label,
            toolchainStatus = toolchainStatus(),
            toolchainVersions = toolchainVersions(),
            shellToolsStatus = if (shellStore.isReady()) "Installed" else "Not installed",
            gradleToolsStatus = gradleToolsStatus(),
            nativeToolsStatus = nativeToolsStatus(),
            openCodeRuntimeStatus = if (runtimeInstalled) "Installed" else "Not installed",
            openCodeVersion = OpenCodeDefaults.VERSION,
            providerKeySet = providerKey.isFile && providerKey.length() > 0,
            savedCommandCount = runCatching {
                val kv = SharedPreferencesKeyValueStore(context)
                decodeCommands(kv.getString(TERMINAL_COMMANDS_KEY, null))
                    .ifEmpty { DefaultTerminalCommands }
                    .size
            }.getOrDefault(0),
        )
    }

    private fun gradleToolsStatus(): String {
        val store = com.closecode.tools.GradleToolsStore(
            context,
            com.closecode.tools.GradleToolsManifest.load(context),
        )
        return if (store.isReady()) "Installed" else "Not installed"
    }

    private fun nativeToolsStatus(): String {
        val store = com.closecode.tools.NativeToolsStore(context)
        return if (store.isReady()) "Installed" else "Not installed"
    }

    /** `1.9.24 · 3.46.0 · 8.13.25` — Kotlin, ECJ, R8, from the shipped pack descriptor. */
    private fun toolchainVersions(): String {
        val toolchain = com.closecode.build.ToolchainManifest.load(context)
        return listOf("kotlin-compiler", "ecj", "r8")
            .mapNotNull { id -> toolchain.byId(id)?.version }
            .joinToString(" · ")
    }

    /** Ready only when every pinned file sits at its manifest size. */
    private fun toolchainStatus(): String {
        val toolchain = com.closecode.build.ToolchainManifest.load(context)
        val dir = File(context.filesDir, "toolchain/${toolchain.version}")
        val missing = toolchain.files.count { component ->
            val file = File(dir, component.fileName)
            !(file.isFile && file.length() == component.sizeBytes)
        }
        return if (missing == 0) "Ready" else "$missing files missing"
    }

    /** Only ever touches directories the platform owns; nothing under `filesDir` is touched. */
    suspend fun clearCaches(): SettingsLive {
        withContext(Dispatchers.IO) {
            runCatching { context.cacheDir?.deleteRecursively() }
            runCatching { context.codeCacheDir?.deleteRecursively() }
        }
        return measure()
    }
}

@Composable
fun SettingsScreen(
    activeId: String,
    onActiveChange: (String) -> Unit,
    onBack: () -> Unit,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onGitHubChanged: () -> Unit = {},
    onOpenSetupGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = rememberProjectRepository()
    val facts = remember(context, repository) { SettingsFacts(context, repository) }
    val scope = rememberCoroutineScope()
    var live by remember { mutableStateOf(SettingsLive()) }
    var detailOpen by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(facts, refreshKey) { live = facts.measure() }

    var setupOpen by remember { mutableStateOf(false) }
    var shellToolsOpen by remember { mutableStateOf(false) }
    var gradleToolsOpen by remember { mutableStateOf(false) }
    var nativeToolsOpen by remember { mutableStateOf(false) }
    var sdkOpen by remember { mutableStateOf(false) }
    var signingOpen by remember { mutableStateOf(false) }
    var credentialsOpen by remember { mutableStateOf(false) }
    var infoPage by remember { mutableStateOf<String?>(null) }
    var comingSoon by remember { mutableStateOf<String?>(null) }
    var aiOpen by remember { mutableStateOf(false) }
    var storageProjectsOpen by remember { mutableStateOf(false) }
    var savedCommandsOpen by remember { mutableStateOf(false) }
    var savedSignInsOpen by remember { mutableStateOf(false) }
    var providerKeyOpen by remember { mutableStateOf(false) }
    var projectLocationOpen by remember { mutableStateOf(false) }
    var developerOpen by remember { mutableStateOf(false) }
    var confirmClearCaches by remember { mutableStateOf(false) }
    var confirmDeleteTools by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var editingString by remember { mutableStateOf<StringSetting?>(null) }
    var updatingRuntime by remember { mutableStateOf(false) }
    val toolchainStore = remember(context) { ToolchainHolder.store(context) }
    val kv = remember(context) { SharedPreferencesKeyValueStore(context) }

    if (developerOpen) {
        DeveloperPage(onBack = { developerOpen = false })
        return
    }
    if (sdkOpen) {
        SdkManagerPage(onBack = { sdkOpen = false })
        return
    }
    if (signingOpen) {
        SigningKeysPage(onBack = { signingOpen = false })
        return
    }
    if (credentialsOpen) {
        CredentialsPage(onBack = { credentialsOpen = false })
        return
    }
    if (aiOpen) {
        SettingsInfoPage(
            title = "OpenCode AI",
            body = "OpenCode runs on this phone for the open project.\n\n" +
                "This screen only sets up the runtime, the provider key and updates. " +
                "Chat lives in the OpenCode tool, not in the editor.",
            onBack = { aiOpen = false },
        )
        return
    }
    if (storageProjectsOpen) {
        StorageProjectsPage(onBack = { storageProjectsOpen = false })
        return
    }
    if (savedCommandsOpen) {
        SavedCommandsPage(
            store = kv,
            onBack = { savedCommandsOpen = false; refreshKey++ },
        )
        return
    }
    if (providerKeyOpen) {
        ProviderKeyPage(
            keySet = live.providerKeySet,
            authCommand = remember(context) { OpenCode.authCommand(context) },
            onBack = { providerKeyOpen = false },
        )
        return
    }
    if (projectLocationOpen) {
        ProjectLocationPage(
            path = live.projectRoot,
            onBack = { projectLocationOpen = false },
        )
        return
    }
    val page = infoPage
    if (page != null) {
        when (page) {
            "privacy" -> SettingsInfoPage("Privacy", PrivacyText) { infoPage = null }
            "licenses" -> SettingsInfoPage("Open source licenses", LicensesText) { infoPage = null }
            "location" -> SettingsInfoPage(
                "Project location",
                "Projects are stored at:\n\n${live.projectRoot}\n\n" +
                    "Changing the folder is not available in this build.",
            ) { infoPage = null }
            else -> SettingsInfoPage(page, "") { infoPage = null }
        }
        return
    }
    if (setupOpen) {
        ToolchainSetupScreen(
            store = toolchainStore,
            onClose = {
                setupOpen = false
                refreshKey++
            },
        )
        return
    }
    if (shellToolsOpen) {
        ShellToolsPage(
            onBack = { shellToolsOpen = false; refreshKey++ },
            onChanged = { refreshKey++ },
        )
        return
    }
    if (gradleToolsOpen) {
        GradleToolsPage(
            onBack = { gradleToolsOpen = false; refreshKey++ },
            onOpenSdk = { sdkOpen = true },
            onChanged = { refreshKey++ },
        )
        return
    }
    if (nativeToolsOpen) {
        NativeToolsPage(
            onBack = { nativeToolsOpen = false; refreshKey++ },
            onOpenGradle = {
                nativeToolsOpen = false
                gradleToolsOpen = true
            },
            onChanged = { refreshKey++ },
        )
        return
    }

    val tokenStore = remember(context) { TokenStore(context) }
    var githubToken by remember { mutableStateOf(tokenStore.read()) }
    var githubLogin by remember { mutableStateOf<String?>(null) }
    var signInOpen by remember { mutableStateOf(false) }
    val gitHubClient = remember { GitHubClient() }
    LaunchedEffect(githubToken) {
        val token = githubToken
        githubLogin = if (token == null) {
            null
        } else {
            withContext(Dispatchers.IO) { gitHubClient.currentUser(token).getOrNull()?.login }
        }
    }
    if (signInOpen) {
        GitHubSignIn(
            onSignedIn = { token ->
                val stored = tokenStore.write(token)
                githubToken = token
                signInOpen = false
                onGitHubChanged()
                if (!stored) {
                    android.widget.Toast.makeText(
                        context,
                        "Signed in for this session only: this phone cannot store the token securely.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onDismiss = { signInOpen = false },
        )
        return
    }
    if (savedSignInsOpen) {
        SavedSignInsPage(
            login = githubLogin,
            onBack = { savedSignInsOpen = false },
            onSignOut = {
                tokenStore.clear()
                githubToken = null
                onGitHubChanged()
                savedSignInsOpen = false
            },
        )
        return
    }

    val allSections = sections(live, githubLogin, settings)
    val current = allSections.firstOrNull { it.id == activeId } ?: allSections.first()

    fun toast(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun handleAction(action: SettingsAction) {
        when (action) {
            SettingsAction.CLEAR_CACHES -> confirmClearCaches = true
            SettingsAction.DOWNLOAD_TOOLCHAIN -> setupOpen = true
            SettingsAction.SETUP_GUIDE -> onOpenSetupGuide()
            SettingsAction.CONNECT_GITHUB -> signInOpen = true
            SettingsAction.DISCONNECT_GITHUB -> confirmSignOut = true
            SettingsAction.SDK_MANAGER -> sdkOpen = true
            SettingsAction.SHELL_TOOLS -> shellToolsOpen = true
            SettingsAction.FULL_GRADLE -> gradleToolsOpen = true
            SettingsAction.NATIVE_TOOLS -> nativeToolsOpen = true
            SettingsAction.SIGNING_KEYS -> signingOpen = true
            SettingsAction.CREDENTIALS -> credentialsOpen = true
            SettingsAction.LICENSES -> infoPage = "licenses"
            SettingsAction.PRIVACY -> infoPage = "privacy"
            SettingsAction.AI_SETTINGS -> aiOpen = true
            SettingsAction.STORAGE_PROJECTS -> storageProjectsOpen = true
            SettingsAction.BIOMETRIC_LOCK -> comingSoon = "Biometric lock"
            SettingsAction.EDIT_GIT_NAME -> editingString = StringSetting.GIT_NAME
            SettingsAction.EDIT_GIT_EMAIL -> editingString = StringSetting.GIT_EMAIL
            SettingsAction.EDIT_GIT_DEFAULT_BRANCH ->
                editingString = StringSetting.GIT_DEFAULT_BRANCH
            SettingsAction.DELETE_BUILD_TOOLS -> confirmDeleteTools = true
            SettingsAction.SAVED_COMMANDS -> savedCommandsOpen = true
            SettingsAction.SAVED_SIGN_INS -> savedSignInsOpen = true
            SettingsAction.AI_PROVIDER_KEY -> providerKeyOpen = true
            SettingsAction.UPDATE_RUNTIME -> {
                if (updatingRuntime) return
                updatingRuntime = true
                scope.launch {
                    val arch = OpenCodeRuntime.alpineArch(android.os.Build.SUPPORTED_ABIS.toList())
                    val ok = runCatching {
                        val resolved = arch ?: error("This phone's CPU has no OpenCode runtime.")
                        withContext(Dispatchers.IO) {
                            OpenCodeRuntime.ensure(context, resolved) {}
                            OpenCodeBinary.ensure(context, android.os.Build.SUPPORTED_ABIS.toList()) {}
                        }
                    }.isSuccess
                    updatingRuntime = false
                    toast(if (ok) "OpenCode runtime updated" else "Could not update OpenCode")
                    refreshKey++
                }
            }
            SettingsAction.SOURCE_CODE -> openUrl(context, "https://github.com/closecode")
            SettingsAction.PROJECT_LOCATION -> infoPage = "location"
            SettingsAction.STORAGE_INSPECT_PROJECTS -> storageProjectsOpen = true
            SettingsAction.STORAGE_INSPECT_BUILD_TOOLS -> gradleToolsOpen = true
            SettingsAction.STORAGE_INSPECT_SHELL_TOOLS -> shellToolsOpen = true
        }
    }

    BackHandler(enabled = detailOpen) { detailOpen = false }
    val selectedTitle = when (activeId) {
        "general" -> "General"
        "editor" -> "Editor"
        "intelligence" -> "Code Intelligence"
        "build" -> "Build & Toolchain"
        "terminal" -> "Terminal"
        "git" -> "Git & GitHub"
        "appearance" -> "Appearance"
        "storage" -> "Storage"
        "security" -> "Security"
        "about" -> "About"
        else -> null
    }
    val section = allSections.firstOrNull { it.id == activeId }
    val c = LocalCcColors.current
    Box(Modifier.fillMaxSize().background(c.panel)) {
        if (!detailOpen) {
            SettingsPanel(
                appName = "CloseCode",
                versionLine = BuildInfo.label,
                selected = selectedTitle,
                onOpen = { cat ->
                    val id = when (cat.title) {
                        "General" -> "general"
                        "Editor" -> "editor"
                        "Code Intelligence" -> "intelligence"
                        "Build & Toolchain" -> "build"
                        "Terminal" -> "terminal"
                        "Git & GitHub" -> "git"
                        "Appearance" -> "appearance"
                        "Storage" -> "storage"
                        "Security" -> "security"
                        "About" -> "about"
                        else -> activeId
                    }
                    onActiveChange(id)
                    detailOpen = true
                },
            )
        } else if (section != null) {
            if (section.id == "appearance") {
                AppearanceSettingsPage(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onBack = { detailOpen = false },
                )
            } else {
                SettingsPage(title = section.title, onBack = { detailOpen = false }) {
                    section.groups.forEach { group ->
                        SetGroup(group.title) {
                            group.rows.forEachIndexed { rIdx, row ->
                                SettingsRow(
                                    row = row,
                                    settings = settings,
                                    onSettingsChange = onSettingsChange,
                                    onAction = ::handleAction,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                if (rIdx != group.rows.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(c.divider)
                                            .padding(start = 62.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val editing = editingString
    if (editing != null) {
        var draft by remember(editing) { mutableStateOf(settings.text(editing)) }
        val title = when (editing) {
            StringSetting.GIT_NAME -> "Git author name"
            StringSetting.GIT_EMAIL -> "Git author email"
            StringSetting.GIT_DEFAULT_BRANCH -> "Default branch"
        }
        AlertDialog(
            onDismissRequest = { editingString = null },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = {
                        Text(if (editing == StringSetting.GIT_DEFAULT_BRANCH) "Branch" else "Value")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSettingsChange(settings.withText(editing, draft))
                        editingString = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingString = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmClearCaches) {
        AlertDialog(
            onDismissRequest = { confirmClearCaches = false },
            title = { Text("Clear caches?") },
            text = { Text("Cached build and editor files will be deleted. Projects are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearCaches = false
                        scope.launch {
                            live = facts.clearCaches()
                            toast("Caches cleared")
                        }
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCaches = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmDeleteTools) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTools = false },
            title = { Text("Delete build tools?") },
            text = { Text("You can download them again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteTools = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                ToolchainHolder.deleteEverything(context)
                            }
                            toast(if (ok) "Build tools deleted" else "Nothing to delete")
                            refreshKey++
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTools = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of GitHub?") },
            text = { Text("The stored token will be removed from this phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        tokenStore.clear()
                        githubToken = null
                        onGitHubChanged()
                        toast("Disconnected from GitHub")
                    },
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }

    val soon = comingSoon
    if (soon != null) {
        AlertDialog(
            onDismissRequest = { comingSoon = null },
            title = { Text("Coming soon") },
            text = {
                Text("$soon is not available in this build yet.")
            },
            confirmButton = {
                TextButton(onClick = { comingSoon = null }) { Text("OK") }
            },
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        android.widget.Toast.makeText(context, "No app can open this link", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Exposed for the settings tests that pin the wording of the fixed auto-save row. */
internal fun toggleStateLabel(on: Boolean): String = if (on) "On" else "Off"

/**
 * One option's label, ticked when it is the choice in force. The tick is the cue that survives
 * without colour: weight and accent already mark the chosen row, and a tick says the same thing to
 * anyone who cannot tell those apart.
 */
internal fun optionLabel(choice: String, selected: Boolean, unit: String): String =
    (if (selected) "✓ " else "") + choice + unit
