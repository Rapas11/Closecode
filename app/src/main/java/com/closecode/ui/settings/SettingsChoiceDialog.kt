package com.closecode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Sans

/** The list a settings row opens when it has more than one real value. */
@Composable
internal fun SettingsChoiceDialog(
    title: String,
    choices: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalCcColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        titleContentColor = c.text,
        textContentColor = c.text2,
        title = {
            Text(
                text = title,
                color = c.text,
                fontSize = 16.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.field),
            ) {
                choices.forEach { choice ->
                    val on = choice == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(choice) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = choice,
                            color = if (on) c.accent else c.text2,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) CcIcon("check", c.accent, 18.dp, 2.4f)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.muted, fontFamily = Sans)
            }
        },
    )
}
