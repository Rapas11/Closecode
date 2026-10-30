package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.closecode.ai.OpenCodeServerLog
import com.closecode.ui.components.TextLink
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The share side: a copy of the log leaves through the same provider the APK shares use. */
internal object OpenCodeLogShare {

    private const val SHARE_DIR = "share"

    /** Hands [logName] (server.log or server.1.log) to another app through FileProvider. */
    fun share(context: android.content.Context, logName: String) {
        runCatching {
            val shareDir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            val handed = java.io.File(shareDir, "opencode-$logName")
            java.io.File(OpenCodeServerLog.dir(context), logName).inputStream().use { input ->
                handed.outputStream().use { input.copyTo(it) }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                handed,
            )
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(send, "Share OpenCode log"),
            )
        }
    }
}

/**
 * The OpenCode server's own log, read aloud in the app: what the server printed, kept in the
 * file build 42 teaches it to keep. It is the answer to "it's broken" — the reason is on this
 * page, and the Share button hands the very file to whoever must see it.
 */
@Composable
internal fun OpenCodeLogPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val previous = remember { mutableStateOf(false) }
    val refreshTick = remember { mutableIntStateOf(0) }
    var lines by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val shown = File(
        OpenCodeServerLog.dir(context),
        if (previous.value) "server.1.log" else "server.log",
    )

    suspend fun readLog(): String = withContext(Dispatchers.IO) {
        shown.takeIf { it.isFile }?.readText().orEmpty()
    }

    LaunchedEffect(previous.value, refreshTick.intValue) {
        lines = readLog()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextLink(label = "‹ Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                text = "Server log",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            TextLink(
                label = if (previous.value) "Current" else "Older",
                onClick = { previous.value = !previous.value },
            )
            Spacer(Modifier.width(12.dp))
            TextLink(label = "Refresh", onClick = { refreshTick.intValue++ })
        }
        Text(
            text = if (shown.isFile) {
                val stamp = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
                    .format(Date(shown.lastModified()))
                "$stamp, ${shown.length() / 1024} KB"
            } else {
                "No log yet. OpenCode writes it when the server starts."
            },
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            if (lines.isEmpty()) {
                Text(
                    text = "The log is empty. The server has not said anything yet.",
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lines,
                        color = c.tx,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 400.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    com.closecode.ui.components.OutlineButton(
                        label = "Share this log",
                        onClick = {
                            OpenCodeLogShare.share(context, if (previous.value) "server.1.log" else "server.log")
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
