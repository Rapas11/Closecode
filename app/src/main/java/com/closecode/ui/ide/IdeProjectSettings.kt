package com.closecode.ui.ide

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.build.ProjectCheck
import com.closecode.build.ProjectPins
import com.closecode.build.TemplateContract
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.TextLink
import com.closecode.ui.model.MinSdkOptions
import com.closecode.ui.model.SdkOptions
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/**
 * Project settings: the package and the Android versions the project check validates,
 * and the check's own results so the fix and the problem are one screen. A CloseCode
 * project's values are editable; an imported or cloned project keeps its own package
 * and shows them here read-only.
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
) {
    val c = LocalPageColors.current
    var draftPackage by remember(packageName) { mutableStateOf(packageName) }
    var draftMin by remember(minSdk) { mutableStateOf(minSdk) }
    var draftTarget by remember(targetSdk) { mutableStateOf(targetSdk) }
    var draftCompile by remember(compileSdk) { mutableStateOf(compileSdk) }

    val problem = ProjectCheck.packageProblem(draftPackage)
    val foreign = problem == null && !draftPackage.startsWith(TemplateContract.PACKAGE_PREFIX)
    val changed = draftPackage != packageName || draftMin != minSdk ||
        draftTarget != targetSdk || draftCompile != compileSdk
    val canSave = !imported && !busy && changed && draftPackage.isNotBlank() && problem == null

    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        Text(
            text = "PROJECT SETTINGS",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SettingsField(
                label = "Package",
                value = draftPackage,
                enabled = !imported,
                onValueChange = { draftPackage = it.trim() },
            )
            when {
                imported -> Text(
                    text = "Imported and cloned projects keep their own package name.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
                )
                problem != null -> Text(
                    text = problem.message,
                    color = c.red,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
                )
                foreign -> Text(
                    text = "This app will be installed as $draftPackage.",
                    color = c.amber,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
                )
            }
            SettingsDropdown(
                label = "Minimum Android version",
                value = draftMin,
                options = MinSdkOptions.filter { it.toIntOrNull() != null && it.toInt() >= 24 },
                enabled = !imported,
                onSelect = { draftMin = it },
            )
            SettingsDropdown(
                label = "Target Android version",
                value = draftTarget,
                options = SdkOptions,
                enabled = !imported,
                onSelect = { draftTarget = it },
            )
            SettingsDropdown(
                label = "Compile Android version",
                value = draftCompile,
                options = SdkOptions,
                enabled = !imported,
                onSelect = { draftCompile = it },
            )

            Text(
                text = "CHECK RESULTS",
                color = c.mu,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
            )
            val findings = report?.findings.orEmpty().sortedBy { it.severity.ordinal }
            when {
                report == null -> Text(
                    text = "Checking…",
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
                findings.isEmpty() -> Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CcIcon(CcIcons.Check, c.success, 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "No problems found.",
                        color = c.tx,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                }
                else -> findings.forEach { finding ->
                    FindingRow(finding)
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    label = if (busy) "Saving…" else "Save",
                    onClick = {
                        onSave(
                            draftPackage,
                            draftMin.toIntOrNull() ?: ProjectPins.MIN_SDK_FLOOR,
                            draftTarget.toIntOrNull() ?: ProjectPins.MIN_SDK_FLOOR,
                            draftCompile.toIntOrNull() ?: ProjectPins.COMPILE_SDK,
                        )
                    },
                    enabled = canSave,
                )
                TextLink(
                    label = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FindingRow(finding: ProjectCheck.Finding) {
    val c = LocalPageColors.current
    val color = when (finding.severity) {
        ProjectCheck.Severity.Error -> c.red
        ProjectCheck.Severity.Warning -> c.amber
        ProjectCheck.Severity.Note -> c.mu
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CcIcon(
            if (finding.severity == ProjectCheck.Severity.Warning) CcIcons.Warning else CcIcons.AlertCircle,
            color,
            13.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = finding.message,
                color = c.tx,
                fontSize = 13.sp,
                fontFamily = Sans,
                lineHeight = 17.sp,
            )
            if (finding.where.isNotEmpty()) {
                Text(
                    text = finding.where,
                    color = c.mu,
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
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        Text(
            text = label,
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(top = 6.dp)
                .clip(shape)
                .background(c.cd)
                .border(1.dp, c.ln, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = if (enabled) c.tx else c.mu,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                ),
                cursorBrush = SolidColor(c.ac),
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
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        Text(
            text = label,
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(40.dp)
                    .clip(shape)
                    .background(c.cd)
                    .border(1.dp, c.ln, shape)
                    .clickable(enabled = enabled) { open = !open }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    color = if (enabled) c.tx else c.mu,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "⌄", color = c.mu, fontSize = 13.sp, fontFamily = Sans)
            }
            if (open && enabled) {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 50.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(c.cd)
                        .border(1.dp, c.ln, shape),
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
                                    if (option == value) c.ac.copy(alpha = 0.12f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option,
                                color = if (option == value) c.ac else c.tx,
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
