package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/** Every full screen uses this. `origin` is the screen the user came from (bold),
 *  `title` is this screen (muted). */
@Composable
fun ScreenHeader(
    origin: String?,
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val c = CcTheme.colors
    Column(Modifier.background(c.content)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.field)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("back", c.text2, 20.dp, 2.2f)
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (origin != null) {
                    OneLine(origin, CcType.screenTitle, c.text, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(7.dp))
                    OneLine(title, CcType.caption.copy(fontWeight = FontWeight.Medium), c.muted)
                } else {
                    OneLine(title, CcType.screenTitle, c.text)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.divider),
        )
    }
}

@Composable
fun HeaderAction(
    icon: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = CcTheme.colors
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.field)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, if (enabled) c.text2 else c.faint, 18.dp, 1.9f)
    }
}
