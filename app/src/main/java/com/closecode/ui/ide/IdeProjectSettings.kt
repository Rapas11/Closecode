package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.graphics.BitmapFactory
import com.closecode.build.ProjectCheck
import com.closecode.build.ProjectPins
import com.closecode.build.SigningKeySource
import com.closecode.build.SigningStore
import com.closecode.build.TemplateContract
import com.closecode.ui.components.CcTextField
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.components.SettingRow
import com.closecode.ui.components.SettingsGroup
import com.closecode.ui.model.MinSdkOptions
import com.closecode.ui.model.SdkOptions
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File

private enum class ProjectField { AppName, PackageName, VersionName, VersionCode, MinSdk, TargetSdk, CompileSdk }

/**
 * Screen 45: APP, VERSION, ANDROID and SIGNING for the open project.
 *
 * Back saves any valid draft, matching the design. SDK and version rows open a small editor;
 * package and signing rows are read-only summaries that lead to their own screens.
 */
@Composable
fun IdeProjectSettingsSheet(
    imported: Boolean,
    packageName: String,
    minSdk: String,
    targetSdk: String,
    compileSdk: String,
    report: ProjectCheck.Report?,
    busy: Boolean,
    onSave: (packageName: String, minSdk: Int, targetSdk: Int, compileSdk: Int) -> Unit,
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
    appName: String = "",
    versionName: String = "",
    versionCode: String = "",
    onSaveApp: ((appName: String, versionName: String, versionCode: String) -> Unit)? = null,
    onPickIcon: (() -> Unit)? = null,
    projectRoot: File? = null,
    onSigning: (() -> Unit)? = null,
    onIcons: (() -> Unit)? = null,
) {
    val c = LocalCcColors.current
    val context = LocalContext.current
    var draftPackage by remember(packageName) { mutableStateOf(packageName) }
    var draftMin by remember(minSdk) { mutableStateOf(minSdk) }
    var draftTarget by remember(targetSdk) { mutableStateOf(targetSdk) }
    var draftCompile by remember(compileSdk) { mutableStateOf(compileSdk) }
    var draftName by remember(appName) { mutableStateOf(appName) }
    var draftVersion by remember(versionName) { mutableStateOf(versionName) }
    var draftVersionCode by remember(versionCode) { mutableStateOf(versionCode) }
    var editing by remember { mutableStateOf<ProjectField?>(null) }

    val problem = ProjectCheck.packageProblem(draftPackage)
    val foreign = problem == null && !draftPackage.startsWith(TemplateContract.PACKAGE_PREFIX)
    val sdkChanged = draftMin != minSdk || draftTarget != targetSdk || draftCompile != compileSdk
    val packageChanged = draftPackage != packageName
    val appChanged = draftName != appName || draftVersion != versionName ||
        draftVersionCode != versionCode
    val appEditable = onSaveApp != null
    val canSave = !imported && !busy && (sdkChanged || packageChanged || (appEditable && appChanged)) &&
        draftPackage.isNotBlank() && problem == null

    val signing = remember(context) { runCatching { SigningStore(context) }.getOrNull() }
    val debug = remember(signing) { runCatching { signing?.debugInfo() }.getOrNull() }
    val debugReady = !debug?.sha256.isNullOrBlank()
    val release = remember(signing) { runCatching { releaseFor(signing) }.getOrNull() }
    val releaseReady = release != null
    val icon = remember(projectRoot, report) { projectIcon(projectRoot) }

    fun save() {
        if (!canSave) return
        if (sdkChanged || packageChanged) {
            onSave(
                draftPackage,
                draftMin.toIntOrNull() ?: ProjectPins.MIN_SDK_FLOOR,
                draftTarget.toIntOrNull() ?: ProjectPins.MIN_SDK_FLOOR,
                draftCompile.toIntOrNull() ?: ProjectPins.COMPILE_SDK,
            )
        }
        if (appEditable && appChanged) {
            onSaveApp?.invoke(draftName, draftVersion, draftVersionCode)
        }
    }

    BackHandler {
        save()
        onDismiss()
    }

    Column(
        Modifier.fillMaxSize().background(c.content),
    ) {
        ScreenHeader(
            origin = draftName.ifBlank { draftPackage.ifBlank { "Project" } },
            title = "Project settings",
            onBack = {
                save()
                onDismiss()
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsGroup("App") {
                SettingRow(
                    icon = "file",
                    tint = c.blue,
                    title = "App name",
                    value = draftName.ifBlank { "—" },
                    onClick = if (appEditable) {
                        { editing = ProjectField.AppName }
                    } else {
                        null
                    },
                )
                SettingRow(
                    icon = "layers",
                    tint = c.purple,
                    title = "Package name",
                    sub = when {
                        imported -> "Imported projects keep their package"
                        problem != null -> problem.message
                        foreign -> "Installs as $draftPackage"
                        else -> null
                    },
                    value = draftPackage.ifBlank { "—" },
                    onClick = if (!imported && !busy) {
                        { editing = ProjectField.PackageName }
                    } else {
                        null
                    },
                    trailing = {},
                )
                SettingRow(
                    icon = "image",
                    tint = Color(0xFFDB4C9B),
                    title = "App icon",
                    sub = if (icon != null) "Custom launcher icon" else "Default Android icon",
                    onClick = onPickIcon,
                    showDivider = false,
                )
                SettingRow(
                    icon = "layers",
                    tint = c.purple,
                    title = "Icons",
                    sub = "Logo, project icons and icon packs",
                    onClick = onIcons,
                    showDivider = false,
                )
            }

            SettingsGroup("Version") {
                SettingRow(
                    icon = "flag",
                    tint = c.green,
                    title = "Version name",
                    value = draftVersion.ifBlank { "—" },
                    onClick = if (appEditable) {
                        { editing = ProjectField.VersionName }
                    } else {
                        null
                    },
                )
                SettingRow(
                    icon = "plus",
                    tint = c.blue,
                    title = "Version code",
                    value = draftVersionCode.ifBlank { "—" },
                    onClick = if (appEditable) {
                        { editing = ProjectField.VersionCode }
                    } else {
                        null
                    },
                    showDivider = false,
                )
            }

            SettingsGroup("Android") {
                SettingRow(
                    icon = "play",
                    tint = c.green,
                    title = "Minimum SDK",
                    value = draftMin.ifBlank { "—" },
                    onClick = if (!imported) {
                        { editing = ProjectField.MinSdk }
                    } else {
                        null
                    },
                )
                SettingRow(
                    icon = "play",
                    tint = c.yellow,
                    title = "Target SDK",
                    value = draftTarget.ifBlank { "—" },
                    onClick = if (!imported) {
                        { editing = ProjectField.TargetSdk }
                    } else {
                        null
                    },
                    showDivider = false,
                )
            }

            SettingsGroup("Signing") {
                SettingRow(
                    icon = "lock",
                    tint = c.teal,
                    title = "Debug key",
                    sub = "Created automatically",
                    trailing = {
                        Text(
                            text = if (debugReady) "Ready" else "Not created",
                            color = if (debugReady) c.green else c.amber,
                            fontSize = 12.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
                SettingRow(
                    icon = "lock",
                    tint = if (releaseReady) c.green else c.amber,
                    title = "Release key",
                    sub = "Needed to publish",
                    value = if (releaseReady) null else "Not set",
                    onClick = onSigning,
                    trailing = if (releaseReady) {
                        {
                            Text(
                                text = "Ready",
                                color = c.green,
                                fontSize = 12.5.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        null
                    },
                    showDivider = false,
                )
            }

            if (report != null) {
                val findings = report.findings.sortedBy { it.severity.ordinal }
                if (findings.isNotEmpty()) {
                    SettingsGroup("Check") {
                        findings.forEachIndexed { index, finding ->
                            SettingRow(
                                icon = if (finding.severity == ProjectCheck.Severity.Warning) "alert" else "close",
                                tint = when (finding.severity) {
                                    ProjectCheck.Severity.Error -> c.red
                                    ProjectCheck.Severity.Warning -> c.amber
                                    ProjectCheck.Severity.Note -> c.muted
                                },
                                title = finding.message,
                                sub = finding.where.ifBlank { null },
                                showDivider = index != findings.lastIndex,
                            )
                        }
                    }
                }
            }

            if (canSave) {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    BigButton(
                        text = if (busy) "Saving…" else "Save changes",
                        onClick = { save() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = "check",
                        enabled = !busy,
                    )
                }
            } else if (imported) {
                IconNote(
                    "Imported and cloned projects keep their own package and version values.",
                    Modifier.padding(horizontal = 18.dp),
                )
            }
                if (appChanged && !appEditable) {
                IconNote(
                    "This screen cannot write app name and version yet.",
                    Modifier.padding(horizontal = 18.dp),
                )
            }
        }
    }

    val active = editing
    if (active != null) {
        ProjectEditDialog(
            field = active,
            current = when (active) {
                ProjectField.AppName -> draftName
                ProjectField.PackageName -> draftPackage
                ProjectField.VersionName -> draftVersion
                ProjectField.VersionCode -> draftVersionCode
                ProjectField.MinSdk -> draftMin
                ProjectField.TargetSdk -> draftTarget
                ProjectField.CompileSdk -> draftCompile
            },
            options = when (active) {
                ProjectField.MinSdk -> MinSdkOptions.filter { it.toIntOrNull()?.let { sdk -> sdk >= 24 } == true }
                ProjectField.TargetSdk, ProjectField.CompileSdk -> SdkOptions
                else -> null
            },
            onDismiss = { editing = null },
            onValue = { value ->
                when (active) {
                    ProjectField.AppName -> draftName = value
                    ProjectField.PackageName -> draftPackage = value.trim()
                    ProjectField.VersionName -> draftVersion = value
                    ProjectField.VersionCode -> draftVersionCode = value.filter(Char::isDigit)
                    ProjectField.MinSdk -> draftMin = value
                    ProjectField.TargetSdk -> draftTarget = value
                    ProjectField.CompileSdk -> draftCompile = value
                }
                editing = null
            },
        )
    }
}

private fun releaseFor(signing: SigningStore?) =
    signing?.generatedInfo() ?: signing?.importedKeys()?.firstOrNull()

private fun projectIcon(root: File?): ImageBitmap? {
    val file = root?.let { File(it, ProjectIconFiles.LAUNCHER) } ?: return null
    if (!file.isFile) return null
    return runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
}

@Composable
private fun ProjectEditDialog(
    field: ProjectField,
    current: String,
    options: List<String>?,
    onDismiss: () -> Unit,
    onValue: (String) -> Unit,
) {
    val c = LocalCcColors.current
    var draft by remember(field) { mutableStateOf(current) }
    val title = when (field) {
        ProjectField.AppName -> "App name"
        ProjectField.PackageName -> "Package name"
        ProjectField.VersionName -> "Version name"
        ProjectField.VersionCode -> "Version code"
        ProjectField.MinSdk -> "Minimum SDK"
        ProjectField.TargetSdk -> "Target SDK"
        ProjectField.CompileSdk -> "Compile SDK"
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.panel)
                .padding(20.dp),
        ) {
            Text(
                title,
                color = c.text,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(12.dp))
            if (options == null) {
                CcTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = title,
                    mono = field != ProjectField.AppName,
                    hint = when (field) {
                        ProjectField.AppName -> "My app"
                        ProjectField.PackageName -> "com.example.app"
                        ProjectField.VersionName -> "1.0"
                        else -> "1"
                    },
                )
            } else {
                Column(
                    Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (option == draft) c.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { draft = option }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option,
                                color = if (option == draft) c.accent else c.text,
                                fontSize = 13.5.sp,
                                fontFamily = Mono,
                                modifier = Modifier.weight(1f),
                            )
                            if (option == draft) {
                                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(c.accent))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    bg = c.field,
                    fg = c.text2,
                )
                BigButton(
                    text = "Save",
                    onClick = { onValue(draft.trim()) },
                    modifier = Modifier.weight(1f),
                    enabled = draft.isNotBlank(),
                )
            }
        }
    }
}
