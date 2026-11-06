package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.BuildTypeOptions
import com.closecode.ui.model.GradleOptions
import com.closecode.ui.model.LanguageOptions
import com.closecode.ui.model.MinSdkOptions
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SdkOptions
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.model.gradleCompatibilityProblem
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

@Composable
fun ConfigForm(
    config: ProjectConfig,
    onConfigChange: (ProjectConfig) -> Unit,
    webProject: Boolean,
    modifier: Modifier = Modifier,
    packageError: String? = null,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!webProject) {
            ConfigTextField(
                label = "Package name",
                value = config.packageName,
                onValueChange = { onConfigChange(config.copy(packageName = it.trimStart())) },
                error = packageError,
                hint = "com.yourname.app",
            )
            ConfigDropdown(
                label = "Language",
                value = config.language,
                options = LanguageOptions,
                onSelect = { onConfigChange(config.copy(language = it)) },
            )
            ConfigDropdown(
                label = "Gradle version",
                value = config.gradleVersion,
                options = GradleOptions,
                onSelect = { onConfigChange(config.copy(gradleVersion = it)) },
            )
            Text(
                text = "Used for desktop builds and export. On-device builds use the " +
                    "bundled toolchain.",
                color = c.mock.muted,
                fontSize = 11.sp,
                fontFamily = Sans,
            )
            gradleCompatibilityProblem(config)?.let { problem ->
                Text(
                    text = problem,
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }
            ConfigDropdown(
                label = "Minimum SDK",
                value = config.minSdk,
                options = MinSdkOptions,
                onSelect = { onConfigChange(config.copy(minSdk = it)) },
            )
            ConfigDropdown(
                label = "Compile SDK",
                value = config.compileSdk,
                options = SdkOptions,
                onSelect = {
                    onConfigChange(config.copy(compileSdk = it, targetSdk = it))
                },
            )
            ConfigDropdown(
                label = "Target SDK",
                value = config.targetSdk,
                options = SdkOptions,
                onSelect = { onConfigChange(config.copy(targetSdk = it)) },
            )
        }
        ConfigDropdown(
            label = "Build configuration",
            value = config.buildType,
            options = BuildTypeOptions,
            onSelect = { onConfigChange(config.copy(buildType = it)) },
        )
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    hint: String? = null,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(15.dp)
    Column {
        Text(
            text = label,
            color = c.mock.textSoft,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(shape)
                .background(c.mock.input)
                .border(
                    1.dp,
                    if (error != null) c.mock.danger else Color.Transparent,
                    shape,
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && hint != null) {
                Text(
                    text = hint,
                    color = c.mock.faint,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                ),
                cursorBrush = SolidColor(c.mock.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Text(
                text = it,
                color = c.mock.danger,
                fontSize = 11.5.sp,
                fontFamily = Sans,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 4.dp, top = 5.dp),
            )
        }
    }
}

@Composable
private fun ConfigDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    val c = LocalPageColors.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)

    Column {
        Text(
            text = label,
            color = c.mock.textSoft,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(shape)
                    .background(c.mock.input)
                    .clickable { open = !open }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    modifier = Modifier.weight(1f),
                )
                CcIcon(CcIcons.Chevron, c.mock.muted, 14.dp)
            }

            if (open) {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 54.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(c.mock.panel)
                        .border(1.dp, c.mock.border, shape)
                ) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable {
                                    onSelect(option)
                                    open = false
                                }
                                .background(
                                    if (option == value) c.mock.selected else Color.Transparent
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option,
                                color = if (option == value) c.mock.accent else c.mock.text,
                                fontSize = 14.sp,
                                fontFamily = Sans,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigSummary(config: ProjectConfig, webProject: Boolean, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val rows = if (webProject) {
        listOf("Build configuration" to config.buildType)
    } else {
        listOf(
            "Package name" to config.packageName,
            "Language" to config.language,
            "Gradle" to config.gradleVersion,
            "SDK" to "min ${config.minSdk} · target ${config.targetSdk}",
            "Build configuration" to config.buildType,
        )
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    color = c.mock.text,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
        }
    }
}
