package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.build.ProjectCheck
import com.closecode.build.ProjectPins
import com.closecode.build.SigningKeySource
import com.closecode.build.SigningStore
import com.closecode.build.TemplateContract
import com.closecode.ui.components.DesignBackButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.model.MinSdkOptions
import com.closecode.ui.model.SdkOptions
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/**
 * Project settings: APP, VERSION, ANDROID and SIGNING for the open project.
 *
 * A CloseCode project's package and SDK levels are editable; an imported or cloned project keeps
 * its own package and shows them here read-only. The design makes back save the changes, so the
 * back button saves when the draft is valid and there is something to save.
 *
 * [appName], [versionName] and [versionCode] are optional because the current caller does not read
 * them yet. When they are absent the APP rows say so instead of inventing values.
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
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    var draftPackage by remember(packageName) { mutableStateOf(packageName) }
    var draftMin by remember(minSdk) { mutableStateOf(minSdk) }
    var draftTarget by remember(targetSdk) { mutableStateOf(targetSdk) }
    var draftCompile by remember(compileSdk) { mutableStateOf(compileSdk) }
    var draftName by remember(appName) { mutableStateOf(appName) }
    var draftVersion by remember(versionName) { mutableStateOf(versionName) }
    var draftVersionCode by remember(versionCode) { mutableStateOf(versionCode) }

    val problem = ProjectCheck.packageProblem(draftPackage)
    val foreign = problem == null && !draftPackage.startsWith(TemplateContract.PACKAGE_PREFIX)
    val changed = draftPackage != packageName || draftMin != minSdk ||
        draftTarget != targetSdk || draftCompile != compileSdk
    val appChanged = draftName != appName || draftVersion != versionName ||
        draftVersionCode != versionCode
    val appEditable = onSaveApp != null
    val canSave = !imported && !busy && (changed || (appEditable && appChanged)) &&
        draftPackage.isNotBlank() && problem == null

    val signing = remember(context) { runCatching { SigningStore(context) }.getOrNull() }
    val debugReady = remember(signing) {
        signing?.let { store ->
            runCatching { store.debugInfo().subject != "Not generated" }.getOrDefault(false)
        } ?: false
    }
    val releaseReady = remember(signing) {
        runCatching {
            signing?.allKeys()?.any { it.source != SigningKeySource.Debug } == true
        }.getOrDefault(false)
    }

    fun save() {
        if (!canSave) return
        if (changed) {
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
        if (canSave) save()
        onDismiss()
    }
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
            DesignBackButton(
                onClick = {
                    if (canSave) save()
                    onDismiss()
                },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Project settings",
                    color = c.mock.text,
                    fontSize = 18.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = draftName.ifBlank { draftPackage },
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ProjectSectionLabel("App")
            if (!appEditable) {
                Text(
                    text = "App name and version are read from the project files. This build does " +
                        "not pass them to this screen yet.",
                    color = c.mock.faint,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            SettingsField(
                label = "App name",
                value = if (appEditable) draftName else draftName.ifBlank { "—" },
                enabled = appEditable,
                onValueChange = { draftName = it },
            )
            SettingsField(
                label = "Package name",
                value = draftPackage,
                enabled = !imported,
                onValueChange = { draftPackage = it.trim() },
            )
            when {
                imported -> Text(
                    text = "Imported and cloned projects keep their own package name.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
                problem != null -> Text(
                    text = problem.message,
                    color = c.mock.danger,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
                foreign -> Text(
                    text = "This app will be installed as $draftPackage.",
                    color = c.mock.warning,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
            }
            ProjectActionRow(
                label = "App icon",
                value = "Default",
                action = if (onPickIcon == null) "" else "Change",
                enabled = onPickIcon != null,
                onClick = { onPickIcon?.invoke() },
            )

            ProjectSectionLabel("Version")
            SettingsField(
                label = "Version name",
                value = if (appEditable) draftVersion else draftVersion.ifBlank { "—" },
                enabled = appEditable,
                onValueChange = { draftVersion = it },
            )
            SettingsField(
                label = "Version code",
                value = draftVersionCode.ifBlank { "—" },
                enabled = appEditable,
                onValueChange = { draftVersionCode = it },
            )

            ProjectSectionLabel("Android")
            SettingsDropdown(
                label = "Minimum SDK",
                value = draftMin,
                options = MinSdkOptions.filter { it.toIntOrNull()?.let { sdk -> sdk >= 24 } == true },
                enabled = !imported,
                onSelect = { draftMin = it },
            )
            SettingsDropdown(
                label = "Target SDK",
                value = draftTarget,
                options = SdkOptions,
                enabled = !imported,
                onSelect = { draftTarget = it },
            )
            SettingsDropdown(
                label = "Compile SDK",
                value = draftCompile,
                options = SdkOptions,
                enabled = !imported,
                onSelect = { draftCompile = it },
            )

            ProjectSectionLabel("Signing")
            ProjectActionRow(
                label = "Debug key",
                value = if (debugReady) "Created automatically · Ready" else "Not generated",
                action = "",
                enabled = false,
                onClick = {},
                ok = debugReady,
            )
            ProjectActionRow(
                label = "Release key",
                value = if (releaseReady) "Ready" else "Not set until you create or import one",
                action = "",
                enabled = false,
                onClick = {},
                ok = releaseReady,
            )

            ProjectSectionLabel("Check results")
            val findings = report?.findings.orEmpty().sortedBy { it.severity.ordinal }
            when {
                report == null -> Text(
                    text = "Checking…",
                    color = c.mock.muted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                findings.isEmpty() -> Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CcIcon(CcIcons.Check, c.mock.green, 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "No problems found.",
                        color = c.mock.text,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                }
                else -> findings.forEach { finding -> FindingRow(finding) }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                label = if (busy) "Saving…" else "Save changes",
                onClick = { save() },
                enabled = canSave,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProjectSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LocalPageColors.current.mock.faint,
        fontSize = 10.5.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProjectActionRow(
    label: String,
    value: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
    ok: Boolean? = null,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = c.mock.text,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                color = when (ok) {
                    true -> c.mock.green
                    false -> c.mock.muted
                    null -> c.mock.muted
                },
                fontSize = 11.5.sp,
                fontFamily = Sans,
                lineHeight = 15.sp,
            )
        }
        if (action.isNotEmpty()) {
            Text(
                text = action,
                color = if (enabled) c.mock.accent else c.mock.faint,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FindingRow(finding: ProjectCheck.Finding) {
    val c = LocalPageColors.current
    val color = when (finding.severity) {
        ProjectCheck.Severity.Error -> c.mock.danger
        ProjectCheck.Severity.Warning -> c.mock.warning
        ProjectCheck.Severity.Note -> c.mock.muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CcIcon(
            if (finding.severity == ProjectCheck.Severity.Warning) {
                CcIcons.Warning
            } else {
                CcIcons.AlertCircle
            },
            color,
            13.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = finding.message,
                color = c.mock.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                lineHeight = 17.sp,
            )
            if (finding.where.isNotEmpty()) {
                Text(
                    text = finding.where,
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                )
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
        Text(
            text = label,
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(top = 6.dp)
                .clip(shape)
                .background(c.mock.field)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = if (enabled) c.mock.text else c.mock.faint,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                ),
                cursorBrush = SolidColor(c.mock.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val c = LocalPageColors.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
        Text(
            text = label,
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(46.dp)
                    .clip(shape)
                    .background(c.mock.field)
                    .clickable(enabled = enabled) { open = !open }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    color = if (enabled) c.mock.text else c.mock.faint,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "⌄", color = c.mock.muted, fontSize = 13.sp, fontFamily = Sans)
            }
            if (open && enabled) {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 56.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(c.mock.panel)
                        .border(1.dp, c.mock.border, shape),
                ) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clickable {
                                    onSelect(option)
                                    open = false
                                }
                                .background(
                                    if (option == value) {
                                        c.mock.accent.copy(alpha = 0.12f)
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option,
                                color = if (option == value) c.mock.accent else c.mock.text,
                                fontSize = 13.sp,
                                fontFamily = Mono,
                            )
                        }
                    }
                }
            }
        }
    }
}
