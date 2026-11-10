package com.closecode.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The list a settings row opens when it has more than one real value. */
@Composable
internal fun SettingsChoiceDialog(
    title: String,
    choices: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CcPopup(
        title = title,
        onDismiss = onDismiss,
        actions = {
            CcPopupButton("Cancel", onClick = onDismiss)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            choices.forEach { choice ->
                CcChoiceRow(
                    label = choice,
                    selected = choice == selected,
                    onClick = { onPick(choice) },
                )
            }
        }
    }
}
